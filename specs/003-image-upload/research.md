# Research: 이미지 업로드

**Feature**: `003-image-upload` | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

각 항목의 형식은 Decision / Rationale / Alternatives considered이다. 각 항목은 다음 셋 중 하나로 표시했다.

- **확정**: 원문, 팀 결정, spec Clarifications(2026-10-08 민서 확정)에 이미 있는 것
- **제안(팀 확인 필요)**: 원문에 없어 이 plan이 합리적 기본값을 고른 것
- **미결 — 기본안**: 팀 결정이 필요해 기본안으로 진행하고 tasks에 확인 작업을 둔 것

---

## R0. 남은 NEEDS CLARIFICATION

- **Decision**: 없음. spec `## Clarifications` Session 2026-10-08에서 다음이 **확정**되었다(민서 확정 2026-10-08).
  - Q1 친구 공개 글 사진도 "주소를 알면 보임"(FR-025)
  - Q2 운영 저장소 점검 11가지를 구현 전에 돌려 업로드 방식 확정(R2)
  - Q3 10MB는 올라가는 파일 기준, 원래 파일 상한 50MB는 설정값(R4)
  - Q4 WebP를 못 만들면 JPEG 0.8, 썸네일 주소는 실제 저장 이름을 읽음(R3·R22)
  - Q5 연결 끊김·시간 초과·5xx만 기기에 보관(R12)
  - 429 코드는 `TOO_MANY_REQUESTS`(007 Q3)
- **Rationale**: spec 맨 위 Clarifications는 확정 사항이다.
- **Alternatives considered**: 없음.

## R1. 업로드 흐름: presign → 브라우저 직접 PUT → complete (확정)

- **Decision**:
  1. `POST /api/images/presign {purpose, contentType, size, thumbContentType?, thumbSize?}` — 판정(R8) 뒤 `image` 행을 `status = 'TEMP'`, 신고 크기로 만들고, 원본·썸네일 Presigned PUT 주소 2개를 준다. 주소는 5분 유효, SigV4, path-style이며 `Content-Type`과 `Content-Length`를 서명에 넣는다.
  2. 브라우저가 두 주소로 직접 `PUT`한다(앱 서버를 거치지 않음, FR-004).
  3. `POST /api/images/{imageId}/complete` — 업로더 확인(R10의 404) → 저장소 `HeadObject` 2번(존재·실제 크기) → 앞부분 읽기로 형식·해상도 검사(R6) → 통과하면 같은 행에 실제 크기·가로·세로를 기록하고 공개 주소를 돌려준다. 실패하면 두 파일과 행을 지우고 400을 준다.
- **Rationale**: 04 §4-1 ③~⑥과 23 §2-2가 정한 흐름이다. 서명에 `Content-Length`를 넣으면 신고 크기와 다른 파일은 저장소가 403으로 거부하므로 "신고보다 크면 거부"(FR-008)를 저장소 단계에서도 막는다. complete는 그래도 다시 확인한다(저장소마다 동작이 다를 수 있음, 23 위험 1).
- **Alternatives considered**:
  - Presigned POST(정책 문서에 `content-length-range`): 크기 범위를 강제할 수 있지만 MinIO·NHN 호환성이 PUT보다 덜 확인되었고, 점검 11가지가 PUT 기준이다.
  - 서버 경유 업로드만 쓰기: 앱 서버가 모든 사진 바이트를 받아야 한다. 대체안(R2)으로만 둔다.

## R2. 운영 저장소 점검과 대체안 (확정 — 방식은 점검 결과로 결정)

- **Decision**:
  - 구현 시작 전(tasks Phase 1)에 운영 NHN MinIO에 점검 11가지(23 §2-3)를 `scripts/lib/presign_check.py`로 돌리고 결과를 docs/23 §2-3 표와 이 문서에 적는다(Clarifications Q2).
  - **기본안(직접 업로드)**: 11/11 통과하면 R1 흐름 그대로다.
  - **대체안(서버 경유)**: 10번(CORS 우리 출처 허용)이 실패하면 `blog.image.upload-mode = PROXY`로 바꾼다. presign은 저장소 주소 대신 `PUT /api/images/{imageId}/content`·`/thumb-content` 주소를 준다. 서버는 업로더 확인 → 크기 상한(신고 크기) 스트림 복사 → 저장소 `PutObject`를 하고, 이어지는 complete는 같다. 다른 점검 항목(서명 위조·익명 목록 등)이 실패하면 운영 저장소 자체를 쓸 수 없으므로 배포 담당에게 넘긴다(tasks 확인 작업).
  - 두 방식 모두 FR-001~FR-024 규칙과 API 응답 모양이 같다. 화면은 presign 응답의 `upload.url`로만 PUT하므로 방식을 몰라도 된다.
- **Rationale**: 업로드 구조가 점검 결과에 달려 있어, 만들기 전에 아는 것이 가장 싸다(Q2 추천 근거). 대체안을 계약에 미리 적어 두면 점검이 실패해도 화면 코드는 바뀌지 않는다.
- **Alternatives considered**: 두 방식을 처음부터 모두 완성(작업이 크게 늘어남, Q2 C안), 배포 직전 점검(Q2 B안, 실패하면 다시 만듦).

## R3. 브라우저 처리: 줄이기·형식·메타데이터 (확정 + 제안)

- **Decision**:
  - 입력: 사용자가 고른 파일의 형식은 파일 앞부분(매직 바이트)으로 판별한다. jpg·png·gif·webp가 아니면 고르는 순간 "jpg, png, gif, webp 사진만 올릴 수 있어요"(R12 즉시 안내).
  - GIF가 아닌 사진: `createImageBitmap(file, { imageOrientation: 'from-image' })`로 EXIF 방향을 반영해 읽고, 긴 변 1920px 이하로 캔버스에 다시 그린 뒤 `canvas.toBlob('image/webp', 0.8)`로 만든다. 결과 Blob의 `type`이 `image/webp`가 아니면(사파리 등) `toBlob('image/jpeg', 0.8)`로 다시 만든다(Clarifications Q4). 캔버스로 다시 그리면 EXIF·XMP 등 메타데이터가 남지 않는다(FR-002).
  - 썸네일: 같은 이미지(GIF는 첫 장면)를 가로 640px 이하로 그려 같은 방식(WebP → JPEG 대체)으로 만든다. 1MB를 넘으면 품질을 0.7 → 0.6으로 낮춰 다시 만들고, 그래도 넘으면 업로드하지 않고 "사진을 처리하지 못했어요"를 보인다.
  - 원래 파일 상한: GIF가 아닌 사진은 원래 파일 50MB(`blog.image.max-source-bytes`, `GET /api/me/storage`의 `limits`로 받음) 이하만 처리한다. 브라우저가 멈추지 않게 하기 위함이다(Q3).
  - 원래 파일 이름은 어디에도 쓰지 않는다. 본문에 넣는 Markdown은 `![](주소)`이다(FR-032).
- **Rationale**: 04 §4-1 ②, C-IMG-1. `createImageBitmap`의 `imageOrientation` 옵션을 쓰지 않으면 세로 사진이 눕는다. 사파리는 `toBlob('image/webp')`를 무시하고 PNG를 돌려주는 것으로 알려져 있어 결과 `type`을 확인해야 한다(Q4 근거). 라이브러리 없이 표준 API로 된다.
- **Alternatives considered**: `browser-image-compression` 같은 라이브러리(번들 크기 증가, 같은 캔버스 방식), 서버에서 줄이기(사진 바이트가 서버를 거침, D-4와 어긋남).

## R4. 10MB 기준 = 실제로 올라가는 파일 (확정)

- **Decision**: presign의 `size`(원본)는 1~10,485,760바이트, `thumbSize`는 1~1,048,576바이트만 받는다(V1 `ck_image_size`·`ck_image_thumb_size`와 같음). GIF는 줄이지 않으므로 원래 파일이 10MB 이하여야 한다. 원래 파일이 10MB를 넘어도 줄인 결과가 10MB 이하면 올라간다. 001 프로필 사진 고르기(001 FR-049, T121)에도 같은 기준을 적용한다.
- **Rationale**: Clarifications Q3. 브라우저에서 줄이는 목적(전송량 1/10)에 맞고, 저장소에 들어가는 파일은 여전히 10MB 이하라 C-IMG-1을 지킨다.
- **Alternatives considered**: 원래 파일 기준(Q3 A안, 고화질 사진을 못 올림).

## R5. 완료 표시는 `width IS NOT NULL` (제안(팀 확인 필요))

- **Decision**: presign은 `width`·`height`를 NULL로 넣고, complete가 검사를 통과하면 실제 값을 채운다. "완료된 사진"은 `width IS NOT NULL`이다. complete를 다시 부르면(같은 업로더) 이미 완료된 행이면 검사 없이 같은 200 응답을 준다(멱등). 글 연결(`attachPostImages`)은 완료된 사진만 연결한다(`AND width IS NOT NULL` 조건 추가).
- **Rationale**: V1에 완료 여부 컬럼이 없고 `status`는 TEMP/ATTACHED뿐이다. `width`·`height`는 NULL 허용이며 complete 전에는 알 수 없는 값이라 자연스러운 표시가 된다. 새 컬럼을 만들지 않아 원칙 I을 지킨다.
- **Alternatives considered**: `status = 'UPLOADED'` 값을 추가(V1 `ck_image_status` 변경 필요, 원칙 I 위반), Redis에 완료 표시(장애 때 사라짐).

## R6. 서버 검사는 머리말만 읽는다 (제안(팀 확인 필요))

- **Decision**: `ImageHeaderReader`가 다음을 직접 읽는다. 파일 전체를 해독하지 않는다.
  - 형식(매직 바이트): JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, GIF `GIF87a`/`GIF89a`, WebP `RIFF????WEBP`
  - 가로·세로: JPEG는 SOF0~SOF15 마커(SOF4·SOF8·SOF12 제외), PNG는 IHDR, GIF는 논리 화면 크기, WebP는 `VP8 `(키 프레임 머리말)·`VP8L`(14비트 크기)·`VP8X`(캔버스 24비트 크기)
  - GIF 프레임 수: 원본을 스트림으로 한 번 읽으며 이미지 기술자(`0x2C`) 블록을 센다. 301번째를 만나면 바로 멈추고 거부한다
  - 앞부분 읽기 크기: 64KB(`blog.image.inspect-head-bytes`). JPEG SOF가 그 안에 없으면(큰 EXIF 뒤에 있는 경우) 손상으로 보고 거부한다 — 브라우저가 다시 그린 파일은 EXIF가 없어 SOF가 앞에 있다
  - 한도(설정값):
    - 글 사진 원본(GIF 아님): 가로·세로 각 4,096px 이하, 전체 픽셀 16,777,216 이하
    - GIF 원본: 가로·세로 각 1,920px 이하, 프레임 300장 이하
    - 썸네일: 가로 640px 이하, 세로 4,096px 이하, 크기 1MB 이하
    - 프로필 사진: 정확히 256×256, 1MB 이하, 썸네일 없음(001 research)
  - `Content-Type`(presign 때 신고)과 매직 바이트 형식이 다르면 거부한다(확장자 위장, US1 #3).
- **Rationale**: 04 §4-1 ⑤는 "해상도 검사"를 요구하고 23 §5-1은 GIF를 "머리말·프레임 수만" 보라고 정했다. JDK `ImageIO`는 WebP 리더가 없고, 전체 해독은 거대 해상도 파일로 메모리를 쓰게 할 수 있다. 머리말 파서는 형식마다 수십 줄이고 단위 테스트로 고정하기 쉽다. 원본 한도 4,096px는 브라우저 결과(1,920px)보다 넉넉하고 04의 예시(1만 px)보다 엄격하다.
- **Alternatives considered**:
  - `ImageIO` + WebP 플러그인 라이브러리(의존 추가, 전체 해독 위험)
  - 원본 한도를 1,920px로 맞추기(반올림 차이로 정상 업로드가 거부될 수 있음)
  - 해상도 검사 생략(FR-007 위반)

## R7. 저장 키와 형식 매핑 (확정)

- **Decision**:
  - 키: `images/{yyyy}/{MM}/{uuid}.{ext}`, 썸네일 `images/{yyyy}/{MM}/{uuid}_thumb.{ext}`. `yyyy`·`MM`은 서비스 시간대(`blog.time-zone`, Asia/Seoul) 기준 presign 시각, `uuid`는 `UUID.randomUUID()` 소문자.
  - 확장자: `image/jpeg` → `jpg`, `image/png` → `png`, `image/gif` → `gif`, `image/webp` → `webp`. 썸네일 확장자는 `thumbContentType`을 따른다(WebP 또는 JPEG 대체, Q4).
  - 키 모양 판별 정규식은 002 `ImageReferenceResolverAdapter.STORAGE_KEY`(`jpg|jpeg|png|gif|webp`)를 그대로 `ImageUrls`로 옮긴다. 원본과 썸네일의 uuid는 같다.
  - 저장소 객체에 `Cache-Control: public, max-age=31536000, immutable`을 넣는다(presign 서명 헤더, FR-029).
- **Rationale**: 04 §4-2, 23 §2-4. 원래 파일 이름을 쓰지 않는다(FR-009).
- **Alternatives considered**: 날짜 경로 없이 uuid만(목록·운영 조회가 불편), 내용 해시 이름(같은 사진이 여러 사람에게 공유되어 소유가 꼬임).

## R8. 판정 순서와 용량·하루·1분 한도 (제안(팀 확인 필요))

- **Decision**: presign 판정 순서와 구현은 다음과 같다. 처음 걸린 단계로 응답한다.
  1. 401 `LOGIN_REQUIRED`
  2. 403 — `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE)`: `ACCOUNT_WITHDRAWN`, `EMAIL_NOT_VERIFIED`, `ACCOUNT_SUSPENDED`
  3. 400 `VALIDATION_FAILED` — `purpose`·`contentType`·`size`·`thumbContentType`·`thumbSize` 칸 오류(R4, `errors[].field`)
  4. 409 `STORAGE_QUOTA_EXCEEDED` — 트랜잭션 A: `MemberLockService.lockForUpdate(me)` → `SELECT coalesce(sum(size_bytes + coalesce(thumb_size_bytes, 0)), 0) FROM image WHERE uploader_id = :me`(`ix_image_uploader`) → 합계 + 신고 크기 > 한도면 409, 아니면 TEMP 행 INSERT → 커밋
  5. 429 `DAILY_UPLOAD_LIMIT` — 커밋 뒤 Redis Lua: `INCR img:daily:{me}:{yyyyMMdd}`, 첫 증가면 `EXPIRE 2일`, 결과가 한도(200)를 넘으면 `DECR` 후 거부. `Retry-After`는 다음 0시(KST)까지 초
  6. 429 `TOO_MANY_REQUESTS` — 001 `RateLimiter.tryAcquire("ratelimit:image:{me}", 20, 1분)`. 거부되면 5단계에서 늘린 하루 장수를 `DECR`로 돌려준다
  7. 5·6단계에서 거부되면 트랜잭션 B로 그 TEMP 행을 지운다(보상). 통과하면 Presigned 주소를 계산해 201로 응답한다
  - Redis 장애 중에는 5·6단계를 통과한다(경고 로그, 02 §2-1). 보상 삭제가 실패해도 TEMP 행은 24시간 뒤 정리 배치가 지운다.
  - "실패한 업로드도 1장으로 센다"(FR-016): 5단계를 통과해 주소를 받은 요청은 업로드·complete가 실패해도 하루 장수를 돌려주지 않는다.
- **Rationale**:
  - README 2026-10-07 "로그인 → 계정 상태 → 볼 수 있나 → 업무 규칙 → 요청 횟수"와 007 Q2(요청 횟수는 맨 끝)를 따른다. 하루 200장은 사진 기능의 업무 규칙이지만 Redis 카운터라 "요청 횟수" 쪽에 두고, 1분 제한보다 먼저 본다.
  - `RedisGuard`는 트랜잭션 안의 Redis 쓰기를 금지(또는 경고)한다(05 J-5). 그래서 용량 판정(DB 잠금)과 Redis 카운터를 나누고 보상으로 맞춘다.
  - 회원 행 `FOR UPDATE`로 같은 회원의 동시 presign을 직렬화하면, 동시에 10건을 보내도 합계가 1GB를 넘지 않는다(US4 #2, SC-004). 회원 테이블에 사용량 컬럼을 두지 않는다(23 §8).
- **Alternatives considered**:
  - Redis를 먼저 세고 DB를 나중에(구현은 단순하지만 409보다 429가 먼저 나와 판정 순서가 바뀜)
  - `pg_advisory_xact_lock(memberId)`(account 모듈 포트가 필요 없지만 잠금 키 규칙을 새로 정해야 함)
  - 사용량을 회원 행에 비정규화(합계와 어긋날 위험, 23 §8에서 기각)

## R9. Redis 메모리 부족(OOM) 때의 503 문구 (미결 — 기본안)

- **Decision**: 002 `RedisGuard.call`은 OOM이면 `AutosaveUnavailableException`(503 `AUTOSAVE_UNAVAILABLE`, "잠시 후 다시 저장할게요")을 던진다. 사진 업로드의 5·6단계도 이 예외를 받을 수 있다. 기본안:
  - 서버: 이 기능은 예외를 그대로 둔다(002 소유 코드를 고치지 않음). 5·6단계에서 503이 나면 보상(TEMP 행 삭제)은 그대로 한다.
  - 화면: presign·complete의 503은 code와 상관없이 "잠시 후 다시 시도해 주세요"로 보이고, 실패 분류(R12)상 "서버 장애"로 기기에 보관해 다시 올린다.
  - 팀 결정 요청: `RedisGuard`의 OOM 처리를 "자동 저장 경로만 `AUTOSAVE_UNAVAILABLE`, 나머지는 공통 503 `TEMPORARILY_UNAVAILABLE`"로 바꿀지. 같은 문제가 007·008·009·010·012에도 있다(ANALYSIS-tier-bc 팀 결정 항목).
- **Rationale**: 002 T032 구현 메모(팀 확인 필요). 공용 코드를 한 기능이 바꾸면 002의 자동 저장 테스트가 흔들린다.
- **확인 (T095, 2026-10-08)**: `ImageRedisOomIT` — Redis `maxmemory` 1바이트 + `noeviction`에서 presign은 `AutosaveUnavailableException`(503 `AUTOSAVE_UNAVAILABLE`)이고 TEMP 행은 보상 삭제된다. 화면은 503을 code와 상관없이 "잠시 후 다시 시도해 주세요"로 보이고 기기에 보관한다(`uploadImage.test.ts` 5xx 분류). 같은 조건에서 HTTP 요청은 세션 쓰기(Spring Session·Redis)가 먼저 흔들릴 수 있다. 공용 처리 변경은 팀 결정으로 남긴다.
- **Alternatives considered**: 이 기능에서 예외를 잡아 `TEMPORARILY_UNAVAILABLE`로 바꾸기(가능하지만 기능마다 같은 코드가 퍼짐).

## R10. 사진 판별·연결 — 002 임시 구현을 최종으로 (확정)

- **Decision**:
  - 주소 → 키: 002 `ImageReferenceResolverAdapter.parseKey`의 규칙(지금 공개 주소·옛 주소 목록 + 키 모양, 쿼리·조각이 붙으면 아님, 대소문자 정확히)을 `media.application.ImageUrls.keyOf(url)`로 옮기고, 어댑터는 `ImageUrls`에 위임한다(FR-024: 판별은 한 곳).
  - 작성자 사진 확인: `findOwned`는 그대로(`storage_key IN (:keys) AND uploader_id = :owner`, 조회 1번). **완료된 사진만**(`width IS NOT NULL`) 작성자 사진으로 본다(R5). complete 전 TEMP 사진 주소가 본문에 있으면 링크로 그려진다 — 정상 흐름에서는 complete 뒤에만 본문에 주소가 들어간다.
  - 연결: `ImageService.syncPostImages`(발행)와 `attachPostImages`(수동 저장·1분 반영)의 SQL과 규칙을 유지하고 `TODO(003)` 주석과 교체 점검표를 "최종" 설명으로 바꾼다. 002 T122 점검표의 테스트(`PublishIT`·`ManualSaveIT`·`AutosaveFlushJobIT`·`PublishQueryCountIT`·`PublishTransactionIT`·`ImageReferenceResolverAdapterIT`·`ContentRendererWiringIT`)가 그대로 통과해야 한다.
  - complete에서 남의 `imageId`는 404 `NOT_FOUND`(고정 본문, FR-006). 없는 번호와 구분하지 않는다.
- **Rationale**: 23 §6-1(2026-10-07 회의 채택), 002 T025·T047·T122. 이미 테스트로 고정된 규칙을 옮기는 것이 위험이 가장 작다.
- **Alternatives considered**: 처음부터 다시 구현(회귀 위험).

## R11. GIF 표시와 다시 렌더링 (확정 + 제안)

- **Decision**:
  - 렌더러: `AstTransformer`가 작성자 사진 중 키 확장자가 `.gif`인 것을 `Link(dest = 원본 공개 주소, title = "움직이는 이미지 재생")` 안의 `Image(dest = 썸네일 공개 주소, alt 유지)`로 바꾼다. 썸네일이 없는 옛 GIF는 바꾸지 않는다(원본 그대로 `<img>`). `LinkAttributeProvider`가 붙이는 `target="_blank" rel="noopener noreferrer nofollow ugc"`를 그대로 쓴다(23 §5-2). 정화 허용 목록은 바꾸지 않는다.
  - 썸네일 주소는 `OwnedImage.thumbStorageKey()`(= `image.thumb_storage_key`)로 만든다. `_thumb.webp`를 가정하지 않는다(Q4).
  - 규칙이 바뀌므로 `RenderVersion.CURRENT`를 1 → 2로 올린다. 002 `RerenderJob`이 옛 발행 글을 다시 렌더링한다(12 §7-7).
  - 화면: 005 `features/post-detail/gifPlayer.ts`의 `playableGifs(container)`가 `a[href$=".gif"] > img`를 찾아 ① `role="button"`·`aria-pressed="false"`·`aria-label`(대체글, 비면 "움직이는 이미지 재생")을 붙이고 ② 클릭·Enter·Space에서 기본 이동을 막고 `img.src`를 원본/썸네일로 바꾼다 ③ 다시 누르면 정지 장면으로 돌아온다. 스크립트가 없거나 실패하면 링크가 새 탭에서 원본을 연다(US6 #5).
  - ▶ 표시는 `postDetail.css`의 `a[href$=".gif"]::after`(class 없이). `prefers-reduced-motion`이어도 사용자가 누르기 전에는 움직이지 않으므로 추가 처리는 없다.
- **Rationale**: 23 §5-2, FR-039. 정화 허용 목록을 넓히지 않고 CSP `script-src 'self'`를 지킨다. 원문의 `/js/gif-play.js`는 서버 화면용이었다. 화면이 React(2026-10-07 H7)이므로 번들 모듈로 둔다.
- **Alternatives considered**: `<video>` 변환(서버 변환이 필요, 범위 밖), GIF 원본을 바로 `<img>`(데이터 절약·움직임 줄이기 목적과 어긋남).

## R12. 업로드 실패 분류 (확정)

- **Decision**: 화면의 `uploadImage`는 결과를 셋으로 나눈다.
  - **보관 후 다시 시도**: `navigator.onLine === false`, `fetch` 네트워크 오류(`TypeError`), 시간 초과(30초, `AbortController`), 5xx(presign·저장소 PUT·complete 어디서든). 원본 Blob을 `pendingImages`에 넣고 본문에 `![](local:{localId})`를 넣으며 화면은 `URL.createObjectURL`로 보인다. `online` 이벤트와 에디터 열기 때 다시 올린다.
  - **즉시 안내(보관 안 함)**: 400(형식·크기·검사 실패), 409 `STORAGE_QUOTA_EXCEEDED`, 429 `DAILY_UPLOAD_LIMIT`·`TOO_MANY_REQUESTS`, 401·403. 본문에 넣지 않고 안내만 한다(429는 `Retry-After`를 문구에 쓰지 않고 code별 고정 문구).
  - **저장소 PUT 403**(서명 만료 등): presign부터 한 번 다시 한다. 다시 실패하면 즉시 안내.
  - 다시 시도 중에 즉시 안내 대상 오류가 나면 그 대기 사진을 대기열에서 빼고 본문의 `local:` 표시를 그대로 둔 채 "업로드하지 못한 사진이 있어요" 안내를 띄운다(발행은 `PENDING_IMAGES`로 막혀 있으므로 사용자가 지우거나 다시 넣는다).
- **Rationale**: Clarifications Q5. 다시 해도 성공할 수 없는 실패를 보관하면 발행만 막히고 하루 한도만 깎인다.
- **Alternatives considered**: 모든 실패 보관(Q5 B안).

## R13. 정리 배치 (확정)

- **Decision**: `ImageCleanupJob`
  - 일정: `blog.image.cleanup.cron`(기본 `0 30 3 * * *`), 시간대 `blog.time-zone`, `@SchedulerLock(name = "imageCleanup", lockAtMostFor = "PT30M")`
  - 후보(1,000개씩, `ix_image_cleanup_temp`·`ix_image_cleanup_detached`):
    ```sql
    SELECT id, storage_key, thumb_storage_key FROM image
     WHERE (status = 'TEMP' AND created_at < now() - :tempTtl)
        OR (detached_at IS NOT NULL AND detached_at < now() - :detachedTtl)
     ORDER BY id LIMIT 1000
    ```
    현재 프로필 사진은 `detached_at IS NULL`이고 `status = 'ATTACHED'`라 조건에 걸리지 않는다(FR-041).
  - 처리: 트랜잭션 밖에서 `ImageStorage.deleteAll(keys)`(S3 `DeleteObjects`, 없는 키도 성공으로 봄) → 성공한 사진만 `DELETE FROM image WHERE id IN (:ids) AND (<위 조건 재확인>)`. 그사이 다시 연결된 사진(`detached_at`이 NULL로 돌아감)은 조건 재확인으로 남는다 — 그 경우 파일은 이미 지워졌으므로 경고 로그를 남긴다(극히 드묾, 7일 경과 직후 같은 사진을 다시 쓰는 경우).
  - 실패: 저장소 삭제 실패한 키의 행은 남고 다음 날 다시 시도한다(US7 #4). 한 묶음이 실패해도 다음 묶음을 계속한다. 30분을 넘으면 멈춘다.
  - `post_image` 행은 FK CASCADE로 함께 지워진다(연결 해제된 사진은 보통 이미 행이 없음).
- **Rationale**: 04 §4-4, FR-041·FR-042, README "배치 잠금 03:30". 저장소 삭제 성공 뒤에만 행을 지워 "기록은 없는데 파일은 남는" 상태를 피한다.
- **Alternatives considered**: 행을 먼저 지우고 파일 삭제(실패하면 고아 파일이 영원히 남음).

## R14. 프로필 사진 업로드 규격 (확정)

- **Decision**: presign `purpose = PROFILE`이면 `contentType = image/webp`, `size ≤ 1MB`, 썸네일 없음(`thumbContentType`·`thumbSize`를 보내면 400). complete는 정확히 256×256을 확인한다. 연결은 001 `PATCH /api/me/profile {profileImageId}`가 `ProfileImageService.attach(me, imageId)`를 부른다: 이전 현재 사진 `detached_at = now()` → 새 사진 `status = 'ATTACHED', detached_at = NULL WHERE id = :id AND uploader_id = :me AND purpose = 'PROFILE' AND width IS NOT NULL`(0행이면 400 `INVALID_PROFILE_IMAGE`). 순서는 `uq_image_profile_current` 때문에 "떼기 → 붙이기"다(001 data-model §2-6).
- **Rationale**: 001 research·data-model, 001 T116(임시 구현을 003이 교체). 프로필 사진도 용량에 센다(FR-014).
- **Alternatives considered**: 프로필 전용 API(흐름 중복).

## R15. 링크 미리보기 대표 이미지 (확정)

- **Decision**: 005 `OgImageResolver`(임시)의 방식(`post.thumbnail_url`에서 썸네일 키를 떼어 `uq_image_thumb_key`로 원본 키를 한 번 찾음)을 최종으로 확정하고 `ImageUrls.keyOf`를 쓰도록 바꾼다(옛 공개 주소로 저장된 `thumbnail_url`도 찾음). GIF는 원본 GIF 주소를 준다(FR-040).
- **Rationale**: 51 `post`에 원본 주소 컬럼이 없고 스키마를 바꾸지 않는다(원칙 I). 005 T062 임시 구현 주석이 003 교체를 요청했다.
- **Alternatives considered**: `post`에 대표 원본 키 컬럼 추가(원칙 I 위반).

## R16. 대체글 권유 (확정)

- **Decision**: 발행 설정 창(002 `PublishDialog`)에 `AltTextPanel`을 넣는다. 본문 Markdown에서 이미지 문법 `![alt](url)` 중 alt가 공백뿐이고 주소가 우리 사진(지금 공개 주소로 시작)인 것을 센다(코드 블록 안은 제외 — 줄 시작 ``` 블록과 인라인 `…`를 건너뛰는 간단한 스캐너). "대체글이 없는 사진이 N장 있어요 [대체글 넣기]"를 누르면 사진마다 작은 미리보기(썸네일 주소가 없으므로 원본 주소, `loading="lazy"`)와 입력칸을 보여 주고, 입력하면 그 위치의 alt를 바꾼다(대괄호·역슬래시는 이스케이프). 125자를 넘으면 "짧을수록 듣기 편해요(125자 이내 권장)" 안내만 한다. 발행은 막지 않는다(FR-034).
- **Rationale**: 23 §4, FR-032~FR-035. 서버 렌더러는 빈 alt를 `alt=""`로 그린다(002 렌더러가 이미 `alt` 속성을 항상 둠 — 회귀 테스트로 확인).
- **Alternatives considered**: 서버가 발행 응답에 대체글 없는 사진 수를 주기(발행 전에 알아야 하므로 맞지 않음).

## R17. 공개 주소 변경 (확정)

- **Decision**: API를 만들지 않고 운영 절차로 둔다(quickstart §6). ① 새 주소 확인 → ② `blog.image.public-base-url` 교체, 옛 주소를 `legacy-base-urls`에 추가, CSP·정화 허용 목록은 같은 설정을 읽으므로 자동 → ③ `RenderVersion`과 무관하게 다시 렌더링하도록 운영 명령 `RerenderJob.rerenderAll()`(002가 없으면 `blog.render.force-version` 설정으로 대신) → ④ `UPDATE post SET thumbnail_url = :new || substr(thumbnail_url, length(:old) + 1) WHERE thumbnail_url LIKE :old || '/%'` → ⑤ 다시 렌더링이 끝나면 CSP에서 옛 출처 제거. 옛 주소 목록에서는 지우지 않는다(FR-031).
- **Rationale**: 23 §2-4·I-5. 드문 운영 작업이고, 잘못 실행하면 모든 글에 영향을 주므로 화면 버튼을 두지 않는다.
- **Alternatives considered**: 관리자 API(범위 밖, 위험).

## R18. 테스트용 저장소 컨테이너 (제안(팀 확인 필요))

- **Decision**: `support/MinioContainerSupport`가 `GenericContainer("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")`(포트 9000, `MINIO_API_CORS_ALLOW_ORIGIN`)를 클래스당 한 번 띄우고, 관리 키로 버킷 `blog`·`images/*` 익명 읽기 정책·앱 전용 키를 만든다(SDK로, `mc` 없이). `@DynamicPropertySource`로 `blog.image.storage.*`를 넣는다. 저장소가 필요 없는 기존 통합 테스트는 이 컨테이너를 띄우지 않도록 별도 베이스 `StorageIntegrationTestBase extends IntegrationTestBase`를 둔다.
- **Rationale**: 23 §2-3 "앱 통합 테스트는 Testcontainers로 같은 고정 이미지". Testcontainers MinIO 모듈은 공식 `minio/minio` 이미지를 기본으로 하므로 이미지 이름만 바꾸는 `GenericContainer`가 단순하다.
- **Alternatives considered**: 저장소 가짜 구현(서명·CORS·정책 동작을 확인하지 못함).

## R19. 설정 키 (제안(팀 확인 필요))

- **Decision**: 기존 `CoreProperties.Image`(`blog.image.public-base-url`·`legacy-base-urls`)는 그대로 두고, 이 기능의 값은 `ImageProperties`(`@ConfigurationProperties("blog.image")`)가 같은 접두어 아래 다른 키로 받는다(`CoreProperties` 주석: "같은 접두어를 여러 클래스가 나눠 바인딩해도 된다"). 저장소 접속은 `blog.image.storage.*`(endpoint·presign-endpoint·region·bucket·access-key·secret-key), 비밀값은 환경 변수(`BLOG_IMAGE_STORAGE_ACCESS_KEY`·`…_SECRET_KEY`)로만 넣는다. 접두어 규칙(ANALYSIS R10)이 바뀌면 따른다.
- **Rationale**: 헌법 VII, 02 §5(비밀값은 환경 변수).
- **Alternatives considered**: `blog.storage.*` 최상위(이미지 외 저장소 용도가 없음).

## R20. 내부·외부 저장소 주소 분리 (제안(팀 확인 필요))

- **Decision**: `S3Client`(서버가 HEAD·GET·DELETE)는 `blog.image.storage.endpoint`(Compose 안에서는 `http://minio:9000`), `S3Presigner`(브라우저가 PUT할 주소)는 `blog.image.storage.presign-endpoint`(로컬 `http://localhost:9000`, 운영은 NHN 공개 엔드포인트)를 쓴다. SigV4는 `Host`를 서명하므로 브라우저가 실제로 접속하는 주소로 서명해야 한다. CSP `connect-src`에는 `presign-endpoint`의 출처를 더한다(001 `SecurityHeadersFilter`는 지금 공개 주소 출처만 넣음 — 두 값이 다르면 `CspContributor`처럼 확장).
- **Rationale**: 컨테이너 안과 브라우저가 보는 주소가 다르다. 같은 값이면 설정 하나로 충분하다.
- **Alternatives considered**: 서명 후 주소 문자열의 호스트만 바꾸기(서명이 깨짐, 점검 3번 "서명 후 경로 변경 403"과 같은 상황).

## R21. 로컬 저장소 구성 고치기 (확정)

- **Decision**: `docker-compose.yml`의 `minio`에서 콘솔 포트 `9001`을 밖으로 열지 않고(`--console-address`는 남겨도 포트 매핑 제거), CORS 허용 출처에 Vite 개발 서버(`http://localhost:5173`)를 더한다. 버킷·정책·앱 전용 키는 일회성 `minio-init` 서비스(`pgsty/mc`, 고정 버전)가 만든다. `scripts/check-storage.sh`(로컬 11가지)와 `scripts/lib/presign_check.py`(운영 공용)를 04 §6-1 기록대로 저장소에 넣는다(지금 저장소에 없음).
- **Rationale**: 23 §2-2 "관리 콘솔은 외부에 열지 않는다, 로컬도 9000만", FR-026·FR-027.
- **Alternatives considered**: 없음.

## R22. WebP를 못 만드는 브라우저와 썸네일 이름 (확정)

- **Decision**: R3의 대체로 원본·썸네일이 JPEG가 될 수 있다. presign은 `contentType`·`thumbContentType`을 따로 받으므로 원본 WebP + 썸네일 JPEG 같은 조합도 허용한다. 썸네일 키 확장자는 썸네일 형식을 따른다. 서버·화면 어디에서도 썸네일 주소를 원본 주소에서 추측하지 않고 `thumb_storage_key`를 읽는다(카드 `post.thumbnail_url`은 002 렌더러가 이미 그렇게 만든다).
- **Rationale**: Clarifications Q4. 실제 아이폰·맥 사파리 확인은 tasks의 Playwright 웹킷 시험과 수동 확인 작업으로 둔다.
- **Alternatives considered**: PNG 그대로(용량 증가, Q4 B안), 업로드 차단(Q4 C안).

## R23. 소셜 사진 복사와 인증 전 회원 (미결 — 기본안)

- **Decision**: 기본안은 **예외 없음**이다. 인증 전 회원의 presign은 403 `EMAIL_NOT_VERIFIED`(FR-012). 001 T084(가입 직후 소셜 사진 복사)는 Google(이메일 확인됨) 가입자만 성공하고, 확인된 이메일이 없는 GitHub 가입자는 "설정에서 직접 올릴 수 있어요"를 본다. ANALYSIS-tier-a 팀 결정 1(R2)이 "가입 때 서버 복사만 예외"로 정해지면, 001이 서버에서 소셜 사진을 받아 `ImageUploadService.importProfile(memberId, bytes)`(이 기능이 그때 추가, 같은 검사·용량 규칙)를 부르는 경로를 만든다.
- **Rationale**: 팀 결정 전에는 spec FR-012를 그대로 지킨다.
- **Alternatives considered**: 인증 전 회원에게 PROFILE만 허용(결정 없이 규칙을 바꿈).

## R24. complete의 멱등과 만료 (제안(팀 확인 필요))

- **Decision**:
  - 이미 완료된 사진(`width IS NOT NULL`)의 complete는 저장소를 다시 보지 않고 같은 200 응답을 준다.
  - 5분이 지나 업로드 주소가 만료되어 파일이 없으면 400 `IMAGE_NOT_UPLOADED`이고 행을 지운다. 화면은 presign부터 다시 한다(R12).
  - TEMP 행이 정리 배치로 지워졌으면(24시간 뒤) 404.
  - 같은 사진에 complete가 동시에 두 번 오면 `SELECT … FOR UPDATE`로 직렬화하고, 두 번째는 첫 번째 결과에 따라 200 또는 404다. 저장소 검사는 행 잠금을 잡지 않은 채 하고(외부 호출), 기록 단계에서만 잠근다 — 두 요청이 모두 검사를 해도 결과는 같다.
- **Rationale**: 네트워크가 끊겨 complete 응답을 못 받은 브라우저가 다시 시도해도 안전해야 한다(R12 보관 후 다시 시도).
- **Alternatives considered**: 두 번째 complete를 409(화면이 처리할 경우가 늘어남).

## R25. 저장 공간 응답에 한도 값 포함 (제안(팀 확인 필요))

- **Decision**: `GET /api/me/storage`는 spec의 `{ usedBytes, quotaBytes, todayCount, dailyLimit }`에 `limits { maxUploadBytes, maxThumbBytes, maxSourceBytes, gifMaxSide, gifMaxFrames, thumbMaxWidth, longSide }`를 더한다. 화면은 에디터를 열 때 한 번 받아 고르는 순간 검사(50MB·GIF 1920px·300프레임)와 90% 안내(FR-018)에 쓴다. `todayCount`는 Redis 값이고 장애면 `null`이다.
- **Rationale**: 수치는 설정값(헌법 VII)이라 화면에 하드코딩하지 않는다. 엔드포인트를 늘리지 않는다.
- **Alternatives considered**: 별도 `GET /api/images/config`(요청 하나 늘어남), 화면 상수(설정과 어긋남).

## R26. 권한 매트릭스 연결 (확정)

- **Decision**: 004 하네스에 `backend/src/test/resources/permission/image.csv`(같은 열 형식)를 더하고 실행기 `image.presign`(owner 003, 대상 없음 `NONE`)·`image.complete`(owner 003, 대상은 행위자 본인 또는 다른 회원이 올린 TEMP 사진 — 하네스의 `postId` 대신 이미지 번호를 쓰도록 실행기가 준비)를 등록한다. 기대값: 비회원 401, 인증 전 403 `EMAIL_NOT_VERIFIED`, 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 정지 403 `ACCOUNT_SUSPENDED`, 회원·관리자 201, 남의 사진 complete는 관리자 포함 404.
- **Rationale**: ANALYSIS-tier-a R6(004 FR-012 위임 → Tier B tasks에서 하네스 연결), 42 §10.
- **Alternatives considered**: 별도 테스트 클래스만(하네스의 "대기 행 0건" 확인에서 빠짐).
