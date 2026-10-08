---

description: "Task list for 009-like-view (좋아요와 조회수)"
---

# Tasks: 좋아요와 조회수

**Input**: Design documents from `/specs/009-like-view/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, view-pipeline.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 interaction 모듈의 좋아요·조회수를 소유한다. 005가 남긴 `ReactionBar.likeButton` 자리, `PostLikeStatusQuery` 기본 구현(항상 false), `useViewBeacon`이 부르는 `/api/posts/{id}/views`(지금 404)를 채운다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `@LoginRequired`, `AccountStatusGuard`(`CONTENT_WRITE`), `RateLimiter`, `ClientIp`, CSRF(`X-XSRF-TOKEN`), 로그인 `returnTo`, 인증 메일 재발송, `PrivacyPage`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`
- 선행: specs/004 — `PostReadService.requireReadable`, 권한 하네스(`support/permission/`, `PostSnapshot`)
- 선행: specs/005 — `ReactionBar`(`likeButton` 자리·조회수 형식), `useViewBeacon`·`recordPostView`, `PostLikeStatusQuery`·`PostReadingPorts`, 상세 응답 `viewer.likedByMe`
- 선행(둘 중 먼저 하는 쪽이 만듦): specs/007 T009 `PostCounterService` — 007이 아직이면 이 기능의 T008이 클래스를 만들고 007이 댓글 메서드를 더한다
- 선행: V2 `shedlock`(배치 3개)

**006 머지 후**

- 없음. 글 완전 삭제 때 `post_like`·`post_view_daily`는 FK CASCADE, 모아 둔 조회는 반영 배치가 건너뛴다(006 코드와 겹치는 파일 없음)

**후속 (다른 스펙이 이 기능을 사용)**

- 011-notification: `PostLiked` 구독(같은 사람·같은 글 1번)
- 012-trending-search: `post.like_count`·`view_count`, `post_view_daily`(점수식 B), `PostLiked`/`PostUnliked`
- 015-withdraw: `LikeWithdrawalPurgeStep`(`WithdrawalPurgeStep` order 30)은 015 tasks가 만들고 이 기능의 `LikePurgeService.purgeByMember`(T043)를 부른다
- 006-manage-delete: 내 글 관리 목록의 조회수 표시(41 M-8)는 `post.view_count`를 그대로 읽는다

**팀 결정 대기 (기본안으로 진행)**

- `RedisGuard` OOM 503 문구(research R13): 조회 기록은 204로 바꿔 숨기고, 좋아요는 화면이 code와 상관없이 되돌림. 공용 수정은 002 소유
- 처리방침 첫 판 시점(Clarifications Q3): 첫 공개 전이면 문단 추가만, 이미 공개했으면 001 재동의 규칙 결정 필요 — 확인 작업 T002

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 처리방침 시점 확인, 설정값·정책 파일

- [X] T001 선행 확인: V1 `post_like`(PK·`ix_post_like_member`)·`post_view_daily`(PK·`ck_post_view_daily_views`·`ix_post_view_daily_date`)·`post.like_count`/`view_count`(`ck_post_counts`)·V2 `shedlock`, `B/post/application/PostReadService.java`, `B/post/application/port/PostLikeStatusQuery.java`, `B/shared/web/ClientIp.java`, `F/components/ReactionBar.tsx`(`likeButton`), `F/features/post-detail/useViewBeacon.ts`가 있는지, `PostCounterService`가 이미 있는지(007) 기록한다 (구현 메모: main 3961a76 기준 모두 있음 — V1 post_like·post_view_daily·ck_post_counts, V2 shedlock, PostReadService·PostLikeStatusQuery·ClientIp, ReactionBar likeButton, useViewBeacon. PostCounterService는 main에 없고 007-comment 브랜치(미병합)에만 있어 T008이 007 파일과 같은 머리말로 만들고 좋아요·조회 메서드를 끝에 더했다)
- [X] T002 **확인 작업(Clarifications Q3)**: 서비스가 이미 일반 공개됐는지 팀에 확인하고 결과를 research R15 아래에 적는다. 공개 전이면 T055(처리방침 문단)만, 공개 후면 001 재동의 규칙(처리방침 버전 올림) 결정을 팀 결정 항목으로 올린다 (구현 메모: 팀에 묻지 않고 "첫 공개 전"으로 가정했다(Clarifications Q3 확정안과 같음). research R15 아래에 기록, T046은 처리방침 문단 추가만 한다)
- [X] T003 [P] 설정값 `B/interaction/application/LikeProperties.java`(`blog.like`)·`ViewProperties.java`(`blog.view`, `@Validated`: `maxPerWindow ≥ 1`, `dedupeWindow ≥ 1m`)와 `R/application.yml` 기본값(research R14), 봇 단어 목록 `R/policy/view-bot-user-agents.txt`를 추가한다 (구현 메모: dedupe-window 1분 이상·daily-retention 1일 이상은 @AssertTrue로 검사. 시험 프로필은 blog.view.flush-interval 24h(배치가 스스로 돌지 않게, initialDelay도 같은 값))

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 카운터 공개 API, 이유 코드, 이벤트, 저장소, 화면 API·수 형식

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T004 [P] 카운터 API 통합 테스트 `T/post/integration/PostCounterServiceIT.java`에 추가(007 T006과 같은 파일, 없으면 새로): `adjustLikeCount` ±1·MANDATORY, `likeCount`, `addViews`가 `view_count`만 늘리고 `updated_at` 그대로·없는 글이면 false, `adjustLikeCounts`, `reconcileLikeCounts`(어긋난 글만 고치고 번호 반환) (구현 메모: 007 브랜치의 PostCounterServiceIT와 같은 파일을 새로 만들면 합칠 때 add/add 충돌이 나므로 T/post/integration/PostCounterLikeViewIT.java로 나눴다)
- [X] T005 [P] 좋아요 저장소 통합 테스트 `T/interaction/integration/LikeRepositoryIT.java`: `insertIfAbsent` 1/0, `deleteIfPresent` 1/0, `exists`, `deleteAllByMember`가 글 번호 목록 반환
- [X] T006 [P] 화면 수 형식 테스트 `F/components/__tests__/formatCount.test.ts`: 0, 999, 1,234, 9,999, 10,000 → `1만`, 12,999 → `1.2만`(내림), 1,000,000 → `100만`

### Implementation for Foundational

- [X] T007 [P] 이유 코드 `B/interaction/domain/LikeReasonCode.java`(`CANNOT_LIKE_OWN_POST` 400 "내 글에는 좋아요를 누를 수 없어요")와 이벤트 `B/shared/event/PostLiked.java`·`PostUnliked.java`(20 §3-3 필드)를 만든다
- [X] T008 `B/post/application/PostCounterService.java`에 좋아요·조회 메서드(contracts/view-pipeline.md §7)를 더한다 — 클래스가 없으면(007 전) 이 작업이 만든다(T004 통과) (구현 메모: 007-comment의 PostCounterService(미병합)와 같은 내용 + 끝에 '009 좋아요·조회수' 구역으로 adjustLikeCount·likeCount·addViews·adjustLikeCounts·reconcileLikeCounts를 더했다. 합칠 때 add/add 충돌은 끝 구역만 남기면 된다. addViews는 n≤0이면 존재 여부만 본다)
- [X] T009 [P] 좋아요 저장소 `B/interaction/infra/LikeRepository.java`(`JdbcClient`)를 만든다(T005 통과)
- [X] T010 [P] 일별 저장소 `B/interaction/infra/ViewDailyRepository.java`(`upsert(postId, date, n)`, `deleteOlderThan(date)`)를 만든다
- [X] T011 [P] 화면: `F/components/formatCount.ts`를 만들고 `F/components/ReactionBar.tsx`의 조회수 식과 좋아요 수 표시가 이것을 쓰게 바꾼다(T006 통과, 005 소유 파일 — 005 회귀 `PostDetailPage.test.tsx`의 `조회 1.2만` 함께 실행). `F/api/likes.ts`(`putLike(postId)`·`deleteLike(postId)`, 001 `client.ts`)와 문구 `F/features/like/likeMessages.ts`를 만든다 (구현 메모: LikeButton이 likeButton 자리를 채우면 ReactionBar 앞의 '♥ N' 대신 버튼이 수를 보인다(버튼이 없을 때만 span). likes.ts는 404여도 공통 404 화면으로 바꾸지 않는다(notFoundScreen:false))

**Checkpoint**: 카운터 API와 저장소가 통합 테스트를 통과한다

---

## Phase 3: User Story 1 - 남의 글에 좋아요를 누르고 취소한다 (Priority: P1) 🎯 MVP

**Goal**: 좋아요·취소가 즉시 화면에 반영되고, 동시 요청에도 1인 1글 1건·수 = 실제 건수

**Independent Test**: 회원 A로 B의 공개 글에 좋아요·취소를 한 번씩, 동시에 20번씩 보내 화면·응답·실제 건수가 같은지 확인한다

### Tests for User Story 1 ⚠️

- [X] T012 [P] [US1] 통합 테스트 `T/interaction/integration/LikeApiIT.java`: `좋아요하면_200_liked_true와_수_+1`(US1 #1), `좋아요_취소_다시_요청은_변화_없음`(US1 #2, 이벤트 없음), `취소하면_수_−1`, `응답_수는_다른_사람_변화까지_반영`, `글을_수정_발행해도_좋아요_그대로`(FR-020), `상세의_likedByMe가_눌린_상태`(US1 #7, `PostLikeStatusQuery` 교체 확인) (구현 메모: 숫자가_아닌_글_번호는_404도 더했다)
- [X] T013 [P] [US1] 동시성 통합 테스트 `T/interaction/integration/LikeConcurrencyIT.java`: `같은_회원_동시_20번은_1건_이벤트_1번`(SC-001, 이벤트 캡처 리스너), `50명_동시는_50`, `좋아요_취소_섞기_3회_모두_수가_건수와_같다`(SC-002, 같은 회원 좋아요 20 + 취소 20 섞기 × 3회, 매번 `like_count = count(*)`) (구현 메모: 섞기 3회는 회차마다 ratelimit:like:{회원} 키를 비운다(회차당 40번 × 3 = 120번이 1분 60번 제한에 걸리므로). 이벤트는 테스트 소스 @Profile("test") @Component LikeEventProbe(AFTER_COMMIT)로 센다)
- [X] T014 [P] [US1] 화면 테스트 `F/features/like/__tests__/useLikeToggle.test.ts`(가짜 타이머): 누르면 즉시 상태·수 변화(SC-005), 0.3초 안 연타는 마지막 상태만 1번 전송(US1 #5), 홀수·짝수 연타(짝수면 요청 없음), 응답 수로 맞춤, 실패면 되돌림 + 안내(US1 #6). `F/components/__tests__/LikeButton.test.tsx`: ♡/♥ 모양, `aria-pressed`, `aria-label` "좋아요 (N)"/"좋아요 취소 (N)", Enter·Space(FR-017)

### Implementation for User Story 1

- [X] T015 [US1] `B/interaction/application/LikeService.java`의 `like`/`unlike`(research R1·R3: 계정 상태 → 글 → 자기 글 → 요청 제한(트랜잭션 밖) → 트랜잭션 { insert/delete → 카운터 → 수 읽기 → 이벤트 })를 구현한다(T012·T013 통과) (구현 메모: 트랜잭션은 TransactionTemplate으로 요청 제한 뒤에만 연다. 판정 뒤 글이 완전 삭제돼 FK 위반이면 404로 바꾼다)
- [X] T016 [US1] `B/interaction/web/LikeController.java`의 `@LoginRequired PUT·DELETE /api/posts/{postId}/like`(숫자 아니면 404)를 구현한다 (구현 메모: 응답 Cache-Control: private, no-store(004 공개 범위 API와 같음))
- [X] T017 [US1] `B/interaction/application/LikeStatusQueryAdapter.java`(`@Component implements PostLikeStatusQuery`, `LikeRepository.exists`)를 등록해 005 기본 구현을 물리고 `B/post/application/port/PostLikeStatusQuery.java` 주석의 "009가 넘겨받는다"를 고친다. 005 `PostDetailApiIT`의 `likedByMe` 단언을 함께 돌린다 (구현 메모: 005 PostReadingPorts의 좋아요 기본 Bean을 지웠다(008 태그와 같은 이유 — @ConditionalOnMissingBean 판정이 스캔 순서에 흔들림). 005 PostDetailIntegrationTest·PostDetailFallbackIntegrationTest·PostDetailAuthorViewIntegrationTest 통과)
- [X] T018 [US1] 화면 `F/features/like/useLikeToggle.ts`와 `F/components/LikeButton.tsx`를 만들고, `F/pages/PostDetailPage.tsx`가 `ReactionBar`의 `likeButton`에 `LikeButton`(처음 상태 `viewer.likedByMe`·`likeCount`)을 넘기게 한다(T014 통과, 005 소유 파일) (구현 메모: LikeButton 루트는 like-area(버튼 + role=status 안내). 005 PostDetailPage.test의 '맨 앞은 like-count' 단언을 'like-area 안의 like-count'로 고쳤다)

**Checkpoint**: 좋아요가 독립적으로 동작한다

---

## Phase 4: User Story 2 - 좋아요를 누를 수 없는 사람·글을 막고 안내한다 (Priority: P1)

**Goal**: 비회원·인증 전·탈퇴 유예·자기 글·볼 수 없는 글을 서버가 막고 화면이 안내한다

**Independent Test**: 행위자 × 글 상태로 좋아요를 요청해 응답과 수 불변을 확인한다

### Tests for User Story 2 ⚠️

- [X] T019 [P] [US2] 권한 매트릭스 `TR/permission/like-view.csv`(research R12의 `post.like`·`post.unlike`·`post.view` 행 전부, owner `009`)와 `T/interaction/integration/LikeViewPermissionMatrixIT.java`, 실행기 `T/interaction/integration/permission/LikeAction.java`·`UnlikeAction.java`(거부 때 `PostSnapshot`에 `like_count`·`post_like` 행 수 포함). `post.view` 실행기는 US3에서 더한다 (구현 메모: 좋아요 수·행 수 비교는 004 PostSnapshot을 고치지 않고 실행기(LikePermissionSupport)가 다른 회원의 좋아요 1건을 미리 넣고 거부 때 전후를 비교한다. 129행(post.view 43행은 T029까지 대기))
- [X] T020 [P] [US2] `LikeApiIT`에 추가: `볼_수_없는_자기_글은_400이_아니라_404`(판정 순서), `좋아요_취소_합쳐_61번째는_429와_Retry_After`(US2 #5), `앞_단계에서_걸린_요청은_세지_않는다`, `관리자도_일반_회원과_같다`, `정지_회원_남은_세션은_403`, `좋아요한_글이_비공개가_되어도_보존되고_다시_공개하면_그대로`(FR-019) (구현 메모: 인증_전_회원은_403_EMAIL_NOT_VERIFIED도 더했다)
- [X] T021 [P] [US2] `LikeButton.test.tsx`에 추가: 비회원이 누르면 "로그인하고 좋아요를 눌러 보세요 [로그인]"(링크 `/login?returnTo={지금 주소}`), 인증 전이면 "이메일 인증 후 누를 수 있어요", 작성자는 버튼 없이 ♥ + 수, 401·403 응답도 같은 안내로(US2 #1~#3)

### Implementation for User Story 2

- [X] T022 [US2] T019·T020에서 드러난 판정 차이를 `LikeService`에서 고친다(예: `PUBLISHED` 확인 위치, 관리자 숨김 글). 모든 `like-view.csv` 좋아요 행 통과(SC-004) (구현 메모: T019·T020에서 판정 차이는 나오지 않았다(작성자 본인의 숨김·비공개 발행 글은 볼 수 있어 400, 임시·휴지통은 404). 006 TrashedPostPermissionMatrixIT의 좋아요는_404 자리를 실제 단언(로그인 404, 비회원 401)으로 채웠다)
- [X] T023 [US2] `LikeButton`의 비회원·인증 전·작성자 표시와 로그인 이동(돌아와서 자동으로 누르지 않음, FR-014)을 구현한다(T021 통과) (구현 메모: 로그인 링크는 004 loginPathFor(지금 경로+쿼리)를 그대로 쓴다)

**Checkpoint**: C-LIKE-1 기준이 모두 통과한다

---

## Phase 5: User Story 3 - 새로고침으로 부풀지 않는 조회수 (Priority: P2)

**Goal**: 방문자별 중복 판정으로 조회를 모으고 1분 안에 반영하며, 장애·중단에도 상세를 막지 않고 조회를 잃지 않는다

**Independent Test**: 비회원 3명(1명은 5번 새로고침)과 회원 1명이 본 뒤 1분 반영 후 조회수 3과 1, 작성자·봇은 늘지 않음을 확인한다

### Tests for User Story 3 ⚠️

- [X] T024 [P] [US3] 단위 테스트 `T/interaction/unit/VisitorKeyResolverTest.java`(m:/v:/h:, `vid` 형식 틀리면 h:, 같은 날 같은 IP·UA → 같은 h:, 비밀값 바뀌면 다른 h:, 키에 IP 문자열 없음)와 `T/interaction/unit/ViewExclusionTest.java`(봇 단어 대소문자 무시, `Sec-Purpose: prefetch`·`Purpose: prefetch`, 작성자·관리자)
- [X] T025 [P] [US3] 통합 테스트 `T/interaction/integration/ViewRecordIT.java`: `24시간_1회`(US3 #1, v1 5번·v2·v3 → 반영 뒤 3), `동시_50번은_1번`(US3 #2·SC-006), `30분_5회_설정`(US3 #3, `@TestPropertySource`, 기간 지나면 다시 셈 — 짧은 기간으로), `작성자는_세지_않는다`(US3 #4), `관리자는_세지_않는다`(FR-031), `봇과_prefetch는_세지_않는다`, `센_것과_안_센_것의_응답이_같다`(US3 #6 — 상태·헤더·본문), `볼_수_없는_글은_404`, `같은_방문자_61번째는_429`, `상세를_먼저_열면_첫_조회와_새로고침이_같은_방문자`(research R5 — 상세 응답의 `vid`를 다음 요청에 실음) (구현 메모: 30분_5회는 시험 전용 컨텍스트(@TestPropertySource)를 늘리지 않으려고 RedisViewStore.record에 1초 기간·5회를 직접 넘겨 확인)
- [X] T026 [P] [US3] 반영 통합 테스트 `T/interaction/integration/ViewFlushJobIT.java`: `1분_반영으로_누적과_일별이_함께`(US4 #1), `자정_넘긴_조회는_그_날짜에`(US4 #2, `Clock` 고정), `중간에_멈춰도_다음_실행이_남은_것만_반영한다`(US3 #8·SC-009 — `view:processing:*`를 미리 만들고 일부 글은 이미 반영된 상태로), `완전_삭제된_글은_건너뛴다`, `updated_at은_바뀌지_않는다`(FR-030), `실행_후_처리_대기_묶음_0개` (구현 메모: Clock 고정 대신 날짜가 다른 모음 키(view:pending:{d})로 자정 경계 확인 — 반영 날짜는 키에 실린 기록 날짜)
- [X] T027 [P] [US3] 장애 통합 테스트 `T/interaction/integration/ViewRedisOutageIT.java`: `RedisOutage` 동안 상세 API 200(비회원 기준)·페이지 셸 200·조회 기록 204, 복구 뒤 정상 기록(US3 #7, SC-008) (구현 메모: docker pause는 연결을 끊지 않아 시간 초과 명령이 복구 순간 늦게 실행될 수 있어, 복구 직후 반영으로 기준을 잡고 새 방문자 +1만 확인)
- [X] T028 [P] [US3] 개인정보 통합 테스트 `T/interaction/integration/ViewPrivacyIT.java`: `X-Forwarded-For`(신뢰 프록시 설정)로 IP `203.0.113.77`·`vid` 고정 값으로 기록 → Redis 전체 키·값 덤프, `post_view_daily`, 캡처한 로그(Logback `ListAppender`)에서 두 문자열 0건(SC-010) (구현 메모: MockMvc는 RemoteIpValve를 거치지 않아 remoteAddr를 직접 지정. 로그는 OutputCaptureExtension으로 캡처)
- [X] T029 [P] [US3] 권한 실행기 `T/interaction/integration/permission/ViewAction.java`(`post.view`, `isWrite=false`) — `like-view.csv` `post.view` 행

### Implementation for User Story 3

- [X] T030 [P] [US3] `B/interaction/application/VisitorKeyResolver.java`(하루 비밀값 `view:salt:{d}` `SET NX`+TTL, 날짜별 메모리 캐시, `ClientIp.of`)를 구현한다(T024 통과) (구현 메모: vid도 Redis 키에 원문 대신 sha256(base64url) 해시로 넣음(data-model v:{vid}와 다름, SC-010에서 vid 원문 0건))
- [X] T031 [P] [US3] Lua `R/redis/view-record.lua`와 `B/interaction/infra/RedisViewStore.java`(`record(postId, key, date)`, `renamePending()`, `scanProcessing()`, `hscan(key)`, `hdel(key, postId)` — 모두 `RedisGuard`, 트랜잭션 밖)를 만든다 (구현 메모: 메서드 이름: record·salt·scan(pattern)·claim(pendingKey)·entries(key)·remove(key, postId). 반영 쪽 장애는 ViewStoreUnavailableException)
- [X] T032 [US3] `B/interaction/application/ViewRecordService.java`(contracts/view-pipeline.md §1 순서, OOM 예외도 204로, 결과 코드 DEBUG 로그만)와 `B/interaction/web/ViewController.java`(`POST /api/posts/{postId}/views` → 204, 비회원 허용, CSRF는 001 기본)를 구현한다(T025·T027 통과)
- [X] T033 [US3] `B/interaction/web/VisitorIdCookieFilter.java`(contracts §2: 대상 경로·비회원·`vid` 없음 → `Set-Cookie`, 응답 상태 무관)를 등록한다(T025 `vid` 테스트 통과). 005 `PageShellControllerIT`·`PostDetailApiIT`를 함께 돌린다 (구현 메모: 005 PageShellControllerIT·PostDetailApiIT는 main에 없어 PostDetailPermissionMatrixIT·TagPageShellIT·NotFoundIndistinguishableIT·SecurityHeadersIT로 대신 확인)
- [X] T034 [US3] `B/interaction/application/ViewFlushJob.java`(contracts §3, `@Scheduled(fixedDelayString)` + `@SchedulerLock("view-flush")`, 글마다 `TransactionTemplate`)를 구현한다(T026 통과)
- [X] T035 [US3] T028·T029 통과 확인. `F/api/posts.ts`의 `recordPostView` 구현 메모("009 소유 — 아직 없으면 404")를 지운다(동작은 그대로)

**Checkpoint**: 조회수가 1분 안에 반영되고 장애·중단에 안전하다

---

## Phase 6: User Story 4 - 일별 조회수를 90일 보관한다 (Priority: P3)

**Goal**: 일별 합계를 90일 보관하고 넘으면 지운다

**Independent Test**: 91일 전·89일 전 행에 정리를 돌려 91일 전만 지워지는지 확인한다

### Tests for User Story 4 ⚠️

- [X] T036 [P] [US4] 통합 테스트 `T/interaction/integration/ViewDailyRetentionJobIT.java`: `91일_전만_지운다`(US4 #3·SC-011, `Clock` 고정, 경계 90일 남김), `누적_조회수는_그대로`, `글을_완전_삭제하면_일별도_사라진다`(US4 #4, CASCADE) (구현 메모: Clock Bean을 바꾸는 새 컨텍스트 대신 ViewDailyRetentionJob.purge(LocalDate)로 날짜를 넘겨 확인, 예약 경로 run()도 따로 확인)
- [X] T037 [P] [US4] 보정 통합 테스트 `T/interaction/integration/LikeReconcileJobIT.java`: 정상이면 0건·WARN 없음(SC-003), `like_count`를 SQL로 틀어 두면 고치고 WARN(글 번호 포함), ShedLock으로 두 인스턴스 동시 실행 시 한 번만(FR-005) (구현 메모: ShedLock은 ImageCleanupLockIT처럼 LockProvider로 잠금을 쥐고 run()이 건너뛰는지 확인)

### Implementation for User Story 4

- [X] T038 [P] [US4] `B/interaction/application/ViewDailyRetentionJob.java`(`@Scheduled(cron = "${blog.view.retention-cron}", zone = "${blog.time-zone}")` + ShedLock)를 구현한다(T036 통과)
- [X] T039 [P] [US4] `B/interaction/application/LikeReconcileJob.java`(`PostCounterService.reconcileLikeCounts` 호출, 0 아니면 WARN)를 구현한다(T037 통과) (구현 메모: 보정 쿼리 UPDATE…RETURNING을 PostCounterService.reconcileLikeCounts에 두고 배치는 호출·WARN만(글 번호 최대 20개))

**Checkpoint**: 모든 스토리가 독립적으로 동작한다

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 탈퇴 정리 Service, 종단 확인, 처리방침, 인계

- [ ] T040 [P] 탈퇴 정리 통합 테스트 `T/interaction/integration/LikePurgeServiceIT.java`: 그 회원 좋아요 전부 삭제, 글별 `like_count` 감소, 이벤트 없음, 다른 회원 좋아요 그대로, MANDATORY
- [ ] T041 [P] 종단 확인 `E/like-view.spec.ts`(Playwright): quickstart §3의 1~9번(두 회원·비로그인 컨텍스트, 네트워크 끊기, 탭 숨김은 `page.evaluate`로 `visibilityState` 흉내)
- [ ] T042 [P] 375px·접근성 확인: 좋아요 버튼 크기 44px 이상, 포커스 표시, 안내 문구 `role="status"`
- [ ] T043 `B/interaction/application/LikePurgeService.java`(contracts §6, `MANDATORY`)를 구현하고(T040 통과) 015가 부를 서명을 클래스 주석에 적는다
- [ ] T044 Redis OOM 경로 확인(research R13): OOM을 흉내 내 좋아요가 503을 받는지, 조회 기록은 204인지 기록하고 화면이 되돌림 + 안내인지 확인한다. 결과를 ANALYSIS-tier-bc 팀 결정 항목에 보탠다
- [ ] T045 [P] 015 인계: `specs/015-withdraw/tasks.md`의 `LikeWithdrawalPurgeStep`이 `LikePurgeService.purgeByMember`와 order 30을 쓰는지 확인한다
- [ ] T046 [P] 처리방침 문단(T002 결과가 "공개 전"일 때): `F/pages/PrivacyPage.tsx`에 research R15 문단을 더한다(001 소유 화면 — 001 담당에게 알림, 버전 값은 바꾸지 않음)
- [ ] T047 `grep -rn "009 좋아요\|009에서 교체\|009가 넘겨받\|TODO(009)" backend/src frontend/src`가 0건인지 확인하고 남은 표시를 정리한다(005 T033·T039·T040 구현 메모)
- [ ] T048 quickstart.md §1~§5를 처음부터 끝까지 실행하고 결과를 기록한다
- [ ] T049 전체 회귀: `./mvnw -pl backend verify`(004·005 테스트 포함)와 `npm test`·`npm run build`·`npm run lint`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001·004·005의 해당 작업이 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: T002는 답을 기다리는 동안 다른 작업을 막지 않는다
- **Foundational (Phase 2)**: Setup 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
- **Polish (Phase 7)**: 원하는 스토리 완료 후. T046은 T002 결과에 따름

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. 다른 스토리에 의존하지 않는다
- **US2 (P1)**: US1의 `LikeService`·`LikeButton`(T015·T018) 후 — 같은 파일
- **US3 (P2)**: Foundational 이후 독립(좋아요와 다른 파일)
- **US4 (P3)**: US3의 반영(T034) 후 — 일별 행이 있어야 정리 확인. 보정(T039)은 Foundational 이후 독립

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 저장소 → Service → Controller·필터 → 배치 → 화면 API → 훅 → 컴포넌트
- 같은 파일을 고치는 작업은 순서대로 한다: `LikeService`(T015 → T022), `LikeApiIT`(T012 → T020), `LikeButton.tsx`(T018 → T023), `LikeButton.test.tsx`(T014 → T021), `PostCounterService`(007 T009 → T008)

### Parallel Opportunities

- Phase 2: 테스트 T004~T006 병렬, 구현 T007·T009·T010·T011 병렬
- US1: 테스트 T012~T014 병렬
- US3: 테스트 T024~T029 병렬, 구현 T030·T031 병렬
- US3과 US1·US2는 서로 다른 파일이라 팀원별 병렬
- US4: T036~T039 병렬
- Polish: T040~T042·T045 병렬

---

## Parallel Example: User Story 3

```bash
# User Story 3 테스트를 함께 작성:
Task: "VisitorKeyResolverTest / ViewExclusionTest in backend/src/test/java/com/team/blog/interaction/unit/"
Task: "ViewRecordIT in backend/src/test/java/com/team/blog/interaction/integration/ViewRecordIT.java"
Task: "ViewFlushJobIT"
Task: "ViewRedisOutageIT"
Task: "ViewPrivacyIT"

# 구현:
Task: "VisitorKeyResolver"
Task: "view-record.lua + RedisViewStore"
```

## Parallel Example: Foundational

```bash
Task: "PostCounterServiceIT 추가", "LikeRepositoryIT", "formatCount.test.ts"
Task: "LikeReasonCode + PostLiked/PostUnliked", "LikeRepository", "ViewDailyRepository", "formatCount.ts + api/likes.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2)

1. Phase 1 확인(T001)·처리방침 시점 질문(T002)·설정(T003)
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 동시 20번·50명·섞기
4. Phase 4 US2 → 판정 순서·안내. 여기까지가 **권장 MVP**(C-LIKE-1 전부)
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 카운터·저장소
2. US1 → 좋아요 → 데모
3. US2 → 거부·안내
4. US3 → 조회수(C-VIEW-1)
5. US4 → 일별 보관·보정
6. Polish → 탈퇴 정리 Service·종단 확인·처리방침

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 서버 → US2 서버 → 보정 배치(`LikeService` 소유)
   - Developer B: US1 화면 → US2 화면
   - Developer C: US3 → US4 보관 정리(조회 파이프라인 소유)
3. `PostCounterService`는 007 담당과 한 파일을 나눠 쓴다 — 메서드 추가만 하고 기존 메서드는 바꾸지 않는다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 001·005 소유 파일을 고치는 작업(T011·T017·T018·T033·T046)은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- 조회수 로그에 IP·UA·`vid`·방문자 키를 넣지 않는다(코드 리뷰 점검 항목)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
