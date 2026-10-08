# Research: 태그와 태그별 글 목록

**Feature**: 008-tag | **Date**: 2026-10-08

표기: (확정) = spec·Clarifications·Tier A 결정에 이미 있음, (제안) = 이 plan이 정한 기본안(팀 확인 필요), (미결) = 팀 결정 대기 — 기본안으로 진행.

---

## R1. 정규화 클래스의 모양 (확정 + 제안)

- **Decision**: `tag.domain.TagNormalizer`(Spring Bean, `BannedWordFilter` 주입)를 하나 둔다. 진입점은 둘이다.
  - `TagNormalization normalize(String raw)`: ①~⑨ 전체. 발행·AI 추천(013)이 쓴다.
  - `Optional<String> normalizeQuery(String raw)`: ①~⑦ + 허용 문자 검사만(⑨ 금칙어 없음, 길이는 30자로 자름). 자동완성 검색어·태그 주소·블로그 필터·검색창 `#태그`(012)가 쓴다. 실패하면 빈 값.
  - 결과 `TagNormalization`은 `Accepted(String name)` | `Rejected(TagReasonCode code)`의 sealed 인터페이스다. 예외를 던지지 않는다 — 발행은 칸마다 결과를 모아야 하기 때문이다(FR-013).
  - 판정 우선순위(한 칸에 문제가 여럿이면 하나만): `INVALID_TAG`(허용 밖 문자·한글/영문/숫자 없음·빈 값) → `TAG_TOO_LONG`(코드 포인트 30 초과) → `TAG_BANNED_WORD`.
- **Rationale**: 원문 22 §2의 `TagRejectedException`은 한 번에 하나만 던져 "모든 오류를 한 번에"(FR-013)를 만들 때 try/catch를 칸마다 써야 한다. 결과 값으로 돌려주면 `PublishValidator`가 단순해진다. 검색어용 진입점을 따로 두면 자동완성에 금칙어 검사를 하지 않는다는 규칙(Edge Cases)이 코드에서 드러난다.
- **Alternatives considered**: 정적 유틸 클래스 — 금칙어 목록이 설정 파일(Bean)이라 주입이 필요해 제외. 예외 방식 — 위 이유로 제외.

## R2. 보이지 않는 글자 목록과 공백 (제안)

- **Decision**:
  - ② 단계 제거 대상은 002 `TitleNormalizer.isRemoved`와 같은 목록(U+200B~200F, U+2060~2069, U+FEFF, U+202A~202E, ISO 제어 문자)이다. 이 판정을 `shared.text.InvisibleCharacters.isInvisible(int cp)`로 옮기고 `TitleNormalizer`와 `TagNormalizer`가 함께 쓴다(tag → post 의존을 만들지 않으려고).
  - 원문 순서대로 ②(제어 문자 제거)가 ⑥(공백 묶음 → `-`)보다 먼저다. 그래서 탭·줄바꿈은 `-`가 아니라 지워진다(`a\tb` → `ab`). 입력칸이 한 줄이라 실제로 생기지 않는다.
  - ⑥의 "공백"은 NFKC 뒤의 `\p{IsWhite_Space}`(U+0020, U+00A0 등)다. NFKC가 U+3000(전각 공백)을 U+0020으로 바꾼다.
  - ③·④의 앞뒤 공백 제거는 `String.strip()`(유니코드 공백 기준).
- **Rationale**: 같은 화면에서 제목과 태그가 다른 목록으로 글자를 지우면 사용자가 헷갈린다. 12 §7-4가 한 목록을 정해 두었다.
- **Alternatives considered**: `TagNormalizer`에 목록 복사 — 두 곳이 갈라질 위험. `Character.getType == FORMAT` 전부 제거 — 한글 자모 조합 등에 영향이 있을 수 있어 12 §7-4 목록만.

## R3. 002 임시 `TagService` 교체와 오류 코드 위치 (제안)

- **Decision**:
  - `TagService`의 공개 메서드 이름과 서명은 그대로 둔다(`normalizeAll(List<String>)`, `validate(List<String>)`, `replacePostTags(long, List<String>)`, `tagNamesOf(long)`). 정적 `normalize(String)`은 지운다(호출처: `TagService` 내부뿐 — tasks에서 grep으로 확인).
  - `normalizeAll`은 `Accepted`인 이름만 입력 순서대로 중복 제거해 돌려준다. `validate`는 `Rejected`인 칸마다 `FieldError("tags[i]", code, message)`를 돌려준다. `i`는 화면이 보낸 원래 칸 번호다(002 T122 점검표).
  - 거부 코드는 `tag.domain.TagReasonCode`(`INVALID_TAG` "쓸 수 없는 글자가 있어요", `TAG_TOO_LONG` "태그는 30자까지 쓸 수 있어요", `TAG_BANNED_WORD` "쓸 수 없는 단어가 들어 있어요")로 옮긴다. `PostReasonCode.INVALID_TAG`는 지운다. 응답의 `code` 문자열은 같아서 화면·계약은 바뀌지 않는다. `TOO_MANY_TAGS`는 개수 규칙이 발행 설정이므로 `PostReasonCode`에 남긴다.
  - `PublishValidator`는 개수 초과일 때도 칸별 오류를 함께 모은다(지금은 개수 초과면 칸 검사를 건너뜀). 개수는 `Accepted` 이름의 중복 제거 후 개수로 센다. 그래야 "11개 + 이모지 태그"를 한 번에 알 수 있다(SC-006).
- **Rationale**: 지금 `TagService`가 `post.domain.PostReasonCode`를 import하고 post가 `TagService`를 import해 순환이 있다. 오류 코드를 tag 모듈로 옮기면 의존이 post → tag 한 방향이 된다.
- **Alternatives considered**: `TagService` 이름을 바꾸고 새 클래스 — 002·005 호출처(`PublishService`·`PublishValidator`·`EditorQueryService`·`PostReadingPorts`)를 모두 고쳐야 해서 제외.

## R4. 예시 표를 테스트 원본 하나로 (제안)

- **Decision**: 22 §2-1 예시와 spec US1 #1·#2·#4의 입력을 `backend/src/test/resources/tag/normalization-cases.csv`(열: `input`, `expected`, `code`, `note`)로 둔다. 서버는 `@CsvFileSource`로, 화면은 Vitest에서 `fs.readFileSync`로 같은 파일을 읽어 `normalizeTag.ts`를 검사한다(금칙어 행은 화면 테스트에서 건너뜀). 전각·보이지 않는 글자는 CSV에 `\uXXXX` 표기로 적고 두 테스트가 풀어 쓴다.
- **Rationale**: SC-001(예시 100% 일치)을 두 구현이 같은 표로 증명한다. 표를 고치면 양쪽이 함께 깨진다.
- **Alternatives considered**: 표를 양쪽 테스트에 복사 — 갈라질 위험.

## R5. 목록 조건 (확정)

- **Decision**:
  - 태그별 목록·태그 머리말 글 수·전체 태그 목록·자동완성 공개 글 수: `VisibilityFilter.forViewer(Viewer.anonymous(), null)`. 보는 사람과 상관없이 전체 공개 글만이다(FR-022 "친구 공개 적용자라도 전체 공개만"). 로그인한 사람에게도 익명 조건을 쓰는 이유를 코드 주석에 남긴다.
  - 블로그 태그 줄·블로그 `?tag=` 목록: 블로그 목록과 같은 `forViewer(viewer, ownerId)`(FR-037, FRIENDS 적용자는 친구에게 친구 글 포함).
  - 작성자 본인 예외 없음(06 V-8): 블로그 주인이 봐도 자기 비공개 글의 태그는 태그 줄에 없다. 자동완성의 "내 태그"만 예외(FR-033).
- **Rationale**: 22 §5·§6 SQL에 빠진 숨김 제외는 H1 공용 조건을 따른다(spec Assumptions). 조건 문구를 한 곳에서만 만들어야 부분 인덱스(`ix_post_feed`·`ix_post_blog`) 술어와 어긋나지 않는다.
- **Alternatives considered**: 태그 전용 WHERE — 004 원칙(목록마다 직접 쓰지 않음) 위반.

## R6. 태그별 카드 목록 SQL (제안)

- **Decision**: 005 `PostCardQueryRepository.cardQuery`에 `CardFilter(Long authorId, Long tagId)`를 받게 넓히고 `tagId`가 있으면 `AND EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = :tagId)`를 더한다. 정렬·커서·`page-size + 1` 조회는 그대로다. 태그 번호는 먼저 `SELECT id FROM tag WHERE name = :name`(유일 인덱스)으로 찾고, 없으면 카드 SQL 없이 빈 페이지를 돌려준다(응답은 "있는데 공개 글 0개"와 같음, SC-005).
- **Rationale**: 1만 건 기준에서 플래너는 `ix_post_tag_tag (tag_id, post_id)`로 그 태그의 글 번호를 모은 뒤 `post` PK로 조회·정렬한다(태그 하나 최대 수천 건). `post_tag`에 `first_public_at` 복사 컬럼은 두지 않는다(spec Implementation Notes "1만 건 기준 불필요"). `TagPerformanceIT`가 EXPLAIN과 시간을 기록한다.
- **Alternatives considered**: `JOIN post_tag` — 같은 결과지만 한 글에 같은 태그가 둘일 수 없어(PK) 차이 없음. 가독성으로 EXISTS. 복사 컬럼 + 인덱스 `(tag_id, first_public_at DESC, post_id DESC)` — 공개 상태 변경마다 동기화가 필요해 측정 실패 때만.

## R7. API 경로 (제안)

- **Decision**:
  - `GET /api/tags?limit=` → 전체 태그 목록. `limit`은 무시하고 `blog.tag.top-limit`(100)로 고정(원칙 VII, 005 `size`와 같은 처리).
  - `GET /api/tags/{name}/summary` → `{name, postCount}`. `GET /api/tags/{name}/posts?cursor=` → 005 `PostCardPage`.
  - `GET /api/tags/suggest?q=` → `[{name, postCount, mine}]`.
  - `GET /api/members/{handle}/tags` → `{items: [{name, postCount}], total}`. `GET /api/members/{handle}/posts?tag=&cursor=` → 005 응답 그대로.
  - API는 정규화된 이름만 받는다. `{name}`·`tag`가 정규화 결과와 다르거나 형식이 틀리면 404 `NOT_FOUND`(005 R-23 "API는 리다이렉트하지 않음"과 같다). 301은 페이지 셸만 한다.
- **Rationale**: 원문 22의 `/api/tags/suggest`와 `/api/tags/{name}`(머리말)을 함께 두면 `suggest`라는 태그 이름의 머리말을 부를 수 없다. 머리말을 두 구간 경로로 두면 겹치지 않는다. 머리말과 목록을 나누면 005 블로그 화면(머리말·목록 동시 호출)과 같은 모양이 된다.
- **Alternatives considered**: 목록 첫 페이지 응답에 머리말 포함 — 005 `PostCardPage` 스키마를 바꿔야 해서 제외.

## R8. 전체 태그 목록 계산 (확정: 매번 계산 / 제안: SQL)

- **Decision**:
  ```sql
  SELECT t.name, count(*) AS post_count
    FROM post_tag pt
    JOIN tag t ON t.id = pt.tag_id
    JOIN post p ON p.id = pt.post_id
    JOIN member m ON m.id = p.author_id
   WHERE <VisibilityFilter 공통 조건>
   GROUP BY t.name
   ORDER BY post_count DESC, t.name ASC
   LIMIT :limit
  ```
  캐시 없음. `TagPerformanceIT`가 글 1만 건(공개 8천)·태그 2천 개·연결 3만 건에서 20번 실행한 p95를 기록한다. 300ms를 넘으면 "공개에서 빠질 때 지우는 캐시"(Clarifications Q1의 B안)로 바꾸는 별도 작업을 만든다(tasks 조건부 작업).
- **Rationale**: 3만 행 해시 집계는 로컬 PostgreSQL에서 수십 ms 수준이다. 캐시 무효화를 004·006·014·015 네 기능에 흩지 않는다.
- **Alternatives considered**: Redis `tags:top` TTL 10분(원문) — Clarifications Q1에서 제외.

## R9. 페이지 셸 응답 (확정: 서버가 301·404 / 제안: 처리 순서)

- **Decision**: `PageShellController`에 더한다.
  - `GET /tags` → 200 셸 + 메타(`forTagIndex()`).
  - `GET /tags/{name}`: ① `normalizeQuery(name)` 실패(형식 틀림·빈 값) → 공통 404 화면(`NotFoundPageRenderer`) ② 결과가 `name`과 다르면 `301 /tags/{encodePathSegment(결과)}`(쿼리 유지) ③ 그 밖에는 글이 없어도 200 셸 + 메타(`forTag(name)`). 공개 글 수를 메타에 넣지 않아 SC-005(빈 태그와 비공개 전용 태그 응답 동일)를 지킨다.
  - `GET /@{handle}`에 `?tag=`가 있으면: 기존 ①(대문자 handle 301) 다음에 `normalizeQuery(tag)` 실패 → 404 화면, 결과가 다르면 `301 /@{handle}?tag={encodeURIComponent 규칙(결과)}`(다른 쿼리 값 유지), 같으면 기존 흐름.
  - `Cache-Control`은 005 `CacheControlPolicy.NO_CACHE`.
- **Rationale**: Clarifications Q2. 005가 블로그·글 상세에 같은 틀을 이미 썼다.
- **Alternatives considered**: 형식은 맞지만 정규화에서 금칙어에 걸리는 이름 — 셸은 금칙어 검사를 하지 않으므로 200 + 빈 목록(태그가 만들어질 수 없으니 항상 빈 목록).

## R10. 주소 인코딩과 보안 필터 (확정 + 제안)

- **Decision**:
  - 서버는 `UriUtils.encodePathSegment(name, UTF_8)`로 링크·`Location`을 만든다(`#` → `%23`, 한글 → `%EC…`, `+`·`.`·`_`·`-`는 그대로).
  - 화면은 `tagPath(name)` 하나를 쓴다: `encodeURIComponent(name)`에서 `%2B` → `+`만 되돌린다(서버와 같은 모양). 005 `TagList`의 `encodeURIComponent`를 이것으로 바꾼다.
  - 블로그 필터 쿼리 값은 `encodeURIComponent`(쿼리에서는 `+`가 공백이므로 `%2B` 유지).
  - 경로 매핑 `/tags/{name}`은 Spring 6+ `PathPatternParser`라 접미사 잘라내기가 없어 `node.js`가 그대로 들어온다. `.net`은 `.`·`..` 구간이 아니라 `StrictHttpFirewall`이 막지 않는다. 확인은 MockMvc가 아니라 **실제 필터 체인을 통과하는 요청**(`TestRestTemplate` 또는 `MockMvc` + `springSecurityFilterChain`에 `StrictHttpFirewall` 포함)으로 한다(SC-002). 테스트 이름: `TagPageShellIT#허용_문자_태그_주소가_모두_왕복된다`.
- **Rationale**: `encodeURIComponent`는 `+`를 `%2B`로 바꿔 서버와 모양이 다르면 같은 페이지가 두 주소가 된다. 경로에서 `+`는 공백이 아니어서 그대로 둬도 안전하다.
- **Alternatives considered**: 화면도 항상 `%2B` — 서버 정규 주소(`+`)와 다르면 301이 한 번 더 생긴다.

## R11. 자동완성 SQL과 요청 제한 (제안)

- **Decision**:
  - 검색어: `normalizeQuery(q)`. 빈 값이면 SQL 없이 `[]`. `LIKE` 패턴은 `_`·`\`를 `\`로 이스케이프한 뒤 `%`를 붙인다(`%`는 허용 문자가 아니라 들어올 수 없음).
  - SQL 한 번:
    ```sql
    WITH cand AS (SELECT id, name FROM tag WHERE name LIKE :prefix ESCAPE '\' ORDER BY name LIMIT 200),
         mine AS (SELECT DISTINCT pt.tag_id FROM post_tag pt JOIN post p ON p.id = pt.post_id
                   WHERE p.author_id = :me AND pt.tag_id IN (SELECT id FROM cand)),
         pub  AS (SELECT pt.tag_id, count(*) AS c FROM post_tag pt JOIN post p ON p.id = pt.post_id
                    JOIN member m ON m.id = p.author_id
                   WHERE <VisibilityFilter 공통 조건> AND pt.tag_id IN (SELECT id FROM cand)
                   GROUP BY pt.tag_id)
    SELECT c.name, COALESCE(pub.c, 0) AS post_count, (mine.tag_id IS NOT NULL) AS mine
      FROM cand c LEFT JOIN pub ON pub.tag_id = c.id LEFT JOIN mine ON mine.tag_id = c.id
     WHERE mine.tag_id IS NOT NULL OR pub.c > 0
     ORDER BY mine DESC, post_count DESC, c.name ASC
     LIMIT :limit
    ```
  - "내 태그"는 `author_id = :me`인 모든 글(공개 범위·상태·휴지통 무관)의 태그다(FR-033 "내 글 전부"). 남의 비공개 글에만 쓰인 태그는 `mine`·`pub` 어느 쪽에도 없어 빠진다.
  - 후보 200개 상한(`cand`)은 한 글자 검색에서 집계 범위를 묶는 안전장치다(설정값 아님, 코드 상수 + 주석). 결과 순서에 영향이 생길 수 있는 경우는 같은 첫 글자 태그가 200개를 넘을 때뿐이다.
  - 제한: `RateLimiter.acquireOrThrow("ratelimit:tag-suggest:" + memberId, 60, 1m)` → 429 `TOO_MANY_REQUESTS` + `Retry-After`. 판정 순서는 401 → 400(없음: `q`가 비어도 200 `[]`) → 429(맨 끝, 007 Q2). Redis 장애면 통과(001 `RateLimiter`).
  - `RedisGuard` OOM이 503 `AUTOSAVE_UNAVAILABLE`로 나갈 수 있다(002 T032). 화면은 code와 상관없이 실패를 무시하므로 영향 없음(미결 항목은 ANALYSIS-tier-bc에서 다룸).
- **Rationale**: 앞부분 일치는 `ix_tag_name_prefix (varchar_pattern_ops)`를 탄다. JDBC 서버 준비문이 일반 계획(generic plan)으로 바뀌면 매개변수 `LIKE`가 인덱스를 못 탈 수 있으나, 태그 표는 수천 행이라 순차 읽기도 수 ms다. `TagPerformanceIT`가 시간을 함께 잰다.
- **Alternatives considered**: `name >= :q AND name < :q || '~'` 범위 조건 — 한글 정렬 규칙에서 상한 문자를 정하기 어렵다.

## R12. 태그 입력 화면 (확정 + 제안)

- **Decision**:
  - `TagInput`(controlled): `value: string[]`(원래 입력 문자열이 아니라 **정규화된 이름**을 칩으로 저장), `max`, `errors: FieldError[]`.
    - Enter·쉼표에서 `normalizeTag(input)`이 성공하면 칩 추가, 이미 있는 이름이면 추가하지 않고 그 칩을 잠깐 강조, 실패하면 입력칸 아래 문구(서버 문구와 같은 표, `tagMessages.ts`)를 보이고 입력을 지우지 않는다.
    - 금칙어는 화면이 모르므로 서버 400 응답의 `tags[i]`가 그 칩을 오류 칩으로 만든다(칩 순서 = 보낸 순서라 번호가 맞음).
    - ×·빈 입력칸 Backspace 삭제, HTML5 드래그로 순서 바꾸기, 칩에 초점 + Alt+←/→로 한 칸 이동(`aria-live`로 "spring을 2번째로 옮겼어요").
    - "2 / 10"은 `aria-describedby`로 입력칸에 연결. 오류 칩은 빨간 테두리 + "⚠" 글자 + 문구(색만으로 구분하지 않음, FR-020).
    - 다시 발행할 때 `initialTags`(002 `EditorQueryService`가 이미 주는 지금 태그)로 미리 채운다(US1 #7).
  - 자동완성 `useTagSuggest(input)`: 입력이 0.3초 멈추고 `compositionstart`~`compositionend` 밖일 때만 부르고, 요청 번호를 올려 늦은 응답을 버린다. 실패·429·빈 결과·불러오는 중에는 목록을 띄우지 않는다. 목록은 `role="listbox"`, ↑/↓/Enter/Esc.
  - 발행하지 않고 닫으면 지금 칩을 002 `localDraftStore`의 태그 칸에 둔다(이미 있는 동작 유지, FR-014).
- **Rationale**: 정규화된 모양을 칩으로 보여 주는 것이 FR-020 "입력 즉시 정규화한 모양"이다. 서버가 다시 검사하므로 화면 규칙이 어긋나도 데이터는 안전하다.
- **Alternatives considered**: dnd 라이브러리(dnd-kit 등) — 칩 10개에는 과하고 번들 크기 증가.

## R13. Tier A 임시 규칙으로 만든 태그 (확정)

- **Decision**: 정리 마이그레이션을 만들지 않는다. 개발 DB는 `docker compose down -v` 후 다시 시드한다(quickstart §1). 다만 **운영 배포가 008보다 먼저 일어나면** 정리 마이그레이션(V{n}__tag_renormalize.sql + Java 마이그레이션)을 별도 작업으로 만든다 — tasks에 확인 작업(T002)으로 둔다.
- **Rationale**: Clarifications Q3.

## R14. 권한 매트릭스 행 (제안)

- **Decision**: `backend/src/test/resources/permission/tag-list.csv`를 004 하네스 형식으로 둔다. 목록 행동 4개, `isWrite = false`, 결과는 `INCLUDED`/`EXCLUDED`.
  - `tag.posts`(대상 글의 첫 태그로 `/api/tags/{name}/posts`에 그 글이 있는가), `tag.top`(`/api/tags`에 그 태그가 있는가 — 대상 글만 그 태그를 씀), `tag.suggest`(`/api/tags/suggest`에 그 태그가 있는가), `blog.tags`(`/api/members/{handle}/tags`에 그 태그가 있는가).
  - 대상 상태: `PUBLISHED_PUBLIC`만 `INCLUDED`, `PUBLISHED_PRIVATE`·`EDITING`(공개 글의 작업본 — 공개본은 계속 보이므로 `INCLUDED`)·`DRAFT`·`TRASHED`·`HIDDEN`·`AUTHOR_WITHDRAWN`은 `EXCLUDED`. `tag.suggest`의 `AUTHOR` 행위자는 모든 상태에서 `INCLUDED`(내 태그), `ANONYMOUS`는 401.
- **Rationale**: ANALYSIS-tier-a R6 — 004 FR가 Tier B에 위임한 목록 노출을 하네스에 연결한다. SC-004를 행 단위로 증명한다.

## R15. 다른 기능과 맞물림 (확정)

- **Decision**:
  - 005 `PostTagNamesQuery`: 008이 `TagNamesQueryAdapter`(Bean)를 등록하면 `PostReadingPorts`의 기본 구현이 물러난다(005 T033). 구현은 `TagService.tagNamesOf`를 그대로 부른다.
  - 012: 검색창 `#태그` 바로 이동(FR-040)은 012가 `normalizeQuery`·`normalizeTag.ts`로 만든다. 008은 함수와 `tagPath`만 공개한다. sitemap(012 FR-040)에 태그 페이지는 넣지 않는다(012 Clarifications: 공개 글 주소 + 블로그 주소만).
  - 013: AI 추천 결과는 `TagNormalizer.normalize`(금칙어 포함)로 거르고 실패는 조용히 버린다(FR-041).
  - 006: 글 완전 삭제 때 `post_tag`는 CASCADE, `tag`는 남는다(FR-017). 008이 할 일 없음. 006 머지 후 `App.tsx` 라우트 줄이 겹칠 수 있다.
  - 014: 관리자 숨김 글은 `hidden_at` 조건으로 빠진다(R5). 할 일 없음.
  - 015: 탈퇴 신청 작성자 글은 `m.withdrawn_at` 조건으로 빠진다. 탈퇴 정리 때 `post_tag`는 글과 함께 CASCADE.

## R16. 설정값 (제안)

- **Decision**: `@ConfigurationProperties("blog.tag")` `TagProperties`:
  ```yaml
  blog:
    tag:
      top-limit: 100            # 전체 태그 목록 개수
      blog-strip:
        limit: 100              # 블로그 태그 줄 API가 돌려주는 최대 개수 ([태그 더 보기]로 펼칠 수 있는 범위)
        initial: 10             # 처음 보이는 개수 (화면에 내려 줌)
      suggest:
        limit: 10
        rate-limit:
          limit: 60
          window: 1m
  ```
  `blog.post.max-tags`(002)는 그대로 쓴다. 0.3초 멈춤은 화면 상수(`TAG_SUGGEST_DEBOUNCE_MS = 300`)다.
- **Rationale**: 원칙 VII. 이름은 002 `markdown.preview-rate-limit`·`autosave.rate-limit` 모양(`limit`·`window`)을 따른다.
- **Alternatives considered**: [태그 더 보기]를 별도 페이지 API로 — 한 블로그의 태그는 많아야 수백 개라 한 번에 100개면 충분하다(spec Assumptions "plan에서 정한다").
