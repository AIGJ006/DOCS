# Data Model: 회원 탈퇴·복구

**Feature**: 015-withdraw | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

새 테이블·컬럼·인덱스는 없다(V1 그대로, research R1). 이 문서는 이 기능이 쓰는 컬럼, 상태 전이, 응답·오류, 이벤트, 설정값을 정리한다.

## 1. 이 기능이 쓰는 엔티티

### 1-1. `member` — 회원 (account 소유)

| 컬럼 | 이 기능에서의 쓰임 |
|---|---|
| `status` | `ACTIVE` → `WITHDRAWN`(신청) → `ACTIVE`(복구). 영구 정지 자동 정리는 `SUSPENDED` → `WITHDRAWN` |
| `withdrawn_at` | 신청 시각. 복구 기한 = `withdrawn_at + grace-period`. 복구하면 NULL |
| `deleted_at` | 익명 처리 시각(order 90). 채워지면 되돌리지 않는다 |
| `handle` | 바꾸지 않는다(영구 예약, FR-030) |
| `nickname`, `bio`, `nickname_changed_at`, `last_active_at` | 유예 중 그대로, 익명 처리 때 NULL |
| `role` | `ADMIN`이면 신청 409, 자동 정리 대상 제외 |
| `updated_at` | 신청·복구·익명 처리 때 갱신 |
| `last_active_visible`, `default_visibility`, `created_at` | 그대로 둔다(개인 정보가 아닌 설정·가입일) |

제약(V1): `ck_member_withdrawn`, `ck_member_deleted`, `ck_member_nickname_null`, `uq_member_nickname`(NULL 무시), `uq_member_handle`. 인덱스: `ix_member_withdraw_purge (withdrawn_at) WHERE status = 'WITHDRAWN' AND deleted_at IS NULL`.

### 1-2. `auth_identity` — 로그인 수단 (account 소유)

- 신청: `provider`로 본인 확인 방법을 고른다(`LOCAL` → 비밀번호, 그 밖 → 확인 문구). `password_hash`와 비교. 회원당 한 행(`uq_auth_identity_member`)
- 메일: `email`이 있으면 접수·복구 메일
- 정리 order 50: 행 삭제(같은 이메일·소셜 계정이 풀림, D-11). 지우기 전에 `email`의 SHA-256을 Redis 정리용으로 읽어 둔다

### 1-3. `friendship` — 친구 관계 (account 소유)

- 정리 order 60: `DELETE FROM friendship WHERE member_a_id = :m OR member_b_id = :m`(요청 중·수락 모두, `requested_by`도 둘 중 하나라 함께 사라진다)

### 1-4. `member_agreement`, `member_suspension` — 남기는 기록

- 지우지 않는다(FR-028). 둘 다 account 소유(001 T106). `member_suspension`은 영구 정지 자동 정리 대상 조회에만 읽는다(research R11)

### 1-5. 다른 모듈 데이터 (각 모듈의 정리 단계로만)

| 데이터 | 단계 | 소유 기능 |
|---|---|---|
| `post`(휴지통 포함)와 CASCADE되는 `comment`·`post_like`·`post_tag`·`post_image`·`post_draft`·`post_view_daily`·`notification` | 10 | 006 |
| 남의 글의 내 `comment`, `post.comment_count` | 20 | 007 |
| 내 `post_like`, `post.like_count` | 30 | 009 |
| 내 `image`(`detached_at` 표시 → 사진 정리 배치가 파일·행 삭제) | 40 | 003 |
| `follow` 양방향 | 65 | 010 |
| `notification`·`notification_actor`·`notification_mute` | 70 | 011 |
| `report_case`(내 콘텐츠 대기 사건), `report.detail`(내가 쓴 신고) | 80 | 014 |

## 2. 상태 전이

```text
            신청 (POST /api/me/withdraw)
  ACTIVE ───────────────────────────────▶ WITHDRAWN (withdrawn_at = t)
    ▲                                        │
    │ 복구 (POST /api/me/restore,             │ now > t + 30일 → 복구 거부 (409 RESTORE_PERIOD_EXPIRED)
    │       now ≤ t + 30일)                    │ withdrawn_at < now - 30일 → 정리 작업
    └────────────────────────────────────────┤
                                             ▼
                                   WITHDRAWN + deleted_at (익명 처리, 되돌릴 수 없음)
                                             ▲
  SUSPENDED (열린 영구 정지, started_at < now - 365일)
    └── 정리 작업이 같은 트랜잭션에서 WITHDRAWN(withdrawn_at = now)으로 바꾼 뒤 정리
```

| 전이 | 조건 | 쓰는 SQL | 이벤트 |
|---|---|---|---|
| 신청 | `status = ACTIVE`, 관리자 아님, 확인·본인 확인 통과 | `UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now WHERE id = :m AND status = 'ACTIVE'` | `MemberWithdrawn` |
| 복구 | `status = WITHDRAWN`, `deleted_at IS NULL`, `now ≤ withdrawn_at + grace` | `UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL, updated_at = :now WHERE id = :m AND status = 'WITHDRAWN' AND deleted_at IS NULL` | `MemberRestored` |
| 익명 처리 | `status = WITHDRAWN`, `deleted_at IS NULL`, `withdrawn_at < now - grace` | order 10~80 뒤 `UPDATE member SET nickname = NULL, bio = NULL, nickname_changed_at = NULL, last_active_at = NULL, deleted_at = :now, updated_at = :now WHERE id = :m AND status = 'WITHDRAWN' AND deleted_at IS NULL` (1행이 아니면 예외 → 롤백) | 없음 |
| 영구 정지 자동 | `status = SUSPENDED`, `role = USER`, 열린 정지 `ends_at IS NULL AND started_at < now - 365일` | `UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now WHERE id = :m AND status = 'SUSPENDED'` → 익명 처리와 같은 단계 | 없음 |

경계: `t + 30일` 정각은 복구 가능이고 정리 대상이 아니다(research R5).

## 3. 응답·요청

### 3-1. `WithdrawalPreview` — `GET /api/me/withdrawal`

| 필드 | 타입 | 설명 |
|---|---|---|
| `handle` | string | 블로그 주소 |
| `postCount` | integer | 내 글 전부(임시·발행·휴지통·숨김) |
| `commentCount` | integer | 남의 글에 쓴 지워지지 않은 댓글 |
| `receivedLikeCount` | integer | 내 글 `like_count` 합 |
| `restoreDeadline` | string(date-time) | 지금 신청하면의 복구 기한 |
| `verification` | `PASSWORD` \| `CONFIRM_TEXT` | 본인 확인 방법 |

### 3-2. `WithdrawRequest` — `POST /api/me/withdraw`

| 필드 | 타입 | 규칙 |
|---|---|---|
| `confirmed` | boolean | `true`가 아니면 400 `WITHDRAW_CONFIRM_REQUIRED` |
| `password` | string? | `verification = PASSWORD`일 때. 로그·예외에 남기지 않음(001 마스킹 대상) |
| `confirmText` | string? | `verification = CONFIRM_TEXT`일 때. 앞뒤 공백 제거 후 `blog.withdraw.confirm-text`와 같아야 함 |

응답 200 `WithdrawResult {restoreDeadline}`.

### 3-3. `RestoreResult` — `POST /api/me/restore`

200 `{status: "ACTIVE"}`.

### 3-4. `MeSummary` 확장 — `GET /api/me` (001)

| 추가 필드 | 타입 | 값 |
|---|---|---|
| `restoreDeadline` | string(date-time)? | `status = WITHDRAWN`일 때 `withdrawn_at + grace`, 아니면 null |
| `restoreExpired` | boolean | `status = WITHDRAWN`이고 `now > restoreDeadline`이면 true |

## 4. 이유 코드 (`AccountReasonCode`에 추가)

문구는 끝에 마침표를 붙이지 않는다(README 2026-10-07).

| 코드 | 상태 | 문구 | 근거 |
|---|---|---|---|
| `ADMIN_CANNOT_WITHDRAW` | 409 | 관리자 권한을 해제한 뒤 탈퇴할 수 있어요 | W-6, spec Implementation Notes |
| `WITHDRAW_CONFIRM_REQUIRED` | 400 | 안내 내용을 확인하고 체크해 주세요 | FR-002 (제안 코드·문구) |
| `CONFIRM_TEXT_MISMATCH` | 400 | '탈퇴'를 정확히 입력해 주세요 | spec Implementation Notes (문구 제안) |
| `RESTORE_PERIOD_EXPIRED` | 409 | 복구 기한이 지났어요 | FR-021a |
| `EMAIL_WITHDRAWAL_PENDING` | 400 (칸 오류, 응답은 `VALIDATION_FAILED`) | 탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요 | FR-022 |

001 코드를 그대로 쓰는 것: `CURRENT_PASSWORD_MISMATCH`(400), `PASSWORD_CHANGE_TEMPORARILY_LOCKED`(429 + `Retry-After`), `LOGIN_REQUIRED`(401), `ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`(403), `TEMPORARILY_UNAVAILABLE`(503, 세션 저장소 장애).

`EMAIL_WITHDRAWAL_PENDING` 문구 안의 마침표는 두 문장 사이의 것이고 끝에는 없다(001 `EMAIL_ALREADY_REGISTERED`와 같은 방식).

## 5. 이벤트 (`shared.event`)

| 이벤트 | 필드 | 발행 | 구독 |
|---|---|---|---|
| `MemberWithdrawn` | `long memberId, Instant withdrawnAt` | 신청 트랜잭션 안, 커밋 후 전달 | account 메일 (012는 구독하지 않음 — 요청 때 공용 조건으로 거름, 012 research R14) |
| `MemberRestored` | `long memberId, Instant restoredAt` | 복구 트랜잭션 안 | account 메일 |

정리 작업·영구 정지 자동 정리는 이벤트를 내지 않는다. 글 완전 삭제의 `PostPurged`는 006 규칙.

## 6. 확장점 (`shared.application.withdraw`)

```java
public interface WithdrawalPurgeStep {
    /** 실행 순서 (10 단위, 새 단계는 사이 값). 같은 값이 둘이면 시작 실패. */
    int order();
    /** 호출한 쪽 트랜잭션(MANDATORY) 안에서 그 회원의 데이터를 정리한다. 외부 호출 금지. 예외를 던지면 그 회원 정리 전체가 롤백된다. */
    void purge(long memberId);
}
```

단계 표와 SQL은 [contracts/purge-steps.md](./contracts/purge-steps.md) §2.

## 7. 포트 (`account.application.port`)

| 포트 | 메서드 | 구현 |
|---|---|---|
| `AuthoredPostStats` | `PostStats statsOf(long memberId)` → `{postCount, receivedLikeCount}` (SQL 1번) | post `AuthoredPostStatsAdapter` |
| `AuthoredCommentStats` | `long countOnOthersPosts(long memberId)` | interaction `AuthoredCommentStatsAdapter` |

기본 구현은 두지 않는다(research R7).

## 8. Redis

| 키 | 쓰임 | TTL | 소유 |
|---|---|---|---|
| `auth:pw-change-fail:{memberId}` | 현재 비밀번호 연속 실패(비밀번호 변경과 탈퇴 합산) | 15분 | 001 |
| Spring Session 인덱스 | 신청 때 전부 삭제, 정리 후 다시 삭제 | 세션 | 001 |
| `blog.withdraw.purge.redis-key-templates` | 정리 커밋 후 삭제 | 각자 | 각 기능 |

## 9. 설정값 (`blog.withdraw.*`, `WithdrawalProperties`)

| 키 | 기본 | 설명 |
|---|---|---|
| `grace-period` | `30d` | 복구 기한·정리 경계 |
| `suspended-purge-after` | `365d` | 영구 정지 자동 정리 |
| `confirm-text` | `탈퇴` | 소셜 본인 확인 문구 |
| `purge.cron` | `0 0 3 * * *` | 정리 작업 시각(`blog.time-zone`) |
| `purge.batch-size` | `100` | 한 번에 처리할 최대 회원 수(탈퇴·영구 정지 각각) |
| `purge.required-orders` | `[10,20,30,40,50,60,65,70,80,90]` | 없으면 작업이 돌지 않는 단계 |
| `purge.redis-key-templates` | research R16 | 정리 후 지울 키 |
