# Quickstart: 공개 범위와 권한 검증

**Feature**: `004-visibility-permission` | **계약**: [contracts/openapi.yaml](./contracts/openapi.yaml), [contracts/events.md](./contracts/events.md) | **데이터**: [data-model.md](./data-model.md)

이 문서는 기능이 끝까지 동작하는지 확인하는 실행 안내서다. 구현 코드와 마이그레이션은 넣지 않는다.

## 1. 사전 조건

- Docker 24+ / Docker Compose v2. Testcontainers와 로컬 실행에 필요하다.
- JDK 21. Maven은 `backend/mvnw`를 쓴다.
- Node 20+. 화면 확인에만 필요하다.
- 선행 기능: 001(로그인·이메일 인증·설정), 002(새 글·발행)의 엔드포인트. 통합 테스트는 이 기능들 없이도 픽스처로 DB 상태를 직접 만든다.
- 스키마: Flyway 기준선 V1(51 "ERD 변경 제안" SQL과 같음). `erd/V1__common_schema.sql` 파일은 저장소에 아직 없으므로 51에서 추출한 내용이 `backend/src/main/resources/db/migration`에 있어야 한다.

## 2. 자동 검증 (주 경로)

```bash
cd backend
./mvnw -q verify -Dit.test='VisibilityMatrixIT,PermissionMatrixIT,VisibilityChangeIT,NotFoundIndistinguishableIT,ListIndexUsageIT,AccountStateIT,AdminPathIT,SessionResilienceIT'
./mvnw -q test -Dtest='PostAccessPolicyTest,VisibilityFilterTest,VisibilityRegistryTest'
```

Testcontainers가 PostgreSQL과 Redis 컨테이너를 띄운다. H2는 쓰지 않는다.

| 테스트 | 확인하는 것 | 기대 결과 | 근거 |
|---|---|---|---|
| `VisibilityMatrixIT` | 공개 범위(PUBLIC/PRIVATE) × 보는 사람(비회원/다른 회원/작성자/관리자) × 노출되는 곳(상세/홈/블로그 목록/블로그 글 수/태그·검색·sitemap 공용 조건/댓글 보기) 전체 조합 | 모든 칸 통과. PRIVATE는 작성자 상세에서만 보임 | FR-047, SC-001·SC-004, 06 §8 |
| `PermissionMatrixIT` | 행위자 × 대상 상태(발행 공개/비공개/수정 중/임시/휴지통/숨김/작성자 탈퇴 유예) × 행동(42 §5-1·§5-2) | 모든 칸이 CSV 기대값과 같음. 남의 글 쓰기 요청 뒤 `title, content_md, status, visibility, edit_version, updated_at` 스냅샷이 같음 | FR-033·034, SC-003, 42 §12 #1·#2·#5 |
| `VisibilityChangeIT` | 공개 범위 변경 규칙 | 아래 3절 시나리오와 같음 | FR-016~022 |
| `NotFoundIndistinguishableIT` | 볼 수 없는 글과 없는 글의 `/@{handle}/posts/{id}` 응답과 API 404 | 상태 코드·본문 바이트·`Cache-Control`·OG 문구가 같음 | FR-013·014, SC-002 |
| `ListIndexUsageIT` | 홈·블로그 대표 쿼리의 실행 계획(`SET LOCAL enable_seqscan = off`) | 계획에 `ix_post_feed`·`ix_post_blog`가 나옴 | 06 R-2b |
| `AccountStateIT` | 인증 전·탈퇴 유예·정지 상태, 존재하지 않는 글에 쓰기 요청 | 각각 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`/`ACCOUNT_SUSPENDED`. 대상이 있든 없든 같은 응답. 정지하면 그 회원의 모든 세션이 사라짐 | FR-028~032, SC-006·SC-008 |
| `AdminPathIT` | `/admin/reports`, `/admin/xyz`, `/api/admin/xyz` | 비회원은 모두 401로 같은 응답, 일반 회원은 404 | FR-044, SC-007 |
| `SessionResilienceIT` | Redis 컨테이너를 멈춘 상태 | 공개 글 상세 200, 로그인 필요 요청 401 | 02 §2-1, Edge Case |

## 3. 수동 검증 (로컬 실행)

```bash
docker compose up -d            # app + PostgreSQL + Redis + MinIO (+ Mailpit)
docker compose ps               # 모두 healthy 확인
```

준비: 회원 A(작성자)와 B(다른 회원)를 만들고 이메일 인증을 끝낸다. 001 화면이나 Mailpit(`http://localhost:8025`)을 쓴다. 로그인은 001 계약의 로그인 엔드포인트로 하고, 세션 쿠키는 `a.jar`·`b.jar`에 저장한다. CSRF 토큰은 쿠키 `XSRF-TOKEN` 값을 헤더로 보낸다(M17 확정 전 기본값).

```bash
csrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
API=http://localhost:8080
# A가 PUBLIC으로 발행한 글 번호와 handle
POST_ID=101; HANDLE=alice
```

### 시나리오 1: 비공개로 바꾸면 즉시 404, "수정됨" 없음 (US1-1, US2-1)

```bash
curl -s -b a.jar -c a.jar -X PUT "$API/api/posts/$POST_ID/visibility" \
  -H "X-XSRF-TOKEN: $(csrf a.jar)" -H 'Content-Type: application/json' \
  -d '{"visibility":"PRIVATE"}'
# 기대: 200 {"visibility":"PRIVATE","firstPublicAt":"<처음 공개 시각>"}

curl -s -o /dev/null -w '%{http_code}\n' -b b.jar "$API/@$HANDLE/posts/$POST_ID"   # 기대: 404
curl -s -o /dev/null -w '%{http_code}\n'          "$API/@$HANDLE/posts/$POST_ID"   # 기대: 404 (비회원)
curl -s -o /dev/null -w '%{http_code}\n' -b a.jar "$API/@$HANDLE/posts/$POST_ID"   # 기대: 200 (작성자, 비공개 배지)
curl -s "$API/api/posts?size=9" | grep -c "\"id\":$POST_ID"                         # 기대: 0
```

DB 확인: `edited_at`, `edit_version`이 변경 전과 같다.

### 시나리오 2: 볼 수 없는 글 = 없는 글 (US2-2)

```bash
diff <(curl -s -D - -b b.jar "$API/@$HANDLE/posts/$POST_ID" | grep -vi '^date:') \
     <(curl -s -D - -b b.jar "$API/@$HANDLE/posts/999999999" | grep -vi '^date:')
# 기대: 차이 없음. 본문에 og:title "볼 수 없는 글이에요", robots noindex, Cache-Control: private, no-store
```

### 시나리오 3: 다시 공개해도 목록 위치 유지 (US1-2)

```bash
curl -s -b a.jar -X PUT "$API/api/posts/$POST_ID/visibility" -H "X-XSRF-TOKEN: $(csrf a.jar)" \
  -H 'Content-Type: application/json' -d '{"visibility":"PUBLIC"}'
# 기대: 200, firstPublicAt이 시나리오 1의 값과 같음. 홈 목록에서 원래 자리.
```

### 시나리오 4: 같은 값 다시 보내기 / 잘못된 값 / 남의 글 (Edge, US1-5, US3-1·2)

```bash
# 같은 값 → 200, updated_at 그대로
curl -s -b a.jar -X PUT "$API/api/posts/$POST_ID/visibility" -H "X-XSRF-TOKEN: $(csrf a.jar)" \
  -H 'Content-Type: application/json' -d '{"visibility":"PUBLIC"}'
# FRIENDS 미적용 → 400 INVALID_VISIBILITY
curl -s -b a.jar -X PUT "$API/api/posts/$POST_ID/visibility" -H "X-XSRF-TOKEN: $(csrf a.jar)" \
  -H 'Content-Type: application/json' -d '{"visibility":"FRIENDS"}'
# B가 A의 글 (작성자 번호를 끼워 넣어도) → 404 NOT_FOUND, 글 변화 없음
curl -s -b b.jar -X PUT "$API/api/posts/$POST_ID/visibility" -H "X-XSRF-TOKEN: $(csrf b.jar)" \
  -H 'Content-Type: application/json' -d '{"visibility":"PRIVATE","authorId":1}'
# 비회원 → 401 LOGIN_REQUIRED
curl -s -X PUT "$API/api/posts/$POST_ID/visibility" -H 'Content-Type: application/json' -d '{"visibility":"PRIVATE"}'
```

### 시나리오 5: 처음 공개 → 홈 맨 위 (US1-6)

A가 비공개로 발행한 글(`firstPublicAt: null`)을 `PUBLIC`으로 바꾼다. 기대 결과: `firstPublicAt`이 지금 시각이고, `GET /api/posts?size=9`의 첫 항목이다. 앱 로그(또는 테스트 리스너)에 `PostVisibilityChanged`와 `PostWentPublic`이 한 번씩 나타난다.

### 시나리오 6: 계정 상태 (US4)

- 인증 전 회원 C로 아무 글 번호(존재하든 아니든)에 공개 범위 변경을 보낸다. 기대 결과: 둘 다 403 `EMAIL_NOT_VERIFIED`, 본문이 같다.
- C로 `PATCH /api/me/settings {"defaultVisibility":"PRIVATE"}`를 보낸다. 기대 결과: 200(인증 전 허용).
- 관리자가 B를 정지한 직후 `b.jar`로 아무 요청을 보낸다. 기대 결과: 비로그인 처리(세션 삭제)되어 쓰기는 401이다. 정지 기간 중에는 로그인할 수 없다.

### 시나리오 7: 관리자 경로 (US7)

```bash
for p in /admin/reports /admin/xyz /api/admin/xyz; do
  curl -s -o /dev/null -w "$p anon=%{http_code} " "$API$p"
  curl -s -o /dev/null -w "user=%{http_code}\n" -b b.jar "$API$p"
done
# 기대: anon=401 (세 경로 응답 동일), user=404
```

### 시나리오 8: 기본 공개 범위 (US5)

A가 `PATCH /api/me/settings {"defaultVisibility":"PRIVATE"}`를 보낸 뒤 [새 글](002)을 만든다. 기대 결과: 새 임시글의 `visibility`가 `PRIVATE`다. 다시 `PUBLIC`으로 되돌린다.

## 4. 화면 확인 (US6)

```bash
cd frontend && npm ci && npm run dev
```

같은 공개 글을 작성자, 다른 회원, 인증 전 회원, 비회원, 관리자로 연다.

| 보는 사람 | 기대 결과 |
|---|---|
| 작성자 | [수정]·[공개 범위]·[삭제]가 보이고, [좋아요]·[신고]·[팔로우]는 없으며 좋아요 수만 보인다 |
| 비회원 | [좋아요]를 누르면 로그인 안내가 나오고, 로그인 뒤 원래 글로 돌아오며 좋아요는 눌리지 않은 상태다 |
| 인증 전 회원 | 댓글 입력창 자리에 "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"가 보인다 |
| 비회원 | 댓글 입력창 자리에 "로그인하고 댓글 쓰기"가 보인다 |

375px 폭에서도 가로 스크롤이 없어야 한다.

## 5. 정리

```bash
docker compose down -v
```
