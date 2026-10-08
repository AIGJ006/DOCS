---

description: "Task list for 010-follow-feed (팔로우·팔로잉 피드)"
---

# Tasks: 팔로우·팔로잉 피드

**Input**: Design documents from `/specs/010-follow-feed/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, follow-sql.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 interaction 모듈의 팔로우 관계와 discovery 모듈의 피드를 소유한다. 005가 남긴 `AuthorFollowStatusQuery` 기본 구현(항상 false), `AuthorCard.followButton` 자리, 블로그 머리말 확장 자리를 채운다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `@LoginRequired`, `AccountStatusGuard`(`ACCOUNT_WRITE`), `WithdrawnAccountGateFilter`, `RateLimiter`, `CursorCodec`·`ListScope`, `MemberQueryService.findReadableBlogOwner`·`normalizeHandle`, CSRF, 로그인 `returnTo`, `SessionBar`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`·`SqlCounter`
- 선행: specs/004 — `VisibilityFilter.forViewer`, 권한 하네스(`support/permission/`, `PostFixtures.State.AUTHOR_WITHDRAWN`), `useAuthGate`, 공통 404 화면
- 선행: specs/005 — `PostCardQueryRepository`·`PostListService`·`PostListCursor`·`PostCardAssembler`, `BlogQueryService`·`BlogHeaderView`, `PostReadingPorts`(`AuthorFollowStatusQuery`), `PageShellController`, `PostCardGrid`·`LoadMoreButton`·`useCursorList`·`listRestore`, `AuthorCard`, `BlogPage`
- 선행(둘 중 먼저 하는 쪽이 만듦): specs/008 T014 `CardFilter` — 008이 아직이면 이 기능의 T011이 `CardFilter(authorId, followerId)`로 만들고 008이 `tagId`를 더한다

**006 머지 후**

- 없음. 006과 겹치는 파일이 없다(글 완전 삭제는 `follow`와 관계없음)

**후속 (다른 스펙이 이 기능을 사용)**

- 011-notification: `MemberFollowed`·`MemberUnfollowed` 구독(새 팔로워 묶음 알림), 새 글 알림은 `follow`를 직접 읽는 `INSERT … SELECT`(25 §4-1), 새 팔로워 알림 링크 `/@내주소/followers`
- 015-withdraw: `FollowWithdrawalPurgeStep`(order 65, 이 기능의 T041), 유예 중 제외는 이 기능의 SQL 조건
- 012-trending-search: 영향 없음

**팀 결정 대기 (기본안으로 진행)**

- 팔로우 코드를 interaction 모듈에 두는 것(research R2), 팔로우에 `ActionKind.ACCOUNT_WRITE`를 쓰는 것(R3), `CANNOT_FOLLOW_SELF` 문구 — 확인 작업 T003

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 설정값, 팀 확인 질문

- [ ] T001 선행 확인: V1 `follow`(PK·`ck_follow_self`·`ix_follow_followee`·`ix_follow_follower`)·`ix_member_withdraw_purge`, `B/post/application/port/AuthorFollowStatusQuery.java`·`B/post/config/PostReadingPorts.java`(`@ConditionalOnMissingBean`), `B/discovery/infra/PostCardQueryRepository.java`(`CardFilter`가 있는지 — 008), `B/discovery/application/BlogHeaderView.java`, `B/discovery/web/PageShellController.java`, `F/components/AuthorCard.tsx`(`followButton`), `F/pages/BlogPage.tsx`, `F/features/post-list/useCursorList.ts`가 있는지 기록한다
- [ ] T002 [P] 설정값: `B/interaction/application/FollowProperties.java`(`@ConfigurationProperties("blog.follow")` + `@Validated`: `rateLimit.limit`(1 이상)·`rateLimit.window`, `listPageSize`(1~100)), `R/application.yml`에 research R12 기본값, 테스트 `T/interaction/unit/FollowPropertiesBindingTest.java`
- [ ] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: ① 팔로우를 interaction 모듈에(R2) ② 팔로우에 `ActionKind.ACCOUNT_WRITE`(R3, 001 `ActionKind` 주석에 "팔로우" 추가) ③ `CANNOT_FOLLOW_SELF` 문구 "자기 자신은 팔로우할 수 없어요". 답이 오기 전에는 기본안으로 진행한다

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 저장소·수·목록 SQL, 카드 조회 팔로우 조건, 이벤트, 이유 코드, 커서, 화면 API

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

- [ ] T004 [P] 통합 테스트 `T/interaction/integration/FollowRepositoryIT.java`(contracts/follow-sql.md §1~§3): `insertIfAbsent`가 처음만 true·두 번째 false, `deleteIfPresent`가 있을 때만 true, 자기 팔로우 행은 `ck_follow_self` 위반, `countFollowers`·`countFollowing`이 탈퇴 유예 회원을 빼고 정지 회원은 셈, `pageFollowers`·`pageFollowing`이 `created_at DESC, 회원 번호 DESC`·커서 이후만·`status = WITHDRAWN` 제외·현재 프로필 사진 키, `followedAmong(viewer, ids)`
- [ ] T005 [P] 통합 테스트 `T/discovery/integration/PostCardFollowFilterIT.java`: `CardFilter(followerId = A)`가 A가 팔로우한 작성자의 공개 발행 글만 돌려주고, 비공개·임시·휴지통·숨김·유예 작성자 글·A 자신의 글은 없음. `followerId` 없는 기존 호출(홈·블로그·008 태그)의 결과가 바뀌지 않음 — 005 `HomePostListIT`·`BlogPostListIT`(이름은 005 tasks 기준)를 함께 돌린다

### Implementation for Foundational

- [ ] T006 [P] `B/interaction/domain/FollowReasonCode.java`(`CANNOT_FOLLOW_SELF` 400 "자기 자신은 팔로우할 수 없어요", 끝 마침표 없음)
- [ ] T007 [P] 이벤트 `B/shared/event/MemberFollowed.java`(`followerId, followeeId, followedAt`)·`B/shared/event/MemberUnfollowed.java`(`followerId, followeeId, unfollowedAt`) — 001 `DomainEvent` 규칙
- [ ] T008 [P] `B/interaction/application/FollowListCursor.java`: 001 `CursorCodec`으로 `followers:{handle}`·`following:{handle}` 범위, 키 `[created_at 마이크로초, 회원 번호]` 인코딩·디코딩(다른 범위 → `InvalidCursorException`)과 단위 테스트 `T/interaction/unit/FollowListCursorTest.java`
- [ ] T009 `B/interaction/infra/FollowRepository.java`(`JdbcClient`, contracts/follow-sql.md §1~§3)를 구현한다. 클래스 주석에 원칙 II 읽기 예외(`member`·`image`, plan Complexity Tracking)를 적는다 (T004 통과)
- [ ] T010 [P] `ListScope`에 `feed()`·`followers(handle)`·`following(handle)` 정적 메서드를 더한다(`B/shared/web/cursor/ListScope.java`, 값 규칙 주석에 세 값 추가 — 001 소유 파일, 담당에게 알림)
- [ ] T011 카드 조회 팔로우 조건: `B/discovery/infra/PostCardQueryRepository.java`의 `CardFilter`에 `followerId`를 더하고(008 T014가 만든 레코드 — 없으면 이 작업이 `CardFilter(authorId, followerId)`로 만들고 `findCards`·`cardQuery`·`PostListService.page`의 기존 호출을 고친다) 있으면 `AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :followerId AND f.followee_id = p.author_id)`를 붙인다. 클래스 주석의 원칙 II 예외 문단에 `follow`를 더한다 (T005 통과)
- [ ] T012 [P] 화면 API `F/api/follows.ts`(`follow(handle)`, `unfollow(handle)`, `listFollowers(handle, cursor)`, `listFollowing(handle, cursor)`, `getFeed(cursor)` — 001 `client.ts`)와 타입 `F/api/types/follow.ts`(`FollowState`, `FollowListItem`, `FeedPage`), 005 `F/api/types/reading.ts`의 `BlogHeader`에 `followerCount`·`followingCount`·`followedByMe`

**Checkpoint**: 저장소·카드 조건·이벤트 준비 완료 — user story 시작 가능

---

## Phase 3: User Story 1 - 다른 회원을 팔로우하고 언팔로우한다 (Priority: P1) 🎯 MVP

**Goal**: 블로그 머리말과 글 상세 작성자 카드에서 바로 팔로우·언팔로우하고, 관계는 언제나 하나다

**Independent Test**: A로 B를 팔로우·언팔로우하고, 같은 팔로우 요청을 동시에 20번 보내 관계 1개·이벤트 1번인지, 자기 자신 팔로우가 거부되는지 확인한다

### Tests for User Story 1 ⚠️

- [ ] T013 [P] [US1] 통합 테스트 `T/interaction/integration/FollowApiIT.java`(`@RecordApplicationEvents`): `US1_1_팔로우_응답`(`{following:true, followerCount}`, `MemberFollowed` 1번) · `US1_2_언팔로우`(`MemberUnfollowed` 1번) · `US1_4_안_한_상태_언팔로우_200_변화없음`(이벤트 0) · 이미 팔로우 중 `PUT` 200·이벤트 0 · `US1_5_자기자신_400`(`CANNOT_FOLLOW_SELF`, 행 0) · `US1_6_비회원_401` · `US1_7_인증전_200` · `US1_8_없는주소_유예회원_404`(본문 바이트 같음, 대문자 주소도 404) · 유예 회원 본인 403 `ACCOUNT_WITHDRAWN` · 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED` · 정지된 대상은 팔로우 가능 · 관리자 동일 · 팔로우·언팔로우 합쳐 31번째 429 `TOO_MANY_REQUESTS` + `Retry-After` · Redis 정지 중 제한 없이 200 · CSRF 헤더 없으면 403 · 판정 순서: 유예 회원이 자기 자신을 팔로우 → 403, 요청 횟수를 넘긴 회원이 없는 주소 → 404(429보다 먼저), 요청 횟수를 넘긴 회원이 자기 자신 → 400
- [ ] T014 [P] [US1] 동시성 통합 테스트 `T/interaction/integration/FollowConcurrencyIT.java`: `US1_3_동시_20번_관계_1개`(행 1, `MemberFollowed` 1번 — SC-001), 팔로우·언팔로우 섞어 50번 3회 → 마지막 상태와 행 수가 맞음·이벤트 수 = 실제 바뀐 수
- [ ] T015 [P] [US1] 통합 테스트 `T/discovery/integration/BlogHeaderFollowIT.java`: `GET /api/members/{handle}`에 `followerCount`·`followingCount`(유예 회원 제외)·`followedByMe`(비회원·내 블로그 false), 005 기존 칸 그대로, 글 상세 `viewer.followingAuthor`가 실제 값(팔로우 전 false → 후 true)
- [ ] T016 [P] [US1] 화면 테스트 `F/features/follow/__tests__/FollowButton.test.tsx`·`useFollowToggle.test.ts`(가짜 타이머): [팔로우] → 누르는 즉시 [팔로잉 ✓]·수 +1, 마우스·초점이면 [언팔로우] 글자, 확인 창 없음, 0.3초 안에 여러 번 누르면 마지막 상태 한 번만 전송, 응답 값으로 맞춤, 실패하면 되돌림 + "잠시 후 다시 시도해 주세요"(`role="status"`), 비회원이면 `useAuthGate` 로그인 안내(요청 없음), `aria-pressed`, 내 블로그면 버튼 없음

### Implementation for User Story 1

- [ ] T017 [US1] `B/interaction/application/FollowService.java`를 구현한다(research R3·R4): `follow(me, handle)`·`unfollow(me, handle)` — `AccountStatusGuard.requireActive(me, ACCOUNT_WRITE)` → `findReadableBlogOwner` 없으면 `NotFoundException` → 자기 자신 400 → `RateLimiter.acquireOrThrow("ratelimit:follow:" + me, …)`(트랜잭션 밖) → `@Transactional` 안에서 `insertIfAbsent`/`deleteIfPresent` → 바뀐 경우만 이벤트 → `countFollowers` → `FollowState`
- [ ] T018 [US1] `B/interaction/web/FollowController.java`(`PUT`·`DELETE /api/members/{handle}/follow`, `@LoginRequired`, `@CurrentUser`) (T013·T014 통과)
- [ ] T019 [P] [US1] `B/interaction/application/FollowQueryService.java`의 `isFollowing`·`headerStats(ownerId, viewer)`(contracts §6)와 `B/interaction/application/AuthorFollowStatusQueryAdapter.java`(`AuthorFollowStatusQuery` 구현 Bean — 005 기본 구현이 물러나는지 확인)
- [ ] T020 [US1] 005 `B/discovery/application/BlogHeaderView.java`에 세 칸을 더하고 `BlogQueryService.getHeader`가 `FollowQueryService.headerStats`를 부르게 한다(005 소유 파일 — 005 블로그 테스트 함께 실행) (T015 통과)
- [ ] T021 [P] [US1] `F/features/follow/useFollowToggle.ts`(즉시 반영·0.3초 마지막 상태·되돌림, 009 `useLikeToggle`과 같은 방식)·`F/features/follow/followMessages.ts`(24 §2 문구, 끝 마침표 없음)
- [ ] T022 [US1] `F/features/follow/FollowButton.tsx`(props: `handle`, `initialFollowing`, `onCountChange?`, `isMe`) (T016 통과)
- [ ] T023 [US1] 005 `F/pages/PostDetailPage.tsx`에서 `AuthorCard`의 `followButton`에 `FollowButton`(`initialFollowing = viewer.followingAuthor`)을 넣고 005 `PostDetailPage.author.test.tsx`에 사례를 더한다
- [ ] T024 [US1] 005 `F/pages/BlogPage.tsx` 머리말에 `FollowButton`(`isMe`면 없음, `followedByMe`로 시작, 팔로워 수 변화를 머리말 수에 반영)을 넣고 005 `BlogPage.test.tsx`에 사례를 더한다

**Checkpoint**: 팔로우·언팔로우가 끝까지 동작한다(US1 단독 데모 가능)

---

## Phase 4: User Story 2 - 팔로우한 사람의 새 공개 글을 피드에서 본다 (Priority: P1)

**Goal**: 로그인한 회원이 별도 피드 페이지에서 팔로우한 사람의 공개 발행 글만 홈과 같은 방식으로 본다

**Independent Test**: A가 B·C를 팔로우한 상태에서 여러 상태의 글을 만들어 두고, 피드에 B·C의 공개 발행 글만 최신순으로 끝까지 중복·누락 없이 나오는지 확인한다

### Tests for User Story 2 ⚠️

- [ ] T025 [P] [US2] 통합 테스트 `T/discovery/integration/FeedApiIT.java`: `US2_1_팔로우한_사람_공개글만_최신순`(같은 `first_public_at`이면 글 번호 큰 순, 카드 칸은 005 카드와 같음) · `US2_2_비공개_임시_휴지통_숨김_유예작성자_제외`(SC-003, 친구 공개 값이 있으면 그것도 제외) · `US2_3_끝까지_넘기기_중복누락_0`(30개, SC-004) · `US2_4_언팔로우_직후_빠짐`(SC-005) · `US2_5_팔로우_없음_hasFollowing_false` · `US2_6_글_없음_hasFollowing_true` · `US2_7_비회원_401` · 유예 회원 403 · 다른 목록 커서 400 `INVALID_CURSOR` · 카드 SQL 1번 + 사진 조회(`SqlCounter`) · 글 1만 건·팔로우 300명에서 `EXPLAIN (ANALYZE)` 200ms 이내 · `Cache-Control: private, no-cache` · 클라이언트 `size` 무시
- [ ] T026 [P] [US2] 화면 테스트 `F/pages/__tests__/FeedPage.test.tsx`: 9개 카드·[더 보기]·이미 있는 글 건너뛰기, 빈 상태 두 문구(`hasFollowing`), 비로그인이면 `/login?returnTo=/feed`, 뒤로 가기 복원(`listKey: 'feed'`, 30분), 로딩·실패 표시는 홈과 같음, 머리말 [피드]는 로그인했을 때만(`SessionBar`)

### Implementation for User Story 2

- [ ] T027 [US2] `B/discovery/application/FeedQueryService.java`: `page(me, cursor)` → `PostListService.page(ListScope.feed(), CardFilter(followerId = me), cursor, Viewer.anonymous())` + 첫 페이지가 비었을 때만 `FollowQueryService.hasFollowing(me)`(interaction 공개 메서드를 더함) → `FeedPage`
- [ ] T028 [US2] `B/discovery/web/FeedController.java`(`GET /api/feed`, `@LoginRequired`, `Cache-Control: private, no-cache` — 005 `CacheControlPolicy.NO_CACHE`) (T025 통과)
- [ ] T029 [US2] `F/pages/FeedPage.tsx`(005 `PostCardGrid`·`LoadMoreButton`·`useCursorList({listKey: 'feed', restore: true})`)와 `F/App.tsx` `/feed` 경로, 001 `F/features/auth/SessionBar.tsx`에 로그인했을 때만 [피드] 링크(001 담당에게 알림) (T026 통과)

**Checkpoint**: 팔로우와 피드가 모두 동작한다(US1 + US2 = 권장 MVP)

---

## Phase 5: User Story 3 - 누구나 팔로워·팔로잉 수와 목록을 본다 (Priority: P2)

**Goal**: 비회원도 블로그 머리말의 수와 20개씩 이어지는 목록을 본다. 탈퇴 유예 회원은 빠진다

**Independent Test**: 비회원으로 B의 팔로워·팔로잉 목록을 열어 정렬·항목·[더 보기]·유예 회원 제외를 확인한다

### Tests for User Story 3 ⚠️

- [ ] T030 [P] [US3] 통합 테스트 `T/interaction/integration/FollowListApiIT.java`: `US3_1_비회원도_수와_목록` · `US3_2_항목_칸과_정렬`(`handle`·`nickname`·`profileImageUrl`·`bio`·`followedByMe`·`isMe`, 최근 팔로우 순·같은 `created_at`이면 회원 번호 큰 순, 45명 → 20·20·5 중복·누락 0) · `US3_3_유예회원_빠졌다가_복구하면_돌아옴`(수와 목록 모두) · `US3_4_빈_목록` · `US3_5_없는_유예_주소_404` · 다른 목록(`following:` ↔ `followers:`, 다른 주소) 커서 400 · SQL 2번(로그인)·1번(비회원) · 클라이언트 `size` 무시 · `Cache-Control: private, no-cache` · 탈퇴 유예 회원이 로그인해 목록을 보면 403(게이트)
- [ ] T031 [P] [US3] 성능 통합 테스트 `T/interaction/integration/FollowCountPerformanceIT.java`(SC-007): 팔로워 1만 명(유예 50명) → 수 9,950, `EXPLAIN (ANALYZE, BUFFERS)`에 `ix_follow_followee`, 실행 10ms 이내(넘으면 실패 메시지에 "24 §5 카운터 검토"). 팔로잉 수도 `ix_follow_follower`
- [ ] T032 [P] [US3] 페이지 셸 통합 테스트 `T/discovery/integration/FollowListPageShellIT.java`: `/@{handle}/followers`·`/following` — 대문자 → 301 소문자(쿼리 유지), 없는 주소·유예·익명 처리 → 404 + 공통 404 HTML(005 `NotFoundPageRenderer`), 정상 → 200 SPA 셸
- [ ] T033 [P] [US3] 화면 테스트 `F/pages/__tests__/FollowListPage.test.tsx`·`F/features/follow/__tests__/FollowCounts.test.tsx`: "공개 글 24 · 팔로워 12 · 팔로잉 30"(숫자 `toLocaleString`, 팔로워·팔로잉은 목록 링크), 목록 제목·항목(소개 첫 줄만, 텍스트 노드), `isMe` 항목 버튼 없음, 20개 [더 보기], 빈 문구 두 가지, 404면 공통 404 화면

### Implementation for User Story 3

- [ ] T034 [US3] `FollowQueryService`에 `followers(handle, cursor, viewer)`·`following(handle, cursor, viewer)`(대상 확인 → `FollowRepository.page*` → `followedAmong` → 사진 주소 `ImageUrlResolver`) (T030·T031 통과)
- [ ] T035 [US3] `B/interaction/web/FollowListController.java`(`GET /api/members/{handle}/followers|following`, 로그인 불필요, `Cache-Control: private, no-cache`)
- [ ] T036 [US3] 005 `B/discovery/web/PageShellController.java`에 `/@{handle}/followers`·`/@{handle}/following` 매핑을 블로그 셸과 같은 규칙으로 더한다(005 소유 파일 — 005 셸 테스트 함께 실행) (T032 통과)
- [ ] T037 [P] [US3] `F/features/follow/FollowCounts.tsx`와 005 `F/pages/BlogPage.tsx` 머리말의 "공개 글 N" 줄 교체(팔로우 버튼 변화가 팔로워 수에 반영)
- [ ] T038 [US3] `F/features/follow/FollowListItem.tsx`와 `F/pages/FollowListPage.tsx`(`mode: 'followers' | 'following'`, 005 `useCursorList`·`LoadMoreButton`, 복원 없음) + `F/App.tsx`에 `/:handle/followers`·`/:handle/following` 경로(`/:handle`보다 먼저) (T033 통과)

**Checkpoint**: 수·목록까지 동작한다

---

## Phase 6: User Story 4 - 탈퇴하는 회원의 팔로우 관계를 정리한다 (Priority: P3)

**Goal**: 탈퇴 유예 회원은 수·목록·피드에서 빠졌다가 복구하면 돌아오고, 익명 처리 때 관계가 양방향 삭제된다

**Independent Test**: A↔B 양방향 팔로우에서 A가 신청·복구·30일 경과하는 각 시점의 B 수·목록·피드와 남은 행을 확인한다

### Tests for User Story 4 ⚠️

- [ ] T039 [P] [US4] 통합 테스트 `T/interaction/integration/FollowWithdrawalIT.java`: `US4_1_유예중_빠짐`(행은 남음, B의 팔로워·팔로잉 수·목록에서 A 없음, B 피드에 A 글 없음 — 015가 없으면 DB에서 `status`·`withdrawn_at`을 직접 바꿈) · `US4_2_복구하면_그대로`(SC-006) · `US4_3_정리하면_양방향_0행`(`FollowWithdrawalPurgeStep.purge(A)`를 트랜잭션 안에서 호출, 남의 관계는 그대로 — SC-008) · 트랜잭션 밖 호출이면 예외(`MANDATORY`) · order 65

### Implementation for User Story 4

- [ ] T040 [US4] 015의 `B/shared/application/withdraw/WithdrawalPurgeStep.java`가 없으면 015 tasks T009 내용 그대로 먼저 만든다(한 파일 — 015 담당에게 알림)
- [ ] T041 [US4] `B/interaction/application/FollowWithdrawalPurgeStep.java`(order 65, contracts/follow-sql.md §7, 지운 행 수 INFO) (T039 통과)
- [ ] T042 [P] [US4] 015 `R/application.yml`의 `blog.withdraw.purge.redis-key-templates`에 `ratelimit:follow:{memberId}`가 있는지 확인하고 없으면 더한다(015 research R16 기본 목록에는 이미 있음)

**Checkpoint**: 모든 user story 완료

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 권한 매트릭스, 종단 확인, 정리, 인계

- [ ] T043 [P] 권한 매트릭스: `TR/permission/follow.csv`(research R11 표, owner `010`)와 `T/interaction/permission/FollowActions.java`(`follow.put`·`follow.delete`·`follow.followers`·`follow.following`·`feed.read` — 대상 회원은 글 픽스처의 작성자, `AUTHOR` 행위자는 자기 자신), `T/interaction/permission/FollowPermissionMatrixIT.java`(004 `AbstractPermissionMatrixIT` 상속)
- [ ] T044 [P] 종단 확인 `E/follow-feed.spec.ts`(Playwright): quickstart §3의 1~11번(두 회원·비로그인 컨텍스트, 네트워크 끊기는 `page.route`)
- [ ] T045 [P] 375px·접근성: 피드·목록·머리말 가로 스크롤 없음, 버튼 44px 이상, 포커스 표시, [팔로잉 ✓]↔[언팔로우] 전환이 키보드 초점에서도 동작, 상태가 색만으로 구분되지 않음
- [ ] T046 `grep -rn "010 팔로우\|TODO(010)\|010이 넘겨받\|010 전까지" backend/src frontend/src`가 0건인지 확인하고 남은 표시를 정리한다(005 `AuthorFollowStatusQuery`·`AuthorCard`·`BlogHeaderView` 주석)
- [ ] T047 [P] 001 `B/shared/security/ActionKind.java` 주석의 `ACCOUNT_WRITE` 설명에 "팔로우"를 더한다(T003 결과가 기본안일 때, 001 담당에게 알림)
- [ ] T048 [P] 011 인계 확인: `specs/011-notification/tasks.md`가 `MemberFollowed`·`MemberUnfollowed` 필드 이름(contracts/follow-sql.md §5)과 새 글 알림의 `follow` 직접 읽기(25 §4-1)를 쓰는지 확인한다
- [ ] T049 quickstart.md §1~§4를 처음부터 끝까지 실행하고 결과를 기록한다
- [ ] T050 전체 회귀: `./mvnw -pl backend verify`(004·005·008 테스트 포함)와 `npm test`·`npm run build`·`npm run lint`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001·004·005의 해당 작업이 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: T003은 답을 기다리는 동안 다른 작업을 막지 않는다
- **Foundational (Phase 2)**: Setup 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
- **Polish (Phase 7)**: 원하는 스토리 완료 후

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. 다른 스토리에 의존하지 않는다
- **US2 (P1)**: Foundational(T011 카드 조건) 이후 서버는 독립. 피드를 채우려면 팔로우 행이 있어야 하므로 시험 데이터는 SQL 픽스처로 넣는다
- **US3 (P2)**: US1의 `FollowQueryService`(T019) 후 — 같은 파일. `BlogPage.tsx`는 US1 T024 뒤에 T037
- **US4 (P3)**: Foundational 이후 독립. 015 실제 흐름 확인은 015 머지 후

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 저장소 → Service → Controller → 화면 API → 훅 → 컴포넌트 → 경로
- 같은 파일을 고치는 작업은 순서대로 한다: `FollowQueryService`(T019 → T027 → T034), `BlogPage.tsx`(T024 → T037), `F/App.tsx`(T029 → T038), `PostCardQueryRepository`(008 T014 → T011)

### Parallel Opportunities

- Phase 2: 테스트 T004·T005 병렬, 구현 T006·T007·T008·T010·T012 병렬
- US1: 테스트 T013~T016 병렬, T019·T021 병렬
- US2와 US3 서버 작업은 서로 다른 파일(discovery ↔ interaction)이라 팀원별 병렬
- US3: 테스트 T030~T033 병렬
- Polish: T043~T045·T047·T048 병렬

---

## Parallel Example: User Story 1

```bash
# User Story 1 테스트를 함께 작성:
Task: "FollowApiIT in backend/src/test/java/com/team/blog/interaction/integration/FollowApiIT.java"
Task: "FollowConcurrencyIT"
Task: "BlogHeaderFollowIT in backend/src/test/java/com/team/blog/discovery/integration/"
Task: "FollowButton.test.tsx / useFollowToggle.test.ts"

# 구현:
Task: "FollowQueryService.isFollowing·headerStats + AuthorFollowStatusQueryAdapter"
Task: "useFollowToggle + followMessages"
```

## Parallel Example: Foundational

```bash
Task: "FollowRepositoryIT", "PostCardFollowFilterIT"
Task: "FollowReasonCode", "MemberFollowed/MemberUnfollowed", "FollowListCursor", "ListScope 정적 메서드", "api/follows.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2)

1. Phase 1 확인(T001)·설정(T002)·팀 확인 질문(T003)
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 동시 20번·자기 팔로우 0
4. Phase 4 US2 → 피드. 여기까지가 **권장 MVP**(팔로우의 직접적인 가치)
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 저장소·카드 조건
2. US1 → 팔로우 버튼 → 데모
3. US2 → 피드 → 데모
4. US3 → 수·목록(발견 경로)
5. US4 → 탈퇴 정리 단계
6. Polish → 권한 매트릭스·종단 확인·인계

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 서버 → US3 서버(`FollowService`·`FollowQueryService` 소유)
   - Developer B: US1 화면 → US3 화면(`FollowButton`·`FollowListPage`)
   - Developer C: US2 서버·화면(피드) → US4
3. 005 소유 파일(`BlogHeaderView`·`BlogQueryService`·`PageShellController`·`BlogPage`·`PostDetailPage`)과 008 `CardFilter`는 담당에게 알리고 그 기능의 회귀 테스트를 함께 돌린다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 001·005·008 소유 파일을 고치는 작업(T010·T011·T020·T023·T024·T029·T036·T037·T047)은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- 목록 SQL과 피드 카드 SQL 밖에서 `member`·`image`·`follow`를 다른 모듈이 읽지 않는다(코드 리뷰 점검 항목)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
