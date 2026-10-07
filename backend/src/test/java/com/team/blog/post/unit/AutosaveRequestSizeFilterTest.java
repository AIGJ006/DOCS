package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.web.AutosaveRequestSizeFilter;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** 크기 제한 필터: 길이를 모르는(chunked) 요청도 읽으면서 세어 413 (002 T065·T080, B-2). */
class AutosaveRequestSizeFilterTest {

    private static final long MAX = 1_048_576;

    private final AutosaveRequestSizeFilter filter =
            new AutosaveRequestSizeFilter(
                    MAX, new ErrorResponseWriter(JsonMapper.builder().build()));

    /** 길이 헤더가 없는 요청 (chunked). */
    static class ChunkedRequest extends MockHttpServletRequest {
        ChunkedRequest(String method, String uri) {
            super(method, uri);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static FilterChain capture(AtomicReference<String> body) {
        return (req, res) -> body.set(read(req));
    }

    private static String read(ServletRequest req) throws IOException {
        return new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void 길이를_모르는_큰_요청은_읽으면서_세어_413() throws Exception {
        MockHttpServletRequest request = new ChunkedRequest("PUT", "/api/posts/1/autosave");
        request.setContent(new byte[(int) MAX + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> passed = new AtomicReference<>();

        filter.doFilter(request, response, capture(passed));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(passed.get()).isNull();
    }

    @Test
    void 길이를_모르는_작은_요청은_본문을_그대로_넘긴다() throws Exception {
        MockHttpServletRequest request = new ChunkedRequest("PUT", "/api/posts/1/autosave");
        request.setContent("{\"title\":\"t\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> passed = new AtomicReference<>();

        filter.doFilter(request, response, capture(passed));

        assertThat(passed.get()).isEqualTo("{\"title\":\"t\"}");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 자동_저장이_아닌_요청은_건드리지_않는다() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PUT", "/api/posts/1/working-copy");
        request.setContent(new byte[(int) MAX + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> passed = new AtomicReference<>();

        filter.doFilter(request, response, capture(passed));

        assertThat(passed.get()).hasSize((int) MAX + 1);
    }
}
