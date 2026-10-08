package com.team.blog.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.notification.application.NotificationEligibility;
import com.team.blog.notification.application.NotificationEligibility.Rejection;
import com.team.blog.notification.domain.MutableType;
import com.team.blog.notification.domain.NotificationType;
import com.team.blog.notification.infra.NotificationMuteRepository;
import com.team.blog.post.application.PostReadService;
import com.team.blog.shared.security.Viewer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 공통 제외 규칙 (011 T011, contracts §2) — 각 조건이 따로 막고, 순서대로 멈춘다. */
class NotificationEligibilityTest {

    private static final long RECEIVER = 1L;
    private static final long ACTOR = 2L;
    private static final long POST = 10L;

    private MemberQueryService members;
    private NotificationMuteRepository mutes;
    private PostReadService posts;
    private NotificationEligibility eligibility;

    @BeforeEach
    void setUp() {
        members = mock(MemberQueryService.class);
        mutes = mock(NotificationMuteRepository.class);
        posts = mock(PostReadService.class);
        eligibility = new NotificationEligibility(members, mutes, posts);
        when(members.findAccessInfo(RECEIVER)).thenReturn(active(Role.USER, false));
        when(members.findAccessInfo(ACTOR)).thenReturn(active(Role.USER, true));
        when(posts.isReadable(eq(POST), any())).thenReturn(true);
    }

    @Test
    void 모두_통과하면_저장한다() {
        assertThat(eligibility.check(NotificationType.COMMENT, RECEIVER, ACTOR, POST)).isEmpty();
        ArgumentCaptor<Viewer> viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(posts).isReadable(eq(POST), viewer.capture());
        // 받는 사람 기준 Viewer (②에서 읽은 값)
        assertThat(viewer.getValue())
                .isEqualTo(new Viewer(RECEIVER, Role.USER, MemberStatus.ACTIVE, false));
    }

    @Test
    void 본인_행동은_다른_것을_보지_않고_멈춘다() {
        assertThat(eligibility.check(NotificationType.LIKE, RECEIVER, RECEIVER, POST))
                .contains(Rejection.SELF);
        verifyNoInteractions(members, mutes, posts);
    }

    @Test
    void 받는_사람_유예_또는_없음() {
        when(members.findAccessInfo(RECEIVER))
                .thenReturn(
                        Optional.of(new MemberAccessInfo(Role.USER, MemberStatus.WITHDRAWN, true)));
        assertThat(eligibility.check(NotificationType.COMMENT, RECEIVER, ACTOR, POST))
                .contains(Rejection.RECEIVER_UNAVAILABLE);
        verify(members, never()).findAccessInfo(ACTOR);
        verifyNoInteractions(mutes, posts);

        when(members.findAccessInfo(RECEIVER)).thenReturn(Optional.empty());
        assertThat(eligibility.check(NotificationType.REPORT_RESOLVED, RECEIVER, null, null))
                .contains(Rejection.RECEIVER_UNAVAILABLE);
    }

    @Test
    void 행동자_유예() {
        when(members.findAccessInfo(ACTOR))
                .thenReturn(
                        Optional.of(new MemberAccessInfo(Role.USER, MemberStatus.WITHDRAWN, true)));
        assertThat(eligibility.check(NotificationType.FOLLOW, RECEIVER, ACTOR, null))
                .contains(Rejection.ACTOR_UNAVAILABLE);
        verifyNoInteractions(mutes, posts);
    }

    @Test
    void 정지_회원은_막지_않는다() {
        when(members.findAccessInfo(RECEIVER))
                .thenReturn(
                        Optional.of(new MemberAccessInfo(Role.USER, MemberStatus.SUSPENDED, true)));
        when(members.findAccessInfo(ACTOR))
                .thenReturn(
                        Optional.of(new MemberAccessInfo(Role.USER, MemberStatus.SUSPENDED, true)));
        assertThat(eligibility.check(NotificationType.FOLLOW, RECEIVER, ACTOR, null)).isEmpty();
    }

    @Test
    void 끈_종류() {
        when(mutes.isMuted(RECEIVER, MutableType.LIKE)).thenReturn(true);
        assertThat(eligibility.check(NotificationType.LIKE, RECEIVER, ACTOR, POST))
                .contains(Rejection.MUTED);
        verifyNoInteractions(posts);
    }

    @Test
    void 운영_알림은_끈_종류를_보지_않는다() {
        when(mutes.isMuted(anyLong(), any())).thenReturn(true);
        assertThat(eligibility.check(NotificationType.CONTENT_HIDDEN, RECEIVER, null, POST))
                .isEmpty();
        assertThat(eligibility.check(NotificationType.REPORT_RESOLVED, RECEIVER, null, null))
                .isEmpty();
        verifyNoInteractions(mutes, posts);
    }

    @Test
    void 글을_읽을_수_없음() {
        when(posts.isReadable(eq(POST), any())).thenReturn(false);
        assertThat(eligibility.check(NotificationType.REPLY, RECEIVER, ACTOR, POST))
                .contains(Rejection.POST_UNREADABLE);
    }

    @Test
    void 새_글은_사람마다_읽기_판정을_하지_않는다() {
        when(posts.isReadable(eq(POST), any())).thenReturn(false);
        assertThat(eligibility.check(NotificationType.NEW_POST, RECEIVER, ACTOR, POST)).isEmpty();
        verify(posts, never()).isReadable(anyLong(), any());
    }

    private static Optional<MemberAccessInfo> active(Role role, boolean verified) {
        return Optional.of(new MemberAccessInfo(role, MemberStatus.ACTIVE, verified));
    }
}
