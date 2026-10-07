package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Redis 장애 중 자동 저장 (002 T068, FR-018, B-5, QS §3). 공유 Redis 컨테이너를 멈추지 않도록 회로 차단기를 강제로 열어 장애를 모사하고,
 * 메모리 부족(OOM)은 자동 저장 스크립트 호출만 OOM 오류를 내게 해서 모사한다.
 */
class AutosaveRedisOutageIT extends IntegrationTestBase {

    static final AtomicBoolean OOM = new AtomicBoolean();

    @Autowired CircuitBreakerRegistry circuitBreakers;
    @Autowired AutosaveFlushJob job;
    @MockitoSpyBean StringRedisTemplate spiedRedis;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void oomOnAutosaveScript() {
        OOM.set(false);
        doAnswer(
                        invocation -> {
                            RedisScript<?> script = invocation.getArgument(0);
                            if (OOM.get() && script.getScriptAsString().contains("autosave-save")) {
                                throw new RedisSystemException(
                                        "OOM command not allowed when used memory > 'maxmemory'.",
                                        new IllegalStateException(
                                                "OOM command not allowed when used memory >"
                                                        + " 'maxmemory'."));
                            }
                            return invocation.callRealMethod();
                        })
                .when(spiedRedis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @AfterEach
    void closeCircuit() {
        OOM.set(false);
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
    }

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    private void redisDown() {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).transitionToForcedOpenState();
    }

    @Test
    void Redis_장애면_DB에_바로_저장하고_요청_제한은_통과() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        redisDown();

        MvcResult first = api().autosave(session, postId, saveBody("장애 중", "DB로", 0));
        MvcResult second = api().autosave(session, postId, saveBody("장애 중 2", "DB로 2", 1));

        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(status(second)).as(body(second)).isEqualTo(200);
        assertThat(((Number) read(second, "$.version")).longValue()).isEqualTo(2);
        Map<String, Object> row =
                jdbc.queryForMap("SELECT title, edit_version FROM post WHERE id = ?", postId);
        assertThat(row).containsEntry("title", "장애 중 2").containsEntry("edit_version", 2L);
        assertThat(fixtures().autosaveHash(postId)).isEmpty();
    }

    @Test
    void Redis_장애_중에도_버전이_다르면_409() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures().posts().post(me).title("DB").contentMd("DB").editVersion(3).create();
        redisDown();

        MvcResult result = api().autosave(session, postId, saveBody("옛", "옛", 2));

        assertThat(status(result)).isEqualTo(409);
        assertThat(((Number) read(result, "$.details.server.version")).longValue()).isEqualTo(3);
        assertThat((String) read(result, "$.details.server.title")).isEqualTo("DB");
    }

    @Test
    void Redis_장애_중_발행_글은_작업본에_저장() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();
        redisDown();

        MvcResult result = api().autosave(session, postId, saveBody("작업본", "작업본", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(fixtures().snapshot(postId)).contains(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT edit_version FROM post_draft WHERE post_id = ?",
                                Long.class,
                                postId))
                .isEqualTo(2L);
    }

    @Test
    void Redis_장애면_1분_반영은_그_회차를_건너뛴다() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).create();
        fixtures().putAutosave(postId, me, "보관", "보관", 1, Instant.now(), true);
        redisDown();

        job.flush();

        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEmpty();
    }

    @Test
    void Redis_메모리_부족이면_503_AUTOSAVE_UNAVAILABLE이고_DB로_우회하지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        OOM.set(true);

        MvcResult result = api().autosave(session, postId, saveBody("제목", "본문", 0));

        assertThat(status(result)).as(body(result)).isEqualTo(503);
        assertThat((String) read(result, "$.code")).isEqualTo("AUTOSAVE_UNAVAILABLE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT edit_version FROM post WHERE id = ?", Long.class, postId))
                .isZero();
    }
}
