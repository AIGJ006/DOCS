package com.team.blog.shared.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 렌더링 결과 HTML의 안전 검사기 (docs/12 §9 "검사 방법", 002 T012).
 *
 * <ol>
 *   <li>실제 태그만 꺼내 태그 이름이 허용 목록(12 §4)에 있는지
 *   <li>속성 이름에 {@code on…}·{@code style}이 없는지
 *   <li>{@code href}·{@code src} 값을 브라우저처럼 엔티티를 풀고 공백·제어 문자를 지운 뒤 {@code javascript:}·{@code
 *       vbscript:}·{@code data:}로 시작하지 않는지
 * </ol>
 *
 * 글자로 표시되는 {@code &lt;script&gt;}는 태그가 아니므로 통과한다.
 */
public final class HtmlSafetyChecker {

    static final Set<String> ALLOWED_TAGS =
            Set.of(
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
                    "img");

    private static final Pattern TAG =
            Pattern.compile("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9:-]*)([^>]*)>", Pattern.DOTALL);
    private static final Pattern ATTR =
            Pattern.compile(
                    "([^\\s\"'>/=]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?",
                    Pattern.DOTALL);
    private static final Pattern NUMERIC_ENTITY =
            Pattern.compile("&#(?:[xX]([0-9a-fA-F]+)|([0-9]+));?");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z]+);?");
    private static final Map<String, String> NAMED =
            Map.of(
                    "colon", ":", "tab", "\t", "newline", "\n", "amp", "&", "lt", "<", "gt", ">",
                    "quot", "\"", "apos", "'", "nbsp", " ");
    private static final List<String> DANGEROUS_SCHEMES =
            List.of("javascript:", "vbscript:", "data:");

    private HtmlSafetyChecker() {}

    /** 문제 목록. 비어 있으면 안전. */
    public static List<String> problems(String html) {
        List<String> problems = new ArrayList<>();
        Matcher tag = TAG.matcher(html);
        while (tag.find()) {
            String name = tag.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TAGS.contains(name)) {
                problems.add("허용되지 않은 태그: " + tag.group());
            }
            if (!tag.group(1).isEmpty()) {
                continue;
            }
            Matcher attr = ATTR.matcher(tag.group(3));
            while (attr.find()) {
                String attrName = attr.group(1).toLowerCase(Locale.ROOT);
                String value =
                        attr.group(2) != null
                                ? attr.group(2)
                                : attr.group(3) != null ? attr.group(3) : attr.group(4);
                if (attrName.startsWith("on") || attrName.equals("style")) {
                    problems.add("위험한 속성: " + attrName + " in " + tag.group());
                }
                if ((attrName.equals("href") || attrName.equals("src")) && value != null) {
                    String normalized = normalizeUrl(value);
                    for (String scheme : DANGEROUS_SCHEMES) {
                        if (normalized.startsWith(scheme)) {
                            problems.add("위험한 주소: " + attrName + "=" + value);
                        }
                    }
                }
            }
        }
        return problems;
    }

    public static boolean isSafe(String html) {
        return problems(html).isEmpty();
    }

    /** 브라우저처럼 엔티티를 풀고(여러 번) 공백·제어 문자를 지운 뒤 소문자로. */
    static String normalizeUrl(String value) {
        String current = value;
        for (int i = 0; i < 3; i++) {
            String decoded = decodeEntities(current);
            if (decoded.equals(current)) {
                break;
            }
            current = decoded;
        }
        StringBuilder sb = new StringBuilder();
        current.codePoints()
                .filter(c -> c > 0x20 && c != 0x7F && !Character.isISOControl(c))
                .forEach(sb::appendCodePoint);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String decodeEntities(String s) {
        Matcher numeric = NUMERIC_ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (numeric.find()) {
            int cp =
                    numeric.group(1) != null
                            ? Integer.parseInt(numeric.group(1), 16)
                            : Integer.parseInt(numeric.group(2));
            numeric.appendReplacement(out, Matcher.quoteReplacement(Character.toString(cp)));
        }
        numeric.appendTail(out);
        Matcher named = NAMED_ENTITY.matcher(out.toString());
        StringBuilder out2 = new StringBuilder();
        while (named.find()) {
            String replacement = NAMED.get(named.group(1).toLowerCase(Locale.ROOT));
            named.appendReplacement(
                    out2,
                    Matcher.quoteReplacement(replacement != null ? replacement : named.group()));
        }
        named.appendTail(out2);
        return out2.toString();
    }
}
