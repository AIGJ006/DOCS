package com.team.blog.discovery.application.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 검색어 주변 문장 만들기 (012 research R9, contracts §6, FR-033·FR-034, Clarifications Q4).
 *
 * <ol>
 *   <li>본문에서 3글자 이상 단어(본문 검색 대상, FR-019)가 대소문자 무시로 처음 나온 곳을 찾는다. 없으면 제목에서 모든 단어로 찾는다. 둘 다 없으면(태그로만
 *       찾은 글) 005 {@code excerpt} 그대로(강조 없음).
 *   <li>찾은 곳 앞 {@code snippet-radius}(40) 코드 포인트 ~ 끝 뒤 40 코드 포인트를 원문 그대로 자른다(서식 기호를 지우지 않음). 서로게이트
 *       쌍을 가르지 않는다. 줄바꿈({@code \r\n}·{@code \n}·{@code \r})은 공백 하나로.
 *   <li>잘라 낸 글자 안 모든 단어의 모든 위치를 {@code [시작, 끝)}(UTF-16)으로 모으고 겹치거나 맞닿으면 합친다.
 *   <li>앞이 잘렸으면 맨 앞, 뒤가 잘렸으면 맨 끝에 {@code …}.
 * </ol>
 *
 * 대소문자 비교는 소문자로 바꾼 사본이 아니라 원문에 대한 정규식({@code CASE_INSENSITIVE | UNICODE_CASE})으로 한다 — {@code İ}처럼
 * 소문자로 바꾸면 길이가 달라지는 글자가 있어도 위치가 원문 기준으로 맞다. 결과에 HTML은 없다.
 */
@Component
public class SnippetBuilder {

    static final String ELLIPSIS = "…";
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");

    private final SearchProperties properties;

    public SnippetBuilder(SearchProperties properties) {
        this.properties = properties;
    }

    public Snippet build(String title, String contentMd, String excerpt, List<SearchWord> words) {
        List<Pattern> all = words.stream().map(SnippetBuilder::pattern).toList();
        List<Pattern> contentWords =
                words.stream().filter(SearchWord::inContent).map(SnippetBuilder::pattern).toList();
        Snippet fromContent = around(contentMd, contentWords, all);
        if (fromContent != null) {
            return fromContent;
        }
        Snippet fromTitle = around(title, all, all);
        if (fromTitle != null) {
            return fromTitle;
        }
        return Snippet.plain(excerpt);
    }

    private static Pattern pattern(SearchWord word) {
        return Pattern.compile(
                Pattern.quote(word.text()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** {@code find} 단어 중 가장 앞에 나온 곳 기준으로 자른다. 없으면 {@code null}. */
    private Snippet around(String source, List<Pattern> find, List<Pattern> mark) {
        if (source == null || source.isEmpty() || find.isEmpty()) {
            return null;
        }
        int start = -1;
        int end = -1;
        for (Pattern p : find) {
            Matcher m = p.matcher(source);
            if (m.find() && (start < 0 || m.start() < start)) {
                start = m.start();
                end = m.end();
            }
        }
        if (start < 0) {
            return null;
        }
        int radius = properties.snippetRadius();
        int from = back(source, start, radius);
        int to = forward(source, end, radius);
        String cut = LINE_BREAK.matcher(source.substring(from, to)).replaceAll(" ");
        boolean head = from > 0;
        boolean tail = to < source.length();
        int shift = head ? ELLIPSIS.length() : 0;
        List<int[]> marks = new ArrayList<>();
        for (int[] range : merge(ranges(cut, mark))) {
            marks.add(new int[] {range[0] + shift, range[1] + shift});
        }
        String text = (head ? ELLIPSIS : "") + cut + (tail ? ELLIPSIS : "");
        return new Snippet(text, marks);
    }

    /** {@code index}에서 코드 포인트 {@code count}개 앞 (처음 넘으면 0). */
    private static int back(String s, int index, int count) {
        int i = index;
        for (int n = 0; n < count && i > 0; n++) {
            i -= Character.charCount(s.codePointBefore(i));
        }
        return i;
    }

    /** {@code index}에서 코드 포인트 {@code count}개 뒤 (끝을 넘으면 길이). */
    private static int forward(String s, int index, int count) {
        int i = index;
        for (int n = 0; n < count && i < s.length(); n++) {
            i += Character.charCount(s.codePointAt(i));
        }
        return i;
    }

    private static List<int[]> ranges(String text, List<Pattern> patterns) {
        List<int[]> ranges = new ArrayList<>();
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                if (m.end() > m.start()) {
                    ranges.add(new int[] {m.start(), m.end()});
                }
            }
        }
        return ranges;
    }

    /** 겹치거나 맞닿은 범위를 합쳐 오름차순으로. */
    static List<int[]> merge(List<int[]> ranges) {
        List<int[]> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.<int[]>comparingInt(r -> r[0]).thenComparingInt(r -> r[1]));
        List<int[]> merged = new ArrayList<>();
        for (int[] r : sorted) {
            if (!merged.isEmpty() && r[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            } else {
                merged.add(new int[] {r[0], r[1]});
            }
        }
        return merged;
    }
}
