# Research: 계정·인증

**Feature**: `001-account-auth` | **Date**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

표기:
- **확정** — 원문(docs) 결정, README "정해진 것", spec Clarifications로 이미 정해진 것. 근거 절 번호를 함께 적는다.
- **제안(팀 확인 필요)** — 원문에 없어 이 계획이 고른 기본값.
- **미결 — 기본안** — 원문이 "나중에 정함"으로 남긴 것. 기본안으로 계획을 진행한다.

spec.md에는 `[NEEDS CLARIFICATION]`이 남아 있지 않다(2026-10-07 Clarifications 3개로 해소). 따라서 Technical Context에도 NEEDS CLARIFICATION이 없다.

---

## A. 기반

### R-01. 기술 스택 — 확정

- **Decision**: Java 21, Spring Boot(3.x 이상, 팀 확정), Maven, Spring Data JPA, Spring Security(폼 로그인 + OAuth2 Client), Spring Session Data Redis, Flyway, PostgreSQL(pg_trgm), Redis(복제 + 자동 전환), MinIO(AWS SDK v2, 이 기능은 003 경유), Spring Mail, React(+ IndexedDB/localforage), JUnit 5 + Testcontainers + Spring Security Test, Docker Compose.
- **Rationale**: 02 §2 기술 스택 표, 07 §6, constitution "기술 제약". 세 사람이 같은 Service·테스트를 공유한다.
- **Alternatives considered**: JWT 인증 — 공통 코드에서 금지(02 §5, H7). 개인 서비스에서 쓰려면 Refresh 토큰 Redis 저장·교체 + 토큰 버전 확인을 갖춰야 한다. H2 테스트 DB — 금지(02 §6, constitution VIII).

### R-02. 스키마 기준과 마이그레이션 — 확정

- **Decision**: [51 통합 ERD](../../docs/51-erd-unified.md)의 "ERD 변경 제안" SQL 블록(= 원문이 말하는 `erd/V1__common_schema.sql`)을 공통 Flyway 기준선 V1로 쓰고, 이 기능은 **마이그레이션을 추가하지 않는다.** `erd/V1__common_schema.sql` 파일은 이 저장소에 없으므로(README "알려진 누락") 51의 SQL 블록이 기준이다.
- **Rationale**: 필요한 컬럼(`member.last_active_at`·`last_active_visible`, `auth_identity.last_login_at`·`email_verified_at`, `member_agreement.version`, `friendship`, `uq_image_profile_current`)이 모두 V1에 있다. constitution I·VI. V1 뼈대는 공통 시작 템플릿(O9, 01 결정 기록 2026-10-07)에 들어간다.
- **Alternatives considered**: 토큰·재발송 횟수용 테이블 — 07 §8이 "테이블 대신 Redis(TTL)"로 결정.

### R-03. 세션 저장소·유지 기간·회원별 세션 삭제 — 확정 + 제안

- **Decision (확정)**: Spring Session Data Redis, 마지막 활동부터 14일(`server.servlet.session.timeout=14d` 상당), 쿠키 `HttpOnly`·`Secure`·`SameSite=Lax`, 로그인 때 세션 ID 재발급, 정지·탈퇴·비밀번호 변경·재설정 때 그 회원 세션 삭제 (07 L-6·§6, 02 §2·§5).
- **Decision (제안, 팀 확인 필요)**: 저장소 종류를 **인덱스 저장소**(`spring.session.redis.repository-type=indexed`, `RedisIndexedSessionRepository`)로 하고 principal 이름 = 회원 번호(문자열)로 둔다. `SessionTerminator.terminateAll(memberId, exceptSessionId?)`가 `findByPrincipalName`으로 그 회원 세션을 찾아 지운다. 014(정지)·015(탈퇴)도 이 Service를 호출한다.
- **Rationale**: 기본 저장소(`RedisSessionRepository`)는 회원별 세션 목록을 찾을 수 없어 "모든 기기 로그아웃"(FR-044·045, 42 P-7)을 구현할 수 없다.
- **Alternatives considered**: 회원별 "세션 버전" 숫자를 두고 요청마다 비교 — 스키마 추가(원칙 I)나 별도 Redis 키가 필요하고 세션이 남아 있어 혼란스럽다. 회원 행에 버전 컬럼 추가는 원칙 I 위반.

### R-04. 이메일 로그인 처리 방식 — 확정 + 제안

- **Decision (확정)**: Spring Security 폼 로그인 + BCrypt (02 §2 "인증", 07 §4·§6).
- **Decision (제안, 팀 확인 필요)**: `loginProcessingUrl("/api/auth/login")`, 요청은 `application/x-www-form-urlencoded`(`email`, `password`, `redirect`), 성공·실패 핸들러가 공통 오류 형식 JSON을 돌려준다(React가 화면 이동을 결정). 실패 제한·IP 제한은 인증 필터 앞의 `LoginRateLimitFilter`, 정지 확인·만료 해제·재동의 판정·`previousLoginAt` 저장은 성공 처리(`LoginService.onSuccess`)에서 한 트랜잭션으로 한다. 정지 계정은 비밀번호가 맞은 뒤에만 정지 안내를 준다(비밀번호가 틀리면 일반 실패 문구).
- **Rationale**: 폼 로그인을 그대로 써서 세션 고정 방지·SecurityContext 저장을 Spring Security 기본 동작에 맡긴다.
- **Alternatives considered**: JSON 본문을 받는 직접 만든 로그인 컨트롤러 — 세션 전략·컨텍스트 저장을 손으로 맞춰야 해서 실수 여지가 크다.

### R-05. CSRF 토큰 저장 방식 — 미결 — 기본안 (M17)

- **Decision (기본안)**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` — 쿠키 `XSRF-TOKEN`(HttpOnly 아님, `SameSite=Lax`, `Secure`), React가 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)에 헤더 `X-XSRF-TOKEN`으로 보낸다. 앱 첫 진입 때 `GET /api/auth/csrf`(204)로 쿠키를 받게 한다. Spring Security 6의 SPA용 요청 처리기(BREACH 대응 XOR 토큰 처리 포함)를 쓴다.
- **Rationale**: 02 §5 "토큰 저장 방식은 M17에서 확정". 쿠키 방식은 Redis 장애 때도 토큰 확인이 세션에 기대지 않고, React SPA 표준 패턴이다. 로그인할 때 토큰을 새로 발급한다(Spring Security 기본).
- **Alternatives considered**: 세션 저장(`HttpSessionCsrfTokenRepository`) + 토큰 조회 API — Redis 장애 때 비로그인 요청(로그인 자체 등)의 토큰도 확인할 수 없다.

### R-06. 소셜 로그인 — 확정

- **Decision**: Spring Security OAuth2 Client. Google은 OIDC(`openid email profile`), 식별값 `sub`, `email_verified = true`인 이메일만 사용. GitHub은 `read:user user:email`, 식별값 숫자 `id`, `/user/emails`에서 `primary && verified` 이메일, 로그인 이름(`login`)은 식별에 쓰지 않음. `state`는 Spring Security 기본 검증. 이미 연결된 `(provider, provider_user_id)`면 로그인, 처음이면 가입 마무리 화면. 네이버는 제외(`provider` 값만 추가하면 나중에 붙일 수 있음). (07 §5, L-5, FR-003·008·030)
- **Rationale**: 소셜 이메일·로그인 이름은 바뀔 수 있어 고유 ID로 식별한다(07 §2).
- **Alternatives considered**: 이메일로 자동 연결 — L-1에서 거부(계정 하나 = 로그인 수단 하나, 계정 연결 없음).

### R-07. 소셜 가입 대기 정보 — 확정 + 제안

- **Decision (확정)**: 마무리 전에는 계정을 만들지 않고 소셜 인증 정보를 세션에 10분만 보관(07 §5, FR-030). 마무리 화면은 소셜 콜백 리다이렉트로 전체 페이지로 열고 가입을 마치면 전체 페이지 이동으로 나간다(12 §8, H3).
- **Decision (제안)**: 세션 속성 `pendingSocialSignup = {provider, providerUserId, email, emailVerified, displayName, pictureUrl, createdAt}`. 이 상태에서는 SecurityContext를 **익명으로 둔다**(로그인된 것으로 보지 않음). 10분이 지나면 `POST /api/auth/social-signup`이 410 `SOCIAL_SIGNUP_EXPIRED`로 거부하고 다시 소셜 로그인으로 안내한다. 사진 주소는 서버가 호스트(`lh3.googleusercontent.com`, `avatars.githubusercontent.com`)와 HTTPS를 확인한 뒤에만 화면에 넘기고 크기 매개변수(Google `=s256-c`, GitHub `&s=256`)를 붙인다(11 §4-2).
- **Rationale**: 대기 정보가 Redis 세션에 있으므로 서버를 늘려도 유지된다. 익명으로 두어야 가입 전 사람이 쓰기 API를 부를 수 없다.
- **Alternatives considered**: 임시 계정 행을 먼저 만들기 — 07 §5 "마무리 전 계정 미생성"에 어긋난다.

### R-08. 같은 이메일의 다른 수단 계정 안내 (FR-033) — 확정 (2026-10-07 Clarification, README "정해진 것")

- **Decision**: 소셜로 처음 로그인했을 때 그 제공자가 **확인한** 이메일(`email_verified`/GitHub `verified`)과 같은 `auth_identity.email`을 가진 다른 수단 계정이 있으면 마무리 화면에 "이 이메일로 가입한 계정이 이미 있어요" + [기존 계정으로 로그인]·[새 계정 만들기]를 보인다. 조회는 `ix_auth_identity_email`. 자동 합치기·연결은 없다(L-1). GitHub에서 사용자가 직접 입력한 이메일은 확인 전이라 안내하지 않는다.
- **Rationale**: 07 §10 F-1. 안내는 그 이메일 주인에게만 보이므로 가입 여부가 남에게 드러나지 않는다.
- **Alternatives considered**: 안내 없음 — 모르고 블로그를 하나 더 만드는 실수가 생긴다.

---

## B. 가입·식별·검증

### R-09. 계정 식별·중복 방지 — 확정

- **Decision**: 이메일 가입은 `(LOCAL, 소문자 이메일)`, 소셜은 `(GOOGLE|GITHUB, 고유 ID)`로 `uq_auth_identity`가 막고, 회원당 수단 하나는 `uq_auth_identity_member`, `LOCAL`의 식별값 = 소문자 이메일은 `ck_auth_local_email`이 보장한다(07 §2·§8, 51 §3). 가입은 `member` INSERT → `auth_identity` INSERT → `member_agreement` 2행 INSERT를 한 트랜잭션에서 하고, UNIQUE 위반(SQLSTATE 23505)은 제약 이름으로 칸별 이유 코드에 매핑해 400 `VALIDATION_FAILED`의 `errors[]`로 돌려준다(선조회에서 걸린 경우와 같은 응답): `uq_auth_identity` → `email`/`EMAIL_ALREADY_REGISTERED`(소셜 마무리에서는 이미 연결된 계정이므로 그 계정으로 로그인 처리), `uq_member_handle` → `handle`/`HANDLE_DUPLICATE` + `details.handleSuggestion`("방금 다른 분이 이 주소를 사용했어요. `…`는 어떠세요?"), `uq_member_nickname` → `nickname`/`NICKNAME_DUPLICATE`("방금 다른 분이 이 닉네임을 사용했어요"). 한 요청의 칸 오류는 모두 모아 한 번에 돌려준다(제안).
- **Rationale**: 동시에 20건을 보내도 계정 1개(SC-001)는 DB만이 보장한다. 화면 검사·선조회는 안내용이다.
- **Alternatives considered**: 애플리케이션 락 — 서버를 늘리면 깨진다.

### R-10. 이메일 정규화·형식 — 확정 + 제안

- **Decision (확정)**: 앞뒤 공백 제거 + 소문자, 최대 254자, 일반 이메일 형식(07 §3, FR-003·004).
- **Decision (제안)**: 형식 검사는 Hibernate Validator `@Email` + 길이 254 + `@` 1개·도메인에 `.` 1개 이상. 국제화 도메인·따옴표 지역부는 받지 않는다. 오류 코드 `EMAIL_INVALID_FORMAT`.
- **Rationale**: "일반 형식"을 서버·화면이 같은 규칙으로 판단하게 한다.
- **Alternatives considered**: RFC 5322 전체 허용 — 메일 발송 실패·표시 문제가 더 크다.

### R-11. 인증·재설정 토큰 — 확정 + 제안

- **Decision (확정)**: 32바이트 `SecureRandom` → Base64URL(패딩 없음). 인증 `auth:verify:{토큰}` = memberId TTL 24시간, 재설정 `auth:reset:{토큰}` = memberId TTL 30분, 사용하면 삭제. 재발송 하루 카운터 `auth:verify-resend:{memberId}:{yyyyMMdd}`. 새 인증 메일을 보내면 이전 링크 무효(07 §3·§4-1, FR-005·006·042).
- **Decision (제안, 팀 확인 필요)**:
  - "이전 링크 무효"는 회원별 최신 토큰 포인터 `auth:verify-latest:{memberId}`(TTL 24시간)로 구현한다. 새 토큰을 만들 때 이전 토큰 키를 지운다. 재설정도 같은 방식(`auth:reset-latest:{memberId}`)으로 최신 링크 하나만 살린다.
  - 사용은 `GETDEL`(원자적)로 한다 → 같은 링크를 동시에 두 번 눌러도 한 번만 성공(SC-006).
  - 메일 링크는 화면 주소(`/verify-email?token=…`, `/reset-password?token=…`)이고, 화면이 `POST …/confirm {token}`을 보낸다. 메일 보안 검사기가 링크를 미리 열어도(GET) 토큰이 소모되지 않게 하기 위해서다.
  - 접근 로그·오류 로그에서 `token` 쿼리 값을 가린다(FR-015). `Referrer-Policy: strict-origin-when-cross-origin`(02 §5)으로 외부 유출을 막는다.
- **Rationale**: 07 §3 "새 메일을 보내면 이전 링크는 무효"를 키 하나 삭제로 보장한다.
- **Alternatives considered**: 토큰 DB 테이블 — 07 §8 결정(Redis TTL)에 어긋나고 스키마 추가가 필요하다.

### R-12. 요청 제한·로그인 잠금 — 확정(수치) + 제안(키·응답)

- **Decision (확정 수치)**: 같은 계정 5회 연속 실패 → 15분 잠금, 같은 IP 로그인 1분 20회(07 L-7), 인증 메일 재발송 회원당 1분 1번·하루 10번(L-10), 비밀번호 찾기 같은 이메일 1분 1번·하루 10번·같은 IP 1시간 20번(07 §4-1), 주소·닉네임 사용 가능 확인 같은 IP 1분 30번(08 §4-2, 09 §6), 비밀번호 변경 현재 비밀번호 5회 실패 → 15분(11 §6-2). Redis 장애 시 모두 통과(02 §2-1).
- **Decision (제안, 팀 확인 필요)**:
  - 로그인 실패 카운터는 **정규화한 이메일 기준 키**(`auth:login-fail:{sha256(email)}`, TTL 15분)로, **가입 여부와 상관없이** 센다. 없는 이메일도 5회 실패하면 같은 잠금 응답을 준다. 그래야 "잠금 응답이 나오면 가입된 이메일"이라는 추측이 불가능하다(SC-004). 성공하면 카운터를 지운다.
  - 키 이름: `rl:login:ip:{ip}`(60초), `rl:verify-resend:{memberId}`(60초), `rl:reset:email:{sha256(email)}`(60초)·`rl:reset:email-day:{sha256(email)}:{yyyyMMdd}`, `rl:reset:ip:{ip}`(1시간), `rl:availability:handle:ip:{ip}`·`rl:availability:nickname:ip:{ip}`(60초), `auth:pw-change-fail:{memberId}`(15분). 이메일은 해시로 넣어 Redis에 평문 개인 정보를 남기지 않는다.
  - 응답: 잠금·제한 초과는 **429** + `Retry-After` + 이유 코드(`LOGIN_TEMPORARILY_LOCKED`, `TOO_MANY_REQUESTS`). 화면 문구는 "잠시 후 다시 시도해 주세요(약 15분)". 비밀번호 찾기의 이메일 제한 초과도 가입 여부와 무관하게 같은 429다.
- **Rationale**: 04·21·30이 이미 429 + `Retry-After`를 쓴다(002·007·009 spec). 42 §4 표에는 429가 없으나 요청 제한은 권한 판정이 아니라 별도 응답이다.
- **Alternatives considered**: 계정이 있을 때만 잠금 — 가입 여부 노출. 회원 번호 기준 키 — 없는 이메일과 응답이 달라진다.

### R-13. 클라이언트 IP·HTTPS 판별 — 확정 (대역 값은 배포 확인)

- **Decision**: `server.forward-headers-strategy=native` + Tomcat `RemoteIpValve`(`server.tomcat.remoteip.internal-proxies` = 로드 밸런서 내부 대역 정규식, `remote-ip-header=x-forwarded-for`, `protocol-header=x-forwarded-proto`). RemoteIpValve는 신뢰 프록시에서 온 요청일 때만 `X-Forwarded-For`를 오른쪽부터 읽어 **가장 오른쪽 신뢰 밖 주소**를 `request.getRemoteAddr()`로 정하고, 그 밖의 출처가 보낸 헤더는 무시한다. 모든 "같은 IP" 제한은 이 값(`ClientIp`)을 쓴다(02 §5, H4, FR-037). 로컬·테스트는 기본값(사설 대역)으로 두고 운영 NHN LB 대역은 배포 담당이 확인한다.
- **Rationale**: 직접 헤더를 파싱하지 않고 검증된 구현을 쓴다. SC-010(위조 헤더로 우회 0%)을 통합 테스트로 확인한다.
- **Alternatives considered**: `framework` 전략(`ForwardedHeaderFilter`) — 신뢰 대역 개념이 없어 위조 헤더를 그대로 믿는다.

### R-14. 비밀번호 정책·저장 — 확정 + 제안

- **Decision (확정)**: 8~16자, 영문 대소문자 1개 이상·숫자 1개 이상·지정 특수문자 1개 이상, 허용 문자는 영문·숫자·지정 특수문자뿐, 이메일 `@` 앞부분 포함 금지, 흔한 비밀번호 금지, BCrypt 저장, 원문·토큰 로그 금지(07 §4, L-3, FR-013~015). 정책은 `PasswordPolicy` 한곳(가입·재설정·변경 공용).
- **Decision (제안, 팀 확인 필요)**:
  - "이메일 앞부분 포함"은 `+` 앞까지의 지역부를 소문자로 바꿔 비밀번호(소문자)에 포함되는지로 본다. 지역부가 3자 미만이면 검사하지 않는다(`ab@…` 때문에 `ab`가 든 비밀번호를 모두 막지 않게). 소셜 계정은 비밀번호가 없어 해당 없음.
  - 흔한 비밀번호 목록은 `policy/common-passwords.txt`(대소문자 무시 완전 일치). 공개 유출 목록 상위 항목 중 우리 규칙을 통과하는 것(예: `Password1!`, `Qwer1234!`)을 팀이 검토해 넣는다.
  - 오류 코드: `PASSWORD_INVALID_LENGTH`, `PASSWORD_MISSING_CHAR_TYPE`, `PASSWORD_INVALID_CHAR`, `PASSWORD_CONTAINS_EMAIL`, `PASSWORD_TOO_COMMON`, `PASSWORD_CONFIRM_MISMATCH`(원문 코드 `PASSWORD_NOT_SUPPORTED`·`PASSWORD_SAME_AS_CURRENT`·`CURRENT_PASSWORD_MISMATCH`와 함께).
  - BCrypt 비용 10(Spring 기본). 16자 상한이라 BCrypt 72바이트 제한에 걸리지 않는다.
- **Rationale**: 화면의 규칙별 ✓ 표시(FR-014)와 서버 오류 코드가 1:1로 맞는다.
- **Alternatives considered**: Argon2 — 팀 결정이 BCrypt(02 §2, 07 §4).

### R-15. 블로그 주소 정책·미리 채우기 — 확정 + 제안

- **Decision (확정)**: 형식 `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$`(DB CHECK와 같음), 접두어와 가입 수단 일치는 Service에서, 예약어는 접두어를 뺀 본문 기준, 금칙어 필터 적용, 미리 채우기 10단계, 가입 후 변경 불가, 대문자 접속 301, 없는 주소 404, 탈퇴 주소 재사용 금지(08 §2~§6, FR-016~021).
- **Decision (제안, 팀 확인 필요)**:
  - **서버**: `HandlePolicy.validate(handle, provider)`와 `HandleSuggester.suggest(email, provider)`(①~⑩ 전부, ⑩은 `uq_member_handle` 조회). 소셜 마무리 화면의 미리 채운 값과 사용 가능 확인의 `suggestion`은 서버가 만든다.
  - **화면(이메일 가입)**: ①~⑧(순수 함수)만 브라우저에서 계산해 칸을 채우고, 0.5초 뒤 `GET /api/handles/availability`가 예약어·중복이면 `suggestion`(⑩ 결과)으로 바꿔 채운다. ⑧의 난수 6자리는 화면이 만든다. 서버·화면 구현은 08 §3 예시 12개를 같은 테스트 데이터로 쓴다(SC-007).
  - ⑩에서 `_n`을 붙여 39자를 넘으면 본문 끝을 잘라 맞춘다(끝이 `_`면 제거). 사용자가 36자 본문을 직접 고친 경우에만 생긴다.
  - ⑩에서 비어 있는 첫 번호는 `handle LIKE '{base}\_%'` 조회 한 번으로 고른다(`uq_member_handle` 인덱스).
  - 오류 코드·`reason`: `HANDLE_INVALID_FORMAT`, `HANDLE_PREFIX_MISMATCH`, `HANDLE_RESERVED`, `HANDLE_BANNED_WORD`, `HANDLE_DUPLICATE`.
  - 한글 자판 상태 입력(FR-018)은 두벌식 자모 → QWERTY 키 위치 변환(`ㅏ`→`k` 등)을 입력 이벤트에서 적용한다. 브라우저는 IME를 강제로 바꿀 수 없기 때문이다. `inputmode="url"`·`autocapitalize="off"`도 함께.
- **Rationale**: 미리 채우기 규칙이 서버와 화면에 모두 필요하지만, 중복·예약어 판단(⑩)은 DB가 있는 서버만 할 수 있다.
- **Alternatives considered**: 화면이 매 입력마다 서버에 미리 채우기를 요청 — 요청 수가 늘고, 이메일 앞부분을 계속 서버로 보낸다.

### R-16. 예약어 목록 관리 — 확정 (2026-10-07 Clarification, README "정해진 것")

- **Decision**: 블로그 주소 예약어(08 §5)와 닉네임 예약어(09 §5)는 설정 파일(`policy/reserved-handles.txt`, `policy/reserved-nicknames.txt`)로 두고, 서비스 이름이 정해지면 추가한다. 운영자 관리 화면은 Tier C.
- **Rationale**: 서비스 이름 미확정. constitution VII.
- **Alternatives considered**: 코드 상수 — 이름이 정해질 때 배포가 필요하다.

### R-17. 닉네임 정책·금칙어 필터 — 확정

- **Decision**: `NicknamePolicy` 한곳(가입·소셜 가입·프로필 수정 공용). ① 앞뒤 공백 제거 + `java.text.Normalizer` NFC → ② 형식 `^[가-힣a-zA-Z0-9]{2,10}$` → ③ 한글·영문 1자 이상 → ④ 예약어 **포함**(대소문자 무시, 금칙어와 같은 변형 4가지) → ⑤ 금칙어(소문자화 후 그대로·숫자 제거·숫자→영문 0→o 1→i 3→e 4→a 5→s 7→t·1→l, 각 변형에서 예외 단어 먼저 제거) → ⑥ 중복(lower, 자기 자신 제외). 거부 문구는 걸린 단어를 알려주지 않는다. 같은 `BannedWordFilter`를 블로그 주소·소개에도 쓴다(소개는 예약어 제외). 목록은 `banned-words.txt`·`banned-words-exceptions.txt`(09 §2~§6, N-1~N-6, FR-022~026).
- **Rationale**: 09 §3 검사 순서와 오류 코드를 그대로 따른다. 저장값은 NFC 정규화 값.
- **Alternatives considered**: DB CHECK로 금칙어 — 09 §10 "표현하기 어려워 Service에서".

### R-18. 소개 — 확정 + 제안

- **Decision (확정)**: 0~200자, 글자만(이스케이프 출력), 줄바꿈 최대 4줄, 연속 빈 줄은 하나로, 이모지 허용, 자동 링크 없음, 앞뒤 공백 제거·NFC, 금칙어 필터(11 §3, R-1~R-3, FR-048).
- **Decision (제안, 팀 확인 필요)**:
  - 길이는 **유니코드 코드 포인트 수**(`String.codePointCount`)로 센다. DB `ck_member_bio`의 `char_length`와 같은 기준이어야 서버 검사를 통과한 값이 DB에서 거부되지 않는다(Java `String.length()`는 이모지를 2로 센다). 화면 글자 수 표시도 같은 기준(`[...str].length`).
  - 줄 수는 정리(연속 빈 줄 → 하나) 뒤 `\n`으로 나눈 줄 수 ≤ 4. 줄바꿈은 `\r\n`·`\r`을 `\n`으로 바꿔 저장.
  - 빈 문자열은 `NULL`로 저장한다(`ck_member_bio`는 둘 다 허용).
- **Rationale**: DB CHECK와 Service 검사가 다르면 500 오류가 난다.
- **Alternatives considered**: 그래핌(눈에 보이는 글자) 기준 — DB CHECK와 달라진다.

---

## C. 프로필·설정

### R-19. 프로필 저장 원자성·동시성 — 확정

- **Decision**: `PATCH /api/me/profile`은 바꾸려는 칸만 받는다. 한 트랜잭션에서 ① `member … FOR UPDATE` ② 닉네임·소개·사진 ID를 **모두** 검사해 오류를 모은다 ③ 하나라도 있으면 400 `VALIDATION_FAILED` + `errors[]`로 아무것도 저장하지 않는다 ④ 닉네임 30일 제한 중 닉네임 변경이 있으면 전체를 409 `NICKNAME_CHANGE_TOO_SOON` ⑤ 통과하면 저장. 같은 닉네임 재저장은 변경 아님(30일 시작 안 함), 대소문자만 바꾸면 변경. 동시 수정은 나중 저장이 반영(낙관적 잠금 없음)(11 §5, R-8, 09 §8, FR-028·052).
- **Rationale**: 프로필은 짧아서 비교 창 없이 다시 고치기 쉽다(R-8). `FOR UPDATE`는 사진 연결의 "떼기 → 붙이기"(11 §4-4)도 직렬화한다.
- **Alternatives considered**: 칸별 API — "하나라도 실패하면 아무것도 저장하지 않음"(SC-008)을 지킬 수 없다.

### R-20. 프로필 사진 연결·소셜 사진 — 확정

- **Decision**: 업로드는 003 흐름(presign `{purpose: PROFILE}` → 직접 업로드 → complete: 형식·매직 바이트·정확히 256×256·1MB 이하, 썸네일 없음). 연결은 `PATCH /api/me/profile {profileImageId}`에서 같은 트랜잭션 안에 media의 `ProfileImageService.attach(memberId, imageId)` 호출: 이전 사진 `detached_at = now()` → 새 사진 `status = ATTACHED, detached_at = NULL`(떼기 → 붙이기). `profileImageId: null`은 기본 이미지(이전 사진 떼기만). 이미지 검사(본인 업로드·`purpose = PROFILE`·행이 존재)는 실패 시 400 `INVALID_PROFILE_IMAGE`. 소셜 사진은 브라우저가 5초 제한으로 받아 가운데 정사각형 256×256 WebP로 바꾼 뒤 같은 흐름으로 올린다. 서버는 외부 주소를 요청·저장하지 않는다(11 §4, R-4~R-7, FR-031·049·050).
- **Decision (제안, 팀 확인 필요)**: GitHub에 확인된 이메일이 없어 마무리 화면에서 이메일을 입력한 계정은 가입 직후 인증 전이라 사진을 올릴 수 없다(FR-049, 42 §9). 이 경우 서버는 소셜 사진 주소를 화면에 넘기지 않고 "프로필 사진 사용" 체크를 숨긴다(인증 뒤 설정에서 직접 올림). spec FR-031과 FR-049가 이 경우를 따로 적지 않아 이렇게 정한다.
- **Rationale**: 회원 테이블에 사진 컬럼이 없다(51, 2026-10-07). 현재 사진 = `uq_image_profile_current` 조건의 행 하나.
- **Alternatives considered**: 서버가 소셜 사진을 내려받기 — SSRF 위험(11 R-6).

### R-21. 화면별 CSP — 확정

- **Decision**: 공통 CSP(12 §8)는 모든 응답에, `/signup/social` HTML 응답에만 `img-src`에 `https://lh3.googleusercontent.com https://avatars.githubusercontent.com`을 더한다(`SocialSignupPageCspFilter`). React는 첫 CSP를 화면 이동 때 바꾸지 않으므로 이 화면은 소셜 콜백 리다이렉트로 전체 페이지 진입, 가입 완료 후 `window.location` 이동으로 나간다(H3, FR-032).
- **Rationale**: 다른 화면에서 외부 사진 서버를 불러오면 독자 IP가 노출된다.
- **Alternatives considered**: 전 화면 허용 — H3에서 거부.

### R-22. 계정 상태 검사 위치 — 확정 + 제안

- **Decision (확정)**: 판정 순서 로그인(401) → 계정 상태(403) → 볼 수 있나(404) → 행동 권한(404) → 업무 규칙(400/409)(42 §3, README "정해진 것"). 인증 전 회원의 글쓰기(새 글·저장·발행·공개 범위 변경)·댓글·사진 업로드·좋아요·신고는 403 `EMAIL_NOT_VERIFIED`, 정지는 403 `ACCOUNT_SUSPENDED`(H7), 탈퇴 유예는 403 `ACCOUNT_WITHDRAWN`(42 P-12).
- **Decision (제안, 팀 확인 필요)**: `AccountStatusGuard.requireActive(memberId, ActionKind)`를 account가 공개하고, 각 모듈 Service가 쓰기 처리 첫머리에서 호출한다. Guard는 세션 값이 아니라 **DB를 매번 읽는다**(`member.status` + `auth_identity.email_verified_at`, PK·UNIQUE 조회 1번). 다른 브라우저에서 인증을 마치거나 정지가 걸린 즉시 반영되게 하기 위해서다. `ActionKind`는 `CONTENT_WRITE`(인증 필요)와 `ACCOUNT_WRITE`(인증 불필요: 닉네임·소개, 비밀번호 변경, 기본 공개 범위, 탈퇴, 친구 요청), `CONTENT_CLEANUP`(인증 불필요: 자기 글·댓글 삭제·복구·영구 삭제, 내 글 관리 목록 — 006·007)으로 나눈다(FR-007, 42 §9). 세 종류 모두 정지·탈퇴 유예는 거부한다. Guard는 쓰기에서만 불리므로, 탈퇴 유예 회원의 허용 목록(`POST /api/me/restore`, `POST /api/auth/logout`, `GET /api/me`, `GET /api/auth/csrf`) 외 모든 `/api/**` 요청 403(spec 004 FR-031)은 별도 필터 `WithdrawnAccountGateFilter`(tasks T042a, 004 research R-23)가 맡는다(Tier A 교차 분석 2026-10-07).
- **Rationale**: 세션에만 상태를 두면 "남은 세션의 쓰기"(H7)를 막지 못한다.
- **Alternatives considered**: 세션 캐시 + 이벤트로 갱신 — 이벤트 유실(20 EV-2) 시 정지가 새어 나간다.

### R-23. 정지 계정 로그인·자동 해제 — 확정

- **Decision**: 비밀번호가 맞으면 열린 정지(`lifted_at IS NULL`)를 `ix_member_suspension_member`로 조회. `ends_at`이 지났으면 같은 트랜잭션에서 `lifted_at = now()`(lifted_by NULL) + `member.status = 'ACTIVE'`로 바꾸고 로그인. 아니면 403 `ACCOUNT_SUSPENDED` + `details {endsAt|null, reason}`("정지된 계정이에요 (~기한). 사유: …", 기한 없으면 영구). 정지 생성·세션 삭제는 014(07 §6, 42 P-7, FR-038). 정지 회원도 비밀번호 재설정은 할 수 있다(42 §9).
- **Rationale**: 51 §4 E5 — 회원당 열린 정지 하나는 Service가 지킨다.
- **Alternatives considered**: 만료 해제 배치 — 원문이 "로그인 때 해제"로 정함.

### R-24. 약관 버전·재동의 — 확정 + 제안

- **Decision (확정)**: `member_agreement(member_id, type, version, agreed_at)`, 가입 트랜잭션에서 `TERMS`·`PRIVACY` 행을 현재 버전으로 INSERT, 로그인 때 저장 버전 ≠ 현재 버전이면 재동의 화면, 재동의하면 `version`·`agreed_at` 갱신, 탈퇴 익명 처리 뒤에도 남김. `/terms`·`/privacy`는 시행일과 버전을 표시하고 처리방침에 "친구에게 최근 활동 시점 표시"를 적는다(07 §3-1, H9, FR-010~012).
- **Decision (제안, 팀 확인 필요)**:
  - 현재 버전·시행일은 설정값 `blog.agreement.terms.version`·`effective-date`, `blog.agreement.privacy.*`. 문서 본문은 프런트 정적 페이지.
  - 로그인 성공 때 재동의가 필요하면 세션 속성 `reagreementRequired = [TERMS, PRIVACY 중 해당]`을 두고 응답에 `reagreementRequired: true`. `ReagreementGateFilter`가 이 세션의 요청을 허용 목록(`GET /api/me`, `GET /api/agreements/current`, `PUT /api/me/agreements`, `POST /api/auth/logout`, `GET /api/auth/csrf`, 정적 파일·화면 셸) 외에는 **403 `REAGREEMENT_REQUIRED`**로 막고, 화면은 재동의 화면으로 이동한다. 로그인 중에 버전이 바뀐 경우는 다음 로그인 때 묻는다(FR-012 "로그인할 때").
- **Rationale**: SC-011(재동의 전 다른 화면 이용 0건)을 서버에서도 지킨다. 화면만 막으면 API로 우회할 수 있다(constitution III 정신).
- **Alternatives considered**: 화면에서만 막기 — 우회 가능. 요청마다 DB로 버전 비교 — 비용 대비 이득이 없다(버전은 배포 때만 바뀜).

### R-25. 직전 로그인 — 확정

- **Decision**: 로그인 성공 때 `auth_identity.last_login_at`을 갱신하기 **전** 값과 로그인 방식을 세션 속성 `previousLoginAt`·`provider`에 담고, 계정 화면(`GET /api/me/settings`)에 "직전 로그인: 일자, 방식"(값이 없으면 "첫 로그인")을 본인에게만 보인다. 스키마 변경 없음(07 §6, 11 §6-4, R-11, FR-057·058). 51 §4의 "마지막 로그인을 보여 준다"는 2026-10-07 "직전 로그인 표시" 결정으로 대체(spec Assumptions ②).
- **Rationale**: 방금 한 로그인 시각은 계정 도용 확인에 의미가 없다.
- **Alternatives considered**: 로그인 이력 테이블 — 스키마 추가(원칙 I), 공통 범위 밖.

### R-26. 최근 활동 갱신·표시 — 확정 + 제안

- **Decision (확정)**: 로그인한 요청이 오면 `member.last_active_at`을 한 시간에 한 번 정도 갱신(Redis로 간격 조절), 친구(ACCEPTED) + 대상 공개 + 보는 사람 공개 + 값 있음일 때만 "오늘 / 어제 / N일 전(2~6일) / 1주 이상"으로, 그 외에는 응답에 넣지 않는다. 설정 `PATCH /api/me/settings {lastActiveVisible}`(기본 켬). 익명 처리 때 비움(015)(06 §6-4, 11 §6-4, R-12, FR-059~061).
- **Decision (제안, 팀 확인 필요)**:
  - `LastActiveTouchFilter`(인증된 요청, 응답 후): `SET member:active-touch:{memberId} 1 NX EX 3600`이 성공할 때만 `UPDATE member SET last_active_at = now() WHERE id = ?`. Redis 장애·DB 오류는 경고 로그 후 무시(V).
  - 날짜 경계는 `blog.time-zone=Asia/Seoul`의 달력 날짜 차이 d로: d=0 "오늘", d=1 "어제", 2≤d≤6 "N일 전", d≥7 "1주 이상"(spec Assumption "한국 시간").
  - 응답 형태 `lastActive: {"bucket": "TODAY"|"YESTERDAY"|"DAYS_AGO"|"OVER_A_WEEK", "days": 2~6(DAYS_AGO일 때만)}`. 조건 밖이면 **키 자체를 뺀다**(null도 넣지 않음). 정확한 시각은 어떤 응답에도 없다.
  - 표시 위치: `GET /api/members/{handle}/friend`(이 기능)와 005의 블로그 머리말(`LastActiveQueryService.lastActiveFor(viewerId, targetId)` 호출, 005와 맞춤).
- **Rationale**: 버킷 계산을 서버가 해야 정확한 시각이 응답에 실리지 않는다(06 §6-4 "API 응답에도 넣지 않는다").
- **Alternatives considered**: 시각을 보내고 화면이 버킷 계산 — 응답에 정확한 시각이 노출된다.

### R-27. 친구 맺기 — 확정 + spec 가정 확인

- **Decision (확정)**: `friendship(member_a_id < member_b_id, requested_by, status, created_at, accepted_at)`. 요청은 `INSERT … ON CONFLICT (member_a_id, member_b_id) DO UPDATE SET status = 'ACCEPTED', accepted_at = now() WHERE friendship.status = 'PENDING' AND friendship.requested_by <> :me RETURNING …` 한 문장으로: 행이 없으면 PENDING 생성, 상대가 보낸 요청이면 바로 수락, 이미 친구거나 내가 보낸 요청이면 변화 없음. 동시 맞요청도 행 하나(PK). 거절·취소·끊기는 행 삭제, 상대에게 알리지 않음(이벤트도 없음). 친구 판정은 `LEAST/GREATEST` 한 행 조회. 친구 목록은 본인만(06 §6-1~§6-3, FR-054~056).
- **Decision (spec 가정 — 팀 확인 필요)**: 친구 요청에는 로그인만 필요하고 이메일 인증은 필요 없다(팔로우와 같음, 42 §10-1). 친구 요청 횟수 제한은 두지 않는다. 자기 자신 요청은 400 `CANNOT_FRIEND_SELF`(제안 코드, `CANNOT_FOLLOW_SELF`와 같은 형식). 대상이 없거나 탈퇴 유예·익명 처리면 404.
- **Rationale**: 원문(42)에 친구 요청 행이 없어 spec Assumptions가 기본값을 정했다.
- **Alternatives considered**: 요청 행과 친구 행을 따로 두기 — V1 스키마와 다르다.

### R-28. 로그아웃 — 확정 + 제안

- **Decision (확정)**: `POST /api/auth/logout` → 서버 세션 삭제, 화면은 그 회원의 IndexedDB 임시 글·백업(`draft:{memberId}:*`, `draft-backup:{memberId}:*`, 키 규칙은 002) 전부 삭제 후 홈으로. 테마 설정은 지우지 않는다(016). 비밀번호 재설정 때는 모든 기기 로그아웃 자동(07 §7, 04 §6-2 #4, FR-041).
- **Decision (제안, 002와 맞춤)**: 삭제 전에 아직 서버로 보내지 않은(dirty) 작업이 있으면 자동 저장 요청을 한 번 보내 보고(최대 3초), 실패해도 사용자에게 "보내지 못한 작업이 지워져요" 확인을 받은 뒤 지운다.
- **Rationale**: 공용 PC 결정(04 §6-2 #4)과 데이터 유실 방지(constitution VI)를 함께 지킨다.
- **Alternatives considered**: 묻지 않고 삭제 — 마지막 몇 초의 작업이 사라질 수 있다.

### R-29. 메일 발송 — 확정 (2026-10-07 Clarification, README "정해진 것")

- **Decision**: Spring Mail(SMTP). 운영 SMTP 제공자·계정은 설정값(`spring.mail.*`, 비밀번호는 환경 변수)으로 배포 때 정한다. 개발은 Mailpit(Docker, SMTP 1025 / 웹 8025). 메일은 `AccountMailService`(@Async)가 **커밋 후**(`@TransactionalEventListener(AFTER_COMMIT)` 또는 커밋 후 콜백) 보낸다. 인증 메일 토큰은 발송 시점에 만들어 이벤트·로그에 싣지 않는다. 비밀번호 찾기는 요청 스레드에서 조회·발송하지 않고 비동기로 넘겨 응답 시간을 가입 여부와 무관하게 맞춘다(SC-004).
- **Rationale**: 07 L-9, FR-009. 메일 실패가 가입·변경을 막지 않는다(constitution V).
- **Alternatives considered**: 메시지 브로커(RabbitMQ) — 공통은 쓰지 않음(02 §2, 개인 확장).

### R-30. Redis 장애 정책 — 확정 + 제안

- **Decision (확정)**: 세션 → 비로그인 처리(읽기 계속, 로그인 필요 요청 401), 요청 제한·실패 카운터 → 통과(경고 로그), 인증·재설정 토큰 확인 → 거부 "잠시 후 다시 시도해 주세요", 새 로그인 → 거부, 최근 활동 갱신 → 건너뜀(02 §2-1, 07 §6, H8, FR-040).
- **Decision (제안, 팀 확인 필요)**: 세션 저장소를 감싼 `ResilientSessionRepository`가 Redis 연결 예외를 잡아 "세션 없음"으로 돌려준다. 새 로그인(폼·소셜)·토큰 확인·소셜 가입 마무리는 **503 `TEMPORARILY_UNAVAILABLE`** + `Retry-After: 30`. 인증 메일 재발송·비밀번호 찾기는 토큰을 저장할 수 없으므로 역시 503.
- **Rationale**: 02 §2-1에 상태 코드가 없다. AI는 503 `AI_UNAVAILABLE`(02 §2-1)을 쓰므로 같은 계열로 맞춘다.
- **Alternatives considered**: 500 — 일시 장애임을 알 수 없다.

### R-31. `member_suspension` 소유 모듈 — 팀 확인 필요 (014와 맞춤)

- **Decision (제안)**: `MemberSuspension` 엔티티·저장소는 account 모듈에 두고, account가 `SuspensionService`(정지 걸기·해제·조회)를 공개한다. 014(신고·숨김)는 정지를 걸 때 이 Service와 `SessionTerminator`를 호출한다. 이 기능(001)은 로그인 때 읽기·만료 해제만 한다.
- **Rationale**: 정지는 `member.status`를 함께 바꿔야 하고, constitution II는 다른 모듈의 테이블 직접 접근을 금지한다. 상태와 이력을 한 모듈이 가져야 한 트랜잭션으로 바꿀 수 있다.
- **Alternatives considered**: moderation(014) 모듈이 소유 — 그러면 001은 로그인 때 014의 공개 Service를 호출하고, `member.status` 변경은 account Service를 다시 호출해야 한다.

### R-32. 원문에 없는 API 경로·이유 코드 — 제안(팀 확인 필요)

- **Decision**: 원문이 정한 경로(`GET /api/handles/availability`, `GET /api/nicknames/availability`, `PATCH /api/me/profile`, `POST /api/me/password`, `PATCH /api/me/settings`, 화면 `/settings`·`/terms`·`/privacy`)와 이유 코드(09 §3, 11 §3·§5·§6-2, 42 §4)는 그대로 쓰고, 나머지는 02 §5-1 규약(복수형 명사, `/api/me/...`, 켜고 끄는 상태는 `PUT`/`DELETE`, 공통 오류 본문)에 맞춰 이 계획이 정했다. 목록은 [contracts/openapi.yaml](./contracts/openapi.yaml)의 `x-origin: proposed` 표시. 친구는 팔로우(`PUT`/`DELETE /api/members/{handle}/follow`, 02 §5-1)와 같은 형태 `PUT`/`DELETE /api/members/{handle}/friend`.
- **Rationale**: 02 §5-1 "명세는 각자 OpenAPI로 작성"이지만, 공통 Service와 통합 테스트가 같은 경로를 쓰면 세 사람의 테스트를 공유할 수 있다.
- **Alternatives considered**: 친구 요청을 `/api/me/friend-requests` 리소스로 만들기 — 맞요청 즉시 수락(FR-054)이 "상태 지정"이라 `PUT` 하나가 더 단순하다.

### R-33. 로그인 후 이동 주소 검사 — 확정 + 제안

- **Decision (확정)**: 보던 페이지로 이동하되 사이트 안 상대 경로만(07 §6, 나민서 D-14, FR-039).
- **Decision (제안)**: `SafeRedirectResolver` — `/`로 시작하고, `//`·`/\`로 시작하지 않고, 제어 문자·`\`가 없고, URL 디코딩 후에도 같은 조건을 만족할 때만 허용. 아니면 `/`. 소셜 로그인은 시작할 때 `redirect`를 세션에 보관해 콜백 뒤에 같은 검사를 한다.
- **Rationale**: 프로토콜 상대 주소(`//evil.com`)와 역슬래시 우회를 막는다.
- **Alternatives considered**: 허용 목록 경로 — 화면이 늘 때마다 고쳐야 한다.

### R-34. 기본 아바타 — 확정 + 제안

- **Decision (확정)**: 닉네임 첫 글자(영문 대문자), 블로그 주소로 정한 8색 중 하나, 라이트·다크 모두 대비 4.5:1 이상, 화면에서 SVG로 그림(11 §4-3, R-5, FR-051).
- **Decision (제안)**: 색 번호 = 블로그 주소 UTF-8 바이트의 FNV-1a 32비트 해시 mod 8. 8색 값은 016(다크 모드) 색 토큰과 맞춘다.
- **Rationale**: 언어·플랫폼 무관하게 같은 결과(프런트만 계산하지만 테스트 데이터 공유 가능).
- **Alternatives considered**: `String.hashCode` — JS에 같은 함수가 없다.

### R-35. 테스트 전략 — 확정 + 제안

- **Decision (확정)**: JUnit 5, Testcontainers PostgreSQL(H2 금지), Spring Security Test, 권한 관련 통합 테스트 필수(02 §6, constitution VIII).
- **Decision (제안)**: Redis도 Testcontainers(`redis:7`). 메일은 통합 테스트에서 캡처용 `MailSender` 대체 Bean으로 링크(토큰)를 꺼낸다. OAuth2는 `SecurityMockMvcRequestPostProcessors.oauth2Login()`/`oidcLogin()`과 가짜 `OAuth2UserService`로 콜백 이후 흐름(신규 → 대기 정보, 기존 → 로그인)을 검증한다(실제 제공자 호출은 수동 quickstart). Redis 장애는 `docker pause`(Testcontainers `getDockerClient().pauseContainerCmd`)로. 동시성은 `ExecutorService` 20개 동시 요청(SC-001). 정책 클래스는 08 §3·09 §2~§7 예시를 파라미터화 테스트로(SC-007). 최근 활동 노출 조합(SC-009) 4×2×2 = 16가지를 표 기반 테스트로.
- **Rationale**: Acceptance Scenario를 테스트로 옮길 수 있어야 한다(constitution VIII).
- **Alternatives considered**: 실제 Google·GitHub로 자동 테스트 — 비밀값·네트워크 의존, CI 불안정.

### R-36. Spring Boot 버전 — 팀 확정 (NEEDS CLARIFICATION 아님)

- **Decision**: "3.x 이상, 팀 확정". 이 계획의 설정 키(`spring.session.redis.repository-type`, `server.tomcat.remoteip.*`, Spring Security 6 SPA CSRF 처리)는 Spring Boot 3.x 이상에서 쓸 수 있다. 4.x로 정해지면 설정 키 이름 변경 여부만 확인한다.
- **Rationale**: 02 §2 "버전 팀 확정"(김민서 문서는 4.1.1 기준).
- **Alternatives considered**: —

---

## 팀 확인 필요 항목 요약

| # | 항목 | 기본안 |
|---|---|---|
| R-03 | Spring Session 인덱스 저장소 + principal = 회원 번호 | 채택 |
| R-04 | 폼 로그인 `/api/auth/login` + JSON 핸들러 | 채택 |
| R-05 | CSRF 토큰 저장(M17) | `XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더 |
| R-07 | 소셜 대기 만료 응답 | 410 `SOCIAL_SIGNUP_EXPIRED` |
| R-10 | 이메일 형식 범위 | ASCII 일반 형식, `EMAIL_INVALID_FORMAT` |
| R-11 | 최신 토큰 하나만 유효(재설정 포함), `POST …/confirm`, `GETDEL` | 채택 |
| R-12 | 실패 카운터를 이메일 해시 기준(가입 여부 무관), 429 + 이유 코드, Redis 키 이름 | 채택 |
| R-13 | 운영 LB 내부 대역 값 | 배포 담당 확인 |
| R-14 | 이메일 지역부 포함 규칙(3자 이상만), 흔한 비밀번호 목록 출처, 비밀번호 오류 코드 | 채택 |
| R-15 | 주소 미리 채우기 서버·화면 분담, 39자 넘침 처리, 주소 오류 코드, 두벌식 변환 | 채택 |
| R-18 | 소개 길이 = 코드 포인트, 4줄 해석, 빈 값 NULL | 채택 |
| R-20 | GitHub 직접 입력 이메일(인증 전) 계정은 소셜 사진 가져오기 생략 | 채택 |
| R-22 | `AccountStatusGuard`(DB 매번 조회) + `ActionKind` 구분 | 채택 |
| R-24 | 재동의 전 403 `REAGREEMENT_REQUIRED` + 허용 목록, 버전 설정 키 | 채택 |
| R-26 | 최근 활동 Redis 키·버킷 응답 형태·005와 표시 위치 | 채택 |
| R-27 | 친구 요청에 인증 불필요·횟수 제한 없음(spec 가정), `CANNOT_FRIEND_SELF` | 채택 |
| R-28 | 로그아웃 전 미전송 작업 전송 시도(002와) | 채택 |
| R-30 | Redis 장애 시 503 `TEMPORARILY_UNAVAILABLE` | 채택 |
| R-31 | `member_suspension` 소유 모듈(014와) | account |
| R-32 | 원문에 없는 API 경로·이유 코드 전체 | contracts/openapi.yaml |
| R-33 | 이동 주소 검사 규칙 | 채택 |
| R-34 | 아바타 색 해시(FNV-1a)·8색 값(016과) | 채택 |
