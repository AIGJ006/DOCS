# 팀 공통 아키텍처 (초안)

> 기준: [01-common-requirements.md](./01-common-requirements.md)의 Tier A + B. `[확정]` MSA 금지, 하나의 저장소·하나의 배포 단위.
> 핵심 아이디어: **Service 계층(업무 규칙)과 ERD는 공통**. 화면은 React, 서버는 REST API, 로그인은 세션 쿠키로 셋이 같다 (2026-10-07 회의 Q2·H7). 컨트롤러·화면 구현과 API 명세(OpenAPI)는 각자 쓴다.

---

## 1. 전체 구조 — 모듈러 모놀리스

```mermaid
flowchart LR
  B["브라우저<br/>(React)"] -->|HTTPS| LB["로드 밸런서<br/>(신뢰 프록시, §5)"]
  LB --> APP

  subgraph APP["Spring Boot 애플리케이션 (1개 배포 단위)"]
    direction TB
    WEB["표현 계층<br/>REST(JSON) API + React 빌드 정적 파일<br/>(같은 도메인에서 서빙)"]
    SEC["Spring Security<br/>세션 쿠키(Spring Session + Redis) + CSRF 토큰"]
    subgraph MODS["기능 모듈 (공통)"]
      ACC["account<br/>회원·인증 식별·프로필"]
      POST["post<br/>글·임시저장·자동 저장·작업본·발행·삭제"]
      TAG["tag<br/>태그 정규화·글-태그"]
      MED["media<br/>이미지 업로드·검증"]
      INT["interaction<br/>댓글·좋아요"]
      DIS["discovery<br/>홈·블로그·태그 목록·(검색)"]
    end
    SHARED["shared<br/>권한 검사·오류 형식·Markdown 렌더러·이벤트"]
    WEB --> SEC --> MODS
    MODS --> SHARED
  end

  APP --> DB[("PostgreSQL<br/>Flyway 마이그레이션")]
  APP --> RD[("Redis (복제 + 자동 전환)<br/>세션·자동 저장 버퍼·요청 제한")]
  B -.->|"Presigned URL로 사진 직접 업로드"| FS
  MED --> FS[("이미지 저장소<br/>MinIO (S3 API)")]
```

| 원칙 | 내용 | 왜 |
|---|---|---|
| 모듈 경계 | 모듈은 **다른 모듈의 Repository·테이블을 직접 쓰지 않고**, 공개된 Service(또는 이벤트)로만 소통 | 개인 확장 모듈이 공통 모듈을 깨지 않게. 나중에 분리 가능 (김민서 SCALE-5) |
| 공통 Service | 권한·상태 전이·검증은 Service에 둔다 | 표현 방식이 달라도 같은 규칙·같은 테스트 |
| 설정값 분리 | 태그 최대 개수, 조회수 중복 기준, 페이지 크기 등은 `application.yml` | 세 사람의 정책 차이를 코드 수정 없이 흡수 |
| 이벤트 | 댓글·좋아요 → `ApplicationEvent` 발행, 알림 등은 `@TransactionalEventListener(AFTER_COMMIT)`로 구독 | 알림(Tier C)·AI·통계를 붙여도 기존 모듈 수정 0줄 |

---

## 2. 기술 스택 (제안)

| 영역 | 선택 | 비고 |
|---|---|---|
| 언어·프레임워크 | Java 21, Spring Boot (버전 팀 확정) | 김민서 문서는 Spring Boot 4.1.1 기준 |
| 데이터 접근 | Spring Data JPA (+ 목록 조회는 필요 시 QueryDSL/JPQL fetch join) | N+1 금지 (나 NFR-07, 김 PERF-2) |
| DB | PostgreSQL (`pg_trgm` 확장 사용) | 셋 다 PostgreSQL |
| 캐시·버퍼 | Redis (AOF `everysec`, `noeviction`), **복제 + 자동 전환**(2026-10-07 회의 H8). 운영은 NHN 제공 Redis (이 두 설정과 관리형 자동 전환 제공 여부를 배포 전 확인) | 세션, 자동 저장 버퍼, 요청 횟수 제한 ([04 문서](./04-draft-and-image.md)), 최근 활동(`member.last_active_at`) 갱신 간격 조절. 장애 시 동작은 §2-1 |
| 파일 저장소 | **MinIO** + Presigned URL(SigV4). 운영은 NHN 제공 MinIO, 로컬 개발은 MinIO 커뮤니티 포크 이미지 | AWS SDK for Java v2, `endpointOverride` + `forcePathStyle(true)`. 공식 MinIO 이미지는 배포 중단이라 로컬 이미지는 [04 §6-1](./04-draft-and-image.md) |
| 메시지 브로커 | 공통은 쓰지 않음. 이벤트는 앱 안에서 커밋 후 처리 ([20 문서](./20-domain-events.md)) | NHN이 RabbitMQ 계정을 제공한다. 유실 없는 알림·메일 발송·AI 비동기 처리가 필요하면 개인 확장으로 쓴다 (도입 시 배포 구성에 들어가므로 팀에 알림) |
| 브라우저 저장 | IndexedDB (예: localforage) | 자동 저장 1차 저장소, 오프라인 사진 대기열 |
| 스케줄러 | Spring `@Scheduled` + ShedLock | 자동 저장 DB 반영(1분), 버려진 사진·빈 임시글 정리 |
| 마이그레이션 | Flyway | 나 NFR-11, 김 MAINT-3 |
| 인증 | Spring Security 폼 로그인 + OAuth2 Client(Google·GitHub), BCrypt | [07 문서](./07-auth.md) |
| 세션 | Spring Session Data Redis (14일, 마지막 활동 기준), `HttpOnly` 세션 쿠키 + CSRF 토큰 (2026-10-07 회의 Q2·H7) | 서버를 늘려도 로그인 유지. 정지·탈퇴·비밀번호 변경 때 세션 삭제로 즉시 로그아웃 |
| 메일 | 개발: Mailpit(Docker), 배포: SMTP (환경 확인 후) | 이메일 인증·비밀번호 재설정 |
| 화면 | **React** + REST API. React 빌드는 API와 같은 도메인에서 서빙 (2026-10-07 회의 Q2·H7) | 글 상세·블로그 주소는 서버가 링크 미리보기 메타(`<title>`·OG)와 404 응답 코드를 넣어 준다 |
| Markdown | **commonmark-java 0.30.0** + GFM 확장(표·취소선·체크리스트·자동 링크·제목 앵커) | 발행할 때 렌더링 → `content_html` 저장, 직접 쓴 HTML은 글자로 ([12 문서](./12-content-sanitize.md)) |
| 정화 | **OWASP Java HTML Sanitizer 20260924.2**, 허용 목록 방식 | 렌더러 뒤에서 한 번 더 (이중 방어) |
| 코드 강조 | highlight.js (브라우저, 우리 서버에서 제공) | 강조 전에도 코드는 `<pre>`로 읽힘 |
| 테스트 | JUnit 5, Testcontainers(PostgreSQL), Spring Security Test | H2 대신 실제 PostgreSQL |
| 실행 | Docker Compose (app + PostgreSQL + Redis + MinIO) | 환경 변수로 비밀값 주입. 공통 시작 템플릿에 포함 (2026-10-07 회의 O9) |
| (개인) | Ollama/LLM(AI, 나·김), CloudFront(강), Redis 트렌딩 캐시(강) | 공통 필수 아님 |

### 2-1. Redis 장애 시 기능별 동작 (2026-10-07 회의 H8)

Redis는 복제 + 자동 전환으로 운영한다(NHN 관리형이면 자동 전환 제공 여부 확인). 그래도 전환 중이거나 멈춘 동안에는 아래처럼 동작한다. 원칙은 **글 읽기는 계속, 보안상 필요한 것만 거부**다.

| 기능 | Redis 장애 시 | 비고 |
|---|---|---|
| 세션 ([07](./07-auth.md) §6) | 세션을 읽지 못하면 **비로그인으로 처리** → 글 읽기는 계속, 로그인이 필요한 요청은 401 | 새 로그인도 세션을 만들 수 없으므로 "잠시 후 다시 시도해 주세요" |
| 요청 제한·중복 방지 카운터 (04·07·21·23·30·31·33·43) | **통과** (경고 로그) | 장애 동안은 제한 없이 동작 |
| 최근 활동 갱신 간격 조절 (`member.last_active_at`) | 갱신을 건너뜀 | 표시용 값이라 늦어져도 됨 |
| 인증·재설정 토큰 ([07](./07-auth.md) §3·§4-1) | **거부** → "잠시 후 다시 시도해 주세요" | 확인할 수 없는 토큰은 받지 않는다 |
| 자동 저장(04)·발행 연타 방지(05)·조회수(31)·트렌딩(32)·인기 태그(22) | 각 문서의 대체 경로 | |
| AI (34) | 503 `AI_UNAVAILABLE` | |

보존해야 하는 데이터(세션·자동 저장)와 캐시(AI 캐시·조회 기록)를 Redis 인스턴스로 나눌지, 용도별 메모리 한도(`maxmemory`)를 어떻게 잡을지는 아키텍처 정리 때 함께 정한다 (M30).

---

## 3. 패키지 구조

기능 모듈 우선(package-by-feature), 모듈 안에서 계층을 나눈다.

```
com.team.blog
├── account/
│   ├── web/            AuthController, ProfileController   ← REST 컨트롤러 (각자)
│   ├── application/    MemberService, ProfileService        ← 업무 규칙 (공통)
│   ├── domain/         Member, AuthIdentity, Role, MemberStatus
│   └── infra/          MemberRepository, AuthIdentityRepository, OAuth/폼 로그인 어댑터
├── post/
│   ├── web/            PostController, ManagePostController
│   ├── application/    PostCommandService(작성·임시저장·발행·삭제), PostQueryService,
│   │                   AutosaveService(Redis 버퍼), AutosaveFlushJob(1분마다 DB 반영)
│   ├── domain/         Post, PostStatus, Visibility, PostAccessPolicy(읽기 판정),
│   │                   VisibilityRule(공개 범위 값마다 1개: PUBLIC·PRIVATE, 선택 FRIENDS)
│   └── infra/          PostRepository
├── tag/                TagService(정규화), Tag, PostTag
├── media/              ImageService(presign·complete·본문 이미지 추출), ImageCleanupJob,
│                       ImageStorage(인터페이스) ← S3ImageStorage(MinIO, S3 API)
├── interaction/        CommentService, LikeService, ViewCountService
├── discovery/          HomeQueryService, BlogQueryService, TagQueryService (읽기 전용 조회)
└── shared/
    ├── security/       CurrentUser, @LoginRequired, AccessPolicy
    ├── markdown/       MarkdownRenderer, HtmlSanitizer
    ├── error/          NotFoundException(→404), ErrorResponse, GlobalExceptionHandler
    └── event/          DomainEvent (PostPublished, CommentCreated, PostLiked …)
```

**개인 확장은 새 모듈 패키지로 추가**한다. 예: 강성찬 `group/`·`mission/`·`streak/`, 나민서 `blog/`·`category/`·`topic/`, 김민서 `revision/`·`bookmark/`·`series/`.

---

## 4. 요청 흐름

### 4-1. 글 발행

```mermaid
sequenceDiagram
  actor U as 작성자
  participant C as PostController
  participant S as PostCommandService
  participant R as MarkdownRenderer
  participant T as TagService
  participant DB as PostgreSQL
  participant E as EventPublisher

  U->>C: 발행 요청 (postId, 제목, 본문, 태그, 공개 범위, baseVersion, Idempotency-Key)
  C->>S: publish(currentUserId, command)
  S->>DB: post 조회 (author_id = currentUserId, deleted_at IS NULL)
  alt 없거나 남의 글
    S-->>C: NotFoundException → 404
  end
  S->>R: render(markdown) → sanitize(html)
  S->>T: 태그 정규화·연결 (post_tag 교체)
  S->>DB: status=PUBLISHED, published_at·first_public_at(최초 1회만), edited_at(다시 발행), excerpt, thumbnail 저장
  S->>E: PostPublished (커밋 후 처리)
  S-->>C: 글 주소
  C-->>U: /@handle/posts/{id} 로 이동
```

- 렌더링은 **저장할 때 1번**, 조회 때는 `content_html`을 그대로 쓴다 (김 PERF-3).
- `published_at`·`first_public_at`은 처음 한 번만 정하고, 다시 발행하면 `edited_at`만 기록한다. 목록은 `first_public_at`으로 정렬한다.
- 연타·재전송은 `Idempotency-Key`(Redis)로 막고, 행 잠금 + `edit_version` 확인으로 한 번 더 막는다.
- 상세 설계는 [05-publish.md](./05-publish.md)에 있다.
- 트랜잭션 안에서 외부 호출(LLM·파일 쓰기·HTTP)을 하지 않는다. 이미지 업로드는 글 저장 전에 별도 요청으로 끝낸다.
- 발행 요청 본문에 업로드가 끝나지 않은 사진(`local:` 주소)이 있으면 400으로 거부한다.
- Redis 자동 저장 키는 **커밋 후** 삭제한다.

### 4-1-1. 자동 저장과 사진 업로드

IndexedDB → Redis → PostgreSQL 3단계 자동 저장과 Presigned URL 사진 업로드의 상세 설계는 [04-draft-and-image.md](./04-draft-and-image.md)에 있다.

```
타이핑 → IndexedDB(1초) → PUT /autosave → Redis(30초 이내) → 스케줄러 → PostgreSQL(1분) / 수동 저장·발행은 즉시
사진   → 브라우저 압축·EXIF 제거 → presign → MinIO 직접 업로드 → complete(서버 재검사) → 본문에는 URL만
```

### 4-2. 글 상세 조회와 권한

```mermaid
flowchart TD
  A["글 상세 요청<br/>(화면 주소 /@handle/posts/{id}·상세 API)"] --> B{"글 존재 &<br/>deleted_at IS NULL?"}
  B -->|아니오| N["404"]
  B -->|예| C{"작성자 본인?"}
  C -->|예| OK["상세 표시 (임시·비공개·숨김 포함)"]
  C -->|아니오| D{"status=PUBLISHED<br/>& visibility=PUBLIC<br/>& hidden_at IS NULL<br/>& 작성자 withdrawn_at IS NULL?"}
  D -->|아니오| N
  D -->|예| V["상세 표시 + 조회수 기록(실패해도 무시)"]
```

이 판정은 `PostAccessPolicy.canRead(post, viewer)` 하나에 둔다. 공용 조건은 `deleted_at IS NULL AND hidden_at IS NULL AND 작성자 withdrawn_at IS NULL`이고, **작성자 본인 예외는 상세(`canRead`)와 내 글 관리에만** 둔다. 목록 조건에는 본인 예외를 넣지 않아 공개 목록 인덱스 조건과 일치시킨다 (2026-10-07 회의 H1). 공개 범위 값마다 `VisibilityRule` Bean을 하나씩 두고, 친구 공개(`FRIENDS`, 공통 규격·선택 구현)나 강성찬의 그룹·링크 공개는 **규칙 Bean을 추가**해서 확장한다. 목록 쿼리도 같은 규칙의 조건(`VisibilityFilter`)만 쓴다. 상세는 [06-visibility.md](./06-visibility.md) §7.

---

## 5. 인증·권한

| 항목 | 공통 규칙 |
|---|---|
| 현재 사용자 | 인증 정보(세션)에서만 꺼낸다. **작성자 ID를 요청 파라미터로 받지 않는다** |
| 소유 검사 | Service에서 `author_id = currentUserId` 조건으로 조회 → 없으면 404 |
| 세션 | **세션 쿠키 하나로 통일** (Spring Session + Redis, 2026-10-07 회의 Q2·H7). 정지·탈퇴·비밀번호 변경 때 그 회원의 세션을 삭제해 즉시 로그아웃. Redis 장애 시 세션 조회 실패는 비로그인으로 처리(§2-1). 공통 코드는 JWT를 쓰지 않는다. 개인 서비스에서 JWT를 쓰려면 Refresh 토큰 Redis 저장·교체 + 토큰 버전 확인을 갖춘다 |
| 비회원 쓰기 | 401 → React가 로그인 화면으로 안내 |
| 정지 계정 | `member.status = SUSPENDED`면 로그인 거부, 쓰기 요청은 403 `ACCOUNT_SUSPENDED` (2026-10-07 회의 H7, [07 §6](./07-auth.md)) |
| CSRF | 세션 쿠키(`HttpOnly`, `Secure`, `SameSite=Lax`) + Spring Security CSRF 토큰. React는 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)에 토큰을 헤더로 보낸다. 토큰 저장 방식은 M17에서 확정 |
| 클라이언트 IP | 앱 앞에 로드 밸런서(신뢰 프록시)가 있다. **로드 밸런서 내부 IP 대역에서 온 요청만** `X-Forwarded-For`의 **가장 오른쪽 신뢰 밖 주소**를 사용자 IP로 쓰고, 그 밖의 출처가 보낸 `X-Forwarded-*`는 무시한다. HTTPS 판별은 `X-Forwarded-Proto`(`Secure` 쿠키·리다이렉트). Spring `server.forward-headers-strategy` + 신뢰 대역 설정. 07·08·09·31·33의 "IP"는 모두 이 값이다. NHN 로드 밸런서 내부 대역은 배포 담당이 확인 (2026-10-07 회의 H4) |
| XSS | 본문은 `ContentRenderer` 하나만 HTML을 만들고 반드시 정화한다. 제목·소개·댓글은 글자만(이스케이프). 상세는 [12 문서](./12-content-sanitize.md) |
| 보안 헤더 | CSP(`default-src 'self'`, `script-src 'self'`, `img-src 'self' {저장소 공개 주소} blob: data:`, `connect-src 'self' {저장소 공개 주소}`, `object-src 'none'`, `frame-ancestors 'none'` 등), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`. `connect-src`는 Presigned 직접 업로드, `blob:`은 오프라인 대기 사진 미리보기용. Google·GitHub 사진 주소는 **소셜 가입 마무리 화면의 `img-src`에만** 넣는다(화면별 CSP). React는 처음 받은 CSP를 화면 이동 때 바꾸지 않으므로 이 화면은 **전체 페이지로 열고 나간다**. 저장소 주소는 `blog.image.public-base-url` 하나를 CSP·정화 허용 목록이 함께 쓴다 (2026-10-07 회의 H3, [12 문서](./12-content-sanitize.md) §8) |
| 비밀값 | OAuth Client Secret·SMTP 비밀번호·저장소(MinIO) 키는 환경 변수로만 |
| 로그인 | 이메일 가입 + Google + GitHub, 로그인 수단이 다르면 별도 계정. 이메일 인증 전에는 쓰기 API 403. 실패 제한·토큰은 Redis. 상세는 [07-auth.md](./07-auth.md) |

### 5-1. API 공통 규약 (2026-10-07 회의 O8)

| 항목 | 규칙 |
|---|---|
| 경로 | `/api/...` 아래 복수형 명사 (`/api/posts`, `/api/members/{handle}`). 경로 변수는 `{postId}`처럼 이름을 붙인다. 내 리소스는 `/api/me/...` (`/api/me/posts`, `/api/me/trash`) |
| 상태 지정 | 켜고 끄는 상태는 `PUT`(지정)·`DELETE`(해제)로, 여러 번 보내도 결과가 같다. 예: `PUT`·`DELETE /api/posts/{postId}/like`, `PUT`·`DELETE /api/members/{handle}/follow` |
| 오류 본문 | `{ "code", "message", "errors": [{ "field", "code", "message" }], "details" }`. `code`는 대문자 이유 코드(`EMAIL_NOT_VERIFIED` 등), `errors`는 입력 검증 오류일 때만, `details`는 추가 정보(선택) |
| 검증 오류 | 400 + `errors`에 필드별 오류 |
| 목록 커서 | 불투명 Base64URL 문자열(안의 시각은 마이크로초까지). 클라이언트는 해석하지 않고 그대로 돌려준다. 잘못된 커서는 400 `INVALID_CURSOR` |
| 인증 | 세션 쿠키 + CSRF 토큰 (§5) |
| 명세 | 각자 OpenAPI(springdoc)로 작성 |

---

## 6. 비기능 공통 기준

세 문서의 수치를 공통 최소선으로 맞춘 값이다. 개인은 더 엄격하게 잡아도 된다.

| 영역 | 공통 기준 | 출처 |
|---|---|---|
| 응답 시간 | 목록·상세 서버 응답 300ms 이내 (글 1만 건) | 나 NFR-05, 강 1초, 김 p95 200ms |
| N+1 | 목록 쿼리 수가 글 수에 비례하지 않음 | 나 NFR-07, 김 PERF-2 |
| 동시성 | 좋아요를 동시에 여러 번 보내도 1건 (복합 PK + `ON CONFLICT DO NOTHING`) | 김 REL-3 |
| 장애 격리 | 조회수·알림·AI 실패가 글쓰기·읽기를 막지 않음. Redis 장애 시에도 글 읽기는 계속(§2-1) | 강 가용성, 나 AI 공통 규칙, 김 REL-4 |
| 반응형 | 375px ~ 데스크톱, 가로 스크롤 없음 | 셋 다 |
| SEO | 글별 `<title>`·description·OG, 공개 글 sitemap. 비공개 글 제외. React 화면이라도 글 상세·블로그 주소는 서버가 링크 미리보기 메타와 404 응답 코드를 넣는다 (2026-10-07 회의 Q2·H7) | 셋 다 |
| 테스트 | 권한 관련 기능은 통합 테스트 필수 | 나 NFR-12, 김 SEC-1 |

---

## 7. 개인 확장이 붙는 지점

| 확장 | 붙는 곳 | 공통 코드 수정 |
|---|---|---|
| 친구 공개 (공통 규격, 선택) | `Visibility.FRIENDS` + `FriendsVisibilityRule` Bean + `friend` 모듈 + 06 §6 마이그레이션 | enum 값 1개 |
| 그룹·링크 공개 (강) | `GroupVisibilityRule`, `LinkVisibilityRule` Bean + `group` 모듈 | enum 값 추가 |
| 카테고리·주제·고정 글 (나) | `post`에 nullable 컬럼 추가 + `category`·`topic` 모듈 | 없음 (컬럼 추가) |
| 작업본/발행본 분리 (김) | `post_revision` 테이블 + 발행 Service 확장 | 발행 로직 |
| 알림·통계·AI | `shared/event` 구독 리스너 추가 | 없음 |
| 이미지 저장소·CDN 교체 | `ImageStorage` 구현체 교체 또는 설정(endpoint·공개 주소) 변경 | 없음 (설정) |
