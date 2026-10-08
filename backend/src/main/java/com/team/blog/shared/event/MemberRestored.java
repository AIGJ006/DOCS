package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 탈퇴 유예 회원이 [복구하기]로 돌아옴 (015 data-model §5). 복구 트랜잭션 안에서 발행하고 커밋 뒤 전달된다. 구독: account 복구 메일. 이미 활동
 * 중인 회원의 복구 요청(변화 없음)은 발행하지 않는다.
 */
public record MemberRestored(long memberId, Instant restoredAt) implements DomainEvent {}
