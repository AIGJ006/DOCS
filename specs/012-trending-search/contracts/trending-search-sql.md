# Contract: 트렌딩·검색·sitemap 규칙과 SQL

**Feature**: 012-trending-search | 원문: [docs/32-trending.md](../../../docs/32-trending.md) §3·§4, [docs/33-search.md](../../../docs/33-search.md) §2~§5·§8 | SQL 전문과 근거는 [research.md](../research.md) R3·R7·R8·R10·R13 (그 문장이 계약이다)

## 1. 트렌딩 계산 (`TrendingRepository.compute(now, limit)`, research R3)

| 항목 | 규칙 |
|---|---|
| 후보 | `VisibilityFilter(비회원)` ∧ `first_public_at > now − 7일` (`ix_post_feed` 범위) |
| 남의 댓글 작성자 수 | `count(DISTINCT c.author_id)` — `c.post_id = p.id`, `c.author_id <> p.author_id`, `c.deleted_at IS NULL`, `c.hidden_at IS NULL` |
| 들어가는 글 | `like_count > 0 OR commenters > 0` |
| 점수 | `(3·like + 2·commenters + 0.1·view) / (경과 시간 + 2)^1.5` |
| 작성자당 | `row_number() OVER (PARTITION BY author_id ORDER BY score DESC, first_public_at DESC, id DESC) <= 3` |
| 정렬 | `score DESC, first_public_at DESC, id DESC` |
| 개수 | 스냅샷 100, 즉시 계산 대체 9 |

## 2. 스냅샷 (`TrendingSnapshotJob`, research R4)

```text
매 10분 (cron 0 */10 * * * *, ShedLock trendingSnapshot, lockAtMostFor PT9M) + 앱 기동 직후 1번
ids  = compute(now, 100)
id   = yyyyMMddHHmm (UTC)
DEL    trending:{id}
RPUSH  trending:{id} ids...           (ids가 있을 때만)
EXPIRE trending:{id} 1800
SET    trending:{id}:count {len(ids)} EX 1800
SET    trending:current {id}
```

## 3. 트렌딩 읽기 (`TrendingQueryService`, research R5)

| 상황 | 동작 | 응답 |
|---|---|---|
| 커서 없음, `current`·`count` 있음 | `pos = 0`부터 읽기 | 200, 10번째가 있으면 `nextCursor` |
| 커서 있음, `count` 있음 | 커서 `pos`부터 읽기 | 200 |
| 커서 있음, `count` 없음(만료) | — | 410 `SNAPSHOT_EXPIRED` |
| 커서 없음, `current` 없음 또는 `count` 없음 | `compute(now, 9)` | 200, `nextCursor: null` |
| Redis 장애 | `compute(now, 9)` | 200, `nextCursor: null` (커서가 있었어도) |
| `count = 0` | — | 200, `items: []` (빈 상태) |
| 다른 목록의 커서 | — | 400 `INVALID_CURSOR` |

읽기: `LRANGE trending:{snap} pos pos+17` → `PostCardQueryRepository.findCardsByIds(ids)`(공용 조건) → 번호 순서대로 보이는 카드만, 10개가 되거나 `count` 끝까지 반복.

## 4. 검색어 정리 (`SearchQueryParser`, research R6)

| 입력 | 단어 | `notice` | 결과 |
|---|---|---|---|
| `트랜잭션 정리 a` | `트랜잭션`, `정리` | `TWO_CHAR_TITLE_TAG_ONLY` | 두 단어 모두 있는 글 |
| `롬복` | `롬복` | `TWO_CHAR_TITLE_TAG_ONLY` | 제목·태그에만 |
| `a b c` | 없음 | — | 400 `SEARCH_QUERY_TOO_SHORT` |
| `100% 할인` | `100%`, `할인` | `TWO_CHAR_TITLE_TAG_ONLY` | `%`를 글자로 |
| `snake_case` | `snake_case` | null | `_`를 글자로 |
| 6단어 이상 | 앞 5단어 | | |
| 50자 넘음 | 50 코드 포인트까지 자른 뒤 나눔 | | |
| `#spring` | (화면이 태그 페이지로 이동 — 서버까지 오면 `#spring` 한 단어) | | |

## 5. 글 검색 단계 (`PostSearchRepository`, research R7·R8)

```text
단어 w:  T(w) = title ILIKE %w%      G(w) = 태그 이름 LIKE %lower(w)%      C(w) = content_md ILIKE %w% (3글자 이상만)
cond1 = ∧ T(w)        cond2 = ∧ (T(w) ∨ G(w))        cond3 = ∧ (T(w) ∨ G(w) ∨ C(w))
관련도순: ① cond1  ② cond2 ∧ ¬cond1  ③ cond3 ∧ ¬cond2      최신순: cond3
단계 안 정렬: first_public_at DESC, id DESC
```

- 단계마다 ① 최근 3,000개 창 안에서 10개 → ② 모자라고 창이 가득 찼으면 가장 긴 단어의 후보(제목 GIN ∪ 태그 ∪ 본문 GIN — 단계에 필요한 것만)에서 전체 조건 10개.
- 한 페이지 = 단계를 이어 9개 + 다음 확인 1개. `nextCursor = [9번째의 단계, first_public_at, id]`.
- 커서 목록 구분 `search:{sort}:{blogOwnerId|-}:{지문}`.
- 블로그 안 검색: 공용 조건에 `p.author_id = :authorId`.
- 한 페이지 SQL: 단계 수 × (1~2) + 카드 1 + 주변 문장 재료 1.

## 6. 주변 문장 (`SnippetBuilder`, research R9)

| 경우 | `text` | `marks` |
|---|---|---|
| 본문에서 찾음 | 처음 나온 곳 앞뒤 40 코드 포인트(원문 그대로, 줄바꿈은 공백), 잘린 쪽에 `…` | 잘라 낸 글자 안 모든 단어 위치 |
| 본문에 없고 제목에서 찾음 | 제목 기준 같은 방식 | 제목 안 위치 |
| 둘 다 없음(태그로만 찾음) | 005 `excerpt`(없으면 빈 문자열) | `[]` |
| 본문에 `<script>alert(1)</script>` | 글자 그대로 | 화면이 텍스트 노드로 그려 실행되지 않음 |

## 7. 사람 검색 (`PeopleSearchRepository`, research R10)

| 입력 | 덩어리 | 결과 |
|---|---|---|
| `김민서` | `김민서` | 닉네임·주소 부분 일치 |
| `김 민서` | `김민서` | 같음 |
| `@kim7550` | `kim7550` | 주소가 정확히 `kim7550`인 회원 먼저 |
| `김` | — | 400 `SEARCH_QUERY_TOO_SHORT` |

조건: `m.withdrawn_at IS NULL AND m.deleted_at IS NULL`, 정렬 `exact DESC, lower(nickname), id`, 최대 20.

## 8. sitemap (`SitemapService`, research R13)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>{base}/</loc></url>
  <url><loc>{base}/@{handle}/posts/{id}</loc><lastmod>{edited_at ?? first_public_at, ISO-8601 UTC}</lastmod></url>
  …
  <url><loc>{base}/@{handle}</loc><lastmod>{그 블로그 공개 글의 최신 lastmod}</lastmod></url>
  …
</urlset>
```

- `{base}` = `blog.site.base-url`(005). 글은 `p.id > :after ORDER BY p.id LIMIT 1000` 반복, 블로그는 `GROUP BY m.handle` 1번.
- 50,000개를 넘으면 앞 50,000개 + WARN.

## 9. 다른 기능과의 약속

| 기능 | 약속 |
|---|---|
| 004 | `VisibilityFilter.forViewer(Viewer.anonymous(), authorId)` 하나로 노출 조건. 바꾸면 트렌딩·검색·sitemap이 함께 바뀐다 |
| 005 | `PostCardQueryRepository.findCardsByIds`를 이 기능이 더한다. `HomePage`·`BlogPage`·`PageShellController`에 자리만 더한다. `blog.list.page-size` 공유 |
| 007 | `comment.deleted_at`·`hidden_at`의 뜻이 바뀌면 알린다(트렌딩 작성자 수) |
| 008 | 태그 이름은 소문자 정규화돼 있다(`ck_tag_name`). 화면 `normalizeTag.ts`로 `#태그` 이동 판정. `/tags/{이름}` 경로 |
| 009 | `like_count`·`view_count`는 중복·자기 반응을 거른 누적값. `VisitorKeyResolver`를 공개 Service로 둔다 |
| 014·015 | 숨김(`hidden_at`)·탈퇴 유예(`withdrawn_at`)는 공용 조건으로 자동 제외 — 이 기능은 이벤트를 구독하지 않는다 |
