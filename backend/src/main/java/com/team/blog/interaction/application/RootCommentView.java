package com.team.blog.interaction.application;

import com.team.blog.interaction.domain.CommentState;
import java.time.Instant;
import java.util.List;

/**
 * 최상위 댓글 (contracts {@code RootComment}) — {@link CommentView}에 답글 수·처음 답글·답글 커서를 더한다.
 *
 * @param replyCount 그 아래 답글 수 (숨긴 답글 포함 — [답글 N개 더 보기]의 N = replyCount − replies 수)
 * @param replies 처음 답글 (기본 3개, 바로 가기면 대상까지)
 * @param repliesNextCursor 더 펼칠 답글이 있으면 그 커서
 */
public record RootCommentView(
        long id,
        CommentState state,
        String content,
        Instant createdAt,
        boolean edited,
        CommentView.Author author,
        CommentView.ReplyTo replyTo,
        boolean mine,
        Long parentId,
        int replyCount,
        List<CommentView> replies,
        String repliesNextCursor) {

    public static RootCommentView of(
            CommentView view, int replyCount, List<CommentView> replies, String nextCursor) {
        return new RootCommentView(
                view.id(),
                view.state(),
                view.content(),
                view.createdAt(),
                view.edited(),
                view.author(),
                view.replyTo(),
                view.mine(),
                view.parentId(),
                replyCount,
                List.copyOf(replies),
                nextCursor);
    }
}
