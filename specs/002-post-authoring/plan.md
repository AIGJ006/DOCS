# Implementation Plan: 글 작성·임시저장·발행 (자동 저장, 발행·수정, 본문 정화)

**Branch**: `002-post-authoring` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-post-authoring/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

인증된 회원이 [새 글]로 임시글을 만들고, 제목·Markdown 본문을 **IndexedDB(1초) → Redis(3~30초) → PostgreSQL(1분)** 3단계로 자동 저장하며, 수동 저장·발행은 즉시 DB에 반영한다(C-POST-2). 발행은 트랜잭션 밖에서 렌더링·정화·요약·썸네일을 끝낸 뒤, 한 트랜잭션에서 행 잠금 → 편집 버전 확인 → 태그 확정 → 사진 연결 → 발행본 반영 → 작업본 삭제를 처리하고, 커밋 후 Redis 정리·도메인 이벤트·멱등 응답 저장을 한다(C-POST-3, 05 §7). 발행 글 수정은 `post_draft` 작업본에 쌓여 독자는 마지막 발행본을 본다. 본문은 commonmark-java 0.30.0 + GFM → AST 변환(제목 단계 낮추기·사진 판별·중첩 20단계) → `escapeHtml`·`sanitizeUrls` 렌더링 → OWASP 허용 목록 정화의 단일 `ContentRenderer`로만 HTML이 되고, 모든 응답에 CSP를 건다(C-POST-1). 편집 충돌은 서버 편집 버전(`edit_version`)으로 감지해 409와 서버 내용을 돌려주고, 브라우저가 jsdiff 비교 창으로 사용자가 고르게 한다. 연타는 버튼 비활성화 + `Idempotency-Key`(Redis `SET NX EX 600`)로, Redis 장애 시에는 행 잠금 + 버전 확인으로 막는다.

이 plan은 원문(04·05·12)의 결정을 그대로 따르고, 원문이 정하지 않은 API 경로(새 글·에디터 열기·수동 저장·변경 취소)와 동시성 세부(버전 단조 증가, 수동 저장의 Redis 경유)만 [research.md](./research.md)에 "제안(팀 확인 필요)"으로 보탰다.

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript/JavaScript + React (화면). Spring Boot는 3.x 이상, 버전은 팀 확정(02 §2).

**Primary Dependencies**: Spring Boot(Web, Validation), Spring Data JPA, Spring Security, Spring Session Data Redis, Spring Data Redis(Lettuce, Lua 스크립트), Flyway, ShedLock(`@Scheduled` 실행 잠금), commonmark-java 0.30.0 + GFM 확장(tables·strikethrough·task-list-items·autolink·heading-anchor), OWASP Java HTML Sanitizer 20260924.2, Resilience4j CircuitBreaker(Redis 장애 전환, 제안), AWS SDK v2(사진 공개 주소 판별은 설정값만 사용, 업로드는 003). 화면: React, localforage(IndexedDB), jsdiff, highlight.js(우리 서버 제공).

**Storage**: PostgreSQL(pg_trgm) — `post`, `post_draft`, `post_tag`, `tag`, `post_image`, `image`(읽기·연결), `member`(기본 공개 범위 읽기). 스키마 기준은 [docs/51](../../docs/51-erd-unified.md) 통합 V1(`erd/V1__common_schema.sql`은 저장소에 없음 → 51의 SQL 블록이 기준). Redis — 자동 저장 버퍼(`autosave:post:{postId}` Hash, `autosave:dirty` Set), 발행 멱등 키(`idem:publish:{memberId}:{key}`), 요청 제한 카운터. 브라우저 IndexedDB — `draft:{memberId}:{postId}`, `draft-backup:{memberId}:{postId}`.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers(PostgreSQL + Redis), Spring Security Test(세션·CSRF·행위자별 401/403/404), 렌더러 단위 테스트(12 §9 공격 문자열 32개·정상 문법 13개·부하·제목 7개 = 52개 코퍼스). 화면: Vitest(단위, 제안)·Playwright(브라우저 E2E — SC-003 알림창 0회, 오프라인·충돌 시나리오, 제안).

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO), 최신 데스크톱·모바일 브라우저(375px~).

**Project Type**: web-service (모듈러 모놀리스 Spring Boot + 같은 도메인에서 서빙하는 React SPA).

**Performance Goals**: 목록·상세 서버 응답 300ms 이내(글 1만 건, 이 기능은 상세가 쓰는 `content_html`을 발행 때 미리 만들어 기여). 렌더링 1회 1초 제한, 본문 100,000자 렌더링 목표 수십 ms(12 §9-3 실측 14ms). 자동 저장 API는 PK 조회 1회 + Lua 1회. N+1 금지(발행의 태그·사진 처리는 묶음 쿼리).

**Constraints**: 자동 저장 사용자당 5초에 1번·요청 1MB(429 `Retry-After`/413), 미리보기 사용자당 1분 60번, 본문 ≤ 100,000자, 제목 1~100자, 태그 0~`blog.post.max-tags`(기본 10), 렌더링 1초·중첩 20단계, Redis AOF `everysec`·`noeviction`, 트랜잭션 안에서 외부 호출·Redis 삭제 금지(05 J-5), `edit_version`에 JPA `@Version` 금지(05 J-3).

**Scale/Scope**: 사용자 3개 서비스 공통 Tier A(C-POST-1·2·3), 글 1만 건 기준. 화면 1개(에디터: 저장 상태·충돌 배너·비교 창·발행 설정·미리보기), REST 엔드포인트 7개, 배치 3개(자동 저장 DB 반영 1분, 빈 임시글 정리 매일, 다시 렌더링).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고 추가만 | **PASS** | 51의 `post`·`post_draft`·`post_tag`·`post_image`·`image`·`tag` 컬럼·제약을 그대로 쓴다. 새 테이블은 ShedLock 실행 잠금용 `shedlock` 하나뿐이며 업무 데이터가 아닌 인프라 테이블로, 추가만 한다(data-model.md "추가 제안"). |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | `post` 모듈이 소유한 테이블(`post`·`post_draft`)만 직접 쓴다. 태그는 `tag` 모듈 `TagService`, 사진 판별·연결은 `media` 모듈 공개 Service와 `shared`의 `ImageReferenceResolver` 포트, 기본 공개 범위는 `account` 모듈 공개 Service로만 접근한다. 후속 기능은 도메인 이벤트로만 붙는다. |
| III. 권한 두 겹, 볼 수 없으면 404 | **PASS** | 모든 쓰기는 Service에서 `author_id = :me AND deleted_at IS NULL` 조회로 소유를 확인하고 없으면 404(42 §3 ③·④). 자동 저장도 Redis 키 유무와 상관없이 매번 DB를 확인해 휴지통 글을 404로 막는다(006 FR-023). 현재 사용자는 세션에서만 꺼낸다. 판정 순서 401 → 403(`EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`) → 404. 관리자도 남의 글은 404. 임시글은 작성자 외 404(읽기 판정은 005·004의 `PostAccessPolicy`). |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | Markdown 원문만 받고 HTML은 `shared`의 `ContentRenderer` 하나가 발행·미리보기·다시 렌더링에서 공용으로 만든다. `escapeHtml(true)` + OWASP 허용 목록 이중 방어, 제목은 NFC·불가시 문자 제거 후 글자로만, 모든 응답에 CSP·`nosniff`·`Referrer-Policy`. 저장소 키 등 비밀값은 환경 변수. |
| V. 부가 기능 실패가 글쓰기·읽기를 막지 않음 | **PASS** | `PostPublished`·`PostEdited`·`PostWentPublic`은 트랜잭션 안에서 발행만 하고 처리는 `AFTER_COMMIT` + `@Async` 리스너(20 EV-1). Redis 장애 시 자동 저장은 DB 직접 저장, 멱등 키·요청 제한은 통과하고 행 잠금 + 버전 확인으로 중복 발행을 막는다(02 §2-1). |
| VI. 데이터는 잃지 않는다 | **PASS** | 3단계 자동 저장, 수동 저장·발행은 즉시 DB, 발행 글 수정은 `post_draft`, Redis 삭제는 커밋 후 조건부(버전 ≤ 확인한 버전). 스키마 변경은 Flyway로만. 빈 임시글 정리는 Redis 보관분이 없을 때만. |
| VII. 수치는 설정값 | **PASS** | 자동 저장 주기(1초·3초·30초·1분)·TTL 24h·요청 제한(5초 1번·1MB)·미리보기 제한(1분 60번)·태그 최대 개수·멱등 보관 600초·렌더링 제한(1초·20단계)·공개 주소는 `application.yml` 설정값. `RENDER_VERSION`만 코드 상수(12 §7-7: 규칙이 코드와 함께 바뀌므로). |
| VIII. 실제 DB로 통합 테스트 | **PASS** | Testcontainers PostgreSQL + Redis로 권한 매트릭스(42 §5-2 행 4개 × 행위자 5종), 동시 발행 20건, 충돌·조건부 삭제, Redis 장애 전환을 통합 테스트로 만든다. 인수 시나리오 7개 스토리는 모두 테스트로 옮긴다(quickstart.md). |

**설계 후 재확인 (Phase 1 이후)**: data-model.md·contracts·quickstart를 만든 뒤 다시 확인했다.

- I: data-model.md는 51의 컬럼·CHECK·인덱스만 사용하고 새 컬럼이 없다. `shedlock` 테이블만 "추가 제안"으로 표시 → PASS 유지.
- II: contracts의 엔드포인트는 모두 `post` 모듈 컨트롤러이고, 태그·사진·회원 데이터는 각 모듈 Service 호출로 정의했다 → PASS 유지.
- III: openapi.yaml의 모든 쓰기 경로에 401·403·404가 있고, 남의 글·없는 글·휴지통 글이 같은 404 `NOT_FOUND` 본문이다. 요청 본문에 작성자 ID 필드가 없다 → PASS 유지.
- IV·V·VI·VII·VIII: research.md의 결정(버전 단조 증가, 조건부 Redis 삭제, 렌더링 시간 제한, 설정 키 목록)이 원칙과 충돌하지 않는다 → PASS 유지.

위반 없음. Complexity Tracking 불필요.

## Project Structure

### Documentation (this feature)

```text
specs/002-post-authoring/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # REST API 계약 (OpenAPI 3.1)
│   └── events.md        # 도메인 이벤트·배치 계약
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
docker-compose.yml                         # app + postgres + redis(AOF everysec, noeviction) + storage(pgsty/silo)

backend/
├── pom.xml                                # commonmark 0.30.0(+gfm 확장), owasp-java-html-sanitizer 20260924.2, shedlock, resilience4j
├── src/main/java/com/team/blog/
│   ├── post/
│   │   ├── web/
│   │   │   ├── PostEditorController.java        # POST /api/posts, GET·PUT·DELETE /api/posts/{postId}/working-copy
│   │   │   ├── AutosaveController.java          # PUT /api/posts/{postId}/autosave
│   │   │   ├── PublishController.java           # POST /api/posts/{postId}/publish (Idempotency-Key)
│   │   │   └── MarkdownPreviewController.java   # POST /api/markdown/preview
│   │   ├── application/
│   │   │   ├── PostCommandService.java          # 새 글·수동 저장·변경 취소·새 임시글로 따로 저장
│   │   │   ├── AutosaveService.java             # 소유·버전 확인 + Redis Lua / 장애 시 DB 직접 저장
│   │   │   ├── PublishService.java              # 05 §7 ①~⑪ 오케스트레이션
│   │   │   ├── PublishIdempotency.java          # idem:publish 키 처리
│   │   │   ├── EditorQueryService.java          # 에디터 열기: max(Redis, post_draft, post)
│   │   │   ├── AutosaveFlushJob.java            # 1분마다 dirty → post / post_draft
│   │   │   ├── EmptyDraftCleanupJob.java        # 매일 새벽 빈 임시글 완전 삭제
│   │   │   └── RerenderJob.java                 # render_version < RENDER_VERSION 글 100개씩
│   │   ├── domain/
│   │   │   ├── Post.java, PostDraft.java, PostStatus.java
│   │   │   ├── PublishCommand.java, PublishResult.java   # post.publish(cmd, rendered, now) (05 J-1)
│   │   │   ├── TitleNormalizer.java             # NFC·불가시·방향·제어 문자 제거 (12 §7-4)
│   │   │   └── PublishValidator.java            # 05 §4 항목별 오류 수집
│   │   └── infra/
│   │       ├── PostRepository.java              # @Lock(PESSIMISTIC_WRITE) 조회, @Modifying 갱신
│   │       ├── PostDraftRepository.java         # UPSERT ... WHERE edit_version < EXCLUDED.edit_version
│   │       ├── RedisAutosaveStore.java          # Lua 실행, dirty Set
│   │       └── RedisIdempotencyStore.java
│   ├── tag/application/TagService.java          # (008 소유) 발행 트랜잭션 안에서 정규화·post_tag 교체
│   ├── media/
│   │   ├── application/ImageService.java        # (003 소유) syncPostImages(postId, authorId, keys)
│   │   └── infra/ImageReferenceResolverAdapter.java  # shared 포트 구현: keyOf·작성자 사진·썸네일 키
│   ├── account/application/MemberQueryService.java   # (001 소유) defaultVisibility(memberId)
│   └── shared/
│       ├── application/markdown/
│       │   ├── ContentRenderer.java             # render(md, ImageContext) → RenderedContent(html, excerpt, imageKeys, renderVersion)
│       │   └── ImageReferenceResolver.java      # 포트 (media가 구현)
│       ├── infra/markdown/
│       │   ├── CommonmarkFactory.java           # Parser·HtmlRenderer(escapeHtml, sanitizeUrls) + GFM 확장
│       │   ├── AstTransformer.java              # 제목 -1단계, h- 앵커, 사진/링크 변환, 중첩 깊이
│       │   ├── LinkAttributeProvider.java       # 외부 링크 target·rel, img loading·decoding
│       │   ├── SanitizerPolicy.java             # 12 §4 PolicyFactory
│       │   └── ExcerptExtractor.java            # 요약 200자
│       ├── infra/redis/RedisGuard.java          # CircuitBreaker 래퍼 (Redis 장애 판정)
│       ├── infra/ratelimit/RateLimiter.java     # Redis 고정 창 카운터 (장애 시 통과)
│       ├── web/SecurityHeadersConfig.java       # CSP·nosniff·Referrer-Policy (001의 SecurityFilterChain에 연결)
│       ├── web/error/                           # ErrorResponse{code,message,errors,details}, GlobalExceptionHandler (공통)
│       └── domain/event/PostPublished.java, PostEdited.java, PostWentPublic.java
├── src/main/resources/
│   ├── application.yml                          # blog.autosave.*, blog.post.*, blog.markdown.*, blog.image.*, blog.publish.*
│   ├── redis/autosave-save.lua, redis/autosave-release.lua
│   └── db/migration/
│       ├── V1__common_schema.sql                # 51 SQL 블록 그대로 (공통 기준선, 001과 공유)
│       └── V{n}__shedlock.sql                   # 추가 제안: ShedLock 실행 잠금 테이블
└── src/test/java/com/team/blog/
    ├── post/unit/                               # TitleNormalizer, PublishValidator, Post.publish 시각 규칙
    ├── post/integration/                        # 권한 매트릭스, 자동 저장·충돌, 발행·재발행, 멱등 20건, Redis 장애, 배치
    └── shared/markdown/                         # 52개 코퍼스, 미리보기 = 발행 동일성, 중첩·부하

frontend/src/
├── pages/EditorPage.tsx                         # /write/{postId}: 에디터·발행 설정·미리보기
├── components/editor/
│   ├── SaveStatus.tsx                           # 글자로 표시하는 4가지 저장 상태
│   ├── ConflictBanner.tsx, DiffDialog.tsx       # jsdiff 줄·단어 비교, 좌우/위아래, 이전·다음 차이
│   └── PublishDialog.tsx                        # 태그·공개 범위, "발행 중…" 비활성화
├── features/editor/
│   ├── localDraftStore.ts                       # localforage draft:/draft-backup: 키, 로그아웃 시 삭제
│   ├── autosaveQueue.ts                         # 1초 로컬, 3초/30초 서버, 단일 요청, 지수 백오프+jitter
│   ├── lifecycle.ts                             # visibilitychange·pagehide(keepalive)·beforeunload
│   └── publish.ts                               # Idempotency-Key 생성, 409 IN_PROGRESS 1초 재시도
└── api/posts.ts                                 # contracts/openapi.yaml 클라이언트 (CSRF 헤더)
```

**Structure Decision**: 팀 공통 구조(02 §3 package-by-feature, 모듈마다 `web/application/domain/infra`)를 따른다. 이 기능은 `post` 모듈이 주인이고, Markdown 렌더러·보안 헤더·오류 형식·이벤트는 여러 기능이 함께 쓰므로 `shared`에 둔다. 사진 판별은 `media`(003)가 구현하는 `shared` 포트로 받아 `shared`가 `media`에 의존하지 않게 했다. `tag`·`media`·`account`의 표시된 클래스는 각 스펙(008·003·001)이 소유하며, 이 기능은 그 공개 메서드만 호출한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

위반 없음 — 작성하지 않는다.
