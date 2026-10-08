package com.team.blog.interaction.domain;

import java.time.Instant;

/**
 * 댓글 표시 상태 (007 data-model §3, FR-019·FR-020, research R10). 저장하지 않고 행 값에서 판정한다.
 *
 * <p>우선순위: 작성자 탈퇴(유예·익명 처리) &gt; 삭제된 자리 &gt; 관리자 숨김 &gt; 정상. 탈퇴·삭제와 남이 보는 숨김은 응답에 작성자·내용을 아예 담지 않는다
 * — 숨김은 작성자 본인만 원문을 받는다(글 주인·관리자도 못 봄, SC-007).
 */
public enum CommentState {
    NORMAL,
    DELETED,
    HIDDEN,
    WITHDRAWN_AUTHOR;

    /**
     * @param authorWithdrawn 작성자가 탈퇴 유예({@code status = WITHDRAWN}) 또는 익명 처리({@code deleted_at})
     * @param deletedAt 삭제된 자리 시각
     * @param hiddenAt 관리자 숨김 시각
     */
    public static CommentState of(boolean authorWithdrawn, Instant deletedAt, Instant hiddenAt) {
        if (authorWithdrawn) {
            return WITHDRAWN_AUTHOR;
        }
        if (deletedAt != null) {
            return DELETED;
        }
        if (hiddenAt != null) {
            return HIDDEN;
        }
        return NORMAL;
    }

    /** 이 상태에서 보는 사람에게 작성자·내용을 보여 주는가. 숨김은 작성자 본인만. */
    public boolean reveals(boolean viewerIsCommentAuthor) {
        return switch (this) {
            case NORMAL -> true;
            case HIDDEN -> viewerIsCommentAuthor;
            case DELETED, WITHDRAWN_AUTHOR -> false;
        };
    }

    /** 답글을 달 수 있는 대상인가 (FR-010 — 정상만). */
    public boolean acceptsReply() {
        return this == NORMAL;
    }

    /** "수정됨" — 내용을 고칠 때만 {@code updated_at}이 바뀐다(FR-028). */
    public static boolean edited(Instant createdAt, Instant updatedAt) {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }
}
