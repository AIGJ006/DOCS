package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.shared.event.FriendAccepted;
import com.team.blog.shared.event.FriendRequested;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** US7 #3 동시 맞요청 (FR-054, data-model §2-5 {@code INSERT … ON CONFLICT}). */
class FriendshipConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired FriendEventRecorder events;

    @Test
    @DisplayName("A→B·B→A 각 10건 동시 → 행 1개 ACCEPTED, 500 0건, 요청·수락 이벤트 각 1번")
    void simultaneousMutualRequests() throws Exception {
        events.clear();
        long a = members().member().handle("alice").create();
        long b = members().member().handle("bob").create();
        Cookie sessionA = TestLogin.loginAs(mockMvc, a);
        Cookie sessionB = TestLogin.loginAs(mockMvc, b);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                Cookie session = i % 2 == 0 ? sessionA : sessionB;
                String target = i % 2 == 0 ? "bob" : "alice";
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return mockMvc.perform(
                                            TestLogin.withCsrf(
                                                    put("/api/members/" + target + "/friend"),
                                                    session))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status FROM friendship");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("status")).isEqualTo("ACCEPTED");
        assertThat(events.of(FriendRequested.class)).hasSize(1);
        assertThat(events.of(FriendAccepted.class)).hasSize(1);
    }
}
