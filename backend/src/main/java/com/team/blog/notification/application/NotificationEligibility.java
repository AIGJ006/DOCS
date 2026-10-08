package com.team.blog.notification.application;

import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.notification.domain.NotificationType;
import com.team.blog.notification.infra.NotificationMuteRepository;
import com.team.blog.post.application.PostReadService;
import com.team.blog.shared.security.Viewer;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 공통 제외 규칙 (011 contracts §2 ①~⑤, research R4, FR-011). 저장 전에 순서대로 확인하고 하나라도 걸리면 저장하지 않는다(정상 경로라 로그
 * 없음).
 *
 * <ol>
 *   <li>받는 사람 = 행동한 사람
 *   <li>받는 사람 탈퇴 유예(또는 없음·익명 처리) — {@link MemberQueryService#findAccessInfo}
 *   <li>행동한 사람 탈퇴 유예(행동자가 있을 때)
 *   <li>받는 사람이 그 종류를 끔 — 운영 알림은 보지 않는다
 *   <li>받는 사람이 그 글을 읽을 수 없음 — {@code COMMENT}·{@code REPLY}·{@code LIKE}만. {@code NEW_POST}는 받는 사람이
 *       많아 비회원 기준 판정 한 번으로 대신한다(R8)
 * </ol>
 *
 * 정지 회원({@code SUSPENDED})은 막지 않는다(spec Assumptions, 42 P-7).
 */
@Component
public class NotificationEligibility {

    /** 걸린 규칙 (테스트·디버그용). */
    public enum Rejection {
        SELF,
        RECEIVER_UNAVAILABLE,
        ACTOR_UNAVAILABLE,
        MUTED,
        POST_UNREADABLE
    }

    private final MemberQueryService members;
    private final NotificationMuteRepository mutes;
    private final PostReadService posts;

    public NotificationEligibility(
            MemberQueryService members, NotificationMuteRepository mutes, PostReadService posts) {
        this.members = members;
        this.mutes = mutes;
        this.posts = posts;
    }

    /** 저장해도 되면 {@code true}. */
    public boolean allows(NotificationType type, long receiverId, Long actorId, Long postId) {
        return check(type, receiverId, actorId, postId).isEmpty();
    }

    /**
     * @param actorId 행동한 사람 (운영 알림은 {@code null})
     * @param postId 관련 글 (없으면 {@code null})
     * @return 걸린 규칙. 통과면 빈 값
     */
    public Optional<Rejection> check(
            NotificationType type, long receiverId, Long actorId, Long postId) {
        if (actorId != null && actorId == receiverId) {
            return Optional.of(Rejection.SELF);
        }
        Optional<MemberAccessInfo> receiver = members.findAccessInfo(receiverId);
        if (receiver.isEmpty() || receiver.get().status() == MemberStatus.WITHDRAWN) {
            return Optional.of(Rejection.RECEIVER_UNAVAILABLE);
        }
        if (actorId != null) {
            Optional<MemberAccessInfo> actor = members.findAccessInfo(actorId);
            if (actor.isEmpty() || actor.get().status() == MemberStatus.WITHDRAWN) {
                return Optional.of(Rejection.ACTOR_UNAVAILABLE);
            }
        }
        if (!type.isOperational() && mutes.isMuted(receiverId, type.mutable())) {
            return Optional.of(Rejection.MUTED);
        }
        if (postId != null && type.needsReadablePost() && type != NotificationType.NEW_POST) {
            MemberAccessInfo info = receiver.get();
            Viewer viewer =
                    new Viewer(receiverId, info.role(), info.status(), info.emailVerified());
            if (!posts.isReadable(postId, viewer)) {
                return Optional.of(Rejection.POST_UNREADABLE);
            }
        }
        return Optional.empty();
    }
}
