package com.team.blog.post.application;

import com.team.blog.account.application.MemberQueryService;
import com.team.blog.post.application.exception.VersionConflictException;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostEditRepository.EditState;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.infra.RedisAutosaveStore.SaveOutcome;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 글 쓰기 명령: 새 글(T048), 수동 저장(T079). */
@Service
public class PostCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);

    private final AccountStatusGuard accountStatusGuard;
    private final MemberQueryService members;
    private final PostRepository posts;
    private final PostAuthoringProperties properties;
    private final Clock clock;
    private final AutosaveService autosaveService;
    private final RedisAutosaveStore store;
    private final SavedContentImages images;
    private final TransactionTemplate tx;

    public PostCommandService(
            AccountStatusGuard accountStatusGuard,
            MemberQueryService members,
            PostRepository posts,
            PostAuthoringProperties properties,
            Clock clock,
            AutosaveService autosaveService,
            RedisAutosaveStore store,
            SavedContentImages images,
            TransactionTemplate tx) {
        this.accountStatusGuard = accountStatusGuard;
        this.members = members;
        this.posts = posts;
        this.properties = properties;
        this.clock = clock;
        this.autosaveService = autosaveService;
        this.store = store;
        this.images = images;
        this.tx = tx;
    }

    /**
     * [새 글] 임시글을 만든다 (FR-001, B-1). 공개 범위는 회원의 기본 공개 범위, 이벤트 없음.
     *
     * @param title 없으면 빈 제목 (입력 그대로 저장, 정리는 발행 때)
     * @param contentMd 없으면 빈 본문
     */
    @Transactional
    public WorkingCopy create(long memberId, String title, String contentMd) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        String t = title == null ? "" : title;
        String md = contentMd == null ? "" : contentMd;
        requireLengths(t, md, properties.post());
        Visibility visibility = Visibility.valueOf(members.defaultVisibility(memberId));
        Instant now = now(clock);
        Post post = posts.save(Post.newDraft(memberId, visibility, t, md, now));
        return new WorkingCopy(
                post.id(),
                post.status(),
                false,
                post.title(),
                post.contentMd(),
                post.editVersion(),
                post.updatedAt(),
                post.visibility(),
                List.of(),
                null);
    }

    /**
     * 수동 저장 (T079, FR-009, D-3, B-3 ②). 자동 저장과 같은 Lua로 버전을 먼저 확보한 뒤 같은 요청 안에서 그 버전으로 DB에 반영하고 작성자
     * 사진을 연결한다. DB 반영이 실패해도 Redis 보관분은 dirty로 남아 1분 반영이 맡으므로 저장은 성공으로 돌려준다. Redis 장애면 DB에 바로 저장한다.
     * 요청 제한은 없다.
     */
    public SaveResult save(
            long postId, long memberId, String title, String contentMd, long baseVersion) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        EditState state = autosaveService.requireOwned(postId, memberId);
        String t = title == null ? "" : title;
        String md = contentMd == null ? "" : contentMd;
        requireLengths(t, md, properties.post());

        Instant now = now(clock);
        SaveOutcome outcome =
                store.save(
                        postId,
                        memberId,
                        baseVersion,
                        state.dbVersion(),
                        t,
                        md,
                        now,
                        properties.autosave().redisTtl());
        return switch (outcome) {
            case SaveOutcome.Accepted accepted -> {
                applyNow(state, postId, memberId, t, md, accepted.version(), now);
                yield new SaveResult(accepted.version(), now);
            }
            case SaveOutcome.Rejected rejected ->
                    throw new VersionConflictException(
                            rejected.redisCopy().orElseGet(state::dbCopy));
            case SaveOutcome.NotOwner notOwner ->
                    throw new PostNotFoundException("수동 저장: 보관분의 회원이 다름");
            case SaveOutcome.Unavailable unavailable ->
                    autosaveService.saveToDatabase(postId, memberId, t, md, baseVersion, now);
        };
    }

    private void applyNow(
            EditState state,
            long postId,
            long memberId,
            String title,
            String contentMd,
            long version,
            Instant now) {
        List<String> keys = images.ownedKeys(contentMd, memberId);
        try {
            tx.executeWithoutResult(
                    status -> {
                        autosaveService.apply(
                                state.postStatus(), postId, title, contentMd, version, now);
                        images.attach(postId, memberId, keys);
                    });
        } catch (DataAccessException e) {
            log.warn(
                    "수동 저장의 DB 반영에 실패했습니다. 1분 반영이 다시 시도합니다: postId={} {}",
                    postId,
                    e.getClass().getSimpleName());
        }
    }

    /** 저장 길이 검사 (B-11): 제목 ≤ titleMax, 본문 ≤ contentMax (코드 포인트 = DB {@code char_length}). */
    static void requireLengths(
            String title, String contentMd, PostAuthoringProperties.Post limits) {
        List<FieldError> errors = new ArrayList<>();
        if (codePoints(title) > limits.titleMax()) {
            errors.add(PostReasonCode.TITLE_TOO_LONG.fieldError("title"));
        }
        if (codePoints(contentMd) > limits.contentMax()) {
            errors.add(PostReasonCode.CONTENT_TOO_LONG.fieldError("contentMd"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    static int codePoints(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /** DB(timestamptz, 마이크로초)와 같은 정밀도의 현재 시각. */
    static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
