---

description: "Task list for 003-image-upload (이미지 업로드)"
---

# Tasks: 이미지 업로드

**Input**: Design documents from `/specs/003-image-upload/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, storage.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 media 모듈과 사진 저장소를 소유한다. 002·005·006·001이 "003에서 교체"로 남긴 임시 구현을 넘겨받는다. 아래 항목은 **직접 만들지 않고** 선행 작업으로 기다린다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — Flyway V1(`image`·`post_image`·인덱스), `CoreProperties.Image`(`public-base-url`·`legacy-base-urls`), `SecurityHeadersFilter`(CSP `img-src`·`connect-src`에 저장소 출처), `shared/error`(공통 오류 본문·`TooManyRequestsException`·`NotFoundException`), `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE)`(001 T037), `RateLimiter`(001 T023), `ImageUrlResolver`·`ProfileImageQuery`(001 T040), `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`
- 선행: specs/002 Foundational·발행 — `ContentRenderer`·`AstTransformer`·`ImageReferenceResolver` 포트, `ImageReferenceResolverAdapter`(임시, TODO(003)), `ImageService.syncPostImages`/`attachPostImages`(임시, TODO(003)), `SavedContentImages`, `RenderVersion`·`RerenderJob`, `PostReasonCode.PENDING_IMAGES`(발행 차단), 화면 `localDraftStore`(`pendingImages` 칸)·`PublishDialog`·`EditorPage`(textarea)
- 선행: specs/004 Foundational — 권한 매트릭스 하네스(`support/permission/`: `PermissionAction`·`AbstractPermissionMatrixIT`·`Actor`)
- 선행: specs/005 — `features/post-detail/gifPlayer.ts`(빈 자리, 005 T041), `OgImageResolver`(임시, 005 T062), 카드 썸네일 표시(`PostCard`)

**006 머지 후**

- 006-manage-delete(브랜치 `006-manage`, 구현 중): `post/application/spi/PostPurgeStep`과 `media/application/ImagePostPurgeStep`(order 20, 006 T060 임시 구현)이 main에 들어온 뒤 T046에서 소유를 넘겨받는다. 006 머지 전에는 T046을 시작하지 않는다

**후속 (다른 스펙이 이 기능을 사용)**

- 001-account-auth: T084(소셜 사진 복사)·T121(프로필 사진 자르기)이 `POST /api/images/presign {purpose: PROFILE}`·complete를 부른다. T116 임시 `ProfileImageService`는 이 기능의 T047이 대신한다. 001 설정 화면은 `StorageUsageBar`(T067)를 넣는다. 사진 고르기 10MB 규칙은 Clarifications Q3(올라가는 파일 기준)을 따른다
- 015-withdraw: `ImageWithdrawalPurgeStep`(`WithdrawalPurgeStep` order 40)은 015 tasks에서 만들고 이 기능의 `ImagePurgeService.detachAllByUploader`(T086)를 부른다
- 005-post-reading: SC-005(카드 썸네일 전송량) 측정은 이 기능 T098이 함께 한다(ANALYSIS-tier-a R7)
- 008·013: 해당 없음. 012: 해당 없음

**팀 결정 대기 (기본안으로 진행)**

- ANALYSIS-tier-a 팀 결정 1(소셜 사진 복사와 인증 전 회원): 기본안 "예외 없음"(research R23). 결정이 바뀌면 001과 함께 `importProfile` 경로를 추가한다(T101)
- `RedisGuard` OOM 503 문구(research R9): 기본안은 화면에서 code와 상관없이 처리. 공용 변경은 002 소유

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 작업 확인, 운영 저장소 점검(Clarifications Q2), 저장소 도구·의존성 준비

- [ ] T001 선행 확인: `R/db/migration/V1__common_schema.sql`에 `image`(`ck_image_size`·`ck_image_thumb_size`·`uq_image_profile_current`·`ix_image_cleanup_temp`·`ix_image_cleanup_detached`)·`post_image`(양쪽 CASCADE)가 있고, `B/shared/security/AccountStatusGuard.java`·`B/shared/infra/ratelimit/RateLimiter.java`·`B/media/application/ImageUrlResolver.java`·`B/media/application/ProfileImageQuery.java`·`B/shared/application/markdown/ImageReferenceResolver.java`·`F/features/editor/localDraftStore.ts`(`PendingImage`)·`F/features/post-detail/gifPlayer.ts`가 있는지 확인한다. 빠진 것이 있으면 소유 스펙에 보고하고 시작하지 않는다
- [ ] T002 **운영 저장소 점검(구현 전, 확인 작업)**: 배포 담당에게 운영 NHN MinIO 엔드포인트·앱 전용 키를 받아 `scripts/lib/presign_check.py`(T003)로 점검 11가지를 돌리고 결과(PASS/FAIL 번호만, 비밀값 제외)를 `docs/23-image.md` §2-3 표와 `specs/003-image-upload/research.md` R2 아래에 적는다. 10번만 실패하면 `blog.image.upload-mode = PROXY`로 정하고 T099~T100을 이 기능 범위에 넣는다. 1~9·11번이 실패하면 팀에 알리고 운영 배포를 보류한다(로컬 개발은 계속). 결과를 받기 전까지 기본안 DIRECT로 진행한다
- [ ] T003 [P] 저장소 점검 스크립트를 저장소에 넣는다: `scripts/check-storage.sh`(로컬 compose의 minio에 `pgsty/mc`로 버킷·정책·앱 키를 준비한 뒤 `presign_check.py` 실행, PASS/FAIL 개수 출력, 실패 시 종료 코드 1)와 `scripts/lib/presign_check.py`(환경 변수 `S3_ENDPOINT`·`APP_KEY`·`APP_SECRET`·`BUCKET`·`SITE_ORIGIN`만 받아 contracts/storage.md §1-1의 11가지를 순서대로 시험, 비밀값을 출력하지 않음). 근거: 23 §2-3, 04 §6-1
- [ ] T004 [P] `docker-compose.yml`을 고친다: `minio`의 `9001` 포트 매핑 제거(FR-026 "관리 콘솔 비공개"), `MINIO_API_CORS_ALLOW_ORIGIN`에 `http://localhost:5173` 추가, 일회성 `minio-init` 서비스(`pgsty/mc` 고정 버전, `depends_on: minio healthy`)가 버킷 `blog`·`images/*` 익명 `GetObject` 정책·앱 전용 사용자(`PutObject`·`GetObject`·`DeleteObject`, 키는 `.env`의 `BLOG_IMAGE_STORAGE_ACCESS_KEY`·`BLOG_IMAGE_STORAGE_SECRET_KEY`)를 만들게 하고, `app`에 `BLOG_IMAGE_STORAGE_ENDPOINT: http://minio:9000`을 넣는다. `.env.example`에 키 이름만 추가한다(값 없음)
- [ ] T005 [P] `backend/pom.xml`에 AWS SDK for Java v2 BOM(`software.amazon.awssdk:bom`, 고정 버전)과 `s3`·`url-connection-client`를 추가하고 `apache-client`·`netty-nio-client`를 제외한다. `./mvnw -pl backend dependency:tree`로 새 의존이 이 셋뿐인지 확인한다(research R1)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 설정값, 이유 코드, 형식·키·머리말 검사, 저장소 추상화와 S3 구현, 사진 행 저장소, 주소 판별 한 곳, 테스트용 저장소 컨테이너, 화면 API

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T006 [P] `ImageHeaderReader` 단위 테스트 `T/media/unit/ImageHeaderReaderTest.java`: 테스트 자원 `TR/images/`(작은 JPEG·PNG·GIF89a·WebP VP8/VP8L/VP8X 각 1개, 같은 내용을 확장자만 바꾼 파일, 잘린 파일, SOF가 64KB 밖에 있는 JPEG, 머리말만 30000×30000인 PNG, 301프레임 GIF)를 만들어 ① 매직 바이트 형식 판별 ② 가로·세로 ③ GIF 프레임 수(301번째에서 멈춤) ④ 잘린 파일·SOF 없음 → `CORRUPT` ⑤ 거대 해상도는 해독 없이 크기만 읽음(힙 사용량 상한 확인)을 검증한다(research R6, FR-007·FR-037)
- [ ] T007 [P] `StorageKeys`·`ImageFormat` 단위 테스트 `T/media/unit/StorageKeysTest.java`: 키 모양 `images/{yyyy}/{MM}/{uuid}.{ext}`·`_thumb.{ext}`, 서비스 시간대 날짜 경계(2026-10-31T15:00Z → `2026/11`), 원본·썸네일 uuid 같음, MIME → 확장자 4종, 원래 파일 이름이 들어갈 자리가 없음(research R7, FR-009)
- [ ] T008 [P] `ImageUrls` 단위 테스트 `T/media/unit/ImageUrlsTest.java`: 002 `ImageReferenceResolverAdapterIT`의 판별 경우를 그대로 옮긴다 — 지금 공개 주소·옛 주소 목록 + 키 모양일 때만 키, 쿼리·조각이 붙으면 아님, 대소문자 다르면 아님, 끝 `/` 정리, 썸네일 키도 판별(research R10, FR-024)
- [ ] T009 [P] 저장소 통합 테스트 `T/media/integration/S3ImageStorageIT.java`(T014 `StorageIntegrationTestBase` 상속): ① `prepareUpload`가 준 주소로 정확한 크기·형식 PUT → 200, `head`가 크기·`Content-Type`·`Cache-Control` 반환 ② 서명보다 큰 본문·다른 `Content-Type` → 403 ③ 만료(설정 1초) → 403 ④ `readHead(key, 64KB)` ⑤ `deleteAll`이 없는 키도 성공으로 보고 실패 키만 돌려줌 ⑥ 익명 GET `images/*` 200, 익명 목록 403(contracts/storage.md §1)
- [ ] T010 [P] 사진 행 저장소 통합 테스트 `T/media/integration/ImageRepositoryIT.java`: `insertTemp`(width NULL), `lockOwned(id, me)`(남의 것·없는 것 empty), `markCompleted`(크기·가로·세로), `deleteById`, `sumUsageBytes(me)`(TEMP·연결·연결 해제·PROFILE 포함, 남의 것 제외), `cleanupCandidates(now, 1000)`(TEMP 24시간·detached 7일, 현재 프로필 제외) — data-model §1-1·§2

### Implementation for Foundational

- [ ] T011 [P] 설정값 `B/media/application/ImageProperties.java`(`@ConfigurationProperties("blog.image")`, data-model §5의 모든 키, `upload-mode` enum `DIRECT|PROXY`, 중첩 `Cleanup`·`Storage` record, `@Validated`로 `maxUploadBytes ≤ 10485760`·`maxThumbBytes ≤ 1048576`·`storage.accessKey`·`secretKey` 비어 있지 않음)와 `R/application.yml` 기본값을 추가한다. 비밀값은 `${…:}` 환경 변수 자리만 둔다(헌법 VII, research R19)
- [ ] T012 [P] 이유 코드 `B/media/domain/MediaReasonCode.java`(`ReasonCode` 구현): `UNSUPPORTED_IMAGE_TYPE`·`IMAGE_TOO_LARGE`·`THUMBNAIL_TOO_LARGE`·`THUMBNAIL_REQUIRED`·`THUMBNAIL_NOT_ALLOWED`(칸 코드), `STORAGE_QUOTA_EXCEEDED`(409), `DAILY_UPLOAD_LIMIT`(429), `IMAGE_NOT_UPLOADED`(400), `IMAGE_REJECTED`(400). 문구는 data-model §7 그대로, 끝 마침표 없음. 1분 제한은 공통 `CommonReasonCode.TOO_MANY_REQUESTS`를 쓴다(007 Q3)
- [ ] T013 [P] 도메인 값 `B/media/domain/ImageFormat.java`(MIME·확장자·매직 바이트), `ImagePurpose.java`, `ImageStatus.java`, `StorageKeys.java`(`Clock`·`blog.time-zone` 주입, `newPair(format, thumbFormat)`), `ImageInspection.java`(record: format, width, height, frames, `Optional<RejectReason>`), `ImageHeaderReader.java`(research R6 규칙, 입력은 `InputStream` + 최대 읽기 바이트)를 만든다(T006·T007 통과)
- [ ] T014 [P] 테스트용 저장소 `T/support/MinioContainerSupport.java`(`GenericContainer("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")`, 9000, CORS 출처, 관리 키로 SDK를 써서 버킷·`images/*` 익명 읽기 정책·앱 전용 사용자 생성)와 `T/support/StorageIntegrationTestBase.java`(`IntegrationTestBase` 상속, `@DynamicPropertySource`로 `blog.image.storage.*` 주입, 테스트마다 `images/` 객체 비우기)를 만든다(research R18)
- [ ] T015 저장소 추상화 `B/media/infra/storage/ImageStorage.java`(`UploadTarget prepareUpload(key, contentType, size, ttl)`, `Optional<StoredObject> head(key)`, `byte[] readHead(key, maxBytes)`, `InputStream openStream(key)`, `Set<String> deleteAll(Collection<String> keys)` → 실패 키, `void put(key, contentType, size, InputStream)`(PROXY용))와 `StorageClientConfig.java`(`S3Client`: `endpoint`, `S3Presigner`: `presign-endpoint`, 둘 다 `forcePathStyle(true)`, 리전, 정적 자격 증명), `S3ImageStorage.java`(Presign 때 `contentType`·`contentLength`·`cacheControl` 서명, `DeleteObjects` 1,000키 단위)를 구현한다(T005·T011·T014 의존, T009 통과, research R1·R20)
- [ ] T016 사진 행 저장소 `B/media/infra/ImageRepository.java`(`JdbcClient`): T010의 메서드 + `findCompletedOwned(Collection<String> keys, long ownerId)`(`width IS NOT NULL`, 조회 1번), `findByThumbKey(thumbKey)`, `deleteIfStillEligible(ids, now)`(대상 조건 재확인) 를 구현한다(T010 통과)
- [ ] T017 주소 판별 한 곳 `B/media/application/ImageUrls.java`(`Optional<String> keyOf(url)`, `boolean isOurs(url)`, 지금 공개 주소·옛 주소 목록은 `CoreProperties.Image`에서)를 만들고, `B/media/infra/ImageReferenceResolverAdapter.java`의 `parseKey`·`STORAGE_KEY`를 `ImageUrls`로 옮겨 위임한다. `findOwned`는 `ImageRepository.findCompletedOwned`로 바꾼다(완료된 사진만, research R5·R10). 클래스 주석의 `TODO(003)`·교체 점검표를 "003 최종 — 규칙과 회귀 테스트 목록"으로 바꾼다. 002 `T/media/integration/ImageReferenceResolverAdapterIT.java`와 T008이 함께 통과해야 한다
- [ ] T018 [P] 회원 행 잠금 공개 Service `B/account/application/MemberLockService.java`(`@Transactional(propagation = MANDATORY) void lockForUpdate(long memberId)` — `MemberRepository.findByIdForUpdate` 위임, 없으면 `NotFoundException`)를 추가한다(plan Constitution II, research R8). 001 소유 파일은 고치지 않는다
- [ ] T019 [P] CSP `connect-src`에 `blog.image.storage.presign-endpoint` 출처를 더한다: `B/media/web/StorageCspContributor.java`(001 `CspContributor`가 `img-src`만 받으면 `B/shared/web/SecurityHeadersFilter.java`에 `connect-src` 확장 메서드를 추가하고 001에 알림). 공개 주소와 presign 주소가 같으면 중복을 넣지 않는다. 검증 `T/media/integration/StorageCspIT.java`(research R20)
- [ ] T020 [P] 화면 API `F/api/images.ts`(`presign(req)`, `putToStorage(target, blob, signal)`(target.headers 그대로, `credentials: 'omit'`), `complete(imageId)`, `getStorageUsage()`), 타입 `F/api/types/images.ts`(contracts/openapi.yaml 스키마), 오류 code → 문구 `F/features/image-upload/uploadMessages.ts`(data-model §7, 503은 code와 상관없이 "잠시 후 다시 시도해 주세요", research R9)와 단위 테스트 `F/features/image-upload/uploadMessages.test.ts`

**Checkpoint**: Foundation ready — 저장소 컨테이너에 서명 PUT·HEAD·삭제가 되고, 머리말 검사·주소 판별이 단위 테스트를 통과한다

---

## Phase 3: User Story 1 - 글에 사진 넣기 (Priority: P1) 🎯 MVP

**Goal**: 인증 회원이 붙여넣은 사진이 브라우저에서 줄어들고 메타데이터가 지워진 뒤 저장소에 직접 올라가고, 서버가 다시 검사한 뒤 본문에 주소만 남는다 (C-IMG-1)

**Independent Test**: 인증 회원으로 GPS가 든 5MB JPEG를 붙여넣고 본문에 주소가 들어가며 저장된 파일에 위치 정보가 없고 이름이 무작위인지 확인한다(`ImagePresignIT`·`ImageCompleteIT`·Playwright `image-upload.spec.ts`)

### Tests for User Story 1 ⚠️

- [ ] T021 [P] [US1] presign 통합 테스트 `T/media/integration/ImagePresignIT.java`(StorageIntegrationTestBase): `US1_6_비회원_401`·`인증전_403_EMAIL_NOT_VERIFIED`·탈퇴 유예 403 `ACCOUNT_WITHDRAWN`·남은 세션 정지 403 `ACCOUNT_SUSPENDED`·CSRF 없음 403; `US1_4_형식_크기_칸오류` — `image/svg+xml`·`size 10485761`·POST인데 썸네일 없음·PROFILE인데 썸네일 있음 → 400 `VALIDATION_FAILED` + `errors[].field`; 정상 → 201, `image` 행 TEMP·width NULL·신고 크기, 응답 주소 2개의 키가 data-model §3 모양이고 요청 어디에도 파일 이름 칸이 없음(US1 #5); `US1_7_1분_21번째_429_TOO_MANY_REQUESTS` + `Retry-After`, 거부된 요청의 TEMP 행이 남지 않음(보상), Redis 중지(`RedisOutage`) 중에는 21번째도 201(02 §2-1)
- [ ] T022 [P] [US1] complete 통합 테스트 `T/media/integration/ImageCompleteIT.java`: 정상 WebP·JPEG(사파리 대체)·PNG 원본 + 썸네일 PUT 뒤 complete → 200, 행에 실제 크기·가로·세로, `url`이 지금 공개 주소 + 키; `US1_3_확장자_위장` — `image/jpeg`로 신고하고 PNG 바이트를 PUT(서명 형식은 맞춤) → 400 `IMAGE_REJECTED` `details.reason=TYPE_MISMATCH`, 두 객체와 행이 없음; 썸네일 가로 641px·원본 4097px·잘린 파일 → 400, 객체·행 삭제; 파일을 올리지 않음 → 400 `IMAGE_NOT_UPLOADED`, 행 삭제; 같은 사진 complete 두 번 → 두 번째도 같은 200(멱등); 동시에 두 번 → 500 없음(research R24); 저장소 컨테이너를 멈추면 503이고 행은 남음
- [ ] T023 [P] [US1] 권한 매트릭스 연결: `TR/permission/image.csv`(열 형식 동일, 행: ANONYMOUS/UNVERIFIED/MEMBER/ADMIN/SUSPENDED/WITHDRAWN × `image.presign`(NONE)·`image.complete`(본인 사진 / 다른 회원 사진 — 실행기가 준비) → 401/403/201/404, owner 003)와 실행기 `T/media/permission/PresignImageAction.java`·`CompleteImageAction.java`(`PermissionAction` `@Component`), 테스트 클래스 `T/media/integration/ImagePermissionMatrixIT.java`(`AbstractPermissionMatrixIT` 상속, `@CsvFileSource("/permission/image.csv")`)를 만든다(research R26, ANALYSIS-tier-a R6)
- [ ] T024 [P] [US1] 브라우저 처리 단위 테스트 `F/features/image-upload/imageProcessor.test.ts`(캔버스·`createImageBitmap`을 가짜로 주입): 긴 변 4000px → 1920px, `toBlob('image/webp')`가 `image/png`를 돌려주면 `image/jpeg` 0.8로 다시 만듦(Q4), 썸네일 가로 640px·1MB 넘으면 품질 0.7→0.6, 그래도 넘으면 실패 결과, 원래 파일 50MB 초과·알 수 없는 형식(매직 바이트)은 처리 전에 거부, 결과 어디에도 원래 파일 이름 없음
- [ ] T025 [P] [US1] 업로드 흐름 단위 테스트 `F/features/image-upload/uploadImage.test.ts`(`fetch` 가짜): presign → 원본·썸네일 PUT(헤더 그대로, `credentials: 'omit'`) → complete → `{kind: 'uploaded', url}`; 저장소 PUT 403이면 presign부터 한 번 다시; 각 오류 code의 분류(R12 표: 보관 vs 즉시 안내)
- [ ] T026 [P] [US1] 에디터 삽입 테스트 `F/features/image-upload/useImageInsert.test.tsx`: textarea 붙여넣기·끌어놓기·[사진] 버튼으로 커서 위치에 대기 표시 → 성공 시 `![](주소)`로 한 번에 교체, 대체글 자리 비움(FR-032), 여러 장은 순서대로, 업로드 중 다른 입력이 있어도 표시 위치를 잃지 않음
- [ ] T027 [P] [US1] 종단 테스트 `E/image-upload.spec.ts`(Playwright, 크로미엄·웹킷): GPS EXIF가 든 JPEG 고정 파일을 붙여넣기 → 본문 주소 → 상세에서 이미지 표시 → 저장소에서 받은 파일 바이트에 `Exif`·`GPS` 표식이 없음(SC-002), 웹킷에서는 주소 확장자가 `.jpg`(Q4)

### Implementation for User Story 1

- [ ] T028 [US1] `B/media/application/ImageUploadService.java` presign(1차): 계정 상태 가드 → 칸 검증(data-model §7) → 트랜잭션 A에서 `StorageKeys.newPair`로 키를 정하고 `ImageRepository.insertTemp` → 커밋 → `RateLimiter.tryAcquire("ratelimit:image:{me}", perMinuteLimit, 1분)` 거부면 트랜잭션 B로 행 삭제 후 `TooManyRequestsException` → `ImageStorage.prepareUpload` 2번 → `ImageUploadTicket`. 용량·하루 한도는 US4(T061)에서 이 메서드에 끼운다(T021 중 US1 부분 통과, research R8)
- [ ] T029 [US1] 같은 클래스 complete: `ImageRepository.lockOwned`가 없으면 `NotFoundException` → 이미 완료면 저장된 값으로 응답 → 트랜잭션 밖에서 `head` 2번(없음 → `IMAGE_NOT_UPLOADED`, 크기 > 신고 → `SIZE_MISMATCH`) → `readHead`로 `ImageHeaderReader`(형식이 신고와 다름 → `TYPE_MISMATCH`, 한도 초과 → `DIMENSION_EXCEEDED`, 썸네일 가로 640px 초과 → `THUMBNAIL_INVALID`, PROFILE 256×256 아님 → `PROFILE_SIZE_INVALID`) → 실패면 `deleteAll` + 행 삭제 후 `IMAGE_REJECTED` → 통과면 짧은 트랜잭션에서 다시 잠그고 `markCompleted`(T022 통과). GIF 프레임 검사는 US6(T076)에서 더한다
- [ ] T030 [US1] `B/media/web/ImageUploadController.java`: `POST /api/images/presign`(201), `POST /api/images/{imageId}/complete`(200), 요청 DTO `PresignRequest`(알 수 없는 필드 무시하지 않고 400 — 파일 이름 칸이 들어와도 저장하지 않음을 테스트로 고정), 응답 DTO는 contracts/openapi.yaml 그대로. 로그에 키·크기·회원 번호만 남기고 주소 서명 쿼리는 남기지 않는다(T021·T022 통과)
- [ ] T031 [P] [US1] 권한 매트릭스 실행기를 완성해 `ImagePermissionMatrixIT`의 owner 003 행이 건너뛰지 않고 통과하게 한다(T023 통과)
- [ ] T032 [P] [US1] 브라우저 처리 `F/features/image-upload/imageProcessor.ts`(research R3: 매직 바이트 판별, `createImageBitmap(file, {imageOrientation: 'from-image'})`, 1920px, WebP 0.8 → 결과 `type` 확인 후 JPEG 0.8 대체, 640px 썸네일 같은 규칙, 1MB 넘으면 품질 낮춤, GIF는 원본 그대로 + 첫 장면 썸네일)를 구현한다(T024 통과)
- [ ] T033 [US1] 한 장 업로드 `F/features/image-upload/uploadImage.ts`(T020·T032 의존): 처리 → presign → `putToStorage` 2번(30초 `AbortController`) → complete → 결과 `uploaded | pending | rejected`(research R12). `pending`이면 호출자가 기기에 보관한다(US3)(T025 통과)
- [ ] T034 [US1] 에디터 연결 `F/features/image-upload/useImageInsert.ts`와 `F/pages/EditorPage.tsx`(textarea `onPaste`·`onDrop`, 툴바 [사진] 버튼 + 숨은 `<input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple>`): 대기 표시 → `uploadImage` → 교체. 업로드 중에는 "사진 올리는 중…" 상태를 저장 상태 옆에 보인다. 자동 저장은 막지 않는다(T026 통과, FR-020)
- [ ] T035 [US1] 종단 테스트 T027을 통과시키고, 개발자 도구 Network 기준 5MB JPEG의 PUT 크기를 측정해 300~500KB인지 quickstart §3-1 결과로 기록한다(SC-001)

**Checkpoint**: 사진을 붙여넣으면 저장소에 올라가고 본문에 주소가 남는다(MVP 최소 증분)

---

## Phase 4: User Story 2 - 내 사진은 나만 내 글에 연결된다 (Priority: P1)

**Goal**: 남이 올린 우리 사진 주소는 내 글에 연결되지 않고 링크로 보이며, 남의 업로드 번호로 완료 확인을 하면 404다 (23 §6-1)

**Independent Test**: 회원 A의 사진 주소를 B의 글에 넣고 발행해 B의 글에서는 링크, A의 글에서는 이미지인지 확인한다(`ImageLinkIT` + 002 회귀)

### Tests for User Story 2 ⚠️

- [ ] T036 [P] [US2] `T/media/integration/ImageLinkIT.java`: `US2_1_남의_사진은_연결되지_않고_링크` — A의 완료 사진 주소를 B 글에 넣어 발행 → `post_image` 0행, 렌더링 결과 `<a …>사진: …</a>`, A의 행 `status`·`detached_at` 그대로; `US2_2_남의_업로드번호_complete_404`(관리자 포함, 고정 본문); 완료 전(width NULL) 내 사진 주소는 연결되지 않음(R5); `US2_3_비공개글_사진_익명_GET_200`(저장소 컨테이너)·익명 목록 403
- [ ] T037 [P] [US2] 프로필 사진 테스트 `T/media/integration/ProfileImageServiceIT.java`: PROFILE presign·complete(256×256 WebP) 뒤 `attach` → 현재 사진 1장(`uq_image_profile_current`), 교체 시 이전 사진 `detached_at` 기록, 남의 사진·POST 사진·완료 전 사진 → 400 `INVALID_PROFILE_IMAGE`, 255×256 → complete 400 `PROFILE_SIZE_INVALID`(research R14)
- [ ] T038 [P] [US2] 대표 이미지 테스트 `T/media/OgImageResolverIntegrationTest.java`(005 기존 테스트 확장): 지금 주소·옛 주소의 `thumbnail_url` 모두 원본 주소로, GIF는 원본 GIF, 썸네일 없는 옛 사진은 그대로, NULL은 기본 이미지(research R15, FR-040)

### Implementation for User Story 2

- [ ] T039 [US2] `B/media/application/ImageService.java`를 최종 구현으로 확정한다: `attachPostImages`의 INSERT·UPDATE에 `AND i.width IS NOT NULL`(완료된 사진만) 조건을 더하고, 클래스 주석의 `TODO(003)`·교체 점검표를 "003 최종 규칙"과 회귀 테스트 목록으로 바꾼다. 002 회귀 `PublishIT`·`ManualSaveIT`·`AutosaveFlushJobIT`·`PublishQueryCountIT`·`PublishTransactionIT`와 T036이 함께 통과해야 한다(research R10)
- [ ] T040 [US2] `B/media/application/OgImageResolver.java`를 최종 구현으로 바꾼다: 주소 판별은 `ImageUrls.keyOf`(옛 주소 포함), 원본 찾기는 `ImageRepository.findByThumbKey`, 클래스 주석의 "임시 구현" 문구를 지운다(T038 통과)
- [ ] T041 [US2] 프로필 사진 연결 `B/media/application/ProfileImageService.java`(001 T116 포트가 있으면 그 인터페이스를 구현하고 `TemporaryProfileImageService`를 지운다. 없으면 같은 시그니처 `attach(memberId, imageId)`·`detach(memberId)`로 만들고 001 T116에 "003이 대신함"을 알린다): contracts/storage.md §3-3 SQL, `MemberLockService` 잠금 안에서 호출됨을 Javadoc에 적는다(T037 통과)
- [ ] T042 [P] [US2] 렌더러 회귀 `T/shared/markdown/ContentRendererWiringIT.java`(002 기존)에 "완료 전 사진은 링크" 경우를 더하고 통과시킨다
- [ ] T043 [US2] 002 `B/post/application/SavedContentImages.java`·발행 경로가 `ImageService`를 그대로 쓰는지 확인하고, 주석의 "003 FR-022" 참조를 최종 규칙 설명으로 고친다(코드 변경 없음이면 주석만)
- [ ] T044 [P] [US2] 화면: 본문 미리보기·상세에서 남의 사진이 링크로 보이는 것은 서버 렌더링 결과라 화면 변경이 없음을 `F/components/editor/__tests__/PreviewPane.test.tsx`에 회귀 경우로 더한다(링크 글자 "사진: …")
- [ ] T045 [US2] `T/media/integration/ImageReferenceResolverAdapterIT.java`(002 기존)의 판별 경우가 `ImageUrls` 위임 뒤에도 통과하는지 확인하고, 중복된 경우는 T008로 옮겨 정리한다
- [ ] T046 [US2] **006 머지 후** `B/media/application/ImagePostPurgeStep.java`(006 T060 임시 구현)의 소유를 넘겨받는다: 주석의 "003 plan에서 소유·검토 후 교체" 문구를 "003 소유"로 바꾸고, `T/media/integration/ImagePostPurgeStepIT.java`(그 글에만 연결된 사진만 `detached_at`, 다른 글·휴지통 글과 함께 쓰는 사진은 그대로, 트랜잭션 롤백 시 되돌림)를 추가한다. SQL은 바꾸지 않는다(contracts/storage.md §3-1)
- [ ] T047 [US2] 001 연결: `PATCH /api/me/profile {profileImageId}`(001 T118)가 T041을 부르는지 확인하고, 001 `T/account/integration/ProfileUpdateIntegrationTest.java`의 사진 경우를 실제 presign·complete 흐름으로 바꾸도록 001 담당에게 요청한다(001 T111·T116 교체 확인 작업)

**Checkpoint**: US1 + US2로 사진 업로드와 소유 규칙이 완성된다(권장 MVP)

---

## Phase 5: User Story 3 - 오프라인·실패 시에도 사진을 잃지 않는다 (Priority: P2)

**Goal**: 연결 끊김·시간 초과·5xx로 실패한 사진은 기기에 보관되어 보이고, 연결되면 다시 올라가 주소로 바뀐다. 다시 해도 안 되는 실패는 바로 안내한다 (Clarifications Q5)

**Independent Test**: 오프라인에서 사진을 붙여넣고, 보이는지·자동 저장 성공·발행 거부·재연결 후 교체를 확인한다(Vitest `pendingUploads.test.ts`, Playwright 오프라인 시나리오)

### Tests for User Story 3 ⚠️

- [ ] T048 [P] [US3] `F/features/image-upload/pendingUploads.test.ts`(fake-indexeddb): `pending` 결과면 `draft:{member}:{post}.pendingImages`에 `{localId, blob}` 저장 + 본문 `![](local:{localId})` + `blob:` 미리보기 주소; `online` 이벤트·에디터 열기 때 차례로 다시 처리 → 성공하면 본문 교체·대기열 제거·변경 표시(dirty); 다시 시도 중 409·429·400이면 대기열에서 빼고 안내(본문 표시는 남김, R12); 로그아웃 `clearMemberDrafts` 뒤 대기 사진 없음(002 FR-014)
- [ ] T049 [P] [US3] `F/features/image-upload/uploadImage.test.ts`에 실패 분류 경우를 더한다: `navigator.onLine=false`·`TypeError`·30초 시간 초과·5xx → `pending`, 409·429·400·401·403 → `rejected`(보관 안 함)
- [ ] T050 [P] [US3] `E/image-upload.spec.ts`에 오프라인 시나리오를 더한다: `context.setOffline(true)` → 붙여넣기 → 이미지 표시·자동 저장 성공 → [발행] "업로드가 끝나지 않은 사진이 있어요"(002 `PENDING_IMAGES`) → 온라인 → 본문 주소로 교체 → 발행 성공(US3 #1~#4, SC-008)

### Implementation for User Story 3

- [ ] T051 [US3] `F/features/image-upload/pendingUploads.ts`: `holdPending(memberId, postId, file)`(localforage 저장 + 표시 문자열), `retryPending(memberId, postId, replace)`(한 번에 하나씩, 실패하면 지수 대기 최대 5분), `window.addEventListener('online')` 등록·해제, 대기 사진 수 반환(T048 통과)
- [ ] T052 [US3] `useImageInsert`·`F/features/editor/openEditor.ts` 연결: `uploadImage` 결과가 `pending`이면 `holdPending`, 에디터를 열 때와 `online` 때 `retryPending`. 저장 상태 옆에 "업로드 대기 사진 N장"을 보인다(T049·T050 통과)
- [ ] T053 [US3] 화면이 `local:` 이미지를 `blob:` 주소로 보이게 하는 미리보기 변환을 `F/components/editor/PreviewPane.tsx`에 더한다(서버 미리보기 결과의 링크 대신 기기 사본 표시. CSP `img-src blob:`은 001이 이미 허용). 서버로 보내는 본문은 `local:` 그대로다

**Checkpoint**: 오프라인에서도 사진이 사라지지 않고, 발행은 업로드가 끝난 뒤에만 된다

---

## Phase 6: User Story 4 - 사용자별 저장 공간 한도 (Priority: P2)

**Goal**: 1인 1GB·하루 200장을 넘지 않게 하고, 설정·에디터에서 사용량을 보여 준다 (23 I-2)

**Independent Test**: 1GB 근처 회원으로 presign 10건을 동시에 보내 합계가 한도를 넘지 않는지, 201번째가 거부되는지, 설정 화면 막대가 보이는지 확인한다(`ImagePresignIT`·`StorageUsageApiIT`)

### Tests for User Story 4 ⚠️

- [ ] T054 [P] [US4] `T/media/integration/ImagePresignIT.java`에 한도 경우를 더한다(T021 다음): `US4_1_용량초과_409` + `details`; `US4_2_동시10건_합계가_한도를_넘지_않음`(`ExecutorService` 10스레드, 성공 합계 ≤ 1GB, 500 없음, SC-004); `US4_3_하루_201번째_429_DAILY_UPLOAD_LIMIT` + `Retry-After`(다음 0시 KST까지), 날짜는 `Clock` 고정; 판정 순서 — 400 칸 오류가 409보다, 409가 429보다 먼저; 1분 제한(429 `TOO_MANY_REQUESTS`)에 걸리면 하루 장수가 늘지 않음; 실패한 complete 뒤에도 하루 장수 유지(FR-016); Redis 중지 중 하루 한도 통과
- [ ] T055 [P] [US4] `T/media/integration/StorageUsageApiIT.java`: `GET /api/me/storage` → 내 TEMP·연결·연결 해제·PROFILE 합계, 남의 사진 제외(FR-014), `todayCount`, `limits`, `Cache-Control: private, no-store`, 비회원 401, 인증 전 회원 200(보기만), Redis 중지 시 `todayCount: null`
- [ ] T056 [P] [US4] `F/components/__tests__/StorageUsageBar.test.tsx`: "사진 저장 공간 312MB / 1GB" 글자·막대 비율·"지운 사진의 공간은 7일 뒤 돌아와요", 단위 표시(KB·MB·GB 한 자리), 불러오기 실패 시 막대 대신 "불러오지 못했어요"
- [ ] T057 [P] [US4] `F/features/image-upload/__tests__/storageHint.test.ts`: 사용량 90% 초과면 "남은 공간 약 100MB", 이하면 안내 없음, 409·`DAILY_UPLOAD_LIMIT` 문구가 data-model §7과 같음

### Implementation for User Story 4

- [ ] T058 [P] [US4] `B/media/application/StorageQuotaService.java`: `long usedBytes(me)`(`ImageRepository.sumUsageBytes`), `void checkQuota(me, addBytes)`(호출자 트랜잭션에서 `MemberLockService.lockForUpdate` 후 합계 비교 → `BusinessRuleException(STORAGE_QUOTA_EXCEEDED, details)`), `DailyReservation reserveDaily(me, today)`(Redis Lua INCR·첫 증가 EXPIRE 2일·한도 초과면 DECR 후 거부 → `TooManyRequestsException(DAILY_UPLOAD_LIMIT, 다음 0시까지 초)`, `RedisGuard`로 장애 시 통과), `void releaseDaily(reservation)`, `Integer todayCount(me)`(장애면 null)
- [ ] T059 [US4] `ImageUploadService.presign`에 한도를 끼운다(T028 다음): 트랜잭션 A 안에서 `checkQuota` → INSERT, 커밋 뒤 `reserveDaily` → `RateLimiter` 거부면 `releaseDaily` → 거부면 보상 삭제(research R8 순서 그대로)(T054 통과)
- [ ] T060 [US4] `B/media/web/StorageUsageController.java`: `GET /api/me/storage`(로그인만, 계정 상태 가드 `ActionKind`는 보기 전용이라 인증 전 허용 — 탈퇴 유예만 403), 응답 `StorageUsage`(research R25)(T055 통과)
- [ ] T061 [P] [US4] `F/components/StorageUsageBar.tsx`(T056 통과)와 `F/features/image-upload/storageHint.ts`(T057 통과)를 구현하고, 에디터 툴바 [사진] 버튼 옆에 90% 안내를 붙인다(`F/pages/EditorPage.tsx`, 에디터를 열 때 `getStorageUsage` 한 번)
- [ ] T062 [US4] 설정 화면 연결: 001 설정 화면(`/settings`, 001 US6)이 있으면 "사진 저장 공간" 항목으로 `StorageUsageBar`를 넣고, 없으면 001 US6 tasks에 넣을 위치를 알리는 확인 작업으로 남긴다(001 후속)
- [ ] T063 [US4] 화면의 고르는 순간 검사가 `limits` 값(50MB·10MB)을 쓰도록 `imageProcessor.ts`의 상수를 `getStorageUsage().limits`로 바꾼다(헌법 VII)

**Checkpoint**: 한도가 동시 요청에도 지켜지고 사용량이 보인다

---

## Phase 7: User Story 5 - 대체글 권유 (Priority: P2)

**Goal**: 발행 설정 창에서 대체글 없는 사진 수를 알려 주고 넣을 수 있게 하되, 발행은 막지 않는다 (23 I-3)

**Independent Test**: 대체글 없는 사진 2장이 든 글을 발행하면서 안내·입력·무시 발행·`alt=""` 출력을 확인한다(`AltTextPanel.test.tsx`, 렌더러 회귀)

### Tests for User Story 5 ⚠️

- [ ] T064 [P] [US5] `F/features/image-upload/altText.test.ts`: 본문에서 우리 사진(지금 공개 주소로 시작) 중 alt가 공백뿐인 것만 찾기, 코드 블록·인라인 코드 안 제외, 외부·`local:` 사진 제외, `setAlt(index, text)`가 그 위치만 바꾸고 `]`·`\`를 이스케이프
- [ ] T065 [P] [US5] `F/components/editor/__tests__/AltTextPanel.test.tsx`: "대체글이 없는 사진이 2장 있어요 [대체글 넣기]" → 사진별 미리보기 + 입력칸 + 도움말 문구(FR-033), 125자 초과 안내(거부 안 함, FR-034), 입력하면 본문 반영, 0장이면 패널 없음, [발행] 버튼은 항상 활성
- [ ] T066 [P] [US5] 렌더러 회귀 `T/shared/markdown/AltRenderingTest.java`: `![](우리 사진)` → `alt=""` 속성이 있음(빠지지 않음), 카드 썸네일 alt는 글 제목(005 `PostCard` 확인), 프로필 사진 alt 빈 값(FR-035)

### Implementation for User Story 5

- [ ] T067 [US5] `F/features/image-upload/altText.ts`(T064 통과)와 `F/components/editor/AltTextPanel.tsx`(T065 통과)를 구현하고 002 `F/components/editor/PublishDialog.tsx`에 넣는다(발행 설정 창 위쪽, 접힌 상태 기본)
- [ ] T068 [US5] T066이 실패하면 002 렌더러(`B/shared/infra/markdown/`)의 alt 출력을 고친다(002에 알림). 통과하면 코드 변경 없음

**Checkpoint**: 대체글 권유가 보이고 발행은 막히지 않는다

---

## Phase 8: User Story 6 - 움직이는 GIF (Priority: P3)

**Goal**: GIF는 변환 없이 올라가고 본문에서 정지 장면 + ▶로 보이며 누르면 재생된다. 너무 크거나 프레임이 많은 GIF는 거부된다 (23 I-4)

**Independent Test**: 1920px 이하·300프레임 이하 GIF를 올려 상세에서 정지 → 재생 → 정지를 확인하고, 2000px·301프레임 GIF가 거부되는지 확인한다

### Tests for User Story 6 ⚠️

- [ ] T069 [P] [US6] `T/media/integration/ImageCompleteIT.java`에 GIF 경우를 더한다(T022 다음): `US6_1_정상_GIF` 원본 바이트가 올린 것과 같음(변환 없음), `US6_2_1921px_GIF_거부_삭제`(`GIF_TOO_LARGE`), `US6_3_301프레임_거부_삭제`(`GIF_TOO_MANY_FRAMES`, 파일 전체를 메모리에 올리지 않음 — 스트림 사용)
- [ ] T070 [P] [US6] 렌더러 테스트 `T/shared/markdown/GifRenderingTest.java`와 자원 `TR/markdown/syntax/13-owned-gif.md`·`.html`: 작성자 GIF(썸네일 있음) → `<a href="{원본}" title="움직이는 이미지 재생" target="_blank" rel="noopener noreferrer nofollow ugc"><img src="{thumb_storage_key 주소}" alt="…" loading="lazy" decoding="async"></a>`, 썸네일 확장자가 `.jpg`여도 그 키 그대로(Q4), 썸네일 없는 옛 GIF는 `<img>` 그대로, 남의 GIF는 링크, 정화 뒤에도 속성 유지, `RenderVersion.CURRENT == 2`
- [ ] T071 [P] [US6] `F/features/image-upload/gifInspector.test.ts`: GIF 머리말에서 가로·세로, 프레임 수(301에서 멈춤), 1921px이면 "GIF는 가로·세로 1920px까지 올릴 수 있어요", 움직이는 WebP·APNG 안내 문구(FR-038)
- [ ] T072 [P] [US6] `F/features/post-detail/__tests__/gifPlayer.test.ts`: `a[href$=".gif"] > img`에 `role="button"`·`aria-pressed`·`aria-label`(대체글, 비면 "움직이는 이미지 재생"), 클릭·Enter·Space로 `src`가 원본 ↔ 썸네일, 기본 이동 막음, GIF가 아닌 링크는 건드리지 않음, 두 번 불러도 중복 등록 없음

### Implementation for User Story 6

- [ ] T073 [US6] `B/shared/infra/markdown/AstTransformer.java`에 작성자 GIF 변환을 더하고(`OwnedImage.thumbStorageKey()` 사용, research R11), `B/shared/application/markdown/RenderVersion.java`의 `CURRENT`를 2로 올린다. 002 렌더러 회귀(`T/shared/markdown/` 전체)와 T070을 함께 통과시킨다(002 소유 파일 — 002 담당에게 변경 알림)
- [ ] T074 [US6] 002 `RerenderJob`이 `render_version < 2` 글을 다시 렌더링하는지 `T/post/integration/RerenderJobIT.java`(002 기존)에 GIF 글 경우를 더해 확인한다
- [ ] T075 [P] [US6] `F/features/image-upload/gifInspector.ts`(T071 통과)를 구현하고 `imageProcessor.ts`의 GIF 갈래에서 고르는 순간 검사한다. 업로드 화면(파일 선택 안내)에 FR-038 문구를 보인다
- [ ] T076 [US6] `ImageUploadService.complete`에 GIF 검사(원본 `openStream` → `ImageHeaderReader` 프레임 세기, `gif-max-side`·`gif-max-frames`)를 더한다(T069 통과)
- [ ] T077 [US6] `F/features/post-detail/gifPlayer.ts`의 빈 자리를 채우고(T072 통과), ▶ 표시 CSS `a[href$=".gif"]::after`를 `F/features/post-detail/postDetail.css`에 더한다(class 없이, 정화 허용 목록 변경 없음). 005 `PostDetailPage`가 이미 `playableGifs(container)`를 부르는지 확인한다

**Checkpoint**: GIF가 정지 장면으로 보이고 누르면 재생된다

---

## Phase 9: User Story 7 - 버려진 사진 정리 (Priority: P3)

**Goal**: 연결되지 않은 지 24시간·연결 해제 7일이 지난 사진의 파일과 기록을 매일 새벽 지우고, 현재 프로필 사진은 지우지 않는다 (04 §4-4)

**Independent Test**: 세 가지 사진을 준비하고 정리 배치를 돌린 뒤 앞의 둘만 지워졌는지 확인한다(`ImageCleanupJobIT`)

### Tests for User Story 7 ⚠️

- [ ] T078 [P] [US7] `T/media/integration/ImageCleanupJobIT.java`(StorageIntegrationTestBase, `Clock` 고정): `US7_1_TEMP_24시간_삭제`(완료 전·완료 모두), `US7_2_연결해제_7일_삭제`, `US7_3_현재_프로필_유지`, 23시간·6일은 유지, `US7_4_저장소_삭제_실패면_행_유지`(삭제 실패 키를 돌려주는 가짜 `ImageStorage`로 바꿔) → 다음 실행에서 지워짐, 정리 사이 다시 연결된 사진은 행 유지(조건 재확인), 1,001개면 두 묶음, `post_image` CASCADE, 로그에 키·회원 번호 없음
- [ ] T079 [P] [US7] 잠금 테스트 `T/media/integration/ImageCleanupLockIT.java`: 두 인스턴스(같은 `LockProvider`)에서 동시에 실행해도 한 번만 처리된다(ShedLock `imageCleanup`)
- [ ] T080 [P] [US7] 탈퇴 정리 메서드 테스트 `T/media/integration/ImagePurgeServiceIT.java`: `detachAllByUploader(m)` 뒤 m의 모든 사진(현재 프로필·TEMP·이미 연결 해제된 사진 포함)이 다음 정리에서 지워질 조건(`detached_at ≤ now() - 7일`)이 되고, 다른 회원 사진은 그대로(contracts/storage.md §3-2, FR-043)

### Implementation for User Story 7

- [ ] T081 [US7] `B/media/application/ImageCleanupJob.java`: `@Scheduled(cron = "${blog.image.cleanup.cron}", zone = "${blog.time-zone}")` + `@SchedulerLock(name = "imageCleanup", lockAtMostFor = …)`, research R13 순서(후보 SELECT → `deleteAll` → `deleteIfStillEligible`), 묶음·최대 시간, INFO 로그 1줄(T078·T079 통과)
- [ ] T082 [US7] `B/media/application/ImagePurgeService.java` `detachAllByUploader(long memberId)`(`@Transactional(propagation = MANDATORY)`, contracts/storage.md §3-2 SQL)를 만든다(T080 통과). Javadoc에 "015 `ImageWithdrawalPurgeStep`(order 40)이 부른다"를 적는다

**Checkpoint**: 정리 배치가 공간을 돌려주고 사용 중인 사진은 남는다

---

## Phase 10: User Story 8 - 사진 공개 주소를 바꿔도 옛 글 사진이 유지된다 (Priority: P3)

**Goal**: 공개 주소를 바꿔도 옛 주소로 쓰인 우리 사진이 링크로 바뀌지 않고 지금 주소의 이미지로 보인다 (23 I-5)

**Independent Test**: 옛 주소·새 주소 설정으로 옛 주소 본문을 다시 렌더링해 새 주소 이미지가 되는지, 목록에 없는 주소는 링크가 되는지 확인한다(`PublicBaseUrlChangeIT`)

### Tests for User Story 8 ⚠️

- [ ] T083 [P] [US8] `T/media/integration/PublicBaseUrlChangeIT.java`(`@TestPropertySource`로 `public-base-url`=새 주소, `legacy-base-urls`=[옛 주소]): `US8_1_옛주소_사진은_새주소_img`(원문 Markdown은 그대로, US8 #3), `US8_2_목록에_없는_주소는_링크`, 썸네일·GIF 링크도 새 주소, CSP `img-src`에 새 출처
- [ ] T084 [P] [US8] 운영 SQL 테스트 `T/media/integration/ThumbnailUrlRebaseIT.java`: quickstart §6 4단계 SQL이 옛 주소로 시작하는 `post.thumbnail_url`만 바꾸고 다른 값은 그대로

### Implementation for User Story 8

- [ ] T085 [US8] T083이 실패하는 부분(예: `ImageUrls`의 옛 주소 처리, 렌더러의 출력 주소)을 고친다. 출력 주소는 항상 `ImageUrlResolver.publicUrl(key)`(지금 주소)로 만든다
- [ ] T086 [US8] 운영 SQL을 `scripts/sql/rebase-image-urls.sql`(변수 `:old`·`:new`, 트랜잭션, 바뀐 행 수 출력)로 두고 quickstart §6 절차와 연결한다(T084 통과, research R17)

**Checkpoint**: 공개 주소 변경 절차가 테스트와 스크립트로 준비된다

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: 여러 스토리에 걸친 보안·성능·검증·다른 기능과의 연결

- [ ] T087 [P] 로그·응답 점검: `B/media/` 전체에서 로그에 서명 쿼리·원래 파일 이름·이메일이 없고 키·크기·회원 번호만 있는지, 응답에 파일 이름 칸이 없는지 확인한다(FR-009, SC-005, 헌법 III)
- [ ] T088 [P] 프런트 보안·접근성 점검: `F/features/image-upload/`·`AltTextPanel`·`StorageUsageBar`·`gifPlayer`에 `dangerouslySetInnerHTML`이 없고, 파일 이름을 화면에 쓰지 않으며, 업로드 중 상태가 `aria-live="polite"`로 읽히는지 확인한다(헌법 IV)
- [ ] T089 [P] 성능 측정 `T/media/integration/ImageUploadPerformanceIT.java`: presign p95 200ms, complete p95 1초(로컬 컨테이너 기준, 100회), 결과를 quickstart §2에 적는다(plan Performance Goals)
- [ ] T090 [P] 화면 문구 점검: 모든 오류·안내 문구가 data-model §7·spec과 글자까지 같고 끝 마침표가 없는지 `F/features/image-upload/uploadMessages.ts`와 서버 `MediaReasonCode`를 대조한다
- [ ] T091 **수동 확인 작업(사파리)**: 실제 아이폰·맥 사파리에서 quickstart §3-2를 실행해 WebP 대체(JPEG)·EXIF 방향·카드 썸네일을 확인하고 결과를 research R22에 적는다(Clarifications Q4 "plan 때 실제 기기로 확인")
- [ ] T092 [P] 005 연결 확인: `PostCard`·상세 본문·링크 미리보기가 `thumbnail_url`·GIF 변환을 그대로 쓰는지 005 회귀(`T/discovery/` 상세·카드 테스트)를 돌린다
- [ ] T093 001 프로필 사진 연결 확인 작업: 001 T084·T121 담당에게 presign `{purpose: PROFILE}`·complete 계약(contracts/openapi.yaml)과 10MB 규칙(Q3: 고른 파일이 10MB를 넘어도 줄인 결과로 판정)을 전달하고, 001 tasks의 "003 선행" 표시를 해제하도록 요청한다(ANALYSIS-tier-a R8)
- [ ] T094 015 연결 확인 작업: 015 tasks의 `ImageWithdrawalPurgeStep`(order 40)이 `ImagePurgeService.detachAllByUploader`를 부르는지 확인한다(015 후속)
- [ ] T095 [P] `RedisGuard` OOM 503 확인 작업(research R9): Redis `maxmemory` 1MB + `noeviction` 컨테이너로 presign을 보내 503 `AUTOSAVE_UNAVAILABLE`이 나오는지 기록하고, 화면이 "잠시 후 다시 시도해 주세요"로 보이며 기기에 보관하는지 확인한다. 공용 처리 변경 여부는 팀 결정(ANALYSIS-tier-bc)
- [ ] T096 quickstart.md §2 자동 테스트 명령 전체를 실행해 통과를 확인한다(002 회귀 포함)
- [ ] T097 quickstart.md §3 화면 확인 1~8과 §5 정리 배치 수동 실행을 `docker compose up` 환경에서 확인한다
- [ ] T098 SC-005·SC-013 측정: quickstart §4대로 카드 9장 썸네일 전송량을 재고 005 quickstart의 SC-005 표와 이 기능 quickstart §4에 기록한다(ANALYSIS-tier-a R7)

### 조건부 작업 (T002 결과가 PROXY일 때만)

- [ ] T099 [P] PROXY 모드 테스트 `T/media/integration/ImageProxyUploadIT.java`: `upload-mode=PROXY`면 presign 응답의 `upload.url`이 `/api/images/{id}/content`, 업로더 아니면 404, 신고 크기 초과 본문은 413 대신 400 `IMAGE_REJECTED`(`SIZE_MISMATCH`)이고 저장소에 남지 않음, 형식 불일치 400, DIRECT 모드에서는 두 경로가 404
- [ ] T100 PROXY 구현 `B/media/web/ImageContentController.java`(`@ConditionalOnProperty(blog.image.upload-mode=PROXY)`, 스트림을 신고 크기까지만 읽어 `ImageStorage.put`), `ImageUploadService.presign`의 주소 갈래(T099 통과). 화면 코드는 바꾸지 않는다
- [ ] T101 (팀 결정 1이 "가입 때 서버 복사 예외"일 때만) `ImageUploadService.importProfile(memberId, InputStream, contentType)`을 추가해 서버가 받은 소셜 사진을 같은 검사·용량 규칙으로 PROFILE 사진으로 만든다. 001 T084와 함께 진행(research R23)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001 Phase 1·2, specs/002 Foundational·발행, specs/004 하네스, specs/005 상세·카드가 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: T002(운영 점검)는 배포 담당 일정에 따르며 로컬 개발을 막지 않는다. 결과가 PROXY면 조건부 작업(T099·T100)을 켠다
- **Foundational (Phase 2)**: Setup의 T003~T005 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
  - US2(T046)는 **006 머지 후**, US2(T041·T047)는 001 T116·T118 상태 확인 필요
  - US4(T062)는 001 설정 화면(US6) 후
  - US6(T073·T074)은 002 렌더러·`RerenderJob` 소유자와 조율
- **Polish (Phase 11)**: US1·US2·US4 완료 후(T093·T094는 001·015 일정에 따름)

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. 다른 스토리에 의존하지 않는다
- **US2 (P1)**: Foundational 이후. 테스트는 US1의 presign·complete를 쓴다(T036은 T028~T030 후)
- **US3 (P2)**: US1의 `uploadImage`·`useImageInsert`(T033·T034) 후
- **US4 (P2)**: US1의 `ImageUploadService.presign`(T028) 후 — 같은 메서드를 고친다
- **US5 (P2)**: Foundational 이후 독립(화면만)
- **US6 (P3)**: US1의 complete(T029) 후(T076은 같은 메서드). 렌더러 T073은 독립
- **US7 (P3)**: Foundational 이후 독립
- **US8 (P3)**: US2의 `ImageUrls` 위임(T017)과 렌더러 후

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 도메인 값 → 저장소 → Service → Controller → 화면 API → 훅 → 컴포넌트
- 같은 파일을 고치는 작업은 순서대로 한다: `ImageUploadService`(T028 → T029 → T059 → T076 → T100), `ImagePresignIT`(T021 → T054), `ImageCompleteIT`(T022 → T069), `uploadImage.test.ts`(T025 → T049), `imageProcessor.ts`(T032 → T063 → T075), `EditorPage.tsx`(T034 → T061)

### Parallel Opportunities

- Phase 1: T003·T004·T005 병렬
- Phase 2: 테스트 T006~T010 병렬, 구현 T011·T012·T013·T014·T018·T019·T020 병렬
- US1: 테스트 T021~T027 병렬, T031·T032 병렬
- US2: 테스트 T036~T038 병렬, T042·T044 병렬
- US4: 테스트 T054~T057 병렬, T058·T061 병렬
- US5·US7·US8은 Foundational 이후 서로 다른 파일이라 팀원별 병렬
- US6: 테스트 T069~T072 병렬, T075·T077 병렬

---

## Parallel Example: User Story 1

```bash
# User Story 1 테스트를 함께 작성:
Task: "ImagePresignIT in backend/src/test/java/com/team/blog/media/integration/ImagePresignIT.java"
Task: "ImageCompleteIT in backend/src/test/java/com/team/blog/media/integration/ImageCompleteIT.java"
Task: "image.csv + ImagePermissionMatrixIT"
Task: "imageProcessor.test.ts / uploadImage.test.ts / useImageInsert.test.tsx in frontend/src/features/image-upload/"
Task: "image-upload.spec.ts in frontend/e2e/"

# 서버와 화면을 함께 구현:
Task: "ImageUploadService.presign/complete + ImageUploadController (backend)"
Task: "imageProcessor.ts (frontend)"
```

## Parallel Example: Foundational

```bash
Task: "ImageHeaderReaderTest", "StorageKeysTest", "ImageUrlsTest", "S3ImageStorageIT", "ImageRepositoryIT"
Task: "ImageProperties + application.yml", "MediaReasonCode", "ImageFormat/StorageKeys/ImageHeaderReader", "MinioContainerSupport", "MemberLockService", "api/images.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2)

1. Phase 1 확인(T001), 점검 스크립트·compose·의존성(T003~T005). 운영 점검(T002)은 병행 요청
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 붙여넣기 → 업로드 → 본문 주소(Playwright T027)
4. Phase 4 US2 → 소유 규칙·프로필 사진·대표 이미지 최종화. 여기까지가 **권장 MVP**(C-IMG-1의 업로드·검사·소유)
5. Deploy/demo if ready (운영은 T002 통과 후)

### Incremental Delivery

1. Setup + Foundational → 저장소·검사 부품
2. US1 → 업로드 → 데모
3. US2 → 소유·연결 최종화(002 임시 구현 제거)
4. US3 → 오프라인 보관
5. US4 → 한도·사용량 화면
6. US5 → 대체글 권유
7. US6 → GIF
8. US7 → 정리 배치
9. US8 → 공개 주소 변경 대비
10. Polish → 사파리·전송량·연결 확인

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A: US1 서버 → US4 서버 → US6 서버(`ImageUploadService` 소유)
   - Developer B: US1 화면 → US3 → US5 → US6 화면
   - Developer C: US2 → US7 → US8
3. `ImageUploadService`는 A가 소유하고, 다른 사람의 변경은 A의 작업 뒤에 붙인다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 002·005·006·001 소유 파일을 고치는 작업(T017·T039·T040·T046·T073)은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
