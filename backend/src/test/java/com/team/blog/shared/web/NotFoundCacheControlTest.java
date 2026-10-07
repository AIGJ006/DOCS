package com.team.blog.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 404 응답이 같은 본문과 {@code Cache-Control: private, no-store}를 갖는다 (004 T019, research R-26·R-30).
 */
@WebMvcTest(
        controllers = NotFoundCacheControlTest.NotFoundProbe.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = SecurityHeadersFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(NotFoundCacheControlTest.NotFoundProbe.class)
class NotFoundCacheControlTest {

    static final String BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    @Autowired MockMvc mockMvc;

    @RestController
    static class NotFoundProbe {
        @GetMapping("/probe/not-found")
        void notFound() {
            throw new NotFoundException("로그용 이유");
        }

        @GetMapping("/probe/post-not-found")
        void postNotFound() {
            throw new PostNotFoundException("비공개 글");
        }

        @GetMapping("/probe/ok")
        String ok() {
            return "ok";
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/probe/not-found", "/probe/post-not-found", "/api/no-such-path"})
    void 이유와_상관없이_같은_404_본문과_캐시_헤더(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(
                        content().json(BODY, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void 정상_응답에는_붙이지_않는다() throws Exception {
        mockMvc.perform(get("/probe/ok"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Cache-Control"));
    }
}
