package com.team.blog.tag.application;

/** 자동완성 후보 (contracts {@code TagSuggestion}). 내 태그면 공개 글 수가 0일 수 있다. */
public record TagSuggestionView(String name, long postCount, boolean mine) {}
