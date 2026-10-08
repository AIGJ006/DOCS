# Research: 회원 탈퇴·복구

**Feature**: 015-withdraw | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

각 항목은 Decision / Rationale / Alternatives considered 순서다. "(확정)"은 원문·Clarifications·이미 머지된 코드로 정해진 것, "(제안)"은 이 계획이 정한 것으로 팀 확인이 필요하다.

---

## R1. 스키마는 V1 그대로 (확정)

- **Decision**: 새 마이그레이션이 없다. 쓰는 것:
  - `member.status`(`ACTIVE`/`SUSPENDED`/`WITHDRAWN`), `withdrawn_at`(신청 시각), `deleted_at`(익명 처리 시각)
  - `ck_member_withdrawn`: `(status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)` — 신청·복구는 두 값을 한 문장에서 같이 바꾼다
  - `ck_member_deleted`: `deleted_at IS NULL OR status = 'WITHDRAWN'` — 영구 정지 자동 정리는 먼저 상태를 WITHDRAWN으로 바꿔야 한다(R11)
  - `ck_member_nickname_null`: `nickname IS NOT NULL OR deleted_at IS NOT NULL` — 닉네임 비우기와 `deleted_at` 기록은 같은 `UPDATE`에서 한다(order 90)
  - `uq_member_nickname`(`lower(nickname)`, NULL 무시) — 익명 처리 커밋 즉시 다른 사람이 그 닉네임을 쓸 수 있다(FR-031)
  - `uq_member_handle` — 주소는 지우지 않으므로 영구 예약(FR-030). 재가입 때 001 `HandleSuggester`가 `옛주소_2`를 제안(FR-033)
  - `ix_member_withdraw_purge (withdrawn_at) WHERE status = 'WITHDRAWN' AND deleted_at IS NULL` — 정리 대상 조회
  - `member_agreement`·`member_suspension`은 읽지도 지우지도 않는다(FR-028, 2026-10-07 E6)
- **Rationale**: spec Implementation Notes "44 ERD 변경 없음", 13 §4, 51. V1/V2는 고치지 않는다.
- **Alternatives considered**: 탈퇴 사유·신청 IP 기록 컬럼 — FR-004(사유 저장 안 함)와 개인 정보 최소화에 어긋난다.

## R2. 유예 중 차단·노출은 기존 장치를 쓴다 (확정)

- **Decision**:
  - 서버: 001 `WithdrawnAccountGateFilter`(T042a, 구현됨)가 허용 목록 4개(`POST /api/me/restore`, `POST /api/auth/logout`, `GET /api/me`, `GET /api/auth/csrf`) 밖의 `/api/**`를 403 `ACCOUNT_WITHDRAWN` `details{action: RESTORE}`로 막는다. 이 기능의 새 API 중 `POST /api/me/restore`만 허용 목록에 있고, `GET /api/me/withdrawal`·`POST /api/me/withdraw`는 유예 회원에게 403이다(이미 신청한 사람이 다시 신청할 일이 없음)
  - 노출: 004 `VisibilityFilter`·`PostAccessPolicy`의 `작성자 withdrawn_at IS NULL` 조건으로 블로그·글·목록·태그·검색이 가려진다(FR-010). 005 블로그 머리말·페이지 셸은 `MemberQueryService.findReadableBlogOwner`가 유예 회원을 없는 주소로 다룬다
  - 댓글 가림(FR-011)은 007, 팔로워·팔로잉 제외(FR-012)는 010, 알림 생성 제외(FR-014)는 011이 각자 구현한다. 이 기능은 그 결과를 통합 테스트(`WithdrawalGraceIT`)로 확인만 하고, 아직 머지 안 된 기능의 항목은 `@Disabled("0NN 머지 후")`로 둔다
  - 닉네임·주소 묶어 두기(FR-013)는 행을 바꾸지 않으므로 자동으로 된다
- **Rationale**: ANALYSIS-tier-a 팀 결정 8(허용 목록 4개 경로), 004 research R-23, 005 T002 구현 메모.
- **Alternatives considered**: 탈퇴 때 글·댓글에 표시를 써 두는 방식 — 복구 때 되돌릴 행이 많아지고 SC-002(100% 같음)를 지키기 어렵다.

## R3. 신청 API와 판정 순서 (확정 + 제안)

- **Decision**: `POST /api/me/withdraw`, 본문 `{confirmed: boolean, password?: string, confirmText?: string}`. 판정 순서:
  1. 비로그인 → 401 `LOGIN_REQUIRED`
  2. 유예 회원 → 403 `ACCOUNT_WITHDRAWN`(게이트 필터)
  3. `AccountStatusGuard.requireActive(me, ACCOUNT_WRITE)` → 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED`. 이메일 인증 전은 통과(42 §9)
  4. 관리자(`role = ADMIN`) → 409 `ADMIN_CANNOT_WITHDRAW` "관리자 권한을 해제한 뒤 탈퇴할 수 있어요"(W-6)
  5. `confirmed != true` → 400 `WITHDRAW_CONFIRM_REQUIRED` "안내 내용을 확인하고 체크해 주세요" (제안 — 원문 API에 체크 값이 없음, FR-002)
  6. 본인 확인 — 로그인 수단은 회원당 하나(`uq_auth_identity_member`)라 방법이 하나로 정해진다:
     - 이메일 가입(`LOCAL`): 잠금 중이면 429 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` + `Retry-After` → 비밀번호가 비었거나 틀리면 400 `CURRENT_PASSWORD_MISMATCH`(빈 값은 실패로 세지 않음, 틀리면 셈) → 맞으면 실패 기록 삭제
     - 소셜 가입(`GOOGLE`·`GITHUB`): `confirmText`의 앞뒤 공백을 뺀 값이 설정값 `blog.withdraw.confirm-text`("탈퇴")와 다르면 400 `CONFIRM_TEXT_MISMATCH` "'탈퇴'를 정확히 입력해 주세요"(제안 문구). 실패 횟수는 세지 않는다(추측할 비밀이 없음)
     - 방법에 맞지 않는 칸(이메일 가입의 `confirmText`, 소셜의 `password`)은 무시한다
  7. 통과 → R4 트랜잭션
- 잠금은 요청 제한이 아니라 비밀번호 추측 방지라서 비밀번호 비교 **전**에 본다(001 비밀번호 변경과 같음). README "429 판정은 맨 끝" 규칙은 요청 횟수 제한에 대한 것이다.
- 실패 기록은 001 T097의 `auth:pw-change-fail:{memberId}`(5번·15분, `blog.auth.password-change.*`)를 그대로 쓴다(Clarifications Q3). 두 화면이 같은 규칙을 쓰도록 잠금 확인·비교·기록을 `CurrentPasswordVerifier`로 묶고 001 `PasswordChangeService`도 이 클래스를 부르게 한다. 001 T097이 먼저 구현돼 코드가 Service 안에 있으면 이 기능이 꺼내 옮긴다. Redis 장애 때 실패 기록은 통과(세지 않음, 001 규칙)
- 잠금 응답 코드: 실패 기록이 하나이므로 코드도 001의 `PASSWORD_CHANGE_TEMPORARILY_LOCKED`(문구 "잠시 후 다시 시도해 주세요(약 15분)")를 그대로 쓴다. 이름에 "변경"이 들어가 어색하지만 새 코드를 만들면 같은 잠금에 코드가 둘이 된다 — 팀 확인(tasks T003)
- **Rationale**: 44 §2·W-2·W-6·W-8, 42 §3·§9, spec Implementation Notes의 오류 코드, Clarifications Q3.
- **Alternatives considered**: 체크 여부를 화면에서만 확인 — FR-002 "요청을 직접 보내도 서버가 거부"를 못 지킨다. 관리자 확인을 본인 확인 뒤에 — 관리자가 비밀번호 실패 횟수를 쓰게 되고 결과는 어차피 거부다.

## R4. 신청 트랜잭션과 세션 (확정 + 제안)

- **Decision**: `WithdrawalService.withdraw(me, request, currentSessionId)`:
  1. 트랜잭션 밖: R3 판정(본인 확인의 Redis 기록 포함)
  2. 트랜잭션(`@Transactional`): `MemberRepository.findByIdForUpdate(me)` → 상태가 아직 `ACTIVE`인지 다시 확인(동시에 두 번 누르면 두 번째는 403 `ACCOUNT_WITHDRAWN`) → `UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now WHERE id = :me AND status = 'ACTIVE'`(1행이 아니면 다시 판정) → `SessionTerminator.terminateAll(me, Optional.empty())` — Redis 장애면 `TemporarilyUnavailableException`(503)으로 트랜잭션 전체 롤백 → `MemberWithdrawn(me, now)` 발행
  3. 컨트롤러: 서비스가 성공하면 지금 요청의 `HttpSession`을 `invalidate()`하고 `SecurityContextHolder`를 비운다. Spring Session은 요청 끝에 세션을 다시 저장하므로 `deleteById`만으로는 지금 세션이 되살아날 수 있다 — 반드시 `invalidate()`
  4. 응답 200 `{restoreDeadline}`(= `withdrawn_at + grace-period`). 화면은 이 값으로 완료 화면을 그린다(이후에는 로그인 상태가 아님)
- 세션 삭제를 트랜잭션 안에서 하는 이유: "신청은 됐는데 다른 기기 세션이 살아 있다"(SC-001 위반)를 막는다. 반대로 Redis 삭제 뒤 DB 커밋이 실패하면 세션만 사라진 정상 계정이 되며, 다시 로그인하면 되므로 받아들인다.
- `last_active_at`·닉네임·소개는 유예 중 그대로 둔다(복구 때 100% 같아야 함, SC-002).
- **Rationale**: 13 §3-1 "즉시", 44 §2, 20 §3-6(커밋 후 `MemberWithdrawn`), 001 `SessionTerminator` 주석("호출한 트랜잭션도 되돌려야 한다").
- **Alternatives considered**: 커밋 후 세션 삭제 — Redis가 그 사이 실패하면 세션이 남는다. 이벤트 구독으로 세션 삭제 — 이벤트는 유실될 수 있다(EV-2).

## R5. 복구와 기한 경계 (확정)

- **Decision**: `POST /api/me/restore`(허용 목록):
  - 비로그인·익명 처리된 회원의 남은 세션 → 401 `LOGIN_REQUIRED`(001 T039·T042a: `deleted_at` 회원은 비로그인 취급)
  - 이미 `ACTIVE` → 200 `{status: "ACTIVE"}`, 변화·이벤트 없음(두 번 누름)
  - `SUSPENDED` → 403 `ACCOUNT_SUSPENDED`(유예 회원은 정지될 수 없으므로 실제로는 없음, 방어용)
  - `WITHDRAWN`: 트랜잭션에서 `findByIdForUpdate` → `now > withdrawn_at + grace-period`이면 409 `RESTORE_PERIOD_EXPIRED` "복구 기한이 지났어요"(FR-021a) → 아니면 `UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL, updated_at = :now` → `MemberRestored(me, now)` → 200 `{status: "ACTIVE"}`
- 경계: 정리 대상은 `withdrawn_at < now - grace-period`(엄격), 복구 가능은 `now ≤ withdrawn_at + grace-period`. 같은 시각 `t = withdrawn_at + 30일`에는 복구 가능·정리 대상 아님으로 겹치지도 비지도 않는다. 단위 테스트 `RestoreDeadlineTest`가 t-1ms, t, t+1ms를 확인한다
- 정리 작업도 회원 행을 `FOR UPDATE`로 잠그고 조건을 다시 확인하므로(R8) 복구와 정리가 동시에 오면 먼저 잠근 쪽만 반영된다. 정리가 먼저 커밋되면 복구 요청은 `deleted_at` 회원이라 401
- 복구 뒤 지금 세션은 그대로 쓴다(복구 화면에서 로그인한 세션). 다른 세션은 신청 때 이미 지워졌다
- **Rationale**: Clarifications Q2(기한 지나면 거부), 44 §3, W-9(로그인만으로 복구하지 않음).
- **Alternatives considered**: 정리 전까지 복구 허용(Q2 B안) — 기각됨.

## R6. `GET /api/me` 확장 (제안)

- **Decision**: 001 `MeSummary`에 두 칸을 더한다. `restoreDeadline`(유예 회원만 `withdrawn_at + grace-period`, 나머지 null), `restoreExpired`(유예 회원이고 기한이 지났으면 true). `status`는 이미 있다. 복구 화면은 `GET /api/me` 하나로 "{기한}까지 복구할 수 있어요 ({N}일 남음)" 또는 "복구 기한이 지났어요"를 고른다. 남은 날짜는 화면이 `blog.time-zone`(KST) 날짜 기준으로 올림 계산한다(`formatDeadline.ts`)
- 기존 칸 의미는 바꾸지 않는다(추가만). 001 contracts `MeSummary`에도 두 칸을 더한다
- **Rationale**: 허용 목록이 4개 경로로 고정이라(ANALYSIS-tier-a 팀 결정 8) 복구 화면 전용 API를 더하면 허용 목록을 넓혀야 한다.
- **Alternatives considered**: `GET /api/me/withdrawal-status` 신설 — 허용 목록 변경 필요. 로그인 응답에만 기한을 싣기 — 새로 고침하면 사라진다.

## R7. 안내 숫자 (확정 + 제안)

- **Decision**: `GET /api/me/withdrawal` → `{handle, postCount, commentCount, receivedLikeCount, restoreDeadline, verification}`. `verification`은 `PASSWORD`(LOCAL) 또는 `CONFIRM_TEXT`(소셜). `restoreDeadline`은 "지금 신청하면"의 기한(now + 30일). `Cache-Control: no-store`. 판정은 401 → 403(게이트, `ACCOUNT_WRITE` 가드)이고 관리자도 숫자는 볼 수 있다(신청 때 409)
  - `postCount`: 내 글 전부(임시·발행·휴지통, 숨김 포함) — post 모듈 `AuthoredPostStats.countAllIncludingTrashed(memberId)` = `SELECT count(*) FROM post WHERE author_id = :m`
  - `receivedLikeCount`: 내 글의 `like_count` 합 — 같은 포트 `sumLikeCount(memberId)`(위와 한 SQL)
  - `commentCount`: 남의 글에 쓴 내 댓글 중 지워지지 않은 것 — interaction 모듈 `AuthoredCommentStats.countOnOthersPosts(memberId)` = `SELECT count(*) FROM comment c JOIN post p ON p.id = c.post_id WHERE c.author_id = :m AND p.author_id <> :m AND c.deleted_at IS NULL` (`ix_comment_author`)
  - 포트는 account가 정의(`account.application.port`)하고 post·interaction이 구현한다. 005의 `PostReadingPorts`처럼 기본 구현을 두지 않는다 — 틀린 숫자(0)를 보여 주느니 시작할 때 Bean이 없어서 실패하는 편이 낫다. 007이 아직 없으면 `AuthoredCommentStatsAdapter`도 만들 수 없으므로 이 API는 007 머지 후 작업이다
- **Rationale**: FR-003, 44 §2 화면, 헌법 II(account가 post·comment 테이블을 직접 읽지 않음).
- **Alternatives considered**: account가 JOIN으로 직접 세기 — 모듈 경계 위반. 신청 응답에 숫자 넣기 — 화면을 열 때 보여 줘야 한다.

## R8. 정리 작업 흐름 (확정 + 제안)

- **Decision**:
  - `WithdrawPurgeJob`(`@Scheduled(cron = "${blog.withdraw.purge.cron}", zone = "${blog.time-zone}")`, 기본 `0 0 3 * * *`, `@SchedulerLock(name = "withdrawPurge", lockAtMostFor = "PT1H")`):
    1. 필수 단계 확인(R9). 빠졌으면 ERROR 로그 후 종료
    2. 대상 조회: `SELECT id FROM member WHERE status = 'WITHDRAWN' AND deleted_at IS NULL AND withdrawn_at < :now - :grace ORDER BY withdrawn_at, id LIMIT :batchSize`(`ix_member_withdraw_purge`) + 영구 정지 대상(R11)
    3. 회원마다 `WithdrawalPurgeRunner.purgeOne(memberId, reason)` 호출, 예외는 잡아 `ERROR 탈퇴 정리 실패 memberId={} step={}`만 남기고 다음 회원으로. 실패한 회원은 조건이 그대로라 다음 날 다시 대상이 된다(FR-024)
    4. 처리 수·실패 수 INFO 로그. 대상이 `batchSize`보다 많으면 남은 회원은 다음 날(하루 탈퇴가 수 명이라 충분, 설정값으로 늘림)
  - `WithdrawalPurgeRunner.purgeOne`(`@Transactional(propagation = REQUIRES_NEW)`):
    1. `SELECT … FROM member WHERE id = :m FOR UPDATE`로 잠그고 대상 조건을 다시 확인(그 사이 복구했으면 아무것도 안 하고 끝)
    2. 영구 정지 대상이면 상태 전이(R11)
    3. 커밋 후 Redis 정리에 쓸 값(로그인 이메일의 SHA-256)을 미리 읽는다 — order 50이 `auth_identity`를 지우기 때문
    4. 단계를 `order()` 오름차순으로 `purge(memberId)`. 단계 예외는 `WithdrawalPurgeStepException(order, 단계 이름)`으로 감싸 다시 던진다 → 트랜잭션 전체 롤백
    5. `TransactionSynchronization.afterCommit`에 `WithdrawalRedisCleaner.clean(memberId, emailHash)` 등록(R10)
  - 정리는 이벤트를 만들지 않는다. 글 완전 삭제의 `PostPurged`(글마다)는 006 규칙대로 같은 트랜잭션에서 발행되고 커밋 후 구독자가 받는다
- 단계 표(contracts/purge-steps.md §2):

  | order | 클래스 (모듈) | 부르는 것 | 만드는 곳 |
  |---|---|---|---|
  | 10 | `PostWithdrawalPurgeStep` (post) | 006 `PostPurgeService.purgeAllByAuthor` | 015 tasks |
  | 20 | `CommentWithdrawalPurgeStep` (interaction) | 007 `CommentPurgeService.purgeByAuthor` | 015 tasks |
  | 30 | `LikeWithdrawalPurgeStep` (interaction) | 009 `LikePurgeService.purgeByMember` | 015 tasks |
  | 40 | `ImageWithdrawalPurgeStep` (media) | 003 `ImagePurgeService.detachAllByUploader` | 015 tasks |
  | 50 | `AuthIdentityWithdrawalPurgeStep` (account) | `DELETE FROM auth_identity WHERE member_id = :m` | 015 tasks |
  | 60 | `FriendshipWithdrawalPurgeStep` (account) | `DELETE FROM friendship WHERE member_a_id = :m OR member_b_id = :m` | 015 tasks |
  | 65 | `FollowWithdrawalPurgeStep` (010 모듈) | 양방향 팔로우 삭제 | 010 tasks |
  | 70 | `NotificationWithdrawalPurgeStep` (011 모듈) | 받은 알림 삭제, 묶음에서 빼고 재계산, 내가 행동한 하나짜리 삭제, 알림 끄기 설정 삭제 | 011 tasks |
  | 80 | `ReportWithdrawalPurgeStep` (moderation) | 내 콘텐츠 대기 사건 `CLOSED_NO_TARGET`, 내가 쓴 신고 설명 NULL | 014 tasks |
  | 90 | `MemberWithdrawalPurgeStep` (account) | 회원 익명화(R1) | 015 tasks |

- 순서 근거: 10이 먼저면 내 글에 달린 남의 댓글·좋아요·알림이 CASCADE로 사라져 뒤 단계 데이터가 준다. 20·30은 남의 글에 남긴 것과 카운터를 맞춘다. 40은 글 삭제(10)가 사진을 이미 일부 표시한 뒤 나머지(프로필·TEMP)를 표시한다. 70은 글·댓글 단계 다음(25 §8). 90은 앞 단계가 회원 행을 참조하므로 마지막
- **Rationale**: 44 §4, 13 §3-3, 20 §3-6, FR-023~FR-029.
- **Alternatives considered**: 모든 대상을 한 트랜잭션 — 한 명 실패가 모두를 되돌린다. 이벤트로 단계 나누기 — 일부만 성공할 수 있다(20 §3-6이 기각).

## R9. 필수 단계 확인 (제안)

- **Decision**: 설정값 `blog.withdraw.purge.required-orders`(기본 `[10, 20, 30, 40, 50, 60, 65, 70, 80, 90]`). 작업 시작 때 등록된 `WithdrawalPurgeStep` Bean의 `order()` 집합이 이것을 모두 포함하지 않으면 아무 회원도 처리하지 않고 `ERROR 탈퇴 정리 단계 누락 orders=[65, 70, 80]`을 남긴다. 같은 `order` 값이 둘이면 애플리케이션 시작을 실패시킨다(순서가 모호해짐, FR-029)
- 개인 확장 단계(사이 값, 예: 45)는 필수 목록에 없어도 실행된다. 필수 목록에 없는 기능을 운영에서 빼려면 설정값을 바꾼다(팀 결정)
- **Rationale**: 010·011·014는 Tier C라 015보다 늦게 머지될 수 있다. 일부 단계만으로 익명 처리하면 팔로우·알림·신고 설명 같은 개인 데이터가 남은 채 `deleted_at`이 찍혀 다시 정리할 방법이 없다(대상 조건이 `deleted_at IS NULL`).
- **Alternatives considered**: 빠진 단계는 건너뛰고 진행 — 위 문제. 필수 목록을 코드 상수로 — 개인 확장·배포 단계 조정이 어렵다(헌법 VII).

## R10. 정리 후 Redis 키 (제안)

- **Decision**: 커밋 뒤 `WithdrawalRedisCleaner.clean(memberId, emailHash)`가 지운다(실패는 WARN만 — 키는 모두 TTL이 있다):
  - 세션: `SessionTerminator.terminateAll(memberId, empty)`(익명 처리된 회원 세션은 이미 비로그인 취급이지만 남기지 않는다). 503 예외는 잡아 WARN
  - 토큰: `AuthTokenStore.revokeLatest(VERIFY, memberId)`·`revokeLatest(RESET, memberId)` — 최신 포인터 `auth:{verify|reset}-latest:{memberId}`가 가리키는 토큰 키와 포인터를 지운다(001 `AuthTokenStore`에 메서드 추가)
  - 실패·요청 횟수: 설정값 `blog.withdraw.purge.redis-key-templates`의 `{memberId}`·`{emailHash}`를 채워 `DEL`. 기본: `auth:pw-change-fail:{memberId}`, `auth:login-fail:{emailHash}`, `rl:verify-resend:{memberId}`, `ratelimit:autosave:{memberId}`, `ratelimit:preview:{memberId}`, `ratelimit:like:{memberId}`, `ratelimit:comment:{memberId}`, `ratelimit:comment-edit:{memberId}`, `ratelimit:image:{memberId}`, `ratelimit:tag-suggest:{memberId}`, `ratelimit:follow:{memberId}`(010). 기능이 회원 번호 키를 더하면 이 목록에 한 줄을 더한다
  - `SCAN`은 쓰지 않는다(키 공간 전체를 훑음). 목록에 빠진 키도 TTL(최대 하루)로 사라진다
  - 자동 저장 키(`autosave:post:{id}`)는 006 `PostPurgeService`가 글마다 커밋 후 지운다. 조회 중복 키(`view:seen:*`)는 회원 키가 섞여 있지만 24시간 TTL이라 그대로 둔다(44 §4 "조회수 정리할 개인 데이터 없음")
- **Rationale**: 13 §3-3 8번, FR-025 11번. 키 이름은 001 research(`rl:`·`auth:`)와 Tier B 기능(`ratelimit:`) 규칙을 그대로 옮겼다 — 두 접두어가 섞여 있는 문제는 ANALYSIS-tier-bc에 적는다.
- **Alternatives considered**: 트랜잭션 안에서 삭제 — 롤백되면 되살릴 수 없다.

## R11. 영구 정지 1년 자동 정리 (확정 + 제안)

- **Decision**: 정리 작업의 대상에 다음을 더한다(Clarifications Q1 B안).
  ```sql
  SELECT m.id FROM member m
   WHERE m.status = 'SUSPENDED' AND m.deleted_at IS NULL AND m.role = 'USER'
     AND EXISTS (SELECT 1 FROM member_suspension s
                  WHERE s.member_id = m.id AND s.lifted_at IS NULL AND s.ends_at IS NULL
                    AND s.started_at < :now - :suspendedPurgeAfter)
   ORDER BY m.id LIMIT :batchSize
  ```
  `purgeOne`이 잠근 뒤 같은 조건을 다시 확인하고 `UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now WHERE id = :m AND status = 'SUSPENDED'`로 바꾼 다음(`ck_member_deleted`·`ck_member_withdrawn` 통과) 같은 단계를 실행한다. `member_suspension` 행은 그대로 둔다(열린 정지로 남지만 로그인 수단이 지워져 로그인할 수 없다). 이벤트·메일은 없다(사용자가 신청한 탈퇴가 아님)
- 관리자 역할(`ADMIN`)은 대상에서 뺀다(관리자 정지는 원문에 없음, 실수 방지). 대상이면 WARN만
- `member_suspension`은 account 모듈 소유다(001 T106 `MemberSuspension`·`MemberSuspensionRepository`·`SuspensionService`, 014는 이 Service로 정지를 건다). 그래서 대상 조회 SQL을 account 안에 둬도 모듈 경계 문제가 없다
- **Rationale**: FR-008, Clarifications Q1, 43 §6(H-8). 정지 이력은 남긴다(FR-028).
- **Alternatives considered**: 정지를 먼저 해제(`lifted_at`) — "해제된 정지"로 보여 이력이 왜곡된다. 별도 작업 — 단계·트랜잭션 규칙이 갈라진다.

## R12. 화면 (확정 + 제안)

- **Decision**:
  - `/settings/withdraw`(`WithdrawPage` + `WithdrawForm`): 001 `SettingsPage` 맨 아래 [회원 탈퇴] 링크(001 T122 자리). 진입 때 `GET /api/me/withdrawal`. 안내 5줄(FR-003, 숫자는 `toLocaleString('ko-KR')`, 기한은 `formatDeadline` "2026년 11월 7일 오후 3:20"), 체크박스 "위 내용을 확인했어요", 본인 확인 칸(`verification`에 따라 비밀번호 또는 "탈퇴" 입력), [탈퇴하기](빨간 버튼, `autoFocus` 없음, 폼 안 Enter 제출 막음 — FR-007), 체크·입력 전 비활성. 오류: `CURRENT_PASSWORD_MISMATCH`·`CONFIRM_TEXT_MISMATCH`는 칸 아래, 429는 "잠시 후 다시 시도해 주세요(약 15분)" + 버튼 비활성, `ADMIN_CANNOT_WITHDRAW`는 화면 위 안내. 사유 입력 칸 없음(FR-004)
  - 성공: 001 `logout.ts`에서 로그아웃 정리 ②만 꺼낸 `clearLocalAccountData(memberId)`(002 `clearMemberDrafts`)를 부르고 `SessionProvider`를 비로그인으로 새로 고친 뒤 `/withdrawn`으로 이동(`state.restoreDeadline`). 미전송 작업 전송(①)은 하지 않는다(어차피 가려지는 글)
  - `/withdrawn`(`WithdrawnPage`): "탈퇴 신청이 완료됐어요 / {기한}까지 로그인하면 복구할 수 있어요 / 그동안 블로그와 글은 다른 사람에게 보이지 않아요" + [홈으로]. `state`가 없으면(새로 고침) 기한 줄 없이 나머지만
  - `/account/restore`(`RestorePage`): `GET /api/me`의 `restoreExpired`로 두 상태 — 복구 가능: "탈퇴 신청한 계정이에요 / {기한}까지 복구할 수 있어요 ({N}일 남음) / 복구하면 블로그·글·댓글이 모두 원래대로 돌아와요" + [로그아웃]·[복구하기] / 기한 지남: "복구 기한이 지났어요" + [로그아웃]. [복구하기] 성공 → 세션 새로 고침 → `/`로 이동 + 토스트 "다시 오신 걸 환영해요". 409 `RESTORE_PERIOD_EXPIRED` → 기한 지남 상태로 바꿈
  - `RestoreGate`: `SessionProvider` 값이 `status = WITHDRAWN`이면 허용 경로(`/account/restore`, `/terms`, `/privacy`, `/forgot-password`, `/reset-password`) 밖은 `/account/restore`로 `replace` 이동(FR-017). 004 `useAuthGate`의 `ACCOUNT_WITHDRAWN` 처리 경로를 `/account/restore`로 맞춘다(ANALYSIS-tier-a R13 — 004 T051은 `/restore`로 적혀 있음)
  - 로그인 화면: 001 로그인 응답 `accountStatus: "WITHDRAWN"`이면 `redirectTo` 대신 `/account/restore`로 이동(001 T108이 응답 값만 정함)
  - 가입 화면: 이메일 칸 오류 `EMAIL_WITHDRAWAL_PENDING`이면 "탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요" + [로그인] 링크
- **Rationale**: 44 §1~§3, W-1~W-9, 07 §7(임시 글 삭제).
- **Alternatives considered**: 단계별 여러 화면 — 44 W-2(한 화면).

## R13. 같은 이메일 가입 (확정)

- **Decision**: 001 `SignupService.signupWithEmail`의 이메일 중복 확인에서 `LOCAL` 로그인 수단이 있고 그 회원이 `status = WITHDRAWN AND deleted_at IS NULL`이면 `EMAIL_ALREADY_REGISTERED` 대신 칸 오류 `EMAIL_WITHDRAWAL_PENDING`을 준다(FR-022, 회의 P2 A안 — 이메일로 탈퇴 신청 여부가 드러나는 것은 감수). 소셜 로그인은 로그인 수단이 있으면 그 계정으로 로그인되어 복구 화면이 나오므로 따로 할 일이 없다. 001 FR-033(소셜 가입 때 같은 이메일 이메일 계정 안내)은 그대로 둔다
- 익명 처리 뒤에는 `auth_identity`가 지워져 같은 이메일·소셜 계정으로 새 계정이 만들어진다(FR-033). 옛 주소는 `uq_member_handle`로 막혀 001 `HandleSuggester`가 `옛주소_2`를 제안한다
- **Rationale**: 13 §3-2, 44 결정 기록 P2, D-11.
- **Alternatives considered**: 메일로만 알림(P2 B안) — 개인 확장.

## R14. 이벤트·메일 (확정)

- **Decision**: `shared.event.MemberWithdrawn(long memberId, Instant withdrawnAt)`, `MemberRestored(long memberId, Instant restoredAt)` — 001 `DomainEvent` 규칙(불변 record, ID·시각만). 구독:
  - account `WithdrawalMailListener`(`@TransactionalEventListener(AFTER_COMMIT)` + `@Async("mailExecutor")`): `auth_identity.email`이 있으면 `R/mail/withdrawal-requested.txt`("탈퇴 신청이 접수됐어요. {기한}까지 로그인하면 복구할 수 있어요")·`R/mail/account-restored.txt`("계정이 복구됐어요"). 이메일이 없는 소셜 계정은 보내지 않는다. 실패는 WARN(주소 없이 회원 번호만)
  - 012 트렌딩·검색 색인이 구독한다(20 §5 표) — 012 plan 몫
- 30일 정리·영구 정지 자동 정리는 이벤트를 내지 않는다(`MemberPurged` 없음)
- **Rationale**: 20 §3-6, 44 §2·§3, W-5.
- **Alternatives considered**: 트랜잭션 안에서 메일 — 외부 호출 금지.

## R15. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/withdraw.csv`(004 하네스 형식, owner `015`, targetState `NONE`):

  | action | ANONYMOUS | UNVERIFIED | MEMBER | ADMIN | SUSPENDED | WITHDRAWN |
  |---|---|---|---|---|---|---|
  | `me.withdrawal` (GET 안내) | 401 | 200 | 200 | 200 | 403 `ACCOUNT_SUSPENDED` | 403 `ACCOUNT_WITHDRAWN` |
  | `me.withdraw` (맞는 본인 확인) | 401 | 200 | 200 | 409 `ADMIN_CANNOT_WITHDRAW` | 403 `ACCOUNT_SUSPENDED` | 403 `ACCOUNT_WITHDRAWN` |
  | `me.restore` | 401 | 200(변화 없음) | 200(변화 없음) | 200(변화 없음) | 403 `ACCOUNT_SUSPENDED` | 200 |

  `me.withdraw` 행은 실행할 때마다 새 회원 픽스처를 쓴다(하네스가 행마다 픽스처를 만드는지 T001에서 확인, 아니면 이 기능의 `PermissionAction`이 직접 만든다). SUSPENDED 행위자는 "로그인 뒤 DB에서 정지로 바꾼 남은 세션"이다(004 하네스 정의)
- **Rationale**: 헌법 III, 42 P-12·§9, 004 FR-031.

## R16. 설정값 (제안)

```yaml
blog:
  withdraw:
    grace-period: 30d               # 복구 기한 = 정리 대상 경계 (FR-021a)
    suspended-purge-after: 365d     # 영구 정지 자동 정리 (Q1)
    confirm-text: 탈퇴               # 소셜 가입 본인 확인 문구
    purge:
      cron: "0 0 3 * * *"           # 03:30 사진 정리보다 먼저 → 같은 날 파일 삭제 (SC-005)
      batch-size: 100
      required-orders: [10, 20, 30, 40, 50, 60, 65, 70, 80, 90]
      redis-key-templates:
        - "auth:pw-change-fail:{memberId}"
        - "auth:login-fail:{emailHash}"
        - "rl:verify-resend:{memberId}"
        - "ratelimit:autosave:{memberId}"
        - "ratelimit:preview:{memberId}"
        - "ratelimit:like:{memberId}"
        - "ratelimit:comment:{memberId}"
        - "ratelimit:comment-edit:{memberId}"
        - "ratelimit:image:{memberId}"
        - "ratelimit:tag-suggest:{memberId}"
        - "ratelimit:follow:{memberId}"
```

- 비밀번호 잠금 수치는 001 `blog.auth.password-change.max-failures`·`lock-duration`을 그대로 쓴다.
- **Rationale**: 헌법 VII. 시각은 003(03:30)·006(03:30)·009(04:10·04:20)과 겹치지 않는다.
