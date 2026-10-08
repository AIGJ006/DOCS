package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 정적 파일 캐시 머리 (016 T011, research R10, data-model §4). 보안 필터를 포함한 전체 앱에서 확인한다 (Spring Security 기본
 * 캐시 머리가 덮지 않는지).
 */
class StaticResourceCacheConfigIT extends IntegrationTestBase {

    @Test
    void theme_init은_no_cache와_ETag_다시_확인하면_304() throws Exception {
        String etag =
                mockMvc.perform(get("/js/theme-init.js"))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                        .andExpect(header().exists(HttpHeaders.ETAG))
                        .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/js/theme-init.js").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void 해시_붙은_assets는_1년_immutable() throws Exception {
        mockMvc.perform(get("/assets/index-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.CACHE_CONTROL,
                                        CacheControlPolicy.STATIC_IMMUTABLE));
    }

    @Test
    void 없는_정적_파일은_기존_404_머리() throws Exception {
        mockMvc.perform(get("/js/no-such.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE));
    }

    @Test
    void SPA_셸_머리는_바꾸지_않는다() throws Exception {
        String cacheControl =
                mockMvc.perform(get("/settings"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.CACHE_CONTROL);
        assertThat(cacheControl).isNotEqualTo(CacheControlPolicy.STATIC_IMMUTABLE);
    }
}
