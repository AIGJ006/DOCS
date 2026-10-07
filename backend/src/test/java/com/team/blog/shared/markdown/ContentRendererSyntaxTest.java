package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 정상 문법 코퍼스 (docs/12 §9-2의 13개, FR-042~045). 입력 {@code markdown/syntax/NN-이름.md}, 기대 HTML {@code
 * NN-이름.html} (앞뒤 공백만 무시하고 정확히 같아야 한다).
 */
class ContentRendererSyntaxTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();

    static Stream<Arguments> corpus() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] inputs = resolver.getResources("classpath:markdown/syntax/*.md");
        return Arrays.stream(inputs)
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(
                        r -> {
                            String name = r.getFilename().replace(".md", "");
                            return Arguments.of(
                                    name,
                                    read(r),
                                    read(
                                            resolver.getResource(
                                                    "classpath:markdown/syntax/"
                                                            + name
                                                            + ".html")));
                        });
    }

    @Test
    void 코퍼스는_13개다() throws IOException {
        assertThat(corpus().count()).isEqualTo(13);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void 기대_HTML과_같다(String name, String markdown, String expected) {
        RenderedContent rendered =
                RENDERER.render(markdown, new ImageContext(TestRenderers.AUTHOR));
        assertThat(rendered.html().strip()).isEqualTo(expected.strip());
        assertThat(rendered.html()).doesNotContain("<h1");
        assertThat(HtmlSafetyChecker.problems(rendered.html())).isEmpty();
        assertThat(rendered.renderVersion()).isEqualTo(RenderVersion.CURRENT).isEqualTo(1);
    }

    @Test
    void 작성자_사진의_키를_모으고_옛_주소도_같은_키다() {
        String md =
                "![a]("
                        + TestRenderers.legacyUrl(TestRenderers.OWNED_KEY)
                        + ")\n\n![b]("
                        + TestRenderers.url(TestRenderers.OWNED_NO_THUMB_KEY)
                        + ")\n\n![c]("
                        + TestRenderers.url(TestRenderers.OWNED_KEY)
                        + ")";
        RenderedContent rendered = RENDERER.render(md, new ImageContext(TestRenderers.AUTHOR));
        assertThat(rendered.ownedImageKeys())
                .containsExactly(TestRenderers.OWNED_KEY, TestRenderers.OWNED_NO_THUMB_KEY);
    }

    @Test
    void 미리보기는_로그인한_본인이_기준이라_남의_글_사진도_내_것이면_img다() {
        String md = "![x](" + TestRenderers.url(TestRenderers.OTHERS_KEY) + ")";
        assertThat(RENDERER.render(md, new ImageContext(TestRenderers.OTHER)).html())
                .contains("<img src=\"" + TestRenderers.url(TestRenderers.OTHERS_KEY) + "\"");
        assertThat(RENDERER.render(md, new ImageContext(TestRenderers.AUTHOR)).html())
                .doesNotContain("<img")
                .contains("[이미지] ");
    }

    @Test
    void 사진_판별은_본문_키_묶음으로_한_번만_조회한다() {
        var resolver = TestRenderers.resolver();
        DefaultContentRenderer renderer =
                TestRenderers.create(
                        resolver,
                        com.team.blog.shared.application.markdown.MarkdownProperties.defaults());
        String md =
                "![a]("
                        + TestRenderers.url(TestRenderers.OWNED_KEY)
                        + ") ![b]("
                        + TestRenderers.url(TestRenderers.OTHERS_KEY)
                        + ") ![c](https://example.com/x.png)";
        renderer.render(md, new ImageContext(TestRenderers.AUTHOR));
        assertThat(resolver.findOwnedCalls()).isEqualTo(1);

        resolver.clear();
        renderer.render("사진 없음", new ImageContext(TestRenderers.AUTHOR));
        assertThat(resolver.findOwnedCalls()).isZero();
    }

    @Test
    void 제목_id는_글자_숫자_밑줄_하이픈만_남긴다() {
        String html =
                RENDERER.render(
                                "# Spring Boot 4.1 <설치>!\n\n# !!!\n\n# !!!",
                                new ImageContext(TestRenderers.AUTHOR))
                        .html();
        assertThat(html).contains("<h2 id=\"h-spring-boot-41-설치\">");
        assertThat(html).contains("<h2 id=\"h-section\">").contains("<h2 id=\"h-section-1\">");
    }

    @Test
    void 메일_링크는_새_탭이_아니고_프로토콜_상대_주소는_외부_취급이다() {
        String html =
                RENDERER.render(
                                "[메일](mailto:a@example.com) [x](//evil.example/a)",
                                new ImageContext(TestRenderers.AUTHOR))
                        .html();
        assertThat(html).contains("<a href=\"mailto:a&#64;example.com\">메일</a>");
        assertThat(html).doesNotContain("href=\"//evil.example/a\">");
    }

    private static String read(Resource r) {
        try {
            return r.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
