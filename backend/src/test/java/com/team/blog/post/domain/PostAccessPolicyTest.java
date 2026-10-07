package com.team.blog.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 읽기 판정 한 곳 (research R-03, FR-033, 06 R-1). 순서: ① 휴지통이면 누구에게도 false ② 작성자 본인이면 true ③ PUBLISHED·숨김
 * 아님·작성자 탈퇴 유예 아님·공개 범위 규칙 참.
 */
class PostAccessPolicyTest {

    private static final long AUTHOR = 10L;
    private static final long OTHER = 20L;
    private static final long ADMIN = 30L;
    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    private final PostAccessPolicy policy =
            new PostAccessPolicy(
                    new VisibilityRegistry(
                            List.of(new PublicVisibilityRule(), new PrivateVisibilityRule())));

    enum Who {
        ANONYMOUS,
        OTHER_MEMBER,
        AUTHOR,
        ADMIN
    }

    enum State {
        PUBLISHED_PUBLIC,
        PUBLISHED_PRIVATE,
        DRAFT_PUBLIC,
        DRAFT_PRIVATE,
        TRASHED_PUBLIC,
        TRASHED_DRAFT,
        HIDDEN_PUBLIC,
        AUTHOR_WITHDRAWN
    }

    static Viewer viewer(Who who) {
        return switch (who) {
            case ANONYMOUS -> Viewer.anonymous();
            case OTHER_MEMBER -> new Viewer(OTHER, Role.USER, MemberStatus.ACTIVE, true);
            case AUTHOR -> new Viewer(AUTHOR, Role.USER, MemberStatus.ACTIVE, true);
            case ADMIN -> new Viewer(ADMIN, Role.ADMIN, MemberStatus.ACTIVE, true);
        };
    }

    static PostView post(State state) {
        return switch (state) {
            case PUBLISHED_PUBLIC ->
                    view(PostStatus.PUBLISHED, Visibility.PUBLIC, null, null, null);
            case PUBLISHED_PRIVATE ->
                    view(PostStatus.PUBLISHED, Visibility.PRIVATE, null, null, null);
            case DRAFT_PUBLIC -> view(PostStatus.DRAFT, Visibility.PUBLIC, null, null, null);
            case DRAFT_PRIVATE -> view(PostStatus.DRAFT, Visibility.PRIVATE, null, null, null);
            case TRASHED_PUBLIC -> view(PostStatus.PUBLISHED, Visibility.PUBLIC, T, null, null);
            case TRASHED_DRAFT -> view(PostStatus.DRAFT, Visibility.PRIVATE, T, null, null);
            case HIDDEN_PUBLIC -> view(PostStatus.PUBLISHED, Visibility.PUBLIC, null, T, null);
            case AUTHOR_WITHDRAWN -> view(PostStatus.PUBLISHED, Visibility.PUBLIC, null, null, T);
        };
    }

    static PostView view(
            PostStatus status,
            Visibility visibility,
            Instant deletedAt,
            Instant hiddenAt,
            Instant authorWithdrawnAt) {
        return new PostView(1L, AUTHOR, status, visibility, deletedAt, hiddenAt, authorWithdrawnAt);
    }

    /** FR-033 표 (42 §5-1). 작성자 탈퇴 유예 행의 작성자 칸은 001 필터가 403으로 막으므로 판정 자체는 true. */
    static Stream<Arguments> matrix() {
        return Stream.of(
                // 상태, 비회원, 다른 회원, 작성자, 관리자
                row(State.PUBLISHED_PUBLIC, true, true, true, true),
                row(State.PUBLISHED_PRIVATE, false, false, true, false),
                row(State.DRAFT_PUBLIC, false, false, true, false),
                row(State.DRAFT_PRIVATE, false, false, true, false),
                row(State.TRASHED_PUBLIC, false, false, false, false),
                row(State.TRASHED_DRAFT, false, false, false, false),
                row(State.HIDDEN_PUBLIC, false, false, true, false),
                row(State.AUTHOR_WITHDRAWN, false, false, true, false));
    }

    static Arguments row(State state, boolean anon, boolean other, boolean author, boolean admin) {
        return Arguments.of(state, anon, other, author, admin);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void 글_상태와_보는_사람별_판정(State state, boolean anon, boolean other, boolean author, boolean admin) {
        PostView p = post(state);
        assertThat(policy.canRead(p, viewer(Who.ANONYMOUS))).as("비회원").isEqualTo(anon);
        assertThat(policy.canRead(p, viewer(Who.OTHER_MEMBER))).as("다른 회원").isEqualTo(other);
        assertThat(policy.canRead(p, viewer(Who.AUTHOR))).as("작성자").isEqualTo(author);
        assertThat(policy.canRead(p, viewer(Who.ADMIN))).as("관리자").isEqualTo(admin);
    }

    @Test
    void 관리자도_자기_글이면_작성자다() {
        Viewer adminAuthor = new Viewer(AUTHOR, Role.ADMIN, MemberStatus.ACTIVE, true);
        assertThat(policy.canRead(post(State.PUBLISHED_PRIVATE), adminAuthor)).isTrue();
        assertThat(policy.canRead(post(State.DRAFT_PRIVATE), adminAuthor)).isTrue();
        assertThat(policy.canRead(post(State.TRASHED_PUBLIC), adminAuthor)).isFalse();
    }

    @Test
    void 인증_전_회원도_공개_글은_본다() {
        Viewer unverified = new Viewer(OTHER, Role.USER, MemberStatus.ACTIVE, false);
        assertThat(policy.canRead(post(State.PUBLISHED_PUBLIC), unverified)).isTrue();
        assertThat(policy.canRead(post(State.PUBLISHED_PRIVATE), unverified)).isFalse();
    }

    @Test
    void 숨김과_작성자_탈퇴는_공개_범위보다_먼저_거른다() {
        PostView hiddenPrivate = view(PostStatus.PUBLISHED, Visibility.PRIVATE, null, T, T);
        assertThat(policy.canRead(hiddenPrivate, viewer(Who.OTHER_MEMBER))).isFalse();
        assertThat(policy.canRead(hiddenPrivate, viewer(Who.AUTHOR))).isTrue();
    }
}
