package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.moderation.application.CaseResolutionService;
import com.team.blog.moderation.application.ReportService;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 신고 동시성 (014 T017, research R4). 서비스를 직접 부른다 — HTTP 세션 비용 없이 경쟁만 본다. */
class ReportConcurrencyIT extends IntegrationTestBase {

    @Autowired ReportService reportService;
    @Autowired CaseResolutionService resolution;
    @Autowired ModerationEventRecorder events;

    private static Viewer member(long id) {
        return new Viewer(id, Role.USER, MemberStatus.ACTIVE, true);
    }

    private static List<Object> runAll(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 20));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        return task.call();
                                    } catch (Exception e) {
                                        return e;
                                    }
                                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 스무_명이_동시에_처음_신고하면_사건_하나에_신고_스무_건() throws Exception {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long m = members().member().create();
            tasks.add(
                    () -> {
                        reportService.report(member(m), "POST", postId, "SPAM", null);
                        return "ok";
                    });
        }

        assertThat(runAll(tasks)).containsOnly("ok");
        ReportFixtures f = new ReportFixtures(jdbc);
        assertThat(f.caseCount()).isEqualTo(1);
        assertThat(f.reportCount()).isEqualTo(20);
    }

    @Test
    void 같은_회원이_동시에_두_번_신고하면_한_건() throws Exception {
        long author = members().member().create();
        long reader = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(
                    () -> {
                        reportService.report(member(reader), "POST", postId, "SPAM", null);
                        return "ok";
                    });
        }

        assertThat(runAll(tasks)).containsOnly("ok");
        assertThat(new ReportFixtures(jdbc).reportCount()).isEqualTo(1);
    }

    @Test
    void 처리와_신고가_겹쳐도_신고는_닫힌_사건에_새로_붙지_않는다() throws Exception {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        Viewer adminViewer = new Viewer(admin, Role.ADMIN, MemberStatus.ACTIVE, true);
        PostFixtures posts = new PostFixtures(jdbc);
        // 회원마다 1분 5건 제한이 있어 회차마다 새 신고자를 쓴다 (10회 × 11명 = 동시 요청 100번 이상)
        for (int round = 0; round < 10; round++) {
            long first = members().member().create();
            List<Long> reporters = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                reporters.add(members().member().create());
            }
            long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
            reportService.report(member(first), "POST", postId, "SPAM", null);
            long caseId =
                    jdbc.queryForObject(
                            "SELECT id FROM report_case WHERE post_id = ? AND status = 'PENDING'",
                            Long.class,
                            postId);
            List<Callable<Object>> tasks = new ArrayList<>();
            tasks.add(
                    () -> {
                        resolution.resolve(adminViewer, caseId, "REJECT", null);
                        return "resolved";
                    });
            for (long r : reporters) {
                tasks.add(
                        () -> {
                            reportService.report(member(r), "POST", postId, "SPAM", null);
                            return "ok";
                        });
            }
            assertThat(runAll(tasks)).containsOnly("ok", "resolved");

            List<Long> inClosed =
                    jdbc.queryForList(
                            "SELECT id FROM report WHERE case_id = ?", Long.class, caseId);
            List<Long> resolved =
                    events.of(ReportResolved.class).stream().map(ReportResolved::reportId).toList();
            assertThat(resolved).as("닫힌 사건의 신고는 모두 함께 처리됐다").containsAll(inClosed);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM report r JOIN report_case c ON c.id ="
                                            + " r.case_id WHERE c.post_id = ?",
                                    Integer.class,
                                    postId))
                    .as("모든 신고가 어딘가에 있다")
                    .isEqualTo(11);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM report_case WHERE post_id = ? AND"
                                            + " status = 'PENDING'",
                                    Integer.class,
                                    postId))
                    .isLessThanOrEqualTo(1);
        }
    }
}
