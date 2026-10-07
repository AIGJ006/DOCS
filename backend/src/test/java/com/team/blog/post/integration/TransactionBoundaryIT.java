package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.AutosaveService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 경계 가드 (002 T119, 05 J-5, research A-6). 트랜잭션 안의 Redis 쓰기는 test 프로필에서 예외(운영은 경고 로그). 발행·변경
 * 취소·수동 저장·자동 저장은 모두 이 가드 아래에서 통과한다(그 밖의 통합 테스트도 같은 프로필로 돈다). 읽기와 커밋 뒤 정리(006 {@code flushNow} 경로)는
 * 허용 목록이다.
 */
class TransactionBoundaryIT extends IntegrationTestBase {

    @Autowired RedisGuard redisGuard;
    @Autowired TransactionTemplate tx;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired CircuitBreakerRegistry circuitBreakers;
    @Autowired AutosaveService autosaveService;

    @Value("${blog.redis.fail-on-write-in-transaction}")
    boolean failOnWrite;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    @Test
    void test_프로필은_가드를_켠다() {
        assertThat(failOnWrite).isTrue();
    }

    @Test
    void 트랜잭션_안의_Redis_쓰기는_예외() {
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s ->
                                                redisGuard.runWrite(
                                                        () ->
                                                                redisTemplate
                                                                        .opsForValue()
                                                                        .set("t119", "x"),
                                                        () -> {})))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("트랜잭션 안에서 Redis에 쓰려고");
        assertThat(redisTemplate.hasKey("t119")).isFalse();
        assertThatThrownBy(
                        () -> tx.execute(s -> redisGuard.callWrite(() -> "written", () -> "none")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 트랜잭션_밖의_쓰기와_트랜잭션_안의_읽기는_허용() {
        redisGuard.runWrite(() -> redisTemplate.opsForValue().set("t119", "x"), () -> {});

        String read =
                tx.execute(
                        s ->
                                redisGuard.call(
                                        () -> redisTemplate.opsForValue().get("t119"), () -> null));

        assertThat(read).isEqualTo("x");
    }

    @Test
    void 커밋_뒤_작업의_쓰기는_허용하고_롤백이면_돌리지_않는다() {
        tx.executeWithoutResult(
                s ->
                        RedisGuard.runAfterCommit(
                                () ->
                                        redisGuard.runWrite(
                                                () ->
                                                        redisTemplate
                                                                .opsForValue()
                                                                .set("t119:commit", "x"),
                                                () -> {})));
        AtomicBoolean ran = new AtomicBoolean();
        tx.executeWithoutResult(
                s -> {
                    RedisGuard.runAfterCommit(() -> ran.set(true));
                    s.setRollbackOnly();
                });

        assertThat(redisTemplate.hasKey("t119:commit")).isTrue();
        assertThat(ran).isFalse();
    }

    @Test
    void 운영_설정은_경고만_남기고_쓴다() {
        RedisGuard lenient = new RedisGuard(redisTemplate, circuitBreakers, false);

        assertThatCode(
                        () ->
                                tx.executeWithoutResult(
                                        s ->
                                                lenient.runWrite(
                                                        () ->
                                                                redisTemplate
                                                                        .opsForValue()
                                                                        .set("t119:prod", "x"),
                                                        () -> {})))
                .doesNotThrowAnyException();
        assertThat(redisTemplate.hasKey("t119:prod")).isTrue();
    }

    @Test
    void 작성_흐름은_가드_아래에서_통과한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        assertThat(status(api().autosave(session, postId, saveBody("자동", "자동", 0)))).isEqualTo(200);
        assertThat(status(api().save(session, postId, saveBody("수동", "수동", 1)))).isEqualTo(200);
        var published =
                api().publish(session, postId, publishBody("발행", "본문", List.of("t"), "PUBLIC", 2));
        assertThat(status(published)).as(body(published)).isEqualTo(200);
        redisTemplate.keys("ratelimit:autosave:*").forEach(redisTemplate::delete);
        assertThat(status(api().autosave(session, postId, saveBody("고침", "고침", 3)))).isEqualTo(200);
        assertThat(status(api().discard(session, postId))).isEqualTo(204);

        // 006 flushNow: 트랜잭션 안에서 불러도 키 정리는 커밋 뒤
        long other = api().createPostId(session);
        new AuthoringFixtures(jdbc, redisTemplate)
                .putAutosave(other, me, "보관", "보관", 5, Instant.now(), true);
        assertThatCode(() -> tx.executeWithoutResult(s -> autosaveService.flushNow(other)))
                .doesNotThrowAnyException();
        assertThat(redisTemplate.hasKey("autosave:post:" + other)).isFalse();
    }
}
