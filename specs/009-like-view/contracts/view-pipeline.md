# Contract: 조회 집계 파이프라인·배치·다른 기능용 Service

**Feature**: 009-like-view | 원문: [docs/31-view-count.md](../../../docs/31-view-count.md) §2~§6·§9, [docs/30-like.md](../../../docs/30-like.md) §4-1·§6·§7, [docs/20-domain-events.md](../../../docs/20-domain-events.md) §3-3

## 1. 조회 기록 한 번의 처리 (`ViewRecordService.record`)

```text
POST /api/posts/{postId}/views
 ① 글 확인  PostReadService.requireReadable + PUBLISHED ──아니면──▶ 404
 ② 제외     작성자 본인 | 관리자 | UA 봇 단어 | Sec-Purpose/Purpose: prefetch ──▶ 204 (기록 없음)
 ③ 방문자 키 m:{id} | v:{vid} | h:{SHA-256(IP\nUA\nsalt(오늘))}   (salt 없음·Redis 장애 ──▶ 204)
 ④ 요청 제한 ratelimit:view:{visitorKey} 60/분 ──넘으면──▶ 429
 ⑤ Lua      view:seen:{postId}:{key} INCR(+첫 회 EXPIRE window) ≤ max ? HINCRBY view:pending:{yyyyMMdd} postId 1
            (Redis 장애·OOM ──▶ 건너뜀)
 ⑥ 204
```

- ②의 봇 단어(대소문자 무시, 부분 일치) 기본 목록 `policy/view-bot-user-agents.txt`: `bot`, `crawler`, `spider`, `preview`, `facebookexternalhit`, `slackbot`, `kakaotalk-scrap`, `discordbot`, `headlesschrome`
- 조회수 로그(DEBUG)는 `postId`와 결과 코드(`COUNTED`·`DUPLICATE`·`EXCLUDED_AUTHOR`·`EXCLUDED_ADMIN`·`EXCLUDED_BOT`·`EXCLUDED_PREFETCH`·`SKIPPED_REDIS`)만 남긴다. IP·UA·`vid`·방문자 키는 남기지 않는다

## 2. `vid` 쿠키 발급 (`VisitorIdCookieFilter`)

- 대상 요청: `GET /api/posts/{postId}`(005 상세 API), `GET /@{handle}/posts/{postId}`(005 페이지 셸)
- 조건: 로그인하지 않았고 요청에 형식이 맞는 `vid`가 없음
- 동작: 응답에 `Set-Cookie: vid={UUID v4}; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax[; Secure]` 추가. 응답 상태와 상관없이(404여도) 붙인다 — 붙이는 조건으로 글 존재가 드러나지 않게
- `Secure`는 001 세션 쿠키 설정과 같은 값을 따른다

## 3. 1분 반영 (`ViewFlushJob`, ShedLock `view-flush`)

```text
for key in SCAN view:processing:*            # 이전 실행이 남긴 묶음 먼저
    drain(key)
for key in SCAN view:pending:*
    RENAME key → view:processing:{날짜}:{uuid}
    drain(새 키)

drain(key):
  for (postId, n) in HSCAN key:
     tx { ok = PostCounterService.addViews(postId, n)            # updated_at 그대로
          if ok: upsert post_view_daily(postId, 날짜, n) }      # ON CONFLICT DO UPDATE views = views + n
     after commit: HDEL key postId                              # ok=false(완전 삭제된 글)도 HDEL
```

- 허용되는 오차: 커밋 직후·`HDEL` 직전 중단 → 그 글 1분치 한 번 더(FR-028)
- 확인: `ViewFlushJobIT#중간에_멈춰도_다음_실행이_남은_것만_반영한다`(처리 중 묶음을 손으로 만들어 두고 실행), `#완전_삭제된_글은_건너뛴다`, `#updated_at은_바뀌지_않는다`, `#자정_넘긴_조회는_그_날짜에`

## 4. 매일 배치

| 배치 | 기본 시각(KST) | ShedLock | 동작 | 결과 기록 |
|---|---|---|---|---|
| `LikeReconcileJob` | 04:10 | `like-reconcile` | `like_count <> 실제 건수`인 글만 고침 | 고친 수가 0이 아니면 WARN + 글 번호 최대 20개 |
| `ViewDailyRetentionJob` | 04:20 | `view-daily-retention` | `view_date < 오늘 − 90일` 삭제 | INFO 삭제 행 수 |

## 5. 이벤트

| 이벤트 | 필드 | 구독 |
|---|---|---|
| `PostLiked` | `postId, postAuthorId, memberId, likedAt` | 011(같은 사람·같은 글 1번), 012 |
| `PostUnliked` | `postId, postAuthorId, memberId, unlikedAt` | 012 |

규칙은 `DomainEvent`(AFTER_COMMIT, 비동기, 유실 허용).

## 6. 다른 기능이 부르는 Service

```java
// 015 LikeWithdrawalPurgeStep(order 30)이 부름 — 단계 클래스는 015 tasks가 만든다
/** 그 회원의 좋아요를 모두 지우고 글별 like_count를 줄인다. 이벤트 없음. MANDATORY 트랜잭션. */
int LikePurgeService.purgeByMember(long memberId);

// 005 PostLikeStatusQuery 구현 (LikeStatusQueryAdapter)
boolean isLikedBy(long postId, long memberId);   // SELECT EXISTS (PK)
```

## 7. post 모듈에 더하는 API (`PostCounterService`, 007과 같은 클래스)

| 서명 | 트랜잭션 | 쓰는 곳 |
|---|---|---|
| `adjustLikeCount(long postId, int delta)` | MANDATORY | 좋아요·취소 |
| `int likeCount(long postId)` | 읽기 | 좋아요 응답 |
| `boolean addViews(long postId, long n)` | MANDATORY | 1분 반영 (0행이면 false) |
| `adjustLikeCounts(Map<Long, Integer>)` | MANDATORY | 탈퇴 정리 |
| `List<Long> reconcileLikeCounts()` | REQUIRED | 보정 배치 |
