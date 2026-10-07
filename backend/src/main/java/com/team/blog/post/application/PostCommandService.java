package com.team.blog.post.application;

import com.team.blog.account.application.MemberQueryService;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 쓰기 명령: 새 글(T048), 수동 저장(T079). */
@Service
public class PostCommandService {

    private final AccountStatusGuard accountStatusGuard;
    private final MemberQueryService members;
    private final PostRepository posts;
    private final PostAuthoringProperties properties;
    private final Clock clock;

    public PostCommandService(
            AccountStatusGuard accountStatusGuard,
            MemberQueryService members,
            PostRepository posts,
            PostAuthoringProperties properties,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.members = members;
        this.posts = posts;
        this.properties = properties;
        this.clock = clock;
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
