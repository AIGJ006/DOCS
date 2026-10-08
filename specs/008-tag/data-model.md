# Data Model: 태그와 태그별 글 목록

**Feature**: `008-tag` | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

**기준**: `backend/src/main/resources/db/migration/V1__common_schema.sql`(51 통합 ERD를 옮긴 공통 스키마)의 `tag`·`post_tag`와 [docs/22-tag.md](../../docs/22-tag.md).

**스키마 변경**: **없음.** V1의 컬럼·제약·인덱스만 쓴다(헌법 I). 새 마이그레이션 번호를 쓰지 않는다. Tier A 임시 규칙으로 생긴 개발 데이터는 다시 만든다(Clarifications Q3, research R13).

---

## 1. 이 기능이 쓰는 엔티티

### 1-1. `tag` — 태그 (tag 모듈 소유, 읽기·쓰기)

| 컬럼 | 타입 | NULL | 이 기능에서의 쓰임 |
|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | `post_tag.tag_id`. 태그별 목록은 이름 → id를 먼저 찾는다 |
| `name` | varchar(30) UQ `uq_tag_name` | 불가 | 정규화된 이름. `ck_tag_name`: `^[가-힣a-z0-9._+#-]{1,30}$` AND `[가-힣a-z0-9]` 포함. `TagNormalizer`가 통과시킨 값만 들어온다(DB 제약은 마지막 방어선) |
| `created_at` | timestamptz | 불가 | 처음 쓰인 시각. 표시하지 않음 |

인덱스: `ix_tag_name_prefix (name varchar_pattern_ops)` — 자동완성 `LIKE 'q%'`.

규칙:

- 쓰는 글이 없어져도 지우지 않는다(FR-017). `fk_post_tag_tag … ON DELETE RESTRICT`가 쓰이는 태그 삭제도 막는다.
- 생성은 발행 트랜잭션의 `INSERT … SELECT unnest(:names) ON CONFLICT (name) DO NOTHING`(002 T046 그대로). 같은 새 태그로 동시 발행해도 `uq_tag_name`이 하나만 남긴다(SC-003).
- 이미 쓰이는 이름이 나중에 금칙어가 되는 경우는 범위 밖(spec Assumptions).

### 1-2. `post_tag` — 글-태그 연결 (tag 모듈 소유, 읽기·쓰기)

| 컬럼 | 타입 | NULL | 이 기능에서의 쓰임 |
|---|---|---|---|
| `post_id` | bigint FK → post CASCADE | 불가 | 글이 완전히 지워지면 연결도 지워진다(006) |
| `tag_id` | bigint FK → tag RESTRICT | 불가 | |
| `position` | smallint | 불가 | 입력 순서 0부터. `uq_post_tag_position (post_id, position)`, `ck_post_tag_position` 0~99 |

PK `(post_id, tag_id)` — 한 글에 같은 태그는 하나. 인덱스 `ix_post_tag_tag (tag_id, post_id)` — 태그별 목록·집계.

쓰기(발행 트랜잭션 안, 쿼리 3번, 002 T122 점검표): `DELETE FROM post_tag WHERE post_id = :postId` → 태그 `INSERT … ON CONFLICT DO NOTHING` → `INSERT INTO post_tag SELECT :postId, t.id, ord - 1 FROM unnest(:names) WITH ORDINALITY JOIN tag`. 트랜잭션이 실패하면 함께 되돌린다.

### 1-3. 읽기만 하는 테이블

| 테이블 | 쓰는 곳 | 조건 |
|---|---|---|
| `post` (`status`, `visibility`, `deleted_at`, `hidden_at`, `author_id`, `first_public_at`, `id`) | 태그별 목록·집계·자동완성 | 004 `VisibilityFilter` 조각만(별칭 `p`) |
| `member` (`withdrawn_at`) | 같은 곳 | `VisibilityFilter`의 `m.withdrawn_at IS NULL`(별칭 `m`) |
| `image` (프로필) | 태그별 카드 | 005 `PostCardQueryRepository` 그대로 |

## 2. 도메인 값 (코드, DB 아님)

### 2-1. `TagNormalization`

```java
public sealed interface TagNormalization {
    record Accepted(String name) implements TagNormalization {}
    record Rejected(TagReasonCode code) implements TagNormalization {}
}
```

### 2-2. `TagReasonCode` (tag.domain, `ReasonCode` 구현)

| code | status | message | 언제 |
|---|---|---|---|
| `INVALID_TAG` | 400 | 쓸 수 없는 글자가 있어요 | 허용 밖 문자, 한글·영문·숫자 없음, 정리 결과가 빈 값 (002 `PostReasonCode.INVALID_TAG`에서 옮김, 문구 같음) |
| `TAG_TOO_LONG` | 400 | 태그는 30자까지 쓸 수 있어요 | 정리 후 코드 포인트 30 초과 |
| `TAG_BANNED_WORD` | 400 | 쓸 수 없는 단어가 들어 있어요 | 001 `BannedWordFilter.containsBanned(name)` 참. 어떤 단어인지 응답·로그에 없음 |

개수 초과 `TOO_MANY_TAGS`(400 "태그가 너무 많아요", field `tags`)는 002 `PostReasonCode`에 남는다.

### 2-3. 발행 오류 응답 (공통 형식, O8)

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력한 내용을 확인해 주세요",
  "errors": [
    { "field": "tags", "code": "TOO_MANY_TAGS", "message": "태그가 너무 많아요" },
    { "field": "tags[2]", "code": "INVALID_TAG", "message": "쓸 수 없는 글자가 있어요" },
    { "field": "tags[5]", "code": "TAG_BANNED_WORD", "message": "쓸 수 없는 단어가 들어 있어요" }
  ],
  "details": null
}
```

- `tags[i]`의 `i`는 화면이 보낸 배열의 원래 번호(정리·중복 제거 전).
- 원문 22 §2-2의 `value` 항목은 넣지 않는다(O8, 002 T036).
- 개수 초과와 칸 오류가 함께 있으면 둘 다 담는다(research R3, SC-006).

### 2-4. 목록 구분 값 (`ListScope`, 커서 안 `l`)

| 목록 | 값 | 예 |
|---|---|---|
| 태그별 글 목록 | `tag:{name}` | `tag:spring-boot`, `tag:c#` |
| 블로그 태그 필터 | `blog:{handle}:tag:{name}` | `blog:kim755030:tag:jpa` |

태그 이름에는 공백·`:`이 없어 `ListScope` 규칙(공백 금지)을 지킨다. 다른 목록의 커서는 400 `INVALID_CURSOR`(005 R-24).

## 3. 응답 모델

| 이름 | 모양 | 쓰는 API |
|---|---|---|
| `TagSummary` | `{ name, postCount }` | `GET /api/tags/{name}/summary` |
| `TagCount` | `{ name, postCount }` | `GET /api/tags` 배열 원소, 블로그 태그 원소 |
| `TagIndex` | `{ items: TagCount[] }` | `GET /api/tags` (최대 100) |
| `TagSuggestion` | `{ name, postCount, mine }` | `GET /api/tags/suggest` 배열 원소 (최대 10) |
| `BlogTags` | `{ items: TagCount[], initialVisible }` | `GET /api/members/{handle}/tags` (최대 100, `initialVisible` = 10) |
| `PostCardPage` | 005 그대로 `{ items, nextCursor }` | `GET /api/tags/{name}/posts`, `GET /api/members/{handle}/posts?tag=` |

`postCount`는 응답 시점에 계산한다(저장하지 않음, Clarifications Q1).

## 4. Redis 키

| 키 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|
| `ratelimit:tag-suggest:{memberId}` | 고정 창 횟수 (001 `RateLimiter` Lua) | 창 길이(1분) | 자동완성 1분 60번 |

태그 목록 캐시 키는 없다(원문 `tags:top`은 쓰지 않음).

## 5. 설정값 (`blog.tag.*`, 기본값)

```yaml
blog:
  post:
    max-tags: 10          # (002, 기존) 한 글의 태그 최대 수
  tag:
    top-limit: 100
    blog-strip:
      limit: 100
      initial: 10
    suggest:
      limit: 10
      rate-limit:
        limit: 60
        window: 1m
```

## 6. 상태 전이

태그 자체에는 상태가 없다. "공개 글 수"는 글의 상태에서 매번 나온다.

| 글의 변화 | 그 글의 태그가 목록·글 수에서 | 반영 시점 |
|---|---|---|
| 전체 공개로 발행 | 들어감 | 다음 요청부터 |
| 비공개 전환·휴지통·관리자 숨김·작성자 탈퇴 신청 | 빠짐 | 다음 요청부터(캐시 없음, FR-029) |
| 복구·숨김 해제·탈퇴 철회 | 다시 들어감 | 다음 요청부터 |
| 완전 삭제 | 연결 삭제(CASCADE), 태그는 남음 | 즉시 |
| 태그만 바꿔 다시 발행 | 연결을 통째로 바꿈 + "수정됨"(002) | 즉시 |
