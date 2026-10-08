package com.team.blog.category.application;

/** 만들거나 바꾼 카테고리 한 개 (017 contracts {@code Category}). */
public record CategoryView(long id, String name, Long parentId, int position) {}
