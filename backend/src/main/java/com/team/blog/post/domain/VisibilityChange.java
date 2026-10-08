package com.team.blog.post.domain;

/**
 * {@link Post#changeVisibility} 결과 (004 T031, data-model §4-2).
 *
 * @param changed 값이 실제로 바뀌었나 (같은 값이면 {@code false} — 아무것도 바뀌지 않음)
 * @param wentPublic 이번 변경으로 {@code first_public_at}을 처음 채웠나 ({@code PostWentPublic} 발행 여부)
 * @param from 바꾸기 전 값
 */
public record VisibilityChange(boolean changed, boolean wentPublic, Visibility from) {}
