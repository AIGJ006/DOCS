# Data Model: 공개 범위와 권한

**Feature**: `004-visibility-permission` | **Date**: 2026-10-07 | **기준**: [docs/51-erd-unified.md](../../docs/51-erd-unified.md) (통합 ERD, 최신)

> **참고**: 51이 가리키는 `erd/V1__common_schema.sql`과 `erd/erdcloud-export.sql` 파일은 **이 저장소에 없다**(README "알려진 누락"). 이 문서는 51 본문의 컬럼표·제약표·"ERD 변경 제안" SQL 블록을 기준으로 한다. 51은 이 SQL 블록이 V1 파일과 같다고 적고 있다. [03](../../docs/03-erd.md)은 설계 결정(E-10·E-17)을 확인하는 데만 참고했다.

이 기능은 **공통 스키마에 새 테이블·컬럼을 추가하지 않는다.** 기존 컬럼으로 판정하고(42 "ERD 변경 제안: 없음"), 선택 구현 `FRIENDS`만 CHECK 교체와 인덱스 추가 마이그레이션을 한다(06 §6-1).

---

## 1. 엔티티와 이 기능이 쓰는 컬럼

### 1-1. `post` — 글 (51 §2 post, 23개 중 이 기능 관련)

| 컬럼 | 타입 (PostgreSQL) | NULL | 기본값 | 이 기능에서의 의미 |
|---|---|---|---|---|
| `id` | bigint identity | 불가 | — | PK |
| `author_id` | bigint | 불가 | — | FK → member(id) RESTRICT. 소유 검사 `author_id = :me` |
| `status` | varchar(20) | 불가 | `'DRAFT'` | `DRAFT` / `PUBLISHED`. 공개 범위는 `PUBLISHED`에서만 노출 의미가 있음 |
| `visibility` | varchar(20) | 불가 | `'PUBLIC'` | `PUBLIC` / `PRIVATE` (적용자 `FRIENDS`). 임시글에서는 "발행할 때 쓸 값" |
| `edit_version` | bigint | 불가 | 0 | 공개 범위 변경 시 **바꾸지 않음** |
| `published_at` | timestamptz | 허용 | — | 최초 발행 일자. `FRIENDS` 친구 블로그 정렬 키 |
| `first_public_at` | timestamptz | 허용 | — | 최초 공개 일자. 처음 `PUBLISHED`+`PUBLIC`이 될 때 한 번만 기록. 공개 목록 정렬 키 |
| `edited_at` | timestamptz | 허용 | — | 재발행 일자("수정됨"). 공개 범위 변경 시 **바꾸지 않음** (V-6) |
| `updated_at` | timestamptz | 불가 | `CURRENT_TIMESTAMP` | 값이 실제로 바뀐 경우에만 애플리케이션이 갱신 |
| `deleted_at` | timestamptz | 허용 | — | 휴지통. NULL이 아니면 작성자에게도 상세 404 |
| `hidden_at` | timestamptz | 허용 | — | 관리자 숨김. 목록 전체에서 제외, 상세는 작성자에게만 |
| `hidden_by` | bigint | 허용 | — | FK → member(id) RESTRICT (014 담당) |
| `hidden_reason` | varchar(30) | 허용 | — | 작성자 안내 문구용 (014 담당) |

관련 제약 (51 §3 post):

| 이름 | 정의 | 이 기능과의 관계 |
|---|---|---|
| `ck_post_status` | `status IN ('DRAFT','PUBLISHED')` | |
| `ck_post_visibility` | `visibility IN ('PUBLIC','PRIVATE')` | 공통 허용값. 적용자는 `FRIENDS` 추가로 교체 |
| `ck_post_public_at` | `NOT (status='PUBLISHED' AND visibility='PUBLIC') OR first_public_at IS NOT NULL` | 공개로 바꿀 때 `first_public_at`을 같은 UPDATE에서 채워야 통과 |
| `ck_post_published` | `status='DRAFT' OR (published_at IS NOT NULL AND length(btrim(title))>0)` | 변경 없음 |
| `ck_post_edited_at` | `edited_at IS NULL OR (published_at IS NOT NULL AND edited_at >= published_at)` | 변경 없음 |

관련 인덱스 (51 §3 post, 공용 조건 = 인덱스 조건, 06 R-2b):

| 이름 | 정의 |
|---|---|
| `ix_post_feed` | `ON post (first_public_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL` |
| `ix_post_blog` | `ON post (author_id, first_public_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL` |
| `ix_post_manage` | `ON post (author_id, status, updated_at DESC) WHERE deleted_at IS NULL` (내 글 관리, 006) |
| `ix_post_trash` | `ON post (author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL` (휴지통, 006) |

JPA 매핑: `Post` 엔티티에 `@SQLRestriction("deleted_at IS NULL")`(13 §2-6 #1)을 붙인다. 휴지통 조회·복구·완전 삭제는 별도 네이티브 쿼리를 쓴다(006 담당). `visibility`는 `@Enumerated(EnumType.STRING)`.

### 1-2. `member` — 회원 (51 §2 member, 이 기능 관련)

| 컬럼 | 타입 | NULL | 기본값 | 의미 |
|---|---|---|---|---|
| `id` | bigint identity | 불가 | — | PK, 세션의 현재 사용자 |
| `role` | varchar(20) | 불가 | `'USER'` | `USER` / `ADMIN` (`ck_member_role`) |
| `status` | varchar(20) | 불가 | `'ACTIVE'` | `ACTIVE` / `SUSPENDED` / `WITHDRAWN` (`ck_member_status`) |
| `default_visibility` | varchar(20) | 불가 | `'PUBLIC'` | 새 글의 시작 공개 범위 (`ck_member_default_visibility`: PUBLIC/PRIVATE) |
| `withdrawn_at` | timestamptz | 허용 | — | 탈퇴 신청 일자. `ck_member_withdrawn`: `(status='WITHDRAWN') = (withdrawn_at IS NOT NULL)`. 공용 목록 조건 `m.withdrawn_at IS NULL` |
| `deleted_at` | timestamptz | 허용 | — | 익명 처리 일자(`ck_member_deleted`: WITHDRAWN일 때만). 블로그 주소 404 판정 |

### 1-3. `auth_identity` — 로그인 수단 (이 기능 관련)

| 컬럼 | 타입 | NULL | 의미 |
|---|---|---|---|
| `member_id` | bigint | 불가 | FK, UNIQUE(`uq_auth_identity_member`): 회원당 하나 |
| `provider` | varchar(20) | 불가 | `LOCAL` / `GITHUB` / `GOOGLE` |
| `email_verified_at` | timestamptz | 허용 | NULL이면 **인증 전 회원**(이메일 가입만). 소셜 가입은 가입 시각으로 채워짐 (42 §2, 07) |

### 1-4. `member_suspension` — 회원 정지 이력 (참조, 014·001 담당)

| 컬럼 | 의미 |
|---|---|
| `member_id`, `reason`, `started_at`, `ends_at`(NULL=영구), `lifted_at` | 정지 회원 로그인 거부 문구 "정지된 계정이에요 (~`ends_at`까지). 사유: `reason`". 현재 정지 여부는 `member.status = 'SUSPENDED'`(51 §4) |

인덱스 `ix_member_suspension_member (member_id, started_at DESC)`.

### 1-5. `friendship` — 친구 관계 (참조, 001 담당. `FRIENDS` 적용자만 판정에 사용)

| 컬럼 | 의미 |
|---|---|
| `member_a_id` < `member_b_id` | 복합 PK, `ck_friendship_order` |
| `status` | `PENDING` / `ACCEPTED` (`ck_friendship_status`) |
| `accepted_at` | `ck_friendship_accepted` |

판정 쿼리: `EXISTS (SELECT 1 FROM friendship WHERE member_a_id = LEAST(:author,:viewer) AND member_b_id = GREATEST(:author,:viewer) AND status = 'ACCEPTED')` — PK 조회.

### 1-6. `post_draft`, `comment`, `post_like` (영향만)

- `post_draft`: 공개 범위 변경 시 **읽지도 쓰지도 않는다**(06 §4). 상세에서 "수정 중" 안내 여부(작성자)만 005가 확인.
- `comment`, `post_like`: 공개 범위를 바꿔도 행을 지우지 않는다(V-7). 보기·쓰기는 부모 글의 `canRead`를 먼저 통과해야 한다(FR-011, 007·009 담당). `post.like_count`·`comment_count`도 그대로.

---

## 2. 값 객체·도메인 타입 (DB 테이블 아님)

| 이름 | 모듈 | 필드 | 설명 |
|---|---|---|---|
| `Visibility` (enum) | post/domain | `PUBLIC`, `PRIVATE` (적용자 `FRIENDS` 추가) | DB 문자열과 같은 이름 |
| `Viewer` | shared/security | `Long id`(비회원 null), `Role role`, `MemberStatus status`, `boolean emailVerified` | 요청마다 세션의 회원 id로 구성 (research R-23). `anonymous()`, `isAuthorOf(post)`, `isAdmin()` |
| `VisibilityRule` (interface) | post/domain | `visibility()`, `canRead(PostView, Viewer)`, `listCondition(Viewer, Long authorId)` | 06 §7. 공통 Bean 2개: `PublicVisibilityRule`, `PrivateVisibilityRule` |
| `PostView` (읽기 투영) | post/infra | `id, authorId, status, visibility, deletedAt, hiddenAt, authorWithdrawnAt` | 상세 판정용 1회 JOIN 결과 |
| `ReasonCode` (enum) | shared/error | `LOGIN_REQUIRED, EMAIL_NOT_VERIFIED, ACCOUNT_WITHDRAWN, ACCOUNT_SUSPENDED, NOT_FOUND, INVALID_VISIBILITY, …` | 오류 본문 `code` |

---

## 3. 검증 규칙

| 규칙 | 위치 | 출처 |
|---|---|---|
| 공개 범위 값은 등록된 `VisibilityRule`의 `visibility()` 집합 안에 있어야 함, 아니면 400 `INVALID_VISIBILITY` | `PostVisibilityService` (⑤단계) + DB `ck_post_visibility` | 06 §4, FR-020 |
| 기본 공개 범위 값도 같은 집합, 아니면 400 `INVALID_VISIBILITY` | account 설정 Service(001)가 post 모듈의 `VisibilityRegistry`를 호출 + `ck_member_default_visibility` | 06 §5, FR-023·024 |
| 소유: `author_id = 세션 회원 id AND deleted_at IS NULL` | Repository 조회 조건 | 02 §5, 42 §3 |
| 요청 본문의 작성자·회원 번호 필드는 DTO에 없음(무시) | Controller DTO | 42 §12 #2 |
| `first_public_at`은 NULL일 때만 채움 | UPDATE의 CASE 식 + 엔티티 메서드 | 05 §3, 06 §4 |

---

## 4. 상태 전이

### 4-1. 글 노출 판정 입력 (status × visibility × deleted_at × hidden_at × author.status)

```
              ┌──── 공개 범위 변경(작성자, 즉시) ────┐
              ▼                                       │
 DRAFT(visibility=v) ──발행(002)──▶ PUBLISHED(PUBLIC) ◀──▶ PUBLISHED(PRIVATE)
      │  (값만 저장)                    │      ▲               │
      │                                 ▼      │ 복구(006)     ▼
      └────────삭제(006)──────────▶  휴지통(deleted_at≠NULL) ◀──┘
                                        │
                                        └─30일/영구 삭제─▶ (행 없음)

 직교 상태:  hidden_at ≠ NULL (관리자 숨김/해제, 014)  — 공개 범위 변경·재발행으로 풀리지 않음
             author.status = WITHDRAWN (탈퇴 유예/복구, 015) — 모든 글이 남에게 404
```

### 4-2. `PUT /api/posts/{postId}/visibility` 처리 (잠금 후)

| 현재 상태 | 요청 값 | 결과 |
|---|---|---|
| 휴지통 / 없음 / 남의 글 | 아무 값 | 404, 변경 없음 |
| 아무 상태 | 등록되지 않은 값 | 400 `INVALID_VISIBILITY`, 변경 없음 |
| 아무 상태 | 현재와 같은 값 | 200, **변경 없음**(`updated_at`도 그대로), 이벤트 없음 |
| `DRAFT` | 다른 값 | `visibility`·`updated_at`만 변경, 이벤트 없음 (research R-25) |
| `PUBLISHED`, `first_public_at IS NULL` | `PUBLIC` | `visibility=PUBLIC`, `first_public_at=now()`, `updated_at=now()` → `PostVisibilityChanged` + `PostWentPublic` |
| `PUBLISHED`, `first_public_at` 있음 | `PUBLIC` | `visibility=PUBLIC`, `updated_at=now()` (`first_public_at` 유지) → `PostVisibilityChanged` |
| `PUBLISHED` | `PRIVATE` | `visibility=PRIVATE`, `updated_at=now()` → `PostVisibilityChanged` |
| 숨김 글 (`hidden_at` 있음) | 위와 같음 | 위와 같이 처리, `hidden_at`은 유지 (43 §4-1) |

항상 불변: `edited_at`, `edit_version`, `published_at`, `post_draft`, 댓글·좋아요 행과 카운터.

### 4-3. 행위자(계정 상태) 전이 — 참조 (001·014·015 담당)

```
ACTIVE ──정지(관리자)──▶ SUSPENDED  [그 회원의 모든 Redis 세션 즉시 삭제]
SUSPENDED ──기간 만료 후 로그인 / 관리자 해제──▶ ACTIVE
ACTIVE ──탈퇴 신청──▶ WITHDRAWN(withdrawn_at)  [세션 삭제, 다시 로그인하면 복구 화면만]
WITHDRAWN ──복구──▶ ACTIVE  /  ──30일──▶ WITHDRAWN + deleted_at(익명 처리)
email_verified_at: NULL ──인증──▶ 일자 (되돌림 없음)
```

---

## 5. 선택 구현: `FRIENDS` 적용 마이그레이션 (06 §6-1, 적용자만)

`backend/src/main/resources/db/migration/V{n}__friends.sql` — 공통 기준선에는 포함하지 않는다.

| 변경 | 내용 |
|---|---|
| CHECK 교체 | `ck_post_visibility`, `ck_member_default_visibility` → `IN ('PUBLIC','FRIENDS','PRIVATE')` |
| 인덱스 추가 | `ix_post_blog_friends ON post (author_id, published_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility IN ('PUBLIC','FRIENDS') AND deleted_at IS NULL AND hidden_at IS NULL` |
| 코드 | `Visibility.FRIENDS` + `FriendsVisibilityRule` Bean (친구 블로그 목록 조건·친구 판정) |

`FRIENDS` 글은 `first_public_at`이 NULL이므로 `ix_post_feed`·`ix_post_blog`에 들어가지 않는다. `FRIENDS → PUBLIC`으로 바꾸면 그 순간 `first_public_at`이 채워진다.

---

## 6. 개인 확장 / 추가 제안

- 없음. 이 기능은 51에 없는 테이블·컬럼·인덱스를 만들지 않는다. (강성찬 그룹·링크 공개, 여러 글 공개 범위 한 번에 바꾸기는 개인 확장이며 이 계획의 범위 밖이다.)
