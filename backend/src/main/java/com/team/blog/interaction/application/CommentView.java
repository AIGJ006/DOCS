package com.team.blog.interaction.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team.blog.interaction.domain.CommentState;
import java.time.Instant;

/**
 * 댓글 하나 (contracts {@code Comment}, data-model §4-1). 작성자·내용은 {@link
 * CommentState#reveals(boolean)}가 참일 때만 담는다 — 탈퇴·삭제와 남이 보는 숨김은 {@code null}(FR-020, SC-007).
 *
 * @param mine 보는 사람이 작성자인가 (비회원은 {@code false})
 * @param parentId 답글이면 최상위 번호
 */
public record CommentView(
        long id,
        CommentState state,
        String content,
        Instant createdAt,
        boolean edited,
        Author author,
        ReplyTo replyTo,
        boolean mine,
        Long parentId) {

    /**
     * 작성자 표시.
     *
     * @param profileImageUrl 없으면 {@code null} — 화면이 기본 아이콘
     * @param isPostAuthor 글 작성자가 쓴 댓글 ([작성자] 배지)
     */
    public record Author(
            String handle,
            String nickname,
            String profileImageUrl,
            @JsonProperty("isPostAuthor") boolean isPostAuthor) {}

    /** 답글 대상: {@code {handle, nickname}} 또는 대상이 탈퇴했으면 {@code {withdrawn: true}}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplyTo(String handle, String nickname, Boolean withdrawn) {

        public static ReplyTo of(String handle, String nickname) {
            return new ReplyTo(handle, nickname, null);
        }

        public static ReplyTo withdrawnMember() {
            return new ReplyTo(null, null, Boolean.TRUE);
        }
    }
}
