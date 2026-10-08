---

description: "Task list for 017-category (2단계 카테고리)"
---

# Tasks: 2단계 카테고리

**Input**: Design documents from `/specs/017-category/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml

**Tests**: 포함한다(헌법 VIII). 각 User Story Phase에서 통합 테스트를 먼저 두고 구현한다. `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `F/` = `frontend/src/`.

## Cross-feature Dependencies

- 선행(모두 main에 있음): 001 `AccountStatusGuard`·`MemberQueryService`, 004 `VisibilityFilter`, 005 카드·블로그·상세·셸, 008 `CardFilter`, 015 `WithdrawalPurgeStep`
- 동시 진행: 011·012·013·014(같은 저장소의 다른 스레드), 016 다크 모드. 겹칠 수 있는 파일은 plan "설계 후 재확인"의 목록이며 모두 줄 추가다. 머지 전 최신 main을 합치고 Flyway 번호(V3)가 겹치지 않는지 확인한다(T046)

## Phase 1: Setup

- [ ] T001 Flyway `backend/src/main/resources/db/migration/V3__category.sql` — `category` 테이블·제약·인덱스, `post.category_id` + `fk_post_category ON DELETE SET NULL` + `ix_post_category` (data-model §1·§2)
- [ ] T002 [P] `B/category/application/CategoryProperties.java` `blog.category.max-count`(기본 100) + `application.yml` 기본값 + `@ConfigurationPropertiesScan` 확인
- [ ] T003 [P] `B/category/domain/CategoryReasonCode.java` — `CATEGORY_NAME_REQUIRED`·`CATEGORY_NAME_TOO_LONG`·`CATEGORY_NAME_DUPLICATED`(409)·`CATEGORY_DEPTH_EXCEEDED`·`TOO_MANY_CATEGORIES`·`CATEGORY_HAS_CHILDREN`(409)·`CATEGORY_ORDER_STALE`(409)·`INVALID_CATEGORY`
- [ ] T004 [P] `B/category/package-info.java` 모듈 설명

## Phase 2: Foundational

- [ ] T005 [P] `T/category/unit/CategoryNameTest.java` — 공백 정리·NFC·1~30자·키 소문자
- [ ] T006 `B/category/domain/CategoryName.java` — 정리·검증(칸 오류)·`key()`
- [ ] T007 `B/category/infra/CategoryRepository.java` — 회원 잠금, 나무 읽기, 만들기·이름/상위/순서 바꾸기·지우기, 하위 수, 형제 번호, 내 글 수 GROUP BY, 공개 글 수 GROUP BY(`VisibilityFilter.forViewer`), 하위 번호, 경로
- [ ] T008 [P] `T/category/support/CategoryApi.java`·`CategoryFixtures.java` — 테스트 호출 도우미·직접 INSERT

## Phase 3: User Story 1 — 내 카테고리 만들고 정리하기 (P1)

- [ ] T010 [P] [US1] `T/category/integration/MyCategoryApiIT.java` — US1 #1~#12 (만들기·2단계·중복·칸 오류·순서·옮기기·삭제·상한·남의 것 404·비회원 401·인증 전 403)
- [ ] T011 [US1] `B/category/application/CategoryService.java` — create / update / reorder / delete (회원 잠금 → 확인 → 쓰기, DB 유일 위반 → 409)
- [ ] T012 [US1] `B/category/application/CategoryQueryService.java` — `myTree`(내 글 수, 최상위 하위 합)
- [ ] T013 [US1] `B/category/web/MyCategoryController.java` — `GET·POST /api/me/categories`, `PATCH·DELETE /api/me/categories/{id}`, `PUT /api/me/categories/order` (`Cache-Control: private, no-store`)
- [ ] T014 [P] [US1] `F/api/categories.ts` — 타입·호출 함수
- [ ] T015 [P] [US1] `F/features/category/categoryTree.ts` — 평탄화(선택 옵션)·형제 순서 바꾸기 계산 + 단위 테스트
- [ ] T016 [US1] `F/pages/ManageCategoriesPage.tsx` + `F/features/category/category.css` — 추가·이름 바꾸기·상위 바꾸기·위/아래·삭제 확인(글 수 문구), 오류 문구 표시, 375px
- [ ] T017 [US1] `F/App.tsx` `/manage/categories` 경로, `F/pages/ManagePostsPage.tsx` [카테고리 관리] 링크
- [ ] T018 [P] [US1] `F/pages/ManageCategoriesPage.test.tsx` — 목록 표시·추가·위로·하위 있는 삭제 문구

## Phase 4: User Story 2 — 글에 카테고리 고르기 (P1)

- [ ] T020 [P] [US2] `T/category/integration/PostCategoryApiIT.java` — US2 #1~#5 (임시·발행 지정, "수정됨"·편집 버전 불변, null, 남의 카테고리 400, 남의 글·휴지통 404, 401·403)
- [ ] T021 [US2] `B/post/application/PostCategoryAssignment.java` — 내 글 잠금(휴지통 제외) 확인 + `UPDATE post SET category_id` (post 모듈 소유 쓰기)
- [ ] T022 [US2] `B/category/application/PostCategoryService.java` — 판정 순서 401 → 403 → 404 → 400
- [ ] T023 [US2] `B/category/web/PostCategoryController.java` — `GET·PUT /api/posts/{postId}/category`
- [ ] T024 [US2] `F/features/category/CategorySelect.tsx` — 분류 없음 + 나무, 즉시 저장, 실패 시 되돌림·알림, [카테고리 관리] 링크
- [ ] T025 [US2] `F/pages/EditorPage.tsx` 제목 위에 `CategorySelect` 넣기 + `F/features/category/__tests__/CategorySelect.test.tsx`

## Phase 5: User Story 3 — 블로그에서 카테고리별로 보기 (P1)

- [ ] T030 [P] [US3] `T/category/integration/BlogCategoryIT.java` — US3 #1~#6 (수·하위 합·노출 조건·필터 9개·커서 범위·404·빈 카테고리·SQL 수)
- [ ] T031 [US3] `CategoryQueryService.blogTree`·`findSubtreeIds` + `B/category/web/BlogCategoryController.java`
- [ ] T032 [US3] `B/discovery/application/CardFilter.java` `categoryIds` + `B/discovery/infra/PostCardQueryRepository.java` `IN (:categoryIds)`
- [ ] T033 [US3] `B/shared/web/cursor/ListScope.java` `blogCategory(handle, id)` + `B/discovery/application/BlogQueryService.java` 필터 + `B/discovery/web/BlogController.java` `?category=`
- [ ] T034 [US3] `B/discovery/web/PageShellController.java` `/@{handle}?category=` 404 화면 + `T/category/integration/CategoryPageShellIT.java`
- [ ] T035 [US3] `F/features/category/BlogCategoryNav.tsx` — 분류 전체보기·나무·글 수·현재 강조, 375px 접힘
- [ ] T036 [US3] `F/pages/BlogPage.tsx` — 사이드바 2단 배치, `?category=` 목록·머리 "이름 · 글 N개 [전체 보기]", 태그 필터와 배타, 빈 문구 + `F/features/category/__tests__/BlogCategoryNav.test.tsx`

## Phase 6: User Story 4 — 글 상세 카테고리 경로 (P2)

- [ ] T040 [P] [US4] `T/category/integration/PostDetailCategoryIT.java` — 하위·최상위·분류 없음 경로, 조회 실패 시 null
- [ ] T041 [US4] `B/post/application/port/PostCategoryPathQuery.java` + `B/category/application/CategoryPathQueryAdapter.java`
- [ ] T042 [US4] `B/post/application/PostDetailView.java` `category` + `PostDetailAssembler` guard
- [ ] T043 [US4] `F/features/category/CategoryPath.tsx` + `F/pages/PostDetailPage.tsx` 제목 위

## Phase 7: Polish & Cross-Cutting

- [ ] T044 `B/category/application/CategoryWithdrawalPurgeStep.java` order 15 + `T/category/integration/CategoryWithdrawalPurgeIT.java`(FR-050)
- [ ] T045 `WithdrawalPurgeStep` javadoc 단계 표에 order 15 줄 추가
- [ ] T046 최신 main 합치기, Flyway 번호 확인, 백엔드 전체 테스트(`./mvnw verify`)·화면 `npm test`·`npm run lint`·`npm run build` 통과
- [ ] T047 `quickstart.md` 실행 기록, README 기능 표에 017 줄
