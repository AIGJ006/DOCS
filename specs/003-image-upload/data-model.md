# Data Model: 이미지 업로드

**Feature**: `003-image-upload` | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

**기준**: `backend/src/main/resources/db/migration/V1__common_schema.sql`(51 통합 ERD를 옮긴 공통 스키마)과 [docs/51-erd-unified.md](../../docs/51-erd-unified.md) `image`·`post_image`.

**스키마 변경**: **없음.** V1의 컬럼·제약·인덱스만 쓴다(헌법 I). 완료 표시는 `width IS NOT NULL`로 한다(research R5). 새 마이그레이션 번호를 쓰지 않는다.

---

## 1. 이 기능이 쓰는 엔티티

### 1-1. `image` — 사진 (media 모듈 소유, 읽기·쓰기)

| 컬럼 | 타입 | NULL | 이 기능에서의 쓰임 |
|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | presign 응답의 `imageId`. complete·연결·프로필 지정의 대상 |
| `uploader_id` | bigint FK → member RESTRICT | 불가 | 올린 회원. complete의 업로더 확인(`= :me`, 아니면 404), 용량 합계, 글 연결 조건(`= 글 작성자`) |
| `storage_key` | varchar(255) UQ | 불가 | `images/{yyyy}/{MM}/{uuid}.{ext}`. 주소 판별·연결 비교의 기준(FR-024) |
| `thumb_storage_key` | varchar(255) UQ | 허용 | `images/{yyyy}/{MM}/{uuid}_thumb.{ext}`. 프로필 사진과 옛 사진은 NULL. 카드·GIF 정지 장면 주소의 원천(Q4) |
| `content_type` | varchar(50) | 불가 | 원본 형식 4종(`ck_image_type`). presign 때 신고, complete 때 매직 바이트와 같은지 확인 |
| `size_bytes` | integer | 불가 | presign 때 신고 크기 → complete 때 실제 크기(≤ 신고). 1~10,485,760(`ck_image_size`) |
| `thumb_size_bytes` | integer | 허용 | 썸네일 크기. ≤1,048,576(`ck_image_thumb_size`) |
| `width`·`height` | integer | 허용 | **NULL = 완료 전**, 값 = complete 통과(research R5). 원본의 픽셀 크기 |
| `status` | varchar(20) | 불가 | `TEMP`(올리기만 함) / `ATTACHED`(글 또는 프로필에 연결됨) |
| `purpose` | varchar(20) | 불가 | `POST` / `PROFILE` |
| `detached_at` | timestamptz | 허용 | 연결 해제 시각. 7일 뒤 정리 대상. 탈퇴 정리는 `now() - 7일`로 기록해 다음 정리에 지움 |
| `created_at` | timestamptz | 불가 | presign 시각. TEMP 24시간 판정 |

원래 파일 이름 컬럼은 없다(2026-10-07 삭제, FR-009).

**관련 제약 (V1, 변경 없음)**: `fk_image_uploader`(RESTRICT), `uq_image_storage_key`, `uq_image_thumb_key`, `ck_image_type`, `ck_image_size`, `ck_image_status`, `ck_image_purpose`, `ck_image_dim`, `ck_image_thumb_size`

**쓰는 인덱스**:

| 인덱스 | 정의 | 용도 |
|---|---|---|
| `ix_image_uploader` | `(uploader_id, created_at DESC)` | 용량 합계(`WHERE uploader_id = :me`), 탈퇴 정리 |
| `ix_image_cleanup_temp` | `(created_at) WHERE status = 'TEMP'` | 정리 배치 TEMP 24시간 |
| `ix_image_cleanup_detached` | `(detached_at) WHERE detached_at IS NOT NULL` | 정리 배치 연결 해제 7일 |
| `uq_image_profile_current` | `UNIQUE (uploader_id) WHERE purpose = 'PROFILE' AND status = 'ATTACHED' AND detached_at IS NULL` | 회원당 현재 프로필 사진 1장. 교체는 떼기 → 붙이기 |
| `uq_image_storage_key`·`uq_image_thumb_key` | UNIQUE | 주소 → 행 조회(작성자 사진 확인, 대표 이미지 원본 찾기) |

### 1-2. `post_image` — 글-사진 연결 (media 모듈이 쓰고, 행 삭제는 FK CASCADE)

| 컬럼 | 타입 | 쓰임 |
|---|---|---|
| `post_id` | bigint FK → post CASCADE | 글 완전 삭제 때 함께 지워짐 |
| `image_id` | bigint FK → image CASCADE | 정리 배치가 사진 행을 지울 때 함께 지워짐 |

PK `(post_id, image_id)`. 작성자가 올린 `purpose = 'POST'`이고 완료된(`width IS NOT NULL`) 사진만 들어간다(FR-022, R5·R10).

### 1-3. 읽기만 하는 것

| 테이블·컬럼 | 쓰임 | 경로 |
|---|---|---|
| `member` 행 | 용량 판정 직렬화 `FOR UPDATE` | account 공개 Service `MemberLockService.lockForUpdate`(research R8) |
| `post.thumbnail_url` | 링크 미리보기 원본 찾기(`OgImageResolver`), 공개 주소 변경 일괄 갱신(운영 SQL, research R17) | 002 렌더러가 값을 만든다 |

## 2. 상태 전이

```text
              presign (용량·하루·1분 통과)
   (없음) ───────────────────────────────▶ TEMP, width NULL
                                              │
                    complete 실패 / 5분 만료 │ complete 통과
             (파일·행 삭제)  ◀────────────────┤
                                              ▼
                                    TEMP, width·height 채움 (완료)
                                              │
            글 저장·발행(작성자, POST)        │ 프로필 지정(PROFILE)
            ProfileImageService.attach        │
                                              ▼
                                    ATTACHED, detached_at NULL
                                              │
     다시 발행에서 빠짐(다른 글 연결 없음) │ 프로필 교체 │ 글 완전 삭제(006 order 20) │ 탈퇴(015 order 40)
                                              ▼
                                    ATTACHED, detached_at = 시각
                                              │
              7일 경과 + 정리 배치(저장소 삭제 성공) │
                                              ▼
                                         (행 삭제)

   TEMP (완료 여부 무관) ── 24시간 경과 + 정리 배치 ──▶ (행 삭제)
   ATTACHED, detached_at 값 ── 다시 저장·발행으로 본문에 돌아옴 ──▶ ATTACHED, detached_at NULL
```

- 탈퇴 정리는 `detached_at = now() - 7일`로 기록해 다음 정리 배치가 바로 지운다(spec FR-043, 2026-10-07 "탈퇴 회원 사진").
- 한 번 `ATTACHED`가 된 사진은 `TEMP`로 돌아가지 않는다. 연결 해제는 `detached_at`으로만 표시한다(002 임시 구현과 같음).

## 3. 저장소 객체

| 객체 | 키 | 헤더 | 생성 | 삭제 |
|---|---|---|---|---|
| 원본 | `images/{yyyy}/{MM}/{uuid}.{ext}` | `Content-Type`(서명), `Content-Length`(서명), `Cache-Control: public, max-age=31536000, immutable` | 브라우저 Presigned PUT (대체안: 서버 `PutObject`) | complete 실패, 정리 배치 |
| 썸네일 | `images/{yyyy}/{MM}/{uuid}_thumb.{ext}` | 같음 | 같음 | 같음 |

- 버킷은 `blog` 하나, 익명 `GetObject`는 `images/*`만, 익명 목록 금지(23 §2-2).
- `{yyyy}/{MM}`은 presign 시각의 서비스 시간대 날짜다. 원본과 썸네일의 uuid는 같다.

## 4. Redis 키

| 키 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|
| `img:daily:{memberId}:{yyyyMMdd}` | 오늘 presign 통과 장수(정수) | 2일(첫 증가 때) | presign 5단계(예약·반납), `GET /api/me/storage`의 `todayCount` |
| `ratelimit:image:{memberId}` | 1분 창 요청 수(001 `RateLimiter`) | 1분 | presign 6단계 |

`yyyyMMdd`는 서비스 시간대(Asia/Seoul) 날짜다(FR-013 "한국 시간 0시"). Redis 장애면 두 판정 모두 통과하고 `todayCount`는 `null`이다.

## 5. 설정값 (`application.yml`, 헌법 VII)

```yaml
blog:
  image:
    public-base-url: ${BLOG_IMAGE_PUBLIC_BASE_URL:http://localhost:9000/blog}   # 001 CoreProperties (기존)
    legacy-base-urls: []                                                       # 001 CoreProperties (기존)
    upload-mode: DIRECT            # DIRECT | PROXY (research R2, 운영 점검 결과로 정함)
    quota-bytes: 1073741824        # 1GB (FR-013)
    daily-limit: 200               # 하루 장수 (FR-013)
    per-minute-limit: 20           # 1분 장수 (FR-011)
    presign-ttl: PT5M              # 업로드 주소 유효 시간 (FR-005)
    max-upload-bytes: 10485760     # 올라가는 원본 (FR-001, V1 ck_image_size와 같아야 함)
    max-thumb-bytes: 1048576       # 썸네일 (V1 ck_image_thumb_size와 같아야 함)
    max-source-bytes: 52428800     # 브라우저가 처리할 원래 파일 상한 50MB (Q3, 화면만 사용)
    long-side: 1920                # 브라우저 줄이기 (FR-002)
    thumb-max-width: 640           # (FR-003)
    max-side: 4096                 # 서버 검사: GIF 아닌 원본 가로·세로 상한 (research R6)
    max-pixels: 16777216           # 서버 검사: 전체 픽셀 상한
    gif-max-side: 1920             # (FR-036)
    gif-max-frames: 300            # (FR-036)
    profile-side: 256              # 프로필 사진 정확한 크기 (001)
    profile-max-bytes: 1048576
    inspect-head-bytes: 65536      # complete 앞부분 읽기 (research R6)
    cleanup:
      cron: "0 30 3 * * *"
      temp-ttl: PT24H
      detached-ttl: P7D
      batch-size: 1000
      max-duration: PT30M
    storage:
      endpoint: ${BLOG_IMAGE_STORAGE_ENDPOINT:http://localhost:9000}             # 서버 → 저장소
      presign-endpoint: ${BLOG_IMAGE_STORAGE_PRESIGN_ENDPOINT:http://localhost:9000}  # 브라우저 → 저장소 (R20)
      region: ${BLOG_IMAGE_STORAGE_REGION:us-east-1}
      bucket: ${BLOG_IMAGE_STORAGE_BUCKET:blog}
      access-key: ${BLOG_IMAGE_STORAGE_ACCESS_KEY:}      # 앱 전용 키, 환경 변수로만
      secret-key: ${BLOG_IMAGE_STORAGE_SECRET_KEY:}
```

- 시작할 때 검증: `max-upload-bytes ≤ 10485760`, `max-thumb-bytes ≤ 1048576`(V1 CHECK보다 크면 INSERT가 실패하므로 기동 실패로 알림), `access-key`·`secret-key`가 비면 기동 실패(테스트는 컨테이너 값 주입).

## 6. 응답·화면 모델

### 6-1. presign 응답 (`ImageUploadTicket`)

| 필드 | 설명 |
|---|---|
| `imageId` | `image.id` |
| `upload` | `{ url, method: "PUT", headers: { Content-Type, Cache-Control } }` — 원본 |
| `thumbUpload` | 같은 모양, 썸네일이 없으면(PROFILE) `null` |
| `expiresAt` | 주소 만료 시각(ISO-8601) |

### 6-2. complete 응답 (`UploadedImage`)

| 필드 | 설명 |
|---|---|
| `imageId` | |
| `url` | 원본 공개 주소(본문에 넣는 절대 주소, FR-010) |
| `thumbUrl` | 썸네일 공개 주소(없으면 `null`) |
| `contentType`·`width`·`height`·`sizeBytes` | 검사 결과 |

### 6-3. 저장 공간 (`StorageUsage`)

`{ usedBytes, quotaBytes, todayCount (nullable), dailyLimit, limits { maxUploadBytes, maxThumbBytes, maxSourceBytes, longSide, thumbMaxWidth, gifMaxSide, gifMaxFrames } }` (research R25)

### 6-4. 브라우저 대기 사진 (002 `localDraftStore.PendingImage` 채움)

| 필드 | 설명 |
|---|---|
| `localId` | `crypto.randomUUID()`. 본문 표시 `![](local:{localId})` |
| `blob` | 원래 파일(처리 전). 다시 올릴 때 처리부터 다시 한다 |
| (추가) `attempts` | 다시 시도 횟수. 화면 안내용, 서버와 무관 |

로그아웃하면 002·001의 `clearMemberDrafts`가 대기 사진도 함께 지운다(002 FR-014).

## 7. 오류 코드 (`MediaReasonCode`, 공통 형식 `{code, message, errors, details}`)

| code | HTTP | message | 언제 |
|---|---|---|---|
| `VALIDATION_FAILED` (공통) | 400 | 입력한 내용을 확인해 주세요 | presign 칸 오류. `errors[].code`는 아래 칸 코드 |
| └ `UNSUPPORTED_IMAGE_TYPE` | (칸) | jpg, png, gif, webp 사진만 올릴 수 있어요 | `contentType`·`thumbContentType` |
| └ `IMAGE_TOO_LARGE` | (칸) | 사진은 10MB까지 올릴 수 있어요 | `size` |
| └ `THUMBNAIL_TOO_LARGE` | (칸) | 사진을 처리하지 못했어요 | `thumbSize` |
| └ `THUMBNAIL_REQUIRED` / `THUMBNAIL_NOT_ALLOWED` | (칸) | 사진을 처리하지 못했어요 | POST인데 썸네일 없음 / PROFILE인데 썸네일 있음 |
| `STORAGE_QUOTA_EXCEEDED` | 409 | 사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요 | 용량 초과. `details: {usedBytes, quotaBytes}` |
| `DAILY_UPLOAD_LIMIT` | 429 | 오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요 | 하루 한도. `Retry-After` |
| `TOO_MANY_REQUESTS` (공통) | 429 | 잠시 후 다시 시도해 주세요 | 1분 20장. `Retry-After` |
| `IMAGE_NOT_UPLOADED` | 400 | 사진이 올라가지 않았어요. 다시 시도해 주세요 | complete 때 파일 없음(만료 포함) |
| `IMAGE_REJECTED` | 400 | 올릴 수 없는 사진이에요 | complete 검사 실패. `details.reason`: `SIZE_MISMATCH`·`TYPE_MISMATCH`·`CORRUPT`·`DIMENSION_EXCEEDED`·`GIF_TOO_LARGE`·`GIF_TOO_MANY_FRAMES`·`THUMBNAIL_INVALID`·`PROFILE_SIZE_INVALID` |
| `NOT_FOUND` (공통) | 404 | 볼 수 없는 페이지예요 | 남의 사진·없는 사진(고정 본문) |
| `INVALID_PROFILE_IMAGE` (001) | 400 | (001 정의) | 프로필 지정 대상이 아님 |

문구 끝에 마침표를 붙이지 않는다(README 2026-10-07). 거대 해상도·손상·형식 위장은 모두 `IMAGE_REJECTED`이며 `details.reason`만 다르다(사용자 문구는 하나).
