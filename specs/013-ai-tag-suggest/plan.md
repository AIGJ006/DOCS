# Implementation Plan: AI 태그 추천

**Branch**: `013-ai-tag-suggest` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-ai-tag-suggest/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

이메일 인증한 작성자가 발행 창의 [AI 태그 추천]을 누르면, 에디터의 지금 제목·본문을 정리해 외부 AI(Gemini 무료 등급) 또는 자체 AI(같은 Docker Compose의 Ollama)에 보내 태그를 최대 5개(남은 자리만큼) 받고, 작성자가 하나씩 눌러야만 태그에 붙는다. 처음 쓸 때와 동의 문구가 바뀌었을 때 외부 전송 동의를 받고, 비공개 글은 외부로 보내지 않는다. 같은 내용은 30일, 같은 글의 비슷한 내용은 7일 재사용하고 이때는 하루 20회에서 빼지 않는다. 외부 한도가 다 되면 자체 AI로 넘어가며, 어떤 실패도 글쓰기·자동 저장·발행을 막지 않는다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** 동의는 V1 `member_agreement (member_id, type='AI', version, agreed_at)`(PK `(member_id, type)`), 철회는 행 삭제. 재사용 저장·횟수·공급자 상태는 테이블 없이 Redis(R1).
- **모듈.** 추천은 `tag` 모듈 `tag.application.suggest`(정규화·인기 태그와 같은 곳), 외부 연결은 `tag.infra.ai`. AI 동의 기록은 테이블 주인인 `account`에 공개 Service `AiConsentService`를 더한다. 글 주인·공개 범위는 post 공개 조회 `PostOwnershipQuery.findOwned(postId, me)`로만 읽는다(R2).
- **API.** `POST /api/posts/{postId}/tag-suggestions` `{title, contentMd, currentTags, refresh}` → `{tags, provider, cached, truncated, remainingToday}`. 화면이 버튼 표시·남은 횟수·"자체 AI로 추천 중" 문구를 미리 정하도록 `GET /api/posts/{postId}/tag-suggestions/status` → `{available, consentRequired, consentVersion, provider, remainingToday}`를 더한다. 동의는 `GET`·`PUT`·`DELETE /api/me/agreements/ai`(`{agreed, version, currentVersion, agreedAt}`, R3·R10·R13). 설정 화면은 `GET`으로 동의 상태를 그린다.
- **판정 순서.** 401 → 403(`AccountStatusGuard` `CONTENT_WRITE` — 인증 전 `EMAIL_NOT_VERIFIED`) → 404(내 글 아님·없음·휴지통) → 503 `AI_UNAVAILABLE`(기능 꺼짐) → 409 `AI_CONSENT_REQUIRED` → 400 `VALIDATION_FAILED`(본문 형식) → 422 `CONTENT_TOO_SHORT`(정리 후 100자 미만) → 재사용 저장소 조회(맞으면 200, 횟수 그대로) → 429 `TOO_MANY_REQUESTS`(`details.kind = AI_DAILY_LIMIT`, 하루 20회) → 공급자 호출(R4).
- **입력 정리 = commonmark AST.** 002가 쓰는 commonmark 파서로 문서를 읽고, 제목·강조·목록·인용 기호는 버리고 글자만, 이미지 통째로 제거, 링크는 글자만, 코드 블록은 언어 이름 + 앞 5줄, 공백·줄바꿈 하나로, NFC. 대소문자는 그대로(R5).
- **공급자 라우터.** `TagSuggesterRouter`가 글 공개 범위(PUBLIC이 아니면 자체 AI만)·Redis 상태(`ai:gemini:count:{날짜}`·`exhausted`·`cooldown`·`unknown-429`)로 Gemini/Ollama를 고른다. Gemini 429 → 같은 요청을 Ollama로, 시간 초과·5xx → 60초 중단 + 이번 요청 503. Ollama 동시 처리는 Redis 카운터(기본 1)로 막고 넘치면 503(`details.reason = BUSY`, "잠시 후 다시 시도해 주세요"). 두 클라이언트는 Spring `RestClient`, 출력은 JSON 스키마 `{tags: [string]}`(Gemini `responseSchema`, Ollama `format`)(R6·R7).
- **재사용 저장소.** 같은 내용 `ai:tag:v{prompt-version}:{SHA-256(정리된 입력 전체)}` 30일(사용자 공유), 같은 글 비슷한 내용 `ai:tag:post:{postId}`(지난 입력 앞 8,000자 + 추천, 3-gram Jaccard ≥ 0.9) 7일. 이미 붙인 태그·인기 태그는 열쇠에 넣지 않고 응답 때마다 빼서 남은 자리만큼 자른다. 실패·빈 결과·모두 걸러진 결과는 저장하지 않는다. [다시 추천]은 비슷한 내용을 건너뛰고, 같은 내용 결과가 자체 AI 것이고 지금 외부 AI를 쓸 수 있으면 다시 만든다(R8).
- **결과 검사.** 008 `TagNormalizer.normalize`(금칙어 포함 9단계)로 정리 → 이미 붙인 태그·중복·거부 결과 제거 → 남은 자리(`blog.post.max-tags` − 붙인 수)와 5 중 작은 수(R9).
- **하루 횟수.** `ai:tag:usage:{memberId}:{yyyyMMdd KST}` — 호출 전에 `INCR`로 자리를 잡고(20 초과면 되돌리고 429), 실패하면 `DECR`. AI가 답을 돌려준 요청만 남는다(Clarifications Q4)(R4).
- **장애 격리.** Redis 장애·메모리 부족(002 `RedisGuard`의 `AutosaveUnavailableException` 포함)은 503 `AI_UNAVAILABLE`로 바꾼다. 추천은 글 저장과 다른 요청이라 실패해도 자동 저장·발행은 영향이 없다. 비밀값(`GEMINI_API_KEY`)은 환경 변수로만, 로그에 비밀값·글 내용을 남기지 않는다(R11).
- **배포.** `docker-compose.yml`에 `ollama` 서비스(고정 태그 이미지, 모델 `qwen2.5:1.5b` 미리 받기, 호스트 포트 노출 없음, `num_thread` 설정)를 더한다. 메모리 2~4GB 추가는 배포 담당 확인(Clarifications Q3)(R12).
- **화면.** 발행 창(008 `TagInput` 옆) [AI 태그 추천]·[다시 추천], 추천 칩 `(+ jpa)`, 동의 창(문구 버전), 상태 문구 6종, 남은 횟수, 설정 화면 "AI 동의" 칸(취소)(R13).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC — `RestClient`, Security, Data Redis, Validation), `JdbcClient`, commonmark(002, 입력 정리), 001 `AccountStatusGuard`·`MemberAgreementRepository`·`AgreementType.AI`·`AccountProperties.Agreement`, 002 `RedisGuard`·`blog.post.max-tags`, 008 `TagNormalizer`·`TagQueryService.top`
- 외부: Google Gemini API(무료 등급, `generateContent`, 키는 `GEMINI_API_KEY`), Ollama(`/api/chat`, 같은 Compose)
- 새 의존성 없음(HTTP는 `RestClient`, 테스트는 spring-test `MockRestServiceServer`)
- 화면: React 18, 008 `TagInput`·`PublishDialog`·`normalizeTag.ts`, 001 설정 화면(T122)

**Storage**:

- PostgreSQL: `member_agreement`(account 소유, type `AI` 행만 이 기능이 씀). 읽기만: `post`(작성자·휴지통·공개 범위 — post 공개 조회로)
- Redis: data-model §2 키 9종(재사용 2, Gemini 상태 4, 하루 횟수 1, 인기 태그 1, 자체 AI 동시 처리 1)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), MockMvc, `MockRestServiceServer`(Gemini·Ollama 응답 흉내 — 429 종류·시간 초과·형식 오류), `MutableClock`, `RedisOutage`, `OutputCaptureExtension`(로그에 비밀값·글 내용 없음). 화면은 Vitest + Testing Library, 종단 확인은 Playwright(AI 응답은 `page.route`로 흉내 — 실제 공급자를 부르지 않음). 서버 통합 테스트의 AI는 테스트 설정의 `FakeTagSuggester`. 실제 Ollama 측정은 `@Tag("ollama")` 수동 테스트

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit + Ollama), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 재사용 응답 p95 100ms 이내(Redis 2~3번 + DB 2번)
- Gemini 응답 10초 시간 제한, Ollama 2,000자 입력 30초 안(참고 5.6~6.9초, SC-006 — 배포 서버에서 다시 잼)
- 상태 조회 p95 50ms 이내

**Constraints**:

- 동의 전에는 어떤 AI에도 글을 보내지 않는다(SC-001). 비공개 글은 외부로 보내지 않는다(FR-030)
- 사용자가 누르기 전에는 태그가 바뀌지 않는다 — 서버는 태그를 저장하지 않는다(SC-003)
- 추천 실패가 글쓰기 API에 영향 없음(SC-002) — 같은 트랜잭션·같은 요청을 공유하지 않는다
- 로그에 비밀값·글 내용 없음(SC-008)
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 회원 수천 명, 외부 무료 한도 하루 약 450회(설정), 회원당 하루 20회
- API 5개(추천, 상태, 동의 조회, 동의 저장, 동의 취소), 공급자 2개 + 꺼짐, 화면 컴포넌트 3개 + 설정 칸 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 `member_agreement` 그대로(type `AI`는 CHECK에 이미 있음). 주제 추천 등은 개인 확장(A-1) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | 추천은 tag, 동의는 account 공개 Service, 글 확인은 post 공개 조회. 다른 모듈 테이블을 직접 읽지 않는다 |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 글 조회 SQL에 `author_id = :me AND deleted_at IS NULL`. 남의 글·없는 글·휴지통 글은 같은 404(34 §9의 403 대신 02 §5). 권한 매트릭스 `post-write.csv`에 owner `013` 행(R14) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 추천 태그는 정규화된 이름(`ck_tag_name` 글자만)이고 화면은 텍스트로만 그린다. 외부 AI 키는 환경 변수 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 모든 실패는 503/429로 끝나고 글쓰기 요청과 분리. Redis 장애 → 503(글쓰기는 계속). SC-002 테스트 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 동의 철회 = 행 삭제(51 §4), 탈퇴 익명 처리 때 동의 기록은 남김(015 FR-028). 재사용 저장소는 내용이 아니라 해시를 열쇠로, 같은 글 항목만 정리된 입력을 7일 보관 |
| VII. 수치는 설정값으로 | **PASS** | `blog.ai.tag-suggest.*`(R15). 모델 이름·한도·시간 제한·길이·동시 처리·유사도 기준 모두 설정 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 동의·권한·재사용·하루 횟수·공급자 전환·Redis 장애를 Testcontainers로, 외부 응답은 `MockRestServiceServer` |

**Gate 결과 (Phase 0 전)**: 위반 없음.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. spec Implementation Notes의 429 `AI_DAILY_LIMIT`는 2026-10-08 확정 "429 코드는 `TOO_MANY_REQUESTS` 하나"(007 Q3)와 부딪힌다. 429 `TOO_MANY_REQUESTS` + `details.kind = "AI_DAILY_LIMIT"`·`resetAt`으로 바꾸고 메시지만 "오늘 추천을 모두 썼어요. 내일 다시 써 보세요"로 둔다(팀 확인 T003).
  2. 503 `AI_UNAVAILABLE`은 원인이 넷(꺼짐·두 공급자 실패·자체 AI 혼잡·저장소 장애)이고 화면 문구가 둘로 갈린다. `details.reason`(`DISABLED`·`FAILED`·`BUSY`·`STORE_UNAVAILABLE`)으로 구분한다.
  3. 002 `RedisGuard`는 Redis 메모리 부족이면 503 `AUTOSAVE_UNAVAILABLE`을 던진다(Tier B 공통 문제 — ANALYSIS-tier-bc). 이 기능은 그 예외를 잡아 `AI_UNAVAILABLE`로 바꾼다.
  4. 화면이 "자체 AI로 추천 중" 문구와 버튼 숨김·남은 횟수를 미리 알아야 해서 원문에 없는 상태 API를 더했다(R3).
  5. 개인정보 처리방침 문구(FR-011)를 바꾸면 처리방침 버전을 올려야 하고, 그러면 001 로그인 재동의가 모든 회원에게 뜬다. 바꾸는 시점은 팀이 정한다(009 처리방침 시점과 함께 — 팀 확인 T004).
  6. 008 자동완성의 요청 제한 키 `ratelimit:tag-suggest:{memberId}`와 이 기능의 이름("tag-suggestions")이 비슷하다. 이 기능의 Redis 키는 모두 `ai:` 접두어라 겹치지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/013-ai-tag-suggest/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml           # 추천, 상태, AI 동의 조회·저장·취소
│   └── providers.md           # 입력 정리, 프롬프트, 공급자 라우팅, Redis 키 동작, 재사용 판정, 외부 요청·응답 모양
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── tag/
│   ├── web/TagSuggestionController.java          # POST·GET(status) /api/posts/{postId}/tag-suggestions
│   ├── application/suggest/
│   │   ├── TagSuggestService.java                # 판정 순서·재사용·하루 횟수·결과 검사
│   │   ├── SuggestInputCleaner.java              # commonmark AST → 정리된 글자
│   │   ├── SuggestCache.java                     # 같은 내용·같은 글 비슷한 내용
│   │   ├── TrigramSimilarity.java                # 3-gram Jaccard
│   │   ├── DailyUsage.java                       # ai:tag:usage:{m}:{date} 자리 잡기·되돌리기
│   │   ├── PopularTagProvider.java               # 상위 50, 하루 1번 (008 TagQueryService.top)
│   │   ├── TagSuggesterRouter.java               # 공급자 고르기·전환
│   │   ├── ProviderState.java                    # ai:gemini:* · ai:ollama:inflight
│   │   ├── TagSuggester.java                     # interface: SuggestOutcome suggest(TagSuggestInput)
│   │   ├── TagSuggestInput.java / SuggestOutcome.java / Provider.java
│   │   ├── TagSuggestReasonCode.java             # AI_CONSENT_REQUIRED·CONTENT_TOO_SHORT·AI_UNAVAILABLE
│   │   └── TagSuggestProperties.java             # blog.ai.tag-suggest.*
│   └── infra/ai/
│       ├── GeminiTagSuggester.java               # RestClient, responseSchema, 429 종류 해석
│       ├── OllamaTagSuggester.java               # RestClient, format 스키마, num_thread
│       ├── DisabledTagSuggester.java             # 꺼짐
│       └── PromptBuilder.java                    # 지시문 → 인기 태그 → 붙인 태그 → 제목+본문
├── account/
│   ├── application/AiConsentService.java         # status / agree(version) / revoke (member_agreement type AI)
│   ├── web/AiConsentController.java              # GET·PUT·DELETE /api/me/agreements/ai
│   └── infra/AccountProperties.java              # blog.agreement.ai.version·effective-date (001 소유)
└── post/application/PostOwnershipQuery.java      # findOwned(postId, me) → Optional<OwnedPost(visibility)> (002 소유 모듈)

backend/src/main/resources/application.yml        # blog.ai.tag-suggest.*, blog.agreement.ai.*
docker-compose.yml                                # + ollama 서비스
.env.example                                      # GEMINI_API_KEY= (값 없음)

backend/src/test/
├── resources/permission/post-write.csv           # + tag-suggest.post / tag-suggest.status (owner 013)
└── java/com/team/blog/tag/
    ├── unit/SuggestInputCleanerTest.java, TrigramSimilarityTest.java, PromptBuilderTest.java
    ├── infra/GeminiTagSuggesterTest.java, OllamaTagSuggesterTest.java   # MockRestServiceServer
    └── integration/
        ├── TagSuggestApiIT.java                  # US1
        ├── AiConsentIT.java                      # US2
        ├── TagSuggestCacheIT.java                # US3
        ├── TagSuggestRoutingIT.java              # US4
        ├── TagSuggestFailureIsolationIT.java     # SC-002, Redis 장애
        ├── TagSuggestLoggingIT.java              # SC-008
        └── OllamaSmokeIT.java                    # @Tag("ollama") 실제 모델 시간 측정

frontend/src/
├── api/tagSuggestions.ts                         # getStatus / suggest / getAiConsent / agreeAi / revokeAi
├── features/ai-suggest/
│   ├── AiTagSuggest.tsx                          # 버튼·상태 문구·추천 칩·남은 횟수
│   ├── AiConsentDialog.tsx                       # 동의 문구 (버전 표시)
│   ├── aiConsentText.ts                          # 문구와 버전 (서버 설정 버전과 같아야 함)
│   ├── useTagSuggest.ts                          # 요청·상태 관리
│   └── AiConsentSettings.tsx                     # 설정 "AI 동의" 칸
├── components/editor/PublishDialog.tsx           # TagInput 옆 자리 (008 소유 파일)
└── pages/PrivacyPage.tsx                         # 외부 AI 전송 문단 (001 소유, 시점은 팀 결정)
```

**Structure Decision**: 02 §3대로 태그 관련 기능은 `tag` 모듈에 둔다. 외부 API 클라이언트는 `tag.infra.ai`에 두고 `TagSuggester` 인터페이스 뒤에 숨겨 테스트에서 바꿔 끼운다. 동의 기록은 테이블 주인인 account가 쓴다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

위반 없음.
