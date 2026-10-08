# Contract: 태그 정규화·주소·페이지 셸

**Feature**: 008-tag | 원문: [docs/22-tag.md](../../../docs/22-tag.md) §2·§5-1·§8, [docs/12-content-sanitize.md](../../../docs/12-content-sanitize.md) §7-4

이 문서는 서버 `TagNormalizer`, 화면 `normalizeTag.ts`·`tagPath.ts`, 그리고 이 규칙을 쓰는 다른 기능(012 검색, 013 AI 추천)이 함께 지키는 계약이다.

## 1. 정규화 단계

| 단계 | 처리 | 서버 | 화면 |
|---|---|---|---|
| ① | NFKC (전각 → 반각, 호환 문자) | `Normalizer.normalize(s, NFKC)` | `s.normalize('NFKC')` |
| ② | 보이지 않는 글자·방향 제어·제어 문자 제거: U+200B~200F, U+2060~2069, U+FEFF, U+202A~202E, ISO 제어 문자(U+0000~001F, U+007F~009F) | `InvisibleCharacters.isInvisible` | 같은 목록 상수 |
| ③ | 앞뒤 공백 제거 (유니코드 공백) | `strip()` | `trim()` + U+00A0 등 (NFKC 뒤라 대부분 U+0020) |
| ④ | 맨 앞 `#` 모두 제거 → 다시 ③ | `^#+` | 같음 |
| ⑤ | 소문자 (언어 설정 무관) | `toLowerCase(Locale.ROOT)` | `toLowerCase()` (JS는 언어 무관) |
| ⑥ | 공백 묶음 → `-` 하나 | `\p{IsWhite_Space}+` | `/\s+/gu` |
| ⑦ | `-` 연속 → 하나, 처음·끝 `-` 제거 | `-{2,}`, `^-|-$` | 같음 |
| ⑧ | 형식: 허용 `[가-힣a-z0-9._+#-]`만, `[가-힣a-z0-9]` 1자 이상 → 아니면 `INVALID_TAG`. 코드 포인트 30 초과 → `TAG_TOO_LONG` | `TagNormalizer` | 같음 |
| ⑨ | 금칙어 (001 `BannedWordFilter`, 변형 4가지·예외 단어) → `TAG_BANNED_WORD` | 발행·AI 추천만 | **하지 않음** (서버 400이 그 칩에 표시) |

한 칸에 문제가 여럿이면 `INVALID_TAG` → `TAG_TOO_LONG` → `TAG_BANNED_WORD` 순서로 하나만 돌려준다.

진입점:

- `normalize(raw)` — ①~⑨. 발행(`TagService.validate`/`normalizeAll`), 013 AI 추천.
- `normalizeQuery(raw)` — ①~⑧(길이 초과도 실패). 자동완성 검색어, 페이지 셸 `/tags/{name}`, 블로그 `?tag=`, 012 검색창 `#태그`. 실패하면 빈 값.

## 2. 예시 표 (테스트 원본)

`backend/src/test/resources/tag/normalization-cases.csv`에 아래 행을 그대로 둔다. 서버 `TagNormalizerTest`와 화면 `normalizeTag.test.ts`가 같은 파일을 읽는다(`code`가 `TAG_BANNED_WORD`인 행은 화면이 건너뜀). `\uXXXX`는 테스트가 풀어 쓴다.

| input | expected | code | 근거 |
|---|---|---|---|
| `Spring Boot` | `spring-boot` | | ⑤⑥ |
| `spring-boot` | `spring-boot` | | US1 #1 |
| `#SPRING  BOOT` | `spring-boot` | | ④⑤⑥ |
| `ｓｐｒｉｎｇ ｂｏｏｔ` (ｓｐｒｉｎｇ ｂｏｏｔ) | `spring-boot` | | ① |
| `#JPA` | `jpa` | | ④⑤ |
| `##jpa` | `jpa` | | ④ 여러 개 |
| `  C++ ` | `c++` | | ③⑤ |
| `C#` | `c#` | | 뒤 `#` 남김 |
| `Node.JS` | `node.js` | | |
| `.NET` | `.net` | | 맨 앞 `.` 허용 |
| `스프링  부트` | `스프링-부트` | | ⑥ |
| `spring--boot-` | `spring-boot` | | ⑦ |
| `자바_기초` | `자바_기초` | | |
| `spr​ing` | `spring` | | ② |
| `　spring　` | `spring` | | ①③ (전각 공백) |
| `...` | | `INVALID_TAG` | 한글·영문·숫자 없음 |
| `---` | | `INVALID_TAG` | ⑦ 뒤 빈 값 |
| `#` | | `INVALID_TAG` | ④ 뒤 빈 값 |
| `ㅋㅋ` | | `INVALID_TAG` | 완성된 한글 아님 |
| `ㅅㅂ` | | `INVALID_TAG` | 완성된 한글 아님 |
| `🔥hot` | | `INVALID_TAG` | 허용 밖 문자(지우지 않음) |
| `a/b` | | `INVALID_TAG` | |
| `c@d` | | `INVALID_TAG` | |
| `a` × 30 | `a` × 30 | | 경계 |
| `a` × 31 | | `TAG_TOO_LONG` | |
| `가` × 31 | | `TAG_TOO_LONG` | 코드 포인트 기준 |
| (금칙어 목록의 첫 단어) | | `TAG_BANNED_WORD` | 테스트가 `policy/banned-words.txt`에서 읽음 (문서에 단어를 적지 않음) |
| (금칙어 + 숫자 끼움, 예: 금칙어 사이 `1`) | | `TAG_BANNED_WORD` | 변형(숫자 제거) |

한 글의 태그 목록 (발행 검증, `PublishValidatorTest`·`TagPublishIT`):

| 입력 배열 | 저장 결과 | 오류 |
|---|---|---|
| `[Spring, spring, jpa]` | `[spring, jpa]` | 없음 (중복은 처음 것만) |
| `[a, b, …, k]` 서로 다른 11개 | — | `tags` `TOO_MANY_TAGS` |
| `[Spring, spring, SPRING, #spring, …]` 같은 이름 11칸 | `[spring]` | 없음 (개수는 중복 제거 후에 센다) |
| `[🔥hot, ok, a/b]` | — | `tags[0]` `INVALID_TAG`, `tags[2]` `INVALID_TAG` (모두 한 번에) |
| 서로 다른 11개 + `🔥hot` | — | `tags` `TOO_MANY_TAGS` + `tags[11]` `INVALID_TAG` (함께) |

## 3. 주소 인코딩

| 이름 | 경로 (`/tags/…`) | 쿼리 (`?tag=…`) |
|---|---|---|
| `spring-boot` | `spring-boot` | `spring-boot` |
| `c#` | `c%23` | `c%23` |
| `c++` | `c++` | `c%2B%2B` |
| `node.js` | `node.js` | `node.js` |
| `.net` | `.net` | `.net` |
| `스프링-부트` | `%EC%8A%A4%ED%94%84%EB%A7%81-%EB%B6%80%ED%8A%B8` | 같음 |
| `자바_기초` | `%EC%9E%90%EB%B0%94_%EA%B8%B0%EC%B4%88` | 같음 |

- 서버: `UriUtils.encodePathSegment(name, UTF_8)`(경로), `UriComponentsBuilder…encode()`(쿼리).
- 화면: `tagPath(name)` = `'/tags/' + encodeURIComponent(name).replace(/%2B/g, '+')`. 쿼리는 `encodeURIComponent` 그대로.
- `.`·`..`·`-`만으로 된 이름은 ⑧에서 막혀 존재할 수 없다(경로 탐색 문제 없음).
- `#`을 인코딩하지 않으면 브라우저가 조각(fragment)으로 읽는다 — 화면에서 태그 주소를 문자열 이어 붙이기로 만들지 않는다.

## 4. 페이지 셸 응답 (`PageShellController`)

| 요청 | 조건 | 응답 |
|---|---|---|
| `GET /tags` | — | 200 셸 + 메타(제목 "태그") |
| `GET /tags/{name}` | `normalizeQuery(name)` 실패 | 404 공통 화면 (`NotFoundPageRenderer`) |
| | 결과 ≠ `name` | 301 `Location: /tags/{encodePathSegment(결과)}` (쿼리 유지), `Cache-Control: no-cache` |
| | 결과 = `name` (글 유무 무관) | 200 셸 + 메타(제목 "#name"). 공개 글 수는 메타에 넣지 않음 (SC-005) |
| `GET /@{handle}?tag={v}` | handle 대문자 | 기존 005 ① 301 (쿼리 유지) — 먼저 |
| | `normalizeQuery(v)` 실패 | 404 공통 화면 |
| | 결과 ≠ `v` | 301 `Location: /@{handle}?tag={인코딩(결과)}` (다른 쿼리 값 유지) |
| | 결과 = `v` | 기존 005 흐름 (없는 블로그 404 / 200 셸) |

예: `/tags/Spring%20Boot` → 301 `/tags/spring-boot`, `/tags/%F0%9F%94%A5` → 404, `/tags/c%23` → 200, `/@kim?tag=JPA` → 301 `/@kim?tag=jpa`.

## 5. 다른 기능이 쓰는 방법

- 012 검색창: 검색어 전체가 `#`으로 시작하는 한 단어(`^#\S+$`)이고 `normalizeQuery` 성공 → `302 /tags/{인코딩}`(서버) / `navigate(tagPath(name))`(화면). `# spring`, `#`, 실패는 보통 검색(FR-040).
- 013 AI 추천: 추천 이름마다 `normalize`(⑨ 포함) → `Accepted`만, 이미 붙은 이름 제외, `max-tags − 현재 개수`개까지(FR-041).
