package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 공통 404 화면 (openapi {@code /@{handle}/posts/{postId}} 404, FR-013·FR-014, research R-26): 고정 OG 문구
 * + {@code noindex}, 요청 값이 들어가지 않고 매번 같은 바이트.
 */
class NotFoundPageRendererTest {

    static final String OG_TITLE = "<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">";
    static final String OG_DESCRIPTION =
            "<meta property=\"og:description\" content=\"친구 공개·비공개 글이거나 삭제된 글입니다.\">";
    static final String ROBOTS = "<meta name=\"robots\" content=\"noindex\">";

    @Test
    void 고정_메타가_들어가고_두_번_렌더링한_바이트가_같다() {
        NotFoundPageRenderer renderer =
                new NotFoundPageRenderer(new ClassPathResource("static/index.html"));
        ResponseEntity<byte[]> first = renderer.render();
        ResponseEntity<byte[]> second = renderer.render();

        String html = new String(first.getBody(), StandardCharsets.UTF_8);
        assertThat(html).contains(OG_TITLE, OG_DESCRIPTION, ROBOTS);
        assertThat(first.getBody()).isEqualTo(second.getBody());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(first.getHeaders().getContentType())
                .isEqualTo(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        assertThat(first.getHeaders()).isEqualTo(second.getHeaders());
    }

    @Test
    void 셸의_app_head_자리에_넣고_셸_내용은_그대로_둔다() {
        String shell =
                "<!doctype html><html><head><meta charset=\"UTF-8\"><!--app-head--></head>"
                        + "<body><div id=\"root\"></div></body></html>";
        NotFoundPageRenderer renderer =
                new NotFoundPageRenderer(
                        new ByteArrayResource(shell.getBytes(StandardCharsets.UTF_8)));
        String html = new String(renderer.render().getBody(), StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("<!--app-head-->");
        assertThat(html).contains("<div id=\"root\"></div>");
        assertThat(html.indexOf(OG_TITLE))
                .isBetween(html.indexOf("<head>"), html.indexOf("</head>"));
    }

    @Test
    void 셸이_없으면_고정_최소_HTML을_쓴다() {
        NotFoundPageRenderer renderer =
                new NotFoundPageRenderer(new ClassPathResource("no/such/index.html"));
        String html = new String(renderer.render().getBody(), StandardCharsets.UTF_8);
        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains(OG_TITLE, OG_DESCRIPTION, ROBOTS, "볼 수 없는 페이지예요");
    }

    @Test
    void 요청_값은_어디에도_들어가지_않는다() {
        NotFoundPageRenderer renderer =
                new NotFoundPageRenderer(new ClassPathResource("static/index.html"));
        // 렌더러는 요청 값을 받는 인자가 없다. 어떤 글 주소든 같은 바이트이므로 이유·주소가 드러나지 않는다.
        String html = new String(renderer.render().getBody(), StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("kim755030", "/posts/");
    }
}
