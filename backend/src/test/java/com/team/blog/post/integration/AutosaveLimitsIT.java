package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** 자동 저장 요청 제한·크기 제한·판정 순서 (002 T065, FR-011, B-2, QS §4-7). */
class AutosaveLimitsIT extends IntegrationTestBase {

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    @Test
    void 오초_안_두_번째_요청은_429와_Retry_After() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        assertThat(status(api().autosave(session, postId, saveBody("a", "1", 0)))).isEqualTo(200);
        MvcResult second = api().autosave(session, postId, saveBody("a", "2", 1));

        assertThat(status(second)).isEqualTo(429);
        assertThat((String) read(second, "$.code")).isEqualTo("RATE_LIMITED");
        assertThat(Integer.parseInt(second.getResponse().getHeader("Retry-After"))).isBetween(1, 5);
    }

    @Test
    void QS_4_7_첫_요청은_409_두_번째는_429() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult first = api().autosave(session, postId, saveBody("a", "1", 7));
        MvcResult second = api().autosave(session, postId, saveBody("a", "1", 7));

        assertThat(status(first)).isEqualTo(409);
        assertThat(status(second)).isEqualTo(429);
    }

    @Test
    void 요청_제한은_소유_확인보다_먼저() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long others = new com.team.blog.support.fixture.PostFixtures(jdbc).post(owner).create();

        assertThat(status(api().autosave(session, others, saveBody("a", "1", 0)))).isEqualTo(404);
        assertThat(status(api().autosave(session, others, saveBody("a", "1", 0)))).isEqualTo(429);
    }

    @Test
    void 일MB를_넘는_본문은_JSON_파싱_전에_413() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        // JSON이 아닌 1,100,000바이트: 파싱했다면 400이 된다
        byte[] huge = "x".repeat(1_100_000).getBytes(StandardCharsets.UTF_8);

        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(put("/api/posts/{id}/autosave", postId), session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(huge))
                        .andReturn();

        assertThat(status(result)).isEqualTo(413);
        assertThat((String) read(result, "$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(redis.hasKey("ratelimit:autosave:" + me)).isFalse();
    }

    @Test
    void 비회원의_큰_요청은_401() throws Exception {
        byte[] huge = "x".repeat(1_100_000).getBytes(StandardCharsets.UTF_8);
        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(put("/api/posts/{id}/autosave", 1L), null)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(huge))
                        .andReturn();
        assertThat(status(result)).isEqualTo(401);
    }
}
