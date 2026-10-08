package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.purge.WithdrawPurgeJob;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.shared.event.MemberRestored;
import com.team.blog.shared.event.MemberWithdrawn;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** 영구 정지 1년 경과 자동 정리 (015 T044, FR-008, research R11). */
@RecordApplicationEvents
class SuspendedPurgeIT extends IntegrationTestBase {

    @Autowired WithdrawPurgeJob job;
    @Autowired WithdrawalPurgeProbe probe;
    @Autowired ApplicationEvents events;

    @BeforeEach
    void setUp() {
        probe.reset();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private long suspendedSince(Duration ago, Instant endsAt, String role, String email) {
        long id = members().member().role(role).email(email).create();
        members().suspend(id, endsAt, "정지");
        jdbc.update(
                "UPDATE member_suspension SET started_at = ? WHERE member_id = ?",
                Timestamp.from(now().minus(ago)),
                id);
        return id;
    }

    private Map<String, Object> member(long id) {
        return jdbc.queryForMap("SELECT * FROM member WHERE id = ?", id);
    }

    @Test
    void 열린_영구_정지_1년_1일이면_정리되고_정지_행은_그대로() {
        Instant before = now();
        long target = suspendedSince(Duration.ofDays(366), null, "USER", "perm@example.com");

        WithdrawPurgeJob.Result result = job.run(now());

        assertThat(result.purged()).isEqualTo(1);
        Map<String, Object> row = member(target);
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(((Timestamp) row.get("withdrawn_at")).toInstant()).isAfterOrEqualTo(before);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("nickname")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_suspension WHERE member_id = ? AND"
                                        + " lifted_at IS NULL",
                                Long.class,
                                target))
                .isEqualTo(1);
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
        assertThat(events.stream(MemberRestored.class)).isEmpty();
        assertThat(mailSender.countTo("perm@example.com")).isZero();
    }

    @Test
    void 아직_1년이_안_됐거나_기간_정지_해제된_정지_관리자는_그대로() {
        long young = suspendedSince(Duration.ofDays(364), null, "USER", "young@example.com");
        long periodic =
                suspendedSince(
                        Duration.ofDays(400),
                        now().plus(Duration.ofDays(5)),
                        "USER",
                        "periodic@example.com");
        long lifted = suspendedSince(Duration.ofDays(400), null, "USER", "lifted@example.com");
        jdbc.update("UPDATE member_suspension SET lifted_at = now() WHERE member_id = ?", lifted);
        long admin = suspendedSince(Duration.ofDays(400), null, "ADMIN", "admin@example.com");

        WithdrawPurgeJob.Result result = job.run(now());

        assertThat(result.purged()).isZero();
        for (long id : new long[] {young, periodic, lifted, admin}) {
            assertThat(member(id).get("deleted_at")).as("member %d", id).isNull();
            assertThat(member(id).get("nickname")).as("member %d", id).isNotNull();
        }
        assertThat(member(young).get("status")).isEqualTo("SUSPENDED");
    }
}
