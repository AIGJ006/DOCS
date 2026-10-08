package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ContentUnhidden;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 숨김 해제와 원래대로 돌아오기 (014 T046, US4, SC-006). */
class UnhideRestoresIT extends IntegrationTestBase {

    @Autowired ModerationEventRecorder events;

    private ReportApi api;
    private PostFixtures posts;
    private long author;
    private long adminId;
    private Cookie admin;

    @BeforeEach
    void setUp() {
        api = new ReportApi(mockMvc);
        posts = new PostFixtures(jdbc);
        author = members().member().create();
        adminId = members().member().role("ADMIN").create();
        admin = TestLogin.loginAs(mockMvc, adminId);
        events.clear();
    }

    private Map<String, Object> counts(long postId) {
        return jdbc.queryForMap(
                "SELECT like_count, comment_count, first_public_at, updated_at,"
                        + " (SELECT count(*) FROM post_like WHERE post_id = p.id) AS likes,"
                        + " (SELECT count(*) FROM comment WHERE post_id = p.id) AS comments"
                        + " FROM post p WHERE id = ?",
                postId);
    }

    @Test
    void 숨겼다_풀면_좋아요_댓글_수와_홈_위치가_그대로() throws Exception {
        long older = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long newer = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        for (int i = 0; i < 12; i++) {
            long m = members().member().create();
            jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, m);
        }
        jdbc.update("UPDATE post SET like_count = 12 WHERE id = ?", postId);
        CommentFixtures comments = new CommentFixtures(jdbc);
        for (int i = 0; i < 3; i++) {
            comments.on(postId, members().member().create()).create();
        }
        Map<String, Object> before = counts(postId);
        ReadingApi reading = new ReadingApi(mockMvc);
        List<Long> homeBefore = ReadingApi.ids(reading.home(null, null));

        long caseId = new ReportFixtures(jdbc).pendingPost(postId, author);
        new ReportFixtures(jdbc).report(caseId, members().member().create(), "SPAM", null);
        assertThat(status(api.resolve(admin, caseId, "HIDE", "SPAM"))).isEqualTo(200);
        assertThat(ReadingApi.ids(reading.home(null, null))).doesNotContain(postId);
        events.clear();

        MvcResult r = api.unhide(admin, "POST", postId);
        assertThat(status(r)).isEqualTo(200);
        assertThat((Boolean) read(r, "$.hidden")).isFalse();

        assertThat(counts(postId)).isEqualTo(before);
        assertThat(ReadingApi.ids(reading.home(null, null))).isEqualTo(homeBefore);
        assertThat(homeBefore).contains(older, newer);
        assertThat(new ReportFixtures(jdbc).status(caseId)).isEqualTo("HIDDEN");
        assertThat(events.of(ContentUnhidden.class))
                .singleElement()
                .satisfies(e -> assertThat(e.ownerId()).isEqualTo(author));
        assertThat(events.of(ReportResolved.class)).isEmpty();
        assertThat(events.of(ContentHidden.class)).isEmpty();
    }

    @Test
    void 작성자가_고치고_공개_범위를_바꾸고_휴지통에_넣었다_빼도_숨김은_그대로() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat(status(api.hide(admin, "POST", postId, "SPAM"))).isEqualTo(200);
        Cookie session = TestLogin.loginAs(mockMvc, author);

        mockMvc.perform(
                        TestLogin.withCsrf(
                                        org.springframework.test.web.servlet.request
                                                .MockMvcRequestBuilders.put(
                                                "/api/posts/{id}/visibility", postId),
                                        session)
                                .contentType("application/json")
                                .content("{\"visibility\":\"PRIVATE\"}"))
                .andReturn();
        mockMvc.perform(
                        TestLogin.withCsrf(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .delete("/api/posts/{id}", postId),
                                session))
                .andReturn();
        mockMvc.perform(
                        TestLogin.withCsrf(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/posts/{id}/restore", postId),
                                session))
                .andReturn();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_at IS NOT NULL AND hidden_reason = 'SPAM' FROM post"
                                        + " WHERE id = ?",
                                Boolean.class,
                                postId))
                .isTrue();
    }

    @Test
    void 숨김이_아닌_대상_해제는_200_자기_것_400_없는_대상_404_댓글_해제는_수_더하기() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult notHidden = api.unhide(admin, "POST", postId);
        assertThat(status(notHidden)).isEqualTo(200);
        assertThat((Boolean) read(notHidden, "$.hidden")).isFalse();

        long own = posts.post(adminId).published("PUBLIC").hidden().create();
        MvcResult ownResult = api.unhide(admin, "POST", own);
        assertThat(status(ownResult)).isEqualTo(400);
        assertThat((String) read(ownResult, "$.code")).isEqualTo("CANNOT_MODERATE_OWN");
        assertThat(status(api.unhide(admin, "POST", posts.nonexistentId()))).isEqualTo(404);
        assertThat(status(api.unhide(admin, "COMMENT", 9_000_000_000L))).isEqualTo(404);
        assertThat(events.all()).isEmpty();

        CommentFixtures comments = new CommentFixtures(jdbc);
        long commentId = comments.on(postId, members().member().create()).create();
        assertThat(status(api.hide(admin, "COMMENT", commentId, "ABUSE"))).isEqualTo(200);
        int hiddenCount = comments.commentCount(postId);
        assertThat(status(api.unhide(admin, "COMMENT", commentId))).isEqualTo(200);
        assertThat(comments.commentCount(postId)).isEqualTo(hiddenCount + 1);
        assertThat(events.of(ContentUnhidden.class)).hasSize(1);
    }
}
