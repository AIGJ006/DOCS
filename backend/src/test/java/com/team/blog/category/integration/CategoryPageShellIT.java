package com.team.blog.category.integration;

import static com.team.blog.category.support.CategoryApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.category.support.CategoryApi;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 블로그 페이지 주소의 카테고리 필터 (017 FR-032, research R8): 이 블로그의 카테고리가 아니면 서버가 404 화면. */
class CategoryPageShellIT extends IntegrationTestBase {

    @Test
    void 카테고리_필터_주소() throws Exception {
        CategoryApi api = new CategoryApi(mockMvc);
        CategoryFixtures categories = new CategoryFixtures(jdbc);
        long owner = members().member().create();
        String handle = categories.handleOf(owner);
        long dev = categories.create(owner, null, "개발");
        long others = categories.create(members().member().create(), null, "남의 것");

        MvcResult ok = api.getRaw(null, "/@" + handle + "?category=" + dev);
        assertThat(status(ok)).isEqualTo(200);
        assertThat(ok.getResponse().getContentType()).startsWith("text/html");

        for (String bad : List.of(String.valueOf(others), "999999", "abc")) {
            MvcResult result = api.getRaw(null, "/@" + handle + "?category=" + bad);
            assertThat(status(result)).as(bad).isEqualTo(404);
            assertThat(result.getResponse().getContentType()).startsWith("text/html");
        }
        // 대문자 주소는 쿼리를 유지한 채 먼저 301
        MvcResult upper = api.getRaw(null, "/@" + handle.toUpperCase() + "?category=" + dev);
        assertThat(status(upper)).isEqualTo(301);
        assertThat(upper.getResponse().getHeader("Location"))
                .isEqualTo("/@" + handle + "?category=" + dev);
    }
}
