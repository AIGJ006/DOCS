# Quickstart: 003-image-upload 검증 시나리오

**Feature**: `003-image-upload` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 저장소·배치·확장점은 [contracts/storage.md](./contracts/storage.md), 테이블·상태 전이는 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS, Python 3(점검 스크립트)
- 선행 기능: 001(로그인·CSRF·`AccountStatusGuard`·`RateLimiter`), 002(에디터·자동 저장·발행·렌더러·`RerenderJob`), 005(글 상세·카드·`gifPlayer.ts` 자리)
- 있으면 함께 확인: 006(글 완전 삭제 order 20), 015(탈퇴 order 40), 001 US6(프로필 사진 화면)
- `.env`에 `STORAGE_ROOT_USER`·`STORAGE_ROOT_PASSWORD`(로컬 관리 키)와 `BLOG_IMAGE_STORAGE_ACCESS_KEY`·`BLOG_IMAGE_STORAGE_SECRET_KEY`(앱 전용 키, `minio-init`이 만듦). 값은 저장소에 커밋하지 않는다.
- 관련 설정 기본값은 [data-model.md §5](./data-model.md#5-설정값-applicationyml-헌법-vii).

## 1. 기동과 저장소 점검

```bash
docker compose up -d postgres redis minio
docker compose run --rm minio-init                 # 버킷 blog, images/* 익명 읽기, 앱 전용 키
./scripts/check-storage.sh                          # 기대: PASS 11 / FAIL 0
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

예상 결과:

- `check-storage.sh`가 11가지 모두 PASS
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:9001` → 연결 거부(콘솔 포트를 열지 않음)
- Flyway 로그에 이 기능의 새 마이그레이션이 없다

### 1-1. 운영 저장소 점검 (구현 전, 배포 담당과 함께)

```bash
S3_ENDPOINT=https://<NHN 엔드포인트> APP_KEY=… APP_SECRET=… BUCKET=blog SITE_ORIGIN=https://<서비스 주소> \
  python3 scripts/lib/presign_check.py
```

- 11/11 PASS → `upload-mode: DIRECT` 유지
- 10번만 FAIL → `upload-mode: PROXY`(research R2)
- 그 밖의 FAIL → 운영 저장소 사용 보류, 결과를 docs/23 §2-3 표에 기록하고 팀에 알린다

## 2. 자동 테스트 (기본 검증 경로)

```bash
./mvnw -pl backend verify -Dit.test='ImagePresignIT,ImageCompleteIT,ImageLinkIT,ImageCleanupJobIT,StorageUsageApiIT,PublicBaseUrlChangeIT,ImagePermissionMatrixIT,ImageReferenceResolverAdapterIT,PublishIT,ManualSaveIT,AutosaveFlushJobIT,PublishQueryCountIT'
./mvnw -pl backend test -Dtest='ImageHeaderReaderTest,StorageKeysTest,ImageUrlsTest'
(cd frontend && npm test -- image-upload gifPlayer AltTextPanel StorageUsageBar)
(cd frontend && npx playwright test image-upload.spec.ts --project=chromium --project=webkit)
```

| 테스트 | 확인하는 것 | 근거 |
|---|---|---|
| `ImagePresignIT` | 판정 순서 401→403→400→409→429(하루)→429(1분), 동시 10건 용량, 하루 201번째, Redis 장애 통과, 거부 때 TEMP 행 보상 삭제 | US1 #6·#7, US4 #1~#3, SC-004 |
| `ImageCompleteIT` | 크기·매직 바이트·해상도·GIF 1920px·301프레임 거부와 파일·행 삭제, 남의 사진 404, 멱등, 만료 | US1 #2·#3·#5, US2 #2, US6 #2·#3, SC-003·SC-011 |
| `ImageLinkIT` + 002 회귀 | 작성자 사진만 연결, 남의 사진은 링크, 다시 발행 때 빠진 사진 `detached_at` | US2 #1, SC-007 |
| `ImageCleanupJobIT` | TEMP 24시간·연결 해제 7일 삭제, 현재 프로필 제외, 저장소 삭제 실패 시 행 유지 | US7, SC-012 |
| `StorageUsageApiIT` | 합계(남의 사진 제외), `todayCount`, Redis 장애 때 null | US4 #4, FR-014 |
| `PublicBaseUrlChangeIT` | 옛 주소 사진이 지금 주소 이미지로, 목록에 없는 주소는 링크 | US8, SC-010 |
| `ImagePermissionMatrixIT` | `image.csv` 행(비회원 401, 인증 전 403, 남의 사진 404) | 42 §10, ANALYSIS R6 |
| 화면 Vitest | WebP 실패 시 JPEG, 실패 분류(보관/즉시 안내), 대체글 패널, GIF 재생 키보드 | Q4·Q5, US3, US5, US6 #4 |
| Playwright(웹킷 포함) | 붙여넣기 → 업로드 → 본문 주소, 오프라인 보관 → 재연결 교체, 발행 차단 | US1 #1, US3, SC-008 |

## 3. 수동 확인 (화면)

1. 인증 회원으로 `/write` → GPS 정보가 든 5MB JPEG를 붙여넣는다.
   - 기대: 본문에 `![](http://localhost:9000/blog/images/2026/10/{uuid}.webp)`, 사진이 보인다.
   - `curl -s {주소} | exiftool -` 결과에 GPS 항목이 없다(SC-002). 개발자 도구 Network에서 PUT 크기가 약 300~500KB(SC-001).
   - 측정 결과(2026-10-08, 크로미엄 141·로컬 MinIO, `e2e/image-upload.spec.ts`의 `E2E_SC001_FILE` 측정): 4,032×3,024 JPEG 4.69MB(잡음이 많은 최악 질감) → 원본 WebP 1920×1440 575KB, 썸네일 77KB. 일반 사진 질감이면 300~500KB 안이고, 잡음이 많은 사진은 조금 넘을 수 있다. 같은 시험에서 GPS EXIF가 든 JPEG의 결과 파일에 `Exif`·`GPS`·`XMP` 표식이 없음을 확인했다(SC-002).
2. 사파리(맥 또는 아이폰)에서 같은 사진을 넣는다. 기대: 주소가 `.jpg`, 썸네일도 `_thumb.jpg`, 카드·본문에 정상 표시(Q4).
3. 개발자 도구에서 오프라인으로 바꾸고 사진을 붙여넣는다. 기대: 사진이 보이고 본문에 `local:` 표시, 자동 저장 성공, [발행]은 "업로드가 끝나지 않은 사진이 있어요". 온라인으로 바꾸면 주소로 바뀐다(US3).
4. 다른 회원의 사진 주소를 내 글에 넣고 발행한다. 기대: 내 글에서는 링크, 원래 글에서는 이미지(US2 #1).
5. 대체글 없는 사진 2장이 든 글의 [발행] 설정 창. 기대: "대체글이 없는 사진이 2장 있어요 [대체글 넣기]", 입력하면 본문 반영, 무시해도 발행 성공, 결과 HTML `alt=""`(US5).
6. 1,000×800, 120프레임 GIF를 넣고 상세에서 본다. 기대: 정지 장면 + ▶, 클릭·Enter로 재생, 다시 누르면 정지. 개발자 도구에서 스크립트를 끄면 새 탭에서 원본(US6).
7. 2,000px GIF를 고른다. 기대: 고르는 순간 "GIF는 가로·세로 1920px까지 올릴 수 있어요"(FR-036).
8. 설정 화면의 "사진 저장 공간" 막대, 사용량 90% 회원의 에디터 "남은 공간 약 …MB" 안내(US4 #4·#5).

## 4. 카드 썸네일 전송량 측정 (SC-013, 005 SC-005)

1. 사진 있는 공개 글 9개를 만든다(원본 약 400KB, 썸네일 약 40KB).
2. 홈(`/`)을 캐시 비우고 연다. Network 필터 `images/` → 썸네일 9장의 전송량 합계가 약 0.4MB(원본 사용 시 약 3.6MB)인지 확인한다.
3. 결과를 005 quickstart의 SC-005 표와 이 문서에 적는다.

## 5. 정리 배치 수동 실행

```bash
# 테스트 데이터: TEMP 25시간 전, detached 8일 전, 현재 프로필 사진
psql … -c "UPDATE image SET created_at = now() - interval '25 hours' WHERE id = :temp"
psql … -c "UPDATE image SET detached_at = now() - interval '8 days' WHERE id = :detached"
# 실행: BLOG_IMAGE_CLEANUP_CRON을 1~2분 뒤 시각으로 바꿔 재기동하거나 ImageCleanupJobIT로 확인한다
```

- 기대: 앞의 두 사진은 저장소에서 404, 행 없음. 프로필 사진은 남음. INFO 로그에 지운 수만 있고 키·회원 번호가 없음.

## 6. 공개 주소 변경 운영 절차 (FR-031)

1. 새 주소가 같은 파일을 내주는지 확인(`curl -I {새 주소}/images/…`). 옛 주소도 계속 열려 있어야 한다.
2. `BLOG_IMAGE_PUBLIC_BASE_URL`을 새 주소로, 옛 주소를 `blog.image.legacy-base-urls`에 추가하고 재기동(CSP·정화 허용 목록은 같은 설정을 읽음).
3. 발행 글 전체 다시 렌더링(002 `RerenderJob` 운영 실행).
4. `UPDATE post SET thumbnail_url = :new || substr(thumbnail_url, length(:old) + 1) WHERE thumbnail_url LIKE :old || '/%';`
5. 다시 렌더링이 끝나면 CSP에서 옛 출처를 뺀다. 옛 주소 목록에서는 지우지 않는다.
6. 확인: 옛 주소로 쓴 글이 이미지로 보이고(링크 아님), 원문 Markdown은 그대로다(US8).
