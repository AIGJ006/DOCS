package com.team.blog.shared.security.integration;

import static com.team.blog.post.support.VisibilityApi.body;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.post.support.VisibilityApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 세션 저장소 장애 (004 T050, 02 §2-1, research R-09, Edge Case). Redis가 멈춘 동안 세션 쿠키를 가진 요청은 비회원으로 처리된다 —
 * 공개 글 읽기 판정은 계속되고, 공개 범위 변경은 401 {@code LOGIN_REQUIRED}다(5xx 아님). Redis가 돌아오면 같은 쿠키로 다시 작성자다. 001
 * {@code SessionResilienceIntegrationTest}의 004 판정 장치 쪽 확인이다.
 */
class SessionResilienceIT extends IntegrationTestBase {

    private int read(long postId, Cookie session) throws Exception {
        return mockMvc.perform(get("/api/__test/posts/{postId}", postId).cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void Redis가_멈추면_비회원으로_판정하고_돌아오면_작성자() throws Exception {
        PostFixtures posts = new PostFixtures(jdbc);
        VisibilityApi api = new VisibilityApi(mockMvc);
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long publicPost = posts.create(me, State.PUBLISHED_PUBLIC);
        long privatePost = posts.create(me, State.PUBLISHED_PRIVATE);
        assertThat(read(privatePost, session)).isEqualTo(200);

        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(read(publicPost, session)).as("공개 글 읽기는 계속").isEqualTo(200);
            assertThat(read(privatePost, session)).as("비회원이라 자기 비공개 글도 404").isEqualTo(404);

            MvcResult change = api.change(session, publicPost, "PRIVATE");
            assertThat(status(change)).isEqualTo(401);
            assertThat(body(change)).contains("\"code\":\"LOGIN_REQUIRED\"");
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT visibility FROM post WHERE id = ?",
                                String.class,
                                publicPost))
                .isEqualTo("PUBLIC");
        assertThat(read(privatePost, session)).isEqualTo(200);
        assertThat(status(api.change(session, publicPost, "PRIVATE"))).isEqualTo(200);
    }
}
