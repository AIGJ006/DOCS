# Data Model: 글 읽기 (전체 글 목록·개인 블로그·글 상세)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-07

## 기준과 범위

- 기준 스키마는 [docs/51-erd-unified.md](../../docs/51-erd-unified.md)(통합 ERD, 2026-10-07)다. [docs/03-erd.md](../../docs/03-erd.md)는 설계 결정 참고용으로만 본다.
- 51과 원문이 가리키는 `erd/V1__common_schema.sql`(UNIQUE·CHECK·인덱스의 원본)과 `erd/erdcloud-export.sql`은 **이 저장소에 없다**(README "알려진 누락"). 그래서 아래 제약·인덱스 이름과 정의는 51 §3 표를 기준으로 적었다.
- 이 기능은 **읽기 전용**이다. 새 테이블·컬럼·인덱스·마이그레이션이 없다(constitution 원칙 I). 아래 "뷰 모델"은 DB에 저장하지 않는 응답 형태다.
- 51에 없는 것을 새로 만든 것은 없다. 원문에 없어 정한 응답 필드·커서 필드는 "추가 제안"으로 표시했다.

---

## 1. 읽는 테이블과 컬럼

### post (51 `post`, 23개 중 사용 컬럼)

| 컬럼 | 타입(PostgreSQL) | NULL | 쓰는 곳 | 규칙 |
|---|---|---|---|---|
| id | bigint | 불가 | 목록·상세·커서 | 커서 동순위 가름(큰 순) |
| author_id | bigint FK → member | 불가 | 목록 JOIN·블로그 조건·작성자 판정 | |
| title | varchar(100) | 불가 | 카드·상세·메타 | 글자 그대로(이스케이프) |
| content_html | text | 불가 | **상세만** | 발행 때 정화된 값을 그대로 출력. 목록에서는 읽지 않음 |
| content_md | text | 불가 | **읽지 않음** | 목록·상세 모두 미조회 (C-READ-1 #6, 40 §6) |
| excerpt | varchar(200) | 허용 | 카드 미리보기, 메타 description(앞 160자) | NULL이면 빈 3줄 |
| thumbnail_url | varchar(500) | 허용 | 카드 썸네일, OG 원본 찾기 | NULL이면 빈 영역 / 기본 OG 이미지 |
| status | varchar(20) | 불가 | 노출 조건·작성자 상태 배지 | `DRAFT` / `PUBLISHED` |
| visibility | varchar(20) | 불가 | 노출 조건·캐시 헤더·배지 | `PUBLIC` / `PRIVATE` (`FRIENDS`는 적용자만) |
| view_count | bigint | 불가 | 상세 "조회 N" | 저장값 그대로. 카드에는 없음 |
| like_count | int | 불가 | 카드·상세 | 0도 표시 |
| comment_count | int | 불가 | 카드·상세 댓글 머리말 | 삭제·숨김 제외 수(21), 0도 표시 |
| published_at | timestamptz | 허용 | 작성자가 보는 비공개 글의 표시 날짜 | |
| first_public_at | timestamptz | 허용 | 정렬·커서·카드 날짜·공개 글 상세 날짜·`article:published_time` | 한 번 정해지면 불변 |
| edited_at | timestamptz | 허용 | "수정됨 · M월 D일", `article:modified_time` | 다시 발행했을 때만 값 |
| deleted_at | timestamptz | 허용 | 노출 조건 | NOT NULL이면 누구에게나 404(작성자 포함) |
| hidden_at | timestamptz | 허용 | 노출 조건·작성자 안내 | NOT NULL이면 목록 제외, 상세는 작성자에게만 |
| hidden_reason | varchar(30) | 허용 | 작성자 숨김 안내의 사유(표시 규칙은 014) | |

관련 제약(51 §3 post): `ck_post_status`, `ck_post_visibility`(PUBLIC/PRIVATE), `ck_post_published`, `ck_post_public_at`(공개 발행이면 `first_public_at` 필수), `ck_post_edited_at`, `ck_post_counts`.

### member (51 `member`)

| 컬럼 | 쓰는 곳 | 규칙 |
|---|---|---|
| id | JOIN, 작성자 판정 | |
| handle | 카드 작성자·블로그 주소·정규 주소·커서 `l` | 소문자 CHECK `ck_member_handle`. 화면 주소의 대문자는 301 |
| nickname | 카드·상세·메타 `{제목} - {닉네임}` | 익명 처리 후에만 NULL(그때는 블로그·글 모두 404라 노출 없음) |
| bio | 블로그 머리말·작성자 카드 | 글자만, 줄바꿈 유지 |
| status | 블로그 404 판정 | `WITHDRAWN`이면 404. `SUSPENDED`는 콘텐츠 계속 보임(42 P-7) |
| withdrawn_at | 노출 조건(`m.withdrawn_at IS NULL`), 블로그 404 | `ck_member_withdrawn`: `status='WITHDRAWN'` ⇔ `withdrawn_at IS NOT NULL` |
| deleted_at | 블로그 404(익명 처리) | `ck_member_deleted`: 익명 처리는 WITHDRAWN만 → `withdrawn_at` 조건으로 함께 걸러짐 |
| role | 관리자 판정(`canRead`, 004) | |

> `member.profile_image_url`은 2026-10-07에 삭제됐다(51 머리말, FK 40개). 40 §2·21 §6 SQL의 이 컬럼 참조는 쓰지 않는다.

### image (51 `image`) — 프로필 사진·OG 원본

| 컬럼 | 쓰는 곳 |
|---|---|
| uploader_id, purpose, status, detached_at | 프로필 사진 JOIN 조건: `purpose='PROFILE' AND status='ATTACHED' AND detached_at IS NULL` (회원당 1행, `uq_image_profile_current`) |
| storage_key, thumb_storage_key | 작은 프로필 사진 = `COALESCE(thumb_storage_key, storage_key)`, OG·블로그 메타 = `storage_key` |

- 주소 = 설정값 `blog.image.public-base-url` + `/` + 키 (03 E-21, 앱이 만든다).
- OG 원본 찾기(research R-26, 첫 응답 경로만): `thumbnail_url`에서 키를 떼어 `thumb_storage_key = :key`(`uq_image_thumb_key`)로 1행 조회 → `storage_key`.

### post_tag + tag (008 소유, Service로 조회)

- `post_tag(post_id, tag_id, position)` → `tag.name`, `ORDER BY position`. `uq_post_tag_position`, PK `(post_id, tag_id)`로 글 하나의 태그를 바로 찾는다.
- 링크 `/tags/{encodeURIComponent(name)}`.

### post_draft (002 소유, Service로 조회)

- 작성자가 볼 때만 `post_id`로 1행 존재 여부와 `updated_at`(마지막 저장 시각) — "수정 중인 내용이 있어요(10월 3일 14:03 저장)".
- 주의: 자동 저장은 Redis 버퍼를 거쳐 1분마다 DB에 반영되므로(02 §4-1-1) `updated_at`은 최대 약 1분 늦을 수 있다. 수동 저장은 즉시 반영.

### post_like, follow (009·010 소유, Service로 조회)

- 좋아요 여부: `EXISTS (post_like WHERE post_id = :postId AND member_id = :me)` — PK `(post_id, member_id)`.
- 팔로우 여부: `EXISTS (follow WHERE follower_id = :me AND followee_id = :authorId)` — PK `(follower_id, followee_id)`.
- 비회원·작성자 본인이면 조회하지 않는다.

### comment (007 소유)

- 이 기능은 `post.comment_count`만 직접 읽는다. 댓글 목록은 007 API(research R-33).

---

## 2. 인덱스 사용

| 조회 | 인덱스 (51 §3) | 조건 일치 |
|---|---|---|
| 홈 목록 | `ix_post_feed ON post (first_public_at DESC, id DESC) WHERE status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL` | 쿼리 WHERE에 인덱스 조건 4개를 모두 포함해야 한다(06 R-2b). `m.withdrawn_at IS NULL`은 JOIN 뒤 필터 |
| 블로그 목록·공개 글 수 | `ix_post_blog ON post (author_id, first_public_at DESC, id DESC) WHERE …(같은 조건)` | `author_id = :authorId` 추가 |
| 상세 | `post_pkey` | |
| 블로그 주인 | `uq_member_handle` | |
| 프로필 사진 JOIN | `uq_image_profile_current ON image (uploader_id) WHERE purpose='PROFILE' AND status='ATTACHED' AND detached_at IS NULL` | JOIN 조건이 인덱스 WHERE와 같아야 한다 |
| OG 원본 | `uq_image_thumb_key` | |
| 태그 | `post_tag_pkey` / `uq_post_tag_position` | |

검증: `ListIndexExplainIntegrationTest`가 홈·블로그 대표 쿼리의 `EXPLAIN`에 `ix_post_feed`·`ix_post_blog`가 나오는지 확인(06 R-2b, quickstart 시나리오 Q-9).

---

## 3. 노출·읽기 판정 (004 규칙의 적용)

### 3-1. 목록 공용 조건 (`VisibilityFilter`, 06 R-2a)

```
p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC'
AND p.deleted_at IS NULL AND p.hidden_at IS NULL
AND m.withdrawn_at IS NULL
[블로그] AND p.author_id = :authorId
[커서]   AND (p.first_public_at, p.id) < (:t, :id)
ORDER BY p.first_public_at DESC, p.id DESC LIMIT :pageSize + 1
```

작성자 본인 예외 없음(06 V-8). `FRIENDS` 적용자의 친구용 블로그 목록은 `visibility IN ('PUBLIC','FRIENDS')`, 정렬·커서 키 `(published_at, id)`, 인덱스 `ix_post_blog_friends`(06 §6-1, **선택 구현·공통 ERD 밖**).

### 3-2. 상세에서 보는 사람별 결과 (42 §5-1)

| 글 상태 | 비회원·인증 전·회원 | 작성자 | 관리자(남의 글) |
|---|---|---|---|
| PUBLISHED · PUBLIC | 200 | 200 + 작성자 버튼 | 200 |
| PUBLISHED · PRIVATE | 404 | 200 + 🔒 + "나만 볼 수 있는 글이에요", 날짜 `published_at`, `noindex`, `no-store` | 404 |
| PUBLISHED + `post_draft` 있음 | 마지막 발행본 | 마지막 발행본 + "수정 중" 안내(저장 시각) | 마지막 발행본 |
| DRAFT | 404 | 페이지: `302 /write/{postId}` / API: `status: DRAFT` + `editorPath` | 404 |
| `deleted_at` 있음(휴지통) | 404 | **404** | 404 |
| `hidden_at` 있음 | 404 | 200 + 숨김 안내 | 관리자 화면(014), 상세 경로는 004 판정을 따름 |
| 작성자 `withdrawn_at` 있음 | 404 | (복구 화면만, 42 P-12) | 404 |
| 없는 번호·숫자 아님 | 404 | 404 | 404 |

### 3-3. 블로그 주소 판정

| 상태 | 결과 |
|---|---|
| 대문자 포함(화면 주소) | 301 소문자 주소 |
| `uq_member_handle`에 없음 | 404 |
| `member.status='WITHDRAWN'`(탈퇴 유예, 익명 처리 포함) | 404 |
| `ACTIVE` / `SUSPENDED` | 200 |

---

## 4. 상태 전이와 목록·상세의 관계

이 기능은 상태를 바꾸지 않는다. 다른 기능의 전이가 읽기 결과에 미치는 영향:

| 전이 (소유 spec) | 목록 | 상세 |
|---|---|---|
| DRAFT → PUBLISHED·PUBLIC (002) | `first_public_at` = 지금 → 맨 위. 이어 보는 커서보다 최신이라 끼어들지 않음 | 200 |
| DRAFT → PUBLISHED·PRIVATE (002) | 안 나옴 | 작성자만 |
| PRIVATE → PUBLIC 처음 (004) | `first_public_at` 이때 정해짐 → 맨 위 | 200, "수정됨" 없음 |
| PUBLIC → PRIVATE (004) | 빠짐. 이미 화면의 카드는 남고 누르면 404 | 남에게 404 |
| 다시 발행 (002) | 순서 그대로(`first_public_at` 불변) | `edited_at` → "수정됨" |
| 휴지통 이동·복구 (006) | 빠짐 / 원래 `first_public_at` 자리로 복귀 | 404 / 복귀 |
| 관리자 숨김·해제 (014) | 빠짐 / 복귀 | 남에게 404, 작성자 안내 |
| 회원 탈퇴 신청·복구 (015) | 그 사람 글 전부 빠짐 / 복귀 | 404 / 복귀, 블로그 404 / 복귀 |

---

## 5. 뷰 모델 (응답 형태, DB에 저장하지 않음)

정확한 필드·타입은 [contracts/openapi.yaml](./contracts/openapi.yaml).

### PostCard (10 §4-2 형식 그대로)
`id, url(/@handle/posts/id), title, excerpt, thumbnailUrl, firstPublicAt, commentCount, likeCount, author{handle, nickname, profileImageUrl}`. 블로그 목록 카드도 같은 형식(화면이 작성자 영역만 생략).

### CursorPage<PostCard>
`items[0..9], nextCursor(string|null)`.

### Cursor (공용, shared)
| 필드 | 뜻 | 출처 |
|---|---|---|
| `v` | 형식 버전, 지금 1 | 10 §4-2 |
| `k` | 정렬 키 `[first_public_at epoch 마이크로초(UTC), id]` | 10 §4-2 |
| `l` | 목록 구분 `home` / `blog:{handle}` | **추가 제안**(research R-24) |

검증 실패(풀리지 않음·모르는 `v`·필드 누락·타입 오류·`l` 불일치) → 400 `INVALID_CURSOR`.

### BlogHeader — **추가 제안**(research R-23)
`handle, nickname, bio, profileImageUrl, publicPostCount, isMe`. 010이 팔로워 수 등 필드를 추가할 수 있다.

### PostDetail — **추가 제안**(research R-22, 필드는 40 §2 기준)
- 공통: `id, canonicalPath, status, visibility, title, contentHtml, hasCodeBlock, displayedAt, firstPublicAt, publishedAt, editedAt, tags[], likeCount, viewCount, commentCount, author{handle, nickname, profileImageUrl, bio}`
- 보는 사람 기준: `viewer{isAuthor, loggedIn, likedByMe, followingAuthor}`
- 작성자에게만: `authorView{hasDraft, draftSavedAt, hidden, hiddenReason}`
- 작성자의 임시글: `status: DRAFT`, `editorPath`만(본문 없음)

### LinkPreviewMeta (첫 응답, 서버 내부)
공개 글: `title, description(≤160), canonicalUrl, ogType=article, ogImage, publishedTime, modifiedTime?`. 그 밖: 공통 문구(`볼 수 없는 글이에요` / `친구 공개·비공개 글이거나 삭제된 글입니다.`) + `robots=noindex`.

---

## 6. 설정값 (원칙 VII)

| 키 | 기본값 | 근거 |
|---|---|---|
| `blog.list.page-size` | 9 | 10 L-2 (클라이언트 `size` 무시) |
| `blog.seo.description-length` | 160 | 40 §5 |
| `blog.seo.default-og-image-url` | (서비스 기본 이미지) | 40 §5 |
| `blog.site.base-url` | `https://{도메인}` | canonical 절대 주소 |
| `blog.image.public-base-url` | (공용, 003·12와 같은 값) | 03 E-21, 02 §5 |
| 프런트 `LIST_RESTORE_TTL_MINUTES` | 30 | 10 L-6 |
