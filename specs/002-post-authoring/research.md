# Research: 글 작성·임시저장·발행

**Feature**: `002-post-authoring` | **Date**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

spec.md에 `[NEEDS CLARIFICATION]`은 없다. 아래는 (A) 원문·회의에서 이미 정해진 결정, (B) 원문에 없어 이 plan이 고른 기본값("제안(팀 확인 필요)"), (C) spec이 가정으로 둔 항목이다. 원문 절 번호는 `docs/` 기준.

---

## A. 확정된 결정 (원문·회의·README "정해진 것")

### A-1. 자동 저장 3단계: IndexedDB → Redis → PostgreSQL

- **Decision**: 브라우저 IndexedDB(입력 1초 멈춤) → 서버 Redis(바뀐 내용이 있을 때 3초 멈춤, 계속 입력 중이면 최대 30초, `visibilitychange`·`pagehide` keepalive) → 스케줄러가 1분마다 PostgreSQL 반영. 수동 저장·발행은 즉시 DB. 원본은 항상 PostgreSQL. 수치는 `application.yml` 초기값.
- **Rationale**: 04 §1 D-1~D-3, §2, §2-1(01 C-POST-2, 결정 기록 2026-10-02). 서버 트래픽을 줄이면서 기기에는 1초 단위로 남기고, TTL과 무관하게 임시글을 다시 열 수 있다.
- **Alternatives considered**: localStorage(약 5MB·동기 API로 화면 멈춤, 04 §2-2), 매 입력 DB 저장(트래픽·쓰기 부하), Redis만 사용(TTL 만료 시 유실, D-1 위반).

### A-2. 발행 글 수정은 `post_draft` 작업본

- **Decision**: 임시글의 저장은 `post`에, 발행 글의 저장은 `post_draft`(글당 0~1행)에 쓴다. 다시 발행·변경 취소 때 작업본을 지운다. 다시 열면 작업본이 있으면 작업본, 없으면 발행본.
- **Rationale**: 04 §6 결정 1, 05 §2(2026-10-02 Q9). 수정 중에도 독자에게 마지막 발행본이 보이고 자동 저장도 DB에 남는다.
- **Alternatives considered**: `post_revision` 발행 이력(김민서 개인 확장, 05 §8), `post`에 작업 컬럼 추가(공통 테이블 변경, 원칙 I).

### A-3. 편집 충돌은 서버 편집 버전 + 사용자 선택

- **Decision**: 자동 저장·수동 저장·발행 요청에 `baseVersion`을 받아 현재 버전과 같을 때만 저장하고 +1. 다르면 409 `VERSION_CONFLICT` + `details.server = {title, contentMd, version, savedAt}`. 브라우저는 편집을 막지 않고 전송만 멈춘 뒤 배너 → 비교 창(jsdiff 줄·단어 비교, `−`/`+` 기호 병기) → [편집 중인 내용으로 저장](확인 문구)·[저장된 내용 불러오기](7일 백업)·[새 임시글로 따로 저장]·닫기.
- **Rationale**: 04 §2-3·§2-7, 05 §5, 01 결정 기록 2026-10-02 "여러 탭·기기 편집 충돌". 서버는 어느 쪽도 몰래 덮어쓰지 않는다.
- **Alternatives considered**: 마지막 저장 우선(몰래 덮어씀), 실시간 공동 편집(CRDT, 범위 초과), "정말 수정하시겠습니까 [예][아니오]"(결과를 알 수 없음, 04 §2-7).

### A-4. Redis 버퍼 원자성은 Lua 스크립트

- **Decision**: `autosave:post:{postId}` Hash(`memberId, title, contentMd, version, savedAt`, TTL 24h, 저장마다 연장), `autosave:dirty` Set. 버전 확인 + 저장 + dirty 추가는 Lua 하나. 발행 후 삭제는 "버전 ≤ 발행 때 확인한 버전"일 때만 지우는 Lua. 운영 설정 AOF `appendfsync everysec`, `maxmemory-policy noeviction`.
- **Rationale**: 04 §2-3·§2-6, 05 §7 ⑨, 02 §2.
- **Alternatives considered**: `WATCH/MULTI`(재시도 루프 필요), 앱 레벨 분산 락(지연·복잡).

### A-5. 스케줄러: 1분 DB 반영, 매일 새벽 빈 임시글 정리

- **Decision**: `@Scheduled` + ShedLock. 반영은 임시글 `UPDATE post … WHERE status='DRAFT' AND edit_version < :version`, 발행 글 `post_draft` UPSERT `WHERE post_draft.edit_version < EXCLUDED.edit_version`, Redis 키는 지우지 않는다. 정리는 `DRAFT` + 제목·본문 빈 + 생성·수정 후 모두 24시간 + Redis 키 없음 → 휴지통 없이 완전 삭제, 이벤트 없음.
- **Rationale**: 04 §2-4·§2-5, 02 §2, 20 §3-1. 빈 임시글 조건은 01 결정 기록보다 04 §2-5가 구체적이라 04를 따른다(spec Assumptions).
- **Alternatives considered**: Quartz(클러스터 테이블 다수), 별도 배치 서버(배포 단위 증가, 원칙 II).

### A-6. 발행 처리 순서와 트랜잭션 경계

- **Decision**: 트랜잭션 밖 ① 검증 ② 렌더링·정화·요약·썸네일·사진 키·`render_version` → 트랜잭션 ③ `SELECT … FOR UPDATE`(`author_id = :me AND deleted_at IS NULL`, 없으면 404) ④ 현재 버전 = max(Redis, `post_draft`, `post`) 비교 ⑤ 태그 `INSERT … ON CONFLICT (name) DO NOTHING` + `post_tag` 교체 ⑥ `post_image` 동기화(작성자가 올린 사진만) ⑦ `post` UPDATE(`published_at = COALESCE`, `first_public_at` CASE, `edited_at` CASE, `edit_version + 1`) ⑧ `post_draft` 삭제 → 커밋 후 ⑨ Redis 조건부 삭제 ⑩ 이벤트 ⑪ 멱등 응답 저장.
- **Rationale**: 05 §7·§9(J-1~J-5), 02 §4-1. CPU 작업을 락 밖에서 끝내 트랜잭션을 짧게 하고, 커밋 실패 시 Redis 내용이 사라지지 않게 한다.
- **Alternatives considered**: 렌더링을 트랜잭션 안에서(락 보유 시간 증가), 조회 시 렌더링(상세 300ms 목표·이중 방어 위치 분산, 12 §2).

### A-7. JPA 지침

- **Decision**: 도메인 메서드 `post.publish(PublishCommand, RenderedContent, Instant)`가 최초 발행·`first_public_at`·`edited_at` 규칙을 한곳에서 판단하고 "이번에 `first_public_at`을 처음 채웠는지"를 돌려준다(20 §3-1 `PostWentPublic` 판단). 반응 수는 `@Modifying` 증감 쿼리, `edit_version`에 `@Version` 금지, 잠금은 `@Lock(PESSIMISTIC_WRITE)`.
- **Rationale**: 05 §9 J-1~J-4, 20 §3-1.
- **Alternatives considered**: JPA 낙관적 락(`@Version`) — Redis Lua·스케줄러와 값이 어긋남(J-3).

### A-8. 발행 연타·재전송 방지

- **Decision**: 브라우저는 [발행] 즉시 버튼 비활성화 + "발행 중…", 시도마다 새 UUID `Idempotency-Key`. 서버는 `SET idem:publish:{memberId}:{key} = {hash, IN_PROGRESS} NX EX 600`: 새 키면 처리 후 `{hash, 응답}`으로 교체, 실패하면 키 삭제. 있으면 hash 다름 → 422 `IDEMPOTENCY_KEY_REUSED`, 처리 중 → 409 `IN_PROGRESS`(브라우저 1초 뒤 같은 키 재시도), 완료 → 저장된 응답. Redis 장애 시 키 처리는 건너뛰고 ③ 행 잠금 + ④ 버전 확인이 두 번째 요청을 409로 막는다.
- **Rationale**: 05 §6 P-4, 02 §2-1. 버전 확인만으로는 이미 성공한 발행에 충돌 창이 뜬다.
- **Alternatives considered**: 버튼 비활성화만(재전송 못 막음), DB 멱등 테이블(51에 없음, 원칙 I).

### A-9. Markdown 렌더링·정화 파이프라인

- **Decision**: commonmark-java 0.30.0 + GFM(표·취소선·체크리스트·자동 링크·제목 앵커) → AST 변환(제목 한 단계 낮춤, `h-` 앵커와 `-1`·`-2`, 우리 사진 판별·`src`를 지금 공개 주소로, 외부·남의 사진 → "[이미지] 대체글" 링크, 중첩 20단계 검사) → `escapeHtml(true)`·`sanitizeUrls(true)` 렌더링(외부 링크 `target=_blank` + `rel="noopener noreferrer nofollow ugc"`, 이미지 `loading="lazy" decoding="async"`) → OWASP Java HTML Sanitizer 20260924.2 허용 목록(12 §4 `PolicyFactory` 그대로). `ContentRenderer` 하나를 발행·미리보기·다시 렌더링이 공용. 각주·수식·Mermaid·임베드 제외. 코드 강조는 브라우저 highlight.js.
- **Rationale**: 12 §1(S-1~S-14)·§2~§6, 02 §2. 원문 검증 52개 테스트 통과(12 §9).
- **Alternatives considered**: 브라우저 렌더링 결과 HTML 저장(신뢰 불가, 12 §2), flexmark-java(팀 검증 없음), 직접 쓴 HTML 일부 허용(공격면 증가, S-2).

### A-10. 렌더링 부하 제한

- **Decision**: 목록·인용 중첩 20단계 초과 또는 렌더링 1초 초과 → 400 `CONTENT_TOO_COMPLEX` "글 구조가 너무 복잡해요 (목록·인용은 20단계까지)". 15단계 허용.
- **Rationale**: 12 §7-5(S-14), §9-3.
- **Alternatives considered**: 제한 없음(ReDoS·스택 고갈 위험), 본문 길이로만 제한(중첩 공격 못 막음).

### A-11. 렌더링 규칙 버전과 다시 렌더링

- **Decision**: 결과마다 `post.render_version` 기록, 코드 상수 `RENDER_VERSION`을 올리면 배치가 `render_version < 현재`인 발행 글을 100개씩 다시 렌더링(`content_html`·`excerpt` 갱신, `edited_at`·`edit_version` 유지). 작성자 사진 판별은 글 작성자 기준.
- **Rationale**: 12 §7-7(S-12), 23 §2-4(공개 주소 변경 절차).
- **Alternatives considered**: 조회 때마다 렌더링(성능), 수동 SQL 갱신(정화 우회 위험).

### A-12. 요약·썸네일

- **Decision**: 요약 = 정화 결과에서 코드 블록·이미지·표(·수식)를 통째로 빼고 제목·문단·목록의 글자(인라인 코드·링크는 글자로)만, 줄바꿈·연속 공백 → 공백 하나, 앞 200자(단어 중간이면 그 단어 앞). 썸네일 = 본문 첫 번째 "작성자가 올린 사진"의 640px 썸네일(`thumb_storage_key`), 없으면 원본 주소.
- **Rationale**: 05 §4, 10 §2-1·§6(L-8).
- **Alternatives considered**: 원문 Markdown 앞 200자(문법 기호 노출), 작성자가 직접 고르는 썸네일(공통 범위 아님).

### A-13. 사진은 본문과 분리, 남이 올린 사진은 연결하지 않음

- **Decision**: 사진은 즉시 업로드해 본문에 절대 주소만 둔다. 업로드 미완료는 `local:` 임시 표시로 자동 저장은 계속, 발행은 400 `PENDING_IMAGES`. 발행 때 `ImageUrls.keyOf`로 키를 뽑아 `uploader_id = 글 작성자`인 사진만 `post_image` 연결·`ATTACHED`, 빠진 사진은 `detached_at`. 남이 올린 우리 저장소 사진은 연결하지 않고 링크로 보인다.
- **Rationale**: 04 §1 D-4·§4-2~§4-4, 05 §7 ⑥, 12 S-6, 2026-10-07 회의(남이 올린 사진). 수동 저장·DB 반영 때의 연결은 003 FR-022가 정한다.
- **Alternatives considered**: 남의 사진도 연결(원래 주인이 지울 수 없게 되고 비공개 글 사진이 다시 공개됨, 12 §6).

### A-14. 보안 헤더 (2차 방어)

- **Decision**: 모든 응답에 `Content-Security-Policy: default-src 'self'; script-src 'self'; connect-src 'self' {저장소 공개 주소}; img-src 'self' {저장소 공개 주소} data: blob:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`. `{저장소 공개 주소}`는 `blog.image.public-base-url`의 출처로 정화 허용 목록과 같은 설정값. 소셜 가입 마무리 화면 예외는 001.
- **Rationale**: 12 §8(S-13), 02 §5, 2026-10-07 회의 H3.
- **Alternatives considered**: nonce 기반 CSP(SPA 정적 파일만 쓰므로 불필요), CSP 없음(원칙 IV 위반).

### A-15. 권한 판정과 응답 코드

- **Decision**: 판정 순서 로그인(401 `LOGIN_REQUIRED`) → 계정 상태(403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`) → 대상·소유(404 `NOT_FOUND`, 남의 글·없는 글·휴지통 글 동일) → 업무 규칙(400/409). 관리자도 남의 글 수정 404. 숨긴 글은 작성자가 계속 수정·다시 발행 가능하고 숨김은 유지.
- **Rationale**: 42 §3·§4·§5-2, 43 §4-1 "작성자가 할 수 있는 것"(수정·다시 발행 가능, 숨김 유지), 13 D-3(휴지통 글 404), 02 §5.
- **Alternatives considered**: 남의 글에 403(존재 노출, 42 P-4).

### A-16. 도메인 이벤트

- **Decision**: 최초 발행 `PostPublished{postId, authorId, visibility, publishedAt}`, 다시 발행 `PostEdited{postId, authorId, editedAt}`, `first_public_at`을 처음 채웠으면 `PostWentPublic{postId, authorId, firstPublicAt}`. Service가 트랜잭션 안에서 발행만 하고 처리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`. 빈 임시글 정리는 이벤트 없음.
- **Rationale**: 20 §1(EV-1~EV-7)·§2·§3-1, 05 §7 ⑩. 20의 "같은 트랜잭션에서 함께 발행"과 05의 "커밋 후"는 "발행은 트랜잭션 안, 처리는 커밋 후"로 같은 뜻(spec 체크리스트 Notes).
- **Alternatives considered**: outbox 테이블(유실 없음, 개인 확장 — EV-2).

### A-17. API 공통 규약

- **Decision**: `/api/...` 복수형 명사·경로 변수 이름, 오류 본문 `{code, message, errors:[{field, code, message}], details}`, 검증 오류는 400 + `errors`에 실패 항목 전부(`code = VALIDATION_FAILED`), 세션 쿠키(Spring Session + Redis, `HttpOnly`·`Secure`·`SameSite=Lax`) + CSRF 토큰 헤더. 자동 저장 `PUT /api/posts/{postId}/autosave`, 발행 `POST /api/posts/{postId}/publish` + `Idempotency-Key`, 미리보기 `POST /api/markdown/preview`.
- **Rationale**: 02 §5·§5-1(2026-10-07 O8), 04 §2-3, 05 §4·§5, 12 §7-6.
- **Alternatives considered**: JWT(공통 금지, H7).

### A-18. 기술 스택 공통값

- **Decision**: Java 21, Spring Boot(3.x 이상, 팀 확정), Maven, Spring Data JPA, Spring Security, Spring Session Data Redis, Flyway, PostgreSQL(pg_trgm), Redis, MinIO(AWS SDK v2), commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer, React + localforage, JUnit 5 + Testcontainers + Spring Security Test, Docker Compose. 로컬 저장소 이미지 `pgsty/silo:RELEASE.2026-09-16T00-00-00Z`.
- **Rationale**: 02 §2, 04 §6-1, constitution 기술 제약.
- **Alternatives considered**: H2 테스트 DB(원칙 VIII 위반), 공식 MinIO 이미지(배포 중단, 04 §6-1).

---

## B. 원문에 없어 고른 기본값 — 제안(팀 확인 필요)

### B-1. 새 글·에디터 열기·수동 저장·변경 취소 API 경로 — 제안(팀 확인 필요)

- **Decision**:
  - 새 글: `POST /api/posts` → 201 `{postId, version: 0, visibility, title, contentMd}`. 본문 `{title?, contentMd?}`가 있으면 그 내용으로 만든다([새 임시글로 따로 저장], FR-024).
  - 에디터 열기: `GET /api/posts/{postId}/working-copy` → `{postId, status, editing, title, contentMd, version, savedAt, visibility, tags}`. "작업 내용(working copy)"은 임시글이면 글 자체, 발행 글이면 작업본(없으면 발행본)이다. 내용은 max(Redis, `post_draft`, `post`) 버전의 출처에서 가져온다.
  - 수동 저장: `PUT /api/posts/{postId}/working-copy {title, contentMd, baseVersion}` → 200 `{version, savedAt}`.
  - 변경 취소: `DELETE /api/posts/{postId}/working-copy` → 204. 작업본이 없으면 그대로 204(여러 번 보내도 같음, 02 §5-1). 임시글(`DRAFT`)에는 변경 취소 개념이 없으므로 409 `NOT_PUBLISHED`.
  - 화면 주소는 `/write/{postId}`(React 라우트).
- **Rationale**: 04 §2-5는 동작(새 글 = DB에 `DRAFT` 행 먼저, 수동 저장, 다시 열기, 변경 취소)만 정하고 경로를 정하지 않았다. 02 §5-1(복수형 명사, `PUT`/`DELETE`로 상태 지정)에 맞춰 "작업 내용"을 하나의 하위 리소스로 두면 열기·저장·버리기가 같은 주소의 GET/PUT/DELETE가 된다. `draft`라는 이름은 글 상태 `DRAFT`(임시글)와 헷갈려 피했다.
- **Alternatives considered**: `POST /api/posts/{postId}/save`·`/discard`(동사 경로, 02 §5-1과 어긋남), `/api/me/posts/{postId}/editor`(`/api/me`는 목록용 41과 겹침), 자동 저장 API에 `manual=true` 플래그(DB 즉시 반영이라는 다른 의미를 한 API에 섞음).

### B-2. 429·413 이유 코드 — 제안(팀 확인 필요)

- **Decision**: 요청 과다는 429 `RATE_LIMITED` + `Retry-After`(초), 요청 본문 1MB 초과는 413 `PAYLOAD_TOO_LARGE`. 1MB 검사는 자동 저장 경로에 한해 `Content-Length`(없으면 읽으면서 셈)로 컨트롤러 앞 필터에서 한다.
- **Rationale**: 04 §2-1은 상태 코드만 정했다. `RATE_LIMITED`는 21 §4 댓글 입력 규칙 표에 이미 쓰인 이름이라 재사용한다.
- **Alternatives considered**: `TOO_MANY_REQUESTS`/`AUTOSAVE_RATE_LIMITED`(기능별 이름 증가).

### B-3. 버전 단조 증가와 경쟁 처리 — 제안(팀 확인 필요)

원문(04 §2-3·§2-4·§2-5, 05 §7)의 흐름을 유지하되, 원문 그대로면 생길 수 있는 몰래 덮어쓰기·작업본 되살아남을 막는 세부를 정했다. 스키마 변경은 없다.

- **Decision**:
  1. **Lua의 현재 버전 = max(Redis 버전, DB 현재 버전)**. DB 현재 버전 = max(`post.edit_version`, `post_draft.edit_version`). 앱은 매 요청 소유 확인 조회(`author_id = :me AND deleted_at IS NULL`, PK 1회)에서 이 값을 함께 읽어 Lua에 넘긴다. Redis 장애 동안 DB로 버전이 올라간 뒤 Redis가 돌아와도 옛 키 때문에 잘못된 409가 나지 않는다. **Redis 키가 있어도 이 DB 조회를 건너뛰지 않는다** — 04 §2-3은 키가 있으면 Redis의 `memberId`만으로 소유를 확인하지만, 그러면 휴지통 글의 자동 저장이 받아들여져 006 FR-023(휴지통 글 저장 404)과 어긋난다(006 plan 요청 반영). Lua 안의 `memberId` 비교는 이중 확인으로 남긴다.
  2. **수동 저장 = 자동 저장과 같은 Lua로 버전을 먼저 확보한 뒤 같은 요청 안에서 그 버전을 DB에 즉시 반영**(임시글 `post`, 발행 글 `post_draft`, 둘 다 `edit_version < :v` 조건). Redis가 순서를 정하는 유일한 지점이 되어, 수동 저장과 다른 탭 자동 저장이 같은 버전 번호를 갖는 경쟁이 없다. DB 반영이 실패해도 내용은 Redis dirty에 남아 1분 안에 반영된다(손실 없음). Redis 장애 시에는 `SELECT … FOR UPDATE` 후 DB 버전으로 확인한다.
  3. **DB 반영(1분)에서 발행 글의 작업본 UPSERT는 `post.edit_version < :version`일 때만**. 발행 커밋과 Redis 정리(⑨) 사이에 옛 Redis 내용이 작업본으로 되살아나는 것을 막는다.
  4. **발행 ⑨ Lua**: Redis 버전 ≤ ④에서 확인한 버전이면 삭제(원문). 더 크면(발행 중에 다른 탭 자동 저장이 끼어듦) 지우지 않고 버전을 `새 post.edit_version + 1`로 다시 매기고 dirty에 둔다. 그 내용은 작업본으로 반영되고, 그 탭의 다음 저장은 409로 비교 창을 거친다(몰래 덮어쓰지 않음).
  5. **변경 취소는 `post.edit_version = 현재 버전 + 1`로 올리고 `post_draft` 삭제**, 커밋 후 4와 같은 Lua로 Redis 정리. 버전이 뒤로 가지 않으므로, 버린 작업본을 들고 있던 다른 탭·늦게 도착한 반영이 작업본을 되살리지 못한다(다른 탭은 409).
  6. **1분 DB 반영은 `deleted_at` 조건을 넣지 않는다**. 휴지통으로 옮기기 직전에 받아들인 내용도 보존해 복구 때 돌아오게 한다(13 D-3, 006 plan 요청). 그 사이 글이 완전 삭제되어 행이 없으면(임시글 UPDATE 0행 + 행 없음, 또는 작업본 UPSERT의 FK 위반 SQLSTATE 23503) 그 Redis 키와 dirty 항목을 버린다.
- **Rationale**: SC-007(작성자 확인 없는 덮어쓰기 0건), FR-016(옛 버전이 새 버전을 덮지 않음), 원칙 VI. 원문 설계는 DB 행 잠금(발행)과 Redis Lua(자동 저장)가 서로 다른 직렬화 지점이라 위 틈이 생긴다.
- **Alternatives considered**: 자동 저장도 매번 DB 행 잠금(Redis 버퍼 의미 상실), 발행 전에 Redis에 "발행 중" 펜스 기록(실패 시 복구 경로 복잡), 원문 그대로 두고 테스트로만 확인(드물지만 SC-007 위반 가능).

### B-4. ShedLock 저장소 — 제안(팀 확인 필요)

- **Decision**: ShedLock JDBC 제공자 + `shedlock` 테이블(Flyway `V{n}__shedlock.sql`, data-model.md "추가 제안"). `AutosaveFlushJob`(1분, `lockAtMostFor` 50초), `EmptyDraftCleanupJob`(매일 03:30 KST, 제안), `RerenderJob`(10분마다 확인, 할 일이 없으면 즉시 종료, 제안).
- **Rationale**: 02 §2·04 §2-4가 ShedLock만 정했고 저장소는 정하지 않았다. Redis 제공자는 Redis 장애 때 잠금을 못 잡아, Redis와 무관한 정리 배치까지 멈춘다. 다른 기능(13·25·30·31·32·44)의 배치도 같은 테이블을 쓴다 — 공통으로 한 번만 만든다.
- **Alternatives considered**: `shedlock-provider-redis-spring`(테이블 불필요, 장애 시 배치 정지), 단일 서버 가정(02 §2가 서버 2대 이상을 전제).

### B-5. Redis 장애 판정 — 제안(팀 확인 필요)

- **Decision**: Resilience4j `CircuitBreaker`로 Redis 호출을 감싼다(연결 실패·타임아웃 비율 50%/최근 20회, 열림 30초, 반열림 5회 — 설정값). 열리면 자동 저장은 DB 직접 저장(행 잠금 + DB 버전 확인), 멱등 키·요청 제한은 통과(경고 로그), 빈 임시글 정리는 그날 건너뜀(Redis 보관분 유무를 확인할 수 없음). 메모리 부족(`OOM` 오류)은 장애가 아니라 쓰기 실패로 보고 503을 돌려 브라우저가 재시도하게 한다(FR-018: 밀어내지 않음).
- **Rationale**: 04 §2-6("Circuit Breaker로 DB 직접 저장"), 02 §2-1. 라이브러리는 원문이 정하지 않았다.
- **Alternatives considered**: Spring Retry만(차단 상태 없음), 매 요청 try/catch(장애 동안 매번 타임아웃 대기).

### B-6. 요약 추출 방식 — 제안(팀 확인 필요)

- **Decision**: 정화된 HTML을 다시 파싱하지 않고, 렌더링에 쓴 변환 후 AST에서 같은 규칙(코드 블록·이미지·표 제외, 제목·문단·목록 글자, 인라인 코드·링크는 글자로, 직접 쓴 HTML은 글자 그대로)으로 뽑는다. 정화 결과와의 일치는 코퍼스 테스트로 확인한다.
- **Rationale**: 10 §2-1은 "정화된 HTML에서"라고 적었지만 정화 결과는 같은 AST에서 나오므로 결과가 같고, HTML 파서(jsoup) 의존성을 늘리지 않는다.
- **Alternatives considered**: jsoup로 정화 HTML 파싱(문구와 정확히 일치, 의존성 추가), 원문 Markdown 정규식(문법 기호 노출).

### B-7. 렌더링 1초 제한 구현 — 제안(팀 확인 필요)

- **Decision**: 파싱 후 AST 방문으로 목록·인용 중첩 깊이를 먼저 검사하고, 전체 렌더링은 전용 고정 크기 스레드 풀(`renderExecutor`, 기본 4)에서 `Future.get(1s)`로 기다린다. 시간을 넘기면 400 `CONTENT_TOO_COMPLEX`를 돌려주고 작업을 취소한다.
- **Rationale**: 12 §7-5는 제한만 정했다. 전용 풀은 느린 렌더링이 요청 스레드를 고갈시키지 않게 한다.
- **Alternatives considered**: 요청 스레드에서 시간만 재기(초과해도 끝날 때까지 점유), 본문 길이 제한만.

### B-8. 멱등 요청 요약(hash) — 제안(팀 확인 필요)

- **Decision**: `SHA-256(postId + 정규화한 요청 JSON(title, contentMd, tags, visibility, baseVersion))`. 키 이름 `idem:publish:{memberId}:{key}`, 값 `{hash, status: IN_PROGRESS | DONE, response}`, `NX EX 600`(설정값 `blog.publish.idempotency-ttl`). 잘못된 UUID 형식의 키는 400 `INVALID_IDEMPOTENCY_KEY`, 키 없음은 400 `IDEMPOTENCY_KEY_REQUIRED`.
- **Rationale**: 05 §6은 "hash(요청 본문)"만 정했다. 다른 글에 같은 키를 쓰는 경우도 422가 되게 `postId`를 넣는다.
- **Alternatives considered**: 원문 바이트 그대로 hash(JSON 키 순서·공백에 따라 같은 요청이 422가 됨).

### B-9. 빈 임시글 판정과 정리 경쟁 — 제안(팀 확인 필요)

- **Decision**: "빈" = `btrim(title) = '' AND btrim(content_md) = ''`(발행 검증의 "공백뿐이면 비었음"과 같은 기준). 배치는 후보를 `FOR UPDATE SKIP LOCKED`로 잡고 각 글의 Redis 키가 없을 때만 삭제한다. 13 D-2(사용자가 직접 지우는 빈 임시글)와 **같은 판정 함수(`EmptyDraftPolicy.isEmpty`, btrim 기준)를 006과 공유**한다(006 plan과 합의).
- **Rationale**: 04 §2-5·13 D-2는 "비어 있으면"만 적었다.
- **Alternatives considered**: `= ''`만(공백만 친 빈 글이 남음).

### B-10. 다시 렌더링 배치의 갱신 범위 — 제안(팀 확인 필요)

- **Decision**: `content_html`·`excerpt`·`render_version`만 바꾸고 `updated_at`은 바꾸지 않는다(내 글 관리 정렬 `ix_post_manage`가 흔들리지 않게). 동시 발행과 겹치지 않게 `UPDATE … WHERE id = :id AND edit_version = :읽은 버전 AND render_version < :현재`(맞지 않으면 다음 회차). 썸네일 주소 갱신은 23 §2-4의 공개 주소 변경 SQL이 맡는다.
- **Rationale**: 12 §7-7은 `edited_at`·`edit_version` 유지만 정했다.
- **Alternatives considered**: `updated_at` 갱신(재렌더링만으로 내 글 관리 순서가 바뀜).

### B-11. 자동 저장 단계의 입력 검사 — spec 가정 + 제안

- **Decision**: 자동 저장·수동 저장도 제목 100자·본문 100,000자를 넘으면 400(`TITLE_TOO_LONG`/`CONTENT_TOO_LONG`). 제목 정리(NFC·불가시 문자 제거)는 발행 때만 하고 저장은 입력 그대로. 빈 제목·빈 본문은 저장 허용.
- **Rationale**: spec Assumptions(원문 누락 → 컬럼 제한 적용 가정). 51 `post.title varchar(100)`, `ck_post_content`, `ck_post_draft_content`.
- **Alternatives considered**: 잘라서 저장(사용자 몰래 내용 손실).

### B-12. 미리보기 권한 — spec 가정

- **Decision**: 로그인한 회원이면 이메일 인증 여부와 상관없이 사용(401만). 사용자당 1분 60번(429 `RATE_LIMITED`). 사진 판별 기준은 로그인한 본인.
- **Rationale**: 12 §7-6("로그인 필요"), spec Assumptions.
- **Alternatives considered**: 인증 전 403(42 §5-2 쓰기 행동이 아니므로 과함).

### B-13. 화면 테스트 도구 — 제안(팀 확인 필요)

- **Decision**: Vitest(자동 저장 큐·타이머·IndexedDB 키 규칙), Playwright(오프라인 전환, 두 탭 충돌, XSS 알림창 0회, 375px 레이아웃).
- **Rationale**: 공통 스택은 서버 테스트만 정했다(02 §2). SC-003은 브라우저 종단 간 시험을 요구한다.
- **Alternatives considered**: Cypress(다중 탭 시나리오가 번거로움), 수동 시험.

### B-14. 에디터 컴포넌트

- **Decision**: 공통은 정하지 않는다(각자 자유, Markdown으로 내보내야 함). 공통 화면 코드는 에디터와 무관한 `title`·`contentMd` 문자열 상태만 다룬다.
- **Rationale**: 12 §2, spec Assumptions.
- **Alternatives considered**: Toast UI 고정(개인 선택 제한).

---

## C. 위임·의존 (다른 스펙)

| 대상 | 이 기능이 기대하는 것 |
|---|---|
| 001 account-auth | 세션·CSRF·`EMAIL_NOT_VERIFIED`·`ACCOUNT_WITHDRAWN` 판정, `MemberQueryService.defaultVisibility`, 로그아웃 시 화면이 `clearLocalDrafts(memberId)` 호출 |
| 003 image-upload | `ImageReferenceResolver` 구현(`keyOf`, 작성자 사진 판별, 썸네일 키), `ImageService.syncPostImages`(발행·수동 저장·DB 반영 때, 003 FR-022), `local:` 임시 표시 규칙 |
| 004 visibility-permission | `Visibility` 값·`PUT /api/posts/{postId}/visibility`(004가 정한 경로, 공개 범위만 바꿀 때 `first_public_at` 규칙 공유) |
| 005 post-reading | 상세가 `content_html`·`edited_at`("수정됨")을 그대로 출력, 임시글 404 |
| 006 manage-delete | 휴지통 이동 직전 `AutosaveService.flushNow(postId)` 호출 후 커밋 뒤 Redis 키 삭제(13 §2), "수정 중" 배지(`post_draft` 존재) |
| 008 tag | `TagService.replacePostTags(postId, rawTags)` 정규화·검증(`INVALID_TAG`·`TOO_MANY_TAGS`·`TAG_TOO_LONG`·`TAG_BANNED_WORD`) |
| 011·012 | `PostPublished`·`PostEdited`·`PostWentPublic` 구독 |
