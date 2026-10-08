# 공급자 계약: 입력 정리, 프롬프트, 라우팅, Redis 동작

**Feature**: 013-ai-tag-suggest | 근거: research R5~R9·R11, docs/34-ai-tag-suggest.md §3~§6

이 문서는 HTTP API 밖의 계약(서버 안 동작과 외부 AI 요청·응답 모양)이다. 테스트는 `MockRestServiceServer`로 외부 응답을 흉내 내고 아래 모양을 검사한다.

## §1. 입력 정리 (`SuggestInputCleaner`)

| 입력 | 결과 |
|---|---|
| `# 제목\n**굵게** _기울임_ ~~취소~~` | `제목 굵게 기울임 취소` |
| `- 항목1\n- 항목2\n> 인용` | `항목1 항목2 인용` |
| `![그림 설명](https://…/a.png)` | (없음 — 대체 글자도 버림) |
| `[스프링 문서](https://spring.io)` | `스프링 문서` |
| ```` ```java\nl1\nl2\nl3\nl4\nl5\nl6\n``` ```` | `java l1 l2 l3 l4 l5` (6번째 줄부터 버림) |
| `` `@Transactional` `` | `@Transactional` |
| `<div>html</div>` | (없음) |
| `\| a \| b \|` 표 | `a b` |

- 결과 = `strip(NFC(collapseWhitespace(title + "\n" + body)))`. 대소문자 그대로.
- 길이 = 코드 포인트 수. `length < min-input-chars(100)`이면 422.
- 재사용 열쇠는 자르기 전 전체 문자열의 SHA-256. 공급자 최대 길이(Gemini 8,000 / Ollama 2,000)로 자르는 것은 호출 직전이고, 자르면 `truncated = true`.

## §2. 프롬프트 (`PromptBuilder`, `prompt-version = 1`)

순서는 고정(앞부분 재사용에 유리 — 34 §4):

```text
[system]
너는 기술 블로그 글에 붙일 태그를 고른다.
- 글에 실제로 나오는 기술·주제만 고른다. 글에 없는 기술은 넣지 않는다.
- 최대 5개. 짧은 소문자 표기를 쓴다. 아래 인기 태그에 같은 뜻이 있으면 그 표기를 쓴다.
- 이미 붙인 태그는 고르지 않는다.
- {"tags": [...]} JSON으로만 답한다.

[user]
인기 태그: spring, jpa, react, …           # Gemini 상위 50 / Ollama 상위 50 중 입력에 나오는 것
이미 붙인 태그: jpa                         # 없으면 "없음"
제목과 본문:
<정리·자른 입력>
```

- 회원 번호·닉네임·이메일·다른 글은 넣지 않는다(FR-012). 지시문을 바꾸면 `prompt-version`을 올린다(같은 내용 재사용 키가 바뀜).
- 인기 태그는 `ai:tag:popular:{오늘}`에서 읽고, 없으면 008 `TagQueryService.top()` 앞 50개 이름을 넣고 1일 저장.

## §3. Gemini 요청·응답

```http
POST {gemini.base-url}/v1beta/models/{gemini.model}:generateContent
x-goog-api-key: <GEMINI_API_KEY>
Content-Type: application/json

{
  "systemInstruction": {"parts": [{"text": "<system>"}]},
  "contents": [{"role": "user", "parts": [{"text": "<user>"}]}],
  "generationConfig": {
    "responseMimeType": "application/json",
    "responseSchema": {"type": "OBJECT", "properties": {"tags": {"type": "ARRAY", "items": {"type": "STRING"}, "maxItems": 5}}, "required": ["tags"]},
    "maxOutputTokens": 100,
    "temperature": 0.2
  }
}
```

| 응답 | 해석 |
|---|---|
| 200, `candidates[0].content.parts[0].text` = `{"tags":["spring","hibernate"]}` | `Success(["spring","hibernate"])` |
| 200, text가 JSON이 아님 / `tags`가 문자열 배열이 아님 / 6개 이상 / candidates 없음 | `Failed(MALFORMED)` |
| 429, `error.details[]`의 `QuotaFailure.violations[].quotaId`에 `PerDay` | `QuotaExceeded(PER_DAY)` |
| 429, `quotaId`에 `PerMinute` | `QuotaExceeded(PER_MINUTE)` |
| 429, 위 둘을 찾지 못함 | `QuotaExceeded(UNKNOWN)` |
| 5xx, 연결 실패, 10초 초과 | `Failed(SERVER_ERROR / CONNECT / TIMEOUT)` |
| 400·401·403(키 오류 등) | `Failed(SERVER_ERROR)` + ERROR 로그(본문 없이 상태 코드만) |

## §4. Ollama 요청·응답

```http
POST {ollama.base-url}/api/chat
Content-Type: application/json

{
  "model": "qwen2.5:1.5b",
  "stream": false,
  "format": {"type": "object", "properties": {"tags": {"type": "array", "items": {"type": "string"}, "maxItems": 5}}, "required": ["tags"]},
  "options": {"num_thread": 4, "num_predict": 100, "temperature": 0.2},
  "messages": [{"role": "system", "content": "<system>"}, {"role": "user", "content": "<user>"}]
}
```

| 응답 | 해석 |
|---|---|
| 200, `message.content` = `{"tags":[…]}` (5개 이하 문자열) | `Success` |
| 200, 형식 깨짐 | `Failed(MALFORMED)` |
| 연결 실패·5xx·30초 초과 | `Failed(…)` |

## §5. 라우팅과 상태 키 (`TagSuggesterRouter`, `ProviderState`)

```text
choose(post):
  if !enabled                         → DISABLED
  if post.visibility != PUBLIC        → OLLAMA if ollama.enabled else NONE      # 외부로 보내지 않음 (FR-030)
  if api-key 비어 있음                 → OLLAMA|NONE
  if EXISTS ai:gemini:exhausted        → OLLAMA|NONE
  if EXISTS ai:gemini:cooldown         → OLLAMA|NONE
  if GET ai:gemini:count:{qd} >= daily-limit → SET ai:gemini:exhausted EX ttl(다음 초기화) ; OLLAMA|NONE
  else                                 → GEMINI

callGemini:
  INCR ai:gemini:count:{qd} (처음이면 EXPIRE 2d)
  outcome = gemini.suggest(input@8000)
  Success              → DEL ai:gemini:unknown-429:{qd} ; return
  QuotaExceeded(PER_DAY)    → SET ai:gemini:exhausted EX ttl(다음 초기화) ; callOllama(같은 요청)
  QuotaExceeded(PER_MINUTE) → SET ai:gemini:cooldown EX 60 ; callOllama
  QuotaExceeded(UNKNOWN)    → SET cooldown EX 60 ; n = INCR unknown-429:{qd} ; n >= 3 → SET exhausted ; callOllama
  Failed(TIMEOUT|SERVER_ERROR|CONNECT) → SET cooldown EX 60 ; return FAILED (Ollama로 넘기지 않음, FR-019)
  Failed(MALFORMED)    → return FAILED

callOllama:
  if !ollama.enabled → FAILED
  n = INCR ai:ollama:inflight ; EXPIRE 60
  if n > max-concurrency → DECR ; return BUSY
  try outcome = ollama.suggest(input@2000) finally DECR
```

- `qd` = `quota-zone`(기본 America/Los_Angeles) 기준 날짜. `ttl(다음 초기화)` = 그 시간대 다음 0시까지 초(최소 1).
- `NONE` = 쓸 수 있는 공급자 없음 → 503 `FAILED`. 상태 API는 이때 `available = false`, `provider = null`.
- Ollama로 넘어간 요청의 `truncated`는 2,000자 기준으로 다시 정한다.

## §6. 재사용 저장소 (`SuggestCache`)

```text
lookup(postId, input, refresh):
  exact = GET ai:tag:v{pv}:{sha256(input)}
  if exact && !(refresh && exact.provider == OLLAMA && router.choose(post) == GEMINI) → exact
  if !refresh:
    p = GET ai:tag:post:{postId}
    if p && jaccard3(input[:8000], p.input) >= 0.9 → p
  → miss

store(postId, input, normalizedTags, provider):     # normalizedTags = 008 Accepted만, 중복 제거, 검사 전
  if normalizedTags is empty → skip (FR-029)
  SET ai:tag:v{pv}:{sha256} {tags, provider, createdAt} EX 30d
  SET ai:tag:post:{postId} {input[:8000], tags, provider, createdAt} EX 7d
```

- `jaccard3(a, b)` = |3-gram(a) ∩ 3-gram(b)| / |3-gram(a) ∪ 3-gram(b)|. 3-gram은 코드 포인트 단위, 공백 포함. 둘 중 하나라도 3자 미만이면 0.
- 저장 값에는 이미 붙인 태그를 빼기 전 목록을 둔다. 응답 때마다 §7 검사를 다시 한다(FR-026).

## §7. 결과 검사

```text
accepted = [n.name for raw in rawTags if (n = TagNormalizer.normalize(raw)) is Accepted]   # 금칙어·형식은 008이 거름
accepted = distinct(accepted)                    # 처음 것
current  = {TagNormalizer.normalize(t).name for t in currentTags if Accepted}
result   = [t for t in accepted if t not in current][: min(max-suggestions, max-tags − |currentTags|)]
```

## §8. 하루 횟수 (`DailyUsage`)

```text
reserve(m):  n = INCR ai:tag:usage:{m}:{kstDate} ; if n == 1: EXPIRE 2d
             if n > 20: DECR ; throw 429 AI_DAILY_LIMIT {resetAt: 다음 KST 0시}
release(m):  DECR (실패·혼잡·형식 깨짐 — AI가 답을 주지 않은 경우)
remaining:   max(0, 20 − GET)
```

AI가 답을 줬으면 검사에서 0개가 되어도 `release`하지 않는다(Clarifications Q4).

## §9. 로그

한 요청에 INFO 한 줄:

```text
ai tag-suggest post=123 provider=GEMINI cached=false outcome=OK inputChars=2140 took=812ms
```

- `outcome` ∈ `OK`, `EMPTY`, `FAILED`, `BUSY`, `LIMIT`, `DISABLED`, `CONSENT`, `TOO_SHORT`.
- 제목·본문·태그 이름·프롬프트·외부 응답 본문·`x-goog-api-key`는 어떤 레벨에도 남기지 않는다. `RestClient` 요청·응답 본문 로그는 끈다.
