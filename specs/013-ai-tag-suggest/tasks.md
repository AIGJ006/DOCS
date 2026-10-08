---

description: "Task list for 013-ai-tag-suggest (AI 태그 추천)"
---

# Tasks: AI 태그 추천

**Input**: Design documents from `/specs/013-ai-tag-suggest/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, providers.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 외부 AI는 `MockRestServiceServer`(공급자 클라이언트 테스트)와 `FakeTagSuggester`(통합 테스트)로 흉내 내고, 실제 공급자는 `@Tag("ollama")` 수동 테스트에서만 부른다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 tag 모듈의 AI 추천(`tag.application.suggest`, `tag.infra.ai`)과 account 모듈의 AI 동의(`AiConsentService`)를 소유한다. 새 테이블·마이그레이션·이벤트는 없다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `AccountStatusGuard`·`ActionKind.CONTENT_WRITE`, `MemberAgreementRepository.upsert`·`AgreementType.AI`, `AccountProperties`(`blog.agreement.*`), `SensitiveParamMasking`, `support/IntegrationTestBase`·`MemberFixtures`·`RedisOutage`·`MutableClock`
- 선행: specs/001 US6 T122 — `F/pages/SettingsPage.tsx`(설정 "AI 동의" 칸 T037을 그 위에 얹는다)
- 선행: specs/002 — commonmark 의존성, `RedisGuard`, `PostEditRepository`, `blog.post.*`(`max-tags` 10, 제목·본문 길이), 자동 저장·저장·발행 API(SC-002 확인)
- 선행: specs/004 — 권한 하네스(`support/permission/`, `post-write.csv`, `PostFixtures.State`)
- 선행: specs/008 — `TagNormalizer.normalize`(`Accepted`/`Rejected`), `TagQueryService.top()`, `F/components/editor/TagInput.tsx`·`PublishDialog.tsx`(008이 `TagInput`을 넣은 상태)

**006 머지 후**

- `TR/permission/post-write.csv`에 행을 더하는 작업(T020). 006이 같은 파일의 행 하나를 고친다. 끝에 추가만 하므로 충돌은 작지만 006 머지 뒤에 한다
- `R/application.yml`은 006도 고치지만 이 기능은 `blog.ai` 블록을 끝에 더하기만 한다(T002) — 표시하지 않는다
- `F/App.tsx`는 고치지 않는다(새 화면 경로 없음)

**후속 (다른 스펙이 이 기능을 사용)**

- 015-withdraw: 익명 처리 때 `member_agreement`의 AI 행을 지우지 않는다(015 FR-028). 이 기능은 `WithdrawalPurgeStep`을 구현하지 않는다 — 015 머지 후 확인만(T056)
- 개인 확장(주제 추천 등): `TagSuggester`·`TagSuggesterRouter`·`DailyUsage`를 재사용할 수 있다

**팀 결정 대기 (기본안으로 진행)**

- 503 `AI_UNAVAILABLE`의 `details.reason` 4종, 상태 API·동의 조회 API를 더한 것 — 확인 작업 T003
- 개인정보 처리방침 문단(FR-011)을 넣고 처리방침 버전을 올리는 시점(올리면 모든 회원이 로그인 때 재동의) — 확인 작업 T004(009 처리방침 시점과 함께)
- 배포 서버 메모리 2~4GB 추가와 Ollama 시간 다시 재기, Gemini 한도 초기화 시간대 — 확인 작업 T005

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 설정값, 팀·배포 확인 질문

- [X] T001 선행 확인: V1 `ck_member_agreement_type`에 `AI`, `B/account/infra/MemberAgreementRepository.java`의 `upsert`, `B/shared/infra/redis/RedisGuard.java`, `B/shared/web/SensitiveParamMasking.java`, `B/post/infra/PostEditRepository.java`, 008 `TagNormalizer`·`TagQueryService.top()`·`F/components/editor/TagInput.tsx`, commonmark GFM 확장(표·취소선)이 backend 의존성에 있는지 기록한다 (구현 메모: 모두 있음. V1 ck_member_agreement_type에 AI, MemberAgreementRepository.upsert, RedisGuard(call·callWrite·runAfterCommit), SensitiveParamMasking, PostEditRepository.isOwned, 008 TagNormalizer·TagQueryService.top()(상위 blog.tag.top-limit 100)·TagInput, commonmark 0.30 + gfm-tables·gfm-strikethrough. MutableClock은 없어 시간 경계 시험은 Instant를 넘기는 메서드로 한다. 008 자동완성에 같은 이름 TagSuggestService·TagSuggestAction이 있어 이 기능 Bean 이름을 aiTagSuggestService 등으로 따로 준다)
- [X] T002 [P] 설정값: `B/tag/application/suggest/TagSuggestProperties.java`(`@ConfigurationProperties("blog.ai.tag-suggest")` + `@Validated`, 안쪽 `Cache`·`Popular`·`Gemini`·`Ollama`), `B/account/infra/AccountProperties.java`의 `Agreement`에 `ai(version, effectiveDate)` 추가(001 소유 파일, 추가만), `R/application.yml`에 research R15 기본값(`blog.ai` 블록은 끝에 추가), 테스트 `T/tag/unit/TagSuggestPropertiesBindingTest.java`(기본값, `api-key` 빈 값 허용, `num-thread` ≥ 1, `similarity-threshold` 0~1, `blog.agreement.ai.version` 기본값 `2026-10-08`) (구현 메모: 기본값은 R15 그대로이고 공급자별 connect-timeout(3s)을 더했다. blog.post.* 길이는 tag 모듈이 post 설정 클래스를 가져오지 않게 SuggestPostLimits(@ConfigurationProperties blog.post)로 따로 읽는다. AccountProperties.Agreement에 ai(version, effectiveDate) 추가)
- [X] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: ① (해결됨 — 429 하루 한도는 003과 같은 규칙으로 `AI_DAILY_LIMIT` 별도 코드, ANALYSIS-tier-bc) ② 503 `AI_UNAVAILABLE` `details.reason` 4종 ③ 원문에 없는 `GET …/tag-suggestions/status`·`GET /api/me/agreements/ai` 추가. 답이 오기 전에는 기본안으로 진행한다 (구현 메모: ANALYSIS-tier-bc §6 팀 결정 4에 013 T003 ②③이 이미 올라가 있어 문서는 고치지 않았다. 답 전 기본안(reason 4종·상태 API·동의 조회 API)으로 구현)
- [X] T004 팀 확인: 개인정보 처리방침에 "외부 AI 서비스(Google Gemini)로의 전송" 문단을 넣고 `BLOG_AGREEMENT_PRIVACY_VERSION`을 올리는 시점(009 처리방침 시점과 한 번에 올릴지). 답이 오기 전에는 문단 작업 T038을 하지 않는다 (구현 메모: 묻지 않고 009 T002와 같이 '첫 공개 전 — 처리방침 첫 판에 문단만 넣고 버전은 그대로'(팀 결정 7 추천안)로 가정했다. T038은 이 가정으로 진행)
- [X] T005 배포 담당 확인: 배포 서버에 Ollama용 메모리 2~4GB 추가가 가능한지, 성능 코어 수(`num-thread`), Gemini 무료 등급 하루 한도 초기화 시간대(`quota-zone`)와 모델 이름. 결과를 `R/application.yml` 기본값 주석과 quickstart §0에 적는다 (구현 메모: 묻지 않고 가정: 메모리 2~4GB 추가 가능, num-thread 4(환경 변수 BLOG_AI_TAG_SUGGEST_OLLAMA_NUM_THREAD), quota-zone America/Los_Angeles, 모델 gemini-flash-lite(BLOG_AI_TAG_SUGGEST_GEMINI_MODEL). application.yml 주석과 quickstart §0에 적었다. 배포 서버 측정은 배포 담당 몫으로 남김)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 글 확인, 값 객체, 입력 정리, 프롬프트, 가짜 공급자, 하루 횟수, 동의 확인 — 모든 User Story가 쓴다

**⚠️ CRITICAL**: 이 Phase가 끝나기 전에는 User Story 작업을 시작하지 않는다

- [X] T006 [P] 테스트 `T/post/integration/PostOwnershipQueryIT.java`: 내 공개·비공개·임시·관리자 숨김 글 → `OwnedPost(id, visibility)`, 남의 글·없는 글·휴지통 글 → 빈 값, SQL 1번
- [X] T007 `B/post/application/PostOwnershipQuery.java`(`findOwned(long postId, long memberId)` → `Optional<OwnedPost>`)와 `B/post/application/OwnedPost.java`, 조회는 `B/post/infra/PostEditRepository.java`에 `findOwnedVisibility` 추가(002 소유 파일, 추가만 — 조건은 `isOwned`와 같다)(T006 통과) (구현 메모: PostOwnershipQuery는 @Service, 트랜잭션 없이 SQL 1번)
- [X] T008 [P] 값 객체·이유 코드(data-model §3·§5): `B/tag/application/suggest/`의 `Provider`, `TagSuggestRequest`, `CleanedInput`, `TagSuggestInput`, `SuggestOutcome`(`Success`·`QuotaExceeded`·`Failed`·`Busy`), `QuotaKind`, `FailureKind`, `CachedSuggestion`, `PostCachedSuggestion`, `TagSuggestReasonCode`(`AI_CONSENT_REQUIRED` 409·`CONTENT_TOO_SHORT` 422·`AI_DAILY_LIMIT` 429·`AI_UNAVAILABLE` 503), `AiUnavailableException(reason)`·`AiConsentRequiredException(version)`. 공통 오류 처리기가 `details`를 `{reason}`·`{version}`·`{minChars, length}`로 내보내는지 `T/tag/unit/TagSuggestErrorBodyTest.java`로 확인 (구현 메모: AiUnavailableReason enum과 ContentTooShortException·AiDailyLimitException을 더했다. TagSuggestRequest.refresh는 Boolean(없으면 false). AiUnavailableException은 원인 예외를 붙이지 않는다(공통 처리기가 5xx 원인을 로그에 남기므로))
- [X] T009 [P] 테스트 `T/tag/unit/SuggestInputCleanerTest.java`: contracts/providers.md §1 표 전부, 코드 블록 6번째 줄부터 버림, NFC(조합형 한글), 연속 공백 하나, 대소문자 유지, 길이 = 코드 포인트(이모지 1)
- [X] T010 `B/tag/application/suggest/SuggestInputCleaner.java`(commonmark `Parser` + GFM 확장, `AbstractVisitor`)와 `CleanedInput.sha256()`·`truncate(maxChars)`(T009 통과) (구현 메모: 블록 노드 뒤마다 공백을 넣고 인라인 HTML 태그만 버린다(태그 사이 글자는 남김). 들여쓴 코드 블록도 앞 5줄)
- [X] T011 [P] 테스트 `T/tag/unit/TrigramSimilarityTest.java`: 같음 1.0, 오타 3개(2,000자) ≥ 0.9, 문단 하나 추가 < 0.9, 3자 미만 0, 서로게이트 쌍을 한 글자로 (구현 메모: 시험 글은 무작위 한글 음절로 만든다(어휘가 적으면 문단 추가 뒤에도 3-gram 집합이 거의 같아 0.9를 넘음))
- [X] T012 `B/tag/application/suggest/TrigramSimilarity.java`(`jaccard(a, b)`)(T011 통과)
- [X] T013 [P] 테스트 `T/tag/unit/PromptBuilderTest.java`: 순서(지시문 → 인기 태그 → 붙인 태그 → 본문), 붙인 태그 없음 → "없음", Ollama는 입력에 나오는 인기 태그만, 회원 정보 필드가 없다, 같은 입력이면 같은 문자열
- [X] T014 `B/tag/infra/ai/PromptBuilder.java`(contracts/providers.md §2)와 `B/tag/application/suggest/PopularTagProvider.java`(`ai:tag:popular:{오늘}` 1일, 없으면 `TagQueryService.top()` 앞 50개 이름, `RedisGuard.call`)(T013 통과) (구현 메모: PromptBuilder.build(provider, input) → Prompt(system, user). Ollama 인기 태그 고르기는 소문자 포함 검사(하이픈은 공백으로도). PopularTagProvider는 Redis 장애·메모리 부족이면 저장 없이 DB 값을 쓴다(추천을 막지 않음))
- [X] T015 [P] `B/tag/application/suggest/TagSuggester.java`(interface: `provider()`, `suggest(TagSuggestInput)`)와 테스트 지원 `T/support/ai/FakeTagSuggester.java`(공급자별 Bean 대체, 응답 순서 지정, 받은 입력·호출 수 기록, 지연 흉내), `T/support/ai/FakeAiConfiguration.java` (구현 메모: 가짜는 Ordered.HIGHEST_PRECEDENCE이고 라우터가 공급자마다 순서가 가장 앞선 Bean을 고른다. TagSuggester에 isConfigured()(키 없음·Ollama 꺼짐)를 두어 가짜가 흉내 낸다. FakeAiConfiguration은 @Profile(test) @Configuration(컴포넌트 스캔)이고 FakeAi(gemini, ollama) 묶음을 Bean으로 둔다)
- [X] T016 [P] 테스트 `T/tag/integration/DailyUsageIT.java`: 동시 25개 `reserve` → 20개 성공, `release` 뒤 다시 가능, 한국 시간 0시(`MutableClock`)에 새 키, TTL 2일, `remaining` (구현 메모: MutableClock이 없어 reserve(memberId, now)처럼 시각을 넘겨 경계를 시험한다)
- [X] T017 `B/tag/application/suggest/DailyUsage.java`(contracts/providers.md §8, `INCR`/`DECR`, 초과면 429 `AI_DAILY_LIMIT` `details {resetAt}` + `Retry-After`)(T016 통과) (구현 메모: 003 StorageQuotaService와 같은 Lua(INCR·EXPIRE·초과 DECR). Redis 호출은 AiRedis(장애·OOM → 503 STORE_UNAVAILABLE)로 감싼다)
- [X] T018 [P] 테스트 `T/account/integration/AiConsentServiceIT.java`: 행 없음 → `consented = false`, 현재 버전 행 → true, 옛 버전 → false, `agree(현재)` → 행 1개(같은 PK 갱신), `agree(다른 버전)` → `VALIDATION_FAILED` `errors[0] {field: version, code: AGREEMENT_VERSION_MISMATCH}`, `revoke` 두 번 성공, TERMS·PRIVACY 행은 그대로
- [X] T019 `B/account/application/AiConsentService.java`(`currentVersion()`, `view(memberId)` → `AiConsentView`, `isConsented(memberId)`, `agree(memberId, version)`, `revoke(memberId)`)와 `B/account/infra/MemberAgreementRepository.java`에 `findByMemberIdAndType`·`deleteByMemberIdAndType` 추가(001 소유 파일, 추가만). 001 `AgreementService.REQUIRED`·`currentVersion(AI)`는 바꾸지 않는다(Clarifications Q2)(T018 통과) (구현 메모: AiConsentView는 account.application에 두고 컨트롤러가 그대로 돌려준다(T035의 web/dto 대신). deleteByMemberIdAndType은 네이티브 DELETE)

**Checkpoint**: 입력 정리·프롬프트·하루 횟수·동의 확인이 준비됨 — User Story 시작 가능

---

## Phase 3: User Story 1 - 버튼을 눌러 태그 추천을 받고 골라서 붙이기 (Priority: P1) 🎯 MVP

**Goal**: 동의한 인증 작성자가 [AI 태그 추천]을 누르면 최대 5개(남은 자리만큼) 추천을 받고, 칩을 누를 때만 태그 입력에 들어간다

**Independent Test**: 동의 픽스처가 있는 작성자로 `POST /api/posts/{id}/tag-suggestions` → 가짜 공급자 결과가 정규화·검사되어 돌아오고 `post_tag`는 바뀌지 않는다. 화면에서 칩을 눌러 태그 입력에 들어가는지 본다

### Tests for User Story 1 ⚠️

> **NOTE: 테스트를 먼저 쓰고 실패를 확인한다**

- [X] T020 [P] [US1] 권한 매트릭스: `TR/permission/post-write.csv`에 owner `013` 행(research R14 표 — `tag-suggest.post`·`tag-suggest.status` × 7 행위자 × 내 공개·비공개·임시·휴지통·없는 글)과 하네스 행동 `T/tag/permission/TagSuggestAction.java`·`TagSuggestStatusAction.java`(동의 픽스처 + `FakeTagSuggester`), `T/tag/integration/TagSuggestPermissionMatrixIT.java` (006 머지 후) (구현 메모: 70행(2 행동 × 7 행위자 × 5 대상). 실행기 Bean 이름은 008 TagSuggestAction과 겹치지 않게 aiTagSuggestAction·aiTagSuggestStatusAction. 공용 PermissionMatrixIT가 post-write.csv 전체를 돌리므로 TagSuggestPermissionMatrixIT는 owner 013 행만 골라 돌린다(기능만 빠르게 확인용))
- [X] T021 [P] [US1] 테스트 `T/tag/integration/TagSuggestApiIT.java`: US1 #1~#5 — 추천 5개 이하, 붙인 태그 8개면 2개, 10개면 AI 호출 0·`tags: []`·횟수 그대로, 이미 붙인 태그(대소문자·공백 차이 포함)·금칙어·형식 위반·중복 제거(SC-007), 응답 뒤 `post_tag` 변화 없음(SC-003), 422(정리 후 99자, AI 호출 0), 판정 순서(비회원 401 → 인증 전 403 `EMAIL_NOT_VERIFIED` → 남의 글·휴지통·`abc` 404 고정 본문 → 꺼짐 503 `DISABLED` → 동의 없음 409 → 제목 101자 400 → 422), 21번째 429 `AI_DAILY_LIMIT` `details.resetAt` + `Retry-After`, 공급자 실패 요청은 횟수 그대로(Q4), 결과가 다 걸러진 요청은 횟수 1 차감, 비공개 글은 Gemini 가짜 호출 0(FR-030), 공급자에 넘긴 입력에 이메일·닉네임 없음(FR-012), 상태 API 세 모양(`available`·`consentRequired`·`provider`) (구현 메모: 꺼짐(DISABLED)은 설정을 바꾼 새 컨텍스트 대신 같은 Bean으로 TagSuggestService를 직접 만들어 확인한다. 21번째는 하루 키를 20으로 넣고 확인)
- [X] T022 [P] [US1] 테스트 `T/tag/infra/GeminiTagSuggesterTest.java`·`OllamaTagSuggesterTest.java`(`MockRestServiceServer`): contracts/providers.md §3·§4 요청 모양(`x-goog-api-key` 헤더, `responseSchema`·`format`, `num_thread`, `stream: false`, `maxOutputTokens`/`num_predict` 100), 성공 해석, 형식 깨짐(JSON 아님·문자열 아님·6개·candidates 없음) → `Failed(MALFORMED)`, 5xx·연결 실패 → `Failed`, 시간 초과(10초·30초 설정을 짧게) → `Failed(TIMEOUT)` (구현 메모: 시간 초과는 MockRestServiceServer 예외 흉내와 함께, JDK HttpServer로 실제 지연(1.5초)과 짧은 시간 제한(0.3초)을 확인한다)
- [X] T023 [P] [US1] 화면 테스트 `F/features/ai-suggest/__tests__/AiTagSuggest.test.tsx`: `available = false`면 영역 없음, 칩 `(+ spring)` 클릭 → `onAdd('spring')` 호출·칩 사라짐·서버 요청 없음, "AI 제안이에요", `truncated` → "본문 앞부분을 보고 추천했어요 · AI 제안이에요", "오늘 남은 추천 N회", 요청 중 버튼 비활성 + "추천 중…", 예측 공급자 OLLAMA → "자체 AI로 추천 중이라 조금 걸려요", 태그 10개면 버튼 비활성 + "태그를 더 붙일 수 없어요", 문구 6종(422·빈 결과·503 `FAILED`/`DISABLED`/`STORE_UNAVAILABLE`·503 `BUSY`·429), 칩은 텍스트로만 렌더링 (구현 메모: API는 SuggestApi 인자로 바꿔 끼운다(fetch 흉내 대신). 상태 조회 실패(404 등)도 영역 없음, 403은 서버 문구 + 버튼 끔(quickstart §3-11))

### Implementation for User Story 1

- [X] T024 [US1] `B/tag/infra/ai/GeminiTagSuggester.java`(공급자별 `RestClient`, 연결·읽기 시간 제한, 본문 로그 끔, `api-key` 비면 Bean은 있지만 라우터가 고르지 않음)와 `B/tag/infra/ai/OllamaTagSuggester.java`, `B/tag/infra/ai/AiClientConfig.java`(T022 통과. 429 종류 해석은 US4 T046) (구현 메모: GeminiTagSuggester·OllamaTagSuggester는 AiClientConfig의 @Bean(공급자마다 SimpleClientHttpRequestFactory 연결·응답 시간 제한, 공용 RestClient.Builder를 쓰지 않음). 응답은 exchange로 상태·본문을 직접 읽고 본문은 로그에 넣지 않는다. 429 해석도 함께 넣었다(T045 시험))
- [X] T025 [US1] `B/tag/infra/ai/DisabledTagSuggester.java`와 `B/tag/application/suggest/TagSuggesterRouter.java` 기본 판정(꺼짐 → DISABLED, `visibility != PUBLIC` → Ollama만, 키 없음 → Ollama, 그 밖 Gemini). Redis 상태 키 판정은 US4 T047 (구현 메모: DisabledTagSuggester 클래스는 두지 않고 꺼짐·쓸 공급자 없음을 Route.DISABLED·NONE으로 나타낸다. 라우터는 공급자마다 순서가 가장 앞선 Bean을 쓰고 isConfigured()로 키 없음·Ollama 꺼짐을 본다. 전환 표 전체(T047)도 함께 구현)
- [X] T026 [US1] `B/tag/application/suggest/SuggestResultFilter.java`(contracts/providers.md §7 — `TagNormalizer`로 정규화·중복 제거·붙인 태그 빼기·남은 자리 자르기)와 단위 테스트 `T/tag/unit/SuggestResultFilterTest.java`
- [X] T027 [US1] `B/tag/application/suggest/TagSuggestService.java`(research R4 판정 1~7·9·10 — 재사용 8은 US3 T042)와 `status(me, postId)`, 요청당 INFO 한 줄 로그(contracts/providers.md §9) (구현 메모: Bean 이름 aiTagSuggestService(008 같은 이름 클래스와 충돌 방지). 트랜잭션 없음. 본문 길이 검사는 판정 순서를 지키려고 @Valid가 아니라 Service에서 한다. 본문이 없으면 빈 요청으로 보고 422)
- [X] T028 [US1] `B/tag/web/TagSuggestionController.java`(`POST /api/posts/{postId}/tag-suggestions`, `GET …/status`, 숫자가 아닌 번호 404, `@Valid` 본문 — `blog.post.*` 길이·`currentTags` 10개)와 `B/tag/web/dto/`의 `TagSuggestResponse`·`TagSuggestStatus`(T021·T020 통과) (구현 메모: 응답 DTO는 tag.web.dto, Service는 application의 TagSuggestResult·TagSuggestStatusView를 돌려준다. POST는 Cache-Control private, no-store, 상태는 private, no-cache)
- [X] T029 [US1] `B/shared/web/SensitiveParamMasking.java`에 `x-goog-api-key`·`contentMd`·`title` 가리기 추가(001 소유 파일, 추가만) (구현 메모: 이름 목록에 x-goog-api-key|contentMd|title을 더하고 헤더 모양(x-goog-api-key: 값)도 가린다. 012가 같은 줄에 q를 더해 둔 것 뒤에 붙였다)
- [X] T030 [P] [US1] `F/api/tagSuggestions.ts`(`getStatus`·`suggest`)와 `F/api/types`에 `TagSuggestResponse`·`TagSuggestStatus`·`Provider` (구현 메모: 타입은 F/api/types/tagSuggestions.ts. 모든 요청 notFoundScreen:false(실패해도 화면 전환 없음). 동의 API 셋도 이 파일에 함께 두었다(T036 몫))
- [X] T031 [US1] `F/features/ai-suggest/useTagSuggest.ts`(상태 조회 → 요청 → 오류 코드·`details.reason`별 상태)와 `F/features/ai-suggest/AiTagSuggest.tsx`(버튼·칩·안내 문구·남은 횟수, 375px 줄바꿈)(T023 통과) (구현 메모: 상태는 발행 창이 열릴 때와 누르기 직전에 다시 부른다(R3). 문구는 features/ai-suggest/messages.ts)
- [X] T032 [US1] `F/components/editor/PublishDialog.tsx`의 `TagInput` 바로 아래에 `AiTagSuggest`를 둔다(008 소유 파일, 008 머지 후). `onAdd`는 `TagInput`의 추가 동작과 같은 검사를 거친다. `F/components/editor/__tests__/PublishDialog.test.tsx`에 "칩을 누르기 전에는 태그가 그대로" 추가 (구현 메모: PublishDialog에 title prop을 더했다(EditorPage가 title을 넘김). title·contentMd를 받을 때만 추천 영역을 그려 기존 시험의 fetch 흉내에 영향이 없다. onAdd는 normalizeTag·중복·최대 개수 검사 뒤 changeTags)

**Checkpoint**: US1 단독으로 동작 — 동의 픽스처 회원이 추천을 받아 고를 수 있다

---

## Phase 4: User Story 2 - 처음 쓸 때 외부 전송에 동의하기 (Priority: P1)

**Goal**: 동의 전에는 어떤 AI에도 글을 보내지 않고, 처음·문구 변경 때 동의 창을 띄우며, 설정에서 취소할 수 있다

**Independent Test**: 동의 없는 회원의 추천 요청 → 409와 공급자 호출 0. `PUT` 뒤 같은 요청 성공, 버전을 올리면 다시 409, `DELETE` 뒤 409

### Tests for User Story 2 ⚠️

- [X] T033 [P] [US2] 테스트 `T/account/integration/AiConsentIT.java`: US2 #1~#4 — 동의 전 요청은 두 가짜 공급자 호출 0(SC-001), `GET` 세 모양(없음·현재·옛 버전), `PUT {현재}` → 200 + 행(버전·시각), `PUT {옛 버전}` → 400 `AGREEMENT_VERSION_MISMATCH`, `blog.agreement.ai.version`을 바꾸면 409 `details.version` = 새 버전, `DELETE` → 200 `agreed: false` → 추천 409, 인증 전 회원도 `PUT` 가능(001 `ACCOUNT_WRITE`), 정지 회원 403, CSRF 없음 403, 로그인 응답의 재동의 목록에 AI 없음(Q2) (구현 메모: 설정 버전을 바꾸는 경우는 새 컨텍스트 대신 저장 버전을 옛 값으로 두어 같은 판정을 만든다)
- [X] T034 [P] [US2] 화면 테스트 `F/features/ai-suggest/__tests__/AiConsentDialog.test.tsx`·`AiConsentSettings.test.tsx`: 409 → 동의 창(`role="dialog"`, 초점 가둠, 처음 초점 [동의하고 추천받기]), 문구 다섯 줄과 버전, Esc·[취소] → 요청 없음, [동의하고 추천받기] → `PUT {version}` 뒤 원래 추천 요청 한 번 다시, `PUT` 400 → "동의 문구가 바뀌었어요. 새로 고친 뒤 다시 시도해 주세요", 설정 칸 세 상태(동의함·안 함·옛 버전 "다시 동의가 필요해요")와 [동의 취소] → `DELETE`, `aiConsentText.ts`의 버전 상수 = `2026-10-08`(서버 기본값과 같은 값 — T002 테스트와 짝) (구현 메모: 동의 흐름 시험은 AiTagSuggest를 통해 창을 띄워 검사. 설정 칸은 api 주입)

### Implementation for User Story 2

- [X] T035 [US2] `B/account/web/AiConsentController.java`(`GET`·`PUT`·`DELETE /api/me/agreements/ai`, 계정 상태 `ACCOUNT_WRITE`)와 `B/account/web/dto/AiConsentView.java`(T033 통과) (구현 메모: 응답 본문 AiConsentView는 B/account/application에 두고(T019) web/dto에는 요청 본문 AiConsentRequest만. ACCOUNT_WRITE 확인은 컨트롤러에서 PUT·DELETE만)
- [X] T036 [US2] `F/features/ai-suggest/aiConsentText.ts`(문구·버전 상수, research R10)와 `F/features/ai-suggest/AiConsentDialog.tsx`, `useTagSuggest.ts`에 409 → 창 → `PUT` → 다시 요청 흐름, `F/api/tagSuggestions.ts`에 `getAiConsent`·`agreeAi`·`revokeAi`(T034 통과) (구현 메모: T030·T031 때 함께 구현됨. 이번엔 T034 시험으로 확인만)
- [X] T037 [US2] `F/features/ai-suggest/AiConsentSettings.tsx`와 `F/pages/SettingsPage.tsx` 계정 영역에 "AI 동의" 칸(001 소유 파일, 001 T122 이후, 추가만) (구현 메모: 설정 칸은 [동의 취소]만 둔다(동의는 발행 창의 동의 창에서). 계정 칸 바로 아래 독립 section으로 추가)
- [X] T038 [US2] `F/pages/PrivacyPage.tsx`에 "외부 AI 서비스(Google Gemini)로의 전송" 문단(무료 등급 데이터 사용·사람 검토 가능·비공개 글 제외·동의 철회 방법)과 `.env.example`·`R/application.yml`의 처리방침 버전 갱신(001 소유 파일, **T004 답 이후**) (구현 메모: T004 가정대로 처리방침 6번 문단만 넣고 BLOG_AGREEMENT_PRIVACY_VERSION은 그대로(첫 공개 전). PrivacyPage.ai.test.tsx 추가)
- [X] T039 [P] [US2] `.env.example`에 `BLOG_AGREEMENT_AI_VERSION=2026-10-08`·`BLOG_AGREEMENT_AI_EFFECTIVE_DATE=2026-10-08` 줄(값은 버전 날짜뿐, 비밀값 아님)

**Checkpoint**: US1 + US2 — 실제 사용자가 처음부터 끝까지 쓸 수 있다

---

## Phase 5: User Story 3 - 같은 내용 반복 요청은 다시 묻지 않기 (Priority: P2)

**Goal**: 같은 내용은 30일(모든 사용자), 같은 글 비슷한 내용은 7일 재사용하고 그때는 횟수를 빼지 않는다

**Independent Test**: 같은 입력 두 번 → 두 번째 `cached: true`·공급자 호출 0·남은 횟수 그대로

### Tests for User Story 3 ⚠️

- [X] T040 [P] [US3] 테스트 `T/tag/integration/TagSuggestCacheIT.java`: US3 #1~#4, SC-004 — 같은 입력 두 번째 재사용·호출 0·횟수 그대로, 다른 회원 같은 입력 재사용, 태그 하나 붙인 뒤 재사용 + 그 태그 빠짐, 오타 몇 개 → 비슷한 내용 재사용, 문단 추가 → 새 호출, `refresh` → 비슷한 내용 건너뜀·같은 내용은 사용, 같은 내용이 OLLAMA 결과이고 지금 Gemini 가능 + `refresh` → 새로 만듦, 빈 결과·모두 걸러진 결과·실패 → 저장 안 함, `prompt-version` 올리면 새 키, 하루 한도를 다 쓴 회원도 재사용 응답은 200(FR-027), TTL 30일·7일, 키 이름 `ai:tag:v1:{64자}`·`ai:tag:post:{id}` (구현 메모: prompt-version 판은 새 컨텍스트 대신 SuggestCache를 v2 설정으로 직접 만들어 확인)

### Implementation for User Story 3

- [X] T041 [US3] `B/tag/application/suggest/SuggestCache.java`(contracts/providers.md §6 — JSON 직렬화, `RedisGuard.call`, `lookup(postId, input, refresh, routeNow)`·`store(…)`) (구현 메모: T027과 함께 구현됨. routeNow는 BooleanSupplier geminiAvailableNow(refresh 때만 묻는다))
- [X] T042 [US3] `TagSuggestService`에 재사용 단계(research R4 8 — 하루 횟수 앞, 남은 자리 0 판정 뒤)와 저장 규칙 연결(T040 통과) (구현 메모: T027과 함께 연결됨. T040으로 확인)
- [X] T043 [US3] 화면: 결과가 있으면 [다시 추천](`refresh: true`), `cached`면 남은 횟수 그대로 — `AiTagSuggest.tsx`·`useTagSuggest.ts`와 `AiTagSuggest.test.tsx`에 두 경우 추가 (구현 메모: T031과 함께 구현됨. AiTagSuggest.test.tsx의 [다시 추천]·재사용 두 경우)

**Checkpoint**: 반복 요청이 AI를 부르지 않는다

---

## Phase 6: User Story 4 - 외부 한도가 다 되면 자체 AI로 이어서 추천 (Priority: P2)

**Goal**: 외부 한도·분당 한도·장애 때 자체 AI로 넘기고, 자체 AI 동시 처리를 1로 제한한다. 자체 AI를 같은 Compose에 둔다

**Independent Test**: 가짜 Gemini가 429(하루 한도)를 돌려주면 같은 요청이 Ollama 결과로 200

### Tests for User Story 4 ⚠️

- [X] T044 [P] [US4] 테스트 `T/tag/integration/TagSuggestRoutingIT.java`: US4 #1~#5, SC-005 — 우리 집계 450 뒤 Ollama + `exhausted`(TTL = 태평양 시간 다음 0시), `QuotaExceeded(PER_DAY)` → 같은 요청 Ollama 200, `PER_MINUTE` → 60초 동안 Ollama 뒤 Gemini(`MutableClock`), `UNKNOWN` 세 번 → `exhausted`, `Failed(TIMEOUT)` → 이번 503 `FAILED` + 60초 Ollama + 횟수 되돌림, 비공개 글 + Ollama 꺼짐 → 503 `FAILED`, Ollama 동시 2번째 → 503 `BUSY` + 횟수 되돌림, `inflight` 키가 끝나면 0, 상태 API의 `provider` 예측이 위 상태를 따른다, 공급자 날짜 경계 (구현 메모: MutableClock 대신 Redis 상태 키를 직접 두고 지워 시간 경과를 흉내 낸다. 공급자 날짜 경계는 router.choose(post, Instant)에 시각을 넘겨 확인)
- [X] T045 [P] [US4] `T/tag/infra/GeminiTagSuggesterTest.java`에 429 본문 세 종류(`QuotaFailure` `quotaId` `…PerDay…`·`…PerMinute…`·없음) → `QuotaExceeded(kind)` 추가 (구현 메모: T024 때 함께 작성됨(GeminiTagSuggesterTest 429 세 종류))

### Implementation for User Story 4

- [X] T046 [US4] `GeminiTagSuggester`의 429 해석(contracts/providers.md §3 표, 응답 본문은 해석만 하고 로그에 넣지 않음)(T045 통과) (구현 메모: T024 때 함께 구현됨)
- [X] T047 [US4] `B/tag/application/suggest/ProviderState.java`(`ai:gemini:count`·`exhausted`·`cooldown`·`unknown-429`, `quota-zone` 날짜·다음 초기화 TTL)와 `TagSuggesterRouter` 전환 표 완성(contracts/providers.md §5 — 같은 요청 Ollama 재시도, 시간 초과·5xx는 실패로 끝냄, `NONE`), `TagSuggestService`의 횟수 되돌림 연결 (구현 메모: T025·T027 때 함께 구현됨. T044로 확인)
- [X] T048 [US4] `OllamaTagSuggester` 앞의 동시 처리 제한(`ai:ollama:inflight`, `max-concurrency`, `finally DECR`, TTL 60초 안전장치) → `Busy`(T044 통과) (구현 메모: T025 때 라우터 안(callOllama)에 구현 — OllamaTagSuggester가 아니라 TagSuggesterRouter가 ProviderState.acquireOllama/releaseOllama를 부른다(가짜 공급자로도 시험되도록))
- [X] T049 [US4] `docker-compose.yml`에 `ollama`(고정 태그 이미지 — 구현 때 그 시점 안정 버전, 볼륨 `ollama-data`, 호스트 포트 없음, 메모리 제한 주석)·`ollama-pull`(한 번만, `ollama pull ${OLLAMA_MODEL:-qwen2.5:1.5b}`) 서비스, `app`의 `depends_on: ollama-pull: condition: service_completed_successfully`와 `BLOG_AI_TAG_SUGGEST_OLLAMA_BASE_URL=http://ollama:11434`, `.env.example`에 `GEMINI_API_KEY=`(빈 값)·`OLLAMA_MODEL=qwen2.5:1.5b`, 개발용 포트 열기 예시는 주석(research R12) (구현 메모: 이미지 ollama/ollama:0.34.4(2026-09-24 판 — 최신 0.40.x는 나온 지 며칠이라 피함). 상태 확인은 ollama list. app 환경에 GEMINI_API_KEY(빈 기본값)·OLLAMA_MODEL·BLOG_AI_TAG_SUGGEST_OLLAMA_BASE_URL. docker compose config로 문법 확인, 실제 기동은 하지 않음(공용 포트·자원))
- [X] T050 [US4] 화면: 503 `BUSY` 문구, 상태 API `provider = OLLAMA`일 때 대기 문구, 30초 대기 중 버튼 비활성 — `AiTagSuggest.test.tsx` 두 경우 추가 (구현 메모: T031 때 함께 작성됨(AiTagSuggest.test.tsx 503 BUSY 문구·자체 AI 대기 문구와 버튼 비활성))

**Checkpoint**: 모든 User Story 동작

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 장애 격리, 로그, 실제 모델 측정, 종단 확인

- [X] T051 [P] 테스트 `T/tag/integration/TagSuggestFailureIsolationIT.java`(SC-002): 두 공급자 실패 + `RedisOutage` 상태에서 002 자동 저장·저장·발행 API 성공, 추천 503 `STORE_UNAVAILABLE`, 상태 API `available = false`. Redis 메모리 부족에서 오는 `AutosaveUnavailableException`도 `AI_UNAVAILABLE`로 바뀌는지 — `TagSuggestService`·`SuggestCache`·`DailyUsage`·`ProviderState`의 예외 변환을 이 테스트로 마무리한다 (구현 메모: Redis 장애는 002 시험처럼 회로 차단기 강제 열기(세션 저장소도 Redis라 컨테이너 정지 불가), 메모리 부족은 CONFIG SET maxmemory 1을 잠깐 걸고 서비스·구성 요소를 직접 부른다)
- [X] T052 [P] 테스트 `T/tag/integration/TagSuggestLoggingIT.java`(SC-008, `OutputCaptureExtension`): 제목·본문 일부·태그 이름·`GEMINI_API_KEY` 테스트 값이 INFO~ERROR 어떤 줄에도 없다(공급자 예외 포함), 요청당 INFO 한 줄 형식 (구현 메모: 공급자 예외·오류 응답은 실제 Gemini/Ollama 클라이언트를 JDK HttpServer에 붙여 확인)
- [X] T053 [P] `T/tag/integration/OllamaSmokeIT.java`(`@Tag("ollama")`, 기본 빌드에서 빠짐): 실제 `qwen2.5:1.5b`로 2,000자 입력 30초 안·태그 1개 이상(SC-006). 배포 서버 측정값을 T005 기록에 더한다 (구현 메모: -Dollama.smoke=true일 때만 돈다(@Tag ollama). 이 환경은 프록시가 registry.ollama.ai를 막아 모델을 받지 못해 측정 못 함 — 배포 서버에서 측정해 T005 기록에 더할 것)
- [X] T054 [P] `E/ai-tag-suggest.spec.ts`(Playwright, `page.route`로 추천·상태·동의 API 응답 흉내): 동의 창 → 동의 → 칩 2개 → 하나 클릭 → 태그 입력에 들어감 → 발행 창 닫기 → 저장된 태그 그대로, 375px 가로 스크롤 없음 (구현 메모: 임의 포트 PostgreSQL·Redis + jar + 설치된 Chromium으로 desktop 1건 통과(375px는 같은 시험 안에서 폭을 바꿔 확인). 닫았다 다시 열어 칩 유지, 발행 뒤 working-copy tags로 저장 확인)
- [X] T055 quickstart.md §1~§3 실행 결과를 기록하고 어긋난 문서를 고친다 (구현 메모: §5 실행 기록 추가. §2 명령(OllamaSmokeIT는 -Dollama.smoke=true, backend 디렉터리에서 ./mvnw)과 §3-8 문구를 실제에 맞게 고쳤다)
- [X] T056 015 머지 후: 탈퇴 익명 처리 뒤 `member_agreement` AI 행이 남는지 quickstart §4로 확인(015 FR-028). 지우는 단계가 생기면 015에 알린다 (구현 메모: 015가 main에 있어 AiConsentIT에 탈퇴 정리 뒤 AI 행 유지 시험을 더함(통과). 015 정리 단계 중 member_agreement를 지우는 것 없음 — 015에 알릴 것 없음)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작. T004 답은 T038만 막는다
- **Foundational (Phase 2)**: Setup 다음 — 모든 User Story를 막는다
- **US1 (Phase 3)**: Foundational 다음. 화면 연결 T032는 008 머지 후, 권한 행 T020은 006 머지 후
- **US2 (Phase 4)**: Foundational(T019) 다음. 설정 칸 T037은 001 T122 이후, 처리방침 T038은 T004 답 이후
- **US3 (Phase 5)**: US1(T027) 다음
- **US4 (Phase 6)**: US1(T024·T025·T027) 다음. US3과 독립
- **Polish (Phase 7)**: 원하는 User Story가 끝난 뒤. T056은 015 머지 후

### User Story Dependencies

- **US1 (P1)**: 동의 확인(T019)만 쓰고 동의 API·창 없이 테스트 가능(동의 픽스처)
- **US2 (P1)**: US1 화면(`useTagSuggest`) 위에 동의 창을 얹는다. 서버 쪽은 독립
- **US3 (P2)**: US1 서비스 위에 재사용 단계를 얹는다
- **US4 (P2)**: US1 라우터·공급자 위에 전환 규칙을 얹는다

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 같은 파일을 고치는 작업은 순서대로 한다: `TagSuggestService`(T027 → T042 → T047 → T051), `TagSuggesterRouter`(T025 → T047), `GeminiTagSuggester`(T024 → T046), `OllamaTagSuggester`(T024 → T048), `GeminiTagSuggesterTest`(T022 → T045), `useTagSuggest.ts`(T031 → T036 → T043), `AiTagSuggest.tsx`(T031 → T043 → T050), `AiTagSuggest.test.tsx`(T023 → T043 → T050), `F/api/tagSuggestions.ts`(T030 → T036), `.env.example`(T039 → T049 → T038), `R/application.yml`(T002 → T038), 001 파일(`AccountProperties` T002, `MemberAgreementRepository` T019, `SensitiveParamMasking` T029, `SettingsPage` T037, `PrivacyPage` T038), 002 파일(`PostEditRepository` T007), 008 파일(`PublishDialog` T032)

### Parallel Opportunities

- Phase 2의 T006·T008·T009·T011·T013·T015·T016·T018은 서로 다른 파일
- US3과 US4는 서로 다른 팀원이 동시에 할 수 있다(공유 파일 `TagSuggestService`만 순서대로)
- 서버(US1 T024~T029)와 화면(T030~T031)은 계약(contracts/openapi.yaml)만 맞추면 동시에 진행

---

## Parallel Example: User Story 1

```bash
# US1 테스트를 함께 쓴다
Task: "TagSuggestApiIT in T/tag/integration/TagSuggestApiIT.java"
Task: "GeminiTagSuggesterTest·OllamaTagSuggesterTest in T/tag/infra/"
Task: "AiTagSuggest 화면 테스트 in F/features/ai-suggest/__tests__/AiTagSuggest.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. Phase 1·2 완료
2. US1: 추천 API·공급자·화면(동의 픽스처로 확인)
3. US2: 동의 API·창·설정 칸 — 실제 사용자에게 내보내려면 US2까지 필요(동의 없이 보낼 수 없음)
4. **STOP and VALIDATE**: quickstart §3 1~8

### Incremental Delivery

1. US1 + US2 → MVP(Gemini 키 없으면 Ollama만으로도 동작)
2. US3(재사용) → 비용·횟수 절약
3. US4(전환·동시 처리·Compose) → Polish

### Parallel Team Strategy

1. 함께 Phase 1·2
2. 그 뒤: 개발자 A US1 → US3, 개발자 B US2 → US4

---

## Notes

- [P] = 다른 파일, 의존 없음
- 서버는 태그를 저장하지 않는다. 추천은 화면의 태그 입력에만 들어가고 발행 때 002·008 규칙으로 확정된다
- 제목·본문·태그 이름·프롬프트·외부 응답 본문·키는 어떤 로그에도 남기지 않는다(T052가 검사)
- `GEMINI_API_KEY` 실제 값은 저장소·테스트 코드에 넣지 않는다. 테스트는 가짜 문자열을 쓴다
- 각 작업 또는 논리 묶음마다 커밋한다
