# Research: 2단계 카테고리

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-08

원문에는 카테고리의 자리(01 §2-4 `category` + `post.category_id`, 02 §7 "post에 nullable 컬럼 + category 모듈")만 있다. 아래 결정은 이 계획이 처음 정한 것이며 모두 민서 개인 확장 범위다(공통 코드·ERD 기준선 변경 없음).

## R1. 블로그 = 회원, 카테고리는 회원에게 딸린다

- **Decision**: `category.member_id`로 회원에게 직접 붙인다. `blog` 테이블은 만들지 않는다.
- **Rationale**: 지금 서비스의 블로그 주소·머리말·목록이 모두 회원(`/@handle`) 기준이다. 블로그 분리는 별도 확장이며, 나중에 하더라도 `category.member_id` → `blog_id` 이동은 마이그레이션 한 번이다.
- **Alternatives**: `blog` 테이블을 먼저 만들기 — 범위가 커지고 001·005 화면 전체를 건드린다.

## R2. 테이블과 2단계 보장

- **Decision**: 자기 참조 `parent_id`(NULL = 최상위) 하나로 두고, 2단계는 서비스에서 "상위의 `parent_id`가 NULL"인지로 확인한다. 같은 회원의 카테고리 쓰기는 `member` 행을 `SELECT … FOR UPDATE`로 잠가 한 줄로 세운다(동시 생성·이동·삭제가 서로의 확인을 지나치지 않게).
- **Rationale**: 2단계 고정이면 경로 열거(materialized path)·클로저 테이블이 필요 없다. 깊이를 DB 제약으로 막으려면 트리거가 필요하므로 잠금 + 서비스 확인이 단순하다. 회원 한 명의 카테고리 쓰기는 드물어 잠금 경합이 없다.
- **Alternatives**: `depth` 컬럼 + CHECK — 상위의 깊이를 보장하려면 결국 트리거가 필요하다.

## R3. 이름 중복은 정리한 이름의 소문자 키로

- **Decision**: 이름 정리 = NFC → 앞뒤 공백 제거 → 안쪽 공백 묶음 한 칸. 키 = `name.toLowerCase(Locale.ROOT)`를 `name_key`에 저장. 유일 인덱스 `uq_category_sibling_name (member_id, COALESCE(parent_id, 0), name_key)`. 위반은 409 `CATEGORY_NAME_DUPLICATED`(서비스가 먼저 확인하고, 잠금이 놓친 경우 DB 예외도 같은 코드로 바꾼다).
- **Rationale**: 같은 상위 안에서만 겹치면 안 된다(티스토리와 같음). `COALESCE`로 최상위끼리도 막는다.

## R4. 순서는 `position` 정수, 형제 묶음 통째로 저장

- **Decision**: `PUT /api/me/categories/order {parentId, ids}` — 보낸 `ids` 집합이 지금 그 상위의 하위 집합과 정확히 같아야 하고(아니면 409 `CATEGORY_ORDER_STALE`), 같으면 0부터 다시 매긴다. 새 카테고리·옮겨 온 카테고리는 `max(position)+1`.
- **Rationale**: [위로]·[아래로]는 화면이 이웃과 자리를 바꾼 배열을 보내면 된다. 묶음 비교로 두 탭 경쟁을 잡는다. `position` 유일 인덱스는 두지 않는다(다시 매길 때 일시 충돌) — 정렬은 `position, id`.

## R5. 글의 카테고리는 즉시 저장하는 상태 지정 (`PUT /api/posts/{id}/category`)

- **Decision**: 발행 요청(002)에 넣지 않고, 공개 범위 변경(004 `PUT /visibility`)처럼 따로 바로 저장한다. 판정 순서 401 → 403(`CONTENT_WRITE`) → 404(내 글 아님·휴지통, 행 잠금) → 400 `INVALID_CATEGORY`. "수정됨"·편집 버전·이벤트 없음.
- **Rationale**: 발행 흐름(멱등 키 해시·자동 저장·버전 충돌)을 건드리지 않아 002와 병렬로 작업하는 다른 기능과 부딪히지 않는다. 카테고리는 본문이 아닌 분류 정보라 즉시 반영이 티스토리 사용감과도 맞다.
- **Write 위치**: `post.category_id`는 post 테이블이므로 쓰기는 post 모듈의 공개 Service `PostCategoryAssignment`(잠금 + UPDATE)에 두고, category 모듈은 카테고리 소유 확인 후 그것을 부른다(원칙 II).

## R6. 블로그 카테고리 목록 = 카테고리 1번 + 글 수 1번

- **Decision**: `GET /api/members/{handle}/categories` → `{totalCount, items:[{id,name,postCount,children:[…]}]}`. ① 카테고리 전부(순서대로) ② `SELECT p.category_id, count(*) FROM post p JOIN member m … WHERE <VisibilityFilter.forViewer(viewer, ownerId)> AND p.category_id IS NOT NULL GROUP BY p.category_id`. 최상위 수 = 자기 + 하위 합(서비스에서 더함). `totalCount`는 005 `PostQueryRepository.countListedByAuthor`.
- **Rationale**: 카테고리 수와 상관없이 SQL이 고정(SC-004). 노출 조건은 004 조각 하나만 쓴다(008 `TagQueryRepository`와 같은 원칙 II 읽기 예외).

## R7. 카테고리 필터는 `CardFilter.categoryIds`

- **Decision**: `GET /api/members/{handle}/posts?category={id}` — category 모듈이 `{id}`가 그 블로그 카테고리인지 확인하고 자기 + 하위 번호 목록을 준다(SQL 1번). discovery는 `CardFilter`에 `categoryIds`를 더해 카드 SQL에 `AND p.category_id IN (:categoryIds)`를 붙인다. 커서 범위 `blog:{handle}:category:{id}`. 형식 오류(숫자 아님·1 미만)·없음·다른 블로그는 같은 404. 태그와 함께 오면 카테고리 조건만 쓴다(화면은 둘을 함께 보내지 않음).
- **Rationale**: discovery가 `category` 테이블을 읽지 않는다(번호 목록만 받음). 카드 SQL·정렬·커서를 그대로 쓴다.
- **인덱스**: `ix_post_category (category_id, first_public_at DESC, id DESC) WHERE category_id IS NOT NULL AND deleted_at IS NULL`.

## R8. 페이지 셸 404

- **Decision**: `PageShellController.blogShell`에서 태그 확인 뒤, 주인 확인 다음에 `?category=`가 있으면 `CategoryQueryService.findSubtreeIds(ownerId, raw)`가 비면 공통 404 화면. 301 대상 없음(번호라 정규화가 없다).

## R9. 글 상세 경로

- **Decision**: post 모듈에 포트 `PostCategoryPathQuery`(기본 구현 없음 — category 모듈 어댑터가 유일 Bean). `PostDetailView`에 `category`(`{id, name, parent: {id, name} | null}` 또는 `null`) 필드를 태그 다음에 더한다. 조회 실패는 005 `guard`로 `null`.

## R10. 탈퇴 정리 단계 order 15

- **Decision**: `CategoryWithdrawalPurgeStep` order 15 — order 10(글 완전 삭제) 바로 뒤에 `DELETE FROM category WHERE member_id = ? AND parent_id IS NOT NULL` → 최상위 삭제. 멱등.

## R11. 설정값

- `blog.category.max-count`(기본 100), `blog.category.name-max-length`는 DB 컬럼 길이와 묶여 있어 설정값으로 두지 않는다(30 고정).

## R12. 화면

- `/manage/categories` (로그인 전용) — 나무 목록, 각 줄 [이름 바꾸기]·상위 선택·[위로]·[아래로]·[삭제], 위에 [카테고리 추가] 입력(이름 + 상위 선택). 내 글 관리 머리에 [카테고리 관리] 링크.
- 글쓰기: 도구 막대 아래·제목 위에 `<select>`(분류 없음 + 최상위 + "　└ 하위"), 옆에 [카테고리 관리]. 고르면 `PUT` → 실패 시 되돌림 + 알림.
- 블로그: 데스크톱 2단(글 목록 | 오른쪽 카테고리), 768px 미만은 목록 위 `<details>` "카테고리". 고른 카테고리는 `aria-current="page"`.
- 글 상세: 제목 위 `nav.post-category` "개발 › Spring".
