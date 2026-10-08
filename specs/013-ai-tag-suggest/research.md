# Research: AI 태그 추천

**Feature**: 013-ai-tag-suggest | **Date**: 2026-10-08

spec Implementation Notes와 원문(34·51·02)에서 정한 것은 "확정", 이 계획이 새로 정한 것은 "제안"으로 표시한다.

## R1. 저장 (확정)

- **Decision**: 테이블 변경 없음.
  - AI 동의: V1 `member_agreement (member_id, type, version, agreed_at)`, PK `(member_id, type)`, `ck_member_agreement_type`에 `AI`가 있다. 동의 = `INSERT … ON CONFLICT (member_id, type) DO UPDATE SET version = :v, agreed_at = :now`(001 `MemberAgreementRepository.upsert`), 철회 = `DELETE … WHERE member_id = :m AND type = 'AI'`(51 §4).
  - 재사용 저장소·하루 횟수·공급자 상태·인기 태그는 Redis(data-model §2). 보존 데이터와 캐시의 Redis 분리·`maxmemory`는 아키텍처 정리(M30) 몫.
- 탈퇴 익명 처리 때 AI 동의 행은 지우지 않는다(015 FR-028, 2026-10-07 E6). 44 §4의 `ai_consent_at` NULL 처리는 V1에서 없어졌다.
- **Rationale**: 2026-10-07 회의 E2·H9, 51 §2·§4, 001 data-model §2-3("AI는 013").

## R2. 모듈 배치 (제안)

- **Decision**:
  - `tag.application.suggest` — 추천 흐름 전체. 008 `TagNormalizer`·`TagQueryService`와 같은 모듈이라 정규화·인기 태그를 바로 쓴다.
  - `tag.infra.ai` — Gemini·Ollama HTTP 클라이언트. `TagSuggester` 인터페이스 뒤에 둔다(34 §2 `GeminiTagSuggester`/`OllamaTagSuggester`/`DisabledTagSuggester`).
  - `account.application.AiConsentService` — `member_agreement`의 AI 행. 001 `AgreementService.currentVersion(AI)`는 지금 "013이 관리"로 예외를 던지므로, AI 버전은 `AiConsentService.currentVersion()`이 `blog.agreement.ai.version`에서 읽는다. 001 `AgreementService.REQUIRED`(가입·로그인 재동의)는 바꾸지 않는다(Clarifications Q2).
  - `post.application.PostOwnershipQuery.findOwned(long postId, long memberId)` → `Optional<OwnedPost(long id, Visibility visibility)>` — 002 `PostEditRepository.isOwned`와 같은 조건(`author_id = :me AND deleted_at IS NULL`)에 공개 범위를 함께 읽는 post 공개 조회(새 클래스, post 모듈).
- **Rationale**: 02 §3, 헌법 II(다른 모듈 테이블 직접 접근 금지).
- **Alternatives considered**: 새 모듈 `ai` — 지금은 태그 추천 하나뿐이고 주제 추천(개인 확장)도 태그와 붙어 있다.

## R3. API (확정 + 제안)

- **Decision**:
  | API | 본문 | 성공 |
  |---|---|---|
  | `POST /api/posts/{postId}/tag-suggestions` | `{title, contentMd, currentTags, refresh}` — 에디터의 지금 값(저장 안 된 것 포함) | 200 `{tags, provider, cached, truncated, remainingToday}` |
  | `GET /api/posts/{postId}/tag-suggestions/status` (제안) | — | 200 `{available, consentRequired, consentVersion, provider, remainingToday}` |
  | `GET /api/me/agreements/ai` (제안) | — | 200 `{agreed, version, currentVersion, agreedAt}` |
  | `PUT /api/me/agreements/ai` (제안) | `{version}` | 200 같은 모양 (버전이 현재와 다르면 400 `VALIDATION_FAILED`, `errors[{field: version, code: AGREEMENT_VERSION_MISMATCH}]` — 001 코드 재사용) |
  | `DELETE /api/me/agreements/ai` (제안) | — | 200 같은 모양 `agreed: false` (없어도 200) |
  - `provider`: `GEMINI` | `OLLAMA`. `cached`: 재사용 저장소로 답했으면 true. `truncated`: 정리된 입력이 공급자 최대 길이를 넘어 잘렸으면 true. `remainingToday`: 이번 요청 뒤 남은 횟수.
  - 상태 API는 발행 창이 열릴 때와 버튼을 누르기 직전에 부른다: `available = false`면 버튼 숨김(기능 꺼짐) 또는 안내, `consentRequired`면 동의 창, `provider = OLLAMA`면 "자체 AI로 추천 중이라 조금 걸려요"를 미리 고른다. 예측일 뿐이고 실제 공급자는 추천 응답의 `provider`다.
  - 동의 상태는 001 `GET /api/me`에 넣지 않는다. 설정 화면은 `GET /api/me/agreements/ai`로, 발행 창은 상태 API의 `consentRequired`로 본다. 동의 API 셋은 모두 로그인만 요구하고 계정 상태 게이트는 001 `ACCOUNT_WRITE`를 따른다(탈퇴 유예는 001 게이트가 막는다).
- **Rationale**: 34 §9. 원문 동의 저장 API가 없어 `PUT`/`DELETE`(상태 지정 규약, 004 R-20)로 정했다.

## R4. 판정 순서와 하루 횟수 (확정 + 제안)

- **Decision**: `TagSuggestService.suggest(me, postId, body)`:
  1. 401 `LOGIN_REQUIRED`(001)
  2. 403 — `AccountStatusGuard.requireActive(me, CONTENT_WRITE)`: 인증 전 `EMAIL_NOT_VERIFIED`, 남은 세션 정지 `ACCOUNT_SUSPENDED`. 탈퇴 유예는 001 게이트
  3. 404 — `PostOwnershipQuery.findOwned(postId, me)`가 비면(남의 글·없음·휴지통·숫자가 아닌 번호) 고정 본문
  4. 503 `AI_UNAVAILABLE` `details.reason = DISABLED` — `blog.ai.tag-suggest.enabled = false`
  5. 409 `AI_CONSENT_REQUIRED` — AI 동의 행이 없거나 버전이 현재와 다름. `details = {version: 현재 버전}`
  6. 400 `VALIDATION_FAILED` — 제목 100자·본문 100,000자·`currentTags` 10개 초과(`blog.post.*` 공유), 형식 오류
  7. 422 `CONTENT_TOO_SHORT` — 정리 후 100 코드 포인트 미만(`min-input-chars`). AI를 부르지 않는다
  8. 재사용 저장소(R8) — 맞으면 200 `cached: true`, 횟수 그대로
  9. 429 `TOO_MANY_REQUESTS` `details = {kind: "AI_DAILY_LIMIT", resetAt}` — 하루 20회
  10. 공급자 호출(R6) — 실패 503 `AI_UNAVAILABLE`(`FAILED`·`BUSY`)
- 하루 횟수 `ai:tag:usage:{memberId}:{yyyyMMdd}`(한국 시간 날짜, TTL 2일):
  - 9에서 `INCR` → 결과가 20 초과면 `DECR` 후 429. 이렇게 자리를 먼저 잡아 동시에 여러 요청이 와도 20을 넘지 않는다.
  - 10이 AI 응답을 받으면(검사에서 0개가 되어도) 그대로 둔다. 시간 초과·형식 오류·한도 초과·자체 AI 혼잡이면 `DECR`(Clarifications Q4).
  - `remainingToday = max(0, 20 − 현재 값)`.
- 9(429)가 8(재사용) 뒤인 까닭: 재사용 응답은 AI를 부르지 않으므로 한도를 다 쓴 사람도 받을 수 있다(FR-027).
- **Rationale**: 42 §3 판정 순서(401 → 403 → 404 → 400/409/422 → 429), README 2026-10-07, 02 §5.

## R5. 입력 정리 (확정)

- **Decision**: `SuggestInputCleaner.clean(title, contentMd)` → `CleanedInput(text)`:
  - 002가 쓰는 commonmark `Parser`(GFM 확장 포함)로 본문을 읽고 노드를 걸으며 글자만 모은다.
    | 노드 | 처리 |
    |---|---|
    | 제목·강조·굵게·취소선·목록 항목·인용 | 기호를 버리고 안의 글자만 |
    | 이미지 | 통째로 제거(대체 글자 포함) |
    | 링크 | 글자만, 주소 버림 |
    | 코드 블록 | 언어 이름 한 줄 + 앞 5줄 |
    | 인라인 코드 | 글자 그대로 |
    | HTML 블록·인라인 HTML | 버림 |
    | 표 | 칸 글자를 공백으로 이음 |
  - 결과: `제목 + "\n" + 본문 글자` → 연속 공백·줄바꿈을 하나로 → `Normalizer.normalize(NFC)` → `strip`. 대소문자는 바꾸지 않는다.
  - 길이는 코드 포인트로 센다. 공급자별 자르기(Gemini 8,000, Ollama 2,000)는 호출 직전에 하고 `truncated`를 정한다. 재사용 열쇠는 자르기 전 전체로 만든다(공급자가 바뀌어도 같은 열쇠).
- **Rationale**: 34 §4 ①~⑤(정리로 약 30% 줄어듦, §5-3).

## R6. 공급자 라우팅 (확정 + 제안)

- **Decision**: `TagSuggesterRouter.route(OwnedPost post, boolean refresh)`:
  | 조건 | 공급자 |
  |---|---|
  | 글 공개 범위가 `PUBLIC`이 아님(`PRIVATE`, 적용자 `FRIENDS`) | Ollama만. 꺼졌거나 바쁘면 503(Gemini로 넘기지 않음, FR-030) |
  | `ai:gemini:exhausted` 있음 | Ollama |
  | `ai:gemini:cooldown` 있음 | Ollama |
  | `ai:gemini:count:{공급자 날짜}` ≥ `gemini.daily-limit`(450) | Ollama (+ `exhausted`를 다음 초기화까지) |
  | Gemini 키가 없음(환경 변수 비어 있음) | Ollama |
  | 그 밖 | Gemini |
  - Gemini 호출 결과:
    | 결과 | 처리 |
    |---|---|
    | 200 + 형식 맞음 | 성공. `count` +1(호출 전에 이미 `INCR`), `unknown-429` 지움 |
    | 429 + 하루 한도(`QuotaFailure`의 `quotaId`에 `PerDay`) | `exhausted`(TTL = 다음 초기화까지) → **같은 요청을 Ollama로** |
    | 429 + 분당 한도(`PerMinute`) | `cooldown` 60초 → 같은 요청을 Ollama로 |
    | 429 + 종류 모름 | `cooldown` 60초, `ai:gemini:unknown-429:{날짜}` +1, 3이면 `exhausted` → 같은 요청을 Ollama로 |
    | 시간 초과(10초)·5xx·연결 실패 | `cooldown` 60초 → 이번 요청은 503 `FAILED`(Ollama까지 기다리게 하지 않음, FR-019) |
    | 200 + 형식 깨짐 | 503 `FAILED`(저장하지 않음) |
  - Ollama: `ai:ollama:inflight` `INCR`(TTL 60초 안전장치) → `max-concurrency`(1) 초과면 `DECR` 후 503 `BUSY`("잠시 후 다시 시도해 주세요"). 끝나면 `DECR`. 시간 초과(30초)·형식 깨짐 → 503 `FAILED`.
  - 공급자 날짜·초기화 시각: `gemini.quota-zone`(기본 `America/Los_Angeles` — 공급자 한도가 태평양 시간 0시에 초기화된다고 보고, 구현 때 콘솔 값으로 확인)으로 계산한다. 회원 하루 횟수는 한국 시간(spec Assumptions).
  - [다시 추천]일 때만 R8의 "같은 내용 결과가 자체 AI 것이면 외부로 다시 만들기"가 이 표를 한 번 더 본다.
- **Rationale**: 34 §3-2·§3-3, FR-017~FR-021·FR-030.

## R7. 외부 요청 모양 (확정 + 제안)

- **Decision**:
  - 프롬프트(`PromptBuilder`, `prompt-version`): ① 고정 지시문(글에 없는 기술 넣지 말 것, 태그 배열로만, 최대 5개, 이미 붙인 태그 제외, 짧은 소문자 표기 선호) → ② 인기 태그(Gemini: 상위 50, Ollama: 상위 50 중 정리된 입력에 실제로 나오는 것만) → ③ 이미 붙인 태그 → ④ 제목 + 정리된 본문(자른 것). 앞부분이 고정이라 공급자 쪽 앞부분 재사용에 유리하다(34 §4).
  - Gemini: `POST {gemini.base-url}/v1beta/models/{model}:generateContent`, 헤더 `x-goog-api-key: ${GEMINI_API_KEY}`, `generationConfig = {responseMimeType: "application/json", responseSchema: {type: OBJECT, properties: {tags: {type: ARRAY, items: {type: STRING}, maxItems: 5}}, required: [tags]}, maxOutputTokens: 100, temperature: 0.2}`.
  - Ollama: `POST {ollama.base-url}/api/chat`, `{model, stream: false, format: {JSON 스키마 같음}, options: {num_thread, num_predict: 100, temperature: 0.2}, messages: [{role: system, …}, {role: user, …}]}`. Ollama는 스키마 `description`을 전하지 않으므로 설명은 지시문에 넣는다(34 §6).
  - 응답 해석: 본문 JSON의 `tags`가 문자열 배열이 아니거나 5개를 넘으면 형식 깨짐(앞 5개만 쓰지 않는다 — 지시를 어긴 응답).
  - `RestClient`는 공급자마다 따로 만들고 연결·읽기 시간 제한을 각자 설정한다. 요청·응답 본문 로그는 끈다.
- **Rationale**: 34 §4·§6, FR-012·FR-016·FR-031.

## R8. 재사용 저장소 (확정)

- **Decision**: `SuggestCache`:
  | 종류 | 키 | 값 | TTL | 공유 |
  |---|---|---|---|---|
  | 같은 내용 | `ai:tag:v{prompt-version}:{SHA-256(정리된 입력 전체)}` | `{tags, provider, createdAt}` | 30일 | 모든 사용자 |
  | 같은 글 비슷한 내용 | `ai:tag:post:{postId}` | `{input(앞 8,000자), tags, provider, createdAt}` | 7일 | 그 글(=작성자) |
  - 조회 순서: `refresh = false`면 같은 내용 → 비슷한 내용(`TrigramSimilarity.jaccard(이번, 지난) ≥ 0.9`, 3-gram은 코드 포인트 단위, 공백 포함) → 없음. `refresh = true`면 비슷한 내용은 건너뛰고 같은 내용만 보되, 그 결과의 `provider = OLLAMA`이고 R6 표가 지금 Gemini를 고르면 새로 만든다(FR-028).
  - 저장: AI 응답의 검사 전 태그(정규화만 한 목록)가 1개 이상일 때만 두 키 모두 쓴다. 실패·빈 결과·검사에서 모두 걸러진 결과는 쓰지 않는다(FR-029).
  - 응답 때마다 R9 검사(이미 붙인 태그 빼기·남은 자리)를 다시 한다 — 열쇠에 붙인 태그·인기 태그가 없다(FR-026).
  - SimHash는 짧은 글에서 실패해 기각(34 §5-3).
- **Rationale**: 34 §5-1, FR-024~FR-029.

## R9. 결과 검사 (확정)

- **Decision**: AI가 준 문자열마다 008 `TagNormalizer.normalize(raw)`(9단계 — 금칙어 포함, 001 `BannedWordFilter`를 008이 씀) → `Accepted(name)`만 남김 → 중복 제거(처음 것) → `currentTags`도 같은 정규화 후 그 이름 빼기 → `min(5, blog.post.max-tags − currentTags 수)`개로 자름. 남는 것이 없으면 200 `tags: []` — 화면 "추천할 태그를 찾지 못했어요".
- 남은 자리가 0이면(이미 10개) AI를 부르지 않고 200 `tags: []`(횟수 그대로). 화면은 버튼을 비활성으로 두고 "태그를 더 붙일 수 없어요"를 보여 준다(제안).
- **Rationale**: 34 §6, FR-003·FR-032, SC-007.

## R10. 동의 (확정 + Clarifications Q1·Q2)

- **Decision**:
  - 현재 버전 `blog.agreement.ai.version`(예 `2026-10-08`), 화면 문구는 `F/features/ai-suggest/aiConsentText.ts`(버전 상수 포함). 두 값이 다르면 배포 오류 — 화면 단위 테스트가 상수를, 서버 설정 테스트가 기본값을 같은 값으로 검사한다.
  - 문구(34 §7-2 + Clarifications Q1): 제목과 본문 앞부분이 Google Gemini(무료 등급)로 전송됨 / Google이 서비스 개선에 쓰고 사람이 검토할 수 있음 / 외부 AI를 못 쓰면 자체 서버 AI로 처리하며 그때는 외부로 나가지 않음 / 비공개·친구 공개 글은 외부로 보내지 않아요 / 개인정보·비밀번호·회사 기밀이 든 글에는 쓰지 말 것. 버튼 [취소] [동의하고 추천받기].
  - 다시 동의: 저장된 버전 ≠ 현재 버전이면 409 → 화면이 같은 창을 띄운다. 001 로그인 재동의(`AgreementService.REQUIRED`)는 TERMS·PRIVACY만(Clarifications Q2).
  - 취소: 설정 화면 "AI 동의" 칸 [동의 취소] → `DELETE` → 다음 버튼에서 다시 동의.
  - 처리방침(FR-011): `PrivacyPage`에 "외부 AI 서비스(Google Gemini)로의 전송" 문단. 처리방침 버전을 올리는 시점은 팀 결정(T004) — 올리면 모든 회원이 로그인 때 재동의한다.
- **Rationale**: 34 §7-2, FR-007~FR-011.

## R11. 장애 격리와 로그 (확정)

- **Decision**:
  - 모든 Redis 호출은 002 `RedisGuard.call`로 감싸고 대체 경로는 `AiUnavailableException(STORE_UNAVAILABLE)`을 던진다. `RedisGuard`가 메모리 부족에서 던지는 `AutosaveUnavailableException`도 잡아 같은 503으로 바꾼다(plan 설계 후 확인 3).
  - 추천 요청은 글 저장 API와 따로 오고 DB에 쓰는 것은 동의 API뿐이라, 추천 실패가 자동 저장·발행을 막을 길이 없다. SC-002는 공급자·Redis를 모두 실패시킨 상태에서 002 자동 저장·저장·발행 API가 성공하는지로 확인한다.
  - 로그: 요청마다 `ai tag-suggest post={id} provider={…} cached={…} outcome={OK|EMPTY|FAILED|BUSY|LIMIT} inputChars={n} took={ms}` INFO. 제목·본문·태그 내용·키는 남기지 않는다. 예외 로그에도 응답 본문을 넣지 않는다(외부 오류 본문에 입력이 되돌아올 수 있음). 001 `SensitiveParamMasking`에 `x-goog-api-key`·`contentMd`·`title`을 더한다.
  - `GEMINI_API_KEY`는 환경 변수로만(`.env.example`에 빈 줄, 실제 값은 저장소에 넣지 않음).
- **Rationale**: 01 §3 원칙 5, 02 §2-1 H8, 34 완료 기준 6·7, 헌법 IV·V.

## R12. 자체 AI 배포 (Clarifications Q3)

- **Decision**: `docker-compose.yml`에 `ollama` 서비스:
  - 이미지 `ollama/ollama`(고정 태그 — 구현 때 그 시점 안정 버전을 적음), 볼륨 `ollama-data:/root/.ollama`, 호스트 포트를 열지 않는다(앱만 `http://ollama:11434`로 부름 — 개발 때만 `docker-compose.override.yml`로 연다).
  - 모델 미리 받기: 한 번만 도는 `ollama-pull` 서비스(같은 이미지, `ollama pull ${OLLAMA_MODEL:-qwen2.5:1.5b}`)를 두고 앱은 그것이 끝난 뒤 뜬다(`depends_on: condition: service_completed_successfully`).
  - 앱 환경 `BLOG_AI_TAG_SUGGEST_OLLAMA_BASE_URL=http://ollama:11434`. `num_thread`는 배포 서버 성능 코어 수 이하(기본 4, 기본값은 약 140배 느림 — 34 §8-1).
  - 메모리 2~4GB 추가, 배포 서버에서 2,000자 입력 시간 다시 재기(SC-006)는 배포 담당 확인(T005).
- **Rationale**: 34 §3·§8, Clarifications Q3.

## R13. 화면 (확정 + 제안)

- **Decision**:
  - 위치: 008 `PublishDialog`의 `TagInput` 바로 아래 `AiTagSuggest`. 상태 API `available = false`(꺼짐)면 영역 전체를 그리지 않는다.
  - 버튼: [AI 태그 추천](첫 요청), 결과가 있으면 [다시 추천](`refresh: true`). 요청 중에는 비활성 + "추천 중…"(예상 공급자가 OLLAMA면 "자체 AI로 추천 중이라 조금 걸려요").
  - 결과: 칩 `(+ jpa)` — 누르면 `TagInput`에 그 이름을 더하고(서버 저장 없음, 발행 때 확정 — FR-004) 칩이 사라진다. 아래 작은 글씨 "AI 제안이에요"(잘렸으면 "본문 앞부분을 보고 추천했어요 · AI 제안이에요"), "오늘 남은 추천 N회".
  - 문구: 422 "글을 조금 더 쓴 뒤 추천받아 보세요", `tags: []` "추천할 태그를 찾지 못했어요", 503 `FAILED`·`DISABLED`·`STORE_UNAVAILABLE` "지금은 추천할 수 없어요", 503 `BUSY` "잠시 후 다시 시도해 주세요", 429 "오늘 추천을 모두 썼어요. 내일 다시 써 보세요", 409 → 동의 창.
  - 동의 창: `role="dialog"`, 초점 가두기, Esc = [취소](아무것도 보내지 않음), [동의하고 추천받기] → `PUT` 성공 뒤 원래 추천 요청을 다시 보낸다.
  - 설정 "AI 동의" 칸(001 `SettingsPage`): `GET /api/me/agreements/ai` 결과로 그린다. `agreed = true`면 "AI 태그 추천에 동의했어요 (YYYY-MM-DD) [동의 취소]", `false`면 "동의하지 않았어요 [동의하기]". 동의 버전이 현재 버전보다 낮으면(`version < currentVersion`) "처리방침이 바뀌어 다시 동의가 필요해요"를 함께 보인다. 상태 API(`/api/posts/{postId}/tag-suggestions/status`)는 글 번호가 있어야 해서 설정 화면에서 쓸 수 없다.
  - (제안) `GET /api/me/agreements/ai` → `{agreed, version, currentVersion, agreedAt}`를 `PUT`/`DELETE`와 같은 경로에 둔다(R3 표 반영). `PUT`·`DELETE`도 같은 모양을 돌려준다.
- **Rationale**: 34 §9 화면, FR-002~FR-004·FR-010·FR-014·FR-020·FR-023.

## R14. 권한 매트릭스 행 (제안)

- **Decision**: 004 하네스 `TR/permission/post-write.csv`에 owner `013` 행 2개(`tag-suggest.post`, `tag-suggest.status`). AI는 테스트 설정의 가짜 공급자(`T/support/ai/FakeTagSuggester`)가 답한다.
  | 대상 글 | ANONYMOUS | UNVERIFIED | MEMBER(남) | AUTHOR | ADMIN(남의 글) | SUSPENDED | WITHDRAWN |
  |---|---|---|---|---|---|---|---|
  | 내 공개·비공개·임시 글 | 401 | 403 `EMAIL_NOT_VERIFIED` | 404 | 200(동의 픽스처) | 404 | 403 `ACCOUNT_SUSPENDED` | 403 `ACCOUNT_WITHDRAWN` |
  | 휴지통·없는 글 | 401 | 403 | 404 | 404 | 404 | 403 | 403 |
  - 인증 전 회원도 403이 404보다 먼저(42 §3).
- **Rationale**: 헌법 III, 42 §3·§4, FR-005.

## R15. 설정값 (확정 + 제안)

```yaml
blog:
  agreement:
    ai:
      version: ${BLOG_AGREEMENT_AI_VERSION:2026-10-08}
      effective-date: ${BLOG_AGREEMENT_AI_EFFECTIVE_DATE:2026-10-08}
  ai:
    tag-suggest:
      enabled: ${BLOG_AI_TAG_SUGGEST_ENABLED:true}
      prompt-version: 1                # 올리면 같은 내용 재사용 키가 바뀜
      min-input-chars: 100
      max-suggestions: 5
      daily-limit-per-member: 20
      cache:
        exact-ttl: 30d
        post-ttl: 7d
        similarity-threshold: 0.9
        post-input-chars: 8000
      popular:
        size: 50
        ttl: 1d
      gemini:
        base-url: https://generativelanguage.googleapis.com
        model: gemini-flash-lite        # 공급자 화면 값으로 조정 (34 §3-1)
        api-key: ${GEMINI_API_KEY:}     # 비어 있으면 Gemini를 쓰지 않음
        daily-limit: 450
        quota-zone: America/Los_Angeles
        timeout: 10s
        cooldown: 60s
        unknown-429-exhaust-count: 3
        max-input-chars: 8000
      ollama:
        enabled: true
        base-url: ${BLOG_AI_TAG_SUGGEST_OLLAMA_BASE_URL:http://localhost:11434}
        model: qwen2.5:1.5b
        num-thread: 4
        timeout: 30s
        max-input-chars: 2000
        max-concurrency: 1
```

- 설정 키 접두어 규칙(`blog.ai.tag-suggest.*` vs 기능 이름)은 Tier A ANALYSIS R10의 공통 미결이다.
