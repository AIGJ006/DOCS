# Quickstart: 012-trending-search 검증 시나리오

**Feature**: `012-trending-search` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 규칙·SQL은 [contracts/trending-search-sql.md](./contracts/trending-search-sql.md), 키·값 객체·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS. PostgreSQL에 `pg_trgm` 확장(V1이 만든다 — 운영 DB는 확장 생성 권한을 배포 때 확인)
- 선행 기능: 001(`RateLimiter`·`CursorCodec`·`MemberQueryService`·임시 머리말·`SensitiveParamMasking`), 004(`VisibilityFilter`·권한 하네스), 005(카드·홈·블로그·페이지 셸·`useCursorList`), 007(댓글 — 트렌딩 작성자 수), 008(태그 — 태그 검색·`normalizeTag`·`/tags`), 009(좋아요·조회 수, `VisitorKeyResolver`)
- 있으면 함께 확인: 014(숨김 글 제외), 015(탈퇴 유예 작성자 제외)

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- 기동 직후 로그 `trending snapshot id=… size=…` 1줄

## 2. 자동 테스트

```bash
./mvnw -pl backend verify -Dit.test='Trending*IT,*Search*IT,SitemapIT' -Dtest='SearchQueryParserTest,SnippetBuilderTest,TrendingScoreTest'
./mvnw -pl backend verify -Dit.test=PostSearchPerformanceIT -Dgroups=slow     # 글 10만 개, 몇 분 걸림
(cd frontend && npx vitest run src/features/search src/features/trending src/pages/__tests__/SearchPage.test.tsx src/pages/__tests__/HomePage.test.tsx)
(cd frontend && npx playwright test e2e/trending-search.spec.ts)
```

| 테스트 | 확인하는 것 |
|---|---|
| `TrendingScoreTest` | 점수식, 같은 반응이면 1시간 된 글 > 하루 된 글 |
| `TrendingSnapshotIT` | US2 #1~#4·#7: 점수 순, 자기 댓글 20개만 있는 글 제외, 작성자당 3개, 8일 전 글 제외, 숨긴 댓글·삭제된 댓글 작성자 미포함(FR-005), 반응 없음 → `count 0`, 비공개·친구 공개·휴지통·숨김·유예 작성자 글 0(SC-003), Redis 키·TTL 1,800, ShedLock 이름 |
| `TrendingApiIT` | US2 #5·#6, SC-004: 첫 페이지 9개 → 새 스냅샷이 생긴 뒤 [더 보기]가 원래 스냅샷을 이어 끝까지 중복·누락 0, 안 본 글 2개를 비공개·휴지통으로 바꾸면 건너뛰고 9개, 100개 끝에서 `nextCursor null`, 31분 뒤(시계 이동) 커서 → 410 `SNAPSHOT_EXPIRED`, Redis 정지 → 첫 9개 + `nextCursor null`(SC-007), 응답 SQL 1번(`SqlCounter`), p95 200ms(SC-002) |
| `SearchQueryParserTest` | contracts §4 표 전부, NFC(조합형 한글), 유니코드 공백 |
| `SnippetBuilderTest` | 앞뒤 40자·`…`, 제목 대체, 서로게이트 쌍 경계, 겹친 강조 합치기, 대소문자 무시 |
| `PostSearchIT` | US1 #1~#7: 단계 순서(제목 → 태그 → 본문), 2글자 단어는 본문 제외 + `notice`, 1글자 무시·AND, 9개씩 끝까지 중복·누락 0(단계 경계 포함), `<script>` 본문이 `snippet.text`에 글자로, `%`·`_`·`\` 글자 그대로, 결과 없음 빈 배열, 최신순, 다른 검색어 커서 400, 비공개·친구 공개·휴지통·숨김·유예 작성자 글 0, 작성자 본인이 검색해도 자기 비공개 글 0 |
| `PostSearchPerformanceIT` | SC-001: 글 10만 개(현실적인 길이·단어 분포), 2·3·5글자와 다단어 검색어 20종 p95 500ms, 글 1만 개 300ms, `EXPLAIN`에 `ix_post_title_trgm`·`ix_post_content_trgm` |
| `PeopleSearchIT` | US3 #1·#2: 유예·익명 처리 회원 제외, 정확히 일치 먼저, 최대 20명, `김 민서`·`@kim7550` 덩어리 규칙, 1글자 400 |
| `BlogSearchIT` | US3 #3: 그 블로그 공개 글만, 없는·유예 블로그 404(본문 고정), 블로그 주인이 검색해도 공개 글만 |
| `SearchRateLimitIT` | FR-037: 같은 회원·같은 `vid`·쿠키 없는 같은 IP+UA 각각 31번째 429 + `Retry-After`, 글·사람 합산, Redis 정지 중 통과, 로그에 검색어 원문 없음(FR-039) |
| `SitemapIT` | FR-040: 공개 글·블로그·첫 화면 주소만, 비공개·친구 공개·휴지통·숨김·유예 작성자 글 없음, 글을 휴지통으로 보낸 직후 다음 요청에서 빠짐, `lastmod`, XML 이스케이프, `no-cache` |
| `SearchPermissionMatrixIT` | `search.csv` (research R16 표) |

## 3. 수동 확인 (브라우저)

1. 회원 A·B·C로 공개 글 몇 개(제목·태그·본문에 "트랜잭션", 제목에 "롬복"), A로 비공개 글 1개(제목 "트랜잭션")를 쓴다
2. B·C로 A의 글 하나에 좋아요·댓글 → `docker compose exec redis redis-cli DEL trending:current` 후 앱 재시작(또는 10분 대기)
3. 홈 → [트렌딩] 탭: 주소가 `/?tab=trending`, "최근 7일 동안 반응이 많은 글 · 10분마다 갱신", A의 글이 보이고 순위 숫자가 없다. 글을 열었다 뒤로 → 카드·스크롤 그대로
4. 반응이 없는 새 DB에서는 "아직 트렌딩 글이 없어요"와 [최신 글 보기]
5. 머리말 검색창에 "트랜잭" → `/search?q=트랜잭`: 제목에 있는 글이 먼저, 본문에만 있는 글이 뒤, 비공개 글 없음, 주변 문장에 "트랜잭"이 강조됨
6. "롬복" → "두 글자 단어는 제목·태그에서만 찾았어요", 결과가 없으면 "'롬복'에 대한 글이 없어요"
7. "a" → 요청 없이 "두 글자 이상 입력해 주세요"
8. 본문에 `<img src=x onerror=alert(1)>`을 쓴 글을 검색 → 주변 문장에 글자로 보이고 알림 창이 뜨지 않는다
9. [사람] 탭에서 "김 민서" → 김민서 회원, `@` 주소·소개 첫 줄. 누르면 블로그
10. `#spring` → `/tags/spring`으로 이동, `# spring` → 보통 검색
11. A 블로그 머리말 "이 블로그에서 검색"에 "트랜잭" → `/@a?q=트랜잭`에 A의 공개 글만
12. 페이지 소스: `/search`와 `/@a?q=…` 첫 응답에 `<meta name="robots" content="noindex">`
13. `http://localhost:8080/sitemap.xml` → 공개 글·블로그 주소. A의 글 하나를 휴지통으로 → 새로 고치면 그 주소가 없다
14. 검색을 1분에 31번 → "잠시 후 다시 시도해 주세요"
15. 375px 폭에서 홈 탭·검색 결과·사람 결과 가로 스크롤 없음

## 4. 다른 기능 확인 (있을 때)

- 014: 관리자가 트렌딩에 있던 글을 숨김 → 다음 [더 보기]·새로 고침에서 빠짐, 검색·sitemap에서도 빠짐. 숨긴 댓글 작성자는 다음 스냅샷의 점수에서 빠짐
- 015: 작성자가 탈퇴 신청 → 트렌딩·검색·sitemap·사람 검색에서 빠짐 → 복구하면 돌아옴
