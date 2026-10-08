package com.team.blog.post.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 휴지통 처리용 잠금 조회 결과 (006 T009, research R4). {@link Post} 엔티티는 {@code @SQLRestriction("deleted_at IS
 * NULL")} 때문에 휴지통 글을 읽을 수 없으므로 휴지통 이동·복구·완전 삭제는 이 값으로 판정한다. 본문({@code contentMd})은 빈 임시글 판정에만 쓴다.
 */
public record TrashablePost(
        long id,
        long authorId,
        PostStatus status,
        Visibility visibility,
        String title,
        String contentMd,
        Instant deletedAt,
        long editVersion) {

    /** 휴지통에 있는가 ({@code deleted_at IS NOT NULL}). */
    public boolean isTrashed() {
        return deletedAt != null;
    }

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    /** 제목·본문이 모두 빈 임시글인가 (002 {@link EmptyDraftPolicy}와 같은 판정, FR-020). */
    public boolean isEmptyDraft() {
        return isDraft() && EmptyDraftPolicy.isEmpty(title, contentMd);
    }

    /**
     * 완전 삭제 예정 시각 = {@code deletedAt + retention}.
     *
     * @throws IllegalStateException 휴지통 글이 아님
     */
    public Instant purgeAt(Duration retention) {
        if (deletedAt == null) {
            throw new IllegalStateException("휴지통 글이 아닙니다: " + id);
        }
        return deletedAt.plus(retention);
    }
}
