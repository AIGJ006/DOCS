package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.media.web.StorageCspContributor;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MinioContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** CSP {@code connect-src}에 업로드 주소 출처 (003 T019, research R20). */
class StorageCspIT extends IntegrationTestBase {

    @Test
    void 업로드_주소_출처가_공개_주소와_다르면_connect_src에_더한다() throws Exception {
        String csp =
                mockMvc.perform(get("/api/auth/csrf"))
                        .andReturn()
                        .getResponse()
                        .getHeader("Content-Security-Policy");

        String connect =
                csp.lines()
                        .flatMap(l -> java.util.Arrays.stream(l.split(";")))
                        .map(String::strip)
                        .filter(d -> d.startsWith("connect-src"))
                        .findFirst()
                        .orElseThrow();
        // 테스트 공개 주소는 기본값 localhost:9000, 업로드 주소는 임의 포트의 저장소 컨테이너
        assertThat(connect)
                .isEqualTo(
                        "connect-src 'self' http://localhost:9000 "
                                + MinioContainerSupport.endpoint());
        assertThat(csp).contains("img-src 'self' http://localhost:9000 data: blob:");
    }

    @Test
    void 같은_출처면_중복해서_넣지_않는다() {
        StorageCspContributor same =
                StorageCspContributor.of("http://localhost:9000", "http://localhost:9000/");
        assertThat(same.extraConnectSrc()).isEmpty();
        assertThat(same.appliesTo(new MockHttpServletRequest())).isFalse();

        StorageCspContributor other =
                StorageCspContributor.of("https://cdn.example", "https://s3.example:9443");
        assertThat(other.extraConnectSrc()).containsExactly("https://s3.example:9443");
    }
}
