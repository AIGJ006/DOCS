---

description: "Task list for 007-comment (댓글·답글)"
---

# Tasks: 댓글·답글

**Input**: Design documents from `/specs/007-comment/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, events.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 interaction 모듈의 댓글을 소유한다. 005가 남긴 `CommentSectionSlot` 자리와 006이 만든 `CommentQueryService`를 넘겨받고, 011·014·015가 쓸 이벤트·공개 Service를 만든다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `@LoginRequired`, `AccountStatusGuard`(`CONTENT_WRITE`·`CONTENT_CLEANUP`), `RateLimiter`, `CursorCodec`·`ListScope`, `ProfileImageQuery.currentKeysOf`·`ImageUrlResolver`, `MemberQueryService`, `shared/error`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`·`SqlCounter`, 인증 메일 재발송 API(화면 안내 버튼)
- 선행: specs/004 — `PostReadService.requireReadable`, `CacheControlPolicy.forPost`, 권한 하네스(`support/permission/`, `PostSnapshot`)
- 선행: specs/005 — `PostDetailPage`·`CommentSectionSlot`(`postId`·`commentCount`·`aroundCommentId`), `RelativeTime`·`DefaultAvatar`, `?comment=` 쿼리를 유지하는 301
- 선행(둘 중 먼저 하는 쪽이 만듦): specs/008 T010 `shared/text/InvisibleCharacters` — 008이 아직이면 이 기능의 T011이 같은 내용으로 만들고 008이 그것을 쓴다

**006 머지 후**

- 006-manage-delete(브랜치 `006-manage`, 구현 중)가 `B/interaction/application/CommentQueryService.java`(`commentIdsOfPost`, 006 T058)를 만든다. 이 기능의 `CommentQueryService` 작업(T032)은 006 머지 후 그 파일을 넓힌다. 006 머지 전에는 같은 이름의 새 클래스를 만들지 않는다
- 006 `PostPurgeIT`(신고 종료 단계)가 `commentIdsOfPost`를 계속 통과해야 한다

**후속 (다른 스펙이 이 기능을 사용)**

- 011-notification: `CommentCreated`·`CommentDeleted` 구독(contracts/events.md §1)
- 014-report-hide: `CommentModerationService.hide/unhide/snapshot` 호출, 댓글 [신고] 버튼을 `CommentItem`의 `actions` 자리에 켬(Clarifications Q4)
- 015-withdraw: `CommentWithdrawalPurgeStep`(`WithdrawalPurgeStep` order 20)은 015 tasks가 만들고 이 기능의 `CommentPurgeService.purgeByAuthor`(T048)를 부른다
- 009-like-view: `PostCounterService`(T009)에 `adjustLikeCount`를 더한다
- 012-trending-search: `post.comment_count`·`CommentCreated` 사용(숨긴 댓글 제외 — 012 Clarifications)

**팀 결정 대기 (기본안으로 진행)**

- 내 댓글 삭제 때 글 읽기 권한을 보지 않는다(research R9 제안). 팀이 "글을 볼 수 있을 때만"으로 정하면 T040과 `comment.csv` 삭제 행을 바꾼다
- `RedisGuard` OOM 503 문구(research R17): 화면은 code와 상관없이 처리. 공용 수정은 002 소유

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 작업 확인, 설정값

- [ ] T001 선행 확인: V1 `comment`(`fk_comment_parent` 복합 FK·`ck_comment_content`·`ck_comment_reply_to`·`ix_comment_root`·`ix_comment_reply`·`ix_comment_author`)와 `post.comment_count`·`ck_post_counts`, `B/post/application/PostReadService.java`, `B/shared/web/CacheControlPolicy.java`, `B/media/application/ProfileImageQuery.java`(`currentKeysOf`), `F/features/post-detail/CommentSectionSlot.tsx`, `T/support/SqlCounter.java`가 있는지 확인한다. 006이 main에 머지됐는지와 `B/interaction/application/CommentQueryService.java` 유무를 기록한다
- [ ] T002 [P] 설정값 `B/interaction/application/CommentProperties.java`(`@ConfigurationProperties("blog.comment")`, `@Validated`: `pageSize`, `replyPreview`, `replyPageSize`, `contentMax`(`@Max(1000)`), `dedupeWindow`, `maxDepth`(`@Max(1)`), `aroundMaxReplies`, `RateLimit create/edit`)와 `R/application.yml` 기본값(research R15)을 추가한다

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 내용 정리·상태 판정·커서·이유 코드, 다른 모듈 공개 API(댓글 수·회원 표시), 저장소 뼈대, 이벤트, 화면 API

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T003 [P] 내용 정리 단위 테스트 `T/interaction/unit/CommentTextTest.java`: NFC, 폭 0·방향 제어 제거, 줄바꿈 유지·`\r\n` 통일, 탭 → 공백, 앞뒤 공백·줄바꿈 제거, 빈 줄 3개 → 1개, 공백만 → `COMMENT_REQUIRED`, 이모지 1000개 통과·1001개 `COMMENT_TOO_LONG`(코드 포인트), 한글 자모 보존(research R3)
- [ ] T004 [P] 상태 판정 단위 테스트 `T/interaction/unit/CommentStateTest.java`: 우선순위(탈퇴 > 삭제 > 숨김 > 정상, 탈퇴 작성자의 숨김 댓글 → `WITHDRAWN_AUTHOR`), 감추기 규칙(숨김은 작성자 본인만 `content`·`author`, 글 주인·관리자에게 null), `edited`, `replyTo` 탈퇴 표시(data-model §3, research R10)
- [ ] T005 [P] 커서 단위 테스트 `T/interaction/unit/CommentCursorTest.java`: `comments:{postId}`·`replies:{rootId}` 구분, 다른 글·다른 목록·`home` 커서 400, 마이크로초 왕복, 이전 방향 커서(`d: prev`)
- [ ] T006 [P] 댓글 수 API 통합 테스트 `T/post/integration/PostCounterServiceIT.java`: `adjustCommentCount` ±1, 트랜잭션 밖 호출은 `IllegalTransactionStateException`(MANDATORY), 0 아래로 가면 트랜잭션 실패, `adjustCommentCounts` 여러 글 한 번(SQL 1번)
- [ ] T007 [P] 회원 표시 통합 테스트 `T/account/integration/MemberDisplayQueryIT.java`: `findDisplays`가 ACTIVE·SUSPENDED는 `withdrawn=false`, WITHDRAWN·익명 처리(`deleted_at`)는 `withdrawn=true`(익명은 handle·nickname null), 없는 번호는 결과에 없음, SQL 1번

### Implementation for Foundational

- [ ] T008 [P] 도메인 `B/interaction/domain/CommentReasonCode.java`(data-model §4-3 4개, 끝 마침표 없음), `CommentState.java`(판정 정적 메서드), `CommentText.java`(정리·판정, `InvisibleCharacters` 사용)를 만든다(T003·T004 통과)
- [ ] T009 [P] post 공개 Service `B/post/application/PostCounterService.java`(`adjustCommentCount`·`adjustCommentCounts`, `JdbcClient`, `@Transactional(propagation = MANDATORY)`)를 만든다(T006 통과). 클래스 주석에 "009가 `adjustLikeCount`를 더한다"를 적는다
- [ ] T010 [P] account 공개 API `B/account/application/MemberDisplay.java`(record)와 `B/account/application/MemberQueryService.java`의 `findDisplays(Collection<Long>)`(빈 입력은 SQL 없이 빈 맵)를 더한다(T007 통과, 001 소유 파일 — 001 담당에게 알림)
- [ ] T011 [P] (008 T010이 아직이면) `B/shared/text/InvisibleCharacters.java`를 만들고 `B/post/domain/TitleNormalizer.java`가 쓰게 바꾼다(008 T005·T010과 같은 내용 — 먼저 하는 쪽만). 이미 있으면 확인만 한다
- [ ] T012 [P] 커서 `B/interaction/application/CommentCursor.java`(001 `CursorCodec` + `ListScope.of("comments:" + postId)`·`"replies:" + rootId`, 키 `[µs, id]`, 방향 확장 필드)를 만든다(T005 통과)
- [ ] T013 [P] 이벤트 `B/shared/event/CommentCreated.java`·`CommentDeleted.java`(record, `DomainEvent`, 필드는 contracts/events.md §1)를 만든다
- [ ] T014 저장소 뼈대 `B/interaction/infra/CommentRepository.java`(`JdbcClient`: `insert`, `findForUpdate(id)`, `findOwnedForUpdate(id, me)`, `lockRootShared(rootId, postId)`, `lockRootForUpdate(rootId)`, `countReplies(rootId)`, `markDeletedPlaceholder(id)`, `delete(id)`, `updateContent(id, content, now)`, `advisoryLock(long key)`, `findRecentDuplicate(...)`)와 `B/interaction/infra/CommentQueryRepository.java`(`findRoots`, `findReplyPreviews`(LATERAL), `findReplies`, `findForAround`)를 만든다. 각 메서드의 SQL은 research R6~R8·R11 그대로
- [ ] T015 [P] 화면 API `F/api/comments.ts`(`listComments(postId, {cursor, around})`, `listReplies(rootId, cursor)`, `createComment(postId, body)`, `editComment(id, content)`, `deleteComment(id)` — CSRF 헤더는 001 공용 `client.ts`), 타입 `F/api/types/comments.ts`(contracts/openapi.yaml 스키마), 문구 `F/features/comments/commentMessages.ts`(data-model §4-3 + 공통 코드, 503은 code와 상관없이 "잠시 후 다시 시도해 주세요")를 만든다

**Checkpoint**: 정리·상태·커서 규칙이 단위 테스트를 통과하고, 댓글 수·회원 표시 공개 API가 있다

---

## Phase 3: User Story 1 - 글의 댓글과 답글 읽기 (Priority: P1) 🎯 MVP

**Goal**: 글을 읽을 수 있는 사람은 댓글을 오래된 순으로 20개씩, 답글은 3개 + 20개씩 펼쳐 읽는다

**Independent Test**: 최상위 45개(일부 답글 8개)가 달린 공개 글을 비회원으로 열어 순서·개수·[더 보기]를 확인하고, 비공개로 바꾼 뒤 다른 회원의 요청이 없는 글과 같은 404인지 본다

### Tests for User Story 1 ⚠️

- [ ] T016 [P] [US1] 통합 테스트 `T/interaction/integration/CommentReadIT.java`(댓글은 SQL 시드): `최상위_오래된_순_20개씩_중복_누락_없음`(US1 #1, 45개 → 20·20·5, 페이지 경계에 같은 `created_at` 5개 — SC-004), `답글은_3개_뒤로_20개씩`(US1 #2, `replyCount` 8, `repliesNextCursor`로 5개), `답글의_답글은_대상_닉네임`·`대상이_탈퇴하면_탈퇴한_사용자에게`(US1 #3), `지운_최상위는_자리로_답글은_그대로`(US1 #4, `author`·`content` null), `임시_비공개_휴지통_없는_글은_같은_404`(US1 #5, 본문 바이트 비교, 작성자 본인의 임시글도 404), `댓글이_없으면_빈_목록`(US1 #6), `글_작성자_댓글은_isPostAuthor`(US1 #7), `공개가_아닌_글의_댓글은_no_store`(FR-015, 작성자가 보는 비공개 글), `페이지_SQL은_4번`(`SqlCounter`, 답글 수 1개·100개에서 같음 — SC-008)
- [ ] T017 [P] [US1] 권한 매트릭스 `TR/permission/comment.csv`(research R13의 4개 행동 × 행위자 × 대상 상태, owner `007`)와 `T/interaction/integration/CommentPermissionMatrixIT.java`(`AbstractPermissionMatrixIT` 상속), 실행기 `T/interaction/integration/permission/ListCommentsAction.java`(`comment.list`, `isWrite=false`)를 만든다. 나머지 실행기는 각 스토리에서 더한다(그 전까지 `pending: 007`)
- [ ] T018 [P] [US1] 화면 테스트 `F/features/comments/__tests__/CommentSection.test.tsx`: 머리말 "댓글 N", 20개 + [댓글 더 보기](불러오는 중 "불러오는 중…" 비활성, 실패 "불러오지 못했어요 [다시 시도]" + 같은 커서 재요청 + 보이는 댓글 유지 — FR-024), 답글 3개 + [답글 5개 더 보기], "@닉네임에게", 상태별 문구 4가지, [작성자] 배지, "첫 댓글을 남겨 보세요", 내용이 HTML로 해석되지 않음(`<b>`가 글자로), `white-space: pre-line`
- [ ] T019 [P] [US1] `F/pages/__tests__/PostDetailPage.test.tsx` 추가: 상세와 댓글 요청이 **동시에** 나간다(상세 응답을 늦춰도 댓글 요청이 먼저 시작), 댓글 요청 실패여도 본문이 보인다(Clarifications Q1)

### Implementation for User Story 1

- [ ] T020 [US1] `B/interaction/application/CommentViewAssembler.java`: 행 → `CommentView`(상태 판정·감추기·`replyTo`·`isPostAuthor`·`mine`), 회원 표시는 `MemberQueryService.findDisplays`, 사진은 `ProfileImageQuery.currentKeysOf` + `ImageUrlResolver`로 한 번씩(research R8)
- [ ] T021 [US1] 목록 Service(**006 머지 후** `CommentQueryService`에 더함 — T032에서 합침, 그 전에는 `CommentReadService`로 만들고 T032에서 옮김): `page(postId, cursor, viewer)` — `PostReadService.requireReadable` + `PUBLISHED` 확인 → `findRoots`(pageSize+1) → `findReplyPreviews` → 조립 → `nextCursor`·`repliesNextCursor`. `replies(rootId, cursor, viewer)` — 최상위 확인 → 그 글 확인 → `findReplies`
- [ ] T022 [US1] `B/interaction/web/CommentController.java`의 `GET /api/posts/{postId}/comments`·`GET /api/comments/{rootId}/replies`(`postId` 숫자 아니면 404, `Cache-Control`은 `CacheControlPolicy.forPost`(그 글의 상태)로)를 구현한다(T016 통과)
- [ ] T023 [US1] 화면 `F/features/comments/useCommentThread.ts`(research R14 상태 모양, id로 한 번만 그리기), `CommentSection.tsx`, `CommentItem.tsx`(상태별 표시, `id="comment-{id}"`, 긴 단어 `overflow-wrap: anywhere`), `ReplyList.tsx`, `comments.css`를 만든다(T018 통과)
- [ ] T024 [US1] `F/features/post-detail/CommentSectionSlot.tsx`가 `CommentSection`을 그리게 바꾸고(구현 메모 제거), `F/pages/PostDetailPage.tsx`가 주소의 글 번호로 상세 요청과 `listComments`를 동시에 시작해 결과를 `CommentSection`에 넘기게 한다(T019 통과, 005 소유 파일 — 005 회귀 `PostDetailPage.test.tsx` 함께 실행)
- [ ] T025 [US1] T017의 `comment.list` 행 전부 통과 확인(SC-001 일부)

**Checkpoint**: 댓글 읽기가 독립적으로 동작한다

---

## Phase 4: User Story 2 - 댓글과 답글 쓰기 (Priority: P1)

**Goal**: 이메일 인증한 회원이 한 단계 답글 구조로 댓글을 쓰고, 중복·과다 요청이 막힌다

**Independent Test**: 인증한 회원 둘로 최상위 → 답글 → 답글의 답글을 쓰고 구조·대상을 확인한다. 같은 요청 5건을 동시에 보내 1개만 생기는지 본다

### Tests for User Story 2 ⚠️

- [ ] T026 [P] [US2] 통합 테스트 `T/interaction/integration/CommentWriteIT.java`: `작성하면_201과_댓글_수_+1`(US2 #1), `금칙어가_있어도_거부하지_않는다`(FR-007), `답글에_답하면_같은_최상위_아래_대상_기록`(US2 #2), `내_답글에_답하면_대상_없음`(US2 #3), `비회원_401_인증_전_403_정지_403_탈퇴_유예_403`(US2 #4·#5), `공백만이면_COMMENT_REQUIRED`·`1001자면_COMMENT_TOO_LONG`(US2 #6·#7, `errors[].field = content`), `읽을_수_없는_글은_내용_검사보다_404가_먼저`(US2 #11), `삭제_숨김_탈퇴_다른_글_댓글에는_답글_불가`(US2 #12, `REPLY_TARGET_UNAVAILABLE`), `숨긴_글에는_쓸_수_없다`(작성자도 404), `11번째는_429와_Retry_After`(US2 #10), `앞_단계에서_걸린_요청은_세지_않는다`(400·404 10번 뒤에도 정상 10개 가능 — Q2), `10초_안_같은_요청은_200과_처음_댓글`(US2 #9, 이벤트 1번), `10초_뒤_같은_내용은_새_댓글`
- [ ] T027 [P] [US2] XSS 통합 테스트 `T/interaction/integration/CommentXssIT.java`: 12 §9-1 공격 문자열 목록(002 `ContentRendererXssTest`의 자원 재사용)을 댓글로 등록 → 응답 `content`가 입력(정리 후)과 같고 HTML로 바뀐 흔적 없음, 응답 `Content-Type: application/json`(SC-005, US2 #8)
- [ ] T028 [P] [US2] 동시성 통합 테스트 `T/interaction/integration/CommentConcurrencyIT.java`(1부): `같은_요청_5건_동시면_댓글은_1개`(SC-003, `CountDownLatch`, 응답 201 1개 + 200 4개, `comment_count` +1)
- [ ] T029 [P] [US2] 권한 실행기 `T/interaction/integration/permission/CreateCommentAction.java`(`comment.create`, 거부 때 `CommentSnapshot` — 그 글의 댓글 행 수 — 전후 같음)
- [ ] T030 [P] [US2] 화면 테스트 `F/features/comments/__tests__/CommentForm.test.tsx`: 비회원·인증 전 안내 문구와 버튼(FR-023), 글자 수 `[...text].length` 표시(이모지 1자), 등록 중 버튼 "등록 중…" 비활성, 실패 때 입력 유지 + 문구, 429 문구, 성공하면 입력 비움. `CommentSection.test.tsx` 추가: 아직 안 불러온 댓글이 있어도 내 댓글이 끝에 바로 보이고, [댓글 더 보기]로 같은 id가 와도 한 번만(Clarifications Q5), 답글은 그 최상위 아래 끝에, 머리말 수 +1

### Implementation for User Story 2

- [ ] T031 [US2] `B/interaction/application/CommentService.java`의 `create(postId, me, content, replyToCommentId)`: research R2 순서 — 계정 상태 → 글(`requireReadable` + `PUBLISHED` + 숨김 아님) → `CommentText` → 대상 미리 확인(R4) → `RateLimiter.acquireOrThrow("ratelimit:comment:" + me, …)` → 트랜잭션(`advisoryLock` → `findRecentDuplicate` → 있으면 기존 댓글 / 없으면 최상위 `FOR SHARE`·대상 재확인 → `insert` → `PostCounterService.adjustCommentCount(+1)` → `CommentCreated` 발행) → 조립. 결과에 "새로 만듦" 여부를 담아 컨트롤러가 201/200을 고른다(T026·T028 통과)
- [ ] T032 [US2] (**006 머지 후**) `B/interaction/application/CommentQueryService.java`(006이 만든 파일)에 T021의 `page`·`replies`를 옮겨 합치고 `commentIdsOfPost`는 그대로 둔다. 006 `PostPurgeIT`를 함께 돌린다
- [ ] T033 [US2] `CommentController`에 `@LoginRequired POST /api/posts/{postId}/comments`(요청 본문 `content`·`replyToCommentId`)를 더한다
- [ ] T034 [US2] 화면 `F/features/comments/CommentForm.tsx`와 `useCommentThread`의 `addMine(view)`(최상위 끝 / 그 최상위 답글 끝, id 중복 무시), [답글] 누르면 그 댓글 아래 입력칸(대상 = 그 댓글)을 구현한다(T030 통과). 인증 메일 재발송 버튼은 001 API를 부른다
- [ ] T035 [US2] T029 행 통과 확인

**Checkpoint**: US1 + US2로 댓글을 읽고 쓴다(MVP)

---

## Phase 5: User Story 3 - 내 댓글 고치고 지우기 (Priority: P1)

**Goal**: 작성자만 자기 댓글을 고치고 지우며, 답글 있는 최상위는 자리로 남고 빈 자리는 정리된다

**Independent Test**: 다른 회원·글 작성자로 남의 댓글 수정·삭제가 404이고 내용이 그대로인지, 자리·빈 자리 정리가 맞는지 확인한다

### Tests for User Story 3 ⚠️

- [ ] T036 [P] [US3] 통합 테스트 `T/interaction/integration/CommentEditDeleteIT.java`: `고치면_내용과_수정됨`(US3 #1), `같은_내용이면_아무것도_안_바뀐다`(US3 #2, `updated_at` 그대로), `글_작성자와_관리자도_남의_댓글은_404`(US3 #3·SC-006, 수정·삭제 모두, DB 행 전후 같음), `답글_있는_최상위는_자리로`(US3 #4, `content = ''`·`deleted_at`, 수 −1), `답글_없는_최상위와_답글은_행_삭제`(US3 #5), `마지막_답글을_지우면_자리도_사라진다`(US3 #6, 수 −1만), `숨긴_댓글은_409_삭제는_가능`(US3 #7, 숨김 댓글 삭제는 수 변화 0), `지운_자리는_다시_지우면_404`(US3 #8), `인증_전_회원도_자기_댓글은_지운다`, `비공개로_바뀐_글의_내_댓글도_지운다`(research R9 제안), `수정_1분_21번째는_429`, `삭제하면_CommentDeleted_자리여도`(이벤트 캡처)
- [ ] T037 [P] [US3] `CommentConcurrencyIT`(2부): `삭제와_답글이_동시면_하나씩`(FR-014 — 100번 반복, 결과가 "자리 + 답글 1" 또는 "행 없음 + 400" 둘 중 하나, 교착 0)
- [ ] T038 [P] [US3] 권한 실행기 `T/interaction/integration/permission/EditCommentAction.java`(`comment.update`)·`DeleteCommentAction.java`(`comment.delete`) — 대상 댓글은 MEMBER 행위자 계정으로 SQL 삽입(research R13)
- [ ] T039 [P] [US3] 화면 테스트 `F/features/comments/__tests__/CommentItem.test.tsx`: 본인 정상 댓글에만 [수정]·[삭제], 숨긴 본인 댓글은 [삭제]만, 남의 댓글에 [신고] 없음(Q4), 수정 칸(취소·저장, 저장 중 비활성, 실패 때 입력 유지), 삭제 확인 후 자리/사라짐 반영, 머리말 수 −1

### Implementation for User Story 3

- [ ] T040 [US3] `CommentService.edit(commentId, me, content)`·`delete(commentId, me)`(research R6·R9: 미리 확인 → 요청 제한(수정만) → 트랜잭션 잠금 순서 최상위 → 답글, 카운터, 빈 자리 정리, `CommentDeleted` 발행)를 구현한다(T036·T037 통과)
- [ ] T041 [US3] `B/interaction/web/CommentCommandController.java`의 `@LoginRequired PATCH /api/comments/{commentId}`(200)·`DELETE`(204)를 구현한다
- [ ] T042 [US3] 화면 `CommentItem.tsx`의 수정 칸·삭제 확인과 `useCommentThread`의 `applyEdited`·`applyDeleted`(자리로 바꾸기 / 지우기 / 빈 자리 정리 반영)를 구현한다(T039 통과)
- [ ] T043 [US3] T038 행 통과 확인 — 이로써 `comment.csv` 전 행이 `pending` 없이 통과한다(SC-001·SC-006)

**Checkpoint**: C-CMT-1 #1~#9가 모두 동작한다

---

## Phase 6: User Story 4 - 글 상태·숨김·탈퇴에 따라 댓글 함께 감추기 (Priority: P2)

**Goal**: 글이 공개에서 빠지면 댓글도 감춰지고, 숨김·탈퇴 작성자 댓글은 정해진 문구로 보이며, 숨김·탈퇴 정리에서도 댓글 수가 맞는다

**Independent Test**: 공개 → 비공개 → 공개로 바꾸며 댓글이 사라졌다 돌아오는지, 숨긴 댓글을 세 사람이 볼 때 표시가 다른지 확인한다

### Tests for User Story 4 ⚠️

- [ ] T044 [P] [US4] 통합 테스트 `T/interaction/integration/CommentVisibilityIT.java`: `비공개로_바꾸면_남에게_404_다시_공개하면_그대로`(US4 #1), `숨긴_댓글은_남과_글_주인에게_문구만`(US4 #2, 응답 JSON 전체에 원문 문자열 0회 — SC-007), `숨긴_댓글은_작성자에게_원문`(US4 #3), `탈퇴_유예_작성자_댓글은_문구와_수_그대로`(US4 #4, 복구하면 원래대로), `휴지통_글의_댓글은_보존되고_복구하면_돌아온다`, `글_작성자_탈퇴_유예면_보기_쓰기_404`, `숨긴_글은_작성자만_보고_쓰기는_404`
- [ ] T045 [P] [US4] 공개 Service 통합 테스트 `T/interaction/integration/CommentModerationServiceIT.java`: `hide`는 수 −1·멱등, `unhide`는 +1·멱등, 삭제된 자리 숨김은 404 예외, `snapshot`(014용)
- [ ] T046 [P] [US4] 탈퇴 정리 통합 테스트 `T/interaction/integration/CommentPurgeServiceIT.java`: US4 #5 — 남의 답글 있는 내 최상위만 자리, 내 답글·답글 없는 최상위·내 답글만 있던 최상위 삭제, 빈 자리 정리(내가 예전에 지운 자리 포함), 글별 수 감소, 남의 답글 `replyTo`는 익명 처리 뒤 `{withdrawn: true}`
- [ ] T047 [P] [US4] `CommentConcurrencyIT`(3부): `동시_작성_20건과_삭제_숨김_해제_탈퇴_정리_뒤_수가_같다`(SC-002 — 모든 글에서 `comment_count = count(*) WHERE deleted_at IS NULL AND hidden_at IS NULL`)

### Implementation for User Story 4

- [ ] T048 [US4] `B/interaction/application/CommentModerationService.java`(contracts/events.md §2-1)와 `B/interaction/application/CommentPurgeService.java`(§2-2, `@Transactional(propagation = MANDATORY)`, SQL 2-a~2-d)를 구현한다(T045·T046·T047 통과). 015가 부를 서명을 클래스 주석에 적는다
- [ ] T049 [US4] 탈퇴 작성자·숨김 표시가 조립(T020)에서 맞는지 확인하고 빠진 경우를 고친다(T044 통과). 화면 `CommentItem`의 회색 아이콘·"숨겨졌어요 (나만 보여요)" 표시를 `CommentItem.test.tsx`에 추가해 확인한다

**Checkpoint**: 댓글 수 불변식이 모든 경로에서 지켜진다

---

## Phase 7: User Story 5 - 알림에서 특정 댓글로 바로 가기 (Priority: P3)

**Goal**: `?comment=` 링크로 들어오면 그 댓글까지 펼치고 스크롤·강조한다

**Independent Test**: 최상위 60개 중 45번째의 5번째 답글 링크를 열어 펼침·강조·[이전 댓글 보기]를 확인한다

### Tests for User Story 5 ⚠️

- [ ] T050 [P] [US5] 통합 테스트 `T/interaction/integration/CommentAroundIT.java`: `대상_최상위부터_20개와_prevCursor`(US5 #1), `prevCursor로_앞_20개`, `4번째_이후_답글이면_대상까지_펼친다`(US5 #2, `focusCommentId`), `상한을_넘는_답글은_처음_3개만`, `다른_글_삭제_숨김_없는_댓글이면_첫_페이지와_같다`(US5 #3 — `around` 없는 응답과 바이트 비교), `숫자가_아닌_around는_무시`
- [ ] T051 [P] [US5] 화면 테스트 `F/features/comments/__tests__/CommentAround.test.tsx`: `aroundCommentId`가 있으면 `listComments({around})`, `focusCommentId` 요소로 `scrollIntoView` + 강조 클래스 2초, [이전 댓글 보기]로 앞에 붙임, `focusCommentId = null`이면 스크롤 없음

### Implementation for User Story 5

- [ ] T052 [US5] `CommentQueryService.page`에 `around` 처리(research R11)와 `CommentQueryRepository.findForAround`를 더하고 `CommentController`가 `around`를 넘기게 한다(T050 통과)
- [ ] T053 [US5] `useCommentThread`의 around·`loadPrevious`와 `CommentSection`의 [이전 댓글 보기]·스크롤·강조(`prefers-reduced-motion`이면 애니메이션 없이 테두리만)를 구현한다(T051 통과)

**Checkpoint**: 모든 스토리가 독립적으로 동작한다

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 성능 측정, 종단 확인, 공통 코드 정리, 인계

- [ ] T054 [P] 성능 측정 `T/interaction/integration/CommentPerformanceIT.java`(`@Tag("perf")`): quickstart §4 시드를 `generate_series`로 만들고 4가지 요청의 p95·SQL 수·`EXPLAIN`을 출력한다. 결과를 quickstart §4 표에 적는다(SC-008)
- [ ] T055 [P] 종단 확인 `E/comment.spec.ts`(Playwright): quickstart §3의 1~9번을 자동화한다(두 회원 컨텍스트)
- [ ] T056 [P] 375px·접근성 확인: 답글 들여쓰기·긴 URL·긴 닉네임에서 가로 스크롤 없음, 버튼 `aria-label`("{닉네임}님 댓글에 답글"), 강조가 색만이 아닌지(테두리), 수정 칸 `Esc` 취소
- [ ] T057 Redis OOM 경로 확인(research R17): `RedisOutage`가 OOM을 흉내 낼 수 있으면 댓글 작성이 503 `AUTOSAVE_UNAVAILABLE`을 받는지 기록하고, 화면이 "잠시 후 다시 시도해 주세요" + 입력 유지인지 확인한다. 결과를 ANALYSIS-tier-bc 팀 결정 항목에 보탠다
- [ ] T058 (002 담당 확인 후) 요청 과다 코드 통일(Clarifications Q3, research R16): `B/post/domain/PostReasonCode.java`의 `RATE_LIMITED`를 지우고 `MarkdownPreviewService`·`AutosaveService`가 `RateLimiter.acquireOrThrow`(공통 `TOO_MANY_REQUESTS`)를 쓰게 바꾼다. 화면 `F/api/client.ts`·에디터의 `RATE_LIMITED` 분기를 `TOO_MANY_REQUESTS`로 바꾸고 002 테스트(`AutosaveRateLimitIT`·`MarkdownPreviewIT` 등 해당 이름)를 갱신한다. 002 소유 파일이므로 002 담당과 같이 한다
- [ ] T059 [P] 004 문구 정리: `specs/004-visibility-permission/spec.md`의 비회원 댓글 문구("로그인하고 댓글 쓰기")를 이 spec FR-023 문구로 맞추는 변경을 004 담당에게 제안한다(문서 변경, ANALYSIS-tier-bc)
- [ ] T060 [P] 015 인계: `specs/015-withdraw/tasks.md`의 `CommentWithdrawalPurgeStep` 작업이 `CommentPurgeService.purgeByAuthor` 서명(contracts/events.md §2-2)과 order 20을 쓰는지 확인한다
- [ ] T061 `grep -rn "007 댓글 기능이\|007-comments가 확장\|TODO(007)" backend/src frontend/src`가 0건인지 확인하고 남은 표시를 정리한다(005 T041·006 T058 구현 메모)
- [ ] T062 quickstart.md §1~§5를 처음부터 끝까지 실행하고 결과를 기록한다
- [ ] T063 전체 회귀: `./mvnw -pl backend verify`(001·004·005·006 테스트 포함)와 `npm test`·`npm run build`·`npm run lint`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001·004·005의 해당 작업이 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2)**: Setup 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
  - US2의 T032는 **006 머지 후**(그 전에는 T021의 임시 이름 `CommentReadService`로 진행)
- **Polish (Phase 8)**: 원하는 스토리 완료 후. T058은 002 담당 일정에 따름

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. 다른 스토리에 의존하지 않는다(댓글은 SQL 시드)
- **US2 (P1)**: Foundational 이후. 화면은 US1의 `useCommentThread`(T023) 후
- **US3 (P1)**: US2의 `CommentService`(T031) 후 — 같은 파일
- **US4 (P2)**: US1의 조립(T020) 후. 공개 Service(T048)는 Foundational 이후 독립
- **US5 (P3)**: US1의 목록 Service 후

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 도메인 → 저장소 → Service → Controller → 화면 API → 훅 → 컴포넌트 → 페이지
- 같은 파일을 고치는 작업은 순서대로 한다: `CommentService`(T031 → T040), `CommentQueryService`(T021 → T032 → T052), `CommentController`(T022 → T033 → T052), `CommentConcurrencyIT`(T028 → T037 → T047), `useCommentThread.ts`(T023 → T034 → T042 → T053), `CommentItem.tsx`(T023 → T042 → T049), `CommentSection.test.tsx`(T018 → T030)

### Parallel Opportunities

- Phase 2: 테스트 T003~T007 병렬, 구현 T008~T013·T015 병렬
- US1: 테스트 T016~T019 병렬
- US2: 테스트 T026~T030 병렬
- US3: 테스트 T036~T039 병렬
- US4: 테스트 T044~T047 병렬, T048은 US1~US3과 병렬 가능(다른 파일)
- Polish: T054~T056·T059·T060 병렬

---

## Parallel Example: User Story 2

```bash
# User Story 2 테스트를 함께 작성:
Task: "CommentWriteIT in backend/src/test/java/com/team/blog/interaction/integration/CommentWriteIT.java"
Task: "CommentXssIT in backend/src/test/java/com/team/blog/interaction/integration/CommentXssIT.java"
Task: "CommentConcurrencyIT 1부 (동시 5건)"
Task: "CreateCommentAction + comment.csv 행"
Task: "CommentForm.test.tsx in frontend/src/features/comments/__tests__/"

# 서버와 화면을 함께 구현:
Task: "CommentService.create + POST 컨트롤러 (backend)"
Task: "CommentForm.tsx + addMine (frontend)"
```

## Parallel Example: Foundational

```bash
Task: "CommentTextTest", "CommentStateTest", "CommentCursorTest", "PostCounterServiceIT", "MemberDisplayQueryIT"
Task: "CommentReasonCode/CommentState/CommentText", "PostCounterService", "MemberQueryService.findDisplays", "CommentCursor", "CommentCreated/CommentDeleted", "api/comments.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2 → US3)

1. Phase 1 확인(T001), 설정(T002)
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 시드 댓글 읽기, 404 동일성
4. Phase 4 US2 → 쓰기·중복·제한
5. Phase 5 US3 → 수정·삭제. 여기까지가 **권장 MVP**(C-CMT-1 #1~#9)
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 규칙·공개 API
2. US1 → 읽기 → 데모
3. US2 → 쓰기 → 데모
4. US3 → 고치기·지우기
5. US4 → 감추기·숨김·탈퇴 정리(014·015가 부를 Service)
6. US5 → 바로 가기(011과 함께 가치)
7. Polish → 측정·종단 확인·공통 코드 정리

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 서버 → US2 서버 → US3 서버(`CommentService`·`CommentQueryService` 소유)
   - Developer B: US1 화면 → US2 화면 → US3 화면 → US5 화면
   - Developer C: US4(`CommentModerationService`·`CommentPurgeService`·혼합 일관성 테스트) → US5 서버
3. `CommentService`는 A가 소유하고 다른 사람의 변경은 A의 작업 뒤에 붙인다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 001·002·005·006 소유 파일을 고치는 작업(T010·T011·T024·T032·T058)은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- 댓글 원문은 로그에 남기지 않는다(오류 로그에는 댓글 번호만)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
