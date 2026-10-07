# Implementation Plan: 공개 범위와 권한

**Branch**: `004-visibility-permission` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-visibility-permission/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

글마다 공개 범위(공통 `PUBLIC`·`PRIVATE`, 선택 구현 `FRIENDS`)를 고르고, 다시 발행하지 않아도 즉시 바꿀 수 있게 한다. 볼 수 없는 글은 상세·목록·글 수·미리보기 어디에도 새지 않게 한다. 모든 기능이 같은 순서(로그인 → 계정 상태 → 대상 확인 → 행동 권한 → 업무 규칙)와 같은 응답 코드(401/403/404/400/409)로 권한을 판정하게 한다.

기술 접근(research.md):

- **읽기 판정 한 곳**: `post.domain.PostAccessPolicy.canRead(post, viewer)`가 판정한다. 공개 범위 값마다 `VisibilityRule` Bean을 둔다(공통 `Public`·`Private`, 적용자 `Friends`).
- **목록 조건 한 곳**: `post.infra.VisibilityFilter.forViewer(viewer, authorId)`를 쓴다. 조건은 `status='PUBLISHED' AND visibility='PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL AND m.withdrawn_at IS NULL`이고, 부분 인덱스 `ix_post_feed`·`ix_post_blog`의 조건과 같다.
- **공개 범위 변경**: `PUT /api/posts/{postId}/visibility`(O8 규약, R-20). 행 잠금 → `first_public_at`은 처음 공개될 때만 기록 → 커밋 후 `PostVisibilityChanged`(+ 처음 공개면 `PostWentPublic`).
- **권한 공통 장치**(`shared/security`·`shared/error`):
  - 세션에서만 얻는 `Viewer`
  - 계정 상태 필터(403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED`)
  - 하나로 통일한 404 `NOT_FOUND`와 공통 404 화면(OG 문구 + `noindex` + `no-store`)
  - `/admin/**`·`/api/admin/**` 보호(비회원 401, 일반 회원 404)
  - 회원별 세션 일괄 삭제
  - Redis 장애 시 비로그인 처리
- **검증**: 공개 범위 매트릭스(06 §8)와 권한 매트릭스(42 §12)를 Testcontainers(PostgreSQL·Redis) 통합 테스트로 모두 확인한다.

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript + React (화면)

**Primary Dependencies**:

- 서버: Spring Boot(3.x 이상, 팀 확정), Spring Web, Spring Security(폼 로그인 + OAuth2 Client), Spring Session Data Redis(`@EnableRedisIndexedHttpSession`), Spring Data JPA(Hibernate `@SQLRestriction`), Flyway, springdoc-openapi
- 공통 스택 중 이 기능이 직접 쓰지 않는 것: MinIO(AWS SDK v2), commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer, IndexedDB/localforage
- 화면: React

**Storage**:

- PostgreSQL(pg_trgm)
  - 사용 컬럼: `post.visibility`·`first_public_at`·`hidden_at`·`deleted_at`, `member.default_visibility`·`role`·`status`·`withdrawn_at`, `auth_identity.email_verified_at`
  - 공통 스키마 변경 없음. 기준은 51이며, `erd/V1__common_schema.sql` 파일은 저장소에 없다.
- Redis: Spring Session 세션과 principal 색인

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test(`@WithUserDetails`/세션 픽스처), MockMvc. 매트릭스는 CSV + `@ParameterizedTest`(R-28). 화면은 React Testing Library(버튼 표시 규칙).

**Target Platform**: Linux 컨테이너(Docker Compose: app + PostgreSQL + Redis + MinIO), 최신 브라우저(375px~데스크톱)

**Project Type**: web-service. 모듈러 모놀리스 백엔드와 같은 도메인에서 서빙하는 React 프런트엔드.

**Performance Goals**:

- 목록·상세 서버 응답 300ms 이내(글 1만 건).
- 상세 판정은 글 + 작성자 JOIN 1회로 끝낸다. 목록은 부분 인덱스를 타는 단일 쿼리(N+1 없음)다.
- `Viewer` 구성은 인증된 요청당 PK 조회 1회다.

**Constraints**:

- 볼 수 없는 글과 없는 글의 응답은 구별할 수 있는 차이가 0이어야 한다(SC-002).
- `PUBLIC`이 아닌 글의 응답은 `Cache-Control: private, no-store`다.
- 403은 계정 상태에만 쓴다.
- 현재 사용자는 세션에서만 얻는다.
- 공개 범위를 바꿔도 `edited_at`·`edit_version`·작업본은 그대로다.
- Redis 장애 시 공개 글 읽기는 계속된다.

**Scale/Scope**:

- 글 1만 건 기준.
- 행위자 7종(비회원·인증 전·회원·작성자·관리자·정지·탈퇴 유예) × 글 상태 7종 × 글 행동 8종.
- 공개 범위 2종(적용자 3종) × 보는 사람 4종 × 노출되는 곳 8곳.
- 이 기능이 소유하는 엔드포인트는 1개(`PUT …/visibility`). 나머지는 공통 장치이며, 다른 기능의 엔드포인트가 이 장치를 쓴다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 (설계 전) | 이유 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | 51의 테이블·컬럼·CHECK를 그대로 쓰고 새 마이그레이션이 없다. `FRIENDS`는 적용자만 CHECK 교체 + 인덱스 추가(06 §6-1, 03 E-10에서 허용된 확장 방식)와 `VisibilityRule` Bean 추가로 붙는다 |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (주석 있음)** | 권한·판정 규칙은 Service·도메인(`post.domain`, `shared/security`)에 두고 공통으로 쓴다. 단, 공용 노출 조건이 `member.withdrawn_at`을 요구해 읽기 쿼리가 `member`를 JOIN한다. Complexity Tracking 참고 |
| III. 권한은 두 겹, 볼 수 없는 것은 404 (NON-NEGOTIABLE) | **PASS** | 이 기능이 원칙 III의 구현 자체다. Service에서 매번 검사(P-1), 세션 기반 `Viewer`, 404 하나로 통일, 공통 404 OG, 공용 목록 조건 |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 새 사용자 입력은 enum 문자열 하나뿐이며 허용 목록으로 검증한다. 404 화면은 고정 문구라 사용자 값을 넣지 않는다. 보안 헤더는 공통 필터를 그대로 쓴다 |
| V. 부가 기능의 실패는 글쓰기·읽기를 막지 않는다 | **PASS** | 이벤트는 AFTER_COMMIT + 비동기라 리스너가 실패해도 변경은 성공한다. Redis 장애 시 세션은 비로그인 처리하고 읽기는 계속된다(R-24) |
| VI. 데이터는 잃지 않는다, Flyway로만 변경 | **PASS** | 비공개로 바꿔도 댓글·좋아요·작업본을 지우지 않는다(V-7). 스키마 변경은 적용자 `V{n}__friends.sql`(Flyway)뿐이다 |
| VII. 수치는 설정값으로 | **PASS** | 이 기능에 조정할 수치가 없다. `FRIENDS` 활성 여부는 설정 플래그가 아니라 Bean과 마이그레이션 유무로 정해진다(02 §7). 세션 만료(14일)는 기존 설정값이다 |
| VIII. 권한·데이터 규칙은 실제 DB로 통합 테스트 | **PASS** | 06 §8·42 §12 매트릭스 전체를 Testcontainers PostgreSQL·Redis로 확인한다. 인수 시나리오는 quickstart·테스트와 1:1로 대응한다 |

**설계 후 재확인 (Phase 1 이후)**:

| 원칙 | 판정 | 설계 산출물에서 확인한 내용 |
|---|---|---|
| I | **PASS** | data-model.md §6 "개인 확장/추가 제안: 없음". 51에 없는 객체를 만들지 않는다 |
| II | **PASS (Complexity Tracking 1건)** | 다른 모듈의 Repository를 호출하지 않는다. `Viewer` 구성은 account 모듈의 공개 Service(`MemberAccessQuery`)를 거친다. 읽기 쿼리의 `member` JOIN만 예외로 기록했다 |
| III | **PASS** | openapi.yaml: 모든 거부 응답이 `LoginRequired`/`AccountStateDenied`/`NotFound` 3종으로 정리되어 있다. 요청 DTO에 작성자 번호가 없다. 404 헤더·본문이 같다 |
| IV | **PASS** | 계약에 HTML을 입력받는 필드가 없다 |
| V | **PASS** | events.md: 리스너 실패 격리, 구독하는 이벤트 없음, 세션 삭제는 이벤트가 아닌 직접 호출 |
| VI | **PASS** | data-model.md §4-2: 어떤 경로에서도 `post_draft`·댓글·좋아요 행을 바꾸지 않는다 |
| VII | **PASS** | 새 수치가 없다 |
| VIII | **PASS** | quickstart.md §2의 통합 테스트 8종은 모두 Testcontainers를 쓴다 |

게이트 결과: **위반 없음**. Complexity Tracking에 원칙 II 관련 정당화 1건을 남긴다.

## Project Structure

### Documentation (this feature)

```text
specs/004-visibility-permission/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # PUT /api/posts/{postId}/visibility, 404·401·403 규격, 관리자 경로
│   └── events.md        # PostVisibilityChanged, PostWentPublic
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/team/blog/
    │   │   ├── post/
    │   │   │   ├── web/
    │   │   │   │   ├── PostVisibilityController.java      # PUT /api/posts/{postId}/visibility
    │   │   │   │   └── dto/VisibilityChangeRequest|Response.java
    │   │   │   ├── application/
    │   │   │   │   ├── PostVisibilityService.java         # 잠금·변경·이벤트 발행
    │   │   │   │   └── PostReadService.java                # 다른 모듈용: requireReadable(postId, viewer)
    │   │   │   ├── domain/
    │   │   │   │   ├── Visibility.java                     # PUBLIC, PRIVATE (적용자 FRIENDS)
    │   │   │   │   ├── Post.java                           # changeVisibility(to, now) → 처음 공개 여부
    │   │   │   │   ├── PostAccessPolicy.java               # canRead (06 R-1)
    │   │   │   │   ├── VisibilityRule.java                 # 인터페이스 (06 §7)
    │   │   │   │   ├── PublicVisibilityRule.java
    │   │   │   │   ├── PrivateVisibilityRule.java
    │   │   │   │   ├── VisibilityRegistry.java             # 등록된 Rule = 허용값 집합
    │   │   │   │   └── PostNotFoundException.java          # extends shared NotFoundException
    │   │   │   └── infra/
    │   │   │       ├── PostRepository.java                 # findForUpdateByIdAndAuthorId (FOR UPDATE)
    │   │   │       ├── PostQueryRepository.java            # 상세 PostView JOIN, 목록 공용 메서드
    │   │   │       └── VisibilityFilter.java               # 공용 노출 조건 (06 R-2·R-2a·R-2b)
    │   │   ├── account/
    │   │   │   └── application/
    │   │   │       ├── MemberAccessQuery.java              # Viewer 구성용 공개 조회 (id → role, status, emailVerified)
    │   │   │       └── MemberSessionService.java           # 회원별 세션 일괄 삭제 (정지·탈퇴·비밀번호, 001/014/015가 호출)
    │   │   └── shared/
    │   │       ├── security/
    │   │       │   ├── SecurityConfig.java                 # /admin/**, /api/admin/** authenticated, CSRF, 세션
    │   │       │   ├── Viewer.java, CurrentViewerResolver.java
    │   │       │   ├── AccountStateFilter.java             # ② 탈퇴 유예·정지 (R-23)
    │   │       │   ├── RequiresVerifiedEmail.java          # ② 인증 전 차단 애너테이션 + 인터셉터
    │   │       │   ├── LoginRequiredEntryPoint.java        # 401 LOGIN_REQUIRED
    │   │       │   ├── AdminPathAccessDeniedHandler.java   # 관리자 경로 일반 회원 → 404
    │   │       │   └── ResilientSessionRepository.java     # Redis 장애 시 비로그인 (R-24)
    │   │       ├── error/
    │   │       │   ├── ReasonCode.java, ErrorResponse.java
    │   │       │   ├── NotFoundException.java, AccountStateException.java, BusinessRuleException.java
    │   │       │   └── GlobalExceptionHandler.java         # 404 본문 하나로 통일
    │   │       ├── web/
    │   │       │   ├── NotFoundPageRenderer.java           # 공통 404 HTML (OG + noindex)
    │   │       │   └── CacheControlPolicy.java             # PUBLIC 아님/404 → private, no-store
    │   │       └── event/
    │   │           ├── PostVisibilityChanged.java
    │   │           └── PostWentPublic.java
    │   └── resources/
    │       ├── application.yml                             # spring.session, server.servlet.session.cookie.*
    │       └── db/migration/                               # 공통 변경 없음 (V1 기준선은 51). 적용자만 V{n}__friends.sql
    └── test/
        ├── java/com/team/blog/
        │   ├── post/domain/
        │   │   ├── PostAccessPolicyTest.java               # unit
        │   │   └── VisibilityRegistryTest.java             # unit
        │   ├── post/infra/VisibilityFilterTest.java        # unit (조건 문자열·파라미터)
        │   ├── post/integration/
        │   │   ├── VisibilityMatrixIT.java                 # 06 §8
        │   │   ├── VisibilityChangeIT.java                 # FR-016~022, 동시성
        │   │   ├── NotFoundIndistinguishableIT.java        # SC-002
        │   │   └── ListIndexUsageIT.java                   # EXPLAIN (R-27)
        │   ├── shared/security/integration/
        │   │   ├── PermissionMatrixIT.java                 # 42 §5 (CSV)
        │   │   ├── AccountStateIT.java
        │   │   ├── AdminPathIT.java
        │   │   └── SessionResilienceIT.java
        │   └── support/IntegrationTestBase.java            # Testcontainers PostgreSQL + Redis, 픽스처
        └── resources/permission/
            ├── post-read.csv                               # 42 §5-1
            └── post-write.csv                              # 42 §5-2 (다른 기능은 각자 CSV 추가)

frontend/
└── src/
    ├── api/
    │   ├── client.ts                                       # CSRF 헤더, 오류 본문 파싱 (code별 분기)
    │   └── posts.ts                                        # setVisibility(postId, visibility)
    ├── features/
    │   ├── visibility/
    │   │   ├── VisibilitySelect.tsx                        # 발행 설정·상세·내 글 관리에서 공용
    │   │   └── VisibilityBadge.tsx                         # 🌐 / 👥 / 🔒 (FR-046)
    │   └── auth-gate/
    │       ├── useAuthGate.ts                              # 401→/login?returnTo, 403 code별 안내 (자동 실행 없음)
    │       └── AuthPrompt.tsx                              # 로그인·이메일 인증·복구 안내
    ├── components/
    │   └── PostActions.tsx                                 # FR-045 버튼 표시 규칙
    └── pages/
        └── NotFoundPage.tsx                                # "볼 수 없는 페이지예요"

docker-compose.yml                                          # app + postgres + redis + minio (공통, 변경 없음)
```

**Structure Decision**: 02 §3의 package-by-feature 웹 애플리케이션 구조(`backend/` + `frontend/`, 공통 경로 규칙)를 따른다.

- 판정 규칙은 `post/domain`(공개 범위·읽기 판정)과 `shared/security`·`shared/error`(행위자·계정 상태·응답 형식)에 둔다.
- 다른 기능(005 상세·목록, 006 내 글 관리, 007 댓글, 008 태그, 009 좋아요, 010 피드, 012 검색·sitemap, 014 관리자)은 다음 공개 API만 호출한다.
  - `PostReadService.requireReadable(postId, viewer)`(③, 실패 시 404)
  - `VisibilityFilter.forViewer(...)`(목록)
  - `@RequiresVerifiedEmail`·`Viewer`(①②)
- 이 기능은 다른 기능의 컨트롤러를 만들지 않는다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 원칙 II: post·discovery 모듈의 **읽기 쿼리**가 account 모듈의 `member` 테이블을 JOIN한다(`m.withdrawn_at IS NULL`). 이 JOIN은 `VisibilityFilter`와 `PostQueryRepository` 한 곳에만 둔다. 쓰기와 Repository 호출은 하지 않는다 | 공용 노출 조건 자체가 "작성자가 탈퇴 신청 상태 아님"을 포함한다(06 R-2a, 2026-10-07 H1). 목록·상세는 N+1 없이 쿼리 한 번이어야 하고(02 §6, 10 §7), 목록은 작성자 정보도 함께 JOIN한다(005) | account Service로 작성자 상태를 따로 조회하면 목록마다 추가 쿼리나 IN 조회가 생기고, 커서 페이지가 탈퇴자 글만큼 짧아진다. `post`에 작성자 탈퇴 여부를 복사하면 51 공통 컬럼을 바꿔야 해서 원칙 I을 위반한다 |
