---

description: "Task list for 001-account-auth (공통 기반 Phase 1·2 소유)"
---

# Tasks: 계정·인증 (가입·로그인·블로그 주소·닉네임·프로필·친구·최근 활동)

**Input**: Design documents from `/specs/001-account-auth/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, contracts/events.md, quickstart.md, `.specify/memory/constitution.md`, README.md("정해진 것", "Tier A plan에서 정한 공통 설계")

**Tests**: constitution VIII(권한·데이터 규칙은 실제 DB 통합 테스트)와 plan의 테스트 계획(R-35)에 따라 테스트 작업을 넣는다. 각 Phase에서 테스트 작업이 구현 작업보다 먼저 오며, **먼저 작성해 실패를 확인한 뒤** 구현한다. 통합 테스트는 모두 Testcontainers PostgreSQL + Redis(H2 금지)를 쓴다.

**Organization**: 이 tasks.md의 **Phase 1(Setup)·Phase 2(Foundational)는 Tier A 5개 기능(001·002·004·005·006) 전체의 공통 기반**이다. Phase 2는 둘로 나눈다 — **2A: 모든 기능이 기다리는 공통 기반**, **2B: 001의 스토리만 기다리는 계정 검증 정책**. 002·004·005·006은 2A 체크포인트 뒤에 시작할 수 있다. Phase 3 이후는 spec.md의 User Story 순서(US1~US8)다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/main/java/com/team/blog/...`, `backend/src/main/resources/...`, `backend/src/test/java/com/team/blog/...`, `frontend/src/...`
- 경로 약어: 아래 설명에서 `B/` = `backend/src/main/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `T/` = `backend/src/test/java/com/team/blog/`, `F/` = `frontend/src/`. 실제 파일은 약어를 풀어 만든다.
- 근거 표기: FR-xxx = spec.md, R-xx = research.md, §n = data-model.md 절, 문서 번호(07 §6 등) = `docs/`.

---

## Cross-feature Dependencies

**이 기능이 먼저 필요로 하는 다른 기능의 작업** (직접 만들지 않는다):

- 선행: specs/002 Frontend `clearMemberDrafts(memberId)`와 미전송 작업 1회 전송 함수 (로그아웃 때 IndexedDB `draft:{memberId}:*`·`draft-backup:{memberId}:*` 삭제, FR-041·R-28) → T072가 호출. 002가 아직 없으면 T072는 호출부만 두고 002 완료 후 연결한다.
- 선행: specs/003 `POST /api/images/presign {purpose: PROFILE}`·complete(256×256·1MB 검사)와 `ImageCleanupJob`(TEMP 24시간, `detached_at` 7일) → T084(소셜 사진 복사), T121(프로필 사진 업로드), US6 #6(7일 뒤 삭제). 003은 Tier B(아직 plan 없음)이므로 **`ProfileImageService`는 이 기능이 임시 구현(T116)을 두고 003에서 교체**한다.
- 선행: specs/004 T009 `post.domain.Visibility`, T013 `VisibilityRegistry.require(raw, field)`(등록된 VisibilityRule = 허용값), T016 `PostReasonCode.INVALID_VISIBILITY`·`InvalidVisibilityException` → T118(새 글 기본 공개 범위 `PATCH /api/me/settings`). 001은 별도 `account.domain.Visibility`를 만들지 않는다(plan과 다름, 아래 Notes).
- 선행: specs/005 `PageShellController`의 `/@{handle}` 대문자 301·없는 주소 404 (US3 #7 — 001은 `MemberQueryService.normalizeHandle`·`findReadableBlogOwner`만 제공, T039).
- 참고(선행 아님): specs/016 색 토큰 — T120 `DefaultAvatar` 8색 값은 016 확정 전까지 임시 값.

**다른 기능이 이 기능에 기대는 작업** (후행 기능이 "선행: specs/001 T0xx"로 참조):

| 제공 | 작업 | 경로 |
|---|---|---|
| backend Maven 골격·패키지 | T001, T002 | `backend/pom.xml`, `B/BlogApplication.java`, 모듈 패키지 |
| frontend React 골격 | T003 | `frontend/`, `F/main.tsx`, `F/App.tsx` |
| docker-compose (app+PostgreSQL+Redis+MinIO+Mailpit) | T004, T005 | `docker-compose.yml`, `backend/Dockerfile` |
| Flyway V1 공통 스키마(51) / shedlock | T011 / T012 | `R/db/migration/V1__common_schema.sql`, `V2__shedlock.sql` |
| Testcontainers 통합 테스트 베이스·지원 | T008, T009 | `T/support/IntegrationTestBase.java`, `T/support/*` |
| ShedLock·스케줄러 / 비동기·DomainEvent | T013 / T014 | `B/shared/config/SchedulingConfig.java`, `AsyncConfig.java`, `B/shared/event/DomainEvent.java` |
| 설정값 바인딩 (`blog.time-zone`, `blog.image.public-base-url`, Clock) | T015 | `B/shared/config/CoreProperties.java`, `TimeConfig.java` |
| 공통 오류 본문·GlobalExceptionHandler·NotFoundException→404 | T018 | `B/shared/error/*` |
| DB UNIQUE 위반 → 제약 이름 | T019 | `B/shared/infra/db/UniqueViolations.java` |
| 불투명 커서 코덱 (`l` 목록 구분 포함) | T021 | `B/shared/web/cursor/*` |
| Redis 장애 판정·요청 제한(장애 시 통과) | T023 | `B/shared/infra/redis/RedisGuard.java`, `B/shared/infra/ratelimit/RateLimiter.java` |
| SecurityFilterChain(세션 쿠키+CSRF `X-XSRF-TOKEN`, 401 EntryPoint, 확장 지점) | T026 | `B/shared/security/SecurityConfig.java`, `SecurityFilterChainCustomizer.java`, `LoginRequiredEntryPoint.java` |
| CurrentUser·MemberPrincipal·@LoginRequired | T027 | `B/shared/security/CurrentUser.java` 외 |
| Spring Session 인덱스 저장소 + Redis 장애 시 비로그인 | T028 | `B/shared/security/session/SessionConfig.java`, `ResilientSessionRepository.java` |
| 보안 헤더/CSP 필터 + 화면별 CSP 확장 지점 | T029 | `B/shared/web/SecurityHeadersFilter.java`, `CspContributor.java` |
| 클라이언트 IP(신뢰 프록시) | T030 | `B/shared/web/ClientIp.java` |
| SPA 정적 `index.html` 대체 경로 | T031 | `B/shared/web/SpaForwardingController.java` |
| `GET /api/auth/csrf` | T032 | `B/account/web/AuthController.java` |
| Member·AuthIdentity 엔티티 | T036 | `B/account/domain/*`, `B/account/infra/*Repository.java` |
| AccountStatusGuard.requireActive(memberId, ActionKind) | T037 | `B/shared/security/AccountStatusGuard.java`(포트), `B/account/application/AccountStatusGuardService.java` |
| SessionTerminator(회원의 모든 세션 삭제) | T038 | `B/account/application/SessionTerminator.java` |
| MemberQueryService(findAccessInfo·findReadableBlogOwner·defaultVisibility·normalizeHandle) | T039 | `B/account/application/MemberQueryService.java` |
| ImageUrlResolver(public-base-url + key)·ProfileImageQuery(현재 프로필 사진의 원본·썸네일 키 `ProfileImageKeys`, 읽기 전용) | T040 | `B/media/application/ImageUrlResolver.java`, `ProfileImageQuery.java` (003이 그대로 소유·확장) |
| frontend `F/api/client.ts` **소유** (004 T024는 404 `onNotFound` 분기만 추가) | T041, T042 | `F/api/client.ts` |
| 탈퇴 유예 회원 `/api/**` 차단 필터(허용 목록 외 403 `ACCOUNT_WITHDRAWN`, 004 FR-031·R-23) | T042a | `B/account/infra/security/WithdrawnAccountGateFilter.java` |
| SuspensionService(정지 걸기·해제·조회, 014 사용) | T106 | `B/account/application/SuspensionService.java` |
| FriendshipQueryService / FriendButton | T129 / T132 | 004 친구 공개 선택 구현, 005 블로그 머리말 |
| LastActiveQueryService / LastActiveBadge | T140 / T142 | 005 블로그 머리말 |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 저장소에 아직 코드가 없다. Tier A 전체가 쓰는 backend·frontend·실행 환경 골격을 만든다(02 §2·§3, constitution "기술 제약").

- [X] T001 backend Maven 프로젝트를 만든다: `backend/pom.xml`(Java 21, Spring Boot 3.x 최신 안정판 — 버전은 팀 확정 전 기본값(R-36), groupId `com.team`, artifactId `blog`), Maven Wrapper(`backend/mvnw`, `backend/.mvn/wrapper/`), `B/BlogApplication.java`(`@SpringBootApplication`, 패키지 `com.team.blog`). 의존성: spring-boot-starter-web·validation·data-jpa·security·oauth2-client·data-redis·mail·actuator, spring-session-data-redis, flyway-core + flyway-database-postgresql, postgresql(runtime), net.javacrumbs.shedlock:shedlock-spring + shedlock-provider-jdbc-template, 테스트: spring-boot-starter-test, spring-security-test, spring-boot-testcontainers, org.testcontainers:postgresql·junit-jupiter. surefire(단위 `*Test`)·failsafe(통합 `*IntegrationTest`, `*IT`) 분리. 저장소 루트 `.gitignore`에 `target/`, `node_modules/`, `dist/`, `.env`, `*.iml`, `.idea/`를 넣는다. (구현 메모: Spring Boot 4.1.1 사용. Boot 4에서 스타터 이름이 바뀌어 spring-boot-starter-webmvc·security-oauth2-client·session-data-redis를 쓰고, Flyway 자동 설정에 spring-boot-starter-flyway가 필요하다. Testcontainers 2.x 이름은 testcontainers-postgresql·testcontainers-junit-jupiter. 테스트용 spring-boot-starter-webmvc-test·security-test를 더함)
- [X] T002 [P] package-by-feature 골격을 만든다(02 §3): `B/account/{web,application,application/policy,application/mail,domain,infra,infra/security,infra/redis}`, `B/post`, `B/tag`, `B/media/application`, `B/interaction`, `B/discovery`, `B/shared/{config,error,event,security,security/session,web,web/cursor,infra/db,infra/redis,infra/ratelimit}` 각각에 모듈 책임을 한 줄로 적은 `package-info.java`. 모듈 경계 규칙(다른 모듈의 Repository·테이블 직접 사용 금지, constitution II)을 `B/package-info.java` 주석에 적는다.
- [X] T003 [P] frontend React 골격을 만든다: `frontend/package.json`(Vite + React 18 + TypeScript + react-router-dom + localforage, devDependencies vitest·@testing-library/react·@testing-library/user-event·jsdom), `frontend/vite.config.ts`(개발 서버 `/api`·`/oauth2`·`/login/oauth2` → `http://localhost:8080` 프록시, build 출력 `frontend/dist`), `frontend/vitest.config.ts`, `F/test/setup.ts`, `frontend/index.html`(인라인 스크립트 없음 — CSP `script-src 'self'`, `<!--app-head-->` 자리 표시자는 005 R-25용으로 `<head>`에 둔다), `F/main.tsx`, `F/App.tsx`(react-router 라우트 자리: `/`, `/signup`, `/signup/social`, `/login`, `/verify-email`, `/forgot-password`, `/reset-password`, `/reagree`, `/settings`, `/terms`, `/privacy`), 빈 폴더 `F/pages`, `F/components`, `F/features`, `F/api`. (구현 메모: React 18.3 + Vite 8 + TypeScript 6.0(typescript-eslint가 7을 아직 지원하지 않음), react-router-dom 7(6.x는 npm audit 중간 위험 2건) + Vitest 5, 화면 테스트용 @testing-library/jest-dom 추가)
- [X] T004 [P] `docker-compose.yml`(저장소 루트)과 `.env.example`을 만든다: `postgres`(이미지 `postgres:18` — 51 검증 버전, `pg_isready` healthcheck, 볼륨), `redis`(`redis:7`, `--appendonly yes --appendfsync everysec --maxmemory-policy noeviction`, healthcheck `redis-cli ping`), `minio`(`pgsty/silo:RELEASE.2026-09-16T00-00-00Z`, `MINIO_ROOT_USER=${STORAGE_ROOT_USER}`·`MINIO_ROOT_PASSWORD=${STORAGE_ROOT_PASSWORD}`·`MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:8080`, 04 §6-1), `mailpit`(`axllent/mailpit`, SMTP 1025·웹 8025, 02 §2), `app`(`backend/Dockerfile` 빌드, 8080, 위 서비스 healthy 후 시작, `env_file: .env`). `.env.example`에는 quickstart §1의 키(`GOOGLE_CLIENT_ID/SECRET`, `GITHUB_CLIENT_ID/SECRET`, `SPRING_MAIL_HOST=mailpit`, `SPRING_MAIL_PORT=1025`, `BLOG_AGREEMENT_TERMS_VERSION=2026-10-07`, `BLOG_AGREEMENT_PRIVACY_VERSION=2026-10-07`, `STORAGE_ROOT_USER/PASSWORD`, `BLOG_IMAGE_PUBLIC_BASE_URL=http://localhost:9000/blog`)를 값 없이 또는 개발용 값으로 둔다. 비밀값은 환경 변수로만 주입(constitution IV). (구현 메모: 이미지 태그는 Testcontainers와 같게 postgres:18-alpine·redis:7-alpine. .env.example에 POSTGRES_DB/USER/PASSWORD·BLOG_TRUSTED_PROXIES 추가)
- [X] T005 `backend/Dockerfile` 다단계 빌드를 만든다: ① `node:lts`에서 `frontend` 빌드 → ② `eclipse-temurin:21-jdk`에서 `frontend/dist`를 `backend/src/main/resources/static/`으로 복사한 뒤 `./mvnw -DskipTests package` → ③ `eclipse-temurin:21-jre` 실행 이미지. React 빌드를 API와 같은 도메인에서 서빙한다(constitution "기술 제약"). (T001, T003 후) (구현 메모: 빌드 컨텍스트는 저장소 루트(compose app 서비스), 실행 이미지는 비루트 사용자)
- [X] T006 [P] 코드 형식 도구를 설정한다: `backend/pom.xml`에 Spotless(google-java-format) `check`를 verify 단계에 연결, `frontend/eslint.config.js` + `frontend/.prettierrc`, 저장소 루트 `.editorconfig`(UTF-8, LF, 들여쓰기 Java 4·TS 2). (구현 메모: google-java-format AOSP 스타일(들여쓰기 4칸, .editorconfig와 일치), 버전 1.28.0(1.37은 JDK 21에서 실패))
- [X] T007 기본 설정 파일을 만든다: `R/application.yml`(`spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.flyway.enabled=true`, `spring.jackson.serialization.write-dates-as-timestamps=false`, `spring.jackson.time-zone=UTC`, `spring.data.redis.timeout=500ms`(장애 판정 빠르게), `spring.mail.host/port`는 `${SPRING_MAIL_HOST:localhost}`·`${SPRING_MAIL_PORT:1025}`, `server.shutdown=graceful`), `R/application-local.yml`(docker-compose 접속 정보), `backend/src/test/resources/application-test.yml`(로그 수준, 메일 비활성 대신 T009의 캡처 Bean 사용). (구현 메모: Boot 4(Jackson 3)는 write-dates-as-timestamps가 spring.jackson.datatype.datetime 아래로 옮겨져 그 키를 씀)

**Checkpoint**: `./mvnw -q verify`(테스트 없음)와 `npm run build`가 통과하고 `docker compose up -d`로 postgres·redis·minio·mailpit이 healthy.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Tier A 모든 기능이 쓰는 공통 기반(2A)과 001 스토리들이 함께 쓰는 계정 검증 정책(2B).

**⚠️ CRITICAL**: 2A가 끝나기 전에는 어떤 기능(001·002·004·005·006)의 User Story도 시작하지 않는다. 2B는 001의 US1·US2·US3·US4·US6만 막는다.

### 2A-1. 테스트 기반 (Testcontainers)

- [X] T008 `T/support/IntegrationTestBase.java`를 만든다: `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`, static 싱글턴 컨테이너 `PostgreSQLContainer("postgres:18")`·`GenericContainer("redis:7")`(`@ServiceConnection`, 모든 테스트 클래스가 재사용), `@BeforeEach`에서 `flyway_schema_history`·`shedlock`을 뺀 모든 테이블 `TRUNCATE … RESTART IDENTITY CASCADE` + Redis `FLUSHALL`. 공개 필드/메서드: `mockMvc`, `jdbc`(JdbcTemplate), `redis`(StringRedisTemplate), `postgres()`, `redisContainer()`. H2를 쓰지 않는다(constitution VIII, R-35). (구현 메모: 이미지는 compose와 같은 postgres:18-alpine·redis:7-alpine. 공용 필드 mailSender(CapturingMailSender)와 회원 픽스처 도우미 members()를 더함)
- [X] T009 [P] 통합 테스트 지원 도구를 만든다 (T008 후): `T/support/CapturingMailSender.java`(`@TestConfiguration`의 `@Primary JavaMailSender`, 보낸 메일을 받는 사람별로 보관, `lastTokenFor(email)`이 본문의 `token=` 값을 꺼냄), `T/support/RedisOutage.java`(Testcontainers `getDockerClient().pauseContainerCmd/unpauseContainerCmd`로 Redis 일시 정지·복구, try-with-resources), `T/support/TestLogin.java` + `T/support/TestLoginController.java`(test 프로필에서만 등록되는 `POST /test/login-as/{memberId}` — `MemberPrincipal`로 SecurityContext를 만들고 세션에 저장해 `SESSION` 쿠키를 돌려줌, `withCsrf(request, session)` 도우미 포함), `T/support/MemberFixtures.java`(JdbcTemplate으로 `member`+`auth_identity`를 넣는 빌더: handle·nickname·provider·status·emailVerified·passwordHash(BCrypt) 지정, 정지 이력 추가 `suspend(memberId, endsAt, reason)`).

### 2A-2. 데이터베이스 (Flyway V1 = 51)

- [X] T010 `T/shared/db/FlywayBaselineIntegrationTest.java`를 먼저 작성한다(실패 확인): 빈 DB에 마이그레이션 적용 후 51 §검증 결과의 카탈로그 수치를 확인 — `information_schema`/`pg_catalog`로 업무 테이블 20개(+`shedlock` 1개), 업무 컬럼 148개, FK 40개, CHECK 52개, 별도 인덱스 42개(UNIQUE 인덱스 5개 포함), GIN 4개, `pg_trgm` 확장 존재, 모든 시각 컬럼 `timestamptz`. 동작 3가지: `uq_member_nickname`(`Kim`·`kim` 두 번째 INSERT 23505), `ck_friendship_order`(a ≥ b 거부), `uq_image_profile_current`(같은 회원 ATTACHED·detached_at NULL PROFILE 두 행 거부). (구현 메모: 51 수치에 PK 20·UNIQUE 제약 9·timestamptz 41개도 함께 확인)
- [X] T011 `R/db/migration/V1__common_schema.sql`을 만든다: `docs/51-erd-unified.md`의 "## ERD 변경 제안" 절 ```` ```sql ```` 블록(첫 줄 `-- 팀 공통 통합 V1: 20개 테이블…`부터 블록 끝까지, `CREATE EXTENSION IF NOT EXISTS pg_trgm` 포함)을 **한 글자도 바꾸지 않고** 옮긴다(예: `awk '/^## ERD 변경 제안/{f=1} f&&/^```sql/{p=1;next} p&&/^```/{exit} p' docs/51-erd-unified.md > …`). 원문이 가리키는 `erd/V1__common_schema.sql`은 저장소에 없으므로 51이 기준이다(README "알려진 누락", R-02). 파일 맨 위에 출처 주석 한 줄만 더한다. 공통 ERD 테이블·컬럼은 이후 바꾸지 않는다(constitution I).
- [X] T012 [P] `R/db/migration/V2__shedlock.sql`을 만든다(추가 제안 — 002 data-model §1-6, 006 R13, README "배치 잠금"): `CREATE TABLE shedlock (name varchar(64) NOT NULL PRIMARY KEY, lock_until timestamptz NOT NULL, locked_at timestamptz NOT NULL, locked_by varchar(255) NOT NULL);` + `COMMENT ON TABLE shedlock IS '배치 실행 잠금 (ShedLock JDBC)'`. 업무 데이터가 아닌 인프라 테이블이며 002·003·006·014·015 배치가 함께 쓴다.

### 2A-3. 스케줄러·비동기·설정값

- [X] T013 [P] `B/shared/config/SchedulingConfig.java`: `@EnableScheduling` + `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`, `JdbcTemplateLockProvider`(`usingDbTime()`, 테이블 `shedlock`), 스케줄러 스레드 풀 크기 설정값 `blog.scheduling.pool-size`(기본 2). cron 시간대는 `blog.time-zone`(Asia/Seoul)을 쓰도록 각 배치가 `zone = "${blog.time-zone}"`을 지정한다는 규칙을 클래스 주석에 적는다(정리 배치 03:30 KST — README). (구현 메모: 잠금 동작 확인용 SchedulingLockIntegrationTest 추가)
- [X] T014 [P] `B/shared/config/AsyncConfig.java`(`@EnableAsync`, `ThreadPoolTaskExecutor` Bean `eventExecutor`·`mailExecutor`, 큐 크기·스레드 수는 `blog.async.*` 설정값, 거부 시 경고 로그)와 `B/shared/event/DomainEvent.java`(마커 인터페이스 — 필드는 ID·enum·`Instant`만, 커밋 후 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`로 처리, 유실 허용 — 20 EV-1~EV-7 규칙을 Javadoc에 적음). (구현 메모: 이름 없는 @Async는 eventExecutor를 쓰도록 AsyncConfigurer 구현. 실행기 크기는 CoreProperties.async)
- [X] T015 [P] 공통 설정값 바인딩: `B/shared/config/CoreProperties.java`(`@ConfigurationProperties("blog")` + `@Validated`: `timeZone`(기본 `Asia/Seoul`), `image.publicBaseUrl`(필수, CSP·이미지 주소가 같은 값을 씀 — 12 §8)), `B/shared/config/TimeConfig.java`(`Clock` Bean = `Clock.systemUTC()`, `ZoneId` Bean = `blog.time-zone`), 단위 테스트 `T/shared/config/CorePropertiesBindingTest.java`(기본값·잘못된 시간대 거부). 각 기능은 자기 모듈에 `@ConfigurationProperties("blog.<기능>")` 클래스를 더한다(constitution VII). (구현 메모: 002가 요구한 blog.image.legacy-base-urls(기본 빈 목록)와 CSP용 publicOrigin(), blog.scheduling·blog.async도 CoreProperties에 둠)
- [X] T016 [P] 계정 설정값 바인딩: `B/account/infra/AccountProperties.java`(`@ConfigurationProperties("blog")`의 하위 `auth`·`member`·`agreement`·`availability`·`policy`)와 `R/application.yml`의 `blog.*` 기본값을 data-model §6 표 그대로 넣는다 — `blog.auth.session-timeout=14d`, `login.max-failures=5`·`lock-duration=15m`·`ip-limit-per-minute=20`, `verify.token-ttl=24h`·`resend-interval=1m`·`resend-daily-limit=10`, `reset.token-ttl=30m`·`email-interval=1m`·`email-daily-limit=10`·`ip-limit-per-hour=20`, `password-change.max-failures=5`·`lock-duration=15m`, `social.pending-ttl=10m`·`photo-hosts=lh3.googleusercontent.com,avatars.githubusercontent.com`, `blog.availability.ip-limit-per-minute=30`, `blog.member.nickname-change-interval=30d`·`bio.max-length=200`·`bio.max-lines=4`·`last-active.touch-interval=1h`, `blog.agreement.terms.version/effective-date`·`privacy.version/effective-date`(환경 변수 `BLOG_AGREEMENT_*`), `blog.policy.*=classpath:policy/*.txt`. 주소 3~36자·닉네임 2~10자는 DB CHECK와 같아야 하므로 설정값이 아닌 코드 상수로 둔다(data-model §6 끝). (구현 메모: AccountPropertiesBindingTest로 기본값 확인. 소개 최대 길이는 DB CHECK 때문에 200 이하만 허용)

### 2A-4. 공통 오류 본문

- [X] T017 `T/shared/error/GlobalExceptionHandlerTest.java`를 먼저 작성한다(`@WebMvcTest` + 테스트용 컨트롤러): 모든 오류 응답 JSON에 `code`·`message`·`errors`·`details` 4개 키가 항상 있음(`errors`는 항상 배열, 칸 오류가 없으면 `[]` / `details`는 없으면 null, 메시지 끝 마침표 없음 — 2026-10-07 결정), `NotFoundException`은 메시지·하위 클래스와 무관하게 404 `{code:"NOT_FOUND", message:"볼 수 없는 페이지예요", errors:[], details:null}`로 **본문이 완전히 같음**(constitution III), `ValidationException` → 400 `VALIDATION_FAILED` + `errors[{field,code,message}]`, Bean Validation 실패(`MethodArgumentNotValidException`) → 400 `VALIDATION_FAILED`, 읽을 수 없는 JSON → 400 `MALFORMED_REQUEST`, `AccountStateException` → 403, `BusinessRuleException` → 400/409, `TooManyRequestsException` → 429 + `Retry-After`, `TemporarilyUnavailableException` → 503 + `Retry-After: 30`, `InvalidCursorException` → 400 `INVALID_CURSOR`, 그 밖의 예외 → 500 `INTERNAL_ERROR`(스택 트레이스·예외 메시지 미노출). (구현 메모: 없는 경로 404 본문 동일·LOGIN_REQUIRED 401도 함께 확인)
- [X] T018 `B/shared/error/`를 구현한다 (T017 통과): `ErrorResponse.java`(record `code, message, errors, details`, `errors`는 null 대신 빈 목록, `@JsonInclude(ALWAYS)` — 02 §5-1 O8), `FieldError.java`(record `field, code, message`), `ReasonCode.java`(인터페이스 `code()`·`status()`·`defaultMessage()` — 기능마다 enum으로 구현해 코드를 더한다), `CommonReasonCode.java`(`VALIDATION_FAILED`, `MALFORMED_REQUEST`, `NOT_FOUND`, `LOGIN_REQUIRED`(401 "로그인이 필요해요"), `CSRF_REJECTED`(403, 제안 코드), `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`(403), `INVALID_CURSOR`, `TOO_MANY_REQUESTS`(429 "잠시 후 다시 시도해 주세요"), `TEMPORARILY_UNAVAILABLE`(503 "잠시 후 다시 시도해 주세요"), `INTERNAL_ERROR`), `ApiException.java`(기반: reasonCode·message·errors·details·headers), `NotFoundException.java`(기능별 하위 클래스 허용, 응답은 항상 같은 본문), `AccountStateException.java`, `BusinessRuleException.java`, `ValidationException.java`(칸 오류 목록 + details), `TooManyRequestsException.java`(retryAfterSeconds), `TemporarilyUnavailableException.java`, `GlobalExceptionHandler.java`(`@RestControllerAdvice`). (구현 메모: 제안 코드 METHOD_NOT_ALLOWED(405)·UNSUPPORTED_MEDIA_TYPE(415) 추가, 필터·Security 처리기용 ErrorResponseWriter 추가, 문구 끝 마침표는 ErrorResponse·FieldError가 자동으로 뗌, 컨트롤러 밖으로 나온 AccessDeniedException은 비로그인 401·로그인 404)
- [X] T019 [P] `B/shared/infra/db/UniqueViolations.java`: `DataIntegrityViolationException`에서 PostgreSQL SQLSTATE `23505`와 위반 제약 이름(`uq_auth_identity`, `uq_member_handle`, `uq_member_nickname` 등)을 꺼내는 `Optional<String> constraintName(Throwable)`. 단위 테스트 `T/shared/infra/db/UniqueViolationsTest.java`(PSQLException 모의 객체). 각 기능이 제약 이름 → 칸별 이유 코드 매핑에 쓴다(R-09). (구현 메모: PSQLException을 읽기 위해 postgresql 의존성을 runtime에서 compile 범위로 바꿈. isViolationOf(e, name) 도우미 추가)

### 2A-5. 불투명 커서

- [X] T020 `T/shared/web/cursor/CursorCodecTest.java`를 먼저 작성한다: `{"v":1,"l":"home","k":[1790755200123456,37]}`가 패딩 없는 Base64URL로 인코딩·디코딩 왕복(005 R-24), 요청 목록의 `l`과 다른 커서 → `InvalidCursorException`(400 `INVALID_CURSOR`), Base64 아님·JSON 아님·`v` ≠ 1·`k` 없음·512자 초과 → 400, 기능이 더한 추가 필드(예: 006 `tab`·`vis`)는 그대로 보존, 클라이언트가 해석할 수 없는 값(내부 ID 노출 외 정보 없음).
- [X] T021 `B/shared/web/cursor/`를 구현한다 (T020 통과): `CursorPayload.java`(record `v`, `l`, `k: List<Object>`, `extra: Map<String,Object>`), `ListScope.java`(값 객체: `of(String)`, `home()`, `blog(handle)`, `myFriends()`, `friendRequests()` 등 — 문자열 상수), `CursorCodec.java`(`String encode(ListScope, List<Object> key, Map<String,Object> extra)`, `CursorPayload decode(String cursor, ListScope expected)`, Jackson + `Base64.getUrlEncoder().withoutPadding()`), `InvalidCursorException.java`(extends `ApiException`, `INVALID_CURSOR`). 005·006·007·008·010·012와 이 기능의 친구 목록이 같은 코덱을 쓴다. (구현 메모: CursorCodec은 @Component이면서 new로도 만들 수 있음. CursorPayload에 longAt·stringAt 도우미(정수 키는 Long), 패딩 붙은 커서·객체 키도 400)

### 2A-6. Redis 장애 판정·요청 제한

- [X] T022 [P] `T/shared/infra/ratelimit/RateLimiterIntegrationTest.java`를 먼저 작성한다: 고정 창 `tryAcquire("rl:test:{ip}", limit=3, window=60s)`가 4번째에 `Denied(retryAfterSeconds>0)`, 창이 지나면 다시 허용, `RedisOutage`로 Redis를 멈추면 **예외 없이 허용**(경고 로그 1줄) — 02 §2-1 "요청 제한·중복 방지 카운터 → 통과". `RedisGuard.isAvailable()`이 정지 중 false·복구 후 true.
- [X] T023 `B/shared/infra/redis/RedisGuard.java`(Redis 호출을 감싸 `RedisConnectionFailureException`·`QueryTimeoutException`·`RedisSystemException`을 잡아 `fallback`을 돌려주는 `<T> T call(Supplier<T>, Supplier<T> fallback)`, `boolean isAvailable()`)과 `B/shared/infra/ratelimit/RateLimiter.java`(Lua 스크립트 `INCR` + 첫 증가 때 `PEXPIRE`, 결과 `RateLimitResult.Allowed|Denied(retryAfterSeconds)`, Redis 장애 시 Allowed)를 구현한다 (T022 통과). 001의 모든 "같은 IP/같은 이메일/회원당" 제한과 002·007·009 등의 제한이 이 클래스를 쓴다. (구현 메모: RateLimiter.acquireOrThrow(429 예외)와 RedisGuard.run(결과 없는 호출) 추가. 로그에는 키 종류만 남김(IP·이메일 해시 제외))

### 2A-7. 보안 기반 (세션 쿠키 + CSRF + CurrentUser + 보안 헤더)

- [X] T024 `T/shared/security/integration/SecurityFoundationIntegrationTest.java`를 먼저 작성한다: ① `GET /api/auth/csrf` → 204 + `XSRF-TOKEN` 쿠키(HttpOnly 아님, `SameSite=Lax`, `Secure`, Path `/`) (R-05) ② `X-XSRF-TOKEN` 없는/다른 POST → 403 `CSRF_REJECTED` 공통 오류 본문 ③ 비로그인으로 `/api/me/**`(테스트 컨트롤러 `GET /api/me/__probe`) 요청 → 401 `LOGIN_REQUIRED` JSON(리다이렉트 아님) ④ `TestLogin`으로 로그인한 세션에서 `@CurrentUser`가 그 회원 번호를 돌려주고 요청 본문의 `memberId`는 무시 ⑤ 세션 쿠키 이름 `SESSION`, `HttpOnly`·`Secure`·`SameSite=Lax`, Redis 세션 TTL 14일(`blog.auth.session-timeout`) ⑥ 모든 응답(API·HTML)에 12 §8 헤더: `Content-Security-Policy: default-src 'self'; script-src 'self'; connect-src 'self' {public-base-url 출처}; img-src 'self' {public-base-url 출처} data: blob:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` ⑦ `GET /settings`(확장자 없는 화면 경로) → 200 `index.html`, `GET /api/없는경로` → 404 공통 본문(SPA로 넘기지 않음). (구현 메모: 주체 이름 색인 값은 JDK 직렬화라 Redis 키를 직접 읽지 않고 FindByIndexNameSessionRepository.findByPrincipalName으로 확인)
- [X] T025 [P] `T/shared/security/integration/SessionResilienceIntegrationTest.java`를 먼저 작성한다(FR-040, 02 §2-1, H8): 로그인 세션을 만든 뒤 `RedisOutage`로 Redis 정지 → 같은 쿠키로 공개 GET(테스트 컨트롤러 `GET /api/__public-probe`) 200, `/api/me/__probe` 401 `LOGIN_REQUIRED`(500 아님), 응답이 `blog.redis` 타임아웃(500ms)+α 안에 옴 → Redis 복구 후 같은 쿠키로 다시 로그인 상태.
- [X] T026 `B/shared/security/SecurityConfig.java`를 구현한다: `SecurityFilterChain` — CSRF `CookieCsrfTokenRepository.withHttpOnlyFalse()`(쿠키 `SameSite=Lax`·`Secure`) + `SpaCsrfTokenRequestHandler`(Spring Security 6 SPA 패턴, BREACH 대응 XOR) + `CsrfCookieFilter`(토큰 쿠키를 매 응답에 실음) (R-05, README "CSRF"), 세션 고정 방지 `changeSessionId`, 폼 로그인·HTTP Basic 기본값 끔, `exceptionHandling`에 `LoginRequiredEntryPoint`(401 `LOGIN_REQUIRED` JSON)·`CsrfAccessDeniedHandler`(403 `CSRF_REJECTED`), 인가: `/api/me/**` authenticated, 나머지는 `permitAll`(로그인 필요 여부는 `@LoginRequired`·Service가 판단 — 42 §3 ①). 확장 지점 `B/shared/security/SecurityFilterChainCustomizer.java`(`void customize(HttpSecurity)`, `@Order` 순서로 모두 적용 — account의 폼·소셜 로그인·필터(US1·US2·US5·US8), 004의 `/admin/**` 규칙이 여기에 붙는다). 파일: `SecurityConfig.java`, `SecurityFilterChainCustomizer.java`, `SpaCsrfTokenRequestHandler.java`, `CsrfCookieFilter.java`, `LoginRequiredEntryPoint.java`, `CsrfAccessDeniedHandler.java`, 비밀번호 인코더 Bean(`BCryptPasswordEncoder` 비용 10, R-14). (구현 메모: CsrfCookieFilter는 BasicAuthenticationFilter 뒤에 둠. 확장 지점 Bean들을 @Order 순서로 적용한 뒤 /api/me/** authenticated·나머지 permitAll 규칙을 마지막에 붙임)
- [X] T027 `B/shared/security/`에 현재 사용자 장치를 구현한다: `MemberPrincipal.java`(record `memberId`, `role`; `getName()` = `String.valueOf(memberId)` — 인덱스 세션 저장소의 principal 이름, R-03), `CurrentUser.java`(컨트롤러 파라미터 애너테이션 `@CurrentUser Long memberId` / `@CurrentUser(required=false) Optional<Long>`), `CurrentUserArgumentResolver.java`(SecurityContext에서만 꺼냄, 필수인데 비로그인이면 401 `LOGIN_REQUIRED`), `LoginRequired.java` + `LoginRequiredInterceptor.java`(애너테이션이 붙은 핸들러를 비로그인이 부르면 401), `WebSecurityMvcConfig.java`(resolver·interceptor 등록). 작성자 ID를 요청 파라미터로 받지 않는다(constitution III). 004의 `Viewer`는 이 값과 T039 `findAccessInfo`로 만든다.
- [X] T028 `B/shared/security/session/`을 구현한다 (T025 통과): `SessionConfig.java`(Spring Session Data Redis **인덱스 저장소** `spring.session.redis.repository-type=indexed` 상당, `maxInactiveInterval` = `blog.auth.session-timeout`(14d), `DefaultCookieSerializer` 이름 `SESSION`·`HttpOnly`·`Secure`·`SameSite=Lax`·Path `/`, `server.servlet.session.cookie.*`와 일치 — R-03, 07 L-6), `ResilientSessionRepository.java`(`FindByIndexNameSessionRepository` 위임 래퍼: `findById`에서 Redis 연결 예외 → `null`(비로그인 처리) + 경고 로그, `save`에서 예외 → 익명 요청이면 무시·로그인 처리 중이면 `TemporarilyUnavailableException`, `findByIndexNameAndIndexValue`는 예외를 그대로 올림 — R-30, README "세션"). `SessionRepositoryFilter`가 래퍼를 쓰도록 Bean을 등록한다. (구현 메모: @EnableRedisIndexedHttpSession 사용. 이 설정은 시작 때 Redis CONFIG SET notify-keyspace-events를 부르므로 CONFIG가 막힌 관리형 Redis라면 미리 Egx를 켜고 ConfigureRedisAction.NO_OP로 바꿔야 함)
- [X] T029 `B/shared/web/SecurityHeadersFilter.java`와 `B/shared/web/CspContributor.java`를 구현한다 (T024 ⑥ 통과): 모든 응답에 12 §8 헤더 3종을 넣고, `{저장소 공개 주소}`는 `blog.image.public-base-url`의 출처(스킴+호스트+포트)로 계산한다. `CspContributor`(인터페이스: `boolean appliesTo(HttpServletRequest)`, `List<String> extraImgSrc()`) Bean이 맞는 요청에만 `img-src` 출처를 더한다 — 소셜 가입 마무리 화면 예외(T082)가 이 지점을 쓴다. 002 plan의 `SecurityHeadersConfig`는 이 필터를 가리킨다. (구현 메모: 필터는 @Component(@Order HIGHEST_PRECEDENCE) 서블릿 필터라 @WebMvcTest 슬라이스에도 잡힘 — CoreProperties가 없는 슬라이스 테스트는 excludeFilters로 빼야 함(GlobalExceptionHandlerTest 참고))
- [X] T030 [P] 클라이언트 IP를 설정한다(FR-037, R-13, H4): `R/application.yml`에 `server.forward-headers-strategy=native`, `server.tomcat.remoteip.internal-proxies`(기본 = 사설 대역 정규식 `10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+|127\.\d+\.\d+\.\d+`, 운영 NHN LB 대역은 배포 담당 확인 — 환경 변수 `BLOG_TRUSTED_PROXIES`), `remote-ip-header=x-forwarded-for`, `protocol-header=x-forwarded-proto`. `B/shared/web/ClientIp.java`(`static String of(HttpServletRequest)` = RemoteIpValve가 정한 `getRemoteAddr()`; 직접 헤더를 파싱하지 않음). 모든 "같은 IP" 제한 키는 이 값을 쓴다.
- [X] T031 [P] `B/shared/web/SpaForwardingController.java`: `/api/**`, `/oauth2/**`, `/login/oauth2/**`, `/actuator/**`, 정적 파일(확장자 있는 경로)을 제외한 화면 경로 GET을 `forward:/index.html`로 넘긴다(React 빌드 서빙, T024 ⑦). 005의 `PageShellController`(`/@{handle}`, `/@{handle}/posts/{postId}`)가 더 구체적인 매핑으로 우선한다는 점을 주석에 적는다. (구현 메모: 경로 정규식 매핑(5단계까지)과 메서드 안 접두사 검사를 같이 둠. /oauth2·/actuator 등 제외 경로와 확장자 있는 경로는 404 공통 본문)
- [X] T032 `B/account/web/AuthController.java`를 만들고 `GET /api/auth/csrf`(204, 토큰 쿠키 발급 — 재동의 전에도 허용)만 둔다(contracts/openapi.yaml `issueCsrfToken`). 나머지 인증 경로는 US1·US4에서 더한다. (T026 후)

### 2A-8. 계정 공개 Service (다른 기능이 호출)

- [X] T033 `T/account/integration/AccountStatusGuardIntegrationTest.java`를 먼저 작성한다(42 §3 ②, H7, R-22, data-model §4-1 표, US1 #3·US5 #5): `MemberFixtures`로 만든 4가지 상태(ACTIVE+인증 전 / ACTIVE+인증 / SUSPENDED / WITHDRAWN) × `ActionKind`(`CONTENT_WRITE`, `ACCOUNT_WRITE`, `CONTENT_CLEANUP`)에서 `requireActive` 결과가 표와 같음 — 인증 전+CONTENT_WRITE → 403 `EMAIL_NOT_VERIFIED`, 인증 전+ACCOUNT_WRITE·CONTENT_CLEANUP → 통과, SUSPENDED → 403 `ACCOUNT_SUSPENDED`, WITHDRAWN → 403 `ACCOUNT_WITHDRAWN`, 판정 우선순위 탈퇴 유예 → 정지 → 인증 전. 호출 사이에 DB의 `member.status`·`email_verified_at`을 바꾸면 **다음 호출에 바로 반영**(세션 값 미사용). 없는 회원·`deleted_at` 있는 회원 → 401 `LOGIN_REQUIRED`. 판정은 SQL 1번. (구현 메모: SQL 1번은 테스트 도구 SqlCounter(DataSource 감싸기)로 확인)
- [X] T034 [P] `T/account/integration/SessionTerminatorIntegrationTest.java`를 먼저 작성한다(R-03, FR-044·045, 42 P-7): 같은 회원으로 세션 3개 + 다른 회원 세션 1개 → `terminateAll(memberId, Optional.empty())` 후 그 회원 세션 3개 모두 401, 다른 회원은 유지. `terminateAll(memberId, Optional.of(currentSessionId))`는 현재 세션만 남김. Redis 정지 중 호출 → `TemporarilyUnavailableException`.
- [X] T035 [P] `T/account/integration/MemberQueryServiceIntegrationTest.java`를 먼저 작성한다: `findAccessInfo(memberId)` → `{role, status, emailVerified}`(004 Viewer용), `findReadableBlogOwner("kim755030")` → `{id, handle, nickname, bio}`, 대문자 입력은 `normalizeHandle`로 소문자 비교, `WITHDRAWN`·`deleted_at` 회원·없는 주소 → `Optional.empty()`(005 404, FR-021), `defaultVisibility(memberId)` → `"PUBLIC"`(002 새 글 기본값). (구현 메모: 005 T048(대문자 handle은 그대로 조회해 없음 처리)과 맞추려고 findReadableBlogOwner는 저장값과 정확히 같은 주소만 찾고, 대문자 입력은 normalizeHandle로 바꾼 뒤 찾으면 나온다는 것을 확인함)
- [X] T036 계정 기본 엔티티를 만든다(data-model §2-1·§2-2, 51 §2): `B/account/domain/Member.java`(14개 컬럼 매핑: `id` IDENTITY, `handle`(변경 메서드 없음 — FR-021), `nickname`, `nicknameChangedAt`, `bio`, `role`, `status`, `defaultVisibility`(**String** 컬럼 값 `PUBLIC`/`PRIVATE` — enum은 004 `post.domain.Visibility`가 소유, Cross-feature Dependencies), `createdAt`, `updatedAt`, `withdrawnAt`, `lastActiveAt`, `lastActiveVisible`, `deletedAt`), `MemberStatus.java`(ACTIVE·SUSPENDED·WITHDRAWN), `Role.java`(USER·ADMIN), `B/account/domain/AuthIdentity.java`(9개 컬럼: `memberId`, `provider`, `providerUserId`, `email`, `passwordHash`, `emailVerifiedAt`, `createdAt`, `lastLoginAt`), `Provider.java`(LOCAL·GOOGLE·GITHUB), `B/account/infra/MemberRepository.java`(`findByIdForUpdate` — `@Lock(PESSIMISTIC_WRITE)`, `existsByHandle`, `findByHandle`), `B/account/infra/AuthIdentityRepository.java`(`findByProviderAndProviderUserId`, `findByMemberId`, `findAllByEmail`). 컬럼 규칙은 DB CHECK가 2중으로 지킨다(`ck_member_handle`, `ck_member_nickname`, `ck_auth_local_email`, `ck_auth_password`). (구현 메모: 생성 메서드 Member.join·AuthIdentity.local/social을 둠(가입 흐름은 US1). 매핑·조회 확인용 AccountEntityMappingIntegrationTest 추가)
- [X] T037 계정 상태 가드를 구현한다 (T033 통과): 포트 `B/shared/security/AccountStatusGuard.java`(`void requireActive(long memberId, ActionKind kind)`)·`B/shared/security/ActionKind.java`(`CONTENT_WRITE` = 글쓰기(새 글·저장·발행·공개 범위 변경)·댓글·사진 업로드·좋아요·신고 / `ACCOUNT_WRITE` = 닉네임·소개, 비밀번호 변경, 기본 공개 범위, 탈퇴, 친구 요청 / `CONTENT_CLEANUP` = 자기 글·댓글 삭제·복구·영구 삭제와 내 글 관리 목록(006·007) — 인증 전도 통과, 정지·탈퇴 유예는 거부 — FR-007, 42 §9), 구현 `B/account/application/AccountStatusGuardService.java`(`member` JOIN `auth_identity` 1번 조회로 `status`·`email_verified_at`을 **매번 DB에서** 읽음, 우선순위 WITHDRAWN → SUSPENDED → 인증 전). 002·003·006·007·009·014는 쓰기 Service 첫머리에서 이 포트를 호출한다(R-22). 쓰기가 아닌 요청의 탈퇴 유예 차단은 T042a 필터가 맡는다. (구현 메모: WITHDRAWN 거부에는 004 contracts에 맞춰 details {action: RESTORE}를 넣음. 없는 회원·익명 처리 회원은 401 LOGIN_REQUIRED(ApiException). JdbcClient로 member LEFT JOIN auth_identity 1번)
- [X] T038 `B/account/application/SessionTerminator.java`를 구현한다 (T034 통과): `int terminateAll(long memberId, Optional<String> exceptSessionId)` — `FindByIndexNameSessionRepository.findByPrincipalName(String.valueOf(memberId))`로 세션을 찾아 `deleteById`, Redis 장애 시 `TemporarilyUnavailableException`. 비밀번호 재설정·변경(US4), 014 정지, 015 탈퇴가 호출한다(004 plan의 `MemberSessionService`는 이 클래스를 가리킨다).
- [X] T039 `B/account/application/MemberQueryService.java`를 구현한다 (T035 통과): `Optional<MemberAccessInfo> findAccessInfo(long)`, `Optional<BlogOwner> findReadableBlogOwner(String handle)`(`status <> 'WITHDRAWN' AND deleted_at IS NULL`), `String defaultVisibility(long)`, `static String normalizeHandle(String)`(소문자, 005 301 판단용 — FR-021), 레코드 `B/account/application/MemberAccessInfo.java`·`BlogOwner.java`. 읽기 전용 트랜잭션. (구현 메모: findAccessInfo는 익명 처리(deleted_at) 회원도 빈 값(비로그인 취급). defaultVisibility는 없는 회원이면 IllegalArgumentException)
- [X] T040 [P] `B/media/application/ImageUrlResolver.java`: `String publicUrl(String storageKey)` = `blog.image.public-base-url` + `/` + key(중복 `/` 정리). 공개 버킷 직접 주소이므로 임시 구현이 아니라 최종 규칙이며 003이 그대로 소유한다. 단위 테스트 `T/media/application/ImageUrlResolverTest.java`. 같은 작업에서 읽기 전용 `B/media/application/ProfileImageQuery.java`(`Optional<ProfileImageKeys> currentKeys(long memberId)`, `Map<Long,ProfileImageKeys> currentKeysOf(Collection<Long>)` — `uq_image_profile_current` 조건 `purpose='PROFILE' AND status='ATTACHED' AND detached_at IS NULL` 조회 1번)와 레코드 `B/media/application/ProfileImageKeys.java`(`original` = `storage_key`, `thumbnail` = `thumb_storage_key`(없으면 null), `display()` = `thumbnail != null ? thumbnail : original`)도 만든다(003이 소유를 넘겨받음). 작은 사진(`/api/me`·친구 목록·카드·블로그 머리말)은 `display()`, og:image는 `original()`을 쓴다. (001 `GET /api/me`·친구 목록·프로필, 005 카드·블로그 머리말·og:image가 사용 — US6 T116을 기다리지 않게 하기 위함) (구현 메모: publicUrl(null)은 null(썸네일 없음 처리용), 빈 키는 IllegalArgumentException. 설정 없이 쓰는 ImageUrlResolver.of(base) 제공. ProfileImageQueryIntegrationTest 추가)

### 2A-9. Frontend 공통 API 클라이언트

- [X] T041 [P] `F/api/client.test.ts`를 먼저 작성한다(Vitest): POST·PUT·PATCH·DELETE에만 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 붙임, `credentials: 'same-origin'`, 오류 응답 본문 `{code,message,errors,details}`를 `ApiError`(status·code·message·errors·details·retryAfter)로 던짐, 본문이 JSON이 아닌 오류도 `ApiError(code='UNKNOWN')`, `ensureCsrf()`는 앱 시작 때 `GET /api/auth/csrf`를 한 번만 부름, 401 응답 때 등록된 `onUnauthorized` 콜백 호출.
- [X] T042 `F/api/client.ts`를 구현하고 `F/main.tsx`에서 렌더 전에 `ensureCsrf()`를 호출한다 (T041 통과): `apiGet/apiPost/apiPut/apiPatch/apiDelete<T>()`, `ApiError` 클래스, 로그인 후 토큰이 바뀌므로 매 요청마다 쿠키를 다시 읽음(quickstart §2). 화면별 403 코드 안내(`useAuthGate`)는 004가 이 클라이언트 위에 만든다. (구현 메모: onUnauthorized(cb)는 해제 함수를 돌려줌. 요청 옵션 {headers, signal}로 Idempotency-Key 같은 헤더를 더할 수 있음. ensureCsrf 실패 시 다음 호출에서 다시 시도하고 main.tsx는 실패해도 렌더함. 테스트용 resetClientForTests 제공)

### 2A-10. 탈퇴 유예 회원 요청 차단 (spec 004 FR-031, specs/004 research R-23)

- [X] T042a `B/account/infra/security/WithdrawnAccountGateFilter.java`와 등록용 `B/account/infra/security/WithdrawnAccountGateCustomizer.java`(T026의 `SecurityFilterChainCustomizer` 구현)를 만들고 테스트 `T/account/integration/WithdrawnAccountGateIntegrationTest.java`를 먼저 작성한다: 로그인 세션의 `/api/**` 요청마다 T039 `MemberQueryService.findAccessInfo(memberId)`(PK 1번, 결과를 request attribute에 두어 004 T011 `CurrentViewerResolver`가 다시 조회하지 않게 함)로 `status = WITHDRAWN`이면 허용 목록(`POST /api/me/restore`(015), `POST /api/auth/logout`, `GET /api/me`, `GET /api/auth/csrf`)을 뺀 모든 요청(GET 포함)을 403 `ACCOUNT_WITHDRAWN` `details{action: RESTORE}`로 막는다. 테스트: 탈퇴 유예 세션의 `GET /api/posts/{id}`·`GET /api/me/settings`·`POST /api/posts` → 403, 허용 목록 4개 → 통과, ACTIVE·SUSPENDED·비로그인 요청은 영향 없음, DB에서 `status`를 바꾸면 다음 요청에 바로 반영. 쓰기 요청의 상태 판정(인증 전·정지)은 T037 `AccountStatusGuard`가 그대로 맡는다. 004 T049 `AccountStateIT`의 탈퇴 유예 항목이 이 필터에 기댄다 (T026·T039 다음). (구현 메모: 아직 없는 허용 경로(015 restore, US1 logout·GET /api/me)는 필터를 통과해 404가 되는지까지만 확인. 로그인했지만 익명 처리(deleted_at)된 회원은 attribute 없이 통과시킴)

**Checkpoint 2A**: 공통 기반 완료 — 002·004·005·006은 여기서 시작할 수 있다. `./mvnw verify`에서 T010·T017·T020·T022·T024·T025·T033·T034·T035·T042a 통과.

### 2B. 계정 검증 정책 (US1·US2·US3·US4·US6 공용, 순수 단위 테스트)

- [X] T043 [P] `T/account/application/policy/BannedWordFilterTest.java`를 먼저 작성한다(FR-025, 09 §4, N-5, R-17): 소문자화 후 ① 그대로 ② 숫자 제거 ③ 숫자→영문 치환(0→o, 1→i, 3→e, 4→a, 5→s, 7→t) ④ 1→l 네 변형 중 하나라도 금칙어 포함이면 거부, 각 변형에서 예외 단어(`시발점`, `시발역`)를 먼저 지움 — `시1발`·`sh1t` 거부, `시발점` 허용(09 #4·#7). 결과 객체에 걸린 단어가 노출되지 않음(boolean만). 테스트용 목록은 `backend/src/test/resources/policy/*.txt`. (구현 메모: 운영 목록을 가리지 않도록 테스트 목록 이름은 `policy/test-banned-words.txt`·`test-banned-words-exceptions.txt`)
- [X] T044 [P] `T/account/application/policy/HandlePolicyTest.java`·`HandleSuggesterTest.java`를 먼저 작성한다(FR-016·017·019, 08 §2·§3·§5, R-15, SC-007): 형식 `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$`(DB `ck_member_handle`와 같음), 접두어 ≠ 가입 수단 → `HANDLE_PREFIX_MISMATCH`, 접두어를 뺀 본문이 예약어 → `HANDLE_RESERVED`(`admin`, `go-admin`), 금칙어 → `HANDLE_BANNED_WORD`, 형식 → `HANDLE_INVALID_FORMAT`(`Kim755030`, `kim-min`, `ab`). Suggester: 08 §3 예시 12개 전부를 파라미터화 테스트로(예: `Kim.Min-Seo+blog@naver.com` → `kim_min_seo`, `kim755030@gmail.com`+GOOGLE → `go-kim755030`), 단계 ①~⑩, 3자 미만 → `user_` + 6자리 난수(정규식으로 검사), 예약어·중복 → `_2`, `_3` … 비어 있는 첫 번호(⑩, 조회는 `HandleLookup` 함수형 인터페이스 모의), 39자 넘침 시 본문 끝 자르기(끝 `_` 제거). (구현 메모: validate는 `List<AccountReasonCode>`를 돌려줌 — 형식 오류면 그것 하나, 아니면 접두어·예약어·금칙어를 모두. HandleLookup은 `takenWithBase(base)`(= base 또는 `base_…`) 한 번. 넘침 기준은 전체 39자가 아니라 본문 36자(이메일 가입은 접두어가 없어 전체 36자))
- [X] T045 [P] `T/account/application/policy/NicknamePolicyTest.java`를 먼저 작성한다(FR-022~026, 09 §2~§7, SC-007): 검사 순서 정리 → 형식 → 글자 포함 → 예약어 → 금칙어 → 중복과 단계별 코드·문구(`NICKNAME_INVALID_FORMAT` "한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)", `NICKNAME_LETTER_REQUIRED` "한글이나 영문을 1자 이상 넣어 주세요", `NICKNAME_RESERVED` "사용할 수 없는 닉네임이에요", `NICKNAME_BANNED_WORD` "사용할 수 없는 단어가 들어 있어요", `NICKNAME_DUPLICATE` "이미 사용 중인 닉네임이에요"). 예시: `ㅋㅋ`·`김 민서`·`12345`·`관리자김`(예약어 **포함**)·`admin123`·`시1발`·`sh1t` 거부, `시발점` 허용, NFD로 분리된 `김민서` → NFC `김민서`로 통과(09 #2), 중복은 `NicknameLookup`(lower, 자기 자신 제외) 모의로 `Kim` 있을 때 `kim` 거부. 소셜 이름 미리 채우기 `suggestFromSocialName("A")` → null, 허용되지 않는 문자 제거·10자 자르기(FR-027).
- [X] T046 [P] `T/account/application/policy/PasswordPolicyTest.java`를 먼저 작성한다(FR-013, R-14, US1 #7): 8~16자(`PASSWORD_INVALID_LENGTH`, 17자 `Abcdefg1!Abcdefg1` 거부), 영문 대·소문자·숫자·특수문자 각 1개 이상(`PASSWORD_MISSING_CHAR_TYPE`, `abc12345` 거부), 허용 문자 밖(공백·한글) `PASSWORD_INVALID_CHAR`, 이메일 `+` 앞 지역부(3자 이상일 때만, 소문자 비교) 포함 `PASSWORD_CONTAINS_EMAIL`(`Kim755030!x` for `kim755030@naver.com`), 흔한 목록(대소문자 무시 완전 일치) `PASSWORD_TOO_COMMON`(`Password1!`, `Qwer1234!`), 확인 불일치 `PASSWORD_CONFIRM_MISMATCH`. 규칙별 결과 목록을 돌려줘 화면 ✓ 표시와 1:1(FR-014).
- [X] T047 정책 목록 파일과 로더를 만든다: `R/policy/reserved-handles.txt`(08 §5/FR-019 목록: `admin administrator root system official support help about terms privacy api login logout signup settings me write search tag tags notifications manage static assets images mail www blog user users null undefined devlog teamblog`, 서비스 이름은 정해지면 추가 — README "정해진 것"), `R/policy/reserved-nicknames.txt`(FR-024: `관리자 운영자 운영진 운영팀 고객센터 공식 매니저 스태프 admin administrator official staff manager system root`), `R/policy/banned-words.txt`·`banned-words-exceptions.txt`(팀 검토 전 임시 최소 목록 + "공개 비속어 목록을 팀이 검토해 교체" 주석, spec Assumptions), `R/policy/common-passwords.txt`(우리 규칙을 통과하는 흔한 비밀번호 — 최소 `Password1!`, `Qwer1234!`), `B/account/application/policy/ReservedWords.java`(`blog.policy.*` 경로에서 읽어 소문자 Set, 한 줄 한 단어, `#` 주석). (구현 메모: AccountReasonCode를 2B에서 먼저 만들었다(T061의 코드 목록 전체). 금칙어 목록에 TODO(팀 검토) 주석)
- [X] T048 `B/account/application/policy/BannedWordFilter.java`를 구현한다 (T043 통과): `boolean containsBanned(String)` — 변형 4가지 + 예외 단어 선제거, 걸린 단어를 반환하거나 로그에 남기지 않는다. 닉네임·블로그 주소·소개가 공용으로 쓴다(FR-025). (구현 메모: 변형 4가지 검사를 VariantMatcher로 분리해 닉네임 예약어 검사(09 §5)도 같은 규칙을 씀. 긴 예외 단어부터 지움)
- [X] T049 `B/account/application/policy/HandlePolicy.java`(`List<String> validate(String handle, Provider provider)` → 이유 코드)와 `HandleSuggester.java`(`String suggest(String email, Provider provider)` ①~⑩, `String nextAvailable(String base)` — `HandleLookup`으로 `handle LIKE '{base}\_%'` 한 번 조회해 비어 있는 첫 번호)를 구현한다 (T044 통과). 운영 구현 `B/account/infra/JdbcHandleLookup.java`는 `uq_member_handle` 인덱스를 쓴다. (구현 메모: Provider에 handlePrefix() 추가. 정책 Bean은 AccountPolicyConfig에서 만듦. JdbcHandleLookup은 `handle = ? OR handle LIKE '{base}\_%' ESCAPE`)
- [X] T050 `B/account/application/policy/NicknamePolicy.java`를 구현한다 (T045 통과): `NicknameCheck check(String raw, Long selfMemberId)` → 정규화 값(trim + `Normalizer.Form.NFC`) + 첫 실패 코드, `String suggestFromSocialName(String)`. 운영 `B/account/infra/JdbcNicknameLookup.java`(`lower(nickname) = lower(?) AND id <> ?`). 가입(이메일·소셜)·프로필 수정 공용(FR-023). (구현 메모: NicknameLookup.existsIgnoreCase(nickname, excludeMemberId), NicknameCheck(normalized, failure). suggestFromSocialName은 중복까지 포함한 check 전체를 통과해야 값을 줌)
- [X] T051 `B/account/application/policy/PasswordPolicy.java`를 구현한다 (T046 통과): `List<PasswordRuleResult> evaluate(String password, String confirm, String email)`·`List<FieldError> violations(...)`. 가입·재설정·변경 공용. 비밀번호 원문을 로그·예외 메시지에 넣지 않는다(FR-015). (구현 메모: PasswordRule 순서 = 화면 체크리스트 5개(LENGTH·LETTER_CASE·DIGIT·SPECIAL·ALLOWED_CHARS) + 서버 전용 3개(NOT_EMAIL·NOT_COMMON·CONFIRM). 칸 이름을 바꾸는 violations 오버로드 추가(US4 변경용))

**Checkpoint 2B**: 정책 단위 테스트(T043~T046)가 원문 예시와 100% 일치(SC-007). 001 User Story를 시작할 수 있다.

---

## Phase 3: User Story 1 - 이메일로 가입하고 인증한 뒤 로그인·로그아웃한다 (Priority: P1) 🎯 MVP

**Goal**: 이메일 가입(인증 전 상태 + 약관 2개 버전 기록) → 24시간 인증 링크 → 로그인 14일 유지 → 로그아웃(서버 세션 + 브라우저 임시 글 삭제). (C-AUTH-1)

**Independent Test**: 새 이메일로 가입 → 인증 전 쓰기 시도 거부(`AccountStatusGuard`, 003 presign 있으면 403) → 인증 링크 확인 → 허용 → 로그아웃 → `/api/me` 401 (quickstart §4-1).

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T052 [P] [US1] `T/account/integration/EmailSignupIntegrationTest.java`: #1 `POST /api/auth/signup`(이메일 `" Kim755030@Naver.com "`) → 201 `{handle, nickname, emailVerified:false}`, `auth_identity.provider_user_id = email = 'kim755030@naver.com'`, `member_agreement` TERMS·PRIVACY 2행(설정 버전, `agreed_at`), `CapturingMailSender`에 인증 메일 1통(커밋 후), 응답 세션으로 `GET /api/me` 200 · #2 같은 이메일(대소문자·공백만 다름) 재가입 → 400 `VALIDATION_FAILED` `errors[email].code = EMAIL_ALREADY_REGISTERED`("이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]"), 계정 수 그대로 · #7 비밀번호 규칙 위반 각각 → 400 해당 `PASSWORD_*`, 아무 행도 생기지 않음 · 약관 버전이 현재와 다름 → `AGREEMENT_VERSION_MISMATCH`, 동의 누락 → `AGREEMENT_REQUIRED`(FR-010) · 한 요청의 여러 칸 오류를 모두 돌려줌 · 이메일 255자 → `EMAIL_INVALID_FORMAT`(R-10).
- [X] T053 [P] [US1] `T/account/integration/EmailVerificationIntegrationTest.java`: 메일 토큰으로 `POST /api/auth/email-verification/confirm` → 200 `{verified:true}`, `email_verified_at` 기록 → 같은 토큰 재사용 400 `LINK_EXPIRED`(SC-006) · #4 24시간 지난 토큰(Redis TTL 조작) → 400 `LINK_EXPIRED` · #5 재발송 1분 안 두 번째 → 429 `TOO_MANY_REQUESTS` + `Retry-After`, 하루 11번째 → 429, 새 메일 발송 후 이전 토큰 → 400(`auth:verify-latest`) · 같은 토큰 동시 2건 → 성공 1건(GETDEL) · 이미 인증 → 409 `ALREADY_VERIFIED` · 비로그인 재발송 → 401 · Redis 정지 중 confirm·재발송 → 503 `TEMPORARILY_UNAVAILABLE`(FR-040). (구현 메모: 세션이 Redis에 있으므로 Redis 정지 중 재발송 HTTP 요청은 세션을 읽지 못해 401이 된다 — HTTP 테스트는 401을, 503 경로는 세션을 읽은 뒤 Redis가 끊긴 경우로 서비스 직접 호출 테스트로 확인)
- [X] T054 [P] [US1] `T/account/integration/EmailLoginLogoutIntegrationTest.java`: 폼 `POST /api/auth/login`(email, password, CSRF) → 200 `LoginResult{redirectTo:"/", reagreementRequired:false, accountStatus:"ACTIVE"}`, 로그인 전후 세션 ID가 다름(세션 고정 방지), `auth_identity.last_login_at` 갱신·세션 속성 `previousLoginAt`(갱신 전 값)·`provider` 저장(FR-057), 이메일 대소문자·공백 무시 · 틀린 비밀번호 → 401 `INVALID_CREDENTIALS` · #6 `POST /api/auth/logout` → 204, 같은 쿠키로 `GET /api/me` 401 `LOGIN_REQUIRED`, 이미 로그아웃이어도 204 · 로그인 상태 유지 14일(세션 TTL) 확인.
- [ ] T055 [P] [US1] Frontend 테스트: `F/components/PasswordRuleChecklist.test.tsx`(규칙 5개 — 8~16자(최대 16자 명시)·영문 대소문자·숫자·특수문자·허용 문자 — 가 입력마다 글자 + ✓/✗ 텍스트로 바뀜, 색만으로 표시하지 않음 — FR-014, US1 #7), `F/features/auth/logout.test.ts`(로그아웃 시 002 `clearMemberDrafts(memberId)` 호출 → `POST /api/auth/logout` → `location.assign('/')`, 테마 키는 지우지 않음 — FR-041).

### Implementation for User Story 1

- [X] T056 [P] [US1] `B/account/domain/MemberAgreement.java`(복합 PK `(memberId, type)`, `version` varchar(20) 빈 값 금지, `agreedAt`), `AgreementType.java`(TERMS·PRIVACY·AI — AI는 013), `B/account/infra/MemberAgreementRepository.java`(재동의용 `upsert` 네이티브 `INSERT … ON CONFLICT (member_id, type) DO UPDATE SET version, agreed_at`) (data-model §2-3).
- [X] T057 [US1] `B/account/application/AgreementService.java`(현재 버전·시행일 = `blog.agreement.*`, `recordOnSignup(memberId, consent)` — 보낸 버전 = 현재 버전 검사 후 TERMS·PRIVACY 2행 INSERT, `needsReagreement(memberId)` → 해당 종류 목록)와 `B/account/web/AgreementController.java`(`GET /api/agreements/current` → `{terms:{version,effectiveDate,path:"/terms"}, privacy:{…}}`, 비로그인 허용 — FR-011)를 구현한다. (구현 메모: `recordOnSignup`은 버전 검사를 `consentErrors`로 분리해 SignupService가 다른 칸 오류와 함께 모은다. MANDATORY 트랜잭션)
- [X] T058 [P] [US1] `B/account/infra/redis/AuthTokenStore.java`를 구현한다(R-11, data-model §3): `issue(TokenType, memberId)` — 32바이트 `SecureRandom` Base64URL(패딩 없음), `auth:verify:{token}`(TTL `verify.token-ttl`)·`auth:reset:{token}`(TTL `reset.token-ttl`), 최신 포인터 `auth:verify-latest:{memberId}`/`auth:reset-latest:{memberId}`가 가리키던 이전 토큰 키 삭제, `consume(TokenType, token)` — `GETDEL` 원자 처리 후 최신 포인터와 같을 때만 memberId 반환, Redis 장애 시 `TemporarilyUnavailableException`. 토큰 값은 로그에 남기지 않는다(FR-015). (구현 메모: 형식이 43자 Base64URL이 아닌 토큰은 Redis 조회 없이 실패)
- [X] T059 [US1] 메일 발송을 구현한다(R-29, FR-009, contracts/events.md §2): `B/account/application/mail/AccountMailService.java`(`@Async("mailExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`로 모듈 내부 이벤트 `VerificationMailRequested(memberId)`를 받아 **발송 시점에** `AuthTokenStore.issue` → 메일, 실패는 경고 로그만), `B/account/application/mail/MailTemplates.java`, 템플릿 `R/mail/verify-email.txt`(링크 `{baseUrl}/verify-email?token=…`, 24시간 안내). SMTP는 `spring.mail.*`(개발 Mailpit, 운영은 배포 때 설정값). (구현 메모: 발신 주소·링크 기준 주소는 새 설정 `blog.mail.from`·`blog.mail.link-base-url`(.env.example에 `BLOG_MAIL_FROM`·`BLOG_MAIL_LINK_BASE_URL`). `fallbackExecution=true`로 트랜잭션 밖 발행도 발송)
- [X] T060 [US1] `B/account/application/SignupService.java`의 `signupWithEmail(EmailSignupCommand)`를 구현한다(FR-002~005·010·013·016·019·022~026, R-09): 이메일 trim·소문자·형식(R-10) → `HandlePolicy.validate(handle, LOCAL)` → `PasswordPolicy` → `NicknamePolicy` → 동의 버전을 **모두** 검사해 칸 오류를 모아 `ValidationException` → 한 트랜잭션에서 `member`(nickname NFC 값, `nickname_changed_at` NULL) → `auth_identity`(LOCAL, BCrypt) → `member_agreement` 2행 → `VerificationMailRequested` 발행. `uq_auth_identity` 23505 → `email`/`EMAIL_ALREADY_REGISTERED`, `uq_member_handle` → `handle`/`HANDLE_DUPLICATE`, `uq_member_nickname` → `nickname`/`NICKNAME_DUPLICATE` (`UniqueViolations` 사용, 선조회와 같은 응답). 성공 후 세션 ID를 새로 발급하고 로그인 상태로 만든다(openapi `signupWithEmail`). Redis 장애로 세션을 만들 수 없으면 503. (구현 메모: 응용 계층 결과 이름은 `SignedUpMember`(web DTO `SignupResult`와 겹치지 않게). 블로그 주소 중복은 선조회 때 "이미 사용 중인 주소예요. `x`는 어떠세요?" + `details.handleSuggestion`, 동시 가입 UNIQUE 위반 때 "방금 다른 분이 …" 문구. Redis를 먼저 `RedisGuard.isAvailable()`로 확인해 장애면 DB에 쓰기 전 503. 가입 직후 로그인도 `LoginService.onSuccess`를 거쳐 `last_login_at`이 기록된다)
- [X] T061 [US1] `B/account/application/EmailVerificationService.java`를 구현한다(FR-005·006): `resend(memberId)` — 이미 인증 409 `ALREADY_VERIFIED`, `RateLimiter`로 `rl:verify-resend:{memberId}`(1분 1번)·`auth:verify-resend:{memberId}:{yyyyMMdd}`(하루 10번, TTL 2일, 날짜는 `blog.time-zone`) → 초과 429, 통과하면 `VerificationMailRequested` 발행(202); `confirm(token)` — `AuthTokenStore.consume`, 실패 400 `LINK_EXPIRED`("링크가 만료됐어요. [인증 메일 다시 보내기]"), 성공 시 `email_verified_at = now()`. 이유 코드 enum `B/account/application/AccountReasonCode.java`(이 기능의 모든 코드를 여기에 모은다: `EMAIL_*`, `HANDLE_*`, `NICKNAME_*`, `PASSWORD_*`, `AGREEMENT_*`, `LINK_EXPIRED`, `ALREADY_VERIFIED`, `INVALID_CREDENTIALS`, `LOGIN_TEMPORARILY_LOCKED`, `SOCIAL_SIGNUP_EXPIRED`, `REAGREEMENT_REQUIRED`, `NICKNAME_CHANGE_TOO_SOON`, `BIO_*`, `INVALID_PROFILE_IMAGE`, `PASSWORD_NOT_SUPPORTED`, `PASSWORD_SAME_AS_CURRENT`, `CURRENT_PASSWORD_MISMATCH`, `PASSWORD_CHANGE_TEMPORARILY_LOCKED`, `CANNOT_FRIEND_SELF`). `INVALID_VISIBILITY`는 004 T016 `PostReasonCode`를 쓴다. (구현 메모: 하루 카운터는 재발송만 센다 — 가입 때 첫 메일은 세지 않음)
- [X] T062 [US1] 이메일 로그인·로그아웃을 Spring Security에 붙인다(R-04, FR-034·036·039·041): `B/account/infra/security/MemberUserDetailsService.java`(LOCAL `provider_user_id` = 소문자 이메일로 조회, principal = `MemberPrincipal`), `B/account/application/LoginService.java`(`onSuccess(memberId, provider, session)` — 갱신 전 `last_login_at`을 세션 속성 `previousLoginAt`·`provider`에 담고 `last_login_at = now()`; 정지·재동의·실패 카운터는 US5에서 확장), `JsonLoginSuccessHandler.java`(200 `LoginResult`, `redirect`는 우선 `/`만 — US5에서 검사기 연결), `JsonLoginFailureHandler.java`(항상 401 `INVALID_CREDENTIALS` "이메일 또는 비밀번호가 올바르지 않아요"), `B/account/infra/security/AccountSecurityCustomizer.java`(`SecurityFilterChainCustomizer`: `formLogin().loginProcessingUrl("/api/auth/login").usernameParameter("email")`, `logout().logoutUrl("/api/auth/logout")` → 204, 세션 무효화). 모두 `B/account/infra/security/` 아래. (구현 메모: `MemberAuthenticationProvider`(DaoAuthenticationProvider 하위)가 principal을 `MemberPrincipal`로 바꾼다 — principal 이름 = memberId라 세션 인덱스가 맞는다. 공급자는 빈으로만 등록(전역 매니저)해 실패 시 두 번 검사하지 않음. Spring 기본 로그인 페이지가 생기지 않게 `loginPage("/login")`(SPA). `reagreementRequired`는 로그인 때 DB로 계산만 하고 차단 필터는 US5. `SessionLogin`이 가입 직후 로그인에서 세션 ID 교체·SecurityContext 저장을 같은 방식으로 한다)
- [X] T063 [US1] `B/account/web/AuthController.java`에 `POST /api/auth/signup`(201 `SignupResult`), `POST /api/auth/email-verification`(202, `@LoginRequired`), `POST /api/auth/email-verification/confirm`(200, 비로그인 허용)을 더하고 요청 DTO `B/account/web/dto/EmailSignupRequest.java`·`TokenRequest.java`·`AgreementConsent.java`(contracts/openapi.yaml 스키마 그대로)를 만든다.
- [X] T064 [US1] `B/account/web/MeController.java`를 만들고 `GET /api/me` → `MeSummary{handle, nickname, role, status, provider, emailVerified, reagreementRequired, profileImageUrl}`(재동의 전에도 허용, 비로그인 401)를 구현한다. `profileImageUrl`은 T040 `ProfileImageQuery.currentKeys(memberId).display()` + `ImageUrlResolver`로 만든다(없으면 null). (구현 메모: `reagreementRequired`는 `AgreementService.needsReagreement`로 매번 계산)
- [X] T065 [P] [US1] 민감 값 로그 가림(FR-015, R-11): `R/logback-spring.xml`과 `B/shared/web/SensitiveParamMasking.java`(접근 로그·요청 로그에서 `token`, `password`, `currentPassword`, `newPassword` 쿼리·폼 값을 `***`로 바꾸는 `Filter`), Tomcat 접근 로그 패턴에서 쿼리 문자열 대신 마스킹된 값 사용. (구현 메모: 필터가 마스킹한 쿼리를 요청 속성 `blog.maskedQuery`에 넣고 접근 로그 패턴은 `%U%{blog.maskedQuery}r`. 애플리케이션 로그 메시지는 logback 변환기 `%maskedMsg`(`MaskedMessageConverter`)로 `name=value`·JSON 형식 값을 가림. 대상에 `passwordConfirm`·`newPasswordConfirm`도 포함)
- [ ] T066 [P] [US1] Frontend 세션·인증 API: `F/api/auth.ts`(`signup`, `login`(form-urlencoded), `logout`, `resendVerification`, `confirmVerification`, `getCurrentAgreements`), `F/api/me.ts`(`getMe`), `F/features/auth/SessionProvider.tsx` + `useSession.ts`(`GET /api/me`, 401이면 비로그인, `reagreementRequired`·`status` 노출).
- [ ] T067 [P] [US1] `F/components/PasswordRuleChecklist.tsx`를 구현한다 (T055 통과): `PasswordPolicy`와 같은 규칙·순서, "최대 16자" 문구, 글자 + ✓ (FR-014).
- [ ] T068 [US1] `F/pages/SignupPage.tsx`: 이메일·블로그 주소·비밀번호·비밀번호 확인·닉네임·필수 동의 2개(약관·처리방침 링크 `/terms`·`/privacy`, 현재 버전을 `getCurrentAgreements`로 받아 함께 전송), 서버 `errors[]`를 칸별로 표시, `EMAIL_ALREADY_REGISTERED`이면 [로그인]·[비밀번호 찾기] 링크, 성공 시 "인증 메일을 보냈어요" 안내. 블로그 주소·닉네임 칸은 US3에서 `HandleInput`·`NicknameInput`으로 교체한다. 375px 가로 스크롤 없음.
- [ ] T069 [P] [US1] `F/pages/LoginPage.tsx`(이메일 로그인 폼, 실패 문구 하나 "이메일 또는 비밀번호가 올바르지 않아요", 성공 시 `redirectTo`로 이동; 정지·잠금·소셜 버튼은 US2·US5에서 추가).
- [ ] T070 [P] [US1] `F/pages/VerifyEmailPage.tsx`: `?token=`을 읽어 `POST /api/auth/email-verification/confirm`(GET으로 소모하지 않음 — R-11), 성공 "인증이 완료됐어요", `LINK_EXPIRED`이면 "링크가 만료됐어요. [인증 메일 다시 보내기]"(로그인 상태면 재발송 호출, 429면 남은 시간 안내), 처리 후 주소창에서 토큰 제거(`history.replaceState`).
- [ ] T071 [P] [US1] `F/pages/TermsPage.tsx`·`F/pages/PrivacyPage.tsx`: 시행일·버전 표시(`GET /api/agreements/current`), 처리방침에 "친구에게 최근 활동 시점 표시" 항목 명시(FR-011, H9). 본문은 정적 텍스트(팀 확정 전 자리 표시 문구).
- [ ] T072 [US1] `F/features/auth/logout.ts`를 구현한다 (T055 통과): ① 002의 미전송 작업 1회 전송(최대 3초, 실패 시 "보내지 못한 작업이 지워져요" 확인 — R-28) ② 002 `clearMemberDrafts(memberId)`로 IndexedDB `draft:{memberId}:*`·`draft-backup:{memberId}:*` 삭제 ③ `POST /api/auth/logout` ④ `location.assign('/')`. 테마 설정은 건드리지 않는다(016). 선행: specs/002 clearMemberDrafts.

**Checkpoint**: US1 단독 동작 — quickstart §4-1·§4-2 첫 부분 통과.

---

## Phase 4: User Story 2 - Google·GitHub 계정으로 가입·로그인한다 (Priority: P1)

**Goal**: 소셜 첫 로그인은 계정을 만들지 않고 10분 보관 → 전체 페이지 가입 마무리(닉네임·`go-`/`gi-` 고정 접두어 주소·약관·사진 사용) → 계정 생성. 이미 연결된 소셜 계정은 바로 로그인. (C-AUTH-1, FR-030~033)

**Independent Test**: 가짜 OAuth2 사용자로 첫 로그인 → `/signup/social` → 가입 → 로그아웃 → 같은 소셜 ID로 다시 로그인 시 같은 계정 (quickstart §4-11).

### Tests for User Story 2 ⚠️

- [ ] T073 [P] [US2] `T/account/integration/SocialSignupIntegrationTest.java`(가짜 `OAuth2UserService`/`OidcUserService` Bean + `oauth2Login()`/`oidcLogin()`, R-35): #1 처음 Google 사용자 → 계정 0개, 세션 `pendingSocialSignup`, 302 `/signup/social`, `GET /api/auth/social-signup` → `{provider:GOOGLE, handlePrefix:"go-", suggestedHandleBody, suggestedNickname, emailRequired:false, existingAccountNotice, profilePhotoUrl(=s256-c 포함), expiresAt}`, 이 상태로 `/api/me` 401(익명 유지) · #2 11분 뒤(`Clock` 조작) `POST /api/auth/social-signup` → 410 `SOCIAL_SIGNUP_EXPIRED`, 계정 없음 · #3 가입한 GitHub 사용자(숫자 ID 같음, 이메일·login 바뀜) → 같은 계정으로 로그인 · #4 같은 이메일의 LOCAL 계정이 있어도 Google 가입 → 계정 2개, `existingAccountNotice:true`(제공자가 확인한 이메일일 때만, FR-033) · GitHub에 확인된 대표 이메일 없음 → `emailRequired:true`, `profilePhotoUrl:null`, 가입 후 인증 전 + 인증 메일 발송(FR-008, R-20) · Google `email_verified=false` 거부 · 사진 주소 호스트가 허용 2곳·HTTPS가 아니면 null · SC-012: 가입 후 모든 테이블에 소셜 사진 주소 문자열 없음 · 동의 2행 기록 · `state` 불일치 콜백 거부 · 정지 계정 소셜 로그인 → `/login?error=social` + `GET /api/auth/social-login-error` 1회 반환.
- [ ] T074 [P] [US2] `T/account/integration/SocialSignupPageCspIntegrationTest.java`(FR-032, R-21, 12 §8): `GET /signup/social` HTML 응답의 CSP `img-src`에 `https://lh3.googleusercontent.com https://avatars.githubusercontent.com` 포함, `/settings`·`/`·`/api/me` 응답에는 없음.
- [ ] T075 [P] [US2] `F/features/profile/socialPhotoImport.test.ts`: 5초 제한 초과 → 실패 결과(가입은 성공 처리, "소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요"), 가운데 정사각형 자르기 → 256×256 WebP Blob, `crossOrigin="anonymous"`, 성공 시 003 presign `{purpose: PROFILE}` → 업로드 → complete → `PATCH /api/me/profile {profileImageId}` 순서 호출(모의 fetch).

### Implementation for User Story 2

- [ ] T076 [US2] OAuth2 Client 등록을 설정한다: `R/application.yml`의 `spring.security.oauth2.client.registration.google`(scope `openid,email,profile`, `${GOOGLE_CLIENT_ID}`·`${GOOGLE_CLIENT_SECRET}`)·`github`(scope `read:user,user:email`, `${GITHUB_CLIENT_*}`), redirect `{baseUrl}/login/oauth2/code/{registrationId}`. 비밀값은 환경 변수만(constitution IV). 네이버 등은 넣지 않는다(FR-001).
- [ ] T077 [P] [US2] 소셜 사용자 정보 서비스: `B/account/infra/security/OidcMemberUserService.java`(Google `sub` 식별, `email_verified = true`인 이메일만), `B/account/infra/security/OAuth2MemberUserService.java`(GitHub 숫자 `id` 식별, `/user/emails`에서 `primary && verified` 이메일, `login`은 식별에 쓰지 않음) → 공통 `SocialProfile{provider, providerUserId, email, emailVerified, displayName, pictureUrl}`(R-06, FR-003·008).
- [ ] T078 [US2] `B/account/application/SocialLoginService.java`와 `B/account/application/PendingSocialSignup.java`(record: provider, providerUserId, email, emailVerified, displayName, pictureUrl, createdAt)를 구현한다(R-07·R-08): `(provider, providerUserId)` 연결 계정이 있으면 `LoginService.onSuccess`로 로그인, 없으면 세션 속성 `pendingSocialSignup` 저장(SecurityContext는 익명 유지). `draft()` — `HandleSuggester.suggest(email, provider)` 본문, `NicknamePolicy.suggestFromSocialName`, `existingAccountNotice` = 확인된 이메일로 `ix_auth_identity_email` 조회 시 다른 provider 계정 존재, `profilePhotoUrl` = 호스트(`blog.auth.social.photo-hosts`)·HTTPS 확인 후 크기 매개변수(Google `=s256-c`, GitHub `&s=256`), `emailRequired`면 null. 10분(`blog.auth.social.pending-ttl`) 지나면 410.
- [ ] T079 [US2] `B/account/infra/security/OAuth2LoginSuccessHandler.java`·`OAuth2LoginFailureHandler.java`를 구현하고 `AccountSecurityCustomizer`에 `oauth2Login()`을 등록한다: 시작 때 `redirect` 쿼리를 세션 `loginRedirect`에 보관, 기존 계정이면 로그인 후 보관 경로로 302, 처음이면 302 `/signup/social`(전체 페이지), 정지 계정이면 오류 본문을 세션에 1회 보관 후 302 `/login?error=social`. Redis 장애 시 503 화면 안내.
- [ ] T080 [US2] `SignupService.completeSocialSignup(SocialSignupCommand, PendingSocialSignup)`를 구현한다(FR-008·010·030): 대기 정보 없음·만료 → 410, `handleBody`에 provider 접두어를 붙여 `HandlePolicy.validate`(접두어 일치), 닉네임·동의 검사, `emailRequired`면 이메일 형식 검사, 한 트랜잭션에서 member·auth_identity(provider, providerUserId, email, `email_verified_at` = 확인된 이메일이면 가입 시각 / 직접 입력이면 NULL + `VerificationMailRequested`)·동의 2행, `uq_auth_identity` 위반이면 이미 연결된 계정으로 로그인 처리(R-09), 성공 후 대기 정보 삭제·세션 ID 재발급·`LoginService.onSuccess`. 응답 `profilePhotoUrl`은 `useProfilePhoto`이고 가져올 수 있을 때만, 서버는 저장하지 않는다(SC-012).
- [ ] T081 [US2] `B/account/web/SocialSignupController.java`: `GET /api/auth/social-signup`(200 `SocialSignupDraft` / 410), `POST /api/auth/social-signup`(201 `SocialSignupResult`), `GET /api/auth/social-login-error`(200 보관 오류 한 번 / 204), DTO `B/account/web/dto/SocialSignupRequest.java`·`SocialSignupDraftResponse.java`·`SocialSignupResult.java`(openapi 스키마 그대로).
- [ ] T082 [P] [US2] `B/account/web/SocialSignupPageCspContributor.java`(T029의 `CspContributor` 구현: `/signup/social` HTML 응답에만 `blog.auth.social.photo-hosts`를 `https://` 출처로 `img-src`에 추가) (T074 통과). plan의 `SocialSignupPageCspFilter`에 해당한다.
- [ ] T083 [US2] `F/pages/SocialSignupPage.tsx`: 진입 시 `GET /api/auth/social-signup`(410이면 "다시 소셜 로그인" 안내), 주소 `go-`/`gi-`는 고칠 수 없는 고정 글자 + 본문 입력, 닉네임 미리 채움(null이면 빈칸 + "닉네임을 입력해 주세요"), `emailRequired`면 이메일 칸, `existingAccountNotice`면 "이 이메일로 가입한 계정이 이미 있어요" + [기존 계정으로 로그인](→ `/login`)·[새 계정 만들기](안내 닫기), 약관 2개, "프로필 사진 사용"(기본 체크, `profilePhotoUrl` 없으면 숨김) 미리보기, 가입 완료 → (사진 사용 시) `socialPhotoImport` → `window.location.assign(redirectTo)`(전체 페이지 이탈 — FR-032).
- [ ] T084 [P] [US2] `F/features/profile/socialPhotoImport.ts`를 구현한다 (T075 통과): `Image`(`crossOrigin="anonymous"`)로 5초 제한 로드 → canvas 가운데 정사각형 → 256×256 WebP → 003 presign `{purpose: PROFILE}`·직접 PUT·complete → `PATCH /api/me/profile {profileImageId}`(US6 T119). 실패하면 가입 결과는 그대로 두고 안내만. 선행: specs/003 presign·complete, US6 T119.
- [ ] T085 [US2] `F/pages/LoginPage.tsx`에 [Google로 계속하기]·[GitHub로 계속하기]를 더한다: `/oauth2/authorization/{google|github}?redirect=…`로 전체 페이지 이동, `?error=social`이면 `GET /api/auth/social-login-error`로 읽어 표시.

**Checkpoint**: US1·US2 모두 단독 동작 (SocialSignupIntegrationTest 통과, quickstart §4-11 수동 확인).

---

## Phase 5: User Story 3 - 가입할 때 블로그 주소와 닉네임을 정한다 (Priority: P1)

**Goal**: 블로그 주소 미리 채우기·사용 가능 확인·동시 가입 한 명만·예약어 거부·가입 후 변경 불가, 닉네임 규칙·중복·동시 요청 한 명만. (C-AUTH-2, FR-016~029) — 검증 정책 자체는 2B(T043~T051)에 있다.

**Independent Test**: 08 §3 예시로 미리 채우기 비교(T044·T088), 09 예시 닉네임 허용·거부(T045), 사용 가능 확인 API와 동시 가입(quickstart §4-2·§4-3).

### Tests for User Story 3 ⚠️

- [ ] T086 [P] [US3] `T/account/integration/AvailabilityIntegrationTest.java`(quickstart §4-3): `GET /api/handles/availability` — `kim755030`(있음) → `{available:false, reason:HANDLE_DUPLICATE, suggestion:"kim755030_2"}`, `admin` → `HANDLE_RESERVED`·`admin_2`, `go-admin` → `HANDLE_RESERVED`, `Kim755030`·`kim-min`·`ab` → `HANDLE_INVALID_FORMAT` · `GET /api/nicknames/availability` — 09 예시 각각의 코드, `시발점`만 available, 금칙어 응답에 단어 없음, 로그인한 본인 닉네임의 대소문자만 바꾼 값 → available(자기 제외) · 같은 IP 31번째 → 429 + `Retry-After` · Redis 정지 중 31번 → 모두 200(제한 통과) · US3 #5: 예약어 주소로 `POST /api/auth/signup` → 400 `HANDLE_RESERVED` · #8: 금칙어 닉네임 가입 → 400 `NICKNAME_BANNED_WORD` · #9: NFD `김민서` 가입 → 저장값 NFC.
- [ ] T087 [P] [US3] `T/account/integration/SignupConcurrencyIntegrationTest.java`(SC-001, US3 #4·#10, R-35): `ExecutorService` 20개 동시 — 같은 이메일 → 계정 1개, 같은 주소·다른 이메일 → 201 1건·나머지 400 `HANDLE_DUPLICATE` + `details.handleSuggestion` + 문구 "방금 다른 분이 이 주소를 사용했어요. `{대안}`는 어떠세요?", 같은 닉네임(대소문자 다름 포함) → 1건만, 진 쪽 "방금 다른 분이 이 닉네임을 사용했어요". 500 응답 0건.
- [ ] T088 [P] [US3] Frontend 테스트: `F/features/handle/prefillHandleFromEmail.test.ts`(08 §3 예시 12개의 ①~⑧ 결과가 서버 `HandleSuggesterTest`와 같은 데이터로 일치, `Kim.Min-Seo+blog@naver.com` → `kim_min_seo`, 3자 미만 → `user_` + 6자리), `F/components/HandleInput.test.tsx`(#3 사용자가 주소 칸을 직접 고친 뒤 이메일을 바꾸면 자동 갱신 멈춤, 대문자 입력 → 소문자 표시, `-` 입력 불가, 두벌식 자모 `ㅏ` → `k` 변환, 소셜 모드에서 접두어 수정 불가, `inputmode="url"`·`autocapitalize="off"`), `F/components/AvailabilityHint.test.tsx`(입력이 0.5초 멈춘 뒤 1번만 요청).

### Implementation for User Story 3

- [ ] T089 [US3] `B/account/application/AvailabilityService.java`와 `B/account/web/AvailabilityController.java`를 구현한다(FR-020·026, 08 §4-2, 09 §6): `GET /api/handles/availability?handle=` → `HandleAvailability{available, reason, suggestion}`(예약어·중복이면 `HandleSuggester.nextAvailable`), `GET /api/nicknames/availability?nickname=` → `{available, code}`(로그인한 회원이면 자기 제외), 둘 다 `RateLimiter` 키 `rl:availability:handle:ip:{ClientIp}`·`rl:availability:nickname:ip:{ClientIp}`(1분 30번, `blog.availability.ip-limit-per-minute`) 초과 429. 사용 가능 확인은 안내용이며 최종 판정은 가입 요청에서 다시 한다.
- [ ] T090 [US3] `SignupService`(이메일·소셜 공용)의 UNIQUE 위반 처리를 완성한다 (T087 통과): `uq_member_handle` → `HANDLE_DUPLICATE` + 새 트랜잭션에서 `HandleSuggester.nextAvailable`로 `details.handleSuggestion`과 문구, `uq_member_nickname` → `NICKNAME_DUPLICATE` "방금 다른 분이 이 닉네임을 사용했어요". 선조회에서 걸린 경우와 응답 형식이 같다(R-09).
- [ ] T091 [P] [US3] `F/features/handle/prefillHandleFromEmail.ts`를 구현한다 (T088 통과): 08 §3 ①~⑧ 순수 함수(⑧ 난수는 `crypto.getRandomValues`), ⑨ 접두어·⑩ 중복 번호는 서버 `suggestion`으로 대체(R-15).
- [ ] T092 [P] [US3] `F/components/HandleInput.tsx`(접두어 고정 표시 옵션, 소문자 변환, `-` 차단, 두벌식 → QWERTY 변환, 안내 "이메일 앞부분으로 미리 채웠어요. 이메일을 드러내고 싶지 않으면 바꿔 주세요."·"블로그 주소는 가입 후 바꿀 수 없어요" — FR-018), `F/components/NicknameInput.tsx`, `F/components/AvailabilityHint.tsx`(0.5초 지연, `F/api/availability.ts` 호출, 429면 조용히 대기), `F/api/availability.ts`를 구현한다 (T088 통과).
- [ ] T093 [US3] `F/pages/SignupPage.tsx`와 `F/pages/SocialSignupPage.tsx`의 주소·닉네임 칸을 `HandleInput`·`NicknameInput`·`AvailabilityHint`로 교체하고, 이메일 입력에 따라 `prefillHandleFromEmail`로 자동 채움(직접 고친 뒤에는 멈춤), 서버가 돌려준 `suggestion`으로 바꿔 채움, 가입 실패 `HANDLE_DUPLICATE`의 `details.handleSuggestion`을 제안으로 표시.

**Checkpoint**: P1 스토리(US1·US2·US3) 완료 — MVP 범위. US3 #6(주소 변경 불가)은 US6 T111, #7(`/@Kim755030` 301)은 005 `PageShellIntegrationTest`가 확인한다.

---

## Phase 6: User Story 4 - 비밀번호를 찾고 바꾼다 (Priority: P2)

**Goal**: 가입 여부를 드러내지 않는 비밀번호 찾기, 30분 1회 재설정 링크(모든 기기 로그아웃), 로그인 상태 비밀번호 변경(다른 기기 로그아웃·지금 기기 유지·알림 메일), 소셜 계정은 비밀번호 없음. (FR-042~045)

**Independent Test**: 가입·미가입 이메일로 찾기 응답이 같고, 재설정 후 다른 세션이 끊김 (quickstart §4-5).

### Tests for User Story 4 ⚠️

- [ ] T094 [P] [US4] `T/account/integration/PasswordResetIntegrationTest.java`: #1 가입·미가입 이메일 `POST /api/auth/password-reset` → 둘 다 202 `{message:"가입된 이메일이면 안내 메일을 보냈어요"}`(상태·본문 100% 같음, SC-004), 메일은 가입 이메일에만 · #2 Google 계정만 있는 이메일 → 재설정 링크 없는 "이 이메일은 Google로 가입되어 비밀번호가 없어요" 메일 · LOCAL + 같은 이메일 GitHub 계정 → 링크 + "그 계정은 GitHub로 로그인하세요" 안내 · #3 세션 2개인 회원이 링크로 새 비밀번호 저장 → 204, 두 세션 모두 401, 같은 토큰 재사용 400 `LINK_EXPIRED`, 31분 지난 토큰 400 · 규칙 위반 비밀번호 → 400 `VALIDATION_FAILED`이고 토큰은 소모되지 않음 · 정지·탈퇴 유예 회원도 재설정 가능(42 §9, FR-044) · 같은 이메일 1분 2번째·하루 11번째, 같은 IP 1시간 21번째 → 429(가입 여부와 무관하게 같은 응답, FR-043) · Redis 정지 중 → 503.
- [ ] T095 [P] [US4] `T/account/integration/PasswordChangeIntegrationTest.java`: #4 세션 A·B 중 A에서 `POST /api/me/password`(현재 비밀번호 맞음) → 204, B는 401, A는 200이며 세션 ID가 바뀜, "비밀번호가 변경됐어요. 본인이 아니라면 [비밀번호 재설정]" 메일 · #5 소셜 계정 → 400 `PASSWORD_NOT_SUPPORTED` · 현재와 같은 새 비밀번호 → 400 `PASSWORD_SAME_AS_CURRENT` · 틀린 현재 비밀번호 → 400 `CURRENT_PASSWORD_MISMATCH` · #6 5회 연속 틀린 뒤 6번째(맞아도) → 429 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` + `Retry-After`(15분) · 인증 전 회원도 변경 가능(ACCOUNT_WRITE), 정지 세션 → 403 `ACCOUNT_SUSPENDED`.

### Implementation for User Story 4

- [ ] T096 [US4] `B/account/application/PasswordResetService.java`를 구현한다(FR-042~044, R-11·R-12·R-29): `request(email)` — 형식만 검사, `RateLimiter`(`rl:reset:email:{sha256(email)}` 1분 1번, `rl:reset:email-day:{sha256(email)}:{yyyyMMdd}` 하루 10번, `rl:reset:ip:{ClientIp}` 1시간 20번) → 조회 **전에** 202를 돌려주고 비동기(`mailExecutor`)로 `findAllByEmail` 조회·발송(응답 시간을 가입 여부와 무관하게, SC-004); `confirm(token, newPassword, confirm)` — 비밀번호 규칙을 먼저 검사(실패 시 토큰 미소모) → `AuthTokenStore.consume(RESET)` → BCrypt 저장 → `SessionTerminator.terminateAll(memberId, empty)`. 템플릿 `R/mail/password-reset.txt`(30분 링크 `/reset-password?token=…` + 같은 이메일 소셜 계정 안내), `R/mail/password-reset-social-only.txt`.
- [ ] T097 [US4] `B/account/application/PasswordChangeService.java`를 구현한다(FR-045, R-12): `AccountStatusGuard.requireActive(memberId, ACCOUNT_WRITE)` → LOCAL 아니면 400 `PASSWORD_NOT_SUPPORTED` → 잠금 확인(`auth:pw-change-fail:{memberId}`, 5회·15분) → 현재 비밀번호 BCrypt 비교(틀리면 카운터 증가·400) → `PasswordPolicy` → 같으면 400 → 저장 → `SessionTerminator.terminateAll(memberId, 현재 세션 제외)` → 현재 세션 ID 재발급 → 커밋 후 알림 메일(`R/mail/password-changed.txt`).
- [ ] T098 [US4] 엔드포인트를 더한다: `B/account/web/AuthController.java`에 `POST /api/auth/password-reset`(202)·`POST /api/auth/password-reset/confirm`(204), `B/account/web/MeController.java`에 `POST /api/me/password`(204), DTO `PasswordResetRequest`·`PasswordResetConfirmRequest`·`PasswordChangeRequest`(`B/account/web/dto/`).
- [ ] T099 [P] [US4] `F/pages/ForgotPasswordPage.tsx`(이메일 입력, 결과 문구 하나 "가입된 이메일이면 안내 메일을 보냈어요", 429 안내)와 `F/pages/ResetPasswordPage.tsx`(`?token=`, 새 비밀번호 + `PasswordRuleChecklist`, 성공 시 "모든 기기에서 로그아웃됐어요" + 로그인 이동, `LINK_EXPIRED` 안내, 처리 후 주소창 토큰 제거)를 구현하고 `F/api/auth.ts`에 호출 함수를 더한다.
- [ ] T100 [P] [US4] `F/features/settings/PasswordChangeForm.tsx`: 현재·새·확인 비밀번호 + `PasswordRuleChecklist`, 오류 코드별 문구, 429 남은 시간 표시. `MySettings.passwordChangeAvailable`이 false면 렌더하지 않는다(US6 `SettingsPage`가 포함).

**Checkpoint**: US4 단독 동작 (quickstart §4-5).

---

## Phase 7: User Story 5 - 로그인 보안: 실패 제한·정지 계정·약관 재동의 (Priority: P2)

**Goal**: 계정당 5회 실패 15분 잠금·IP 1분 20회, 같은 실패 문구, 정지 계정 로그인 거부·만료 자동 해제, 재동의 전 차단, 위조 IP 헤더 무시, 외부 이동 차단. (FR-012·035~039)

**Independent Test**: 5회 실패 후 맞는 비밀번호로도 거부, 정지 처리 후 남은 세션 쓰기 403, 약관 버전 변경 후 재동의 화면 (quickstart §4-4·§4-8).

### Tests for User Story 5 ⚠️

- [ ] T101 [P] [US5] `T/account/integration/LoginSecurityIntegrationTest.java`: #1 같은 이메일 5회 실패 후 맞는 비밀번호 → 429 `LOGIN_TEMPORARILY_LOCKED`("잠시 후 다시 시도해 주세요(약 15분)") + `Retry-After`, 가입하지 않은 이메일로 같은 6번 → 상태·본문 100% 같음(SC-003·004), 성공하면 카운터 초기화 · #2 틀린 이메일·틀린 비밀번호 → 같은 401 `INVALID_CREDENTIALS` · #3 열린 정지 회원이 맞는 비밀번호 → 403 `ACCOUNT_SUSPENDED` `details{endsAt, reason}`(영구면 `endsAt:null`), 틀린 비밀번호면 일반 401 · #4 `ends_at` 지난 정지 → `lifted_at` 기록(`lifted_by` NULL)·`status=ACTIVE`·로그인 200 · #5 로그인 후 DB에서 정지로 바꾸면 그 세션의 `ACCOUNT_WRITE`·`CONTENT_WRITE` 요청 403 `ACCOUNT_SUSPENDED`(H7) · #8 `redirect=//evil.com`·`https://evil.com`·`/\evil.com`·`%2F%2Fevil.com` → `redirectTo:"/"`, `/settings?tab=1` → 그대로 · WITHDRAWN 회원 로그인 → 200 `accountStatus:"WITHDRAWN"`(015 복구 화면) · Redis 정지 중 로그인 → 503 `TEMPORARILY_UNAVAILABLE`.
- [ ] T102 [P] [US5] `T/account/integration/ReagreementIntegrationTest.java`(#6, SC-011, R-24): 설정 `blog.agreement.terms.version`을 올린 컨텍스트(`@TestPropertySource`)에서 예전 버전 회원 로그인 → `reagreementRequired:true` → `GET /api/me/settings`·`PATCH /api/me/profile` 등 허용 목록 밖 → 403 `REAGREEMENT_REQUIRED`, 허용 목록(`GET /api/me`, `GET /api/agreements/current`, `PUT /api/me/agreements`, `POST /api/auth/logout`, `GET /api/auth/csrf`, 화면 셸) → 통과 → `PUT /api/me/agreements`(현재 버전) 204 → `member_agreement.version`·`agreed_at` 갱신, 이후 200 · 다른 버전 값 → 400 `AGREEMENT_VERSION_MISMATCH`.
- [ ] T103 [P] [US5] `T/account/integration/TrustedProxyIntegrationTest.java`(#7, SC-010, FR-037, R-13): 신뢰 대역 밖 원격 주소에서 매번 다른 `X-Forwarded-For`로 로그인 21번 → 21번째 429(헤더 무시), 신뢰 대역(사설) 프록시 원격 주소 + `X-Forwarded-For: 203.0.113.5, 10.0.0.2` → 클라이언트 IP `203.0.113.5`, 신뢰 밖에서 `X-Forwarded-Proto: https` 위조 → HTTPS로 보지 않음. (RemoteIpValve 동작을 위해 `webEnvironment = RANDOM_PORT` + 실제 HTTP 클라이언트 사용)
- [ ] T104 [P] [US5] 단위 테스트: `T/account/infra/security/SafeRedirectResolverTest.java`(R-33 규칙: `/`로 시작, `//`·`/\` 시작 금지, 제어 문자·`\` 금지, URL 디코딩 후 같은 검사), `F/features/auth/safeRedirect.test.ts`(같은 표 데이터).

### Implementation for User Story 5

- [ ] T105 [P] [US5] `B/account/infra/redis/LoginFailureCounter.java`(`auth:login-fail:{sha256(정규화 이메일)}`, TTL 15분, 가입 여부와 무관하게 셈, 성공 시 삭제, Redis 장애 시 통과)와 `B/account/infra/security/LoginRateLimitFilter.java`(`/api/auth/login` POST에만, 인증 필터 **앞**: `rl:login:ip:{ClientIp}` 1분 20회 초과 → 429 `TOO_MANY_REQUESTS`, 이메일 잠금 중 → 429 `LOGIN_TEMPORARILY_LOCKED` + `Retry-After`)를 구현하고 `AccountSecurityCustomizer`에 등록한다(FR-035, R-12).
- [ ] T106 [P] [US5] `B/account/domain/MemberSuspension.java`(data-model §2-4, 8개 컬럼), `B/account/infra/MemberSuspensionRepository.java`(`findOpenByMemberId` — `lifted_at IS NULL ORDER BY started_at DESC LIMIT 1`, `ix_member_suspension_member`), `B/account/application/SuspensionService.java`(`Optional<OpenSuspension> findOpen(memberId)`, `liftIfExpired(memberId, now)` — 같은 트랜잭션에서 `lifted_at = now()` + `member.status = ACTIVE`, 014용 공개 메서드 `suspend(memberId, reason, endsAt, suspendedBy)`·`lift(memberId, liftedBy)`는 시그니처와 TODO만 — 생성은 specs/014, R-31)를 구현한다.
- [ ] T107 [P] [US5] `B/account/infra/security/SafeRedirectResolver.java`와 `F/features/auth/safeRedirect.ts`를 구현한다 (T104 통과). `JsonLoginSuccessHandler`·`OAuth2LoginSuccessHandler`가 이 검사기로 `redirectTo`를 정한다(FR-039).
- [ ] T108 [US5] `LoginService.onSuccess`를 확장한다(FR-012·038, R-23·R-24): 비밀번호가 맞은 뒤 `SuspensionService.liftIfExpired` → 열린 정지가 남아 있으면 인증을 되돌리고 403 `ACCOUNT_SUSPENDED` `details{endsAt, reason}`("정지된 계정이에요 (~기한). 사유: …", 기한 없으면 영구), `LoginFailureCounter.reset`, `AgreementService.needsReagreement` → 세션 속성 `reagreementRequired`, `status = WITHDRAWN`이면 `accountStatus:"WITHDRAWN"`(복구는 015). 같은 판정을 소셜 로그인 경로(T078)에도 적용한다. Redis 장애로 세션 저장 불가 → 503.
- [ ] T109 [US5] `B/account/infra/security/ReagreementGateFilter.java`(세션에 `reagreementRequired`가 있으면 허용 목록 밖 `/api/**` 요청을 403 `REAGREEMENT_REQUIRED`로 막음)와 `PUT /api/me/agreements`(`B/account/web/MeController.java`, `AgreementService.reagree` — upsert 후 세션 표시 해제, 같은 버전 재전송도 204)를 구현하고 필터를 `AccountSecurityCustomizer`에 등록한다 (T102 통과).
- [ ] T110 [US5] Frontend: `F/pages/ReagreementPage.tsx`(`/reagree`, 바뀐 문서 링크·동의 후 `PUT /api/me/agreements`), `F/App.tsx` 라우트 가드(세션 `reagreementRequired`면 `/reagree`·`/terms`·`/privacy` 외 이동 차단, 403 `REAGREEMENT_REQUIRED` 응답 시 이동), `F/pages/LoginPage.tsx`에 정지 문구(기한 Asia/Seoul 날짜, 없으면 "영구")·잠금 문구·`?redirect=` 전달(`safeRedirect`) 추가.

**Checkpoint**: US5 단독 동작 (quickstart §4-4·§4-8).

---

## Phase 8: User Story 6 - 프로필과 계정 설정을 고친다 (Priority: P2)

**Goal**: 닉네임·소개·사진을 [저장] 한 번으로(하나라도 실패하면 아무것도 저장 안 함), 닉네임 30일 제한, 새 글 기본 공개 범위·최근 활동 공개 설정, 블로그 주소·이메일 읽기 전용. (FR-046~053)

**Independent Test**: 세 칸을 함께 저장해 반영 확인, 한 칸을 규칙 위반으로 보내 아무것도 안 바뀜 확인 (quickstart §4-6).

### Tests for User Story 6 ⚠️

- [ ] T111 [P] [US6] `T/account/integration/ProfileUpdateIntegrationTest.java`: #1 `PATCH /api/me/profile {nickname, bio, profileImageId}` → 200 `MyProfile`, 같은 트랜잭션에서 사진 연결 · #2 올바른 닉네임 + 201자 소개 → 400 `errors[bio].code=BIO_TOO_LONG`("소개는 200자까지 쓸 수 있어요"), 닉네임 그대로(SC-008) · 여러 칸 동시 실패 → 모두 반환 · #3 `<script>` 소개 → 원문 그대로 저장(화면 이스케이프는 T122) · #4 닉네임 변경 후 30일 안 → `GET /api/me/profile`의 `nicknameChangeAvailableAt` = 변경+30일, 닉네임 변경 + 소개 → 409 `NICKNAME_CHANGE_TOO_SOON` `details.nextChangeAvailableAt`이고 소개도 안 바뀜, 닉네임 없이 소개만 → 200 · 같은 닉네임 재저장 → `nickname_changed_at` 그대로 · 가입 직후 첫 변경 허용 · 대소문자만 변경 → 변경으로 셈, 자기 자신 중복 제외 · #5 남이 올린 사진·`purpose=POST` 사진·없는 ID → 400 `INVALID_PROFILE_IMAGE` · #6 새 사진 연결 → 이전 행 `detached_at` 기록·새 행 `ATTACHED`, `profileImageId:null` → 이전 행만 떼기, 현재 사진은 `detached_at IS NULL`(003 정리 대상 아님) · #7 본문 `memberId: 다른 회원`·`handle` → 무시, 본인만 변경(FR-047, US3 #6) · 같은 회원 동시 저장 2건 → 둘 다 성공·나중 값 반영·현재 사진 1장(`uq_image_profile_current` 위반 0) · 인증 전 회원도 닉네임·소개 변경 가능 · 비로그인 401, 정지 세션 403.
- [ ] T112 [P] [US6] `T/account/integration/AccountSettingsIntegrationTest.java`(FR-046·053·061): `GET /api/me/settings` → `{email, provider, previousLogin, defaultVisibility:"PUBLIC", lastActiveVisible:true, passwordChangeAvailable}`(소셜이면 false) · `PATCH {defaultVisibility:"PRIVATE"}` → 200 · `"FRIENDS"`(Rule 미등록) → 400 `INVALID_VISIBILITY` · `PATCH {lastActiveVisible:false}` → 200 · 보낸 칸만 바뀜 · 빈 본문 → 400 · 인증 전 회원 허용.
- [ ] T113 [P] [US6] `T/account/application/policy/BioPolicyTest.java`(FR-048, R-18): 코드 포인트 기준 200자(이모지 200개 허용, 201개 `BIO_TOO_LONG`), `\r\n`·`\r` → `\n`, 연속 빈 줄 → 하나로 정리 후 줄 5개 → `BIO_TOO_MANY_LINES`("소개는 4줄까지 쓸 수 있어요"), 금칙어 → `BIO_BANNED_WORD`(예약어 검사 없음), 앞뒤 공백 제거·NFC, 빈 문자열 → null.
- [ ] T114 [P] [US6] Frontend 테스트: `F/components/DefaultAvatar.test.tsx`(닉네임 첫 글자, 영문은 대문자, 색 번호 = 블로그 주소 UTF-8 바이트 FNV-1a 32비트 mod 8 — R-34 고정 예시값, SVG 렌더, 파일 요청 없음), `F/components/ProfileImageCropper.test.tsx`(jpg·png·gif·webp 외·10MB 초과 거부, 결과 256×256 Blob).

### Implementation for User Story 6

- [ ] T115 [P] [US6] `B/account/application/policy/BioPolicy.java`를 구현한다 (T113 통과). 최대 길이·줄 수는 `blog.member.bio.*`(DB `ck_member_bio`와 같은 200 유지).
- [ ] T116 [P] [US6] 프로필 사진 연결 포트와 **임시 구현**을 만든다(003에서 교체): `B/media/application/ProfileImageService.java`(인터페이스 `void attach(long memberId, long imageId)`·`void detach(long memberId)`·`current`는 T040 `ProfileImageQuery`에 위임), `B/media/application/TemporaryProfileImageService.java`(data-model §2-6 SQL 그대로: 이전 사진 `detached_at = now()` → 새 사진 `status='ATTACHED', detached_at=NULL WHERE id=? AND uploader_id=? AND purpose='PROFILE'`, 0행이면 400 `INVALID_PROFILE_IMAGE`; 클래스 주석 "specs/003이 소유·교체"). 크기 256×256·1MB 검사는 003 complete 단계 책임.
- [ ] T117 [US6] `B/account/application/ProfileService.java`를 구현한다 (T111 통과, FR-028·047·049~052, R-19): 한 트랜잭션 — `MemberRepository.findByIdForUpdate` → `AccountStatusGuard(ACCOUNT_WRITE)` → 보낸 칸만 검사(`NicknamePolicy`(자기 제외)·`BioPolicy`·사진 ID 존재) 오류를 모두 모음 → 있으면 400(아무것도 저장 안 함) → 닉네임이 실제로 바뀌었고 `nickname_changed_at + 30일 > now`면 409 → 저장(`nickname_changed_at = now()`는 실제 변경 때만, `updated_at` 갱신) → 사진 `ProfileImageService.attach/detach`(떼기 → 붙이기). `uq_member_nickname` 위반 → `NICKNAME_DUPLICATE`.
- [ ] T118 [US6] `B/account/application/AccountSettingsService.java`를 구현한다 (T112 통과): `defaultVisibility`는 004 T013 `VisibilityRegistry.require(raw, "defaultVisibility")`로 등록 값만 허용(아니면 004 T016 `InvalidVisibilityException` → 400 `INVALID_VISIBILITY`, DB CHECK `ck_member_default_visibility`와 일치), `lastActiveVisible` 변경, `AccountStatusGuard(ACCOUNT_WRITE)`. 선행: specs/004 T009·T013·T016.
- [ ] T119 [US6] `B/account/web/MeController.java`에 `GET /api/me/profile`(`MyProfile` — `nicknameChangeAvailableAt`, `profileImageUrl` via `ImageUrlResolver`), `PATCH /api/me/profile`(알 수 없는 필드 무시 — `@JsonIgnoreProperties(ignoreUnknown = true)`, `profileImageId: null`과 "칸 없음"을 구분하는 DTO), `GET /api/me/settings`(`MySettings`, `previousLogin`은 US8 T141에서 채움 — 그 전에는 null), `PATCH /api/me/settings`를 더한다. DTO `B/account/web/dto/ProfileUpdateRequest.java`·`MyProfileResponse.java`·`SettingsUpdateRequest.java`·`MySettingsResponse.java`.
- [ ] T120 [P] [US6] `F/components/DefaultAvatar.tsx`를 구현한다 (T114 통과): SVG 원형 + 첫 글자, FNV-1a mod 8 색(016 토큰 확정 전 임시 8색, 라이트·다크 모두 글자 대비 4.5:1 이상 — FR-051).
- [ ] T121 [P] [US6] `F/components/ProfileImageCropper.tsx`를 구현한다 (T114 통과): 파일 선택(jpg·png·gif·webp, 10MB 이하), 정사각형 위치·확대·축소, canvas로 256×256 출력(메타데이터 제거, GIF 첫 장면), `blob:` 미리보기, 003 presign `{purpose: PROFILE}`·업로드·complete로 `imageId` 획득(저장 전까지 연결 안 함 — FR-049). 선행: specs/003.
- [ ] T122 [US6] `F/pages/SettingsPage.tsx`(`/settings`, 비로그인은 `/login?redirect=/settings`)와 `F/api/me.ts` 확장: 프로필 영역(사진·[기본 이미지로]·닉네임(30일 제한 중 비활성 + "다음 변경 가능일")·소개(글자 수 코드 포인트 기준, 텍스트 노드로만 출력)·블로그 주소 `@handle` 읽기 전용) + [저장] 한 번(바꾼 칸만 전송, 실패 칸 모두 표시), 계정 영역(이메일 읽기 전용, 로그인 수단, `PasswordChangeForm`(T100), 새 글 기본 공개 범위 선택, 최근 활동 공개 토글, 약관·처리방침 링크), 회원 탈퇴 자리(→ 015). 375px 가로 스크롤 없음.

**Checkpoint**: US6 단독 동작 (quickstart §4-6).

---

## Phase 9: User Story 7 - 친구를 맺는다 (Priority: P3)

**Goal**: 블로그에서 친구 요청 → 수락(맞요청은 즉시 수락) → 서로 친구, 거절·취소·끊기는 알리지 않음, 친구 목록은 본인만. (C-FRIEND-1, FR-054~056)

**Independent Test**: 회원 두 명으로 요청 → 수락 → 목록 → 끊기 (quickstart §4-7 앞부분).

### Tests for User Story 7 ⚠️

- [ ] T123 [P] [US7] `T/account/integration/FriendshipIntegrationTest.java`(`@RecordApplicationEvents`): #1 A `PUT /api/members/{B}/friend` → `{status:REQUEST_SENT}`, B `GET /api/me/friend-requests`에 A, B `PUT /api/members/{A}/friend` → `{status:FRIENDS}`, `GET /api/me/friends` 양쪽에 상대 · #2 B가 맞요청 → 별도 행 없이 ACCEPTED, `FriendAccepted{requesterId=A, accepterId=B}` 1번 · 처음 요청 → `FriendRequested` 1번, 이미 친구·내가 보낸 요청 재전송 → 변화 없음·이벤트 없음(EV-4) · #4 B `DELETE /api/members/{A}/friend`(거절) → 행 삭제, 이벤트 없음 · #5 자기 자신 → 400 `CANNOT_FRIEND_SELF` · #6 친구 목록은 `/api/me/friends`뿐(다른 회원 목록 경로 없음), 비로그인 401 · 없는 주소·탈퇴 유예 회원 → 404 `NOT_FOUND` · `GET /api/members/{handle}/friend` 상태 `SELF`·`NONE`·`REQUEST_SENT`·`REQUEST_RECEIVED`·`FRIENDS` · 목록 커서(`CursorCodec`, 다른 목록 커서 → 400 `INVALID_CURSOR`) · 인증 전 회원도 요청 가능(ACCOUNT_WRITE, R-27).
- [ ] T124 [P] [US7] `T/account/integration/FriendshipConcurrencyIntegrationTest.java`(#3): A→B와 B→A 요청 각 10건 동시 → `friendship` 행 1개, 상태 ACCEPTED, 500 응답 0건.
- [ ] T125 [P] [US7] `F/components/FriendButton.test.tsx`: 상태별 표시 — `SELF` 숨김, `NONE` [친구 요청], `REQUEST_SENT` [요청 취소], `REQUEST_RECEIVED` [수락]·[거절], `FRIENDS` [친구 끊기](확인 후), 비로그인은 클릭 시 로그인 이동, 처리 후 응답 상태로 갱신.

### Implementation for User Story 7

- [ ] T126 [P] [US7] `B/account/domain/Friendship.java`(복합 PK `FriendshipId(memberAId, memberBId)` — 항상 `LEAST/GREATEST`, `requestedBy`, `status`, `createdAt`, `acceptedAt`)와 `FriendshipStatus.java`(PENDING·ACCEPTED)를 만든다(data-model §2-5).
- [ ] T127 [P] [US7] 공유 이벤트 `B/shared/event/FriendRequested.java`(`requesterId, receiverId, requestedAt`)·`FriendAccepted.java`(`requesterId, accepterId, acceptedAt`) record를 만든다(`DomainEvent` 구현, contracts/events.md §1).
- [ ] T128 [US7] `B/account/infra/FriendshipRepository.java`(data-model §2-5의 `INSERT … ON CONFLICT … DO UPDATE … WHERE friendship.status = 'PENDING' AND friendship.requested_by <> :me RETURNING status, (xmax = 0) AS inserted` 네이티브 쿼리, 한 행 조회, 삭제)와 `B/account/infra/FriendListQueryRepository.java`(친구 목록 `UNION ALL` + `member` JOIN **SQL 1번** — media의 `image` 테이블은 JOIN하지 않고 프로필 사진은 T040 `ProfileImageQuery.currentKeysOf(상대 ID 목록)` 1번으로 붙임(constitution II), 정렬 `accepted_at DESC, other_id DESC`, 받은 요청 `status='PENDING' AND requested_by <> :me` `created_at DESC`, 커서 키 `(시각 µs, other_id)`)를 구현한다.
- [ ] T129 [US7] `B/account/application/FriendshipService.java`(`requestOrAccept(me, handle)` — 대상 조회(없음·탈퇴 유예·익명 → 404), 자기 자신 400, `AccountStatusGuard(ACCOUNT_WRITE)`, 결과가 새 행이면 `FriendRequested`, PENDING→ACCEPTED면 `FriendAccepted` 발행; `remove(me, handle)` — 행 삭제·이벤트 없음; `view(me, handle)`; `listFriends`·`listReceivedRequests`(커서))와 공개 `B/account/application/FriendshipQueryService.java`(`boolean areFriends(long a, long b)` — 한 행 조회, 004 친구 공개 선택 구현·US8이 사용)를 구현한다 (T123·T124 통과).
- [ ] T130 [US7] `B/account/web/FriendController.java`: `GET·PUT·DELETE /api/members/{handle}/friend`(`FriendshipView`), `GET /api/me/friends`·`GET /api/me/friend-requests`(`{items, nextCursor}`), DTO `B/account/web/dto/FriendshipViewResponse.java`·`FriendItemResponse.java`·`FriendRequestItemResponse.java`(openapi 스키마, `lastActive`는 US8에서).
- [ ] T131 [P] [US7] `F/api/friends.ts`와 `F/features/friends/useFriendship.ts`·`useFriendLists.ts`(커서 [더 보기], 중복 ID 건너뛰기)를 구현한다.
- [ ] T132 [US7] `F/components/FriendButton.tsx`를 구현한다 (T125 통과). 블로그 머리말 배치는 specs/005가 한다.
- [ ] T133 [US7] `F/pages/SettingsPage.tsx`에 "받은 친구 요청"([수락]·[거절]) 목록과 "내 친구" 목록(프로필 사진 또는 `DefaultAvatar`, 닉네임 `@handle`, [친구 끊기])을 더한다(FR-055·056).

**Checkpoint**: US7 단독 동작.

---

## Phase 10: User Story 8 - 직전 로그인과 친구의 최근 활동을 본다 (Priority: P3)

**Goal**: 계정 화면에 직전 로그인(일자·방식, 없으면 "첫 로그인"), 친구끼리만 "오늘/어제/N일 전/1주 이상" 최근 활동(정확한 시각 없음). (C-ACT-1, FR-057~061)

**Independent Test**: 두 번 로그인 후 계정 화면 확인, 친구·비친구·요청 중 회원으로 같은 회원의 최근 활동 노출 비교 (quickstart §4-6 끝·§4-7).

### Tests for User Story 8 ⚠️

- [ ] T134 [P] [US8] `T/account/domain/LastActiveBucketTest.java`(R-26): Asia/Seoul 달력 날짜 차이 d — 같은 날 23:59/00:01 경계, d=0 `TODAY`, 1 `YESTERDAY`, 2~6 `DAYS_AGO`(days), ≥7 `OVER_A_WEEK`, UTC 자정과 KST 자정이 다른 경우.
- [ ] T135 [P] [US8] `T/account/integration/LastActiveVisibilityMatrixTest.java`(SC-009, US8 #3~#6, FR-060): 보는 사람(비회원·친구 아님·요청 중·친구) × 대상 공개(켬·끔) × 보는 사람 공개(켬·끔) 16가지 표 기반 — 친구+둘 다 켬+값 있음일 때만 `GET /api/members/{handle}/friend`·`GET /api/me/friends`에 `lastActive` 키, 나머지는 **키 자체 없음**(null도 없음), 대상 `last_active_at` NULL이면 없음, 친구 끊기 직후 없음(#6), 응답 JSON 어디에도 `lastActiveAt`·시각 문자열 없음.
- [ ] T136 [P] [US8] `T/account/integration/PreviousLoginAndActivityIntegrationTest.java`: #1 10월 6일 로그인 기록(`last_login_at`) 후 다시 로그인 → `GET /api/me/settings`의 `previousLogin{at: 10월 6일 값, provider}`(방금 로그인 아님) · #2 첫 로그인 → `previousLogin:null` · 다른 회원은 볼 수 없음(본인 `/api/me`만) · 인증된 요청 → `member.last_active_at` 갱신, 1시간 안 두 번째 요청은 UPDATE 없음(`member:active-touch:{id}`), Redis 정지 중 요청 → 갱신 건너뛰고 응답 정상(FR-059·040).
- [ ] T137 [P] [US8] `F/components/LastActiveBadge.test.tsx`: `TODAY` "오늘", `YESTERDAY` "어제", `DAYS_AGO`+3 "3일 전", `OVER_A_WEEK` "1주 이상", 값이 없으면 아무것도 렌더하지 않음.

### Implementation for User Story 8

- [ ] T138 [P] [US8] `B/account/domain/LastActiveBucket.java`(enum + `static Optional<LastActiveView> of(Instant lastActiveAt, Instant now, ZoneId zone)`)와 `B/account/domain/PreviousLogin.java`(record `at`, `provider`)를 구현한다 (T134 통과).
- [ ] T139 [US8] `B/account/infra/redis/ActiveTouchThrottle.java`(`SET member:active-touch:{memberId} 1 NX EX 3600`, 간격 `blog.member.last-active.touch-interval`, Redis 장애 시 false)와 `B/account/infra/security/LastActiveTouchFilter.java`(인증된 요청의 응답 **후** throttle 통과 시 `UPDATE member SET last_active_at = now() WHERE id = ?`, 예외는 경고 로그 후 무시 — constitution V)를 구현하고 `AccountSecurityCustomizer`에 등록한다.
- [ ] T140 [US8] `B/account/application/LastActiveService.java`(설정·갱신 내부용)와 공개 `B/account/application/LastActiveQueryService.java`(`Optional<LastActiveView> lastActiveFor(Long viewerId, long targetId)` — `FriendshipQueryService.areFriends` + 두 사람 `last_active_visible` + 값 있음일 때만, 목록용 배치 메서드 `lastActiveForMany(viewerId, targetIds)` — 쿼리 1번)를 구현한다 (T135 통과). 005 블로그 머리말이 `lastActiveFor`를 호출한다.
- [ ] T141 [US8] 응답에 연결한다: `FriendController`의 `FriendshipView`·`FriendItem`에 조건부 `lastActive`(`@JsonInclude(NON_ABSENT)` — 키 생략), `GET /api/me/settings`의 `previousLogin`을 세션 속성 `previousLoginAt`·`provider`에서 채움(T062에서 저장, FR-057·058).
- [ ] T142 [P] [US8] Frontend: `F/components/LastActiveBadge.tsx`(T137 통과), `F/pages/SettingsPage.tsx` 계정 영역에 "직전 로그인: YYYY.MM.DD HH:mm, 로그인 방식" / "첫 로그인"(Asia/Seoul 표시)과 "최근 활동을 친구에게 보이기" 설명("끄면 나도 친구의 최근 활동을 볼 수 없어요"), 친구 목록 항목에 `LastActiveBadge`. 블로그 머리말 배치는 specs/005.

**Checkpoint**: 모든 User Story 단독 동작.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: 여러 스토리에 걸친 검증·품질.

- [ ] T143 [P] `T/account/integration/RedisOutageIntegrationTest.java`(FR-040, Edge Case H8, quickstart §4-9): Redis 정지 중 — 공개 `GET /api/agreements/current` 200, 기존 세션 `GET /api/me` 401, 이메일·소셜 로그인 503, 인증·재설정 confirm 503, 비밀번호 찾기·재발송 503, 사용 가능 확인 31번 모두 200, 최근 활동 갱신 건너뜀, 복구 후 정상.
- [ ] T144 [P] `T/account/integration/SensitiveDataLoggingIntegrationTest.java`(FR-015): `OutputCaptureExtension`으로 가입·로그인 실패·재설정·변경·인증 확인 전 과정의 로그에 비밀번호 원문·토큰 값·`password_hash`가 나오지 않음, Redis 키에 평문 이메일 없음(`sha256`).
- [ ] T145 [P] `T/account/integration/QueryCountIntegrationTest.java`(plan Performance Goals, N+1 금지): Hibernate Statistics/datasource-proxy로 친구 목록·받은 요청 페이지당 SQL 2번(목록 1 + 프로필 사진 `currentKeysOf` 1), 이메일 로그인 1회 SQL 수(인증 수단 1 + 정지 1 + 동의 1 + `last_login_at` 1), 사용 가능 확인 1~2번, `AccountStatusGuard` 1번.
- [ ] T146 [P] `T/account/contract/AccountApiContractTest.java`: `com.atlassian.oai:swagger-request-validator-mockmvc`로 `specs/001-account-auth/contracts/openapi.yaml`(테스트 리소스로 복사하는 Maven 단계 포함)에 대해 주요 성공·오류 응답(가입·로그인·me·profile·settings·friend)의 형식이 계약과 일치하는지 검사.
- [ ] T147 [P] 화면 접근성·반응형 확인(quickstart §4-12): `/signup`, `/login`, `/signup/social`, `/settings`를 375px에서 가로 스크롤 없음, 비밀번호 규칙 글자 + ✓, 주소 칸 소문자·`-` 차단·한글 자판 입력, 로그아웃 후 IndexedDB `draft:{memberId}:*` 없음·테마 유지 — 결과를 PR 설명에 기록.
- [ ] T148 보안 헤더 전수 확인(12 §11 #7): 모든 화면·API 응답에 CSP·nosniff·Referrer-Policy가 있고 `/signup/social`만 소셜 사진 호스트 예외가 있는지 `SecurityFoundationIntegrationTest`·`SocialSignupPageCspIntegrationTest` 결과와 실제 `docker compose` 응답(curl -I)으로 대조.
- [ ] T149 quickstart.md 전체 시나리오(§2~§4-12)를 `docker compose up -d` 환경에서 실행해 기대 결과와 대조하고, 다른 부분은 이 tasks.md 또는 해당 스펙 담당에게 보고한다.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존 없음. T005는 T001·T003 후.
- **Foundational 2A**: Phase 1 후. **모든 기능(001·002·004·005·006)의 User Story를 막는다.** 내부 순서: T008 → T009·T010 → T011 → T012; 테스트(T017·T020·T022·T024·T025·T033~T035·T041)가 각 구현보다 먼저.
- **Foundational 2B**: T047(목록 파일) 후 T048~T051. 001의 US1·US2·US3·US4·US6만 막는다(US5·US7·US8의 일부 작업은 2A만으로 시작 가능하지만 로그인 흐름이 US1에 있으므로 실질적으로 US1 후).
- **User Stories (Phase 3+)**: 2A·2B 후.
- **Polish (Phase 11)**: 원하는 스토리 완료 후.

### User Story Dependencies

- **US1 (P1)**: 2A·2B 후. 다른 스토리에 의존하지 않는다. (T072는 specs/002 `clearMemberDrafts` 선행)
- **US2 (P1)**: US1의 `SignupService`(T060)·`LoginService`(T062)·`AccountSecurityCustomizer`(T062) 후. T084는 specs/003 presign·complete와 US6 T119 선행.
- **US3 (P1)**: US1 T060(가입 경로)·US2 T080(소셜 가입) 후 T090·T093. 정책은 2B에 있으므로 T086·T089·T091·T092는 US1과 병렬 가능.
- **US4 (P2)**: US1(T058 토큰 저장소·T059 메일·로그인) 후. 2A T038(SessionTerminator) 사용.
- **US5 (P2)**: US1 T062(LoginService) 후. T108의 소셜 경로는 US2 T078 후.
- **US6 (P2)**: US1 후. T118은 specs/004 T009·T013·T016, T121은 specs/003 선행. T122는 US4 T100을 포함한다.
- **US7 (P3)**: US1 후(로그인 세션). T133은 US6 T122(SettingsPage) 후.
- **US8 (P3)**: US7 T129(`FriendshipQueryService`)·T130 후, US6 T119(`GET /api/me/settings`) 후.

### Within Each User Story

- 테스트를 먼저 작성하고 실패를 확인한 뒤 구현한다.
- 엔티티 → 저장소 → Service → 컨트롤러 → 화면 순서.
- 같은 파일을 고치는 작업(`AuthController.java`: T032·T063·T098, `MeController.java`: T064·T098·T109·T119, `SignupService.java`: T060·T080·T090, `LoginService.java`: T062·T108, `AccountSecurityCustomizer.java`: T062·T079·T105·T109·T139, `SettingsPage.tsx`: T122·T133·T142, `LoginPage.tsx`: T069·T085·T110)은 순서대로 진행한다.

### Parallel Opportunities

- Phase 1: T002·T003·T004·T006 병렬.
- Phase 2A: T012·T013·T014·T015·T016 병렬, 테스트 T022·T025·T034·T035·T041 병렬, T019·T030·T031·T040 병렬.
- Phase 2B: 테스트 T043~T046 병렬.
- 2A 완료 후 **002·004·005·006 기능 작업과 001 2B·US1을 동시에** 진행할 수 있다.
- 각 스토리의 [P] 테스트는 모두 병렬. US3의 T086·T091·T092는 US1 구현과 병렬.

---

## Parallel Example: Phase 2A

```bash
# 테스트 기반이 생긴 뒤(T008·T009) 병렬로 먼저 작성:
Task: "FlywayBaselineIntegrationTest in backend/src/test/java/com/team/blog/shared/db/FlywayBaselineIntegrationTest.java"
Task: "GlobalExceptionHandlerTest in backend/src/test/java/com/team/blog/shared/error/GlobalExceptionHandlerTest.java"
Task: "CursorCodecTest in backend/src/test/java/com/team/blog/shared/web/cursor/CursorCodecTest.java"
Task: "RateLimiterIntegrationTest in backend/src/test/java/com/team/blog/shared/infra/ratelimit/RateLimiterIntegrationTest.java"
Task: "SessionResilienceIntegrationTest in backend/src/test/java/com/team/blog/shared/security/integration/SessionResilienceIntegrationTest.java"
Task: "client.test.ts in frontend/src/api/client.test.ts"

# 서로 다른 파일이라 병렬 구현:
Task: "V2__shedlock.sql in backend/src/main/resources/db/migration/V2__shedlock.sql"
Task: "SchedulingConfig in backend/src/main/java/com/team/blog/shared/config/SchedulingConfig.java"
Task: "AsyncConfig + DomainEvent in backend/src/main/java/com/team/blog/shared/{config,event}/"
Task: "CoreProperties + TimeConfig in backend/src/main/java/com/team/blog/shared/config/"
```

## Parallel Example: User Story 1

```bash
# 테스트 먼저 (모두 병렬):
Task: "EmailSignupIntegrationTest in backend/src/test/java/com/team/blog/account/integration/EmailSignupIntegrationTest.java"
Task: "EmailVerificationIntegrationTest in backend/src/test/java/com/team/blog/account/integration/EmailVerificationIntegrationTest.java"
Task: "EmailLoginLogoutIntegrationTest in backend/src/test/java/com/team/blog/account/integration/EmailLoginLogoutIntegrationTest.java"
Task: "PasswordRuleChecklist.test.tsx + logout.test.ts in frontend/src/"

# 서로 다른 파일 구현 병렬:
Task: "MemberAgreement entity in backend/src/main/java/com/team/blog/account/domain/MemberAgreement.java"
Task: "AuthTokenStore in backend/src/main/java/com/team/blog/account/infra/redis/AuthTokenStore.java"
Task: "PasswordRuleChecklist in frontend/src/components/PasswordRuleChecklist.tsx"
Task: "VerifyEmailPage, TermsPage, PrivacyPage in frontend/src/pages/"
```

## Parallel Example: User Story 7·8

```bash
Task: "FriendshipIntegrationTest in backend/src/test/java/com/team/blog/account/integration/FriendshipIntegrationTest.java"
Task: "FriendshipConcurrencyIntegrationTest in backend/src/test/java/com/team/blog/account/integration/FriendshipConcurrencyIntegrationTest.java"
Task: "LastActiveVisibilityMatrixTest in backend/src/test/java/com/team/blog/account/integration/LastActiveVisibilityMatrixTest.java"
Task: "Friendship entity + FriendRequested/FriendAccepted events"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2A 공통 기반(→ 다른 Tier A 기능 착수 가능) → Phase 2B 정책.
2. Phase 3 US1(이메일 가입·인증·로그인·로그아웃).
3. **STOP and VALIDATE**: quickstart §4-1, 인증 전 쓰기 거부(`AccountStatusGuard`).
4. MVP 확장(P1 전체): US2(소셜)·US3(주소·닉네임 화면·동시성)까지 — 세 팀원 공통 로그인 수단이 모두 갖춰진다.

### Incremental Delivery

1. Setup + 2A → 공통 기반 공개(002·004·005·006 병행 시작)
2. 2B + US1 → MVP 데모
3. US2 → US3 → (P1 완료)
4. US4 → US5 → US6 (P2: 계정 복구·보안·프로필)
5. US7 → US8 (P3: 친구·최근 활동)
6. Polish(T143~T149)

### Parallel Team Strategy

1. 함께: Phase 1 + 2A(공통 기반 담당 1명이 주도, 다른 사람은 테스트 작성)
2. 2A 이후: 개발자 A — 001 2B·US1~US3, 개발자 B — 002/004 Foundational, 개발자 C — 005/006
3. 001 US4~US8은 P1 완료 후 나눠 진행

---

## Notes

- [P] = 다른 파일, 미완료 작업에 의존하지 않음. [Story] 라벨은 스토리 추적용.
- 공통 기반 위치·이름(처음엔 plan.md와 달랐으나 2026-10-07 Tier A 교차 분석 `specs/ANALYSIS-tier-a.md`에서 plan.md·002·004·005·006 문서를 이 기준으로 맞춤):
  - `SecurityConfig`를 plan의 `account/infra/security/`가 아니라 `shared/security/`에 두고, account의 폼·소셜 로그인·필터는 `SecurityFilterChainCustomizer`(T026)로 붙인다(공통 기반 소유 규칙: shared/security = 001 Phase 2).
  - `AccountStatusGuard`는 포트를 `shared/security`, 구현을 `account/application`에 둔다(T037). 다른 모듈은 shared에만 의존한다.
  - `ResilientSessionRepository`는 `shared/security/session/`(T028).
  - `RateLimiter`는 `shared/infra/ratelimit/`, `RedisGuard`는 `shared/infra/redis/`(T023). 002는 키·한도만 더한다.
  - `account.domain.Visibility`를 만들지 않고 `member.default_visibility`는 String으로 매핑, 허용 값은 004 `VisibilityRegistry`(T036·T118). enum은 004 T009 `post.domain.Visibility` 하나뿐.
  - `ActionKind.CONTENT_CLEANUP`(T037): 006 삭제·복구·영구 삭제·관리 목록과 007 자기 댓글 삭제는 인증 전에도 허용, 정지·탈퇴 유예는 거부.
  - 탈퇴 유예 회원의 허용 목록 외 `/api/**` 403은 T042a `WithdrawnAccountGateFilter`(spec 004 FR-031, 004 research R-23).
  - 프로필 사진 키는 T040 `ProfileImageKeys(original, thumbnail)`로 둘 다 돌려준다. 작은 사진은 `display()`, og:image는 `original()`. 친구 목록(T128)도 `image` 테이블을 직접 JOIN하지 않고 이 Query를 쓴다(constitution II).
  - plan의 "마이그레이션 없음"과 달리 공통 기반 소유 규칙에 따라 이 tasks.md가 `V1__common_schema.sql`(51 그대로)과 `V2__shedlock.sql`을 만든다(이 기능 고유의 스키마 변경은 여전히 없음).
- 다른 기능이 이 기능의 파일을 확장하는 곳(충돌 주의): 004 T019가 `B/shared/error/GlobalExceptionHandler.java`(T018)의 404 응답에 `Cache-Control: private, no-store`를 연결하고, 004 T024가 `F/api/client.ts`(T042)에 404 `onNotFound` 분기를 더하며, 004 T066 `AdminPathSecurityCustomizer`가 T026의 `SecurityFilterChainCustomizer`를 구현한다. 004·005는 T009의 `MemberFixtures`·`TestLogin`·`RedisOutage`를 공용으로 쓴다.
- 각 작업 또는 논리 묶음마다 커밋하고, 체크포인트에서 스토리를 단독 검증한다.
