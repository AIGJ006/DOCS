package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 관리자가 회원을 정지함 (014 data-model §6, 20 §3-5). 세션은 정지 Service가 직접 지운다 — 이 이벤트에 기대지 않는다(FR-036). 알림
 * 없음, 지금 구독자 없음.
 *
 * @param until 정지 종료 시각 ({@code null} = 영구)
 */
public record MemberSuspended(long memberId, Instant until, Instant suspendedAt)
        implements DomainEvent {}
