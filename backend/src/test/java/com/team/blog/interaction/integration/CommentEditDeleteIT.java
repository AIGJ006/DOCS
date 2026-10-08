package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentEventProbe;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.shared.event.CommentDeleted;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 내 댓글 고치고 지우기 (007 T036, US3, FR-026~033, SC-006). */
class CommentEditDeleteIT extends IntegrationTestBase {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired CommentEventProbe events;

    private long author;
    private long me;
    private long other;
    private long postId;
    private Cookie mine;
    private Cookie others;

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    private CommentFixtures comments() {
        return new CommentFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        events.arm();
        author = members().member().create();
        me = members().member().create();
        other = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        mine = TestLogin.loginAs(mockMvc, me);
        others = TestLogin.loginAs(mockMvc, other);
    }

    @AfterEach
    void tearDown() {
        events.reset();
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM comment WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void 고치면_내용과_수정됨() throws Exception {
        long id = comments().on(postId, me).content("처음").at(BASE).create();
        MvcResult result = api().edit(mine, id, " 고친 내용 ");

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.content")).isEqualTo("고친 내용");
        assertThat((Boolean) read(result, "$.edited")).isTrue();
        assertThat(row(id).get("content")).isEqualTo("고친 내용");
        assertThat(comments().commentCount(postId)).isEqualTo(1);
    }

    @Test
    void 같은_내용이면_아무것도_안_바뀐다() throws Exception {
        long id = comments().on(postId, me).content("그대로").at(BASE).create();
        Object before = row(id).get("updated_at");
        MvcResult result = api().edit(mine, id, "그대로\n\n\n");

        assertThat(status(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.edited")).isFalse();
        assertThat(row(id).get("updated_at")).isEqualTo(before);
    }

    @Test
    void 글_작성자와_관리자도_남의_댓글은_404() throws Exception {
        long id = comments().on(postId, me).content("남의 것").create();
        Map<String, Object> before = row(id);
        Cookie postAuthor = TestLogin.loginAs(mockMvc, author);
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());
        String notFound = CommentApi.body(api().edit(others, 9_999_999L, "x"));
        for (Cookie session : List.of(postAuthor, admin, others)) {
            MvcResult edit = api().edit(session, id, "바꿈");
            MvcResult delete = api().delete(session, id);
            assertThat(status(edit)).isEqualTo(404);
            assertThat(status(delete)).isEqualTo(404);
            assertThat(CommentApi.body(edit)).isEqualTo(notFound);
            assertThat(CommentApi.body(delete)).isEqualTo(notFound);
        }
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void 답글_있는_최상위는_자리로() throws Exception {
        long root = comments().on(postId, me).content("원문").create();
        comments().on(postId, other).parent(root).create();

        assertThat(status(api().delete(mine, root))).isEqualTo(204);
        assertThat(row(root).get("content")).isEqualTo("");
        assertThat(row(root).get("deleted_at")).isNotNull();
        assertThat(comments().commentCount(postId)).isEqualTo(1);
        MvcResult page = api().list(null, postId);
        assertThat((String) read(page, "$.items[0].state")).isEqualTo("DELETED");
    }

    @Test
    void 답글_없는_최상위와_답글은_행_삭제() throws Exception {
        long lonely = comments().on(postId, me).create();
        long root = comments().on(postId, other).create();
        long reply = comments().on(postId, me).parent(root).create();

        assertThat(status(api().delete(mine, lonely))).isEqualTo(204);
        assertThat(status(api().delete(mine, reply))).isEqualTo(204);
        assertThat(row(lonely)).isNull();
        assertThat(row(reply)).isNull();
        assertThat(row(root)).isNotNull();
        assertThat(comments().commentCount(postId)).isEqualTo(1);
    }

    @Test
    void 마지막_답글을_지우면_자리도_사라진다() throws Exception {
        long root = comments().on(postId, other).content("자리").deleted().create();
        long reply = comments().on(postId, me).parent(root).create();
        assertThat(comments().commentCount(postId)).isEqualTo(1);

        assertThat(status(api().delete(mine, reply))).isEqualTo(204);
        assertThat(row(root)).isNull();
        assertThat(comments().commentCount(postId)).isZero();
        List<CommentDeleted> deleted = events.of(CommentDeleted.class);
        assertThat(deleted).extracting(CommentDeleted::commentId).containsExactly(reply, root);
        assertThat(deleted.get(1).authorId()).isEqualTo(other);
    }

    @Test
    void 숨긴_댓글은_409_삭제는_가능() throws Exception {
        long id = comments().on(postId, me).content("숨김").hidden().create();
        assertThat(comments().commentCount(postId)).isZero();

        MvcResult edit = api().edit(mine, id, "고침");
        assertThat(status(edit)).isEqualTo(409);
        assertThat((String) read(edit, "$.code")).isEqualTo("COMMENT_HIDDEN");
        assertThat((List<Object>) read(edit, "$.errors")).isEmpty();
        assertThat(status(api().delete(mine, id))).isEqualTo(204);
        assertThat(row(id)).isNull();
        assertThat(comments().commentCount(postId)).isZero();
    }

    @Test
    void 지운_자리는_다시_지우면_404() throws Exception {
        long root = comments().on(postId, me).create();
        comments().on(postId, other).parent(root).create();
        assertThat(status(api().delete(mine, root))).isEqualTo(204);

        assertThat(status(api().delete(mine, root))).isEqualTo(404);
        assertThat(status(api().edit(mine, root, "되살리기"))).isEqualTo(404);
    }

    @Test
    void 인증_전_회원도_자기_댓글은_지운다() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        long id = comments().on(postId, unverified).create();
        Cookie session = TestLogin.loginAs(mockMvc, unverified);

        assertThat(status(api().edit(session, id, "고침"))).isEqualTo(403);
        assertThat(status(api().delete(session, id))).isEqualTo(204);
    }

    @Test
    void 비공개로_바뀐_글의_내_댓글도_지운다() throws Exception {
        long id = comments().on(postId, me).create();
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", postId);

        assertThat(status(api().edit(mine, id, "고침"))).isEqualTo(404);
        assertThat(status(api().delete(mine, id))).isEqualTo(204);
        assertThat(comments().commentCount(postId)).isZero();
    }

    @Test
    void 수정_1분_21번째는_429() throws Exception {
        long id = comments().on(postId, me).create();
        for (int i = 0; i < 20; i++) {
            assertThat(status(api().edit(mine, id, "고침 " + i))).isEqualTo(200);
        }
        MvcResult result = api().edit(mine, id, "스물한 번째");
        assertThat(status(result)).isEqualTo(429);
        assertThat((String) read(result, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void 삭제하면_CommentDeleted_자리여도() throws Exception {
        long root = comments().on(postId, me).create();
        comments().on(postId, other).parent(root).create();

        assertThat(status(api().delete(mine, root))).isEqualTo(204);
        List<CommentDeleted> deleted = events.of(CommentDeleted.class);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).commentId()).isEqualTo(root);
        assertThat(deleted.get(0).postAuthorId()).isEqualTo(author);
        assertThat(deleted.get(0).authorId()).isEqualTo(me);
    }
}
