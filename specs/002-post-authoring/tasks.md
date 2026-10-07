---

description: "Task list for 002-post-authoring (글 작성·임시저장·발행)"
---

# Tasks: 글 작성·임시저장·발행 (자동 저장, 발행·수정, 본문 정화)

**Input**: Design documents from `/specs/002-post-authoring/` (spec.md, plan.md, research.md, data-model.md, contracts/openapi.yaml, contracts/events.md, quickstart.md), `.specify/memory/constitution.md`, README.md "정해진 것"·"Tier A plan에서 정한 공통 설계"

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 포함한다. constitution 원칙 VIII(권한·데이터 규칙은 Testcontainers PostgreSQL 통합 테스트)과 plan.md Testing 절이 요구한다. 각 스토리에서 테스트 작업을 구현보다 먼저 두고, 먼저 실패하는 것을 확인한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/java/com/team/blog/...`, `backend/src/test/java/com/team/blog/...`, `backend/src/main/resources/...`, `frontend/src/...`, `frontend/e2e/...`(Playwright)
- 패키지는 plan.md Project Structure를 따른다. 단, 공통 기반 소유 규칙에 맞춰 오류 본문·예외는 001의 `shared/error/`, 이벤트 record는 001·004·006과 같은 `shared/event/`에 둔다(plan.md의 `shared/web/error/`·`shared/domain/event/` 표기와 다름 — 아래 "Notes"의 문제 목록 참고).
- 근거 표기: FR-xxx = spec.md, B-n/A-n = research.md, DM §n = data-model.md, EV = contracts/events.md, QS §n = quickstart.md, docs/NN §n = 원문.

---

## Cross-feature Dependencies

002는 아래 항목을 **직접 만들지 않는다**. 선행 작업이 끝나야 해당 Phase를 시작할 수 있다. 001·004 tasks.md의 작업 번호가 확정되면 괄호 안 설명 옆에 T번호를 채운다.

**선행 (002가 사용)**

- 선행: specs/001 Phase 1 (backend Maven 골격 `com.team.blog` package-by-feature, `frontend/` React 골격·라우터, `docker-compose.yml` app+PostgreSQL+Redis+MinIO+Mailpit — Redis는 `appendonly yes`, `appendfsync everysec`, `maxmemory-policy noeviction`)
- 선행: specs/001 Phase 2 (Flyway `V1__common_schema.sql` — docs/51 SQL 블록 기준 `post`·`post_draft`·`post_tag`·`tag`·`post_image`·`image`·`member` 전체 테이블·CHECK·인덱스)
- 선행: specs/001 Phase 2 (`shedlock` 테이블 Flyway 마이그레이션 + ShedLock `JdbcTemplateLockProvider`·`@EnableSchedulerLock` 설정, B-4)
- 선행: specs/001 Phase 2 (Testcontainers 통합 테스트 베이스 `backend/src/test/java/com/team/blog/support/IntegrationTestBase.java` — PostgreSQL + Redis, 회원 픽스처: 인증 완료·인증 전·탈퇴 유예·관리자 로그인 세션과 CSRF 토큰)
- 선행: specs/001 Phase 2 (`shared/error/` — `ErrorResponse{code,message,errors,details}`, `GlobalExceptionHandler`, `NotFoundException`→404 `NOT_FOUND`, 상태 코드·`errors[]`·`details`·응답 헤더(`Retry-After`)를 실을 수 있는 업무 예외 기반 클래스)
- 선행: specs/001 Phase 2 (`shared/security/` — 세션 쿠키 + CSRF `X-XSRF-TOKEN`, `CurrentUser`, 401 `LOGIN_REQUIRED` 진입점, Redis 장애 시 비로그인 처리)
- 선행: specs/001 Phase 2 (계정 상태 가드 `AccountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE)` — DB 매번 조회, 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`, 001 research R-22. 002의 새 글·자동 저장·수동 저장·발행·변경 취소 Service가 첫머리에서 호출한다)
- 선행: specs/001 Phase 2 (보안 헤더/CSP 필터 — FR-049 헤더 전체, `{저장소 공개 주소}` = `blog.image.public-base-url`의 출처. 002는 검증 테스트만 둔다)
- 선행: specs/001 Phase 2 (설정값 바인딩 — `blog.image.public-base-url`, `blog.image.legacy-base-urls`를 CSP 필터와 정화 허용 목록이 **같은 값 하나**로 쓰도록 바인딩한 `ImageProperties`, H3)
- 선행: specs/001 Phase 2 (프런트 CSRF 처리 `frontend/src/features/auth/csrf`) + specs/004 T024 (공용 API 클라이언트 오류 처리 `frontend/src/api/client.ts` — `ApiError(status, code, errors, details)`)
- 선행: specs/001 (account 모듈 공개 Service `MemberQueryService.defaultVisibility(memberId)` — 새 글의 공개 범위 초기값, FR-001. 001 plan의 `MemberQueryService`에 이 메서드가 명시돼 있지 않으므로 001 tasks에 포함되었는지 확인)
- 선행: specs/004 T009 (`backend/src/main/java/com/team/blog/post/domain/Visibility.java` — 002 T018 `Post`가 이 enum을 매핑하므로 T018보다 먼저)
- 선행: specs/004 T013 (`VisibilityRegistry.require(raw, field)` — 허용값 집합, `INVALID_VISIBILITY` 판정, T045)
- 선행: specs/004 T010·T011·T017 (`Viewer`, `CurrentViewerResolver`, `PostReadService.requireReadable(postId, viewer)` — US1·US4의 "독자가 보는 내용" 단언, T039·T089)
- 선행: specs/004 T021~T023 (테스트 픽스처 `support/fixture/MemberFixtures`·`PostFixtures`, 권한 매트릭스 하네스 `support/permission/`(`Actor`, `PermissionAction` 실행기 등록), `backend/src/test/resources/permission/post-write.csv`의 002 대기 행 — 002가 실행기를 등록해 채운다, T042·T070·T090)
- 공유: specs/004 T030 (`shared/event/PostWentPublic` record — 002·004 중 먼저 구현하는 쪽이 만들고 다른 쪽은 재사용, T008)
- 공유: specs/004 T032 (`PostRepository.findForUpdateByIdAndAuthorId` — 002 T021이 Foundational에서 먼저 만들므로 004 T032는 확인만 하면 된다)
- 선행: specs/004 T039 (화면 `frontend/src/features/visibility/VisibilitySelect.tsx` — 발행 설정의 공개 범위 선택, US1 T055)
- 선행(테스트 한정): specs/005 (글 상세 `GET /@{handle}/posts/{id}`·`GET /api/posts/{postId}` — US1 비로그인 읽기 HTTP 확인, US2 Playwright 알림창 0회 확인. 005가 없으면 해당 단언만 004 `PostReadService.requireReadable`과 DB 값으로 대신한다)

**임시 구현(stub) — 소유 스펙이 교체**

- 003-image-upload(Tier B, plan 없음): `media/infra/ImageReferenceResolverAdapter`(T025), `media/application/ImageService.syncPostImages`(T047)를 002가 최소 구현으로 두고 `// TODO(003): 003 plan 확정 후 교체` 표시. 003 FR-022(수동 저장·DB 반영 때 연결), `local:` 임시 표시 규칙은 003이 확정한다.
- 008-tag(Tier B, plan 없음): `tag/application/TagService`(T046)를 002가 최소 구현(05 §4: 소문자·앞뒤 공백 제거·중복 제거·1~30자, 최대 `blog.post.max-tags`)으로 두고 `// TODO(008)` 표시. NFKC·하이픈·금칙어(`TAG_BANNED_WORD`) 등 정규화는 008이 교체한다(spec Assumptions ①).

**제공 (다른 스펙이 002에 기대하는 것)**

- 004·005·006: `Post`·`PostStatus`·`PostDraft` 도메인과 `PostRepository.findForUpdateByIdAndAuthorId`·`PostDraftRepository`(T018·T019·T021). 004는 `Post.changeVisibility`(004 T031), 006은 `moveToTrash`·`restore`를 이 엔티티에 **추가**한다.
- 005: `PostDraftQueryService.findSavedAt(postId)`(T022, 작업본 유무·마지막 저장 시각), 발행 때 저장된 `content_html`·`excerpt`·`thumbnail_url`·`first_public_at`·`edited_at`(T050), 코드 강조 유틸 `frontend/src/features/markdown/highlightCode.ts`(T062).
- 006: `@SQLRestriction("deleted_at IS NULL")`이 붙은 `Post`(T018), `EmptyDraftPolicy.isEmpty`(T020, `Post.isEmptyDraft()`가 위임), `AutosaveService.flushNow(postId)`(T079), `RedisAutosaveStore.release`(조건부, `autosave-release.lua`)와 `RedisAutosaveStore.delete`(무조건 DEL + SREM, 완전 삭제 커밋 후용) (T033).
- 001: 로그아웃 때 호출할 `clearMemberDrafts(memberId)`(T083, `frontend/src/features/editor/localDraftStore.ts`).
- 011·012: `PostPublished`·`PostEdited`(T008)·`PostWentPublic`(004) 이벤트(커밋 후, T050).
- 012 등: `ContentRenderer.render`(T023·T031).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 공통 기반(001 Phase 1·2, 004 Foundational) 완료 확인과 002 전용 의존성 추가

- [ ] T001 specs/001 Phase 1·2 완료 확인: `./mvnw -f backend/pom.xml test`가 `IntegrationTestBase`(Testcontainers PostgreSQL+Redis)로 통과하는지, Flyway V1에 `post`(`ck_post_status`·`ck_post_visibility`·`ck_post_published`·`ck_post_public_at`·`ck_post_edited_at`·`ck_post_content`, `ix_post_manage`)·`post_draft`(`ck_post_draft_content`, FK ON DELETE CASCADE)·`post_tag`·`tag`·`post_image`·`image`(`uq_image_storage_key`)·`shedlock`이 있는지, `docker-compose.yml` Redis가 `appendonly yes`·`appendfsync everysec`·`maxmemory-policy noeviction`인지(`docker compose exec redis redis-cli CONFIG GET maxmemory-policy`), CSP 필터가 `blog.image.public-base-url`을 쓰는지 확인한다. 빠진 항목은 여기서 만들지 말고 001 담당에게 요청한다(Cross-feature Dependencies 참고).
- [ ] T002 specs/004 Foundational 완료 확인: `Visibility`(004 T009)·`VisibilityRegistry`(T013)·`Viewer`/`CurrentViewerResolver`(T010·T011)·`PostReadService`(T017)·픽스처와 권한 매트릭스 하네스(T021~T023, `post-write.csv`의 002 대기 행)·`frontend/src/api/client.ts`(T024)가 있는지 확인한다. 004 T009 `Visibility`는 002 T018보다 먼저 있어야 한다. 004 `PostAccessPolicy`는 `PostView` 투영을 쓰므로 002 `Post`와 순환 의존이 없다.
- [ ] T003 `backend/pom.xml`에 002 전용 의존성을 **추가만** 한다: `org.commonmark:commonmark` 0.30.0 + `commonmark-ext-gfm-tables`·`commonmark-ext-gfm-strikethrough`·`commonmark-ext-task-list-items`·`commonmark-ext-autolink`(모두 0.30.0), `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer` 20260924.2, `io.github.resilience4j:resilience4j-spring-boot3`(B-5). ShedLock(`shedlock-spring`, `shedlock-provider-jdbc-template`)은 001이 넣었는지 확인하고 없을 때만 추가한다(A-9, plan Technical Context).
- [ ] T004 [P] `frontend/package.json`에 `localforage`, `diff`(jsdiff), `highlight.js`, 개발 의존성 `vitest`·`@testing-library/react`·`fake-indexeddb`·`@playwright/test`를 추가하고, `frontend/vitest.config.ts`·`frontend/playwright.config.ts`가 없으면 만든다(Playwright `testDir: 'e2e'`, baseURL `http://localhost:8080`, 프로젝트 2개: 데스크톱 1280px·모바일 375px) (B-13).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 002가 소유하는 공통 기반 — post 도메인(`Post`, `PostStatus`, `PostDraft`), `ContentRenderer`(Markdown 렌더+정화), `EmptyDraftPolicy` — 와 모든 스토리가 쓰는 Redis 자동 저장 보관소 읽기·정리, 설정값, 오류 코드

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### 설정·오류·이벤트

- [ ] T005 [P] 설정값 클래스 `backend/src/main/java/com/team/blog/post/config/PostAuthoringProperties.java`(`@ConfigurationProperties`)와 `backend/src/main/java/com/team/blog/shared/application/markdown/MarkdownProperties.java`를 만들고 `backend/src/main/resources/application.yml`에 초기값을 추가한다(원칙 VII, plan Constraints): `blog.autosave.redis-ttl=24h`, `blog.autosave.flush-interval=1m`, `blog.autosave.rate-limit`(5초에 1번), `blog.autosave.max-request-bytes=1048576`, `blog.post.max-tags=10`, `blog.post.title-max=100`, `blog.post.content-max=100000`, `blog.publish.idempotency-ttl=600s`, `blog.markdown.render-timeout=1s`, `blog.markdown.max-nesting=20`, `blog.markdown.render-threads=4`, `blog.markdown.preview-rate-limit`(1분에 60번), `blog.markdown.rerender-batch-size=100`, `blog.markdown.rerender-interval=10m`, `blog.cleanup.empty-draft-cron="0 30 3 * * *"`(zone `Asia/Seoul`, 제안 B-4), `blog.cleanup.empty-draft-age=24h`, `resilience4j.circuitbreaker.instances.redis`(실패율 50%, 최근 20회, 열림 30초, 반열림 5회, B-5). `blog.image.*`는 001의 `ImageProperties`를 쓰고 여기서 다시 바인딩하지 않는다.
- [ ] T006 [P] 화면 수치 설정 `frontend/src/features/editor/editorConfig.ts`: 로컬 저장 대기 1000ms, 서버 전송 대기 3000ms, 계속 입력 시 최대 30000ms, 재시도 2s→4s→8s… 최대 60s + 무작위 지연(jitter), 미리보기 대기 500ms, 백업 보관 7일, `IN_PROGRESS` 재시도 1000ms (FR-007·010·047, 04 §2-1).
- [ ] T007 [P] 오류 코드와 예외: `backend/src/main/java/com/team/blog/post/application/PostReasonCodes.java`(상수 `TITLE_REQUIRED`·`TITLE_TOO_LONG`·`CONTENT_REQUIRED`·`CONTENT_TOO_LONG`·`CONTENT_TOO_COMPLEX`·`PENDING_IMAGES`·`TOO_MANY_TAGS`·`INVALID_TAG`·`INVALID_VISIBILITY`·`VERSION_CONFLICT`·`IN_PROGRESS`·`IDEMPOTENCY_KEY_REUSED`·`IDEMPOTENCY_KEY_REQUIRED`·`INVALID_IDEMPOTENCY_KEY`·`NOT_PUBLISHED`·`RATE_LIMITED`·`PAYLOAD_TOO_LARGE`·`AUTOSAVE_UNAVAILABLE`)와 `backend/src/main/java/com/team/blog/post/application/exception/`의 `VersionConflictException`(409, `details.server = ServerCopy`), `PublishValidationException`(400 `VALIDATION_FAILED`, `errors[]` 전부), `NotPublishedException`(409), `PublishInProgressException`(409), `IdempotencyKeyReusedException`(422), `AutosaveUnavailableException`(503), `RateLimitedException`(429 + `Retry-After` 초), `PayloadTooLargeException`(413)을 001 `shared/error`의 업무 예외 기반 클래스를 상속해 만든다. 값 객체 `backend/src/main/java/com/team/blog/post/domain/ServerCopy.java`(`title, contentMd, version, savedAt`, DM §6). 메시지는 contracts/openapi.yaml 예시 문구를 그대로 쓴다.
- [ ] T008 [P] 도메인 이벤트 record `backend/src/main/java/com/team/blog/shared/event/PostPublished.java`(`long postId, long authorId, Visibility visibility, Instant publishedAt`), `backend/src/main/java/com/team/blog/shared/event/PostEdited.java`(`long postId, long authorId, Instant editedAt`) — 글자 필드 금지(EV-3, contracts/events.md §1). `backend/src/main/java/com/team/blog/shared/event/PostWentPublic.java`(`long postId, long authorId, Instant firstPublicAt`)는 004 T030과 공유: 이미 있으면 재사용하고, 없으면 같은 필드로 만든다(먼저 구현하는 쪽이 만듦).

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T009 테스트 지원 `backend/src/test/java/com/team/blog/post/support/AuthoringFixtures.java`: 004 T021의 `support/fixture/MemberFixtures`·`PostFixtures`(JdbcTemplate INSERT)를 재사용하고, 없는 것만 더한다 — 작업본 있는 발행 글, 반응 수를 지정한 발행 글, `render_version`을 지정한 발행 글, 생성·수정 시각을 과거로 둔 임시글, Redis `autosave:post:{id}` Hash(`memberId,title,contentMd,version,savedAt`)·`autosave:dirty`를 직접 넣는 헬퍼, 요청 전후 비교용 스냅샷(`title`·`content_md`·`status`·`edit_version`·`updated_at`, SC-008), 커밋 후 이벤트 수집 리스너(`@TransactionalEventListener(AFTER_COMMIT)` 테스트 빈), 테스트용 `StubImageReferenceResolver`(소유 키 집합을 테스트가 지정) 설정 `backend/src/test/java/com/team/blog/post/support/PostTestConfig.java`를 만든다. 001 `IntegrationTestBase`의 회원·세션 픽스처를 재사용한다.
- [ ] T010 [P] `EmptyDraftPolicy` 단위 테스트 `backend/src/test/java/com/team/blog/post/unit/EmptyDraftPolicyTest.java`: `("", "")`·`("  ", "\n\t ")` → 빈 글, 제목만·본문만 있음 → 빈 글 아님, `local:` 사진만 있는 본문 → 빈 글 아님. 판정에 쓰는 공백 문자 집합이 배치 SQL(T115)의 `btrim(x, <같은 문자 집합>)`과 같다는 것을 상수 비교로 확인한다(B-9, 아래 Notes의 btrim 문제 참고).
- [ ] T011 [P] 영속 통합 테스트 `backend/src/test/java/com/team/blog/post/integration/PostPersistenceIT.java`: `Post.newDraft(...)` 저장 시 `edit_version=0`·`status=DRAFT`·`render_version=1`·`content_html=''`; SQL로 `view_count`·`like_count`·`comment_count`를 바꾼 뒤 엔티티의 제목을 바꿔 저장해도 반응 수가 덮이지 않음(05 J-2); `ck_post_published`(발행인데 빈 제목)·`ck_post_public_at`·`ck_post_edited_at`·`ck_post_content`(100,001자) 위반이 DB에서 거부됨; `post_draft`는 글 완전 삭제 시 CASCADE로 사라짐; `PostRepository.findForUpdateByIdAndAuthorId`가 남의 글·휴지통 글에 빈 결과.
- [ ] T012 [P] XSS 코퍼스 테스트 `backend/src/test/java/com/team/blog/shared/markdown/ContentRendererXssTest.java` + 입력 파일 `backend/src/test/resources/markdown/xss/`(docs/12 §9-1 32개: 직접 쓴 태그 7, 위험한 링크 11, 이미지 3, 속성 탈출 3, 문맥 탈출 3, 다른 문법 안 5 등 원문 표의 모든 입력). 검사기 `backend/src/test/java/com/team/blog/shared/markdown/HtmlSafetyChecker.java`는 docs/12 §9 "검사 방법"대로 ① 태그 이름 ∈ 허용 목록 ② 속성 이름에 `on…`·`style` 없음 ③ `href`·`src`를 엔티티 해제·공백/제어 문자 제거 후 `javascript:`·`vbscript:`·`data:`로 시작하지 않음을 확인하고, 검사기 자체 테스트(위험 HTML 6개 검출, 안전 HTML 3개 통과)도 둔다 (FR-040·041·043, SC-003).
- [ ] T013 [P] 정상 문법 코퍼스 테스트 `backend/src/test/java/com/team/blog/shared/markdown/ContentRendererSyntaxTest.java` + 기대 HTML `backend/src/test/resources/markdown/syntax/`(docs/12 §9-2 13개): `#`→`<h2 id="h-원인">`, `######`→`h6`, 같은 제목 `-1`·`-2`, 본문에 `h1` 없음(FR-042); 굵게·기울임·취소선·인라인 코드; 표 `align`; 체크리스트 `<input type="checkbox" disabled checked>`(FR-045); 코드 블록 `class="language-java"` + 내용 이스케이프; 내부 링크(`/`로 시작) `rel`·`target` 없음, 외부 링크 `target="_blank" rel="noopener noreferrer nofollow ugc"`(FR-043); 작성자 사진은 공개 주소 `img` + `loading="lazy" decoding="async"`, `legacy-base-urls`로 쓴 작성자 사진도 지금 공개 주소로 바뀜, 외부 이미지·남이 올린 우리 사진은 `<a …>[이미지] 대체글</a>`(대체글 없으면 주소)(FR-044); `<b>직접 쓴 HTML</b>`은 글자로 보임(FR-040).
- [ ] T014 [P] 부하 제한 테스트 `backend/src/test/java/com/team/blog/shared/markdown/ContentRendererLimitsTest.java`: 인용 25단계·목록 25단계 → `ContentTooComplexException`(메시지 "글 구조가 너무 복잡해요 (목록·인용은 20단계까지)"), 인용 15단계 허용, 100,000자 본문 렌더링 1초 이내(SC-009), 렌더링이 `blog.markdown.render-timeout`을 넘으면(테스트에서 느린 변환기 주입) 예외 + 작업 취소 (FR-046, A-10, B-7).
- [ ] T015 [P] 요약·썸네일 테스트 `backend/src/test/java/com/team/blog/shared/markdown/ExcerptAndThumbnailTest.java`: 코드 블록·이미지·표 제외, 제목·문단·목록 글자만, 인라인 코드·링크는 글자로, 줄바꿈·연속 공백 → 공백 하나, 200자에서 단어 중간이면 그 단어 앞에서 자름, 코드만 있는 글 → 빈 요약(FR-027, B-6); 썸네일 = 본문 첫 번째 작성자 사진의 `thumb_storage_key` 공개 주소, 썸네일 없는 옛 사진이면 원본 주소, 남의 사진·외부 사진은 건너뜀, 사진 없으면 null(FR-028, A-12); `ownedImageKeys`는 본문 순서.
- [ ] T016 [P] Redis 보관소 정리 통합 테스트 `backend/src/test/java/com/team/blog/post/integration/RedisAutosaveStoreReleaseIT.java`: `autosave-release.lua`(입력 확인한 버전 v0, 새 DB 버전 v1) — Redis `version ≤ v0`이면 키 DEL + `autosave:dirty`에서 SREM, `version > v0`이면 지우지 않고 `version = v1 + 1`로 다시 매기며 dirty 유지(B-3 ④·⑤, DM §4); 키가 없으면 아무 일 없음; `find(postId)`가 Hash를 `AutosaveEntry`로 읽음.
- [ ] T017 [P] Redis 장애 판정·요청 제한 테스트 `backend/src/test/java/com/team/blog/shared/redis/RedisGuardAndRateLimiterIT.java`: CircuitBreaker를 강제로 연 상태에서 `RedisGuard.execute`가 대체 경로를 부름, `OOM` 오류는 실패로 세지 않고 `AutosaveUnavailableException`으로 바뀜(B-5, FR-018); `RateLimiter.tryAcquire("ratelimit:autosave:{id}", 1, 5s)` 두 번째 → 거부 + `retryAfterSeconds`, 창이 지나면 허용; Redis 장애 시 통과(경고 로그).

### Implementation for Foundational

- [ ] T018 [P] `backend/src/main/java/com/team/blog/post/domain/PostStatus.java`(`DRAFT`, `PUBLISHED`)와 `backend/src/main/java/com/team/blog/post/domain/Post.java` JPA 엔티티: DM §1-1의 모든 컬럼 매핑 — `title` varchar(100), `content_md` text "≤ 100,000자", `content_html`, `excerpt` varchar(200) NULL, `thumbnail_url` varchar(500) NULL, `status`, `visibility`(004 `Visibility`, `@Enumerated(STRING)`), `edit_version` bigint(**`@Version` 금지**, 05 J-3), `render_version` int, `published_at`·`first_public_at`·`edited_at`·`created_at`·`updated_at`, `deleted_at`·`hidden_at`·`hidden_by`·`hidden_reason`(매핑만, 이 기능은 바꾸지 않음), 반응 수 3개는 `insertable=false, updatable=false`(05 J-2). 팩토리 `Post.newDraft(authorId, visibility, title, contentMd, now)`(edit_version 0), `isDraft()`, `isPublished()`, `isEmptyDraft()`(T020 위임). 발행 → 임시글 전이 메서드는 두지 않는다(P-1, FR-033). 클래스에 `@SQLRestriction("deleted_at IS NULL")`을 건다(004·006이 기대하는 매핑 — 휴지통 글은 엔티티 조회에서 빠지고, 휴지통 포함 조회는 006 `TrashPostRepository`, 002의 1분 반영은 네이티브 SQL T077). 004·006이 메서드를 추가할 수 있게 클래스 주석에 소유·확장 규칙을 적는다.
- [ ] T019 [P] `backend/src/main/java/com/team/blog/post/domain/PostDraft.java` JPA 엔티티: `post_id` PK(= FK → post ON DELETE CASCADE), `title` varchar(100) 기본 `''`, `content_md` text "≤ 100,000자(`ck_post_draft_content`)", `edit_version` bigint, `created_at`(수정 시작), `updated_at`(마지막 저장 = 에디터 `savedAt`) (DM §1-2).
- [ ] T020 [P] `backend/src/main/java/com/team/blog/post/domain/EmptyDraftPolicy.java`: `static boolean isEmpty(String title, String contentMd)` — 공백 문자 집합 상수(`WHITESPACE = " \t\r\n"`)로 앞뒤를 지운 뒤 둘 다 빈 문자열이면 참. 같은 상수를 SQL용 문자열로도 노출해 T115 배치 SQL이 `btrim(title, :ws)`로 같은 기준을 쓰게 한다. 006(13 D-2)과 공용(B-9).
- [ ] T021 `backend/src/main/java/com/team/blog/post/infra/PostRepository.java`(`findForUpdateByIdAndAuthorId(id, authorId)` — `@Lock(PESSIMISTIC_WRITE)`, `WHERE id = :id AND author_id = :me AND deleted_at IS NULL`, 004 plan과 같은 이름)와 `backend/src/main/java/com/team/blog/post/infra/PostDraftRepository.java`(`findById`, `deleteByPostId`) (A-6 ③, A-15). T018·T019 이후.
- [ ] T022 `backend/src/main/java/com/team/blog/post/application/PostDraftQueryService.java`: 다른 모듈(005 상세·006 관리)용 공개 읽기 메서드 `Optional<Instant> findSavedAt(long postId)`(작업본 `updated_at`, 없으면 empty)와 `Set<Long> postIdsWithDraft(Collection<Long> postIds)`(묶음 1쿼리, N+1 금지). T021 이후.
- [ ] T023 [P] 렌더러 API `backend/src/main/java/com/team/blog/shared/application/markdown/`: `ContentRenderer.java`(`RenderedContent render(String contentMd, ImageContext ctx)`), `RenderedContent.java`(`html, excerpt, ownedImageKeys(본문 순서), thumbnailUrl, renderVersion`), `ImageContext.java`(`ownerMemberId` — 발행·다시 렌더링은 글 작성자, 미리보기는 로그인한 본인, FR-044), `RenderVersion.java`(`CURRENT = 1` 코드 상수, A-11), `ContentTooComplexException.java`(400 `CONTENT_TOO_COMPLEX`).
- [ ] T024 [P] 사진 판별 포트 `backend/src/main/java/com/team/blog/shared/application/markdown/ImageReferenceResolver.java`: `Optional<String> keyOf(String url)`(공개 주소·옛 주소 목록으로 시작하고 나머지가 `images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}` 모양일 때만, docs/12 §6), `Map<String, OwnedImage> findOwned(Collection<String> keys, long ownerId)`(`OwnedImage(storageKey, thumbStorageKey)`), `String publicUrlOf(String storageKey)` (plan Structure Decision: shared가 media에 의존하지 않음).
- [ ] T025 사진 판별 임시 구현 `backend/src/main/java/com/team/blog/media/infra/ImageReferenceResolverAdapter.java`(`// TODO(003): 교체`): `keyOf`는 001 `ImageProperties`의 `public-base-url`·`legacy-base-urls`와 위 정규식, `findOwned`는 JdbcTemplate 한 번 `SELECT storage_key, thumb_storage_key FROM image WHERE storage_key = ANY(:keys) AND uploader_id = :ownerId`(DM §1-4), `publicUrlOf` = `public-base-url + "/" + key`. T024 이후.
- [ ] T026 `backend/src/main/java/com/team/blog/shared/infra/markdown/CommonmarkFactory.java`: `Parser`(GFM 표·취소선·체크리스트·자동 링크 확장만, 각주·수식·Mermaid·임베드 없음 — FR-003·S-7)와 `HtmlRenderer`(`escapeHtml(true)`, `sanitizeUrls(true)`, T028 `AttributeProviderFactory` 등록)를 싱글턴으로 만든다 (A-9). T023 이후.
- [ ] T027 `backend/src/main/java/com/team/blog/shared/infra/markdown/AstTransformer.java`: ① 목록·인용 중첩 깊이 검사(`blog.markdown.max-nesting` 초과 → `ContentTooComplexException`) ② 제목 한 단계 낮춤(1→2 … 5→6, 6은 6) ③ 제목 `id` = `h-` + 글자·숫자·`_`·`-`만(1~100자), 같은 이름은 `-1`·`-2` ④ 이미지: `ImageReferenceResolver.keyOf` → `findOwned`(본문 키 묶음 1회) → 작성자 사진이면 `destination`을 지금 공개 주소로 바꾸고 키를 순서대로 수집, 아니면 `Image` 노드를 텍스트 "[이미지] " + 대체글(없으면 주소)인 `Link` 노드로 교체 (FR-042·044, docs/12 §3·§6·§7-3). 원문 `content_md`는 바꾸지 않는다. T023·T024 이후.
- [ ] T028 [P] `backend/src/main/java/com/team/blog/shared/infra/markdown/LinkAttributeProvider.java`: `a`의 `href`가 `http:`/`https:` 절대 주소면 `target="_blank"`·`rel="noopener noreferrer nofollow ugc"`, `/`로 시작하는 상대 경로(`//` 제외)·`mailto:`는 추가 속성 없음; `img`에 `loading="lazy"`·`decoding="async"` (FR-043, docs/12 §5).
- [ ] T029 [P] `backend/src/main/java/com/team/blog/shared/infra/markdown/SanitizerPolicy.java`: docs/12 §4 `PolicyFactory` 코드를 그대로 옮기고 `PUBLIC_BASE_URL`은 001 `ImageProperties.publicBaseUrl`을 주입받는다(CSP와 같은 설정값 하나, H3) (FR-041).
- [ ] T030 [P] `backend/src/main/java/com/team/blog/shared/infra/markdown/ExcerptExtractor.java`: 변환 후 AST에서 요약 추출(B-6) — 코드 블록·이미지·표 노드 통째로 제외, 제목·문단·목록의 텍스트·인라인 코드·링크 글자, 직접 쓴 HTML은 글자 그대로, 공백 정리, 200자 + 단어 경계 자르기 (FR-027).
- [ ] T031 `backend/src/main/java/com/team/blog/shared/infra/markdown/DefaultContentRenderer.java`(`ContentRenderer` 구현)와 `backend/src/main/java/com/team/blog/shared/infra/markdown/RenderExecutorConfig.java`(전용 고정 풀 `renderExecutor`, 크기 `blog.markdown.render-threads`): parse → `AstTransformer` → `HtmlRenderer` → `SanitizerPolicy` → `ExcerptExtractor` → 썸네일 결정(첫 작성자 사진의 `thumbStorageKey`, 없으면 원본 키의 공개 주소) 전체를 `renderExecutor`에서 실행하고 `Future.get(render-timeout)` 초과 시 취소 + `ContentTooComplexException`(B-7). 발행·미리보기·다시 렌더링이 이 빈 하나만 쓴다(FR-040 "하나의 렌더러"). T026~T030 이후. 이 작업으로 T012~T015가 통과해야 한다.
- [ ] T032 [P] `backend/src/main/java/com/team/blog/shared/infra/redis/RedisGuard.java`: Resilience4j `CircuitBreaker("redis")`로 Redis 호출을 감싸는 `<T> T execute(Supplier<T> call, Supplier<T> fallback)`, `isOpen()`; 연결 실패·타임아웃만 실패로 세고 `OOM` 응답은 기록하지 않고 `AutosaveUnavailableException`으로 던진다(B-5, FR-018).
- [ ] T033 `backend/src/main/resources/redis/autosave-release.lua`와 `backend/src/main/java/com/team/blog/post/infra/RedisAutosaveStore.java`(1부): `Optional<AutosaveEntry> find(long postId)`(`autosave:post:{postId}` Hash), `void release(long postId, long checkedVersion, long newDbVersion)`(Lua: `version ≤ v0` → DEL + SREM `autosave:dirty`, 크면 `version = v1 + 1` + dirty 유지), `void delete(long postId)`(무조건 DEL + SREM — 006 완전 삭제 커밋 후용), 모든 호출은 `RedisGuard` 경유. 값 객체 `backend/src/main/java/com/team/blog/post/infra/AutosaveEntry.java`(`memberId, title, contentMd, version, savedAt`). 트랜잭션 안에서 호출되면 경고(05 J-5, T119에서 강제). T032 이후.
- [ ] T034 `backend/src/main/java/com/team/blog/shared/infra/ratelimit/RateLimiter.java`: Redis 고정 창 카운터(`INCR` + 첫 증가 때 `EXPIRE`)로 `Result tryAcquire(String key, int limit, Duration window)`(`allowed`, `retryAfterSeconds` = 남은 TTL), Redis 장애 시 허용 + 경고 로그(B-5, 02 §2-1). 001의 `account/infra/redis/RateLimiter`가 같은 일을 하면 새로 만들지 말고 그것을 `shared`로 옮기는 것을 001 담당과 정한다(Notes 참고). T032 이후.

**Checkpoint**: Foundation ready — `./mvnw -f backend/pom.xml test -Dtest='*Markdown*,ContentRenderer*,ExcerptAndThumbnailTest,PostPersistenceIT,RedisAutosaveStoreReleaseIT,RedisGuardAndRateLimiterIT,EmptyDraftPolicyTest'` 통과. user story implementation can now begin

---

## Phase 3: User Story 1 - 새 글을 쓰고 발행한다 (Priority: P1) 🎯 MVP

**Goal**: 인증된 회원이 [새 글]로 임시글을 만들고 제목·Markdown 본문을 써서 태그·공개 범위를 골라 발행하면, 전체 공개 글은 `/@주소/posts/{번호}`에서 누구나 읽는다 (C-POST-1, C-POST-3, FR-001·002·004·005·026~034·036·038·039).

**Independent Test**: 회원으로 새 글 → 제목·본문 입력 → 발행 → 비로그인으로 글 주소 조회(읽기 성공) (QS §4-1, §4-2, §4-3).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T035 [P] [US1] `backend/src/test/java/com/team/blog/post/unit/TitleNormalizerTest.java`: NFD 한글 → NFC, U+200B~U+200F·U+2060~U+2069·U+FEFF·U+202A~U+202E(U+202E 방향 뒤집기 포함)·제어 문자 제거, 앞뒤 공백 제거, 정리 후 0자 → `TITLE_REQUIRED`, 101자 → `TITLE_TOO_LONG` (FR-004, docs/12 §7-4·§9-3 제목 항목).
- [ ] T036 [P] [US1] `backend/src/test/java/com/team/blog/post/unit/PublishValidatorTest.java`: 실패 항목을 **모두** 모음 — 빈 제목 + `![대기](local:7f3e)` 본문 → `title/TITLE_REQUIRED`와 `contentMd/PENDING_IMAGES` 둘 다(US1 #3, QS §4-2); 공백뿐인 본문 → `CONTENT_REQUIRED`; 100,001자 → `CONTENT_TOO_LONG`; 태그 11개(`blog.post.max-tags=10`) → `TOO_MANY_TAGS`; 형식 오류 태그 → `tags[i]/INVALID_TAG`(+ `value`); `VisibilityRegistry`에 없는 값 → `visibility/INVALID_VISIBILITY` (FR-026, DM §3).
- [ ] T037 [P] [US1] `backend/src/test/java/com/team/blog/post/unit/PostPublishRulesTest.java`: `Post.publish(cmd, rendered, now)` 시각 규칙(DM §2 의사 코드) — PRIVATE 최초 발행: `published_at=now`, `first_public_at=null`, `edited_at=null`, `firstPublish=true`, `wentPublic=false`; 이후 PUBLIC으로 다시 발행: `first_public_at=now`, `edited_at=now`, `wentPublic=true`(US1 #4); PUBLIC 최초 발행: 둘 다; 이미 공개된 글을 다시 발행: `first_public_at` 유지, `wentPublic=false`; 매번 `edit_version = 현재 + 1`, `status=PUBLISHED`, `render_version` 기록 (FR-031·032).
- [ ] T038 [P] [US1] `backend/src/test/java/com/team/blog/post/integration/CreatePostIT.java`: 기본 공개 범위 PRIVATE 회원의 `POST /api/posts` → 201, `Location: /api/posts/{postId}/working-copy`, 본문 `{postId, status: DRAFT, version: 0, visibility: PRIVATE, title: "", contentMd: ""}`, DB에 그 회원 소유 DRAFT 행 1개(US1 #1, FR-001); 본문 `{title, contentMd}`를 주면 그 내용으로 생성(FR-024 대비); 제목 101자 → 400 `TITLE_TOO_LONG`; 비회원 401 `LOGIN_REQUIRED`, 인증 전 403 `EMAIL_NOT_VERIFIED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, CSRF 헤더 없음 403.
- [ ] T039 [P] [US1] `backend/src/test/java/com/team/blog/post/integration/PublishIT.java`: (US1 #2) 제목·본문을 채운 임시글을 `PUBLIC`으로 발행 → 200 `{url: "/@{handle}/posts/{id}", publishedAt, firstPublicAt, editedAt: null, version}`, DB `status=PUBLISHED`·`content_html`(정화됨)·`excerpt`·`thumbnail_url`·`render_version=RenderVersion.CURRENT`·`post_tag` 입력 순서, 004 `PostReadService.requireReadable(postId, Viewer.anonymous())`가 성공(005가 있으면 `GET /@{handle}/posts/{id}` 비로그인 200도 확인); (US1 #3) 빈 제목 + 공백 본문 + `local:` 사진 → 400 `VALIDATION_FAILED` + `errors` 3개, 글은 DRAFT·`edit_version` 그대로; (US1 #4) PRIVATE 발행 후 PUBLIC으로 다시 발행 → `first_public_at` = 두 번째 발행 시각, 다시 PRIVATE→PUBLIC 해도 그 값 유지; `baseVersion` 불일치 → 409 `VERSION_CONFLICT` + `details.server{title,contentMd,version,savedAt}`, 글 불변(FR-020); Redis 보관분 버전이 DB보다 크면 그 버전이 "현재 버전"(A-6 ④); (US1 #6) 본문에 남이 올린 우리 저장소 사진 → `post_image` 연결 없음, `content_html`에서 `[이미지] …` 링크, 원래 주인 글의 `post_image`는 그대로(FR-029, C-POST-3 #9); 작성자 사진은 `post_image` 연결 + `image.status='ATTACHED'`, 다시 발행 때 빠진 사진은 `detached_at` 기록; 렌더링 실패(`CONTENT_TOO_COMPLEX`)는 트랜잭션 전에 400.
- [ ] T040 [P] [US1] `backend/src/test/java/com/team/blog/post/integration/PublishTransactionIT.java`: 최초 PUBLIC 발행 → 커밋 후 `PostPublished` + `PostWentPublic` 각 1번, PRIVATE 최초 발행 → `PostPublished`만(contracts/events.md §1 조합표); 트랜잭션 안 단계(태그 확정 stub이 예외)에서 실패하면 롤백되어 이벤트 0건·`post` 불변·Redis `autosave:post:{id}` 그대로(05 §7, FR-030 "저장 실패 시 보관분을 지우지 않음"); 성공 시 커밋 후 Redis 키가 `version ≤ 확인한 버전`이면 삭제(⑨); 이벤트 리스너가 예외를 던져도 발행 응답 200(원칙 V, FR-039).
- [ ] T041 [P] [US1] `backend/src/test/java/com/team/blog/post/integration/WorkingCopyReadIT.java`: 작성자의 `GET /api/posts/{id}/working-copy` — 임시글이면 `post`의 내용, Redis 보관분 버전이 더 크면 Redis의 제목·본문·버전·`savedAt`(max(Redis, post_draft, post), B-1), `tags`·`visibility`·`status`·`editing=false`·`url=null`; 남의 글·없는 글·휴지통 글 404 `NOT_FOUND`(본문 동일), 비회원 401.
- [ ] T042 [P] [US1] 권한 매트릭스: 004 하네스(004 T022)에 002 행동 실행기 `backend/src/test/java/com/team/blog/post/integration/permission/PostAuthoringPermissionActions.java`(`PermissionAction` 구현: 새 글·에디터 열기·발행)를 등록하고 `backend/src/test/resources/permission/post-write.csv`의 002 대기 행을 채운다: 행동 {새 글, 발행} × 행위자 {비회원 401 `LOGIN_REQUIRED`, 인증 전 403 `EMAIL_NOT_VERIFIED`, 정지 403 `ACCOUNT_SUSPENDED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 다른 회원 404, 관리자(남의 글) 404}, 행동 {에디터 열기(읽기, 계정 상태 판정 없음)} × {비회원 401, 다른 회원 404, 관리자 404}, 작성자의 휴지통 글은 모든 행동 404; 매 행 요청 전후 스냅샷(`title`·`content_md`·`status`·`edit_version`·`updated_at`) 동일 (US1 #5, FR-036, SC-008, 42 §5-2). 요청 본문에 `authorId`를 넣어도 무시.

### Implementation for User Story 1

- [ ] T043 [P] [US1] `backend/src/main/java/com/team/blog/post/domain/TitleNormalizer.java`: `String normalize(String raw)` — `Normalizer.normalize(NFC)` → U+200B~U+200F, U+2060~U+2069, U+FEFF, U+202A~U+202E, `Character.isISOControl` 제거 → `strip()` (FR-004).
- [ ] T044 [P] [US1] `backend/src/main/java/com/team/blog/post/domain/PublishCommand.java`(`postId, memberId(세션), title(정리됨), contentMd, rawTags, visibility, baseVersion, idempotencyKey`), `backend/src/main/java/com/team/blog/post/domain/PublishResult.java`(`url, publishedAt, firstPublicAt, editedAt, version, firstPublish, wentPublic`), `Post.publish(PublishCommand, RenderedContent, Instant now)` 도메인 메서드 추가(`backend/src/main/java/com/team/blog/post/domain/Post.java`): DM §2 시각 규칙, `edit_version = 현재 버전 + 1`(현재 버전은 인자), 본문·HTML·요약·썸네일·공개 범위·`render_version`·`updated_at` 갱신, 반응 수·`hidden_*`는 건드리지 않음 (05 J-1, A-7).
- [ ] T045 [US1] `backend/src/main/java/com/team/blog/post/domain/PublishValidator.java`: 05 §4 항목별 오류 수집 — 제목(T043 정리 후 1~100), 본문(strip 후 ≥1, ≤100,000), 업로드 대기 사진(본문에 `local:` 주소, 003 규칙), 태그(개수 ≤ `blog.post.max-tags`, 형식은 `TagService.validate` 위임), 공개 범위(004 `VisibilityRegistry.require(raw, "visibility")`) → 하나라도 있으면 `PublishValidationException`(FR-026). T043 이후.
- [ ] T046 [P] [US1] 태그 임시 구현 `backend/src/main/java/com/team/blog/tag/application/TagService.java`(`// TODO(008): 008 정규화로 교체`): `List<FieldError> validate(List<String> rawTags)`(소문자·앞뒤 공백 제거·중복 제거 후 각 1~30자, 위반 시 `tags[i]/INVALID_TAG`), `void replacePostTags(long postId, List<String> rawTags)`(발행 트랜잭션 안: `INSERT INTO tag(name) … ON CONFLICT (name) DO NOTHING` 묶음 1회 → `post_tag` 삭제 후 입력 순서 `position` 0부터 묶음 INSERT, N+1 금지), `List<String> tagNamesOf(long postId)` (DM §1-3).
- [ ] T047 [P] [US1] 사진 연결 임시 구현 `backend/src/main/java/com/team/blog/media/application/ImageService.java`(`// TODO(003): 003 FR-022 확정 후 교체`): `void syncPostImages(long postId, long authorId, List<String> ownedKeys)` — `uploader_id = authorId`인 키만 `post_image` 연결(없으면 INSERT) + `image.status='ATTACHED'`, 그 글에 연결돼 있었으나 목록에서 빠진 사진은 `detached_at = now()`; 남이 올린 사진은 연결하지 않음 (DM §1-4, A-13).
- [ ] T048 [US1] `backend/src/main/java/com/team/blog/post/application/PostCommandService.java`(1부): `create(memberId, title?, contentMd?)` — 001 `AccountStatusGuard.requireActive(memberId, CONTENT_WRITE)`(403) → 길이 검사(제목 ≤100, 본문 ≤100,000, B-11) 후 `Post.newDraft(memberId, MemberQueryService.defaultVisibility(memberId), …)` 저장, 이벤트 없음 (FR-001, B-1).
- [ ] T049 [US1] `backend/src/main/java/com/team/blog/post/application/EditorQueryService.java`: `WorkingCopy open(postId, memberId)` — `author_id = :me AND deleted_at IS NULL` 조회(없으면 `PostNotFoundException`/404), `post`·`post_draft`·`RedisAutosaveStore.find` 중 버전이 가장 큰 출처의 제목·본문·버전·`savedAt`(Redis `savedAt` / 작업본 `updated_at` / 글 `updated_at`), `editing` = 발행 글이고 작업본 또는 더 큰 Redis 보관분이 있음, `tags` = `TagService.tagNamesOf`, 발행 글이면 `url` (FR-034, B-1, contracts `WorkingCopy`). Redis 장애면 DB만.
- [ ] T050 [US1] `backend/src/main/java/com/team/blog/post/application/PublishService.java`: 05 §7 순서 — 트랜잭션 밖 ⓪ 001 `AccountStatusGuard.requireActive(memberId, CONTENT_WRITE)` ① `PublishValidator` ② `ContentRenderer.render(contentMd, ImageContext(작성자))`(`ContentTooComplexException`은 `contentMd/CONTENT_TOO_COMPLEX` 오류로 400) → `TransactionTemplate` 안 ③ `PostRepository.findForUpdateByIdAndAuthorId`(없으면 404) ④ 현재 버전 = max(Redis `find`, `post_draft.edit_version`, `post.edit_version`) ≠ `baseVersion`이면 `VersionConflictException(ServerCopy)` ⑤ `TagService.replacePostTags` ⑥ `ImageService.syncPostImages(postId, authorId, rendered.ownedImageKeys)` ⑦ `post.publish(...)` ⑧ `post_draft` 삭제 + 이벤트 `publishEvent`(최초면 `PostPublished`, 아니면 `PostEdited`, `wentPublic`이면 `PostWentPublic`) → 커밋 후 같은 스레드에서 ⑨ `RedisAutosaveStore.release(postId, 확인한 버전, 새 버전)`(실패는 경고 로그) → `PublishResult` 반환. 트랜잭션 안에서 Redis·외부 호출 금지(05 J-5), `@Version` 쓰지 않음 (FR-030~032·038·039, A-6, A-16). ⑪ 멱등 응답 저장은 US6(T111)에서 붙인다.
- [ ] T051 [US1] `backend/src/main/java/com/team/blog/post/web/PostEditorController.java` + DTO `backend/src/main/java/com/team/blog/post/web/dto/`(`CreatePostRequest`, `WorkingCopyResponse`): `POST /api/posts` → 201 + `Location`, `GET /api/posts/{postId}/working-copy` → 200; 현재 사용자는 001 `CurrentUser`에서만, 요청 본문의 작성자 필드는 받지 않음 (contracts/openapi.yaml `createPost`·`getWorkingCopy`, 원칙 III).
- [ ] T052 [US1] `backend/src/main/java/com/team/blog/post/web/PublishController.java` + DTO(`PublishRequest`, `PublishResponse`): `POST /api/posts/{postId}/publish`, `Idempotency-Key` 헤더 필수(없음 400 `IDEMPOTENCY_KEY_REQUIRED`, UUID 아님 400 `INVALID_IDEMPOTENCY_KEY`, B-8) — 중복 판정은 US6에서 추가 (contracts `publishPost`, FR-038).
- [ ] T053 [P] [US1] `frontend/src/api/posts.ts`: contracts/openapi.yaml 타입(`WorkingCopy`, `SaveRequest`, `SaveResponse`, `PublishRequest`, `PublishResponse`, `ServerCopy`, `ErrorResponse`)과 `createPost(body?)`, `getWorkingCopy(postId)`, `publishPost(postId, body, idempotencyKey)`를 001 `frontend/src/api/client.ts`(CSRF 헤더) 위에 추가한다(004의 `setVisibility`와 같은 파일 — 기존 함수는 건드리지 않음).
- [ ] T054 [US1] `frontend/src/pages/EditorPage.tsx`와 라우트 등록(001 프런트 골격의 라우터 파일): `/write/new`는 `createPost()` 후 `/write/{postId}`로 교체 이동, `/write/{postId}`는 `getWorkingCopy`로 제목(최대 100자)·Markdown 본문 입력(에디터 종류는 자유, 상태는 `title`·`contentMd` 문자열만 — B-14), 비로그인 401은 로그인 화면으로; `frontend/src/components/editor/NewPostButton.tsx`([새 글] 버튼, 공통 머리말 배치는 001/005 화면 담당) (FR-001·002, B-1).
- [ ] T055 [US1] `frontend/src/components/editor/PublishDialog.tsx`: 태그 입력(최대 `max-tags`, 임시 칩 입력 — 008 화면이 교체), 004 `VisibilitySelect`로 공개 범위(초기값 = working copy `visibility`), [발행] → `publishPost`(시도마다 `crypto.randomUUID()` 키), 400 `errors[]`를 `field`별로 각 입력칸 옆에 모두 표시(`title`은 에디터 제목칸, `contentMd`는 본문칸, `tags[i]`는 해당 칩), 성공 시 응답 `url`로 이동 (FR-026·038, US1 #2·#3).

**Checkpoint**: User Story 1 단독 동작 — QS §4-1·§4-2·§4-3 curl 시나리오와 T035~T042 통과

---

## Phase 4: User Story 2 - 본문의 스크립트는 실행되지 않는다 (Priority: P1)

**Goal**: 무엇을 써도 독자 화면에서 스크립트가 실행되지 않고, 외부 링크·이미지는 안전하게 바뀌며, 미리보기와 발행 결과가 같다 (C-POST-1, FR-040~049).

**Independent Test**: docs/12 §9-1 공격 문자열 32개를 본문·제목에 넣고 발행한 뒤 결과에 실행 가능한 스크립트가 없고 브라우저에서 알림창이 뜨지 않는다 (QS §4-6, QS §5 마지막 행).

### Tests for User Story 2 ⚠️

- [ ] T056 [P] [US2] `backend/src/test/java/com/team/blog/post/integration/MarkdownPreviewIT.java`: `POST /api/markdown/preview {contentMd}` → 200 `{html}`(QS §4-6 기대값: `&lt;script&gt;` 글자, `javascript` 링크 없음, spring.io 링크 `target`·`rel`, 외부 이미지 → `[이미지] e` 링크, `<h2 id="h-제목">`, `h1` 없음); 비회원 401, **인증 전 회원도 200**(B-12); 1분 61번째 → 429 `RATE_LIMITED` + `Retry-After`; 25단계 인용 → 400 `CONTENT_TOO_COMPLEX`; 100,001자 → 400 `CONTENT_TOO_LONG`; 사진 판별 기준은 로그인한 본인(남의 사진은 링크) (FR-047, docs/12 §7-6).
- [ ] T057 [P] [US2] `backend/src/test/java/com/team/blog/post/integration/PreviewPublishParityIT.java`: T013 정상 문법 13개 + 작성자 사진·외부 사진 본문 각각에 대해 미리보기 `html`과 발행 후 `post.content_html`이 바이트 단위로 같음 (SC-004, US2 #6).
- [ ] T058 [P] [US2] `backend/src/test/java/com/team/blog/post/integration/PublishSanitizeIT.java`: 32개 공격 문자열을 본문에 넣어 발행 → `content_html`이 T012 `HtmlSafetyChecker` 통과, 같은 문자열을 제목에 넣어 발행 → `post.title`은 글자 그대로 저장(HTML 렌더링 안 함, FR-004 정리만)(US2 #1·#2); 인용 25단계 → 400 `errors[0].code=CONTENT_TOO_COMPLEX`·글 DRAFT 유지, 15단계 → 200(US2 #7); 100,000자 본문 발행 1초 이내(SC-009); `#`~`######`·표·체크리스트·코드 블록 → 기대 HTML, `h1` 없음(US2 #5).
- [ ] T059 [P] [US2] `backend/src/test/java/com/team/blog/post/integration/SecurityHeadersIT.java`: `/api/markdown/preview`, `/api/posts/{id}/publish`, SPA 진입 `/`·`/write/1` 응답에 `Content-Security-Policy: default-src 'self'; script-src 'self'; connect-src 'self' {공개 주소 출처}; img-src 'self' {공개 주소 출처} data: blob:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`이 있고 `{공개 주소 출처}`가 `blog.image.public-base-url`의 출처와 같음 (FR-049, QS §2; 필터 구현은 001).
- [ ] T060 [P] [US2] `frontend/e2e/xss.spec.ts`(Playwright): 32개 공격 문자열 글을 API로 발행하고 005 글 상세 화면과 에디터 미리보기를 열어 `page.on('dialog')` 0건, 외부 링크 클릭 시 새 탭 + `window.opener === null`, 외부 이미지가 `<img>`가 아닌 링크로 보임 (SC-003, US2 #1·#3·#4). 005 상세가 없으면 미리보기만 검증하고 상세 단언은 `test.fixme`로 둔다.

### Implementation for User Story 2

- [ ] T061 [US2] `backend/src/main/java/com/team/blog/post/application/MarkdownPreviewService.java`와 `backend/src/main/java/com/team/blog/post/web/MarkdownPreviewController.java`: `POST /api/markdown/preview` — 로그인만 확인(401, 계정 상태 403 판정 없음 — B-12), `RateLimiter.tryAcquire("ratelimit:preview:{memberId}", 60, 1m)` 거부 시 429 + `Retry-After`, 길이 ≤100,000(초과 400 `CONTENT_TOO_LONG`), `ContentRenderer.render(contentMd, ImageContext(로그인한 본인))`의 `html`만 반환 (FR-047, contracts `previewMarkdown`).
- [ ] T062 [P] [US2] `frontend/src/features/markdown/highlightCode.ts`: 번들에 포함한 `highlight.js`(우리 서버 제공, CSP `script-src 'self'`)로 컨테이너 안 `pre > code[class^="language-"]`를 강조하고, 언어 미지원·예외 시 try/catch로 원래 이스케이프된 코드를 그대로 둔다(FR-045, docs/12 §7-2). 005 상세 화면도 이 함수를 쓴다.
- [ ] T063 [US2] `frontend/src/components/editor/PreviewPane.tsx`와 `frontend/src/pages/EditorPage.tsx` 연결: 입력이 500ms 멈추면 `POST /api/markdown/preview`(이전 요청 `AbortController`로 취소), 서버가 정화한 `html`을 표시하고 `highlightCode` 적용, "최종 결과는 서버 렌더러 기준" 안내, 400 `CONTENT_TOO_COMPLEX`·429는 패널 안 안내 문구로 표시(편집은 막지 않음) (FR-047, C-POST-1 #5). API 함수 `previewMarkdown`을 `frontend/src/api/posts.ts`에 추가.

**Checkpoint**: User Stories 1 AND 2 동작 — QS §4-6 통과, T056~T060 통과

---

## Phase 5: User Story 3 - 쓰다 만 글을 잃지 않고 이어 쓴다 (Priority: P1)

**Goal**: 입력하면 이 기기에 1초 단위, 서버(Redis)에 3~30초, DB에 1분마다 저장되고, 수동 저장은 즉시 DB에 반영된다. 오프라인에도 이 기기에 남고 연결되면 동기화된다 (C-POST-2, FR-006~019·025).

**Independent Test**: 글을 쓰다 네트워크를 끊고 계속 입력 → 상태 표시 확인 → 복구 후 동기화 → 다른 기기(IndexedDB 비운 브라우저)에서 임시글을 열어 같은 내용 확인 (QS §4-1 자동 저장, §4-7, QS §5).

### Tests for User Story 3 ⚠️

- [ ] T064 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/AutosaveIT.java`: `PUT /api/posts/{id}/autosave {title, contentMd, baseVersion: 0}` → 200 `{version: 1, savedAt}`, Redis Hash 필드 5개·TTL ≈ 24h(저장마다 연장)·`autosave:dirty`에 postId, DB는 아직 그대로(US3 #1 서버 부분); 같은 `baseVersion`으로 다시 → 409 `VERSION_CONFLICT` + `details.server.version=1`(QS §4-1, FR-020); 늦게 온 옛 `baseVersion` 요청이 새 버전을 덮지 않음(FR-016); 남의 글·없는 글 404, **Redis 키가 있어도** 휴지통 글 404(B-3 ①, 006 FR-023); 제목 101자·본문 100,001자 → 400 `TITLE_TOO_LONG`/`CONTENT_TOO_LONG`(B-11); `local:` 사진 주소 포함 본문은 200(FR-025); 태그 필드를 보내도 무시(FR-006); 발행 글에 대한 자동 저장도 Redis에만 쓰고 `post` 불변.
- [ ] T065 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/AutosaveLimitsIT.java`: 5초 안 두 번째 요청 → 429 `RATE_LIMITED` + `Retry-After`, 판정 순서 401 → 403 → 429 → 404 → 400 → 409(QS §4-7: 첫 요청 409, 두 번째 429); 1,100,000바이트 본문 → 413 `PAYLOAD_TOO_LARGE`이며 컨트롤러·JSON 파싱 전에 거부(`Content-Length` 없는 chunked 요청도 읽으면서 세어 거부) (FR-011, B-2).
- [ ] T066 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/ManualSaveIT.java`: 임시글 `PUT /api/posts/{id}/working-copy {title, contentMd, baseVersion}` → 200 `{version, savedAt}`, 같은 요청 안에서 `post.title`·`content_md`·`edit_version`·`updated_at` 즉시 반영(FR-009, D-3); Redis 키를 지운 뒤(24h 만료 모사) `GET working-copy`가 같은 내용(US3 #3·#6, FR-008); 발행 글 수동 저장 → `post_draft` 생성·`post` 불변(FR-015); 수동 저장 후 다른 탭의 같은 `baseVersion` 자동 저장 → 409(B-3 ②, 같은 버전 번호 경쟁 없음); 수동 저장 때 `ImageService.syncPostImages` 호출(003 FR-022); 남의 글·휴지통 글 404.
- [ ] T067 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/AutosaveFlushJobIT.java`: dirty 임시글 → `UPDATE post … WHERE status='DRAFT' AND edit_version < :v` 반영, 발행 글 → `post_draft` UPSERT `WHERE post_draft.edit_version < EXCLUDED.edit_version` 그리고 `post.edit_version < :v`일 때만(B-3 ③); 반영 후 Redis 키는 남고 dirty에서 SREM(반영 중 키 버전이 바뀌었으면 dirty 유지); **휴지통 글도 반영**(`deleted_at` 조건 없음, B-3 ⑥); 완전 삭제된 글(행 없음·FK 23503)이면 Redis 키·dirty 항목 삭제; 한 글 실패가 다른 글 반영을 막지 않음; 반영 때 사진 연결(003 FR-022) 호출; ShedLock 이름 `autosave-flush` (FR-007 ③, EV §3).
- [ ] T068 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/AutosaveRedisOutageIT.java`(Redis 컨테이너를 공유하지 않도록 CircuitBreaker `transitionToForcedOpenState()`로 모사): 자동 저장 200이고 DB에 바로 저장(`SELECT … FOR UPDATE` + DB 버전 확인), 버전 불일치는 여전히 409; 요청 제한은 통과(경고 로그); `AutosaveFlushJob`은 그 회차 건너뜀; Redis `OOM` 응답 모사 → 503 `AUTOSAVE_UNAVAILABLE`(밀어내지 않음) (FR-018, B-5, QS §3 "Redis 장애").
- [ ] T069 [P] [US3] `backend/src/test/java/com/team/blog/post/integration/AutosaveVersionRaceIT.java`: Redis 장애 동안 DB로 버전이 5까지 오른 뒤 Redis가 돌아왔을 때 옛 Redis 키(version 3)가 있어도 `baseVersion=5` 자동 저장이 200(현재 = max(Redis, DB), B-3 ①); `AutosaveService.flushNow(postId)`가 `deleted_at` 조건 없이 즉시 DB 반영하고 키 정리를 호출한 쪽 트랜잭션의 커밋 후로 등록(EV §4 — 006 계약 테스트).
- [ ] T070 [P] [US3] 권한 매트릭스: `PostAuthoringPermissionActions`(T042)에 자동 저장·수동 저장 실행기를 추가하고 `backend/src/test/resources/permission/post-write.csv` 대기 행을 채운다: 행동 {자동 저장, 수동 저장} × 행위자 {비회원 401, 인증 전 403, 정지 403, 탈퇴 유예 403, 다른 회원 404, 관리자 404}, 작성자의 휴지통 글(Redis 키 있음) 404, 전후 스냅샷과 Redis Hash 동일 (FR-019·036, SC-008). 러너는 T042.
- [ ] T071 [P] [US3] `frontend/src/features/editor/__tests__/localDraftStore.test.ts`(Vitest + fake-indexeddb): 키 `draft:{memberId}:{postId}` 값 `{title, contentMd, baseVersion, dirty, pendingImages, updatedAt}`, 백업 `draft-backup:{memberId}:{postId}` `{title, contentMd, baseVersion, backedUpAt}`; 로그인 정보·토큰 필드 없음; `clearMemberDrafts(memberId)`가 그 회원의 `draft:`·`draft-backup:` 키만 모두 지우고 다른 회원 키는 남김(US3 #7); 7일 지난 백업은 `purgeExpiredBackups()`에서 삭제; 발행 성공·`dirty=false`로 떠날 때 `draft:` 삭제 (FR-014, DM §5).
- [ ] T072 [P] [US3] `frontend/src/features/editor/__tests__/autosaveQueue.test.ts`(가짜 타이머): 입력 1초 멈춤 → 로컬 저장, 3초 더 멈춤 → 서버 전송, 계속 입력 중이면 30초마다 전송, 바뀐 내용이 없으면 전송 안 함; 탭 하나에서 요청은 한 번에 하나(응답 뒤 다음); 실패 시 2s→4s→8s…60s + jitter 재시도, 그동안 로컬 유지; 429는 `Retry-After`만큼 대기; 413은 재시도하지 않고 오류 상태; 200이면 `baseVersion=version`, `dirty=false`; 409면 서버 전송 멈춤 + 충돌 상태로 전환(로컬 저장 계속) (FR-007·010·011·021).
- [ ] T073 [P] [US3] `frontend/src/features/editor/__tests__/lifecycle.test.ts`와 `frontend/src/features/editor/__tests__/openEditor.test.ts`: `visibilitychange`(hidden) → 즉시 전송, `pagehide` → `fetch(…, {keepalive: true})`, 미전송 내용이 있을 때만 `beforeunload` 확인창(US3 #4, FR-012); 에디터 열기 — 로컬 `dirty && baseVersion == 서버 version` → 로컬 내용 + 안내 "이 기기에 저장되지 않은 변경을 불러왔어요"(US3 #5), `dirty && baseVersion != 서버 version` → 충돌(비교 창 열기 요청), dirty 아님 → 서버 내용 (FR-022, DM §5).
- [ ] T074 [P] [US3] `frontend/src/components/editor/__tests__/SaveStatus.test.tsx`: 네 상태를 색이 아닌 글자로 — "✓ 저장됨 14:03" / "● 이 기기에 저장됨 (동기화 대기)" / "⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화" / "⚠ 다른 곳에서 수정됨 — 이 기기에만 저장 중 [비교하기]" (FR-013), `aria-live="polite"`.
- [ ] T075 [P] [US3] `frontend/e2e/autosave-offline.spec.ts`(Playwright): 입력 → 1초 뒤 "● 이 기기에 저장됨 (동기화 대기)" → 3초 뒤 "✓ 저장됨 HH:MM"(US3 #1); `context.setOffline(true)` 후 입력 → 오프라인 문구, `setOffline(false)` → 자동 저장(US3 #2); 미전송 상태에서 페이지 이동 → `beforeunload` 대화상자(US3 #4); 새 브라우저 컨텍스트(IndexedDB 없음)로 같은 글을 열면 서버에 저장된 내용(US3 #3).

### Implementation for User Story 3

- [ ] T076 [US3] `backend/src/main/resources/redis/autosave-save.lua`와 `RedisAutosaveStore.save(...)`(`backend/src/main/java/com/team/blog/post/infra/RedisAutosaveStore.java` 2부): 입력 `memberId, baseVersion, dbVersion, title, contentMd, savedAt, ttl, postId`; 현재 = max(Redis `version`, `dbVersion`); 키가 있고 `memberId` 다르면 거부, `baseVersion ≠ 현재`면 `{0, 현재}`, 같으면 `HSET` 5필드 + `EXPIRE ttl` + `SADD autosave:dirty postId` 후 `{1, 현재+1}` (DM §4, A-4, B-3 ①). 거부 결과에는 409용 Redis 내용을 함께 돌려준다.
- [ ] T077 [P] [US3] 편집 저장 쿼리 추가 `backend/src/main/java/com/team/blog/post/infra/PostEditRepository.java`(네이티브 SQL, JdbcTemplate): `findOwnedEditState(postId, memberId)` = `SELECT p.status, p.edit_version, d.edit_version, p.updated_at, d.updated_at FROM post p LEFT JOIN post_draft d ON d.post_id = p.id WHERE p.id = :id AND p.author_id = :me AND p.deleted_at IS NULL`(PK 1회); `updateDraftPostIfNewer(id, title, contentMd, v)` = `UPDATE post SET title, content_md, edit_version = :v, updated_at = now() WHERE id = :id AND status = 'DRAFT' AND edit_version < :v`; `upsertWorkingCopyIfNewer(id, title, contentMd, v)` = `INSERT INTO post_draft … ON CONFLICT (post_id) DO UPDATE … WHERE post_draft.edit_version < EXCLUDED.edit_version` 그리고 `post.status='PUBLISHED' AND post.edit_version < :v` 조건(B-3 ③, DM §1-2). 반영 쿼리들은 `deleted_at` 조건을 넣지 않는다(B-3 ⑥).
- [ ] T078 [US3] `backend/src/main/java/com/team/blog/post/application/AutosaveService.java`: `SaveResult autosave(postId, memberId, SaveRequest)` — 001 `AccountStatusGuard.requireActive(memberId, CONTENT_WRITE)`(403) → `RateLimiter("ratelimit:autosave:{memberId}", 1, 5s)`(429) → `findOwnedEditState`(없으면 404, Redis 키 유무와 무관) → 길이 검사(400) → `RedisGuard.execute(Lua save, fallback)`; fallback(Redis 장애)은 트랜잭션에서 `findForUpdateByIdAndAuthorId` → DB 현재 버전 = max(post, post_draft) 확인 → 임시글은 `post`, 발행 글은 `post_draft`에 바로 저장; 거부면 `VersionConflictException(ServerCopy)`; OOM → 503 (FR-007 ②·016·018·019·020, A-4, B-3 ①).
- [ ] T079 [US3] `AutosaveService.flushNow(long postId)`(006용 공개 메서드, EV §4)와 `PostCommandService.save(postId, memberId, SaveRequest)`(`backend/src/main/java/com/team/blog/post/application/PostCommandService.java` 2부, 수동 저장): `AccountStatusGuard`(403) → 소유 확인(404) → 길이 검사(400) → 같은 Lua로 버전을 먼저 확보한 뒤 같은 요청 안에서 그 버전으로 `updateDraftPostIfNewer`/`upsertWorkingCopyIfNewer` 즉시 반영 + `ImageService.syncPostImages`(003 FR-022), DB 반영 실패 시에도 Redis dirty에 남음(B-3 ②); Redis 장애면 T078 fallback과 같은 DB 경로 (FR-009, D-3). T077·T078 이후.
- [ ] T080 [US3] `backend/src/main/java/com/team/blog/post/web/AutosaveRequestSizeFilter.java`: `PUT /api/posts/*/autosave`에만 적용, `Content-Length > blog.autosave.max-request-bytes`이면 즉시 413 `PAYLOAD_TOO_LARGE`, 길이가 없으면 읽으면서 세어 넘으면 413(B-2). 보안 필터 체인 뒤(401/403 판정 후) 순서로 등록.
- [ ] T081 [US3] `backend/src/main/java/com/team/blog/post/web/AutosaveController.java`(`PUT /api/posts/{postId}/autosave`, `SaveRequest`/`SaveResponse` DTO, 429에 `Retry-After` 헤더, 503 `AUTOSAVE_UNAVAILABLE`)와 `PostEditorController`에 `PUT /api/posts/{postId}/working-copy`(수동 저장) 추가 (contracts `autosave`·`saveWorkingCopy`).
- [ ] T082 [US3] `backend/src/main/java/com/team/blog/post/application/AutosaveFlushJob.java`: `@Scheduled(fixedDelayString = blog.autosave.flush-interval)` + `@SchedulerLock(name = "autosave-flush", lockAtMostFor = "50s")`; `SMEMBERS autosave:dirty` → 각 키 `find` → 임시글 `updateDraftPostIfNewer`, 발행 글 `upsertWorkingCopyIfNewer` → 사진 연결(003 FR-022) → 키 버전이 반영한 버전과 같으면 SREM; 행 없음·FK 23503 → 키·dirty 삭제; 글마다 예외 격리(로그 후 다음); Redis 장애면 그 회차 건너뜀; Redis 키는 지우지 않음 (FR-007 ③, EV §3, A-5).
- [ ] T083 [P] [US3] `frontend/src/features/editor/localDraftStore.ts`(localforage): `loadDraft`, `saveDraft`, `removeDraft`, `saveBackup`, `purgeExpiredBackups(7일)`, `clearMemberDrafts(memberId)`(001 로그아웃이 호출 — `draft:{memberId}:*`·`draft-backup:{memberId}:*` 전부 삭제), `pendingImages`는 003 규칙대로 Blob 보관 (FR-014, DM §5). 001 로그아웃 코드(`frontend/src/features/auth/logout`)에서 호출하도록 001 담당에 이 함수 이름을 알린다.
- [ ] T084 [P] [US3] `frontend/src/features/editor/autosaveQueue.ts`: T072 규칙 구현(1초 로컬, 3초/30초 서버, 단일 요청, 지수 백오프 + jitter, `Retry-After`, 409 시 전송 멈춤 콜백, `navigator.onLine`·`online`/`offline` 이벤트로 오프라인 상태), `editorConfig.ts` 수치 사용 (FR-007·010).
- [ ] T085 [P] [US3] `frontend/src/features/editor/lifecycle.ts`(`visibilitychange`·`pagehide` keepalive·`beforeunload`)와 `frontend/src/features/editor/openEditor.ts`(서버 working copy + 로컬 초안 병합 판정, 백업 만료 정리) (FR-012·022).
- [ ] T086 [P] [US3] `frontend/src/components/editor/SaveStatus.tsx`: T074의 네 문구, 시각은 `savedAt`을 `HH:MM`(Asia/Seoul)으로 (FR-013).
- [ ] T087 [US3] `frontend/src/pages/EditorPage.tsx` 통합: `openEditor`로 초기 내용 결정, 입력 → `autosaveQueue`, `SaveStatus` 표시, [저장] 버튼 → `PUT working-copy`(즉시 DB), `lifecycle` 등록·해제, 업로드 대기 사진(`local:`)이 있어도 자동 저장 계속, 발행 성공 시 `removeDraft`; `frontend/src/api/posts.ts`에 `autosave`·`saveWorkingCopy` 추가 (FR-007~014·025).

**Checkpoint**: User Stories 1~3 동작 — QS §4-1·§4-7, QS §5 1~3행 통과

---

## Phase 6: User Story 4 - 발행한 글을 고쳐 다시 발행한다 (Priority: P2)

**Goal**: 발행 글을 고치는 동안 독자는 마지막 발행본을 보고, 다시 발행하면 주소·발행일·목록 위치·반응 수는 그대로 "수정됨"만 붙는다. [변경 취소]로 작업본을 버린다 (C-POST-3, FR-015·032~035).

**Independent Test**: 발행 글을 고쳐 저장 → 비로그인 조회(옛 내용) → 다시 발행 → 비로그인 조회(새 내용, 주소·날짜·반응 수 동일) (QS §4-4, §4-5 첫 줄).

### Tests for User Story 4 ⚠️

- [ ] T088 [P] [US4] `backend/src/test/java/com/team/blog/post/integration/RepublishIT.java`: SQL로 `view_count`·`like_count`·`comment_count`를 설정한 발행 글을 작업본으로 고쳐 다시 발행 → `url`·`published_at`·`first_public_at`·반응 수 3개 불변, `edited_at` 기록, `post_draft` 삭제, `PostEdited` 1번(US4 #2, SC-005, FR-032); 같은 내용으로 다시 발행해도 `edited_at` 갱신(Edge Case); 다시 발행하며 공개 범위 변경 가능, PRIVATE였던 글이 처음 공개되면 `PostEdited` + `PostWentPublic`; 숨긴 글(`hidden_at`)을 다시 발행해도 `hidden_*` 유지(A-15, 43 §4-1); 발행 → 임시글 되돌리기 API가 없고 어떤 저장·발행도 `status`를 `DRAFT`로 바꾸지 않음(US4 #5, FR-033).
- [ ] T089 [P] [US4] `backend/src/test/java/com/team/blog/post/integration/PublishedWorkingCopyIT.java`: 발행 글을 자동·수동 저장(1분 반영 포함)한 뒤 `post.title`·`content_md`·`content_html`·`excerpt`·`edited_at` 불변이고 004 `PostReadService.requireReadable` 경로(005 상세가 있으면 HTTP)로 본 내용이 마지막 발행본(US4 #1, SC-006); `GET working-copy` → 작업본이 있으면 `editing=true`·작업본 내용·`version`, 없으면 `editing=false`·발행본(US4 #4, FR-034); `PostDraftQueryService.findSavedAt`이 작업본 `updated_at`.
- [ ] T090 [P] [US4] `backend/src/test/java/com/team/blog/post/integration/DiscardWorkingCopyIT.java` + `PostAuthoringPermissionActions`(T042)에 변경 취소 실행기 추가·`post-write.csv` 대기 행 {변경 취소} × 6 행위자 채우기: `DELETE /api/posts/{id}/working-copy` → 204, `post_draft` 삭제, `post.edit_version = 현재 + 1`, `updated_at` 갱신, 발행본 그대로, 커밋 후 Redis 조건부 정리(US4 #3, FR-035, B-3 ⑤); 작업본 없음 → 204(멱등); 임시글 → 409 `NOT_PUBLISHED`; 버린 작업본 버전을 들고 있던 다른 탭의 저장 → 409(작업본 되살아나지 않음); 남의 글·없는 글·휴지통 글 404, 전후 스냅샷 동일(QS §4-3·§4-4).
- [ ] T091 [P] [US4] `backend/src/test/java/com/team/blog/post/integration/PublishAutosaveRaceIT.java`: 발행 ④에서 버전 v0를 확인한 뒤 커밋 전에 다른 탭 자동 저장이 Redis를 v0+1로 올리면(트랜잭션 안 훅으로 끼워 넣기), 커밋 후 Redis 키를 지우지 않고 `version = 새 post.edit_version + 1`·dirty 유지, 다음 반영에서 작업본 생성, 그 탭의 다음 저장은 409(Edge Case 1, B-3 ④); 발행 커밋과 ⑨ 사이에 1분 반영이 돌아도 `post.edit_version ≥ v`라 작업본이 되살아나지 않음(B-3 ③).
- [ ] T092 [P] [US4] `frontend/src/pages/__tests__/EditorPage.discard.test.tsx`: 발행 글 + `editing=true`면 "수정 중" 표시와 [변경 취소] 버튼, 확인 후 `DELETE working-copy` → 로컬 `draft:` 삭제 → 발행본으로 다시 열림; 임시글에는 버튼 없음.

### Implementation for User Story 4

- [ ] T093 [US4] `PostCommandService.discard(postId, memberId)`(`backend/src/main/java/com/team/blog/post/application/PostCommandService.java` 3부)와 `Post.discardWorkingCopy(long currentVersion, Instant now)`(`backend/src/main/java/com/team/blog/post/domain/Post.java`): `AccountStatusGuard`(403) → 트랜잭션에서 `findForUpdateByIdAndAuthorId`(404) → DRAFT면 `NotPublishedException` → 현재 버전 = max(Redis, post_draft, post) → `post_draft` 삭제 → `edit_version = 현재 + 1`, `updated_at = now` → 커밋 후 `RedisAutosaveStore.release(postId, 현재, 새 버전)`; 이벤트 없음 (FR-035, B-3 ⑤, DM §2 전이표).
- [ ] T094 [US4] `PostEditorController`(`backend/src/main/java/com/team/blog/post/web/PostEditorController.java`)에 `DELETE /api/posts/{postId}/working-copy` → 204 추가 (contracts `discardWorkingCopy`).
- [ ] T095 [US4] `frontend/src/pages/EditorPage.tsx` 발행 글 모드: "수정 중" 표시, [변경 취소](확인 문구는 006 `confirmDialogs.ts`가 있으면 재사용, 없으면 "고치던 내용을 버리고 발행한 내용으로 돌아갈까요?"), 성공 시 로컬 초안 삭제 후 `getWorkingCopy` 다시 불러오기; `frontend/src/api/posts.ts`에 `discardWorkingCopy` 추가 (US4 #3·#4).

**Checkpoint**: User Stories 1~4 동작 — QS §4-4 통과, SC-005·006 테스트 통과

---

## Phase 7: User Story 5 - 여러 탭·기기에서 같은 글을 고쳐도 몰래 덮어쓰지 않는다 (Priority: P2)

**Goal**: 저장 충돌을 감지하면 편집을 막지 않고 배너를 띄우고, 비교 창에서 작성자가 [편집 중인 내용으로 저장]·[저장된 내용 불러오기]·[새 임시글로 따로 저장]·닫기 중 하나를 고른다 (FR-020~024, SC-007).

**Independent Test**: 같은 글을 탭 두 개에서 열고 A 탭에서 저장한 뒤 B 탭에서 입력해 배너와 비교 창의 세 가지 선택을 확인한다 (QS §5 4행).

### Tests for User Story 5 ⚠️

- [ ] T096 [P] [US5] `backend/src/test/java/com/team/blog/post/integration/ConflictDetailsIT.java`: 자동 저장·수동 저장·발행 각각의 409 `details.server`가 현재 버전 출처(Redis면 Hash, 작업본이면 `post_draft`, 아니면 `post`)의 `title`·`contentMd`·`version`·`savedAt`이고 서버 내용은 바뀌지 않음(FR-020, SC-007); 서버 버전을 `baseVersion`으로 다시 보내면 200(덮어쓰기는 사용자가 고를 때만); `POST /api/posts {title, contentMd}`로 새 임시글 생성 후 원래 글은 그대로(US5 #5).
- [ ] T097 [P] [US5] `frontend/src/features/editor/__tests__/diffModel.test.ts`: 줄 단위 비교 + 바뀐 줄 안 단어 단위 강조, 제목이 다르면 제목 비교 포함, 바뀌지 않은 긴 구간(예: 6줄 이상) 접기, [이전 차이]·[다음 차이] 인덱스 이동 (FR-023).
- [ ] T098 [P] [US5] `frontend/src/components/editor/__tests__/DiffDialog.test.tsx`: 지운 부분 빨간 배경 + `−`, 추가 부분 초록 배경 + `+`(색과 기호 함께); 너비 ≥ 768px 좌우 나란히, 375px 위아래 합친 보기·가로 스크롤 없음; [편집 중인 내용으로 저장] → "14:03에 저장된 내용이 지금 편집 중인 내용으로 바뀌어요. 정말 저장할까요?" 확인에 동의할 때만 `PUT working-copy`(baseVersion = server.version)(US5 #3); [저장된 내용 불러오기] → 에디터가 서버 내용, 편집 중 내용은 `draft-backup:`에 7일 백업 + 안내(US5 #4); [새 임시글로 따로 저장] → `createPost({title, contentMd})` 후 새 글로 이동(US5 #5); 닫기 → 배너 유지·전송 멈춤 (FR-024).
- [ ] T099 [P] [US5] `frontend/src/features/editor/__tests__/conflict.test.ts`: 자동 저장 409 → 편집 가능 유지·로컬 저장 계속·서버 전송 멈춤·배너 "⚠ 다른 탭이나 기기에서 이 글이 수정되었어요(14:03). 지금 내용은 이 기기에만 저장되고 있어요. [비교하기]"(US5 #1, FR-021); 충돌 상태에서 [비교하기]·[저장]·[발행] → 비교 창 열림(US5 #2); 입력 중에는 창을 자동으로 띄우지 않음; 에디터 열기에서 `dirty && baseVersion != server` → 즉시 비교 창(FR-022).
- [ ] T100 [P] [US5] `frontend/e2e/two-tabs.spec.ts`(Playwright, 같은 컨텍스트 두 페이지): A 저장 → B 입력 → B 배너, 입력 계속 가능 → [비교하기] → 세 버튼 각각의 결과(서버 내용 비교, 확인 문구, 백업 안내, 새 임시글 생성) (US5 Independent Test).

### Implementation for User Story 5

- [ ] T101 [P] [US5] `frontend/src/features/editor/diffModel.ts`: jsdiff `diffLines` + 바뀐 줄 쌍에 `diffWordsWithSpace`, 제목 비교, 접기 구간·차이 인덱스 계산 (FR-023, A-3).
- [ ] T102 [P] [US5] `frontend/src/features/editor/conflict.ts`: 충돌 상태(`serverCopy`, 감지 시각) 저장소, `autosaveQueue` 전송 멈춤/재개, 저장·발행 동작 가로채기, 백업 저장(`localDraftStore.saveBackup`) (FR-021·024).
- [ ] T103 [P] [US5] `frontend/src/components/editor/ConflictBanner.tsx`: T099 문구, [비교하기] 버튼, `role="alert"` (FR-021).
- [ ] T104 [US5] `frontend/src/components/editor/DiffDialog.tsx`: `diffModel` 결과를 좌우/위아래(768px 기준)로 표시, 색 + `−`/`+`, [이전 차이]·[다음 차이], 접힌 구간 펼치기, 결과를 이름에 담은 버튼 3개 + 닫기, 확인 문구는 `savedAt`을 HH:MM으로 (FR-023·024). T101 이후.
- [ ] T105 [US5] `frontend/src/pages/EditorPage.tsx` 연결: `SaveStatus` 충돌 상태, `ConflictBanner`, `DiffDialog` 동작(덮어쓰기 성공 시 `baseVersion` 갱신·배너 해제, 불러오기 시 에디터 내용 교체, 새 임시글 이동), `openEditor` 충돌 판정 시 즉시 열기 (US5 #1~#5). T102~T104 이후.

**Checkpoint**: User Stories 1~5 동작 — T096~T100 통과, SC-007

---

## Phase 8: User Story 6 - [발행]을 여러 번 눌러도 한 번만 발행된다 (Priority: P2)

**Goal**: 버튼 연타·네트워크 재전송에도 발행은 한 번이고, 이미 성공한 발행에 충돌 창이 뜨지 않는다 (FR-037, SC-002).

**Independent Test**: 같은 발행 요청 키로 동시 20건 → 편집 버전 +1 (QS §3 "멱등 발행", QS §4-5).

### Tests for User Story 6 ⚠️

- [ ] T106 [P] [US6] `backend/src/test/java/com/team/blog/post/integration/PublishIdempotencyIT.java`: 같은 `Idempotency-Key`·같은 본문 동시 20건(ExecutorService + CountDownLatch) → `edit_version` 정확히 +1, 이벤트 1세트, 나머지는 200(첫 응답과 같은 본문) 또는 409 `IN_PROGRESS`(US6 #2, SC-002); 끝난 키 재전송 → 저장된 응답 200, 이벤트 재발행 없음(QS §4-5); 같은 키 다른 내용 → 422 `IDEMPOTENCY_KEY_REUSED`(US6 #3); 같은 키를 다른 글에 사용 → 422(hash에 postId, B-8); 검증 실패(400)나 트랜잭션 실패 후 키가 풀려 같은 키·같은 본문 재시도 가능(FR-037); JSON 키 순서·공백만 다른 같은 요청은 같은 hash; Redis 값 `{hash, status, response}`·TTL 600초.
- [ ] T107 [P] [US6] `backend/src/test/java/com/team/blog/post/integration/PublishRedisOutageIT.java`(CircuitBreaker 강제 열림): 멱등 키 처리를 건너뛰고(경고 로그) 같은 `baseVersion`으로 연달아 두 번 발행하면 첫 번째 200, 두 번째 409 `VERSION_CONFLICT`(행 잠금 + 버전 확인), 발행은 1번 (FR-037, A-8, QS §3 "Redis 장애").
- [ ] T108 [P] [US6] `frontend/src/features/editor/__tests__/publish.test.ts`: [발행] 클릭마다 새 UUID, 응답 전 버튼 비활성화 + "발행 중…"(US6 #1), 409 `IN_PROGRESS` → 1초 뒤 **같은 키**로 재시도, 409 `VERSION_CONFLICT` → 비교 창 요청(US5와 연결), 400 → 입력칸 옆 오류, 성공 → 로컬 초안 삭제 후 `url`로 이동.

### Implementation for User Story 6

- [ ] T109 [P] [US6] `backend/src/main/java/com/team/blog/post/infra/RedisIdempotencyStore.java`: 키 `idem:publish:{memberId}:{key}`, `tryStart(hash)` = `SET … {hash, IN_PROGRESS} NX EX blog.publish.idempotency-ttl` 결과(NEW / 기존 값), `complete(response)` = `SET … {hash, DONE, response} XX KEEPTTL`, `release()` = DEL; 모든 호출 `RedisGuard` 경유(장애 시 "건너뜀" 결과) (A-8, DM §4).
- [ ] T110 [US6] `backend/src/main/java/com/team/blog/post/application/PublishIdempotency.java`: hash = SHA-256(`postId` + 정렬된 키로 정규화한 JSON(`title, contentMd, tags, visibility, baseVersion`)); 기존 값이 hash 다름 → 422, `IN_PROGRESS` → 409, `DONE` → 저장된 `PublishResponse` 재생 (B-8). T109 이후.
- [ ] T111 [US6] `PublishService`(`backend/src/main/java/com/team/blog/post/application/PublishService.java`)·`PublishController` 통합: 컨트롤러 진입 직후 `PublishIdempotency` 판정(재생이면 서비스 호출·이벤트 없이 저장된 응답 200), 성공 커밋 후 ⑪ `complete(response)`(실패는 경고 로그), 400·404·409·트랜잭션 실패 시 `release()`, Redis 장애면 건너뛰고 ③④에 맡김 (FR-037, EV §2). T110 이후.
- [ ] T112 [US6] `frontend/src/features/editor/publish.ts`와 `frontend/src/components/editor/PublishDialog.tsx` 수정: T108 규칙(버튼 비활성화 + "발행 중…", 시도마다 키, `IN_PROGRESS` 1초 재시도, 충돌 시 `conflict.ts`로 비교 창) (FR-037, US6 #1).

**Checkpoint**: User Stories 1~6 동작 — QS §4-5, SC-002

---

## Phase 9: User Story 7 - 정화 규칙이 바뀌면 기존 글도 새 규칙을 따르고, 빈 임시글은 정리된다 (Priority: P3)

**Goal**: `RENDER_VERSION`을 올리면 이전 버전 발행 글이 100개씩 다시 렌더링되고(`edited_at`·`edit_version` 불변), 24시간 지난 빈 임시글은 매일 새벽 휴지통 없이 완전 삭제된다 (FR-048·050, SC-010).

**Independent Test**: 렌더링 규칙 버전을 올리고 다시 렌더링 작업을 실행해 이전 버전 글 0건 확인, 24시간 지난 빈 임시글이 정리 작업 뒤 사라지는지 확인 (QS §3 "배치").

### Tests for User Story 7 ⚠️

- [ ] T113 [P] [US7] `backend/src/test/java/com/team/blog/post/integration/RerenderJobIT.java`: `render_version < RenderVersion.CURRENT`인 발행 글 250개(테스트에서 현재 버전을 2로 주입) → 작업 실행 후 0건, `content_html`·`excerpt` 갱신, `edited_at`·`edit_version`·`updated_at` 불변(US7 #1, SC-010, B-10); 임시글은 대상 아님; 사진 판별은 글 작성자 기준; 읽은 뒤 `edit_version`이 바뀐 글은 이번 회차 건너뜀(`WHERE edit_version = :읽은 버전`); `CONTENT_TOO_COMPLEX` 글은 건너뛰고 경고 로그 + 남은 건수 지표; 대상 0건이면 즉시 종료; 이벤트 없음.
- [ ] T114 [P] [US7] `backend/src/test/java/com/team/blog/post/integration/EmptyDraftCleanupJobIT.java`: `created_at`·`updated_at` 모두 25시간 전이고 제목·본문이 공백뿐인 DRAFT → 완전 삭제(휴지통 거치지 않음, US7 #2); 생성 25시간 전이지만 수정 1시간 전 → 남음; 제목이나 본문이 있음 → 남음; Redis `autosave:post:{id}` 있음 → 남음; 발행 글·휴지통 글 → 대상 아님; Redis 장애(CircuitBreaker 열림) → 그날 건너뜀; 이벤트 0건; `EmptyDraftPolicy`와 SQL 판정이 같은 결과(T010 문자 집합) (FR-050, B-9, EV §3).

### Implementation for User Story 7

- [ ] T115 [P] [US7] `backend/src/main/java/com/team/blog/post/infra/PostMaintenanceRepository.java`(네이티브 SQL): `findRerenderTargets(cur, afterId, limit)` = `SELECT id, author_id, content_md, edit_version FROM post WHERE status='PUBLISHED' AND render_version < :cur AND id > :afterId ORDER BY id LIMIT :limit`(새 인덱스 없음, DM §1-1); `updateRendered(id, html, excerpt, cur, readVersion)` = `UPDATE post SET content_html, excerpt, render_version = :cur WHERE id = :id AND edit_version = :readVersion AND render_version < :cur`(`updated_at` 건드리지 않음); `lockEmptyDraftCandidates(limit)` = `SELECT id FROM post WHERE status='DRAFT' AND btrim(title, :ws) = '' AND btrim(content_md, :ws) = '' AND created_at < now() - :age AND updated_at < now() - :age AND deleted_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED`(`:ws` = `EmptyDraftPolicy` 문자 집합); `deleteById(id)`.
- [ ] T116 [US7] `backend/src/main/java/com/team/blog/post/application/RerenderJob.java`: `@Scheduled(fixedDelayString = blog.markdown.rerender-interval)` + `@SchedulerLock(name = "post-rerender")`; 100개씩 `ContentRenderer.render(contentMd, ImageContext(작성자))` → `updateRendered`, 렌더링은 트랜잭션 밖, 실패 글은 건너뛰고 로그, 남은 건수 Micrometer 게이지 (FR-048, A-11, B-10). T115 이후.
- [ ] T117 [US7] `backend/src/main/java/com/team/blog/post/application/EmptyDraftCleanupJob.java`: `@Scheduled(cron = blog.cleanup.empty-draft-cron, zone = "Asia/Seoul")` + `@SchedulerLock(name = "empty-draft-cleanup")`; Redis 장애(`RedisGuard.isOpen()`)면 즉시 종료; 트랜잭션마다 후보 100개 잠금 → 각 글 Redis `EXISTS autosave:post:{id}`가 0인 것만 `DELETE`(CASCADE) → 반복; 이벤트 없음 (FR-050, EV §3). T115 이후.

**Checkpoint**: All user stories should now be independently functional

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T118 [P] `backend/src/test/java/com/team/blog/post/integration/PublishQueryCountIT.java`: 태그 10개·사진 10장 글 발행 시 SQL 수가 태그·사진 수에 비례하지 않음(Hibernate Statistics 또는 datasource-proxy), 자동 저장 1건 = DB 조회 1회 + Lua 1회 (plan Performance Goals, N+1 금지).
- [ ] T119 [P] 트랜잭션 경계 가드 `backend/src/main/java/com/team/blog/shared/infra/redis/RedisGuard.java` 수정 + `backend/src/test/java/com/team/blog/post/integration/TransactionBoundaryIT.java`: `TransactionSynchronizationManager.isActualTransactionActive()`가 참일 때 Redis 쓰기를 부르면 test 프로필에서 예외(운영은 경고 로그) — 발행·변경 취소·수동 저장 테스트 전체가 이 가드 아래 통과 (05 J-5, A-6). 단, 006 `flushNow` 호출 경로처럼 읽기만 하는 경우는 허용 목록.
- [ ] T120 [P] 운영 로그·지표 `backend/src/main/java/com/team/blog/post/application/PostAuthoringMetrics.java`: Redis 장애로 DB 직접 저장한 횟수, 멱등 처리 건너뜀, ⑨ 정리 실패, 1분 반영 실패 글 수, 다시 렌더링 남은 건수를 Micrometer로 노출하고 경고 로그에 postId·memberId만 남김(제목·본문 금지).
- [ ] T121 [P] `frontend/e2e/editor-responsive.spec.ts`: 375px·데스크톱에서 에디터·저장 상태·발행 설정·미리보기·충돌 배너·비교 창에 가로 스크롤 없음(`document.documentElement.scrollWidth <= innerWidth`), 저장 상태가 글자로 보임 (constitution 비기능 최소선, FR-013·023).
- [ ] T122 [P] stub 교체 점검표를 `backend/src/main/java/com/team/blog/media/infra/ImageReferenceResolverAdapter.java`·`backend/src/main/java/com/team/blog/media/application/ImageService.java`·`backend/src/main/java/com/team/blog/tag/application/TagService.java`의 클래스 주석에 남긴다: 003·008이 교체할 때 유지해야 할 계약(T039·T047의 사진 연결 단언, T036·T046의 태그 오류 코드·`tags[i]` 필드 이름)과 그 계약을 확인하는 테스트 이름.
- [ ] T123 quickstart.md 검증: `docker compose up -d` 후 QS §2(보안 헤더), §3(자동 테스트 전체 `./mvnw -f backend/pom.xml test`, `cd frontend && npm test && npx playwright test`), §4-1~§4-7 curl 시나리오, §5 화면 확인 표를 순서대로 실행하고 기대 결과와 다른 항목을 기록한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: 001 Phase 1·2와 004 Foundational(T009 `Visibility`는 002 T018보다 먼저, T013 `VisibilityRegistry`·T017 `PostReadService`·T021~T023 하네스는 002 스토리 테스트보다 먼저)이 필요하다. 004 T031·T032와 006은 002 T018·T021 뒤에 `Post`·`PostRepository`에 메서드를 추가한다. 001 `MemberQueryService.defaultVisibility`는 US1 T048 전에 필요하다. 005 상세는 T039·T060·T089의 HTTP 단언에만 필요하다(없으면 대체 단언).
- **Setup (Phase 1)**: 위 선행 확인 후 시작 - T003·T004만 실제 변경
- **Foundational (Phase 2)**: Setup 완료 후 - BLOCKS all user stories. 004·005·006도 T018~T022(도메인·리포지토리), T020(EmptyDraftPolicy), T023~T031(ContentRenderer), T033(release)에 의존한다.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1(P1) → MVP. US2(P1)·US3(P1)는 Foundational 뒤 US1과 병렬 가능하지만 화면 작업(T063·T087)은 US1의 `EditorPage.tsx`(T054)를 확장한다.
  - US4(P2)는 US3의 수동 저장·작업본 반영(T077·T079)에 의존한다(발행 글 작업본이 생겨야 고쳐 다시 발행을 시험할 수 있음).
  - US5(P2)는 US3의 409 처리(`autosaveQueue`, T084)와 `localDraftStore` 백업(T083)에 의존한다.
  - US6(P2)은 US1의 `PublishService`·`PublishController`(T050·T052)에 의존한다. 화면 T112는 US5 T102가 있으면 비교 창과 연결한다.
  - US7(P3)은 Foundational의 렌더러·`EmptyDraftPolicy`만 필요하다(US1과 독립, 검증 데이터는 SQL 픽스처).
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 이후 - 다른 스토리 의존 없음
- **User Story 2 (P1)**: Foundational 이후 - 발행 경로 테스트(T057·T058)는 US1의 발행 API 필요
- **User Story 3 (P1)**: Foundational 이후 - US1의 `PostEditorController`(T051)에 PUT 추가, `EditorPage`(T054) 확장
- **User Story 4 (P2)**: US1 + US3 이후
- **User Story 5 (P2)**: US3 이후 (백엔드 409는 US1·US3에서 이미 구현, 이 스토리는 대부분 화면)
- **User Story 6 (P2)**: US1 이후
- **User Story 7 (P3)**: Foundational 이후 - 독립

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- 도메인(값 객체·엔티티 메서드) → 리포지토리 쿼리 → Service → Controller/Filter → 화면
- 같은 파일을 고치는 작업(`PostCommandService.java`: T048→T079→T093, `PostEditorController.java`: T051→T081→T094, `PublishService.java`: T050→T111, `EditorPage.tsx`: T054→T063→T087→T095→T105, `post-write.csv`: T042→T070→T090, `RedisAutosaveStore.java`: T033→T076)은 순서대로 한다.
- Story complete before moving to next priority

### Parallel Opportunities

- Setup: T004는 T003과 병렬
- Foundational: T005~T008 병렬, 테스트 T010~T017 병렬(T009 픽스처 이후), 구현 T018·T019·T020·T023·T024·T028·T029·T030·T032 병렬
- US1: 테스트 T035~T042 병렬, 구현 T043·T044·T046·T047·T053 병렬
- US2: T056~T060 병렬, T062 병렬
- US3: 테스트 T064~T075 병렬, 구현 T077·T083·T084·T085·T086 병렬
- US4: T088~T092 병렬
- US5: T096~T100 병렬, T101~T103 병렬
- US6: T106~T108 병렬, T109 병렬
- US7: T113·T114·T115 병렬
- Foundational 완료 후 US1·US2(백엔드 미리보기 T061)·US3(백엔드 T076~T082)·US7은 서로 다른 개발자가 동시에 진행할 수 있다.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "TitleNormalizerTest in backend/src/test/java/com/team/blog/post/unit/TitleNormalizerTest.java"
Task: "PublishValidatorTest in backend/src/test/java/com/team/blog/post/unit/PublishValidatorTest.java"
Task: "PostPublishRulesTest in backend/src/test/java/com/team/blog/post/unit/PostPublishRulesTest.java"
Task: "CreatePostIT in backend/src/test/java/com/team/blog/post/integration/CreatePostIT.java"
Task: "PublishIT in backend/src/test/java/com/team/blog/post/integration/PublishIT.java"
Task: "PublishTransactionIT in backend/src/test/java/com/team/blog/post/integration/PublishTransactionIT.java"

# Launch independent implementation files together:
Task: "TitleNormalizer in backend/src/main/java/com/team/blog/post/domain/TitleNormalizer.java"
Task: "TagService stub in backend/src/main/java/com/team/blog/tag/application/TagService.java"
Task: "ImageService.syncPostImages stub in backend/src/main/java/com/team/blog/media/application/ImageService.java"
Task: "API client in frontend/src/api/posts.ts"
```

## Parallel Example: User Story 3

```bash
# Backend tests:
Task: "AutosaveIT in backend/src/test/java/com/team/blog/post/integration/AutosaveIT.java"
Task: "ManualSaveIT in backend/src/test/java/com/team/blog/post/integration/ManualSaveIT.java"
Task: "AutosaveFlushJobIT in backend/src/test/java/com/team/blog/post/integration/AutosaveFlushJobIT.java"
# Frontend tests:
Task: "localDraftStore.test.ts in frontend/src/features/editor/__tests__/localDraftStore.test.ts"
Task: "autosaveQueue.test.ts in frontend/src/features/editor/__tests__/autosaveQueue.test.ts"
# Frontend modules (different files):
Task: "localDraftStore.ts", "autosaveQueue.ts", "lifecycle.ts + openEditor.ts", "SaveStatus.tsx"
```

## Parallel Example: Foundational (ContentRenderer)

```bash
Task: "ContentRendererXssTest + HtmlSafetyChecker in backend/src/test/java/com/team/blog/shared/markdown/"
Task: "ContentRendererSyntaxTest in backend/src/test/java/com/team/blog/shared/markdown/ContentRendererSyntaxTest.java"
Task: "LinkAttributeProvider in backend/src/main/java/com/team/blog/shared/infra/markdown/LinkAttributeProvider.java"
Task: "SanitizerPolicy in backend/src/main/java/com/team/blog/shared/infra/markdown/SanitizerPolicy.java"
Task: "ExcerptExtractor in backend/src/main/java/com/team/blog/shared/infra/markdown/ExcerptExtractor.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Cross-feature 선행 확인 (001 Phase 1·2, 004 Foundational)
2. Complete Phase 1: Setup
3. Complete Phase 2: Foundational (CRITICAL - 렌더러·정화가 여기 있으므로 MVP 발행 결과도 이미 안전하다)
4. Complete Phase 3: User Story 1
5. **STOP and VALIDATE**: QS §4-1~§4-3, T035~T042
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 렌더러 52개 코퍼스 통과
2. US1 → 새 글·발행 (MVP)
3. US2 → 미리보기·보안 헤더·E2E XSS (P1, 공개 전 필수)
4. US3 → 자동 저장 3단계 (P1, 긴 글 쓰기 전 필수)
5. US4 → 고쳐 다시 발행·변경 취소
6. US5 → 충돌 비교 창
7. US6 → 발행 멱등
8. US7 → 다시 렌더링·빈 임시글 정리
9. Polish → QS 전체 검증

P1 세 개(US1~US3)가 모두 끝나야 "Tier A C-POST-1·2의 최소 완료"로 본다.

### Parallel Team Strategy

1. 팀이 Foundational을 함께 끝낸다 (렌더러 담당 / 도메인·Redis 담당으로 나눔)
2. 이후:
   - 개발자 A: US1 → US6 (발행 축)
   - 개발자 B: US3 → US4 → US5 (저장·충돌 축)
   - 개발자 C: US2 → US7 (렌더러 축)
3. `EditorPage.tsx`·`PostCommandService.java`·`PostEditorController.java`는 공유 파일이므로 위 순서표대로 병합한다.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

**spec/plan에서 발견한 문제 (수정하지 않고 기록만)**

1. 패키지 경로 불일치: 002 plan은 오류 본문을 `shared/web/error/`, 이벤트를 `shared/domain/event/`에 두지만 001·004·006 plan은 `shared/error/`·`shared/event/`다. 이 tasks는 공통 기반 소유자(001) 기준인 `shared/error/`·`shared/event/`를 쓴다.
2. `btrim` 판정 차이: research B-9·contracts/events.md의 `btrim(title) = ''`는 PostgreSQL에서 공백(U+0020)만 지우므로, 줄바꿈·탭만 있는 본문을 "빈 글"로 보는 Java 판정(`strip()`)과 결과가 다르다. T010·T020·T115에서 같은 문자 집합(`" \t\r\n"`)을 쓰도록 정했으나 팀 확인이 필요하다(006 FR-020과 공유).
3. `Visibility` enum 위치: 001 plan은 `account.domain.Visibility`, 004 plan은 `post.domain.Visibility`를 둔다. 002 `Post`는 004의 것을 쓴다고 가정했다. 하나로 정해야 한다.
4. 로그아웃 초안 삭제 함수 이름: 001 plan은 `clearMemberDrafts`, 002 research C는 `clearLocalDrafts(memberId)`. 이 tasks는 001 이름(`clearMemberDrafts`)을 쓴다.
5. `MemberQueryService.defaultVisibility(memberId)`는 002 research C가 001에 기대하지만 001 plan의 `MemberQueryService` 설명에 명시돼 있지 않다.
6. `RateLimiter` 중복: 001 plan은 `account/infra/redis/RateLimiter`, 002 plan은 `shared/infra/ratelimit/RateLimiter`. T034에서 공용 하나로 합칠지 001과 정해야 한다.
7. `Post` 엔티티 공동 수정: 002가 만들고 004(`changeVisibility`, 004 T031)·006(`moveToTrash`·`restore`)이 메서드를 추가한다. 006 plan은 `@SQLRestriction("deleted_at IS NULL")`을 006이 건다고 적었지만 004·006 tasks는 002 Foundational에 있기를 기대하므로 T018에 넣었다. 002의 1분 반영(`deleted_at` 조건 없음, B-3 ⑥)은 네이티브 SQL(T077)이라 이 제한의 영향을 받지 않는다. 006 `Post.isEmptyDraft()`는 `EmptyDraftPolicy`에 위임해 판정을 하나로 둔다.
8. `PostWentPublic` 소유: 002 plan은 `shared/domain/event/PostWentPublic.java`, 004 plan·tasks(T030)는 `shared/event/PostWentPublic.java`이고 "먼저 구현하는 쪽이 만든다"로 공유한다. 이 tasks는 T008에서 `shared/event/`에 없을 때만 만든다.
9. 미리보기 계약의 403: contracts/openapi.yaml `previewMarkdown`에는 403이 없지만, CSRF 토큰이 없으면 Spring Security가 403을 준다(다른 경로는 CsrfToken 파라미터 설명에 적혀 있음). 계약 보완이 필요하다.
10. `PostRepository.findForUpdateByIdAndAuthorId`가 002 T021과 004 T032에 모두 있다. 002 Foundational이 먼저 만들므로 004 T032는 확인 작업이 된다.
11. 계정 상태 403 판정: 002 plan·contracts는 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`만 적었으나 001 research R-22·004 하네스에는 정지 `ACCOUNT_SUSPENDED`도 있다. 이 tasks는 001 `AccountStatusGuard.requireActive(…, CONTENT_WRITE)`를 Service 첫머리에서 호출해 세 코드를 모두 다룬다(contracts/openapi.yaml `Forbidden` 예시에 정지 추가 필요).
12. spec FR-047은 미리보기 "사용자당 1분에 60번"을 정했으나 plan 설정 키 목록에 이름이 없어 T005에서 `blog.markdown.preview-rate-limit`로 정했다.
