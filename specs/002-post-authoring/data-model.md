# Data Model: 글 작성·임시저장·발행

**Feature**: `002-post-authoring` | **Date**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

**스키마 기준**: [docs/51-erd-unified.md](../../docs/51-erd-unified.md) 통합 V1(2026-10-07). 51이 기준으로 가리키는 `erd/V1__common_schema.sql`은 **이 저장소에 없다**(README "알려진 누락"). 51 본문의 SQL 블록이 V1과 같다고 51이 밝히므로, 이 문서와 Flyway 기준선은 51의 SQL 블록을 따른다. [docs/03](../../docs/03-erd.md)은 설계 결정 참고용이다.

이 기능은 51의 테이블·컬럼·제약을 **바꾸지 않는다**(원칙 I). 51에 없는 것은 "추가 제안"으로 표시했다.

---

## 1. PostgreSQL 엔티티

### 1-1. `post` — 글 (소유: `post` 모듈)

| 컬럼 | 타입 | NULL | 기본값 | 이 기능에서의 쓰임 |
|---|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | — | 글 번호, 주소 `/@{handle}/posts/{id}` |
| `author_id` | bigint FK → member RESTRICT | 불가 | — | 소유 확인 `author_id = :me` (세션에서만) |
| `title` | varchar(100) | 불가 | `''` | 임시글의 저장 제목 / 발행본 제목. 발행 때 정리(NFC·불가시·방향·제어 문자 제거·trim) 후 1~100자 |
| `content_md` | text | 불가 | `''` | Markdown 원문, ≤ 100,000자(`ck_post_content`). 임시글은 자동 저장 반영, 발행 글은 발행본 |
| `content_html` | text | 불가 | `''` | 발행 때 한 번 만든 정화 HTML. 임시글 저장 때는 렌더링하지 않음 |
| `excerpt` | varchar(200) | 허용 | — | 발행 때 만든 요약(대상 글자가 없으면 `''` 또는 NULL — 화면은 둘 다 빈 요약) |
| `thumbnail_url` | varchar(500) | 허용 | — | 첫 번째 작성자 사진의 640px 썸네일 주소(없으면 원본 주소, 사진이 없으면 NULL) |
| `status` | varchar(20) | 불가 | `'DRAFT'` | `DRAFT` / `PUBLISHED` (`ck_post_status`) |
| `visibility` | varchar(20) | 불가 | `'PUBLIC'` | 새 글 때 `member.default_visibility`로 설정(DB 기본값에 기대지 않음), 발행 때 요청 값. `PUBLIC`/`PRIVATE` (`ck_post_visibility`) |
| `view_count`, `like_count`, `comment_count` | bigint/int | 불가 | 0 | 이 기능은 읽지도 바꾸지도 않음(다시 발행 때 유지). 엔티티 변경 감지 대상에서 제외(05 J-2) |
| `edit_version` | bigint | 불가 | 0 | 서버 편집 버전. 새 글 0, 받아들인 저장·발행·변경 취소마다 +1(단조 증가, research B-3). JPA `@Version` 금지 |
| `render_version` | integer | 불가 | 1 | 발행·다시 렌더링 때 `RENDER_VERSION` 기록 |
| `published_at` | timestamptz | 허용 | — | 최초 발행 때 한 번 `COALESCE(published_at, now())` |
| `first_public_at` | timestamptz | 허용 | — | 처음으로 `PUBLISHED` + `PUBLIC`이 된 순간 한 번 |
| `edited_at` | timestamptz | 허용 | — | 다시 발행할 때마다 `now()` |
| `created_at` | timestamptz | 불가 | now | [새 글] 시각, 빈 임시글 정리 조건 |
| `updated_at` | timestamptz | 불가 | now | 앱이 갱신: 새 글·임시글 저장 반영·발행·변경 취소. 다시 렌더링은 갱신하지 않음(research B-10) |
| `deleted_at` | timestamptz | 허용 | — | 006 소관. 이 기능의 API(새 글 제외 전부)는 Redis 키 유무와 상관없이 매 요청 DB에서 `deleted_at IS NULL`을 확인한다(휴지통 글은 404). 1분 DB 반영 배치만 이 조건을 쓰지 않는다(휴지통 직전 내용 보존) |
| `hidden_at`, `hidden_by`, `hidden_reason` | — | 허용 | — | 014 소관. 이 기능은 읽지도 바꾸지도 않음(숨김은 다시 발행해도 유지, 43 §4-1) |

**제약 (51 §3, 이 기능과 관련된 것)**

| 이름 | 정의 | 이 기능에서 |
|---|---|---|
| `ck_post_status` | `status IN ('DRAFT','PUBLISHED')` | 발행 → 임시글 전이 없음(P-1) |
| `ck_post_visibility` | `visibility IN ('PUBLIC','PRIVATE')` | 400 `INVALID_VISIBILITY`를 앱이 먼저 판정 |
| `ck_post_published` | `status = 'DRAFT' OR (published_at IS NOT NULL AND length(btrim(title)) > 0)` | 발행 검증(제목 필수)의 DB 최후 방어 |
| `ck_post_public_at` | `NOT (status='PUBLISHED' AND visibility='PUBLIC') OR first_public_at IS NOT NULL` | `first_public_at` CASE 규칙의 DB 방어 |
| `ck_post_edited_at` | `edited_at IS NULL OR (published_at IS NOT NULL AND edited_at >= published_at)` | 다시 발행만 `edited_at` 기록 |
| `ck_post_content` | `char_length(content_md) <= 100000` | 저장·발행 모두 400 `CONTENT_TOO_LONG`을 앱이 먼저 판정 |
| `fk_post_author` | → `member(id)` RESTRICT | — |

**인덱스**: `post_pkey`(모든 쓰기의 소유 확인·잠금 `WHERE id = ? AND author_id = ? AND deleted_at IS NULL`), `ix_post_manage (author_id, status, updated_at DESC) WHERE deleted_at IS NULL`(빈 임시글 정리 후보 검색에도 사용 가능), `ix_post_feed`·`ix_post_blog`(발행 후 목록 노출 — 005 소관). 다시 렌더링 배치의 `render_version < :cur` 검색은 인덱스 없이 PK 순 100개씩(글 1만 건, 드문 배치) — 새 인덱스를 만들지 않는다.

### 1-2. `post_draft` — 발행 글 작업본 (소유: `post` 모듈)

| 컬럼 | 타입 | NULL | 기본값 | 쓰임 |
|---|---|---|---|---|
| `post_id` | bigint PK, FK → post CASCADE | 불가 | — | 발행 글 하나에 0~1행 |
| `title` | varchar(100) | 불가 | `''` | 고치는 중인 제목(입력 그대로) |
| `content_md` | text | 불가 | `''` | 고치는 중인 본문, ≤ 100,000자(`ck_post_draft_content`) |
| `edit_version` | bigint | 불가 | 0 | 이 작업본 내용의 편집 버전(항상 `post.edit_version`보다 큼) |
| `created_at` | timestamptz | 불가 | now | 수정을 시작한 시각 |
| `updated_at` | timestamptz | 불가 | now | 마지막 저장 시각(에디터의 `savedAt`) |

- 생기는 때: 발행 글(`status = PUBLISHED`)에 대한 수동 저장, 또는 자동 저장의 1분 DB 반영.
- 사라지는 때: 다시 발행(⑧), 변경 취소, 글 완전 삭제(CASCADE).
- 쓰기 조건: `INSERT … ON CONFLICT (post_id) DO UPDATE … WHERE post_draft.edit_version < EXCLUDED.edit_version`, 그리고 `post.status = 'PUBLISHED' AND post.edit_version < :version`일 때만(research B-3 ③). `deleted_at` 조건은 넣지 않는다(휴지통 직전 내용 보존, B-3 ⑥). 글이 완전 삭제돼 FK 위반(23503)이 나면 그 Redis 키를 버린다.

### 1-3. `post_tag`, `tag` — 발행 때 확정 (소유: `tag` 모듈, 008)

- 이 기능은 발행 트랜잭션 ⑤에서 `TagService.replacePostTags(postId, rawTags)`를 호출만 한다. `tag(name UQ, ck_tag_name)`에 `INSERT … ON CONFLICT (name) DO NOTHING`, `post_tag(post_id, tag_id, position)`를 입력 순서(0부터, `uq_post_tag_position`, `ck_post_tag_position` 0~99)로 통째로 교체.
- 자동 저장·수동 저장은 태그를 다루지 않는다(FR-006). 에디터 열기 응답의 `tags`는 현재 `post_tag`의 이름 목록(발행 설정 초기값).

### 1-4. `post_image`, `image` — 발행 때 연결 (소유: `media` 모듈, 003)

- 렌더링 ②에서 본문의 우리 사진 키 목록을 뽑고, `ImageReferenceResolver`(003 구현)가 `SELECT storage_key, thumb_storage_key FROM image WHERE storage_key = ANY(:keys) AND uploader_id = :authorId` 한 번으로 작성자 사진을 판별한다(`uq_image_storage_key`).
- 발행 트랜잭션 ⑥에서 `ImageService.syncPostImages(postId, authorId, ownedKeys)`: 작성자 사진만 `post_image` 연결 + `image.status = 'ATTACHED'`, 빠진 사진 `detached_at = now()`. 남이 올린 사진은 연결하지 않는다.
- 수동 저장·1분 DB 반영 때의 연결은 003 FR-022가 정하고 같은 메서드를 쓴다.

### 1-5. `member` — 읽기만 (소유: `account` 모듈, 001)

- `default_visibility`: 새 글의 `post.visibility` 초기값(`MemberQueryService.defaultVisibility(memberId)`).
- 계정 상태(`status`, `auth_identity.email_verified_at`)는 001의 보안 계층이 401/403으로 먼저 판정한다.

### 1-6. `shedlock` — 추가 제안 (51에 없음)

ShedLock JDBC 제공자의 실행 잠금 테이블. 업무 데이터가 아닌 인프라 테이블이며 여러 기능(02·13·25·30·31·32·44의 배치)이 함께 쓴다. 한 번만 `V{n}__shedlock.sql`로 추가한다(research B-4, 팀 확인 필요).

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `name` | varchar(64) PK | 작업 이름(`autosave-flush`, `empty-draft-cleanup`, `post-rerender`) |
| `lock_until` | timestamptz NOT NULL | 잠금 만료 |
| `locked_at` | timestamptz NOT NULL | 잠근 시각 |
| `locked_by` | varchar(255) NOT NULL | 잠근 서버 |

---

## 2. 글 상태와 전이

| 상태 | 판별 | 작성자 | 독자 |
|---|---|---|---|
| 임시글 | `status = 'DRAFT'` | 에디터·내 글 관리 | 404 |
| 발행됨 | `status = 'PUBLISHED'`, `post_draft` 없음 | 에디터는 발행본을 불러옴 | 공개 범위에 따라 |
| 수정 중 | `status = 'PUBLISHED'`, `post_draft` 있음 | 에디터는 작업본을 불러옴 | **마지막 발행본** |

| 전이 | 트리거 | 바뀌는 값 | 이벤트 |
|---|---|---|---|
| (없음) → 임시글 | `POST /api/posts` | 행 생성, `edit_version = 0`, `visibility = member.default_visibility` | 없음 |
| 임시글 → 임시글 | 자동 저장 반영·수동 저장 | `title`, `content_md`, `edit_version`, `updated_at` | 없음 |
| 임시글 → 발행됨 | 발행 | `status = PUBLISHED`, 본문·`content_html`·`excerpt`·`thumbnail_url`·`visibility`·`render_version`, `published_at = now()`, (`PUBLIC`이면) `first_public_at = now()`, `edited_at` 그대로 NULL, `edit_version = 현재 + 1` | `PostPublished` (+ `PostWentPublic`) |
| 발행됨 → 수정 중 | 발행 글 수동 저장·자동 저장 반영 | `post_draft` 생성, `post`는 그대로 | 없음 |
| 수정 중 → 수정 중 | 저장 반복 | `post_draft.title·content_md·edit_version·updated_at` | 없음 |
| 발행됨/수정 중 → 발행됨 | 다시 발행 | 본문·HTML·요약·썸네일·`visibility`·`render_version`, `edited_at = now()`, `published_at`·`first_public_at` 유지(단, 처음 공개면 `first_public_at = now()`), `edit_version = 현재 + 1`, `post_draft` 삭제 | `PostEdited` (+ 처음 공개면 `PostWentPublic`) |
| 수정 중 → 발행됨 | 변경 취소 | `post_draft` 삭제, `post.edit_version = 현재 + 1`, `updated_at` | 없음 |
| 발행됨 → 임시글 | — | **전이 없음** (P-1, 내리려면 `PRIVATE`) | — |
| 임시글 → (삭제) | 빈 임시글 정리 배치 | 행 완전 삭제(CASCADE) | 없음 |
| 발행 글(이전 `render_version`) | 다시 렌더링 배치 | `content_html`, `excerpt`, `render_version` (`edited_at`·`edit_version`·`updated_at` 유지) | 없음 |

**"현재 버전"** = max(Redis `version`, `post_draft.edit_version`, `post.edit_version`). Redis 장애 시 앞의 둘을 뺀 DB 값.

**시각 규칙(도메인 메서드 `Post.publish`, 05 §3·§7 ⑦)**

```
firstPublish   = (published_at IS NULL)
published_at   = COALESCE(published_at, now)
wentPublic     = (first_public_at IS NULL AND newVisibility = PUBLIC)
first_public_at= wentPublic ? now : first_public_at
edited_at      = firstPublish ? edited_at : now
```

## 3. 검증 규칙

| 항목 | 저장(자동·수동) | 발행 | 오류 코드 |
|---|---|---|---|
| 제목 | 길이 ≤ 100 (입력 그대로) | NFC → U+200B~U+200F·U+2060~U+2069·U+FEFF·U+202A~U+202E·제어 문자 제거 → trim → 1~100자 | `TITLE_REQUIRED`, `TITLE_TOO_LONG` |
| 본문 | 길이 ≤ 100,000 | trim 후 1자 이상, ≤ 100,000 | `CONTENT_REQUIRED`, `CONTENT_TOO_LONG` |
| 업로드 대기 사진 | 허용(`local:` 그대로 저장) | 본문에 `local:` 주소가 있으면 거부 | `PENDING_IMAGES` |
| 렌더링 | 하지 않음 | 중첩 ≤ 20, 1초 이내 | `CONTENT_TOO_COMPLEX` |
| 태그 | 받지 않음 | 0~`blog.post.max-tags`(기본 10), 정규화·형식은 008 | `TOO_MANY_TAGS`, `INVALID_TAG`, `TAG_TOO_LONG`, `TAG_BANNED_WORD` |
| 공개 범위 | 받지 않음 | `PUBLIC`/`PRIVATE`(값 추가는 004) | `INVALID_VISIBILITY` |
| 기준 버전 | 필수, 현재 버전과 같아야 함 | 같음 | 409 `VERSION_CONFLICT` |
| 요청 크기 | 자동 저장 ≤ 1MB | — | 413 `PAYLOAD_TOO_LARGE` |

발행 검증은 항목별 오류를 모두 모아 400 `VALIDATION_FAILED`의 `errors`로 돌려준다(필드 `title`, `contentMd`, `tags[i]`, `visibility`).

---

## 4. Redis (서버 쪽 임시 보관·멱등·요청 제한)

| 키 | 형식 | TTL | 내용 |
|---|---|---|---|
| `autosave:post:{postId}` | Hash | 24h(`blog.autosave.redis-ttl`), 저장마다 연장 | `memberId`, `title`, `contentMd`, `version`, `savedAt`(ISO-8601) |
| `autosave:dirty` | Set | 없음 | DB에 아직 반영하지 않은 `postId` |
| `idem:publish:{memberId}:{key}` | String(JSON) | 600초(`blog.publish.idempotency-ttl`) | `{hash, status: IN_PROGRESS\|DONE, response}` |
| `ratelimit:autosave:{memberId}` | String 카운터 | 5초 | 5초에 1번(`blog.autosave.rate-limit`) |
| `ratelimit:preview:{memberId}` | String 카운터 | 60초 | 1분에 60번(`blog.markdown.preview-rate-limit`) |

**Lua 스크립트**

- `autosave-save.lua` — 입력 `memberId, baseVersion, dbVersion, title, contentMd, savedAt, ttl, postId`. 현재 = max(Redis `version`, `dbVersion`)(research B-3 ①). 키가 있고 `memberId`가 다르면 거부, `baseVersion ≠ 현재`면 `{0, 현재}`(409), 같으면 HSET + EXPIRE + SADD dirty 후 `{1, 현재+1}`. 자동 저장과 수동 저장이 함께 쓴다.
- `autosave-release.lua` — 입력 `확인한 버전 v0`, `새 DB 버전 v1`. Redis `version ≤ v0`이면 DEL, 크면 `version = v1 + 1`로 다시 매기고 dirty 유지(research B-3 ④·⑤). 발행·변경 취소 커밋 후에 쓴다.

운영 설정: `appendonly yes`, `appendfsync everysec`, `maxmemory-policy noeviction`(02 §2). 보존 데이터와 캐시의 인스턴스 분리·`maxmemory`는 M30에서 정한다.

---

## 5. 브라우저 IndexedDB (localforage)

| 키 | 값 | 보관 |
|---|---|---|
| `draft:{memberId}:{postId}` | `{ title, contentMd, baseVersion, dirty, pendingImages: [{localId, blob, alt}], updatedAt }` | 발행 성공 또는 `dirty = false`로 에디터를 떠날 때 삭제 |
| `draft-backup:{memberId}:{postId}` | `{ title, contentMd, baseVersion, backedUpAt }` | [저장된 내용 불러오기] 때 만들고 7일 뒤 삭제(에디터 열 때 만료분 정리) |

- 로그인 정보·토큰은 저장하지 않는다. 로그아웃 때 `draft:{memberId}:*`·`draft-backup:{memberId}:*`를 모두 지운다.
- 다시 열 때: `dirty && baseVersion == 서버 version` → 충돌 아님("이 기기에 저장되지 않은 변경을 불러왔어요"), `dirty && baseVersion != 서버 version` → 비교 창.

---

## 6. 값 객체 (서버, 저장하지 않음)

| 이름 | 필드 | 쓰임 |
|---|---|---|
| `RenderedContent` | `html`, `excerpt`, `ownedImageKeys`(본문 순서), `thumbnailUrl`, `renderVersion` | 렌더링 ② 결과 → 트랜잭션 ⑥·⑦ |
| `PublishCommand` | `postId`, `memberId`(세션), `title`(정리됨), `contentMd`, `rawTags`, `visibility`, `baseVersion`, `idempotencyKey` | 발행 입력 |
| `PublishResult` | `url`, `publishedAt`, `firstPublicAt`, `editedAt`, `version`, `firstPublish`, `wentPublic` | 응답·이벤트 판단 |
| `ServerCopy` | `title`, `contentMd`, `version`, `savedAt` | 409 `details.server`, 에디터 열기 |
