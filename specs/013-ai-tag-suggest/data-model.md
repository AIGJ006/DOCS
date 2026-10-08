# Data Model: AI 태그 추천

**Feature**: 013-ai-tag-suggest | **Date**: 2026-10-08 | 스키마 변경 없음(V1), 새 테이블·컬럼 없음

## 1. 쓰는·읽는 테이블 (V1)

| 테이블 | 컬럼 | 쓰는 곳 | 비고 |
|---|---|---|---|
| `member_agreement` | `member_id, type, version, agreed_at` (PK `(member_id, type)`) | `AiConsentService` — type `AI` 행만 읽고 쓴다 | `ck_member_agreement_type`에 `AI`가 이미 있다. 동의 = 001 `MemberAgreementRepository.upsert`, 철회 = 행 삭제(51 §4) |
| `post` | `id, author_id, visibility, deleted_at` | `PostOwnershipQuery.findOwned`(post 모듈 공개 조회) | 조건 `id = :postId AND author_id = :me AND deleted_at IS NULL` — 002 `PostEditRepository.isOwned`와 같다. `status`·`hidden_at`은 보지 않는다(임시 글·관리자 숨김 글도 작성자는 편집 중일 수 있음) |
| `tag`·`post_tag` | — | 008 `TagQueryService.top()`으로만(인기 태그) | tag 모듈 안이라 같은 모듈 호출 |

AI 동의 행은 탈퇴 익명 처리 때 지우지 않는다(015 FR-028). 이 기능은 015 `WithdrawalPurgeStep`을 구현하지 않는다.

```sql
-- 동의 (001 upsert 재사용)
INSERT INTO member_agreement (member_id, type, version, agreed_at)
VALUES (:memberId, 'AI', :version, :now)
ON CONFLICT (member_id, type) DO UPDATE SET version = EXCLUDED.version, agreed_at = EXCLUDED.agreed_at;

-- 철회 (없어도 성공)
DELETE FROM member_agreement WHERE member_id = :memberId AND type = 'AI';

-- 글 확인 (PostOwnershipQuery)
SELECT id, visibility FROM post
 WHERE id = :postId AND author_id = :memberId AND deleted_at IS NULL;
```

`MemberAgreementRepository`에 `deleteByMemberIdAndType(memberId, type)`·`findByMemberIdAndType(memberId, type)`이 없으면 더한다(001 소유 파일, 추가만).

## 2. Redis 키

| 키 | 타입 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|---|
| `ai:tag:v{promptVersion}:{sha256}` | String(JSON) | `{tags, provider, createdAt}` | 30일 | 같은 내용 재사용(모든 사용자 공유) |
| `ai:tag:post:{postId}` | String(JSON) | `{input(앞 8,000 코드 포인트), tags, provider, createdAt}` | 7일 | 같은 글 비슷한 내용 재사용(글당 하나, 덮어씀) |
| `ai:tag:usage:{memberId}:{yyyyMMdd}` | String(정수) | 오늘 AI 호출 수(한국 시간 날짜) | 2일 | 하루 20회 |
| `ai:tag:popular:{yyyyMMdd}` | String(JSON) | 인기 태그 이름 배열(최대 50) | 1일 | 프롬프트 ② |
| `ai:gemini:count:{yyyyMMdd}` | String(정수) | 오늘 Gemini 호출 수(공급자 날짜 `quota-zone`) | 2일 | 우리가 센 외부 호출 수 |
| `ai:gemini:exhausted` | String | `"1"` | 다음 공급자 초기화 시각까지 | 하루 한도 소진 |
| `ai:gemini:cooldown` | String | `"1"` | 60초 | 분당 한도·시간 초과·5xx·모르는 429 |
| `ai:gemini:unknown-429:{yyyyMMdd}` | String(정수) | 종류 모르는 429 횟수 | 2일 | 3번이면 `exhausted` |
| `ai:ollama:inflight` | String(정수) | 지금 처리 중인 자체 AI 요청 수 | 60초(매 `INCR` 때 다시 설정, 안전장치) | 동시 처리 1 |

- 모든 키는 `ai:` 접두어. 008 자동완성 요청 제한 `ratelimit:tag-suggest:{memberId}`와 겹치지 않는다(plan 설계 후 확인 6).
- `sha256` = 정리된 입력 전체(자르기 전, NFC)의 SHA-256 16진수 64자. `promptVersion` = `blog.ai.tag-suggest.prompt-version`.
- 모든 호출은 002 `RedisGuard.call`로 감싼다. 실패하면 503 `AI_UNAVAILABLE`(`STORE_UNAVAILABLE`). 메모리 부족에서 오는 `AutosaveUnavailableException`도 같이 바꾼다.

## 3. 값 객체 (tag.application.suggest)

```java
public enum Provider { GEMINI, OLLAMA }

/** 요청 본문 (R3). contentMd는 에디터의 지금 값, 저장 안 된 것 포함. */
public record TagSuggestRequest(String title, String contentMd, List<String> currentTags, boolean refresh) {}

/** post 공개 조회 결과 (post.application). */
public record OwnedPost(long id, Visibility visibility) {}

/** 정리된 입력 (R5). length는 코드 포인트 수. */
public record CleanedInput(String text, int length) {
    public String sha256() { … }
    public CleanedInput truncate(int maxChars) { … }   // 코드 포인트 단위로 자름
}

/** 공급자에 넘기는 값. 회원 정보·다른 글은 없다 (FR-012). */
public record TagSuggestInput(String text, boolean truncated, List<String> popularTags, List<String> currentTags) {}

/** 공급자 결과. */
public sealed interface SuggestOutcome {
    record Success(List<String> rawTags) implements SuggestOutcome {}
    record QuotaExceeded(QuotaKind kind) implements SuggestOutcome {}     // Gemini 429
    record Failed(FailureKind kind) implements SuggestOutcome {}          // TIMEOUT, SERVER_ERROR, MALFORMED, CONNECT
    record Busy() implements SuggestOutcome {}                             // Ollama 동시 처리 초과
}
public enum QuotaKind { PER_DAY, PER_MINUTE, UNKNOWN }

/** 재사용 저장소 값. */
public record CachedSuggestion(List<String> tags, Provider provider, Instant createdAt) {}
public record PostCachedSuggestion(String input, List<String> tags, Provider provider, Instant createdAt) {}

public interface TagSuggester {
    Provider provider();
    SuggestOutcome suggest(TagSuggestInput input);
}
```

## 4. 응답 모델

```java
/** POST /api/posts/{postId}/tag-suggestions 200 */
public record TagSuggestResponse(
        List<String> tags,            // 0~5개, 정규화·검사 뒤
        Provider provider,            // 이 결과를 만든 공급자 (재사용이면 저장된 값)
        boolean cached,
        boolean truncated,            // 이번 정리된 입력이 그 공급자 최대 길이를 넘었는가
        int remainingToday) {}

/** GET /api/posts/{postId}/tag-suggestions/status 200 */
public record TagSuggestStatus(
        boolean available,            // 기능 켜짐 ∧ (이 글로 쓸 수 있는 공급자가 하나 이상)
        boolean consentRequired,      // 동의 없음 ∨ 버전 다름
        String consentVersion,        // 현재 AI 동의 버전
        Provider provider,            // 지금 고를 공급자 예측, available = false면 null
        int remainingToday) {}

/** GET·PUT·DELETE /api/me/agreements/ai 200 (account.web) */
public record AiConsentView(
        boolean agreed,               // 행이 있고 version = currentVersion
        String version,               // 저장된 버전, 없으면 null
        String currentVersion,
        Instant agreedAt) {}          // 없으면 null
```

상태 API에는 남은 태그 자리를 두지 않는다. 붙인 태그는 화면만 안다(저장 안 된 값). 화면은 `TagInput`의 지금 개수와 `blog.post.max-tags`(008 화면 상수 10)로 버튼 비활성을 정한다.

## 5. 이유 코드 (`TagSuggestReasonCode`, 제안)

| 코드 | 상태 | 메시지 | `details` | 언제 |
|---|---|---|---|---|
| `AI_CONSENT_REQUIRED` | 409 | AI 태그 추천을 쓰려면 동의가 필요해요 | `{version}` 현재 버전 | 동의 행 없음·버전 다름 |
| `CONTENT_TOO_SHORT` | 422 | 글을 조금 더 쓴 뒤 추천받아 보세요 | `{minChars, length}` | 정리 후 100 코드 포인트 미만 |
| `AI_DAILY_LIMIT` | 429 | 오늘 추천을 모두 썼어요. 내일 다시 써 보세요 | `{resetAt}` 다음 KST 0시, `Retry-After` = 그때까지 초 | 오늘 20회를 다 씀 |
| `AI_UNAVAILABLE` | 503 | 지금은 추천할 수 없어요 / 잠시 후 다시 시도해 주세요(`BUSY`) | `{reason}` = `DISABLED`·`FAILED`·`BUSY`·`STORE_UNAVAILABLE` | 기능 꺼짐·공급자 실패·자체 AI 혼잡·Redis 장애 |

공통(재사용): 401 `LOGIN_REQUIRED`, 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`·`CSRF_REJECTED`, 404 `NOT_FOUND`(고정 본문), 400 `VALIDATION_FAILED`(칸 오류 — 동의 버전 불일치는 001 `AGREEMENT_VERSION_MISMATCH`를 `errors[].code`로). 이 기능에는 1분·1시간 빈도 제한이 없어 `TOO_MANY_REQUESTS`를 쓰지 않는다.

하루 한도는 spec Implementation Notes대로 429 `AI_DAILY_LIMIT`이다. 003 `DAILY_UPLOAD_LIMIT`(민서 확정 2026-10-08)과 같은 규칙 — 빈도 제한은 `TOO_MANY_REQUESTS`, 하루 한도처럼 뜻이 다른 것은 별도 코드(ANALYSIS-tier-bc에서 맞춤).

## 6. 설정값

| 접두어 | 클래스 | 키 |
|---|---|---|
| `blog.ai.tag-suggest` | `TagSuggestProperties` | `enabled`(true), `prompt-version`(1), `min-input-chars`(100), `max-suggestions`(5), `daily-limit-per-member`(20), `cache.exact-ttl`(30d)·`post-ttl`(7d)·`similarity-threshold`(0.9)·`post-input-chars`(8000), `popular.size`(50)·`ttl`(1d) |
| `blog.ai.tag-suggest.gemini` | 같은 클래스 안 `Gemini` | `base-url`, `model`, `api-key`(`${GEMINI_API_KEY:}`), `daily-limit`(450), `quota-zone`(America/Los_Angeles), `timeout`(10s), `cooldown`(60s), `unknown-429-exhaust-count`(3), `max-input-chars`(8000) |
| `blog.ai.tag-suggest.ollama` | 같은 클래스 안 `Ollama` | `enabled`(true), `base-url`, `model`(qwen2.5:1.5b), `num-thread`(4), `timeout`(30s), `max-input-chars`(2000), `max-concurrency`(1) |
| `blog.agreement.ai` | 001 `AccountProperties.Agreement`에 `ai` 추가 | `version`, `effective-date` |

글 태그 상한은 002 `blog.post.max-tags`(10), 제목·본문 길이는 002 `blog.post.*`를 함께 쓴다.

## 7. 상태 흐름 (한 요청)

```text
401 → 403 → 404 → 503 DISABLED → 409 동의 → 400 형식 → 정리 → 422 짧음
  → 남은 자리 0이면 200 tags: [] (AI 안 부름, 횟수 그대로)
  → 재사용(같은 내용 → [refresh 아니면] 비슷한 내용) 맞음 → 검사 → 200 cached: true
  → usage INCR (> 20이면 DECR, 429)
  → 라우터 → Gemini | Ollama
       Gemini 429 PER_DAY/PER_MINUTE/UNKNOWN → 상태 키 → 같은 요청 Ollama
       Gemini 시간 초과·5xx → cooldown → usage DECR → 503 FAILED
       Ollama 혼잡 → usage DECR → 503 BUSY
       형식 깨짐 → usage DECR → 503 FAILED
  → 성공: 정규화 → (1개 이상이면) 재사용 저장 두 곳 → 검사 → 200 cached: false
```
