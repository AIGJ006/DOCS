package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.ids;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Redis(세션 저장소) 장애에도 공개 글은 비회원으로 계속 읽힌다 (005 T073, Edge Cases, 02 §2-1, 원칙 V). 001의 Redis 장애 처리(세션을
 * 못 읽으면 비로그인)를 쓴다 — Redis 컨테이너를 일시 정지한 동안 세션 쿠키가 있는 요청을 보낸다.
 */
class ReadingRedisOutageIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    @Test
    void Redis가_멈춰도_홈_상세_상세_화면이_비회원으로_200() throws Exception {
        ReadingApi api = new ReadingApi(mockMvc);
        Cookie session = fixture.loginAs(mockMvc, "B");
        long postId = fixture.postOf("A", "republished");
        assertThat((Boolean) read(api.detail(session, postId), "$.viewer.loggedIn")).isTrue();

        try (RedisOutage outage = RedisOutage.start()) {
            MvcResult home = api.home(session, null);
            assertThat(status(home)).as(body(home)).isEqualTo(200);
            assertThat(ids(home)).hasSize(9);

            MvcResult detail = api.detail(session, postId);
            assertThat(status(detail)).as(body(detail)).isEqualTo(200);
            assertThat((Boolean) read(detail, "$.viewer.loggedIn")).isFalse();
            assertThat((String) read(detail, "$.title")).isEqualTo("JPA N+1 정리");

            MvcResult page = api.getAs(session, "/@kim755030/posts/{id}", postId);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            assertThat(body(page)).contains("<title>JPA N+1 정리 - 김민서</title>");
        }
    }
}
