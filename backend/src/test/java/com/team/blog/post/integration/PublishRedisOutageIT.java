package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Redis 장애 중 발행 (002 T107, FR-037, research A-8, QS §3 "Redis 장애"). 멱등 키 처리는 건너뛰고(경고 로그) 행 잠금 + 버전
 * 확인이 두 번째 요청을 409로 막는다. 장애는 회로 차단기를 강제로 열어 모사한다.
 */
@Import(PostTestConfig.class)
class PublishRedisOutageIT extends IntegrationTestBase {

    @Autowired CircuitBreakerRegistry circuitBreakers;
    @Autowired CommittedEvents events;

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @AfterEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
    }

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    @Test
    void 멱등_키를_건너뛰어도_같은_기준_버전의_두_번째_발행은_409이고_발행은_한_번() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = publishBody("장애 중 발행", "본문", List.of(), "PUBLIC", 0);
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).transitionToForcedOpenState();

        MvcResult first = api().publish(session, postId, request, key);
        MvcResult second = api().publish(session, postId, request, key);

        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(status(second)).as(body(second)).isEqualTo(409);
        assertThat((String) read(second, "$.code")).isEqualTo("VERSION_CONFLICT");
        assertThat(((Number) read(second, "$.details.server.version")).longValue()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT edit_version FROM post WHERE id = ?", Long.class, postId))
                .isEqualTo(1);
        assertThat(events.of(PostPublished.class)).hasSize(1);
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
        assertThat(redis.hasKey("idem:publish:" + me + ":" + key)).isFalse();
    }
}
