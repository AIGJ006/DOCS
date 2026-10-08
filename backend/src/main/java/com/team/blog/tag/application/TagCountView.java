package com.team.blog.tag.application;

/** 이름 + 공개 글 수 (contracts {@code TagCount}). */
public record TagCountView(String name, long postCount) {}
