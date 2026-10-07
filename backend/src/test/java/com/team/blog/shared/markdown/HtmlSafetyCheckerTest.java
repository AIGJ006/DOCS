package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 검사기 자체 확인 (12 §9: 위험한 HTML 6개를 모두 잡고, 안전한 HTML 3개를 잘못 판정하지 않는다). */
class HtmlSafetyCheckerTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<p>hi</p><script>alert(1)</script>",
                "<img src=\"https://cdn.devlog.example/a.webp\" onerror=\"alert(1)\" />",
                "<p style=\"background:url(javascript:alert(1))\">x</p>",
                "<a href=\"javascript:alert(1)\">x</a>",
                "<a href=\"&#106;ava&#x73;cript&colon;alert(1)\">x</a>",
                "<img src=\" \tdata:image/svg+xml;base64,PHN2Zz4=\" />",
            })
    void 위험한_HTML을_잡는다(String html) {
        assertThat(HtmlSafetyChecker.problems(html)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>",
                "<p><a rel=\"noopener noreferrer nofollow ugc\" href=\"https://spring.io\""
                        + " target=\"_blank\">외부</a> <a href=\"/&#64;kim/posts/1\">내부</a></p>",
                "<ul><li><input type=\"checkbox\" disabled=\"\" checked=\"\" /> 완료</li></ul>"
                        + "<pre><code class=\"language-java\">x &#61; 1;</code></pre>",
            })
    void 안전한_HTML은_통과한다(String html) {
        assertThat(HtmlSafetyChecker.problems(html)).isEmpty();
    }
}
