package com.team.blog.shared.event;

/**
 * 도메인 이벤트 마커 (20 EV-1~EV-7).
 *
 * <p><b>규칙</b>
 *
 * <ul>
 *   <li>이벤트는 record로 만들고 필드는 ID·enum·{@link java.time.Instant}만 둔다. 엔티티·본문·비밀번호·토큰·이메일은 싣지 않는다
 *       (FR-015).
 *   <li>발행은 업무 트랜잭션 안에서 {@code ApplicationEventPublisher.publishEvent}로 한다.
 *   <li>구독은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} +
 *       {@code @Async("eventExecutor")}로 커밋 뒤 비동기 처리한다. 롤백된 업무의 이벤트는 전달되지 않는다.
 *   <li>유실을 허용한다(서버 종료·대기열 초과 시 버려질 수 있음). 이벤트가 없어도 핵심 기능이 맞게 동작해야 한다(constitution V). 유실 없는 처리가
 *       필요하면 개인 확장으로 메시지 브로커를 쓴다(02 §2).
 *   <li>구독자 실패는 발행자에게 전파되지 않는다(경고 로그).
 * </ul>
 */
public interface DomainEvent {}
