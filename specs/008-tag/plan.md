# Implementation Plan: 태그와 태그별 글 목록

**Branch**: `008-tag` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-tag/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

작성자가 발행 설정 창에서 태그를 붙이고, 같은 뜻의 입력은 하나의 정규화 규칙으로 같은 태그가 된다(C-TAG-1). 독자는 `/tags/{이름}`에서 그 태그의 공개 글을 홈과 같은 카드로 보고, `/tags`에서 많이 쓰인 태그 100개를 보며, 블로그 위 태그 줄과 `?tag=` 필터로 한 사람의 글을 거른다. 로그인한 작성자에게는 자동완성을 준다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **정규화 클래스 하나.** `tag.domain.TagNormalizer`가 9단계(NFKC → 보이지 않는 글자 제거 → 앞뒤 공백 → 맨 앞 `#` → `Locale.ROOT` 소문자 → 공백 묶음 `-` → `-` 정리 → 형식 → 금칙어)를 한 곳에서 처리하고, 결과를 이름 또는 거부 코드(`INVALID_TAG`·`TAG_TOO_LONG`·`TAG_BANNED_WORD`)로 돌려준다. 발행·자동완성(금칙어 제외)·태그 주소·블로그 필터가 이 클래스를 쓰고, 012 검색·013 AI 추천도 같은 것을 쓴다(R1·R2). 22 §2-1 예시 표를 CSV 한 벌로 두고 서버·화면 테스트가 함께 읽는다(R4).
- **002 임시 `TagService`를 교체한다.** 공개 메서드(`normalizeAll`·`validate`·`replacePostTags`·`tagNamesOf`)와 교체 점검표(002 T122: `tags[i]` 필드, 쿼리 3번, 트랜잭션 실패 시 되돌림)를 그대로 지키고 안의 규칙만 `TagNormalizer`로 바꾼다. 지금 `TagService`가 `post.domain.PostReasonCode`를 가져다 써서 생기는 tag → post 의존을 `tag.domain.TagReasonCode`로 끊는다(R3).
- **스키마 변경 없음.** V1의 `tag`(`ck_tag_name`·`ix_tag_name_prefix`)와 `post_tag`(`uq_post_tag_position`·`ix_post_tag_tag`, 글 삭제 CASCADE, 태그 삭제 RESTRICT)를 그대로 쓴다. Tier A 임시 규칙으로 생긴 태그는 운영 데이터가 없으므로 정리 마이그레이션 없이 개발·테스트 데이터만 새로 만든다(Clarifications Q3, R13).
- **목록 조건은 004 `VisibilityFilter` 하나.** 태그별 목록·전체 태그 목록·자동완성의 공개 글 수는 `forViewer(Viewer.anonymous(), null)`(전체 공개만, FRIENDS 적용자라도 친구 글 제외), 블로그 태그 줄·필터는 블로그 목록과 같은 `forViewer(viewer, ownerId)`를 쓴다(R5). 태그별 카드 목록은 005 `PostCardQueryRepository`에 "태그 번호" 조건을 더해 같은 SQL 한 번으로 읽는다(R6).
- **전체 태그 목록은 저장하지 않고 매번 계산한다**(Clarifications Q1). `post_tag ⋈ post ⋈ member` GROUP BY를 요청마다 실행하고, 글 1만 건·태그 3만 연결에서 300ms 이내인지 측정 작업을 둔다. 넘으면 그때만 "공개에서 빠질 때 지우는 캐시"로 바꾼다(R8).
- **페이지 주소의 301·404는 서버가 준다**(Clarifications Q2). 005 `PageShellController`에 `/tags`·`/tags/{name}`을 더하고 블로그 셸 `/@{handle}`에서 `?tag=` 값을 정규화한다. 정규화되지 않으면 301, 형식이 틀리면 공통 404 화면, 형식에 맞으면 글이 없어도 200 셸이다. `node.js`처럼 점이 든 이름은 `SpaForwardingController`(점 있는 경로 제외)가 받지 못하므로 이 매핑이 반드시 있어야 한다(R9·R10).
- **자동완성.** `GET /api/tags/suggest?q=`는 `@LoginRequired`(비회원 401, 인증 전 허용), 001 `RateLimiter.acquireOrThrow("ratelimit:tag-suggest:{memberId}", 60, 1m)`(429 `TOO_MANY_REQUESTS`, Redis 장애 시 통과), `LIKE :prefix ESCAPE '\'`(태그에 쓰는 `_`를 이스케이프)로 "내 태그 먼저 → 공개 글 수 많은 순 → 이름 순" 10개를 SQL 한 번으로 고른다(R11).
- **화면.** 발행 설정 창의 임시 칩 입력(002)을 `TagInput`으로 바꾼다: Enter·쉼표 추가, 정규화 미리보기 칩, ×·Backspace 삭제, 끌어서·Alt+방향키 순서 바꾸기, "2 / 10", 오류 칩(색 + 글자), 0.3초 멈춤·한글 조합 중 호출 안 함·늦은 응답 버림 자동완성. `/tags`·`/tags/:name` 화면과 블로그 태그 줄·필터를 더하고, 태그 링크는 `tagPath(name)` 하나로 만든다(R12).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation), `JdbcClient`, Flyway, Resilience4j(`RedisGuard`), 001 `RateLimiter`·`BannedWordFilter`, 005 `PostCardQueryRepository`·`PostListService`·`PostListCursor`·`PageShellController`·`SpaShellRenderer`·`NotFoundPageRenderer`, 004 `VisibilityFilter`
- 새 의존성 없음. `java.text.Normalizer`(NFKC), Spring `UriUtils.encodePathSegment`
- 화면: React 18, react-router 7, 005 `useCursorList`·`PostCardGrid`·`LoadMoreButton`. 끌어서 순서 바꾸기는 HTML 드래그 이벤트 + 키보드(Alt+방향키)로 직접 구현한다(라이브러리 추가 없음, R12)

**Storage**:

- PostgreSQL: `tag`, `post_tag`(V1, 변경 없음), 읽기만 `post`·`member`(목록 조건)
- Redis: 자동완성 제한 `ratelimit:tag-suggest:{memberId}`(001 `RateLimiter`). 태그 목록 캐시는 두지 않는다(Clarifications Q1)
- 브라우저: 발행하지 않고 닫은 태그는 002 `localDraftStore`의 기존 칸(그 브라우저에만)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc(실제 Security 필터 체인 — `StrictHttpFirewall`이 `%23`·`%2B`·한글 인코딩을 통과하는지). 화면은 Vitest + Testing Library, 종단 확인은 Playwright. 헌법 VIII에 따라 공개 조건·동시 생성·권한 행은 실제 DB 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 태그별 목록·전체 태그 목록·블로그 태그 줄 서버 응답 300ms 이내(글 1만 건, 연결 3만 건, SC-007·FR-029)
- 태그별 목록은 SQL 2번(태그 번호 1번 + 카드 1번), 전체 태그 목록·자동완성·블로그 태그 줄은 각 SQL 1번. 태그 수에 비례해 쿼리가 늘지 않는다
- 발행 트랜잭션 안의 태그 교체는 지금처럼 태그 수와 상관없이 쿼리 3번(002 T122)

**Constraints**:

- 공개 범위 조건은 `VisibilityFilter`에서만 가져온다(목록마다 WHERE를 직접 쓰지 않음)
- 비공개 글에만 쓰인 태그와 아무도 안 쓴 태그의 페이지·API 응답은 바이트 단위로 같다(SC-005)
- 금칙어 거부는 어떤 단어인지 응답·로그에 남기지 않는다
- 자동완성 실패·제한이 태그 입력과 발행을 막지 않는다(SC-008)
- 화면은 375px 폭부터 가로 스크롤 없음, 칩 오류는 색만으로 구분하지 않음

**Scale/Scope**:

- 글 1만 건, 태그 수천 개, 연결 3만 건 기준
- 화면 2개(`/tags`, `/tags/:name`) + 발행 설정 창 태그 입력 교체 + 블로그 태그 줄·필터
- API 6개(태그별 머리말·목록, 전체 목록, 자동완성, 블로그 태그, 블로그 목록 `?tag=`), 페이지 셸 2개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 `tag`·`post_tag`를 그대로 쓰고 마이그레이션이 없다. 태그 최대 개수는 기존 설정값 `blog.post.max-tags`(강성찬 5개 가능) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (설계 조건부)** | 태그 쓰기·정규화·집계는 `tag` 모듈. 카드 목록은 005 discovery가 가진 읽기 전용 예외(Complexity Tracking, 005 plan) 안에서 `post_tag` 조건만 더한다. 태그 집계는 `post`·`member`를 읽어야 해 같은 예외를 `tag.infra.TagQueryRepository` 한 곳에만 둔다(아래 Complexity Tracking). 지금 있는 tag → post 의존(`PostReasonCode`)은 없앤다(R3) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 태그 붙이기 권한은 발행 권한 그대로(002·004). 자동완성은 `@LoginRequired`, 후보 SQL에 `author_id = :me` 또는 공개 조건만 쓴다. 블로그 태그는 005 `BlogQueryService.requireOwner`(없음·탈퇴 유예 같은 404). 권한 매트릭스 하네스에 목록 행동 4개를 더한다(R14) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 태그 이름은 허용 문자만 저장되고, 화면은 글자로만 렌더링한다. 주소는 `UriUtils.encodePathSegment`·`tagPath()`로만 만든다 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 자동완성 실패·429·503은 화면이 조용히 무시한다. 글 상세 태그 조회 실패는 빈 목록(005 R-30). 요청 제한은 Redis 장애 때 통과 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 쓰는 글이 없어진 태그도 지우지 않는다(FR-017). 글 완전 삭제 시 `post_tag`는 FK CASCADE(006) |
| VII. 수치는 설정값으로 | **PASS** | `blog.post.max-tags`(기존), `blog.tag.top-limit`(100), `blog.tag.suggest.limit`(10)·`rate-limit`(60/1m)·`debounce`는 화면 상수, `blog.tag.blog-strip.limit`(100)·`initial`(10). 목록 크기는 005 `blog.list.page-size`(9) |
| VIII. 실제 DB로 통합 테스트 | **PASS** | Testcontainers로 공개 조건 행렬(SC-004), 동시 10건 발행(SC-003), 응답 동일성(SC-005), 1만 건 측정(SC-007), 실제 Security 필터의 주소 왕복(SC-002) |

**Gate 결과 (Phase 0 전)**: 위반 없음. 원칙 II의 읽기 예외는 005와 같은 방식이라 Complexity Tracking에 적는다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. 자동완성 경로 `/api/tags/suggest`와 태그 이름 `suggest`가 겹치지 않도록 머리말은 `/api/tags/{name}/summary`, 목록은 `/api/tags/{name}/posts`로 두 구간 경로를 쓴다(R7).
  2. `?tag=c++`처럼 쿼리 문자열의 `+`는 공백으로 읽히므로 화면은 쿼리 값을 `encodeURIComponent`로, 경로 조각은 `tagPath()`로 만든다(R10).
  3. `TitleNormalizer`(post)와 `TagNormalizer`(tag)가 같은 "보이지 않는 글자" 목록을 쓰도록 `shared.text.InvisibleCharacters`로 옮긴다. 002 소유 파일을 고치므로 002 회귀 테스트를 함께 돌린다(R2).

## Project Structure

### Documentation (this feature)

```text
specs/008-tag/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 태그 머리말·목록·전체·자동완성·블로그 태그 REST 계약
│   └── normalization.md # 정규화 규칙·예시 표(테스트 CSV 원본)·주소 인코딩·페이지 셸 응답
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── tag/
│   ├── domain/
│   │   ├── TagNormalizer.java              # 9단계 정규화 (금칙어 검사 포함/제외 두 진입점)
│   │   ├── TagNormalization.java           # 결과: Accepted(name) | Rejected(TagReasonCode)
│   │   └── TagReasonCode.java              # INVALID_TAG, TAG_TOO_LONG, TAG_BANNED_WORD (ReasonCode)
│   ├── application/
│   │   ├── TagService.java                 # (002 임시 → 최종) normalizeAll / validate / replacePostTags / tagNamesOf
│   │   ├── TagQueryService.java            # summary / top / suggest / blogTags / findIdByName
│   │   ├── TagSuggestService.java          # 요청 제한 + 검색어 정규화 + 후보 조회
│   │   ├── TagProperties.java              # @ConfigurationProperties("blog.tag")
│   │   └── TagNamesQueryAdapter.java       # 005 PostTagNamesQuery 구현 Bean (기본 구현 물러남)
│   ├── infra/
│   │   └── TagQueryRepository.java         # 집계 SQL (VisibilityFilter 사용, 원칙 II 예외 한 곳)
│   └── web/
│       ├── TagController.java              # GET /api/tags, /api/tags/{name}/summary, /api/tags/{name}/posts, /api/tags/suggest
│       └── BlogTagController.java          # GET /api/members/{handle}/tags
├── discovery/
│   ├── infra/PostCardQueryRepository.java  # + CardFilter(authorId, tagId) — post_tag EXISTS 조건
│   ├── application/PostListService.java    # page(scope, filter, cursor, viewer)로 넓힘
│   ├── application/BlogQueryService.java   # listPosts(handle, tag, cursor, viewer)
│   ├── application/TagPostQueryService.java# 태그별 목록 (scope tag:{name})
│   ├── application/LinkPreviewMetaFactory.java # + forTag(name), forTagIndex()
│   └── web/
│       ├── BlogController.java             # listBlogPosts에 ?tag= (정규화 안 되면 404)
│       └── PageShellController.java        # + /tags, /tags/{name}, /@{handle}?tag= 301·404
├── post/
│   ├── domain/PublishValidator.java        # 개수 + 칸별 오류를 함께 모음 (SC-006)
│   ├── domain/PostReasonCode.java          # INVALID_TAG 제거(TagReasonCode로 이동, 코드 값 같음)
│   ├── domain/TitleNormalizer.java         # InvisibleCharacters 사용
│   └── config/PostReadingPorts.java        # (변경 없음 — 008 Bean이 등록되면 기본 구현이 물러남)
└── shared/
    ├── text/InvisibleCharacters.java       # (신규) 폭 0·방향 제어·제어 문자 판정 (12 §7-4)
    └── web/cursor/ListScope.java           # + tag(name), blogTag(handle, name)

backend/src/test/
├── resources/tag/normalization-cases.csv   # 22 §2-1 예시 표 (서버·화면 테스트 공용 원본)
├── resources/permission/tag-list.csv       # 목록 행동 4개 × 대상 상태
└── java/com/team/blog/tag/
    ├── unit/TagNormalizerTest.java         # CSV 매개변수 테스트 + 경계
    └── integration/
        ├── TagPublishIT.java               # US1: 정규화·순서·오류 모두·동시 10건·수정됨
        ├── TagPostListIT.java              # US2: 공개 조건·빈 태그 동일 응답·커서
        ├── TagPageShellIT.java             # US2 #4~#6, US5 #3: 실제 Security 필터로 301·404·왕복
        ├── TagSuggestIT.java               # US3: 후보 범위·순서·401·429·Redis 장애
        ├── TagIndexIT.java                 # US4: 상위 100·0개 제외·즉시 반영
        ├── BlogTagIT.java                  # US5: 태그 줄·필터·커서 범위
        ├── TagPerformanceIT.java           # SC-007·FR-029: 1만 건 측정 + EXPLAIN
        └── TagPermissionMatrixIT.java      # 004 하네스 tag-list.csv

frontend/src/
├── api/tags.ts                             # getTagSummary·listTagPosts·listTopTags·suggestTags·getBlogTags
├── features/tag/
│   ├── normalizeTag.ts                     # 서버와 같은 규칙(금칙어 제외) — 칩 미리보기·검색 공용
│   ├── tagPath.ts                          # /tags/{encodePathSegment 규칙}
│   ├── useTagSuggest.ts                    # 0.3초·조합 중 안 부름·요청 번호로 늦은 응답 버림
│   └── tagMessages.ts                      # 오류 code → 문구
├── components/editor/TagInput.tsx          # 칩·끌어서·Alt+방향키·"2 / 10"·오류 칩·자동완성 목록
├── components/editor/PublishDialog.tsx     # 임시 칩 입력 → TagInput
├── components/TagList.tsx                  # 링크를 tagPath()로
├── components/BlogTagStrip.tsx             # 블로그 위 태그 줄 (10개 + [태그 더 보기])
├── pages/TagPage.tsx                       # /tags/:name
├── pages/TagIndexPage.tsx                  # /tags
├── pages/BlogPage.tsx                      # 태그 줄 + ?tag= 필터 머리말
└── App.tsx                                 # /tags, /tags/:name 라우트 (/:handle 앞)
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 정규화·쓰기·집계는 `tag` 모듈, 카드 목록과 페이지 셸은 005가 만든 `discovery`를 넓힌다. post 모듈은 발행 검증(`PublishValidator`)의 오류 모음 방식과 `INVALID_TAG` 위치만 바뀐다. 012 검색창 `#태그` 이동과 013 AI 추천은 `TagNormalizer`·`normalizeTag.ts`를 호출만 한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `tag.infra.TagQueryRepository`가 `post`·`member` 테이블을 JOIN해 읽는다(원칙 II 읽기 예외) | 공개 글 수 집계는 `post_tag`와 글의 공개 조건(`post.status`·`visibility`·`deleted_at`·`hidden_at`, `member.withdrawn_at`)을 한 SQL로 묶어야 300ms 안에 끝난다. 조건 문구는 004 `VisibilityFilter`가 주므로 규칙이 갈라지지 않는다 | post 모듈 API로 "공개 글 번호 목록"을 받아 tag 모듈에서 세면 글 1만 건마다 번호를 메모리로 옮겨야 하고(N+1 또는 거대 IN), 캐시를 두면 Clarifications Q1(매번 계산)과 어긋난다 |
| `discovery.infra.PostCardQueryRepository`가 `post_tag`를 읽는다 | 태그별 카드 목록을 카드 SQL 한 번으로 만들려면 `EXISTS (post_tag)` 조건이 같은 SQL에 있어야 한다. 005가 이미 `member`·`image`를 같은 예외로 읽는다 | tag 모듈이 글 번호를 먼저 주고 discovery가 `id IN (...)`으로 읽으면 커서·정렬을 두 번 처리해야 하고 페이지 경계가 틀어진다 |
