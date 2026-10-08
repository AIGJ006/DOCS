package com.team.blog.shared.web.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** React 셸에 링크 미리보기 메타 넣기 (005 T011, FR-037, FR-044 이스케이프, research R-25). */
class SpaShellRendererTest {

    private static final String SHELL =
            "<!doctype html><html lang=\"ko\"><head><meta charset=\"UTF-8\" />"
                    + "<!--app-head--><title>블로그</title>"
                    + "<script type=\"module\" crossorigin src=\"/assets/index-abc.js\"></script>"
                    + "</head><body><div id=\"root\"></div></body></html>";

    /** 본문이 있는 script 태그 (인라인 스크립트). */
    private static final Pattern INLINE_SCRIPT =
            Pattern.compile("<script[^>]*>\\s*[^<\\s]", Pattern.CASE_INSENSITIVE);

    private static SpaShellRenderer renderer(String shell) {
        return new SpaShellRenderer(new ByteArrayResource(shell.getBytes(StandardCharsets.UTF_8)));
    }

    private static LinkPreviewMeta article(String title, String description) {
        return new LinkPreviewMeta(
                title,
                description,
                "https://blog.example.com/@kim755030/posts/42",
                "article",
                title,
                description,
                "https://storage.example.com/blog/images/3f2a.png",
                "2026-10-02T14:03:12.123456Z",
                "2026-10-03T05:03:00Z",
                false);
    }

    @Test
    void 자리_표시자에_메타를_넣고_원래_제목을_바꾼다() {
        String html = renderer(SHELL).render(article("JPA N+1 정리 - 김민서", "지연 로딩 정리"));

        assertThat(html).doesNotContain("<!--app-head-->").doesNotContain("<title>블로그</title>");
        assertThat(html)
                .contains("<title>JPA N+1 정리 - 김민서</title>")
                .contains("<meta name=\"description\" content=\"지연 로딩 정리\">")
                .contains(
                        "<link rel=\"canonical\" href=\"https://blog.example.com/@kim755030/posts/42\">")
                .contains("<meta property=\"og:type\" content=\"article\">")
                .contains("<meta property=\"og:title\" content=\"JPA N+1 정리 - 김민서\">")
                .contains("<meta property=\"og:description\" content=\"지연 로딩 정리\">")
                .contains(
                        "<meta property=\"og:image\""
                                + " content=\"https://storage.example.com/blog/images/3f2a.png\">")
                .contains(
                        "<meta property=\"article:published_time\""
                                + " content=\"2026-10-02T14:03:12.123456Z\">")
                .contains(
                        "<meta property=\"article:modified_time\" content=\"2026-10-03T05:03:00Z\">")
                .doesNotContain("noindex");
        assertThat(html.indexOf("<title>")).isLessThan(html.indexOf("</head>"));
        assertThat(html).contains("<div id=\"root\"></div>");
        assertThat(html).contains("src=\"/assets/index-abc.js\"");
    }

    @Test
    void 속성_값과_제목을_이스케이프한다() {
        String evil = "제목 \"><script>alert('x')</script> & 끝";

        String html = renderer(SHELL).render(article(evil, evil));

        assertThat(html).doesNotContain("<script>alert");
        String escaped = "제목 &quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; 끝";
        assertThat(html)
                .contains("<title>" + escaped + "</title>")
                .contains("<meta name=\"description\" content=\"" + escaped + "\">")
                .contains("<meta property=\"og:title\" content=\"" + escaped + "\">");
        assertThat(INLINE_SCRIPT.matcher(html).find()).isFalse();
    }

    @Test
    void 값이_없는_메타는_생략하고_제목이_없으면_원래_제목을_둔다() {
        String html = renderer(SHELL).render(LinkPreviewMeta.empty());

        assertThat(html)
                .contains("<title>블로그</title>")
                .doesNotContain("<!--app-head-->")
                .doesNotContain("og:")
                .doesNotContain("description")
                .doesNotContain("canonical")
                .doesNotContain("robots");
    }

    @Test
    void noindex면_robots_메타() {
        LinkPreviewMeta meta =
                new LinkPreviewMeta(
                        null, null, null, null, "볼 수 없는 글이에요", null, null, null, null, true);

        String html = renderer(SHELL).render(meta);

        assertThat(html)
                .contains("<meta name=\"robots\" content=\"noindex\">")
                .contains("<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">")
                .contains("<title>블로그</title>");
    }

    @Test
    void 자리_표시자가_없으면_head_끝에_넣는다() {
        String shell =
                "<html><head><title>x</title></head><body><div id=\"root\"></div></body></html>";

        String html = renderer(shell).render(article("새 제목", null));

        assertThat(html).contains("<title>새 제목</title>").doesNotContain("<title>x</title>");
        assertThat(html.indexOf("og:title")).isLessThan(html.indexOf("</head>"));
    }

    @Test
    void 셸이_없으면_최소_HTML() {
        SpaShellRenderer renderer =
                new SpaShellRenderer(new ClassPathResource("no/such/index.html"));

        String html = renderer.render(article("제목", "설명"));

        assertThat(html).contains("<div id=\"root\"></div>").contains("<title>제목</title>");
        assertThat(INLINE_SCRIPT.matcher(html).find()).isFalse();
    }

    @Test
    void 테스트_셸에도_인라인_스크립트가_없다() {
        SpaShellRenderer renderer =
                new SpaShellRenderer(new ClassPathResource("static/index.html"));

        String html = renderer.render(article("제목", "설명"));

        assertThat(html).contains("<div id=\"root\"></div>");
        assertThat(INLINE_SCRIPT.matcher(html).find()).isFalse();
    }

    @Test
    void 테마_결정_태그는_메타를_넣은_뒤에도_app_head_앞에_그대로_있다() {
        // 016 T023: 빌드 셸의 <head> 앞쪽 color-scheme 메타·theme-init.js가 상세·블로그 셸에도 남는다(첫 그리기 전 테마 결정).
        SpaShellRenderer renderer =
                new SpaShellRenderer(new ClassPathResource("static/index.html"));

        String html = renderer.render(article("제목", "설명"));

        int script = html.indexOf("<script src=\"/js/theme-init.js\"></script>");
        assertThat(html.indexOf("<meta name=\"color-scheme\" content=\"light dark\" />"))
                .isBetween(html.indexOf("<meta charset"), script);
        assertThat(script).isLessThan(html.indexOf("og:title"));
        assertThat(INLINE_SCRIPT.matcher(html).find()).isFalse();
    }
}
