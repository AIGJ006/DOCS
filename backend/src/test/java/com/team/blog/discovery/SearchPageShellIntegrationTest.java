package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 검색 화면 셸 (012 T021·T037, FR-038): {@code /search}·{@code /@{handle}?q=} 첫 응답에 noindex. */
class SearchPageShellIntegrationTest extends IntegrationTestBase {

    private static final String NOINDEX = "<meta name=\"robots\" content=\"noindex\">";

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 검색_화면은_noindex_셸이고_검색어를_메타에_넣지_않는다() throws Exception {
        MvcResult result = api().getAs(null, "/search?q={q}&tab=posts", "비밀검색어");

        assertThat(status(result)).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/html");
        assertThat(body(result)).contains(NOINDEX).doesNotContain("비밀검색어");
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        assertThat(body(api().getAs(null, "/search"))).contains(NOINDEX);
    }

    @Test
    void 블로그_안_검색은_noindex_그냥_블로그는_색인() throws Exception {
        members().member().handle("shellq").nickname("셸검색").create();

        MvcResult searched = api().getAs(null, "/@shellq?q={q}", "트랜잭션");
        MvcResult plain = api().getAs(null, "/@shellq");

        assertThat(status(searched)).isEqualTo(200);
        assertThat(body(searched)).contains(NOINDEX).doesNotContain("트랜잭션");
        assertThat(status(plain)).isEqualTo(200);
        assertThat(body(plain)).doesNotContain(NOINDEX);
    }
}
