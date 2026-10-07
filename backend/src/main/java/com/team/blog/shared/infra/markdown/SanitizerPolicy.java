package com.team.blog.shared.infra.markdown;

import com.team.blog.shared.config.CoreProperties;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 정화 허용 목록 (12 §4 {@code PolicyFactory} 그대로, FR-041). 렌더러에 버그가 있어도 여기서 한 번 더 막는다.
 *
 * <p>{@code img src}는 공개 주소({@code blog.image.public-base-url}) + {@code /}로 시작할 때만 남는다. 이 값은 001
 * {@code CoreProperties.Image}에서 받아 CSP {@code img-src}와 같은 설정값 하나를 쓴다(H3). 옛 주소로 쓴 작성자 사진은 AST
 * 변환에서 이미 지금 공개 주소로 바뀌어 있다.
 */
@Component
public class SanitizerPolicy {

    private final PolicyFactory policy;

    @Autowired
    public SanitizerPolicy(CoreProperties properties) {
        this(properties.image().publicBaseUrl());
    }

    private SanitizerPolicy(String publicBaseUrl) {
        this.policy = build(stripTrailingSlash(publicBaseUrl.strip()));
    }

    /** 설정 없이 만드는 생성 (단위 테스트·도구용). */
    public static SanitizerPolicy of(String publicBaseUrl) {
        return new SanitizerPolicy(publicBaseUrl);
    }

    public String sanitize(String html) {
        return policy.sanitize(html);
    }

    private static PolicyFactory build(String publicBaseUrl) {
        String imagePrefix = publicBaseUrl + "/";
        return new HtmlPolicyBuilder()
                .allowElements(
                        "p",
                        "br",
                        "hr",
                        "blockquote",
                        "h2",
                        "h3",
                        "h4",
                        "h5",
                        "h6",
                        "strong",
                        "em",
                        "del",
                        "ul",
                        "ol",
                        "li",
                        "input",
                        "code",
                        "pre",
                        "table",
                        "thead",
                        "tbody",
                        "tr",
                        "th",
                        "td",
                        "a",
                        "img")
                .allowAttributes("id")
                .matching(Pattern.compile("h-[\\p{L}\\p{N}_-]{1,100}"))
                .onElements("h2", "h3", "h4", "h5", "h6")
                .allowAttributes("start")
                .matching(Pattern.compile("\\d{1,6}"))
                .onElements("ol")
                .allowAttributes("type")
                .matching(Pattern.compile("checkbox"))
                .onElements("input")
                .allowAttributes("checked", "disabled")
                .onElements("input")
                .allowAttributes("class")
                .matching(Pattern.compile("language-[a-z0-9+#-]{1,20}"))
                .onElements("code")
                .allowAttributes("align")
                .matching(Pattern.compile("left|center|right"))
                .onElements("th", "td")
                .allowUrlProtocols("http", "https", "mailto")
                .allowAttributes("href", "title")
                .onElements("a")
                .allowAttributes("target")
                .matching(Pattern.compile("_blank"))
                .onElements("a")
                .allowAttributes("rel")
                .matching(Pattern.compile("noopener noreferrer nofollow ugc"))
                .onElements("a")
                .allowAttributes("src")
                .matching(
                        (elementName, attributeName, value) ->
                                value.startsWith(imagePrefix) ? value : null)
                .onElements("img")
                .allowAttributes("alt", "title")
                .onElements("img")
                .allowAttributes("loading")
                .matching(Pattern.compile("lazy"))
                .onElements("img")
                .allowAttributes("decoding")
                .matching(Pattern.compile("async"))
                .onElements("img")
                .toFactory();
    }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }
}
