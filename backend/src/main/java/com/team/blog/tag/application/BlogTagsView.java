package com.team.blog.tag.application;

import java.util.List;

/** 블로그 태그 줄 (contracts {@code BlogTags}). 화면은 처음 {@code initialVisible}개를 보이고 [태그 더 보기]로 펼친다. */
public record BlogTagsView(List<TagCountView> items, int initialVisible) {}
