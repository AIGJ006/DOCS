package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.application.WithdrawalProperties;
import com.team.blog.account.application.purge.WithdrawPurgeJob;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Outcome;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Reason;
import com.team.blog.account.application.purge.WithdrawalStepRegistry;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.shared.event.MemberRestored;
import com.team.blog.shared.event.MemberWithdrawn;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 탈퇴 30일 정리 작업 (015 T042, US3, contracts/purge-steps.md §2-2·§3, SC-003·SC-004·SC-005). 시계 Bean을
 * 바꾸는 새 컨텍스트를 만들지 않도록 {@code withdrawn_at}을 과거로 옮기고 {@link WithdrawPurgeJob#run(Instant)}을 지금 시각으로
 * 부른다. 010·011·014 단계는 015 임시 구현(order 65·70·80)이 채운다.
 */
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class WithdrawPurgeJobIT extends IntegrationTestBase {

    @Autowired WithdrawPurgeJob job;
    @Autowired WithdrawalPurgeRunner runner;
    @Autowired WithdrawalPurgeProbe probe;
    @Autowired WithdrawalStepRegistry registry;
    @Autowired WithdrawalMemberRepository memberRepository;
    @Autowired WithdrawalPolicy policy;
    @Autowired WithdrawalProperties properties;
    @Autowired ApplicationEvents events;

    private PostFixtures posts;
    private CommentFixtures comments;

    @BeforeEach
    void setUp() {
        probe.reset();
        posts = new PostFixtures(jdbc);
        comments = new CommentFixtures(jdbc);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 유예가 {@code days}일 지난 탈퇴 회원. */
    private long withdrawnDaysAgo(long id, int days) {
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(now().minus(Duration.ofDays(days))),
                id);
        return id;
    }

    private Map<String, Object> member(long id) {
        return jdbc.queryForMap("SELECT * FROM member WHERE id = ?", id);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private void like(long postId, long memberId) {
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, memberId);
        jdbc.update("UPDATE post SET like_count = like_count + 1 WHERE id = ?", postId);
    }

    @Test
    void US3_1_개인정보_없음_주소_남음(CapturedOutput output) {
        long a =
                members()
                        .member()
                        .handle("kim755030")
                        .nickname("김민서닉")
                        .email("leaver@example.com")
                        .lastActive(now(), true)
                        .create();
        jdbc.update("UPDATE member SET bio = '소개', nickname_changed_at = now() WHERE id = ?", a);
        jdbc.update(
                "INSERT INTO member_agreement (member_id, type, version) VALUES (?, 'TERMS',"
                        + " '2026-01')",
                a);
        jdbc.update(
                "INSERT INTO member_suspension (member_id, reason, started_at, ends_at,"
                        + " suspended_by) VALUES (?, '지난 정지', now() - interval '100 days',"
                        + " now() - interval '90 days', ?)",
                a,
                members().member().role("ADMIN").create());
        withdrawnDaysAgo(a, 31);
        long agreements = count("SELECT count(*) FROM member_agreement WHERE member_id = ?", a);
        long suspensions = count("SELECT count(*) FROM member_suspension WHERE member_id = ?", a);

        WithdrawPurgeJob.Result result = job.run(now());

        assertThat(result).isEqualTo(new WithdrawPurgeJob.Result(1, 0, 0, false));
        Map<String, Object> row = member(a);
        assertThat(row.get("handle")).isEqualTo("kim755030");
        assertThat(row.get("nickname")).isNull();
        assertThat(row.get("bio")).isNull();
        assertThat(row.get("last_active_at")).isNull();
        assertThat(row.get("nickname_changed_at")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", a)).isZero();
        assertThat(count("SELECT count(*) FROM member_agreement WHERE member_id = ?", a))
                .isEqualTo(agreements);
        assertThat(count("SELECT count(*) FROM member_suspension WHERE member_id = ?", a))
                .isEqualTo(suspensions);
        // 로그에 이메일·닉네임이 없다
        assertThat(output.getAll())
                .contains("탈퇴 정리 완료 memberId=" + a)
                .doesNotContain("leaver@example.com")
                .doesNotContain("김민서닉");
    }

    @Test
    void US3_2_답글_달린_댓글만_자리와_카운터() {
        long a = members().member().email("purged@example.com").create();
        long b = members().member().create();
        long c = members().member().create();
        long bPost = posts.create(b, PostFixtures.State.PUBLISHED_PUBLIC);
        long bPost2 = posts.create(b, PostFixtures.State.PUBLISHED_PUBLIC);
        long aPost = posts.create(a, PostFixtures.State.PUBLISHED_PUBLIC);
        long aTrashed = posts.create(a, PostFixtures.State.TRASHED);
        long aDraft = posts.create(a, PostFixtures.State.DRAFT);

        long replied = comments.on(bPost, a).content("답글 달린 내 댓글").create();
        comments.on(bPost, c).parent(replied).replyTo(a).content("남의 답글").create();
        long lonely = comments.on(bPost, a).content("답글 없는 내 댓글").create();
        long myReply = comments.on(bPost2, a).content("남의 글 내 답글").create();
        comments.on(aPost, b).content("내 글의 남 댓글").create();
        like(bPost, a);
        like(bPost, c);
        like(bPost2, a);
        like(aPost, b);
        withdrawnDaysAgo(a, 31);

        job.run(now());

        assertThat(count("SELECT count(*) FROM post WHERE author_id = ?", a)).isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM post WHERE id IN (?, ?, ?)",
                                aPost,
                                aTrashed,
                                aDraft))
                .isZero();
        Map<String, Object> placeholder =
                jdbc.queryForMap("SELECT content, deleted_at FROM comment WHERE id = ?", replied);
        assertThat(placeholder.get("deleted_at")).isNotNull();
        assertThat(count("SELECT count(*) FROM comment WHERE id IN (?, ?)", lonely, myReply))
                .isZero();
        assertThat(count("SELECT count(*) FROM post_like WHERE member_id = ?", a)).isZero();
        // SC-004: 모든 글에서 카운터 = 실제 행 수
        for (long post : List.of(bPost, bPost2)) {
            assertThat(comments.commentCount(post)).isEqualTo((int) comments.normalCount(post));
            assertThat(count("SELECT like_count FROM post WHERE id = ?", post))
                    .isEqualTo(count("SELECT count(*) FROM post_like WHERE post_id = ?", post));
        }
        assertThat(count("SELECT like_count FROM post WHERE id = ?", bPost)).isEqualTo(1);
        // 이벤트는 PostPurged(내 글마다)만
        assertThat(events.stream(PostPurged.class).map(PostPurged::postId).toList())
                .containsExactlyInAnyOrder(aPost, aTrashed, aDraft);
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
        assertThat(events.stream(MemberRestored.class)).isEmpty();
        assertThat(mailSender.countTo("purged@example.com")).isZero();
    }

    @Test
    void US3_3_한_단계_실패하면_전부_취소_다른_회원은_정리() {
        long a = withdrawnDaysAgo(members().member().nickname("실패할회원").create(), 40);
        long c = withdrawnDaysAgo(members().member().create(), 35);
        long b = members().member().create();
        long aPost = posts.create(a, PostFixtures.State.PUBLISHED_PUBLIC);
        long bPost = posts.create(b, PostFixtures.State.PUBLISHED_PUBLIC);
        like(bPost, a);
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id) VALUES (?, ?), (?, ?)", a, b, b, a);
        probe.failFor(a);

        WithdrawPurgeJob.Result first = job.run(now());

        assertThat(first).isEqualTo(new WithdrawPurgeJob.Result(1, 0, 1, false));
        assertThat(member(a).get("nickname")).isEqualTo("실패할회원");
        assertThat(member(a).get("deleted_at")).isNull();
        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", a)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post WHERE id = ?", aPost)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_like WHERE member_id = ?", a)).isEqualTo(1);
        assertThat(count("SELECT like_count FROM post WHERE id = ?", bPost)).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM follow WHERE follower_id = ? OR followee_id = ?",
                                a,
                                a))
                .isEqualTo(2);
        assertThat(member(c).get("deleted_at")).isNotNull();

        probe.reset();
        WithdrawPurgeJob.Result second = job.run(now());

        assertThat(second).isEqualTo(new WithdrawPurgeJob.Result(1, 0, 0, false));
        assertThat(member(a).get("deleted_at")).isNotNull();
        assertThat(count("SELECT count(*) FROM post WHERE id = ?", aPost)).isZero();
        assertThat(count("SELECT like_count FROM post WHERE id = ?", bPost)).isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM follow WHERE follower_id = ? OR followee_id = ?",
                                a,
                                a))
                .isZero();
    }

    @Test
    void US3_4_신고_사건_종료와_설명_비우기() {
        long a = members().member().create();
        long b = members().member().create();
        long bPost = posts.create(b, PostFixtures.State.PUBLISHED_PUBLIC);
        long myComment = comments.on(bPost, a).content("신고된 내 댓글").create();
        long caseOnMe =
                jdbc.queryForObject(
                        "INSERT INTO report_case (target_type, comment_id, target_author_id)"
                                + " VALUES ('COMMENT', ?, ?) RETURNING id",
                        Long.class,
                        myComment,
                        a);
        long caseOnB =
                jdbc.queryForObject(
                        "INSERT INTO report_case (target_type, post_id, target_author_id)"
                                + " VALUES ('POST', ?, ?) RETURNING id",
                        Long.class,
                        bPost,
                        b);
        long myReport =
                jdbc.queryForObject(
                        "INSERT INTO report (case_id, reporter_id, reason, detail) VALUES (?, ?,"
                                + " 'OTHER', '자세한 설명') RETURNING id",
                        Long.class,
                        caseOnB,
                        a);
        withdrawnDaysAgo(a, 31);

        job.run(now());

        Map<String, Object> closed =
                jdbc.queryForMap(
                        "SELECT status, handled_at, handled_by FROM report_case WHERE id = ?",
                        caseOnMe);
        assertThat(closed.get("status")).isEqualTo("CLOSED_NO_TARGET");
        assertThat(closed.get("handled_at")).isNotNull();
        assertThat(closed.get("handled_by")).isNull();
        Map<String, Object> report =
                jdbc.queryForMap("SELECT reason, detail FROM report WHERE id = ?", myReport);
        assertThat(report.get("reason")).isEqualTo("OTHER");
        assertThat(report.get("detail")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM report_case WHERE id = ?",
                                String.class,
                                caseOnB))
                .isEqualTo("PENDING");
    }

    @Test
    void US3_5_사진_표시_알림_정리() {
        long a = members().member().create();
        long b = members().member().create();
        long c = members().member().create();
        long image = new ImageFixtures(jdbc).image(a).attached().create();
        long bImage = new ImageFixtures(jdbc).image(b).attached().create();
        long bPost = posts.create(b, PostFixtures.State.PUBLISHED_PUBLIC);
        // ① 받은 알림
        long received =
                jdbc.queryForObject(
                        "INSERT INTO notification (receiver_id, type, last_actor_id, actor_count,"
                                + " group_key) VALUES (?, 'FOLLOW', ?, 1, 'follow') RETURNING id",
                        Long.class,
                        a,
                        b);
        // ② B가 받은 묶음 알림에 A·C → C만 남음
        long group =
                jdbc.queryForObject(
                        "INSERT INTO notification (receiver_id, type, post_id, last_actor_id,"
                                + " actor_count, group_key) VALUES (?, 'LIKE', ?, ?, 2, ?)"
                                + " RETURNING id",
                        Long.class,
                        b,
                        bPost,
                        a,
                        "like:" + bPost);
        jdbc.update(
                "INSERT INTO notification_actor (notification_id, actor_id, created_at) VALUES"
                        + " (?, ?, now() - interval '1 hour'), (?, ?, now())",
                group,
                c,
                group,
                a);
        // ③ A가 행동한 하나짜리
        long single =
                jdbc.queryForObject(
                        "INSERT INTO notification (receiver_id, type, post_id, last_actor_id,"
                                + " actor_count) VALUES (?, 'NEW_POST', ?, ?, 1) RETURNING id",
                        Long.class,
                        c,
                        bPost,
                        a);
        jdbc.update("INSERT INTO notification_mute (member_id, type) VALUES (?, 'LIKE')", a);
        withdrawnDaysAgo(a, 31);

        job.run(now());

        Instant detachedAt =
                jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                image)
                        .toInstant();
        assertThat(detachedAt).isBefore(now().minus(Duration.ofDays(6)));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                bImage))
                .isNull();
        assertThat(count("SELECT count(*) FROM notification WHERE id IN (?, ?)", received, single))
                .isZero();
        Map<String, Object> left =
                jdbc.queryForMap(
                        "SELECT actor_count, last_actor_id FROM notification WHERE id = ?", group);
        assertThat(left.get("actor_count")).isEqualTo(1);
        assertThat(((Number) left.get("last_actor_id")).longValue()).isEqualTo(c);
        assertThat(count("SELECT count(*) FROM notification_mute WHERE member_id = ?", a)).isZero();
    }

    @Test
    void US3_6_옛_주소_404() throws Exception {
        long a = members().member().handle("leaver77").create();
        withdrawnDaysAgo(a, 31);

        job.run(now());

        mockMvc.perform(get("/api/members/leaver77")).andExpect(status().isNotFound());
    }

    @Test
    void 단계는_order_순으로_불린다() {
        long a = members().member().create();
        posts.create(a, PostFixtures.State.PUBLISHED_PUBLIC);
        withdrawnDaysAgo(a, 31);

        job.run(now());

        assertThat(registry.orders())
                .isSorted()
                .contains(10, 20, 30, 40, 50, 60, 65, 70, 80, 85, 90);
        // order 85가 불린 때: 10(글)·50(로그인 수단)은 끝났고 90(익명화)은 아직
        assertThat(probe.seen()).containsExactly(new WithdrawalPurgeProbe.Seen(a, 0, 0, true));
    }

    @Test
    void 필수_단계가_빠지면_아무도_처리하지_않고_ERROR(CapturedOutput output) {
        long a = withdrawnDaysAgo(members().member().create(), 31);
        WithdrawalProperties.Purge purge = properties.purge();
        WithdrawalProperties missing99 =
                new WithdrawalProperties(
                        properties.gracePeriod(),
                        properties.suspendedPurgeAfter(),
                        properties.confirmText(),
                        new WithdrawalProperties.Purge(
                                purge.cron(),
                                purge.batchSize(),
                                List.of(10, 20, 99),
                                purge.redisKeyTemplates()));
        WithdrawPurgeJob strict =
                new WithdrawPurgeJob(memberRepository, runner, registry, policy, missing99);

        WithdrawPurgeJob.Result result = strict.run(now());

        assertThat(result.aborted()).isTrue();
        assertThat(result.purged()).isZero();
        assertThat(member(a).get("deleted_at")).isNull();
        assertThat(output.getAll()).contains("탈퇴 정리 단계 누락 orders=[99]");
    }

    @Test
    void 그_사이_복구한_회원은_건너뜀() {
        long a = withdrawnDaysAgo(members().member().nickname("돌아온회원").create(), 31);
        jdbc.update("UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", a);

        assertThat(runner.purgeOne(a, Reason.GRACE_EXPIRED)).isEqualTo(Outcome.SKIPPED);
        assertThat(member(a).get("nickname")).isEqualTo("돌아온회원");
        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", a)).isEqualTo(1);
        assertThat(probe.seen()).isEmpty();
    }

    @Test
    void 정확히_30일_전_신청이면_대상_아님() {
        Instant now = now();
        long a = members().member().create();
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(now.minus(properties.gracePeriod())),
                a);
        long older = withdrawnDaysAgo(members().member().create(), 31);

        WithdrawPurgeJob.Result result = job.run(now);

        assertThat(result.purged()).isEqualTo(1);
        assertThat(member(a).get("deleted_at")).isNull();
        assertThat(member(older).get("deleted_at")).isNotNull();
    }

    @Test
    void 이미_익명_처리된_회원은_다시_고르지_않는다() {
        long a = withdrawnDaysAgo(members().member().create(), 31);
        assertThat(job.run(now()).purged()).isEqualTo(1);
        assertThat(job.run(now())).isEqualTo(new WithdrawPurgeJob.Result(0, 0, 0, false));
        assertThat(runner.purgeOne(a, Reason.GRACE_EXPIRED)).isEqualTo(Outcome.SKIPPED);
    }
}
