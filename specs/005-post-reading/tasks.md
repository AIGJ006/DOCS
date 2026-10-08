---

description: "Task list for 005-post-reading (전체 글 목록·개인 블로그·글 상세)"
---

# Tasks: 글 읽기 (전체 글 목록·개인 블로그·글 상세)

**Input**: Design documents from `/specs/005-post-reading/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md, `.specify/memory/constitution.md`

**Tests**: 포함한다. constitution 원칙 VIII(권한·데이터 규칙은 Testcontainers PostgreSQL 통합 테스트 필수)과 spec의 Acceptance Scenarios·quickstart 시나리오(Q-1~Q-12)를 테스트로 옮긴다. 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고, 구현 전에 실패하는지 확인한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 읽기 전용이며(새 테이블·컬럼·마이그레이션 없음, plan Constitution Check I·VI) 공통 기반을 소유하지 않는다. 아래 항목은 **직접 만들지 않고** 선행 작업으로 기다린다. 작업 번호는 각 스펙의 tasks.md 기준(2026-10-07 작성본)이다.

**선행 (이 기능 시작 전에 끝나 있어야 함 — Phase 1 T001·T002에서 확인)**

- 선행: specs/001 T001~T005 (Setup) — backend Maven 골격(`com.team.blog`, package-by-feature), frontend React 골격(라우터, Vitest + Testing Library, `index.html`의 `<!--app-head-->` 자리 표시자 — T003), `docker-compose.yml`(app + PostgreSQL + Redis + MinIO + Mailpit)
- 선행: specs/001 T010·T011 — Flyway `V1__common_schema.sql`(docs/51 기준 전체 테이블·CHECK·인덱스: `ix_post_feed`·`ix_post_blog`·`uq_image_profile_current`·`uq_image_thumb_key`·`uq_member_handle`·`uq_post_tag_position`)
- 선행: specs/001 T008·T009 — `support/IntegrationTestBase`(Testcontainers PostgreSQL + Redis), `TestLogin`·`RedisOutage` 등 테스트 도구 → T005·T072·T073
- 선행: specs/001 T017·T018 — `shared/error`(`ErrorResponse{code,message,errors,details}`, `GlobalExceptionHandler`, `NotFoundException`→404 `NOT_FOUND`, `InvalidCursorException`→400 `INVALID_CURSOR`)
- 선행: specs/001 T026·T027·T028 — `SecurityConfig`(세션 쿠키 + CSRF `XSRF-TOKEN` 쿠키/`X-XSRF-TOKEN` 헤더), `CurrentUser`, `ResilientSessionRepository`(Redis 장애 시 비로그인) → T073
- 선행: specs/001 T020·T021 — `shared/web/cursor/CursorCodec`·`CursorPayload`·`ListScope`·`InvalidCursorException`(목록 구분 필드 `l` 검증 포함, research R-24) → T006·T007
- 선행: specs/001 T015 — `CoreProperties`(`blog.time-zone`, `blog.image.public-base-url`)와 `Clock`/`ZoneId` Bean → T003
- 선행: specs/001 T029·T031 — 보안 헤더/CSP 필터(`script-src 'self'`), `SpaForwardingController`(SPA 대체 경로 — `PageShellController`의 더 구체적인 매핑이 우선) → T038·T050
- 선행: specs/001 T039·T040 — `MemberQueryService.findReadableBlogOwner(handle)`(→ `BlogOwner{id, handle, nickname, bio}`, `WITHDRAWN`·익명 처리 → empty)·`normalizeHandle`, `media/application/ImageUrlResolver`(최종 규칙, 003 소유), `media/application/ProfileImageQuery.currentKeys(memberId)`·`currentKeysOf(ids)` → `ProfileImageKeys(original, thumbnail)`(작은 사진은 `display()` = 썸네일 없으면 원본, og:image는 `original()`; 읽기 전용, 003이 넘겨받음) → T010·T035·T048·T050
- 선행: specs/001 T041·T042 — `frontend/src/api/client.ts`(**001 T042가 만들고 소유**: `ApiError{status,code,message,errors,details,retryAfter}`, `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더. 004 T024는 404 분기만 추가) → T013
- 선행: specs/004 T024 — `client.ts`에 404 `NOT_FOUND` → `onNotFound`(공통 404 화면 전환) 분기 추가 → T042·T051
- 선행: specs/004 T010·T011 — `shared/security/Viewer`(`id, role, status, emailVerified`, `isAuthorOf`, `isAdmin`)·`CurrentViewerResolver` → 모든 컨트롤러
- 선행: specs/004 T014~T017 — `post/infra/PostView`·`PostQueryRepository.findPostView`, `post/domain/PostAccessPolicy.canRead(PostView, Viewer)`, `PostNotFoundException`, `PostReadService` → T034·T036
- 선행: specs/004 T018 — `post/infra/VisibilityFilter.forViewer(viewer, authorId)` → `SqlCondition(sql, params)`(별칭 `p`·`m`) → T010
- 선행: specs/004 T019·T020 — `shared/web/CacheControlPolicy.forPost(status, visibility, hidden)`·`forPost(PostView)`·`notFound()`(+ 404에 `private, no-store` 연결), `shared/web/NotFoundPageRenderer`(공통 404 HTML: 06 §3-1 OG + `noindex`, `<!--app-head-->` 사용) → T037·T038·T050
- 선행: specs/004 T021~T023 — 테스트 픽스처(`MemberFixtures`·`PostFixtures`), 권한 매트릭스 하네스(`Actor`, `PermissionAction`, `AbstractPermissionMatrixIT`, 목록 행동 `INCLUDED`/`EXCLUDED` 비교), `permission/post-read.csv` → T005·T027·T028
- 선행: specs/004 T025 — `frontend/src/pages/NotFoundPage.tsx` → T042·T051
- 선행: specs/002 T018·T019·T021 — `post/domain/Post`(`@SQLRestriction("deleted_at IS NULL")` — 이 기능의 네이티브 조회에는 적용되지 않음)·`PostStatus`·`PostDraft`, `PostRepository`·`PostDraftRepository`
- 선행: specs/002 T050 — 발행 때 `content_html`·`excerpt`·`thumbnail_url`·`first_public_at`·`edited_at` 저장(이 기능은 읽기만, `ContentRenderer`를 호출하지 않음). 테스트는 T004 픽스처로 독립 실행 가능

**선행 (해당 User Story 테스트·구현 전에 필요)**

- 선행: specs/004 T044(004 US2) — `PostQueryRepository.countListedByAuthor(viewer, authorId)`(블로그 공개 글 수) → US3(T048)
- 선행: specs/001 T120(001 US6)·T132(001 US7)·T142(001 US8) — frontend `DefaultAvatar`, `FriendButton`, `LastActiveBadge`(+ `GET /api/members/{handle}/friend`) → Phase 2(T015), US3(T051). 미완이면 임시 아이콘·빈 자리로 두고 나중에 연결
- 선행: specs/004 T035·T039(004 US1)·T052(004 US4)·T060·T061(004 US6) — `PUT /api/posts/{postId}/visibility`, `VisibilitySelect.tsx`·`VisibilityBadge.tsx`, `useAuthGate.ts`, `api/types/viewerFlags.ts`(`{isAuthor, loggedIn, emailVerified, isAdmin}` — 005 상세 응답 `viewer`가 채움), `components/PostActions.tsx`(FR-045) → US2(T039·T042), US4(T058·T059)
- 선행: specs/002 T022 — `PostDraftQueryService.findSavedAt(postId)`(작업본 유무·`post_draft.updated_at`) → US4(T055)
- 선행: specs/002 T062(002 US2) — `frontend/src/features/markdown/highlightCode.ts`(상세 코드 강조 공용) → US2(T040)
- 선행: specs/002 T054(002 US1)·T093·T094(002 US4) — 에디터 경로 `/write/{postId}`·`/write/new`, `DELETE /api/posts/{postId}/working-copy`(변경 취소) → US3(T051), US4(T058·T059), US6(T071)
- 선행: specs/006 — `DELETE /api/posts/{postId}`(휴지통으로) + 확인 창 문구(`confirmDialogs.ts`) → US4(T059)

**임시 구현 + 소유 스펙에서 교체 (Tier B·C가 아직 plan 전이거나 미구현)**

- 003-image-upload: `media/application/OgImageResolver`(T062, 썸네일 키 → 원본 키), GIF 재생 자리 `frontend/src/features/post-detail/gifPlayer.ts`(T041, no-op)
- 007-comment: 상세 댓글 영역 자리 `CommentSectionSlot`(T041, 머리말만; 007이 `GET /api/posts/{postId}/comments[?around=]` 컴포넌트를 연결)
- 008-tag: `PostTagNamesQuery` 임시 구현 = 빈 목록(T033)
- 009-like-view: `PostLikeStatusQuery` 임시 구현 = false(T033), `POST /api/posts/{postId}/views`(없으면 비콘 404 무시, T040)
- 010-follow-feed: `AuthorFollowStatusQuery` 임시 구현 = false(T033), 작성자 카드 [팔로우] 자리(T039)
- 014-report-hide: [신고] 버튼 동작·숨김 사유 표시 자리(T039·T058)

**후속 (다른 스펙이 이 기능을 사용)**

- specs/004 T073·T074: 005 완료 뒤 목록·상세 HTTP 경로 매트릭스와 `/@{handle}/posts/{postId}` 화면 404 바이트 비교를 추가 검증
- specs/006: 상세·홈·블로그 API(`GET /api/posts/{postId}`, `GET /api/posts`, `GET /api/members/{handle}/posts`, `GET /api/members/{handle}`)로 휴지통·복구 결과를 검증
- specs/002 T060: XSS Playwright가 005 상세 화면을 연다
- specs/001 US3 #7: 블로그 주소 대문자 301·없는 주소 404는 이 기능 T045가 확인
- 007·008·010·012: 공용 `CursorCodec` + `ListScope` 규약을 같은 방식으로 사용, 010은 `BlogHeaderView`에 팔로워 수·팔로우 여부 필드 추가

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/java/com/team/blog/...`, `backend/src/test/java/com/team/blog/...`, `backend/src/main/resources/...`, `frontend/src/...` (plan.md Project Structure)
- 모듈: 목록·블로그·첫 응답(메타) = `discovery`, 글 상세 = `post`, 커서·셸 렌더링 = `shared`, 사진 주소 = `media`(003 소유, 임시 구현)
- 백엔드 테스트 이름: 단위 `*Test`, 통합 `*IntegrationTest`/`*MatrixTest`(Testcontainers PostgreSQL + Redis, 001의 통합 테스트 베이스 상속, H2 금지)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 공통 기반은 001·002·004가 소유한다. 이 Phase는 선행 작업이 끝났는지 확인만 한다.

- [X] T001 001 Phase 1·2(공통 기반 체크포인트 specs/001 T001~T042) 완료를 확인한다: 골격(specs/001 T001~T003, `frontend/index.html`의 `<!--app-head-->` 자리 표시자 포함), `docker-compose.yml`(specs/001 T004), Flyway `V1__common_schema.sql`(specs/001 T011 — `post`·`member`·`image`·`post_tag`·`tag`·`post_draft`·`post_like`·`follow`와 인덱스 `ix_post_feed`·`ix_post_blog`·`uq_image_profile_current`·`uq_image_thumb_key`·`uq_member_handle`), `IntegrationTestBase`·테스트 도구(specs/001 T008·T009 — `TestLogin`, `RedisOutage`), `shared/error`(specs/001 T018 — `NOT_FOUND`·`INVALID_CURSOR`), `CursorCodec`·`ListScope`(specs/001 T021 — `l` 검증 포함), `CoreProperties`(specs/001 T015), Redis 장애 시 비로그인(specs/001 T028), `SecurityConfig`+CSRF(specs/001 T026), 보안 헤더/CSP `script-src 'self'`(specs/001 T029), SPA 대체 경로 `SpaForwardingController`(specs/001 T031), `ImageUrlResolver`(specs/001 T040), `MemberQueryService.findReadableBlogOwner`·`normalizeHandle`(specs/001 T039), `frontend/src/api/client.ts`(specs/001 T042). `cd backend && ./mvnw -q verify`, `cd frontend && npm test`가 통과해야 한다. 빠진 항목은 이 기능에서 만들지 말고 해당 기능 담당에게 알린다. (구현 메모: 2026-10-07 확인 — 001 Phase 1·2 산출물이 모두 있고 `./mvnw -q verify`·`npm test` 통과. 다른 점: `MemberFixtures`는 `support/fixture/`가 아니라 001이 둔 `backend/src/test/java/com/team/blog/support/MemberFixtures.java`, SQL 수 세기 도우미는 001 `support/SqlCounter`. 001 T039 `findReadableBlogOwner`·T040 `ImageUrlResolver`·`ProfileImageQuery`, T042 `client.ts`, 004 T024 `onNotFound`·T025 `NotFoundPage` 모두 있음. 001 US6~US8(`DefaultAvatar`·`FriendButton`·`LastActiveBadge`·친구 API)과 004 US1·US4·US6(`PostActions`·`VisibilitySelect`·`useAuthGate`·`viewerFlags`), 004 T044 `countListedByAuthor`는 아직 없음)
- [X] T002 004 Phase 2(Foundational) 완료를 확인한다: `shared/security/Viewer`(specs/004 T010 — `id, role, status, emailVerified`, `isAuthorOf`, `isAdmin`)·`CurrentViewerResolver`(specs/004 T011), `post/infra/PostView`·`PostQueryRepository.findPostView`(specs/004 T014), `post/domain/PostAccessPolicy.canRead(PostView, Viewer)`(specs/004 T015), `post/infra/VisibilityFilter.forViewer(viewer, authorId)` → `SqlCondition(sql, params)`(specs/004 T018), `shared/web/CacheControlPolicy`(specs/004 T019), `shared/web/NotFoundPageRenderer`(specs/004 T020), 테스트 픽스처 `support/fixture/MemberFixtures`·`PostFixtures`(specs/004 T021), 권한 매트릭스 하네스 `support/permission/*`(specs/004 T022)와 `permission/post-read.csv`(specs/004 T023), frontend `api/client.ts`(specs/004 T024)·`pages/NotFoundPage.tsx`(specs/004 T025). 002 Foundational(`post/domain/Post`·`PostStatus`·`PostDraft`, 발행 시 `excerpt`·`thumbnail_url`·`first_public_at`·`content_html` 저장)도 확인한다. `VisibilityFilter` 조각이 `ix_post_feed`·`ix_post_blog`의 부분 인덱스 WHERE를 그대로 포함하는지 읽어 확인한다(research R-06). (구현 메모: 확인함. 004 T019 `CacheControlPolicy`·T020 `NotFoundPageRenderer`(`<!--app-head-->` 자리 사용)·T021~T023 픽스처·하네스·`post-read.csv` 모두 있음. `VisibilityFilter.forViewer`가 `p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.hidden_at IS NULL AND m.withdrawn_at IS NULL`를 그 순서·문구로 내보내 `ix_post_feed`·`ix_post_blog` 술어와 같음(research R-06 충족). 004 T044 `PostQueryRepository.countListedByAuthor`는 없어 T010에 같은 조건으로 임시로 둠)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: US1·US2·US3가 함께 쓰는 이 기능 고유의 기반 — 설정값, 테스트 픽스처, 목록 커서 변환, 카드 조회(SQL 1번), 셸 렌더러, 공용 카드 화면 부품

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] 설정값 바인딩 `backend/src/main/java/com/team/blog/discovery/application/ReadingProperties.java`(`@ConfigurationProperties` — plan의 키 그대로 `blog.list.page-size`(기본 9), `blog.seo.description-length`(기본 160), `blog.seo.default-og-image-url`, `blog.site.base-url`; 001의 "기능별 `blog.<기능>` 클래스" 규칙(specs/001 T015)과 키 이름이 다르므로 공용 `CoreProperties`와 같은 접두어를 겹쳐 바인딩해도 되는지 확인하고, 안 되면 `blog.list.*`·`blog.seo.*`·`blog.site.*`를 각각 작은 record로 나눈다)를 만들고 `backend/src/main/resources/application.yml`에 기본값을 추가한다. `blog.image.public-base-url`·`blog.time-zone`은 001 `CoreProperties`를 쓴다. 클라이언트 `size`는 어디서도 쓰지 않는다(원칙 VII, research R-04, data-model §6). (구현 메모: `blog.list`·`blog.seo`·`blog.site`를 record 하나(`ReadingProperties`, 접두어 `blog`)에 나눠 담았다 — 002 `PostAuthoringProperties`와 같은 방식이라 `CoreProperties`와 겹치지 않는다. 기본값은 `application.yml`에, `blog.site.base-url`·`blog.seo.default-og-image-url`은 환경 변수로 바꿀 수 있게 하고 `.env.example`에 적었다)
- [X] T004 [P] 테스트 픽스처 `backend/src/test/resources/fixtures/post-reading.sql`을 quickstart §1 그대로 작성한다: 회원 A(`kim755030`) 공개·노출 글 12개(수정 중 `post_draft` 1개, `edited_at` 있는 재발행 1개, `PRIVATE`→`PUBLIC`으로 공개 범위만 바꾼 글 1개(`edited_at` NULL), `<pre><code>` 포함 글 1개, 제목에 `"><script>alert(1)</script>` 넣은 글 1개) + `PRIVATE` 3개·`DRAFT` 1개·휴지통(`deleted_at`) 1개·관리자 숨김(`hidden_at`, `PUBLIC`) 1개, 회원 B(`na_ms`) 공개 글 8개(그중 2개는 `first_public_at`이 같은 마이크로초), 회원 C(탈퇴 신청 `status='WITHDRAWN'`, `withdrawn_at` 있음) 공개 글 2개, 회원 D 공개 글 0개, 회원 A 프로필 사진 `image`(purpose `PROFILE`, `thumb_storage_key` 있음), 첫 사진 `image` 행(`thumb_storage_key`=`{uuid}_thumb.webp`, `storage_key`=원본). 홈 기준 공개·노출 글 합계 20개. 51의 CHECK(`ck_post_public_at`, `ck_post_edited_at`, `ck_member_withdrawn` 등)를 통과해야 한다. (구현 메모: 글·회원 번호를 고정하지 않고(IDENTITY) 제목·handle로 찾는다(T005). 회원 B `na_ms`, C `kang_sc`(탈퇴 신청), D `empty_d`, 숨김 처리자 `admin_ops`를 더했다. 로그인 수단은 `ck_auth_password` 때문에 GITHUB로 넣었다. 다시 발행 글에 태그 3개(spring·jpa·성능, position 0·1·2)를 함께 넣어 T025의 순서 확인에 쓴다)
- [X] T005 [P] 픽스처 로더·행위자 도우미 `backend/src/test/java/com/team/blog/discovery/support/PostReadingFixture.java`: T004 SQL을 각 테스트 전에 적용하고, 글 id를 이름으로 꺼내는 메서드(`postOf("A", "republished")` 등)를 둔다. 행위자(비회원·인증 전·회원 B·작성자 A·관리자·정지 회원) 세션과 CSRF는 004 하네스(specs/004 T022 `Actor`)와 001 통합 테스트 베이스의 도우미를 재사용하고, 추가 행이 필요하면 004 T021 `MemberFixtures`·`PostFixtures`를 쓴다. 실행 SQL 수를 세는 도우미(Hibernate Statistics 또는 datasource-proxy)를 여기 둔다(001 베이스에 이미 있으면 그것을 쓴다). (구현 메모: `PostReadingFixture.load(jdbc)`(ResourceDatabasePopulator) + `postOf("A","republished")`·`memberId`·`loginAs`·`HOME_TITLES`·`A_BLOG_TITLES`. 행위자 세션은 001 `TestLogin.loginAs`, 추가 회원·글은 001 `support/MemberFixtures`·004 `support/fixture/PostFixtures`를 쓴다. SQL 수는 001 `support/SqlCounter`를 쓰고, SQL 문자열까지 봐야 하는 확인용으로 `discovery/support/SqlCapture`를 더했다)
- [X] T006 [P] 목록 커서 변환 단위 테스트 `backend/src/test/java/com/team/blog/discovery/application/PostListCursorTest.java`를 먼저 작성한다(실패 확인): `PostListCursor.encode(ListScope.home(), firstPublicAt, id)` → 001 `CursorCodec`으로 `{"v":1,"l":"home","k":[1790755200123456,37]}`(epoch 마이크로초 UTC 정수, 패딩 없는 Base64URL), `decode`는 `OffsetDateTime`·id를 마이크로초 손실 없이 돌려줌(`…12.123456Z` 왕복), `blog:kim755030` 범위 커서를 홈 범위로 풀면·`k` 길이가 2가 아니면·`k[0]`이 정수가 아니면·`k[1]`이 1 미만이면 `InvalidCursorException`(→ 400 `INVALID_CURSOR`). 손상 문자열·모르는 `v`·`l` 불일치 검증은 001 `CursorCodecTest`(specs/001 T020)가 이미 하므로 반복하지 않는다(research R-05·R-24, FR-006, Edge Cases 마이크로초).
- [X] T007 [P] 목록 커서 변환 `backend/src/main/java/com/team/blog/discovery/application/PostListCursor.java`(record `CursorKey(OffsetDateTime firstPublicAt, long id)`, `encode(ListScope, OffsetDateTime, long)`, `CursorKey decode(String cursor, ListScope expected)`)를 001 `shared/web/cursor/CursorCodec`·`CursorPayload`·`ListScope`(specs/001 T021 — `l` 필드 검증 포함)로 구현한다. 홈 범위 `ListScope.home()`, 블로그 범위 `ListScope.blog(handle)`(소문자 handle). 시각↔마이크로초 변환은 `ChronoUnit.MICROS`로 하고 밀리초로 자르지 않는다(T006 통과). (구현 메모: `CursorKey`는 `OffsetDateTime`(UTC)과 `long id`. 시각 범위 밖·id<1·키 2개 아님은 `InvalidCursorException`)
- [X] T008 [P] 공용 응답 형태 `backend/src/main/java/com/team/blog/discovery/application/PostCardView.java`(record: `id, url, title, excerpt, thumbnailUrl, firstPublicAt(Instant, 마이크로초), commentCount, likeCount, author{handle, nickname, profileImageUrl}` — `url` = `/@{handle}/posts/{id}`, 본문 필드 없음)와 `CursorPage.java`(record `items`, `nextCursor` nullable)를 만든다(contracts `PostCard`·`PostCardPage`, data-model §5).
- [X] T009 카드 조회 통합 테스트 `backend/src/test/java/com/team/blog/discovery/infra/PostCardQueryRepositoryIntegrationTest.java`를 먼저 작성한다(실패 확인): 공개·노출 글만(PRIVATE·DRAFT·휴지통·숨김·탈퇴 신청 작성자 글 제외), `first_public_at DESC, id DESC`, 같은 마이크로초 두 글이 `(t,id)` 커서 경계에서 빠지지 않음, `limit` = page-size+1, `authorId` 조건, 실행 SQL이 정확히 1번, 실행 SQL 문자열에 `content_md`·`content_html`이 없음, 프로필 사진은 `COALESCE(thumb_storage_key, storage_key)`(SC-002, C-READ-1 #6, Edge Cases 동순위).
- [X] T010 카드 조회 `backend/src/main/java/com/team/blog/discovery/infra/PostCardQueryRepository.java`를 구현한다: `List<PostCardRow> findCards(Viewer viewer, Long authorId, CursorKey after, int limit)` — 네이티브 SQL 한 번 `post p JOIN member m ON m.id = p.author_id LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE' AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL`, WHERE는 004 `VisibilityFilter.forViewer(viewer, authorId)`의 `SqlCondition.sql()`(별칭 `p`·`m` 계약)과 `params()`를 그대로 쓰고 커서가 있으면 `AND (p.first_public_at, p.id) < (:t, :id)`만 덧붙인다. `ORDER BY p.first_public_at DESC, p.id DESC LIMIT :limit`. 선택 컬럼은 `p.id, p.title, p.excerpt, p.thumbnail_url, p.first_public_at, p.comment_count, p.like_count, m.handle, m.nickname, COALESCE(pi.thumb_storage_key, pi.storage_key)`만(본문 컬럼 금지). `:t`는 epoch 마이크로초 → `OffsetDateTime`(UTC), 읽을 때도 `OffsetDateTime`으로 받아 마이크로초를 잃지 않는다. `CursorKey`는 T007 `PostListCursor`의 것을 쓴다. 프로필 키는 001 `media/application/ImageUrlResolver`(specs/001 T040)로 주소화. 공개 글 수는 여기 만들지 않고 004 `PostQueryRepository.countListedByAuthor`(specs/004 T044)를 쓴다. member·image JOIN은 plan Complexity Tracking의 원칙 II 예외이므로 이 클래스 밖에서 그 테이블을 읽지 않는다(research R-06·R-07). (구현 메모: `findCards(viewer, authorId, after, limit)` + `PostCardRow`. 커서는 행 값 비교 `(p.first_public_at, p.id) < (:cursorAt, :cursorId)`. 004 T044가 아직 없어 블로그 공개 글 수 `countListed(viewer, authorId)`를 같은 `VisibilityFilter` 조건으로 이 클래스에 임시로 뒀다 — 004 T044가 생기면 그것으로 바꾼다)
- [X] T011 셸 렌더러 단위 테스트 `backend/src/test/java/com/team/blog/shared/web/shell/SpaShellRendererTest.java`를 먼저 작성한다: `index.html`의 `<!--app-head-->` 자리에 `LinkPreviewMeta`를 넣은 결과, 속성 값 이스케이프(`"`, `<`, `>`, `&`, `'`), 값이 null인 메타 태그는 생략, 출력에 본문 있는 `<script>` 태그가 생기지 않음(FR-037, FR-044 이스케이프).
- [X] T012 셸 렌더러 `backend/src/main/java/com/team/blog/shared/web/shell/LinkPreviewMeta.java`(record: `title, description, canonicalUrl, ogType, ogTitle, ogDescription, ogImage, publishedTime, modifiedTime, noindex`)와 `SpaShellRenderer.java`(빌드된 `index.html`을 클래스패스 `static/index.html`에서 한 번 읽어 캐시, `render(LinkPreviewMeta)` → HTML 문자열, 템플릿 엔진 없이 문자열 삽입, HTML 속성 이스케이프는 OWASP Encoder 또는 직접 구현)를 구현한다(research R-25). 공통 404 HTML은 만들지 않고 004 `NotFoundPageRenderer`를 쓴다. (구현 메모: `render(LinkPreviewMeta)` → HTML 문자열. 셸의 원래 `<title>`을 바꿔치기하고(메타 title이 있을 때만) 나머지는 `<!--app-head-->` 자리에 넣는다 — 자리 표시자가 없으면 `</head>` 앞. 이스케이프는 `&<>"'` 직접 구현(OWASP Encoder 의존성 추가 없음). 공통 문구 메타는 `LinkPreviewMeta.unavailable()`)
- [X] T013 [P] 프런트 API 타입·호출: `frontend/src/api/posts.ts`(002·004도 함수를 두는 공용 파일 — 있으면 함수만 추가)에 `listHomePosts(cursor?)`, `getPostDetail(postId)`, `frontend/src/api/members.ts`에 `getBlogHeader(handle)`, `listBlogPosts(handle, cursor?)`, 타입 파일 `frontend/src/api/types/reading.ts`에 contracts/openapi.yaml 스키마 그대로의 `PostCard`, `PostCardPage`, `BlogHeader`, `PostDetail`(`viewer`는 004 `api/types/viewerFlags.ts`(specs/004 T060)의 타입을 확장)을 만든다. 요청은 공용 `frontend/src/api/client.ts`(specs/001 T042 소유 — `ApiError`·CSRF 헤더; 404 → 공통 404 화면 전환 `onNotFound`는 specs/004 T024가 추가)를 쓰고 `size`는 보내지 않는다. (구현 메모: 004 T060 `api/types/viewerFlags.ts`가 아직 없어 `ViewerFlags`를 `api/types/reading.ts`에 같은 이름·뜻으로 두고 `PostDetailViewer`가 확장한다 — 004가 만들면 import만 바꾼다)
- [X] T014 [P] 카드 부품 컴포넌트 테스트 `frontend/src/components/__tests__/PostCard.test.tsx`·`RelativeTime.test.tsx`를 먼저 작성한다: 썸네일 `loading="lazy"`·`alt`=제목, `thumbnailUrl` null이면 같은 크기 빈 영역, 제목 1줄 말줄임 + `title` 속성에 전체 제목, 요약 3줄 + 비어도 3줄 높이(min-height), 댓글 수·좋아요 수 0도 표시, 카드 전체 링크 `/@{handle}/posts/{id}`와 작성자 링크 `/@{handle}` 둘 다 키보드 포커스 가능, `showAuthor=false`면 작성자 영역 없음, 프로필 사진 null이면 기본 아이콘. 상대 시간: 1분 미만 "방금 전", 1시간 미만 "N분 전", 24시간 미만 "N시간 전", 이후 `YYYY.MM.DD`(Asia/Seoul), `<time datetime>`은 UTC ISO-8601(FR-007~014, research R-29).
- [X] T015 [P] 카드 부품 구현 `frontend/src/components/RelativeTime.tsx`, `frontend/src/components/AuthorChip.tsx`(작은 프로필 사진 또는 001 `DefaultAvatar`(specs/001 T120 — 미완이면 임시 원형 아이콘) + 닉네임, 블로그 링크), `frontend/src/components/PostCard.tsx`(제목·요약은 React 텍스트 노드, `line-clamp: 1`/`3`, 썸네일 비율 예 `aspect-ratio: 16/9; object-fit: cover`, `showAuthor` prop), `frontend/src/components/PostCardGrid.tsx`(CSS grid, 같은 줄 카드 높이 동일, 375px~데스크톱 가로 스크롤 없음, 배치 개수 예 ≥1024px 3개·640~1023px 2개·<640px 1개)를 만든다. 색·글꼴·비율은 각자 정할 수 있게 CSS 변수로 둔다(FR-007·015, research R-09). (구현 메모: 001 T120 `DefaultAvatar`가 아직 없어 `frontend/src/components/DefaultAvatar.tsx`를 임시로 뒀다(001이 만들면 지운다). 색·비율·한 줄 카드 개수는 CSS 변수 `--card-*`(`--card-min-width`로 `auto-fill`)로 뺐다)
- [X] T016 [P] 이어 보기 훅 테스트 `frontend/src/features/post-list/__tests__/useCursorList.test.ts`를 먼저 작성한다: 첫 요청은 커서 없음, `loadMore`는 마지막 응답의 `nextCursor`를 그대로 보냄, 이어 붙일 때 이미 있는 `id`는 건너뜀, `nextCursor` null이면 `done=true`(FR-004·005, research R-10).
- [X] T017 [P] 이어 보기 훅 `frontend/src/features/post-list/useCursorList.ts`(`items`, `nextCursor`, `done`, `status: idle|loading|error`, `loadMore()`, `retry()` — 실패 시 같은 커서 유지)와 버튼 `frontend/src/components/LoadMoreButton.tsx`([더 보기], `done`이면 버튼 대신 "모든 글을 다 봤어요")를 구현한다. 로딩·실패 문구는 US6에서 완성한다. (구현 메모: `loadedOnce`를 더해 첫 응답 전/후를 구분한다(빈 목록 문구·첫 요청 재시도 판단))

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 홈에서 최신 공개 글 둘러보기 (Priority: P1) 🎯 MVP

**Goal**: 누구나 홈(`/`)에서 공개·발행 글을 최초 공개 일자 최신순으로 9개씩 카드로 보고 [더 보기]로 이어 본다. 보는 도중 변경이 있어도 중복·누락이 없다(C-READ-1).

**Independent Test**: quickstart Q-1~Q-4. 공개 글 20개와 비공개·임시·휴지통·숨김·탈퇴 신청 작성자 글을 섞어 두고 `GET /api/posts`를 커서로 끝까지 받아 공개 글만 9+9+2로 정렬되어 나오는지, 도중에 발행·삭제·비공개 전환을 해도 중복·누락이 없는지, 잘못된 커서가 400인지 확인한다.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T018 [P] [US1] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/HomeListIntegrationTest.java`: (US1 #1, Q-1) `GET /api/posts?size=50` → 9개, 공개·노출 글만, `firstPublicAt` 내림차순·같으면 `id` 큰 순, `nextCursor` 문자열, 응답 JSON에 본문 필드 없음, 헤더 `Cache-Control: private, no-cache`(research R-31). (US1 #2, Q-2, SC-004) 커서로 9+9+2, 세 번째 `nextCursor` null, 빈 `items` 응답 0건, 공개 글을 18개로 맞추면 두 번째 응답이 9개 + `null`. (US1 #3, Q-3, SC-003) 첫 페이지 커서를 받은 뒤 새 글 발행(`first_public_at`=now)·1페이지 글 휴지통·2페이지 글 `PRIVATE` 전환 → 이어 받은 ID 합집합에 중복 0, 비공개 전환 글 외 누락 0, 새 글은 없음, 커서 없이 새로 받으면 새 글이 맨 위. (Edge) 같은 마이크로초 두 글이 페이지 경계에 걸려도 둘 다 나옴. (SC-002) 요청 1번당 SQL 1번. (구현 메모: 호출 도우미 `discovery/support/ReadingApi`(목록·머리말·상세·화면 경로)를 더했다. 응답 헤더 문구는 Spring `CacheControl`이 `no-cache, private`로 쓰므로 004 `CacheControlPolicy.NO_CACHE`(`private, no-cache`) 문자열을 그대로 붙여 spec·004와 바이트가 같게 했다)
- [X] T019 [P] [US1] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/HomeCursorValidationIntegrationTest.java`(US1 #6, Q-4, FR-006): `cursor=abc%25%25`, `{"v":2,"l":"home","k":[1,1]}`, `{"v":1,"l":"home"}`(k 누락), `k` 타입 오류, 블로그 목록에서 받은 커서(`l`=`blog:kim755030`)를 홈에 보냄 → 모두 400, 본문 `{"code":"INVALID_CURSOR","message":…,"errors":[],"details":null}`. (구현 메모: 오류 메시지는 001 `CommonReasonCode.INVALID_CURSOR`의 "목록을 처음부터 다시 불러와 주세요"다(spec 본문의 예시 문구 대신 001 소유 문구를 따랐다). 블로그 목록이 필요한 세 경우는 T049까지 `@Disabled`였고 Phase 5에서 켰다. 빈 `cursor=`는 첫 페이지로 본다)
- [X] T020 [P] [US1] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/HomeListActorIntegrationTest.java`(06 V-8, 42 §5-1, 004 FR-009): 비회원·인증 전 회원·회원 B·작성자 A·관리자·정지 회원 세션으로 `GET /api/posts`를 끝까지 받은 id 순서가 모두 같고, 작성자 A에게도 자기 PRIVATE·DRAFT·숨김 글이 없으며 관리자에게도 숨김·PRIVATE 글이 없다. (HTTP 경로 매트릭스 교차 검증은 specs/004 T073이 005 완료 후 추가한다.) (구현 메모: 정지·인증 전 회원은 픽스처에 없어 `PostReadingFixture.suspendedMember()`·`unverifiedMember()`로 추가 회원을 만든다. 탈퇴 유예 작성자 본인 세션은 001 필터가 403으로 막아 이 테스트에 넣지 않았다(권한 매트릭스가 본다))
- [X] T021 [P] [US1] 화면 테스트 `frontend/src/pages/__tests__/HomePage.test.tsx`: 첫 진입 시 `listHomePosts()` 1번 → 카드 9개, [더 보기] 클릭 → 다음 9개가 아래에 이어 붙음, 마지막 응답 `nextCursor` null → 버튼 없음 + "모든 글을 다 봤어요", 중복 id 카드는 한 번만(US1 #2·#3).

### Implementation for User Story 1

- [X] T022 [US1] 서비스 `backend/src/main/java/com/team/blog/discovery/application/HomeQueryService.java`: `CursorPage<PostCardView> listHome(String cursor, Viewer viewer)` — 커서가 있으면 T007 `PostListCursor.decode(cursor, ListScope.home())`로 `CursorKey(t,id)`를 얻고, `PostCardQueryRepository.findCards(viewer, null, after, pageSize + 1)`로 호출, 결과가 `pageSize+1`개면 앞 `pageSize`개 + 마지막 카드의 `(firstPublicAt, id)`를 `PostListCursor.encode(ListScope.home(), …)`로 `nextCursor` 인코딩, 아니면 `nextCursor = null`. `pageSize`는 `ReadingProperties.list.pageSize`(FR-003·005·006, research R-04·R-05). (구현 메모: 홈·블로그가 함께 쓰는 `PostListService.page(scope, authorId, cursor, viewer)`와 `PostCardAssembler`(주소는 002 `PostUrls`, 사진은 001 `ImageUrlResolver`)로 나눴다. `HomeQueryService.listHome`은 그 위에 `ListScope.home()`만 얹는다)
- [X] T023 [US1] 컨트롤러 `backend/src/main/java/com/team/blog/discovery/web/HomePostController.java`: `GET /api/posts?cursor=` — `size` 파라미터는 받아도 무시, 현재 사용자는 세션에서만(`Viewer` 인자 해석기), 응답 헤더 `Cache-Control: private, no-cache`(Spring `CacheControl.noCache().cachePrivate()`), `InvalidCursorException`은 공통 `GlobalExceptionHandler`가 400 `INVALID_CURSOR`로 바꾼다(contracts `listHomePosts`). (구현 메모: `size`는 `String`으로 받아 무시한다(숫자가 아니어도 400이 되지 않게). 헤더는 004 `CacheControlPolicy.NO_CACHE` 문자열)
- [X] T024 [US1] 홈 화면 `frontend/src/pages/HomePage.tsx`: `useCursorList(listHomePosts)` + `PostCardGrid` + `PostCard(showAuthor)` + `LoadMoreButton`, 라우트 `/`를 `frontend/src/App.tsx`(또는 001 라우터 파일)에 등록. 끝이면 "모든 글을 다 봤어요"(FR-004). 빈 목록·로딩·실패 문구는 US6(T068~T071)에서 붙인다. (구현 메모: `App.tsx`의 `/` 자리 표시자를 `HomePage`로 바꿨다(공유 파일))

**Checkpoint**: User Story 1 is fully functional and testable independently (MVP)

---

## Phase 4: User Story 2 - 글 상세 읽기 (Priority: P1)

**Goal**: 독자가 `/@{handle}/posts/{postId}`로 들어와 제목·작성자·날짜·본문·태그·좋아요/조회수·작성자 카드·댓글 영역을 본다. 볼 수 없는 글은 없는 글과 똑같은 404다(C-READ-2).

**Independent Test**: quickstart Q-7·Q-8. 공개 글을 비회원으로 열어 모든 요소가 응답·화면에 있는지, 재발행 글에만 `editedAt`이 있는지, 상세 GET 후 `view_count`가 그대로인지, 없는 글·숫자 아닌 번호·남의 비공개·임시·휴지통·숨김·탈퇴 신청 작성자 글이 API와 페이지 모두 같은 404 본문인지 확인한다.

### Tests for User Story 2 ⚠️

- [X] T025 [P] [US2] 통합 테스트 `backend/src/test/java/com/team/blog/post/PostDetailIntegrationTest.java`(US2 #1·#3·#4, Q-7): 비회원이 공개 재발행 글 `GET /api/posts/{id}` → `title`, `contentHtml`(저장된 `content_html` 그대로), `displayedAt == firstPublicAt`, `editedAt` 있음, `tags`는 `post_tag.position` 순(008 미구현이면 빈 배열), `likeCount`·`viewCount`·`commentCount`, `author{handle,nickname,profileImageUrl,bio}`, `viewer{loggedIn:false,isAuthor:false,likedByMe:false,followingAuthor:false}`, `authorView` null, 응답에 `contentMd` 없음, `hasCodeBlock`은 코드 블록 글만 true. 공개 범위만 바꾼 글은 `editedAt` null. 상세 GET 3번 전후 DB `view_count` 불변(SC-007). 헤더 `Cache-Control: private, no-cache`. 비작성자 회원은 SQL 4번 이하, 비회원은 2번 이하(글+작성자, 태그)(research R-15). (구현 메모: 호출은 `discovery/support/ReadingApi.detail`을 쓴다. SQL 수 상한은 비회원 2번(글+작성자 1 + 태그 1), 회원 4번(001 탈퇴 게이트의 계정 조회 1 + 글+작성자 1 + 태그 1 + 좋아요·팔로우 포트 — 임시 구현은 SQL을 쓰지 않아 실제로는 3번)으로 확인한다)
- [X] T026 [P] [US2] 통합 테스트 `backend/src/test/java/com/team/blog/post/PostDetailFallbackIntegrationTest.java`(research R-30, 원칙 V): 태그·좋아요 여부·팔로우 여부 조회 Bean이 예외를 던지도록 `@MockBean`으로 바꿔도 `GET /api/posts/{id}`는 200, `tags=[]`, `likedByMe=false`, `followingAuthor=false`이고 경고 로그가 남는다. 글+작성자 조회가 실패하면 500(상세 실패). (구현 메모: `@MockBean`은 폐기돼 001·004와 같이 `@MockitoBean`(`org.springframework.test.context.bean.override.mockito`)을 쓴다. "글+작성자 조회 실패 → 500"은 `PostQueryRepository`를 목으로 바꾸면 같은 컨텍스트의 다른 테스트가 모두 깨지므로 `PostDetailQueryFailureIntegrationTest`로 떼어 냈다)
- [X] T027 [P] [US2] 권한 매트릭스 실행기 등록 `backend/src/test/java/com/team/blog/discovery/support/ReadingPermissionActions.java`(004 하네스 specs/004 T022의 `PermissionAction` 구현을 테스트 Bean으로 등록): `read-detail-api` = `GET /api/posts/{postId}`, `read-detail-page` = `GET /@{작성자 handle}/posts/{postId}`. `backend/src/test/resources/permission/post-read.csv`(specs/004 T023 소유)에 이 두 행동의 행(owner `005`)이 없으면 data-model §3-2 표대로 추가한다 — 7개 글 상태 + `NONEXISTENT` × 비회원·인증 전·회원·작성자·관리자, 작성자 DRAFT는 API 200·페이지 302, 작성자 휴지통 404, 관리자 남의 PRIVATE 404. (구현 메모: `post-read.csv`에 owner `005` 84행을 더했다(8개 대상 상태 × 비회원·인증 전·회원·작성자·관리자 + 정지·탈퇴 신청 행위자 × 공개 글). **화면 404 행의 `expectedCode`는 비워 둔다** — 404 화면 본문은 공통 HTML이라 오류 `code`가 없다(같은 404임은 T028이 본문 바이트로 확인). 004 하네스가 `/api/**`만 막는 001 탈퇴 게이트 때문에 `AUTHOR × AUTHOR_WITHDRAWN`·`WITHDRAWN × PUBLISHED_PUBLIC`은 API 403 `ACCOUNT_WITHDRAWN` / 화면 200이다. `AUTHOR × DRAFT` 화면은 이 Phase에서 200이고 **US4 T057이 302로 바꿔야 한다** — 그때 이 행을 함께 고친다)
- [X] T028 [US2] 통합 테스트 `backend/src/test/java/com/team/blog/post/PostDetailPermissionMatrixTest.java`(US2 #2, Q-8, SC-009): 004 `AbstractPermissionMatrixIT`를 상속해 `post-read.csv`의 owner `005` 행을 실행하고(T027 필요), 404 기대 행 전부에 대해 API 본문이 바이트 단위로 같고(`{"code":"NOT_FOUND","message":"볼 수 없는 페이지예요","errors":[],"details":null}`), 페이지 본문이 004 `NotFoundPageRenderer` 출력과 바이트 단위로 같으며(`Date` 헤더 제외), `postId=abc`도 같은 404이고, 모든 404에 `Cache-Control: private, no-store`가 붙는지 확인한다(research R-17). (구현 메모: 파일 이름은 004·002의 기존 관례를 따라 `backend/src/test/java/com/team/blog/post/integration/permission/PostDetailPermissionMatrixIT.java`로 뒀다 — `*Test`는 surefire가, `*IT`는 failsafe가 돌리고 002 `PostAuthoringPermissionMatrixIT`가 같은 자리에 있다. 002처럼 `@MethodSource`로 owner `005` 행만 고른다(그래야 004 행을 두 번 돌리지 않는다))
- [X] T029 [P] [US2] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/PostPageShellReaderIntegrationTest.java`(FR-026 ①②③⑥, FR-043): `GET /@kim755030/posts/{공개 글}` → 200 `text/html`, `<div id="root">` 포함, 헤더 `private, no-cache`, 조회수 불변. `GET /@Kim755030/posts/{id}?comment=120` → 301 `Location: /@kim755030/posts/{id}?comment=120`(쿼리 유지, research R-32). `GET /@kim755030/posts/abc` → 404 공통 페이지. 응답 HTML에 본문 있는 `<script>` 태그 없음(FR-037). (구현 메모: 작성자가 보는 자기 비공개 글 200, 없는 글 번호(99999999)가 SPA 대체 경로에 먹히지 않고 404인지, 대문자 301이 볼 수 없는 글에도 먼저 적용되는지(①이 ②③보다 먼저)를 함께 본다)
- [X] T030 [P] [US2] 화면 테스트 `frontend/src/pages/__tests__/PostDetailPage.test.tsx`(US2 #1·#3·#5·#6): 제목은 유일한 `h1`이며 `<b>` 같은 문자열이 글자로 보임, 작성자 영역 `닉네임 @handle` 링크 `/@handle`, 날짜 `RelativeTime`, `editedAt` 있으면 "수정됨 · 10월 3일"(Asia/Seoul), 태그 `#이름` 링크 `/tags/{encodeURIComponent(name)}` 입력 순서, 반응 줄 좋아요 수 맨 앞·"조회 1,234"·12,345 → "조회 1.2만"·조회수 안내 문구, 작성자 카드 소개 줄바꿈 유지(`white-space: pre-line`), 댓글 머리말에 `commentCount`, URL `?comment=55`가 댓글 영역 prop으로 넘어감, API 404면 004 `NotFoundPage` 표시, 응답 `canonicalPath`가 현재 경로와 다르면 `navigate(canonicalPath + search, {replace:true})`. (구현 메모: 라우트를 `/:handle/posts/:postId`로 두고 `@`를 화면이 떼어 낸다 — react-router 7은 한 구간의 일부만 파라미터로 받지 못한다(`/@:handle` 불가, `compilePath`가 `/:`만 파라미터로 읽는다). `@`로 시작하지 않는 주소는 공통 404로 본다)
- [X] T031 [P] [US2] 훅 테스트 `frontend/src/features/post-detail/__tests__/useViewBeacon.test.ts`(FR-041, US2 #4, research R-16·R-28): 문서가 보이는 상태로 1초 지나면 `POST /api/posts/{id}/views`(`keepalive: true`, CSRF 헤더는 001 규약 `X-XSRF-TOKEN`) 1번, 1초 전에 `visibilitychange`로 숨으면 요청 없음, 다시 보이면 남은 시간부터, 실패해도 예외·재시도 없음, `enabled=false`면 아무 요청 없음. (구현 메모: 훅 이름은 `useViewBeacon({postId, enabled})`, 지연 시간은 `VIEW_BEACON_DELAY_MS`(1000)로 내보내 테스트가 같은 값을 쓴다. 숨은 상태로 들어온 경우도 함께 본다)

### Implementation for User Story 2

- [X] T032 [P] [US2] 응답 형태 `backend/src/main/java/com/team/blog/post/application/PostDetailView.java`(record, contracts `PostDetail` 필드 그대로: `id, status, editorPath, canonicalPath, visibility, title, contentHtml, hasCodeBlock, displayedAt, firstPublicAt, publishedAt, editedAt, tags, likeCount, viewCount, commentCount, author{handle,nickname,profileImageUrl,bio}, viewer{loggedIn,isAuthor,likedByMe,followingAuthor,emailVerified,isAdmin}, authorView`). `viewer.emailVerified`·`isAdmin`은 004 research R-29(`PostActions` 버튼 표시)와 맞추기 위해 더한다 — contracts `PostDetail.viewer`에 반영됨(Tier A 교차 분석 2026-10-07). null 필드는 JSON에서 `null`로 둔다. (구현 메모: `viewer.isAuthor`·`isAdmin`은 `@JsonProperty`로 JSON 이름을 못 박았다 — record 접근자 `isAuthor()`가 `author`로 줄어들 수 있다. `authorView`는 US4가 채울 nested record `PostDetailView.AuthorView`(hasDraft·draftSavedAt·hidden·hiddenReason)로 미리 두고 이 Phase에서는 항상 `null`)
- [X] T033 [P] [US2] 부가 정보 포트와 임시 구현(Tier B·C 미구현 대비, research R-30): `backend/src/main/java/com/team/blog/post/application/port/PostTagNamesQuery.java`(`List<String> namesInOrder(long postId)`, 임시 `EmptyPostTagNamesQuery` = 빈 목록, 008에서 `TagService.findNamesInOrder`로 교체), `PostLikeStatusQuery.java`(`boolean isLikedBy(long postId, long memberId)`, 임시 false, 009에서 교체), `AuthorFollowStatusQuery.java`(`boolean isFollowing(long followerId, long followeeId)`, 임시 false, 010에서 교체). 임시 구현은 `@ConditionalOnMissingBean`으로 등록하고 클래스 주석에 교체할 spec을 적는다. (구현 메모: 태그 임시 구현은 빈 목록이 아니라 002 `TagService.tagNamesOf`(`post_tag.position` 순서)를 쓴다 — 002가 이미 발행 때 태그를 저장하므로 빈 목록을 주면 US2 #1이 거짓이 된다. 008이 자기 Bean을 등록하면 `@ConditionalOnMissingBean`으로 물러난다. 세 Bean은 `post/config/PostReadingPorts.java`에 모았다)
- [X] T034 [US2] 상세 행 조회: 004 `backend/src/main/java/com/team/blog/post/infra/PostQueryRepository.java`(specs/004 T014 — `findPostView`는 판정 필드만 줌)에 `Optional<PostDetailRow> findDetailRow(long postId)`를 추가하고 `PostDetailRow`(같은 패키지 record, `toPostView()`로 004 `PostView` 생성)를 만든다: 쿼리 1번 `post p JOIN member m LEFT JOIN image pi(프로필, T010과 같은 조건)` — `p.id, author_id, title, content_html, status, visibility, view_count, like_count, comment_count, published_at, first_public_at, edited_at, deleted_at, hidden_at, hidden_reason, thumbnail_url, excerpt, m.handle, m.nickname, m.bio, m.withdrawn_at, COALESCE(pi.thumb_storage_key, pi.storage_key)`. 휴지통 행도 읽는다(판정은 `PostAccessPolicy`가). `content_md`는 선택하지 않는다(40 §6, data-model §1). (구현 메모: 004 소유 파일 `PostQueryRepository`에 `findDetailRow`와 `toDetailRow`만 더했다(공유 파일 — 병합 때 확인). 행은 같은 패키지 `PostDetailRow`이고 `hidden_reason`·`thumbnail_url`·`excerpt`는 US4·US5가 쓸 자리로 함께 읽는다)
- [X] T035 [US2] 상세 조립 `backend/src/main/java/com/team/blog/post/application/PostDetailAssembler.java`: `displayedAt` = `visibility == PUBLIC ? firstPublicAt : publishedAt`(research R-13), `editedAt`은 저장값 그대로, `hasCodeBlock` = `contentHtml`에 `<pre><code` 포함, `canonicalPath` = `/@{author.handle}/posts/{id}`, 프로필 주소는 001 `ImageUrlResolver`(specs/001 T040), `viewer` 플래그(`Viewer`에서 `loggedIn`, `isAuthor = viewer.isAuthorOf(authorId)`, `emailVerified`, `isAdmin`), 비회원·작성자면 좋아요·팔로우 포트를 호출하지 않고 false, 포트 예외는 잡아서 기본값 + 경고 로그. `authorView`는 이 Phase에서 null(US4에서 채움). (구현 메모: 포트 예외는 `guard(...)`로 잡아 태그는 빈 목록, 좋아요·팔로우는 false로 두고 경고 로그를 남긴다. 비회원·작성자에게는 좋아요·팔로우 포트를 아예 부르지 않는다)
- [X] T036 [US2] 상세 서비스 `backend/src/main/java/com/team/blog/post/application/PostQueryService.java`에 `getDetail(String rawPostId, Viewer viewer)`: ② `rawPostId`가 `^[0-9]+$`이 아니거나 long 범위를 넘으면 `PostNotFoundException`(004) → ③ `findDetailRow` 없음 또는 `PostAccessPolicy.canRead(row.toPostView(), viewer)`(specs/004 T015)가 false면 같은 `PostNotFoundException`(이유 구분 없음) → `PostDetailAssembler`. `PostReadService.requireReadable`을 따로 부르지 않아 판정과 데이터 조회가 쿼리 1번이다. 이 메서드는 `view_count`를 바꾸지 않는다(FR-026, FR-041, research R-11·R-12·R-22). 작성자 임시글 분기는 US4(T056)에서 추가한다. (구현 메모: `requireReadableRow(rawPostId, viewer)`를 따로 내보내 컨트롤러가 헤더용 상태를 보고 같은 행으로 응답을 만들게 했다(조회 1번). `detailOf(row, viewer)`가 조립만 한다. T038 화면 경로도 같은 메서드를 쓴다)
- [X] T037 [US2] 컨트롤러 `backend/src/main/java/com/team/blog/post/web/PostDetailController.java`: `GET /api/posts/{postId}`(경로 변수는 `String` — 숫자가 아니어도 400이 아니라 404), `Viewer`는 004 `CurrentViewerResolver`로만 받는다. 응답 헤더는 004 `CacheControlPolicy.forPost(status, visibility, hidden)`(specs/004 T019, `hidden = hidden_at != null`; 또는 오버로드 `forPost(PostView)`): `PUBLISHED`·`PUBLIC`·숨김 아님이면 `private, no-cache`, 작성자가 보는 숨김 PUBLIC 글을 포함한 그 밖은 `private, no-store`. 404는 001 `GlobalExceptionHandler` + 004 연결이 `private, no-store`로 낸다(FR-042, research R-17). (구현 메모: 헤더는 `CacheControlPolicy.forPost(status, visibility, hidden)` 문자열을 그대로 붙인다 — Spring `CacheControl`은 `no-cache, private` 순서로 써서 004·spec과 바이트가 달라진다)
- [X] T038 [US2] 첫 응답 컨트롤러 `backend/src/main/java/com/team/blog/discovery/web/PageShellController.java`에 `GET /@{handle}/posts/{postId}` 추가(이 Phase는 ①②③⑥): ① `handle`에 대문자가 있으면 `301 Location: /@{소문자}/posts/{postId}` + 원래 쿼리 문자열 유지 → ② 숫자 아님 → 404 → ③ `PostQueryService`와 같은 조회·`canRead`로 불가면 404(004 `NotFoundPageRenderer` HTML, `private, no-store`) → ⑥ 200 `SpaShellRenderer.render(기본 메타)` + `CacheControlPolicy`. 이 경로는 조회수를 바꾸지 않는다. ④(handle 불일치 301)는 US5(T064), ⑤(작성자 임시글 302)는 US4(T057)에서 추가한다. `/@{handle}` 경로가 SPA 정적 대체 경로보다 우선하도록 매핑 순서를 확인한다(research R-11·R-25). (구현 메모: `@RestController`로 두고 `ResponseEntity<byte[]>`를 돌려준다(404는 004 `NotFoundPageRenderer`의 바이트 그대로, 200은 셸 HTML `text/html;charset=UTF-8`). 매핑 `"/@{handle}/posts/{postId}"`가 001 `SpaForwardingController`의 일반 화면 경로보다 구체적이어서 먼저 선택되는 것을 T029가 확인한다. ⑥의 메타는 이 Phase에서 `LinkPreviewMeta.empty()`이고 US5 T063·T064가 글 메타로 채운다)
- [X] T039 [P] [US2] 상세 화면 부품 `frontend/src/components/TagList.tsx`(`#이름`, 링크 `/tags/{encodeURIComponent(name)}`, 순서 유지), `frontend/src/components/ReactionBar.tsx`(좋아요 수 맨 앞 → 004 `PostActions`가 고른 [좋아요] → "조회 1,234"/"1.2만" + 안내 문구 → [신고]; 버튼 클릭은 004 `useAuthGate`, 실제 좋아요·신고 API는 009·014가 연결할 자리), `frontend/src/components/AuthorCard.tsx`(프로필 사진/기본 아이콘·닉네임·`@handle`·소개(텍스트, 줄바꿈 유지)·[블로그 가기]·[팔로우] 자리 — 010 미구현이면 [팔로우]는 비회원에게 로그인 안내만 하는 004 `useAuthGate` 버튼, 내 글이면 없음)(FR-029·032·033·034). (구현 메모: [좋아요]·[신고]·[팔로우] 버튼 자체는 004 `PostActions`·`useAuthGate`(아직 없음)·009·014 소유라 `ReactionBar`의 `likeButton`·`reportButton`, `AuthorCard`의 `followButton` prop 자리만 뒀다. 조회수 안내 문구는 31 W-8 "같은 사람은 하루에 한 번만 세요"를 `title`로 붙이고 `VIEW_COUNT_NOTICE`로 내보낸다. 1만 이상은 `1.2만`)
- [X] T040 [P] [US2] 조회 기록 훅 `frontend/src/features/post-detail/useViewBeacon.ts`(T031 기준: `document.visibilityState` + 1초 타이머, `fetch('/api/posts/{id}/views', {method:'POST', keepalive:true})` + 001 CSRF 헤더 `X-XSRF-TOKEN`, 실패 무시, 재시도 없음, `enabled` 인자)와 코드 강조 지연 로드 `frontend/src/features/post-detail/loadHighlighter.ts`(`hasCodeBlock`일 때만 002의 `frontend/src/features/markdown/highlightCode.ts`(specs/002 T062)를 동적 `import()`해 본문 컨테이너에 적용 — 강조 규칙을 새로 만들지 않음, 같은 출처 번들)를 만든다(FR-031·041, research R-14·R-28). `/api/posts/{id}/views`는 009 소유 — 없으면 404를 무시한다. (구현 메모: 기록 요청 자체는 `api/posts.ts`의 `recordPostView`(CSRF 쿠키→헤더 규약을 한곳에 두기 위해)로 보내고 훅은 가시성·1초만 맡는다. `loadHighlighter`는 002 `highlightCode`를 동적 `import()`한다 — 002 `PreviewPane`이 같은 모듈을 정적으로 쓰고 있어 **번들은 쪼개지지 않는다**(vite `INEFFECTIVE_DYNAMIC_IMPORT` 경고). 코드 블록이 없는 글에서 강조를 실행하지 않는 효과만 남는다)
- [X] T041 [P] [US2] 댓글·GIF 연결 자리: `frontend/src/features/post-detail/CommentSectionSlot.tsx`(머리말 "댓글 {commentCount}"; 007의 댓글 컴포넌트가 있으면 `postId`·`aroundCommentId`(URL `?comment=`)를 넘겨 상세 API와 동시에 `GET /api/posts/{postId}/comments[?around=]`를 부르게 하고, 없으면 머리말만 — research R-33, FR-035, US2 #5·#6), `frontend/src/features/post-detail/gifPlayer.ts`(003 FR-039 GIF 재생 모듈을 본문 렌더 뒤 호출하는 자리, 003 전까지 no-op 임시 구현 — "003에서 교체" 주석)(FR-036·037). (구현 메모: `CommentSectionSlot`은 머리말 "댓글 {n}"과 `data-post-id`·`data-around-comment`만 둔다(007이 컴포넌트를 넣을 자리). `gifPlayer.ts`는 `playableGifs(container)` no-op — 003에서 교체)
- [X] T042 [US2] 상세 화면 `frontend/src/pages/PostDetailPage.tsx`, 라우트 `/@:handle/posts/:postId`: `getPostDetail(postId)` 호출(404 → 004 `NotFoundPage`), `canonicalPath`가 현재 경로와 다르면 쿼리 유지한 `replace` 이동, 제목 `h1`(텍스트 노드), 작성자 영역 `AuthorChip`(`닉네임 @handle`), 날짜 `RelativeTime(displayedAt)` + `editedAt` 있으면 "수정됨 · M월 D일", 본문은 서버가 정화한 `contentHtml`만 `dangerouslySetInnerHTML`로 넣는다(다른 값에는 절대 쓰지 않음), 그 뒤 `loadHighlighter`·`gifPlayer`, `TagList`, `ReactionBar`, `AuthorCard`, `CommentSectionSlot`, `useViewBeacon({enabled: !viewer.isAuthor})`. 화면 조회수는 응답의 `viewCount` 그대로(FR-028~035·040·041). (구현 메모: 라우트는 T030 메모대로 `/:handle/posts/:postId`(공유 파일 `App.tsx`). 본문만 `dangerouslySetInnerHTML`을 쓰고 제목·소개·닉네임은 텍스트 노드다. `canonicalPath`가 다르면 쿼리를 유지해 `navigate(..., {replace:true})`. 002 E2E `frontend/e2e/xss.spec.ts`의 `test.fixme('005 글 상세 화면에서도 알림창 0번')`을 실제 시험으로 바꿨다 — 32개 공격 문자열을 발행하고 상세 주소를 열어 알림창 0번·`article` 안 `<script>` 0개를 확인한다(통과))

**Checkpoint**: User Stories 1 AND 2 both work independently

---

## Phase 5: User Story 3 - 개인 블로그 페이지 (Priority: P1)

**Goal**: `/@{handle}`에서 그 사람의 프로필 머리말과 공개 글을 홈과 같은 카드·정렬·9개·[더 보기]로 본다. 본인이 봐도 비공개·숨긴 글은 없다(C-BLOG-1).

**Independent Test**: quickstart Q-5·Q-6. 공개 글 12개·비공개 3개·숨김 1개인 회원 A의 블로그를 본인·남으로 열어 머리말·`publicPostCount=12`, 카드 9개 + 다음 3개, 비공개·숨김 미노출, 없는/탈퇴 신청 주소 404, 대문자 주소 301을 확인한다.

### Tests for User Story 3 ⚠️

- [X] T043 [P] [US3] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/BlogPageIntegrationTest.java`(US3 #1~#5, Q-5, SC-008): `GET /api/members/kim755030` 비회원·회원 B·작성자 A 모두 `publicPostCount=12`, `isMe`는 A만 true, `profileImageUrl`은 썸네일 키 주소, `bio` 그대로. `GET /api/members/kim755030/posts`를 A 세션으로 → 9개 + `nextCursor`, 다음 3개 + `null`, PRIVATE·DRAFT·휴지통·숨김 글 0건, 정렬 `first_public_at DESC, id DESC`. 회원 D → `publicPostCount=0`, `items=[]`, `nextCursor=null`. 블로그 커서를 홈에, 홈 커서를 블로그에, `blog:na_ms` 커서를 `kim755030` 블로그에 보내면 400 `INVALID_CURSOR`. 목록 요청 1번당 SQL은 블로그 주인 조회 1번 + 카드 1번. 헤더 `private, no-cache`. (구현 메모: 회원 B 블로그는 공개 글이 8개(한 페이지)라 커서가 없다 — "다른 사람 블로그 커서" 확인은 A 블로그의 커서를 B 블로그에 보내서 한다. 머리말 SQL은 주인 1 + 글 수 1 + 사진 1번, 목록은 주인 1 + 카드 1번)
- [X] T044 [P] [US3] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/BlogNotFoundIntegrationTest.java`(US3 #4, Q-6, FR-022): API `GET /api/members/nobody_here`, 회원 C(탈퇴 신청), 익명 처리 회원(`deleted_at` 있음) → 404 `NOT_FOUND`(본문 동일), `GET /api/members/Kim755030` → 404(API는 리다이렉트 안 함, research R-23), 목록 `GET /api/members/nobody_here/posts` → 404. `SUSPENDED` 회원 블로그는 200(42 P-7). (구현 메모: 익명 처리 회원은 51 `ck_member_deleted`·`ck_member_withdrawn` 때문에 `status='WITHDRAWN'`·`withdrawn_at`·`nickname=NULL`을 함께 넣어야 만들 수 있다. 404 본문은 001 공통 본문과 바이트가 같고 헤더는 `private, no-store`)
- [X] T045 [P] [US3] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/BlogPageShellIntegrationTest.java`(FR-022·043, research R-27): `GET /@Kim755030?x=1` → 301 `/@kim755030?x=1`, `GET /@nobody_here`·회원 C 주소·익명 처리 회원 주소 → 404 + 공통 404 HTML(004 `NotFoundPageRenderer` 출력과 바이트 단위로 같음) + `private, no-store`, `GET /@kim755030` → 200, `<title>김민서 (@kim755030)</title>`, description = 소개 앞 160자(없으면 태그 없음), canonical `{base-url}/@kim755030`, `og:type=profile`, `og:image` = 프로필 사진 원본(`storage_key`, 없으면 기본 이미지), 값 이스케이프, 인라인 스크립트 없음. (구현 메모: 닉네임은 51 `ck_member_nickname`이 한글·영문·숫자 2~10자만 허용해 `<b>` 같은 값을 넣을 수 없다 — 이스케이프 확인은 소개(bio)로 한다. description은 소개의 줄바꿈·연속 공백을 한 칸으로 모은 뒤 앞 160자다(메타 속성 값에 줄바꿈을 넣지 않으려고). 소개가 없으면 `description`·`og:description` 태그를 만들지 않는다)
- [X] T046 [P] [US3] 화면 테스트 `frontend/src/pages/__tests__/BlogPage.test.tsx`(US3 #1·#2·#5): 머리말(프로필 사진/기본 아이콘, 닉네임, `@handle`, 소개 텍스트 줄바꿈 유지, "공개 글 N"), 카드에 작성자 영역 없음, [더 보기] 동작, 글 0개일 때 `isMe=false`면 "아직 공개한 글이 없어요", `isMe=true`면 "첫 글을 써 보세요" + [글쓰기](002 새 글 진입), 404면 `NotFoundPage`. (구현 메모: 라우트는 `/:handle`이고 `@`는 화면이 떼어 낸다(T030 메모와 같은 이유))

### Implementation for User Story 3

- [X] T047 [P] [US3] 응답 형태 `backend/src/main/java/com/team/blog/discovery/application/BlogHeaderView.java`(record `handle, nickname, bio, profileImageUrl, publicPostCount, isMe` — contracts `BlogHeader`; 010이 필드를 추가할 수 있게 둔다). (구현 메모: `isMe`는 `@JsonProperty`로 JSON 이름을 못 박았다(record 접근자 `isMe()`가 `me`로 줄어들 수 있다))
- [X] T048 [US3] 서비스 `backend/src/main/java/com/team/blog/discovery/application/BlogQueryService.java`: `getHeader(String handle, Viewer viewer)` — 001 `MemberQueryService.findReadableBlogOwner(handle)`(없음·`WITHDRAWN`·익명 처리면 empty → `NotFoundException`; 대문자 handle은 그대로 조회해 없음 처리), `publicPostCount` = 004 `PostQueryRepository.countListedByAuthor(viewer, ownerId)`(specs/004 T044, 작성자 본인도 같은 수), `isMe` = `viewer.isAuthorOf(ownerId)`, 프로필 사진은 001 `media/application/ProfileImageQuery.currentKeys(ownerId).display()`(썸네일, 없으면 원본; specs/001 T040, `uq_image_profile_current` 조건 1번, 003이 소유를 넘겨받음)의 키를 001 `ImageUrlResolver`(specs/001 T040)로 주소화 — 머리말 SQL은 주인 1 + 사진 1 + 글 수 1번. `listPosts(String handle, String cursor, Viewer viewer)` — 주인 확인 후 `PostListCursor.decode(cursor, ListScope.blog(owner.handle()))`, `PostCardQueryRepository.findCards(viewer, ownerId, after, pageSize + 1)`, `nextCursor`는 `PostListCursor.encode(ListScope.blog(owner.handle()), …)`. 작성자 본인 예외 없음(06 V-8, FR-019~022, research R-23·R-24). (구현 메모: 004 T044 `countListedByAuthor`가 아직 없어 T010에 임시로 둔 `PostCardQueryRepository.countListed(viewer, authorId)`를 쓴다 — 004가 만들면 이 호출만 바꾼다. 주인을 두 번 찾지 않게 `requireOwner(handle)`를 내보내 화면 경로(T050)도 같은 메서드를 쓴다)
- [X] T049 [US3] 컨트롤러 `backend/src/main/java/com/team/blog/discovery/web/BlogController.java`: `GET /api/members/{handle}`, `GET /api/members/{handle}/posts?cursor=`(`size` 무시), 두 응답 모두 `Cache-Control: private, no-cache`(research R-31). 001의 `GET /api/members/{handle}/friend` 등 같은 접두어 경로와 충돌하지 않는지 확인한다. (구현 메모: `size`는 `String`으로 받아 무시한다. 001이 뒤에 더할 `GET /api/members/{handle}/friend`는 더 구체적인 경로라 겹치지 않는다)
- [X] T050 [US3] `PageShellController`에 `GET /@{handle}` 추가(`backend/src/main/java/com/team/blog/discovery/web/PageShellController.java`, T038 다음): ① 대문자 → 301 소문자(쿼리 유지) ② `findReadableBlogOwner` empty → 004 `NotFoundPageRenderer` 404 ③ 200 + `SpaShellRenderer` 블로그 메타(`<title>{닉네임} (@{handle})</title>`, description = 소개 앞 `seo.description-length`자, canonical `{site.base-url}/@{handle}`, `og:type=profile`, `og:image` = 프로필 사진 원본 키 주소 또는 `seo.default-og-image-url`), `private, no-cache`(research R-27). handle 정규화는 001 `MemberQueryService.normalizeHandle`(specs/001 T039)을 쓰고, 프로필 원본 키는 member·image 테이블을 직접 읽지 말고 001 `ProfileImageQuery.currentKeys(ownerId).original()` + `ImageUrlResolver`(둘 다 specs/001 T040)로 만든다. 001 US3 #7(블로그 주소 대문자 301·없는 주소 404)의 확인은 T045가 맡는다. (구현 메모: `PageShellController`에 `GET /@{handle}`을 더했다. og:image는 `ProfileImageQuery.currentKeys(...).original()`(없으면 `blog.seo.default-og-image-url`), canonical은 `blog.site.base-url` + `/@handle`. 001 `SecurityFoundationIntegrationTest`의 "화면 경로는 index.html로 넘긴다" 목록에서 `/@kim755030`·`/@kim755030/posts/12`를 뺐다 — 이제 005가 더 구체적인 매핑으로 가져갔고 그 경로는 T029·T045가 확인한다(공유 파일))
- [X] T051 [US3] 블로그 화면 `frontend/src/pages/BlogPage.tsx`, 라우트 `/@:handle`: `getBlogHeader`와 `listBlogPosts`를 동시에 호출, 머리말(프로필 사진 또는 001 `DefaultAvatar`(specs/001 T120), 닉네임, `@handle`, 소개 `pre-line` 텍스트, 공개 글 수), 로그인했고 `isMe=false`면 001 `FriendButton`(specs/001 T132)·`LastActiveBadge`(specs/001 T142, 001 `GET /api/members/{handle}/friend`)를 머리말에 넣는다(001 미완이면 빈 자리), `PostCardGrid` + `PostCard(showAuthor=false)` + `LoadMoreButton`, 빈 상태 문구(FR-023), 404 → `NotFoundPage`. 개인 확장(카테고리 사이드바·시리즈 탭)용 빈 슬롯 prop만 둔다(spec Assumptions). (구현 메모: 001 `FriendButton`(T132)·`LastActiveBadge`(T142)와 개인 확장(카테고리·시리즈)은 아직 없어 `headerSlot`·`sidebarSlot` prop 자리만 뒀다. 빈 상태 문구는 `EMPTY_BLOG_TEXT`·`EMPTY_MY_BLOG_TEXT`로 내보낸다)

**Checkpoint**: All P1 stories (US1·US2·US3) independently functional

---

## Phase 6: User Story 4 - 작성자가 자기 글을 볼 때 (Priority: P2)

**Goal**: 작성자는 자기 글 상세에서 독자와 같은 모습 + 상태 표시·[수정]·[공개 범위 ▾]·[삭제]를 보고, 임시글 주소는 에디터로, 수정 중인 글은 마지막 발행본 + "수정 중" 안내를 본다(40 R-4·R-5, §2-1).

**Independent Test**: quickstart Q-11. 회원 A로 공개·비공개·수정 중·숨김·임시·휴지통 글의 상세를 차례로 열어 API 응답(`viewer.isAuthor`, `authorView`, `displayedAt`, 캐시 헤더)과 화면 표시·리다이렉트·조회 기록 미실행을 확인한다.

### Tests for User Story 4 ⚠️

- [X] T052 [P] [US4] 통합 테스트 `backend/src/test/java/com/team/blog/post/PostDetailAuthorViewIntegrationTest.java`(US4 #1~#6, FR-038~040): 작성자 A가 `GET /api/posts/{id}` — 공개 글: `viewer.isAuthor=true`, `likedByMe=false`·`followingAuthor=false`이고 좋아요·팔로우 포트 미호출, `authorView{hasDraft:false,hidden:false}`. 수정 중 글: `contentHtml`이 `post.content_html`(마지막 발행본)과 같고 `post_draft` 내용이 아님, `authorView.hasDraft=true`, `draftSavedAt` = `post_draft.updated_at`; 같은 글을 회원 B가 보면 같은 `contentHtml`이고 `authorView` null(FR-040). PRIVATE 글: `displayedAt == publishedAt`, `Cache-Control: private, no-store`. 숨김 글: 200, `authorView.hidden=true`, `hiddenReason` 저장값. DRAFT: 200 `{id, status:"DRAFT", editorPath:"/write/{id}"}`만, `contentHtml` 없음. 휴지통 글: 404(독자와 같은 본문). 작성자 SQL 5번 이하. (구현 메모: 좋아요·팔로우 포트는 `@MockitoBean`으로 바꿔 작성자에게 한 번도 부르지 않는지 확인한다(기본 구현이 람다 Bean이라 spy가 안 된다). 작업본 조회 실패는 002 `PostDraftQueryService`를 `@MockitoSpyBean`으로 감싸 흉내 낸다. DRAFT 응답은 JSON 키가 `id`·`status`·`editorPath` 셋뿐인지 본다. 작성자 SQL은 계정 상태 1 + 글+작성자 1 + 태그 1 + 작업본 1 = 4번)
- [X] T053 [P] [US4] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/AuthorPageShellIntegrationTest.java`(US4 #2·#5, US5 #5, Q-9 셋째 줄, FR-045): A 세션으로 `GET /@kim755030/posts/{임시글}` → 302 `Location: /write/{id}`; 회원 B·비회원은 같은 주소 404. A의 PRIVATE 글 페이지 → 200, 공통 문구 메타 + `noindex`, `private, no-store`. A의 숨김 글 페이지 → 200, 공통 문구 + `noindex`, `private, no-store`. A의 휴지통 글 → 404. (구현 메모: 작성자 임시글 302에도 `Cache-Control: private, no-store`를 붙인다. 작성자가 보는 비공개·숨김 글 응답에 제목·요약·canonical이 없는지도 본다)
- [X] T054 [P] [US4] 화면 테스트 `frontend/src/features/post-detail/__tests__/AuthorStatusBanner.test.tsx`·`frontend/src/pages/__tests__/PostDetailPage.author.test.tsx`: 수정 중이면 "수정 중인 내용이 있어요(10월 3일 14:03 저장) [이어서 수정] [변경 취소]"(Asia/Seoul), PRIVATE면 "🔒 비공개" + "나만 볼 수 있는 글이에요", 숨김이면 "운영 정책에 따라 숨겨진 글이에요. 다른 사람에게는 보이지 않아요", 작성자에게 [수정]·[공개 범위 ▾]·[삭제] 있고 [좋아요]·[신고]·[팔로우] 없음(좋아요 수만), `useViewBeacon` 요청 0건, `status: DRAFT` 응답이면 `editorPath`로 `replace` 이동(US4 #1~#4·#6, SC-007). (구현 메모: 확인 창 버튼은 002 에디터와 같은 [버리기]/[계속 고치기]다. [공개 범위 ▾]·[삭제]는 자리(prop)로 넘긴 부품이 그려지는지만 본다(아래 T059 메모))

### Implementation for User Story 4

- [X] T055 [US4] `PostDetailAssembler`(`backend/src/main/java/com/team/blog/post/application/PostDetailAssembler.java`)에 작성자 분기 추가: `viewer.isAuthorOf(authorId)`면 `authorView{hasDraft, draftSavedAt, hidden = hidden_at != null, hiddenReason}`을 채운다 — `draftSavedAt`은 002 `PostDraftQueryService.findSavedAt(postId)`(specs/002 T022, `post_draft.updated_at`, 본문은 읽지 않음; 자동 저장은 최대 약 1분 늦을 수 있음)로, `hasDraft = draftSavedAt.isPresent()`. 작성자에게는 좋아요·팔로우 포트를 호출하지 않는다. 작업본 조회 실패는 `hasDraft=false` + 경고 로그(research R-30, data-model §1 post_draft). (구현 메모: 작성자에게만 `authorView`를 채운다. 작업본 저장 시각을 읽으려고 002 `PostDraftQueryService.findSavedAt`이 엔티티 전체(`content_md` 포함)를 읽던 것을 `PostDraftRepository.findUpdatedAtByPostId`(`updated_at`만) 조회로 바꿨다(002 공유 파일, 결과는 같음). 부가 정보 조회 하나가 실패해 함께 쓰는 읽기 트랜잭션이 rollback-only가 되면 응답 전체가 500이 되므로 `PostQueryService.detailOf`를 `Propagation.NOT_SUPPORTED`로 두어 각 Service가 자기 트랜잭션에서 돌게 했다)
- [X] T056 [US4] `PostQueryService.getDetail`(`backend/src/main/java/com/team/blog/post/application/PostQueryService.java`)에 ⑤ 추가: `canRead` 통과 후 `status == DRAFT && viewer.isAuthorOf(authorId)`면 `PostDetailView.draft(id, "/write/{id}")`만 반환(본문 없음). `PostDetailController`는 이 응답과 PRIVATE·숨김 글에 `private, no-store`를 붙인다(research R-22, FR-042). (구현 메모: 응답 타입을 `PostDetailResponse`(sealed: `PostDetailView` | `PostDetailView.Draft`)로 나눴다 — `PostDetailView`의 숫자·불리언 필드를 null로 둘 수 없어 임시글 응답은 `{id, status:"DRAFT", editorPath}` 세 필드만 담는 record다. `isAuthorDraft`·`editorPath`를 내보내 화면 경로(T057)가 같은 판정을 쓴다. 캐시 헤더는 기존 `CacheControlPolicy.forPost`가 임시글·비공개·숨김에 이미 `private, no-store`를 준다)
- [X] T057 [US4] `PageShellController`(`backend/src/main/java/com/team/blog/discovery/web/PageShellController.java`)에 ⑤ 작성자 본인 임시글 → `302 Location: /write/{postId}`를 추가하고, `visibility != PUBLIC` 또는 숨김 글을 작성자가 볼 때는 공개 메타 대신 공통 문구 + `noindex` 메타(`LinkPreviewMeta.unavailable()`)와 `private, no-store`로 200을 낸다(FR-045, research R-18). (구현 메모: `post-read.csv`의 `AUTHOR,DRAFT,read-detail-page` 행을 200에서 302로 고쳤다(T027 메모). 302에도 `private, no-store`. 작성자가 보는 공개 글의 메타는 US5(T064)가 채운다)
- [X] T058 [P] [US4] 작성자 안내 `frontend/src/features/post-detail/AuthorStatusBanner.tsx`: 수정 중 안내("수정 중인 내용이 있어요({M월 D일 HH:mm} 저장)", [이어서 수정] → `/write/{id}`, [변경 취소] → 확인 창 후 002 `DELETE /api/posts/{postId}/working-copy`, 성공 시 상세 다시 불러오기), 🔒 비공개 배지(004 `VisibilityBadge`) + "나만 볼 수 있는 글이에요", 숨김 안내(사유 표시는 014가 붙일 자리)(FR-038·039). (구현 메모: 004 `VisibilityBadge`가 아직 없어 🔒 비공개는 이 부품 안의 글자 배지(`data-testid=private-badge`)로 뒀다. [변경 취소] 확인 문구는 002 `EditorPage.DISCARD_CONFIRM`과 같은 문구를 이 파일에 따로 둔다(에디터 페이지를 import하면 상세 화면 번들에 에디터 코드가 딸려 온다). 숨김 사유는 `hiddenReasonSlot` 자리(014))
- [X] T059 [US4] `frontend/src/pages/PostDetailPage.tsx`에 작성자 분기 연결: `status === 'DRAFT'`면 `navigate(editorPath, {replace:true})`, `AuthorStatusBanner` 표시, 작성자 버튼은 004 `PostActions`(FR-045 규칙)로 [수정](→ `/write/{id}`), [공개 범위 ▾](004 `VisibilitySelect`, `PUT /api/posts/{postId}/visibility` 즉시 변경 후 상세 다시 불러오기), [삭제](006 휴지통 확인 창 → `DELETE /api/posts/{postId}`, 성공 시 내 글 관리 또는 홈으로)를 그린다. 작성자에게는 [좋아요]·[신고]·[팔로우]를 그리지 않고 `useViewBeacon({enabled:false})`(US4 #6). (구현 메모: 004 `PostActions`·`VisibilitySelect`·`PUT /api/posts/{postId}/visibility`(004 US1)와 006 `DELETE /api/posts/{postId}`·휴지통 확인 창이 이 브랜치에 없어 만들지 않았다 — `features/post-detail/AuthorActions.tsx`가 [수정](→ `/write/{id}`)만 그리고 [공개 범위 ▾]·[삭제]는 `PostDetailPage`의 `visibilityControl`·`deleteControl` prop(`{postId, visibility, reload}`를 받는 함수) 자리로 둔다. 004·006이 `App.tsx` 라우트에서 채우거나 `PostActions`로 바꾼다. API 응답 타입은 `PostDetailResponse = PostDetail | PostDetailDraft`로 나눴다(`api/types/reading.ts`))

**Checkpoint**: User Story 4 works on top of US2 and is independently testable

---

## Phase 7: User Story 5 - 주소 처리와 링크 미리보기 (Priority: P2)

**Goal**: 잘못 옮겨 적은 글 주소는 볼 수 있는 글이면 정규 주소로 301, 볼 수 없으면 404(작성자 비노출). 공개 글은 제목·요약·대표 사진(원본) 미리보기 메타를, 그 밖은 공통 문구 + `noindex`를 첫 응답에 넣는다(40 R-3·R-10, §5, H7).

**Independent Test**: quickstart Q-9·Q-10. 공개 글을 다른 블로그 주소로 열어 301(쿼리 유지), 비공개 글은 404, 공개 글 페이지의 `<title>`·description·canonical·`og:*`·`article:*` 값과 이스케이프, 볼 수 없는 글의 공통 메타를 `curl`로 확인한다.

### Tests for User Story 5 ⚠️

- [X] T060 [P] [US5] 통합 테스트 `backend/src/test/java/com/team/blog/discovery/PageShellIntegrationTest.java`(US5 #1~#5, FR-026·027·043~045, Q-9·Q-10): ④ `GET /@na_ms/posts/{A의 공개 글}?comment=120` → 301 `/@kim755030/posts/{id}?comment=120`; `GET /@na_ms/posts/{A의 PRIVATE 글}`(비회원·회원 B·작성자 A 모두) → 비작성자는 404·이동 없음; `GET /@kim755030/posts/12x` → 404. 공개 글 `?utm_source=x` → `<title>{제목} - {닉네임}</title>`, `<meta name="description">`가 `excerpt` 앞 160자 이하, `<link rel="canonical" href="{base-url}/@kim755030/posts/{id}">`(쿼리 없음), `og:type=article`, `og:title`, `og:description`, `og:image`가 원본 주소(`_thumb` 아님), `article:published_time` = `first_public_at`, `article:modified_time`은 `edited_at` 있는 글에만. 제목 `"><script>alert(1)</script>` 글은 속성 값으로 이스케이프되어 실행 가능한 태그가 없음. `thumbnail_url` NULL 글은 `og:image` = `blog.seo.default-og-image-url`. 모든 응답 HTML에 본문 있는 `<script>` 태그 0개. (구현 메모: 작성자 본인이 다른 블로그 주소로 자기 비공개 글을 열면 볼 수 있는 글이라 ④ 301이 된다는 것과, 없는 블로그 주소(`/@nobody_here/posts/{공개 글}`)도 ④로 바른 주소에 옮겨진다는 것을 함께 본다. 160자 자르기는 이모지(서로게이트 쌍)를 섞은 200자 요약으로 코드포인트 기준인지 확인한다. 요약이 없는 글은 description·og:description 태그가 없다)
- [X] T061 [P] [US5] 통합 테스트 `backend/src/test/java/com/team/blog/media/OgImageResolverIntegrationTest.java`(research R-26): `thumbnail_url`의 키가 `image.thumb_storage_key`와 일치 → 그 행 `storage_key` 주소, 일치 행 없음(썸네일 없는 옛 사진) → `thumbnail_url` 그대로, `thumbnail_url` NULL → 기본 이미지, 조회는 `uq_image_thumb_key`로 1번. (구현 메모: 저장소 공개 주소(`blog.image.public-base-url`)로 시작하지 않는 주소는 조회 없이 그대로 쓰는 경우를 더했다)

### Implementation for User Story 5

- [X] T062 [P] [US5] OG 원본 찾기 임시 구현 `backend/src/main/java/com/team/blog/media/application/OgImageResolver.java`(003 소유 media 모듈 — "003에서 교체" 주석): `String originalImageUrl(String thumbnailUrl)` — `blog.image.public-base-url` 접두어를 떼어 키를 얻고 `SELECT storage_key FROM image WHERE thumb_storage_key = :key` 1번, 결과 없으면 `thumbnailUrl`, 입력 NULL이면 `blog.seo.default-og-image-url`(research R-26, data-model §1 image). (구현 메모: 기본 이미지 주소는 discovery `ReadingProperties`를 media가 가져다 쓰지 않으려고 `@Value("${blog.seo.default-og-image-url}")`로 받는다(같은 키·같은 기본값). 공개 주소 접두어는 001 `ImageUrlResolver`의 규칙으로 만든다. 쿼리는 `JdbcClient` 1번)
- [X] T063 [P] [US5] 메타 생성 `backend/src/main/java/com/team/blog/discovery/application/LinkPreviewMetaFactory.java`: `forPublicPost(row)` — title `"{제목} - {닉네임}"`, description = `excerpt` 앞 `seo.description-length`자(코드포인트 기준, NULL이면 생략), canonical = `site.base-url` + `/@{handle}/posts/{id}`(쿼리 없음), `ogType=article`, `ogTitle`=제목, `ogDescription`=description, `ogImage` = `OgImageResolver`, `publishedTime` = `first_public_at`(ISO-8601), `modifiedTime` = `edited_at`(있을 때만); `unavailable()` — og:title "볼 수 없는 글이에요", og:description "친구 공개·비공개 글이거나 삭제된 글입니다.", `noindex=true`(004 `NotFoundPageRenderer`와 같은 문구 상수를 공유); `forBlog(owner)`는 T050 내용을 이리로 옮긴다(FR-044·045, research R-18·R-27). (구현 메모: `forBlog`를 `PageShellController`에서 옮겼고 설명 자르기를 UTF-16 길이에서 코드포인트로 바꿨다(블로그 소개도 같다). 공통 문구는 `LinkPreviewMeta.UNAVAILABLE_*` 상수를 004 `NotFoundPageRenderer.HEAD`도 쓰게 바꿨다(004 공유 파일 — 출력 바이트는 같다))
- [X] T064 [US5] `PageShellController`(`backend/src/main/java/com/team/blog/discovery/web/PageShellController.java`)에 ④ 추가: `canRead` 통과 뒤 경로 `handle` ≠ 작성자 handle이면 `301 Location: /@{작성자 handle}/posts/{postId}` + 원래 쿼리 문자열(research R-32); ④는 ③보다 뒤에만 실행해 볼 수 없는 글의 작성자를 드러내지 않는다. ⑥ 200 응답의 메타를 `LinkPreviewMetaFactory.forPublicPost`로 바꾼다(작성자가 보는 PRIVATE·숨김은 T057의 `unavailable()` 유지). OG 원본 조회는 이 경로에서만 한다(API는 하지 않음). (구현 메모: ④는 ③(판정) 바로 뒤, ⑤(작성자 임시글 302)보다 앞이다 — 다른 블로그 주소로 연 자기 임시글은 301 뒤 302가 된다. `PageShellController`는 이제 메타를 `LinkPreviewMetaFactory`에만 맡긴다)
- [X] T065 [US5] 화면 안 이동의 정규 주소 처리 확인: `frontend/src/pages/PostDetailPage.tsx`에서 `canonicalPath`가 현재 `pathname`과 다르면 쿼리·해시를 유지한 `replace` 이동(T042)과, `document.title`을 `{제목} - {닉네임}`(볼 수 없는 글은 "볼 수 없는 글이에요")으로 바꾸는 처리를 넣는다. 서버 메타는 첫 응답에서만 쓰이고 SPA 안 이동은 `document.title`만 바꾼다(research R-22·R-25). (구현 메모: 쿼리뿐 아니라 `#comment-{id}` 조각도 유지한다. 볼 수 없는 글(404)이면 `document.title`을 "볼 수 없는 글이에요"로 둔다)

**Checkpoint**: User Story 5 complete — 주소 정리·미리보기 메타가 첫 응답에 들어간다

---

## Phase 8: User Story 6 - 편안한 목록 탐색 (Priority: P3)

**Goal**: [더 보기]를 여러 번 누른 뒤 글을 열었다 뒤로 와도 30분 안이면 카드·스크롤 위치가 복원되고, 로딩·실패·빈 목록일 때 안내를 받는다(10 L-6, §4-4, §8).

**Independent Test**: quickstart Q-12(목록 부분). [더 보기] 3번 → 스크롤 → 글 열기 → 뒤로 가기에서 27개·스크롤 복원, 보관 시각 31분 전이면 처음 9개, 오프라인 [더 보기] 실패 문구와 같은 커서 재요청, 빈 홈 문구를 확인한다.

### Tests for User Story 6 ⚠️

- [X] T066 [P] [US6] 단위 테스트 `frontend/src/features/post-list/__tests__/listRestore.test.ts`(US6 #1·#2, FR-018, SC-010): 키 `list-restore:{목록키}`(홈 `home`, 블로그 `blog:{handle}`)로 `{items, nextCursor, scrollY, savedAt}` 저장·복원, `LIST_RESTORE_TTL_MINUTES`(기본 30) 초과·값 없음·JSON 손상·`sessionStorage` 접근 예외 → `null`(처음 9개부터). (구현 메모: 보관값 형태가 다르면(`items`가 배열이 아님·`nextCursor`가 문자열/null이 아님) null, 30분 경계는 30분 정각까지 복원하고 1ms 넘으면 null로 본다)
- [X] T067 [P] [US6] 화면 테스트 `frontend/src/pages/__tests__/HomePage.states.test.tsx`(US6 #3·#4, FR-016·017): [더 보기] 요청 중 버튼 "불러오는 중…" + `disabled`, 실패 시 "불러오지 못했어요 [다시 시도]" → [다시 시도]가 같은 `cursor`로 요청, 첫 목록 실패 시 "글을 불러오지 못했어요 [다시 시도]", 글 0개 + 로그인 회원 → "아직 올라온 글이 없어요. 첫 글의 주인공이 되어 보세요 [글쓰기]", 비회원 → 같은 문구 + [로그인]. 뒤로 가기(`popstate` 흉내)로 돌아오면 저장된 카드 수·`scrollTo` 호출 복원. (구현 메모: "뒤로 가기"는 `MemoryRouter`에서 `navigate(-1)`(POP)로 흉내 낸다. 30분이 지난 뒤 돌아오면 처음 9개를 다시 부르는 것과 링크로 새로 들어오면(PUSH) 복원하지 않는 것도 함께 본다. 기존 `HomePage.test.tsx`·`BlogPage.test.tsx`의 `beforeEach`에 `sessionStorage.clear()`를 더했다(앞 테스트의 보관값이 넘어오지 않게))

### Implementation for User Story 6

- [X] T068 [P] [US6] 복원 저장소 `frontend/src/features/post-list/listRestore.ts`(`save(listKey, state)`, `load(listKey)`; 모든 `sessionStorage` 접근은 try/catch) + 프런트 설정 `frontend/src/config.ts`에 `LIST_RESTORE_TTL_MINUTES = 30`(원칙 VII, research R-10). (구현 메모: `save`·`load`에 `clear`·`storageKey`를 더했다. 저장 시각은 `Date.now()`(epoch 밀리초))
- [X] T069 [US6] `frontend/src/features/post-list/useCursorList.ts`에 복원 연결: 초기화 때 `listRestore.load(listKey)`가 있으면 요청 없이 `items`·`nextCursor`를 쓰고 그린 뒤 `window.scrollTo(0, scrollY)`, 글 링크로 떠날 때(`pagehide`/라우트 이동 전)와 [더 보기] 성공 때 `save`. 첫 목록 실패 상태(`initialError`)와 [더 보기] 실패 상태를 구분한다. (구현 메모: 복원은 react-router `useNavigationType() === 'POP'`(뒤로·앞으로·새로 고침)일 때만 한다 — 링크로 새로 들어온 홈은 처음부터. 보관은 카드 상태가 바뀔 때(첫 응답·[더 보기] 성공)와 화면을 떠날 때(라우트 이동으로 내려갈 때의 정리 함수, `pagehide`) 한다. 복원한 목록은 첫 요청을 건너뛰고 그린 뒤 `window.scrollTo(0, scrollY)`. 반환값에 `initialError`(첫 목록 실패)를 더했다)
- [X] T070 [US6] `frontend/src/components/LoadMoreButton.tsx`에 상태 문구 추가: `loading` → "불러오는 중…" + `disabled`, `error` → "불러오지 못했어요" + [다시 시도](`retry()`), `done` → "모든 글을 다 봤어요"(FR-016). (구현 메모: "불러오는 중…"+비활성, "불러오지 못했어요 [다시 시도]", "모든 글을 다 봤어요"는 Phase 2(T017)에서 이미 들어 있었다. 이번에는 문구를 상수(`LOADING_TEXT`·`INITIAL_LOAD_FAILED_TEXT`)로 내보내고 홈·블로그가 같은 첫 실패 문구를 쓰게 했다)
- [X] T071 [US6] 빈 목록·첫 실패 상태: `frontend/src/pages/HomePage.tsx`에 "글을 불러오지 못했어요 [다시 시도]"와 빈 홈 문구(로그인 회원 [글쓰기] → 002 새 글 진입, 비회원 [로그인] → `/login?returnTo=/`)를, `frontend/src/pages/BlogPage.tsx`에 같은 첫 실패 문구와 복원(`listKey = blog:{handle}`)을 연결한다(FR-016·017·018). (구현 메모: 빈 홈의 [글쓰기]는 002 새 글 진입 `/write/new`, [로그인]은 `/login?returnTo=/`(001 로그인 화면이 아직 `returnTo`를 읽지 않아 로그인 뒤에는 서버가 정한 주소로 간다 — 001 몫). 세션 확인 중에는 두 버튼 모두 그리지 않는다. 블로그는 `listKey = blog:{handle}`)

**Checkpoint**: All user stories should now be independently functional

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 성능·장애 격리·반응형·문서 검증 등 여러 스토리에 걸친 확인

- [X] T072 [P] 성능·인덱스 통합 테스트 `backend/src/test/java/com/team/blog/discovery/ListIndexExplainIntegrationTest.java`(SC-001, 06 R-2b, data-model §2): 글 1만 건 시드(작성자 50명, 상태 섞음) 후 홈·블로그 대표 쿼리의 `EXPLAIN (FORMAT JSON)`에 `ix_post_feed`·`ix_post_blog`가 나오는지, 프로필 JOIN이 `uq_image_profile_current`를 쓰는지, `GET /api/posts`·`GET /api/members/{handle}/posts`·`GET /api/posts/{id}` 서버 처리 시간이 각각 300ms 이내인지(웜업 후 중앙값) 확인한다. (구현 메모: EXPLAIN 대상 문장을 실제 실행 문장과 같게 하려고 `PostCardQueryRepository.cardQuery(viewer, authorId, after, limit)`(SQL·파라미터 묶음 `CardQuery`)를 열고 `findCards`가 이를 쓰게 했다. 시드는 작성자 50명·프로필 사진 50장·글 사진 1,000장·글 1만 건(임시 5%·비공개 10%·휴지통 2%·숨김 1% 섞음) 뒤 `ANALYZE`. 홈은 커서 없음·있음 둘 다 `ix_post_feed`, 블로그는 `ix_post_blog`, 둘 다 `uq_image_profile_current`. 시간은 MockMvc 서버 처리 시간(웜업 5회 뒤 11회 중앙값)으로 잰다 — 인덱스·쿼리 변경 없이 통과)
- [X] T073 [P] 장애 격리 통합 테스트 `backend/src/test/java/com/team/blog/discovery/ReadingRedisOutageIntegrationTest.java`(Edge Cases, 02 §2-1, 원칙 V): Redis 컨테이너를 일시 정지한 상태에서 세션 쿠키가 있는 요청으로 `GET /api/posts`, `GET /api/posts/{공개 글}`, `GET /@kim755030/posts/{공개 글}`이 200이고 `viewer.loggedIn=false`로 응답하는지 확인한다(001의 Redis 장애 처리 사용). (구현 메모: 001 `RedisOutage`로 Redis 컨테이너를 멈춘 채 A의 세션 쿠키로 요청한다. 화면 주소는 첫 응답 메타(제목)까지 확인. 코드 변경 없이 통과)
- [X] T074 [P] 반응형·접근성 확인 `frontend/src/components/__tests__/PostCardGrid.layout.test.tsx` 또는 Playwright 스크립트 `frontend/e2e/reading-responsive.spec.ts`: 375px·640px·1024px 폭에서 홈·블로그·상세 `document.documentElement.scrollWidth <= innerWidth`, 같은 줄 카드 높이 동일, 썸네일 없는 카드 높이 동일, 카드·작성자 링크 Tab 이동 가능(FR-014·015, SC-006). (구현 메모: jsdom에는 배치 계산이 없어 Playwright `frontend/e2e/reading-responsive.spec.ts`로 했다(desktop 프로젝트에서 폭을 375·640·1024로 직접 바꿈). 처음 실행에서 상세 375px이 `scrollWidth` 1691로 넘쳤다 — 정화된 본문 HTML의 긴 코드 블록·표에 가로 스크롤 처리가 없었다(에디터 `editor.css`의 `.post-content pre`는 상세 본문에 걸리지 않음). 상세 본문 전용 `features/post-detail/postDetail.css`(`.post-detail-content` 안 `pre`·`table`은 상자 안 가로 스크롤, 사진·영상 `max-width: 100%`)를 더해 통과. E2E 환경에는 사진 저장소가 없어 카드는 모두 썸네일 없는 카드다(썸네일 있는 카드 높이는 Phase 3 `PostCard` 단위 테스트가 본다). Tab 이동은 카드 링크 → 작성자 링크 → Enter로 블로그 이동까지 본다)
- [X] T075 [P] 코드 강조·GIF 번들 분리 확인: `frontend/vite.config.ts`(또는 팀 번들러 설정)에서 highlight.js가 별도 청크로 분리되는지 빌드 결과로 확인하고, 코드 블록이 없는 글 상세에서 그 청크 요청이 0건인지 `frontend/e2e/reading-highlight.spec.ts`로 확인한다(FR-031, quickstart Q-12). (구현 메모: 에디터 미리보기(002 `PreviewPane`)가 `highlightCode`를 정적으로 가져와 상세의 동적 `import()`가 무력했다(빌드 경고 INEFFECTIVE_DYNAMIC_IMPORT, 첫 번들 448kB에 highlight.js 포함). `vite.config.ts`는 그대로 두고 `App.tsx`에서 에디터 화면(`/write/new`·`/write/:postId`)을 `React.lazy` + `Suspense`로 나눴다 — 빌드 결과 `highlightCode-*.js`(155kB) 별도 청크, 첫 번들 255kB, 경고 없음. `frontend/e2e/reading-highlight.spec.ts`: 코드 블록 없는 글 상세에서 그 청크 요청 0건, 코드 블록 글에서 1건 + `code.hljs`. 기존 002 E2E(에디터 반응형·자동 저장·두 탭·XSS)도 나눈 뒤 다시 통과)
- [X] T076 OpenAPI 계약 일치 확인: `backend/src/test/java/com/team/blog/discovery/ReadingContractConformanceTest.java`에서 T018·T025·T043 응답 예시를 `specs/005-post-reading/contracts/openapi.yaml` 스키마로 검증하거나(검증 라이브러리가 공통 pom에 없으면) 필드 이름·필수 여부를 단언하는 테스트를 둔다. T032의 `viewer.emailVerified`·`isAdmin` 추가처럼 계약과 다른 점은 보고 목록으로 남긴다. (구현 메모: failsafe가 돌도록 파일 이름을 `ReadingContractConformanceIntegrationTest.java`로 했다(DB가 필요한 통합 테스트). 공통 pom에 OpenAPI 검증 라이브러리가 없어 계약 YAML을 SnakeYAML(스프링 부트 의존성으로 이미 테스트 경로에 있음)로 읽고 `$ref`·`allOf`를 풀어 필수 필드·계약에 없는 필드·기본 타입(null 허용 포함)을 안쪽 객체까지 본다. 대상: 홈·블로그 목록(PostCardPage), 블로그 머리말(본인·남), 상세(비회원·회원·작성자 작업본·작성자 비공개·임시글 본문 `id·status·editorPath`), 오류(400·404 ErrorResponse). 계약과 다른 점은 없었다 — T032의 `viewer.emailVerified`·`isAdmin`은 계약에 이미 들어 있다. 계약에 필수 필드를 하나 더해 실패하는 것도 확인했다)
- [X] T077 quickstart 검증: `specs/005-post-reading/quickstart.md` §2 기동 → §3 Q-1~Q-12를 순서대로 실행하고, §4 자동 테스트 명령(`./mvnw -Dtest=CursorCodecTest test`(001 소유 — 이 기능은 `PostListCursorTest`도 함께), `./mvnw -Dit.test='HomeListIntegrationTest,BlogPageIntegrationTest,ListIndexExplainIntegrationTest' verify`, `./mvnw -Dit.test='PostDetailIntegrationTest,PostDetailPermissionMatrixTest,PageShellIntegrationTest' verify`, `npm test -- post-list post-detail`)이 모두 통과하는지 확인한다. 픽스처를 로컬 DB에 적용하는 방법(quickstart §2 `psql < …/post-reading.sql`)이 T004 파일과 맞는지도 확인한다. (구현 메모: §2 `docker compose up`은 공용 포트(8080/5432)를 써서 대신 임의 포트 PostgreSQL·Redis 컨테이너 + React 빌드를 넣은 jar로 기동하고, 픽스처는 문서대로 `psql < backend/src/test/resources/fixtures/post-reading.sql`(빈 DB, `ON_ERROR_STOP`)로 오류 없이 적용됨을 확인했다. §3 Q-1~Q-10은 curl 40개 확인으로 모두 통과(Q-3의 휴지통·비공개 전환은 006·004 API가 이 브랜치에 없어 SQL로 흉내, 새 글 발행은 002 API). 픽스처 회원 A는 비밀번호 없는 소셜 수단뿐이라 로컬 DB에서 가입한 이메일 계정의 로그인 수단을 A에 옮겨 `A_COOKIE`를 얻었다 — quickstart의 "001 로그인 절차로 얻는다"는 이 픽스처로는 그대로 안 된다. Q-11·Q-12(브라우저)는 자동 테스트로 대신했다: 작성자 화면 `PostDetailPage.author.test.tsx`·`AuthorPageShellIntegrationTest`, 조회 기록 `useViewBeacon` 테스트, 복원·문구 `HomePage.states.test.tsx`·`listRestore.test.ts`, 반응형·코드 강조 E2E(T074·T075), Redis 중지 T073. §4 명령은 이름을 실제 파일에 맞춰 `-Dtest='CursorCodecTest,PostListCursorTest'`(28개), `-Dit.test='HomeListIntegrationTest,BlogPageIntegrationTest,ListIndexExplainIntegrationTest'`(16개), `-Dit.test='PostDetailIntegrationTest,PostDetailPermissionMatrixIT,PageShellIntegrationTest'`(105개 — 문서의 `PostDetailPermissionMatrixTest`는 실제 `PostDetailPermissionMatrixIT`), `npm test -- post-list post-detail`(25개)로 모두 통과)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 001 Phase 1·2, 002 Foundational, 004 Foundational이 끝나야 한다(Cross-feature Dependencies) — 이 Phase는 확인만
- **Foundational (Phase 2)**: Setup 확인 뒤 시작 — BLOCKS all user stories
  - T009(테스트) → T010(구현), T011 → T012, T014 → T015, T016 → T017
  - T006(테스트) → T007(구현), T010은 T003·T007·T008 다음, T012는 T003 다음
- **User Stories (Phase 3+)**: 모두 Foundational 완료 뒤 시작
  - US1(P1)·US2(P1)·US3(P1)는 서로 독립 — 병렬 가능
  - US4(P2)는 US2의 상세 API·화면(T032~T042) 위에 작성자 분기를 더한다 → US2 완료 필요
  - US5(P2)는 US2의 `PageShellController` 상세 경로(T038)와 US3의 블로그 메타(T050) 위에 쌓는다 → US2 필요, T063의 `forBlog` 이동은 US3 완료 뒤
  - US6(P3)는 US1의 홈 화면(T024)과 US3의 블로그 화면(T051)이 있어야 한다
- **Polish (Phase 9)**: 원하는 스토리 완료 뒤

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 뒤 시작, 다른 스토리 의존 없음
- **User Story 2 (P1)**: Foundational 뒤 시작, 다른 스토리 의존 없음(태그·좋아요·팔로우·댓글·GIF는 임시 구현/자리로 독립)
- **User Story 3 (P1)**: Foundational 뒤 시작. `PageShellController` 파일을 US2(T038)와 함께 고치므로 T050은 T038 다음에 한다(같은 파일)
- **User Story 4 (P2)**: US2 완료 필요(같은 `PostQueryService`·`PostDetailAssembler`·`PostDetailPage`·`PageShellController` 파일 확장)
- **User Story 5 (P2)**: US2 완료 필요, US4의 T057 다음에 T064(같은 파일). 블로그 메타 정리는 US3 뒤
- **User Story 6 (P3)**: US1·US3 화면 완료 필요

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- 응답 형태(record)·포트 → Repository → Service → Controller → 화면
- `PageShellController`·`PostDetailAssembler`·`PostQueryService`·`PostDetailPage.tsx`는 여러 스토리가 같은 파일을 고치므로 스토리 순서(US2 → US3 → US4 → US5)대로 한다
- Story complete before moving to next priority

### Parallel Opportunities

- Foundational: T003·T004·T005·T006·T008·T011·T013·T014·T016 동시 진행 가능
- US1·US2·US3는 Foundational 뒤 서로 다른 개발자가 동시에 진행 가능(단 `PageShellController`는 순서 지킴)
- 각 스토리의 [P] 테스트는 동시에 작성 가능
- US2의 T032·T033, T039·T040·T041은 서로 다른 파일이라 병렬

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "HomeListIntegrationTest in backend/src/test/java/com/team/blog/discovery/HomeListIntegrationTest.java"
Task: "HomeCursorValidationIntegrationTest in backend/src/test/java/com/team/blog/discovery/HomeCursorValidationIntegrationTest.java"
Task: "HomeListActorIntegrationTest in backend/src/test/java/com/team/blog/discovery/HomeListActorIntegrationTest.java"
Task: "HomePage test in frontend/src/pages/__tests__/HomePage.test.tsx"
```

## Parallel Example: User Story 2

```bash
# Tests
Task: "PostDetailIntegrationTest in backend/src/test/java/com/team/blog/post/PostDetailIntegrationTest.java"
Task: "PostDetailFallbackIntegrationTest in backend/src/test/java/com/team/blog/post/PostDetailFallbackIntegrationTest.java"
Task: "PostPageShellReaderIntegrationTest in backend/src/test/java/com/team/blog/discovery/PostPageShellReaderIntegrationTest.java"
Task: "PostDetailPage test in frontend/src/pages/__tests__/PostDetailPage.test.tsx"
Task: "useViewBeacon test in frontend/src/features/post-detail/__tests__/useViewBeacon.test.ts"

# Models / ports / UI parts
Task: "PostDetailView in backend/src/main/java/com/team/blog/post/application/PostDetailView.java"
Task: "Tag/Like/Follow ports + stubs in backend/src/main/java/com/team/blog/post/application/port/"
Task: "TagList, ReactionBar, AuthorCard in frontend/src/components/"
Task: "useViewBeacon, loadHighlighter in frontend/src/features/post-detail/"
```

## Parallel Example: User Story 3

```bash
Task: "BlogPageIntegrationTest in backend/src/test/java/com/team/blog/discovery/BlogPageIntegrationTest.java"
Task: "BlogNotFoundIntegrationTest in backend/src/test/java/com/team/blog/discovery/BlogNotFoundIntegrationTest.java"
Task: "BlogPageShellIntegrationTest in backend/src/test/java/com/team/blog/discovery/BlogPageShellIntegrationTest.java"
Task: "BlogPage test in frontend/src/pages/__tests__/BlogPage.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: 001·002·004 선행 작업 확인
2. Phase 2: Foundational (CRITICAL - 카드 SQL 1번·셸 렌더러·카드 부품)
3. Phase 3: User Story 1 (홈 목록)
4. **STOP and VALIDATE**: quickstart Q-1~Q-4, `HomeListIntegrationTest`·`HomeCursorValidationIntegrationTest`
5. Deploy/demo if ready

Tier A 완료 기준(C-READ-1·C-READ-2·C-BLOG-1)은 US1·US2·US3가 모두 필요하므로, 실제 공통 1차 목표는 **US1 + US2 + US3**다.

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1(홈) → Test independently → Deploy/Demo (MVP!)
3. US2(상세) → 404 동일성·권한 매트릭스 통과 → Deploy/Demo
4. US3(블로그) → Tier A 읽기 완료
5. US4(작성자 화면) → US5(주소·미리보기) → US6(복원·상태 문구)
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 → US6
   - Developer B: US2 → US4
   - Developer C: US3 → US5(US2의 T038 이후)
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 이 기능은 Flyway 파일·새 테이블·새 컬럼을 만들지 않는다(원칙 I). 스키마가 필요해 보이면 멈추고 보고한다
- 목록 노출 조건은 004 `VisibilityFilter`만, 상세 판정은 004 `PostAccessPolicy.canRead`만 쓴다(직접 WHERE 작성 금지, 06 R-2)
- 현재 사용자는 세션에서만 얻는다. 작성자 ID를 요청 파라미터로 받지 않는다(원칙 III)
- 임시 구현(OgImageResolver = 003, 태그·좋아요·팔로우 포트 = 008·009·010, 댓글 자리 = 007, GIF 자리 = 003)은 각 클래스 주석에 교체할 spec을 적는다
- Verify tests fail before implementing; commit after each task or logical group
- Stop at any checkpoint to validate story independently
