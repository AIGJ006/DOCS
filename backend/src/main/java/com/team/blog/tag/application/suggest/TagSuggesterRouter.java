package com.team.blog.tag.application.suggest;

import com.team.blog.post.application.OwnedPost;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 공급자 고르기와 전환 (013 T025·T047·T048, contracts/providers.md §5, research R6).
 *
 * <table>
 *   <caption>고르기</caption>
 *   <tr><td>기능 꺼짐</td><td>{@link Route#DISABLED}</td></tr>
 *   <tr><td>공개 범위가 PUBLIC 아님</td><td>자체 AI만 (꺼졌으면 {@link Route#NONE}) — 외부로 보내지 않는다(FR-030)</td></tr>
 *   <tr><td>Gemini 키 없음·소진·쉬는 중·우리 집계 한도</td><td>자체 AI | NONE</td></tr>
 *   <tr><td>그 밖</td><td>Gemini</td></tr>
 * </table>
 *
 * Gemini 429는 같은 요청을 자체 AI로 다시 보내고, 시간 초과·서버 오류·연결 실패는 60초 쉬게 하고 이번 요청은 실패로 끝낸다(자체 AI까지 기다리게 하지 않음,
 * FR-019). 자체 AI는 동시 처리 수를 넘으면 {@link SuggestOutcome.Busy}.
 *
 * <p>공급자 Bean이 여럿이면 순서가 가장 앞선 것을 쓴다(시험의 가짜 공급자). 꺼짐을 따로 Bean({@code DisabledTagSuggester})으로 두지 않고
 * {@link Route#DISABLED}로 나타낸다.
 */
@Component
public class TagSuggesterRouter {

    /** 고른 길. */
    public enum Route {
        DISABLED,
        NONE,
        GEMINI,
        OLLAMA;

        public Optional<Provider> provider() {
            return switch (this) {
                case GEMINI -> Optional.of(Provider.GEMINI);
                case OLLAMA -> Optional.of(Provider.OLLAMA);
                default -> Optional.empty();
            };
        }
    }

    /**
     * 호출 결과.
     *
     * @param provider 마지막으로 부른 공급자 (아무도 부르지 않았으면 {@code null})
     * @param truncated 그 공급자 최대 길이로 입력을 잘랐는가
     * @param outcome 결과
     */
    public record Routed(Provider provider, boolean truncated, SuggestOutcome outcome) {}

    private final Map<Provider, TagSuggester> suggesters = new EnumMap<>(Provider.class);
    private final ProviderState state;
    private final TagSuggestProperties properties;

    /** {@code suggesters}는 Spring이 순서대로 넣은 목록이다 — 공급자마다 처음 것을 쓴다. */
    public TagSuggesterRouter(
            List<TagSuggester> suggesters, ProviderState state, TagSuggestProperties properties) {
        for (TagSuggester s : suggesters) {
            this.suggesters.putIfAbsent(s.provider(), s);
        }
        this.state = state;
        this.properties = properties;
    }

    /** 지금 고를 길 (Redis 상태를 읽는다 — 장애면 503 {@code STORE_UNAVAILABLE}). */
    public Route choose(OwnedPost post, Instant now) {
        if (!properties.enabled()) {
            return Route.DISABLED;
        }
        Route fallback = ollamaConfigured() ? Route.OLLAMA : Route.NONE;
        if (post.visibility() != Visibility.PUBLIC) {
            return fallback;
        }
        TagSuggester gemini = suggesters.get(Provider.GEMINI);
        if (gemini == null || !gemini.isConfigured()) {
            return fallback;
        }
        if (state.geminiBlocked(now)) {
            return fallback;
        }
        return Route.GEMINI;
    }

    /** 고른 길로 부른다. */
    public Routed suggest(
            Route route,
            CleanedInput input,
            List<String> popular,
            List<String> currentTags,
            Instant now) {
        return switch (route) {
            case GEMINI -> callGemini(input, popular, currentTags, now);
            case OLLAMA -> callOllama(input, popular, currentTags);
            case DISABLED, NONE ->
                    new Routed(null, false, new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        };
    }

    private Routed callGemini(
            CleanedInput input, List<String> popular, List<String> currentTags, Instant now) {
        TagSuggester gemini = suggesters.get(Provider.GEMINI);
        TagSuggestInput sent = inputFor(Provider.GEMINI, input, popular, currentTags);
        state.countGeminiCall(now);
        SuggestOutcome outcome = gemini.suggest(sent);
        switch (outcome) {
            case SuggestOutcome.Success s -> {
                state.geminiSucceeded(now);
                return new Routed(Provider.GEMINI, sent.truncated(), s);
            }
            case SuggestOutcome.QuotaExceeded q -> {
                switch (q.kind()) {
                    case PER_DAY -> state.exhaust(now);
                    case PER_MINUTE -> state.cooldown();
                    case UNKNOWN -> state.unknown429(now);
                }
                return callOllama(input, popular, currentTags);
            }
            case SuggestOutcome.Failed f -> {
                if (f.kind() != FailureKind.MALFORMED) {
                    state.cooldown();
                }
                return new Routed(Provider.GEMINI, sent.truncated(), f);
            }
            case SuggestOutcome.Busy b -> {
                return new Routed(Provider.GEMINI, sent.truncated(), b);
            }
        }
    }

    private Routed callOllama(CleanedInput input, List<String> popular, List<String> currentTags) {
        if (!ollamaConfigured()) {
            return new Routed(null, false, new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        }
        TagSuggestInput sent = inputFor(Provider.OLLAMA, input, popular, currentTags);
        if (!state.acquireOllama()) {
            return new Routed(Provider.OLLAMA, sent.truncated(), new SuggestOutcome.Busy());
        }
        try {
            return new Routed(
                    Provider.OLLAMA,
                    sent.truncated(),
                    suggesters.get(Provider.OLLAMA).suggest(sent));
        } finally {
            state.releaseOllama();
        }
    }

    private boolean ollamaConfigured() {
        TagSuggester ollama = suggesters.get(Provider.OLLAMA);
        return ollama != null && ollama.isConfigured();
    }

    private TagSuggestInput inputFor(
            Provider provider, CleanedInput input, List<String> popular, List<String> current) {
        int max = properties.maxInputChars(provider);
        CleanedInput cut = input.truncate(max);
        return new TagSuggestInput(cut.text(), input.longerThan(max), popular, current);
    }
}
