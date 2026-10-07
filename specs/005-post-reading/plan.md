# Implementation Plan: 글 읽기 (전체 글 목록·개인 블로그·글 상세)

**Branch**: `005-post-reading` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-post-reading/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

누구나 홈(`/`)에서 공개·발행 글을 최초 공개 일자 최신순으로 9개씩 카드로 보고, `/@블로그주소`에서 한 사람의 프로필과 공개 글을, `/@블로그주소/posts/{글 번호}`에서 글 상세를 본다(C-READ-1·C-READ-2·C-BLOG-1, Tier A).

기술 접근(→ [research.md](./research.md)):

- **목록**: `GET /api/posts`, `GET /api/members/{handle}/posts`. 글 + 작성자 + 프로필 사진을 **SQL 한 번**(LIMIT 10)으로 읽고 본문 컬럼은 읽지 않는다. 정렬 `(first_public_at DESC, id DESC)`, 부분 인덱스 `ix_post_feed`·`ix_post_blog`를 그대로 탄다. 노출 조건은 004의 공용 `VisibilityFilter`(06 R-2a)만 쓴다.
- **커서**: 공용 `CursorCodec`(shared) — `{"v":1,"k":[epoch 마이크로초, id]}` + 목록 구분 필드 `l`을 패딩 없는 Base64URL로. 잘못되면 400 `INVALID_CURSOR`.
- **상세**: `GET /api/posts/{postId}`(신규 제안) — `PostAccessPolicy.canRead`(004) 판정 후 글+작성자 1번, 태그 1번, 좋아요 여부 1번, 팔로우 여부 1번, (작성자일 때) 작업본 1번 = 최대 5쿼리. `content_html`만 읽는다.
- **첫 응답(H7)**: 화면은 React이지만 `/@{handle}`·`/@{handle}/posts/{postId}`는 서버의 `PageShellController`가 React `index.html`에 링크 미리보기 메타를 넣고 상태 코드(200/301/302/404)를 정해 보낸다. 상세 주소 처리 순서는 40 §3 ①~⑥ 그대로.
- **화면**: React 카드 그리드(375px~ 가로 스크롤 없음), [더 보기] + `sessionStorage` 30분 복원, 조회 기록 비콘(1초 가시성), 코드 강조는 코드 블록이 있을 때만 동적 로드. 인라인 스크립트 없음(CSP `script-src 'self'`).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript/JavaScript + React (화면)

**Primary Dependencies**: Spring Boot 3.x 이상(팀 확정), Maven, Spring Web MVC, Spring Data JPA(+ 목록·상세 읽기 전용 네이티브/JPQL 조회), Spring Security, Spring Session Data Redis, Flyway, React(+ react-router), highlight.js(코드 블록이 있을 때만 동적 로드). 본문 렌더링(commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer)은 002가 발행 때 끝내므로 이 기능은 **호출하지 않는다**. MinIO(AWS SDK v2)도 직접 쓰지 않고 `blog.image.public-base-url` + 저장 키로 주소만 만든다.

**Storage**: PostgreSQL(pg_trgm 확장은 이 기능과 무관). 읽기 전용 — 새 테이블·컬럼·마이그레이션 없음. Redis는 세션(Spring Session)만 간접 사용. 브라우저 `sessionStorage`(목록 복원).

**Testing**: JUnit 5 + Testcontainers(PostgreSQL, Redis) + Spring Security Test + MockMvc. 목록 인덱스 사용 여부는 `EXPLAIN` 통합 테스트. 화면은 컴포넌트 테스트(팀 프런트 테스트 도구, 제안: Vitest + Testing Library) + 375px 수동/자동 확인.

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO), 최신 모바일·데스크톱 브라우저.

**Project Type**: web-service + SPA (모듈러 모놀리스, React 빌드를 같은 도메인에서 서빙)

**Performance Goals**: 목록·상세 서버 응답 300ms 이내(글 1만 건, 02 §6). 목록 1요청 = SQL 1번(C-READ-1 #6), 상세 API 최대 5쿼리(+ 댓글은 007 API), 카드 9장 썸네일 약 0.4MB(10 L-8).

**Constraints**: N+1 금지, 본문 컬럼(`content_md`) 미조회, 반응형 375px~ 가로 스크롤 없음, 인라인 스크립트 없음(CSP `script-src 'self'`), 상세 `Cache-Control: private, no-cache` / `PUBLIC`이 아니면 `private, no-store`, 볼 수 없는 글과 없는 글의 응답 동일(404), Redis 장애 시 비회원으로 계속 읽기(02 §2-1).

**Scale/Scope**: 글 1만 건 기준. API 4개(목록 2, 블로그 머리말 1, 상세 1) + 서버 첫 응답 경로 2개(`/@{handle}`, `/@{handle}/posts/{postId}`) + 화면 3개(홈·블로그·상세) + 공용 커서 코덱.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고 추가만 | **PASS** | 51 통합 ERD의 기존 컬럼·인덱스(`post.first_public_at`·`excerpt`·`thumbnail_url`·카운트 컬럼, `ix_post_feed`·`ix_post_blog`, `uq_image_profile_current`)만 읽는다. 스키마 변경·마이그레이션 없음. 개인 확장(카테고리 사이드바·시리즈 탭 등)은 화면 자리만 남긴다. |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (조건부)** | 컨트롤러는 discovery·post 모듈에 두고, 태그·좋아요·팔로우·댓글·회원·사진 주소는 각 모듈의 공개 Service로 가져온다. 단, 목록 카드 SQL(10 §7)과 상세 "글+작성자" 조회는 post·member·image 테이블을 **읽기 전용 JOIN 한 번**으로 읽는다(C-READ-1 #6 "SQL 1번"). 02 §3이 discovery를 "읽기 전용 조회" 모듈로 둔 설계이며, 이 예외를 Complexity Tracking에 기록한다. |
| III. 권한 두 겹, 볼 수 없으면 404 | **PASS** | 상세는 API·첫 응답 모두 `PostAccessPolicy.canRead`(004) 하나로 판정하고, 판정 뒤에만 블로그 주소 불일치 301(40 R-3). 없는 글·볼 수 없는 글·숫자가 아닌 번호 → 같은 404 본문·같은 공통 메타. 목록은 `VisibilityFilter` 공용 조건만(작성자 본인 예외 없음, 06 V-8). 현재 사용자는 세션에서만, 작성자 ID 파라미터 없음. 화면의 버튼 숨김은 보조(004 FR-045). |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 본문은 발행 때 정화된 `content_html`을 그대로 출력(재렌더 없음). 제목·소개·닉네임은 React 텍스트 노드로, 메타 태그 값은 서버가 HTML 속성 이스케이프. 인라인 스크립트 없음, 조회 기록·GIF 재생·코드 강조 모두 우리 서버가 주는 별도 파일. |
| V. 부가 기능 실패가 읽기를 막지 않음 | **PASS** | 조회 기록은 상세 응답 경로 밖의 별도 요청(실패 무시). 좋아요·팔로우 여부·작업본 조회 실패 시 기본값(false/없음)으로 상세를 계속 보여준다(제안). Redis(세션) 장애 시 비회원으로 처리돼 공개 글 읽기는 계속. |
| VI. 데이터를 잃지 않음 | **PASS (해당 적음)** | 읽기 전용. 수정 중인 글은 독자·작성자 모두 마지막 발행본을 보고, 작성자에게만 작업본 저장 시각 안내(C-POST-3). 스키마 변경 없음 → Flyway 파일 추가 없음. |
| VII. 수치는 설정값으로 | **PASS** | 페이지 크기 9(`blog.list.page-size`), 미리보기 설명 길이 160(`blog.seo.description-length`), 사이트 주소·기본 OG 이미지·저장소 공개 주소, 화면의 복원 보관 시간 30분(프런트 설정)을 설정값으로 둔다. 클라이언트의 `size`는 무시하고 설정값을 쓴다. |
| VIII. 실제 DB 통합 테스트 | **PASS** | 42 §5-1 보기 표 전체(비회원·회원·작성자·관리자 × 상태 7종)와 목록 노출 조건, 커서 중복·누락, `EXPLAIN` 인덱스 사용, 404 응답 동일성을 Testcontainers PostgreSQL로 검증. 각 Acceptance Scenario를 테스트 1개 이상으로 옮긴다(quickstart.md). |

**Post-design 재확인 (Phase 1 이후)**: data-model.md·contracts/openapi.yaml·quickstart.md 작성 후 다시 확인했다. 새로 생긴 위반 없음. 설계 중 추가된 것은 (a) 상세 API·블로그 머리말 API 경로, (b) 커서 목록 구분 필드 `l`, (c) OG 원본 이미지 조회(썸네일 키 → 원본 키) 1쿼리(첫 응답 경로에만)이며, 모두 원칙 I(스키마 변경 없음)·III(판정 후 응답)·VII(설정값)을 지킨다. 원칙 II의 읽기 전용 JOIN 예외는 그대로 Complexity Tracking에 둔다.

## Project Structure

### Documentation (this feature)

```text
specs/005-post-reading/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── openapi.yaml     # Phase 1 output — 목록·블로그·상세 API (서버 이벤트·배치 없음 → events.md 없음)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── discovery/
│   ├── web/            HomePostController         GET /api/posts
│   │                   BlogController             GET /api/members/{handle}, GET /api/members/{handle}/posts
│   │                   PageShellController        GET /@{handle}, GET /@{handle}/posts/{postId} (메타 + 상태 코드, H7)
│   ├── application/    HomeQueryService, BlogQueryService, PostCardView, BlogHeaderView, CursorPage<T>
│   └── infra/          PostCardQueryRepository    (카드 SQL 1번, VisibilityFilter 조건 사용)
├── post/
│   ├── web/            PostDetailController       GET /api/posts/{postId}
│   ├── application/    PostQueryService.getDetail(), PostDetailView, PostDetailAssembler
│   ├── domain/         PostAccessPolicy, VisibilityRule, Visibility        ← 004 소유, 이 기능은 사용만
│   └── infra/          VisibilityFilter(004 소유, 사용만), PostQueryRepository(004 소유, 상세 행 findDetailRow 추가 — tasks T034)
├── account/application/ MemberQueryService.findReadableBlogOwner(handle)   ← 공개 Service 사용
├── media/application/   ImageUrlResolver (public-base-url + key), ProfileImageQuery(ProfileImageKeys: 머리말은 display(), og:image는 original() — 001 T040),
│                        OgImageResolver(썸네일 키 → 원본)
├── tag/application/     TagService.findNamesInOrder(postId)                ← 008, 없으면 빈 목록
├── interaction/application/ LikeService.isLikedBy(), CommentService      ← 009·007
└── shared/
    ├── web/cursor/     CursorCodec, CursorPayload, ListScope, InvalidCursorException
    ├── web/shell/      SpaShellRenderer (index.html의 <head> 자리 표시자에 메타 삽입), LinkPreviewMeta
    ├── web/            CacheControlPolicy, NotFoundPageRenderer (004 T019·T020 소유, 사용만)
    ├── security/       CurrentUser / Viewer (기존)
    └── error/          GlobalExceptionHandler (INVALID_CURSOR·NOT_FOUND 매핑, 기존)

backend/src/main/resources/
├── application.yml     blog.list.page-size, blog.site.base-url, blog.seo.description-length,
│                       blog.seo.default-og-image-url, blog.image.public-base-url(기존 공용)
└── db/migration/       (이 기능은 마이그레이션 없음)

backend/src/test/java/com/team/blog/
├── shared/web/cursor/  CursorCodecTest                         (unit)
├── discovery/          HomeListIntegrationTest, BlogPageIntegrationTest,
│                       ListIndexExplainIntegrationTest, PageShellIntegrationTest   (integration)
└── post/               PostDetailIntegrationTest, PostDetailPermissionMatrixTest    (integration)

frontend/src/
├── pages/              HomePage, BlogPage, PostDetailPage (NotFoundPage는 004 T025 소유, 사용만)
├── components/         PostCard, PostCardGrid, LoadMoreButton, RelativeTime, AuthorChip,
│                       AuthorCard, ReactionBar, TagList, AuthorStatusBanner
├── features/
│   ├── post-list/      useCursorList (중복 ID 건너뛰기), listRestore (sessionStorage 30분)
│   └── post-detail/    useViewBeacon (1초 가시성 → POST /api/posts/{postId}/views), loadHighlighter
└── api/                posts.ts, members.ts

docker-compose.yml       (변경 없음: app + PostgreSQL + Redis + MinIO)
```

**Structure Decision**: 공통 구조(02 §3 package-by-feature, `backend/` + `frontend/`)를 그대로 쓴다. 목록·블로그·첫 응답(메타)은 읽기 전용 조회 모듈인 `discovery`, 글 상세 조회는 `post`(02 §3 `PostQueryService`), 커서·메타 렌더링·캐시 헤더처럼 다른 목록 기능(008·010·012·007)도 쓰는 것은 `shared`에 둔다. 권한 판정 클래스(`PostAccessPolicy`·`VisibilityFilter`)는 004가 만들고 이 기능은 호출만 한다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 원칙 II 부분 예외: `discovery` 카드 조회와 `post` 상세의 "글+작성자" 조회가 member·image 테이블을 **읽기 전용 JOIN**으로 읽는다 | C-READ-1 #6 "목록 한 번에 SQL 1번"과 10 §7의 단일 쿼리 설계, 상세 쿼리 최대 5번(40 §6)을 지키기 위해서다. 02 §3이 discovery를 "읽기 전용 조회" 모듈로 정했다. 쓰기는 하지 않고, 조회 조건은 004의 `VisibilityFilter`, 사진 주소 생성은 media의 `ImageUrlResolver`를 쓴다 | 모듈별 Service로 나눠 읽으면(글 → 회원 IN → 사진 IN) 쿼리 3번으로 카드 수에 비례하지는 않지만 "SQL 1번" 완료 기준을 어기고 응답 시간이 늘어난다. JOIN 대상 컬럼을 `PostCardQueryRepository` 한곳에 모아 다른 코드가 남의 테이블을 직접 읽지 않게 한다 |
