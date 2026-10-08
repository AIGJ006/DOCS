# Storage·Jobs·Extension Contract: 003-image-upload

**기준**: [docs/23-image.md](../../../docs/23-image.md) §2·§5·§6, [docs/04-draft-and-image.md](../../../docs/04-draft-and-image.md) §4·§6-1, [docs/13-delete-withdraw.md](../../../docs/13-delete-withdraw.md) §2-5·§3-3

## 1. 저장소 설정 (로컬·운영 공통 규칙)

| 항목 | 규칙 | 확인 |
|---|---|---|
| 제품 | MinIO(S3 API). 로컬 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`(고정), 클라이언트 `pgsty/mc`(고정), 운영 NHN 제공 MinIO | 점검 11가지 |
| 버킷 | `blog` 하나. 사진은 `images/` 아래만 | — |
| 앱 전용 키 | `s3:PutObject`·`s3:GetObject`·`s3:DeleteObject`만(`arn:aws:s3:::blog/images/*`). 환경 변수 `BLOG_IMAGE_STORAGE_ACCESS_KEY`·`BLOG_IMAGE_STORAGE_SECRET_KEY`로만 | 점검 8 |
| 익명 읽기 | `GetObject`만, `images/*`만 | 점검 7·9 |
| 익명 목록 | 금지 | 점검 6 |
| 업로드 | Presigned PUT(SigV4, path-style, 5분). 서명 헤더: `host`·`content-type`·`content-length`·`cache-control` | 점검 1~5 |
| CORS | 우리 출처의 `PUT`만, 허용 헤더 `Content-Type`·`Cache-Control`. 로컬 출처 `http://localhost:8080`·`http://localhost:5173` | 점검 10·11 |
| 관리 콘솔 | 외부에 열지 않는다(로컬 compose에서 9001 포트 매핑 제거) | 수동 |
| 캐시 | 객체 `Cache-Control: public, max-age=31536000, immutable` | — |

### 1-1. 점검 11가지 (23 §2-3)

| # | 요청 | 기대 |
|---|---|---|
| 1 | 정상 Presigned PUT | 200 |
| 2 | 서명 위조 | 403 |
| 3 | 서명 후 경로 변경 | 403 |
| 4 | 서명과 다른 `Content-Type` | 403 |
| 5 | 만료된 주소(시험은 1초) | 403 |
| 6 | 익명 `GET /{bucket}?list-type=2` | 403 |
| 7 | 익명 `GET /{bucket}/images/…/{uuid}.webp` | 200 |
| 8 | 서명 없는 익명 `PUT` | 403 |
| 9 | `images/*` 밖 익명 읽기 | 403 |
| 10 | CORS 사전 요청, 우리 출처 | 허용 |
| 11 | CORS 사전 요청, 다른 출처 | 거부 |

- 실행: 로컬 `scripts/check-storage.sh`(compose의 minio에 대해), 운영 `S3_ENDPOINT=… APP_KEY=… APP_SECRET=… BUCKET=blog SITE_ORIGIN=https://… python3 scripts/lib/presign_check.py`
- 결과 기록: docs/23 §2-3 표와 이 기능 research R2. 비밀값은 기록하지 않는다.
- 다시 돌리는 때: 로컬 이미지 버전 변경, NHN 저장소 변경 공지, 버킷 정책·CORS·키 권한 변경.
- **10번 실패 → `blog.image.upload-mode = PROXY`(research R2). 1~9·11번 실패 → 운영 저장소를 쓸 수 없음, 배포 담당에게 넘긴다.**

## 2. 정리 배치 `ImageCleanupJob`

| 항목 | 값 |
|---|---|
| 일정 | `blog.image.cleanup.cron`(기본 `0 30 3 * * *`), 시간대 `blog.time-zone` |
| 잠금 | ShedLock `imageCleanup`, `lockAtMostFor = blog.image.cleanup.max-duration`(30분) |
| 대상 | `status = 'TEMP' AND created_at < now() - temp-ttl(24시간)` 또는 `detached_at < now() - detached-ttl(7일)` |
| 제외 | 현재 프로필 사진(`ATTACHED`, `detached_at IS NULL`) — 조건상 걸리지 않음 |
| 묶음 | 1,000개(`ORDER BY id`) |
| 순서 | ① 후보 SELECT(트랜잭션 없음) → ② 저장소 `DeleteObjects`(원본·썸네일 키) → ③ 성공한 사진만 `DELETE FROM image WHERE id IN (…) AND <대상 조건 재확인>` |
| 실패 | 저장소 삭제 실패 키의 행은 남김(다음 날 다시). 묶음 하나의 예외는 다음 묶음을 막지 않음. 30분이 지나면 멈춤 |
| 기록 | 실행마다 INFO 로그 1줄: 후보 수, 지운 수, 실패 수, 걸린 시간(사진 키·회원 번호는 남기지 않음) |
| 이벤트 | 발행하지 않는다 |

## 3. 확장점 구현

### 3-1. 글 완전 삭제 — `ImagePostPurgeStep` (006 `PostPurgeStep`, order 20)

- 006 T060 임시 구현을 넘겨받는다(006 머지 후). 규칙은 그대로다: 지울 글에만 연결된 사진의 `detached_at = now()`. 다른 글(휴지통 포함)과 함께 쓰는 사진은 그대로 둔다. `post_image` 행은 이어지는 `DELETE FROM post`의 CASCADE가 지운다.
- 호출자 트랜잭션 안에서 실행하고 외부 호출을 하지 않는다.

### 3-2. 회원 탈퇴 정리 — `ImagePurgeService.detachAllByUploader(memberId)` (015 `WithdrawalPurgeStep`, order 40)

```sql
UPDATE image SET detached_at = now() - :detachedTtl
 WHERE uploader_id = :memberId
   AND (detached_at IS NULL OR detached_at > now() - :detachedTtl);
```

- 015의 `ImageWithdrawalPurgeStep`(order 40)이 이 메서드를 부른다. 015가 확장점 인터페이스를 만들기 전에는 이 메서드만 있다.
- 현재 프로필 사진도 포함된다(`detached_at`이 채워져 현재 사진이 아니게 됨). TEMP 사진도 같이 표시되어 다음 정리에 지워진다.
- 다음 정리 배치(최대 24시간 뒤)가 파일과 행을 지운다(2026-10-07 "탈퇴 회원 사진").

### 3-3. 프로필 사진 — `ProfileImageService` (001 T116 포트의 최종 구현)

| 메서드 | 동작 |
|---|---|
| `attach(memberId, imageId)` | 같은 트랜잭션에서 ① 현재 프로필 사진 `detached_at = now()` ② `UPDATE image SET status = 'ATTACHED', detached_at = NULL WHERE id = :imageId AND uploader_id = :memberId AND purpose = 'PROFILE' AND width IS NOT NULL` — 0행이면 400 `INVALID_PROFILE_IMAGE` |
| `detach(memberId)` | 현재 프로필 사진 `detached_at = now()` |

회원 행 잠금(`MemberLockService.lockForUpdate`) 안에서 부른다(001 data-model §2-6, `uq_image_profile_current`).

## 4. 렌더러 규칙 변경 (002 소유 렌더러에 더함)

| 입력(작성자 사진) | 출력 |
|---|---|
| 일반 사진 | `<img src="{지금 공개 주소}/{storage_key}" alt="…" loading="lazy" decoding="async">` (변경 없음) |
| GIF, 썸네일 있음 | `<a href="{지금 공개 주소}/{storage_key}" title="움직이는 이미지 재생" target="_blank" rel="noopener noreferrer nofollow ugc"><img src="{지금 공개 주소}/{thumb_storage_key}" alt="…" loading="lazy" decoding="async"></a>` |
| GIF, 썸네일 없음(옛 사진) | 일반 사진과 같음 |
| 남이 올린 우리 사진·외부 사진 | 링크(변경 없음, 002) |

- `RenderVersion.CURRENT` 1 → 2. 002 `RerenderJob`이 `render_version < 2`인 발행 글을 다시 렌더링한다.
- 정화 허용 목록은 바꾸지 않는다(`a`의 `href`·`title`·`target`·`rel`, `img`의 `src`·`alt`·`title`·`loading`·`decoding`은 이미 허용).

## 5. 화면 동작 계약 (React)

| 부품 | 입력 | 결과 |
|---|---|---|
| `useImageInsert` | textarea 붙여넣기·끌어놓기·[사진] 버튼 | 커서 위치에 `![](업로드 대기 표시)` → 성공 시 `![](공개 주소)` 한 번에 교체. 대체글 자리는 비움(FR-032) |
| `uploadImage` | 파일 1개 | `uploaded`(주소) / `pending`(기기 보관, `local:{id}`) / `rejected`(안내 문구) — research R12 |
| `pendingUploads` | `online` 이벤트, 에디터 열기 | `pendingImages`를 차례로 다시 처리·업로드, 성공하면 본문의 `local:{id}`를 주소로 바꾸고 대기열에서 뺌 |
| `AltTextPanel` | 발행 설정 창 열기 | 대체글 빈 우리 사진 수 안내, 입력 시 본문 alt 갱신. 발행은 막지 않음 |
| `gifPlayer.playableGifs` | 글 상세 본문 컨테이너 | `a[href$=".gif"] > img`에 버튼 역할·재생·정지(클릭·Enter·Space) |
| `StorageUsageBar` | 설정 화면 | "사진 저장 공간 {used} / {quota}" 막대 + "지운 사진의 공간은 7일 뒤 돌아와요" |
