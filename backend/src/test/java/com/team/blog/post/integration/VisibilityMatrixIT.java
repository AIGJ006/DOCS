package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.ReferenceListQueries;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공개 범위 매트릭스 (004 T040, US2 인수 1·3·4·5, FR-003~FR-011·FR-047, SC-001·SC-004, 06 §8).
 *
 * <p>글 상태 7가지({@link State}) × 보는 사람(비회원/다른 회원/작성자/관리자) × 노출되는 곳(상세 {@code requireReadable}, 홈 대표
 * 쿼리, 블로그 대표 쿼리, 블로그 글 수, 공용 조건만 건 목록 — 태그·검색·sitemap·피드가 같은 조건)을 모두 확인한다. 댓글 보기는 007이 같은 {@code
 * requireReadable}을 부르므로 상세 칸과 같다.
 *
 * <p>목록·글 수는 보는 사람과 무관하다 — 작성자 본인 블로그에도 비공개·임시·휴지통·숨김 글은 나오지 않는다(06 V-8, H1). 상세는 작성자만 비공개·임시·숨김 글을
 * 볼 수 있고 휴지통은 작성자도 못 본다. 작성자 탈퇴 유예 글을 작성자가 HTTP로 열면 001 탈퇴 게이트가 먼저 403을 준다({@code post-read.csv});
 * 여기서는 판정 장치만 본다.
 */
class VisibilityMatrixIT extends IntegrationTestBase {

    private enum Who {
        ANONYMOUS,
        MEMBER,
        AUTHOR,
        ADMIN
    }

    /** 상세를 볼 수 있는 사람 (FR-033). */
    private static final Map<State, Set<Who>> DETAIL =
            Map.of(
                    State.PUBLISHED_PUBLIC, EnumSet.allOf(Who.class),
                    State.EDITING, EnumSet.allOf(Who.class),
                    State.PUBLISHED_PRIVATE, EnumSet.of(Who.AUTHOR),
                    State.DRAFT, EnumSet.of(Who.AUTHOR),
                    State.TRASHED, EnumSet.noneOf(Who.class),
                    State.HIDDEN, EnumSet.of(Who.AUTHOR),
                    State.AUTHOR_WITHDRAWN, EnumSet.of(Who.AUTHOR));

    /** 목록·글 수에 나오는 상태 (보는 사람과 무관). */
    private static final Set<State> LISTED = EnumSet.of(State.PUBLISHED_PUBLIC, State.EDITING);

    @Autowired private PostReadService postReadService;
    @Autowired private VisibilityFilter visibilityFilter;
    @Autowired private PostQueryRepository postQueryRepository;

    private PostFixtures posts;
    private ReferenceListQueries lists;
    private long bystanderPost;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
        lists = new ReferenceListQueries(jdbc, visibilityFilter);
        // 목록이 비어서 통과하는 일이 없게 다른 작성자의 공개 글 하나
        bystanderPost = posts.create(members().member().create(), State.PUBLISHED_PUBLIC);
    }

    private Map<Who, Viewer> viewers(long author) {
        long member = members().member().create();
        long admin = members().member().role("ADMIN").create();
        String status =
                jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, author);
        Map<Who, Viewer> viewers = new LinkedHashMap<>();
        viewers.put(Who.ANONYMOUS, Viewer.anonymous());
        viewers.put(Who.MEMBER, new Viewer(member, Role.USER, MemberStatus.ACTIVE, true));
        viewers.put(Who.AUTHOR, new Viewer(author, Role.USER, MemberStatus.valueOf(status), true));
        viewers.put(Who.ADMIN, new Viewer(admin, Role.ADMIN, MemberStatus.ACTIVE, true));
        return viewers;
    }

    private boolean readable(long postId, Viewer viewer) {
        try {
            postReadService.requireReadable(postId, viewer);
            return true;
        } catch (PostNotFoundException e) {
            return false;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(State.class)
    void 상태마다_상세_홈_블로그_글수_공용조건_모든_칸(State state) {
        long author = members().member().create();
        long postId = posts.create(author, state);
        boolean listed = LISTED.contains(state);

        viewers(author)
                .forEach(
                        (who, viewer) -> {
                            String cell = state + " × " + who;
                            assertThat(readable(postId, viewer))
                                    .as("상세·댓글 보기 " + cell)
                                    .isEqualTo(DETAIL.get(state).contains(who));
                            assertThat(lists.home(viewer).contains(postId))
                                    .as("홈 " + cell)
                                    .isEqualTo(listed);
                            assertThat(lists.blog(viewer, author).contains(postId))
                                    .as("블로그 목록 " + cell)
                                    .isEqualTo(listed);
                            assertThat(lists.blogCount(viewer, author))
                                    .as("블로그 글 수 " + cell)
                                    .isEqualTo(listed ? 1 : 0);
                            assertThat(postQueryRepository.countListedByAuthor(viewer, author))
                                    .as("공용 글 수 메서드 " + cell)
                                    .isEqualTo(listed ? 1 : 0);
                            assertThat(lists.listedEverywhere(viewer).contains(postId))
                                    .as("공용 조건(태그·검색·sitemap) " + cell)
                                    .isEqualTo(listed);
                            assertThat(lists.home(viewer)).as("홈 " + cell).contains(bystanderPost);
                        });
    }

    @Test
    void US2_1_관리자도_남의_비공개_글은_404() {
        long author = members().member().create();
        long postId = posts.create(author, State.PUBLISHED_PRIVATE);
        Viewer admin = viewers(author).get(Who.ADMIN);

        assertThatThrownBy(() -> postReadService.requireReadable(postId, admin))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void US2_3_볼_수_없는_글은_어디에도_없고_세지_않는다_작성자_본인_블로그도() {
        long author = members().member().create();
        List<Long> shown =
                List.of(
                        posts.create(author, State.PUBLISHED_PUBLIC),
                        posts.create(author, State.PUBLISHED_PUBLIC),
                        posts.create(author, State.EDITING));
        List<Long> unseen =
                List.of(
                        posts.create(author, State.PUBLISHED_PRIVATE),
                        posts.create(author, State.DRAFT),
                        posts.create(author, State.TRASHED),
                        posts.create(author, State.HIDDEN));

        viewers(author)
                .forEach(
                        (who, viewer) -> {
                            assertThat(lists.blog(viewer, author))
                                    .as("블로그 목록 " + who)
                                    .containsExactlyInAnyOrderElementsOf(shown);
                            assertThat(lists.blogCount(viewer, author))
                                    .as("블로그 글 수 " + who)
                                    .isEqualTo(3);
                            assertThat(postQueryRepository.countListedByAuthor(viewer, author))
                                    .as("공용 글 수 " + who)
                                    .isEqualTo(3);
                            assertThat(lists.home(viewer))
                                    .as("홈 " + who)
                                    .doesNotContainAnyElementsOf(unseen);
                            assertThat(lists.listedEverywhere(viewer))
                                    .as("공용 조건 " + who)
                                    .containsAll(shown)
                                    .doesNotContainAnyElementsOf(unseen);
                        });
    }

    @Test
    void US2_4_숨긴_글은_작성자_블로그에도_없고_상세는_작성자만() {
        long author = members().member().create();
        long hidden = posts.create(author, State.HIDDEN);
        Map<Who, Viewer> viewers = viewers(author);

        assertThat(lists.blog(viewers.get(Who.AUTHOR), author)).doesNotContain(hidden);
        assertThat(readable(hidden, viewers.get(Who.AUTHOR))).isTrue();
        assertThat(readable(hidden, viewers.get(Who.ADMIN))).isFalse();
        assertThat(readable(hidden, viewers.get(Who.MEMBER))).isFalse();
    }

    @Test
    void US2_5_작성자가_탈퇴_유예가_되면_상세_404_목록_제외() {
        long author = members().member().create();
        long postId = posts.create(author, State.PUBLISHED_PUBLIC);
        assertThat(lists.home(Viewer.anonymous())).contains(postId);

        posts.withdraw(author);

        Map<Who, Viewer> viewers = viewers(author);
        assertThat(readable(postId, viewers.get(Who.ANONYMOUS))).isFalse();
        assertThat(readable(postId, viewers.get(Who.MEMBER))).isFalse();
        assertThat(readable(postId, viewers.get(Who.ADMIN))).isFalse();
        assertThat(lists.home(Viewer.anonymous())).doesNotContain(postId);
        assertThat(lists.blogCount(Viewer.anonymous(), author)).isZero();
    }
}
