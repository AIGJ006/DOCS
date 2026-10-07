# Quickstart: 글 작성·임시저장·발행 검증

**Feature**: `002-post-authoring` | **계약**: [contracts/openapi.yaml](./contracts/openapi.yaml), [contracts/events.md](./contracts/events.md) | **데이터**: [data-model.md](./data-model.md)

이 문서는 기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드·마이그레이션 전문은 넣지 않는다.

## 1. 사전 조건

- Docker 24+ / Docker Compose v2, JDK 21, Node.js LTS(화면 E2E 때), `curl`, `jq`.
- 001(로그인·세션·CSRF), 003(사진 판별 포트 `ImageReferenceResolver`·`syncPostImages`), 008(`TagService`)의 최소 구현이 있어야 한다. 없으면 통합 테스트는 테스트용 대역(stub) 빈으로 대신한다.
- `.env`(저장소에 커밋하지 않음): `DB_PASSWORD`, `STORAGE_ROOT_USER`, `STORAGE_ROOT_PASSWORD`, `BLOG_IMAGE_PUBLIC_BASE_URL=http://localhost:9000/blog`.
- Flyway 기준선은 [docs/51](../../docs/51-erd-unified.md)의 SQL 블록(`erd/V1__common_schema.sql`은 저장소에 없음) + `shedlock` 테이블(추가 제안).

## 2. 기동

```bash
docker compose up -d postgres redis storage      # redis: appendonly yes, appendfsync everysec, maxmemory-policy noeviction
docker compose up -d app                         # 또는 ./mvnw -f backend/pom.xml spring-boot:run
docker compose exec redis redis-cli CONFIG GET maxmemory-policy   # → noeviction
curl -sI http://localhost:8080/ | grep -iE 'content-security-policy|x-content-type-options|referrer-policy'
```

**기대**: CSP에 `script-src 'self'`, `img-src 'self' http://localhost:9000 data: blob:`, `connect-src 'self' http://localhost:9000`, `frame-ancestors 'none'`, `base-uri 'none'`가 있고 `nosniff`, `strict-origin-when-cross-origin`이 보인다(FR-049).

## 3. 자동 검증 (주 경로)

```bash
./mvnw -f backend/pom.xml test                                   # 단위 + Testcontainers(PostgreSQL, Redis) 통합
./mvnw -f backend/pom.xml test -Dtest='*Markdown*,*Sanitize*'    # 렌더러 코퍼스 52개만
cd frontend && npm test && npx playwright test editor            # 화면 (제안 도구, research B-13)
```

| 테스트 묶음 | 확인하는 것 | 근거 |
|---|---|---|
| 렌더러 코퍼스 | 12 §9-1 공격 문자열 32개 → 허용 목록 밖 태그·`on*`·`style`·위험 스킴 0개 / 정상 문법 13개 기대 HTML 일치 / 인용·목록 25단계 거부, 15단계 허용 / 10만 자 1초 이내 / 제목 U+202E·폭 0 문자 제거·NFD→NFC | SC-003·009, US2 |
| 미리보기 = 발행 | 같은 본문의 `/api/markdown/preview` 결과와 발행 후 `content_html`이 바이트 단위로 같음 | SC-004 |
| 권한 매트릭스 | 행위자(비회원·인증 전·탈퇴 유예·남·관리자) × 행동(새 글·자동 저장·수동 저장·발행·변경 취소) → 401/403/403/404/404, 요청 전후 `title`·`content_md`·`status`·`edit_version`·`updated_at` 동일. 휴지통 글은 작성자도 404(Redis 키가 있어도) | SC-008, 42 §5-2 |
| 자동 저장·충돌 | 같은 `baseVersion`으로 두 번 → 두 번째 409 + `details.server`; 늦게 온 옛 버전이 새 버전을 덮지 않음; 1분 반영 후 임시글은 `post`, 발행 글은 `post_draft`에 반영 | FR-016·020, SC-007 |
| 멱등 발행 | 같은 `Idempotency-Key`로 동시 20건 → 발행 1번, `edit_version` +1, 나머지 200(같은 응답) 또는 409 `IN_PROGRESS`; 같은 키 다른 내용 → 422 | SC-002, US6 |
| 다시 발행 | 전후 주소·`published_at`·`first_public_at`·`view_count`·`like_count`·`comment_count` 동일, `edited_at` 기록, `post_draft` 삭제 | SC-005, US4 |
| Redis 장애 | Redis 컨테이너 정지 → 자동 저장 200(DB 직접), 발행 연타는 409 `VERSION_CONFLICT`로 막힘, 읽기 정상 | FR-018·037 |
| 배치 | `RENDER_VERSION` 올린 뒤 `RerenderJob` → 이전 버전 0건, `edited_at`·`edit_version` 불변; 25시간 지난 빈 임시글은 정리, Redis 키가 있으면 남음 | SC-010, US7 |

## 4. 수동 시나리오 (curl)

로그인 경로는 001 계약을 따른다. 아래는 쿠키 파일 `a.txt`(회원 A, 이메일 인증 완료, 기본 공개 범위 PRIVATE)와 `b.txt`(회원 B)에 세션이 들어 있고, `$XA`·`$XB`에 각자의 CSRF 토큰이 있다고 가정한다.

```bash
API=http://localhost:8080
a() { curl -s -b a.txt -c a.txt -H "X-XSRF-TOKEN: $XA" -H 'Content-Type: application/json' "$@"; }
b() { curl -s -b b.txt -c b.txt -H "X-XSRF-TOKEN: $XB" -H 'Content-Type: application/json' "$@"; }
```

### 4-1. 새 글 → 자동 저장 → 발행 → 비로그인 읽기 (US1, US3)

```bash
ID=$(a -X POST $API/api/posts | jq -r .postId)          # 기대: 201, version 0, visibility "PRIVATE"
a -X PUT $API/api/posts/$ID/autosave -d '{"title":"JPA N+1","contentMd":"## 문제\n본문","baseVersion":0}'
                                                        # 기대: 200 {"version":1,"savedAt":…}
a -X PUT $API/api/posts/$ID/autosave -d '{"title":"x","contentMd":"y","baseVersion":0}' -w '%{http_code}\n'
                                                        # 기대: 409 VERSION_CONFLICT, details.server.version = 1
a -X POST $API/api/posts/$ID/publish -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title":"JPA N+1","contentMd":"## 문제\n본문","tags":["jpa"],"visibility":"PUBLIC","baseVersion":1}'
                                                        # 기대: 200 {"url":"/@a/posts/$ID","version":2,"editedAt":null,…}
curl -s -o /dev/null -w '%{http_code}\n' $API/@a/posts/$ID   # 기대: 200 (비로그인)
```

### 4-2. 검증 실패는 모두 모아서 (US1 #3)

```bash
a -X POST $API/api/posts | jq -r .postId > id2
a -X POST $API/api/posts/$(cat id2)/publish -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title":"  ","contentMd":"![대기](local:7f3e)","tags":[],"visibility":"PUBLIC","baseVersion":0}' | jq '.errors[].code'
# 기대: 400, "TITLE_REQUIRED", "PENDING_IMAGES" 둘 다, 글은 DRAFT 그대로
```

### 4-3. 남의 글 404 (US1 #5, FR-036)

```bash
b -X PUT $API/api/posts/$ID/autosave -d '{"title":"t","contentMd":"c","baseVersion":2}' -w '%{http_code}\n'   # 기대: 404 NOT_FOUND
b -X POST $API/api/posts/$ID/publish -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title":"t","contentMd":"c","tags":[],"visibility":"PUBLIC","baseVersion":2}' -w '%{http_code}\n'      # 기대: 404
b -X DELETE $API/api/posts/$ID/working-copy -w '%{http_code}\n'                                               # 기대: 404
```

### 4-4. 수정 중에도 독자는 발행본 (US4)

```bash
a -X PUT $API/api/posts/$ID/working-copy -d '{"title":"JPA N+1 (고침)","contentMd":"## 문제\n고친 본문","baseVersion":2}'
                                                        # 기대: 200 version 3, post_draft 생김
curl -s $API/@a/posts/$ID | grep -c '고친 본문'         # 기대: 0 (마지막 발행본)
a $API/api/posts/$ID/working-copy | jq '{editing,version}'   # 기대: {"editing":true,"version":3}
a -X DELETE $API/api/posts/$ID/working-copy -w '%{http_code}\n'   # 기대: 204, 발행본 그대로, version 4
```

### 4-5. 같은 키 재전송·다른 내용 (US6)

```bash
K=$(uuidgen); BODY='{"title":"JPA N+1","contentMd":"본문2","tags":[],"visibility":"PUBLIC","baseVersion":4}'
a -X POST $API/api/posts/$ID/publish -H "Idempotency-Key: $K" -d "$BODY"   # 기대: 200 version 5, editedAt 기록
a -X POST $API/api/posts/$ID/publish -H "Idempotency-Key: $K" -d "$BODY"   # 기대: 200 같은 응답(발행 1번)
a -X POST $API/api/posts/$ID/publish -H "Idempotency-Key: $K" -d "${BODY/본문2/본문3}" -w '%{http_code}\n'   # 기대: 422
```

### 4-6. XSS·링크·이미지 (US2)

```bash
a -X POST $API/api/markdown/preview -d '{"contentMd":"<script>alert(1)</script>\n\n[x](JaVaScRiPt:alert(1)) [s](https://spring.io)\n\n![e](https://evil.example/a.png)\n\n# 제목"}' | jq -r .html
# 기대: &lt;script&gt; 글자, javascript 링크 없음, spring.io 링크에 target="_blank" rel="noopener noreferrer nofollow ugc",
#       외부 이미지는 <a …>[이미지] e</a>, <h2 id="h-제목">, <h1> 없음
```

### 4-7. 요청 제한 (FR-011)

```bash
for i in 1 2; do a -X PUT $API/api/posts/$ID/autosave -d '{"title":"t","contentMd":"c","baseVersion":0}' -o /dev/null -w '%{http_code} '; done
# 기대: 첫 번째 409(옛 버전), 두 번째(5초 안) 429 + Retry-After 헤더 — 요청 제한은 대상·버전 확인보다 먼저
head -c 1100000 /dev/zero | tr '\0' 'a' | jq -Rs '{title:"t",contentMd:.,baseVersion:0}' | \
  a -X PUT $API/api/posts/$ID/autosave -d @- -o /dev/null -w '%{http_code}\n'      # 기대: 413 (본문을 읽기 전 필터에서 거부)
```

## 5. 화면 확인 (브라우저, 375px와 데스크톱)

| 시나리오 | 기대 결과 |
|---|---|
| 입력 후 1초 멈춤 → 3초 더 멈춤 | "● 이 기기에 저장됨 (동기화 대기)" → "✓ 저장됨 HH:MM" |
| 개발자 도구 Offline 후 입력 | "⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화", Online 전환 후 자동 저장 |
| 미전송 상태에서 탭 닫기 | 확인창 |
| 같은 글 탭 두 개, A 저장 후 B 입력 | B에 충돌 배너, 입력은 계속됨, [비교하기] → 빨강 `−` / 초록 `+` 비교 창, 세 버튼 동작(US5) |
| [발행] 연타 | 버튼 비활성화 + "발행 중…", 글 주소로 이동 |
| 로그아웃 | IndexedDB에서 `draft:{memberId}:*`·`draft-backup:{memberId}:*` 0개 |
| 32개 공격 문자열 글 열람 | 알림창 0회(Playwright `dialog` 이벤트 0건) |
