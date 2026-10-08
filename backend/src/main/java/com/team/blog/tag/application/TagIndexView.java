package com.team.blog.tag.application;

import java.util.List;

/** 전체 태그 목록 (contracts {@code TagIndex}). */
public record TagIndexView(List<TagCountView> items) {}
