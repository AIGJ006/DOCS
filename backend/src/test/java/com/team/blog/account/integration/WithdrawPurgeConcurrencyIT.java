package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.account.application.WithdrawalProperties;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Outcome;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Reason;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 복구와 정리가 동시에 들어올 때 (015 T043, research R5·R8). 회원 행 {@code FOR UPDATE}로 둘 중 하나만 반영되고, 반쯤 정리된 회원이
 * 없어야 한다. 기한 정각 근처(±50ms)로 신청 시각을 옮겨 두 결과가 모두 나올 수 있게 한다.
 */
class WithdrawPurgeConcurrencyIT extends IntegrationTestBase {

    private static final int ROUNDS = 20;

    @Autowired WithdrawalPurgeRunner runner;
    @Autowired WithdrawalPurgeProbe probe;
    @Autowired WithdrawalProperties properties;

    @Test
    void 복구와_정리_중_하나만_반영된다() throws Exception {
        probe.reset();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long me = members().member().create();
                Cookie session = TestLogin.loginAs(mockMvc, me);
                long offsetMs = (round % 5) * 25 - 50; // -50 … +50ms
                jdbc.update(
                        "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                        Timestamp.from(
                                Instant.now()
                                        .minus(properties.gracePeriod())
                                        .plus(Duration.ofMillis(offsetMs))),
                        me);
                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> restore =
                        pool.submit(
                                () -> {
                                    start.await();
                                    return mockMvc.perform(
                                                    TestLogin.withCsrf(
                                                            post("/api/me/restore"), session))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                });
                Future<Outcome> purge =
                        pool.submit(
                                () -> {
                                    start.await();
                                    return runner.purgeOne(me, Reason.GRACE_EXPIRED);
                                });
                start.countDown();
                int restoreStatus = restore.get(30, TimeUnit.SECONDS);
                Outcome outcome = purge.get(30, TimeUnit.SECONDS);

                Map<String, Object> row =
                        jdbc.queryForMap(
                                "SELECT status, nickname, deleted_at FROM member WHERE id = ?", me);
                long identities =
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_identity WHERE member_id = ?",
                                Long.class,
                                me);
                if (outcome == Outcome.PURGED) {
                    assertThat(restoreStatus).as("round %d", round).isIn(401, 409);
                    assertThat(row.get("deleted_at")).isNotNull();
                    assertThat(row.get("nickname")).isNull();
                    assertThat(identities).isZero();
                } else {
                    assertThat(restoreStatus).as("round %d", round).isIn(200, 409);
                    assertThat(row.get("deleted_at")).isNull();
                    assertThat(row.get("nickname")).isNotNull();
                    assertThat(identities).isEqualTo(1);
                    if (restoreStatus == 200) {
                        assertThat(row.get("status")).isEqualTo("ACTIVE");
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
