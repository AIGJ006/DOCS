---

description: "Task list for 012-trending-search (트렌딩·검색)"
---

# Tasks: 트렌딩·검색

**Input**: Design documents from `/specs/012-trending-search/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, trending-search-sql.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 discovery 모듈의 트렌딩·검색·sitemap을 소유한다. 새 테이블·이벤트 구독은 없고, 005 카드·홈·블로그 화면과 004 공용 조건을 그대로 쓴다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `RateLimiter`, `CursorCodec`·`ListScope`, `MemberQueryService.findReadableBlogOwner`·`normalizeHandle`, `SensitiveParamMasking`, ShedLock(`SchedulingConfig`), `SessionBar`, `support/IntegrationTestBase`·`MemberFixtures`·`RedisOutage`·`SqlCounter`·`MutableClock`
- 선행: specs/004 — `VisibilityFilter.forViewer`, 권한 하네스(`support/permission/`, `PostFixtures.State`)
- 선행: specs/005 — `PostCardQueryRepository`·`PostCardAssembler`·`PostCardView`·`CursorPage`, `PageShellController`·`LinkPreviewMeta`(`noindex`)·`blog.site.base-url`, `HomePage`·`BlogPage`·`PostCardGrid`·`LoadMoreButton`·`useCursorList`·`listRestore`
- 선행: specs/007(댓글 행 — 트렌딩 작성자 수), specs/008(태그 행·`normalizeTag.ts`·`/tags/{이름}` 화면), specs/009(`like_count`·`view_count` 누적, `VisitorKeyResolver`)

**006 머지 후**

- `F/App.tsx`에 `/search` 경로를 더하는 작업(T029). 006이 같은 파일에 `/manage/posts` 경로를 더한다. 그 밖에 겹치는 파일은 없다

**후속 (다른 스펙이 이 기능을 사용)**

- 014-report-hide·015-withdraw: 숨김·탈퇴 유예는 공용 조건으로 자동 제외 — 할 일 없음(이벤트를 구독하지 않음)
- 015 data-model 이벤트 표의 "012" 구독자 표시는 Tier B/C analyze에서 고친다(plan 설계 후 확인 3)

**팀 결정 대기 (기본안으로 진행)**

- 새 이유 코드 `SNAPSHOT_EXPIRED`(410)·`SEARCH_QUERY_TOO_SHORT`(400), 주변 문장 응답을 `snippetHtml` 대신 `snippet {text, marks}`로 바꾼 것, 사람 검색 정렬(정확히 일치 → 닉네임 → 번호) — 확인 작업 T003

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 설정값, 팀 확인 질문

- [X] T001 선행 확인: V1 `pg_trgm` 확장·GIN 인덱스 4개·`ix_post_feed`·`uq_comment_post_id`·`ix_post_tag_tag`, `B/discovery/infra/PostCardQueryRepository.java`, `B/discovery/web/PageShellController.java`, `B/shared/web/SensitiveParamMasking.java`, 009 `VisitorKeyResolver`·008 `F/features/tag/normalizeTag.ts`·`tagPath.ts`가 있는지, Testcontainers PostgreSQL 이미지에서 `pg_trgm`이 만들어지는지 기록한다 (구현 메모: 모두 있음 — V1 pg_trgm·GIN 4개·ix_post_feed·uq_comment_post_id·ix_post_tag_tag, PostCardQueryRepository·PageShellController·SensitiveParamMasking·009 VisitorKeyResolver·008 normalizeTag.ts·tagPath.ts. 시험 PostgreSQL 이미지는 postgres:18-alpine이고 V1의 CREATE EXTENSION pg_trgm이 통과한다. MutableClock은 저장소에 없어 시각은 메서드 인자(now)로 넘겨 확인한다. 001 SessionBar는 공통 머리말 SiteHeader(site-header-actions)로 바뀌어 검색창은 거기에 넣는다)
- [X] T002 [P] 설정값 `B/discovery/application/trending/TrendingProperties.java`(`@ConfigurationProperties("blog.trending")` + `@Validated`), `B/discovery/application/search/SearchProperties.java`(`blog.search`), `R/application.yml`에 research R17 기본값, 테스트 `T/discovery/unit/TrendingSearchPropertiesBindingTest.java` (구현 메모: 시험 프로필은 blog.trending.refresh-cron '-'(예약 끔)·refresh-on-startup false — 새 키 refresh-on-startup(기본 true)를 더했다)
- [X] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: ① 새 코드 `SNAPSHOT_EXPIRED`(410 "순위가 새로 바뀌었어요")·`SEARCH_QUERY_TOO_SHORT`(400 "두 글자 이상 입력해 주세요") ② 주변 문장 응답 `snippet {text, marks}`(R9) ③ 사람 검색의 정확히 일치 다음 정렬(R10). 답이 오기 전에는 기본안으로 진행한다 (구현 메모: ANALYSIS-tier-bc §6 팀 결정 4(R7)에 012 T003 세 항목이 이미 올라가 있어 문서는 고치지 않았다. 기본안으로 진행)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 이유 코드, 카드 번호 조회, 검색어 처리, 테스트 도구

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 [P] `B/discovery/application/DiscoveryReasonCode.java`(`SNAPSHOT_EXPIRED` 410, `SEARCH_QUERY_TOO_SHORT` 400 — 001 `ReasonCode` 구현, 메시지 끝 마침표 없음)와 `GlobalExceptionHandler`가 410을 공통 본문으로 쓰는지 확인하는 테스트 `T/discovery/unit/DiscoveryReasonCodeTest.java` (구현 메모: 새 컨텍스트 없이 GlobalExceptionHandler.handleApi를 직접 불러 본문 확인. 예외 SnapshotExpiredException·SearchQueryTooShortException)
- [X] T005 [P] 005 `B/discovery/infra/PostCardQueryRepository.java`에 `findCardsByIds(List<Long> ids)`(같은 SELECT + `VisibilityFilter(비회원)` + `AND p.id = ANY(:ids)`, 순서 보장 없음)를 더하고 `T/discovery/infra/PostCardQueryRepositoryByIdsIT.java`(볼 수 없는 글 빠짐, 빈 목록, SQL 1번) (005 소유 파일 — 005 담당에게 알림) (구현 메모: 005 담당에게 알림 대상 — 보고에 적음. 매개변수는 Long[] 배열로 ANY(:ids))
- [X] T006 [P] 테스트 먼저 `T/discovery/unit/SearchQueryParserTest.java`(contracts §4 표, NFC 조합형, 유니코드 공백, 50 코드 포인트, 5단어, 서로게이트) — 실패 확인 (구현 메모: 사람 검색어 parsePeople도 같은 시험에 넣음. 구현과 같은 묶음으로 작성해 실패 단계는 컴파일 실패로 확인)
- [X] T007 `B/discovery/application/search/SearchQueryParser.java`·`SearchQuery.java`·`SearchWord.java`(research R6, `fingerprint()` 포함)와 `PeopleQuery` 만들기(덩어리 규칙 R10) (T006 통과) (구현 메모: PeopleQuery는 SearchQueryParser.parsePeople이 만든다. toString에 원문을 넣지 않는다(FR-039))
- [X] T008 [P] 테스트 도구 `T/discovery/support/SearchFixtures.java`(제목·본문·태그·`first_public_at`·좋아요·조회·댓글 작성자를 지정해 공개 글을 빠르게 넣기, 대량 넣기는 `COPY`), `TrendingRedisHelper.java`(스냅샷 키 직접 쓰기·지우기·TTL 확인)

**Checkpoint**: 공용 부품 준비 완료 — User Story 작업 시작 가능

---

## Phase 3: User Story 1 - 키워드로 공개 글 찾기 (Priority: P1) 🎯 MVP

**Goal**: 검색창에서 공개 글을 제목 → 태그 → 본문 단계로(또는 최신순) 9개씩 찾고, 검색어 주변 문장을 안전하게 강조해 보여 준다.

**Independent Test**: 제목·본문·태그·비공개에 같은 단어가 있는 글을 만들고 검색해 단계 순서, 2글자 규칙, AND, 이어 보기, 강조 안전성, 특수문자를 확인한다.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [P] [US1] `T/discovery/unit/SnippetBuilderTest.java` — contracts §6 표, 앞뒤 40 코드 포인트와 `…`, 제목 대체, `excerpt` 대체, 서로게이트 쌍 경계, 겹친 강조 합치기, 대소문자 무시(`İ`처럼 길이가 바뀌는 글자 포함), 줄바꿈 → 공백 (구현 메모: 2글자 단어는 본문에서 기준 위치를 찾지 않고(FR-019) 제목에서 찾는다. 맞닿은 강조도 합친다)
- [X] T010 [P] [US1] `T/discovery/integration/PostSearchIT.java` — US1 #1~#7·SC-003·SC-004·SC-005: 단계 순서, 2글자 단어 본문 제외 + `notice`, 1글자 무시·AND, 25개 결과를 9개씩 끝까지(단계 경계가 페이지 중간에 오는 경우 포함) 중복·누락 0, 본문 `<script>`가 `snippet.text`에 글자 그대로, 코드 블록 안 글자도 찾음(FR-023), `100%`·`snake_case`·`a\b` 글자 그대로, 결과 없음 `items []`, 최신순, 다른 검색어·정렬 커서 400 `INVALID_CURSOR`, 비공개·친구 공개(적용자)·휴지통·숨김·작성자 유예 글 0, 작성자 본인·관리자가 검색해도 자기 비공개 글 0, 최근창(설정 5로 줄여) 밖의 글도 ② 경로로 찾음, 남는 단어 없음 400 `SEARCH_QUERY_TOO_SHORT` (구현 메모: 최근창 축소는 새 컨텍스트 대신 PostSearchRepository.find에 창 5를 직접 넘겨 확인. 친구 공개는 V1 ck_post_visibility에 없어(PUBLIC·PRIVATE만) 해당 경우가 없다. 작업본(post_draft)의 제목은 찾지 않음도 확인)
- [X] T011 [P] [US1] `T/discovery/integration/PostSearchPerformanceIT.java`(`@Tag("slow")`) — SC-001: 글 10만 개(본문 길이·단어 분포를 현실적으로 — 같은 문장 반복 금지, 33 §8), 검색어 20종 p95 500ms, 글 1만 개 p95 300ms(헌법 목록 기준), `EXPLAIN (ANALYZE)` 결과를 로그로 남기고 `ix_post_title_trgm`·`ix_post_content_trgm` 사용 확인 (구현 메모: 기본 빌드에서 빼고 -Dblog.perf=true로만 돈다(008 관례, pom 그대로). 시드는 낱말 약 6,300개 Zipf 분포+글마다 주제 낱말. 측정(공유 머신): 1만 p50 36ms·p95 263ms, 10만 p50 127ms·p95 461ms. 처음 구현은 10만 p95 4.6s — 최근창 SQL이 창 밖 전체 글에 본문 ILIKE를 걸고, 후보 SQL이 최신순 색인을 훑으며 후보 밖 글까지 조건을 따져서였다. 창 경계를 색인 조건으로, 후보를 id = ANY(ARRAY(...))로 바꾸고 ③·최신순 후보를 모든 단어 본문 AND(GIN 교집합) ∪ 단어별 제목·태그로 고쳤다(research R8 모양은 그대로))
- [ ] T012 [P] [US1] 화면 테스트 `F/features/search/__tests__/SnippetText.test.tsx`(범위대로 `<mark>`, `<script>` 글자가 텍스트 노드, 빈 `marks`), `F/features/search/__tests__/SearchBox.test.tsx`(`#spring` → `/tags/spring`, `# spring`·`#` → 보통 검색, 남는 단어 없음이면 요청 없이 "두 글자 이상 입력해 주세요"), `F/pages/__tests__/SearchPage.test.tsx`(글 탭 기본, 정렬 전환, 2글자 안내, 결과 없음 문구 "'{검색어}'에 대한 글이 없어요", 429 문구, [더 보기], 뒤로 가기 복원)

### Implementation for User Story 1

- [X] T013 [US1] `B/discovery/infra/PostSearchRepository.java` — research R7 단계 조건(단어마다 이름 붙은 매개변수, `escapeLike`), R8 ① 최근창 SQL·② 후보 `UNION` SQL, `snippetSources(ids)`. 클래스 주석에 plan Complexity Tracking 2행(원칙 II 읽기 예외)을 적는다 (T007 다음) (구현 메모: ① 최근창 SQL은 창 크기(count)를 LATERAL로 함께 돌려받아 창이 가득 찼을 때만 ② 후보 SQL을 돌린다)
- [X] T014 [US1] `B/discovery/application/search/SnippetBuilder.java`·`Snippet.java`(research R9) (T009 통과)
- [X] T015 [US1] `B/discovery/application/search/SearchCursor.java`(`ListScope` `search:{sort}:{blogOwnerId|-}:{지문}`, 키 `[stage, first_public_at 마이크로초, id]`)와 `PostSearchService.java`(단계 잇기 9 + 1, 카드 `findCardsByIds` 순서 맞추기, 주변 문장, `notice`, 로그는 길이·단어 수·단계·시간만 — FR-039) (T013·T014 다음) (구현 메모: 트랜잭션을 열지 않는다 — 요청 제한(Redis)이 판정 순서상 SQL 앞에 있어 서비스가 beforeSearch 콜백으로 받는다. 3글자 이상 단어가 없으면 ③ 단계는 ②와 같아 건너뛴다)
- [X] T016 [US1] `B/discovery/web/SearchController.java` `GET /api/search/posts`(`q`·`sort`·`cursor`, 이 단계는 `blog` 없이) — 응답은 카드 필드 + `snippet`을 평평하게(openapi `PostSearchItem`) (T010 통과) (구현 메모: q가 없어도 400 SEARCH_QUERY_TOO_SHORT, 모르는 sort는 400 VALIDATION_FAILED. 응답 Cache-Control private, no-cache)
- [X] T017 [P] [US1] 001 `B/shared/web/SensitiveParamMasking.java`에 `q`를 더해 접근 로그·오류 로그에서 가린다(001 소유 — 001 담당에게 알림)와 테스트 1개 (구현 메모: 001 담당에게 알림 대상 — 보고에 적음. 이름 목록에 q를 더해 접근 로그·로그 문구 모두 가린다)
- [ ] T018 [P] [US1] `F/api/discovery.ts`(`searchPosts({q, sort, cursor, blog})`, 타입은 openapi와 같게)와 `F/features/search/SnippetText.tsx`·`searchMessages.ts`
- [ ] T019 [US1] `F/features/search/SearchBox.tsx`(R6 `#태그` 판정은 008 `F/features/tag/normalizeTag.ts`, 이동 주소는 `tagPath.ts`, 남는 단어 판정은 서버와 같은 규칙의 작은 함수 `F/features/search/parseQuery.ts`)와 001 `F/features/auth/SessionBar.tsx`에 머리말 검색창 자리(001 담당에게 알림) (T012 일부 통과)
- [ ] T020 [US1] `F/pages/SearchPage.tsx`(`/search?q&tab&sort`, 글 탭 — 005 `PostCardGrid`의 미리보기 자리를 `SnippetText`로, `useCursorList({listKey: 'search:posts:{sort}:{q}', restore})`, 2글자 안내·결과 없음·429) (T012 통과)
- [X] T021 [US1] 005 `B/discovery/web/PageShellController.java`에 `GET /search` 셸(`LinkPreviewMeta` `noindex = true`)을 더하고 `T/discovery/SearchPageShellIntegrationTest.java`(첫 응답에 `<meta name="robots" content="noindex">`) (005 소유 파일) (구현 메모: 005 LinkPreviewMetaFactory에 forSearch()와 noindex(meta)를 더했다. 메타에 검색어를 넣지 않는다)

**Checkpoint**: 글 검색 완료 — MVP

---

## Phase 4: User Story 2 - 홈에서 요즘 반응이 많은 글 보기 (Priority: P2)

**Goal**: 홈 [트렌딩] 탭에서 10분마다 갱신되는 순위를 홈과 같은 카드·9개씩 [더 보기]로 보고, 보는 도중 순위가 바뀌어도 이어 보기가 맞는다.

**Independent Test**: 반응·나이가 다른 글로 순위를 만들고 탭·이어 보기·건너뛰기·만료·장애 대체를 확인한다.

### Tests for User Story 2 ⚠️

- [X] T022 [P] [US2] `T/discovery/unit/TrendingScoreTest.java` — 점수식(설정값 가중치), 1시간 된 글 > 하루 된 글, 동점 정렬 (구현 메모: 점수식은 TrendingProperties.score(SQL과 같은 식)로 확인)
- [X] T023 [P] [US2] `T/discovery/integration/TrendingSnapshotIT.java` — US2 #1~#4·#7, FR-005~FR-008, SC-003: 점수 순, 자기 댓글 20개만 → 제외, 조회만 많은 글 제외, 작성자 5개 → 3개, 8일 전 글 제외(7일 경계는 `MutableClock`), 숨긴 댓글·삭제된 자리의 작성자 미포함·같은 사람 여러 댓글 1명, 반응 없음 → `count 0`·`current` 갱신, 비공개·휴지통·숨김·유예 작성자 글 0, 키 TTL 1,800, 잠금 이름 `trendingSnapshot`, 계산 시간 기록(1초 넘으면 T046) (구현 메모: MutableClock 대신 compute(now)·refresh(now)에 시각을 넘겨 7일 경계 확인. 친구 공개는 V1에 없어 해당 없음. 계산 시간은 몇 건 규모에서 수 ms(1초 미만))
- [X] T024 [P] [US2] `T/discovery/integration/TrendingApiIT.java` — US2 #5·#6, SC-002·SC-004·SC-007: 첫 페이지 9개 → 새 스냅샷 생성 → 이전 커서로 끝까지 중복·누락 0, 안 본 글 2개 비공개·휴지통 → 건너뛰고 9개, 100개 끝 `nextCursor null`, 31분 뒤 커서 → 410 `SNAPSHOT_EXPIRED`(본문 고정), 다른 목록 커서 400, Redis 정지 → 첫 9개·`nextCursor null`, `current` 없음 → 즉시 계산, 비회원 200, 탈퇴 유예 회원 403, p95 200ms·카드 SQL 1번 (구현 메모: 31분 뒤는 스냅샷 키를 지워(TTL 만료와 같은 상태) 확인. 측정 p95 10ms(MockMvc, 글 100개 스냅샷). SQL 수는 서비스 직접 호출로 셈(카드 1번))
- [ ] T025 [P] [US2] 화면 테스트 `F/features/trending/__tests__/TrendingList.test.tsx`(안내 문구, 순위 숫자 없음, 빈 상태 + [최신 글 보기], 410이면 "순위가 새로 바뀌었어요" 후 처음부터, `nextCursor null`이면 버튼 숨김)와 `F/pages/__tests__/HomePage.test.tsx`에 탭(기본 최신, `/?tab=trending` 직접 열기, 탭마다 복원 키 분리)

### Implementation for User Story 2

- [X] T026 [US2] `B/discovery/infra/TrendingRepository.java`(research R3 SQL, 공용 조건은 `VisibilityFilter` 조각, 클래스 주석에 Complexity Tracking 1행)와 `TrendingSnapshotStore.java`(research R4 Redis 명령 — 파이프라인, `RedisConnectionFailureException` 등은 `Optional.empty`로) (T022·T023 실패 확인) (구현 메모: Redis 장애는 RedisGuard 대체 경로에서 StoreUnavailableException을 던져 서비스가 즉시 계산으로 바꾼다(Optional.empty 대신 — 없음과 장애를 구분))
- [X] T027 [US2] `B/discovery/application/trending/TrendingSnapshotJob.java`(`@Scheduled(cron)` + `@SchedulerLock(name = "trendingSnapshot", lockAtMostFor = "PT9M")` + `ApplicationReadyEvent` 1번, Redis 장애면 WARN)와 `TrendingCursor.java`·`TrendingQueryService.java`(contracts §3 표 — 18개씩 읽어 10개 채우기, 410, 대체 경로) (T023 통과) (구현 메모: 기동 직후 실행은 같은 이름의 ShedLock을 DefaultLockingTaskExecutor로 직접 잡는다(자기 호출은 프록시를 거치지 않음). 새 설정 blog.trending.refresh-on-startup)
- [X] T028 [US2] `B/discovery/web/TrendingController.java` `GET /api/posts/trending`(응답 005 `CursorPage<PostCardView>`) (T024 통과) (구현 메모: Cache-Control private, no-cache)
- [ ] T029 [US2] (**006 머지 후** — `App.tsx`) `F/api/discovery.ts`에 `getTrending(cursor)`, `F/features/trending/TrendingList.tsx`·`trendingMessages.ts`, 005 `F/pages/HomePage.tsx`에 `[최신] [트렌딩]` 탭(`role="tablist"`, `?tab=trending`, `useCursorList({listKey: 'trending'})`), `F/App.tsx`에 `/search` 경로(US1 화면 연결) (T025 통과)

**Checkpoint**: 트렌딩 완료

---

## Phase 5: User Story 3 - 사람 찾기와 블로그 안 검색 (Priority: P3)

**Goal**: 사람 탭에서 닉네임·블로그 주소로 활동 회원을 찾고, 블로그 페이지 안에서 그 작성자의 공개 글만 검색한다. 검색 요청 제한을 붙인다.

**Independent Test**: 같은 닉네임의 활동·탈퇴 신청 회원, 정확·부분 일치 주소 회원으로 사람 검색을, 두 작성자의 같은 단어 글로 블로그 안 검색을, 31번째 검색으로 요청 제한을 확인한다.

### Tests for User Story 3 ⚠️

- [X] T030 [P] [US3] `T/discovery/integration/PeopleSearchIT.java` — US3 #1·#2: 유예·익명 처리 제외, 정지 회원 포함, 정확히 일치 먼저(닉네임·주소 각각), 최대 20명, `김 민서`·`@kim7550` 덩어리, 1글자 400, `%`·`_` 글자 그대로, 프로필 사진·소개 첫 줄 (구현 메모: 닉네임은 V1 uq_member_nickname(lower) 때문에 같은 값 둘을 만들 수 없어 '김민서'·'김민서탈퇴'로 확인. 정지 회원 포함도 확인)
- [X] T031 [P] [US3] `T/discovery/integration/BlogSearchIT.java` — US3 #3: 그 블로그 공개 글만, 없는 주소·유예 회원 블로그 404(본문 고정), 대문자 주소도 같은 결과, 블로그 주인이 검색해도 공개 글만, 블로그별 커서 분리(다른 블로그 커서 400)
- [X] T032 [P] [US3] `T/discovery/integration/SearchRateLimitIT.java` — FR-037: 회원·`vid`·쿠키 없는 IP+UA 각각 31번째 429 + `Retry-After`, 글·사람 합산, Redis 정지 중 통과, 판정 순서(429가 맨 끝 — 31번째라도 블로그 404가 먼저), 로그에 검색어 원문 없음 (009 머지 후) (구현 메모: Redis 정지 시험은 세션도 Redis에 있어 vid 쿠키 방문자로 확인. 로그는 OutputCaptureExtension, Redis 키에 검색어·vid 원문 없음도 확인)
- [ ] T033 [P] [US3] 화면 테스트 `F/features/search/__tests__/PeopleResultItem.test.tsx`와 `SearchPage.test.tsx`에 사람 탭(결과·빈 상태·1글자 안내), `F/pages/__tests__/BlogPage.test.tsx`에 블로그 검색창·`?q=` 결과

### Implementation for User Story 3

- [X] T034 [US3] `B/discovery/infra/PeopleSearchRepository.java`(research R10 SQL)와 `B/discovery/application/search/PeopleSearchService.java`, `SearchController`에 `GET /api/search/people` (T030 통과) (구현 메모: 소개 첫 줄은 서버가 잘라 bioFirstLine으로 준다(첫 줄바꿈 앞, 앞뒤 공백 제거, 비면 null))
- [X] T035 [US3] `SearchController`·`PostSearchService`에 `blog` 매개변수(001 `findReadableBlogOwner` → 없으면 `NotFoundException`, 커서 목록 구분에 주인 번호) (T031 통과)
- [X] T036 [US3] (**009 머지 후**) `B/discovery/application/search/SearchRateLimit.java`(`ratelimit:search:{visitorKey}`, 009 `VisitorKeyResolver`, 001 `RateLimiter.acquireOrThrow`, 판정 순서 맨 끝)를 두 API에 붙인다 (T032 통과)
- [ ] T037 [US3] `F/features/search/PeopleResultItem.tsx`, `SearchPage` 사람 탭, `F/api/discovery.ts`에 `searchPeople(q)`, 005 `F/pages/BlogPage.tsx`에 "이 블로그에서 검색" 입력과 `?q=` 결과(목록 자리, 같은 카드·정렬 탭), 005 `PageShellController`의 `/@{handle}` 셸에서 `q`가 있으면 `noindex` (T033 통과)

**Checkpoint**: 모든 User Story 완료

---

## Phase 6: 사이트 지도 (FR-040)

**Purpose**: 검색 엔진용 `/sitemap.xml` (Clarifications Q2로 이 기능 소유)

- [X] T038 [P] `T/discovery/integration/SitemapIT.java` — 공개 글·블로그·첫 화면 주소만, 비공개·친구 공개(적용자)·휴지통·숨김·유예 작성자 글과 공개 글 없는 블로그 없음, 휴지통으로 보낸 직후 다음 요청에서 빠짐, `lastmod` = `edited_at ?? first_public_at`, 주소 XML 이스케이프, `Content-Type: application/xml`, `Cache-Control: no-cache`, 탈퇴 유예 회원 요청도 200(게이트 밖), 글 1만 개 2초 이내·메모리에 전체를 모으지 않음(1,000개씩 읽는지 `SqlCounter`) (구현 메모: lastmod는 초 단위 ISO-8601 UTC. 1만 개는 글 SQL 11번(1,000개씩 + 마지막 빈 확인) + 블로그 1번. 008 태그 페이지는 팀 결정(008 T074 '포함하지 않음')대로 넣지 않았다)
- [X] T039 `B/discovery/infra/SitemapRepository.java`(research R13 — 글은 `p.id > :after` 1,000개씩, 블로그는 `GROUP BY` 1번), `B/discovery/application/SitemapService.java`(흘려 쓰기, 50,000개 한도 WARN), `B/discovery/web/SitemapController.java`(`StreamingResponseBody`) (T038 통과) (구현 메모: StreamingResponseBody 대신 응답 스트림에 요청 스레드에서 바로 흘려 쓴다(비동기 디스패치 없음, 메모리에 모으지 않는 것은 같음))

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 권한 매트릭스, 종단 확인, 성능 결정, 인계

- [ ] T040 [P] 권한 매트릭스 `TR/permission/search.csv`(research R16 표, owner `012`)와 `T/discovery/integration/SearchPermissionMatrixIT.java`(004 하네스) — 006 plan `TrashedPostPermissionMatrixIT`의 검색·sitemap 행과 겹치지 않게 006 행은 006 테스트가 맡는다
- [ ] T041 [P] Playwright `E/trending-search.spec.ts` — quickstart §3 3·5~13, 375px 폭 가로 스크롤 없음, 탭·정렬 키보드 조작
- [ ] T042 [P] 화면 접근성: 홈 탭(`aria-selected`), 검색창 이름 "검색"·"이 블로그에서 검색", `<mark>`가 색만이 아니라 굵기로도 구분되는지(대비 4.5:1)
- [ ] T043 공용 조건 회귀: 004 `VisibilityFilter`를 쓰는 다른 목록(홈·블로그·피드·태그) 테스트를 함께 돌려 `findCardsByIds` 추가가 기존 카드 SQL을 바꾸지 않았는지 확인
- [ ] T044 [P] 005 `T/discovery/ReadingContractConformanceIntegrationTest.java`처럼 openapi 예시와 실제 응답 모양 비교 테스트를 트렌딩·검색에 더한다(`snippet.marks` 모양 포함)
- [ ] T045 운영 확인 메모를 quickstart §0에 맞춰 남긴다: 운영 PostgreSQL의 `pg_trgm` 확장 생성 권한, `blog.site.base-url` 운영값, 검색 엔진 콘솔 sitemap 등록
- [ ] T046 성능 결정: T011·T023 측정 결과로 `ix_comment_post_author`(32 ERD 제안)·`blog.search.recent-window` 조정이 필요한지 판단해 기록한다. 인덱스가 필요하면 V3 이후 마이그레이션 작업(`R/db/migration/V{다음}__comment_post_author_index.sql`, `CREATE INDEX CONCURRENTLY`는 Flyway 트랜잭션 밖 설정)을 새로 만든다 — V1/V2는 고치지 않는다
- [ ] T047 quickstart.md §2 명령 전체 실행, §3 수동 확인, §4 다른 기능 확인(있는 기능만) 결과를 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2)**: Setup 다음 — 모든 User Story를 막는다
- **US1 (Phase 3)**: Foundational 다음. 태그 단계는 008 머지 후
- **US2 (Phase 4)**: Foundational(T005) 다음. 점수 재료는 007·009 머지 후. 화면 연결 T029는 006 머지 후
- **US3 (Phase 5)**: US1(T015·T016) 다음. 요청 제한 T036은 009 머지 후
- **사이트 지도 (Phase 6)**: Foundational 다음, 다른 스토리와 독립
- **Polish (Phase 7)**: 원하는 User Story가 끝난 뒤

### User Story Dependencies

- **US1 (P1)**: 독립
- **US2 (P2)**: 독립(검색과 공유하는 것은 `findCardsByIds`뿐)
- **US3 (P3)**: US1 검색 서비스·화면 위에 쌓는다

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 같은 파일을 고치는 작업은 순서대로 한다: `SearchController`(T016 → T034 → T035 → T036), `PostSearchService`(T015 → T035), `F/api/discovery.ts`(T018 → T029 → T037), `SearchPage.tsx`(T020 → T037), `SearchPage.test.tsx`(T012 → T033), `PageShellController`(T021 → T037), 005 파일(`PostCardQueryRepository` T005, `HomePage` T029, `BlogPage` T037), 001 파일(`SensitiveParamMasking` T017, `SessionBar` T019), `F/App.tsx`(T029)

### Parallel Opportunities

- Phase 2의 T004·T005·T006·T008은 서로 다른 파일
- US1과 US2, 사이트 지도는 서로 다른 팀원이 동시에 할 수 있다(공유 파일 `F/api/discovery.ts`만 순서대로)

---

## Parallel Example: User Story 1

```bash
# US1 테스트를 함께 쓴다
Task: "SnippetBuilderTest in T/discovery/unit/SnippetBuilderTest.java"
Task: "PostSearchIT in T/discovery/integration/PostSearchIT.java"
Task: "PostSearchPerformanceIT in T/discovery/integration/PostSearchPerformanceIT.java"
Task: "SnippetText·SearchBox·SearchPage 화면 테스트"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1·2 완료
2. US1: 글 검색 API·화면
3. **STOP and VALIDATE**: quickstart §3 5~8, `PostSearchPerformanceIT`

### Incremental Delivery

1. US1 → MVP
2. US2(트렌딩) → 홈 탭
3. US3(사람·블로그 안 검색·요청 제한) → 사이트 지도 → Polish

### Parallel Team Strategy

1. 함께 Phase 1·2
2. 그 뒤: 개발자 A US1 → US3, 개발자 B US2 → 사이트 지도

---

## Notes

- [P] = 다른 파일, 의존 없음
- 노출 조건은 `VisibilityFilter` 하나만 쓴다. 트렌딩·검색·sitemap SQL에 상태 조건을 직접 쓰지 않는다
- 검색어 원문은 로그·DB 어디에도 남기지 않는다(테스트 T010·T032가 로그를 검사)
- 각 작업 또는 논리 묶음마다 커밋한다
