package com.team.blog.notification.infra;

import java.time.Instant;

/**
 * 목록 SQL 한 행 (011 research R10). 알림 + 행동자 + 프로필 사진 키 + 글(읽기 판정용 상태) + 글 작성자 + 댓글 앞부분 + 받는 사람 주소.
 *
 * @param actorProfileKey 행동자의 지금 프로필 사진 저장소 키 (없으면 {@code null})
 * @param commentHead 댓글 내용 앞부분 ({@code preview-scan}자까지)
 */
public record NotificationRow(
        long id,
        String type,
        Long postId,
        Long commentId,
        String result,
        int actorCount,
        Instant readAt,
        Instant updatedAt,
        Long actorId,
        String actorHandle,
        String actorNickname,
        Instant actorWithdrawnAt,
        Instant actorDeletedAt,
        String actorProfileKey,
        String postTitle,
        Long postAuthorId,
        String postStatus,
        String postVisibility,
        Instant postDeletedAt,
        Instant postHiddenAt,
        String postHiddenReason,
        String postAuthorHandle,
        Instant postAuthorWithdrawnAt,
        String commentHead,
        Instant commentDeletedAt,
        Instant commentHiddenAt,
        String commentHiddenReason,
        String receiverHandle) {}
