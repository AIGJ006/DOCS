package com.team.blog.shared.web.shell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * React 셸에 링크 미리보기 메타를 넣어 첫 응답 HTML을 만든다 (005 T012, H7, research R-25).
 *
 * <p>빌드된 {@code classpath:static/index.html}을 기동 때 한 번 읽어 캐시하고, {@code <!--app-head-->} 자리(004
 * {@code NotFoundPageRenderer}와 같은 규약)에 메타 태그를 끼워 넣는다. 자리 표시자가 없으면 {@code </head>} 앞에 넣는다. 템플릿 엔진을
 * 쓰지 않고 문자열로 조립하며, 값은 HTML 속성으로 이스케이프한다 — 인라인 스크립트를 만들지 않으므로 CSP {@code script-src 'self'}를 지킨다.
 *
 * <p>공통 404 HTML은 만들지 않는다 — 004 {@code NotFoundPageRenderer}를 쓴다.
 */
@Component
public class SpaShellRenderer {

    private static final Logger log = LoggerFactory.getLogger(SpaShellRenderer.class);

    static final String PLACEHOLDER = "<!--app-head-->";

    private static final String FALLBACK =
            "<!doctype html><html lang=\"ko\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + PLACEHOLDER
                    + "<title>블로그</title></head><body><div id=\"root\"></div></body></html>";

    private final String shell;

    public SpaShellRenderer(@Value("classpath:static/index.html") Resource shell) {
        this.shell = read(shell);
    }

    /** 메타를 넣은 HTML. */
    public String render(LinkPreviewMeta meta) {
        String head = head(meta);
        String html = shell;
        if (meta.title() != null) {
            html = replaceTitle(html, meta.title());
        }
        int placeholder = html.indexOf(PLACEHOLDER);
        if (placeholder >= 0) {
            return html.substring(0, placeholder)
                    + head
                    + html.substring(placeholder + PLACEHOLDER.length());
        }
        int headEnd = html.indexOf("</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + head + html.substring(headEnd);
        }
        return head + html;
    }

    private static String head(LinkPreviewMeta meta) {
        List<String> tags = new ArrayList<>();
        named(tags, "description", meta.description());
        if (meta.canonicalUrl() != null) {
            tags.add("<link rel=\"canonical\" href=\"" + escape(meta.canonicalUrl()) + "\">");
        }
        property(tags, "og:type", meta.ogType());
        property(tags, "og:title", meta.ogTitle());
        property(tags, "og:description", meta.ogDescription());
        property(tags, "og:image", meta.ogImage());
        property(tags, "article:published_time", meta.publishedTime());
        property(tags, "article:modified_time", meta.modifiedTime());
        if (meta.noindex()) {
            named(tags, "robots", "noindex");
        }
        return String.join("", tags);
    }

    private static void named(List<String> tags, String name, String content) {
        if (content != null) {
            tags.add("<meta name=\"" + name + "\" content=\"" + escape(content) + "\">");
        }
    }

    private static void property(List<String> tags, String property, String content) {
        if (content != null) {
            tags.add("<meta property=\"" + property + "\" content=\"" + escape(content) + "\">");
        }
    }

    /** 셸의 {@code <title>} 내용을 바꾼다 (없으면 메타 앞에 새로 만든다). */
    private static String replaceTitle(String html, String title) {
        String replacement = "<title>" + escape(title) + "</title>";
        int start = html.indexOf("<title>");
        if (start < 0) {
            return html.replaceFirst(
                    "(?i)</head>",
                    java.util.regex.Matcher.quoteReplacement(replacement) + "</head>");
        }
        int end = html.indexOf("</title>", start);
        if (end < 0) {
            return html;
        }
        return html.substring(0, start) + replacement + html.substring(end + "</title>".length());
    }

    /** HTML 속성·텍스트 양쪽에서 안전한 이스케이프 (FR-044). */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String read(Resource resource) {
        if (resource == null || !resource.exists()) {
            log.warn("React 셸이 없어 최소 HTML로 첫 응답을 만듭니다");
            return FALLBACK;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("React 셸을 읽지 못해 최소 HTML을 씁니다: {}", e.getMessage());
            return FALLBACK;
        }
    }
}
