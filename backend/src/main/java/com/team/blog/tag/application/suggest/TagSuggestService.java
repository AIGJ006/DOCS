package com.team.blog.tag.application.suggest;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.post.application.OwnedPost;
import com.team.blog.post.application.PostOwnershipQuery;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.tag.application.suggest.TagSuggesterRouter.Route;
import com.team.blog.tag.application.suggest.TagSuggesterRouter.Routed;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 태그 추천 (013 T027·T042·T047, research R4, data-model §7). Bean 이름은 008 자동완성 {@code
 * tag.application.TagSuggestService}와 겹치지 않게 {@code aiTagSuggestService}다.
 *
 * <p>판정 순서: 401(컨트롤러) → 403 계정 상태({@code CONTENT_WRITE}) → 404 내 글 아님 → 503 {@code DISABLED} → 409
 * 동의 → 400 본문 형식 → 422 정리 후 짧음 → 남은 자리 0이면 {@code []}(AI 안 부름) → 재사용(횟수 그대로) → 429 하루 한도 → 공급자.
 *
 * <p>트랜잭션을 열지 않는다 — Redis 쓰기가 DB 트랜잭션에 묶이지 않고(05 J-5), 공급자 호출 동안 연결을 잡지 않는다. DB에 쓰는 것이 없어 추천 실패가 글
 * 저장·발행에 닿지 않는다(SC-002). 서버는 태그를 저장하지 않는다(SC-003).
 *
 * <p>로그는 요청마다 INFO 한 줄({@code ai tag-suggest post=… provider=… cached=… outcome=… inputChars=…
 * took=…ms}). 제목·본문·태그 이름·키는 남기지 않는다(SC-008).
 */
@Service("aiTagSuggestService")
public class TagSuggestService {

    private static final Logger log = LoggerFactory.getLogger(TagSuggestService.class);

    private final AccountStatusGuard accountStatusGuard;
    private final PostOwnershipQuery ownership;
    private final AiConsentService consent;
    private final SuggestInputCleaner cleaner;
    private final SuggestResultFilter filter;
    private final SuggestCache cache;
    private final DailyUsage usage;
    private final PopularTagProvider popular;
    private final TagSuggesterRouter router;
    private final TagSuggestProperties properties;
    private final SuggestPostLimits limits;
    private final Clock clock;

    public TagSuggestService(
            AccountStatusGuard accountStatusGuard,
            PostOwnershipQuery ownership,
            AiConsentService consent,
            SuggestInputCleaner cleaner,
            SuggestResultFilter filter,
            SuggestCache cache,
            DailyUsage usage,
            PopularTagProvider popular,
            TagSuggesterRouter router,
            TagSuggestProperties properties,
            SuggestPostLimits limits,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.ownership = ownership;
        this.consent = consent;
        this.cleaner = cleaner;
        this.filter = filter;
        this.cache = cache;
        this.usage = usage;
        this.popular = popular;
        this.router = router;
        this.properties = properties;
        this.limits = limits;
        this.clock = clock;
    }

    /** 추천 받기 ({@code POST /api/posts/{postId}/tag-suggestions}). */
    public TagSuggestResult suggest(long memberId, String rawPostId, TagSuggestRequest request) {
        long started = System.nanoTime();
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        OwnedPost post = requireOwned(rawPostId, memberId);
        Outcome line = new Outcome(post.id(), started);
        try {
            TagSuggestResult result = suggest(memberId, post, request, line);
            line.ok(result);
            return result;
        } catch (AiUnavailableException e) {
            line.failed(
                    e.reason() == AiUnavailableReason.BUSY
                            ? "BUSY"
                            : e.reason() == AiUnavailableReason.DISABLED ? "DISABLED" : "FAILED");
            throw e;
        } catch (AiConsentRequiredException e) {
            line.failed("CONSENT");
            throw e;
        } catch (ContentTooShortException e) {
            line.failed("TOO_SHORT");
            throw e;
        } catch (AiDailyLimitException e) {
            line.failed("LIMIT");
            throw e;
        } catch (RuntimeException e) {
            line.failed("ERROR");
            throw e;
        }
    }

    private TagSuggestResult suggest(
            long memberId, OwnedPost post, TagSuggestRequest request, Outcome line) {
        if (!properties.enabled()) {
            throw new AiUnavailableException(AiUnavailableReason.DISABLED);
        }
        if (!consent.isConsented(memberId)) {
            throw new AiConsentRequiredException(consent.currentVersion());
        }
        TagSuggestRequest body =
                request == null ? new TagSuggestRequest("", "", List.of(), false) : request;
        validate(body);
        CleanedInput input = cleaner.clean(body.title(), body.contentMd());
        line.inputChars = input.length();
        if (input.length() < properties.minInputChars()) {
            throw new ContentTooShortException(properties.minInputChars(), input.length());
        }
        Instant now = clock.instant();
        List<String> current = body.currentTags();

        if (filter.slots(current) == 0) {
            Provider predicted = router.choose(post, now).provider().orElse(Provider.OLLAMA);
            line.provider = predicted;
            return new TagSuggestResult(
                    List.of(), predicted, false, false, usage.remaining(memberId, now));
        }

        Optional<SuggestCache.Hit> hit =
                cache.lookup(
                        post.id(),
                        input,
                        body.isRefresh(),
                        () -> router.choose(post, now) == Route.GEMINI);
        if (hit.isPresent()) {
            Provider provider = hit.get().provider();
            line.provider = provider;
            line.cached = true;
            return new TagSuggestResult(
                    filter.select(hit.get().tags(), current),
                    provider,
                    true,
                    input.longerThan(properties.maxInputChars(provider)),
                    usage.remaining(memberId, now));
        }

        int used = usage.reserve(memberId, now);
        Routed routed;
        try {
            Route route = router.choose(post, now);
            if (route == Route.DISABLED) {
                throw new AiUnavailableException(AiUnavailableReason.DISABLED);
            }
            routed =
                    router.suggest(
                            route,
                            input,
                            popular.today(now),
                            filter.normalizedCurrent(current),
                            now);
        } catch (RuntimeException e) {
            usage.release(memberId, now);
            throw e;
        }
        line.provider = routed.provider();
        switch (routed.outcome()) {
            case SuggestOutcome.Success success -> {
                List<String> accepted = filter.accepted(success.rawTags());
                cache.store(post.id(), input, accepted, routed.provider(), now);
                return new TagSuggestResult(
                        filter.select(accepted, current),
                        routed.provider(),
                        false,
                        routed.truncated(),
                        Math.max(0, properties.dailyLimitPerMember() - used));
            }
            case SuggestOutcome.Busy busy -> {
                usage.release(memberId, now);
                throw new AiUnavailableException(AiUnavailableReason.BUSY);
            }
            case SuggestOutcome.Failed failed -> {
                usage.release(memberId, now);
                throw new AiUnavailableException(AiUnavailableReason.FAILED);
            }
            case SuggestOutcome.QuotaExceeded quota -> {
                usage.release(memberId, now);
                throw new AiUnavailableException(AiUnavailableReason.FAILED);
            }
        }
    }

    /** 추천 버튼 상태 ({@code GET …/tag-suggestions/status}). 판정 순서 401 → 403 → 404 → 200. */
    public TagSuggestStatusView status(long memberId, String rawPostId) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        OwnedPost post = requireOwned(rawPostId, memberId);
        boolean consentRequired = !consent.isConsented(memberId);
        String version = consent.currentVersion();
        Instant now = clock.instant();
        try {
            Route route = router.choose(post, now);
            int remaining = usage.remaining(memberId, now);
            Provider provider = route.provider().orElse(null);
            return new TagSuggestStatusView(
                    provider != null, consentRequired, version, provider, remaining);
        } catch (AiUnavailableException e) {
            return new TagSuggestStatusView(false, consentRequired, version, null, 0);
        }
    }

    private OwnedPost requireOwned(String rawPostId, long memberId) {
        long postId;
        try {
            postId = Long.parseLong(rawPostId);
        } catch (NumberFormatException | NullPointerException e) {
            throw new NotFoundException("post id is not a number");
        }
        if (postId <= 0) {
            throw new NotFoundException("post id is not positive");
        }
        return ownership
                .findOwned(postId, memberId)
                .orElseThrow(() -> new NotFoundException("not my post"));
    }

    /** 400 — 제목·본문 길이(코드 포인트, 002 {@code blog.post.*})·붙인 태그 수. */
    private void validate(TagSuggestRequest body) {
        List<FieldError> errors = new ArrayList<>();
        if (codePoints(body.title()) > limits.titleMax()) {
            errors.add(
                    new FieldError(
                            "title", "TITLE_TOO_LONG", "제목은 " + limits.titleMax() + "자까지예요"));
        }
        if (codePoints(body.contentMd()) > limits.contentMax()) {
            errors.add(
                    new FieldError(
                            "contentMd",
                            "CONTENT_TOO_LONG",
                            "본문은 " + String.format("%,d", limits.contentMax()) + "자까지예요"));
        }
        if (body.currentTags().size() > limits.maxTags()) {
            errors.add(new FieldError("currentTags", "TOO_MANY_TAGS", "태그가 너무 많아요"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static int codePoints(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /** 요청 한 줄 로그. */
    private static final class Outcome {
        private final long postId;
        private final long started;
        Provider provider;
        boolean cached;
        int inputChars;

        Outcome(long postId, long started) {
            this.postId = postId;
            this.started = started;
        }

        void ok(TagSuggestResult result) {
            write(result.tags().isEmpty() ? "EMPTY" : "OK");
        }

        void failed(String outcome) {
            write(outcome);
        }

        private void write(String outcome) {
            log.info(
                    "ai tag-suggest post={} provider={} cached={} outcome={} inputChars={} took={}ms",
                    postId,
                    provider == null ? "NONE" : provider,
                    cached,
                    outcome,
                    inputChars,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
