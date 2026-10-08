# Implementation Plan: 회원 탈퇴·복구

**Branch**: `015-withdraw` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-withdraw/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

회원은 설정의 [회원 탈퇴]에서 잃게 되는 것(글 수·남의 글 댓글 수·받은 좋아요 수·다시 쓸 수 없는 주소)을 숫자로 확인하고, 체크와 본인 확인(이메일 가입은 비밀번호, 소셜 가입은 "탈퇴" 입력)을 거쳐 탈퇴를 신청한다. 신청 즉시 모든 기기에서 로그아웃되고 블로그·글은 404가 된다. 30일 안에 로그인해 [복구하기]를 누르면 그대로 돌아오고, 30일이 지나면 매일 새벽 정리 작업이 회원 한 명씩 한 트랜잭션으로 글·댓글·좋아요·사진·로그인 수단·관계·알림·신고를 정리하고 회원 기록을 블로그 주소만 남긴 익명 기록으로 바꾼다. 영구 정지 1년이 지난 회원도 같은 정리를 받는다(Clarifications Q1).

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `member.withdrawn_at`·`deleted_at`, `ck_member_withdrawn`(상태 WITHDRAWN ⇔ 신청 시각), `ck_member_deleted`(익명 처리는 WITHDRAWN만), `ck_member_nickname_null`(닉네임 NULL은 익명 처리 뒤만), `uq_member_nickname`(NULL 무시 → 즉시 해제), `ix_member_withdraw_purge`(정리 대상 부분 인덱스)를 그대로 쓴다. 동의(`member_agreement`)·정지 이력(`member_suspension`)은 지우지 않는다(R1).
- **유예 중 차단은 이미 있다.** 001 `WithdrawnAccountGateFilter`가 허용 목록(`POST /api/me/restore`, `POST /api/auth/logout`, `GET /api/me`, `GET /api/auth/csrf`) 밖의 모든 `/api/**`를 403 `ACCOUNT_WITHDRAWN` `details{action: RESTORE}`로 막고, 004 공용 노출 조건(`작성자 withdrawn_at IS NULL`)이 블로그·글·목록을 가린다. 이 기능은 허용 목록을 바꾸지 않는다(R2).
- **신청 = 판정 → 한 트랜잭션.** `POST /api/me/withdraw {confirmed, password?, confirmText?}`. 판정 순서 401 → 403(게이트·`AccountStatusGuard` `ACCOUNT_WRITE`: 정지 403, 인증 전 통과) → 409 `ADMIN_CANNOT_WITHDRAW` → 400 `WITHDRAW_CONFIRM_REQUIRED` → 본인 확인(이메일 가입: 001 잠금 확인 429 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` → 비밀번호 400 `CURRENT_PASSWORD_MISMATCH`, 실패 기록은 비밀번호 변경과 같은 `auth:pw-change-fail:{memberId}` / 소셜: 400 `CONFIRM_TEXT_MISMATCH`). 통과하면 한 트랜잭션에서 회원 행 잠금 → `status = WITHDRAWN`, `withdrawn_at = now()` → 001 `SessionTerminator.terminateAll`(Redis 장애면 503, 전체 롤백) → `MemberWithdrawn` 발행. 컨트롤러가 지금 요청의 세션도 무효화하고 200 `{restoreDeadline}`을 돌려준다(R3·R4).
- **복구 = 기한 확인 → 한 트랜잭션.** `POST /api/me/restore`. 회원 행을 잠그고 `withdrawn_at + 30일 ≥ now()`이면 `status = ACTIVE`, `withdrawn_at = NULL` → `MemberRestored`. 기한이 지났으면 409 `RESTORE_PERIOD_EXPIRED`. 정리 대상 조건 `withdrawn_at < now() - 30일`과 경계가 정확히 맞물리고, 정리 작업도 같은 행을 잠그고 다시 확인하므로 복구와 정리가 겹치지 않는다(R5).
- **로그인 상태 요약에 기한을 더한다.** 허용 목록의 `GET /api/me` 응답 `MeSummary`에 `restoreDeadline`·`restoreExpired`를 더해(유예 회원만 값) 복구 화면이 추가 API 없이 그린다(R6).
- **안내 숫자는 각 모듈이 준다.** `GET /api/me/withdrawal`(신청 화면용). 글 수(휴지통 포함)·받은 좋아요 합은 post 모듈, 남의 글 댓글 수는 interaction 모듈이 account가 정의한 포트(`WithdrawalImpactQuery`의 두 조각)를 구현해 준다 — account가 다른 모듈 테이블을 직접 읽지 않는다(R7).
- **30일 정리 = 확장점 + 회원별 트랜잭션.** `shared` 패키지의 `WithdrawalPurgeStep { int order(); void purge(long memberId); }`를 각 모듈이 구현하고, `WithdrawPurgeJob`(매일 03:00 KST, ShedLock — 03:30 사진 정리보다 먼저 돌아 같은 날 사진까지 지워짐)이 대상 회원마다 `WithdrawalPurgeRunner.purgeOne`(`REQUIRES_NEW`)을 부른다. 단계: 10 글(006 `PostPurgeService.purgeAllByAuthor`) / 20 댓글(007 `CommentPurgeService.purgeByAuthor`) / 30 좋아요(009 `LikePurgeService.purgeByMember`) / 40 사진(003 `ImagePurgeService.detachAllByUploader`) / 50 로그인 수단 / 60 친구 / 65 팔로우(010) / 70 알림(011) / 80 신고(014) / 90 회원 익명화 / 커밋 후 Redis 키 삭제. 한 단계라도 실패하면 그 회원 전부 롤백, 다음 회원으로 넘어가고 다음 날 다시 시도한다(R8~R10).
- **필수 단계가 다 있어야 돈다.** 설정값 `blog.withdraw.purge.required-orders`(기본 10·20·30·40·50·60·65·70·80·90)에 있는 단계 중 하나라도 Bean이 없으면 정리 작업은 아무 회원도 처리하지 않고 ERROR 로그를 남긴다. 010·011·014보다 015가 먼저 머지돼도 일부만 정리된 익명 회원이 생기지 않는다(R9).
- **영구 정지 1년 자동 정리.** 같은 작업이 `status = SUSPENDED`이고 종료 없는 열린 정지가 1년(`blog.withdraw.suspended-purge-after`) 넘은 회원을 찾아, 같은 트랜잭션에서 `status = WITHDRAWN`, `withdrawn_at = now()`로 바꾼 뒤 같은 단계를 실행한다. 정지 이력 행은 그대로 둔다. 이벤트는 내지 않는다(R11).
- **화면.** `/settings/withdraw`(안내·체크·본인 확인, [탈퇴하기]는 기본 포커스 아님), `/withdrawn`(완료, 브라우저 임시 글 삭제), `/account/restore`(복구·기한 지남 두 상태), 유예 회원은 어느 주소로 가도 복구 화면으로 보내는 `RestoreGate`, 가입 화면의 "탈퇴 신청한 계정이 있어요" 안내(R12·R13).
- **메일.** 커밋 후 `MemberWithdrawn`·`MemberRestored` 구독으로 이메일이 있는 계정에만 접수·복구 메일(001 `AccountMailService`·`mailExecutor`). 실패는 경고 로그만(R14).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation, Mail), Spring Data JPA(`MemberRepository.findByIdForUpdate`), `JdbcClient`, Flyway, ShedLock JDBC(V2), Resilience4j(`RedisGuard`), 001 `SessionTerminator`·`AccountStatusGuard`·`AuthTokenStore`·`PasswordEncoder`·`AccountMailService`·`MailTemplates`·`WithdrawnAccountGateFilter`, 006 `PostPurgeService`, 007 `CommentPurgeService`, 009 `LikePurgeService`, 003 `ImagePurgeService`
- 새 의존성 없음
- 화면: React 18 + react-router 7, 001 `SessionProvider`·`logout.ts`(`registerLogoutCleanup`)·`SettingsPage`(회원 탈퇴 자리), 004 `useAuthGate`

**Storage**:

- PostgreSQL: `member`(account 소유: `status`·`withdrawn_at`·`deleted_at`·닉네임·소개·최근 활동), `auth_identity`·`friendship`(account 소유, 정리 단계 50·60), `member_agreement`·`member_suspension`(읽기만, 지우지 않음). 다른 모듈 테이블은 각 모듈의 정리 Service로만
- Redis: 비밀번호 실패 기록 `auth:pw-change-fail:{memberId}`(001, 15분), 세션(Spring Session 인덱스), 정리 후 지우는 키 목록(research R10)
- 브라우저: 탈퇴 완료 때 IndexedDB `draft:{memberId}:*`·`draft-backup:{memberId}:*` 삭제(002 `clearMemberDrafts`, 001 `registerLogoutCleanup`)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), 메일은 001 `support/CapturingMailSender`, Spring Security Test, MockMvc, `@RecordApplicationEvents`. 화면은 Vitest + Testing Library, 종단 확인은 Playwright. 헌법 VIII에 따라 신청·복구·정리(전부 성공/전부 취소)·경계 시각·동시 복구와 정리는 실제 DB·Redis 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 신청·복구 p95 300ms 이내(BCrypt 비교 1번 + 트랜잭션 SQL 3~4번 + 세션 삭제)
- 정리: 회원 한 명 글 100개·댓글 1,000개 기준 10초 이내, 한 번에 최대 100명(`batch-size`), 작업 전체 `lockAtMostFor` 1시간
- 안내 화면 숫자 조회 SQL 3번(글 수·받은 좋아요 합 1번, 댓글 수 1번, 회원·로그인 수단 1번)

**Constraints**:

- 정리 단계는 모두 호출한 쪽 트랜잭션(`MANDATORY`) 안에서 실행하고, 트랜잭션 안에서 외부 호출(파일 삭제·메일·Redis)을 하지 않는다. Redis 키 삭제는 커밋 뒤
- 정리 작업은 이벤트를 만들지 않는다(20 §3-6). 글 완전 삭제의 `PostPurged`만 post 모듈 규칙대로 나간다
- 탈퇴 사유를 묻거나 저장하지 않는다(FR-004). 로그에 이메일·닉네임·비밀번호를 남기지 않고 회원 번호만 남긴다
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 회원 수천 명, 하루 탈퇴 수 명 기준
- API 3개 새로(`GET /api/me/withdrawal`, `POST /api/me/withdraw`, `POST /api/me/restore`), 1개 확장(`GET /api/me`), 가입 오류 코드 1개 추가
- 배치 1개(`WithdrawPurgeJob`), 정리 단계 10개 중 7개(10·20·30·40·50·60·90)를 이 기능이 만들고 3개(65·70·80)는 010·011·014가 만든다
- 화면 3개 + 복구 게이트 1개, 메일 2종

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 컬럼·제약·인덱스 그대로. 정리 단계 순서는 10 단위로 두고 새 단계는 사이 값(FR-029). 개인 확장 단계는 Bean 추가만으로 끼운다 |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | account는 `member`·`auth_identity`·`friendship`만 쓴다. 다른 모듈 데이터는 그 모듈의 정리 단계(`WithdrawalPurgeStep` 구현)와 안내 숫자 포트(`WithdrawalImpactQuery` 구현)로만. 확장점 인터페이스는 44 §4대로 `shared`에 둬 account ↔ post 순환 의존을 만들지 않는다 |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 신청은 로그인 → 계정 상태 → 관리자 → 확인 → 본인 확인 순. 유예 중 차단은 001 게이트 필터(서버)와 `RestoreGate`(화면) 두 겹. 유예·익명 회원 블로그는 같은 404(004). 권한 매트릭스에 `me.withdrawal`·`me.withdraw`·`me.restore` 행(R15) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 입력은 비밀번호·확인 문구·체크뿐이고 저장하지 않는다. 안내 화면의 주소·숫자는 텍스트 노드로만 출력 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 메일 실패는 신청·복구를 막지 않는다(커밋 후 비동기). 정리 후 Redis 키 삭제 실패는 경고만(키는 TTL로 사라짐). 단, 세션 삭제는 탈퇴의 핵심 효과라 Redis 장애면 신청 자체를 503으로 거부한다(SC-001 우선, R4) |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 유예 30일 동안 아무것도 지우지 않는다. 정리는 회원 한 명 단위 전부 성공/전부 취소(FR-024). 동의·정지 이력은 남긴다(FR-028) |
| VII. 수치는 설정값으로 | **PASS** | `blog.withdraw.grace-period`(30d), `suspended-purge-after`(365d), `purge.cron`·`batch-size`·`required-orders`·`redis-key-templates`, 확인 문구 `confirm-text`("탈퇴"). 비밀번호 잠금 수치는 001 `blog.auth.password-change.*` 그대로 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 신청·복구·경계 시각·동시 복구와 정리·한 단계 강제 실패 롤백·정리 후 카운터 불변식·개인 정보 잔존 검사 |

**Gate 결과 (Phase 0 전)**: 위반 없음. Complexity Tracking은 필요 없다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. 44 §4 원문은 order 90에서 `ai_consent_at`·`suspended_until`·`suspended_reason`을 비운다고 했지만 V1에서 이 값은 `member_agreement`·`member_suspension`으로 옮겨졌고 남겨야 하는 기록이다(spec Implementation Notes, 2026-10-07 E6). order 90은 `member` 행만 바꾼다.
  2. 44 §4는 친구 단계(60)를 "적용자"로 적었지만 spec FR-025 6번(2026-10-07 M1)대로 항상 실행한다.
  3. 신청 요청의 `confirmed` 칸은 원문 API(`{password?, confirmText?}`)에 없다. FR-002 "서버도 확인 없는 요청을 거부"를 지키려고 더한 제안이다(R3).
  4. 잠금 응답 코드는 001 비밀번호 변경의 `PASSWORD_CHANGE_TEMPORARILY_LOCKED`(429)를 그대로 쓴다. 실패 기록을 합쳐 세기로 했으므로(Q3) 코드도 하나다. 이름에 "변경"이 들어가는 점은 팀 확인 항목이다(R3).
  5. 010·011·014의 정리 단계(65·70·80)는 각 기능 tasks가 만든다. 그 기능이 015보다 먼저 구현되면 이 기능의 확장점 인터페이스 작업(tasks T009)을 그 기능이 먼저 한다(둘 중 먼저 하는 쪽이 만듦, 한 파일).

## Project Structure

### Documentation (this feature)

```text
specs/015-withdraw/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 안내·신청·복구 API, GET /api/me 확장, 가입 오류 코드
│   └── purge-steps.md   # WithdrawalPurgeStep 확장점, 단계 표·SQL, 정리 작업 흐름, Redis 정리, 이벤트·메일
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── shared/application/withdraw/
│   └── WithdrawalPurgeStep.java                 # 확장점 (44 §4) — 각 모듈이 구현
├── shared/event/
│   ├── MemberWithdrawn.java                     # memberId, withdrawnAt
│   └── MemberRestored.java                      # memberId, restoredAt
├── account/
│   ├── web/
│   │   ├── WithdrawalController.java            # GET /api/me/withdrawal, POST /api/me/withdraw, POST /api/me/restore
│   │   └── dto/WithdrawRequest.java, WithdrawalPreview.java, WithdrawResult.java, RestoreResult.java
│   ├── application/
│   │   ├── WithdrawalService.java               # 신청 (판정 순서·트랜잭션·세션 삭제·이벤트)
│   │   ├── RestoreService.java                  # 복구 (기한·잠금·이벤트)
│   │   ├── WithdrawalPreviewService.java        # 안내 숫자 (포트 두 개 + 회원·로그인 수단)
│   │   ├── CurrentPasswordVerifier.java         # 001 비밀번호 변경과 공용: 잠금 확인·BCrypt·실패 기록
│   │   ├── WithdrawalProperties.java            # blog.withdraw.*
│   │   ├── port/AuthoredPostStats.java          # post 모듈이 구현: 글 수(휴지통 포함)·받은 좋아요 합
│   │   ├── port/AuthoredCommentStats.java       # interaction 모듈이 구현: 남의 글 댓글 수
│   │   ├── purge/WithdrawPurgeJob.java          # 매일 03:00 (ShedLock) — 대상 찾기·필수 단계 확인
│   │   ├── purge/WithdrawalPurgeRunner.java     # 회원 1명 = REQUIRES_NEW 트랜잭션, 단계 실행, 커밋 후 Redis
│   │   ├── purge/WithdrawalRedisCleaner.java    # 세션·토큰·실패 기록·요청 횟수 키
│   │   ├── purge/AuthIdentityWithdrawalPurgeStep.java   # order 50
│   │   ├── purge/FriendshipWithdrawalPurgeStep.java     # order 60
│   │   ├── purge/MemberWithdrawalPurgeStep.java         # order 90
│   │   ├── mail/WithdrawalMailListener.java     # MemberWithdrawn·MemberRestored → 접수·복구 메일
│   │   ├── AccountReasonCode.java               # + ADMIN_CANNOT_WITHDRAW, WITHDRAW_CONFIRM_REQUIRED, CONFIRM_TEXT_MISMATCH, RESTORE_PERIOD_EXPIRED, EMAIL_WITHDRAWAL_PENDING
│   │   ├── MeSummary.java / MeQueryService.java # + restoreDeadline, restoreExpired
│   │   └── SignupService.java                   # 같은 이메일이 유예 계정이면 EMAIL_WITHDRAWAL_PENDING
│   └── infra/
│       ├── WithdrawalMemberRepository.java      # 상태 전이·대상 조회·익명화 (JdbcClient)
│       └── redis/AuthTokenStore.java            # + revokeLatest(TokenType, memberId)
├── post/application/
│   ├── PostWithdrawalPurgeStep.java             # order 10 → PostPurgeService.purgeAllByAuthor (006 R25)
│   └── AuthoredPostStatsAdapter.java            # AuthoredPostStats 구현
├── interaction/application/
│   ├── CommentWithdrawalPurgeStep.java          # order 20 → CommentPurgeService.purgeByAuthor (007)
│   ├── LikeWithdrawalPurgeStep.java             # order 30 → LikePurgeService.purgeByMember (009)
│   └── AuthoredCommentStatsAdapter.java         # AuthoredCommentStats 구현
└── media/application/
    └── ImageWithdrawalPurgeStep.java            # order 40 → ImagePurgeService.detachAllByUploader (003)

backend/src/main/resources/
├── application.yml                              # blog.withdraw.*
└── mail/withdrawal-requested.txt, account-restored.txt

backend/src/test/
├── resources/permission/withdraw.csv            # me.withdrawal·me.withdraw·me.restore
└── java/com/team/blog/account/
    ├── unit/WithdrawRequestValidationTest.java, RestoreDeadlineTest.java
    └── integration/
        ├── WithdrawalApiIT.java                 # US1
        ├── WithdrawalGraceIT.java               # 유예 중 노출·허용 목록 (US1 #7, US2 #2)
        ├── RestoreApiIT.java                    # US2, FR-021a 경계
        ├── WithdrawPurgeJobIT.java              # US3: 단계 순서·불변식·개인 정보 잔존·롤백·필수 단계
        ├── WithdrawPurgeConcurrencyIT.java      # 복구와 정리 동시
        ├── SuspendedPurgeIT.java                # 영구 정지 1년
        ├── RejoinAfterPurgeIT.java              # US4
        ├── WithdrawalMailIT.java                # 접수·복구 메일, 이메일 없는 소셜 계정
        └── WithdrawPermissionMatrixIT.java      # 004 하네스

frontend/src/
├── api/withdrawal.ts                            # getWithdrawalPreview / withdraw / restore
├── api/me.ts                                    # MeSummary + restoreDeadline, restoreExpired
├── features/withdraw/
│   ├── WithdrawForm.tsx                         # 안내·체크·본인 확인·[탈퇴하기]
│   ├── RestoreGate.tsx                          # 유예 회원이면 /account/restore로
│   ├── formatDeadline.ts                        # "2026년 11월 7일 오후 3:20" / N일 남음 (KST)
│   └── withdrawMessages.ts
├── features/auth/logout.ts                      # + clearLocalAccountData(memberId) (로그아웃 ②만 공개)
├── pages/WithdrawPage.tsx                       # /settings/withdraw
├── pages/WithdrawnPage.tsx                      # /withdrawn
├── pages/RestorePage.tsx                        # /account/restore
├── pages/SignupPage.tsx                         # EMAIL_WITHDRAWAL_PENDING 안내
└── App.tsx                                      # 경로 3개 + RestoreGate
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 탈퇴·복구·정리 작업은 account 모듈에 두고(회원 상태 소유), 확장점 인터페이스와 이벤트 2종만 `shared`에 둔다. 각 모듈의 정리 단계 클래스는 그 모듈 패키지에 두되 Tier B(003·006·007·009) 단계 클래스는 이 기능의 tasks가 만든다(각 기능 tasks의 "015 인계" 항목). 010·011·014 단계는 그 기능 tasks가 만든다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (위반 없음).
