# Quickstart: 013-ai-tag-suggest 검증 시나리오

**Feature**: `013-ai-tag-suggest` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 입력 정리·프롬프트·라우팅·Redis 동작은 [contracts/providers.md](./contracts/providers.md), 키·값 객체·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS. 자체 AI를 실제로 돌리려면 Docker에 메모리 4GB 이상 여유
- 선행 기능: 001(`AccountStatusGuard`·`MemberAgreementRepository`·`AccountProperties`·설정 화면·`SensitiveParamMasking`), 002(commonmark·`RedisGuard`·`blog.post.*`·자동 저장/발행 API), 004(권한 하네스 `post-write.csv`), 008(`TagNormalizer`·`TagQueryService.top`·`PublishDialog`·`TagInput`)
- Gemini 키는 선택이다. 없으면(`GEMINI_API_KEY` 비어 있음) 공개 글도 자체 AI로 간다. 키는 `.env`에만 넣고 저장소에 올리지 않는다

## 1. 기동

```bash
cp .env.example .env                        # GEMINI_API_KEY= 는 비워 두거나 개인 키를 넣는다
docker compose up -d postgres redis minio mailpit ollama ollama-pull
docker compose logs -f ollama-pull          # "success"가 보이면 모델 받기 끝 (처음 한 번, 약 1GB)
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `docker compose port ollama 11434`가 비어 있다(호스트 포트 없음 — 개발 때만 override로 연다)
- 앱 기동 로그에 `GEMINI_API_KEY` 값이 없다

## 2. 자동 테스트

```bash
./mvnw -pl backend verify -Dit.test='TagSuggest*IT,AiConsentIT' -Dtest='SuggestInputCleanerTest,TrigramSimilarityTest,PromptBuilderTest,GeminiTagSuggesterTest,OllamaTagSuggesterTest'
./mvnw -pl backend verify -Dit.test=OllamaSmokeIT -Dgroups=ollama            # 실제 Ollama, 수동
(cd frontend && npx vitest run src/features/ai-suggest)
(cd frontend && npx playwright test e2e/ai-tag-suggest.spec.ts)
```

| 테스트 | 확인하는 것 |
|---|---|
| `SuggestInputCleanerTest` | providers §1 표 전부, NFC, 코드 포인트 길이, 대소문자 유지 |
| `TrigramSimilarityTest` | 같은 문자열 1.0, 오타 몇 개 ≥ 0.9, 문단 추가 < 0.9, 3자 미만 0 |
| `PromptBuilderTest` | 순서 고정(지시문 → 인기 태그 → 붙인 태그 → 본문), 회원 정보 없음, Ollama는 입력에 나오는 인기 태그만 |
| `GeminiTagSuggesterTest` | providers §3 표: `x-goog-api-key` 헤더, `responseSchema`, 429 `PerDay`/`PerMinute`/모름 구분, 형식 깨짐(6개, 문자열 아님), 10초 시간 초과 |
| `OllamaTagSuggesterTest` | providers §4: `format` 스키마, `num_thread`, `stream: false`, 30초 시간 초과 |
| `TagSuggestApiIT` | US1 #1~#5: 추천 최대 5개·남은 자리만큼(붙인 태그 8개 → 2개, 10개 → AI 안 부르고 `[]`), 이미 붙인 태그·금칙어·형식 위반 제거(SC-007), 422(정리 후 99자), 응답 뒤 `post_tag` 변화 없음(SC-003), 판정 순서(401 → 403 `EMAIL_NOT_VERIFIED` → 404 남의 글·휴지통 → 503 `DISABLED` → 409 → 400 → 422), 하루 20회 뒤 429 `AI_DAILY_LIMIT` `details.resetAt`·`Retry-After`, 실패 요청은 횟수 그대로(Q4) |
| `AiConsentIT` | US2 #1~#4: 동의 전 요청은 공급자 호출 0번(SC-001 — 가짜 공급자 호출 수), `PUT` 뒤 `member_agreement` AI 행(버전·시각), 버전을 올리면 409 다시, 다른 버전 `PUT` → 400 `AGREEMENT_VERSION_MISMATCH`, `DELETE` 뒤 409, 로그인 재동의 목록에 AI 없음(Q2), `GET` 세 모양, 인증 전 회원도 `PUT` 가능 |
| `TagSuggestCacheIT` | US3 #1~#4, SC-004: 같은 입력 두 번째 `cached: true`·호출 0·횟수 그대로, 다른 회원 같은 입력도 재사용, 태그 하나 붙인 뒤 → 재사용 + 그 태그 빠짐, 오타 몇 개 → 비슷한 내용 재사용, 문단 추가 → 새 호출, `refresh` → 비슷한 내용 건너뜀, 같은 내용이 Ollama 결과이고 Gemini 가능 → 새로 만듦, 빈 결과·실패는 저장 안 함, `prompt-version` 올리면 새 키 |
| `TagSuggestRoutingIT` | US4 #1~#5, SC-005: 450회 뒤 Ollama, 429 `PerDay` → 같은 요청 Ollama 성공(사용자 실패 없음), `PerMinute` → 60초 Ollama 뒤 Gemini, 모르는 429 세 번 → `exhausted`, 시간 초과 → 이번 503 `FAILED` + 60초 Ollama, 비공개 글은 Gemini 호출 0(FR-030), 비공개 글 + Ollama 꺼짐 → 503, Ollama 동시 2번째 → 503 `BUSY`, 키 없음 → Ollama, 공급자 날짜(태평양 시간) 경계 |
| `TagSuggestFailureIsolationIT` | SC-002: 두 공급자·Redis 모두 실패 상태에서 002 자동 저장·저장·발행 API 성공, 추천 503 `STORE_UNAVAILABLE`, 상태 API `available = false` |
| `TagSuggestLoggingIT` | SC-008: 제목·본문·태그·키 문자열이 로그(INFO~ERROR, 예외 포함)에 없다, 요청당 INFO 한 줄 |
| `TagSuggestPermissionMatrixIT` | `post-write.csv`의 owner 013 행(research R14 표) |
| `OllamaSmokeIT` | SC-006: 실제 `qwen2.5:1.5b`로 2,000자 입력 30초 안, 태그 1개 이상 |

## 3. 수동 확인 (브라우저)

1. 회원 A(인증)로 새 글에 Spring·JPA 이야기 300자 정도를 쓰고 [발행]을 연다. 태그 입력 아래 [AI 태그 추천]이 보인다
2. [AI 태그 추천] → 동의 창: 외부 전송·Google 데이터 사용·자체 AI 대체·비공개 글 제외·개인정보 주의 문구와 버전. Esc → 아무 요청도 안 감(개발자 도구 Network)
3. [동의하고 추천받기] → 칩 `(+ spring)` 등 5개 이하, "AI 제안이에요", "오늘 남은 추천 19회". 칩을 누르면 태그 입력에 들어가고 칩이 사라진다. 발행하지 않고 닫으면 저장된 태그는 그대로
4. 같은 내용으로 다시 [AI 태그 추천] → 즉시 답, 남은 횟수 그대로
5. 본문을 50자로 줄이고 누르기 → "글을 조금 더 쓴 뒤 추천받아 보세요"
6. 글 공개 범위를 비공개로 저장하고 누르기 → "자체 AI로 추천 중이라 조금 걸려요" 뒤 결과(`provider: OLLAMA`)
7. `docker compose stop ollama` 후 비공개 글로 누르기 → "지금은 추천할 수 없어요". 그 상태로 자동 저장·발행이 잘 된다
8. 설정 → "AI 동의" 칸: "AI 태그 추천에 동의했어요 (날짜) [동의 취소]" → 취소 → 다시 추천을 누르면 동의 창
9. `BLOG_AGREEMENT_AI_VERSION`을 바꿔 재기동 → 로그인 재동의 화면은 뜨지 않고, 추천을 누를 때만 동의 창이 다시 뜬다
10. `BLOG_AI_TAG_SUGGEST_ENABLED=false`로 재기동 → 버튼이 없다
11. 인증 전 회원 B → 버튼을 눌러도 "이메일 인증 후 이용할 수 있어요"
12. 앱 로그를 `grep`해 글 제목·본문 일부가 없다
13. 375px 폭에서 발행 창·칩·동의 창이 가로 스크롤 없이 보인다

## 4. 다른 기능 확인 (있을 때)

- 015: 회원이 탈퇴 익명 처리된 뒤에도 `member_agreement`의 AI 행이 남아 있다
- 개인정보 처리방침(`/privacy`)에 "외부 AI 서비스(Google Gemini)로의 전송" 문단 — 반영 시점은 팀 결정(T004)
