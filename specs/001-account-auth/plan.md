# Implementation Plan: 계정·인증 (가입·로그인·블로그 주소·닉네임·프로필·친구·최근 활동)

**Branch**: `001-account-auth` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-account-auth/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

방문자는 이메일·Google·GitHub 세 수단 중 하나로 계정을 만들고(계정 하나 = 로그인 수단 하나), 가입 때 바꿀 수 없는 블로그 주소(`[go-|gi-]본문`)와 닉네임을 정하고 약관·처리방침에 버전과 함께 동의한다. 로그인은 세션 쿠키(Spring Session + Redis, 14일)로 유지되고, 회원은 프로필(닉네임·소개·사진)·계정 설정(비밀번호, 새 글 기본 공개 범위, 최근 활동 공개)을 본인만 바꾸며, 친구를 맺고 친구끼리만 대략적인 최근 활동을 본다(C-AUTH-1·C-AUTH-2·C-FRIEND-1·C-ACT-1, 모두 Tier A).

기술 접근(→ [research.md](./research.md)):

- **로그인**: Spring Security 폼 로그인(`POST /api/auth/login`, JSON 응답 핸들러) + OAuth2 Client(Google OIDC `sub`·`email_verified`, GitHub 숫자 ID·`user:email`). 로그인할 때 세션 ID 재발급, 세션 속성 `previousLoginAt`·`provider`. 처음 소셜 로그인은 계정을 만들지 않고 세션에 10분 보관 → 전체 페이지 `/signup/social`(이 화면만 CSP `img-src`에 소셜 사진 호스트 추가).
- **세션 삭제**: Spring Session의 **인덱스 저장소**(`FindByIndexNameSessionRepository`, principal = 회원 번호)로 "그 회원의 모든 세션"을 찾아 지운다(비밀번호 재설정·변경, 정지·탈퇴는 014·015가 같은 `SessionTerminator` 호출).
- **계정 상태 검사**: 쓰기 요청마다 `AccountStatusGuard`가 DB의 `member.status`·`auth_identity.email_verified_at`을 다시 읽어 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_SUSPENDED`/`ACCOUNT_WITHDRAWN`을 정한다(42 §3 ②, H7). 다른 모듈(002·003·007·009·014)은 이 Guard를 호출한다.
- **검증 정책 한곳**: `HandlePolicy`(+`HandleSuggester`, 08 §3 10단계), `NicknamePolicy`(09 §3 6단계), `BioPolicy`, `PasswordPolicy`, 공용 `BannedWordFilter`(변형 4가지 + 예외 단어). 예약어·금칙어·흔한 비밀번호 목록은 설정 파일.
- **동시성**: 계정 중복은 `uq_auth_identity`, 주소는 `uq_member_handle`, 닉네임은 `uq_member_nickname`(lower), 친구는 PK `(member_a_id, member_b_id)` + `INSERT … ON CONFLICT`가 막고, 위반은 정해진 이유 코드로 바꾼다. 프로필 저장은 `member … FOR UPDATE`로 같은 회원의 저장을 한 줄로 세운다.
- **Redis**: 인증·재설정 토큰(TTL), 재발송·요청 제한·로그인 실패 카운터, 최근 활동 갱신 간격(1시간). Redis 장애 시 02 §2-1 표대로(세션 → 비로그인, 제한 → 통과, 토큰·새 로그인 → 503 거부, 최근 활동 → 건너뜀).
- **메일**: Spring Mail(SMTP, 설정값으로 교체). 개발은 Mailpit. 메일은 커밋 후 비동기로 보내 실패해도 가입·변경은 성공한다.
- **스키마**: 51 통합 V1의 `member`·`auth_identity`·`member_agreement`·`member_suspension`·`friendship`·`image`(PROFILE)를 그대로 쓴다. **새 테이블·컬럼·마이그레이션 없음.**

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript/JavaScript + React (화면)

**Primary Dependencies**: Spring Boot 3.x 이상(팀 확정), Maven, Spring Web MVC, Spring Data JPA, Spring Security(폼 로그인 + OAuth2 Client), Spring Session Data Redis(인덱스 저장소), Spring Data Redis, Spring Mail, Hibernate Validator, Flyway, BCrypt(Spring Security `BCryptPasswordEncoder`), React(+ react-router, IndexedDB/localforage — 로그아웃 때 임시 글 삭제). 사진 업로드는 003의 presign·complete(MinIO, AWS SDK v2)를 호출만 한다. commonmark-java·OWASP Sanitizer는 이 기능에서 쓰지 않는다(소개는 글자만).

**Storage**: PostgreSQL — `member`, `auth_identity`, `member_agreement`, `member_suspension`(읽기 + 만료 해제), `friendship`, `image`(PROFILE 연결, media 모듈 Service 경유). 스키마 기준은 [51 통합 ERD](../../docs/51-erd-unified.md)(원문이 가리키는 `erd/V1__common_schema.sql`은 저장소에 없음 → 51의 SQL 블록이 기준). Redis — 세션, 토큰, 카운터. 브라우저 IndexedDB(로그아웃 때 그 회원의 임시 글 삭제, 키 규칙은 002).

**Testing**: JUnit 5 + Testcontainers(PostgreSQL, Redis) + Spring Security Test + MockMvc. 메일은 테스트용 캡처 `MailSender`(통합 테스트), Mailpit(로컬 수동 검증). OAuth2는 `oauth2Login()` 테스트 지원 + 가짜 사용자 정보로 콜백 이후 흐름 검증. Redis 장애는 Redis 컨테이너 일시 정지로 검증. 정책 클래스는 08 §3·09 §2~§7 예시 표를 그대로 단위 테스트 데이터로 쓴다(SC-007). 화면은 컴포넌트 테스트(팀 프런트 도구, 제안: Vitest + Testing Library).

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO, 개발용 Mailpit), 최신 모바일·데스크톱 브라우저.

**Project Type**: web-service + SPA (모듈러 모놀리스, React 빌드를 같은 도메인에서 서빙)

**Performance Goals**: 공통 기준 목록·상세 300ms 이내(글 1만 건, 02 §6)를 이 기능의 조회 API에도 적용. 로그인 1회 = 인증 수단 조회 1번(`uq_auth_identity`) + 정지·동의 확인 각 1번 + `last_login_at` 갱신 1번. 친구 목록·받은 요청 목록은 커서 페이지당 SQL 1번(회원·프로필 사진 JOIN, N+1 금지). 사용 가능 확인 API는 UNIQUE 인덱스 조회 1~2번. BCrypt 비용은 기본 10(로그인 응답 수십 ms 수준, 제안).

**Constraints**: 비밀번호·토큰은 로그·이벤트에 남기지 않음(FR-015), 로그인 실패·비밀번호 찾기 응답은 가입 여부와 무관하게 같음(SC-004), 현재 사용자는 세션에서만(작성자 ID 파라미터 없음), 소개·닉네임은 이스케이프 출력, 소셜 사진 호스트는 가입 마무리 화면 CSP에만, 비밀값(OAuth Secret·SMTP·MinIO 키)은 환경 변수, Redis 장애 시 읽기 계속, 375px~ 가로 스크롤 없음, 비밀번호 규칙 표시는 글자 + ✓(색만 쓰지 않음).

**Scale/Scope**: REST 작업 28개(인증 8, 소셜 5 — Spring Security 기본 경로 2 포함, 사용 가능 확인 2, 내 정보·프로필·설정·비밀번호·재동의 7, 약관 조회 1, 친구 5 — [contracts/openapi.yaml](./contracts/openapi.yaml)), 화면 10개(가입·로그인·소셜 가입 마무리·인증 결과·비밀번호 찾기/재설정·재동의·설정·약관·처리방침), 공유 이벤트 2개([contracts/events.md](./contracts/events.md)), 다른 모듈에 내주는 공개 Service 5개(`AccountStatusGuard`, `SessionTerminator`, `MemberQueryService`, `FriendshipQueryService`, `LastActiveQueryService`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고 추가만 | **PASS** | 51 통합 V1의 기존 테이블·컬럼·제약(`ck_member_handle`·`ck_member_nickname`·`uq_member_nickname`·`uq_auth_identity`·`uq_auth_identity_member`·`ck_auth_local_email`·`member_agreement` PK·`ck_friendship_order`·`uq_image_profile_current` 등)만 쓴다. 새 테이블·컬럼 없음. 토큰·카운터는 원문대로 Redis(07 §8). 개인 확장(모든 기기 로그아웃 버튼, 이전 닉네임 묶어 두기, 소개 외부 링크)은 넣지 않는다. |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | 모든 코드는 `account` 모듈. 프로필 사진 연결·해제와 사진 주소는 media의 공개 Service(`ProfileImageService`, `ImageUrlResolver`)로, 로그아웃 때 브라우저 임시 글 삭제는 002의 프런트 모듈 함수로 한다. 다른 모듈은 account의 공개 Service(`AccountStatusGuard` 등)만 호출한다. `member_suspension` 소유 모듈은 014와 맞춰야 한다(research R-31, 팀 확인 필요) — 이 기능은 account 모듈의 `SuspensionService`로만 읽고 만료 해제한다. |
| III. 권한 두 겹, 볼 수 없으면 404 | **PASS** | 수정 API는 모두 `/api/me/...`로 주소·본문에 회원 ID가 없고 요청의 다른 ID는 무시(FR-047). 계정 상태 판정은 Service(`AccountStatusGuard`)에서 DB 기준으로 매번(42 §3 ②). 친구 대상이 없거나 탈퇴 유예·익명 처리면 404. 친구 목록·받은 요청·직전 로그인은 본인만. 최근 활동은 판정 4조건을 모두 만족할 때만 응답에 넣고 아니면 필드 자체를 뺀다(FR-060, SC-009). 화면 버튼 숨김은 보조. |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 소개·닉네임은 글자만 받고 React 텍스트 노드로 출력(HTML·Markdown 해석 없음, 자동 링크 없음). CSP는 공통 헤더 + 소셜 가입 마무리 화면만 `img-src`에 두 호스트 추가(화면별 CSP, 전체 페이지 진입·이탈). OAuth Client Secret·SMTP 비밀번호는 환경 변수. 서버는 외부 사진 주소를 요청하지 않는다(SSRF 없음). |
| V. 부가 기능의 실패가 핵심을 막지 않음 | **PASS** | 인증·알림 메일은 커밋 후 비동기 발송(실패해도 가입·변경 성공, 재발송 가능). 소셜 사진 복사 실패는 가입 성공 + 기본 이미지. 최근 활동 갱신 실패·Redis 장애는 건너뜀. Redis 장애 시 세션을 못 읽으면 비로그인으로 읽기 계속, 보안상 필요한 것(새 로그인·토큰 확인)만 503으로 거부(02 §2-1, H8). 친구 이벤트는 커밋 후 발행, 구독자가 없어도 동작. |
| VI. 데이터를 잃지 않고 정책대로 지움 | **PASS** | 계정 생성과 동의 기록은 한 트랜잭션(FR-010). 프로필은 하나라도 실패하면 아무것도 저장하지 않음(FR-052). 이전 프로필 사진은 `detached_at` 기록 → 7일 뒤 003 정리 배치가 삭제, 연결된 사진은 지우지 않음. 로그아웃 때 IndexedDB 삭제는 공용 PC 결정(04 §6-2 #4)이며, 삭제 전에 미전송 작업을 한 번 보내 보는 것을 002와 맞춘다(제안, research R-28). 스키마 변경 없음 → Flyway 파일 추가 없음. |
| VII. 수치는 설정값으로 | **PASS** | 세션 14일, 실패 5회·잠금 15분, IP 1분 20회, 인증 24시간·재설정 30분, 재발송 1분 1번·하루 10번, 재설정 요청 제한, 사용 가능 확인 1분 30번, 소셜 보관 10분, 닉네임 30일, 소개 200자·4줄, 최근 활동 갱신 1시간, 약관·처리방침 현재 버전, 예약어·금칙어·흔한 비밀번호 목록, 서비스 시간대(Asia/Seoul)를 모두 `blog.*` 설정값·설정 파일로 둔다(data-model.md §6). 닉네임 2~10자·주소 형식처럼 DB CHECK와 같아야 하는 값은 코드 상수(바꾸면 CHECK도 바꿔야 하므로). |
| VIII. 실제 DB 통합 테스트 | **PASS** | 권한·데이터 규칙(인증 전 403, 정지 403, 본인만 수정, 친구 목록 본인만, 최근 활동 노출 조합 전체, 동시 가입·동시 닉네임·동시 맞요청)을 Testcontainers PostgreSQL + Redis로 검증한다. US1~US8의 Acceptance Scenario 56개를 테스트 1개 이상으로 옮긴다(quickstart.md 매핑). |

**Post-design 재확인 (Phase 1 이후)**: data-model.md·contracts/openapi.yaml·contracts/events.md·quickstart.md를 작성한 뒤 다시 확인했다. **새로 생긴 위반 없음.** 설계 중 더해진 것은 (a) 원문에 없는 API 경로·이유 코드(가입·로그인·인증·재설정·친구·재동의 등, 모두 "제안" 표시), (b) 재동의 전 요청을 막는 403 `REAGREEMENT_REQUIRED`, (c) 요청 제한 429·Redis 장애 503 응답, (d) Redis 키 이름 규칙이며, 원칙 I(스키마 변경 없음)·III(본인만, 판정 후 응답)·V(장애 격리)·VII(설정값)을 지킨다. 원칙 II의 `member_suspension` 소유 모듈은 014 계획과 맞출 항목으로 남긴다(위반 아님).

## Project Structure

### Documentation (this feature)

```text
specs/001-account-auth/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   ├── openapi.yaml     # Phase 1 output — 인증·가입·프로필·설정·친구 REST API
│   └── events.md        # Phase 1 output — FriendRequested·FriendAccepted (20 §3-7)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── account/
│   ├── web/            AuthController        POST /api/auth/signup, /email-verification(·/confirm),
│   │                                         /password-reset(·/confirm), GET /api/auth/csrf
│   │                   SocialSignupController GET·POST /api/auth/social-signup, GET /api/auth/social-login-error
│   │                   AvailabilityController GET /api/handles/availability, /api/nicknames/availability
│   │                   MeController          GET /api/me, PATCH·GET /api/me/profile, GET·PATCH /api/me/settings,
│   │                                         POST /api/me/password, PUT /api/me/agreements
│   │                   AgreementController   GET /api/agreements/current
│   │                   FriendController      GET·PUT·DELETE /api/members/{handle}/friend,
│   │                                         GET /api/me/friends, GET /api/me/friend-requests
│   │                   SocialSignupPageCspFilter (/signup/social 응답에만 img-src 추가)
│   ├── application/    SignupService, EmailVerificationService, PasswordResetService, PasswordChangeService,
│   │                   LoginService(성공·실패 처리, 정지 확인·만료 해제, 재동의 판정, previousLoginAt),
│   │                   SocialLoginService(PendingSocialSignup), ProfileService, AccountSettingsService,
│   │                   AgreementService, FriendshipService, LastActiveService,
│   │                   AccountStatusGuardService(포트 shared/security/AccountStatusGuard·ActionKind),
│   │                   SessionTerminator, MemberQueryService,                          ← 다른 모듈에 공개
│   │                   FriendshipQueryService, LastActiveQueryService, SuspensionService ← 다른 모듈에 공개
│   │                   policy/ HandlePolicy, HandleSuggester, NicknamePolicy, BioPolicy, PasswordPolicy,
│   │                           BannedWordFilter, ReservedWords
│   │                   mail/   AccountMailService(@Async, 커밋 후), MailTemplates
│   ├── domain/         Member(default_visibility는 String, 004 VisibilityRegistry로 검증), MemberStatus, Role, AuthIdentity, Provider,
│   │                   MemberAgreement, AgreementType, MemberSuspension, Friendship, FriendshipStatus,
│   │                   LastActiveBucket, PreviousLogin
│   └── infra/          MemberRepository, AuthIdentityRepository, MemberAgreementRepository,
│                       MemberSuspensionRepository, FriendshipRepository(ON CONFLICT 네이티브 쿼리),
│                       FriendListQueryRepository(회원 JOIN 1번, 사진은 media ProfileImageQuery 1번),
│                       security/ AccountSecurityCustomizer(shared/security SecurityConfig에 붙음), JsonLoginSuccessHandler, JsonLoginFailureHandler,
│                                 LoginRateLimitFilter, MemberUserDetailsService, OAuth2MemberUserService,
│                                 OidcMemberUserService, OAuth2LoginSuccessHandler, SafeRedirectResolver,
│                                 ReagreementGateFilter, WithdrawnAccountGateFilter, LastActiveTouchFilter
│                       redis/    AuthTokenStore, LoginFailureCounter, ActiveTouchThrottle (RateLimiter는 shared/infra/ratelimit)
├── media/application/  ProfileImageService.attach(memberId, imageId)·detach(memberId),
│                       ImageUrlResolver, ProfileImageQuery(ProfileImageKeys 원본·썸네일) ← 003 소유, 이 기능은 호출만
└── shared/
    ├── error/          ErrorResponse{code,message,errors,details}, GlobalExceptionHandler (기존 공용)
    ├── security/       CurrentUser (세션의 회원 번호), @LoginRequired, SecurityConfig·SecurityFilterChainCustomizer,
    │                   AccountStatusGuard·ActionKind(CONTENT_WRITE·ACCOUNT_WRITE·CONTENT_CLEANUP), session/ResilientSessionRepository
    ├── infra/          redis/RedisGuard, ratelimit/RateLimiter
    ├── web/            ClientIp (RemoteIpValve 결과 사용), RetryAfter
    └── event/          FriendRequested, FriendAccepted (record)

backend/src/main/resources/
├── application.yml     blog.auth.*, blog.member.*, blog.agreement.*, blog.availability.*, blog.time-zone,
│                       spring.session.*, spring.security.oauth2.client.*, spring.mail.*,
│                       server.forward-headers-strategy, server.tomcat.remoteip.*
├── policy/             reserved-handles.txt, reserved-nicknames.txt, banned-words.txt,
│                       banned-words-exceptions.txt, common-passwords.txt
├── mail/               verify-email, password-reset, password-reset-social-only, password-changed 템플릿
└── db/migration/       (이 기능은 마이그레이션 없음 — 공통 V1 기준선 사용)

backend/src/test/java/com/team/blog/account/
├── application/policy/ HandlePolicyTest, HandleSuggesterTest(08 §3 예시 12개), NicknamePolicyTest(09 예시),
│                       BioPolicyTest, PasswordPolicyTest, BannedWordFilterTest              (unit)
├── application/        LastActiveBucketTest (Asia/Seoul 날짜 경계)                         (unit)
└── integration/        EmailSignupIntegrationTest, EmailVerificationIntegrationTest, LoginSecurityIntegrationTest,
                        SocialSignupIntegrationTest, PasswordResetIntegrationTest, PasswordChangeIntegrationTest,
                        ProfileUpdateIntegrationTest, AccountStatusGuardIntegrationTest, ReagreementIntegrationTest,
                        TrustedProxyIntegrationTest, FriendshipIntegrationTest, LastActiveVisibilityMatrixTest,
                        RedisOutageIntegrationTest, ConcurrencyIntegrationTest               (integration)

frontend/src/
├── pages/              SignupPage, LoginPage, SocialSignupPage(전체 페이지), VerifyEmailPage,
│                       ForgotPasswordPage, ResetPasswordPage, ReagreementPage, SettingsPage,
│                       TermsPage, PrivacyPage
├── components/         PasswordRuleChecklist(글자 + ✓), HandleInput(접두어 고정·소문자·두벌식→영문),
│                       NicknameInput, AvailabilityHint(0.5초 지연), DefaultAvatar(SVG, 8색),
│                       ProfileImageCropper, FriendButton, LastActiveBadge
├── features/
│   ├── auth/           useSession, logout(→ 002 clearMemberDrafts 호출 후 홈 이동), safeRedirect,
│   │                   (CSRF: XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더는 api/client.ts가 붙임)
│   ├── handle/         prefillHandleFromEmail (08 §3 ①~⑧, 서버와 같은 예시로 테스트)
│   ├── profile/        socialPhotoImport (5초 제한, 가운데 정사각형 256×256 WebP → 003 업로드)
│   └── friends/        friend 상태·목록 훅
└── api/                client.ts(공통 클라이언트, 이 기능 T042 소유 · 004 T024가 404 분기 추가),
                        auth.ts, me.ts, friends.ts, availability.ts

docker-compose.yml       app + PostgreSQL + Redis + MinIO (+ 개발용 mailpit 서비스, 02 §2 "개발: Mailpit(Docker)")
```

**Structure Decision**: 공통 구조(02 §3 package-by-feature, `backend/` + `frontend/`)를 그대로 쓴다. 회원·인증·프로필·친구·최근 활동은 모두 `account` 모듈에 둔다(02 §3 "account: 회원·인증 식별·프로필"). 친구 관계는 공통 C-FRIEND-1이므로 별도 모듈을 만들지 않고 account에 두고, 친구 공개(`FRIENDS`) 선택 구현자는 02 §7대로 자기 `friend`/`VisibilityRule` 쪽에서 account의 `FriendshipQueryService`를 호출한다. 프로필 사진 행(`image`)은 media 모듈 소유이므로 account는 `ProfileImageService`를 같은 트랜잭션 안에서 호출만 한다. 블로그 주소 페이지(`/@{handle}`)의 301·404 첫 응답은 005의 `PageShellController`가 만들고, account는 `MemberQueryService.findReadableBlogOwner(handle)`과 소문자 정규화 규칙을 제공한다.

## Complexity Tracking

> 원칙 위반 없음 — 이 표는 비워 둔다.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (없음) | | |
