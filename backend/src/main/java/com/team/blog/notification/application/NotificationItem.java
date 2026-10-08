package com.team.blog.notification.application;

import com.team.blog.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;

/**
 * 알림 목록 응답 항목 (011 data-model §5, openapi {@code NotificationItem}). 문장은 주지 않고 조각(닉네임·제목·미리보기·사유
 * 코드)과 이동 주소만 준다 — 문장은 화면이 조립한다(research R10·R16). 신고자·관리자 정보는 없다(FR-022).
 *
 * @param actor 운영 알림은 {@code null}
 * @param othersCount 묶음의 "외 N명" (하나짜리 0)
 * @param post 글이 없는 종류·댓글 숨김은 {@code null}
 * @param comment {@code COMMENT}·{@code REPLY}이고 글을 읽을 수 있을 때만
 * @param report {@code REPORT_RESOLVED}만
 * @param hidden {@code CONTENT_HIDDEN}만
 * @param url 누르면 갈 곳 (없으면 {@code null} — 누르면 읽음만)
 */
public record NotificationItem(
        long id,
        NotificationType type,
        boolean read,
        Instant updatedAt,
        Actor actor,
        int othersCount,
        Post post,
        Comment comment,
        Report report,
        Hidden hidden,
        String url) {

    /** 행동한 사람: {@link ActorMember} 또는 {@link ActorWithdrawn}. */
    public sealed interface Actor permits ActorMember, ActorWithdrawn {}

    public record ActorMember(String handle, String nickname, String profileImageUrl)
            implements Actor {}

    /** 탈퇴 유예·익명 처리된 사람 — JSON {@code {"withdrawn": true}}. */
    public record ActorWithdrawn(boolean withdrawn) implements Actor {
        public static final ActorWithdrawn INSTANCE = new ActorWithdrawn(true);
    }

    /** 글: {@link PostReadable} 또는 {@link PostUnavailable}. */
    public sealed interface Post permits PostReadable, PostUnavailable {}

    public record PostReadable(String title, String url) implements Post {}

    /** 받는 사람이 지금 읽을 수 없는 글 — JSON {@code {"unavailable": true}}. */
    public record PostUnavailable(boolean unavailable) implements Post {
        public static final PostUnavailable INSTANCE = new PostUnavailable(true);
    }

    public record Comment(long id, String preview) {}

    public record Report(String result) {}

    /**
     * @param reason 숨김 중일 때만 지금 사유 코드
     */
    public record Hidden(String targetType, boolean stillHidden, String reason) {}

    /** 한 페이지. */
    public record Page(List<NotificationItem> items, String nextCursor) {}

    public record UnreadCount(long count) {}

    public record ReadAllResult(int updated) {}
}
