package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.application.CaseResolutionService;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.shared.event.ReportResult;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 관리자 사건 목록·상세·처리 (014 T028·T043, US2 #2~#7, quickstart §2 {@code CaseResolutionIT}). */
class CaseResolutionIT extends IntegrationTestBase {

    @Autowired ModerationEventRecorder events;
    @Autowired CaseResolutionService resolution;

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
        author = members().member().handle("author01").nickname("글쓴이").create();
        adminId = members().member().role("ADMIN").nickname("운영자").create();
        admin = TestLogin.loginAs(mockMvc, adminId);
        events.clear();
    }

    private long pendingWith(long postId, int reporters, String reason) {
        long caseId = reports.pendingPost(postId, author);
        for (int i = 0; i < reporters; i++) {
            reports.report(caseId, members().member().create(), reason, null);
        }
        return caseId;
    }

    @Test
    void 대기_탭은_신고_수_많은_순_커서로_이어지고_고아_사건은_빠진다() throws Exception {
        long few =
                pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 1, "SPAM");
        long many =
                pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 3, "ABUSE");
        long orphanPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        pendingWith(orphanPost, 2, "SPAM");
        jdbc.update("DELETE FROM post WHERE id = ?", orphanPost);
        for (int i = 0; i < 20; i++) {
            pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 1, "SPAM");
        }

        MvcResult first;
        try (SqlCounter.Scope sql = SqlCounter.start()) {
            first = api.cases(admin, "PENDING", null);
            assertThat(sql.count()).as("목록 SQL 수").isLessThanOrEqualTo(5);
        }
        assertThat(status(first)).isEqualTo(200);
        List<Integer> ids = read(first, "$.items[*].caseId");
        assertThat(ids).hasSize(20);
        assertThat(ids.get(0)).isEqualTo((int) many);
        assertThat((Integer) read(first, "$.items[0].reportCount")).isEqualTo(3);
        assertThat((Integer) read(first, "$.items[0].reasonCounts.ABUSE")).isEqualTo(3);
        assertThat((String) read(first, "$.items[0].authorHandle")).isEqualTo("author01");
        assertThat((String) read(first, "$.items[0].status")).isEqualTo("PENDING");
        String cursor = read(first, "$.nextCursor");
        assertThat(cursor).isNotNull();

        MvcResult second = api.cases(admin, "PENDING", cursor);
        List<Integer> rest = read(second, "$.items[*].caseId");
        assertThat(rest).hasSize(2).doesNotContainAnyElementsOf(ids);
        assertThat((Object) read(second, "$.nextCursor")).isNull();
        List<Integer> all = new ArrayList<>(ids);
        all.addAll(rest);
        assertThat(all).contains((int) few).hasSize(22);

        assertThat(status(api.cases(admin, "nope", null))).isEqualTo(400);
        assertThat(status(api.cases(admin, "PENDING", "broken"))).isEqualTo(400);
    }

    @Test
    void 상세는_스냅샷과_현재_상태_작성자_정보를_싣고_원문은_싣지_않는다() throws Exception {
        long postId =
                posts.post(author)
                        .title("신고 당시 제목")
                        .contentMd("당시 본문")
                        .published("PUBLIC")
                        .create();
        long caseId = reports.pendingPost(postId, author);
        reports.report(caseId, members().member().create(), "OTHER", "광고 링크가 있어요");
        reports.report(caseId, adminId, "SPAM", null);
        jdbc.update(
                "UPDATE post SET title = '고친 제목', content_md = '고친 본문', visibility = 'PRIVATE'"
                        + " WHERE id = ?",
                postId);
        members().suspend(author, Instant.now().plus(Duration.ofDays(3)), "앞선 정지");

        MvcResult r = api.detail(admin, caseId);
        assertThat(status(r)).isEqualTo(200);
        assertThat((String) read(r, "$.snapshotTitle")).isEqualTo("신고 당시 제목");
        assertThat((String) read(r, "$.snapshotContent")).isEqualTo("당시 본문");
        assertThat(ReportApi.body(r)).doesNotContain("고친 본문");
        assertThat((String) read(r, "$.currentState")).isEqualTo("PRIVATE");
        assertThat((Integer) read(r, "$.reportCount")).isEqualTo(2);
        assertThat((String) read(r, "$.otherDetails[0].detail")).isEqualTo("광고 링크가 있어요");
        assertThat((Boolean) read(r, "$.reportedByMe")).isTrue();
        assertThat((Boolean) read(r, "$.onlyMyReport")).isFalse();
        assertThat((String) read(r, "$.author.handle")).isEqualTo("author01");
        assertThat((Boolean) read(r, "$.author.suspendedNow")).isTrue();
        assertThat((Integer) read(r, "$.author.suspensionCount")).isEqualTo(1);
        assertThat(ReportApi.body(r)).doesNotContain("reporterId");
        assertThat(status(api.detail(admin, 9_999_999L))).isEqualTo(404);
    }

    @Test
    void 숨기기는_신고마다_결과를_내고_대상을_숨긴다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = pendingWith(postId, 7, "SPAM");

        MvcResult r = api.resolve(admin, caseId, "HIDE", "SPAM");
        assertThat(status(r)).isEqualTo(200);
        assertThat((String) read(r, "$.status")).isEqualTo("HIDDEN");
        assertThat((String) read(r, "$.currentState")).isEqualTo("HIDDEN");
        assertThat(reports.status(caseId)).isEqualTo("HIDDEN");
        Map<String, Object> post =
                jdbc.queryForMap("SELECT hidden_by, hidden_reason FROM post WHERE id = ?", postId);
        assertThat(post.get("hidden_by")).isEqualTo(adminId);
        assertThat(post.get("hidden_reason")).isEqualTo("SPAM");

        assertThat(events.of(ReportResolved.class))
                .hasSize(7)
                .allMatch(e -> e.result() == ReportResult.ACTION_TAKEN && e.targetId() == postId);
        assertThat(events.of(ContentHidden.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.ownerId()).isEqualTo(author);
                            assertThat(e.targetType()).isEqualTo(ReportTargetType.POST);
                        });
        MvcResult again = api.resolve(admin, caseId, "REJECT", null);
        assertThat(status(again)).isEqualTo(409);
        assertThat((String) read(again, "$.code")).isEqualTo("REPORT_ALREADY_HANDLED");
        assertThat((String) read(again, "$.details.status")).isEqualTo("HIDDEN");
    }

    @Test
    void 반려는_대상을_그대로_두고_문제없음으로_알린다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = pendingWith(postId, 2, "ABUSE");

        assertThat(status(api.resolve(admin, caseId, "REJECT", null))).isEqualTo(200);
        assertThat(reports.status(caseId)).isEqualTo("REJECTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_at IS NULL FROM post WHERE id = ?",
                                Boolean.class,
                                postId))
                .isTrue();
        assertThat(events.of(ReportResolved.class))
                .hasSize(2)
                .allMatch(e -> e.result() == ReportResult.NO_VIOLATION);
        assertThat(events.of(ContentHidden.class)).isEmpty();
    }

    @Test
    void 숨기기_사유가_없으면_400() throws Exception {
        long caseId =
                pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 1, "SPAM");
        MvcResult r = api.resolve(admin, caseId, "HIDE", null);
        assertThat(status(r)).isEqualTo(400);
        assertThat((String) read(r, "$.errors[0].field")).isEqualTo("reason");
        assertThat(reports.status(caseId)).isEqualTo("PENDING");
    }

    @Test
    void 동시에_처리하면_하나만_200() throws Exception {
        long caseId =
                pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 3, "SPAM");
        long admin2 = members().member().role("ADMIN").create();
        Viewer a1 = new Viewer(adminId, Role.ADMIN, MemberStatus.ACTIVE, true);
        Viewer a2 = new Viewer(admin2, Role.ADMIN, MemberStatus.ACTIVE, true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (Viewer v : List.of(a1, a2)) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        resolution.resolve(v, caseId, "HIDE", "SPAM");
                                        return "ok";
                                    } catch (RuntimeException e) {
                                        return e.getClass().getSimpleName();
                                    }
                                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("ok", "BusinessRuleException");
        } finally {
            pool.shutdownNow();
        }
        assertThat(events.of(ReportResolved.class)).hasSize(3);
        assertThat(events.of(ContentHidden.class)).hasSize(1);
    }

    @Test
    void 자기_글_사건과_혼자_신고한_사건은_400_남의_신고가_섞이면_처리할_수_있다() throws Exception {
        long ownPost = posts.create(adminId, PostFixtures.State.PUBLISHED_PUBLIC);
        long ownCase = reports.pendingPost(ownPost, adminId);
        reports.report(ownCase, members().member().create(), "SPAM", null);
        MvcResult own = api.resolve(admin, ownCase, "REJECT", null);
        assertThat(status(own)).isEqualTo(400);
        assertThat((String) read(own, "$.code")).isEqualTo("CANNOT_MODERATE_OWN");

        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = reports.pendingPost(postId, author);
        reports.report(caseId, adminId, "SPAM", null);
        MvcResult detail = api.detail(admin, caseId);
        assertThat((Boolean) read(detail, "$.onlyMyReport")).isTrue();
        MvcResult alone = api.resolve(admin, caseId, "HIDE", "SPAM");
        assertThat(status(alone)).isEqualTo(400);
        assertThat((String) read(alone, "$.code")).isEqualTo("CANNOT_HANDLE_OWN_REPORT");

        reports.report(caseId, members().member().create(), "SPAM", null);
        assertThat(status(api.resolve(admin, caseId, "HIDE", "SPAM"))).isEqualTo(200);
    }

    @Test
    void 대상이_사라진_사건은_대상_없음으로_닫고_409() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = pendingWith(postId, 2, "SPAM");
        jdbc.update("DELETE FROM post WHERE id = ?", postId);

        MvcResult r = api.resolve(admin, caseId, "HIDE", "SPAM");
        assertThat(status(r)).isEqualTo(409);
        assertThat((String) read(r, "$.details.status")).isEqualTo("CLOSED_NO_TARGET");
        assertThat(reports.status(caseId)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(events.all()).isEmpty();
    }

    @Test
    void 처리됨_탭은_처리한_관리자_닉네임과_지금_숨김_여부를_싣는다() throws Exception {
        long hiddenPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long hiddenCase = pendingWith(hiddenPost, 1, "SPAM");
        long rejected =
                pendingWith(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), 1, "SPAM");
        api.resolve(admin, hiddenCase, "HIDE", "SPAM");
        api.resolve(admin, rejected, "REJECT", null);

        MvcResult r = api.cases(admin, "HANDLED", null);
        assertThat(status(r)).isEqualTo(200);
        List<Integer> ids = read(r, "$.items[*].caseId");
        assertThat(ids).containsExactly((int) rejected, (int) hiddenCase);
        assertThat((String) read(r, "$.items[1].handledByNickname")).isEqualTo("운영자");
        assertThat((Boolean) read(r, "$.items[1].targetHiddenNow")).isTrue();
        assertThat((Boolean) read(r, "$.items[0].targetHiddenNow")).isFalse();
        assertThat((String) read(r, "$.items[0].status")).isEqualTo("REJECTED");
    }

    @Test
    void 댓글_사건_숨기기와_반려() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long commenter = members().member().create();
        long c1 = comments.on(postId, commenter).content("나쁜 말이 담긴 댓글").create();
        long c2 = comments.on(postId, commenter).create();
        long case1 = reports.pendingComment(c1, commenter);
        reports.report(case1, members().member().create(), "ABUSE", null);
        long case2 = reports.pendingComment(c2, commenter);
        reports.report(case2, members().member().create(), "ABUSE", null);

        MvcResult detail = api.detail(admin, case1);
        assertThat((String) read(detail, "$.currentState")).isEqualTo("VISIBLE");
        assertThat((Object) read(detail, "$.snapshotTitle")).isNull();

        assertThat(status(api.resolve(admin, case1, "HIDE", "ABUSE"))).isEqualTo(200);
        assertThat(status(api.resolve(admin, case2, "REJECT", null))).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_reason FROM comment WHERE id = ?", String.class, c1))
                .isEqualTo("ABUSE");
        assertThat(events.of(ContentHidden.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.targetType()).isEqualTo(ReportTargetType.COMMENT);
                            assertThat(e.postId()).isEqualTo(postId);
                        });
        MvcResult handled = api.cases(admin, "HANDLED", null);
        assertThat((String) read(handled, "$.items[1].title")).isEqualTo("나쁜 말이 담긴 댓글");
        assertThat((Boolean) read(handled, "$.items[1].targetHiddenNow")).isTrue();
    }
}
