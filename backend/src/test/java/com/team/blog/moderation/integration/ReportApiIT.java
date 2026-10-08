package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.body;
import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.application.ReportService;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 신고 접수 API (014 T016, US1 #1~#7, quickstart §2 {@code ReportApiIT}). */
class ReportApiIT extends IntegrationTestBase {

    @Autowired ReportService reportService;
    @Autowired ModerationEventRecorder events;

    private ReportApi api;
    private ReportFixtures reports;
    private PostFixtures posts;
    private long author;
    private long reader;
    private Cookie readerSession;

    @BeforeEach
    void setUp() {
        api = new ReportApi(mockMvc);
        reports = new ReportFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        author = members().member().create();
        reader = members().member().create();
        readerSession = TestLogin.loginAs(mockMvc, reader);
        events.clear();
    }

    @Test
    void 공개_글_신고는_사건과_스냅샷을_만들고_같은_회원이_다시_해도_한_건() throws Exception {
        long postId =
                posts.post(author).title("신고될 글").contentMd("문제 본문").published("PUBLIC").create();

        MvcResult first = api.reportPost(readerSession, postId, "SPAM");
        assertThat(status(first)).isEqualTo(200);
        assertThat(body(first)).isEqualTo("{\"accepted\":true}");
        assertThat(first.getResponse().getHeader("Cache-Control")).contains("no-store");
        MvcResult again = api.report(readerSession, "POST", postId, "ABUSE", null);
        assertThat(status(again)).isEqualTo(200);

        assertThat(reports.caseCount()).isEqualTo(1);
        assertThat(reports.reportCount()).isEqualTo(1);
        Map<String, Object> c = jdbc.queryForMap("SELECT * FROM report_case");
        assertThat(c.get("target_type")).isEqualTo("POST");
        assertThat(c.get("post_id")).isEqualTo(postId);
        assertThat(c.get("target_author_id")).isEqualTo(author);
        assertThat(c.get("snapshot_title")).isEqualTo("신고될 글");
        assertThat(c.get("snapshot_content")).isEqualTo("문제 본문");
        assertThat(c.get("status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT reason FROM report", String.class))
                .isEqualTo("SPAM");
        assertThat(events.all()).as("접수는 이벤트를 내지 않는다").isEmpty();
    }

    @Test
    void 댓글_신고는_댓글_내용을_스냅샷으로_남긴다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long commentId = new CommentFixtures(jdbc).on(postId, author).content("나쁜 댓글").create();

        assertThat(status(api.report(readerSession, "COMMENT", commentId, "ABUSE", null)))
                .isEqualTo(200);

        Map<String, Object> c = jdbc.queryForMap("SELECT * FROM report_case");
        assertThat(c.get("comment_id")).isEqualTo(commentId);
        assertThat(c.get("post_id")).isNull();
        assertThat(c.get("snapshot_title")).isNull();
        assertThat(c.get("snapshot_content")).isEqualTo("나쁜 댓글");
    }

    @Test
    void 기타는_설명이_있어야_하고_다른_사유의_설명은_저장하지_않는다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long other = members().member().create();

        MvcResult empty = api.report(readerSession, "POST", postId, "OTHER", "   ");
        assertThat(status(empty)).isEqualTo(400);
        assertThat((String) read(empty, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(empty, "$.errors[0].field")).isEqualTo("detail");
        assertThat((String) read(empty, "$.errors[0].code")).isEqualTo("REPORT_DETAIL_REQUIRED");
        assertThat(reports.reportCount()).isZero();

        MvcResult tooLong = api.report(readerSession, "POST", postId, "OTHER", "가".repeat(201));
        assertThat(status(tooLong)).isEqualTo(400);
        assertThat((String) read(tooLong, "$.errors[0].code")).isEqualTo("TOO_LONG");

        assertThat(status(api.report(readerSession, "POST", postId, "OTHER", " 설명 ")))
                .isEqualTo(200);
        assertThat(
                        status(
                                api.report(
                                        TestLogin.loginAs(mockMvc, other),
                                        "POST",
                                        postId,
                                        "SPAM",
                                        "버려질 설명")))
                .isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT detail FROM report ORDER BY id", String.class))
                .containsExactly("설명", null);
    }

    @Test
    void 형식_오류는_칸마다_모아서_400() throws Exception {
        MvcResult bad =
                api.raw(
                        readerSession,
                        "{\"targetType\":\"USER\",\"targetId\":-1,\"reason\":\"X\"}");
        assertThat(status(bad)).isEqualTo(400);
        List<String> fields = read(bad, "$.errors[*].field");
        assertThat(fields).containsExactlyInAnyOrder("targetType", "targetId", "reason");
        MvcResult missing = api.raw(readerSession, "{}");
        assertThat(status(missing)).isEqualTo(400);
        List<String> codes = read(missing, "$.errors[*].code");
        assertThat(codes).containsOnly("REQUIRED");
    }

    @Test
    void 자기_것은_400_비회원_401_인증_전_403() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long unverified = members().member().emailVerified(false).create();

        MvcResult own = api.reportPost(TestLogin.loginAs(mockMvc, author), postId, "SPAM");
        assertThat(status(own)).isEqualTo(400);
        assertThat((String) read(own, "$.code")).isEqualTo("CANNOT_REPORT_OWN");
        assertThat((String) read(own, "$.message")).doesNotEndWith(".");

        MvcResult anon = api.reportPost(null, postId, "SPAM");
        assertThat(status(anon)).isEqualTo(401);
        assertThat((String) read(anon, "$.code")).isEqualTo("LOGIN_REQUIRED");

        MvcResult pre = api.reportPost(TestLogin.loginAs(mockMvc, unverified), postId, "SPAM");
        assertThat(status(pre)).isEqualTo(403);
        assertThat((String) read(pre, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(reports.caseCount()).isZero();
    }

    @Test
    void 볼_수_없는_대상은_고정_본문_404() throws Exception {
        long priv = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        long trashed = posts.create(author, PostFixtures.State.TRASHED);
        long hidden = posts.create(author, PostFixtures.State.HIDDEN);
        long missing = posts.nonexistentId();
        long publicPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long placeholder = comments.on(publicPost, author).deleted().create();
        long hiddenComment = comments.on(publicPost, author).hidden().create();
        long onPrivate = comments.on(priv, author).create();

        String expected =
                "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";
        for (long id : new long[] {priv, trashed, hidden, missing}) {
            MvcResult r = api.reportPost(readerSession, id, "SPAM");
            assertThat(status(r)).as("글 " + id).isEqualTo(404);
            assertThat(body(r)).isEqualTo(expected);
        }
        for (long id : new long[] {placeholder, hiddenComment, onPrivate, 9_000_000_000L}) {
            MvcResult r = api.report(readerSession, "COMMENT", id, "SPAM", null);
            assertThat(status(r)).as("댓글 " + id).isEqualTo(404);
            assertThat(body(r)).isEqualTo(expected);
        }
        assertThat(reports.caseCount()).isZero();
    }

    @Test
    void 판정_순서() throws Exception {
        long unverifiedAuthor = members().member().emailVerified(false).create();
        long own = posts.create(unverifiedAuthor, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult r1 = api.reportPost(TestLogin.loginAs(mockMvc, unverifiedAuthor), own, "SPAM");
        assertThat(status(r1)).as("인증 전 회원의 자기 글 → 403").isEqualTo(403);

        long priv = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult r2 =
                api.raw(readerSession, "{\"targetType\":\"POST\",\"targetId\":" + priv + "}");
        assertThat(status(r2)).as("형식 오류인 남의 비공개 글 → 400").isEqualTo(400);

        long hidden = posts.create(author, PostFixtures.State.HIDDEN);
        MvcResult r3 = api.report(readerSession, "POST", hidden, "OTHER", " ");
        assertThat(status(r3)).as("숨김 글 + 기타 빈 설명 → 404").isEqualTo(404);
    }

    @Test
    void 분당_6번째와_하루_51번째는_429와_Retry_After() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        for (int i = 0; i < 5; i++) {
            assertThat(status(api.reportPost(readerSession, postId, "SPAM"))).isEqualTo(200);
        }
        MvcResult sixth = api.reportPost(readerSession, postId, "SPAM");
        assertThat(status(sixth)).isEqualTo(429);
        assertThat((String) read(sixth, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(sixth.getResponse().getHeader("Retry-After")).isNotBlank();

        long other = members().member().create();
        redis.opsForValue()
                .set(ReportService.RATE_LIMIT_PREFIX + other + ":1d", "50", Duration.ofHours(1));
        MvcResult daily = api.reportPost(TestLogin.loginAs(mockMvc, other), postId, "SPAM");
        assertThat(status(daily)).isEqualTo(429);
        assertThat(daily.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void Redis가_멈춰도_신고는_받는다() {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Viewer viewer = new Viewer(reader, Role.USER, MemberStatus.ACTIVE, true);
        try (RedisOutage outage = RedisOutage.start()) {
            reportService.report(viewer, "POST", postId, "SPAM", null);
        }
        assertThat(reports.reportCount()).isEqualTo(1);
    }

    @Test
    void 처리된_뒤의_신고는_새_사건이고_신고가_쌓여도_대상은_숨겨지지_않는다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long admin = members().member().role("ADMIN").create();
        long handled = reports.handledPost(postId, author, "REJECTED", admin, Instant.now());
        reports.report(handled, reader, "SPAM", null);

        assertThat(status(api.reportPost(readerSession, postId, "SPAM"))).isEqualTo(200);
        for (int i = 0; i < 9; i++) {
            long m = members().member().create();
            assertThat(status(api.reportPost(TestLogin.loginAs(mockMvc, m), postId, "SPAM")))
                    .isEqualTo(200);
        }

        assertThat(reports.caseCount()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM report r JOIN report_case c ON c.id ="
                                        + " r.case_id WHERE c.status = 'PENDING'",
                                Integer.class))
                .isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_at IS NULL FROM post WHERE id = ?",
                                Boolean.class,
                                postId))
                .as("FR-017 자동 숨김 없음")
                .isTrue();
        assertThat(events.all()).isEmpty();
    }
}
