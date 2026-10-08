package com.team.blog.tag.application;

/**
 * 태그 페이지 머리말 (contracts {@code TagSummary}).
 *
 * @param name 정규화된 이름
 * @param postCount 전체 공개 조건의 글 수 (아무도 안 쓴 태그면 0)
 */
public record TagSummaryView(String name, long postCount) {}
