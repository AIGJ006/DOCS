# Research: 트렌딩·검색

**Feature**: 012-trending-search | **Date**: 2026-10-08

spec Implementation Notes와 원문(32·33·51)에서 정한 것은 "확정", 이 계획이 새로 정한 것은 "제안"으로 표시한다.

## R1. 스키마 (확정)

- **Decision**: V1 그대로. 마이그레이션 없음.
  - `CREATE EXTENSION IF NOT EXISTS pg_trgm`(V1 7행)과 GIN `ix_post_title_trgm (title)`, `ix_post_content_trgm (content_md)`, `ix_member_nickname_trgm`, `ix_member_handle_trgm`.
  - 트렌딩 후보 범위: `ix_post_feed (first_public_at DESC, id DESC) WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL`.
  - 글마다 댓글 작성자 수: `uq_comment_post_id (post_id, id)`(답글 FK용 UNIQUE)가 `post_id` 범위 조회를 맡는다.
  - 태그: `uq_tag_name`, `ix_tag_name_prefix (name varchar_pattern_ops)`, `ix_post_tag_tag (tag_id, post_id)`.
- 32 ERD 제안 `ix_comment_post_author ON comment (post_id, author_id) WHERE deleted_at IS NULL`은 넣지 않는다(spec Implementation Notes "지금은 넣지 않음"). `TrendingSnapshotIT`의 계산 시간이 1초를 넘으면 V3 이후 마이그레이션 후보로 올린다(tasks T046).
- **Rationale**: 헌법 I, 51 §3·§4.

## R2. 모듈 배치 (확정)

- **Decision**: 모두 `discovery`. 노출 조건은 004 `VisibilityFilter.forViewer(Viewer.anonymous(), authorId)` 하나만 쓴다 — 트렌딩·검색·sitemap은 보는 사람과 관계없이 "남에게 보이는 글"이다(블로그 주인이 자기 블로그에서 검색해도 공개 글만, 06 V-8).
- 카드: 005 `PostCardQueryRepository`에 `findCardsByIds(List<Long> ids)`(같은 SELECT + `AND p.id = ANY(:ids)`, 순서는 호출한 쪽이 맞춤)를 더하고 `PostCardAssembler`로 `PostCardView`를 만든다.
- **Rationale**: 02 §3(탐색은 discovery), 005·010과 같은 카드·노출 조건.

## R3. 트렌딩 계산 SQL (확정 + Clarifications Q1)

- **Decision**: `TrendingRepository.compute(now, limit)` — 스냅샷 작업은 `limit = 100`, 즉시 계산 대체는 `limit = 9`.
  ```sql
  WITH candidate AS (
    SELECT p.id, p.author_id, p.like_count, p.view_count, p.first_public_at,
           (SELECT count(DISTINCT c.author_id) FROM comment c
             WHERE c.post_id = p.id AND c.author_id <> p.author_id
               AND c.deleted_at IS NULL AND c.hidden_at IS NULL) AS commenters
      FROM post p
      JOIN member m ON m.id = p.author_id
     WHERE <VisibilityFilter(비회원)>
       AND p.first_public_at > :since
  ), scored AS (
    SELECT *, (:wLike * like_count + :wComment * commenters + :wView * view_count)
              / power(extract(epoch FROM (:now - first_public_at)) / 3600.0 + :offsetHours, :gravity) AS score
      FROM candidate
     WHERE like_count > 0 OR commenters > 0
  ), ranked AS (
    SELECT *, row_number() OVER (PARTITION BY author_id
                                 ORDER BY score DESC, first_public_at DESC, id DESC) AS rn
      FROM scored
  )
  SELECT id FROM ranked
   WHERE rn <= :perAuthor
   ORDER BY score DESC, first_public_at DESC, id DESC
   LIMIT :limit
  ```
  - `:since = now − 7일`, 가중치 3·2·0.1, 보정 2시간, 지수 1.5(FR-004·FR-006·FR-007·FR-008).
  - 좋아요·조회 수는 `post.like_count`·`view_count`(009가 중복·자기 반응을 거른 누적값).
  - 숨긴 댓글·삭제된 자리 제외(`hidden_at IS NULL`, `deleted_at IS NULL` — Clarifications Q1). 같은 사람 여러 댓글은 1명.
  - 댓글 작성자가 탈퇴 유예여도 센다(원문에 제외 규칙 없음).
- **Rationale**: 32 §3-1, T-2~T-6.
- **Alternatives considered**: 댓글 작성자 수를 `LEFT JOIN … GROUP BY`로 — 7일 후보가 적어 상관 서브쿼리와 차이가 없고, 작성자당 순위 창과 섞이면 SQL이 길어진다.

## R4. 스냅샷 저장 (확정 + 제안)

- **Decision**: `TrendingSnapshotJob` — `@Scheduled(cron = "${blog.trending.refresh-cron}")`(기본 `0 */10 * * * *`), `@SchedulerLock(name = "trendingSnapshot", lockAtMostFor = "PT9M")`. 앱이 뜬 직후 한 번 더 실행한다(`ApplicationReadyEvent`, 같은 잠금).
  1. `ids = TrendingRepository.compute(now, 100)`
  2. 스냅샷 ID `yyyyMMddHHmm`(UTC, `now` 기준)
  3. Redis(파이프라인): `DEL trending:{id}` → (있으면) `RPUSH trending:{id} …ids` → `EXPIRE trending:{id} 1800` → `SET trending:{id}:count {n} EX 1800` → `SET trending:current {id}`
  - 0개여도 `count = 0`을 쓰고 `current`를 바꾼다(빈 순위와 만료를 구분, FR-015).
  - Redis 장애면 WARN 후 끝(다음 10분에 다시). 이전 스냅샷은 TTL까지 쓰인다.
- **Rationale**: 32 §3-2 T-7. `:count` 키는 빈 목록을 Redis List로 표현할 수 없어서 더했다(제안).

## R5. 트렌딩 읽기 (확정 + 제안)

- **Decision**: `GET /api/posts/trending?cursor=` (`size`는 받지 않음 — 005 `blog.list.page-size` 9 고정)
  1. 커서가 있으면 `snap`·`pos`를 꺼낸다(`ListScope` `trending`, 키 `[snapshotId, position]`, 다른 목록 커서는 400 `INVALID_CURSOR`). 없으면 `GET trending:current`, `pos = 0`.
  2. `GET trending:{snap}:count`가 없으면: 커서가 있었으면 **410 `SNAPSHOT_EXPIRED`**("순위가 새로 바뀌었어요"), 없었으면 3-b.
  3. a. `LRANGE trending:{snap} pos pos+17`(18개씩) → `findCardsByIds` → 번호 순서대로 지금 볼 수 있는 것만 담는다. 10개(9 + 다음 확인 1)가 될 때까지 또는 `count` 끝까지 반복. `nextCursor`는 9번째 카드 다음 위치(10번째가 있을 때만).
     b. 대체 경로(Redis 장애, `current` 없음, 첫 요청의 `count` 없음): `TrendingRepository.compute(now, 9)` → 카드 → `nextCursor: null`(FR-013).
  - 응답 `{items, nextCursor}`는 005 `PostCardPage`와 같은 모양.
- 건너뛰기(FR-012): 스냅샷 뒤 비공개·휴지통·숨김·작성자 유예가 된 글은 카드 SQL의 공용 조건이 뺀다.
- **Rationale**: 32 §4, 10 §7, O8.
- **Alternatives considered**: 원문 커서 `{스냅샷ID}:{위치}` 그대로 — 2026-10-07 회의 O8로 모든 커서는 불투명 Base64URL + 목록 구분 필드.

## R6. 검색어 처리 (확정)

- **Decision**: `SearchQueryParser.parse(String raw)` → `SearchQuery(words, hasTwoCharWord)`:
  1. `null`·빈 문자열 → 단어 없음
  2. `Normalizer.normalize(raw, NFC)`, `strip()`
  3. 앞 50 코드 포인트(`blog.search.max-length`)
  4. `\s+`(유니코드 공백 포함 — `Pattern.UNICODE_CHARACTER_CLASS`)로 나눔
  5. 1 코드 포인트 단어 버림
  6. 앞 5단어(`blog.search.max-words`)
  - 단어 없음 → 400 `SEARCH_QUERY_TOO_SHORT`("두 글자 이상 입력해 주세요", 제안 코드). 화면은 요청 전에 같은 규칙으로 막는다.
  - `SearchWord(text, length, inContent = length >= 3)`.
  - 2글자 단어가 하나라도 있으면 응답 `notice: "TWO_CHAR_TITLE_TAG_ONLY"`(FR-024).
- `#태그` 이동(FR-025)은 화면이 008 `normalizeTag.ts`로 판정한다: 입력 전체가 `#`으로 시작하고 공백이 없고 정규화가 통과하면 `/tags/{이름}`으로 이동, 아니면 보통 검색(서버는 `#`을 글자로 찾음).
- **Rationale**: 33 §2, Q-2·Q-3, 22 T-11.

## R7. 단계 조건 (확정)

- **Decision**: 단어 `w`마다 `pat = '%' || escapeLike(w) || '%'`, `tagPat = '%' || escapeLike(w.toLowerCase(Locale.ROOT)) || '%'`(`escapeLike`: `\` → `\\`, `%` → `\%`, `_` → `\_`).
  - `T(w)` = `p.title ILIKE :pat ESCAPE '\'`
  - `G(w)` = `EXISTS (SELECT 1 FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = p.id AND t.name LIKE :tagPat ESCAPE '\')`
  - `C(w)` = `p.content_md ILIKE :pat ESCAPE '\'` — 3글자 이상만
  - `cond1 = AND T(w)`, `cond2 = AND (T(w) OR G(w))`, `cond3 = AND (T(w) OR G(w) [OR C(w)])`
  - 관련도순 단계: ① `cond1` ② `cond2 AND NOT cond1` ③ `cond3 AND NOT cond2`. 최신순: `cond3` 하나(단계 값 0).
  - 본문은 `content_md` 원문(코드 블록 포함, FR-023).
- **Rationale**: 33 §2·§3-1, FR-019~FR-023·FR-027.

## R8. 단계 실행과 이어 보기 (확정 + 제안)

- **Decision**: `PostSearchService.page(query, sort, blogOwnerId, cursor)`:
  - 필요한 수 `need = 10`(9 + 다음 확인 1). 커서의 단계부터 시작하고, 그 단계에서는 `(p.first_public_at, p.id) < (:t, :id)`, 다음 단계부터는 처음부터.
  - 단계마다 ① 최근창:
    ```sql
    WITH recent AS (
      SELECT p.id FROM post p JOIN member m ON m.id = p.author_id
       WHERE <VisibilityFilter(비회원, blogOwnerId)>
       ORDER BY p.first_public_at DESC, p.id DESC
       LIMIT :recentWindow
    )
    SELECT p.id, p.first_public_at FROM post p JOIN recent r ON r.id = p.id
     WHERE <단계 조건> [AND 커서]
     ORDER BY p.first_public_at DESC, p.id DESC
     LIMIT :need
    ```
    ② ①이 `need`보다 적고 최근창이 가득 찼으면(더 오래된 글이 있음) 후보 조회:
    ```sql
    WITH cand AS (
      SELECT id FROM post WHERE title ILIKE :longPat ESCAPE '\'
      UNION SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id = pt.tag_id
             WHERE t.name LIKE :longTagPat ESCAPE '\'                -- 단계 ②·③·최신순
      UNION SELECT id FROM post WHERE content_md ILIKE :longPat ESCAPE '\'  -- 단계 ③·최신순, 가장 긴 단어가 3글자 이상
    )
    SELECT p.id, p.first_public_at FROM post p JOIN cand c ON c.id = p.id
      JOIN member m ON m.id = p.author_id
     WHERE <VisibilityFilter(비회원, blogOwnerId)> AND <단계 조건> [AND 커서]
     ORDER BY p.first_public_at DESC, p.id DESC
     LIMIT :need
    ```
    `:longPat`은 가장 긴 단어(같으면 앞 단어). 결과는 ②가 ①을 포함하므로 ②만 쓴다.
  - 단계에서 모자라면 다음 단계로 이어 `need`를 채운다(FR-028). 모은 행 중 앞 9개가 이번 페이지, 10번째가 있으면 `nextCursor = [9번째의 단계, first_public_at, id]`.
  - 9개 번호로 `findCardsByIds` 1번 + 주변 문장 재료 `SELECT id, title, content_md FROM post WHERE id = ANY(:ids)` 1번.
  - 태그 `EXISTS`를 본문 `ILIKE`와 `OR`로 섞으면 본문 GIN을 못 써 1,129ms(33 §8) — 후보를 `UNION`으로 나눈 까닭이다.
  - 커서 목록 구분: `search:{relevance|latest}:{blogOwnerId|-}:{지문}` — 지문은 정규화한 단어들을 `\u0000`으로 이은 SHA-256 앞 16자(16진). 다른 검색어·정렬·블로그의 커서는 400 `INVALID_CURSOR`(제안).
- **Rationale**: 33 §4·§8, FR-028·FR-030.

## R9. 주변 문장 (확정 + Clarifications Q4 + 제안)

- **Decision**: `SnippetBuilder.build(title, contentMd, words)` → `Snippet(text, marks)`:
  1. 본문에서 각 단어를 대소문자 무시(`Pattern.quote` + `CASE_INSENSITIVE | UNICODE_CASE`)로 찾아 가장 앞 위치. 없으면 제목에서. 둘 다 없으면 005 `excerpt` 그대로(강조 없음).
  2. 찾은 곳의 시작 앞 40 코드 포인트 ~ 끝 뒤 40 코드 포인트(`blog.search.snippet-radius`)를 원문 그대로 자른다(서식 기호를 지우지 않음). 서로게이트 쌍을 가르지 않는다. 줄바꿈은 공백 하나로.
  3. 앞이 잘렸으면 맨 앞, 뒤가 잘렸으면 맨 끝에 `…`.
  4. `marks`: 잘라 낸 글자 안에서 모든 단어의 모든 위치 `[start, end)`(UTF-16 단위 — JS 문자열 색인과 같음), 겹치면 합친다.
  - 응답은 `snippet: {text, marks}`이고 HTML 문자열이 없다. 화면 `SnippetText`가 `text`를 범위로 잘라 텍스트 노드와 `<mark>`로 그린다 — 이스케이프는 React가 하고 강조는 그 뒤다(FR-034, SC-005).
- **Rationale**: 33 §5, Q-6, 헌법 IV.
- **Alternatives considered**: 원문 `snippetHtml`(서버가 이스케이프 후 `<mark>` 삽입) — 화면이 `dangerouslySetInnerHTML`을 써야 하고, 서버 이스케이프가 한 번만 빠져도 스크립트가 실행된다.

## R10. 사람 검색 (Clarifications Q3 + 제안)

- **Decision**: `GET /api/search/people?q=` → `{items: [{handle, nickname, profileImageUrl, bioFirstLine}]}`(최대 20, 커서 없음).
  - 검색어: NFC → `strip` → 50자 → 모든 공백 제거 → 맨 앞 `@` 하나 제거 → 2 코드 포인트 미만이면 400 `SEARCH_QUERY_TOO_SHORT`.
  - SQL:
    ```sql
    SELECT m.handle, m.nickname, m.bio, COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key,
           (lower(m.nickname) = lower(:token) OR m.handle = lower(:token)) AS exact
      FROM member m
      LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
           AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
     WHERE m.withdrawn_at IS NULL AND m.deleted_at IS NULL
       AND (m.nickname ILIKE :pat ESCAPE '\' OR m.handle ILIKE :pat ESCAPE '\')
     ORDER BY exact DESC, lower(m.nickname), m.id
     LIMIT :limit
    ```
  - 탈퇴 신청·익명 처리 제외(FR-036). 정지 회원은 블로그가 보이므로(001 `findReadableBlogOwner`와 같은 기준) 나온다.
  - 정확히 일치 다음 순서는 닉네임 가나다 → 회원 번호(원문에 없음, 제안).
  - `bioFirstLine`: 소개의 첫 줄(010 목록과 같은 규칙).
  - 사람 탭은 공개 글 수와 관계없이 활동 회원을 보여 준다(블로그 페이지는 글이 없어도 열림).
- **Rationale**: 33 §5, 09 N-2(닉네임 공백 불가).

## R11. 블로그 안 검색 (확정)

- **Decision**: `GET /api/search/posts?q=&sort=&cursor=&blog={handle}` — `blog`가 있으면 001 `MemberQueryService.findReadableBlogOwner(normalizeHandle(blog))`가 비면 404(없음·유예·익명 처리, 같은 본문). 있으면 R8의 `VisibilityFilter(…, ownerId)`(`AND p.author_id = :authorId`).
  - 화면 주소 `/@{handle}?q=…`. 블로그 페이지가 `q`가 있으면 글 목록 대신 검색 결과를 보여 준다.
- **Rationale**: 33 Q-7, FR-031.

## R12. 요청 제한과 기록 (확정 + 제안)

- **Decision**: 글 검색·사람 검색 합쳐 `ratelimit:search:{visitorKey}` 1분 30번(`blog.search.rate-limit`), 001 `RateLimiter.acquireOrThrow` → 429 `TOO_MANY_REQUESTS` + `Retry-After`. 방문자 키는 009 `VisitorKeyResolver`(`m:{회원}`/`v:{vid}`/`h:{해시}`). Redis 장애면 통과(001 동작).
  - 판정 순서: 403(001 게이트 — 탈퇴 유예) → 404(블로그 안 검색의 블로그) → 400(검색어·커서) → 429(맨 끝, README 2026-10-07).
  - 009 머지 전에는 제한을 붙이지 않는다(tasks T036).
  - 로그: `search posts len={검색어 코드 포인트 수} words={단어 수} stage={마지막 단계} took={ms}` INFO. 검색어 원문·회원 번호는 남기지 않는다(FR-039). 001 `SensitiveParamMasking`에 `q`를 더해 접근 로그에서도 가린다.
- **Rationale**: 33 §5 Q-8, 31 §2-1, 02 §2-1 H8.

## R13. sitemap (Clarifications Q2 + 제안)

- **Decision**: `GET /sitemap.xml` → `application/xml;charset=UTF-8`, `Cache-Control: no-cache`, `StreamingResponseBody`.
  - 글: 공용 조건(비회원)으로 `SELECT p.id, m.handle, p.first_public_at, p.edited_at FROM post p JOIN member m … WHERE <VisibilityFilter> AND p.id > :after ORDER BY p.id LIMIT 1000`을 반복. `<loc>{blog.site.base-url}/@{handle}/posts/{id}</loc><lastmod>{edited_at ?? first_public_at}</lastmod>`.
  - 블로그: `SELECT m.handle, max(coalesce(p.edited_at, p.first_public_at)) FROM post p JOIN member m … WHERE <VisibilityFilter> GROUP BY m.handle ORDER BY m.handle` → `<loc>{base}/@{handle}</loc>`. 공개 글이 1개 이상인 활동 회원만 자동으로 남는다.
  - 주소는 XML 이스케이프한다. 첫 화면 `/`도 넣는다.
  - URL 50,000개를 넘으면(sitemap 규약 한도) 이 단계에서는 앞 50,000개만 쓰고 WARN — 색인 파일 나누기는 그때 정한다(글 1만 건 규모에서는 해당 없음).
  - 친구 공개 글은 공용 조건이 뺀다. 캐시하지 않으므로 삭제·비공개·숨김·작성자 유예가 다음 요청부터 빠진다(13 §5 C-POST-5 기준 1).
- `robots.txt`는 이 기능 범위가 아니다(배포 때 검색 엔진 콘솔에 sitemap 주소를 등록).
- **Rationale**: 02 SEO 행, 06 §3 노출 표, 20 §5 구독 표.

## R14. 이벤트 (제안)

- **Decision**: 구독하지 않는다.
  | spec Implementation Notes의 이벤트 | 필요 없는 이유 |
  |---|---|
  | `PostWentPublic`·`PostEdited`·`PostVisibilityChanged` | 검색은 V1 trigram 인덱스를 DB가 갱신. 트렌딩은 10분마다 다시 계산 |
  | `PostTrashed`·`PostRestored`·`PostPurged`·`ContentHidden`·`ContentUnhidden` | 트렌딩 읽기·검색·sitemap이 요청 때 공용 조건으로 거른다 |
  | `MemberWithdrawn`·`MemberRestored` | 같은 이유(공용 조건의 `m.withdrawn_at IS NULL`) |
- 015 data-model 이벤트 표의 "012" 구독자 표시는 Tier B/C analyze에서 고쳤다(ANALYSIS-tier-bc).
- **Rationale**: Clarifications Q2 근거("sitemap을 캐시하지 않으면 012가 구독할 사건이 거의 없다"), 헌법 V(이벤트 유실이 결과를 틀리게 하지 않음).

## R15. 화면 (확정 + 제안)

- **Decision**:
  - 홈(005 `HomePage`): 상단 `[최신] [트렌딩]` 탭(`role="tablist"`), `/?tab=trending`이면 트렌딩. 트렌딩 탭 아래 "최근 7일 동안 반응이 많은 글 · 10분마다 갱신". 카드·9개·[더 보기]·복원(30분)은 005 `useCursorList({listKey: 'trending'})`. 빈 상태 "아직 트렌딩 글이 없어요" + [최신 글 보기]. 410이면 "순위가 새로 바뀌었어요" 안내(상단 알림 줄) 후 처음부터 다시 불러온다. 순위 숫자는 없다.
  - 머리말 검색창(001 `SessionBar` 자리, 모든 페이지): 입력 `type="search"`, 이름 "검색". 제출 때 `#태그` 판정(R6) → `/tags/{이름}`, 아니면 `/search?q=…`.
  - `/search`(`SearchPage`): `[글] [사람]` 탭(`tab=posts|people`), 글 탭 정렬 `[관련도순] [최신순]`(`sort`), 2글자 안내, 결과 없음 "'{검색어}'에 대한 글이 없어요"(2글자 안내와 함께), 단어 없음 "두 글자 이상 입력해 주세요"(요청하지 않음), 429 "잠시 후 다시 시도해 주세요". 카드는 005 `PostCard`에 미리보기 자리만 `SnippetText`로. 사람 결과는 사진·닉네임·`@주소`·소개 첫 줄, 누르면 블로그. 복원 키 `search:{tab}:{sort}:{q}`.
  - 블로그(005 `BlogPage`): 머리말에 "이 블로그에서 검색" 입력, `?q=`가 있으면 목록 자리에 검색 결과(같은 카드·정렬 탭).
  - 셸: `/search`와 `?q=`가 있는 `/@{handle}`의 첫 응답은 005 `LinkPreviewMeta`의 `noindex = true`(FR-038). 005 `PageShellController`에 `/search`를 더한다.
- **Rationale**: 32 §5, 33 §5, FR-001·FR-014~FR-016·FR-032~FR-038.

## R16. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/search.csv`(004 하네스 형식, owner `012`). 대상은 글 상태(`PUBLISHED_PUBLIC`·`PRIVATE`·`DRAFT`·`TRASHED`·`HIDDEN`·`AUTHOR_WITHDRAWN`, 적용자는 `FRIENDS`).
  | action | 기대 |
  |---|---|
  | `trending.list` | 모든 행위자(비회원·인증 전·회원·작성자·관리자·정지 남은 세션) 200, `PUBLISHED_PUBLIC`만 포함. 탈퇴 유예 행위자 403 |
  | `search.posts` | 위와 같음. 작성자 본인·관리자도 비공개·숨김 글 0 |
  | `search.posts.blog` | 대상 블로그가 정상이면 위와 같음, 작성자 유예 블로그는 404 |
  | `search.people` | 탈퇴 유예 대상 회원 0 |
  | `sitemap` | 모든 행위자 200(게이트 밖 경로), `PUBLISHED_PUBLIC` 주소만 |
- **Rationale**: 헌법 III, SC-003, 006 plan `TrashedPostPermissionMatrixIT`의 검색·sitemap 행.

## R17. 설정값 (제안)

```yaml
blog:
  trending:
    window: 7d
    refresh-cron: "0 */10 * * * *"
    snapshot-size: 100
    snapshot-ttl: 30m
    per-author: 3
    weight-like: 3
    weight-commenter: 2
    weight-view: 0.1
    offset-hours: 2
    gravity: 1.5
    read-chunk: 18          # 건너뛰기용으로 한 번에 읽는 번호 수
  search:
    max-length: 50
    max-words: 5
    recent-window: 3000     # 33 §4 (결과 순서와 무관)
    snippet-radius: 40
    people-limit: 20
    rate-limit:
      limit: 30
      window: 1m
```

- 카드 수는 005 `blog.list.page-size`(9)를 함께 쓴다.
