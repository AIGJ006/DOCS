package com.team.blog.post.web.dto;

/**
 * 새 글 요청 (contracts {@code CreatePostRequest}). 작성자 필드는 받지 않는다 — 보내도 무시된다(원칙 III).
 *
 * @param title 없으면 빈 제목
 * @param contentMd 없으면 빈 본문 (비교 창의 [새 임시글로 따로 저장], FR-024)
 */
public record CreatePostRequest(String title, String contentMd) {}
