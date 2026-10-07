/**
 * 팀 공통 블로그 플랫폼 루트 패키지 (package-by-feature, 02 §3).
 *
 * <p>모듈: account · post · tag · media · interaction · discovery · shared. 개인 확장은 새 모듈 패키지로 추가한다.
 *
 * <p><b>모듈 경계 규칙 (constitution II)</b>
 *
 * <ul>
 *   <li>모듈은 다른 모듈의 Repository·엔티티·테이블을 직접 쓰지 않는다. 다른 모듈의 {@code application} 패키지에 있는 공개 Service를
 *       호출하거나, {@code shared.event}의 도메인 이벤트로만 소통한다.
 *   <li>shared는 기능 모듈에 의존하지 않는다. 기능 모듈이 shared에 정의된 포트(예: {@code
 *       shared.security.AccountStatusGuard})를 구현한다.
 *   <li>권한·상태 전이·검증 같은 업무 규칙은 Service 계층에 둔다. 컨트롤러는 입력을 옮기기만 한다.
 *   <li>공통 ERD(Flyway V1 = docs/51)의 테이블·컬럼은 바꾸지 않는다. 개인 확장은 새 테이블·nullable 컬럼·새 모듈로 추가만 한다
 *       (constitution I).
 * </ul>
 */
package com.team.blog;
