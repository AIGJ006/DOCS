package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 운영 지표 (002 T120). Redis 장애 동안의 DB 직접 저장·멱등 처리 건너뜀·⑨ 정리 실패를 센다. */
class PostAuthoringMetricsIT extends IntegrationTestBase {

    @Autowired MeterRegistry meters;
    @Autowired CircuitBreakerRegistry circuitBreakers;

    @AfterEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
    }

    private double count(String name) {
        return meters.get(name).counter().count();
    }

    @Test
    void Redis_장애_동안의_우회를_센다() throws Exception {
        EditorApi api = new EditorApi(mockMvc);
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api.createPostId(session);
        double fallback = count("blog.post.autosave.db-fallback");
        double skipped = count("blog.post.publish.idempotency-skipped");
        double releaseFailed = count("blog.post.autosave.release-failed");
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).transitionToForcedOpenState();

        assertThat(status(api.autosave(session, postId, saveBody("장애", "장애", 0)))).isEqualTo(200);
        MvcResult published =
                api.publish(session, postId, publishBody("장애", "장애", List.of(), "PUBLIC", 1));

        assertThat(status(published)).as(body(published)).isEqualTo(200);
        assertThat(count("blog.post.autosave.db-fallback")).isEqualTo(fallback + 1);
        assertThat(count("blog.post.publish.idempotency-skipped")).isEqualTo(skipped + 1);
        assertThat(count("blog.post.autosave.release-failed")).isEqualTo(releaseFailed + 1);
        assertThat(meters.find("blog.post.autosave.flush-failed").gauge()).isNotNull();
        assertThat(meters.find("blog.post.rerender.remaining").gauge()).isNotNull();
    }
}
