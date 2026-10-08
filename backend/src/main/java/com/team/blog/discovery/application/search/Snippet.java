package com.team.blog.discovery.application.search;

import java.util.List;

/**
 * 검색어 주변 문장 (012 data-model §3, research R9, openapi {@code Snippet}). HTML 문자열이 아니다 — 화면은 {@code
 * text}를 {@code marks} 범위로 잘라 텍스트 노드와 {@code <mark>}로만 그린다(FR-034, 헌법 IV).
 *
 * @param text 원문 그대로 자른 글자(서식 기호 포함, 줄바꿈은 공백), 잘린 쪽에 {@code …}
 * @param marks 강조 범위 {@code [시작, 끝)} — UTF-16 단위(JS 문자열 색인과 같음), 겹치지 않고 오름차순
 */
public record Snippet(String text, List<int[]> marks) {

    public Snippet {
        marks = List.copyOf(marks);
    }

    public static Snippet plain(String text) {
        return new Snippet(text == null ? "" : text, List.of());
    }
}
