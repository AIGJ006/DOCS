package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.event.PostEdited;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 발행한 글을 고쳐 다시 발행 (002 T088, US4 #2·#5, SC-005, FR-032·033, A-15). 주소·발행일·최초 공개 시각·반응 수는 그대로,
 * "수정됨"({@code edited_at})만 붙는다.
 */
@Import(PostTestConfig.class)
class RepublishIT extends IntegrationTestBase {

    @Autowired CommittedEvents events;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    private Map<String, Object> row(long postId) {
        return jdbc.queryForMap(
                "SELECT status, visibility, published_at, first_public_at, edited_at, view_count,"
                        + " like_count, comment_count, edit_version, title, content_md,"
                        + " content_html, hidden_at, hidden_by, hidden_reason FROM post WHERE id ="
                        + " ?",
                postId);
    }

    private String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }

    private static Instant instant(Object ts) {
        return ts == null ? null : ((Timestamp) ts).toInstant();
    }

    private int draftCount(long postId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_draft WHERE post_id = ?", Integer.class, postId);
    }

    @Test
    void 작업본으로_고쳐_다시_발행하면_주소_발행일_반응_수는_그대로_수정됨만_붙는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().publishedWithReactions(me, 1234, 56, 7);
        Map<String, Object> before = row(postId);
        assertThat(status(api().save(session, postId, saveBody("고친 제목", "고친 본문", 1))))
                .isEqualTo(200);
        assertThat(draftCount(postId)).isEqualTo(1);

        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody("고친 제목", "고친 본문", List.of("spring"), "PUBLIC", 2));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.url"))
                .isEqualTo("/@" + handleOf(me) + "/posts/" + postId);
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(3);
        Map<String, Object> after = row(postId);
        assertThat(after.get("published_at")).isEqualTo(before.get("published_at"));
        assertThat(after.get("first_public_at")).isEqualTo(before.get("first_public_at"));
        assertThat(after.get("view_count")).isEqualTo(1234L);
        assertThat(after.get("like_count")).isEqualTo(56);
        assertThat(after.get("comment_count")).isEqualTo(7);
        assertThat(after.get("edited_at")).isNotNull();
        assertThat(Instant.parse(read(result, "$.editedAt")))
                .isEqualTo(instant(after.get("edited_at")));
        assertThat(after.get("title")).isEqualTo("고친 제목");
        assertThat((String) after.get("content_html")).contains("고친 본문");
        assertThat(after.get("status")).isEqualTo("PUBLISHED");
        assertThat(draftCount(postId)).isZero();
        assertThat(events.of(PostEdited.class)).hasSize(1);
        assertThat(events.of(PostEdited.class).get(0).postId()).isEqualTo(postId);
        assertThat(events.of(PostPublished.class)).isEmpty();
        assertThat(events.of(PostWentPublic.class)).isEmpty();
    }

    @Test
    void 같은_내용으로_다시_발행해도_수정_시각은_새로_기록된다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        Map<String, Object> original = row(postId);
        String title = (String) original.get("title");
        String md = (String) original.get("content_md");

        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody(title, md, List.of(), "PUBLIC", 1))))
                .isEqualTo(200);
        Instant firstEdit = instant(row(postId).get("edited_at"));
        Thread.sleep(5);
        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody(title, md, List.of(), "PUBLIC", 2))))
                .isEqualTo(200);

        Instant secondEdit = instant(row(postId).get("edited_at"));
        assertThat(firstEdit).isNotNull();
        assertThat(secondEdit).isAfter(firstEdit);
        assertThat(events.of(PostEdited.class)).hasSize(2);
    }

    @Test
    void 나만_보기였던_글을_다시_발행하며_처음_공개하면_PostEdited와_PostWentPublic() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PRIVATE").create();
        assertThat(row(postId).get("first_public_at")).isNull();

        MvcResult result =
                api().publish(session, postId, publishBody("이제 공개", "본문", List.of(), "PUBLIC", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> after = row(postId);
        assertThat(after.get("visibility")).isEqualTo("PUBLIC");
        assertThat(after.get("first_public_at")).isNotNull();
        assertThat(after.get("first_public_at")).isEqualTo(after.get("edited_at"));
        assertThat(events.of(PostEdited.class)).hasSize(1);
        assertThat(events.of(PostWentPublic.class)).hasSize(1);
        assertThat(events.of(PostPublished.class)).isEmpty();
    }

    @Test
    void 다시_발행하며_공개_범위를_나만_보기로_바꿀_수_있고_최초_공개_시각은_남는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        Object firstPublicAt = row(postId).get("first_public_at");

        MvcResult result =
                api().publish(session, postId, publishBody("숨김", "본문", List.of(), "PRIVATE", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(row(postId).get("visibility")).isEqualTo("PRIVATE");
        assertThat(row(postId).get("first_public_at")).isEqualTo(firstPublicAt);
        assertThat(events.of(PostWentPublic.class)).isEmpty();
    }

    @Test
    void 관리자가_숨긴_글을_다시_발행해도_숨김은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").hidden().create();
        Map<String, Object> before = row(postId);

        MvcResult result =
                api().publish(session, postId, publishBody("고침", "본문", List.of(), "PUBLIC", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> after = row(postId);
        assertThat(after.get("hidden_at")).isNotNull().isEqualTo(before.get("hidden_at"));
        assertThat(after.get("hidden_by")).isNotNull().isEqualTo(before.get("hidden_by"));
        assertThat(after.get("hidden_reason")).isEqualTo(before.get("hidden_reason"));
    }

    @Test
    void 어떤_저장_발행_변경_취소도_발행_글을_임시글로_되돌리지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();

        assertThat(status(api().autosave(session, postId, saveBody("자동", "자동", 1)))).isEqualTo(200);
        assertThat(row(postId).get("status")).isEqualTo("PUBLISHED");
        assertThat(status(api().save(session, postId, saveBody("수동", "수동", 2)))).isEqualTo(200);
        assertThat(row(postId).get("status")).isEqualTo("PUBLISHED");
        assertThat(status(api().discard(session, postId))).isEqualTo(204);
        assertThat(row(postId).get("status")).isEqualTo("PUBLISHED");
        long version = (Long) row(postId).get("edit_version");
        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody(
                                                        "다시", "다시", List.of(), "PUBLIC", version))))
                .isEqualTo(200);
        assertThat(row(postId).get("status")).isEqualTo("PUBLISHED");
        // 발행 → 임시글로 되돌리는 API는 없다 (P-1): 그런 경로는 404
        MvcResult unpublish =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                org.springframework.test.web.servlet.request
                                                        .MockMvcRequestBuilders.post(
                                                        "/api/posts/{postId}/unpublish", postId),
                                                null)
                                        .cookie(session))
                        .andReturn();
        assertThat(status(unpublish)).isIn(404, 405);
        assertThat(row(postId).get("status")).isEqualTo("PUBLISHED");
    }
}
