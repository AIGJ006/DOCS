# Research: 신고·관리자 숨김·회원 정지

**Feature**: 014-report-hide | **Date**: 2026-10-08

spec Implementation Notes·Clarifications와 원문(43·13·20·21·25·42·51)에서 정한 것은 "확정", 이 계획이 새로 정한 것은 "제안"으로 표시한다.

## R1. 저장 (확정)

- **Decision**: 스키마 변경 없음(V1).
  - `report_case`: 대상마다 사건. `target_type` POST|COMMENT, `post_id`·`comment_id`(FK `ON DELETE SET NULL`, `ck_report_case_target_ref`로 둘 중 하나만), `target_author_id`(FK RESTRICT — 회원 행은 익명 처리 뒤에도 남음), `snapshot_title`(100)·`snapshot_content`(2000), `status` PENDING|HIDDEN|REJECTED|CLOSED_NO_TARGET, `handled_by`·`handled_at`.
  - 대기 사건 하나: `uq_report_case_open_post (post_id) WHERE status = 'PENDING' AND post_id IS NOT NULL`, `uq_report_case_open_comment`(같은 모양). 동시에 들어온 첫 신고 둘이 사건을 둘 만들지 못한다(E4).
  - `report`: 신고자 한 명의 신고. `uq_report_case_reporter (case_id, reporter_id)`, `ck_report_reason` 6개, `ck_report_detail CHECK (reason <> 'OTHER' OR length(btrim(detail)) > 0)` — `detail`이 NULL이면 CHECK 결과가 NULL이라 통과한다. 그래서 "기타면 설명 필수"는 Service가 검사하고, 30일 뒤 `detail = NULL`도 막히지 않는다(51 "원문 유지와 표기 판단").
  - `member_suspension`: 이력. 회원당 열린 정지(`lifted_at IS NULL`) 하나는 DB 제약이 없어 Service 규칙(E5).
  - `post`·`comment`의 `hidden_at`·`hidden_by`(FK RESTRICT)·`hidden_reason varchar(30)` — 사유 코드 6개를 그대로 저장(Clarifications Q3).
  - 인덱스: `ix_report_case_pending (created_at) WHERE PENDING`, `ix_report_case_post`·`ix_report_case_comment`, `ix_report_case_target_author`, `ix_report_reporter`, `ix_member_suspension_member (member_id, started_at DESC)`. 처리됨 목록(`handled_at DESC`)과 30일 정리용 인덱스는 없다 — 사건 수가 수천 건이라 전체 훑기로 충분하고, 느려지면 V3 이후 후보(13 §2-5 "전용 인덱스 없음").
- **Rationale**: 2026-10-07 회의 E1·E3·E4·E5·E8, 51 §2·§3.

## R2. 모듈 배치 (제안)

- **Decision**:
  - 새 모듈 `com.team.blog.moderation`(006 T061이 임시로 쓴 이름 확정). `report_case`·`report`는 이 모듈만 읽고 쓴다.
  - 글 숨김: post 모듈 새 공개 Service `post.application.PostModerationService` — `hide(postId, adminId, reason, now)`(이미 숨김이면 false, 멱등), `unhide(postId)`, `snapshot(postId)` → `PostSnapshot(id, authorId, title, contentHead)`, `currentState(postId)` → `TargetState`. 숨김은 글 행 `FOR UPDATE` 후 `hidden_*`만 바꾼다(카운터 변화 없음).
  - 댓글 숨김: 007 `CommentModerationService.hide/unhide/snapshot`(007 contracts/events.md §2-1). `snapshot`이 돌려줄 값을 이 기능이 정한다: `CommentSnapshot(commentId, postId, authorId, content, deleted, hidden, authorWithdrawn)` — 007과 맞추는 작업은 먼저 하는 쪽(T006).
  - 정지: 001 `SuspensionService`(001 T106이 `suspend`·`lift` 시그니처와 TODO를 둔다) — 이 기능이 구현을 채운다. 세션 삭제는 001 `SessionTerminator`.
  - 회원 정보: 001 `MemberQueryService`에 `handlesOf(Collection<Long>)` → `Map<Long, String>`(목록의 작성자 주소)와 `findAdminView(handle)` → `AdminMemberInfo(id, handle, nickname, role, status, createdAt, withdrawnAt)`(익명 처리된 회원은 empty)를 더한다(001 소유 파일, 추가만).
  - 대상 판정: 글은 004 `PostReadService.requireReadable(postId, viewer)`, 댓글은 그 댓글의 글을 같은 판정으로 + 댓글이 정상(삭제·숨김·작성자 탈퇴 유예 아님).
- **Rationale**: 헌법 II, 006 research R11, 007 contracts/events.md §2, 001 tasks T106.
- **Alternatives considered**: 숨김 SQL을 moderation이 직접 `UPDATE post` — 원칙 II 위반이고, 댓글 수 카운터(007)가 어긋난다.

## R3. 신고 API와 판정 순서 (확정 + 제안)

- **Decision**: `POST /api/reports` `{targetType: POST|COMMENT, targetId, reason, detail}` → 200 `{accepted: true}`(새 신고·중복 모두).
  1. 401 `LOGIN_REQUIRED`
  2. 403 — `AccountStatusGuard.requireActive(me, CONTENT_WRITE)`: 인증 전 `EMAIL_NOT_VERIFIED`, 남은 세션 정지 `ACCOUNT_SUSPENDED`, 탈퇴 유예 `ACCOUNT_WITHDRAWN`(001 게이트)
  3. 400 `VALIDATION_FAILED` — `targetType`·`reason` 값 밖, `targetId` 숫자 아님, `detail` 200자 초과(코드 포인트, 앞뒤 공백 제거 뒤)
  4. 404 `NOT_FOUND`(고정 본문) — 대상 없음·볼 수 없음(비공개·임시·휴지통·숨김·작성자 탈퇴 유예, 댓글은 삭제·숨김·작성자 탈퇴 유예도)
  5. 400 `CANNOT_REPORT_OWN` "내 글이나 댓글은 신고할 수 없어요"
  6. 400 `VALIDATION_FAILED` `errors[{field: detail, code: REPORT_DETAIL_REQUIRED, message: "기타 사유를 적어 주세요"}]` — 기타인데 설명이 비었음
  7. 429 `TOO_MANY_REQUESTS` — `ratelimit:report:{memberId}:1m` 5번, `…:1d` 50번(001 `RateLimiter`, Redis 장애면 통과, `Retry-After`)
  8. 저장(R4)
- 횟수는 7까지 온 요청만 센다(형식 오류·404·자기 신고는 세지 않음). 새 신고·중복은 가리지 않는다(spec Assumptions).
- 기타가 아닌 사유의 설명은 저장하지 않는다(NULL) — 설명 칸은 기타일 때만 화면에 나온다.
- **Rationale**: 43 §2, 42 §3 판정 순서·§8, README 2026-10-07(429 맨 끝), 001 칸 오류 방식(plan 설계 후 확인 1).

## R4. 신고 저장 (확정 + 제안)

- **Decision**: 한 트랜잭션:
  1. 대기 사건 만들기 시도: `INSERT INTO report_case (target_type, post_id|comment_id, target_author_id, snapshot_title, snapshot_content) VALUES … ON CONFLICT (post_id) WHERE status = 'PENDING' AND post_id IS NOT NULL DO NOTHING RETURNING id`(댓글은 `comment_id` 쪽 부분 UNIQUE).
  2. 돌아온 행이 없으면 `SELECT id FROM report_case WHERE post_id = :id AND status = 'PENDING' FOR UPDATE`. 그 사이 처리돼 없으면 1로 한 번 더(최대 2번, 그래도 없으면 503 — 실제로는 일어나지 않음).
  3. 1에서 사건을 만들었을 때만 스냅샷을 넣는다(첫 신고 시점, FR-008). 글: 제목, `content_md` 앞 2,000 코드 포인트. 댓글: 내용 전체(`snapshot_title` NULL).
  4. `INSERT INTO report (case_id, reporter_id, reason, detail) VALUES … ON CONFLICT (case_id, reporter_id) DO NOTHING` — 같은 회원의 두 번째 신고는 아무것도 안 바뀌고 200(FR-007, SC-002). 사유가 달라도 처음 것을 둔다.
- 사건 행 `FOR UPDATE`(2)·새 행(1)은 처리(R6)의 `FOR UPDATE`와 순서를 맞춰, 처리 중인 사건에 새 신고가 붙지 않는다. 처리된 뒤의 신고는 새 사건이 된다(E4).
- 접수는 알림·이벤트를 만들지 않는다(FR-010).
- **Rationale**: 43 §2, E1·E4, SC-002.

## R5. 관리자 경로와 API (확정 + 제안)

- **Decision**:
  - 화면 `/admin/reports`, `/admin/reports/{caseId}`, `/admin/members/{handle}`. API `/api/admin/**`. 004 US7(T063~T067)의 `AdminPathSecurityCustomizer`가 비회원 401·일반 회원 404를 처리하고, 각 Service가 `viewer.isAdmin()`을 다시 확인한다(두 번째 겹). 관리자도 `AccountStatusGuard.requireActive(me, CONTENT_WRITE)`.
  - API(제안):
    | API | 본문 | 성공 |
    |---|---|---|
    | `GET /api/admin/reports?tab=PENDING\|HANDLED&cursor=` | — | 200 `{items: [CaseListItem], nextCursor}` 20개 |
    | `GET /api/admin/reports/{caseId}` | — | 200 `CaseDetail` |
    | `POST /api/admin/reports/{caseId}/resolution` | `{action: HIDE\|REJECT, reason}` (HIDE면 사유 필수) | 200 `CaseDetail` |
    | `PUT /api/admin/posts/{postId}/hidden` | `{reason}` | 200 `{hidden: true, caseId}` |
    | `DELETE /api/admin/posts/{postId}/hidden` | — | 200 `{hidden: false}` |
    | `PUT`·`DELETE /api/admin/comments/{commentId}/hidden` | 같음 | 같음 |
    | `GET /api/admin/members/{handle}` | — | 200 `AdminMemberView` |
    | `POST /api/admin/members/{handle}/suspensions` | `{duration: P1D\|P7D\|P30D\|PERMANENT, reason}` | 201 `AdminMemberView` |
    | `DELETE /api/admin/members/{handle}/suspensions/current` | — | 200 `AdminMemberView`(열린 정지가 없어도 200) |
  - 대기 목록: 대기 사건마다 신고 수·사유별 수·최근 신고 시각. 정렬 신고 수 DESC → 최근 신고 DESC → 사건 번호 DESC, 커서 키 `[count, lastReportedAt, id]`(001 `CursorCodec`, 목록 구분 `admin-reports:pending`). 넘기는 사이 신고 수가 바뀌면 같은 사건이 두 번 보이거나 빠질 수 있다 — 관리자 화면이라 받아들이고 화면은 사건 번호로 중복을 거른다.
  - 처리됨 목록: `status <> 'PENDING'` `handled_at DESC, id DESC`, 커서 키 `[handledAt, id]`. 항목에 결과·처리 관리자 닉네임(없으면 "자동")·대상이 지금 숨김인지(해제 버튼).
  - 상세: 스냅샷, 현재 상태(R7 표), 신고 수·사유별 수·기타 설명 목록(신고자 정보 없이 시각만), `reportedByMe`, `onlyMyReport`, 작성자 정보(주소·닉네임·가입일·이전에 숨겨진 수 = `count(*) FROM report_case WHERE target_author_id = :a AND status = 'HIDDEN'`·정지 이력·지금 정지 중인지). 현재 원문은 주지 않는다(FR-015).
- **Rationale**: 43 §3, 42 P-10, 004 research R-11·R-12(관리자 경로), FR-012~FR-015.

## R6. 처리 (확정)

- **Decision**: `CaseResolutionService.resolve(admin, caseId, action, reason)` 한 트랜잭션:
  1. 관리자 확인, 형식(HIDE면 `reason` 6개 중 하나, REJECT면 무시) — 400
  2. `SELECT … FROM report_case WHERE id = :id FOR UPDATE` — 없음 404
  3. `status <> 'PENDING'` → 409 `REPORT_ALREADY_HANDLED` "이미 처리된 신고예요"(`details {status}`). 늦은 쪽은 아무것도 바꾸지 않는다(FR-016, SC-003 — 두 번 누름·두 관리자 동시)
  4. 대상이 사라짐(`post_id`·`comment_id` 모두 NULL) → `CLOSED_NO_TARGET`으로 닫고 409 `REPORT_ALREADY_HANDLED` `details {status: CLOSED_NO_TARGET}`
  5. `target_author_id = admin` → 400 `CANNOT_MODERATE_OWN`
  6. 신고자가 관리자 자신뿐 → 400 `CANNOT_HANDLE_OWN_REPORT`(다른 회원 신고가 하나라도 있으면 통과 — Clarifications Q2)
  7. HIDE: `PostModerationService.hide` 또는 `CommentModerationService.hide`(삭제된 자리 댓글이면 007이 NotFound → 사건을 `CLOSED_NO_TARGET`으로 닫고 409, 4와 같음). REJECT: 대상은 그대로
  8. `UPDATE report_case SET status = :HIDDEN|REJECTED, handled_by = :admin, handled_at = now() WHERE id = :id`
  9. 이벤트(커밋 뒤 구독): 신고마다 `ReportResolved(reportId, reporterId, targetType, targetId, ACTION_TAKEN|NO_VIOLATION, now)`, HIDE이고 7에서 새로 숨겼으면 `ContentHidden(targetType, targetId, ownerId, postId, now)` 1번(신고자 정보 없음)
- 정지는 같은 화면에서 따로 부른다(R10). 숨기기와 정지를 한 요청에 묶지 않는다 — 한쪽 실패가 다른 쪽을 되돌리지 않게.
- 처리 결과는 신고 한 건마다 알림 하나(FR-028) — 사건에 신고 7건이면 이벤트 7개.
- **Rationale**: 43 §3·§7, H-3·H-12, 20 §3-5, FR-016·FR-019·FR-028·FR-029.

## R7. 직접 숨김·해제와 현재 상태 (Clarifications Q1 + 제안)

- **Decision**:
  - `PUT /api/admin/{posts|comments}/{id}/hidden {reason}`: 관리자 확인 → 대상을 관리자 `Viewer`로 볼 수 있나(글: `requireReadable`, 댓글: 그 글 + 댓글 정상) — 아니면 404(남의 비공개 글 포함) → 자기 콘텐츠 400 `CANNOT_MODERATE_OWN` → 대기 사건이 있으면(`FOR UPDATE`) R6 7~9와 같이 그 사건을 `HIDDEN`으로 닫음, 없으면 사건을 새로 만들고(스냅샷 포함, 신고 0건) 바로 `HIDDEN` + `handled_by/at` → `ContentHidden` 1번. 이미 숨김이면 200 그대로(사건·이벤트 없음).
  - `DELETE …/hidden`: 관리자 확인 → 대상이 있고(휴지통 포함) 숨김이면 `unhide`, 아니면 200 그대로. 자기 콘텐츠 400. 대상이 아예 없으면 404. 사건 상태는 `HIDDEN` 그대로(spec Assumptions — 기록 유지). `ContentUnhidden(targetType, targetId, ownerId, postId, now)` 발행, 알림 없음(FR-030).
  - 해제 뒤 노출·좋아요·댓글·목록 위치는 숨김이 행을 지우거나 `first_public_at`을 바꾸지 않으므로 그대로 돌아온다. 댓글 숨김·해제의 `comment_count` ±1은 007(FR-026·FR-027).
  - 숨긴 글을 작성자가 고치고 다시 발행·공개 범위 변경·휴지통·복구해도 `hidden_*`는 그대로(FR-023) — 002·004·006 SQL은 `hidden_*`를 건드리지 않는다. 이 기능이 회귀 테스트로 확인한다.
  - 현재 상태(상세 화면 "현재: …"):
    | 대상 | 판정 순서 → 이름 |
    |---|---|
    | 글 | 사라짐 → `GONE` · 작성자 탈퇴 유예 → `AUTHOR_WITHDRAWN` · 숨김 → `HIDDEN` · 휴지통 → `TRASHED` · 비공개 → `PRIVATE` · 그 밖 → `PUBLIC` |
    | 댓글 | 사라짐 → `GONE` · 자리(삭제) → `DELETED` · 작성자 탈퇴 유예 → `AUTHOR_WITHDRAWN` · 숨김 → `HIDDEN` · 글을 비회원이 볼 수 없음 → `POST_NOT_VISIBLE` · 그 밖 → `VISIBLE` |
- **Rationale**: Clarifications Q1(2026-10-08), FR-020·FR-023·FR-027·FR-030, 42 §5-2·§6·§11.

## R8. 대상 없음 자동 종료 (확정 + 제안)

- **Decision**: 닫는 SQL은 하나: `UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL WHERE status = 'PENDING' AND <조건>`. 이벤트·알림 없음(FR-030).
  | 언제 | 조건 | 어디서 |
  |---|---|---|
  | 글 완전 삭제(영구 삭제·30일 휴지통·탈퇴 order 10) | `post_id = :p` + `comment_id IN (그 글 댓글)` | `ReportPostPurgeStep`(006 `PostPurgeStep` order 10, 006 임시 구현을 넘겨받음 — **006 머지 후**). 같은 트랜잭션, DELETE 전 |
  | 댓글 본인 삭제(자리·행 삭제·빈 자리 정리) | `comment_id = :c OR (post_id IS NULL AND comment_id IS NULL)` | `OrphanCaseCloser`가 007 `CommentDeleted` 구독(커밋 뒤, `eventExecutor`). 행이 지워졌으면 FK가 이미 NULL이라 둘째 조건이 잡는다 |
  | 탈퇴 30일 정리 | `target_author_id = :m` + `UPDATE report SET detail = NULL WHERE reporter_id = :m` | `ReportWithdrawalPurgeStep`(015 order 80, `MANDATORY`) |
  | 매일 04:45 | `post_id IS NULL AND comment_id IS NULL` | `ReportSnapshotCleanupJob` 첫 단계(구독 유실·015 order 20 댓글 정리로 남은 것) |
  | 처리 시도(R6 4·7) | 그 사건 | `CaseResolutionService` |
- 자리로 남긴 댓글(`deleted_at` 있음)도 "대상 없음"으로 닫는다(plan 설계 후 확인 4). 관리자는 숨길 내용이 없다.
- 글이 휴지통에 있는 동안은 대기를 유지한다(복구될 수 있음, 현재 상태 `TRASHED`).
- **Rationale**: 13 §2-5, 43 §5, FR-032, 006 contracts/events.md §2, 015 contracts/purge-steps.md §2.

## R9. 보관 정리 (확정)

- **Decision**: `ReportSnapshotCleanupJob` 매일 04:45 KST(`zone = "${blog.time-zone}"`), ShedLock `reportSnapshotCleanup`(`lockAtMostFor` 10분):
  1. R8 고아 사건 닫기
  2. `UPDATE report SET detail = NULL WHERE detail IS NOT NULL AND case_id IN (SELECT id FROM report_case WHERE status <> 'PENDING' AND handled_at < now() - :retention)`
  3. `UPDATE report_case SET snapshot_title = NULL, snapshot_content = NULL WHERE status <> 'PENDING' AND handled_at < now() - :retention AND (snapshot_title IS NOT NULL OR snapshot_content IS NOT NULL)`
  - 사건·상태·사유·처리 일자·대상 작성자는 남긴다(FR-033). 1,000건씩 나눠 반복(`LIMIT` 서브쿼리), 로그는 건수만.
- 시각 04:45는 006(03:30)·011(04:30)과 겹치지 않게 골랐다(제안).
- **Rationale**: 13 §2-5, 2026-10-07 H5, SC-008.

## R10. 회원 정지 (확정 + 제안)

- **Decision**: `SuspensionService`(001 소유 파일, 001 T106이 만든 시그니처를 채움):
  - `suspend(memberId, reason, Duration|PERMANENT, adminId, now)`: 회원 행 `FOR UPDATE` → 관리자 → 400 `CANNOT_SUSPEND_ADMIN`(자기 자신 포함), 탈퇴 유예(`WITHDRAWN`) → 400 `CANNOT_SUSPEND_WITHDRAWN`(FR-041, `ck_member_withdrawn`이 막기 전에), 열린 정지 있음 → 409 `ALREADY_SUSPENDED` → `INSERT member_suspension (member_id, reason, started_at, ends_at, suspended_by)` + `UPDATE member SET status = 'SUSPENDED'` → `SessionTerminator.terminateAll(memberId, Optional.empty())` → `MemberSuspended(memberId, endsAt, now)`.
  - 세션 삭제는 커밋 전에 같은 흐름에서 한다. Redis 장애로 실패하면 예외 → 롤백 → 503 `TEMPORARILY_UNAVAILABLE`(plan 설계 후 확인 5). 커밋 직후 한 번 더 지운다(커밋 사이에 새로 로그인한 세션 대비, 실패는 WARN). 그래도 남은 세션의 쓰기는 `AccountStatusGuard`가 DB 상태로 403.
  - `lift(memberId, adminId, now)`: 열린 정지가 없으면 그대로. 있으면 `lifted_at = now, lifted_by = admin`, `status = SUSPENDED`일 때만 `ACTIVE`. 알림·이벤트 없음(FR-030).
  - 기한 지남 자동 해제(`lifted_by` NULL)와 로그인 거부 403 `ACCOUNT_SUSPENDED` `details {endsAt, reason}`, 화면 문구는 001 T108·T110. 이 기능은 테스트로 확인만 한다.
  - 사유 1~200자(앞뒤 공백 제거), 기간 `P1D`·`P7D`·`P30D`·`PERMANENT`(설정 `blog.moderation.suspension-durations`).
- 정지 회원의 글·댓글은 그대로 보인다(FR-036 — 공용 조건에 정지가 없음).
- **Rationale**: 43 §6, 07 §6, 42 P-7, 20 §3-5, E3·E5, FR-035~FR-041.

## R11. 이벤트 (확정)

- **Decision**: `shared.event`(record, 필드는 ID·enum·`Instant`):
  | 이벤트 | 필드 | 발행 | 구독 |
  |---|---|---|---|
  | `ReportResolved` | `reportId, reporterId, targetType, targetId, result, resolvedAt` | 처리(신고마다) | 011 `REPORT_RESOLVED` |
  | `ContentHidden` | `targetType, targetId, ownerId, postId, hiddenAt` | 처리 숨김·직접 숨김(새로 숨겼을 때만) | 011 `CONTENT_HIDDEN`(+ 댓글이면 그 댓글 알림 삭제) |
  | `ContentUnhidden` | `targetType, targetId, ownerId, postId, unhiddenAt` | 해제 | 없음(011·012 구독 안 함) |
  | `MemberSuspended` | `memberId, until, suspendedAt` | 정지 | 없음(세션은 직접 삭제) |
  - enum `ReportTargetType {POST, COMMENT}`, `ReportResult {ACTION_TAKEN, NO_VIOLATION}`. `CLOSED_NO_TARGET`은 이벤트 없음. 43 §7의 `reason` 필드는 20 EV-3에 따라 넣지 않는다(사유는 011이 보여 줄 때 `hidden_reason`에서 읽음).
  - 011 T045와 같은 파일 — 먼저 하는 쪽이 만들고 다른 쪽은 확인만(T007).
- **Rationale**: 20 §3-5, 25, 011 data-model §3.

## R12. 화면 (확정 + 제안)

- **Decision**:
  - [신고] 버튼: 글은 005 `ReactionBar.reportButton` 자리(004 `PostActions` 표시 규칙 — 작성자에게 없음), 댓글은 007 `CommentItem`의 버튼 자리(작성자 본인 댓글·자리·숨김·탈퇴 댓글에는 없음). 비회원·인증 전은 보이되 누르면 004 `useAuthGate` 안내(FR-004).
  - `ReportDialog`(`role="dialog"`, 초점 가둠): 사유 라디오 6개(`reasonLabels.ts`), 기타면 설명 칸(200자, 남은 글자 수), [취소]·[신고하기]. 성공 → 창 닫고 알림 줄 "신고가 접수됐어요. 검토 후 처리할게요". 404 → "볼 수 없는 글이에요" 후 창 닫기, 429 → "잠시 후 다시 시도해 주세요".
  - 관리자 `/admin/reports`: [대기]·[처리됨] 탭(`?tab=`), 항목 = 대상 종류 배지, 스냅샷 제목(댓글은 내용 앞 40자), `@작성자`, 신고 N건 + 사유별 수, 최근 신고 상대 시각. [더 보기] 20개씩.
  - `/admin/reports/{caseId}`: 스냅샷(텍스트로만, 줄바꿈 유지), "현재: 비공개" 등 상태, 신고 수·사유별 수·기타 설명(시각만), 작성자 카드(가입일·숨겨진 콘텐츠 N개·정지 이력, [회원 화면]). 처리: 사유 선택 + [숨기기], [문제없음], 둘 다 확인 창. 409면 "이미 처리된 신고예요" 후 다시 불러오기. `onlyMyReport`면 처리 버튼 비활성 + "내가 혼자 신고한 건은 처리할 수 없어요". 처리됨 사건이고 대상이 지금 숨김이면 [숨김 해제].
  - `/admin/members/{handle}`: 정보, 정지 이력 표, 지금 정지 중이면 종료 시각·사유·[정지 해제], 아니면 기간 선택(1일·7일·30일·영구) + 사유(필수, 200자) + [정지](확인 창 "모든 기기에서 로그아웃돼요").
  - 작성자 숨김 안내: "운영 정책에 따라 숨겨진 글이에요 (사유: {이름}). 다른 사람에게는 보이지 않아요" — 005 `AuthorStatusBanner`의 문장 조립을 사유를 받는 함수로 바꾼다(plan 설계 후 확인 6). 사유가 없으면(옛 데이터) 괄호 없이.
  - 직접 숨김 버튼은 관리자에게 글 상세·댓글에서 보이지 않는다(제안 — 관리자 화면의 사건·회원 화면에서만 처리). 직접 숨김 API는 관리자 화면에서 "글 주소로 숨기기" 칸으로 부른다.
- **Rationale**: 43 §2·§3·§4, FR-004·FR-013·FR-014·FR-022·FR-035.
- **Alternatives considered**: 글 상세에 관리자 [숨기기] 버튼 — 004 `PostActions`(42 §11 표)에 관리자 버튼 칸이 없어 004 표시 규칙을 바꿔야 한다. 팀 확인 T004에 함께 올린다.

## R13. 권한 매트릭스 행 (제안)

- **Decision**: 새 CSV `TR/permission/moderation.csv`(004 하네스 같은 형식, owner `014`):
  | 행동 | 대상 | ANONYMOUS | UNVERIFIED | MEMBER | AUTHOR | ADMIN | SUSPENDED | WITHDRAWN |
  |---|---|---|---|---|---|---|---|---|
  | `report.post` | 공개 글 | 401 | 403 `EMAIL_NOT_VERIFIED` | 200 | 400 `CANNOT_REPORT_OWN` | 200 | 403 | 403 |
  | `report.post` | 비공개·임시·휴지통·숨김·작성자 유예·없는 글 | 401 | 403 | 404 | 작성자가 볼 수 있는 자기 글(비공개·임시·숨김)은 400 `CANNOT_REPORT_OWN`, 휴지통·없는 글은 404 | 404 | 403 | 403 |
  | `admin.post.hide` | 공개 글 | 401 | 404 | 404 | 404 | 200 | 404 | 403 |
  | `admin.post.hide` | 비공개·임시·휴지통·작성자 유예·없는 글 | 401 | 404 | 404 | 404 | 404 | 404 | 403 |
  | `admin.post.unhide` | 숨김 글 | 401 | 404 | 404 | 404 | 200 | 404 | 403 |
  - 관리자 행동의 일반 회원 칸은 004 경로 규칙의 404, 탈퇴 유예는 001 게이트 403(게이트가 경로 규칙보다 먼저 — 004 R-23). 실제 값은 tasks T027에서 004 `AdminPathIT`과 맞춘다.
  - 댓글 행(`report.comment`, `admin.comment.hide`)은 007 `comment.csv`의 대상 상태를 빌려 같은 CSV에 둔다.
- **Rationale**: 헌법 III, 42 §3·§5·§8, 004 research R-28.

## R14. 설정값 (제안)

```yaml
blog:
  moderation:
    snapshot-content-chars: 2000
    detail-max-chars: 200
    rate-limit:
      per-minute: 5
      per-day: 50
    retention: 30d
    cleanup-cron: "0 45 4 * * *"
    cleanup-batch-size: 1000
    admin-page-size: 20
    suspension-durations: [P1D, P7D, P30D, PERMANENT]
    suspension-reason-max-chars: 200
```

- 설정 키 접두어 규칙(`blog.moderation` vs `blog.report`)은 Tier A ANALYSIS R10 공통 미결이다.
