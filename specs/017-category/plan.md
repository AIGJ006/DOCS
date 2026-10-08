# Implementation Plan: 2단계 카테고리

**Branch**: `017-category` (작업 브랜치 `claude/017-category-vu25oc`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-category/spec.md`

## Summary

블로그 주인이 2단계 카테고리를 만들고(이름·상위·순서·삭제), 글쓰기 화면에서 글마다 카테고리를 바로 지정하며, 독자는 블로그 사이드바에서 카테고리별 공개 글 수를 보고 카테고리 글만 모아 보고, 글 상세 제목 위에서 카테고리 경로를 본다.

기술 접근 (근거는 [research.md](./research.md)):

- **스키마 추가만**: Flyway `V3__category.sql` — `category` 테이블, `post.category_id` nullable 컬럼(`ON DELETE SET NULL`), 인덱스 2개(R2·R3·R7).
- **새 모듈 `category`**: `web`·`application`·`domain`·`infra`. 카테고리 쓰기는 회원 행 잠금으로 줄 세우고 2단계·이름 중복·개수 상한을 서비스에서 확인한다(R2~R4).
- **글의 카테고리 = 즉시 저장 상태 지정**: `PUT /api/posts/{id}/category`. post 테이블 쓰기는 post 모듈 `PostCategoryAssignment`가 하고 category 모듈이 소유 확인 후 부른다(R5).
- **블로그 목록**: 카테고리 목록은 SQL 2번(R6), 카테고리 필터는 005 카드 SQL에 `CardFilter.categoryIds` 조건 하나(R7), 페이지 셸 404(R8).
- **글 상세**: 포트 `PostCategoryPathQuery` + `PostDetailView.category`(R9).
- **탈퇴**: `CategoryWithdrawalPurgeStep` order 15(R10).
- **화면**: `/manage/categories`, 글쓰기 카테고리 선택, 블로그 사이드바·필터 머리, 글 상세 경로(R12).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript + React 18 (화면)

**Primary Dependencies**: Spring Boot 4.1.1, `JdbcClient`, Flyway, 001 `AccountStatusGuard`·`MemberQueryService.findReadableBlogOwner`, 004 `VisibilityFilter`, 005 `PostCardQueryRepository`·`PostListService`·`BlogQueryService`·`PageShellController`·`PostDetailAssembler`, 015 `WithdrawalPurgeStep`. 새 라이브러리 없음

**Storage**: PostgreSQL `category`(category 소유), `post.category_id`(post 소유, 쓰기는 post 모듈만). Redis 사용 없음

**Testing**: JUnit 5 + Testcontainers(PostgreSQL·Redis) + MockMvc, Vitest + Testing Library

**Target Platform**: Docker Compose(app + PostgreSQL + Redis + MinIO + Mailpit), 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 + React SPA)

**Performance Goals**: 블로그 카테고리 목록 SQL 2번(+주인 1번), 카테고리 글 목록 = 주인 1번 + 카테고리 확인 1번 + 카드 1번, 1만 건 300ms

**Constraints**: 공통 ERD 테이블·컬럼 변경 없음(추가만). 발행·자동 저장 흐름 변경 없음

**Scale/Scope**: 회원당 카테고리 최대 100개, API 9개, 화면 1개 + 기존 화면 3곳 확장

## Constitution Check

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 추가만 | **PASS** | 새 테이블 `category` + `post.category_id` nullable 컬럼 + 인덱스만 추가(02 §7 "공통 코드 수정: 없음 (컬럼 추가)"). 공통 Tier A·B 완료 기준에 영향 없음 |
| II. 모듈러 모놀리스 | **PASS (읽기 예외 1건 — Complexity Tracking)** | 새 `category` 모듈. `post.category_id` 쓰기는 post 모듈 `PostCategoryAssignment`만. discovery는 `category`를 읽지 않고 번호 목록을 받는다. 예외: 카테고리별 공개 글 수 SQL이 `post`·`member`를 읽는다(008 태그 글 수와 같은 종류) |
| III. 권한 두 겹, 404 | **PASS** | 관리 API는 세션 사용자 것만 조회(남의 것·없는 것 같은 404). 글 지정은 004 판정 순서. 블로그 목록·글 수는 `VisibilityFilter.forViewer` 하나 |
| IV. 콘텐츠는 실행되지 않는다 | **PASS** | 카테고리 이름은 React 텍스트 노드로만 출력 |
| V. 부가 기능 실패가 읽기를 막지 않는다 | **PASS** | 글 상세의 경로 조회 실패는 `null`. 블로그 사이드바 조회 실패는 화면이 사이드바만 숨긴다 |
| VI. 정책대로 지운다 | **PASS** | 카테고리 삭제 시 글은 남고 분류만 비운다(`ON DELETE SET NULL`). 탈퇴 정리 order 15. 스키마는 Flyway |
| VII. 수치는 설정값 | **PASS** | `blog.category.max-count`(100). 페이지 크기는 005 `blog.list.page-size` 공유 |
| VIII. 실제 DB 통합 테스트 | **PASS** | 관리·지정·블로그 목록·상세·탈퇴 정리를 Testcontainers IT로, 권한(남의 것·계정 상태) 포함 |

**설계 후 재확인**: data-model·contracts 작성 후 새 위반 없음. 002 발행 흐름을 바꾸지 않으므로 011~016과 겹치는 파일은 `CardFilter`·`PostCardQueryRepository`·`BlogQueryService`·`BlogController`·`PageShellController`·`PostDetailView`·`PostDetailAssembler`와 화면 `BlogPage`·`PostDetailPage`·`EditorPage`·`ManagePostsPage`·`App.tsx`뿐이며 모두 줄 추가다.

## Project Structure

### Documentation (this feature)

```text
specs/017-category/
├── spec.md
├── plan.md              # This file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
├── checklists/requirements.md
├── tasks.md
└── analysis.md          # /speckit-analyze 결과
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/V3__category.sql
backend/src/main/java/com/team/blog/
├── category/
│   ├── web/MyCategoryController.java          # /api/me/categories (GET·POST·PATCH·DELETE·PUT order)
│   ├── web/PostCategoryController.java        # /api/posts/{id}/category (GET·PUT)
│   ├── web/BlogCategoryController.java        # /api/members/{handle}/categories
│   ├── application/CategoryService.java       # 만들기·수정·순서·삭제
│   ├── application/CategoryQueryService.java  # 내 나무·블로그 나무·하위 번호·경로
│   ├── application/PostCategoryService.java   # 글 지정·읽기 (post 모듈 PostCategoryAssignment 호출)
│   ├── application/CategoryPathQueryAdapter.java       # post 포트 구현
│   ├── application/CategoryWithdrawalPurgeStep.java    # order 15
│   ├── application/CategoryProperties.java    # blog.category.*
│   ├── domain/CategoryName.java               # 이름 정리·키
│   ├── domain/CategoryReasonCode.java
│   └── infra/CategoryRepository.java          # JdbcClient
├── post/application/PostCategoryAssignment.java   # 잠금 + UPDATE post.category_id
├── post/application/port/PostCategoryPathQuery.java
├── post/application/PostDetailView.java           # + category
├── post/application/PostDetailAssembler.java      # + 경로 (guard)
├── discovery/application/CardFilter.java          # + categoryIds
├── discovery/infra/PostCardQueryRepository.java   # + IN (:categoryIds)
├── discovery/application/BlogQueryService.java    # + category 필터
├── discovery/web/BlogController.java              # + ?category=
├── discovery/web/PageShellController.java         # + ?category= 404
└── shared/web/cursor/ListScope.java               # + blogCategory

frontend/src/
├── api/categories.ts
├── features/category/ (CategoryTree.ts, CategorySelect.tsx, BlogCategoryNav.tsx, CategoryPath.tsx, category.css)
├── pages/ManageCategoriesPage.tsx              # /manage/categories
├── pages/EditorPage.tsx                        # 제목 위 CategorySelect
├── pages/BlogPage.tsx                          # 사이드바 + ?category=
├── pages/PostDetailPage.tsx                    # 제목 위 CategoryPath
├── pages/ManagePostsPage.tsx                   # [카테고리 관리] 링크
└── App.tsx                                     # 경로 1개
```

**Structure Decision**: 02 §7의 "category 모듈" 그대로 새 패키지를 둔다. 기존 파일은 확장 자리만 더한다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `category.infra.CategoryRepository`의 카테고리별 공개 글 수 SQL이 `post`·`member`를 읽기 전용으로 읽는다(원칙 II 읽기 예외) | 노출 조건(004 `VisibilityFilter` 조각, 별칭 `p`·`m`)과 `GROUP BY category_id`를 한 SQL로 해야 SQL 수가 카테고리 수와 무관하다(SC-004) | 카테고리마다 post 모듈에 글 수를 물으면 카테고리 수만큼 SQL(N+1). post 모듈에 "카테고리별 수" API를 두면 post가 카테고리 개념을 알게 된다 |
