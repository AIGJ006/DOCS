# Data Model: 2단계 카테고리

**Feature**: [spec.md](./spec.md) | **Migration**: `V3__category.sql`

## 1. `category` (새 테이블, category 모듈 소유)

| 컬럼 | 타입 | 규칙 |
|---|---|---|
| `id` | `bigint GENERATED ALWAYS AS IDENTITY` | PK |
| `member_id` | `bigint NOT NULL` | 주인. `fk_category_member → member(id) ON DELETE CASCADE` |
| `parent_id` | `bigint NULL` | 상위(없으면 최상위). `fk_category_parent → category(id) ON DELETE RESTRICT`. 상위의 `parent_id`는 NULL이어야 한다(서비스 확인, 2단계) |
| `name` | `varchar(30) NOT NULL` | 정리한 이름(NFC·앞뒤 공백 제거·안쪽 공백 한 칸), 1~30자 |
| `name_key` | `varchar(60) NOT NULL` | `lower(name, Locale.ROOT)` — 중복 비교 키 |
| `position` | `integer NOT NULL` | 같은 상위 안 순서(0부터). 정렬 `position, id` |
| `created_at`·`updated_at` | `timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

제약·인덱스

- `ck_category_name CHECK (char_length(name) BETWEEN 1 AND 30)`
- `ck_category_parent_self CHECK (parent_id IS NULL OR parent_id <> id)`
- `ck_category_position CHECK (position >= 0)`
- `uq_category_sibling_name UNIQUE (member_id, COALESCE(parent_id, 0), name_key)` — 같은 상위 안 이름 유일(FR-004)
- `ix_category_member (member_id, parent_id, position)` — 나무 읽기
- `ix_category_parent (parent_id) WHERE parent_id IS NOT NULL` — 하위 확인·FK

## 2. `post.category_id` (추가 컬럼, post 모듈 소유)

- `category_id bigint NULL`, `fk_post_category → category(id) ON DELETE SET NULL` (FR-011: 카테고리를 지우면 분류 없음)
- `ix_post_category (category_id, first_public_at DESC, id DESC) WHERE category_id IS NOT NULL AND deleted_at IS NULL` — 카테고리 글 목록·글 수
- JPA `Post` 엔티티에는 매핑하지 않는다(발행·자동 저장 UPDATE가 이 컬럼을 건드리지 않게). 쓰기는 `PostCategoryAssignment`의 SQL만

## 3. 규칙 요약

| 규칙 | 어디서 |
|---|---|
| 2단계 | `CategoryService` — 상위가 내 최상위여야 함, 하위가 있는 카테고리는 다른 상위 아래로 못 감 |
| 이름 1~30자 | `CategoryName.of` → 칸 오류, DB CHECK |
| 같은 상위 안 이름 유일 | 서비스 확인 + `uq_category_sibling_name` |
| 회원당 최대 개수 | `blog.category.max-count` |
| 동시 쓰기 | `SELECT id FROM member WHERE id = ? FOR UPDATE` 로 회원 단위 직렬화 |
| 삭제 | 하위 있으면 409, 없으면 DELETE (글은 SET NULL) |

## 4. 상태 전이

카테고리에는 상태가 없다(있음 / 지워짐). 글의 카테고리는 `NULL ↔ 카테고리 번호`로 언제든 바뀌며 글 상태(임시·발행·공개 범위)와 독립이다. 휴지통 글은 지정할 수 없지만 값은 그대로 남고 복원하면 그대로다.

## 5. 응답 형태

- `CategoryNode { id, name, postCount, children: CategoryNode[] }` — 하위의 `children`은 항상 `[]`
- `MyCategories { maxCount, items: CategoryNode[] }` — `postCount` = 내 글 수(휴지통 제외, 상태 무관), 최상위는 하위 포함
- `BlogCategories { totalCount, items: CategoryNode[] }` — `postCount` = 블로그 목록 노출 조건의 글 수, 최상위는 하위 포함
- `PostCategory { categoryId | null }`
- `PostDetail.category: { id, name, parent: { id, name } | null } | null`
