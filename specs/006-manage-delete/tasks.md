---

description: "Task list for 006-manage-delete (내 글 관리와 글 삭제·휴지통)"
---

# Tasks: 내 글 관리와 글 삭제·휴지통

**Input**: Design documents from `/specs/006-manage-delete/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, events.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers PostgreSQL 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 테스트 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 공통 기반을 소유하지 않는다(006이 소유하는 공통 항목은 `PostPurgeStep` 확장점과 `TrashPurgeJob`뿐). 아래 항목은 **직접 만들지 않고** 선행 작업으로 기다린다. 005는 그 tasks.md의 작업 번호를 적었고, 001·002·004는 이 문서 작성 시점에 tasks.md 번호가 확정되지 않아 Phase와 클래스·경로 이름으로 적었다(번호는 각 tasks.md 확정 뒤 채운다).

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1 (Setup) — backend Maven 프로젝트 골격(`com.team.blog`, package-by-feature: account·post·tag·media·interaction·discovery·shared, 모듈마다 web/application/domain/infra), frontend React 골격(라우터·Vitest + Testing Library), `docker-compose.yml`(app + PostgreSQL + Redis + MinIO + Mailpit)
- 선행: specs/001 Phase 2 (Foundational) — Flyway V1 공통 스키마(docs/51 기준: `post`·`post_draft`·`post_tag`·`tag`·`comment`·`post_like`·`post_image`·`image`·`post_view_daily`·`notification`·`notification_actor`·`report_case`·`report`와 `ix_post_manage`·`ix_post_trash`·FK CASCADE/SET NULL)
- 선행: specs/001 Phase 2 — `shedlock` 테이블 마이그레이션과 ShedLock `LockProvider`(JDBC)·`@EnableSchedulerLock` 설정
- 선행: specs/001 Phase 2 — Testcontainers 통합 테스트 베이스(`support/IntegrationTestBase`: PostgreSQL + Redis, 로그인 세션·CSRF 헬퍼, 회원 픽스처: 일반·이메일 인증 전·탈퇴 유예·관리자)
- 선행: specs/001 Phase 2 — `shared/error`(공통 오류 본문 `{code, message, errors, details}`, `GlobalExceptionHandler`, `NotFoundException`→404 `NOT_FOUND`, 400 검증 오류 형식)
- 선행: specs/001 Phase 2 — `shared/security`(세션 쿠키 + CSRF `X-XSRF-TOKEN`, `CurrentUser`, 비회원 401 `LOGIN_REQUIRED` 진입점), Redis 장애 시 비로그인 처리, 계정 상태 가드 `AccountStatusGuard.requireActive(memberId, ActionKind)`(001 research R-22·001 T037: 006은 `ActionKind.CONTENT_CLEANUP` — 인증 전 허용, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED`)
- 선행: specs/001 T021 — 불투명 커서 코덱(`shared/web/cursor/CursorCodec`·`ListScope`(`of(String)`), Base64URL JSON `{"v":1,"l":"manage:{tab}[:{filter}]","k":[…]}`, `l` 불일치·풀리지 않는 값은 400 `INVALID_CURSOR`)
- 선행: specs/001 Phase 2 — 설정값 바인딩(`@ConfigurationPropertiesScan`, `blog.*` 접두어 규칙), 보안 헤더/CSP 필터
- 선행: specs/004 Foundational — `PostAccessPolicy.canRead`(① `deleted_at IS NULL`을 가장 먼저 확인), `VisibilityFilter.forViewer`(공개 목록 공용 조건 `deleted_at IS NULL AND hidden_at IS NULL …`), `VisibilityRule`, `Viewer`·`CurrentViewerResolver`, 권한 매트릭스 CSV 테스트 하네스(`backend/src/test/java/com/team/blog/support/permission/`: `PermissionAction`·`PermissionActionRegistry`·`AbstractPermissionMatrixIT`·`PostSnapshot`, 픽스처 `support/fixture/PostFixtures`의 `TRASHED` 상태, `backend/src/test/resources/permission/post-read.csv`·`post-write.csv` — owner 열이 006인 삭제·복구·영구 삭제 행은 006이 실행기를 등록해야 실행된다), frontend `api/client.ts`(오류 본문 `ApiError` 파싱·CSRF 헤더)
- 선행: specs/002 Foundational — `Post` 엔티티(`@SQLRestriction("deleted_at IS NULL")` 포함 — 004 data-model도 같은 매핑을 적었다), `PostStatus`, `PostDraft`(작업본), `PostRepository`, `EmptyDraftPolicy.isEmpty(title, contentMd)`(`" \t\r\n"` 문자 집합으로 trim, SQL은 `btrim(x, E' \t\r\n')` — 002 빈 임시글 정리 배치와 공용)

**선행 (해당 User Story 테스트·구현 전에 필요)**

- 선행: specs/002 US(자동 저장) — `AutosaveService.flushNow(postId)`(Redis `autosave:post:{postId}`를 `deleted_at` 조건 없이 DB에 반영하고 커밋 후 키 삭제를 호출자 트랜잭션의 afterCommit에 등록, 002 contracts/events.md), 키 무조건 삭제 메서드(`autosave:post:{id}` DEL + `autosave:dirty` SREM — 완전 삭제 커밋 후용. 002 `RedisAutosaveStore`에 없으면 002에 추가 요청), 자동 저장·수동 저장(`PUT …/working-copy`)·발행 API가 Redis 키가 있어도 DB `deleted_at IS NULL`을 확인해 휴지통 글을 404로 거부(research R7 (b)), 1분 반영 배치가 `deleted_at` 조건을 쓰지 않고 FK 23503이면 키를 버림(R7 (a)) → US1(T017·T018·T021)
- 선행: specs/002 US — `POST /api/posts`(새 임시글), `DELETE /api/posts/{postId}/working-copy`(변경 취소), frontend `api/posts.ts` → US5
- 선행: specs/004 US1 — `PUT /api/posts/{postId}/visibility`(휴지통 글 404), frontend `features/visibility/VisibilitySelect.tsx`·`VisibilityBadge.tsx` → US1(T018), US3(T049), US5(T068)
- 선행: specs/004 US4 — frontend `features/auth-gate/useAuthGate.ts`(401 → `/login?returnTo`) → US3(T046)
- 선행: specs/005 T024 (`GET /api/posts` 홈 목록), T038 (`GET /api/posts/{postId}` 상세), T050 (`GET /api/members/{handle}`·`GET /api/members/{handle}/posts` 블로그 머리말·글 수·목록) → US1(T019), US2(T027)
- 선행: specs/001 T021 (`shared/web/cursor/ListScope` — 커서 안 목록 구분 값 `l`, `ListScope.of(String)` 있음) → US3(T033·T039)

**임시 구현 + 소유 스펙에서 교체 (Tier B·C가 아직 plan 전)**

- 003-image-upload: `media/application/ImagePostPurgeStep`(order 20)을 006이 임시 구현한다(T060). 003 plan이 소유를 넘겨받아 검토·교체한다
- 007-comment: `interaction/application/CommentQueryService.commentIdsOfPost(postId)`를 006이 최소 구현한다(T058). 007이 확장·소유한다
- 014-report-hide: 신고 모듈의 `ReportPostPurgeStep`(order 10)을 006이 임시 구현한다(T061, 패키지 `com.team.blog.moderation` 가정 — 014 plan이 확정). 014가 소유를 넘겨받는다

**후속 (다른 스펙이 이 기능을 사용)**

- 015-withdraw: `PostWithdrawalPurgeStep`(`WithdrawalPurgeStep` order 10)은 015 tasks에서 만들고, 006의 `PostPurgeService.purgeAllByAuthor(authorId)`(T063)를 호출한다
- 012-trending-search·005(sitemap): `PostTrashed`·`PostRestored`·`PostPurged` 구독(이 기능은 발행만 한다)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/java/com/team/blog/…`, `backend/src/test/java/com/team/blog/…`, `backend/src/main/resources/…`, `frontend/src/…` (plan.md Project Structure)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 공통 기반은 001이 소유하므로 이 Phase는 선행 작업 완료 확인만 한다

- [X] T001 specs/001 Phase 1·2 완료를 확인한다: `./mvnw -pl backend verify`가 빈 상태로 통과하고, Flyway V1에 `post.deleted_at`·`post.hidden_at`·`ix_post_manage (author_id, status, updated_at DESC) WHERE deleted_at IS NULL`·`ix_post_trash (author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL`·`shedlock` 테이블이 있고, `post_tag`·`comment`·`post_like`·`post_image`·`post_draft`·`post_view_daily`·`notification`의 `post_id` FK가 CASCADE, `report_case.post_id`·`comment_id`가 SET NULL인지 `backend/src/main/resources/db/migration/V1__common_schema.sql`에서 확인한다. 빠진 것이 있으면 001에 보고하고 이 기능은 시작하지 않는다 (구현 메모: 2026-10-08 확인 — V1에 post.deleted_at·hidden_at·ix_post_manage·ix_post_trash, 나열한 FK CASCADE·report_case SET NULL이 그대로 있고 shedlock은 V2(001 T012)에 있다. 마지막 ./mvnw -q verify로 전체 통과를 확인했다)
- [X] T002 specs/002·004 Foundational 완료를 확인한다: `backend/src/main/java/com/team/blog/post/domain/Post.java`에 `@SQLRestriction("deleted_at IS NULL")`이 있고, `EmptyDraftPolicy.isEmpty`, `PostAccessPolicy.canRead`, `VisibilityFilter.forViewer`, 권한 매트릭스 CSV 하네스(`backend/src/test/resources/permission/`)가 있는지 확인한다. 없으면 해당 스펙에 보고한다(이 기능에서 만들지 않는다) (구현 메모: 확인함 — Post의 @SQLRestriction, EmptyDraftPolicy.isEmpty, PostAccessPolicy.canRead(삭제 여부 먼저), VisibilityFilter.forViewer, 004 하네스(support/permission)·post-write.csv 모두 있음. 004 공용 러너 PermissionMatrixIT(004 T045)는 아직 없어 006 행 러너를 따로 둔다(T019 메모))

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 006이 소유하는 휴지통 공용 부품 — 설정값, 이벤트 record, `PostPurgeStep` 확장점, 휴지통 전용 네이티브 저장소, 완전 삭제 공용 루틴(`PostPurgeService`), 관리 화면 공용 프런트 부품

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T003 [P] `TrashablePost` 단위 테스트를 `backend/src/test/java/com/team/blog/post/unit/TrashablePostTest.java`에 작성한다: `deletedAt == null`이면 `isTrashed() == false`, 값이 있으면 true; `purgeAt(Duration.ofDays(30))`은 `deletedAt + 30일`(마이크로초 보존); `isTrashed() == false`일 때 `purgeAt`은 예외; `isDraft()`는 `status == DRAFT`
- [X] T004 [P] `TrashPostRepository` 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/TrashPostRepositoryIT.java`에 작성한다(IntegrationTestBase 상속): ① `lockOwned(postId, me)`가 휴지통 글(`deleted_at` 값 있음)도 돌려준다(`@SQLRestriction` 우회) ② 남의 글·없는 글은 `Optional.empty()` ③ 002의 `PostRepository.findById`는 휴지통 글을 돌려주지 않는다(FR-039, `@SQLRestriction` 회귀 확인) ④ `markTrashed(id, now)`는 `deleted_at`만 바꾸고 `status`·`visibility`·`first_public_at`·`published_at`·`edited_at`·`hidden_at`·`updated_at`을 바꾸지 않는다(research R2·R3) ⑤ `clearTrashed(id)`는 `deleted_at = NULL`만 바꾼다 ⑥ `findExpiredIds(cutoff, 100)`는 `deleted_at < cutoff`인 글만 `ORDER BY deleted_at, id`로 최대 100개 돌려준다 (구현 메모: tx.execute 람다의 Optional이 assertThat 겹침(IntPredicate)을 일으켜 조회 도우미 메서드로 감쌌다)
- [X] T005 [P] `PostPurgeService` 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostPurgeServiceIT.java`에 작성한다: 테스트용 `PostPurgeStep` 빈 두 개(order 20, 10)를 `@TestConfiguration`으로 등록해 ① `order()` 오름차순으로 호출되고 ② 호출 시점에 `post` 행이 아직 있으며(DELETE 전) ③ 호출 뒤 `post` 행이 없다 ④ 단계가 예외를 던지면 `post` 행이 남는다(전체 롤백) ⑤ `notify = true`이면 `PostPurged(postId, authorId)`가 한 번 기록되고(`@RecordApplicationEvents`) `false`이면 없다 ⑥ 커밋 후 Redis `autosave:post:{id}` 키와 `autosave:dirty`의 해당 id가 사라진다 ⑦ Redis 컨테이너를 멈춘 상태에서도 DB 삭제는 커밋된다(경고 로그만) ⑧ 트랜잭션 밖에서 호출하면 `IllegalTransactionStateException`(Propagation.MANDATORY) (구현 메모: 테스트 단계는 @TestConfiguration 대신 테스트 소스 @Component `post/support/PurgeStepProbe`(arm() 전에는 아무것도 안 함)로 둬 새 Spring 컨텍스트(=연결 풀)를 만들지 않는다. 실제 단계 신고 10·사진 20과 겹치지 않게 order 5·25를 쓴다. 이벤트는 002 PostTestConfig.CommittedEvents(커밋 후 수집)로 본다)

### Implementation for Foundational

- [X] T006 [P] 설정값 클래스를 만든다: `backend/src/main/java/com/team/blog/post/application/TrashProperties.java`(`@ConfigurationProperties("blog.post.trash")`: `Duration retention = P30D`, `String purgeCron = "0 30 3 * * *"`, `int purgeBatchSize = 100`, `Duration purgeMaxDuration = PT30M`)와 `backend/src/main/java/com/team/blog/post/application/ManageProperties.java`(`@ConfigurationProperties("blog.manage")`: `int pageSize = 20`). `backend/src/main/resources/application.yml`에 같은 기본값을 추가한다(data-model §5, 헌법 VII) (구현 메모: application.yml blog.post.trash.*·blog.manage.page-size 추가. 002 PostAuthoringProperties가 blog.post를 record로 바인딩하지만 모르는 키(trash)는 무시하므로 겹치지 않는다)
- [X] T007 [P] 도메인 이벤트 record 세 개를 `backend/src/main/java/com/team/blog/shared/event/PostTrashed.java`(`long postId, long authorId, Instant trashedAt`), `PostRestored.java`(`long postId, long authorId, Instant restoredAt`), `PostPurged.java`(`long postId, long authorId`)로 만든다. 제목·본문 등 글자 필드는 넣지 않는다(contracts/events.md §1, 20 EV-3)
- [X] T008 [P] 완전 삭제 확장점 인터페이스 `backend/src/main/java/com/team/blog/post/application/spi/PostPurgeStep.java`를 만든다: `int order()`(10 단위), `void beforePurge(long postId)`(호출자 트랜잭션 안에서 실행, 예외 시 그 글의 완전 삭제 전체 롤백). Javadoc에 order 10 = 신고(`ReportPostPurgeStep`), 20 = 사진(`ImagePostPurgeStep`)을 적는다(contracts/events.md §2, research R10)
- [X] T009 [P] 잠금 조회 결과 값 객체 `backend/src/main/java/com/team/blog/post/domain/TrashablePost.java`(record: `long id, long authorId, PostStatus status, Visibility visibility, String title, String contentMd, Instant deletedAt, long editVersion`; `isTrashed()`, `isDraft()`, `purgeAt(Duration retention)`)를 만든다. `Post` 엔티티는 `@SQLRestriction` 때문에 휴지통 글을 읽을 수 없으므로 휴지통 처리는 이 값 객체로 한다(T003 통과) (구현 메모: 빈 임시글 판정 isEmptyDraft()도 함께 둠(002 EmptyDraftPolicy 사용))
- [X] T010 휴지통 전용 네이티브 저장소 `backend/src/main/java/com/team/blog/post/infra/TrashPostRepository.java`를 `NamedParameterJdbcTemplate`로 구현한다(T009 의존): `Optional<TrashablePost> lockOwned(long postId, long me)` = `SELECT id, author_id, status, visibility, title, content_md, deleted_at, edit_version FROM post WHERE id = :postId AND author_id = :me FOR UPDATE`(research R4); `Optional<TrashablePost> lockById(long postId)`(배치 재확인용, 같은 SELECT에서 author 조건 없음); `void markTrashed(long id, Instant now)` = `UPDATE post SET deleted_at = :now WHERE id = :id`(`updated_at` 갱신 금지, R3); `void clearTrashed(long id)` = `UPDATE post SET deleted_at = NULL WHERE id = :id`; `void deleteById(long id)` = `DELETE FROM post WHERE id = :id`; `List<Long> findExpiredIds(Instant cutoff, int limit)` = `SELECT id FROM post WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff ORDER BY deleted_at, id LIMIT :limit`; `List<Long> findIdsByAuthorIncludingTrashed(long authorId)`. 시각은 마이크로초 `Instant`로 매핑한다(T004 통과) (구현 메모: 시각은 OffsetDateTime(UTC, 마이크로초로 자름)으로 쓰고 읽는다. deleteById는 지운 행 수를 돌려준다)
- [X] T011 완전 삭제 공용 루틴 `backend/src/main/java/com/team/blog/post/application/PostPurgeService.java`를 구현한다(T006·T007·T008·T010 의존): `@Transactional(propagation = MANDATORY) void purge(long postId, long authorId, boolean notify)` — ① `List<PostPurgeStep>`을 `order()` 순으로 `beforePurge(postId)` ② `TrashPostRepository.deleteById` ③ `notify`이면 `ApplicationEventPublisher`로 `PostPurged` 발행(빈 임시글은 false, 20 §3-1) ④ `TransactionSynchronization.afterCommit`에 002의 Redis 키 삭제(`autosave:post:{id}` DEL + `autosave:dirty` SREM)를 등록하고 Redis 예외는 경고 로그만 남긴다(헌법 V) ⑤ `DataIntegrityViolationException`의 SQLSTATE가 23001 또는 23503이면 `PostPurgeFkViolationException`(같은 패키지에 새로 만듦)으로 바꿔 다시 던지고 오류 로그에 postId만 남긴다(research R22). 트랜잭션 안에서 외부 호출(파일 삭제 등)을 하지 않는다(T005 통과) (구현 메모: FK 판정은 원인 사슬의 SQLException.getSQLState(). Redis 정리는 002 RedisAutosaveStore.delete를 RedisGuard.runAfterCommit으로 커밋 뒤에 부른다(002 메모: 트랜잭션 안 Redis 쓰기 금지). T063 purgeAllByAuthor도 이때 같이 둠)
- [ ] T012 [P] 확인창 컴포넌트 `frontend/src/components/ConfirmDialog.tsx`를 만든다(이미 다른 기능이 만들었으면 재사용하고 이 작업은 확인만): 제목·본문·확인 버튼 글자·취소 버튼, `role="dialog"`·`aria-modal`, Esc·[취소]는 닫기, 확인 버튼에 처음 포커스, Promise<boolean>을 돌려주는 `confirm()` 훅 제공. 글자는 텍스트로만 렌더링한다(`dangerouslySetInnerHTML` 금지, 헌법 IV)
- [ ] T013 [P] 알림 메시지 컴포넌트 `frontend/src/components/Toast.tsx`를 만든다(이미 있으면 재사용): 글자 + 선택적 링크 버튼 1개(예: "복구했어요 [발행 글 탭에서 보기]"), `role="status"`, 5초 뒤 자동 닫힘
- [ ] T014 [P] 확인·안내 문구 모음 `frontend/src/features/manage-posts/confirmDialogs.ts`를 만든다: 휴지통 "휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요"([휴지통으로]/[취소], FR-018), 영구 삭제 "영구 삭제하면 되돌릴 수 없어요. 댓글·좋아요도 함께 지워져요"(FR-029), 공개로 바꾸기 "모든 사람이 볼 수 있게 돼요"(FR-015), 변경 취소 "수정 중인 내용을 버리고 발행된 글로 되돌릴까요?"(FR-016), 토스트 "빈 글이라 바로 삭제했어요"(FR-020)·"복구했어요"(FR-027)·휴지통 안내 "휴지통의 글은 30일 뒤 자동으로 완전히 삭제돼요"(FR-011)
- [ ] T015 줄 단위 처리 훅 `frontend/src/features/manage-posts/useRowAction.ts`를 만든다(T013 의존): `run(rowId, action, {onSuccess})` — 실행 중 그 줄 버튼 비활성화, 성공 시 `onSuccess(result)`로 그 줄만 갱신, 실패 시 `rowErrors[rowId]`에 오류 code별 문구(`NOT_FOUND` → "이미 처리된 글이에요. 목록을 다시 불러왔어요", 그 밖 → 서버 `message`)를 넣고, `NOT_FOUND`이면 주입받은 `reload()`를 호출한다(FR-013, research R24). 화면 전체를 다시 그리지 않는다

**Checkpoint**: Foundation ready — `PostPurgeService`·`TrashPostRepository`가 통합 테스트를 통과하고, user story 구현을 시작할 수 있다

---

## Phase 3: User Story 1 - 글을 휴지통으로 옮겨 즉시 감추기 (Priority: P1) 🎯 MVP

**Goal**: 작성자가 자기 글을 지우면 즉시 모든 사람(작성자 포함)에게서 사라지고 휴지통에 30일 보관된다. 빈 임시글은 바로 완전 삭제된다 (C-POST-5 #1·#4·#5)

**Independent Test**: 공개 발행 글 하나를 `DELETE /api/posts/{postId}`로 지운 뒤 비회원·다른 회원·작성자·관리자로 상세·홈·블로그·블로그 글 수를 조회해 어디에도 나오지 않는지 확인한다(`PostTrashApiIT`, `TrashedPostPermissionMatrixIT`)

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T016 [US1] 휴지통 이동 API 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostTrashApiIT.java`에 작성한다(테스트 이름은 인수 시나리오와 1:1): `US1_1_공개글_삭제하면_휴지통으로` — 200 `{trashed:true, purgeAt}`이고 `purgeAt == deleted_at + 30일`, DB `status`·`visibility`·`first_public_at`·`updated_at`·`hidden_at` 그대로(FR-019·FR-026); `US1_2_빈_임시글은_바로_완전삭제` — 제목·본문 `''` 및 공백만(`"  "`, `"\n"`)인 DRAFT는 200 `{purged:true}`, 행 없음, `PostPurged`·`PostTrashed` 미발행(FR-020, research R8); 제목만 빈 DRAFT는 휴지통으로; `US1_3_남의글_삭제는_404_변경없음`·없는 글 404 — 요청 전후 `post` 행 전체 컬럼이 같다(FR-017, SC-004); `US1_4_이미_휴지통이면_성공_변화없음` — 200, `purgeAt` 첫 응답과 같음, 이벤트 0회(FR-025, R6); `US1_6_댓글_좋아요는_보존` — `comment`·`post_like`·`post_tag`·`post_image`·`post_draft` 행 수와 `like_count`·`comment_count`가 삭제 전과 같다(FR-022, 행은 SQL 픽스처로 직접 넣음); `PostTrashed`가 상태가 바뀐 경우에만 정확히 1회(`@RecordApplicationEvents`, FR-038); 판정 순서 — 비회원 401 `LOGIN_REQUIRED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED`, 이메일 인증 전 회원은 200(FR-037, research R5), 계정 상태 거부가 404보다 먼저(남의 글에도 403), CSRF 헤더 없으면 403 (구현 메모: 휴지통 글 404 본문은 바이트까지 공통 본문과 비교. 숨김·비공개·작업본 있는 글·임시글도 휴지통으로 가는지, 탭 문자만 있는 발행 글은 빈 글 판정을 하지 않는지 더했다)
- [X] T017 [US1] 같은 파일 `backend/src/test/java/com/team/blog/post/integration/PostTrashApiIT.java`에 자동 저장 반영 테스트를 추가한다(T016 다음): `US1_7_trash_flushesPendingAutosave` — 발행 글에 Redis `autosave:post:{id}` version 13 버퍼가 있는 상태에서 삭제하면 `post_draft`에 version 13 제목·본문이 있고, 임시글이면 `post`에 반영되며, 커밋 후 Redis 키와 `autosave:dirty` 항목이 없다(FR-024, research R7); 반영 뒤 판정 — Redis에만 내용이 있고 DB는 빈 임시글이면 휴지통으로 간다(빈 글로 지우지 않음, R8); Redis 컨테이너를 멈춘 상태에서도 삭제 200(경고 로그, 헌법 V) (구현 메모: Redis 정지 중에는 세션을 읽지 못해 HTTP가 401이 되므로(002 T053 메모) 그 경우만 PostTrashService를 직접 부른다)
- [X] T018 [US1] 같은 파일 `backend/src/test/java/com/team/blog/post/integration/PostTrashApiIT.java`에 휴지통 글 쓰기 차단 테스트를 추가한다(T017 다음, 002·004 API 선행 필요): `US1_5_휴지통글_쓰기는_모두_404` — 작성자가 휴지통 글에 `PUT /api/posts/{id}/autosave`(Redis 키가 남아 있는 상태 포함), `PUT /api/posts/{id}/working-copy`, `POST /api/posts/{id}/publish`, `PUT /api/posts/{id}/visibility`, `DELETE /api/posts/{id}/working-copy`를 보내면 모두 404 `NOT_FOUND`이고 DB가 바뀌지 않는다(FR-023, 엣지 케이스 "휴지통으로 옮긴 뒤 자동 저장 도착") (구현 메모: 004 PUT /api/posts/{id}/visibility가 아직 없어 그 한 줄은 별도 테스트로 떼어 handler 존재를 Assumptions로 확인한다(지금은 건너뜀). 에디터 열기(GET working-copy)도 404인지 함께 본다)
- [X] T019 [P] [US1] 권한 매트릭스 연결: ① 004 하네스에 006 행동 실행기 `backend/src/test/java/com/team/blog/post/permission/TrashPostAction.java`(`PermissionAction`, `name()="post.trash"`(004 `post-write.csv`의 삭제 행 action 이름에 맞춤), `owner()="006"`, `perform` = `DELETE /api/posts/{postId}` + CSRF)를 테스트 Bean으로 등록해 `post-write.csv`의 006 삭제 행이 건너뛰지 않고 실행되게 한다(004 Polish의 대기 행 0건 확인 대상) ② 목록 누출 테스트 `backend/src/test/java/com/team/blog/post/integration/TrashedPostPermissionMatrixIT.java`를 작성한다: 004 `PostFixtures`로 공개 발행·비공개 발행·임시글을 만든 뒤 `DELETE /api/posts/{id}`로 휴지통에 넣고, 비회원·다른 회원·작성자·관리자별로 상세(`GET /api/posts/{id}` 404)·홈·블로그 목록 미포함·블로그 글 수 감소·태그 목록·검색·sitemap·댓글 목록·좋아요 요청(404 또는 미포함)을 확인한다. 상세 판정 행 자체는 004 `post-read.csv`의 `TRASHED` 행이 이미 다루므로 중복 작성하지 않는다. 작성자에게는 `GET /api/me/posts?tab=trash`에만 보이는지도 확인한다(US3 구현 전에는 이 한 줄을 `@Disabled("US3")`로 둔다). 태그·검색·sitemap·댓글·좋아요는 해당 기능(008·012·007·009)이 없으면 `Assumptions.assumeTrue`로 건너뛴다(quickstart §0). 근거: US1-1, FR-021·FR-039, SC-001, 42 §5-1 (구현 메모: 004 공용 러너가 없어 owner=006 행만 도는 `post/integration/permission/TrashPermissionMatrixIT`를 더했다(002·005와 같은 방식). post-write.csv의 `AUTHOR,TRASHED,post.trash` 행이 404였는데 FR-025(이미 휴지통이면 변화 없이 성공)·research R6과 달라 200으로 고쳤다(004 소유 파일의 006 행). 태그·검색·sitemap·댓글·좋아요는 handler가 없으면 Assumptions로 건너뛰고, 생기면 실패해 단언을 더하라고 알린다)

### Implementation for User Story 1

- [X] T020 [P] [US1] 삭제 응답 DTO `backend/src/main/java/com/team/blog/post/web/dto/TrashResponse.java`를 만든다: sealed interface + record `Trashed(boolean trashed = true, Instant purgeAt)` / `Purged(boolean purged = true)`. JSON은 contracts/openapi.yaml `TrashedResult`(`additionalProperties: false`, `{trashed, purgeAt}`)·`PurgedResult`(`{purged}`)와 정확히 같아야 한다 (구현 메모: sealed interface + record. Trashed는 (trashed, purgeAt), Purged는 (purged). 응답 키 집합은 PostTrashApiIT가 확인)
- [X] T021 [US1] `backend/src/main/java/com/team/blog/post/application/PostTrashService.java`에 `TrashOutcome trash(long me, long postId)`를 `@Transactional`로 구현한다(T010·T011 의존): ⓪ 001 `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP)`(인증 전 허용, 탈퇴 유예·정지 403 — 42 §3 순서상 소유 조회보다 먼저) ① `trashPostRepository.lockOwned(postId, me)` 없으면 `NotFoundException`(404) ② 이미 휴지통이면 변화·이벤트 없이 `Trashed(deletedAt + retention)` 반환(FR-025, R6) ③ 002 `AutosaveService.flushNow(postId)` 호출 — Redis 장애 예외는 잡아 경고 로그 후 계속(R7) ④ 반영 후 값을 다시 잠금 조회해 `status == DRAFT && EmptyDraftPolicy.isEmpty(title, contentMd)`이면 `postPurgeService.purge(postId, me, false)` 후 `Purged` 반환(FR-020) ⑤ 아니면 `markTrashed(postId, now)`, `PostTrashed(postId, me, now)` 발행, `Trashed(now + retention)` 반환. `updated_at`은 건드리지 않는다(R3). 현재 사용자는 인자로만 받고 요청 값에서 꺼내지 않는다(헌법 III) (구현 메모: 반영 뒤 같은 잠금 쿼리로 다시 읽어 판정한다(이미 잠근 행이라 대기 없음). flushNow의 Redis OOM(AutosaveUnavailableException)·Redis 장애는 경고 로그 뒤 계속, DB 오류는 그대로 던진다. 시각은 Clock에서 마이크로초로 자른다)
- [X] T022 [US1] `backend/src/main/java/com/team/blog/post/web/PostTrashController.java`를 만들고 `DELETE /api/posts/{postId}`를 연결한다(T020·T021 의존): `CurrentUser`(001)에서 회원 id를 꺼내 `PostTrashService.trash` 호출, 200 + `TrashResponse`. `ActionKind.CONTENT_WRITE`(인증 필요)를 쓰지 않고 서비스가 `CONTENT_CLEANUP`을 쓴다 — 이메일 인증 전 회원도 삭제할 수 있어야 한다(FR-037, research R5). `postId`는 `long` 경로 변수이고 1 미만이면 404
- [X] T023 [US1] `PostTrashService.trash`와 `PostPurgeService`에 운영 로그를 추가한다: 휴지통 이동·빈 임시글 즉시 삭제를 INFO로 `postId`·`authorId`·결과(trashed/purged/already)만 남기고 제목·본문은 남기지 않는다. 대상 파일 `backend/src/main/java/com/team/blog/post/application/PostTrashService.java`, `backend/src/main/java/com/team/blog/post/application/PostPurgeService.java` (구현 메모: PostPurgeService.purge도 INFO(postId·authorId·notify))
- [ ] T024 [P] [US1] 프런트 API 함수 `frontend/src/api/managePosts.ts`를 만들고 `trashPost(postId): Promise<{trashed:true,purgeAt:string} | {purged:true}>`(`DELETE /api/posts/{postId}`)를 추가한다. 공통 `api/client.ts`(001 T042 소유: CSRF 헤더·오류 code 파싱, 004 T024가 404 분기 추가)를 쓴다
- [ ] T025 [US1] 휴지통 처리 훅 `frontend/src/features/manage-posts/useTrashActions.ts`를 만들고 `trash(row)`를 구현한다(T012·T014·T015·T024 의존): 확인창(FR-018) → `trashPost` → `purged`면 토스트 "빈 글이라 바로 삭제했어요", `trashed`면 줄 제거·현재 탭 수 −1·휴지통 수 +1을 `onRowRemoved`·`onCountsChange` 콜백으로 알린다. 실패 처리는 `useRowAction`에 맡긴다

**Checkpoint**: `PostTrashApiIT`(US1 부분)와 `TrashedPostPermissionMatrixIT`가 통과한다 — 지운 글이 어디에도 새지 않는다

---

## Phase 4: User Story 2 - 휴지통에서 원래 모습 그대로 복구하기 (Priority: P1)

**Goal**: 휴지통 글을 확인창 없이 복구하면 상태·공개 범위·목록 위치·댓글·좋아요·태그·작업본이 삭제 전과 같다 (C-POST-5 #2, 13 D-4)

**Independent Test**: 댓글·좋아요·태그가 달린 공개 글을 휴지통에 넣었다가 `POST /api/posts/{postId}/restore`로 복구한 뒤, 홈 목록 위치·댓글 수·좋아요 수·태그가 삭제 전과 같은지 비교한다(`PostRestoreApiIT`)

### Tests for User Story 2 ⚠️

- [X] T026 [P] [US2] 복구 API 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostRestoreApiIT.java`에 작성한다: `US2_1_공개글_복구하면_원래_위치` — 200 `{restored:true, status:"PUBLISHED", visibility:"PUBLIC"}`, `deleted_at` NULL, `first_public_at`·`updated_at`·`status`·`visibility`·`hidden_at`이 삭제 전과 같다(SC-002, research R2·R3); `US2_2_임시글_복구하면_원래_위치` — 임시글 3개 중 가운데 글을 지웠다 복구하면 `ORDER BY updated_at DESC, id DESC` 순서가 삭제 전과 같다; `US2_3_작업본_있는_발행글_복구` — `post_draft` 행과 내용이 그대로; `US2_4_휴지통에_없는_글_복구는_404` — 정상 글·남의 휴지통 글·없는 글 모두 404이고 DB 변화 없음(FR-028); 숨긴 글(`hidden_at` 값)을 지웠다 복구해도 숨김 유지(43 §4-1); 댓글·좋아요·태그 행 수와 `like_count`·`comment_count` 동일(SC-002); `PostRestored`는 실제 복구 때만 1회; 판정 순서 401·403(`ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED`)·인증 전 200. 같은 작업에서 004 하네스용 실행기 `backend/src/test/java/com/team/blog/post/permission/RestorePostAction.java`(`POST /api/posts/{postId}/restore`, owner 006)를 등록해 `post-write.csv`의 복구 행을 실행되게 한다 (구현 메모: 실행기는 `post/permission/RestorePostAction`. 판정 순서는 남의 휴지통 글에도 403이 먼저인지 함께 본다)
- [X] T027 [P] [US2] 복구 후 공개 목록 위치 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostRestoreListPositionIT.java`에 작성한다(005 선행): 공개 글 5개 중 3번째 글을 지웠다가 복구하면 `GET /api/posts`(홈)와 `GET /api/members/{handle}/posts`(블로그)의 id 순서와 블로그 글 수가 삭제 전과 같다(US2-1, 13 D-4)

### Implementation for User Story 2

- [X] T028 [P] [US2] 복구 응답 DTO `backend/src/main/java/com/team/blog/post/web/dto/RestoreResponse.java`(record `boolean restored = true, PostStatus status, Visibility visibility`)를 만든다. contracts/openapi.yaml `RestoredResult`와 같아야 한다(research R19)
- [X] T029 [US2] `backend/src/main/java/com/team/blog/post/application/PostTrashService.java`에 `RestoreOutcome restore(long me, long postId)`를 추가한다: `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP)` → `lockOwned` 없으면 404, `isTrashed() == false`이면 404(FR-028, 42 §5-2), 맞으면 `clearTrashed(postId)`, `PostRestored(postId, me, now)` 발행, `status`·`visibility` 반환. `updated_at`·`first_public_at`·`hidden_at`은 건드리지 않는다(FR-027, R3). INFO 로그는 id만
- [X] T030 [US2] `backend/src/main/java/com/team/blog/post/web/PostTrashController.java`에 `POST /api/posts/{postId}/restore`를 추가한다(T028·T029 의존): 200 + `RestoreResponse`, 이메일 인증 전 허용(`CONTENT_WRITE` 쓰지 않음)
- [ ] T031 [P] [US2] `frontend/src/api/managePosts.ts`에 `restorePost(postId): Promise<{restored:true,status,visibility}>`(`POST /api/posts/{postId}/restore`)를 추가한다
- [ ] T032 [US2] `frontend/src/features/manage-posts/useTrashActions.ts`에 `restore(row)`를 추가한다(T031 의존): 확인창 없이 호출(FR-027) → 휴지통 줄 제거·휴지통 수 −1·원래 탭 수 +1 → 토스트 "복구했어요" + 링크 버튼 `status === "PUBLISHED"`이면 "[발행 글 탭에서 보기]"(`/manage/posts?tab=published`), DRAFT이면 "[임시글 탭에서 보기]"(`?tab=drafts`)

**Checkpoint**: US1·US2 API가 모두 통합 테스트를 통과한다 — 지우고 되살리는 왕복이 원래 상태를 100% 보존한다

---

## Phase 5: User Story 3 - 내 글 관리 화면에서 내 글 훑어보기 (Priority: P1)

**Goal**: 로그인한 회원이 `/manage/posts`에서 [임시글]·[발행 글]·[휴지통] 세 탭으로 자기 글만 20개씩 본다. 탭 옆에 글 수가 보인다 (C-MANAGE-1)

**Independent Test**: 임시글 3개, 공개·비공개 발행 글 25개(그중 1개 휴지통 → 발행 24), 휴지통 글 1개를 가진 회원과 다른 회원을 시드하고 `GET /api/me/posts`를 탭·필터·커서별로 호출해 본인 글만, 개수·정렬·필터·[더 보기]가 맞는지 확인한다(`ManagePostApiIT`)

### Tests for User Story 3 ⚠️

- [ ] T033 [P] [US3] 커서 단위 테스트를 `backend/src/test/java/com/team/blog/post/unit/ManageCursorTest.java`에 작성한다. 목록 구분은 001 T021이 정한 공용 필드 `l`(`ListScope`)을 쓴다 — research R16의 `t`·`f` 필드 이름(제안)을 공용 형식에 맞춘 것: 임시글·발행 글 커서 `{"v":1,"l":"manage:drafts:all|manage:published:all|manage:published:public|manage:published:private","k":[updatedAtµs,id]}`, 휴지통 `{"v":1,"l":"manage:trash","k":[deletedAtµs,id]}`의 인코딩·디코딩 왕복(마이크로초 보존, Base64URL 패딩 없음); 풀리지 않는 값·모르는 `v`·`k` 누락/타입 오류·`l`이 요청 탭·필터와 다름(예: `manage:published:public` 커서를 `visibility=private`로, `home` 커서를 관리 목록에) → 모두 `InvalidCursorException`(400 `INVALID_CURSOR`, research R16)
- [ ] T034 [P] [US3] 관리 목록 API 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/ManagePostApiIT.java`에 작성한다: `US3_1_기본탭_임시글_글수` — `tab` 없으면 drafts, `counts == {drafts:3, published:24, trash:1}`(FR-003·FR-004, 숨긴 글 포함·필터 무관); `US3_2_다른회원_값_무시` — `?authorId=다른회원&memberId=…`를 붙여도 본인 글만(FR-002, SC-007); `US3_3_비회원_401` — 401 `LOGIN_REQUIRED`; `US3_4_발행글_25개_더보기` — 첫 응답 20개 + `nextCursor`, 두 번째 5개 + `nextCursor:null` + `counts:null`, 합쳐 중복 0·누락 0, `updated_at DESC, id DESC`(같은 `updated_at` 글 포함, SC-006); `?size=100`은 무시되어 20개(FR-006); `US3_5_비공개_필터` — `visibility=private`이면 PRIVATE 발행 글만, `visibility=private`를 drafts·trash 탭에 주면 무시(FR-009); `US3_6_상태_표시_필드` — `editing`(post_draft 존재), 휴지통 항목은 원래 `status`·`deletedAt`·`purgeAt = deletedAt+30일`, 정렬 `deleted_at DESC, id DESC`(FR-005·FR-011); `US3_7_숨긴글_발행탭에_hidden_true`(FR-010); `US3_8_발행글_조회수_좋아요수_댓글수`(41 M-8); 응답 항목 JSON 키 집합이 정확히 `id,title,status,visibility,editing,hidden,updatedAt,publishedAt,editedAt,deletedAt,purgeAt,viewCount,likeCount,commentCount`이고 `contentMd`·`contentHtml` 없음(FR-012); 모르는 `tab` → 400 `INVALID_TAB` + `errors[0].field == "tab"`; `visibility=friends` → 400 `INVALID_VISIBILITY`; 다른 탭 커서 → 400 `INVALID_CURSOR`; `Cache-Control: private, no-store`; `GET /api/me/trash`가 `GET /api/me/posts?tab=trash`와 같은 본문(research R15); 이메일 인증 전 200, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`
- [ ] T035 [P] [US3] 성능·인덱스 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/ManagePostPerformanceIT.java`에 작성한다: 회원 1명에게 글 1만 건(임시·발행·휴지통 혼합)과 다른 회원 글 1만 건을 `generate_series`로 시드한 뒤 ① 탭 3개 첫 페이지·두 번째 페이지 서버 응답이 각각 300ms 이내(SC-005, 워밍업 후 측정) ② 목록 SQL의 `EXPLAIN`에 drafts·published는 `ix_post_manage`, trash는 `ix_post_trash`가 나온다 ③ 첫 요청의 SQL 실행 수 = 2(목록 + 개수), 커서 요청 = 1(Hibernate statistics 또는 datasource-proxy로 셈, N+1 금지)
- [ ] T036 [P] [US3] 프런트 목록 훅 테스트를 `frontend/src/features/manage-posts/useManagePosts.test.ts`(Vitest)에 작성한다: [더 보기] 응답에 이미 화면에 있는 id가 섞여 오면 건너뛴다(41 §5, research R17, SC-006); `counts`는 첫 응답 값만 쓰고 이후 응답의 `null`로 덮지 않는다; `adjustCounts({trash:+1, published:-1})`가 화면 숫자만 바꾼다(FR-004); 탭·필터를 바꾸면 커서와 목록을 초기화한다
- [ ] T037 [P] [US3] 표시 계산 테스트를 `frontend/src/features/manage-posts/format.test.ts`(Vitest)에 작성한다: `daysUntilPurge(purgeAt, now)`는 한국 시간 날짜 차이 — 10월 2일 삭제(`purgeAt` 11월 1일)를 10월 5일에 보면 27, 0 이하면 "곧 완전 삭제"(research R23); `formatSavedAt`은 24시간 이내 "N분 전"/"N시간 전", 그 밖 "10월 3일 14:03"(FR-007); `formatPublished` "발행 2026.10.01" + `editedAt` 있으면 "· 수정됨 10월 3일"(FR-008); 휴지통 원래 상태 "(임시글이었음)"/"(발행 글이었음)"(FR-011)

### Implementation for User Story 3

- [ ] T038 [P] [US3] 탭 enum `backend/src/main/java/com/team/blog/post/domain/ManageTab.java`(`DRAFTS("drafts")`, `PUBLISHED("published")`, `TRASH("trash")`, `static ManageTab fromParam(String)` — null이면 DRAFTS, 모르는 값이면 `InvalidTabException`)과 400 `INVALID_TAB` 오류(`errors: [{field:"tab", code:"INVALID_TAB", message}]`, research R20)를 만든다. 예외 클래스는 `backend/src/main/java/com/team/blog/post/domain/InvalidTabException.java`에 두고 001의 `GlobalExceptionHandler` 규칙(400 검증 오류)에 맞춘다
- [ ] T039 [P] [US3] 커서 값 객체 `backend/src/main/java/com/team/blog/post/domain/ManageCursor.java`(record `ManageTab tab, String filter, Instant key, long id`; `ListScope scope()` = `manage:{tab}[:{filter}]`, `encode(CursorCodec)`, `static decode(String raw, ManageTab tab, String filter, CursorCodec)`)를 001의 `CursorCodec`과 `ListScope.of(String)`(둘 다 specs/001 T021)로 구현한다. 시각은 epoch 마이크로초, `l` 불일치는 `InvalidCursorException`(T033 통과)
- [ ] T040 [P] [US3] 응답 DTO를 `backend/src/main/java/com/team/blog/post/web/dto/ManagePostItem.java`(14개 필드, 본문 필드 없음 — data-model §2-1), `ManageCounts.java`(`drafts, published, trash`), `ManagePostPage.java`(`items`, `nextCursor` nullable, `counts` nullable)로 만든다. `title`은 `varchar(100)` 그대로, `deletedAt`·`purgeAt`은 휴지통 탭에서만 값
- [ ] T041 [US3] 목록 조회 저장소 `backend/src/main/java/com/team/blog/post/infra/ManagePostQueryRepository.java`를 `NamedParameterJdbcTemplate` 네이티브 SQL로 구현한다(T038·T040 의존, research R18): 임시글·발행 글 = `SELECT p.id, p.title, p.status, p.visibility, (d.post_id IS NOT NULL) AS editing, (p.hidden_at IS NOT NULL) AS hidden, p.updated_at, p.published_at, p.edited_at, p.deleted_at, p.view_count, p.like_count, p.comment_count FROM post p LEFT JOIN post_draft d ON d.post_id = p.id WHERE p.author_id = :me AND p.status = :status AND p.deleted_at IS NULL [AND p.visibility = :vis] [AND (p.updated_at, p.id) < (:t, :id)] ORDER BY p.updated_at DESC, p.id DESC LIMIT :limitPlusOne`; 휴지통 = `… WHERE p.author_id = :me AND p.deleted_at IS NOT NULL [AND (p.deleted_at, p.id) < (:t, :id)] ORDER BY p.deleted_at DESC, p.id DESC LIMIT :limitPlusOne`; 개수 = `SELECT p.status AS k, count(*) FROM post p WHERE p.author_id = :me AND p.deleted_at IS NULL GROUP BY p.status UNION ALL SELECT 'TRASH', count(*) FROM post p WHERE p.author_id = :me AND p.deleted_at IS NOT NULL` 한 문장. `content_md`·`content_html`은 SELECT하지 않는다(FR-012)
- [ ] T042 [US3] `backend/src/main/java/com/team/blog/post/application/ManagePostQueryService.java`를 구현한다(T039·T041 의존): `ManagePostPage list(long me, ManageTab tab, String visibilityFilter, String rawCursor)` — 먼저 `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP)`(탈퇴 유예·정지 403, 인증 전 허용 — contracts `listMyPosts` 403), 페이지 크기는 `ManageProperties.pageSize`(클라이언트 값 무시), `pageSize + 1`개 조회로 `nextCursor` 결정, `visibilityFilter`는 `tab == PUBLISHED`일 때만 적용(`public|private` 외 값은 400 `INVALID_VISIBILITY`, 다른 탭에서는 무시), `rawCursor == null`일 때만 개수 쿼리 실행, 휴지통 항목 `purgeAt = deletedAt + TrashProperties.retention`. `@Transactional(readOnly = true)`
- [ ] T043 [US3] `backend/src/main/java/com/team/blog/post/web/ManagePostController.java`를 만든다(T042 의존): `GET /api/me/posts?tab=&visibility=&cursor=`와 별칭 `GET /api/me/trash?cursor=`(tab=trash 고정, 같은 Service 메서드, research R15). 사용자 id는 `CurrentUser`에서만 꺼내고 `authorId` 등 요청 파라미터를 선언하지 않는다(FR-002). 응답 헤더 `Cache-Control: private, no-store`. 이메일 인증 전 허용(42 §10)
- [ ] T044 [P] [US3] `frontend/src/api/managePosts.ts`에 타입 `ManagePostItem`·`ManageCounts`·`ManagePostPage`와 `listMyPosts({tab, visibility, cursor})`(`GET /api/me/posts`)를 추가한다. `size` 파라미터는 보내지 않는다
- [ ] T045 [P] [US3] 표시 계산 함수 `frontend/src/features/manage-posts/format.ts`(`daysUntilPurge`, `formatSavedAt`, `formatPublished`, `originalStatusLabel`)를 Asia/Seoul 기준으로 구현한다(T037 통과)
- [ ] T046 [US3] 목록 훅 `frontend/src/features/manage-posts/useManagePosts.ts`를 구현한다(T044 의존): 상태 `items`·`nextCursor`·`counts`·`loading`·`error`; `loadMore()`는 이미 있는 id를 건너뛰고 이어 붙임; `reload()`는 첫 페이지 재요청(counts 갱신); `removeRow(id)`·`updateRow(id, patch)`·`adjustCounts(delta)`; 401이면 004 `useAuthGate`로 `/login?returnTo=/manage/posts?…` 이동(FR-001)(T036 통과)
- [ ] T047 [P] [US3] 탭 컴포넌트 `frontend/src/features/manage-posts/ManageTabs.tsx`를 만든다: "임시글 3 · 발행 글 24 · 휴지통 1" 형식(FR-004), 기본 [임시글], 선택 탭은 `aria-selected`, 탭 변경 시 URL `?tab=` 갱신
- [ ] T048 [P] [US3] 임시글 줄 `frontend/src/features/manage-posts/DraftRow.tsx`를 만든다(FR-007): 제목(비면 회색 "(제목 없음)"), "마지막 저장 …"(`formatSavedAt`), [이어 쓰기](`/write/{id}`)·[삭제](`useTrashActions.trash`), 공개 범위는 표시하지 않음, 줄 아래 `rowErrors[id]` 표시. 한 줄 목록(카드 아님), 제목은 텍스트로만 렌더링
- [ ] T049 [P] [US3] 발행 글 줄 `frontend/src/features/manage-posts/PublishedRow.tsx`를 만든다(FR-008·FR-010): 제목 앞 004 `VisibilityBadge`(🌐/🔒 + 스크린 리더 글자 "공개"/"비공개"), `editing`이면 [수정 중] 배지, `hidden`이면 "운영 정책에 따라 숨겨짐" 배지, "발행 2026.10.01 · 수정됨 10월 3일", 조회수·좋아요 수·댓글 수, [보기](상세 링크)·[수정]/`editing`이면 [이어서 수정]·[삭제](`useTrashActions.trash`). [공개 범위 ▾]·[변경 취소]는 US5에서 붙인다
- [ ] T050 [P] [US3] 휴지통 줄 `frontend/src/features/manage-posts/TrashRow.tsx`를 만든다(FR-011): 제목 + `originalStatusLabel`, "삭제 10월 2일 · 27일 뒤 완전 삭제"(`daysUntilPurge`, 0 이하면 "곧 완전 삭제"), [복구](`useTrashActions.restore`). [영구 삭제]는 US4에서 붙인다
- [ ] T051 [US3] 페이지 `frontend/src/pages/ManagePostsPage.tsx`를 만들고 라우터에 `/manage/posts`를 등록한다(T046~T050 의존): URL `?tab=drafts|published|trash&visibility=public|private`(기본 drafts), 발행 글 탭에만 [전체]·[공개]·[비공개] 필터(FR-009), 휴지통 탭 위 안내 문구(FR-011), [더 보기] 버튼(`nextCursor`가 null이면 숨김), 빈 목록 안내, 375px에서 가로 스크롤 없이 버튼 줄바꿈(헌법 비기능). 검색·일괄 처리 UI는 만들지 않는다(FR-004, 41 M-10)

**Checkpoint**: 로그인한 회원이 `/manage/posts`에서 자기 글만 탭별로 보고, 삭제·복구 버튼이 동작한다(US1·US2와 연결됨)

---

## Phase 6: User Story 4 - 영구 삭제와 30일 뒤 자동 완전 삭제 (Priority: P2)

**Goal**: 휴지통 글을 즉시 영구 삭제하거나, 30일 지난 휴지통 글을 매일 새벽 배치가 완전히 지운다. 댓글·좋아요·태그 연결·작업본·조회 기록은 함께 지우고, 태그 자체·신고 기록은 남긴다 (C-POST-5 #3, 13 D-1·D-5)

**Independent Test**: 댓글(남의 것 포함)·좋아요·태그·사진·대기 중 신고가 있는 휴지통 글을 `DELETE /api/posts/{postId}/permanent`로 지우고, 31일 전에 지운 글로 `TrashPurgeJob`을 실행한 뒤 남은 데이터를 확인한다(`PostPurgeIT`, `TrashPurgeJobIT`)

### Tests for User Story 4 ⚠️

- [ ] T052 [P] [US4] 영구 삭제 API 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostPermanentDeleteApiIT.java`에 작성한다: `US4_1_휴지통글_영구삭제` — 200 `{purged:true}`, 행 없음, `PostPurged` 1회; `US4_2_휴지통에_없는_글은_404` — 정상 글·남의 휴지통 글·없는 글 404, DB 변화 없음(FR-029, SC-004); 같은 글 두 번째 요청 404; 판정 순서 401·403·인증 전 200; CSRF 없으면 403. 같은 작업에서 004 하네스용 실행기 `backend/src/test/java/com/team/blog/post/permission/PurgePostAction.java`(`DELETE /api/posts/{postId}/permanent`, owner 006)를 등록해 `post-write.csv`의 영구 삭제 행을 실행되게 한다
- [ ] T053 [P] [US4] 완전 삭제 연쇄 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/PostPurgeIT.java`에 작성한다(SQL 픽스처로 행을 직접 넣음): `US4_1_연관행_모두_삭제` — 영구 삭제 뒤 `comment`(남이 쓴 댓글·답글 포함)·`post_like`·`post_tag`·`post_image`·`post_draft`·`post_view_daily`·`notification`·`notification_actor`에 그 글 관련 행 0건(FR-031); `US4_4_그글에서만_쓴_사진만_detached` — 전용 사진 `image.detached_at` 채워짐, 다른 정상 글·다른 휴지통 글과 공유한 사진은 NULL(FR-032, research R12); `US4_5_대기신고_대상없음_종료` — 글 대상·그 글 댓글 대상 `PENDING` 사건은 `CLOSED_NO_TARGET`, `handled_at` 값, `handled_by` NULL, `post_id`·`comment_id` NULL, `report` 행 유지; 이미 처리된 사건은 상태 그대로(FR-033); `US4_6_태그는_남음` — `tag` 행 유지(FR-034); `US4_7_번호_재사용_안함` — 새 글 id > 지운 id(FR-035); `purgeAllByAuthor(authorId)` — 그 회원의 정상·휴지통 글이 모두 지워지고 글마다 `PostPurged` 1회, 다른 회원 글은 그대로(015용, research R25)
- [ ] T054 [P] [US4] 휴지통 비우기 배치 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/TrashPurgeJobIT.java`에 작성한다(`TrashPurgeJob.run()`을 직접 호출, `Clock` 고정): `US4_3_30일_지난글만_삭제` — `deleted_at` 31일 전 글은 사라지고 29일 전 글·정상 글은 남는다(SC-003); 250개 시드 → 한 번 실행으로 100·100·50 묶음 모두 처리; 대상 조회 뒤 처리 전에 복구된 글은 남는다(잠근 뒤 재확인); 한 글의 `PostPurgeStep`이 예외를 던져도 나머지 글은 지워지고 실패 글은 남는다(글마다 트랜잭션); `purge-max-duration`을 아주 짧게 주면 다음 묶음을 시작하지 않는다; 두 스레드에서 `run()`을 동시에 호출해도 ShedLock(`trashPurgeJob`)으로 한 번만 처리된다; 배치가 지운 글마다 `PostPurged` 1회; 실행 결과 INFO 로그에 처리·건너뜀·실패 수·소요 시간

### Implementation for User Story 4

- [ ] T055 [P] [US4] 영구 삭제 응답은 T020의 `TrashResponse.Purged`를 재사용한다. `backend/src/main/java/com/team/blog/post/web/dto/TrashResponse.java`의 Javadoc에 `DELETE /api/posts/{postId}/permanent` 응답(contracts `PurgedResult`)으로도 쓰인다고 적는다
- [ ] T056 [US4] `backend/src/main/java/com/team/blog/post/application/PostTrashService.java`에 `void purgePermanently(long me, long postId)`를 추가한다: `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP)` → `lockOwned` 없거나 `isTrashed() == false`이면 404(FR-029), 맞으면 `postPurgeService.purge(postId, me, true)`. INFO 로그는 id만
- [ ] T057 [US4] `backend/src/main/java/com/team/blog/post/web/PostTrashController.java`에 `DELETE /api/posts/{postId}/permanent`를 추가한다(T055·T056 의존): 200 `{purged:true}`, 이메일 인증 전 허용(`CONTENT_WRITE` 쓰지 않음)
- [ ] T058 [P] [US4] (007 소유 예정, 최소 구현) `backend/src/main/java/com/team/blog/interaction/application/CommentQueryService.java`에 공개 메서드 `List<Long> commentIdsOfPost(long postId)` = `SELECT id FROM comment WHERE post_id = :postId`(답글 포함)를 추가한다. 클래스가 없으면 이 메서드 하나로 만들고 Javadoc에 "007이 확장·소유, 006 신고 종료 단계용"을 적는다(research R11)
- [ ] T059 [P] [US4] `CommentQueryService.commentIdsOfPost` 통합 테스트를 `backend/src/test/java/com/team/blog/interaction/integration/CommentIdsOfPostIT.java`에 작성한다: 댓글·답글 id를 모두 돌려주고 다른 글의 댓글은 포함하지 않는다. 댓글이 없으면 빈 목록
- [ ] T060 [P] [US4] (003 소유 예정, 임시 구현) `backend/src/main/java/com/team/blog/media/application/ImagePostPurgeStep.java`를 `PostPurgeStep` 구현 `@Component`(order 20)로 만든다: `UPDATE image i SET detached_at = now() WHERE i.detached_at IS NULL AND EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = i.id AND pi.post_id = :id) AND NOT EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = i.id AND pi.post_id <> :id)`(research R12). 파일 삭제는 하지 않는다(003 정리 배치가 7일 뒤). Javadoc에 "003-image-upload plan에서 소유·검토 후 교체"를 적는다
- [ ] T061 [US4] (014 소유 예정, 임시 구현) `backend/src/main/java/com/team/blog/moderation/application/ReportPostPurgeStep.java`를 `PostPurgeStep` 구현 `@Component`(order 10)로 만든다(T058 의존): `commentIds = commentQueryService.commentIdsOfPost(postId)` 후 `UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL WHERE status = 'PENDING' AND (post_id = :id OR comment_id = ANY(:commentIds))`(13 §2-5 0단계, research R11). 이벤트는 발행하지 않는다. 패키지 이름 `moderation`은 가정이며 Javadoc에 "014-report-hide plan이 패키지·소유 확정 후 이전"을 적는다
- [ ] T062 [US4] 휴지통 비우기 배치 `backend/src/main/java/com/team/blog/post/application/TrashPurgeJob.java`를 구현한다(T010·T011 의존, contracts/events.md §3): `@Scheduled(cron = "${blog.post.trash.purge-cron}", zone = "${blog.time-zone}")` + `@SchedulerLock(name = "trashPurgeJob", lockAtMostFor = "PT40M")`; `run()`은 `cutoff = now - retention`으로 `findExpiredIds(cutoff, purgeBatchSize)`를 반복하고, 글마다 `TransactionTemplate`(REQUIRES_NEW)에서 `lockById` → 없거나 `deletedAt == null` 또는 `deletedAt >= cutoff`이면 건너뜀 → 아니면 `postPurgeService.purge(id, authorId, true)`; 예외(23001·23503 포함)는 그 글만 롤백·ERROR 로그 후 다음 글(실패 id는 같은 실행에서 다시 고르지 않도록 제외 목록 유지); 대상이 없거나 `purgeMaxDuration`에 이르면 종료; 끝에 처리·건너뜀·실패 수·소요 시간 INFO 로그. 시각은 주입한 `Clock`으로 계산(T054 통과)
- [ ] T063 [US4] `backend/src/main/java/com/team/blog/post/application/PostPurgeService.java`에 `void purgeAllByAuthor(long authorId)`(`@Transactional(propagation = MANDATORY)`)를 추가한다: `findIdsByAuthorIncludingTrashed(authorId)`의 글마다 `purge(id, authorId, true)`. 015의 `PostWithdrawalPurgeStep`(order 10)이 호출할 공개 메서드이며, `PostWithdrawalPurgeStep` 클래스 자체는 015 tasks에서 만든다(research R25)
- [ ] T064 [P] [US4] `frontend/src/api/managePosts.ts`에 `purgePost(postId): Promise<{purged:true}>`(`DELETE /api/posts/{postId}/permanent`)를 추가한다
- [ ] T065 [US4] `frontend/src/features/manage-posts/useTrashActions.ts`에 `purge(row)`(확인창 FR-029 → `purgePost` → 줄 제거·휴지통 수 −1)를 추가하고, `frontend/src/features/manage-posts/TrashRow.tsx`에 [영구 삭제] 버튼을 붙인다(T050·T064 의존)

**Checkpoint**: 영구 삭제와 30일 배치가 모두 같은 `PostPurgeService` 경로로 지우고, 신고·사진 단계가 DELETE 전에 실행된다

---

## Phase 7: User Story 5 - 관리 화면에서 바로 처리하기 (Priority: P2)

**Goal**: 관리 화면 각 줄에서 공개 범위 변경(004)·변경 취소(002)·새 글(002)을 바로 하고, 그 줄만 바뀐다. 실패하면 줄 아래에 이유를 보이고 목록을 다시 불러온다 (C-MANAGE-1 #7, 41 §4)

**Independent Test**: 관리 화면에서 [공개 범위 ▾], [변경 취소], [새 글]을 각각 눌러 결과가 002·004 규칙과 같고 그 줄만 바뀌는지 컴포넌트 테스트로 확인한다

### Tests for User Story 5 ⚠️

- [ ] T066 [P] [US5] 발행 글 줄 컴포넌트 테스트를 `frontend/src/features/manage-posts/PublishedRow.test.tsx`(Vitest + Testing Library, API는 모킹)에 작성한다: `US5_1` 공개 → 비공개는 확인창 없이 `PUT /api/posts/{id}/visibility` 호출, 배지만 🔒로 바뀌고 "수정됨" 글자가 생기지 않는다; `US5_2` 비공개 → 공개는 "모든 사람이 볼 수 있게 돼요" 확인 후 호출, [취소]면 호출 없음; `US5_3` [변경 취소]는 확인 후 `DELETE /api/posts/{id}/working-copy` 호출, [수정 중] 배지가 사라지고 버튼이 [수정]으로 바뀜; 제목 `<img src=x onerror=alert(1)>`이 글자 그대로 보인다(헌법 IV)
- [ ] T067 [P] [US5] 페이지 컴포넌트 테스트를 `frontend/src/pages/ManagePostsPage.test.tsx`에 작성한다: `US5_4` [새 글]은 확인창 없이 `POST /api/posts` 후 `/write/{postId}`로 이동; `US5_5` [삭제]·[복구] 요청이 404 `NOT_FOUND`면 그 줄 아래에 이유가 보이고 `GET /api/me/posts`를 다시 부른다(FR-013); 성공한 처리 뒤 다른 줄의 DOM은 다시 만들어지지 않는다(그 줄만 갱신); 비회원(401)은 `/login?returnTo=%2Fmanage%2Fposts…`로 이동(US3-3, FR-001)

### Implementation for User Story 5

- [ ] T068 [US5] `frontend/src/features/manage-posts/PublishedRow.tsx`에 004 `VisibilitySelect`를 [공개 범위 ▾]로 붙인다(T049 의존): PRIVATE → PUBLIC일 때만 `confirmDialogs` 공개 문구로 확인, 004 `api/posts.ts`의 `setVisibility` 호출, 성공 시 `updateRow(id, {visibility})`만, 실패는 `useRowAction`(FR-015, 06 §4)
- [ ] T069 [US5] `frontend/src/features/manage-posts/PublishedRow.tsx`에 [변경 취소]를 붙인다(T068 다음): `editing`일 때만 보이고, 확인(FR-016) 후 002 `api/posts.ts`의 작업본 삭제(`DELETE /api/posts/{postId}/working-copy`) 호출, 성공 시 `updateRow(id, {editing:false})`
- [ ] T070 [US5] `frontend/src/pages/ManagePostsPage.tsx`에 [새 글] 버튼을 붙인다(T051 의존): 확인창 없이 002 `api/posts.ts`의 새 글 생성(`POST /api/posts`) → `/write/{postId}`로 이동(FR-014). 실패 시 페이지 상단에 이유 표시
- [ ] T071 [US5] 세 줄 컴포넌트(`frontend/src/features/manage-posts/DraftRow.tsx`, `PublishedRow.tsx`, `TrashRow.tsx`)가 모두 `useRowAction`의 `rowErrors[id]`를 줄 바로 아래 `role="alert"` 글자로 보이고, 실행 중 그 줄 버튼을 비활성화하는지 맞춘다(FR-013). `ManagePostsPage`가 `useManagePosts.reload`를 `useRowAction`에 주입한다(T066·T067 통과)

**Checkpoint**: 모든 user story가 독립적으로 동작한다

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 여러 스토리에 걸친 동시성·보안·검증

- [ ] T072 동시성 통합 테스트를 `backend/src/test/java/com/team/blog/post/integration/TrashConcurrencyIT.java`에 작성하고 통과시킨다(FR-036, 13 §2-4, 엣지 케이스): 같은 글에 삭제·복구·영구 삭제를 각 10개 스레드로 동시에 보내도 최종 상태가 정상/휴지통/없음 중 하나로 정해지고 500 응답 0건; `PostTrashed`·`PostRestored`·`PostPurged` 발행 수가 실제 상태 변화 수와 같다; 휴지통 이동과 `TrashPurgeJob.run()`이 겹쳐도 deadlock·500 없음; 관리 목록 조회는 쓰기 잠금 중에도 막히지 않는다
- [ ] T073 [P] 로그·응답 점검: `backend/src/main/java/com/team/blog/post/application/` 아래 006 클래스(`PostTrashService`, `PostPurgeService`, `TrashPurgeJob`)의 로그에 제목·본문·이메일이 없고 id·건수만 있는지, 006 API 응답 본문에 `contentMd`·`contentHtml`·`authorId`가 없는지 확인하고 고친다(FR-012, 헌법 III·IV)
- [ ] T074 [P] 프런트 보안·접근성 점검: `frontend/src/features/manage-posts/`와 `frontend/src/pages/ManagePostsPage.tsx`에 `dangerouslySetInnerHTML`이 없는지, 공개 범위 표시에 스크린 리더 글자("공개"/"비공개")가 있는지, 확인창 포커스 이동이 맞는지 확인한다(FR-008, 헌법 IV)
- [ ] T075 quickstart.md §2 자동 테스트를 실행한다: `./mvnw -pl backend verify -Dit.test='ManagePostApiIT,ManagePostPerformanceIT,PostTrashApiIT,PostRestoreApiIT,PostRestoreListPositionIT,PostPermanentDeleteApiIT,PostPurgeIT,PostPurgeServiceIT,TrashPostRepositoryIT,TrashPurgeJobIT,TrashConcurrencyIT,TrashedPostPermissionMatrixIT,CommentIdsOfPostIT,PermissionMatrixIT'`(마지막은 004 소유 — 006 실행기 등록 뒤 owner 006 행 건너뜀 0건)와 `cd frontend && npx vitest run src/features/manage-posts src/pages/ManagePostsPage.test.tsx`가 모두 통과하는지 확인한다
- [ ] T076 quickstart.md §3 curl 시나리오 1)~7)과 DB 확인 쿼리, §4 화면 확인 1~6(375px·데스크톱 가로 스크롤 없음 포함)을 `docker compose up` 환경에서 수동으로 실행해 기대 결과와 같은지 확인한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001 Phase 1·2, specs/002·004 Foundational이 끝나야 Phase 1(T001·T002) 확인을 통과한다
- **Setup (Phase 1)**: Cross-feature 선행 완료 확인만 한다
- **Foundational (Phase 2)**: Setup 완료 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
  - US1(T018)은 002 자동 저장·발행 API와 004 공개 범위 API의 휴지통 404 처리가 선행되어야 한다; US1(T017·T021)은 002 `AutosaveService.flushNow` 선행
  - US1(T019)·US2(T027)는 005 상세·홈·블로그 API(005 T024·T038·T050) 선행, US3(T033·T039)는 001 T021 `ListScope` 선행
  - US5는 002 새 글·변경 취소 API와 004 `VisibilitySelect` 선행
- **Polish (Phase 8)**: US1·US2·US4 완료 후(T072는 세 동작과 배치가 모두 필요)

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 이후 시작. 다른 스토리에 의존하지 않는다(T019의 "휴지통 탭에만 보임" 한 줄은 US3 후 활성화)
- **User Story 2 (P1)**: Foundational 이후 시작. 테스트 픽스처에서 휴지통 상태를 SQL로 직접 만들면 US1 없이도 테스트 가능. 실제 화면 흐름은 US1과 함께 의미가 있다
- **User Story 3 (P1)**: 백엔드(T033~T043)는 Foundational 이후 독립. 프런트 줄 컴포넌트의 [삭제]·[복구] 버튼(T048~T050)은 US1 T025·US2 T032의 `useTrashActions`에 의존
- **User Story 4 (P2)**: 백엔드는 Foundational 이후 독립(`PostPurgeService`는 Phase 2). 프런트 T065는 US3 T050(`TrashRow`)에 의존
- **User Story 5 (P2)**: US3의 `PublishedRow`·`ManagePostsPage`(T049·T051)에 의존

### Within Each User Story

- Tests (T0xx 테스트 작업) MUST be written and FAIL before implementation
- DTO·값 객체 → Repository → Service → Controller → 프런트 API → 훅 → 컴포넌트
- 같은 파일을 고치는 작업(`PostTrashApiIT` T016→T017→T018, `PostTrashService` T021→T023→T029→T056, `PostTrashController` T022→T030→T057, `useTrashActions` T025→T032→T065, `api/managePosts.ts` T024→T031→T044→T064, `PublishedRow` T049→T068→T069)은 순서대로 한다
- Story complete before moving to next priority

### Parallel Opportunities

- Phase 2: 테스트 T003·T004·T005, 구현 T006·T007·T008·T009, 프런트 T012·T013·T014가 서로 병렬
- US1: T019(매트릭스)는 T016~T018(PostTrashApiIT)과 병렬, T020·T024는 서비스 구현과 병렬
- US2: T026·T027 테스트 병렬, T028·T031 병렬
- US3: 테스트 T033~T037 모두 병렬, 구현 T038·T039·T040·T044·T045 병렬, 줄 컴포넌트 T047~T050 병렬
- US4: 테스트 T052·T053·T054 병렬, T058·T059·T060·T064 병렬
- US5: T066·T067 병렬
- Foundational 완료 후 US1·US3(백엔드)·US4(백엔드)는 서로 다른 파일 위주라 팀원별로 병렬 진행 가능(공유 파일 `PostTrashService`·`PostTrashController`만 조율)

---

## Parallel Example: User Story 3

```bash
# User Story 3 테스트를 함께 작성:
Task: "ManageCursorTest in backend/src/test/java/com/team/blog/post/unit/ManageCursorTest.java"
Task: "ManagePostApiIT in backend/src/test/java/com/team/blog/post/integration/ManagePostApiIT.java"
Task: "ManagePostPerformanceIT in backend/src/test/java/com/team/blog/post/integration/ManagePostPerformanceIT.java"
Task: "useManagePosts.test.ts in frontend/src/features/manage-posts/useManagePosts.test.ts"
Task: "format.test.ts in frontend/src/features/manage-posts/format.test.ts"

# User Story 3 값 객체·DTO를 함께 구현:
Task: "ManageTab in backend/src/main/java/com/team/blog/post/domain/ManageTab.java"
Task: "ManageCursor in backend/src/main/java/com/team/blog/post/domain/ManageCursor.java"
Task: "ManagePostItem/ManageCounts/ManagePostPage in backend/src/main/java/com/team/blog/post/web/dto/"

# User Story 3 줄 컴포넌트를 함께 구현:
Task: "ManageTabs.tsx", "DraftRow.tsx", "PublishedRow.tsx", "TrashRow.tsx" in frontend/src/features/manage-posts/
```

## Parallel Example: User Story 4

```bash
Task: "PostPermanentDeleteApiIT in backend/src/test/java/com/team/blog/post/integration/PostPermanentDeleteApiIT.java"
Task: "PostPurgeIT in backend/src/test/java/com/team/blog/post/integration/PostPurgeIT.java"
Task: "TrashPurgeJobIT in backend/src/test/java/com/team/blog/post/integration/TrashPurgeJobIT.java"
Task: "ImagePostPurgeStep in backend/src/main/java/com/team/blog/media/application/ImagePostPurgeStep.java"
Task: "CommentQueryService.commentIdsOfPost in backend/src/main/java/com/team/blog/interaction/application/CommentQueryService.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2 → US3)

1. Cross-feature 선행(001 Phase 1·2, 002·004 Foundational) 완료 확인 — Phase 1
2. Complete Phase 2: Foundational (`PostPurgeService`, `TrashPostRepository`, 이벤트, 확장점)
3. Complete Phase 3: User Story 1 → **STOP and VALIDATE**: `PostTrashApiIT`·`TrashedPostPermissionMatrixIT`로 "지운 글이 새지 않음"(SC-001) 확인 — API 수준 최소 증분
4. Phase 4(US2)·Phase 5(US3)까지 끝내면 Tier A 완료 기준 C-POST-5 #1·#2·#4·#5와 C-MANAGE-1을 만족하는 **권장 MVP**가 된다(세 스토리 모두 P1)
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 휴지통 공용 부품 준비
2. US1 → 삭제(휴지통·빈 임시글 즉시 삭제) API + 권한 매트릭스
3. US2 → 복구 API
4. US3 → 내 글 관리 화면(목록·탭·개수·커서) + 삭제·복구 버튼 연결 → 데모(MVP)
5. US4 → 영구 삭제 + 30일 배치 (C-POST-5 #3 완성)
6. US5 → 줄 단위 공개 범위 변경·변경 취소·새 글
7. Polish → 동시성·quickstart 전체 검증

### Parallel Team Strategy

With multiple developers:

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 → US2 (PostTrashService·PostTrashController 소유)
   - Developer B: US3 백엔드(ManagePost*) → US3 프런트
   - Developer C: US4 백엔드(PurgeStep 구현체·TrashPurgeJob) → US5 프런트
3. `PostTrashService`·`PostTrashController`·`useTrashActions`는 A가 소유하고, C의 T056·T057·T065는 A의 작업 뒤에 붙인다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 테스트 이름은 인수 시나리오 번호(`US1_1_…`)로 시작해 spec과 1:1로 대응시킨다(plan Constitution Check VIII)
- 이 기능은 Flyway 마이그레이션을 추가하지 않는다(data-model "스키마 변경 없음"). `shedlock` 테이블은 001 소유
- 임시 구현(T058·T060·T061)은 소유 스펙(007·003·014)이 plan을 만들 때 넘겨준다 — 그때 이 tasks.md의 해당 항목에 "이전 완료"를 표시한다
- Verify tests fail before implementing; commit after each task or logical group
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
