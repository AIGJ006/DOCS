package com.team.blog.discovery.application.search;

/**
 * 검색어 단어 하나 (012 data-model §3, research R6).
 *
 * @param text 정규화(NFC)한 단어 원문 — 대소문자를 바꾸지 않는다(비교는 {@code ILIKE})
 * @param length 코드 포인트 수 (2 이상)
 */
public record SearchWord(String text, int length) {

    /** 본문에서도 찾는가 — 3글자 이상만 (FR-019, trigram 인덱스가 3글자 단위). */
    public boolean inContent() {
        return length >= 3;
    }
}
