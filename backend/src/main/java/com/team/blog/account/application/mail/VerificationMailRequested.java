package com.team.blog.account.application.mail;

/**
 * 인증 메일 발송 요청 (contracts/events.md §2 — 모듈 내부, 공유 이벤트 아님). 가입·재발송 트랜잭션 커밋 후 {@link
 * AccountMailService}가 비동기로 처리한다. 토큰은 발송 시점에 만들므로 이벤트에 싣지 않는다(FR-015).
 */
public record VerificationMailRequested(long memberId) {}
