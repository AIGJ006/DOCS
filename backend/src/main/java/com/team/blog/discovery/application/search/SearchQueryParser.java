package com.team.blog.discovery.application.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 검색어 정리 — 이 서비스의 검색어 규칙은 이 클래스 하나다 (012 research R6·R10, contracts §4·§7, FR-018·FR-036).
 *
 * <p>글 검색 {@link #parse}: ① NFC → ② 앞뒤 공백 제거 → ③ 앞 {@code max-length}(50) 코드 포인트 → ④ 유니코드 공백으로 나눔 →
 * ⑤ 1 코드 포인트 단어 버림 → ⑥ 앞 {@code max-words}(5)단어. {@code %}·{@code _}·{@code \}·{@code #}은 글자 그대로
 * 남긴다(SQL에서 이스케이프한다). 화면 {@code features/search/parseQuery.ts}가 같은 규칙으로 요청 전에 막는다.
 *
 * <p>사람 검색 {@link #parsePeople}: ①~③ → 모든 공백 제거 → 맨 앞 {@code @} 하나 제거 → 2 코드 포인트 미만이면 빈 값.
 */
@Component
public class SearchQueryParser {

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EDGE_WHITESPACE =
            Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    private final SearchProperties properties;

    public SearchQueryParser(SearchProperties properties) {
        this.properties = properties;
    }

    /** 글 검색어. 남는 단어가 없으면 {@link SearchQuery#isEmpty()}. */
    public SearchQuery parse(String raw) {
        String text = prepare(raw);
        List<SearchWord> words = new ArrayList<>();
        for (String token : WHITESPACE.split(text)) {
            int length = token.codePointCount(0, token.length());
            if (length < 2) {
                continue;
            }
            words.add(new SearchWord(token, length));
            if (words.size() == properties.maxWords()) {
                break;
            }
        }
        return SearchQuery.of(words);
    }

    /** 사람 검색어. 덩어리가 2글자 미만이면 빈 값. */
    public Optional<PeopleQuery> parsePeople(String raw) {
        String token = WHITESPACE.matcher(prepare(raw)).replaceAll("");
        if (token.startsWith("@")) {
            token = token.substring(1);
        }
        if (token.codePointCount(0, token.length()) < 2) {
            return Optional.empty();
        }
        return Optional.of(new PeopleQuery(token));
    }

    /** ①~③. */
    private String prepare(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = EDGE_WHITESPACE.matcher(text).replaceAll(""); // 앞뒤 공백 (유니코드 공백 포함)
        int max = properties.maxLength();
        if (text.codePointCount(0, text.length()) > max) {
            text = text.substring(0, text.offsetByCodePoints(0, max));
        }
        return text;
    }
}
