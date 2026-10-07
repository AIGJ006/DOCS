# Quickstart: 글 읽기 검증 시나리오

**Feature**: [spec.md](./spec.md) | **Contracts**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Data model**: [data-model.md](./data-model.md)

이 문서는 구현이 끝난 뒤 기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드·마이그레이션·테스트 본문은 tasks 단계에서 만든다.

## 1. 사전 조건

- 001(회원·블로그 주소), 002(발행: `excerpt`·`thumbnail_url`·`first_public_at`·`content_html` 저장), 004(`PostAccessPolicy`·`VisibilityFilter`)가 구현되어 있다. 008·009·010·007이 없으면 태그 빈 목록, 좋아요·팔로우 false, 댓글 영역 비어 있음으로 확인한다(research R-30).
- Docker, JDK 21, Node(프런트 빌드), `curl`, `jq`.
- 시드 데이터: tasks 단계에서 만드는 테스트 픽스처(제안 경로 `backend/src/test/resources/fixtures/post-reading.sql`)를 로컬 DB에도 적용한다. 픽스처 내용:
  - 회원 A(`kim755030`): 공개·노출 글 12개(그중 수정 중(`post_draft`) 1개, 다시 발행(`edited_at`) 1개, `PRIVATE`→`PUBLIC`으로 공개 범위만 바꾼 글 1개, 코드 블록 글 1개 포함) + 그 밖에 `PRIVATE` 3개, 임시글 1개, 휴지통 1개, 관리자 숨김(`PUBLIC`) 1개
  - 회원 B(`na_ms`, 공개 글 8개, 그중 2개는 `first_public_at`이 같은 마이크로초)
  - 회원 C(탈퇴 신청, 공개 글 2개), 회원 D(공개 글 0개)
  - 합계 공개·노출 글 20개(홈 기준)

## 2. 기동

```bash
docker compose up -d                       # app + PostgreSQL + Redis + MinIO
docker compose exec -T postgres psql -U blog -d blog < backend/src/test/resources/fixtures/post-reading.sql
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/posts   # 200
```

변수:

```bash
BASE=http://localhost:8080
A_COOKIE='SESSION=<회원 A로 로그인한 세션 쿠키>'   # 001의 로그인 절차로 얻는다
```

## 3. 시나리오 (수동 확인)

### Q-1 홈 첫 페이지 (US1 #1, FR-001·002·003)

```bash
curl -s "$BASE/api/posts?size=50" | jq '{n: (.items|length), ids: [.items[].id], next: .nextCursor}'
```
기대: `n = 9`(`size=50` 무시), 공개·노출 글만, `firstPublicAt` 내림차순(같으면 `id` 큰 순), `next`는 문자열. 응답 헤더 `Cache-Control: private, no-cache`.

### Q-2 [더 보기]와 끝 (US1 #2, FR-004, SC-004)

```bash
C1=$(curl -s "$BASE/api/posts" | jq -r .nextCursor)
C2=$(curl -s "$BASE/api/posts?cursor=$C1" | jq -r .nextCursor)
curl -s "$BASE/api/posts?cursor=$C2" | jq '{n: (.items|length), next: .nextCursor}'
```
기대: 9 + 9 + 2 = 20, 세 번째 응답 `next = null`. 빈 `items`를 받는 요청이 없다. 공개 글을 정확히 18개로 맞추면 두 번째 응답에서 `next = null`이고 `n = 9`.

### Q-3 보는 도중 발행·삭제·비공개 (US1 #3, FR-005, SC-003)

1. `C1`을 받은 뒤 회원 A로 새 글 발행(002 API), 이미 받은 1페이지의 글 하나를 휴지통으로(006), 2페이지에 나올 글 하나를 `PRIVATE`로(004).
2. `curl "$BASE/api/posts?cursor=$C1"` 이후 끝까지 이어 받는다.

기대: 새 글은 이어 받는 목록에 없음, 1·2·3페이지 합친 ID에 중복 0건, 비공개 전환한 글만 빠지고 그 밖에 빠진 글 0건. 새로 고침(커서 없음)하면 새 글이 맨 위.

### Q-4 잘못된 커서 (US1 #6, FR-006)

```bash
curl -s "$BASE/api/posts?cursor=abc%25%25" | jq .                                  # 풀리지 않음
curl -s "$BASE/api/posts?cursor=$(printf '{"v":2,"l":"home","k":[1,1]}' | basenc --base64url | tr -d '=')" | jq .code
curl -s "$BASE/api/posts?cursor=$(printf '{"v":1,"l":"home"}' | basenc --base64url | tr -d '=')" | jq .code
BC=$(curl -s "$BASE/api/members/kim755030/posts" | jq -r .nextCursor)
curl -s "$BASE/api/posts?cursor=$BC" | jq .code                                    # 다른 목록의 커서
```
기대: 모두 400, 본문 `{"code":"INVALID_CURSOR","message":…,"errors":[],"details":null}` 형식.

### Q-5 블로그 머리말·목록 (US3 #1~#3, FR-019~023)

```bash
curl -s "$BASE/api/members/kim755030" | jq                                  # 비회원
curl -s -H "Cookie: $A_COOKIE" "$BASE/api/members/kim755030" | jq '.publicPostCount, .isMe'
curl -s -H "Cookie: $A_COOKIE" "$BASE/api/members/kim755030/posts" | jq '[.items[].id]'
```
기대: `publicPostCount = 12`(본인·남 같음), 본인이 봐도 목록에 `PRIVATE`·숨김·임시·휴지통 글 없음, 9개 + `nextCursor`, 다음 페이지 3개 + `null`. `isMe`는 본인일 때만 true.

### Q-6 블로그 주소 404·301 (US3 #4)

```bash
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$BASE/@Kim755030"      # 301 → /@kim755030
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/@nobody_here"                    # 404
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/@<회원 C handle>"                 # 404 (탈퇴 신청)
curl -s "$BASE/api/members/<회원 C handle>" | jq .code                            # NOT_FOUND
```

### Q-7 상세 — 공개 글 (US2 #1·#3·#4, FR-028~035)

```bash
curl -s "$BASE/api/posts/<다시 발행한 글 id>" | jq '{title, displayedAt, editedAt, tags, likeCount, viewCount, commentCount, author, viewer}'
curl -s "$BASE/api/posts/<공개 범위만 바꾼 글 id>" | jq .editedAt                    # null
V1=$(curl -s "$BASE/api/posts/42" | jq .viewCount); curl -s "$BASE/api/posts/42" >/dev/null
V2=$(curl -s "$BASE/api/posts/42" | jq .viewCount); [ "$V1" = "$V2" ] && echo "view unchanged"
```
기대: `displayedAt = firstPublicAt`, 태그는 입력 순서, `viewer.isAuthor=false`, 상세 GET만으로 조회수 불변. `Cache-Control: private, no-cache`. 응답에 `contentMd` 없음.

### Q-8 상세 — 404 동일성 (US2 #2, FR-026 ③, SC-009)

```bash
for p in 99999999 abc <A의 PRIVATE id> <A의 임시글 id> <A의 휴지통 id> <A의 숨김 id> <회원 C의 공개 글 id>; do
  curl -s -D - "$BASE/@kim755030/posts/$p" -o "/tmp/r_$p.html" | head -1
  sha256sum "/tmp/r_$p.html"
done
```
기대: 모두 `404`, 본문 해시가 모두 같다(공통 404 메타 + `noindex`). API(`/api/posts/$p`)도 모두 404 `NOT_FOUND`, 본문 동일. 응답 헤더 `Cache-Control: private, no-store`.

### Q-9 주소 처리 순서 (US5 #1~#3, FR-026)

```bash
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$BASE/@na_ms/posts/<A의 공개 글 id>?comment=120"   # 301 → /@kim755030/posts/{id}?comment=120
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/@na_ms/posts/<A의 PRIVATE id>"                              # 404 (이동 없음)
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' -H "Cookie: $A_COOKIE" "$BASE/@kim755030/posts/<A의 임시글 id>"   # 302 → /write/{id}
```

### Q-10 링크 미리보기 메타 (US5 #4·#5, FR-044·045)

```bash
curl -s "$BASE/@kim755030/posts/<공개 글 id>?utm_source=x" | grep -E '<title>|og:|canonical|description|article:'
curl -s -H "Cookie: $A_COOKIE" "$BASE/@kim755030/posts/<A의 PRIVATE id>" | grep -E 'og:title|robots'
```
기대: 공개 글은 `<title>{제목} - {닉네임}</title>`, description 160자 이하, canonical에 쿼리 없음, `og:image`가 원본(`_thumb` 아님) 또는 기본 이미지, `article:modified_time`은 다시 발행한 글에만. 작성자가 보는 비공개 글은 공통 문구 + `noindex`, 헤더 `private, no-store`. 제목에 `"><script>`를 넣은 글은 속성 값으로 이스케이프되어 나온다. 응답 HTML에 인라인 `<script>`(본문 있는 script 태그)가 없다.

### Q-11 작성자 화면 (US4, FR-038~040) — 브라우저

회원 A로 로그인해 차례로 연다: 공개 글(작성자 버튼, [좋아요]·[신고]·[팔로우] 없음, 조회 기록 요청 없음 — 개발자 도구 Network에 `/views` 없음), `PRIVATE` 글(🔒 + "나만 볼 수 있는 글이에요", 날짜 = 최초 발행 일자), 수정 중 글(마지막 발행본 + "수정 중인 내용이 있어요(… 저장) [이어서 수정] [변경 취소]"), 숨긴 글(숨김 안내), 임시글(에디터로 이동), 휴지통 글(404).

### Q-12 조회 기록·화면 동작 (FR-041, US6) — 브라우저

- 비회원으로 공개 글을 열고 1초 이상 보면 `POST /api/posts/{id}/views` 1번(204). 1초 전에 다른 탭으로 가면 요청 없음. 서버를 멈춘 상태(`/views` 실패)에서도 상세는 정상.
- 홈에서 [더 보기] 3번 → 스크롤 → 카드 열기 → 뒤로 가기: 카드 27개 + 스크롤 위치 복원. `sessionStorage`의 보관 시각을 31분 전으로 바꾸고 돌아오면 처음 9개.
- 네트워크 오프라인 후 [더 보기]: "불러오지 못했어요 [다시 시도]", 온라인 후 [다시 시도] → 같은 커서로 요청(Network 확인).
- 375px 폭(개발자 도구)에서 홈·블로그·상세 가로 스크롤 없음, 같은 줄 카드 높이 같음, 썸네일 없는 카드도 높이 같음.
- 코드 블록이 있는 글에서만 코드 강조 파일 요청이 생긴다.
- Redis 중지(`docker compose stop redis`) 후 공개 글 상세·홈이 비회원 상태로 열린다(02 §2-1). 확인 후 `docker compose start redis`.

## 4. 자동 테스트

```bash
cd backend
./mvnw -Dtest=CursorCodecTest test
./mvnw -Dit.test='HomeListIntegrationTest,BlogPageIntegrationTest,ListIndexExplainIntegrationTest' verify
./mvnw -Dit.test='PostDetailIntegrationTest,PostDetailPermissionMatrixTest,PageShellIntegrationTest' verify
cd ../frontend && npm test -- post-list post-detail
```

| 테스트 | 확인하는 것 | 근거 |
|---|---|---|
| CursorCodecTest | 왕복(마이크로초 보존), 패딩 없음, 손상·`v`·누락·타입·`l` 불일치 → 예외 | FR-006, R-24 |
| HomeListIntegrationTest | 노출 조건, 정렬·동순위(같은 마이크로초), 9개 고정, 마지막 판단, 보는 도중 변경 시 중복·누락 0, **쿼리 수 1**(Hibernate Statistics 또는 datasource-proxy), 본문 컬럼 미조회 | US1, SC-002·003·004 |
| BlogPageIntegrationTest | 본인·남 동일 목록, 공개 글 수, 404(없음·탈퇴), 빈 블로그 | US3, SC-008 |
| ListIndexExplainIntegrationTest | `EXPLAIN`에 `ix_post_feed`·`ix_post_blog` 사용 (글 1만 건 시드) + 응답 300ms 이내 | 06 R-2b, SC-001 |
| PostDetailPermissionMatrixTest | 42 §5-1 표 전체(행위자 × 상태), 404 본문 동일 | US2 #2, US4, SC-009 |
| PostDetailIntegrationTest | 표시 날짜·수정됨, 태그 순서, 좋아요·팔로우 여부, 작업본 안내, 쿼리 최대 5번, 상세 GET 후 `view_count` 불변, 캐시 헤더 | US2, FR-030·042, SC-007·011 |
| PageShellIntegrationTest | 처리 순서 ①~⑥, 301 쿼리 유지, canonical, OG 메타·이스케이프, `noindex`, 인라인 스크립트 없음 | US5, FR-026·043~045 |
| 프런트 테스트 | 카드 3줄 높이·빈 썸네일, 상대 시간, 중복 ID 건너뛰기, 복원 30분, 로딩·실패·빈 상태 문구, 작성자일 때 비콘 미실행 | FR-009~018, FR-041 |

모든 통합 테스트는 Testcontainers PostgreSQL(H2 금지, 원칙 VIII)과 Spring Security Test(`@WithUserDetails` 등)로 행위자를 바꾼다.
