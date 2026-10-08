# Implementation Plan: 이미지 업로드

**Branch**: `003-image-upload` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-image-upload/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

이메일 인증을 마친 회원이 에디터에 사진을 붙여넣으면 브라우저가 줄이고(긴 변 1920px, WebP 0.8 — 안 되면 JPEG 0.8) 위치 정보를 지운 뒤 640px 썸네일과 함께 사진 저장소(MinIO)에 직접 올린다(C-IMG-1). 서버는 업로드 권한 발급(presign)과 완료 확인(complete)만 맡고, 사진 데이터는 앱 서버를 거치지 않는다. 1인 1GB·하루 200장·1분 20장 한도, 남의 사진 연결 막기, 버려진 사진 정리, GIF 정지 장면 + 재생, 대체글 권유, 공개 주소 변경 대응을 함께 만든다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **업로드 = presign → 브라우저 직접 PUT → complete.** `POST /api/images/presign`이 `image` 행(`status = TEMP`, 신고 크기)을 만들고 원본·썸네일 Presigned PUT 2개(SigV4, path-style, 5분, `Content-Type`·`Content-Length` 서명)를 준다. `POST /api/images/{imageId}/complete`가 `HeadObject`·앞부분 읽기로 실제 크기·형식(매직 바이트)·해상도를 다시 검사한다. 완료 표시는 `width IS NOT NULL`이다(스키마 변경 없음, R5).
- **운영 저장소 점검이 먼저.** 운영 NHN MinIO에서 점검 11가지를 구현 전에 돌린다(Clarifications Q2). 이 plan은 **직접 업로드를 기본안**으로 설계하고, CORS가 막히면 같은 규칙의 **서버 경유 업로드**(`PUT /api/images/{imageId}/content`)로 바꾸는 대체안을 계약에 함께 적는다(R2). 점검 결과는 tasks Phase 1의 확인 작업이 받는다.
- **해상도 검사는 머리말만 읽는다.** JPEG(SOF)·PNG(IHDR)·GIF(논리 화면 + 프레임 블록 수)·WebP(VP8/VP8L/VP8X) 머리말을 직접 읽는 `ImageHeaderReader`를 둔다. 전체 해독을 하지 않아 거대 해상도 공격에 안전하고, JDK `ImageIO`가 WebP를 읽지 못하는 문제도 피한다(R6).
- **한도 순서.** 판정 순서는 README 2026-10-07 결정과 007 Q2를 따른다: 401 → 403 → 400(형식·크기) → 409 `STORAGE_QUOTA_EXCEEDED`(회원 행 `FOR UPDATE` 후 합계) → 429 `DAILY_UPLOAD_LIMIT` → 429 `TOO_MANY_REQUESTS`(1분 20장). Redis 쓰기는 트랜잭션 밖에서 해야 하므로(`RedisGuard` 규칙), 용량 확인·TEMP 행 생성 트랜잭션을 커밋한 뒤 하루·1분 횟수를 세고, 거부되면 TEMP 행을 지우는 보상 처리를 한다(R8).
- **사진 판별·연결은 002 임시 구현의 규칙을 그대로 옮긴다.** 주소 → 키 판별은 `ImageUrls.keyOf` 한 곳(002 `ImageReferenceResolverAdapter.parseKey` 이전), 연결은 `ImageService.syncPostImages`/`attachPostImages`(작성자 사진만)를 최종 구현으로 확정한다. 002 T122 교체 점검표의 테스트가 그대로 통과해야 한다(R10).
- **GIF.** 본문의 작성자 GIF는 렌더러가 `<a href="{원본.gif}"><img src="{썸네일 주소}"></a>`로 바꾸고(`RenderVersion` 1 → 2, 다시 렌더링 배치가 옛 글을 고친다), 화면은 005 `gifPlayer.ts` 자리를 채워 ▶ 표시·재생·정지를 한다. 썸네일 주소는 `thumb_storage_key`를 읽어 만들고 확장자를 가정하지 않는다(Clarifications Q4).
- **정리.** 매일 03:30(KST) `ImageCleanupJob`(ShedLock)이 TEMP 24시간·연결 해제 7일 사진을 1,000개씩 고르고, 트랜잭션 밖에서 저장소 `DeleteObjects` → 성공한 키만 행 삭제(대상 조건 재확인)한다. 현재 프로필 사진은 제외한다.
- **다른 기능과 맞물림.** 글 완전 삭제 `ImagePostPurgeStep`(order 20)은 006 임시 구현을 넘겨받고, 탈퇴 정리는 `ImagePurgeService.detachAllByUploader`를 015의 `WithdrawalPurgeStep`(order 40)이 부른다. 001 프로필 사진(`purpose = PROFILE`, 정확히 256×256·1MB·썸네일 없음)과 `ProfileImageService` 임시 구현(001 T116)의 최종 구현도 이 기능이 맡는다.

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Data JPA, Security, Session Data Redis, Validation), Flyway, ShedLock JDBC(V2 `shedlock`), Resilience4j(`RedisGuard`), commonmark-java + OWASP Java HTML Sanitizer(002 렌더러)
- 서버(**새로 추가**): AWS SDK for Java v2 `software.amazon.awssdk:s3`(BOM으로 버전 고정, `S3Client` + `S3Presigner`, `endpointOverride` + `forcePathStyle(true)`), HTTP 클라이언트 `url-connection-client`(Netty를 들이지 않음). 이미지 해독 라이브러리는 추가하지 않는다(R6)
- 화면: React 18, react-router 7, localforage(002 `localDraftStore`), 브라우저 Canvas·`createImageBitmap`. 이미지 처리 라이브러리는 추가하지 않는다(R3)

**Storage**:

- PostgreSQL: `image`(V1, 컬럼·제약 변경 없음), `post_image`, `member`(용량 확인 잠금만), `post.thumbnail_url`(공개 주소 변경 때 일괄 갱신)
- 사진 저장소: MinIO 버킷 `blog`, 키 `images/{yyyy}/{MM}/{uuid}.{ext}`·`images/{yyyy}/{MM}/{uuid}_thumb.{ext}`. 로컬 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`, 운영 NHN 제공 MinIO
- Redis: 하루 장수 `img:daily:{memberId}:{yyyyMMdd}`(TTL 2일), 1분 제한 `ratelimit:image:{memberId}`(001 `RateLimiter`)
- 브라우저: IndexedDB(localforage) `draft:{memberId}:{postId}.pendingImages`(002가 만든 칸을 채움)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis, **MinIO 고정 이미지** `GenericContainer`), Spring Security Test, MockMvc. 화면은 Vitest + Testing Library(+ fake-indexeddb), 종단 확인은 Playwright(크로미엄, 웹킷 — WebP 대체 확인). 헌법 VIII에 따라 업로더 확인·용량 동시성·정리 배치는 실제 DB와 실제 저장소 컨테이너로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저(사파리 포함)

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- presign 응답 p95 200ms 이내(서명은 로컬 계산, 저장소 호출 없음), complete 응답 p95 1초 이내(HEAD 2번 + 앞부분 64KB 읽기 2번, GIF는 프레임 수를 세려고 원본 전체를 스트림으로 한 번 읽음 — 최대 10MB)
- 5MB 휴대폰 사진의 업로드 전송량 약 300~500KB(SC-001), 카드 9장 썸네일 약 0.4MB(SC-013, 005 SC-005와 같은 측정)
- 정리 배치는 1회 최대 30분, 묶음 1,000개, 저장소 삭제는 `DeleteObjects` 한 번에 최대 1,000키

**Constraints**:

- 사진 데이터는 앱 서버를 거치지 않는다(대체안을 켰을 때만 예외, R2)
- 트랜잭션 안에서 저장소 호출·Redis 쓰기를 하지 않는다(presign 서명 계산은 외부 호출이 아님)
- 업로더가 아닌 사람의 `complete`·`content`는 없는 사진과 같은 404
- 원래 파일 이름은 요청·응답·로그·DB 어디에도 두지 않는다(요청 스키마에 이름 칸이 없음)
- 앱 전용 키는 환경 변수로만, `PutObject`·`GetObject`·`DeleteObject` 권한만
- 화면은 375px 폭부터 가로 스크롤 없음, 인라인 스크립트 없음(CSP `script-src 'self'`)

**Scale/Scope**:

- 회원 1명 사진 최대 약 1GB(작은 사진 기준 수천 장), 하루 200장
- API 3개(presign·complete·저장 공간) + 대체안 2개(서버 경유 업로드), 배치 1개(정리), 확장점 구현 2개(글 완전 삭제·탈퇴), 화면 부품 5개(붙여넣기 업로드·대기열·대체글 패널·저장 공간 막대·GIF 재생)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 `image`·`post_image`의 컬럼·제약·인덱스(`ix_image_uploader`, `ix_image_cleanup_temp`, `ix_image_cleanup_detached`, `uq_image_profile_current`, `uq_image_storage_key`, `uq_image_thumb_key`)를 그대로 쓴다. 완료 표시는 새 컬럼 대신 `width IS NOT NULL`로 한다(R5). 새 마이그레이션 없음 |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | `image`·`post_image`는 media 모듈만 쓴다. 렌더러(shared)는 `ImageReferenceResolver` 포트로, post 모듈은 `ImageService` 공개 메서드로, 완전 삭제·탈퇴는 `PostPurgeStep`·`WithdrawalPurgeStep` 확장점으로 부른다. 용량 확인의 `member` 행 잠금은 account 모듈의 공개 Service `MemberLockService.lockForUpdate(memberId)`(001 `MemberRepository.findByIdForUpdate` 위임)를 거친다(R8) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 컨트롤러는 로그인만 보고, Service가 `AccountStatusGuard.requireActive(me, CONTENT_WRITE)`와 `uploader_id = :me`를 다시 확인한다. 남의 `imageId`는 없는 사진과 같은 404 본문. 사진 파일 읽기는 04 결정 2("주소를 알면 보임")로 권한 확인 대상이 아니다(FR-025, Clarifications Q1) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 형식은 확장자·`Content-Type`이 아니라 매직 바이트로 판별하고 SVG는 받지 않는다. 대체글은 Markdown 원문의 `![…]`이며 렌더러의 정화를 그대로 거친다. GIF 재생은 번들 스크립트(`script-src 'self'`)이고 정화 허용 목록을 넓히지 않는다(▶는 CSS `::after`) |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | Redis 장애 중 하루·1분 제한은 통과한다(02 §2-1). 업로드 실패는 기기에 보관하고 자동 저장은 계속된다(FR-019·FR-020). 정리 배치의 저장소 삭제 실패는 행을 남기고 다음 날 다시 시도한다. GIF 재생 스크립트가 실패해도 링크가 새 탭에서 원본을 연다 |
| VI. 데이터는 잃지 않고, 지울 때는 정책대로 지운다 | **PASS** | 지금 쓰는 프로필 사진·연결된 사진은 지우지 않는다. 저장소 삭제 성공 뒤에만 행을 지운다. 대상 조건을 삭제 직전에 다시 확인해 그사이 다시 연결된 사진을 지우지 않는다(R13). 스키마 변경 없음 |
| VII. 수치는 설정값으로 | **PASS** | 1GB·200장·1분 20장·5분·10MB·50MB·1920px·300프레임·640px·1MB·24시간·7일·배치 시각·묶음 크기를 `blog.image.*`로 둔다(data-model §5). 설정 키 접두어 규칙(ANALYSIS R10)이 정해지면 따른다 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | Testcontainers PostgreSQL·Redis·MinIO로 업로더 404, 동시 presign 10건 용량, 하루 201번째, 형식 위장·해상도·GIF 프레임 거부와 파일 삭제, 정리 배치 3가지 조건, 권한 매트릭스 행(`image.presign`·`image.complete`)을 확인한다 |

**Gate 결과 (Phase 0 전)**: 위반 없음. Complexity Tracking은 필요 없다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. 완료 표시를 `width IS NOT NULL`로 두면 PROFILE·POST 모두 같은 규칙이 되고, 정리 배치는 완료 여부와 상관없이 TEMP 24시간을 지운다. 컬럼을 늘리지 않아 원칙 I을 지킨다.
  2. `MemberLockService`는 account 모듈의 공개 Service다. 001에 같은 공개 메서드가 없으므로 이 기능이 account 모듈에 클래스 하나를 추가한다(001 소유 파일은 고치지 않음). 원칙 II 예외가 아니다.
  3. 002의 `RedisGuard`는 Redis 메모리 부족(OOM) 때 503 `AUTOSAVE_UNAVAILABLE`("잠시 후 다시 저장할게요")을 던진다. 사진 업로드 화면에는 맞지 않는 문구라, 화면은 503을 code와 상관없이 "잠시 후 다시 시도해 주세요"로 보이고 업로드를 기기에 보관한다(R9). 공용 처리 변경은 팀 결정 항목으로 남긴다.
  4. 공개 주소 변경 절차(FR-031)는 운영 절차라 API를 만들지 않고 quickstart의 운영 절차 + 다시 렌더링 배치(002 `RerenderJob`)로 다룬다.

## Project Structure

### Documentation (this feature)

```text
specs/003-image-upload/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # presign·complete·저장 공간 REST 계약 (+ 대체안 서버 경유 업로드)
│   └── storage.md       # 저장소 설정·점검 11가지·정리 배치·확장점 계약
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                         # + software.amazon.awssdk:bom·s3·url-connection-client
├── src/main/java/com/team/blog/
│   ├── media/
│   │   ├── web/
│   │   │   ├── ImageUploadController.java          # POST /api/images/presign, POST /api/images/{imageId}/complete
│   │   │   ├── ImageContentController.java         # (대체안, 기본 꺼짐) PUT /api/images/{imageId}/content|thumb-content
│   │   │   └── StorageUsageController.java         # GET /api/me/storage
│   │   ├── application/
│   │   │   ├── ImageUploadService.java             # presign(용량·하루·1분 판정, 보상) / complete(검사·삭제)
│   │   │   ├── StorageQuotaService.java            # 사용량 합계·하루 장수 예약/반납
│   │   │   ├── ImageUrls.java                      # 주소 ↔ 키 판별 한 곳 (002 parseKey 이전)
│   │   │   ├── ImageService.java                   # syncPostImages / attachPostImages (TODO(003) 제거, 최종)
│   │   │   ├── ImagePurgeService.java              # detachAllByUploader(memberId) — 015 탈퇴 order 40이 호출
│   │   │   ├── ImagePostPurgeStep.java             # PostPurgeStep order 20 (006 임시 구현을 넘겨받음)
│   │   │   ├── ImageCleanupJob.java                # 매일 03:30 정리 (@Scheduled + ShedLock)
│   │   │   ├── ProfileImageService.java            # 001 T116 포트의 최종 구현 (attach/detach)
│   │   │   ├── ProfileImageQuery.java              # (001 T040, 변경 없음)
│   │   │   ├── ImageUrlResolver.java               # (001 T040, 변경 없음)
│   │   │   ├── OgImageResolver.java                # 임시 → 최종 (thumb 키 → 원본 키)
│   │   │   └── ImageProperties.java                # @ConfigurationProperties("blog.image") 한도·검사·정리 값
│   │   ├── domain/
│   │   │   ├── ImageFormat.java                    # JPEG/PNG/GIF/WEBP: MIME·확장자·매직 바이트
│   │   │   ├── ImagePurpose.java / ImageStatus.java
│   │   │   ├── StorageKeys.java                    # images/{yyyy}/{MM}/{uuid}(_thumb).{ext} 생성 (서비스 시간대)
│   │   │   ├── ImageHeaderReader.java              # 머리말만 읽어 형식·가로·세로·GIF 프레임 수
│   │   │   ├── ImageInspection.java                # 검사 결과 값 객체 + 거부 사유
│   │   │   └── MediaReasonCode.java                # ReasonCode enum
│   │   └── infra/
│   │       ├── ImageRepository.java                # JdbcClient: insertTemp, lockOwned, markCompleted, cleanup 후보 …
│   │       ├── ImageReferenceResolverAdapter.java  # ImageUrls·ImageRepository 위임으로 정리 (TODO(003) 제거)
│   │       └── storage/
│   │           ├── ImageStorage.java               # prepareUpload / head / readHead / openStream / deleteAll
│   │           ├── S3ImageStorage.java             # AWS SDK v2 구현
│   │           └── StorageClientConfig.java        # S3Client·S3Presigner Bean (blog.image.storage.*)
│   ├── account/application/MemberLockService.java  # (신규, account 공개 Service) lockForUpdate(memberId)
│   └── shared/infra/markdown/AstTransformer.java   # 작성자 GIF → 링크 + 썸네일 img, RenderVersion.CURRENT = 2
├── src/main/resources/application.yml              # blog.image.* 기본값
└── src/test/java/com/team/blog/
    ├── support/MinioContainerSupport.java          # pgsty/silo 고정 이미지 + 버킷·정책 준비
    ├── media/unit/                                 # ImageHeaderReaderTest, StorageKeysTest, ImageUrlsTest
    └── media/integration/
        ├── ImagePresignIT.java                     # US1·US4: 판정 순서·용량 동시성·하루 한도·1분 제한
        ├── ImageCompleteIT.java                    # US1·US2·US6: 검사·업로더 404·파일 삭제·GIF
        ├── ImageLinkIT.java                        # US2: 작성자 사진만 연결 (002 점검표 회귀)
        ├── ImageCleanupJobIT.java                  # US7: 24시간·7일·프로필 제외·삭제 실패 재시도
        ├── StorageUsageApiIT.java                  # US4 #4·#5
        ├── PublicBaseUrlChangeIT.java              # US8: 옛 주소·새 주소 렌더링
        └── ImagePermissionMatrixIT.java            # 004 하네스 image.presign·image.complete 행

frontend/src/
├── api/images.ts                                   # presign·직접 PUT·complete·storage
├── features/image-upload/
│   ├── imageProcessor.ts                           # 1920px·WebP→JPEG 대체·640px 썸네일·EXIF 제거(캔버스 재그리기)
│   ├── gifInspector.ts                             # GIF 머리말에서 가로·세로·프레임 수 (고르는 순간 거부)
│   ├── uploadImage.ts                              # 한 장 업로드 흐름 + 실패 분류(보관/즉시 안내)
│   ├── pendingUploads.ts                           # local: 대기열 재시도 (online·에디터 열기)
│   ├── useImageInsert.ts                           # textarea 붙여넣기·끌어놓기·파일 선택 → 본문 삽입
│   ├── altText.ts                                  # 본문에서 대체글 빈 사진 찾기·바꾸기
│   └── uploadMessages.ts                           # 오류 code → 문구
├── components/editor/AltTextPanel.tsx              # 발행 설정 창 안 "대체글이 없는 사진이 N장" 패널
├── components/StorageUsageBar.tsx                  # 설정 화면 "사진 저장 공간 312MB / 1GB"
├── features/post-detail/gifPlayer.ts               # 005 빈 자리를 채움: ▶·재생·정지·Enter
└── features/post-detail/postDetail.css             # a[href$=".gif"]::after ▶

docker-compose.yml                                  # minio 콘솔 포트(9001) 닫기, minio-init(mc) 버킷·정책·앱 키, CORS에 Vite 출처
scripts/check-storage.sh, scripts/lib/presign_check.py  # 저장소 점검 11가지 (로컬·운영 공용)
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 이 기능의 코드는 대부분 `media` 모듈에 있다. 다른 모듈에는 다음만 추가하거나 고친다: shared 렌더러의 GIF 변환(`AstTransformer`, 002 소유 파일이라 002 회귀 테스트를 함께 돌림), account의 회원 행 잠금 Service. post의 발행·저장 경로는 기존 `ImageService` 호출을 그대로 쓴다. 001의 프로필 사진 화면(T084·T121)과 설정 화면은 이 기능의 API와 `StorageUsageBar`를 호출만 한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (위반 없음).
