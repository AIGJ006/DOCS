# Implementation Plan: 트렌딩·검색

**Branch**: `012-trending-search` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-trending-search/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

누구나 홈의 [트렌딩] 탭에서 최근 7일 안에 처음 공개되고 반응(좋아요·남의 댓글)이 있는 공개 글을 점수순으로 홈과 같은 카드·9개씩 [더 보기]로 본다. 순위는 10분마다 상위 100개를 미리 계산해 30분 보관하고, [더 보기]는 처음 받은 순위 목록을 끝까지 따라간다. 검색창에서는 공개 글을 제목 → 태그 → 본문 단계로(또는 최신순) 9개씩 찾고 검색어 주변 문장을 강조해 보여 주며, 사람 탭에서 닉네임·블로그 주소로 회원을 찾고, 블로그 안에서도 같은 규칙으로 찾는다. 검색 엔진용 `/sitemap.xml`은 요청 때마다 공용 노출 조건으로 만든다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1의 `pg_trgm` 확장과 GIN `ix_post_title_trgm`·`ix_post_content_trgm`·`ix_member_nickname_trgm`·`ix_member_handle_trgm`, 범위용 `ix_post_feed`, 댓글 작성자 수용 `uq_comment_post_id (post_id, id)`를 그대로 쓴다. 32 ERD 제안 `ix_comment_post_author`는 넣지 않고 측정 뒤 V3 이후 후보로 남긴다(R1).
- **모듈.** 트렌딩·검색·sitemap 모두 `discovery`(홈·블로그 목록과 같은 곳). 카드는 005 `PostCardAssembler`·`PostCardView`를 그대로 쓰고, 노출 조건은 004 `VisibilityFilter` 하나만 쓴다(R2).
- **트렌딩 계산 = SQL 1번.** `TrendingSnapshotJob`(10분마다, ShedLock `trendingSnapshot`)이 공용 조건 + `first_public_at > now − 7일`(`ix_post_feed`) 후보에 대해 글마다 "남의 댓글 작성자 수"(작성자 제외, 삭제·숨김 제외 — Clarifications Q1)를 세고 점수식으로 정렬, 작성자당 3개(`row_number`), 상위 100개를 Redis `trending:{스냅샷ID}`(List)와 `trending:{스냅샷ID}:count`에 30분 TTL로 넣고 `trending:current`를 바꾼다(R3·R4).
- **트렌딩 읽기 = Redis 1번 + 카드 SQL 1번.** `GET /api/posts/trending?cursor=` — 커서(001 `CursorCodec`, `ListScope` `trending`, 키 `[스냅샷ID, 위치]`) 위치부터 번호를 넉넉히 읽어 카드 SQL(005 카드 SQL + `p.id = ANY(:ids)`)로 지금 볼 수 있는 글만 9개 채운다. 스냅샷이 사라졌으면 410 `SNAPSHOT_EXPIRED`, Redis 장애·스냅샷 없음이면 계산 SQL을 바로 돌려 첫 9개 + `nextCursor: null`(R5).
- **검색어 처리 한 곳.** `SearchQueryParser`: NFC → 앞뒤 공백 → 50자(코드 포인트) → 공백으로 나눔 → 1글자 버림 → 앞 5단어. 2글자는 제목·태그만, 3글자 이상은 제목·태그·본문. 남는 단어 없음 → 400 `SEARCH_QUERY_TOO_SHORT`, 2글자가 있으면 `notice: TWO_CHAR_TITLE_TAG_ONLY`(R6).
- **글 검색 = 단계별 SQL.** 관련도순은 ① 모든 단어가 제목 ② 제목 또는 태그 ③ 어딘가(본문은 3글자 이상만) 순으로, 단계마다 "최근 3,000개 창 안에서 10개" → 모자라면 "가장 긴 단어의 trigram 후보(제목·본문 GIN, 태그 EXISTS를 UNION으로 나눔) 안에서 전체 조건". 한 단계가 끝나면 같은 페이지에서 다음 단계로 잇는다. 최신순은 ③ 조건 하나. `ILIKE … ESCAPE '\'`로 `%`·`_`·`\`를 글자로 찾는다. 커서 키 `[단계, first_public_at, id]`, 목록 구분에 검색어 지문을 넣어 다른 검색어의 커서를 거부한다(R7·R8).
- **주변 문장은 범위로 준다.** 본문(없으면 제목)에서 검색어가 처음 나온 곳 앞뒤 40자를 원문 그대로 자르고(Clarifications Q4), 응답은 `snippet {text, marks: [[시작, 끝]]}`이다. 화면은 텍스트 노드와 `<mark>`로만 그린다 — HTML 문자열을 주고받지 않아 "먼저 이스케이프, 그다음 강조"가 구조로 보장된다(FR-034, 헌법 IV)(R9).
- **사람 검색.** `GET /api/search/people?q=` — 공백·맨 앞 `@`를 지운 한 덩어리(2글자 이상, Clarifications Q3)로 닉네임·주소 `ILIKE` 부분 일치, 탈퇴 신청·익명 처리 제외, 정확히 일치 먼저, 최대 20명(R10).
- **블로그 안 검색.** `GET /api/search/posts?q=&blog={handle}` — 001 `MemberQueryService.findReadableBlogOwner`(없음·유예 404) 뒤 같은 SQL에 `p.author_id = :owner`. 화면은 `/@{handle}?q=`(R11).
- **요청 제한·기록.** 글·사람 검색 합쳐 같은 방문자 1분 30번(`ratelimit:search:{visitorKey}`, 방문자 키는 009 `VisitorKeyResolver` `m:`/`v:`/`h:`, 001 `RateLimiter`, Redis 장애면 통과), 429 `TOO_MANY_REQUESTS`. 로그에는 검색어 길이·단어 수·걸린 시간만(R12).
- **sitemap.** `GET /sitemap.xml` — 요청마다 공용 조건(비회원 기준)으로 공개 글 정규 주소(`lastmod` = 다시 발행 시각 또는 최초 공개 시각)와 공개 글이 1개 이상인 블로그 주소를 XML로 흘려 보낸다. 캐시하지 않으므로 이벤트를 구독할 것이 없다(Clarifications Q2)(R13).
- **이벤트 구독 없음.** 트렌딩 스냅샷의 글은 읽을 때 다시 거르고, 검색은 V1 trigram 인덱스를 DB가 갱신하며, sitemap은 요청 때 만든다. spec Implementation Notes의 "검색 색인 갱신용 이벤트"는 쓰지 않는다(R14).
- **화면.** 홈 `[최신] [트렌딩]` 탭(`/?tab=trending`, 안내 문구, 빈 상태, 410 안내), 머리말 검색창(`#태그` 한 단어면 008 `normalizeTag`로 태그 페이지 이동), `/search?q=&tab=posts|people&sort=relevance|latest`(셸 `noindex`), 블로그 머리말 검색창(R15).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Scheduling), `JdbcClient`, Spring Data Redis(`StringRedisTemplate`), ShedLock JDBC, 001 `RateLimiter`·`CursorCodec`·`ListScope`·`MemberQueryService.findReadableBlogOwner`·`CacheControlPolicy`, 004 `VisibilityFilter`, 005 `PostCardQueryRepository`·`PostCardAssembler`·`PostCardView`·`PageShellController`·`LinkPreviewMeta(noindex)`·`blog.site.base-url`, 008 `TagNormalizer`(검색어 태그 비교는 소문자만), 009 `VisitorKeyResolver`, media `ImageUrlResolver`
- 새 의존성 없음(XML은 `StringBuilder` + 이스케이프로 흘려 씀)
- 화면: React 18 + react-router 7, 005 `PostCardGrid`·`LoadMoreButton`·`useCursorList`·`listRestore`·`HomePage`·`BlogPage`, 008 `normalizeTag.ts`, 001 `SessionBar`

**Storage**:

- PostgreSQL 읽기만: `post`·`member`·`image`(카드), `comment`(남의 댓글 작성자 수), `post_tag`·`tag`(태그 검색)
- Redis: `trending:current`(String), `trending:{yyyyMMddHHmm}`(List, 글 번호 최대 100), `trending:{yyyyMMddHHmm}:count`(String), 둘 다 TTL 1,800초. 요청 제한 `ratelimit:search:{visitorKey}`

**Testing**: JUnit 5, Testcontainers(PostgreSQL 16 + `pg_trgm`, Redis), MockMvc, `MutableClock`, `RedisOutage`(001 도구), `SqlCounter`. 화면은 Vitest + Testing Library, 종단 확인은 Playwright. 헌법 VIII에 따라 노출 조건(SC-003), 끝까지 넘기기(SC-004), 주변 문장 스크립트 0(SC-005), 성능(SC-001 글 10만 개 500ms, SC-002 트렌딩 200ms)을 실제 DB로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 트렌딩 p95 200ms 이내(Redis `LRANGE` 1번 + 카드 SQL 1번, 요청 때 전체 계산 없음 — SC-002)
- 스냅샷 계산 1초 이내(최근 7일 공개 글 수백~수천 개 기준)
- 글 검색 p95 500ms 이내(글 10만 개, SC-001), 헌법 목록 기준 300ms(글 1만 개)도 함께 잰다
- 사람 검색 p95 100ms 이내(회원 수천 명)
- sitemap: 글 1만 개 2초 이내, 메모리에 전체 목록을 모으지 않음

**Constraints**:

- 노출 조건은 `VisibilityFilter` 하나만(목록마다 WHERE를 새로 쓰지 않음, 006 R-2)
- 검색어는 회원 정보와 함께 저장하지 않고 로그에 남기지 않는다(FR-039)
- 응답에 HTML 문자열이 없다(주변 문장은 글자 + 강조 범위)
- 트렌딩·검색 화면은 비회원도 쓴다. 볼 수 없는 블로그는 같은 404 본문
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 글 1만 건(헌법 기준)·10만 건(검색 성능 검증), 회원 수천 명, 최근 7일 공개 글 수백 개
- API 4개(트렌딩, 글 검색, 사람 검색, sitemap), 정리 작업 1개(스냅샷), 화면 페이지 1개 + 홈 탭 + 검색창 2곳

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 인덱스 그대로. 오늘/주/월 탭·인기 검색어 등은 개인 확장(T-11, Q-9) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (예외 2건 — Complexity Tracking)** | 모두 discovery. 블로그 주인은 001 `MemberQueryService`, 방문자 키는 009 `VisitorKeyResolver`(공개 Service). 예외: 트렌딩 계산 SQL이 `comment`를, 검색 SQL이 `post_tag`·`tag`를 읽기 전용으로 읽는다(005 카드 SQL의 `member`·`image` 예외와 같은 종류). 사람 검색의 `member`·`image` 읽기도 같은 예외 |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 모든 글 목록이 비회원 기준 공용 조건(블로그 주인이 봐도 공개 글만, 06 V-8). 블로그 안 검색의 없음·유예 블로그는 같은 404. 권한 매트릭스 `search.csv`(R16) — 휴지통·숨김·비공개·친구 공개·작성자 유예 글 0 |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 주변 문장은 글자 + 강조 범위로 주고 화면은 텍스트 노드·`<mark>`로만 그린다. sitemap XML은 주소를 XML 이스케이프 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 순위 보관소 장애 → 즉시 계산 첫 9개(FR-013). 요청 제한 장애 → 통과. 스냅샷 작업 실패 → 이전 스냅샷이 30분 동안 쓰이고 그 뒤 즉시 계산 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 새로 저장하는 데이터 없음. Redis 스냅샷은 30분 TTL |
| VII. 수치는 설정값으로 | **PASS** | `blog.trending.*`(기간 7일, 갱신 10분, 보관 100개·30분, 작성자당 3, 가중치 3·2·0.1, 지수 1.5, 보정 2시간), `blog.search.*`(최대 50자·5단어, 최근창 3,000, 주변 40자, 사람 20명, 제한 30/1m) |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 점수·작성자 3개·7일 경계, 스냅샷 이어 보기·만료·건너뛰기, 검색 단계·AND·특수문자·2글자 규칙, 10만 건 `EXPLAIN`, sitemap 노출 조건 |

**Gate 결과 (Phase 0 전)**: 원칙 II 읽기 예외를 Complexity Tracking에 적었다. 005·008·010과 같은 종류의 예외다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. spec Implementation Notes의 응답 `snippetHtml`은 화면이 HTML 문자열을 그대로 넣어야 해서 헌법 IV와 부딪힌다. `snippet {text, marks}`로 바꿨다(R9). 결과 표시와 FR-034 기준은 같다.
  2. 트렌딩 410의 코드 `SNAPSHOT_EXPIRED`와 검색어 부족 400 `SEARCH_QUERY_TOO_SHORT`는 원문에 없는 새 코드다(제안 — 팀 확인 T003).
  3. 015 data-model §이벤트 표는 `MemberWithdrawn`·`MemberRestored`의 구독자로 012를 적었지만, 이 계획은 구독하지 않는다(요청 때 공용 조건으로 거름). Tier B/C analyze에서 015 문서를 고쳤다.
  4. 009가 아직이면 `VisitorKeyResolver`가 없다. 검색 요청 제한은 009 머지 후 붙인다. 그 전에는 제한 없이 통과한다(Redis 장애 때와 같은 동작, R12).
  5. 트렌딩 탭은 005 `HomePage`를 고친다. 홈 목록 커서·복원 키(`home`)와 섞이지 않게 트렌딩은 `trending` 키를 쓴다.

## Project Structure

### Documentation (this feature)

```text
specs/012-trending-search/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml           # 트렌딩, 글 검색, 사람 검색, sitemap
│   └── trending-search-sql.md # 트렌딩 계산·스냅샷·읽기, 검색어 처리, 단계 SQL, 주변 문장, 사람 검색, sitemap
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/discovery/
├── web/
│   ├── TrendingController.java              # GET /api/posts/trending
│   ├── SearchController.java                # GET /api/search/posts, /api/search/people
│   ├── SitemapController.java               # GET /sitemap.xml
│   └── PageShellController.java             # + /search 셸 noindex, /@{handle}?q= 셸 noindex (005 소유)
├── application/
│   ├── trending/
│   │   ├── TrendingSnapshotJob.java         # 10분, ShedLock trendingSnapshot
│   │   ├── TrendingQueryService.java        # 스냅샷 읽기·건너뛰기·즉시 계산 대체
│   │   ├── TrendingCursor.java              # ListScope trending, [snapshotId, position]
│   │   └── TrendingProperties.java          # blog.trending.*
│   ├── search/
│   │   ├── SearchQueryParser.java           # 정규화·자르기·단어 나누기 → SearchQuery
│   │   ├── SearchQuery.java / SearchWord.java
│   │   ├── PostSearchService.java           # 단계 잇기·커서·주변 문장
│   │   ├── PeopleSearchService.java
│   │   ├── SearchCursor.java                # ListScope search:{sort}:{blog|-}:{지문}
│   │   ├── SnippetBuilder.java              # 앞뒤 40자 + 강조 범위
│   │   ├── SearchRateLimit.java             # ratelimit:search:{visitorKey}
│   │   └── SearchProperties.java            # blog.search.*
│   ├── SitemapService.java                  # 공개 글·블로그 주소 흘려 쓰기
│   └── DiscoveryReasonCode.java             # SNAPSHOT_EXPIRED(410), SEARCH_QUERY_TOO_SHORT(400)
└── infra/
    ├── TrendingRepository.java              # 계산 SQL (comment 읽기 — Complexity Tracking)
    ├── TrendingSnapshotStore.java           # Redis RPUSH·EXPIRE·SET·LRANGE
    ├── PostSearchRepository.java            # 단계 SQL (post_tag·tag 읽기 — Complexity Tracking)
    ├── PeopleSearchRepository.java          # member·image
    ├── SitemapRepository.java               # 커서로 나눠 읽기
    └── PostCardQueryRepository.java         # + findCardsByIds(ids, viewer) (005 소유)

backend/src/main/resources/application.yml   # blog.trending.*, blog.search.*

backend/src/test/
├── resources/permission/search.csv          # trending·search.posts·search.people·sitemap × 글 상태
└── java/com/team/blog/discovery/
    ├── unit/SearchQueryParserTest.java, SnippetBuilderTest.java, TrendingScoreTest.java
    └── integration/
        ├── TrendingSnapshotIT.java          # US2 #1~#4·#7, 점수·7일·작성자 3개·숨긴 댓글
        ├── TrendingApiIT.java               # US2 #5·#6, 410, Redis 장애
        ├── PostSearchIT.java                # US1
        ├── PostSearchPerformanceIT.java     # SC-001 (10만 건, @Tag("slow"))
        ├── PeopleSearchIT.java              # US3 #1·#2
        ├── BlogSearchIT.java                # US3 #3
        ├── SearchRateLimitIT.java           # FR-037
        ├── SitemapIT.java                   # FR-040
        └── SearchPermissionMatrixIT.java    # 004 하네스

frontend/src/
├── api/discovery.ts                         # getTrending / searchPosts / searchPeople
├── features/search/
│   ├── SearchBox.tsx                        # 머리말·블로그 검색창, #태그 이동
│   ├── SnippetText.tsx                      # 텍스트 노드 + <mark>
│   ├── PeopleResultItem.tsx
│   └── searchMessages.ts
├── features/trending/
│   ├── TrendingList.tsx                     # 안내 문구·빈 상태·410 처리
│   └── trendingMessages.ts
├── pages/HomePage.tsx                       # [최신] [트렌딩] 탭 (005 소유 파일)
├── pages/SearchPage.tsx                     # /search
├── pages/BlogPage.tsx                       # 머리말 검색창, ?q= 결과 (005 소유 파일)
├── features/auth/SessionBar.tsx             # 검색창 자리 (001 소유 임시 머리말)
└── App.tsx                                  # /search 경로
```

**Structure Decision**: 02 §3대로 공개 글 목록·탐색은 모두 `discovery`에 둔다. 005 파일(`PostCardQueryRepository`·`PageShellController`·`HomePage`·`BlogPage`)은 메서드·자리를 더할 뿐 구조를 바꾸지 않는다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `discovery.infra.TrendingRepository`의 계산 SQL이 `comment`를 읽는다(원칙 II 읽기 예외) | 후보 글마다 "남의 댓글 작성자 수(삭제·숨김 제외)"를 같은 SQL 안에서 세야 점수·작성자당 3개·상위 100개를 한 번에 정할 수 있다(32 §3-1) | 007 공개 Service로 글 번호 목록을 넘겨 수를 받으면 SQL 2번 + 정렬을 메모리에서 다시 해야 하고, 저장 카운터를 새로 두면 스키마 변경(헌법 I) |
| `discovery.infra.PostSearchRepository`가 `post_tag`·`tag`를, `PeopleSearchRepository`가 `member`·`image`를 읽는다 | 태그 단계(②)와 단어별 "제목 또는 태그" 조건이 같은 SQL 안에 있어야 단계·정렬·커서가 한 문장에서 결정된다. 사람 결과는 닉네임·주소·소개·사진을 한 번에 보여 준다 | 008 `TagQueryService`로 태그에 맞는 글 번호를 먼저 받으면 인기 태그는 수천 개 번호를 넘겨야 하고 단계 경계가 SQL 밖으로 나온다. 사람 검색을 001 Service로 하면 001에 검색 SQL이 생긴다 |
