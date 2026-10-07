# Tier A 교차 분석 보고서 (001·002·004·005·006)

- 분석일: 2026-10-07
- 대상: `specs/001-account-auth`, `specs/002-post-authoring`, `specs/004-visibility-permission`, `specs/005-post-reading`, `specs/006-manage-delete`의 spec·plan·research·data-model·contracts·quickstart·tasks
- 기준: `.specify/memory/constitution.md`, `README.md`
- 방법: `/speckit-analyze` 검사 항목(중복, 모호, 미명세, constitution 정렬, 커버리지 공백, 불일치)을 기능 안과 기능 사이에 적용했다. 이미 합의된 공통 규칙 11개로 문서를 최소 수정(1단계)한 뒤 남은 것을 정리했다. 작업 번호(T###)는 바꾸지 않았다. 새 작업은 001 `T042a` 하나다.

## 1. 발견 표

### 1-1. 1단계에서 해결됨

| ID | 범주 | 심각도 | 위치 | 요약 | 처리 |
|---|---|---|---|---|---|
| C1 | Constitution 정렬 | CRITICAL | 001 tasks T128, plan Structure·Complexity Tracking | account의 `FriendListQueryRepository`가 media의 `image` 테이블을 직접 JOIN했다. 001 plan Complexity Tracking은 "(없음)"이라 원칙 II 위반이다. | **해결됨**: T128을 `member` JOIN 1번 + `ProfileImageQuery.currentKeysOf` 1번으로 바꿨다. T145의 SQL 수 기대값도 페이지당 2번으로 고쳤고, plan Structure와 Notes도 맞췄다. |
| G1 | 커버리지 공백 | HIGH | 004 spec FR-031, 004 research R-23, 004 tasks T054, 001 tasks | "탈퇴 유예 회원은 허용 목록 외 모든 요청 403" 규칙을 구현하는 작업이 어디에도 없었다. 001 Guard는 쓰기에서만 불리고, 004 T054는 001에 미뤘다. | **해결됨**: 001 tasks Phase 2A에 `T042a WithdrawnAccountGateFilter`를 추가했다(허용 목록: 복구, `/api/auth/logout`, `GET /api/me`, `GET /api/auth/csrf`). 001 research R-22, data-model §4-1, 004 research R-23, 004 tasks(선행·T001·T054)와 연결했다. |
| D1 | 중복 | HIGH | 002 tasks T017·T032·T034 ↔ 001 T022·T023 | `RedisGuard`·`RateLimiter`를 두 기능이 각각 만들도록 되어 있었다. | **해결됨**: 001 T023이 소유하고 002는 CircuitBreaker·OOM 처리와 키 확인만 한다. |
| I1 | 불일치 | HIGH | 002 plan·events.md, 001 plan, 005 plan | 패키지 위치가 달랐다(`shared/web/error`, `shared/domain/event`, `account/infra/security/SecurityConfig`, `account/infra/redis/RateLimiter`, `post/domain/VisibilityFilter`). | **해결됨**: `shared/error`, `shared/event`, `shared/infra/ratelimit`, `shared/security`(+account Customizer), `post/infra`로 맞췄다. |
| I2 | 불일치 | HIGH | 004 plan Summary·Structure·Constitution II, 004 research R-23 | 001에 없는 이름을 썼다(`MemberAccessQuery`, `MemberSessionService`, `AccountStateFilter`, `@RequiresVerifiedEmail`). | **해결됨**: `MemberQueryService`, `SessionTerminator`, `AccountStatusGuard.requireActive(memberId, ActionKind)`, 001 T042a로 바꿨다. |
| I3 | 불일치 | HIGH | 001 T037, 006 tasks T021·T029·T042·T056, 006 research R5, 006 contracts | 006이 삭제·복구·관리 목록에 `ACCOUNT_WRITE`를 빌려 썼다. R5는 "정지 회원은 오지 않는다"고 했는데 tasks는 403 `ACCOUNT_SUSPENDED`를 기대했다. | **해결됨**: 001 `ActionKind`에 `CONTENT_CLEANUP`(인증 전 허용, 정지·탈퇴 유예 거부)을 추가했다(T033·T037, R-22, data-model §4-1). 006 tasks·R5·contracts가 이 값을 쓴다. |
| I4 | 불일치 | HIGH | 002 research B-9·events.md, 006 research R8·data-model | PostgreSQL 인자 없는 `btrim`은 U+0020만 지워서, Java 판정과 SQL 배치·006 즉시 삭제 판정이 달랐다. | **해결됨**: Java·SQL 모두 `" \t\r\n"` 문자 집합을 쓴다(`btrim(x, E' \t\r\n')`). 006 R8의 함수 이름도 002 `EmptyDraftPolicy`로 맞췄다. |
| U1 | 미명세 | HIGH | 002 contracts/openapi.yaml | 쓰기 API에 403 `ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`이 없었고, 미리보기에는 CSRF 403이 없었다. | **해결됨**: `Forbidden` 예시 4개를 넣었고 `PreviewForbidden`을 새로 만들었다. |
| I5 | 불일치 | MEDIUM | 005 research R-28·R-35, 006 contracts | CSRF 헤더가 `X-CSRF-TOKEN`으로 적혀 있거나 정해지지 않았다. | **해결됨**: `XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더, 실패 코드 `CSRF_REJECTED`(제안). |
| I6 | 불일치 | MEDIUM | 005 contracts `PostDetail.viewer`, data-model | 005 T032와 004 R-29는 `emailVerified`·`isAdmin`을 쓰는데 계약에 없었다. | **해결됨**: 계약(필수 필드)과 data-model에 추가했다. |
| I7 | 불일치 | MEDIUM | 006 research R16, contracts, plan, tasks T033·T039 | 커서의 목록 구분이 `t`·`f` 필드였고, `ListScope` 소유자가 "005 T007"로 적혀 있었다. | **해결됨**: `l = manage:{tab}[:{filter}]`, 소유자는 001 T021(`ListScope.of(String)`). |
| I8 | 불일치 | MEDIUM | 005 plan Structure | `CacheControlSupport`, `PostRepository`(상세 행), `NotFoundPage`의 소유자가 잘못 적혀 있었다. | **해결됨**: 004 `CacheControlPolicy`·`NotFoundPageRenderer`, 004 소유 `PostQueryRepository`(T034가 메서드 추가), `NotFoundPage`(004 T025). |
| I9 | 중복 | MEDIUM | 001 plan Structure `account/domain/Visibility` | `Visibility` enum이 둘이었다. | **해결됨**: `post.domain.Visibility`(004 T009) 하나. 001은 String으로 매핑하고 `VisibilityRegistry`로 검사한다. |
| A1 | 모호 | MEDIUM | 001 T040 `ProfileImageQuery`, 005 T048·T050 | 돌려주는 키가 썸네일인지 원본인지 정해지지 않았다. | **해결됨**: `ProfileImageKeys(original, thumbnail)`, `display()`는 썸네일(없으면 원본). 005 T048은 `display()`, T050(og:image)은 `original()`. |
| I10 | 미명세 | MEDIUM | 006 contracts `AccountWithdrawn` | 403이 탈퇴 유예 하나만 있었다. | **해결됨**: `ACCOUNT_SUSPENDED`·`CSRF_REJECTED` 예시와 판정 순서 설명 추가. |
| I11 | 불일치 | LOW | 002·005·006 tasks, 001·004 plan | `frontend/src/api/client.ts` 소유자 표기가 달랐다. | **해결됨**: 001 T042가 만들고, 004 T024는 404 `onNotFound` 분기만 추가. |
| I12 | 불일치 | LOW | 004 research R-23 | 허용 목록이 `POST /api/logout`이었고 정지 차단 범위가 넓게 적혀 있었다. | **해결됨**: 001 경로(`/api/auth/logout`)와 쓰기 Guard 기준으로 고쳤다. |
| I13 | 불일치 | LOW | 002 plan, 002 research §C | 001 소유 파일을 다른 이름으로 적었고, 로그아웃 초안 삭제 함수 이름이 일관되지 않았다. | **해결됨**: `V2__shedlock.sql`·`SecurityHeadersFilter`(001 소유), `clearMemberDrafts`. |
| I14 | 불일치 | LOW | 006 spec Implementation Notes | PATCH 표기 확인. | **해결됨(변경 불필요)**: 이미 `PUT`. |
| R11 | 불일치 | LOW | 002 T117, 006 T062 | 배치 cron 시간대를 `Asia/Seoul`로 하드코딩했다. | **해결됨**: `zone = "${blog.time-zone}"`(001 T013)으로 바꿨다. |

### 1-2. 남은 발견

| ID | 범주 | 심각도 | 위치 | 요약 | 권고 |
|---|---|---|---|---|---|
| R1 | 불일치 | HIGH | 001 tasks T017 ↔ 004 research R-26·contracts, 002·005·006 contracts | 404 본문이 문서마다 다르다. 001 T017은 `errors:null`, 나머지는 `errors: []`이고, 002·005는 메시지 끝에 마침표가 있다. "본문 완전 동일" 테스트끼리 서로 깨진다. | 001 T017·T018 기준 하나로 정한다. 다수안은 `errors: []`·마침표 없음(→ 팀 결정 4). |
| R2 | 불일치(spec) | HIGH | 001 spec FR-031 ↔ FR-049 | 소셜 사진은 가입 때 한 번만 복사하는데, 인증 전 회원은 사진을 업로드할 수 없다. 확인된 이메일이 없는 GitHub 가입자는 소셜 사진을 받지 못한다. | spec 수정 필요(→ 팀 결정 1). |
| R3 | 불일치 | MEDIUM | 001 T017(`MALFORMED_REQUEST`) ↔ 004 R-21·T027·T036(`INVALID_REQUEST`) | 읽을 수 없는 JSON의 이유 코드가 둘이다. | 하나로 정한다(→ 팀 결정 2). |
| R4 | 불일치 | MEDIUM | 001 T018·contracts(`TOO_MANY_REQUESTS`) ↔ 002 contracts·T007(`RATE_LIMITED`) | 429 이유 코드가 둘이다. | 001 공통 코드로 맞추거나 구분 근거를 적는다(→ 팀 결정 3). |
| R5 | Constitution 정렬 | MEDIUM | 005 T034 → 004 소유 `PostQueryRepository`, 004 plan Complexity Tracking | 005가 004 파일에 `image` LEFT JOIN을 추가하는데, 이 원칙 II 예외가 004 plan에는 기록되지 않았다. | 004 plan Complexity Tracking에 추가하거나, T034가 001 `ProfileImageQuery`를 쓰게 한다. |
| R6 | 커버리지 공백 | MEDIUM | 004 spec FR-012·036·037·038·041·043 | Tier B/C 기능에 위임한 6개 FR은 Tier A 작업이 없다(의도). | 해당 기능 tasks 작성 때 004 권한 매트릭스 하네스(T021~T023)에 연결. |
| R7 | 커버리지 공백 | MEDIUM | 005 spec SC-005(썸네일 전송량) | 확인 작업이 없다(003 썸네일 생성에 의존). | 003 tasks 또는 005 Polish에 측정 항목 추가. |
| R8 | 미명세 | MEDIUM | 001 T084·T116(임시)·T121, 003(Tier B) | 003이 없어 001 프로필 사진 기능은 임시 구현으로 일부만 동작한다. | 001 체크포인트에 "사진은 003 완료 전 임시" 명시(→ 팀 결정 5). |
| R9 | 미명세 | MEDIUM | 002·004·006 spec·events | sitemap 소유 기능이 없다. | 012 또는 005에 배정(→ 팀 결정 6). |
| R10 | 불일치 | MEDIUM | 001 T015(`blog.<기능>`) ↔ 002·005·006 설정 키 | 설정 키 접두어 규칙이 지켜지지 않는다. | 규칙을 정한 뒤 각 `*Properties` 작업 수정(→ 팀 결정 7). |
| R12 | 불일치 | LOW | 51 공통 스키마 `ck_post_published` | DB CHECK는 공백(U+0020)만 지운다. Java 검사가 더 엄격해 실제 문제는 없다. | 51은 유지, 002 data-model에 주석. |
| R13 | 불일치 | LOW | 004(복구 화면 `/restore`) ↔ 015 spec(`/account/restore`) | 복구 화면 경로가 다르다. | 015 plan 때 `/account/restore`로 맞춘다. |
| R14 | 미명세 | LOW | 001 T042a ↔ T109(`ReagreementGateFilter`) | 두 필터의 실행 순서가 정해지지 않았다. | 탈퇴 유예 판정을 먼저 둔다고 명시. |
| R15 | 커버리지 | LOW | 002 SC-001, 005 SC-011 | 측정 테스트 없이 간접 확인만 된다. | Polish의 quickstart 검증 항목에 명시. |
| R16 | 불일치(범위 밖) | LOW | 009 spec Implementation Notes | Tier B 문서에 옛 CSRF 헤더 이름이 남아 있다. | 009 plan 단계에서 `X-XSRF-TOKEN`으로. |

## 2. 요구사항 ↔ 작업 커버리지

| 기능 | FR 수 | 작업에 연결된 FR | 커버리지 | 작업 수 | 비고 |
|---|---|---|---|---|---|
| 001 계정·인증 | 61 | 61 | 100% | 150 (T042a 포함) | SC-002·005는 간접 확인 |
| 002 글 작성 | 50 | 50 | 100% | 123 | SC-001 간접(R15) |
| 004 공개 범위·권한 | 49 | 43 | 87.8% (Tier A 범위 100%) | 78 | 6개는 Tier B/C 위임(R6) |
| 005 글 읽기 | 45 | 45 | 100% | 77 | SC-005 작업 없음(R7) |
| 006 관리·삭제 | 39 | 39 | 100% | 76 | — |
| **합계** | **244** | **238** | **97.5%** | **504** | Tier A 범위만 보면 100% |

## 3. Constitution 정렬

- 원칙 II(모듈 경계): C1 해결, R5는 기록 누락(위반 아님)으로 남음.
- 원칙 III(볼 수 없으면 404, 본문 동일): 규칙은 모두 따르지만 404 예시 본문이 문서마다 다르다(R1).
- 원칙 VII(설정값): R10 남음, R11 해결.
- 원칙 I·IV·V·VI·VIII: 위반 없음.

## 4. 연결되지 않은 작업

FR이나 SC에 직접 연결되지 않은 작업은 모두 기반·확인 작업(Setup, 선행 확인, quickstart 검증, 설정값 클래스, 픽스처)이다. FR과 무관한 기능을 만드는 작업은 없다.

## 5. 지표

- 요구사항(FR) 244 / 작업 504 / 커버리지 97.5%(Tier A 범위 100%)
- 발견 35건: 해결 20(CRITICAL 1 · HIGH 7 · MEDIUM 6 · LOW 6), 남음 15(CRITICAL 0 · HIGH 2 · MEDIUM 8 · LOW 5)

## 6. 팀 결정 필요

1. **소셜 사진 충돌(R2)**: 가입 때 서버 복사만 FR-049 예외로 둘지, 인증 후 한 번 더 복사를 허용할지.
2. **400 형식 오류 코드(R3)**: `MALFORMED_REQUEST` vs `INVALID_REQUEST`.
3. **429 코드(R4)**: `TOO_MANY_REQUESTS` vs `RATE_LIMITED`.
4. **404 본문(R1)**: `errors`를 `null`/`[]` 중 무엇으로, 메시지 끝 마침표 여부.
5. **003 미완성 동안 001 사진 기능(R8)**: "003 전까지 임시"로 받아들일지.
6. **sitemap 소유(R9)**: 012 또는 005.
7. **설정 키 규칙(R10)**: `blog.<기능>` vs `blog.<도메인>`.
8. **제안 승인**: `CSRF_REJECTED`, `ActionKind.CONTENT_CLEANUP`, 001 T042a 허용 목록 4개 경로.

## 7. 다음 행동

- CRITICAL은 없다. 구현(`/speckit-implement`)을 시작해도 된다.
- 다만 R1(404 본문)은 001 T017 구현 전에 정해야 한다. 공통 기반 테스트라 나중에 바꾸면 여러 기능의 테스트를 다시 고쳐야 한다.
