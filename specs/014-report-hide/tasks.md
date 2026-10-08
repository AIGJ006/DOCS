---

description: "Task list for 014-report-hide (신고·관리자 숨김·회원 정지)"
---

# Tasks: 신고·관리자 숨김·회원 정지

**Input**: Design documents from `/specs/014-report-hide/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, moderation-sql.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다. 011이 없으면 알림 대신 이벤트 기록기(`T/support/event/RecordedEvents`)로 이벤트 수·필드를 확인한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 새 모듈 `moderation`(신고 사건·신고·관리자 API·보관 정리)을 소유한다. 글 숨김은 post 모듈에 새 공개 Service를 더하고, 댓글 숨김은 007, 정지는 001 `SuspensionService`를 채운다. 새 테이블·마이그레이션은 없다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `AccountStatusGuard`·`ActionKind.CONTENT_WRITE`, `RateLimiter`, `CursorCodec`·`ListScope`, `SessionTerminator`, `MemberQueryService`, `SchedulingConfig`(ShedLock), `support/IntegrationTestBase`·`MemberFixtures`·`RedisOutage`·`MutableClock`·`SqlCounter`
- 선행: specs/001 US5 T106·T108·T110 — `MemberSuspension`·`MemberSuspensionRepository`·`SuspensionService`(시그니처·`findOpen`·`liftIfExpired`), 로그인 정지 거부·자동 해제·정지 문구(US5는 이것 위에 쌓는다)
- 선행: specs/004 — `PostReadService.requireReadable`·`Viewer`, 관리자 경로 US7(T063~T067: `AdminPathSecurityCustomizer`·`AdminPathAccessDeniedHandler`·`AdminPathAuthenticationEntryPoint`·`AdminRouteGate`), `useAuthGate`(T052)·`PostActions`(T061), 권한 하네스(`support/permission/`)
- 선행: specs/005 — `ReactionBar.reportButton` 자리, `AuthorStatusBanner`(`hiddenReasonSlot`), 상세 `authorView.hiddenReason`
- 선행: specs/007 — `CommentModerationService`(hide·unhide·snapshot), `CommentQueryService.commentIdsOfPost`, `CommentDeleted`, `CommentItem` 버튼 자리, `comment.csv`. US3은 007 머지 후

**006 머지 후**

- `ReportPostPurgeStep`·`moderation/package-info.java`를 006 임시 구현에서 넘겨받는 작업(T009). 006이 `com.team.blog.moderation`에 만든 파일이다
- `F/App.tsx`에 `/admin/*` 경로를 더하는 작업(T040). 006이 같은 파일에 `/manage/posts` 경로를 더한다
- 006 `F/components/ConfirmDialog.tsx`·`useToast.tsx`를 쓰는 화면 작업(T025·T039·T049·T054)
- `TR/permission/post-write.csv`는 고치지 않는다(새 CSV `moderation.csv`)

**먼저 하는 쪽이 만든다 (다른 기능과 같은 파일)**

- `B/shared/event/ReportResolved.java`·`ContentHidden.java`·enum `ReportTargetType`·`ReportResult`와 `F/features/moderation/reasonLabels.ts` — 011 T045·T047(T007·T015)
- `B/shared/application/withdraw/WithdrawalPurgeStep.java` — 015 T009(T062)
- 007 `CommentModerationService.snapshot`이 돌려줄 `CommentSnapshot` 필드와 `hiddenOf` — 007 T045 근처(T006)

**후속 (다른 스펙이 이 기능을 사용)**

- 011-notification: `ReportResolved`·`ContentHidden`을 구독해 운영 알림 2종. 사유 이름은 `reasonLabels.ts`
- 015-withdraw: order 80 `ReportWithdrawalPurgeStep`(T062), 탈퇴 유예 회원 정지 불가(T053)
- 012·005·006·008: 숨김은 004 공용 조건으로 자동 제외 — 할 일 없음(T031이 회귀 확인)

**팀 결정 대기 (기본안으로 진행)**

- 새 모듈 `moderation`을 헌법 II 모듈 목록에 넣는 것(011 `notification`과 함께) — 확인 작업 T003
- 새 이유 코드 7개, `REPORT_DETAIL_REQUIRED`를 칸 오류로 둔 것, 관리자 직접 숨김 버튼을 관리자 화면에만 둔 것 — 확인 작업 T004
- 자리로 남은 댓글의 대기 사건도 "대상 없음"으로 닫는 것, 정지 때 세션 삭제가 실패하면 정지하지 않고 503 — 확인 작업 T005

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 설정값, 팀 확인 질문

- [X] T001 선행 확인: V1 `report_case`(부분 UNIQUE 2개·`ck_report_case_target_ref`)·`report`(`uq_report_case_reporter`·`ck_report_detail`)·`member_suspension`·`post`/`comment`의 `hidden_*`, `B/account/application/SuspensionService.java`(001 T106), `B/shared/security/AdminPath*.java`(004 US7), 006 `B/post/application/spi/PostPurgeStep.java`·`B/moderation/application/ReportPostPurgeStep.java`, 007 `CommentModerationService`·`CommentDeleted`, `B/shared/event/`에 011이 만든 이벤트가 있는지 기록한다 (구현 메모: 모두 있음 — V1 report_case·report·member_suspension·hidden_*, 001 SuspensionService(TODO 상태), 004 AdminPath*, 006 PostPurgeStep·ReportPostPurgeStep(임시), 007 CommentModerationService·CommentDeleted, 011 ReportResolved·ContentHidden·ReportTargetType·ReportResult·reasonLabels.ts, 015 WithdrawalPurgeStep·InterimReportWithdrawalPurgeStep(임시). 새 마이그레이션 없음)
- [X] T002 [P] 설정값 `B/moderation/application/ModerationProperties.java`(`@ConfigurationProperties("blog.moderation")` + `@Validated`), `R/application.yml`에 research R14 기본값(끝에 추가), 테스트 `T/moderation/unit/ModerationPropertiesBindingTest.java`(기본값, 기간 목록에 `PERMANENT`, `per-minute` ≤ `per-day`)
- [X] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: 헌법 II 모듈 목록에 `moderation`(이 기능)·`notification`(011)을 더할지. 답이 오기 전에는 새 모듈로 진행한다 (구현 메모: ANALYSIS-tier-bc 팀 결정 항목에 이미 올라가 있어 공유 문서는 고치지 않고 기본안(새 모듈 moderation)으로 진행)
- [X] T004 팀 확인 질문: ① 새 이유 코드 7개(data-model §5) ② `REPORT_DETAIL_REQUIRED`를 `VALIDATION_FAILED`의 칸 오류로(spec은 최상위 코드) ③ 관리자 직접 숨김을 관리자 화면에서만(글 상세 버튼 없음 — 004 `PostActions` 표 변경 불필요). 답이 오기 전에는 기본안 (구현 메모: ANALYSIS-tier-bc 팀 결정 항목에 이미 있음. 기본안으로 진행 — 이유 코드는 ModerationReasonCode 5개 + AccountReasonCode 3개(CANNOT_SUSPEND_ADMIN·CANNOT_SUSPEND_WITHDRAWN·ALREADY_SUSPENDED), REPORT_DETAIL_REQUIRED는 VALIDATION_FAILED의 detail 칸 오류, 직접 숨김은 관리자 화면에서만)
- [X] T005 팀 확인 질문: ① 자리로 남은(본인 삭제) 댓글의 대기 사건도 "대상 없음"으로 닫기(원문은 완전 삭제만) ② 정지 때 세션 삭제가 실패하면 정지하지 않고 503(헌법 V "장애 때 통과"와 반대 방향 — 보안 조치라 반쯤 된 상태를 남기지 않음). 답이 오기 전에는 기본안 (구현 메모: ANALYSIS-tier-bc 팀 결정 항목에 이미 있음. 기본안으로 진행. 다만 세션 삭제는 '커밋 전'이 아니라 트랜잭션 시작 전에 한 번(실패면 503·DB 변화 없음), 커밋 뒤 한 번 더 한다 — 트랜잭션 안 Redis 삭제 금지 규칙(blog.redis.fail-on-write-in-transaction) 때문)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모듈 뼈대, 이벤트, 글 숨김 Service, 사건 저장소, 다른 모듈 맞춤 — 모든 User Story가 쓴다

**⚠️ CRITICAL**: 이 Phase가 끝나기 전에는 User Story 작업을 시작하지 않는다

- [X] T006 [P] 007 맞춤: `B/interaction/application/CommentModerationService.java`의 `snapshot(commentId)`이 `CommentSnapshot(commentId, postId, authorId, content, deleted, hidden, authorWithdrawn)`을 돌려주고 `hiddenOf(Collection<Long>)`이 있는지 확인하고, 없으면 더한다(007 소유 파일, 추가만 — 007이 먼저면 확인만). 테스트는 007 `CommentModerationServiceIT`에 두 경우 추가 (구현 메모: 007이 먼저 만든 CommentSnapshot에서 createdAt을 빼고 deleted·hidden·authorWithdrawn을 더했다(authorWithdrawn은 MemberQueryService.findDisplays). hiddenOf 추가, hide·unhide는 바뀌었는지 boolean을 돌려준다)
- [X] T007 [P] 이벤트 `B/shared/event/ReportResolved.java`·`ContentHidden.java`·`ContentUnhidden.java`·`MemberSuspended.java`, enum `ReportTargetType`·`ReportResult`(data-model §6). 011 T045가 먼저 만들었으면 필드를 대조만 하고 `ContentUnhidden`·`MemberSuspended`만 더한다 (구현 메모: 011이 먼저 만든 ReportResolved·ContentHidden·ReportTargetType·ReportResult는 필드 대조만(같음). ContentUnhidden(targetType, targetId, ownerId, postId, unhiddenAt)·MemberSuspended(memberId, until, suspendedAt) 추가)
- [X] T008 [P] 모듈 뼈대 `B/moderation/package-info.java`(006 문구를 "moderation 모듈: 신고·숨김·정지 관리(014)"로), `B/moderation/domain/ReportReason.java`·`CaseStatus.java`·`ResolutionAction.java`·`ReportTarget.java`·`TargetState.java`, `B/moderation/application/ModerationReasonCode.java`(data-model §5), 테스트 `T/moderation/unit/ReportReasonTest.java`(V1 `ck_report_reason` 6개와 같음, `hidden_reason` 30자 이내) (구현 메모: TargetState는 글·댓글 값을 한 enum에 모았다(PUBLIC·PRIVATE·TRASHED·HIDDEN·AUTHOR_WITHDRAWN·GONE·VISIBLE·DELETED·POST_NOT_VISIBLE). ModerationReasonCode는 정지 관련 3개를 뺀 5개(정지 3개는 account))
- [X] T009 `B/moderation/application/ReportPostPurgeStep.java` 소유를 넘겨받는다(**006 머지 후**): 클래스 주석의 "임시 구현·이전" 문구를 지우고, 닫는 SQL을 T013 `ReportCaseRepository.closeNoTarget…`으로 옮긴다. 006 `PostPurgeIT`이 그대로 통과하는지 확인 (구현 메모: Clock을 받아 ReportCaseRepository.closeNoTargetForPost/closeNoTargetForComments(1,000개씩)로 옮김. PostPurgeIT 6건 통과)
- [X] T010 [P] 테스트 `T/post/integration/PostModerationServiceIT.java`: `hide` → `hidden_*` 기록·`true`, 다시 → `false`, 휴지통 글도 숨김, `unhide` → NULL·`true`, 좋아요·댓글 수·`first_public_at`·`updated_at` 그대로, `snapshot` 제목 + 앞 2,000 코드 포인트(이모지 경계), `currentState` R7 표 6가지, `hiddenOf`
- [X] T011 `B/post/application/PostModerationService.java`와 `PostSnapshot.java`(post 모듈 새 공개 Service, 쿼리는 `B/post/infra/PostModerationRepository.java` 새 파일)(T010 통과) (구현 메모: PostSnapshot에 hidden·trashed를 더했다(직접 숨김의 이미 숨김 판정용). currentState는 post 모듈 자체 enum State를 돌려주고 moderation이 이름으로 TargetState에 옮긴다 — post→moderation 의존 순환을 피하려고)
- [X] T012 [P] 테스트 `T/moderation/integration/ReportCaseRepositoryIT.java`: `ON CONFLICT … DO NOTHING RETURNING`이 대기 사건이 있으면 빈 값, 없으면 새 번호, `HIDDEN` 사건이 있어도 새 `PENDING` 가능, 신고 중복 무시, 닫기 SQL 네 조건(contracts/moderation-sql.md §5), `ck_report_detail`이 `detail` NULL 통과
- [X] T013 `B/moderation/infra/ReportCaseRepository.java`·`ReportRepository.java`(`JdbcClient`, contracts/moderation-sql.md §1·§3·§4·§5)(T012 통과)
- [X] T014 [P] 001 추가: `B/account/application/MemberQueryService.java`에 `findAdminView`와 `AdminMemberInfo.java`(001 소유 파일, 추가만), 테스트 `T/account/integration/MemberQueryAdminIT.java`(익명 처리면 empty, 대문자 주소, 탈퇴 유예 포함). 목록의 주소·닉네임 묶음 조회는 007 `findDisplays`(007 T010)를 쓴다 — 새 `handlesOf`·`nicknamesOf`를 만들지 않는다(ANALYSIS-tier-bc) (구현 메모: findAdminViewById(long)도 더했다(처리 화면 작성자 카드 — 익명 처리 회원은 주소·닉네임 null))
- [X] T015 [P] 화면 공용: `F/features/moderation/reasonLabels.ts`(6개 코드 → 이름, 011 T047과 같은 파일 — 먼저 하는 쪽이 만듦)와 테스트, `F/api/types/moderation.ts`(contracts 스키마 타입) (구현 메모: reasonLabels.ts는 011 것을 그대로 쓰고 테스트만 더함. 응답 타입은 F/api/types/moderation.ts)

**Checkpoint**: 사건 저장·글 숨김·이벤트 준비됨 — User Story 시작 가능

---

## Phase 3: User Story 1 - 문제 있는 글·댓글 신고하기 (Priority: P1) 🎯 MVP

**Goal**: 인증 회원이 남의 글·댓글을 사유와 함께 신고하고, 같은 대상에는 대기 사건 하나에 신고가 모인다

**Independent Test**: 인증 회원·인증 전 회원·비회원·작성자로 같은 공개 글을 신고해 200·403·401·400이 나오고, 같은 회원이 두 번 신고해도 신고가 1건인지 본다

### Tests for User Story 1 ⚠️

> **NOTE: 테스트를 먼저 쓰고 실패를 확인한다**

- [X] T016 [P] [US1] 테스트 `T/moderation/integration/ReportApiIT.java`: US1 #1~#7 — quickstart §2 표 `ReportApiIT` 행 전부(스냅샷 내용, 중복 200·1건, 기타 칸 오류, 자기 것 400, 401·403, 볼 수 없는 대상 404 고정 본문, 1분 6번째·하루 51번째 429 + `Retry-After`, Redis 정지 중 통과, 처리 뒤 새 사건, 접수 이벤트 0, 신고가 10건 쌓여도 대상은 숨겨지지 않음 — FR-017), 판정 순서(인증 전 회원의 자기 글 → 403, 형식 오류인 남의 비공개 글 → 400, 숨김 글 + 기타 빈 설명 → 404), 기타가 아닌 사유의 설명은 저장 안 됨 (구현 메모: Redis 정지 중 통과는 HTTP로 하면 세션을 못 읽어 401이 되므로 ReportService를 Viewer로 직접 불러 확인한다. 하루 51번째는 Redis 키(ratelimit:report:{me}:1d)를 50으로 미리 넣어 재현)
- [X] T017 [P] [US1] 테스트 `T/moderation/integration/ReportConcurrencyIT.java`: 20명 동시 첫 신고 → 사건 1·신고 20, 같은 회원 동시 2번 → 1, 처리와 신고 동시 100번 → 신고가 닫힌 사건에 붙지 않거나(새 사건) 처리 전에 붙어 함께 닫힘, 교착 0 (구현 메모: 서비스를 직접 부른다. 처리와 신고 경쟁은 10회차 × (처리 1 + 신고 10) — 회원당 1분 5건 제한 때문에 회차마다 새 신고자. '닫힌 사건에 붙은 신고는 모두 ReportResolved가 났다'로 확인)
- [X] T018 [P] [US1] 화면 테스트 `F/features/moderation/__tests__/ReportDialog.test.tsx`·`ReportButton.test.tsx`: 사유 6개 라디오, 기타일 때만 설명 칸·200자 카운터·빈 설명이면 [신고하기] 비활성, 성공 → 창 닫힘 + "신고가 접수됐어요. 검토 후 처리할게요", 404 → "볼 수 없는 글이에요", 429 → "잠시 후 다시 시도해 주세요", 401·403 → `useAuthGate` 호출, Esc·[취소] → 요청 없음, 초점 가둠
- [X] T019 [P] [US1] 권한 매트릭스: `TR/permission/moderation.csv`에 `report.post` 행(research R13 표 — 7 행위자 × 9 대상 상태)과 하네스 행동 `T/moderation/permission/ReportPostAction.java`(거부 때 `report_case`·`report` 행 수 전후 같음), `T/moderation/integration/ModerationPermissionMatrixIT.java` (구현 메모: 실행기는 파일 하나 T/moderation/permission/ModerationActions.java에 중첩 클래스로 둔다(report.post·report.comment·admin.post.hide·admin.post.unhide·admin.comment.hide와 004 post-write.csv의 post.hide·post.unhide). 실제 값과 R13 표가 다른 곳: 작성자의 임시글 신고는 400 CANNOT_REPORT_OWN(표와 같음), 관리자 행동의 탈퇴 유예 행위자는 403이 아니라 004 경로 규칙의 404(경로 규칙이 계정 게이트보다 먼저 — 004 AdminPathIT과 같음). PermissionMatrixIT NOT_STARTED는 비움)

### Implementation for User Story 1

- [X] T020 [US1] `B/moderation/application/ReportTargetResolver.java`: 글 → 004 `PostReadService.requireReadable(postId, viewer)` + `PostModerationService.snapshot`, 댓글 → 007 `CommentModerationService.snapshot` + 그 글 `requireReadable` + 정상 댓글 확인. 아니면 404 예외 → `ReportTarget` (구현 메모: 댓글은 snapshot 뒤 그 글 requireReadable, 자리·탈퇴 작성자 댓글은 404, 숨긴 댓글은 작성자 본인이 아니면 404(본인이면 다음 단계에서 CANNOT_REPORT_OWN 400))
- [X] T021 [US1] `B/moderation/application/ReportService.java`(research R3 판정 1~8, R4 저장 — 2번 재시도, 스냅샷은 사건을 만들 때만) (구현 메모: 요청 DTO 칸을 Object로 받아 형식 오류를 칸 오류로 모은다(바디 파싱 실패 MALFORMED_REQUEST를 피하려고). 요청 제한 키 ratelimit:report:{me}:1m·:1d. 저장 중 FK 위반(판정 뒤 대상 완전 삭제)은 404)
- [X] T022 [US1] `B/moderation/web/ReportController.java`(`POST /api/reports`)와 `B/moderation/web/dto/ReportRequest.java`·`ReportAccepted.java`(T016·T017·T019 통과)
- [X] T023 [P] [US1] `F/api/reports.ts`(`createReport`)
- [X] T024 [US1] `F/features/moderation/useReport.ts`·`ReportDialog.tsx`·`ReportButton.tsx`(`targetType`·`targetId`를 받음, 375px 한 줄 배치)(T018 통과) (구현 메모: useReport는 결과를 accepted·failed·gated로 돌려주고, 404 문구는 글이면 '볼 수 없는 글이에요', 댓글이면 '볼 수 없는 댓글이에요'. viewer를 주면 비회원·인증 전 회원은 창 대신 안내)
- [X] T025 [US1] `F/pages/PostDetailPage.tsx`가 `ReactionBar`의 `reportButton`에 `ReportButton`을 넘긴다(004 `PostActions`의 표시 규칙 — 작성자에게 없음). 005 소유 파일, 005 회귀 `PostDetailPage.test.tsx` 함께 실행. 성공 알림 줄은 006 `useToast`(006 머지 후, 그 전에는 창 안 문구) (구현 메모: 006 useToast로 접수 안내. PostDetailPage 회귀 통과)
- [X] T026 [US1] 댓글 [신고]: `F/features/comments/CommentItem.tsx`의 버튼 자리에 `ReportButton`(`COMMENT`) — 작성자 본인 댓글·자리·숨김·탈퇴 작성자 댓글에는 없음(007 소유 파일, 007 머지 후). `comment.csv`의 신고 행(`report.comment`)을 `moderation.csv`에 더하고 행동 `T/moderation/permission/ReportCommentAction.java` (구현 메모: CommentItem에 선택 prop viewer를 더하고 CommentSection이 넘긴다. 007 테스트 두 곳('014 전 [신고] 없음' 단언)을 [신고]가 남의 정상 댓글에만 있는 단언으로 바꿨다)

**Checkpoint**: 신고가 쌓인다 — 관리자 처리 없이도 접수·중복·제한 확인 가능

---

## Phase 4: User Story 2 - 관리자가 쌓인 신고를 한 번에 판단하고 숨기기 (Priority: P1)

**Goal**: 관리자가 대상별 대기 사건을 스냅샷으로 보고 숨기기·문제없음으로 한 번에 닫고, 신고 없이도 볼 수 있는 글을 바로 숨긴다. 숨긴 글은 작성자 외 모두에게 사라진다

**Independent Test**: 같은 글에 3명이 신고한 뒤 관리자가 숨기기 → 신고 3건이 닫히고 `ReportResolved` 3·`ContentHidden` 1, 비회원·회원에게 그 글이 404

### Tests for User Story 2 ⚠️

- [X] T027 [P] [US2] 테스트 `T/moderation/integration/AdminAccessIT.java`: US2 #1, SC-007 — 004 `AdminPathIT` 표를 이 기능의 실제 경로(`/admin/reports`, `/admin/reports/1`, `/admin/members/a`, `/api/admin/reports`, `/api/admin/posts/1/hidden` PUT)로 다시: 비회원 같은 401, 일반 회원 같은 404 본문·`Cache-Control: private, no-store`, 관리자 통과, 서비스 두 번째 겹(경로 규칙을 끈 테스트 설정에서도 일반 회원 404) (구현 메모: '경로 규칙을 끈 테스트 설정'은 새 테스트 컨텍스트를 만들지 않으려고 Service를 일반 회원 Viewer로 직접 불러 404를 확인하는 것으로 대신했다)
- [X] T028 [P] [US2] 테스트 `T/moderation/integration/CaseResolutionIT.java`: quickstart §2 표 `CaseResolutionIT` 행 전부(대기 탭 정렬·커서·고아 제외, 상세에 현재 원문 없음·`currentState`·`reportedByMe`·`onlyMyReport`·작성자 정보, 숨기기 7건, 반려, 동시 처리 하나만 200, 자기 글 400, 혼자 신고 400·남 신고 섞이면 200, 대상 없음 409, 처리됨 탭 `handledByNickname`·`targetHiddenNow`), 응답 SQL 수(`SqlCounter`, 목록 ≤ 5) (구현 메모: 목록 SQL 수를 5 이하로 맞추려고 사유별 수를 대기 목록 SQL에 FILTER 집계로 함께 센다(처리됨 탭은 따로 stats 1번))
- [X] T029 [P] [US2] 테스트 `T/moderation/integration/DirectHideIT.java`(글 부분): 신고 없는 글 → 새 `HIDDEN` 사건(신고 0, 스냅샷 있음)·`ContentHidden` 1·`ReportResolved` 0, 대기 사건 있는 글 → 그 사건 `HIDDEN`·`ReportResolved` 신고마다, 남의 비공개 글·휴지통 글 404, 자기 글 400, 이미 숨김 200·이벤트 0
- [X] T030 [P] [US2] 테스트 `T/moderation/integration/HiddenContentVisibilityIT.java`(글 부분, SC-001): 숨긴 글이 비회원·회원·관리자에게 상세 404, 홈·블로그·태그·검색·트렌딩·sitemap·블로그 글 수에서 빠짐(있는 기능만 — 없는 목록은 `Assumptions.abort`), 작성자 상세 200 + `authorView.hidden = true`·`hiddenReason = SPAM`, 006 관리 목록 `hidden: true`, 작성자에게도 홈·블로그 목록에 없음 (구현 메모: 검색·태그·트렌딩·sitemap·관리 목록은 응답이 200일 때만 확인한다. 012·009·010 목록은 모두 004 VisibilityFilter(p.hidden_at IS NULL)를 쓰고 있어 고칠 곳이 없었다)
- [X] T031 [P] [US2] 화면 테스트 `F/pages/admin/__tests__/AdminReportsPage.test.tsx`·`AdminReportDetailPage.test.tsx`: 탭 전환(`?tab=`), 항목 표시(배지·제목·`@작성자`·신고 수·사유별 수·상대 시각), [더 보기] 중복 사건 거름, 스냅샷 텍스트로만(`<script>` 글자 그대로), "현재: 비공개", 숨기기 사유 없으면 버튼 비활성, 확인 창, 409 → "이미 처리된 신고예요" + 다시 불러오기, `onlyMyReport` → 버튼 비활성 + 문구, 대상 없음 표시 (구현 메모: 구현(T039)과 같은 묶음에서 썼다 — 화면 테스트가 처음부터 통과해 실패 확인 단계는 없었다)
- [X] T032 [P] [US2] 화면 테스트 `F/features/moderation/__tests__/HiddenReasonText.test.tsx`: 사유가 있으면 "운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요", 없으면 괄호 없음, 모르는 코드면 "기타"
- [X] T033 [P] [US2] 권한 매트릭스: `moderation.csv`에 `admin.post.hide` 행과 행동 `T/moderation/permission/AdminHidePostAction.java`

### Implementation for User Story 2

- [X] T034 [US2] `B/moderation/infra/CaseListQueryRepository.java`(contracts/moderation-sql.md §2)와 `B/moderation/application/CaseQueryService.java`(목록 두 탭·커서 `admin-reports:{tab}`·상세 조립 — 작성자 정보는 001 `findAdminView`·`SuspensionService.findOpen`·`history` 수, 숨겨진 수는 `report_case` 집계) (구현 메모: 응답 레코드(CaseListItem·CaseDetail·CasePage·HiddenState·AdminMemberView)는 web/dto가 아니라 application에 둔다 — application이 web에 의존하지 않게. tab 값은 계약대로 PENDING·HANDLED(대문자), 모르는 값은 400 VALIDATION_FAILED(tab))
- [X] T035 [US2] `B/moderation/application/CaseResolutionService.java`(research R6 1~9 — `PostModerationService.hide`, 댓글은 `CommentModerationService.hide`, 이벤트는 트랜잭션 안에서 `publishEvent`) (구현 메모: 다른 모듈 Service의 예외가 트랜잭션을 rollback-only로 만들지 않도록 CaseCloser가 숨기기 전에 스냅샷으로 대상 유무를 먼저 본다. 대상 없음 종료는 커밋한 뒤 409를 던진다)
- [X] T036 [US2] `B/moderation/application/DirectHideService.java`의 `hide(admin, type, id, reason)`(research R7 — 대상 판정은 T020 `ReportTargetResolver`를 관리자 `Viewer`로, 대기 사건 있으면 R6 7~9 재사용, 없으면 §4 새 사건) (구현 메모: 이미 숨김인 대상은 읽기 판정 없이 200 {hidden:true, caseId:null}(관리자도 숨긴 글을 읽을 수 없어서). 자기 콘텐츠 판정(400)이 볼 수 있는지 판정보다 먼저)
- [X] T037 [US2] `B/moderation/web/AdminReportController.java`(`GET /api/admin/reports`, `GET …/{caseId}`, `POST …/{caseId}/resolution`)·`AdminHideController.java`(`PUT /api/admin/posts/{postId}/hidden`)와 dto(`CaseListItem`·`CaseDetail`·`ResolutionRequest`·`HideRequest`·`HiddenState`). 모든 Service 첫머리에 `viewer.isAdmin()` 확인 + `requireActive(CONTENT_WRITE)`(T027~T030·T033 통과)
- [X] T038 [P] [US2] `F/api/admin.ts`(목록·상세·처리·숨김·해제·회원·정지·해제) (구현 메모: tab은 계약대로 PENDING·HANDLED를 보낸다(화면 주소는 ?tab=pending|handled). 정지·해제 응답은 계약대로 AdminMemberView)
- [X] T039 [US2] 화면 `F/pages/admin/AdminReportsPage.tsx`·`AdminReportDetailPage.tsx`와 `F/features/admin/CaseList.tsx`·`CaseSnapshot.tsx`(텍스트 노드, `white-space: pre-wrap`)·`ResolutionForm.tsx`·`adminText.ts`, 처리 확인은 006 `ConfirmDialog`(006 머지 후), "글 주소로 숨기기" 칸(직접 숨김)(T031 통과) (구현 메모: 목록 응답(CaseListItem)에 대상 번호가 없어 처리됨 탭 [숨김 해제]는 사건 상세를 먼저 불러 대상 번호를 얻은 뒤 DELETE한다. 위험 동작 채운 버튼 바탕으로 tokens.css에 --color-danger-fill(라이트·다크 같은 #c92a2a, --color-on-fill 글자 대비 4.5:1 이상)을 더하고 tokenContrast.test 쌍에 넣었다)
- [X] T040 [US2] `F/App.tsx`에 `/admin/reports`·`/admin/reports/:caseId`·`/admin/members/:handle`을 004 `AdminRouteGate` 아래에 더한다(**006 머지 후** — 같은 파일) (구현 메모: /admin 아래 중첩 라우트(index → /admin/reports, 모르는 하위 경로는 NotFoundPage). App.tsx의 Placeholder를 지웠다)
- [X] T041 [US2] `F/features/moderation/HiddenReasonText.tsx`와 `F/features/post-detail/AuthorStatusBanner.tsx`의 숨김 문장을 사유를 받는 조립으로 바꾼다(`HIDDEN_NOTICE` 상수 → 함수, `hiddenReasonSlot` 제거 또는 `HiddenReasonText`로 채움). 005 소유 파일, 005 회귀 `AuthorStatusBanner.test.tsx` 함께 실행(T032 통과) (구현 메모: hiddenReasonSlot prop을 지우고 AuthorStatusBanner가 HiddenReasonText에 authorView.hiddenReason을 넘긴다. HIDDEN_NOTICE는 사유 없는 문장으로 남겼다. 005 테스트 두 곳(AuthorStatusBanner·PostDetailPage.author)의 숨김 문장 단언을 사유 포함 문장으로 바꿨다)

**Checkpoint**: US1 + US2 — 신고부터 숨김까지 끝까지 동작(MVP)

---

## Phase 5: User Story 3 - 숨긴 댓글 표시 (Priority: P2)

**Goal**: 관리자가 댓글을 숨기면 다른 사람에게는 정해진 문구, 작성자에게는 원문과 안내가 보이고 답글은 유지된다

**Independent Test**: 답글이 달린 댓글을 숨긴 뒤 다른 회원·글 주인·댓글 작성자로 보고 문구·버튼·댓글 수를 확인

### Tests for User Story 3 ⚠️

- [X] T042 [P] [US3] `HiddenContentVisibilityIT`(댓글 부분, 007 머지 후): US3 #1~#3 — 숨긴 댓글이 다른 회원·글 주인에게 "운영 정책에 따라 숨겨진 댓글이에요"(내용·작성자 null), 작성자에게 원문 + 숨김 표시, 그 아래 답글 그대로, 숨긴 댓글 수정·답글 거부·삭제 허용, 댓글 수 −1 (구현 메모: 숨긴 댓글 수정·답글은 007 규칙대로 409·400(REPLY_TARGET_UNAVAILABLE)이라 4xx 범위로 확인)
- [X] T043 [P] [US3] `DirectHideIT`(댓글 부분)과 `CaseResolutionIT`에 댓글 사건 숨기기·반려 추가, `moderation.csv`에 `admin.comment.hide` 행과 행동 `T/moderation/permission/AdminHideCommentAction.java`

### Implementation for User Story 3

- [X] T044 [US3] `AdminHideController`에 `PUT /api/admin/comments/{commentId}/hidden`, `DirectHideService`·`CaseResolutionService`의 댓글 경로(007 `hide`가 삭제된 자리에서 NotFound → 대상 없음 처리)(T042·T043 통과)
- [X] T045 [US3] 관리자 상세 화면의 댓글 스냅샷 표시(제목 없음, 내용 전체), 처리됨 목록 댓글 항목 — `CaseSnapshot.tsx`·`CaseList.tsx`와 테스트 두 경우 추가

**Checkpoint**: 글·댓글 숨김 모두 동작

---

## Phase 6: User Story 4 - 숨김 해제와 원래대로 돌아오기 (Priority: P2)

**Goal**: 처리됨 탭에서 숨김을 해제하면 좋아요·댓글·목록 위치가 숨기기 전과 같고 알림은 없다

**Independent Test**: 좋아요 12·댓글 3 글을 숨겼다 해제해 수와 홈 위치가 처음과 같은지

### Tests for User Story 4 ⚠️

- [X] T046 [P] [US4] 테스트 `T/moderation/integration/UnhideRestoresIT.java`: quickstart §2 표 `UnhideRestoresIT` 행 전부(SC-006 — 좋아요·댓글 행 수·`like_count`·`comment_count`·홈 목록 순서 숨기기 전후 같음, 숨긴 글의 작성자 수정·다시 발행·공개 범위 변경·휴지통·복구 뒤에도 `hidden_at` 그대로), 해제 → `ContentUnhidden` 1·`ReportResolved`·`ContentHidden` 0·사건 상태 `HIDDEN` 그대로, 숨김 아닌 대상 해제 200·이벤트 0, 자기 콘텐츠 해제 400, 없는 대상 404, 댓글 해제 → 댓글 수 +1
- [X] T047 [P] [US4] 화면 테스트: 처리됨 탭 항목 `targetHiddenNow`면 [숨김 해제] → 확인 → `DELETE` → 버튼 사라짐, 사건 결과 표시는 "숨김" 그대로 — `AdminReportsPage.test.tsx`에 추가

### Implementation for User Story 4

- [X] T048 [US4] `DirectHideService.unhide(admin, type, id)`와 `AdminHideController`의 `DELETE …/posts/{postId}/hidden`·`…/comments/{commentId}/hidden`(research R7 — 사건 상태 그대로, `ContentUnhidden`)(T046 통과)
- [X] T049 [US4] 처리됨 탭 [숨김 해제] 버튼과 상세 화면 [숨김 해제] — `CaseList.tsx`·`AdminReportDetailPage.tsx`, 확인은 006 `ConfirmDialog`·결과 알림 줄은 006 `useToast`(006 머지 후)(T047 통과)

**Checkpoint**: 숨김을 되돌릴 수 있다

---

## Phase 7: User Story 5 - 회원 정지 (Priority: P3)

**Goal**: 관리자가 회원을 기간·사유와 함께 정지하면 모든 세션이 즉시 끊기고, 기한이 지나면 다음 로그인 때 풀린다

**Independent Test**: 회원을 7일 정지해 세션이 끊기고 로그인이 거부되는지, 기한 뒤 로그인하면 자동 해제되는지

### Tests for User Story 5 ⚠️

- [X] T050 [P] [US5] 테스트 `T/moderation/integration/SuspensionIT.java`: quickstart §2 표 `SuspensionIT` 행 전부(세션 3개 모두 끊김, 로그인 403 `details`, 영구, 기한 지남 자동 해제는 001 T108 결과 확인, 정지 회원 글 보임, 관리자·사유 없음·탈퇴 유예 400, 이미 정지 409, 동시 정지 → 열린 정지 1, `RedisOutage` 중 정지 → 503·DB 변화 없음, 해제 → `lifted_by`·`ACTIVE`, 알림·이벤트(`MemberSuspended` 1 외) 없음) (구현 메모: Redis 정지 중 정지는 HTTP로 하면 관리자 세션을 못 읽어 401이 되므로 SuspensionService를 직접 불러 TemporarilyUnavailableException·DB 변화 없음을 확인)
- [X] T051 [P] [US5] 화면 테스트 `F/pages/admin/__tests__/AdminMemberPage.test.tsx`: 정보·정지 이력 표, 정지 중이면 종료 시각(영구 표시)·사유·[정지 해제], 아니면 기간 4개 + 사유(필수, 200자) + [정지] 확인 창 "모든 기기에서 로그아웃돼요", 관리자 회원이면 정지 칸 없음, 400·409 문구, 상세 화면 작성자 카드 [회원 화면] 링크

### Implementation for User Story 5

- [X] T052 [US5] `B/account/application/SuspensionService.java`의 `suspend`·`lift`·`history` 구현(contracts/moderation-sql.md §7 — 회원 행 잠금, 세션 삭제를 커밋 전 + 커밋 뒤 한 번 더, 실패 → `TEMPORARILY_UNAVAILABLE`)와 `SuspensionRecord.java`, 이유 코드 `CANNOT_SUSPEND_ADMIN`·`CANNOT_SUSPEND_WITHDRAWN`·`ALREADY_SUSPENDED`는 `B/account/application/AccountReasonCode.java`에 추가(001 소유 파일, 001 T106 TODO를 채움) (구현 메모: 세션 삭제는 트랜잭션 시작 전에 한 번(실패면 503·DB 변화 없음) + RedisGuard.runAfterCommit로 커밋 뒤 한 번 더(실패는 WARN). plan R10의 '커밋 전'은 트랜잭션 안 Redis 쓰기 금지 규칙과 부딪혀 이렇게 바꿨다. 기존 TODO 시그니처는 호출하는 곳이 없어 새 시그니처 suspend(memberId, reason, SuspensionDuration, adminId, now)로 바꿨다. SuspensionDuration enum은 모듈 순환을 피하려고 account에 둔다)
- [X] T053 [US5] `B/moderation/application/AdminMemberService.java`(조회 = `findAdminView` + 숨겨진 수 + 이력, 정지·해제는 `SuspensionService`)와 `B/moderation/web/AdminMemberController.java`(`GET /api/admin/members/{handle}`, `POST …/suspensions` 201, `DELETE …/suspensions/current`), dto `AdminMemberView`·`SuspendRequest`(T050 통과)
- [X] T054 [US5] 화면 `F/pages/admin/AdminMemberPage.tsx`, `F/features/admin/SuspendForm.tsx`·`SuspensionHistory.tsx`, 상세 화면 작성자 카드에 [회원 화면], 정지·해제 확인은 006 `ConfirmDialog`(006 머지 후)(T051 통과)

**Checkpoint**: 모든 User Story 동작

---

## Phase 8: 대상 변화와 기록 보관 (FR-031~FR-034)

**Purpose**: 대상이 사라진 사건 자동 종료, 30일 보관 정리, 탈퇴 정리 단계

- [X] T055 [P] 테스트 `T/moderation/integration/OrphanCaseIT.java`: quickstart §2 표 `OrphanCaseIT` 행 전부(글 영구 삭제 → 그 글·댓글 사건 닫힘·스냅샷 남음, 댓글 본인 삭제 행·자리 → 닫힘(Awaitility), 탈퇴 order 80, 배치 고아 정리, 휴지통 이동만으로는 대기, 비공개 전환은 대기 유지 — FR-031)
- [X] T056 [P] 테스트 `T/moderation/integration/ReportSnapshotCleanupIT.java`: 31일 처리 사건 스냅샷·설명 NULL, 29일 그대로, 대기 그대로, 1,000건 넘게 반복, ShedLock 이름·시각, 로그 건수만
- [X] T057 `B/moderation/application/OrphanCaseCloser.java`(007 `CommentDeleted` 구독, `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`)(T055 통과) (구현 메모: @TransactionalEventListener에는 @Transactional을 붙이지 않는다(Spring 6.1+ 시작 실패). UPDATE 한 번이라 자동 커밋)
- [X] T058 `B/moderation/application/ReportSnapshotCleanupJob.java`(04:45 `zone = "${blog.time-zone}"`, ShedLock `reportSnapshotCleanup`, contracts/moderation-sql.md §8)(T056 통과)
- [X] T059 [P] 테스트 `T/moderation/integration/ReporterAnonymityIT.java`(SC-004): 신고 3건 처리 뒤 작성자가 받는 상세 `authorView`·006 관리 목록·011 알림 목록(있으면)·`ContentHidden` 이벤트 필드에 신고자 번호·닉네임·신고 수가 없다
- [X] T060 FR-034 확인: 작성자가 탈퇴 신청(015 없으면 `member.status = WITHDRAWN` 픽스처) → 대기 사건 유지·처리 화면 "현재: 작성자 탈퇴", 신고자가 탈퇴 신청 → 신고 기록 그대로 — `OrphanCaseIT`에 두 경우 추가
- [X] T061 [P] 확장점 확인: `B/shared/application/withdraw/WithdrawalPurgeStep.java`가 없으면 015 contracts/purge-steps.md §1 그대로 만든다(015 T009와 같은 파일 — 먼저 하는 쪽) (구현 메모: 015가 이미 만들어 둠 — 확인만)
- [X] T062 `B/moderation/application/ReportWithdrawalPurgeStep.java`(order 80, `MANDATORY`, contracts/moderation-sql.md §5 셋째 줄 — 사건 닫기 + 신고 설명 NULL, 로그는 회원 번호·건수만)와 `OrphanCaseIT` 탈퇴 경우(T061 다음) (구현 메모: 015 임시 InterimReportWithdrawalPurgeStep을 지웠다)

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 종단 확인, 다른 기능 인계

- [X] T063 [P] `E/report-hide.spec.ts`(Playwright): 회원 B 신고 → 관리자 K 처리 화면 → 숨기기 → B로 글 404 → 작성자 A 상세 숨김 안내 → K 해제 → B로 글 보임, 375px 가로 스크롤 없음 (구현 메모: desktop 프로젝트 1개 워커로 통과(4.5s). 작성자·독자·관리자 셋을 E2E_EMAIL·E2E_READER_EMAIL·E2E_ADMIN_EMAIL로 받고, 375px 확인은 독자 창을 375px로 열어 신고 창에서 한다)
- [X] T064 인계 확인: 011 `ModerationNotificationIT`가 이 기능이 내는 이벤트로 통과하는지(011 머지 후), 007 contracts/events.md `CommentDeleted` 구독자에 014가 있고 "015 `MemberPurged`가 대신" 문장이 고쳐졌는지, 015 contracts/purge-steps.md order 80 행이 T062와 같은지 기록한다 (구현 메모: 011 ModerationNotificationIT 5건 통과. 007 contracts/events.md CommentDeleted 구독자에 014 OrphanCaseCloser가 있고 탈퇴 정리 문장은 order 20·70·80으로 이미 고쳐져 있다. 015 contracts/purge-steps.md order 80 행(CLOSED_NO_TARGET + 신고 설명 NULL)이 ReportWithdrawalPurgeStep과 같다. 다른 기능 문서는 고치지 않았다)
- [X] T065 quickstart.md §1~§4 실행 결과를 기록하고 어긋난 문서를 고친다 (구현 메모: §1 새 마이그레이션 없음 확인. §2 백엔드 IT 전부 통과, vitest 7파일 34건 통과, E2E 통과. §3 수동 브라우저 확인은 E2E 흐름과 IT로 대신했다(사람이 직접 클릭하지 않음). §4는 OrphanCaseIT·HiddenContentVisibilityIT·AccountWithdrawalPurgeStepsIT로 확인. quickstart §2에 E2E 계정 환경 변수와 예약 닉네임 안내를 더했다)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2)**: Setup 다음 — 모든 User Story를 막는다. T009는 006 머지 후(다른 작업과 독립)
- **US1 (Phase 3)**: Foundational 다음. 댓글 신고 T026은 007 머지 후
- **US2 (Phase 4)**: Foundational 다음. 신고 데이터는 픽스처로 넣을 수 있어 US1과 동시 진행 가능. 경로 T040은 006 머지 후
- **US3 (Phase 5)**: US2(T035·T036) 다음, 007 머지 후
- **US4 (Phase 6)**: US2(T036) 다음
- **US5 (Phase 7)**: Foundational 다음, 001 US5(T106·T108) 다음. 다른 스토리와 독립
- **대상 변화·보관 (Phase 8)**: Foundational 다음. T057은 007, T062는 015 확장점(T061) 다음
- **Polish (Phase 9)**: 원하는 스토리가 끝난 뒤

### User Story Dependencies

- **US1 (P1)**: 독립
- **US2 (P1)**: 독립(신고 픽스처). 화면 처리 흐름은 US1과 함께 끝까지 확인
- **US3 (P2)**: US2 처리·직접 숨김 위에 댓글 경로를 더한다
- **US4 (P2)**: US2 숨김 위에 해제를 더한다
- **US5 (P3)**: 독립

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 같은 파일을 고치는 작업은 순서대로 한다: `ReportCaseRepository`(T013 → T009), `DirectHideService`(T036 → T044 → T048), `CaseResolutionService`(T035 → T044), `AdminHideController`(T037 → T044 → T048), `moderation.csv`(T019 → T026 → T033 → T043), `HiddenContentVisibilityIT`(T030 → T042), `DirectHideIT`(T029 → T043), `OrphanCaseIT`(T055 → T060 → T062), `CaseList.tsx`(T039 → T045 → T049), `AdminReportsPage.test.tsx`(T031 → T047), `F/api/admin.ts`(T038), 001 파일(`MemberQueryService` T014, `SuspensionService`·`AccountReasonCode` T052), 005 파일(`PostDetailPage` T025, `AuthorStatusBanner` T041), 007 파일(`CommentModerationService` T006, `CommentItem` T026), 006 파일(`ReportPostPurgeStep` T009, `F/App.tsx` T040)

### Parallel Opportunities

- Phase 2의 T006·T007·T008·T010·T012·T014·T015는 서로 다른 파일
- US1(신고)과 US2(관리자)는 서로 다른 팀원이 동시에 할 수 있다(공유는 `ReportTargetResolver` T020 — US2 T036이 그 뒤)
- US5(정지)와 Phase 8은 다른 스토리와 독립

---

## Parallel Example: User Story 2

```bash
# US2 테스트를 함께 쓴다
Task: "AdminAccessIT in T/moderation/integration/AdminAccessIT.java"
Task: "CaseResolutionIT in T/moderation/integration/CaseResolutionIT.java"
Task: "DirectHideIT in T/moderation/integration/DirectHideIT.java"
Task: "HiddenContentVisibilityIT in T/moderation/integration/HiddenContentVisibilityIT.java"
Task: "관리자 화면 테스트 in F/pages/admin/__tests__/"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. Phase 1·2 완료
2. US1: 신고 API·창·글 [신고]
3. US2: 관리자 목록·상세·처리·직접 숨김, 숨김 효과, 작성자 안내
4. **STOP and VALIDATE**: quickstart §3 1~11

### Incremental Delivery

1. US1 + US2 → MVP(신고 → 숨김)
2. US4(해제) → US3(댓글, 007 머지 후)
3. US5(정지) → Phase 8(대상 없음·보관) → Polish

### Parallel Team Strategy

1. 함께 Phase 1·2
2. 그 뒤: 개발자 A US1 → US3 → Phase 8, 개발자 B US2 → US4 → US5

---

## Notes

- [P] = 다른 파일, 의존 없음
- moderation은 `report_case`·`report`만 직접 쓴다. 글·댓글·회원은 공개 Service로만(헌법 II)
- 스냅샷·신고 설명은 관리자 화면에서도 텍스트로만 그린다(`dangerouslySetInnerHTML` 금지, 헌법 IV)
- 작성자에게 가는 응답·알림·이벤트에 신고자 정보를 넣지 않는다(T059가 검사)
- 각 작업 또는 논리 묶음마다 커밋한다
