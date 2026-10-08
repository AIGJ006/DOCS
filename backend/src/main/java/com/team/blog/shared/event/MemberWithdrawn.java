package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 회원이 탈퇴를 신청함 — 유예 시작 (015 data-model §5, 20 §3-6). 신청 트랜잭션 안에서(세션 삭제 성공 뒤) 발행하고 커밋 뒤 전달된다. 구독:
 * account 접수 메일. 012는 구독하지 않는다(요청 때 공용 조건으로 거름, 012 research R14). 30일 정리·영구 정지 자동 정리는 이 이벤트를 내지
 * 않는다.
 *
 * @param withdrawnAt 신청 시각 ({@code member.withdrawn_at}). 복구 기한 = 이 값 + {@code
 *     blog.withdraw.grace-period}
 */
public record MemberWithdrawn(long memberId, Instant withdrawnAt) implements DomainEvent {}
