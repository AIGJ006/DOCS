# Research: 공개 범위와 권한

**Feature**: `004-visibility-permission` | **Date**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

원문에서 이미 결정된 것은 **확정**으로, 원문에 없어 이 계획이 고른 기본값은 **제안(팀 확인 필요)**으로 표시한다. spec.md에는 `[NEEDS CLARIFICATION]`이 없다.

---

## A. 확정 사항 (원문·회의 결정)

### R-01 공개 범위 값과 공통 구현 범위 — 확정

- **Decision**: 공통은 `PUBLIC`·`PRIVATE` 두 값이다. `FRIENDS`는 공통 규격이지만 선택 구현이고 기본으로 꺼져 있다. 값 이름으로 `PROTECTED`는 쓰지 않는다.
- **Rationale**: 06 §1 V-1·V-3, 01 C-POST-4, 2026-10-07 회의 M1. 공통 범위를 작게 유지하면서, 구현하는 사람끼리는 같은 ERD와 규칙으로 비교할 수 있다. `PROTECTED`는 "일부 공개"로 읽혀 헷갈린다.
- **Alternatives considered**: `FRIENDS`를 공통 필수로 하는 안(공통 범위가 커져 M1에서 제외), 글별 허용 목록(개인 확장 영역).

### R-02 권한 없음 응답 = 404 하나 — 확정

- **Decision**: 없는 글, 볼 수 없는 글, 남의 글, 관리자 전용 경로(로그인한 일반 회원)는 모두 404 `NOT_FOUND`이며 본문이 같다. 예외 클래스는 `PostNotFoundException`(→ `NotFoundException`) 하나다. 글 주소의 404 화면 OG 문구도 같다("볼 수 없는 글이에요" / "친구 공개·비공개 글이거나 삭제된 글입니다." + `noindex`).
- **Rationale**: 06 V-5·§3-1·R-4, 42 P-4, 01 결정 기록(2026-10-02), Constitution III. 403을 주면 "글이 있다"는 사실이 드러난다.
- **Alternatives considered**: 403 FORBIDDEN(존재가 드러남), 이유 코드 세분화(42 §4가 금지).

### R-03 읽기 판정은 한 곳에서 — 확정

- **Decision**: `PostAccessPolicy.canRead(post, viewer)`가 판정한다. 순서: ① 존재하고 `deleted_at IS NULL` → ② 작성자 본인이면 표시(임시·비공개·숨김 포함) → ③ 아니면 `status = PUBLISHED AND hidden_at IS NULL AND author.withdrawn_at IS NULL`이고, 그 글의 공개 범위에 맞는 `VisibilityRule.canRead`가 참일 때만 표시. 공통은 `PublicVisibilityRule`(항상 참)과 `PrivateVisibilityRule`(항상 거짓, 작성자 예외는 ②에서 이미 처리)이다.
- **Rationale**: 06 §7 R-1·R-3, 02 §4-2, 13 §2-6 #3(삭제 여부를 가장 먼저 확인).
- **Alternatives considered**: 컨트롤러·React에서 판정(06 R-1이 금지), 공개 범위마다 `if` 분기(확장할 때 공통 코드를 고쳐야 함, R-3).

### R-04 목록 조건 = 공용 `VisibilityFilter` = 공개 목록 인덱스 조건 — 확정

- **Decision**: 모든 공개 목록은 `VisibilityFilter.forViewer(viewer, authorId)`만 쓴다. 공통 결과는 `p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.hidden_at IS NULL AND m.withdrawn_at IS NULL`이다. 목록 조건에는 작성자 본인 예외를 넣지 않는다. `ix_post_feed`·`ix_post_blog`의 부분 인덱스 WHERE가 이 조건에 그대로 들어 있어야 한다.
- **Rationale**: 06 R-2·R-2a·R-2b, 01 결정 기록 H1(2026-10-07), 06 V-8(내 블로그 페이지에도 내 비공개 글은 나오지 않음). 조건이 하나라도 빠지면 PostgreSQL이 부분 인덱스를 쓰지 못한다.
- **Alternatives considered**: 목록마다 WHERE를 직접 쓰는 안(누락 위험, R-2가 금지), 작성자 본인이면 자기 비공개 글도 블로그 목록에 보여 주는 안(V-8·H1이 거부).

### R-05 판정 순서 5단계와 응답 코드 — 확정

- **Decision**: ① 로그인 필요(401 `LOGIN_REQUIRED`) → ② 계정 상태(403 `ACCOUNT_WITHDRAWN` / `EMAIL_NOT_VERIFIED` / `ACCOUNT_SUSPENDED`) → ③ 대상을 볼 수 있나(404) → ④ 행동 권한(404) → ⑤ 업무 규칙(400 / 409). 처음 걸린 단계의 응답을 준다. 403은 계정 상태에만 쓴다.
- **Rationale**: 42 P-2·P-3·P-4·§3·§4, 2026-10-07 H7(정지 계정 쓰기 403 `ACCOUNT_SUSPENDED`). ②를 ③보다 먼저 검사하므로 대상의 존재가 드러나지 않는다.
- **Alternatives considered**: 기능마다 순서를 따로 두는 안(30 §2의 옛 순서. 2026-10-07 결정으로 42 순서로 통일).

### R-06 자기 글 좋아요 = 400 `CANNOT_LIKE_OWN_POST` — 확정 (README "정해진 것" 2026-10-07)

- **Decision**: 판정 순서는 로그인 → 계정 상태 → 볼 수 있나 → 자기 글(400) → 요청 횟수(429)이다. 이미 그 상태여도 200이다.
- **Rationale**: README 결정 표(004 FR-037·009 FR-009·010), 42 P-9·§3·§4(403은 계정 상태에만).
- **Alternatives considered**: 30 K-1의 403(42 §4 원칙과 충돌해 폐기).

### R-07 현재 사용자와 소유 검사 — 확정

- **Decision**: 현재 사용자 id는 Spring Security 세션(Spring Session Data Redis)에서만 꺼낸다. 요청 본문·쿼리에 있는 `authorId`·`memberId`는 DTO에 선언하지 않아 바인딩되지 않는다(무시). 소유 검사는 Service에서 `WHERE id = :postId AND author_id = :me AND deleted_at IS NULL` 조회 한 번으로 하고, 결과가 없으면 404다(③·④를 함께 처리).
- **Rationale**: 02 §5, 42 §3 "구현", 42 §12 #2, Constitution III.
- **Alternatives considered**: 글을 먼저 조회한 뒤 작성자를 비교하는 안(같은 결과지만 쿼리가 늘고, 비교를 빠뜨릴 위험이 있음).

### R-08 인증·세션 기술 — 확정

- **Decision**: Spring Security 폼 로그인 + OAuth2 Client, Spring Session Data Redis(14일, 마지막 활동 기준), 쿠키 `HttpOnly`·`Secure`·`SameSite=Lax` + Spring Security CSRF 토큰. JWT는 쓰지 않는다. 정지·탈퇴·비밀번호 변경 때 그 회원의 Redis 세션을 모두 지운다.
- **Rationale**: 02 §2·§5, 07 §6, 2026-10-07 Q2·H7, Constitution 기술 제약.
- **Alternatives considered**: JWT(공통에서 제외, 개인 서비스만 조건부 허용).

### R-09 Redis 장애 시 세션 — 확정

- **Decision**: 세션을 읽지 못하면 비로그인으로 처리한다. 공개 글 읽기는 계속되고, 로그인이 필요한 요청은 401이다.
- **Rationale**: 02 §2-1(H8), 07 §6, Constitution V.
- **Alternatives considered**: 503으로 전체 거부(읽기까지 막혀 원칙 V 위반).

### R-10 공개 범위 변경 처리 — 확정 (메서드는 R-20 참조)

- **Decision**: 행 잠금(`SELECT … FOR UPDATE`) 뒤에 바꾼다. `edited_at`, `edit_version`, `post_draft`는 건드리지 않는다. `first_public_at`은 `first_public_at IS NULL AND status = 'PUBLISHED' AND to = 'PUBLIC'`일 때만 `now()`로 채운다. 임시글은 값만 저장한다. 같은 값이면 200을 주고 아무것도 바꾸지 않는다. 응답은 `{ visibility, firstPublicAt }`이고, 모르는 값이나 비활성 `FRIENDS`는 400 `INVALID_VISIBILITY`, 없는 글·남의 글·휴지통 글은 404다.
- **Rationale**: 06 §4·V-6·V-7, 05 §3(P-3), 13 §2-3, 42 §5-2.
- **Alternatives considered**: 낙관적 잠금(`edit_version`. 공개 범위 변경이 편집 버전을 올리면 작업 중인 자동 저장과 충돌하므로 06 §4가 배제), 공개로 바꿀 때마다 `first_public_at` 갱신(목록 맨 위로 올리기 악용, P-3이 금지).

### R-11 도메인 이벤트 — 확정

- **Decision**: 공개 범위 값이 실제로 바뀐 경우에만, 같은 트랜잭션 안에서 `PostVisibilityChanged(postId, authorId, from, to, changedAt)`를 발행한다. 이번 변경으로 `first_public_at`이 처음 채워졌으면 `PostWentPublic(postId, authorId, firstPublicAt)`도 함께 발행한다. 처리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`이다.
- **Rationale**: 20 EV-1·EV-4·EV-5·§3-1, 06 §4, 02 §1. 같은 값을 다시 보내면 이벤트도 없다.
- **Alternatives considered**: outbox 테이블(공통에서 제외, EV-2 유실 허용).

### R-12 캐시 금지 — 확정

- **Decision**: `PUBLIC`이 아닌 글의 응답(상세 HTML·API)에는 `Cache-Control: private, no-store`를 붙이고, CDN 캐시 키에 넣지 않는다. 상세는 공개 글도 `private, no-cache`다(40 R-9).
- **Rationale**: 06 R-5, 40 R-9, FR-015.
- **Alternatives considered**: `Vary: Cookie`만 쓰는 안(중간 캐시마다 동작이 달라 보장되지 않음).

### R-13 관리자 경로 — 확정 (경로 범위는 R-22 참조)

- **Decision**: `/admin/**`는 모두 인증이 필요하다. 비회원은 있는 주소든 없는 주소든 401(로그인 화면)이고, 로그인한 일반 회원은 404다.
- **Rationale**: 42 P-10(회의 P3), FR-044. Spring Security의 경로 단위 `authenticated()` 규칙은 경로가 있는지 확인하기 전에 적용되므로 응답이 같다.
- **Alternatives considered**: 관리자 화면을 별도 도메인으로 분리(배포 단위가 늘어 Constitution II와 충돌).

### R-14 `FRIENDS` 적용 규격 — 확정 (선택 구현)

- **Decision**: 적용자만 `V{n}__friends.sql`을 추가한다. 내용은 `ck_post_visibility`·`ck_member_default_visibility`를 `('PUBLIC','FRIENDS','PRIVATE')`로 교체하고 `ix_post_blog_friends`를 추가하는 것이다. 코드는 `Visibility.FRIENDS` enum 값 하나와 `FriendsVisibilityRule` Bean을 추가한다. 친구 판정은 `member_a_id = LEAST(:author, :viewer) AND member_b_id = GREATEST(:author, :viewer) AND status = 'ACCEPTED'`이다. 친구가 보는 블로그 목록은 `published_at DESC, id DESC` 순이고 커서는 `k = (published_at, id)`다.
- **Rationale**: 06 §6-1·§6-3, 02 §7, 03 E-10, 51 §4(V1에는 CHECK 확장을 적용하지 않음).
- **Alternatives considered**: 공통 V1에 `FRIENDS` CHECK를 미리 넣는 안(M1에서 비활성으로 결정).

### R-15 통합 테스트 방식 — 확정

- **Decision**: JUnit 5 + Testcontainers(PostgreSQL, Redis) + Spring Security Test. H2는 쓰지 않는다. 06 §8 매트릭스(공개 범위 × 보는 사람 × 노출되는 곳)와 42 §12 매트릭스(행위자 × 대상 상태 × 행동)를 모두 자동 테스트로 만든다.
- **Rationale**: Constitution VIII, 02 §2·§6, 06 §8, 42 §12, FR-047.
- **Alternatives considered**: H2 또는 Mock 저장소(부분 인덱스·CHECK·`FOR UPDATE` 동작을 검증할 수 없음).

### R-16 공통 기술 스택 — 확정 (팀 공통값)

- **Decision**: Java 21, Spring Boot(3.x 이상, 팀 확정), Maven, Spring Data JPA, Spring Security, Spring Session Data Redis, Flyway, PostgreSQL(pg_trgm), Redis, MinIO(AWS SDK v2), commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer, React(+ IndexedDB/localforage), JUnit 5 + Testcontainers + Spring Security Test, Docker Compose.
- **Rationale**: 02 §2, Constitution 기술 제약. 이 기능에서 직접 쓰는 것은 Spring Security, Spring Session, JPA, PostgreSQL, React다.
- **Alternatives considered**: 없음(공통 결정).

---

## B. 제안 (원문에 없어 이 계획이 고른 기본값, 팀 확인 필요)

### R-20 공개 범위 변경 HTTP 메서드 — 제안(팀 확인 필요)

- **Decision**: `PUT /api/posts/{postId}/visibility` + 본문 `{ "visibility": "PRIVATE" }`. 해제(`DELETE`)는 없다.
- **Rationale**: spec Assumptions·Implementation Notes가 "계획에서 확정"으로 넘긴 항목이다. 2026-10-07 O8(02 §5-1 "상태 지정은 PUT(지정)·DELETE(해제), 여러 번 보내도 결과가 같다")이 06 §4·41 §5(2026-10-02~03의 PATCH)보다 최근이다. 공개 범위 변경은 "값을 지정"하는 일이고, 같은 값을 다시 보내도 200이며 아무것도 바뀌지 않으므로(06 §4) 멱등 PUT의 의미와 같다. 06 §4·41 §5의 PATCH 표기는 이 결정으로 대체되며, specs/006-manage-delete의 계약도 PUT으로 맞춰야 한다.
- **Alternatives considered**: `PATCH`(06·41 원문 그대로. O8 규약보다 이전 표기이고, 부분 수정 의미라 멱등이 보장되지 않음), `POST /api/posts/{postId}/visibility`(멱등이 아님).

### R-21 값 검증(400)과 소유 확인(404)의 순서 — 제안(팀 확인 필요)

- **Decision**: 요청 본문의 `visibility`는 문자열로 받고, ①·② 다음 소유 조회(③·④, 404)를 거친 뒤 ⑤ 단계에서 등록된 `VisibilityRule` 집합으로 검증한다(400 `INVALID_VISIBILITY`). 본문이 아예 없거나 JSON이 아니면 400 `INVALID_REQUEST`다(형식 오류, 판정 단계 밖).
- **Rationale**: 42 §3 "처음 걸린 단계의 응답"에 맞춰, 남의 글에 잘못된 값을 보내도 항상 404가 되게 한다. 기능마다 응답이 달라지지 않는다(SC-008). 허용값은 등록된 Rule Bean의 집합이므로 `FRIENDS`를 적용하지 않은 환경은 Bean이 없어 자동으로 400이 된다.
- **Alternatives considered**: Bean Validation으로 컨트롤러에서 먼저 400(대상과 무관하므로 존재를 드러내지는 않지만, 42 §3 순서와 어긋나 테스트 기대값이 기능마다 달라질 수 있음).

### R-22 관리자 API 경로 범위 — 제안(팀 확인 필요)

- **Decision**: 화면 `/admin/**`와 API `/api/admin/**`를 같은 규칙으로 묶는다. 비회원이면 401 `LOGIN_REQUIRED`다. API는 JSON 오류 본문을 주고, 화면 경로는 React 셸을 401 상태로 내려보내 클라이언트가 `/login?returnTo=`로 안내한다. 로그인한 일반 회원은 404 `NOT_FOUND`다(`AccessDeniedHandler`가 관리자 경로에서는 403 대신 404를 준다).
- **Rationale**: 42 P-10은 `/admin/**`만 적었다. 43의 관리자 API 경로는 원문에 정해져 있지 않지만, React + REST 구조(H7)에서는 API도 같은 규칙이어야 존재가 드러나지 않는다.
- **Alternatives considered**: `/api/admin/**`를 일반 404로 두는 안(비회원에게 있는 경로와 없는 경로의 응답이 달라질 수 있음).

### R-23 계정 상태(②) 검사 위치와 데이터 출처 — 제안(팀 확인 필요)

- **Decision**: 세션에는 `memberId`만 둔다. 인증된 요청마다 `ViewerResolver`가 PK 조회 한 번으로 `Viewer(id, role, status, emailVerified)`를 만든다(`member` + `auth_identity.email_verified_at`, account 모듈의 공개 Service를 통해). ② 검사는 001이 소유한 두 장치가 맡는다(Tier A 교차 분석 2026-10-07로 정합화): 쓰기 Service 첫머리의 `AccountStatusGuard.requireActive(memberId, ActionKind)`(001 T037, `shared/security` 포트)와 탈퇴 유예 전용 필터 `WithdrawnAccountGateFilter`(001 T042a). 이 기능은 새 필터·애너테이션을 만들지 않는다.
  - `WITHDRAWN`이면 허용 목록(`POST /api/me/restore`, `POST /api/auth/logout`, `GET /api/me`의 상태 확인, `GET /api/auth/csrf`)을 뺀 모든 `/api/**` 요청에 403 `ACCOUNT_WITHDRAWN`(FR-031, 001 T042a 필터).
  - `SUSPENDED`이면 쓰기 요청에 403 `ACCOUNT_SUSPENDED`(각 쓰기 Service가 `AccountStatusGuard`를 호출, `ActionKind` 종류와 무관). 세션 삭제와 경쟁해 남은 세션 대비, H7.
  - 이메일 인증 전이면 FR-032의 차단 대상 행동(`ActionKind.CONTENT_WRITE`)에 403 `EMAIL_NOT_VERIFIED`. `ACCOUNT_WRITE`·`CONTENT_CLEANUP`(자기 글 삭제·복구·관리 목록)은 통과.
- **Rationale**: 상태를 세션에 복사해 두면 이메일 인증·복구·정지 때 세션을 갱신해야 하고 누락 위험이 있다. PK 조회 하나는 300ms 기준에 영향이 없다. 정지·탈퇴 때는 Redis 세션 삭제(R-08)가 1차 방어이고, 이 필터가 2차 방어다.
- **Alternatives considered**: 세션 속성에 상태를 캐시(갱신 누락 위험), 컨트롤러마다 수동 검사(순서 불일치 위험, P-2 위반).

### R-24 Redis 장애 시 세션 저장소 처리 방식 — 제안(팀 확인 필요)

- **Decision**: Spring Session의 `SessionRepository`를 감싸는 `ResilientSessionRepository`를 둔다. `findById`에서 `RedisConnectionFailureException`이 나면 `null`(비로그인)을 반환하고 경고 로그를 남긴다. 새 세션 저장이 실패하면 로그인 요청에 503 "잠시 후 다시 시도해 주세요"를 준다. 회원별 세션 일괄 삭제에는 `@EnableRedisIndexedHttpSession`(`FindByIndexNameSessionRepository`)을 쓴다.
- **Rationale**: 02 §2-1의 "비로그인 처리"를 구현하는 방법은 원문에 없다. 기본 `SessionRepositoryFilter`는 예외를 그대로 던져 500이 된다. 정지·탈퇴 때 "그 회원의 모든 세션 삭제"(42 P-7)에는 principal 이름 색인이 필요하다.
- **Alternatives considered**: 서블릿 필터에서 예외를 잡는 안(세션 필터보다 앞에 두기 어렵고 범위가 넓음).

### R-25 이벤트 발행 범위: 임시글의 공개 범위 변경 — 제안(팀 확인 필요)

- **Decision**: `status = PUBLISHED`인 글의 값이 실제로 바뀐 경우에만 `PostVisibilityChanged`를 발행한다. 임시글은 값만 저장하고 이벤트를 내지 않는다.
- **Rationale**: 임시글은 공개 범위와 상관없이 노출이 없다(06 §2). 구독자(검색 색인·sitemap·트렌딩, 20 §5)에게 의미가 없다. 공개 범위는 발행할 때 `PostPublished(visibility)`로 전달된다.
- **Alternatives considered**: 값이 바뀌면 상태와 상관없이 발행(무해하지만 리스너가 할 일 없이 다시 조회함).

### R-26 404 응답의 동일성 보장 방법 — 제안(팀 확인 필요)

- **Decision**:
  - API: `GlobalExceptionHandler`가 `NotFoundException` 계열을 모두 같은 본문 `{ "code": "NOT_FOUND", "message": "볼 수 없는 페이지예요", "errors": [], "details": null }`으로 바꾼다.
  - 글 주소 `/@{handle}/posts/{postId}`: 서버가 고정 템플릿(`NotFoundPageRenderer`)으로 같은 HTML(공통 OG + `noindex`), 같은 헤더(`Cache-Control: private, no-store`)를 낸다.
  - 로그: 사용자 응답에는 이유가 없고, 서버 로그에는 디버그 수준에서만 이유를 남긴다(06 R-4 "사용자에게 보내지 않는다").
  - 테스트: 볼 수 없는 글과 없는 글의 응답 바이트(상태·본문·주요 헤더)를 비교한다(SC-002).
- **Rationale**: FR-013·FR-014, SC-002.
- **Alternatives considered**: 404마다 다른 메시지(구분 가능해져 R-4 위반).

### R-27 부분 인덱스 사용 검증 방법 — 제안(팀 확인 필요)

- **Decision**: Testcontainers 통합 테스트에서 홈·블로그 대표 쿼리를 `SET LOCAL enable_seqscan = off` 상태로 `EXPLAIN (FORMAT JSON)` 실행해, 계획에 `ix_post_feed` / `ix_post_blog`가 있는지 확인한다. 그 밖에 글 1만 건 시드로 응답 시간을 측정하는 성능 테스트를 둔다(SC, 02 §6).
- **Rationale**: 06 R-2b는 "EXPLAIN에 두 인덱스가 나오는지"를 요구한다. 데이터가 적으면 플래너가 순차 탐색을 고르므로, 순차 탐색을 끄고 "조건이 부분 인덱스 술어와 맞는지"(인덱스를 쓸 수 있는지)를 검증한다.
- **Alternatives considered**: 대량 시드만으로 확인(테스트가 느리고 플래너 통계에 따라 흔들림).

### R-28 매트릭스 테스트 구성 — 제안(팀 확인 필요)

- **Decision**: 매트릭스를 `src/test/resources/permission/*.csv`(행위자, 대상 상태, 행동, 기대 상태 코드, 기대 이유 코드)로 두고, JUnit 5 `@ParameterizedTest @CsvFileSource`로 실행한다. 쓰기 행동은 요청 전후에 `post`의 `title, content_md, status, visibility, edit_version, updated_at, edited_at, first_public_at`을 스냅샷해 비교한다(42 §12 #1). 다른 기능의 칸(댓글·좋아요·신고·알림 등)은 각 기능 스펙이 엔드포인트를 만들 때 같은 CSV 형식으로 추가한다.
- **Rationale**: FR-047, SC-001·SC-003·SC-008. 표를 코드와 분리하면 42 표와 줄 단위로 대조할 수 있다.
- **Alternatives considered**: 칸마다 테스트 메서드(수백 개, 누락을 찾기 어려움).

### R-29 화면 버튼 표시 기준 데이터 — 제안(팀 확인 필요)

- **Decision**: 글 상세 응답(005 담당)에 `viewer: { isAuthor, loggedIn, emailVerified, isAdmin }` 같은 표시 플래그를 넣고, React `PostActions`가 FR-045 규칙으로 버튼을 고른다. 버튼을 누른 결과는 공통 `useAuthGate`가 처리한다. 401이면 `/login?returnTo=` 안내, `EMAIL_NOT_VERIFIED`면 "인증 메일 다시 보내기" 안내, `ACCOUNT_WITHDRAWN`이면 `/account/restore`로 안내하고(015 화면 경로, ANALYSIS-tier-a R13), 로그인 뒤 자동 실행은 하지 않는다.
- **Rationale**: 42 §11, H6, FR-045. 서버 판정은 별도로 매번 한다(P-1).
- **Alternatives considered**: 화면이 세션 정보와 작성자 id를 직접 비교(작성자 id를 화면에 노출할 필요가 생김).

### R-30 404 응답 캐시 — 제안(팀 확인 필요)

- **Decision**: 글 주소와 API의 404 응답에도 `Cache-Control: private, no-store`를 붙인다.
- **Rationale**: 비공개였다가 공개로 바뀐 글이 중간 캐시의 404로 계속 보이는 일을 막고, 공개 글 404와 비공개 글 404의 헤더가 달라지지 않게 한다(FR-013 "모두 같아야").
- **Alternatives considered**: 404를 짧게 캐시(헤더 동일성은 지켜지지만 공개 전환이 늦게 반영됨).
