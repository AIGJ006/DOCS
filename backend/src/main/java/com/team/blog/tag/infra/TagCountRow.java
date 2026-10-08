package com.team.blog.tag.infra;

/** 태그 이름 + 글 수 한 줄 (008 전체 태그 목록·블로그 태그 줄). */
public record TagCountRow(String name, long postCount) {}
