package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.bytes;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.support.TagApi;
import com.team.blog.tag.support.TagFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 태그 화면 주소의 첫 응답 (008 T028·T051·T055, SC-002, US2 #4~#6, US5 #3, contracts/normalization.md §4).
 * 요청은 {@code MockMvc} + 실제 {@code springSecurityFilterChain}(그 안의 {@code StrictHttpFirewall})을 거치고,
 * 주소는 브라우저처럼 이미 인코딩한 문자열을 그대로 보낸다.
 */
class TagPageShellIT extends IntegrationTestBase {

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    private TagApi api() {
        return new TagApi(mockMvc);
    }

    private void assertNotFoundPage(MvcResult result, String label) {
        assertThat(status(result)).as(label).isEqualTo(404);
        assertThat(bytes(result)).as(label).isEqualTo(notFoundPageRenderer.render().getBody());
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void 허용_문자_태그_주소가_모두_왕복된다() throws Exception {
        long author = members().member().create();
        TagFixtures tags = new TagFixtures(jdbc);
        List<String> names = List.of("c#", "c++", "node.js", ".net", "스프링-부트", "자바_기초");
        for (String name : names) {
            tags.attach(
                    new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                    name);
        }

        for (String name : names) {
            String path = "/tags/" + TagApi.segment(name);
            MvcResult result = api().getRaw(null, path);

            assertThat(status(result))
                    .as(path + " " + result.getResponse().getHeader("Location"))
                    .isEqualTo(200);
            assertThat(result.getResponse().getContentType()).startsWith("text/html");
            String html = body(result);
            assertThat(html).as(path).contains("<div id=\"root\">");
            assertThat(html).as(path).contains("<title>#" + name.replace("&", "&amp;"));
            assertThat(html)
                    .as(path)
                    .contains(
                            "<link rel=\"canonical\" href=\"http://localhost:8080" + path + "\">");
            assertThat(result.getResponse().getHeader("Cache-Control"))
                    .isEqualTo("private, no-cache");
        }
        assertThat(TagApi.segment("c#")).isEqualTo("c%23");
        assertThat(TagApi.segment("c++")).isEqualTo("c++");
    }

    @Test
    void 정규화되지_않은_주소는_301() throws Exception {
        MvcResult spaced = api().getRaw(null, "/tags/Spring%20Boot?from=search&x=1");
        assertThat(status(spaced)).isEqualTo(301);
        assertThat(spaced.getResponse().getHeader("Location"))
                .isEqualTo("/tags/spring-boot?from=search&x=1");
        assertThat(spaced.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");

        MvcResult sharp = api().getRaw(null, "/tags/C%23");
        assertThat(status(sharp)).isEqualTo(301);
        assertThat(sharp.getResponse().getHeader("Location")).isEqualTo("/tags/c%23");

        MvcResult leadingHash = api().getRaw(null, "/tags/%23JPA");
        assertThat(status(leadingHash)).isEqualTo(301);
        assertThat(leadingHash.getResponse().getHeader("Location")).isEqualTo("/tags/jpa");

        MvcResult korean =
                api().getRaw(null, "/tags/%EC%8A%A4%ED%94%84%EB%A7%81%20%20%EB%B6%80%ED%8A%B8");
        assertThat(status(korean)).isEqualTo(301);
        assertThat(korean.getResponse().getHeader("Location"))
                .isEqualTo("/tags/%EC%8A%A4%ED%94%84%EB%A7%81-%EB%B6%80%ED%8A%B8");
    }

    @Test
    void 형식이_틀린_이름은_404() throws Exception {
        for (String path :
                List.of(
                        "/tags/%F0%9F%94%A5",
                        "/tags/%E3%85%8B%E3%85%8B",
                        "/tags/...",
                        "/tags/c%40d",
                        "/tags/" + "a".repeat(31))) {
            assertNotFoundPage(api().getRaw(null, path), path);
        }
    }

    @Test
    void 글이_없는_태그도_200() throws Exception {
        MvcResult none = api().getRaw(null, "/tags/nobody-uses");
        long author = members().member().create();
        new TagFixtures(jdbc)
                .attach(
                        new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PRIVATE),
                        "nobody-uses");
        MvcResult privateOnly = api().getRaw(null, "/tags/nobody-uses");

        assertThat(status(none)).isEqualTo(200);
        assertThat(body(none)).contains("<div id=\"root\">").doesNotContain("공개 글");
        assertThat(bytes(privateOnly)).isEqualTo(bytes(none));
    }
}
