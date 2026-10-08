package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.body;
import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** 댓글 읽기 (007 T016, US1, SC-004·SC-008, FR-015). 댓글은 SQL로 넣는다. */
class CommentReadIT extends IntegrationTestBase {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private long author;
    private long reader;
    private long postId;

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    private CommentFixtures comments() {
        return new CommentFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        author = members().member().create();
        reader = members().member().create();
        postId = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    @Test
    void 최상위_오래된_순_20개씩_중복_누락_없음() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            // 18~22번째는 같은 작성 시각 — 페이지 경계(20)에 걸친다
            Instant at = i >= 17 && i <= 21 ? BASE.plusSeconds(17) : BASE.plusSeconds(i);
            expected.add(comments().on(postId, reader).at(at).create());
        }

        List<Integer> seen = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        String cursor = null;
        do {
            MvcResult page = api().listAfter(null, postId, cursor);
            assertThat(status(page)).isEqualTo(200);
            List<Integer> ids = read(page, "$.items[*].id");
            sizes.add(ids.size());
            seen.addAll(ids);
            cursor = read(page, "$.nextCursor");
        } while (cursor != null);

        assertThat(sizes).containsExactly(20, 20, 5);
        assertThat(seen).containsExactlyElementsOf(expected.stream().map(Long::intValue).toList());
    }

    @Test
    void 답글은_3개_뒤로_20개씩() throws Exception {
        long root = comments().on(postId, reader).at(BASE).create();
        List<Integer> replyIds = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            replyIds.add(
                    (int)
                            comments()
                                    .on(postId, author)
                                    .parent(root)
                                    .at(BASE.plusSeconds(i + 1))
                                    .create());
        }
        long small = comments().on(postId, reader).at(BASE.plusSeconds(100)).create();
        for (int i = 0; i < 8; i++) {
            comments().on(postId, author).parent(small).at(BASE.plusSeconds(101 + i)).create();
        }

        MvcResult page = api().list(null, postId);
        assertThat((Integer) read(page, "$.items[0].replyCount")).isEqualTo(25);
        assertThat((List<Integer>) read(page, "$.items[0].replies[*].id"))
                .containsExactlyElementsOf(replyIds.subList(0, 3));
        assertThat((Integer) read(page, "$.items[1].replyCount")).isEqualTo(8);
        assertThat((List<Object>) read(page, "$.items[1].replies")).hasSize(3);

        String cursor = read(page, "$.items[0].repliesNextCursor");
        MvcResult first = api().replies(null, root, cursor);
        assertThat(status(first)).isEqualTo(200);
        assertThat((List<Integer>) read(first, "$.items[*].id"))
                .containsExactlyElementsOf(replyIds.subList(3, 23));
        MvcResult second = api().replies(null, root, read(first, "$.nextCursor"));
        assertThat((List<Integer>) read(second, "$.items[*].id"))
                .containsExactlyElementsOf(replyIds.subList(23, 25));
        assertThat((String) read(second, "$.nextCursor")).isNull();

        MvcResult smallReplies =
                api().replies(null, small, read(page, "$.items[1].repliesNextCursor"));
        assertThat((List<Object>) read(smallReplies, "$.items")).hasSize(5);
    }

    @Test
    void 답글의_답글은_대상_닉네임() throws Exception {
        long target = members().member().nickname("대상회원").handle("target1").create();
        long root = comments().on(postId, reader).at(BASE).create();
        comments().on(postId, target).parent(root).at(BASE.plusSeconds(1)).create();
        comments().on(postId, reader).parent(root).replyTo(target).at(BASE.plusSeconds(2)).create();

        MvcResult page = api().list(null, postId);
        assertThat((Object) read(page, "$.items[0].replies[0].replyTo")).isNull();
        assertThat((String) read(page, "$.items[0].replies[1].replyTo.nickname")).isEqualTo("대상회원");
        assertThat((String) read(page, "$.items[0].replies[1].replyTo.handle"))
                .isEqualTo("target1");
    }

    @Test
    void 대상이_탈퇴하면_탈퇴한_사용자에게() throws Exception {
        long target = members().member().nickname("떠날회원").create();
        long root = comments().on(postId, reader).at(BASE).create();
        comments().on(postId, reader).parent(root).replyTo(target).at(BASE.plusSeconds(2)).create();
        posts().withdraw(target);

        MvcResult page = api().list(null, postId);
        assertThat((Boolean) read(page, "$.items[0].replies[0].replyTo.withdrawn")).isTrue();
        assertThat(body(page)).doesNotContain("떠날회원");
    }

    @Test
    void 지운_최상위는_자리로_답글은_그대로() throws Exception {
        long root = comments().on(postId, reader).content("지운 원문").deleted().at(BASE).create();
        comments()
                .on(postId, author)
                .parent(root)
                .content("남은 답글")
                .at(BASE.plusSeconds(1))
                .create();

        MvcResult page = api().list(null, postId);
        assertThat((String) read(page, "$.items[0].state")).isEqualTo("DELETED");
        assertThat((Object) read(page, "$.items[0].author")).isNull();
        assertThat((Object) read(page, "$.items[0].content")).isNull();
        assertThat((String) read(page, "$.items[0].replies[0].content")).isEqualTo("남은 답글");
    }

    @Test
    void 임시_비공개_휴지통_없는_글은_같은_404() throws Exception {
        Cookie other = TestLogin.loginAs(mockMvc, reader);
        String expected = body(api().list(other, posts().nonexistentId()));
        for (PostFixtures.State state :
                List.of(
                        PostFixtures.State.DRAFT,
                        PostFixtures.State.PUBLISHED_PRIVATE,
                        PostFixtures.State.TRASHED,
                        PostFixtures.State.AUTHOR_WITHDRAWN)) {
            long writer = members().member().create();
            long id = posts().create(writer, state);
            comments().on(id, reader).create();
            MvcResult result = api().list(other, id);
            assertThat(status(result)).as(state.name()).isEqualTo(404);
            assertThat(body(result)).as(state.name()).isEqualTo(expected);
        }
        // 작성자 본인의 임시글도 404
        long draft = posts().create(author, PostFixtures.State.DRAFT);
        MvcResult own = api().list(TestLogin.loginAs(mockMvc, author), draft);
        assertThat(status(own)).isEqualTo(404);
        assertThat(body(own)).isEqualTo(expected);
        assertThat(status(api().list(null, "abc"))).isEqualTo(404);
    }

    @Test
    void 댓글이_없으면_빈_목록() throws Exception {
        MvcResult page = api().list(null, postId);
        assertThat(status(page)).isEqualTo(200);
        assertThat((List<Object>) read(page, "$.items")).isEmpty();
        assertThat((Object) read(page, "$.nextCursor")).isNull();
        assertThat((Object) read(page, "$.prevCursor")).isNull();
        assertThat((Object) read(page, "$.focusCommentId")).isNull();
    }

    @Test
    void 글_작성자_댓글은_isPostAuthor() throws Exception {
        comments().on(postId, author).at(BASE).create();
        comments().on(postId, reader).at(BASE.plusSeconds(1)).create();

        MvcResult page = api().list(null, postId);
        assertThat((Boolean) read(page, "$.items[0].author.isPostAuthor")).isTrue();
        assertThat((Boolean) read(page, "$.items[1].author.isPostAuthor")).isFalse();
        assertThat((Boolean) read(page, "$.items[0].mine")).isFalse();
    }

    @Test
    void 공개가_아닌_글의_댓글은_no_store() throws Exception {
        long privatePost = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult own = api().list(TestLogin.loginAs(mockMvc, author), privatePost);
        assertThat(status(own)).isEqualTo(200);
        assertThat(own.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store");

        MvcResult open = api().list(null, postId);
        assertThat(open.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-cache");
    }

    @Test
    void 페이지_SQL은_4번() throws Exception {
        long few = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long many = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        for (int i = 0; i < 20; i++) {
            long r1 = comments().on(few, reader).at(BASE.plusSeconds(i)).create();
            comments().on(few, author).parent(r1).at(BASE.plusSeconds(100 + i)).create();
            long r2 = comments().on(many, reader).at(BASE.plusSeconds(i)).create();
            for (int j = 0; j < 100; j++) {
                long writer = j % 2 == 0 ? author : reader;
                comments()
                        .on(many, writer)
                        .parent(r2)
                        .at(BASE.plusSeconds(1000 + i * 100 + j))
                        .create();
            }
        }
        // 글 판정(004) 1번 + 댓글 4번(최상위·답글 미리보기·회원 표시·프로필 사진)
        assertThat(sqlCount(few)).isEqualTo(5);
        assertThat(sqlCount(many)).isEqualTo(5);
    }

    private int sqlCount(long id) throws Exception {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(status(api().list(null, id))).isEqualTo(200);
            return scope.count();
        }
    }
}
