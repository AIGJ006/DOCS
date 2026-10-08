package com.team.blog.interaction.application;

import java.util.List;

/**
 * 댓글 목록 한 페이지 (contracts {@code CommentPage}).
 *
 * @param prevCursor 바로 가기·이전 방향일 때 앞에 더 있으면
 * @param focusCommentId 바로 가기 대상을 펼쳤으면 그 번호 (무시했으면 {@code null})
 */
public record CommentPage(
        List<RootCommentView> items, String nextCursor, String prevCursor, Long focusCommentId) {}
