package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 관리자 직접 숨김 (014 T029·T043, research R7). */
class DirectHideIT extends IntegrationTestBase {

    @Autowired ModerationEventRecorder events;

    private ReportApi api;
    private ReportFixtures reports;
    private PostFixtures posts;
    private long author;
    private long adminId;
    private Cookie admin;

    @BeforeEach
    void setUp() {
        api = new ReportApi(mockMvc);
        reports = new ReportFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        author = members().member().create();
        adminId = members().member().role("ADMIN").create();
        admin = TestLogin.loginAs(mockMvc, adminId);
        events.clear();
    }

    @Test
    void 신고_없는_글은_새_숨김_사건을_만든다() throws Exception {
        long postId =
                posts.post(author).title("바로 숨길 글").contentMd("본문").published("PUBLIC").create();

        MvcResult r = api.hide(admin, "POST", postId, "COPYRIGHT");
        assertThat(status(r)).isEqualTo(200);
        assertThat((Boolean) read(r, "$.hidden")).isTrue();
        long caseId = ((Number) read(r, "$.caseId")).longValue();

        Map<String, Object> c = jdbc.queryForMap("SELECT * FROM report_case WHERE id = ?", caseId);
        assertThat(c.get("status")).isEqualTo("HIDDEN");
        assertThat(c.get("handled_by")).isEqualTo(adminId);
        assertThat(c.get("snapshot_title")).isEqualTo("바로 숨길 글");
        assertThat(reports.reportCount()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_reason FROM post WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("COPYRIGHT");
        assertThat(events.of(ContentHidden.class)).hasSize(1);
        assertThat(events.of(ReportResolved.class)).isEmpty();
    }

    @Test
    void 대기_사건이_있으면_그_사건을_숨김으로_닫는다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = reports.pendingPost(postId, author);
        reports.report(caseId, members().member().create(), "SPAM", null);
        reports.report(caseId, members().member().create(), "ABUSE", null);

        MvcResult r = api.hide(admin, "POST", postId, "SPAM");
        assertThat(status(r)).isEqualTo(200);
        assertThat(((Number) read(r, "$.caseId")).longValue()).isEqualTo(caseId);
        assertThat(reports.status(caseId)).isEqualTo("HIDDEN");
        assertThat(reports.caseCount()).isEqualTo(1);
        assertThat(events.of(ReportResolved.class)).hasSize(2);
        assertThat(events.of(ContentHidden.class)).hasSize(1);
    }

    @Test
    void 볼_수_없는_글은_404_자기_글은_400_이미_숨김은_200_이벤트_없음() throws Exception {
        long priv = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        long trashed = posts.create(author, PostFixtures.State.TRASHED);
        assertThat(status(api.hide(admin, "POST", priv, "SPAM"))).isEqualTo(404);
        assertThat(status(api.hide(admin, "POST", trashed, "SPAM"))).isEqualTo(404);
        assertThat(status(api.hide(admin, "POST", posts.nonexistentId(), "SPAM"))).isEqualTo(404);

        long own = posts.create(adminId, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult ownResult = api.hide(admin, "POST", own, "SPAM");
        assertThat(status(ownResult)).isEqualTo(400);
        assertThat((String) read(ownResult, "$.code")).isEqualTo("CANNOT_MODERATE_OWN");

        long hidden = posts.create(author, PostFixtures.State.HIDDEN);
        MvcResult already = api.hide(admin, "POST", hidden, "SPAM");
        assertThat(status(already)).isEqualTo(200);
        assertThat((Boolean) read(already, "$.hidden")).isTrue();
        assertThat((Object) read(already, "$.caseId")).isNull();

        MvcResult noReason =
                api.hide(
                        admin,
                        "POST",
                        posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                        null);
        assertThat(status(noReason)).isEqualTo(400);
        assertThat(reports.caseCount()).isZero();
        assertThat(events.all()).isEmpty();
    }

    @Test
    void 댓글_직접_숨김() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long commenter = members().member().create();
        long id = comments.on(postId, commenter).content("숨길 댓글").create();
        long placeholder = comments.on(postId, commenter).deleted().create();
        long own = comments.on(postId, adminId).create();
        int before = comments.commentCount(postId);

        MvcResult r = api.hide(admin, "COMMENT", id, "ABUSE");
        assertThat(status(r)).isEqualTo(200);
        assertThat(comments.commentCount(postId)).isEqualTo(before - 1);
        long caseId = ((Number) read(r, "$.caseId")).longValue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT snapshot_content FROM report_case WHERE id = ?",
                                String.class,
                                caseId))
                .isEqualTo("숨길 댓글");
        assertThat(events.of(ContentHidden.class))
                .singleElement()
                .satisfies(e -> assertThat(e.targetType()).isEqualTo(ReportTargetType.COMMENT));

        assertThat(status(api.hide(admin, "COMMENT", placeholder, "ABUSE"))).isEqualTo(404);
        assertThat(status(api.hide(admin, "COMMENT", 9_000_000_000L, "ABUSE"))).isEqualTo(404);
        assertThat(status(api.hide(admin, "COMMENT", own, "ABUSE"))).isEqualTo(400);
        MvcResult again = api.hide(admin, "COMMENT", id, "ABUSE");
        assertThat(status(again)).isEqualTo(200);
        assertThat(events.of(ContentHidden.class)).hasSize(1);
    }
}
