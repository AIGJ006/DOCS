package com.team.blog.discovery.application.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 정리한 글 검색어 (012 data-model §3, research R6). 단어는 0~{@code blog.search.max-words}개이고 각 2 코드 포인트
 * 이상이다. 단어가 없으면 화면·서버 모두 "두 글자 이상 입력해 주세요"(400 {@code SEARCH_QUERY_TOO_SHORT}).
 *
 * <p>원문 검색어는 로그·DB에 남기지 않는다(FR-039) — 이 객체도 {@link #toString()}에 단어를 넣지 않는다.
 *
 * @param words 단어 (입력 순서)
 * @param hasTwoCharWord 2글자 단어가 있는가 → 응답 {@code notice: TWO_CHAR_TITLE_TAG_ONLY} (FR-024)
 */
public record SearchQuery(List<SearchWord> words, boolean hasTwoCharWord) {

    public SearchQuery {
        words = List.copyOf(words);
    }

    public static SearchQuery of(List<SearchWord> words) {
        return new SearchQuery(words, words.stream().anyMatch(w -> !w.inContent()));
    }

    public boolean isEmpty() {
        return words.isEmpty();
    }

    /** 단어 중 하나라도 본문에서 찾는가 (3글자 이상 단어가 있는가). */
    public boolean anyInContent() {
        return words.stream().anyMatch(SearchWord::inContent);
    }

    /** 가장 긴 단어 (같으면 앞 단어) — 후보 조회의 기준 (research R8). */
    public SearchWord longest() {
        SearchWord best = null;
        for (SearchWord word : words) {
            if (best == null || word.length() > best.length()) {
                best = word;
            }
        }
        if (best == null) {
            throw new IllegalStateException("단어가 없는 검색어");
        }
        return best;
    }

    /** 커서 목록 구분용 지문: 단어들을 {@code \u0000}으로 이은 SHA-256 앞 16자(16진, research R8). */
    public String fingerprint() {
        String joined = words.stream().map(SearchWord::text).collect(Collectors.joining("\u0000"));
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 검색어 코드 포인트 수의 합 (운영 로그용 — 원문 대신). */
    public int codePointLength() {
        return words.stream().mapToInt(SearchWord::length).sum();
    }

    @Override
    public String toString() {
        return "SearchQuery[words=" + words.size() + ", hasTwoCharWord=" + hasTwoCharWord + "]";
    }
}
