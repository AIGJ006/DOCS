package com.team.blog.notification.application;

import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.notification.application.NotificationItem.Actor;
import com.team.blog.notification.application.NotificationItem.ActorMember;
import com.team.blog.notification.application.NotificationItem.ActorWithdrawn;
import com.team.blog.notification.application.NotificationItem.Comment;
import com.team.blog.notification.application.NotificationItem.Hidden;
import com.team.blog.notification.application.NotificationItem.Post;
import com.team.blog.notification.application.NotificationItem.PostReadable;
import com.team.blog.notification.application.NotificationItem.PostUnavailable;
import com.team.blog.notification.application.NotificationItem.Report;
import com.team.blog.notification.domain.NotificationType;
import com.team.blog.notification.infra.NotificationRow;
import com.team.blog.post.domain.PostAccessPolicy;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/**
 * 목록 행 → 응답 항목 (011 research R10, FR-019~FR-024). 알림은 보여 줄 때 다시 판단한다: 읽기 판정은 행으로 {@link PostView}를
 * 만들어 004 {@link PostAccessPolicy#canRead}를 메모리에서 부른다(추가 SQL 없음).
 *
 * <ul>
 *   <li>행동자: 탈퇴 유예({@code withdrawn_at})·익명 처리({@code deleted_at})면 {@code {withdrawn: true}}. 운영
 *       알림은 {@code null}.
 *   <li>글: 읽을 수 있으면 지금 제목과 주소, 아니면 {@code {unavailable: true}}·미리보기 없음·{@code url null}. 댓글 숨김 알림은
 *       {@code post null}(제목을 보여 주지 않음, FR-020).
 *   <li>댓글 미리보기: 공백을 한 칸으로 줄이고 앞 {@code preview-length} 코드 포인트 + 넘으면 "…". 서식으로 해석하지 않는다.
 *   <li>이동 주소(FR-024): 댓글·답글·댓글 숨김 {@code /@{h}/posts/{p}?comment={c}#comment-{c}}, 좋아요·새 글·글 숨김
 *       {@code /@{h}/posts/{p}}, 팔로우 {@code /@{내 주소}/followers}, 신고 결과 {@code null}.
 * </ul>
 */
@Component
public class NotificationItemAssembler {

    private final PostAccessPolicy accessPolicy;
    private final ImageUrlResolver imageUrls;
    private final NotificationProperties properties;

    public NotificationItemAssembler(
            PostAccessPolicy accessPolicy,
            ImageUrlResolver imageUrls,
            NotificationProperties properties) {
        this.accessPolicy = accessPolicy;
        this.imageUrls = imageUrls;
        this.properties = properties;
    }

    public NotificationItem assemble(NotificationRow row, Viewer viewer) {
        NotificationType type = NotificationType.valueOf(row.type());
        boolean readable = row.postId() != null && canRead(row, viewer);
        String postUrl = readable ? "/@" + row.postAuthorHandle() + "/posts/" + row.postId() : null;
        String commentUrl =
                readable && row.commentId() != null
                        ? postUrl + "?comment=" + row.commentId() + "#comment-" + row.commentId()
                        : null;

        Actor actor = type.isOperational() ? null : actor(row);
        int others = type.isGrouped() ? Math.max(row.actorCount() - 1, 0) : 0;
        Post post = null;
        Comment comment = null;
        NotificationItem.Report report = null;
        Hidden hidden = null;
        String url = null;

        switch (type) {
            case COMMENT, REPLY -> {
                post = postRef(row, readable, postUrl);
                if (readable && row.commentId() != null && row.commentHead() != null) {
                    comment = new Comment(row.commentId(), preview(row.commentHead()));
                }
                url = commentUrl;
            }
            case LIKE, NEW_POST -> {
                post = postRef(row, readable, postUrl);
                url = postUrl;
            }
            case FOLLOW -> url = "/@" + row.receiverHandle() + "/followers";
            case REPORT_RESOLVED -> report = new Report(row.result());
            case CONTENT_HIDDEN -> {
                if (row.commentId() != null) {
                    boolean still = row.commentHiddenAt() != null;
                    hidden = new Hidden("COMMENT", still, still ? row.commentHiddenReason() : null);
                    url = commentUrl;
                } else {
                    boolean still = row.postHiddenAt() != null;
                    hidden = new Hidden("POST", still, still ? row.postHiddenReason() : null);
                    post = row.postId() == null ? null : postRef(row, readable, postUrl);
                    url = postUrl;
                }
            }
        }
        return new NotificationItem(
                row.id(),
                type,
                row.readAt() != null,
                row.updatedAt(),
                actor,
                others,
                post,
                comment,
                report,
                hidden,
                url);
    }

    private boolean canRead(NotificationRow row, Viewer viewer) {
        Visibility visibility;
        PostStatus status;
        try {
            visibility = Visibility.valueOf(row.postVisibility());
            status = PostStatus.valueOf(row.postStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        PostView view =
                new PostView(
                        row.postId(),
                        row.postAuthorId(),
                        status,
                        visibility,
                        row.postDeletedAt(),
                        row.postHiddenAt(),
                        row.postAuthorWithdrawnAt());
        return accessPolicy.canRead(view, viewer);
    }

    private static Post postRef(NotificationRow row, boolean readable, String postUrl) {
        return readable ? new PostReadable(row.postTitle(), postUrl) : PostUnavailable.INSTANCE;
    }

    private Actor actor(NotificationRow row) {
        if (row.actorId() == null) {
            return null;
        }
        if (row.actorWithdrawnAt() != null
                || row.actorDeletedAt() != null
                || row.actorHandle() == null) {
            return ActorWithdrawn.INSTANCE;
        }
        return new ActorMember(
                row.actorHandle(), row.actorNickname(), imageUrls.publicUrl(row.actorProfileKey()));
    }

    /** 공백(줄바꿈 포함)을 한 칸으로 줄이고 앞 {@code preview-length} 코드 포인트, 넘으면 "…". */
    String preview(String head) {
        String collapsed = head.strip().replaceAll("\\s+", " ");
        int limit = properties.previewLength();
        if (collapsed.codePointCount(0, collapsed.length()) <= limit) {
            return collapsed;
        }
        int end = collapsed.offsetByCodePoints(0, limit);
        return collapsed.substring(0, end) + "…";
    }
}
