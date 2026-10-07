# Events: 계정·인증 (001-account-auth)

**기준**: [docs/20-domain-events.md](../../../docs/20-domain-events.md) §1(EV-1~EV-7)·§2·§3-7, [06 §6-2](../../../docs/06-visibility.md), 2026-10-07 회의 M1.

## 1. 공유 도메인 이벤트 (`shared/event`)

전달 방식은 20 EV-1 그대로: 발행은 Service 계층에서 상태가 **실제로 바뀐 경우에만 한 번**(EV-4), 처리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`. 유실은 허용(EV-2), 순서는 보장하지 않는다. 필드는 ID·enum·`Instant`만(EV-3, 닉네임·이메일 같은 글자 없음). 이름은 `{대상}{과거분사}` record(EV-7).

| 이벤트 | 언제 (발행 조건) | 필드 | 발행 위치 | 예상 구독자 |
|---|---|---|---|---|
| `FriendRequested` | 친구 요청으로 `friendship` 행이 **새로 생겼을 때** (`INSERT … ON CONFLICT`의 결과가 새 행) | `requesterId: long`, `receiverId: long`, `requestedAt: Instant` | `account.application.FriendshipService.requestOrAccept` | 011 친구 알림(선택 기능, 기본 비활성) |
| `FriendAccepted` | `PENDING → ACCEPTED`로 바뀌었을 때. 받은 요청 [수락]과 **맞요청으로 바로 수락된 경우** 모두 | `requesterId: long`(처음 요청한 쪽 = `requested_by`), `accepterId: long`, `acceptedAt: Instant` | 같은 메서드 | 011 친구 알림(선택) |

발행하지 않는 경우:
- 이미 친구이거나 내가 보낸 요청이 남아 있어 아무것도 바뀌지 않음(EV-4).
- **거절·요청 취소·친구 끊기** — 상대에게 알리지 않는다는 06 §6-2 원칙을 이벤트 단계에서 지킨다(20 §3-7).
- 탈퇴로 친구 행이 지워짐(015의 정리 단계, 이벤트 아님).

```java
// shared/event
public record FriendRequested(long requesterId, long receiverId, Instant requestedAt) implements DomainEvent {}
public record FriendAccepted(long requesterId, long accepterId, Instant acceptedAt) implements DomainEvent {}
```

**20 §3-7과의 차이(spec Assumptions ③)**: 20은 이 두 이벤트를 "친구 공개 규격 적용자만"으로 적었지만, 2026-10-07 M1로 친구 맺기가 공통이 되었으므로 **모든 서비스가 발행**한다. 구독(친구 알림)만 선택이다. 구독자가 없으면 아무 일도 일어나지 않는다.

이 기능은 다른 모듈의 이벤트를 구독하지 않는다. `MemberWithdrawn`·`MemberRestored`(20 §3-6)는 015가 발행한다.

## 2. 모듈 내부 커밋 후 처리 (공유 이벤트 아님)

메일 발송은 실패해도 원래 처리(가입·변경)가 성공해야 하므로(constitution V) 커밋 후 비동기로 한다. 공유 이벤트 목록(20)에 넣지 않고 account 모듈 안에서만 쓴다(제안, 팀 확인 필요).

| 계기 | 처리 | 담는 값 | 비고 |
|---|---|---|---|
| 이메일 가입 커밋, GitHub 직접 입력 이메일 소셜 가입 커밋, 재발송 요청 | 인증 토큰 생성(Redis) → 인증 메일 | `memberId` | 토큰은 발송 시점에 만들어 이벤트·로그에 싣지 않는다(FR-015). Redis 장애면 발송 실패 → 사용자는 재발송 |
| 비밀번호 찾기 요청 | 이메일로 `auth_identity` 조회(`ix_auth_identity_email`) → 재설정 링크 또는 소셜 안내 메일 | 정규화한 이메일(메모리 안에서만) | 요청 응답은 조회 전에 202로 돌려 가입 여부에 따른 응답 시간 차이를 없앤다(SC-004) |
| 비밀번호 변경 커밋 | "비밀번호가 변경됐어요. 본인이 아니라면 [비밀번호 재설정]" 알림 메일 | `memberId` | — |
| 인증된 요청 응답 후 | `last_active_at` 갱신(1시간에 1번, `SET NX`) | `memberId` | Redis·DB 실패는 경고 로그 후 무시(FR-059) |

## 3. 배치

이 기능에는 스케줄 배치가 없다. 기대는 다른 기능의 배치:

| 배치 | 소유 | 이 기능과의 관계 |
|---|---|---|
| `ImageCleanupJob`(TEMP 24시간, `detached_at` 7일 삭제) | 003(media) | 바꾼 이전 프로필 사진·저장하지 않은 업로드를 지운다. `ATTACHED` + `detached_at IS NULL` 프로필 사진은 지우지 않는다(FR-050) |
| 탈퇴 30일 익명 처리 | 015 | `nickname = NULL`, `last_active_at = NULL`, 친구 행 삭제. `member_agreement`·`member_suspension`은 남긴다 |

토큰·카운터 만료는 Redis TTL이 처리한다(배치 없음).
