# Research: 좋아요와 조회수

**Feature**: 009-like-view | **Date**: 2026-10-08

표기: (확정) = spec·Clarifications·Tier A 결정에 이미 있음, (제안) = 이 plan이 정한 기본안(팀 확인 필요), (미결) = 팀 결정 대기 — 기본안으로 진행.

---

## R1. 좋아요 처리 SQL과 카운터 (확정 + 제안)

- **Decision**: 한 트랜잭션(`LikeService.like`):
  1. `INSERT INTO post_like (post_id, member_id) VALUES (:p, :m) ON CONFLICT DO NOTHING` → 영향 행 수 `changed`
  2. `changed = 1`이면 `PostCounterService.adjustLikeCount(p, +1)`
  3. `PostCounterService.likeCount(p)` → 응답 `likeCount`
  4. `changed = 1`이면 `PostLiked` 발행(커밋 후 전달)
  - 취소는 1번이 `DELETE FROM post_like WHERE post_id = :p AND member_id = :m`.
  - 원문 30 §4의 CTE 한 문장(`WITH ins AS (…) UPDATE post …`)은 interaction이 post 테이블을 직접 고치게 되므로 쓰지 않는다. 같은 트랜잭션의 두 문장이라 원자성은 같다.
- **Rationale**: 동시에 같은 (글, 회원) INSERT가 오면 두 번째는 PK 충돌 대기 후 "아무것도 안 함"(0행)이 되어 카운터가 한 번만 오른다. 카운터 `UPDATE`는 행 잠금으로 직렬화된다. 좋아요·취소 섞기에서도 각 트랜잭션이 실제 변화(1행)일 때만 ±1이라 수 = 건수(SC-002, 30 §4-2 검증과 같은 성질).
- **Alternatives considered**: `SELECT … FOR UPDATE` 후 분기 — 같은 결과에 SQL 1번 더. 원문 CTE — 위 이유로 제외.

## R2. `PostCounterService` 확장 (제안)

- **Decision**: 007 R5의 post 공개 Service에 더한다(먼저 구현하는 쪽이 클래스를 만듦).
  - `adjustLikeCount(long postId, int delta)` — `MANDATORY`
  - `int likeCount(long postId)` — 읽기
  - `addViews(long postId, long n)` — `MANDATORY`, `UPDATE post SET view_count = view_count + :n WHERE id = :p`(`updated_at` 건드리지 않음, FR-030). 영향 행 0이면 `false`(완전 삭제된 글)
  - `adjustLikeCounts(Map<Long, Integer>)` — 탈퇴 정리용
  - `reconcileLikeCounts()` — 보정 배치용(R9 SQL, `RETURNING id, 고치기 전·후`)
- **Rationale**: 원칙 II. post 엔티티의 `like_count`·`view_count`는 이미 `insertable = false, updatable = false`라 JPA가 덮어쓰지 않는다(`Post.java`).

## R3. 판정 순서와 응답 (확정)

- **Decision**: 401 `LOGIN_REQUIRED` → 403 `AccountStatusGuard.requireActive(me, CONTENT_WRITE)`(탈퇴 유예 → 정지 → 인증 전) → 404 `PostReadService.requireReadable(p, viewer)` + `status = PUBLISHED` → 400 `CANNOT_LIKE_OWN_POST`(`post.authorId = me`) → 429 `RateLimiter.acquireOrThrow("ratelimit:like:" + me, 60, 1m)`(좋아요·취소 같은 키). 성공 200 `{liked, likeCount}`.
  - 관리자는 일반 회원과 같다(42 §7). 관리자 숨김 글은 `PostReadService`가 관리자에게도 404인지 004 규칙을 따른다(42 §7 "볼 수 있는 글만").
  - `CANNOT_LIKE_OWN_POST` 문구: "내 글에는 좋아요를 누를 수 없어요".
- **Rationale**: README "정해진 것" 2026-10-07, 004 R-06, Clarifications Q4.

## R4. 조회 기록 흐름 (확정 + 제안)

- **Decision**: `POST /api/posts/{postId}/views`(CSRF 헤더 필요, 비회원 허용) → `ViewRecordService.record`:
  1. 글 확인: `PostReadService.requireReadable` + `PUBLISHED` → 아니면 404
  2. 제외(기록 없이 204): 보는 사람이 작성자 / 관리자(Clarifications Q1) / `User-Agent`에 봇 단어(대소문자 무시) / `Sec-Purpose`·`Purpose` 헤더에 `prefetch`
  3. 방문자 키 결정(R5)
  4. 요청 제한 `ratelimit:view:{visitorKey}` 60/분 → 넘으면 429(제외 판정 뒤, 기록 앞)
  5. Lua(R6) — Redis 장애면 건너뜀
  6. 204(셌든 안 셌든 같은 응답, 본문 없음)
  - 상세 화면은 작성자에게 요청 자체를 보내지 않지만(005 `useViewBeacon`) 서버도 2에서 거른다.
  - 판정 순서에서 404가 제외보다 먼저다 — 볼 수 없는 글은 봇·관리자여도 404(존재를 드러내지 않음, 같은 응답 규칙).
- **Rationale**: 31 §3·§4, FR-024~026·031.

## R5. 방문자 키와 `vid` 쿠키 (확정 + 제안)

- **Decision**:
  - 회원: `m:{memberId}`
  - 비회원 + 유효한 `vid` 쿠키(UUID 형식): `v:{vid}`
  - 비회원 + 쿠키 없음/형식 틀림: `h:{base64url(SHA-256(ClientIp.of(req) + "\n" + UA + "\n" + 오늘의 비밀값))}`
  - 오늘의 비밀값: Redis `view:salt:{yyyyMMdd(KST)}`를 `SET NX`(32바이트 난수, TTL 26시간)로 만들고 읽는다. 앱 메모리에 날짜별로 캐시(하루가 바뀌면 버림). Redis 장애면 키를 만들 수 없으므로 기록을 건너뛴다.
  - **`vid` 발급 시점(제안)**: `VisitorIdCookieFilter`가 `GET /api/posts/{postId}`(상세 API)와 `GET /@{handle}/posts/{postId}`(페이지 셸) 응답에, 비회원이고 `vid`가 없으면 `Set-Cookie: vid=<UUID>; Max-Age=31536000; Path=/; HttpOnly; Secure; SameSite=Lax`를 붙인다. 조회 기록 요청 자체는 쿠키를 만들지 않는다.
  - 로컬 개발(HTTP)에서는 `Secure`를 뺀다 — 001 세션 쿠키와 같은 설정값(`server.servlet.session.cookie.secure`)을 따른다.
- **Rationale**: 조회 기록 요청에서 처음 쿠키를 주면 첫 조회는 `h:`, 새로고침은 `v:`로 세어 기본 설정(24시간 1회)에서도 2가 된다. 상세를 먼저 열고 1초 뒤 기록 요청이 가므로 그때는 쿠키가 있다. 쿠키를 막은 브라우저는 계속 `h:`라 하루 안에서는 같은 키다.
- **Alternatives considered**: 첫 응답부터 모든 경로에 쿠키 — 쿠키 범위가 넓어져 처리방침 설명이 커진다.

## R6. Redis Lua (확정)

- **Decision**: `redis/view-record.lua`, KEYS = `[view:seen:{postId}:{visitorKey}, view:pending:{yyyyMMdd}]`, ARGV = `[windowSeconds, maxPerWindow, postId]`:
  ```lua
  local n = redis.call('INCR', KEYS[1])
  if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
  if n <= tonumber(ARGV[2]) then
    redis.call('HINCRBY', KEYS[2], ARGV[3], 1)
    redis.call('EXPIRE', KEYS[2], 172800)
    return 1
  end
  return 0
  ```
  - 기간은 첫 조회부터 시작(FR-022). 동시 50번도 `INCR` 원자성으로 `max`번만 모은다(SC-006).
  - `RedisGuard.callWrite`(트랜잭션 밖) 안에서 실행, 장애·회로 열림이면 건너뜀. OOM이면 지금 `RedisGuard`가 503 `AUTOSAVE_UNAVAILABLE`을 던지므로 `ViewRecordService`가 그 예외도 잡아 204로 바꾼다(조회 기록은 어떤 경우에도 상세를 막지 않음 — R13).
  - 날짜는 서비스 시간대(`blog.time-zone`, Asia/Seoul) 기준 기록 시각의 날짜(US4 #2).
- **Rationale**: 31 §5-1, 검증 31 §9.

## R7. 조회수 개인정보 (확정)

- **Decision**: Redis 키에는 `h:` 해시만, DB에는 글·날짜·합계만, 조회수 로그에는 글 번호와 결과(셈/중복/제외 이유 코드)만 남긴다. IP·UA·`vid`·방문자 키를 로그 메시지·MDC에 넣지 않는다. `ViewPrivacyIT`가 테스트 요청 IP(예: `203.0.113.77`)와 `vid` 값으로 Redis 전체 키·값, `post_view_daily`, 캡처한 로그를 검색해 0건을 확인한다(SC-010). 서버 접속 로그·001 로그인 제한 키(`rl:login:ip:*`)는 범위 밖(Clarifications Q2).
- **Rationale**: FR-034. 하루 비밀값이 사라지면 해시로 IP를 되돌릴 수 없다.

## R8. 1분 반영 배치 (확정)

- **Decision**: `ViewFlushJob`(`@Scheduled(fixedDelayString = "${blog.view.flush-interval}")` + ShedLock `view-flush`, lockAtMostFor 5분):
  1. `SCAN view:processing:*` — 남은 처리 중 묶음 먼저(이전 실행이 중간에 멈춘 것)
  2. `SCAN view:pending:*` — 각 키를 `RENAME view:pending:{d} → view:processing:{d}:{uuid}`(이름 바꾸는 순간부터 새 조회는 새 `pending` 키로)
  3. 각 처리 중 키: `HSCAN`으로 (postId, n)을 읽고, 글마다 트랜잭션 { `PostCounterService.addViews(p, n)` → 0행이면 건너뜀(완전 삭제) / 아니면 `INSERT INTO post_view_daily … ON CONFLICT (post_id, view_date) DO UPDATE SET views = post_view_daily.views + EXCLUDED.views` } → 커밋 뒤 `HDEL key postId`
  4. 키가 비면 Redis가 지운다
  - 커밋 직후·`HDEL` 직전에 멈추면 그 글 1분치가 한 번 더 더해진다(FR-028 허용).
  - 실행 중 Redis 장애면 그 실행을 끝내고 다음 실행을 기다린다(처리 중 키는 남아 있음).
- **Rationale**: 31 §5-2, SC-009.
- **Alternatives considered**: `GETDEL` 한 번 — 커밋 전 멈추면 유실. 글 묶음 한 트랜잭션 — 한 글 FK 오류가 전체를 되돌림.

## R9. 좋아요 보정·일별 보관 배치 (확정)

- **Decision**:
  - `LikeReconcileJob`(매일 04:10 KST, ShedLock `like-reconcile`):
    ```sql
    UPDATE post p SET like_count = c.n
      FROM (SELECT p2.id, count(l.post_id) AS n FROM post p2 LEFT JOIN post_like l ON l.post_id = p2.id GROUP BY p2.id) c
     WHERE p.id = c.id AND p.like_count <> c.n
    RETURNING p.id, c.n
    ```
    → `PostCounterService.reconcileLikeCounts()`가 실행하고 고친 건수를 돌려준다. 0이 아니면 WARN(글 번호 최대 20개). 대상은 처음엔 전체 글(30 §4-1).
  - `ViewDailyRetentionJob`(매일 04:20 KST, ShedLock `view-daily-retention`): `DELETE FROM post_view_daily WHERE view_date < :today - :retentionDays` (기본 90일, `ix_post_view_daily_date`). 누적 `view_count`는 그대로.
- **Rationale**: FR-005·FR-032. 시각은 003 정리(03:30)·006 휴지통 비우기와 겹치지 않게 둔다(설정값).

## R10. 화면 (확정 + 제안)

- **Decision**:
  - `LikeButton`(005 `ReactionBar`의 `likeButton` 자리): 작성자 본인은 버튼 없이 ♥ + 수(지금 005 표시 그대로). 비회원·인증 전 회원도 버튼을 보인다(H6).
    - 비회원이 누르면 "로그인하고 좋아요를 눌러 보세요 [로그인]"(`/login?returnTo={지금 주소}`), 돌아와서 자동으로 누르지 않는다(FR-014).
    - 인증 전 회원이 누르면 "이메일 인증 후 누를 수 있어요"(+ 001 인증 메일 다시 보내기 링크).
    - `aria-pressed`, `aria-label="좋아요 (N)"`/`"좋아요 취소 (N)"`, ♡/♥ 모양, Enter·Space.
  - `useLikeToggle(initialLiked, initialCount)`: 누르면 즉시 화면 상태를 뒤집고 0.3초 타이머를 다시 건다. 타이머가 끝나면 "마지막 원하는 상태"가 서버 확인 상태와 다를 때만 `PUT`/`DELETE`를 한 번 보낸다. 응답의 `liked`·`likeCount`로 맞추고, 실패하면 서버 확인 상태로 되돌리고 "좋아요를 반영하지 못했어요"(`role="status"`). 401·403 응답은 위 안내로.
  - 수 형식 `formatCount`(`1,234` / `1.2만` 내림)는 005 `ReactionBar`의 조회수 식을 꺼내 좋아요·조회수가 같이 쓴다.
  - 목록 카드에는 내가 눌렀는지를 보이지 않는다(005 그대로).
- **Rationale**: 30 §5, K-7.

## R11. 이벤트와 탈퇴 정리 (확정)

- **Decision**:
  - `PostLiked(postId, postAuthorId, memberId, likedAt)`, `PostUnliked(postId, postAuthorId, memberId, unlikedAt)`(20 §3-3 필드명).
  - `LikePurgeService.purgeByMember(memberId)`(`MANDATORY`): `DELETE FROM post_like WHERE member_id = :m RETURNING post_id` → 글별 개수 → `PostCounterService.adjustLikeCounts(−n)`. 이벤트 없음. 015의 `LikeWithdrawalPurgeStep`(order 30)이 부르고 단계 클래스는 015 tasks가 만든다(003·007과 같은 나눔).
  - 탈퇴 **유예** 중에는 아무것도 바꾸지 않는다(FR-021).
- **Rationale**: 44 §4, 13 §3-3.

## R12. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/like-view.csv`(owner `009`):
  - `post.like`(쓰기): ANONYMOUS 401, UNVERIFIED 403 `EMAIL_NOT_VERIFIED`, SUSPENDED 403, WITHDRAWN 403, AUTHOR 400 `CANNOT_LIKE_OWN_POST`(볼 수 있는 글) / 404(볼 수 없는 글 — 404가 먼저), MEMBER·ADMIN은 `PUBLISHED_PUBLIC`·`EDITING` 200, 그 밖 404
  - `post.unlike`: 같은 표
  - `post.view`(쓰기 아님): 모든 행위자 `PUBLISHED_PUBLIC` 204, 볼 수 없는 상태 404(작성자 본인의 비공개 글은 204 — 볼 수 있지만 세지 않음)
  - 거부된 좋아요는 `PostSnapshot`의 `like_count`와 `post_like` 행 수가 전후 같음(SC-004)
- **Rationale**: ANALYSIS-tier-a R6(004 FR-037 위임).

## R13. Redis 메모리 부족 503 (미결 — 기본안)

- **Decision**: 조회 기록은 OOM 예외도 잡아 204(R6). 좋아요 요청 제한(`RateLimiter`)은 지금 OOM이면 503 `AUTOSAVE_UNAVAILABLE`이 나간다 — 좋아요 화면은 code와 상관없이 되돌림 + "좋아요를 반영하지 못했어요". 공용 수정 여부는 ANALYSIS-tier-bc 팀 결정 항목(002 소유).
- **Rationale**: 007 R17과 같은 미결.

## R14. 설정값 (제안)

- **Decision**:
  ```yaml
  blog:
    like:
      rate-limit: { limit: 60, window: 1m }
      reconcile-cron: "0 10 4 * * *"
    view:
      dedupe-window: 24h
      max-per-window: 1
      rate-limit: { limit: 60, window: 1m }
      flush-interval: 1m
      daily-retention: 90d
      retention-cron: "0 20 4 * * *"
      bot-user-agent-words: classpath:policy/view-bot-user-agents.txt
      visitor-cookie-max-age: 365d
  ```
  - 화면 안내 문구("같은 사람은 하루에 한 번만 세요")는 005가 둔 상수다. 중복 기준을 바꾸는 팀원은 설정과 그 상수를 함께 바꾼다(FR-033).
- **Rationale**: 원칙 VII, 31 §2-2.

## R15. 개인정보 처리방침 문구 (확정)

- **Decision**: 첫 공개 전 처리방침 첫 판(001 `PrivacyPage`, 버전 `blog.agreement.privacy.version`)에 "조회수 중복 방지용 무작위 식별자 쿠키(vid, 1년)와, 쿠키를 쓰지 않는 경우 접속 주소·브라우저 정보를 하루 동안만 쓰는 되돌릴 수 없는 값"을 넣는다. 첫 판이라 재동의 흐름을 만들지 않는다(Clarifications Q3). 이미 공개 후라면 001 재동의 규칙을 따르는 별도 결정이 필요하다 — tasks에 확인 작업.
- **Rationale**: FR-034.

- **확인 결과 (T002, 2026-10-08)**: 팀 확인 없이 "아직 일반 공개 전"으로 가정했다(Clarifications Q3 확정안). 처리방침 첫 판에 문단만 더하고(T046) 처리방침 버전(`blog.agreement.privacy.version`)과 재동의 흐름은 바꾸지 않는다. 이미 공개된 뒤라면 001 재동의 규칙(버전 올림) 결정이 따로 필요하다.
