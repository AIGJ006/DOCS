package com.team.blog.shared.infra.markdown;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.renderer.html.AttributeProvider;

/**
 * 제목 {@code id} (FR-042, 12 §3·§7-3). {@code h-} + 제목 글자 중 글자·숫자·{@code _}·{@code -}만(공백은 {@code
 * -}, 소문자, 최대 100자). 같은 이름은 문서 순서대로 {@code -1}, {@code -2}…를 붙인다. 남는 글자가 없으면 {@code h-section}.
 *
 * <p>{@code h-} 접두어는 페이지의 다른 요소 이름과 겹치지 않게 한다(DOM clobbering 방지). 렌더링 한 번마다 새 인스턴스를 만든다(같은 이름 세기).
 */
public class HeadingAnchorProvider implements AttributeProvider {

    static final String PREFIX = "h-";
    static final int MAX_SLUG = 100;
    static final String FALLBACK = "section";

    private final Set<String> used = new HashSet<>();

    @Override
    public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
        if (node instanceof Heading heading) {
            attributes.put("id", next(textOf(heading)));
        }
    }

    String next(String text) {
        String base = slug(text);
        String candidate = base;
        for (int n = 1; !used.add(candidate); n++) {
            candidate = truncate(base, MAX_SLUG - (String.valueOf(n).length() + 1)) + "-" + n;
        }
        return PREFIX + candidate;
    }

    static String slug(String text) {
        StringBuilder sb = new StringBuilder();
        text.strip()
                .toLowerCase(Locale.ROOT)
                .codePoints()
                .forEach(
                        c -> {
                            if (Character.isWhitespace(c)) {
                                sb.append('-');
                            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                                sb.appendCodePoint(c);
                            }
                        });
        String s = sb.toString().replaceAll("-{2,}", "-");
        if (s.isEmpty() || s.chars().allMatch(c -> c == '-')) {
            return FALLBACK;
        }
        return truncate(s, MAX_SLUG);
    }

    private static String truncate(String s, int maxCodePoints) {
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }

    /** 제목 안의 글자 (인라인 코드·직접 쓴 HTML 포함 — 화면에 글자로 보이는 그대로). */
    static String textOf(Node root) {
        StringBuilder sb = new StringBuilder();
        Node node = root.getFirstChild();
        while (node != null) {
            if (node instanceof Text t) {
                sb.append(t.getLiteral());
            } else if (node instanceof Code c) {
                sb.append(c.getLiteral());
            } else if (node instanceof HtmlInline h) {
                sb.append(h.getLiteral());
            }
            node = AstWalk.next(root, node, true);
        }
        return sb.toString();
    }
}
