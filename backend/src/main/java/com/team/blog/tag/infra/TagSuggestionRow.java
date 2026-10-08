package com.team.blog.tag.infra;

/** 자동완성 후보 한 줄 (008 research R11). {@code postCount}는 전체 공개 조건의 글 수, {@code mine}은 내 글에 쓴 태그인가. */
public record TagSuggestionRow(String name, long postCount, boolean mine) {}
