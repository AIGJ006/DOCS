---

description: "Task list for 004-visibility-permission (공개 범위와 권한)"
---

# Tasks: 공개 범위와 권한

**Input**: Design documents from `/specs/004-visibility-permission/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, events.md), quickstart.md

**Tests**: 포함한다. Constitution VIII("권한·데이터 규칙은 실제 DB로 통합 테스트")와 spec FR-047(매트릭스 전체 자동 통합 테스트)이 요구한다. 통합 테스트는 Testcontainers PostgreSQL(+ Redis)을 쓰고 H2는 쓰지 않는다(research R-15). 각 스토리에서 테스트 작업을 구현 작업보다 먼저 두고, 먼저 실패하는지 확인한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/java/com/team/blog/...`, `backend/src/test/java/com/team/blog/...`, `frontend/src/...` (plan.md Project Structure)
- 모든 작업에 저장소 루트 기준 전체 경로를 적는다. 다른 기능 소유 파일은 "NNN 소유"로 표시한다.

---

## Cross-feature Dependencies

이 기능은 Tier A 공통 기반 위에서 동작하고, 동시에 002·005·006이 쓰는 공통 판정 장치(Phase 2)를 소유한다. 다른 기능이 소유한 항목은 여기서 만들지 않는다.

**선행 (이 기능이 기다리는 것)**

- 선행: specs/001 Phase 1 (backend Maven 골격 `com.team.blog` package-by-feature, frontend React 골격, `docker-compose.yml` app+PostgreSQL+Redis+MinIO+Mailpit)
- 선행: specs/001 T011 (Flyway V1 `backend/src/main/resources/db/migration/V1__common_schema.sql`, docs/51 SQL 그대로: `post.visibility`·`first_public_at`·`hidden_at`·`deleted_at`, `ck_post_visibility`·`ck_post_public_at`, `ix_post_feed`·`ix_post_blog`, `member.role`·`status`·`default_visibility`·`withdrawn_at`, `auth_identity.email_verified_at`, `friendship`)
- 선행: specs/001 T008 (Testcontainers 통합 테스트 베이스 `backend/src/test/java/com/team/blog/support/IntegrationTestBase.java`), T009 (테스트 지원: `TestLogin` `POST /test/login-as/{memberId}`(test 프로필), `MemberFixtures`(status·emailVerified·정지 이력), `RedisOutage`)
- 선행: specs/001 T018 (`backend/src/main/java/com/team/blog/shared/error/`: ErrorResponse, FieldError, `ReasonCode` 인터페이스, `CommonReasonCode`, ApiException, NotFoundException(하위 타입과 무관하게 같은 404 본문), AccountStateException, BusinessRuleException, ValidationException, GlobalExceptionHandler)
- 선행: specs/001 T026 (`backend/src/main/java/com/team/blog/shared/security/SecurityConfig.java` + 확장 지점 `SecurityFilterChainCustomizer`, `LoginRequiredEntryPoint` 401 `LOGIN_REQUIRED`), T027 (`MemberPrincipal`·`CurrentUser`·`CurrentUserArgumentResolver`), T028 (`backend/src/main/java/com/team/blog/shared/security/session/ResilientSessionRepository`)
- 선행: specs/001 T037 (`backend/src/main/java/com/team/blog/shared/security/AccountStatusGuard.java` + `ActionKind`(CONTENT_WRITE/ACCOUNT_WRITE/CONTENT_CLEANUP), 구현 `backend/src/main/java/com/team/blog/account/application/AccountStatusGuardService.java`, 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED`), T038 (`backend/src/main/java/com/team/blog/account/application/SessionTerminator.java` `terminateAll(memberId, Optional<String> exceptSessionId)` — 004 plan의 MemberSessionService 대체)
- 선행: specs/001 T039 (`backend/src/main/java/com/team/blog/account/application/MemberQueryService.java` `findAccessInfo(memberId)` → `MemberAccessInfo{role, status, emailVerified}` — 004 plan의 MemberAccessQuery 대체), T042 (`frontend/src/api/client.ts`: CSRF 헤더, `ApiError{status,code,message,errors,details,retryAfter}`, `onUnauthorized` 콜백), T042a (`backend/src/main/java/com/team/blog/account/infra/security/WithdrawnAccountGateFilter.java` — 탈퇴 유예 회원의 허용 목록 외 `/api/**` 403 `ACCOUNT_WITHDRAWN`, FR-031·research R-23)
- 체크포인트: specs/001 Phase 2A(T008~T042) 완료 후 이 기능 Phase 2를 시작한다
- 선행: specs/001 — `PATCH /api/me/settings`(`defaultVisibility`, 001 T118이 004 `VisibilityRegistry`로 값 검사 — 001 쪽 후행 의존)와 `FriendshipQueryService`(US8 선택 구현만)
- 선행: specs/002 Phase 2 — post 도메인 `backend/src/main/java/com/team/blog/post/domain/Post.java`(`@SQLRestriction("deleted_at IS NULL")`, `visibility` `@Enumerated(EnumType.STRING)`), `PostStatus`, `backend/src/main/java/com/team/blog/post/infra/PostRepository.java`, `post_draft` 작업본. US1(T031·T032)이 이 파일에 메서드를 **추가**한다
- 선행: specs/002 — `POST /api/posts`(새 임시글이 `MemberQueryService.defaultVisibility`로 시작, US5 검증용)
- 공유: `PostWentPublic` record(`com.team.blog.shared.event`)는 002(발행)와 004(공개 범위 변경)가 함께 발행한다. 먼저 구현하는 쪽이 만들고 다른 쪽은 재사용한다(T030)

**후행 (이 기능의 Phase 2를 기다리는 것)**

- 후행: specs/002·005·006·007·008·009·010·012·014는 `PostReadService.requireReadable`(T017), `PostAccessPolicy`(T015), `VisibilityFilter`(T018), `Viewer`·`CurrentViewerResolver`(T010·T011), `CacheControlPolicy`(T019), `NotFoundPageRenderer`(T020), 권한 매트릭스 하네스(T021~T023)를 사용만 한다
- 후행: specs/002·006·014는 자기 행동(저장·발행·변경 취소·삭제·복구·영구 삭제·숨김)의 `PermissionAction` 실행기를 하네스(T022)에 등록해 `post-write.csv`의 대기 행을 채운다
- 후행(교차 검증): specs/005의 `GET /api/posts`·`GET /api/members/{handle}/posts`·`GET /api/posts/{postId}`·`PageShellController(/@{handle}/posts/{postId})`가 생기면 T073·T074로 HTTP 경로 매트릭스와 화면 404 동일성을 검증한다

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 공통 기반(001 소유)이 준비됐는지 확인만 한다. 이 기능은 새 프로젝트 골격·마이그레이션을 만들지 않는다.

- [X] T001 specs/001 Phase 2A(T008~T042) 완료 확인: `backend/src/test/java/com/team/blog/support/IntegrationTestBase.java`(001 T008)·`TestLogin`·`MemberFixtures`·`RedisOutage`(001 T009), `backend/src/main/java/com/team/blog/shared/error/`의 `CommonReasonCode`에 `LOGIN_REQUIRED`·`EMAIL_NOT_VERIFIED`·`ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED`·`NOT_FOUND`(001 T018), `backend/src/main/java/com/team/blog/shared/security/SecurityConfig.java`·`SecurityFilterChainCustomizer`·`CurrentUser`(001 T026·T027), `AccountStatusGuard`(001 T037)·`SessionTerminator`(001 T038)·`ResilientSessionRepository`(001 T028)·`MemberQueryService.findAccessInfo`(001 T039)·`WithdrawnAccountGateFilter`(001 T042a)가 있고 `./mvnw -q verify`가 통과하는지 확인한다. 빠진 것은 만들지 말고 001에 되돌린다 (구현 메모: 2026-10-07 확인 — 001 Phase 2A 산출물이 모두 있고 `./mvnw -q verify` 통과(통합 68건). `MemberFixtures`는 `support/fixture/`가 아니라 001이 둔 `backend/src/test/java/com/team/blog/support/MemberFixtures.java`에 있다)
- [X] T002 V1 기준선에 이 기능이 기대는 스키마 객체가 있는지 `backend/src/main/resources/db/migration/V1__*.sql`에서 확인한다: `ck_post_visibility` = `visibility IN ('PUBLIC','PRIVATE')`, `ck_post_public_at` = `NOT (status='PUBLISHED' AND visibility='PUBLIC') OR first_public_at IS NOT NULL`, `ix_post_feed ON post (first_public_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL`, `ix_post_blog ON post (author_id, first_public_at DESC, id DESC)`(같은 WHERE), `ck_member_default_visibility`(PUBLIC/PRIVATE, 기본 `'PUBLIC'`), `ck_member_withdrawn` = `(status='WITHDRAWN') = (withdrawn_at IS NOT NULL)` (data-model §1). 다르면 001에 보고한다(이 기능은 공통 마이그레이션을 추가하지 않는다) (구현 메모: V1 그대로 확인함, 다른 점 없음)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 읽기 판정·목록 조건·공개 범위 값·404/캐시 규격·권한 매트릭스 하네스. 이 기능의 모든 스토리와 002·005·006이 이 Phase를 기다린다.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational (먼저 작성, 실패 확인) ⚠️

- [X] T003 [P] `VisibilityRegistry` 단위 테스트 작성 `backend/src/test/java/com/team/blog/post/domain/VisibilityRegistryTest.java`: 공통 Bean 구성(Public·Private Rule)에서 허용값이 정확히 {`PUBLIC`, `PRIVATE`}; `"FRIENDS"`·`"PROTECTED"`·`"public"`(소문자)·`""`·`null`은 `InvalidVisibilityException`(400 `INVALID_VISIBILITY`, errors[0].field = 넘긴 필드 이름); 같은 `visibility()`를 돌려주는 Rule 두 개를 등록하면 생성 시 예외(research R-03·R-21, FR-001·FR-020·FR-049)
- [X] T004 [P] `PostAccessPolicy` 단위 테스트 작성 `backend/src/test/java/com/team/blog/post/domain/PostAccessPolicyTest.java`: `PostView`(status, visibility, deletedAt, hiddenAt, authorWithdrawnAt) × `Viewer`(비회원/다른 회원/작성자/관리자) 표를 FR-033 그대로 검증 — 휴지통은 작성자 포함 모두 false(삭제 여부를 가장 먼저 확인, 13 §2-6 #3); 작성자는 DRAFT·PRIVATE·숨김 글 true; 비작성자는 `PUBLISHED`+`PUBLIC`+`hidden_at NULL`+작성자 `withdrawn_at NULL`일 때만 true; 관리자도 남의 PRIVATE·숨김 글 false(FR-005); 작성자가 탈퇴 유예이면 남에게 false(research R-03, FR-008·FR-010)
- [X] T005 [P] `VisibilityFilter` 단위 테스트 작성 `backend/src/test/java/com/team/blog/post/infra/VisibilityFilterTest.java`: `forViewer(viewer, null)`의 SQL 조각이 정확히 `p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.hidden_at IS NULL AND m.withdrawn_at IS NULL`이고, `authorId`를 주면 `AND p.author_id = :authorId` + 파라미터가 붙는다; 보는 사람이 그 블로그의 작성자여도 조각이 같다(작성자 예외 없음, 06 V-8·H1, FR-007·FR-009, research R-04)
- [X] T006 [P] `CacheControlPolicy` 단위 테스트 작성 `backend/src/test/java/com/team/blog/shared/web/CacheControlPolicyTest.java`: 발행·`PUBLIC`·숨김 아님 글 = `private, no-cache`; 그 밖(PRIVATE·DRAFT, 숨긴 `PUBLIC` 글 — `hidden=true`, 작성자가 보는 경우 포함) = `private, no-store`; 404 = `private, no-store` (research R-12·R-30, FR-015)
- [X] T007 [P] `NotFoundPageRenderer` 단위 테스트 작성 `backend/src/test/java/com/team/blog/shared/web/NotFoundPageRendererTest.java`: 결과 HTML에 `<meta property="og:title" content="볼 수 없는 글이에요">`, `<meta property="og:description" content="친구 공개·비공개 글이거나 삭제된 글입니다.">`, `<meta name="robots" content="noindex">`가 있고, 두 번 렌더링한 바이트가 같으며 요청 값(handle·postId)이 본문에 들어가지 않는다(openapi `/@{handle}/posts/{postId}` 404, FR-013·FR-014, research R-26)
- [X] T008 [P] 공통 404 화면 테스트 작성 `frontend/src/pages/NotFoundPage.test.tsx`: 어떤 404든 같은 문구 "볼 수 없는 페이지예요"만 보이고 이유를 구분하지 않음; API 클라이언트가 404 `NOT_FOUND`를 받으면 이 화면으로 바뀜 (FR-014)

### Implementation for Foundational

- [X] T009 [P] 공개 범위 enum 작성 `backend/src/main/java/com/team/blog/post/domain/Visibility.java`: 공통 값 `PUBLIC`, `PRIVATE`만 둔다(DB 문자열과 같은 이름). `FRIENDS`는 선택 구현자만 추가(US8), `PROTECTED`는 쓰지 않는다(FR-001, research R-01). 이 enum이 유일한 공개 범위 타입이다(001은 `account.domain.Visibility`를 만들지 않고 `Member.defaultVisibility`를 String으로 매핑, 001 T036)
- [X] T010 [P] `Viewer` record 작성 `backend/src/main/java/com/team/blog/shared/security/Viewer.java`: 필드 `Long id`(비회원 null), `Role role`, `MemberStatus status`, `boolean emailVerified`; `anonymous()`, `isAuthenticated()`, `isAdmin()`, `isAuthorOf(long authorId)`(관리자도 자기 글이면 작성자, 42 §2) (data-model §2)
- [X] T011 `CurrentViewerResolver` 작성 `backend/src/main/java/com/team/blog/shared/security/CurrentViewerResolver.java`(+ `WebMvcConfigurer` 등록): 001의 `CurrentUser`/`MemberPrincipal`(세션의 회원 번호, 001 T027)만 입력으로 쓰고, 001 `MemberQueryService.findAccessInfo(memberId)` → `MemberAccessInfo{role, status, emailVerified}`(001 T039, PK 1번)로 `Viewer`를 만든다. 세션이 없거나 세션 저장소 장애로 읽지 못하면 `Viewer.anonymous()`. 같은 요청에서는 request attribute로 한 번만 조회. 요청 본문·쿼리의 `authorId`·`memberId`는 절대 읽지 않는다(FR-026, research R-07·R-23) (depends on T010) (구현 메모: 등록은 `shared/security/ViewerWebMvcConfig.java`. `@WebMvcTest` 슬라이스에서도 Bean이 만들어지도록 `MemberQueryService`를 `ObjectProvider`로 지연 조회. 컨트롤러 밖용 `current()`·`resolve(request)` 추가. 확인 테스트 `shared/security/integration/CurrentViewerResolverIntegrationTest.java`(PK 조회 1번·요청 값 무시·Redis 장애 시 비회원))
- [X] T012 공개 범위 규칙 인터페이스와 공통 구현 작성 `backend/src/main/java/com/team/blog/post/domain/VisibilityRule.java`, `PublicVisibilityRule.java`, `PrivateVisibilityRule.java`: `visibility()`, `canRead(PostView, Viewer)`, `listCondition(Viewer, Long authorId)`. Public은 항상 true, Private는 항상 false(작성자 예외는 `PostAccessPolicy` ②에서 처리), Private의 `listCondition`은 "목록에 넣지 않음"(06 §7 R-3, research R-03) (depends on T009, T010) (구현 메모: `listCondition`의 반환 타입으로 `post/domain/ListCondition.java`(sql·params, `excluded()`)를 둠. `p.visibility = 'PUBLIC'` 문구는 `PublicVisibilityRule`에만 있다)
- [X] T013 `VisibilityRegistry` 작성 `backend/src/main/java/com/team/blog/post/domain/VisibilityRegistry.java`: 등록된 `VisibilityRule` Bean 목록 = 허용값 집합; `rule(Visibility)`, `allowedValues()`, `require(String raw, String field)` → 허용 집합 밖이면 `InvalidVisibilityException(field)`. 같은 값의 Rule이 둘이면 기동 실패 (research R-21, data-model §3) (depends on T012, T016) (구현 메모: `require`는 대소문자·공백을 고치지 않고 정확히 같은 이름만 받음. 목록 조건 조합용 `rules()` 추가)
- [X] T014 [P] 상세 판정용 읽기 투영과 조회 작성 `backend/src/main/java/com/team/blog/post/infra/PostView.java`, `backend/src/main/java/com/team/blog/post/infra/PostQueryRepository.java`: `findPostView(long postId)` → `Optional<PostView>`(`id, authorId, status, visibility, deletedAt, hiddenAt, authorWithdrawnAt`), `post p JOIN member m ON m.id = p.author_id` 네이티브 쿼리 1번(휴지통 행도 읽어 `PostAccessPolicy`가 먼저 거르게 함). member JOIN은 plan Complexity Tracking의 원칙 II 예외 그대로 이 클래스와 `VisibilityFilter`에만 둔다
- [X] T015 `PostAccessPolicy` 작성 `backend/src/main/java/com/team/blog/post/domain/PostAccessPolicy.java`: `canRead(PostView, Viewer)` 순서 ① `deletedAt == null`이 아니면 false ② `viewer.isAuthorOf(authorId)`이면 true ③ `status == PUBLISHED && hiddenAt == null && authorWithdrawnAt == null && registry.rule(visibility).canRead(post, viewer)` (research R-03, FR-010, 06 R-1) (depends on T012, T013, T014)
- [X] T016 [P] 오류 타입 작성 `backend/src/main/java/com/team/blog/post/domain/PostNotFoundException.java`(001의 `shared/error/NotFoundException` 상속, 메시지·이유 구분 없음), `backend/src/main/java/com/team/blog/post/domain/PostReasonCode.java`(001 `ReasonCode` 인터페이스 구현 enum, 값 `INVALID_VISIBILITY` — 001의 `CommonReasonCode`는 고치지 않음), `backend/src/main/java/com/team/blog/post/domain/InvalidVisibilityException.java`(001의 `BusinessRuleException` 상속, code `INVALID_VISIBILITY`, message "공개 범위를 다시 선택해 주세요", errors `[{field, code: INVALID_VISIBILITY, message: "허용되지 않은 공개 범위예요"}]`) (openapi 400 예시, research R-02) (구현 메모: 001 `BusinessRuleException`에 칸 오류 목록을 받는 생성자 `(ReasonCode, String, List<FieldError>, Map)` 하나를 추가함(공유 파일). 002 발행 검증이 모아 쓰도록 `InvalidVisibilityException.fieldError(field)` 제공)
- [X] T017 다른 모듈용 읽기 판정 Service 작성 `backend/src/main/java/com/team/blog/post/application/PostReadService.java`: `requireReadable(long postId, Viewer viewer)` → `PostView` 또는 `PostNotFoundException`. 없음·휴지통·비공개·숨김·탈퇴 유예 작성자를 응답에서 구분하지 않고, 이유는 DEBUG 로그에만 남긴다(06 R-4, research R-26). 007 댓글·009 좋아요·005 상세가 ③단계로 호출한다(FR-011) (depends on T015, T016)
- [X] T018 공용 목록 조건 작성 `backend/src/main/java/com/team/blog/post/infra/VisibilityFilter.java`(+ `backend/src/main/java/com/team/blog/post/infra/SqlCondition.java` record: `String sql`, `Map<String,Object> params`): `forViewer(Viewer viewer, Long authorId)`가 T005의 조각을 돌려준다. 별칭 계약 `p`=post, `m`=member(작성자)를 Javadoc에 적고, 조건 순서·문구는 부분 인덱스 술어와 같게 유지한다(06 R-2·R-2a·R-2b, FR-009, research R-04) (depends on T009, T010)
- [X] T019 [P] 캐시 헤더 정책 작성 `backend/src/main/java/com/team/blog/shared/web/CacheControlPolicy.java`(`forPost(PostStatus status, Visibility visibility, boolean hidden)` — `PUBLISHED`·`PUBLIC`·숨김 아님일 때만 `private, no-cache`, 그 밖(작성자가 보는 비공개·임시·숨김 글 포함)은 `private, no-store`; 편의 오버로드 `forPost(PostView)`; `notFound()`)와, 001의 `backend/src/main/java/com/team/blog/shared/error/GlobalExceptionHandler.java`의 `NotFoundException` 처리에 `Cache-Control: private, no-store` 헤더를 붙이는 연결(본문 `{code: NOT_FOUND, message: "볼 수 없는 페이지예요", errors: [], details: null}`은 그대로, 모든 `NotFoundException` 하위 타입이 같은 본문·헤더) (research R-12·R-26·R-30, FR-015) (구현 메모: `forPost`·`notFound`는 static. `GlobalExceptionHandler`는 `NotFoundException` 계열뿐 아니라 없는 경로·로그인 회원의 `AccessDeniedException`·`ResponseStatusException(404)` 등 모든 404에 같은 `Cache-Control: private, no-store`를 붙임)
- [X] T020 [P] 공통 404 화면 렌더러 작성 `backend/src/main/java/com/team/blog/shared/web/NotFoundPageRenderer.java`: 고정 `<head>` 메타(T007의 og:title·og:description·robots noindex)를 React 빌드 `classpath:static/index.html`의 `<!--app-head-->` 자리(005 research의 셸 규약)에 넣어 404 + `Cache-Control: private, no-store` + `text/html; charset=UTF-8` 응답을 만든다. 셸 파일이 없으면(테스트·개발) 고정 최소 HTML을 쓴다. 사용자 값은 넣지 않는다. 005 `PageShellController`가 볼 수 없는 글·없는 글·없는 블로그에 이 렌더러를 쓴다 (FR-013, research R-26) (구현 메모: 셸은 `@Value("classpath:static/index.html")`로 받아 기동 때 한 번 읽고 바이트를 고정. `<!--app-head-->`가 없으면 `</head>` 앞에 넣음. `render()` → `ResponseEntity<byte[]>`)
- [X] T021 [P] 글 테스트 픽스처 작성 `backend/src/test/java/com/team/blog/support/fixture/PostFixtures.java`(JdbcTemplate 직접 INSERT, 002 엔드포인트 없이 동작; 회원은 001 T009 `MemberFixtures`로 만든다 — `ACTIVE`/`ADMIN`/`SUSPENDED`/`WITHDRAWN`(`withdrawn_at` 채움)/인증 전(`email_verified_at NULL`, provider LOCAL)): 글 상태 `PUBLISHED_PUBLIC`(published_at·first_public_at 채움), `PUBLISHED_PRIVATE`(first_public_at NULL 또는 과거값 선택), `EDITING`(발행 글 + `post_draft` 행), `DRAFT`, `TRASHED`(deleted_at), `HIDDEN`(hidden_at·hidden_by), `AUTHOR_WITHDRAWN`(작성자를 WITHDRAWN으로). CHECK `ck_post_public_at`·`ck_post_published`(`status='DRAFT' OR (published_at IS NOT NULL AND length(btrim(title))>0)`)·`ck_post_edited_at`·`ck_member_withdrawn`을 만족하게 만든다(data-model §1) (구현 메모: 회원은 001 `support/MemberFixtures`(위치 그대로)를 쓴다. 발행 글은 `published_at`=하루 전·`edit_version`=1, `EDITING`의 작업본 버전은 글 버전+1, `HIDDEN`은 `hidden_by`에 관리자(없으면 만듦)·`hidden_reason`=SPAM. 빌더 `post(authorId)`와 `nonexistentId()`·`withdraw(memberId)` 제공)
- [X] T022 권한 매트릭스 CSV 하네스 작성 `backend/src/test/java/com/team/blog/support/permission/`: `Actor`(ANONYMOUS, UNVERIFIED, MEMBER, AUTHOR, ADMIN, SUSPENDED, WITHDRAWN — 42 §2) 로그인 세션은 001 T009 `TestLogin`(`POST /test/login-as/{memberId}`)으로 만들고 CSRF 헤더 준비, `TargetState`(T021의 7개 상태 + `NONEXISTENT`), `PermissionAction` 인터페이스(`name()`, `owner()`, `perform(MockMvc, session, postId)`), `PermissionActionRegistry`(각 기능이 테스트 Bean으로 실행기 등록, 실행기가 없는 행은 JUnit `Assumptions.abort("pending: <owner spec>")`로 건너뜀), `PostSnapshot`(`title, content_md, status, visibility, edit_version, updated_at, edited_at, first_public_at` 비교, 42 §12 #1), 기대값 비교(상태 코드 + 오류 `code`; 목록 행동은 `INCLUDED`/`EXCLUDED`로 포함 여부 비교 — 005 `list-home` 행 등), 하위 클래스가 `permission/*.csv` 파일을 지정(006 `post-trashed.csv` 등 기능별 CSV 추가 가능), `AbstractPermissionMatrixIT`(`@ParameterizedTest @CsvFileSource`, IntegrationTestBase 상속) (research R-28, FR-047) (depends on T021) (구현 메모: 42 §5-2의 "인증 전" 칸은 자기 글 삭제·복구가 허용되는 칸이라 `Actor.UNVERIFIED_AUTHOR`(인증 전 작성자)를 더하고, `UNVERIFIED`·`MEMBER`·`ADMIN`은 작성자가 아닌 회원, `SUSPENDED`·`WITHDRAWN`은 그 상태인 작성자 본인의 남은 세션으로 정함. 대상 없는 행동(새 글)용 `TargetState.NONE` 추가. 실행기는 테스트 소스에 `@Component`로 두면 통합 테스트 컨텍스트의 컴포넌트 스캔이 찾는다. 쓰기 거부 행의 `PostSnapshot`은 `deleted_at`·`hidden_at`·작업본도 비교. 004 `post.read` 실행기 `support/permission/ReadPostAction.java`와 테스트 컨트롤러 `support/ReadProbeController.java`를 미리 둠 — 경로는 T041의 `/test/posts/{postId}` 대신 탈퇴 유예 필터가 걸리도록 `GET /api/__test/posts/{postId}`. 하네스 자체 확인 `support/permission/PermissionHarnessIT.java`)
- [X] T023 [P] 매트릭스 CSV 작성 `backend/src/test/resources/permission/post-read.csv`(42 §5-1 / FR-033 표: 7개 글 상태 × 비회원·인증 전·회원·작성자·관리자, 열 `actor,targetState,action,expectedStatus,expectedCode,owner`)와 `backend/src/test/resources/permission/post-write.csv`(42 §5-2 / FR-034 표: 새 글 만들기·자동/수동 저장·발행/다시 발행·변경 취소·공개 범위 변경·삭제·복구/영구 삭제·숨김/숨김 해제 × 5 행위자 × 대상 상태, 휴지통 글의 저장·발행·공개 범위 변경은 작성자도 404, 정지 세션 쓰기 403 `ACCOUNT_SUSPENDED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`; owner 열에 002/004/006/014 표기) (구현 메모: action 이름 — `post.read`(004), `post.create`·`post.autosave`·`post.save`·`post.publish`·`post.discard`(002), `post.visibility`(004), `post.trash`·`post.restore`·`post.purge`(006), `post.hide`·`post.unhide`(014). 005의 `read-detail-api`·`read-detail-page` 행은 005가 더한다. post-read 42행, post-write 156행)
- [X] T024 [P] 공통 API 클라이언트에 404 분기 추가 `frontend/src/api/client.ts`(001 T042가 만든 파일: CSRF 헤더, `ApiError{status,code,message,errors,details,retryAfter}`, `onUnauthorized` 콜백): 404 `NOT_FOUND`를 받으면 `onNotFound` 콜백(공통 404 화면 전환)을 부른다. 001의 기존 동작은 바꾸지 않는다 (구현 메모: `onNotFound(cb)`는 해제 함수를 돌려줌. `frontend/src/App.tsx`에서 등록해 404 `NOT_FOUND`를 받으면 지금 주소에서 공통 404 화면을 보이고, 주소가 바뀌면 풀림. 없는 화면 경로 `*`도 같은 화면)
- [X] T025 공통 404 화면 작성 `frontend/src/pages/NotFoundPage.tsx`: "볼 수 없는 페이지예요" 고정 문구 + 홈 링크, 375px에서 가로 스크롤 없음 (depends on T024)

**Checkpoint**: 판정 장치 준비 완료 — 004의 스토리와 002·005·006이 병렬로 시작할 수 있다

---

## Phase 3: User Story 1 - 글마다 공개 범위를 고르고 바로 바꾸기 (Priority: P1) 🎯 MVP

**Goal**: 작성자가 `PUT /api/posts/{postId}/visibility`로 다시 발행하지 않고 즉시 공개 범위를 바꾼다. `edited_at`·`edit_version`·작업본·댓글·좋아요는 그대로, `first_public_at`은 처음 공개될 때만 기록된다.

**Independent Test**: 공개 글을 비공개 → 공개로 바꾸며 다른 회원의 상세 판정(`PostReadService`), 홈 대표 쿼리 위치, `edited_at`·`edit_version`, 댓글·좋아요 행 수를 확인한다(quickstart 시나리오 1·3·4·5).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T026 [P] [US1] `Post.changeVisibility` 단위 테스트 작성 `backend/src/test/java/com/team/blog/post/domain/PostChangeVisibilityTest.java`: data-model §4-2 표 전 행 — 같은 값이면 `changed=false`·`updatedAt` 그대로; DRAFT는 값만 바뀌고 `wentPublic=false`; PUBLISHED + `first_public_at NULL` → PUBLIC이면 `first_public_at=now`·`wentPublic=true`; 이미 있으면 유지; PRIVATE로 바꿔도 `first_public_at` 유지; `edited_at`·`edit_version`·`published_at`·`hidden_at` 불변
- [X] T027 [P] [US1] 계약 테스트 작성 `backend/src/test/java/com/team/blog/post/web/PostVisibilityControllerContractTest.java`(MockMvc, contracts/openapi.yaml `setPostVisibility`): 200 본문 `{visibility, firstPublicAt}`(마이크로초 ISO-8601 UTC 또는 null) + `Cache-Control: private, no-store`; 400 본문 `{code: INVALID_VISIBILITY, errors: [{field: visibility, ...}], details: null}`; 본문 없음/JSON 아님 → 400 `INVALID_REQUEST`; CSRF 헤더 없으면 거부; 401·403·404 응답이 `LoginRequired`/`AccountStateDenied`/`NotFound` 컴포넌트 형식과 같음 (구현 메모: 파일 이름을 `PostVisibilityControllerContractIT.java`로 했다 — 공용 Testcontainers 컨텍스트(IntegrationTestBase)를 쓰므로 Failsafe(`*IT`)로 돈다. 본문 없음·JSON 아님은 001 코드 `MALFORMED_REQUEST`로 확인한다(T036 메모). 403 본문은 `EMAIL_NOT_VERIFIED`+`details.action=RESEND_VERIFICATION`, `ACCOUNT_WITHDRAWN`+`RESTORE`, `ACCOUNT_SUSPENDED`+`details:null`을 바이트 단위로 비교한다. 숫자가 아닌·0·음수 postId도 401→403→404 순서를 지키도록 404로 본다)
- [X] T028 [P] [US1] 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/VisibilityChangeIT.java`(Testcontainers): US1 인수 1~6 — ① PUBLIC→PRIVATE 즉시 다른 회원 `requireReadable` 404, `edited_at` 불변 ② 공개→비공개→공개 후 `first_public_at` 원래 값, 홈 대표 쿼리 순서 유지 ③ 댓글·좋아요 행·`like_count`·`comment_count` 불변 ④ 작업본(`post_draft`)·`edit_version` 불변 ⑤ 남의 글(본문에 `authorId` 끼워 넣기 포함)·없는 글·휴지통 글 404 + `PostSnapshot` 동일 ⑥ 비공개 발행 글을 처음 PUBLIC으로 → `firstPublicAt`=지금, 홈 대표 쿼리 첫 행; Edge — 같은 값 200 + `updated_at` 그대로, 임시글은 값만 저장, `FRIENDS`·모르는 값 400(남의 글이면 404가 먼저, research R-21), 숨김 글은 바꿔도 `hidden_at` 유지, 동시 요청 10건(PUBLIC/PRIVATE 교대)이 직렬화되어 최종값 일관·`first_public_at` 한 번만 기록 (FR-016~FR-020, quickstart 시나리오 1·3·4·5) (구현 메모: ③ 댓글(007)·좋아요(010) 표는 아직 없어 `like_count`·`comment_count` 열 불변만 확인한다. 홈 대표 쿼리는 `support/ReferenceListQueries`(T040 도우미를 먼저 만들었다))
- [X] T029 [P] [US1] 이벤트 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/VisibilityChangeEventIT.java`: 테스트용 `@TransactionalEventListener(AFTER_COMMIT)` 수집기로 — 발행 글 값이 실제로 바뀌면 `PostVisibilityChanged(postId, authorId, from, to, changedAt)` 정확히 1번(`changedAt` = `updated_at`); 처음 공개면 `PostWentPublic(postId, authorId, firstPublicAt)`도 1번; 다시 공개(공개→비공개→공개)·같은 값·임시글 변경은 이벤트 없음; 롤백되면 없음; 리스너가 예외를 던져도 200 (contracts/events.md, research R-11·R-25, FR-021) (구현 메모: 수집기는 테스트 소스 `post/support/VisibilityEventProbe`(`@Profile("test") @Component`, `arm()` 전에는 기록하지 않음) — 새 테스트 컨텍스트를 만들지 않는다)

### Implementation for User Story 1

- [X] T030 [P] [US1] 도메인 이벤트 record 작성 `backend/src/main/java/com/team/blog/shared/event/PostVisibilityChanged.java`(`long postId, long authorId, Visibility from, Visibility to, Instant changedAt`)와 `backend/src/main/java/com/team/blog/shared/event/PostWentPublic.java`(`long postId, long authorId, Instant firstPublicAt`). `PostWentPublic`을 002가 이미 만들었으면 재사용하고 필드만 확인한다(events.md 공통 규칙 EV-3·EV-7: id·enum·Instant만) (구현 메모: `PostWentPublic`은 002가 이미 만들어 그대로 쓴다(필드 `postId, authorId, firstPublicAt` 확인). `PostVisibilityChanged`만 새로 만들었다)
- [X] T031 [US1] 002 소유 `backend/src/main/java/com/team/blog/post/domain/Post.java`에 `changeVisibility(Visibility to, Instant now)` → `VisibilityChange(boolean changed, boolean wentPublic, Visibility from)` 메서드를 **추가**한다: 같은 값이면 아무것도 바꾸지 않음; `first_public_at`은 `firstPublicAt == null && status == PUBLISHED && to == PUBLIC`일 때만 `now`(05 P-3); 값이 바뀌면 `updatedAt = now`; `editedAt`·`editVersion`·`publishedAt`·`hiddenAt`은 건드리지 않음(06 V-6, `ck_post_public_at` 통과) (depends on specs/002 Post, T009) (구현 메모: 반환 record는 `post/domain/VisibilityChange.java`. 숨긴 글(`hidden_at`)도 값만 바뀐다)
- [X] T032 [US1] 002 소유 `backend/src/main/java/com/team/blog/post/infra/PostRepository.java`에 `findForUpdateByIdAndAuthorId(long postId, long authorId)`를 추가한다(`@Lock(PESSIMISTIC_WRITE)` = `SELECT … FOR UPDATE`, `@SQLRestriction`으로 휴지통 제외, 결과 없으면 Service가 404 — ③·④를 조회 한 번으로, research R-07·R-10). 002가 같은 의미의 잠금 조회를 이미 두었으면 그것을 쓴다 (구현 메모: 002가 같은 이름·의미(`@Lock(PESSIMISTIC_WRITE)`, `@SQLRestriction`)로 이미 두어 그대로 쓴다 — 새로 추가하지 않았다)
- [X] T033 [P] [US1] 요청·응답 DTO 작성 `backend/src/main/java/com/team/blog/post/web/dto/VisibilityChangeRequest.java`(필드 `String visibility` 하나뿐 — `authorId` 등 다른 필드는 선언하지 않아 무시, 42 §12 #2)와 `backend/src/main/java/com/team/blog/post/web/dto/VisibilityChangeResponse.java`(`Visibility visibility`, `Instant firstPublicAt` nullable, 마이크로초 직렬화)
- [X] T034 [US1] `PostVisibilityService` 작성 `backend/src/main/java/com/team/blog/post/application/PostVisibilityService.java`: 순서 ① 로그인(Viewer 비회원이면 401) ② 001 `AccountStatusGuard.requireActive(viewer.id(), CONTENT_WRITE)`(인증 전 403 `EMAIL_NOT_VERIFIED`, 정지 403 `ACCOUNT_SUSPENDED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`) ③④ `findForUpdateByIdAndAuthorId` 없으면 `PostNotFoundException` ⑤ `VisibilityRegistry.require(raw, "visibility")` → `post.changeVisibility(to, now)` → 발행 글이고 `changed`면 같은 트랜잭션에서 `ApplicationEventPublisher`로 `PostVisibilityChanged`, `wentPublic`이면 `PostWentPublic`도 발행. 관리자 예외 없음(남의 글 404, FR-035). 반환 `{visibility, firstPublicAt}` (FR-016~FR-021, FR-028, research R-05·R-10·R-11·R-21·R-25) (depends on T013, T016, T030, T031, T032) (구현 메모: 응답 값은 내부 record `PostVisibilityService.VisibilityChangeResult`. 이벤트는 발행 글에서 값이 실제로 바뀔 때만 낸다(임시글·같은 값은 없음, events.md))
- [X] T035 [US1] 컨트롤러 작성 `backend/src/main/java/com/team/blog/post/web/PostVisibilityController.java`: `PUT /api/posts/{postId}/visibility`(postId `int64 ≥ 1`), `Viewer`는 `CurrentViewerResolver`로만 받음, 응답 헤더 `Cache-Control: private, no-store`, springdoc 어노테이션을 contracts/openapi.yaml과 맞춤(PATCH 아님, research R-20) (depends on T011, T033, T034) (구현 메모: springdoc이 pom에 없어 어노테이션은 달지 않았다(계약 일치는 T077이 contracts/openapi.yaml을 직접 읽어 확인). postId는 문자열로 받아 숫자·1 이상이 아니면 404로 본다 — 형 변환 400이 401·403보다 먼저 나오지 않게(42 §3 순서))
- [X] T036 [US1] 본문 형식 오류 매핑 확인: 001 소유 `backend/src/main/java/com/team/blog/shared/error/GlobalExceptionHandler.java`가 `HttpMessageNotReadableException`(본문 없음·JSON 아님)을 400 `INVALID_REQUEST`(research R-21 제안 코드)로 바꾸는지 확인하고, 없으면 그 매핑 하나만 추가한다(T027이 통과해야 함) (구현 메모: 001 `GlobalExceptionHandler`가 이미 `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST`(001 코드)로 바꾼다. research R-21 제안 `INVALID_REQUEST`는 팀 결정(R3) 전이라 001 코드를 그대로 쓰고 고치지 않았다. 대신 001 `AccountStatusGuardService`의 `EMAIL_NOT_VERIFIED`에 `details.action = RESEND_VERIFICATION`을 붙였다(공용 파일 최소 변경, 계약 403 예시와 맞춤))
- [X] T037 [P] [US1] 화면 테스트 작성 `frontend/src/features/visibility/VisibilitySelect.test.tsx`, `frontend/src/features/visibility/VisibilityBadge.test.tsx`(React Testing Library): 선택지는 "🌐 전체 공개"·"🔒 나만 보기" 두 개(FRIENDS 비활성 기본); 바꾸면 `setVisibility` 호출 후 응답 값으로 표시 갱신; 400이면 "공개 범위를 다시 선택해 주세요" 표시하고 이전 값 복원; 404면 공통 404 화면으로 이동; 배지는 값별 아이콘·글자(🌐 전체 공개 / 👥 친구 공개 / 🔒 나만 보기, FR-046)
- [X] T038 [P] [US1] API 함수 작성 `frontend/src/api/posts.ts`에 `setVisibility(postId: number, visibility: Visibility): Promise<{visibility; firstPublicAt: string | null}>` 추가(`PUT /api/posts/{postId}/visibility`, 공통 API 클라이언트로 CSRF 헤더 포함)와 `frontend/src/features/visibility/visibilityOptions.ts`(값·라벨·아이콘 목록, 공통은 PUBLIC·PRIVATE) (구현 메모: `Visibility` 타입은 `'PUBLIC' | 'PRIVATE' | 'FRIENDS'`로 넓혔다(FRIENDS는 적용 환경 응답에서만). 선택지는 `FRIENDS_VISIBILITY_ENABLED`(`VITE_FRIENDS_VISIBILITY=true`)일 때만 친구 공개를 넣는다)
- [X] T039 [US1] 컴포넌트 작성 `frontend/src/features/visibility/VisibilitySelect.tsx`(발행 설정 002·상세 005·내 글 관리 006에서 공용, `value`·`onChange` 또는 `postId` 지정 시 즉시 저장 모드)와 `frontend/src/features/visibility/VisibilityBadge.tsx` (depends on T038) (구현 메모: 002 `PublishDialog`의 임시 선택 상자를 값만 고르는 모드로 바꿨고, 005 상세의 `visibilityControl` 자리를 `App.tsx`에서 즉시 저장 모드로 채웠다. 006 내 글 관리는 006 T068에서 연결)

**Checkpoint**: US1 단독으로 동작·검증 가능 (MVP)

---

## Phase 4: User Story 2 - 볼 수 없는 글은 어디로도 새지 않는다 (Priority: P1)

**Goal**: 비공개·임시·휴지통·숨김·탈퇴 유예 작성자의 글이 상세·목록·글 수·미리보기 어디에도 나오지 않고, 없는 글과 똑같은 404를 받는다.

**Independent Test**: 볼 수 없는 글과 없는 글의 404 응답(상태·본문 바이트·헤더)이 같고, 공용 조건을 쓴 홈·블로그 대표 쿼리와 블로그 글 수에서 빠지는지 확인한다(quickstart 시나리오 2, VisibilityMatrixIT·NotFoundIndistinguishableIT·ListIndexUsageIT).

### Tests for User Story 2 ⚠️

- [X] T040 [P] [US2] 공개 범위 매트릭스 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/VisibilityMatrixIT.java`(+ 테스트 도우미 `backend/src/test/java/com/team/blog/support/ReferenceListQueries.java`: `VisibilityFilter`로 만든 홈 대표 쿼리 `ORDER BY p.first_public_at DESC, p.id DESC LIMIT 10`, 블로그 대표 쿼리 `author_id` 조건 추가, 블로그 글 수 `COUNT(*)`, 태그·검색·sitemap이 공유할 공용 조건): 공개 범위(PUBLIC/PRIVATE) × 보는 사람(비회원/다른 회원/작성자/관리자) × 노출되는 곳(상세 `requireReadable`/홈/블로그 목록/블로그 글 수/공용 조건/댓글 보기 = `requireReadable`) 모든 칸. US2 인수 1(관리자도 PRIVATE 404), 3(어디에도 없고 세지 않음), 4(숨긴 글은 작성자 본인 블로그 목록에도 없음, 상세는 작성자만), 5(작성자 탈퇴 유예 → 상세 404·목록 제외); 임시·휴지통 행 포함 (FR-003~FR-011, FR-047, SC-001·SC-004, 06 §8) (구현 메모: 도우미 `support/ReferenceListQueries`는 US1에서 먼저 만들었다. 상태 7가지를 `@ParameterizedTest`로 돌리고 보는 사람 4명 × 노출되는 곳(상세·홈·블로그 목록·블로그 글 수·`countListedByAuthor`·공용 조건)을 한 칸씩 본다. 댓글 보기는 같은 `requireReadable`이라 상세 칸과 같다. 작성자 탈퇴 유예 글의 작성자 칸은 판정 장치 기준(true)이고 HTTP는 001 탈퇴 게이트 403(`post-read.csv`))
- [X] T041 [P] [US2] 404 동일성 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/NotFoundIndistinguishableIT.java`(+ 테스트 전용 컨트롤러 `backend/src/test/java/com/team/blog/support/ReadProbeController.java`: `GET /test/posts/{postId}` → `PostReadService.requireReadable`): 남의 PRIVATE·DRAFT·휴지통·숨김·탈퇴 유예 작성자 글과 없는 번호의 API 404 상태·본문 바이트·`Content-Type`·`Cache-Control`이 모두 같음; `PUT /api/posts/{id}/visibility`로 남의 글과 없는 글을 보낸 응답도 같음; `NotFoundPageRenderer` 결과가 요청과 무관하게 같음 (FR-013·FR-014, SC-002, research R-26·R-30) (구현 메모: `ReadProbeController`는 이미 있던 테스트 컨트롤러를 그대로 쓴다 — 경로는 `/test/posts/{id}`가 아니라 `/api/__test/posts/{postId}`(`/api/**` 아래라 001 탈퇴 게이트가 실제 API처럼 적용됨). 비회원·회원·관리자 세션 각각에서 비교하고, 공개 범위 변경은 남의 글(공개 포함)·내 휴지통 글·없는 번호가 같음을 본다)
- [X] T042 [P] [US2] 부분 인덱스 사용 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/ListIndexUsageIT.java`: 같은 트랜잭션에서 `SET LOCAL enable_seqscan = off` 후 `ReferenceListQueries`의 홈·블로그 대표 쿼리를 `EXPLAIN (FORMAT JSON)`으로 실행해 계획에 `ix_post_feed`·`ix_post_blog`가 나오는지 확인 (06 R-2b, research R-27) (구현 메모: 작은 데이터 + `enable_seqscan = off`라 조건 문구가 인덱스 술어와 어긋나면 실패한다. 1만 건 실제 계획은 005 `ListIndexExplainIntegrationTest`)
- [X] T043 [P] [US2] 공용 조건 우회 방지 테스트 작성 `backend/src/test/java/com/team/blog/post/VisibilityFilterUsageGuardTest.java`: `backend/src/main/java` 아래 소스에서 `visibility = 'PUBLIC'`·`visibility='PUBLIC'`·`Visibility.PUBLIC`을 조건으로 쓰는 SQL/JPQL 문자열이 `VisibilityFilter.java`와 `PublicVisibilityRule.java` 밖에 있으면 실패(목록마다 조건을 따로 만들지 않음, FR-009, 06 R-2) (구현 메모: 주석을 건너뛰는 간단한 Java 글자 분석기로 문자열(텍스트 블록 포함)과 `+`로 바로 이어 붙인 두 문자열만 본다. Java 코드의 `== Visibility.PUBLIC` 비교(캐시 정책 등)는 SQL 조건이 아니라 대상이 아니다. 검사기 자체 시험도 같은 파일에 둔다)

### Implementation for User Story 2

- [X] T044 [US2] 블로그 글 수용 공용 메서드 추가 `backend/src/main/java/com/team/blog/post/infra/PostQueryRepository.java`: `countListedByAuthor(Viewer viewer, long authorId)` — `post p JOIN member m` + `VisibilityFilter.forViewer(viewer, authorId)`만 사용(작성자 본인도 같은 수, 06 V-8). 005 블로그 머리말·008 태그 글 수가 같은 조건을 쓰도록 Javadoc에 적는다 (FR-006·FR-009) (depends on T018) (구현 메모: 005 `PostCardQueryRepository.countListed`(004가 없어 임시로 둔 같은 조건)를 지우고 005 `BlogQueryService` 머리말 글 수가 이 메서드를 쓰도록 바꿨다(005 테스트 호출도 함께))

**Checkpoint**: US1·US2가 각각 동작 — 공개 범위를 바꿔도 새지 않음이 매트릭스로 보장됨

---

## Phase 5: User Story 3 - 남의 글은 바꿀 수 없다 (Priority: P1)

**Goal**: 남의 글에 대한 쓰기 요청은 화면과 무관하게 서버에서 404이고 데이터가 전혀 바뀌지 않는다. 요청에 작성자 번호를 넣어도 무시되고, 관리자도 남의 글 내용은 못 바꾼다.

**Independent Test**: 회원 B 세션으로 A의 글에 쓰기 행동을 보내 모두 404이고 `PostSnapshot`이 같은지 확인한다(PermissionMatrixIT, quickstart 시나리오 4).

### Tests for User Story 3 ⚠️

- [X] T045 [P] [US3] 권한 매트릭스 통합 테스트 작성 `backend/src/test/java/com/team/blog/shared/security/integration/PermissionMatrixIT.java`(`AbstractPermissionMatrixIT` 상속): `post-read.csv`·`post-write.csv` 모든 행 실행 — 쓰기 행은 요청 전후 `PostSnapshot` 동일(SC-003), 본문에 다른 회원의 `authorId`를 넣은 변형도 실행(42 §12 #2), 관리자 남의 글 수정·삭제·공개 범위 변경 404(42 §12 #5). 실행기가 아직 없는 행동(002·006·014 소유)은 "pending: owner"로 건너뛴다. US3 인수 1·2·3 (FR-025·FR-026·FR-033·FR-034·FR-035, SC-001·SC-003·SC-008) (구현 메모: 한 러너가 두 CSV 모든 행을 돌리므로 기능별 행 러너를 지웠다 — 006 `TrashPermissionMatrixIT`(합침), 002 `PostAuthoringPermissionMatrixIT`, 005 `PostDetailPermissionMatrixIT`의 행 메서드, `PermissionHarnessIT`의 post-read 행 메서드(중복 실행 방지, 005의 404 동일성 보충 테스트는 남김). 작성자 번호 끼워 넣기는 테스트 소스 필터 `support/permission/OwnerFieldInjector`(`@Profile("test") @Component`, 켤 때만 동작)가 모든 쓰기 행의 JSON 본문·쿼리 매개변수에 `authorId`·`memberId`·`ownerId`·`userId`를 더해 같은 기대값 + 대상 글 작성자 불변 + 끼워 넣은 회원 글 0건을 본다(실행기를 고치지 않음). 현재 472행 중 건너뜀 44 = 014 숨김 28 + 읽기 행동(`post.editor`)의 끼워 넣기 변형 16)
- [X] T046 [P] [US3] 요청 DTO 작성자 필드 금지 테스트 작성 `backend/src/test/java/com/team/blog/shared/security/RequestDtoNoOwnerFieldTest.java`: 클래스패스의 `com.team.blog..web.dto` 패키지 요청 DTO에 `authorId`·`memberId`·`ownerId`·`userId` 필드나 생성자 인자가 없음을 리플렉션으로 확인(FR-026, Constitution III) (구현 메모: 대상은 `..web.dto.*Request` 타입과 모든 컨트롤러의 `@RequestBody` 타입(안의 `com.team.blog` 타입까지), 필드·record 구성 요소·생성자 인자 이름)

### Implementation for User Story 3

- [X] T047 [US3] 이 기능 소유 행동의 매트릭스 실행기 등록 `backend/src/test/java/com/team/blog/support/permission/actions/SetVisibilityAction.java`(`PUT /api/posts/{postId}/visibility {"visibility":"PRIVATE"}` / 대상이 PRIVATE면 PUBLIC, owner 004)와 `backend/src/test/java/com/team/blog/support/permission/actions/ReadDetailAction.java`(`ReadProbeController` 경유 상세 판정, owner 004) (depends on T022, T035, T041) (구현 메모: `ReadDetailAction`은 만들지 않았다 — 같은 행동 이름 `post.read`(owner 004)의 실행기 `support/permission/ReadPostAction`이 기반 단계(T022)에서 이미 `ReadProbeController`로 상세 판정을 부르고, 같은 이름의 다른 클래스는 레지스트리가 거부한다)
- [X] T048 [US3] 대기 행 보고 작성 `backend/src/test/java/com/team/blog/support/permission/PendingRowReport.java`: 매트릭스 실행 후 건너뛴 행을 owner별로 집계해 테스트 로그에 출력(002·006·014가 실행기를 등록할 때 남은 칸을 추적, Polish T075에서 0건 확인) (depends on T022) (구현 메모: JUnit 확장(`AfterAllCallback`)으로 `AbstractPermissionMatrixIT`에 붙였다. 러너가 끝나면 `pending rows: 014=28`처럼 로그에 남기고 `pendingOwners(러너)`로 T075가 확인한다)

**Checkpoint**: US1~US3 (P1 전부) 완료 — Tier A 004 완료 기준(C-POST-4·C-OWN-1·C-READ-2) 충족

---

## Phase 6: User Story 4 - 계정 상태에 맞는 일관된 거부와 안내 (Priority: P2)

**Goal**: 비회원·인증 전·탈퇴 유예·정지 회원이 어느 기능에서든 같은 순서(① 로그인 → ② 계정 상태 → ③ 대상 → ④ 권한 → ⑤ 규칙)로 판정되어 같은 응답과 안내를 받는다.

**Independent Test**: 같은 비공개 글과 없는 글에 대해 비회원·인증 전·탈퇴 유예 회원으로 공개 범위 변경을 보내 각각 401/403 응답이 나오고 존재 여부와 무관하게 같은지 확인한다(quickstart 시나리오 6).

### Tests for User Story 4 ⚠️

- [X] T049 [P] [US4] 계정 상태 통합 테스트 작성 `backend/src/test/java/com/team/blog/shared/security/integration/AccountStateIT.java`: 비회원 → 401 `LOGIN_REQUIRED`(US4-1); 인증 전 회원의 공개 범위 변경 → 403 `EMAIL_NOT_VERIFIED` + `details.action = RESEND_VERIFICATION`, 있는 글·없는 글 응답 바이트 동일(US4-2·US4-6); 인증 전 회원 `PATCH /api/me/settings {"defaultVisibility":"PRIVATE"}` → 200(FR-032); 탈퇴 유예 → 403 `ACCOUNT_WITHDRAWN` + `details.action = RESTORE`(US4-4); 정지 직후 남은 세션의 쓰기 → 403 `ACCOUNT_SUSPENDED` 또는 세션 삭제로 401(H7); `SessionTerminator.terminateAll` 후 그 회원의 두 기기 세션이 모두 401(US4-5, SC-006); 인증 전 작성자가 자기 글 휴지통 이동·복구는 허용(US4-3, 006 실행기 등록 전에는 pending) (FR-028~FR-032, SC-008) (구현 메모: `PATCH /api/me/settings`(001 T118)는 이 브랜치에 아직 없어 그 확인은 핸들러가 있을 때만 돌고 없으면 `pending: 001`로 건너뛴다(001 브랜치와 합치면 자동으로 실행). 정지 직후 남은 세션은 지금 403 `ACCOUNT_SUSPENDED`(001이 정지 때 세션을 지우면 401도 허용). 인증 전 작성자의 휴지통 이동·복구는 006 API가 있어 바로 확인한다. 모든 거부 응답이 자기 글·남의 비공개 글·없는 글·허용되지 않는 값에서 바이트까지 같은지 본다)
- [X] T050 [P] [US4] 세션 저장소 장애 통합 테스트 작성 `backend/src/test/java/com/team/blog/shared/security/integration/SessionResilienceIT.java`: 001 T009 `RedisOutage`로 Redis를 멈춘 동안 세션 쿠키를 가진 요청이 비회원으로 처리됨 — 공개 글 `GET /test/posts/{id}`(ReadProbe) 200, `PUT /api/posts/{id}/visibility` 401 `LOGIN_REQUIRED`; Redis 재개 후 정상 (02 §2-1, research R-09, Edge Case) (구현 메모: 읽기 판정은 `ReadProbeController`(`/api/__test/posts/{id}`)로 부른다. 장애 동안 자기 비공개 글은 비회원 판정이라 404)
- [X] T051 [P] [US4] 화면 테스트 작성 `frontend/src/features/auth-gate/useAuthGate.test.ts`, `frontend/src/features/auth-gate/AuthPrompt.test.tsx`: 401 → `/login?returnTo=<현재 경로>` 안내, 로그인 후 원래 페이지로 돌아오되 원래 행동은 자동 실행되지 않음(H6); `EMAIL_NOT_VERIFIED` → "인증 메일 다시 보내기" 안내; `ACCOUNT_WITHDRAWN` → `/restore` 안내(015 화면 경로 `/account/restore`로 015 구현 때 맞춤, ANALYSIS-tier-bc); `ACCOUNT_SUSPENDED` → "정지된 계정이에요" 안내 (FR-029, research R-29) (구현 메모: 훅 테스트는 `useAuthGate.test.ts`(JSX 없이 `createElement`). 로그인 뒤 `returnTo`로 돌아가는 처리는 001 `LoginPage` 몫이라 여기서는 로그인 주소에 돌아올 경로만 있고 행동 정보가 없음을 확인한다)

### Implementation for User Story 4

- [X] T052 [P] [US4] 공통 거부 처리 훅 작성 `frontend/src/features/auth-gate/useAuthGate.ts`: `ApiError`를 받아 code별로 안내(401 로그인 이동 + `returnTo`, 403 code별 `AuthPrompt`), 자동 재시도·자동 실행 없음 (depends on T024) (구현 메모: code → 안내 종류 변환은 순수 함수 `authGate.ts`(`authPromptFor`, `loginPathFor`, `RESTORE_PATH`)에 두고 훅은 `handle(error) → boolean`·`prompt`·`dismiss`·`loginPath`를 준다. 401은 기본 로그인 화면 이동, `unauthorized: 'prompt'`면 그 자리 안내(US6 버튼용). 006 `useManagePosts`의 자체 401 이동을 이 훅으로 바꿨다)
- [X] T053 [US4] 안내 컴포넌트 작성 `frontend/src/features/auth-gate/AuthPrompt.tsx`: 로그인 안내 / 이메일 인증 안내(001 재발송 API 호출 버튼) / 탈퇴 유예 복구 화면 안내 / 정지 안내 문구 (depends on T052) (구현 메모: 탈퇴 유예 안내의 [계정 복구하기]는 001 복구 화면 `/restore`로 연결한다(라우트는 001 몫))
- [X] T054 [US4] 판정 순서 정리 확인 `backend/src/main/java/com/team/blog/post/application/PostVisibilityService.java`: 계정 상태 검사가 대상 조회(잠금)보다 먼저이고, 비회원 401이 계정 상태보다 먼저인지 T049로 확인한다. 탈퇴 유예 회원의 허용 목록 외 요청 403은 001 T042a `WithdrawnAccountGateFilter`가 맡는다. AccountStateIT가 001 가드의 다른 공백을 드러내면 001 소유 파일은 고치지 말고 001에 되돌린다 (FR-028·FR-031, 42 §3, research R-23) (구현 메모: 코드 변경 없음 — ① 401 → ② 계정 상태 → ③④ 잠금 조회 → ⑤ 값 검사 순서를 AccountStateIT(없는 글 + 허용되지 않는 값에도 계정 상태 응답)로 확인했다. 001 가드의 공백은 발견되지 않았다(EMAIL_NOT_VERIFIED details는 US1에서 보탬))

**Checkpoint**: 계정 상태 응답이 기능과 무관하게 같음

---

## Phase 7: User Story 5 - 새 글의 기본 공개 범위 (Priority: P2)

**Goal**: 회원 설정의 기본 공개 범위(처음 `PUBLIC`)로 [새 글]이 시작하고, 값 규칙은 공개 범위 변경과 같다.

**Independent Test**: 기본 공개 범위를 PRIVATE로 바꾼 뒤 `POST /api/posts`로 만든 임시글의 `visibility`가 PRIVATE인지 확인한다(quickstart 시나리오 8).

### Tests for User Story 5 ⚠️

- [X] T055 [P] [US5] 기본 공개 범위 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/DefaultVisibilityIT.java`: 새 회원 `member.default_visibility = 'PUBLIC'`(US5-1); `PATCH /api/me/settings {"defaultVisibility":"PRIVATE"}` 200 후 `POST /api/posts`(002) 임시글 `visibility = PRIVATE`(US5-2); `"FRIENDS"`·모르는 값 → 400 `INVALID_VISIBILITY`(errors[0].field = `defaultVisibility`); 비회원 401; 경로에 회원 번호가 없어 남의 설정을 바꿀 방법이 없음(본문에 `memberId`를 넣어도 본인 값만 바뀜, US5-3) (FR-023·FR-024) (구현 메모: `PATCH /api/me/settings`(001 T118)가 이 브랜치에 없어 그 API를 부르는 4칸(PRIVATE 저장, FRIENDS·모르는 값 400, 비회원 401, 본문 `memberId` 무시)은 핸들러가 있을 때만 돌고 지금은 `pending: 001`로 건너뛴다. 새 글이 기본값을 따르는지는 DB `default_visibility`를 직접 바꾼 뒤 002 `POST /api/posts`로 확인했다(US5-1·US5-2 통과))

### Implementation for User Story 5

- [X] T056 [US5] 기본 공개 범위 값 검증 확인: 001 T118의 `PATCH /api/me/settings`가 `defaultVisibility`를 post 모듈 `VisibilityRegistry.require(raw, "defaultVisibility")`로 검사하는지 확인한다(001 소유, 004는 고치지 않음 — 허용값 = 등록된 VisibilityRule 집합, data-model §3). T055가 이 확인을 대신한다 (depends on T013) (구현 메모: 확인 보류 — 001 설정 API가 이 브랜치에 없다. 001이 `VisibilityRegistry.require(raw, "defaultVisibility")`를 쓰면 T055의 400 칸이 통과한다(001 브랜치와 합친 뒤 DefaultVisibilityIT를 돌린다). 004는 001 파일을 고치지 않았다. 001과 합친 뒤: 001 `AccountSettingsService`가 `VisibilityRegistry.require(raw, "defaultVisibility")`로 검사함을 확인했고 DefaultVisibilityIT 6건·AccountStateIT 8건이 건너뜀 없이 통과)
- [X] T057 [P] [US5] 설정 화면 선택지 공유: 001 `frontend/src/pages/SettingsPage.tsx`의 기본 공개 범위 선택이 `frontend/src/features/visibility/visibilityOptions.ts`(T038)를 쓰도록 연결한다(값·라벨 한 곳 관리) (구현 메모: 001 `SettingsPage.tsx`가 아직 없다(`/settings`는 Placeholder). 001이 만들 때 `VisibilitySelect`를 값만 고르는 모드 + `label="새 글 기본 공개 범위"`로 쓰면 선택지·라벨이 `visibilityOptions.ts` 한 곳에서 온다 — 그 쓰임을 VisibilitySelect 테스트에 더했다. 001 소유 화면은 만들지 않았다. 001과 합친 뒤: 설정 화면이 `VisibilitySelect`(label "새 글 기본 공개 범위", `save`로 `PATCH /api/me/settings`)를 쓴다 — 001 SettingsPage.test에 선택지·거부 시 되돌림 확인 추가)

**Checkpoint**: 기본 공개 범위가 공개 범위 변경과 같은 값 규칙을 씀

---

## Phase 8: User Story 6 - 화면에는 할 수 있는 행동만 보인다 (Priority: P2)

**Goal**: 글 상세의 버튼과 댓글 입력창이 FR-045 규칙(2026-10-07 H6 반영)대로 보이고, 누르면 공통 안내가 나온다.

**Independent Test**: 같은 공개 글을 작성자·다른 회원·인증 전 회원·비회원·관리자 표시 플래그로 렌더링해 버튼과 클릭 결과를 비교한다(quickstart §4).

### Tests for User Story 6 ⚠️

- [X] T058 [P] [US6] 화면 테스트 작성 `frontend/src/components/PostActions.test.tsx`: 작성자 → [수정]·[공개 범위]·[삭제] 보임, [좋아요]·[신고]·[팔로우] 없음, 좋아요 수만(US6-1); 비회원·인증 전 회원·다른 회원 → [좋아요]·[신고] 보임, 비회원이 누르면 로그인 안내이고 로그인 후 자동으로 눌리지 않음(US6-2); 관리자 → [숨김]/[숨김 해제]만 추가, 남의 글 [수정]·[삭제] 없음 (FR-045) (구현 메모: [팔로우]는 `onFollow`(010)를 줄 때만 그린다(작성자 카드 자리와 겹치지 않게). 관리자 [숨김]은 관리자면 보이고 판정은 014 서버가 한다)
- [X] T059 [P] [US6] 화면 테스트 작성 `frontend/src/components/CommentInputGate.test.tsx`: 비회원 → "로그인하고 댓글 쓰기"; 인증 전 → "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"; 회원·관리자 → 입력창(children) 렌더링 (US6-3, FR-045)

### Implementation for User Story 6

- [X] T060 [P] [US6] 표시 플래그 타입 작성 `frontend/src/api/types/viewerFlags.ts`: `{ isAuthor, loggedIn, emailVerified, isAdmin }`(005 상세 응답의 `viewer` 필드, research R-29). 화면이 작성자 id를 직접 비교하지 않는다 (구현 메모: 005가 `api/types/reading.ts`에 임시로 둔 `ViewerFlags`를 이 파일로 옮기고 reading.ts는 다시 내보낸다(이름·뜻 그대로). 비회원 값 `ANONYMOUS_VIEWER`도 둔다)
- [X] T061 [US6] 버튼 표시 규칙 컴포넌트 작성 `frontend/src/components/PostActions.tsx`: FR-045 규칙으로 버튼 선택, [공개 범위]는 `VisibilitySelect`(T039), 누름 결과는 `useAuthGate`(T052) 사용 (depends on T039, T052, T060) (구현 메모: 비회원·인증 전 회원은 화면이 아는 사실로 바로 안내(`useAuthGate().show`)하고 요청을 보내지 않는다. 회원 요청이 거부되면 `useAuthGate({unauthorized:'prompt'})`가 안내한다. 005 상세 화면은 이미 `AuthorActions`·`ReactionBar` 자리로 나뉘어 있어 지금은 이 부품을 끼우지 않았다 — 009 좋아요·014 신고/숨김이 버튼 동작을 만들 때 이 부품으로 모은다)
- [X] T062 [US6] 댓글 입력창 안내 컴포넌트 작성 `frontend/src/components/CommentInputGate.tsx`(007 댓글 입력창이 감싸서 사용) (depends on T053, T060) (구현 메모: 인증 메일 재발송 버튼은 `features/auth-gate/ResendVerificationButton`으로 빼서 `AuthPrompt`와 함께 쓴다)

**Checkpoint**: 화면 버튼 규칙이 서버 판정을 보조함

---

## Phase 9: User Story 7 - 관리자 화면 주소 숨기기 (Priority: P3)

**Goal**: `/admin/**`·`/api/admin/**`는 비회원에게 있는 주소·없는 주소 모두 같은 401, 로그인한 일반 회원에게 404.

**Independent Test**: 비회원·일반 회원으로 `/admin/reports`, `/admin/xyz`, `/api/admin/xyz`를 열어 응답을 비교한다(quickstart 시나리오 7).

### Tests for User Story 7 ⚠️

- [X] T063 [P] [US7] 관리자 경로 통합 테스트 작성 `backend/src/test/java/com/team/blog/shared/security/integration/AdminPathIT.java`(+ 테스트 전용 `GET /api/admin/__probe` 컨트롤러): 비회원 GET·POST `/admin/reports`·`/admin/xyz` → 401 동일 응답, `/api/admin/xyz` → 401 `LOGIN_REQUIRED` JSON(있는 경로 `__probe`와 바이트 동일); 일반 회원 → 세 경로 모두 404 `NOT_FOUND`(본문 = 공통 404); 관리자 → `__probe` 200, 없는 경로 404 (FR-044, SC-007, research R-13·R-22) (구현 메모: 테스트 전용 컨트롤러는 `support/AdminProbeController`(GET·POST `/api/admin/__probe`). 비회원 POST는 CSRF 토큰이 있든 없든 GET과 같은 401, 인증 전·정지 회원도 일반 회원과 같은 404, 관리자도 CSRF 없는 쓰기는 403 `CSRF_REJECTED`, 관리자에서 일반 회원으로 바뀐 남은 세션은 곧바로 404인지도 본다)

### Implementation for User Story 7

- [X] T064 [P] [US7] 일반 회원 거부 처리기 작성 `backend/src/main/java/com/team/blog/shared/security/AdminPathAccessDeniedHandler.java`: 관리자 경로에서 `AccessDeniedException`을 403 대신 공통 404(`/api/admin/**` = JSON `NOT_FOUND` 본문 + `Cache-Control: private, no-store`, `/admin/**` = `NotFoundPageRenderer`)로 바꾼다 (구현 메모: 비회원(CSRF 실패로 온 쓰기 포함)은 비회원 진입점 401로 넘기고, 관리자는 001 `CsrfAccessDeniedHandler`에 맡긴다. `/admin/**` 404는 `NotFoundPageRenderer` 바이트 그대로)
- [X] T065 [P] [US7] 비회원 진입점 작성 `backend/src/main/java/com/team/blog/shared/security/AdminPathAuthenticationEntryPoint.java`: `/api/admin/**`는 001의 401 `LOGIN_REQUIRED` 진입점에 위임, `/admin/**` 화면은 401 상태로 React 셸(`classpath:static/index.html`, 메타 없음)을 내려 클라이언트가 `/login?returnTo=`로 안내 (research R-22) (구현 메모: 셸은 `classpath:static/index.html`에서 `<!--app-head-->` 자리를 비워 내리고 `Cache-Control: private, no-store`. 파일이 없으면 최소 셸)
- [X] T066 [US7] 관리자 경로 규칙 작성 `backend/src/main/java/com/team/blog/shared/security/AdminPathSecurityCustomizer.java`: 001 T026의 확장 지점 `SecurityFilterChainCustomizer`를 구현해(001 `SecurityConfig.java`는 고치지 않음) `/admin/**`·`/api/admin/**` → `hasRole("ADMIN")`을 다른 matcher보다 먼저(@Order로 앞에) 두고, 예외 처리에 T064·T065를 경로 한정(`defaultAccessDeniedHandlerFor`·`defaultAuthenticationEntryPointFor`)으로 등록. 경로 존재 확인 전에 적용되어야 함 (depends on T064, T065) (구현 메모: `hasRole("ADMIN")`(세션에 든 역할) 대신 매 요청 DB 역할(001 `MemberQueryService.findAccessInfo`, PK 1번 — 탈퇴 게이트가 읽어 둔 값이 있으면 재사용)로 판정한다(`AdminPaths`) — 관리자에서 내려간 회원의 남은 세션을 막는다. 001 `SecurityConfig`가 진입점·거부 처리기를 직접 지정해 `defaultAuthenticationEntryPointFor`·`defaultAccessDeniedHandlerFor`가 무시되므로, 001 처리기를 기본값으로 둔 경로별 위임 처리기(`DelegatingAuthenticationEntryPoint`·`RequestMatcherDelegatingAccessDeniedHandler`)를 customizer에서 다시 지정했다. `SecurityConfig.java`는 고치지 않았다)
- [X] T067 [P] [US7] 화면 라우트 가드 작성 `frontend/src/features/auth-gate/AdminRouteGate.tsx`: `/admin/*` 라우트에서 비로그인 → `/login?returnTo=` 안내, 로그인한 일반 회원 → `NotFoundPage`, 관리자 → 하위 화면(014) 렌더링 (depends on T025, T052) (구현 메모: `App.tsx`에 `/admin/*` 라우트를 더해 가드로 감쌌다(하위 화면은 014가 채울 Placeholder). 비로그인은 `<Navigate replace>`로 로그인 화면 이동)

**Checkpoint**: 관리자 기능(014)의 존재가 드러나지 않음

---

## Phase 10: User Story 8 - (선택 구현) 친구 공개 (Priority: P3)

**Goal**: `FRIENDS`를 적용한 사람만 친구 공개를 켠다. 공통 빌드에서는 이 Phase를 **건너뛴다**(공통 기본 비활성, 2026-10-07 M1). 공통 빌드의 "FRIENDS → 400"(US8-1)은 T003·T028이 이미 검증한다.

**Independent Test**: 적용 환경에서 친구 공개 글을 작성자·수락된 친구·요청 중 상대·비회원으로 열고 목록을 비교한다.

### Tests for User Story 8 (적용자만) ⚠️

- [X] T068 [P] [US8] 친구 공개 통합 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/FriendsVisibilityIT.java`(적용자 프로필에서만 실행): 수락된 친구 상세 보임, 비회원·친구 아님·PENDING 상대 404; 친구가 보는 블로그 목록·글 수에만 포함, 홈·공용 조건에는 없음; 친구 끊은 순간 404·목록 제외; FRIENDS→PUBLIC 시 `first_public_at`=지금(홈 맨 위); 친구 블로그 목록 `published_at DESC, id DESC` (US8-2·US8-3, FR-048) (구현 메모: 공통 `verify`에서는 건너뛴다 — 환경 변수 `BLOG_FRIENDS_IT=true`일 때만 `test`+`friends` 프로필로 돌고, 공용 DB에 V900을 적용하지 않도록 자기 PostgreSQL·Redis 컨테이너를 따로 띄운다(기본 실행에는 새 컨텍스트·연결이 생기지 않음). 친구 관계는 `friendship` 표에 직접 넣는다. 친구 블로그 목록 인덱스(`ix_post_blog_friends`) EXPLAIN과 적용 환경의 `PUT … FRIENDS` 200도 확인한다. 7건 통과)

### Implementation for User Story 8 (적용자만)

- [X] T069 [US8] 적용자 마이그레이션 작성 `backend/src/main/resources/db/migration/V{n}__friends.sql`: `ck_post_visibility`·`ck_member_default_visibility`를 `IN ('PUBLIC','FRIENDS','PRIVATE')`로 교체, `CREATE INDEX ix_post_blog_friends ON post (author_id, published_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility IN ('PUBLIC','FRIENDS') AND deleted_at IS NULL AND hidden_at IS NULL` (data-model §5, research R-14) (구현 메모: 공통 `db/migration`이 아니라 `db/friends/V900__friends.sql`에 두고 `application-friends.yml`이 Flyway `locations`에 더할 때만 적용된다(`out-of-order: true` — 이후 공통 V3…가 V900보다 작아서). V1·V2는 고치지 않았다)
- [X] T070 [US8] `backend/src/main/java/com/team/blog/post/domain/Visibility.java`에 `FRIENDS` 추가와 `backend/src/main/java/com/team/blog/post/domain/FriendsVisibilityRule.java` 작성: `canRead` = 001 `FriendshipQueryService`로 `member_a_id = LEAST(:author,:viewer) AND member_b_id = GREATEST(:author,:viewer) AND status = 'ACCEPTED'` 확인, 비회원 false (depends on T069) (구현 메모: `Visibility.FRIENDS` 값은 공통 enum에 두되 Rule Bean은 `blog.visibility.friends.enabled=true`(friends 프로필)일 때만 등록되어 공통 빌드는 계속 400. 001 `FriendshipQueryService`는 만들지 않고 post 모듈의 좁은 창구 `FriendshipChecker` + 읽기 전용 `JdbcFriendshipChecker`(같은 조건부 등록)로 `friendship`을 읽는다 — 001이 서비스를 만들면 그것을 감싸면 된다)
- [X] T071 [US8] 친구 블로그 목록 조건 추가 `backend/src/main/java/com/team/blog/post/infra/VisibilityFilter.java`: `forFriendBlog(viewer, authorId)` — `visibility IN ('PUBLIC','FRIENDS')` + 친구 EXISTS, 정렬·커서 `k = (published_at, id)`, 홈·태그·검색·sitemap 조건은 바뀌지 않음 (depends on T070) (구현 메모: `forFriendBlog(viewer, authorId)`는 `visibility IN ('PUBLIC','FRIENDS')`(부분 인덱스 술어) + `(PUBLIC OR 친구 EXISTS)`. 블로그 글 수도 같은 조건의 COUNT. 005 블로그 API·커서(`published_at, id`)에 연결하는 일은 적용자 몫으로 남겼다(공통 화면은 그대로))
- [X] T072 [US8] 화면 작성 `frontend/src/features/visibility/VisibilitySelect.tsx`·`visibilityOptions.ts`에 "👥 친구 공개" 선택지(적용 빌드 플래그로만)와 친구가 없을 때 "아직 친구가 없어서 지금은 나만 볼 수 있어요. [친구 초대] [전체 공개로 바꾸기]" 안내 (US8-4) (구현 메모: 선택지는 `VITE_FRIENDS_VISIBILITY=true` 빌드(또는 `friendsEnabled` prop)에서만. 친구가 없을 때 안내는 `hasFriends={false}`를 줄 때만 보인다 — 친구 수는 001 친구 API 몫이라 부르는 쪽이 넘긴다. [친구 초대]는 `/friends`(바꿀 수 있음))

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: 다른 기능이 엔드포인트를 만든 뒤의 교차 검증과 마무리

- [X] T073 [P] specs/005 목록·상세 API 완료 후 `backend/src/test/java/com/team/blog/post/integration/VisibilityMatrixIT.java`에 HTTP 경로 칸 추가: `GET /api/posts`, `GET /api/members/{handle}/posts`(작성자 본인 포함 비공개·숨김 글 없음), 블로그 머리말 글 수, `GET /api/posts/{postId}`(PRIVATE는 작성자만 200 + `private, no-store`) (FR-047, SC-004) (구현 메모: 005 목록·상세 API가 이 브랜치에 있어 `ReadingApi`로 `PostFixtures.State` 전부(`@EnumSource`: 공개·비공개·수정 중·임시글·휴지통·숨김·탈퇴 작성자 등)마다 비회원·다른 회원·작성자 본인·관리자 칸을 HTTP로 확인했다 — 홈·블로그 목록 미노출, 블로그 머리말 글 수, 상세 200/404와 `private, no-store`)
- [X] T074 [P] specs/005 `PageShellController` 완료 후 `backend/src/test/java/com/team/blog/post/integration/NotFoundIndistinguishableIT.java`에 `/@{handle}/posts/{postId}` 화면 비교 추가: 볼 수 없는 글과 없는 번호의 상태·본문 바이트·`Cache-Control: private, no-store`·og 문구·robots noindex 동일(`Date` 헤더 제외) (quickstart 시나리오 2, SC-002) (구현 메모: 005 `PageShellController`의 `/@{handle}/posts/{postId}`로 볼 수 없는 글(비공개·임시글·휴지통)과 없는 번호를 비교했다 — 상태·본문 바이트·`Cache-Control`·og 문구·robots noindex 동일, `Date` 헤더 제외)
- [X] T075 specs/002·006 행동 실행기 등록 후 `PermissionMatrixIT`를 돌려 `PendingRowReport`의 Tier A 행(저장·발행·변경 취소·삭제·복구·영구 삭제) 건너뜀 0건 확인. 숨김(014)·댓글(007) 행은 해당 기능 착수 때 같은 방식으로 채운다 (SC-001) (구현 메모: `PermissionMatrixIT`에 `@AfterAll Tier_A_대기_행은_0건`을 두어 `PendingRowReport.pendingOwners()`가 014·007 외 owner를 갖지 않음을 매 실행 확인한다. 현재 남은 대기 행은 숨김(014)뿐이고 002·006 Tier A 행은 모두 실행된다)
- [X] T076 [P] 상세 판정 쿼리 수 테스트 작성 `backend/src/test/java/com/team/blog/post/integration/ReadDecisionQueryCountIT.java`: `requireReadable` 1회 = SQL 1번(글 + 작성자 JOIN), `CurrentViewerResolver`는 인증 요청당 PK 조회 1번 (plan Performance Goals) (구현 메모: 상세 판정 쿼리 수는 기존 시험 지원 `SqlCounter`로 셌다 — `requireReadable` 1회 = SQL 1번, 인증 요청의 `CurrentViewerResolver` PK 조회 1번)
- [X] T077 [P] 계약 일치 테스트 작성 `backend/src/test/java/com/team/blog/post/web/VisibilityOpenApiContractIT.java`: springdoc `/v3/api-docs`의 `PUT /api/posts/{postId}/visibility` 경로·요청·응답 스키마·응답 코드(200/400/401/403/404)가 `specs/004-visibility-permission/contracts/openapi.yaml`과 같음 (구현 메모: springdoc이 pom에 없어 `/v3/api-docs`가 없다. pom을 바꾸지 않고 시험 의존성인 SnakeYAML로 `contracts/openapi.yaml`을 읽어 ① 핸들러 매핑(PUT·경로 변수) ② 요청·응답 DTO record 구성 요소 ③ 실제 응답의 상태 코드(200/400/401/403/404)·필드를 계약과 대조했다)
- [X] T078 quickstart.md 검증: §2 자동 검증 명령(`VisibilityMatrixIT,PermissionMatrixIT,VisibilityChangeIT,NotFoundIndistinguishableIT,ListIndexUsageIT,AccountStateIT,AdminPathIT,SessionResilienceIT` + 단위 테스트 3종) 통과, §3 수동 시나리오 1~8과 §4 화면 확인(375px 가로 스크롤 없음)을 `docker compose up -d`로 실행 (구현 메모: §2 자동 검증 명령은 `./mvnw -q verify`에 포함돼 통과. 공용 포트·고정 컨테이너 이름을 쓰지 말라는 팀 규칙 때문에 `docker compose up -d` 대신, 빈 임의 포트로 띄운 Postgres·Redis 컨테이너 + 빌드한 jar(local 프로필)에 Playwright `frontend/e2e/visibility.spec.ts`(§3 시나리오 1·3·5·7과 관리자 경로, §4 375px 가로 스크롤 없음)를 006 `manage-posts.spec.ts`와 함께 `--workers=1`로 돌려 4건 통과했다(두 파일이 같은 회원을 써서 병렬이면 글 수가 섞인다). 시나리오 2·4·6·8은 통합 테스트(T041·T074, T029, T049, T055)로 대신했다)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001 Phase 1·2 완료 → 이 기능 Phase 1 시작. specs/002 Phase 2(Post·PostRepository)는 US1의 T031·T032 전에 필요하다(Phase 2는 002 없이 진행 가능 — `PostView`·`PostQueryRepository`는 네이티브 SQL이라 Post 엔티티가 필요 없다)
- **Setup (Phase 1)**: 001 완료 확인만
- **Foundational (Phase 2)**: Phase 1 이후. 이 기능의 모든 스토리와 **002·005·006의 권한 관련 작업을 막는다**
- **User Stories (Phase 3~10)**: Phase 2 이후
  - US1(P1) → 002 Phase 2 필요
  - US2(P1), US3(P1): Phase 2 이후 독립. US3의 T047은 US1의 T035(엔드포인트)와 US2의 T041(ReadProbe)을 쓴다
  - US4(P2): Phase 2 이후. 테스트 대상 쓰기 행동으로 US1 엔드포인트를 쓴다. 화면 작업은 T024(Phase 2) 이후
  - US5(P2): 001 설정 API·002 새 글 API 필요
  - US6(P2): US1의 T039, US4의 T052·T053 이후
  - US7(P3): Phase 2 이후 독립(T066은 001 T026의 `SecurityFilterChainCustomizer` 사용, T067만 T025·T052 이후)
  - US8(P3, 선택 구현): 공통 빌드에서는 하지 않음
- **Polish (Phase 11)**: T073·T074는 005, T075는 002·006 완료 후

### User Story Dependencies

- **US1 (P1)**: Foundational + 002 Post/PostRepository. 다른 스토리 의존 없음
- **US2 (P1)**: Foundational만. 공개 범위가 바뀐 데이터는 픽스처로 만든다
- **US3 (P1)**: Foundational + US1 엔드포인트(실행기 1종). 하네스는 Phase 2
- **US4 (P2)**: Foundational + 001 가드·세션. US1 엔드포인트를 테스트 대상으로 사용
- **US5 (P2)**: Foundational + 001 `/api/me/settings` + 002 `POST /api/posts`
- **US6 (P2)**: US1(VisibilitySelect) + US4(useAuthGate) 화면 작업, 005 상세 응답의 `viewer` 플래그
- **US7 (P3)**: Foundational + 001 SecurityConfig
- **US8 (P3)**: 선택 구현. Foundational + 001 FriendshipQueryService

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- enum·record → Repository·Policy → Service → Controller → 화면 순서
- 다른 기능 소유 파일(T019·T031·T032·T036·T057)은 메서드·연결 한 줄만 **추가**하고, 이미 있으면 확인만 한다. T056은 확인만 한다

### Parallel Opportunities

- Phase 2: T003~T008(테스트 6종), T009·T010·T014·T016·T019·T020·T021·T023·T024가 서로 다른 파일
- US1: T026~T029 테스트 4종, T030·T033·T037·T038
- US2: T040~T043 테스트 4종
- US3: T045·T046
- US4: T049~T051, T052
- US6: T058·T059·T060
- US7: T063·T064·T065·T067
- Phase 2 이후 US1·US2·US7은 다른 사람이 동시에 진행 가능

---

## Parallel Example: Foundational

```bash
# 테스트 먼저 (모두 다른 파일):
Task: "VisibilityRegistryTest in backend/src/test/java/com/team/blog/post/domain/VisibilityRegistryTest.java"
Task: "PostAccessPolicyTest in backend/src/test/java/com/team/blog/post/domain/PostAccessPolicyTest.java"
Task: "VisibilityFilterTest in backend/src/test/java/com/team/blog/post/infra/VisibilityFilterTest.java"
Task: "CacheControlPolicyTest in backend/src/test/java/com/team/blog/shared/web/CacheControlPolicyTest.java"
Task: "NotFoundPageRendererTest in backend/src/test/java/com/team/blog/shared/web/NotFoundPageRendererTest.java"

# 서로 의존하지 않는 구현:
Task: "Visibility enum in backend/src/main/java/com/team/blog/post/domain/Visibility.java"
Task: "Viewer record in backend/src/main/java/com/team/blog/shared/security/Viewer.java"
Task: "PostView + PostQueryRepository in backend/src/main/java/com/team/blog/post/infra/"
Task: "CacheControlPolicy in backend/src/main/java/com/team/blog/shared/web/CacheControlPolicy.java"
Task: "NotFoundPageRenderer in backend/src/main/java/com/team/blog/shared/web/NotFoundPageRenderer.java"
Task: "MemberFixtures/PostFixtures in backend/src/test/java/com/team/blog/support/fixture/"
```

## Parallel Example: User Story 1

```bash
# 테스트 4종 동시에:
Task: "PostChangeVisibilityTest in backend/src/test/java/com/team/blog/post/domain/PostChangeVisibilityTest.java"
Task: "PostVisibilityControllerContractTest in backend/src/test/java/com/team/blog/post/web/PostVisibilityControllerContractTest.java"
Task: "VisibilityChangeIT in backend/src/test/java/com/team/blog/post/integration/VisibilityChangeIT.java"
Task: "VisibilityChangeEventIT in backend/src/test/java/com/team/blog/post/integration/VisibilityChangeEventIT.java"

# 서로 다른 파일의 구현:
Task: "PostVisibilityChanged/PostWentPublic in backend/src/main/java/com/team/blog/shared/event/"
Task: "VisibilityChangeRequest/Response in backend/src/main/java/com/team/blog/post/web/dto/"
Task: "setVisibility in frontend/src/api/posts.ts"
```

## Parallel Example: User Story 2

```bash
Task: "VisibilityMatrixIT in backend/src/test/java/com/team/blog/post/integration/VisibilityMatrixIT.java"
Task: "NotFoundIndistinguishableIT in backend/src/test/java/com/team/blog/post/integration/NotFoundIndistinguishableIT.java"
Task: "ListIndexUsageIT in backend/src/test/java/com/team/blog/post/integration/ListIndexUsageIT.java"
Task: "VisibilityFilterUsageGuardTest in backend/src/test/java/com/team/blog/post/VisibilityFilterUsageGuardTest.java"
```

---

## Implementation Strategy

### MVP First (Foundational + User Story 1)

1. 001 Phase 1·2 완료 확인 (Phase 1)
2. Phase 2 Foundational 완료 — 002·005·006이 기다리므로 Tier A 전체의 임계 경로다. 가장 먼저 끝낸다
3. Phase 3 US1 (002 Phase 2 이후)
4. **STOP and VALIDATE**: VisibilityChangeIT·VisibilityChangeEventIT, quickstart 시나리오 1·3·4·5

### Incremental Delivery

1. Setup + Foundational → 002·005·006에 판정 장치 제공
2. US1 → 공개 범위 변경 (MVP)
3. US2 → 새지 않음 매트릭스·404 동일성·인덱스 검증
4. US3 → 권한 매트릭스 (P1 완료 = Tier A 004 완료 기준)
5. US4·US5·US6 (P2) → 계정 상태 일관성, 기본 공개 범위, 화면 버튼
6. US7 (P3) → 관리자 경로 숨김 (014 착수 전까지)
7. Polish → 005·002·006 완료 뒤 HTTP 경로·화면 404·대기 행 0건 확인
8. US8은 `FRIENDS` 적용자만

### Parallel Team Strategy

1. 한 사람이 Phase 2를 먼저 끝낸다(다른 기능이 기다림)
2. 이후: A — US1(+US3 실행기), B — US2(+US7), C — US4·US6 화면
3. 005가 끝나면 T073·T074, 002·006이 끝나면 T075

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 인수 시나리오 ↔ 테스트: US1-1~6 → T028, US2-1·3·4·5 → T040, US2-2 → T041·T074, US3-1·2·3 → T045, US3-4 → specs/007이 `comment-write.csv`로 추가, US4-1~6 → T049, US5-1~3 → T055, US6-1~3 → T058·T059, US7-1·2 → T063, US8 → T068
- quickstart ↔ 테스트: 시나리오 1·3·4·5 → T028·T029, 2 → T041·T074, 6 → T049, 7 → T063, 8 → T055
- 이 기능은 공통 마이그레이션을 추가하지 않는다(적용자의 `V{n}__friends.sql`만 예외)
- Verify tests fail before implementing
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
