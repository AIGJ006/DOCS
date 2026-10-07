package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * XSS 공격 문자열 코퍼스 (docs/12 §9-1의 32개, FR-040·041·043, SC-003). 입력은 {@code
 * src/test/resources/markdown/xss/}. 결과 HTML은 {@link HtmlSafetyChecker}를 통과해야 한다.
 */
class ContentRendererXssTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();

    static Stream<Arguments> corpus() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:markdown/xss/*.md");
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(
                        r -> {
                            try {
                                return Arguments.of(
                                        r.getFilename(),
                                        r.getContentAsString(StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
    }

    @Test
    void 코퍼스는_32개다() throws IOException {
        assertThat(corpus().count()).isEqualTo(32);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void 결과_HTML에_실행_가능한_스크립트가_없다(String name, String markdown) {
        RenderedContent rendered =
                RENDERER.render(markdown, new ImageContext(TestRenderers.AUTHOR));
        String html = rendered.html();

        assertThat(HtmlSafetyChecker.problems(html)).as(name + " → " + html).isEmpty();
        String lower = html.toLowerCase(Locale.ROOT);
        assertThat(lower).as(name).doesNotContain("<script", "<svg", "<iframe", "<style", "<math");
        if (name.contains("-tag-") || name.contains("-context-") || name.contains("-syntax-")) {
            // 직접 쓴 HTML은 글자로 보인다 (S-2)
            assertThat(html).as(name + " → " + html).contains("&lt;");
        }
    }

    @Test
    void 대체글의_onerror는_alt_값_안의_글자로만_남는다() {
        String md = read("21-image-alt-onerror.md");
        String html = RENDERER.render(md, new ImageContext(TestRenderers.AUTHOR)).html();
        assertThat(html).startsWith("<p><img src=\"" + TestRenderers.url(TestRenderers.OWNED_KEY));
        assertThat(HtmlSafetyChecker.problems(html)).isEmpty();
        assertThat(html).doesNotContain(" onerror=");
    }

    @Test
    void 위험한_링크는_주소가_제거되고_글자만_남는다() {
        for (String file :
                List.of(
                        "08-link-javascript.md",
                        "09-link-mixed-case.md",
                        "17-link-vbscript.md",
                        "18-link-data.md")) {
            String html = RENDERER.render(read(file), new ImageContext(1L)).html();
            assertThat(html).as(file).contains("클릭").doesNotContain("href=\"javascript");
        }
    }

    private static String read(String file) {
        try {
            return new PathMatchingResourcePatternResolver()
                    .getResource("classpath:markdown/xss/" + file)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
