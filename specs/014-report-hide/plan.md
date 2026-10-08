# Implementation Plan: 신고·관리자 숨김·회원 정지

**Branch**: `014-report-hide` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-report-hide/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

이메일 인증한 회원이 남의 글·댓글을 사유 6가지 중 하나로 신고하면, 대상마다 대기 사건 하나에 신고가 모이고 첫 신고 때 스냅샷(글: 제목 + 원문 앞 2,000자, 댓글: 내용)이 남는다. 관리자는 `/admin/reports`에서 대상별로 묶인 대기 사건을 스냅샷만 보고 "숨기기(사유)" 또는 "문제없음"으로 한 번에 닫고, 신고 없이도 볼 수 있는 글·댓글을 바로 숨길 수 있다. 숨긴 글은 작성자 외 모두에게 비공개 글과 같이 사라지고, 숨긴 댓글은 정해진 문구로 남는다. 해제하면 모든 것이 숨기기 전으로 돌아온다. 관리자는 회원을 1일·7일·30일·영구 정지할 수 있고, 정지하는 순간 그 회원의 모든 세션이 끊긴다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `report_case`·`report`·`member_suspension`, `post`·`comment`의 `hidden_at`·`hidden_by`·`hidden_reason`, 대기 사건 하나 부분 UNIQUE `uq_report_case_open_post`·`uq_report_case_open_comment`, `uq_report_case_reporter`, `ck_report_detail`(NULL 통과)을 그대로 쓴다(R1).
- **모듈.** 신고·사건·관리자 API는 새 모듈 `moderation`(006이 `ReportPostPurgeStep`을 임시로 둔 패키지 `com.team.blog.moderation`을 확정). 글 숨김·스냅샷은 post 모듈 새 공개 Service `PostModerationService`, 댓글은 007 `CommentModerationService`, 정지는 001 `SuspensionService`(T106 시그니처)를 이 기능이 채운다. 다른 모듈 테이블은 직접 쓰지 않는다(R2).
- **신고 접수 = 한 트랜잭션.** `POST /api/reports {targetType, targetId, reason, detail}` — 판정 401 → 403(`CONTENT_WRITE`) → 400 형식 → 404(볼 수 없는 대상, 004 `PostReadService.requireReadable` + 007 댓글 상태) → 400 `CANNOT_REPORT_OWN` → 400 `REPORT_DETAIL_REQUIRED`(기타인데 설명 없음) → 429 `TOO_MANY_REQUESTS`(1분 5건·하루 50건, Redis 장애면 통과) → 대기 사건 `INSERT … ON CONFLICT DO NOTHING`(부분 UNIQUE) + 없으면 기존 사건 `FOR UPDATE` → 신고 `INSERT … ON CONFLICT (case_id, reporter_id) DO NOTHING`. 새 신고든 중복이든 200(R3·R4).
- **관리자 경로.** `/admin/**`·`/api/admin/**`는 004 US7 규칙(비회원 401, 일반 회원 404)을 그대로 쓴다. API: 대기·처리됨 목록, 사건 상세, 처리(`POST …/resolution {action: HIDE|REJECT, reason}`), 직접 숨김·해제(`PUT`/`DELETE /api/admin/{posts|comments}/{id}/hidden`), 회원 조회·정지·해제(`/api/admin/members/{handle}…`)(R5).
- **처리 = 사건 행 잠금.** `SELECT … FOR UPDATE` → `PENDING`이 아니면 409 `REPORT_ALREADY_HANDLED`("이미 처리된 신고예요"). 자기 콘텐츠면 400 `CANNOT_MODERATE_OWN`, 자기 혼자 신고한 사건이면 400 `CANNOT_HANDLE_OWN_REPORT`. 숨기기면 대상 숨김 → 사건 `HIDDEN` + `handled_by/at` → 신고마다 `ReportResolved(ACTION_TAKEN)` + `ContentHidden` 1번. 반려면 `REJECTED` + `ReportResolved(NO_VIOLATION)`(R6).
- **직접 숨김.** 관리자가 볼 수 있는 대상만(004 판정, 남의 비공개 글은 404). 대기 사건이 있으면 그 사건을 숨김으로 닫고(신고자 알림), 없으면 신고 없는 사건을 새로 만들어 바로 `HIDDEN`으로 닫는다(스냅샷 포함). 해제는 `DELETE …/hidden` 하나 — 사건 상태는 `HIDDEN` 그대로, `ContentUnhidden` 발행(알림 없음)(R7).
- **대상 없음 자동 종료.** 글 완전 삭제는 006 `PostPurgeStep` order 10 `ReportPostPurgeStep`(006 임시 구현을 넘겨받음 — 글과 그 글 댓글의 대기 사건). 댓글 본인 삭제(자리·행 삭제)는 `CommentDeleted` 구독으로 그 댓글 사건과 대상 FK가 모두 NULL인 대기 사건을 닫는다. 탈퇴 정리는 015 order 80 `ReportWithdrawalPurgeStep`. 매일 배치가 남은 고아 사건을 한 번 더 닫는다(R8).
- **보관.** 매일 04:45 KST ShedLock `reportSnapshotCleanup`: `handled_at` 30일 지난 사건의 `snapshot_*`와 그 신고들의 `detail`을 NULL로(R9).
- **정지.** `SuspensionService.suspend(memberId, reason, duration, adminId)` — 회원 행 `FOR UPDATE` → 관리자·탈퇴 유예·이미 정지 거부 → `member_suspension` INSERT + `member.status = SUSPENDED` → 같은 트랜잭션 안에서 001 `SessionTerminator.terminateAll`(실패하면 롤백 + 503) → `MemberSuspended`. 해제는 `lifted_at/by` + `ACTIVE`. 기한 지남 자동 해제·로그인 거부 문구는 001 T108·T110(R10).
- **이벤트.** `shared.event`의 `ReportResolved`·`ContentHidden`·`ContentUnhidden`·`MemberSuspended`와 enum `ReportTargetType`·`ReportResult`. 011과 "먼저 하는 쪽이 만든다"(011 T045). 숨김·처리 알림 문구와 저장은 011(R11).
- **화면.** 글 [신고](005 `ReactionBar.reportButton`)·댓글 [신고](007 `CommentItem` 자리) → `ReportDialog`(사유 6개, 기타면 설명 200자). 관리자 `/admin/reports`(대기·처리됨 탭), `/admin/reports/{caseId}`(스냅샷·현재 상태·작성자 정보·처리·정지), `/admin/members/{handle}`. 작성자 숨김 안내에 사유(005 `AuthorStatusBanner`). 사유 이름은 011과 공유하는 `F/features/moderation/reasonLabels.ts`(R12).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Scheduling), `JdbcClient`, ShedLock JDBC, 001 `AccountStatusGuard`·`RateLimiter`·`CursorCodec`·`ListScope`·`SessionTerminator`·`SuspensionService`(T106)·`MemberQueryService`, 004 `PostReadService`·`Viewer`·관리자 경로 규칙(US7 T063~T067), 006 `PostPurgeStep`·`ReportPostPurgeStep`(임시), 007 `CommentModerationService`·`CommentQueryService`·`CommentDeleted`
- 새 의존성 없음
- 화면: React 18 + react-router 7, 004 `useAuthGate`·`PostActions`·`AdminRouteGate`, 005 `ReactionBar`·`AuthorStatusBanner`, 007 `CommentItem`, 006 `ConfirmDialog`·`useToast`(006 머지 후)

**Storage**:

- PostgreSQL: `report_case`·`report`(moderation 소유), `member_suspension`·`member.status`(account 소유 — `SuspensionService`로만), `post.hidden_*`(post — `PostModerationService`로만), `comment.hidden_*`(interaction — 007 `CommentModerationService`로만)
- Redis: 요청 제한 `ratelimit:report:{memberId}:1m`·`ratelimit:report:{memberId}:1d`(001 `RateLimiter`)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), MockMvc, `MutableClock`, `RedisOutage`, `SqlCounter`, 004 권한 하네스(새 CSV `moderation.csv`), 011이 있으면 알림 행 확인(없으면 이벤트 기록기). 동시성은 `CountDownLatch` 두 스레드(SC-002·FR-016). 화면은 Vitest + Testing Library, 종단 확인은 Playwright

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 신고 접수 p95 100ms 이내(SQL 4~6번)
- 관리자 대기 목록 p95 300ms 이내(대기 사건 수천 건, 20개씩)
- 처리 한 번 p95 300ms 이내(신고 수백 건 사건 포함, 이벤트는 커밋 뒤)
- 정지 p95 500ms 이내(세션 삭제 포함)

**Constraints**:

- 작성자에게 가는 어떤 응답·알림에도 신고자 번호·이름·수가 없다(SC-004)
- 일반 회원은 관리자 화면 존재를 알 수 없다(SC-007 — 004 규칙)
- 관리자도 현재 원문을 열어 볼 수 없다. 처리 화면은 스냅샷과 현재 상태 이름만(FR-015)
- 숨김·해제는 좋아요·댓글 행을 지우지 않는다(SC-006)
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 회원 수천 명, 대기 사건 수백~수천 건, 관리자 수 명
- API: 회원 1개(신고), 관리자 10개(목록·상세·처리 3개, 글·댓글 숨김·해제 4개, 회원 조회·정지·해제 3개)
- 화면: 신고 창 1개, 관리자 화면 3개, 기존 화면 연결 4곳(글 상세 신고·숨김 안내, 댓글 신고, 라우트)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 표·제약 그대로. 회원 신고·이의 제기는 개인 확장(범위 밖) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (모듈 목록 확인 필요)** | 신고 표는 moderation만 쓴다. 글·댓글 숨김, 정지, 회원 정보는 각 모듈 공개 Service로만(R2). 목록의 작성자 주소는 001 `MemberQueryService.handlesOf`(새 메서드)로 묶어 읽는다. 새 모듈 `moderation`은 헌법 II 목록에 없다(011 `notification`과 같은 상황 — 팀 확인 T003) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 신고 대상 판정은 004 `PostReadService`·`PostAccessPolicy` 하나. 관리자 경로는 004 규칙(일반 회원 404). 관리자 API도 서비스에서 `role = ADMIN`을 다시 확인한다(두 번째 겹). 권한 매트릭스 `moderation.csv`(R13) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 스냅샷은 원문(Markdown·댓글 글자)을 텍스트 노드로만 그린다 — 관리자 화면에서도 HTML로 렌더링하지 않는다. 신고 설명도 텍스트 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 신고 요청 제한은 Redis 장애 때 통과. 알림은 커밋 뒤 비동기(011). 정지의 세션 삭제 실패는 정지 자체를 되돌린다(정지 회원이 로그인 상태로 남지 않게 — R10) |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 숨김은 행을 지우지 않는다. 대상이 지워져도 사건·신고·스냅샷은 남고, 30일 뒤 스냅샷·설명만 비운다(13 §2-5). 정지 이력은 탈퇴 뒤에도 남는다 |
| VII. 수치는 설정값으로 | **PASS** | `blog.moderation.*`(스냅샷 길이 2,000, 설명 200, 요청 제한 5/1분·50/1일, 보관 30일, 정리 시각, 목록 크기 20, 정지 기간 목록) |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 신고 중복·동시 처리·숨김 효과·해제 복원·정지 세션 삭제·보관 정리를 Testcontainers로 |

**Gate 결과 (Phase 0 전)**: 위반 없음.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. spec Implementation Notes는 `REPORT_DETAIL_REQUIRED`를 최상위 400 코드로 적었지만, 001·002가 칸 규칙을 `VALIDATION_FAILED`의 `errors[].code`로 내는 방식(`AGREEMENT_VERSION_MISMATCH`·`TOO_MANY_TAGS`)에 맞춰 칸 오류로 둔다. 자기 신고 `CANNOT_REPORT_OWN`은 업무 규칙이라 최상위 코드(팀 확인 T004).
  2. 새 이유 코드 7개(`CANNOT_REPORT_OWN`·`REPORT_ALREADY_HANDLED`·`CANNOT_MODERATE_OWN`·`CANNOT_HANDLE_OWN_REPORT`·`CANNOT_SUSPEND_ADMIN`·`CANNOT_SUSPEND_WITHDRAWN`·`ALREADY_SUSPENDED` — 원문은 앞 둘만)(팀 확인 T004).
  3. 007이 댓글 행을 지우면(답글 없는 본인 삭제·빈 자리 정리) FK `ON DELETE SET NULL`로 대기 사건이 `post_id`·`comment_id` 모두 NULL인 채 `PENDING`으로 남는다. 007 `CommentDeleted` 구독과 매일 배치로 닫는다(R8). 007 contracts/events.md에 구독자 014를 더해야 한다(ANALYSIS-tier-bc).
  4. 자리로 남긴 댓글(본인 삭제, `deleted_at` 있음)의 대기 사건도 "대상 없음"으로 닫는다고 정했다. 원문은 "완전히 지워지면"만 적었다(제안 — 내용이 이미 사라져 숨길 것이 없다).
  5. 정지 중 세션 삭제를 같은 트랜잭션에 넣어, Redis 장애면 정지를 하지 않고 503 `TEMPORARILY_UNAVAILABLE`을 준다. "Redis 장애는 통과"(헌법 V)와 반대 방향이지만, 정지는 보안 조치라 반쯤 된 상태를 남기지 않는다(R10).
  6. 005 `AuthorStatusBanner`의 `HIDDEN_NOTICE`는 사유 없는 문장이고 사유 자리(`hiddenReasonSlot`)가 문장 뒤에 붙는다. FR-022 문장("…숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는…")을 만들려면 005 파일의 문장 조립을 고쳐야 한다(005 소유 파일 — T041).

## Project Structure

### Documentation (this feature)

```text
specs/014-report-hide/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml           # 신고, 관리자 신고·숨김·회원 정지
│   └── moderation-sql.md      # 접수·처리·직접 숨김·대상 없음·보관 정리·정지 SQL, 이벤트, 다른 모듈 공개 Service
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── moderation/                                   # 새 모듈 (006 임시 패키지 확정)
│   ├── web/
│   │   ├── ReportController.java                 # POST /api/reports
│   │   ├── AdminReportController.java            # GET 목록·상세, POST resolution
│   │   ├── AdminHideController.java              # PUT·DELETE /api/admin/{posts|comments}/{id}/hidden
│   │   ├── AdminMemberController.java            # GET·POST·DELETE /api/admin/members/{handle}…
│   │   └── dto/                                  # ReportRequest, CaseListItem, CaseDetail, ResolutionRequest, HideRequest, AdminMemberView, SuspendRequest
│   ├── application/
│   │   ├── ReportService.java                    # 접수
│   │   ├── ReportTargetResolver.java             # 글·댓글 → ReportTarget (볼 수 있나·작성자·스냅샷)
│   │   ├── CaseResolutionService.java            # 처리(숨기기·반려)
│   │   ├── DirectHideService.java                # 직접 숨김·해제
│   │   ├── CaseQueryService.java                 # 대기·처리됨 목록, 상세
│   │   ├── OrphanCaseCloser.java                 # 대상 없음 종료 (CommentDeleted 구독 + 배치)
│   │   ├── ReportPostPurgeStep.java              # 006 임시 구현을 넘겨받음 (order 10)
│   │   ├── ReportWithdrawalPurgeStep.java        # 015 order 80
│   │   ├── ReportSnapshotCleanupJob.java         # 04:45 ShedLock
│   │   ├── AdminMemberService.java               # 회원 조회(정보·숨겨진 수·정지 이력)·정지·해제 → account 공개 Service
│   │   ├── ModerationProperties.java             # blog.moderation.*
│   │   └── ModerationReasonCode.java
│   ├── domain/ReportReason.java, CaseStatus.java, ReportTarget.java
│   └── infra/ReportCaseRepository.java, ReportRepository.java, CaseListQueryRepository.java
├── post/application/PostModerationService.java   # hide / unhide / snapshot / currentState (post 모듈 새 공개 Service)
├── interaction/application/CommentModerationService.java   # 007 소유 — snapshot 필드 맞춤만
├── account/application/SuspensionService.java    # 001 T106 시그니처 → suspend / lift 구현
├── account/application/MemberQueryService.java   # + handlesOf(ids), findAdminView(handle) (001 소유, 추가만)
└── shared/event/ReportResolved.java, ContentHidden.java, ContentUnhidden.java, MemberSuspended.java, ReportTargetType.java, ReportResult.java

backend/src/main/resources/application.yml        # blog.moderation.*

backend/src/test/
├── resources/permission/moderation.csv           # 신고·직접 숨김 × 행위자 × 대상 상태
└── java/com/team/blog/moderation/
    ├── integration/ReportApiIT.java, ReportConcurrencyIT.java, AdminAccessIT.java, CaseResolutionIT.java,
    │   DirectHideIT.java, HiddenContentVisibilityIT.java, UnhideRestoresIT.java, SuspensionIT.java,
    │   OrphanCaseIT.java, ReportSnapshotCleanupIT.java, ReporterAnonymityIT.java, ModerationPermissionMatrixIT.java
    └── unit/ReportReasonTest.java

frontend/src/
├── api/reports.ts, api/admin.ts
├── features/moderation/
│   ├── reasonLabels.ts                           # 011과 공유 (먼저 하는 쪽이 만듦)
│   ├── ReportButton.tsx, ReportDialog.tsx, useReport.ts
│   └── HiddenReasonText.tsx                      # 작성자 숨김 안내 문장
├── features/admin/
│   ├── CaseList.tsx, CaseSnapshot.tsx, ResolutionForm.tsx, SuspendForm.tsx, SuspensionHistory.tsx
│   └── adminText.ts
├── pages/admin/AdminReportsPage.tsx, AdminReportDetailPage.tsx, AdminMemberPage.tsx
├── App.tsx                                       # /admin/* 경로 (006 머지 후)
├── pages/PostDetailPage.tsx                      # ReactionBar.reportButton (005 소유)
├── features/post-detail/AuthorStatusBanner.tsx   # 사유 문장 (005 소유)
└── features/comments/CommentItem.tsx             # 댓글 [신고] (007 소유)
```

**Structure Decision**: 02 §3의 package-by-feature 구조에 새 모듈 `moderation`을 더한다(006이 가정한 이름). 신고 표는 이 모듈만 쓰고, 숨김·정지는 대상 표의 주인 모듈이 공개 Service로 연다. 관리자 API는 모두 `/api/admin/**` 아래에 두어 004 경로 규칙 하나로 막는다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

위반 없음.
