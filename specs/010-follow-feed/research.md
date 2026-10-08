# Research: 팔로우·팔로잉 피드

**Feature**: 010-follow-feed | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

각 항목은 Decision / Rationale / Alternatives considered 순서다. "(확정)"은 원문·Clarifications·이미 머지된 코드로 정해진 것, "(제안)"은 이 계획이 정한 것으로 팀 확인이 필요하다.

---

## R1. 스키마는 V1 그대로 (확정)

- **Decision**: 새 마이그레이션이 없다. `follow`(PK `(follower_id, followee_id)`, 두 FK `member(id) ON DELETE RESTRICT`, `ck_follow_self CHECK (follower_id <> followee_id)`, `ix_follow_followee (followee_id, created_at DESC)`, `ix_follow_follower (follower_id, created_at DESC)`).
  - 팔로워 목록·수: `ix_follow_followee`. 팔로잉 목록·수: `ix_follow_follower`. 팔로우 여부·피드 `EXISTS`: PK
  - 같은 `created_at` 안의 정렬 기준(회원 번호)은 인덱스에 없지만 PK로 행이 유일하고, 같은 마이크로초가 드물어 추가 정렬 비용이 작다
- **Rationale**: 24 §10, 51 §2. 자기 팔로우는 서버 판정(400)과 `ck_follow_self` 두 겹(FR-003).
- **Alternatives considered**: `member.follower_count` 컬럼 — F-6(볼 때 센다)과 다르고 1만 명 초과 때 검토(24 §5).

## R2. 모듈 위치 (제안)

- **Decision**: 팔로우 관계(저장·수·목록·팔로우 여부·탈퇴 정리)는 `interaction` 모듈, 피드는 `discovery` 모듈에 둔다. `follow` 테이블은 interaction이 소유한다.
- **Rationale**: 02 §3 모듈 표에 팔로우가 없다. interaction은 "회원이 다른 회원·글에 하는 행동"(댓글·좋아요)을 모은 곳이라 팔로우가 같은 성격이고, 피드는 홈·블로그와 같은 카드 목록이라 discovery다. 새 모듈을 만들면 모듈 목록(헌법 II)을 바꿔야 한다.
- **Alternatives considered**: account(친구와 같은 곳) — 친구는 공개 범위 판정에 쓰이는 계정 관계이고, 팔로우는 이벤트·피드 쪽 관계라 성격이 다르다. 새 `follow` 모듈 — 위 이유.

## R3. 팔로우 API와 판정 순서 (확정 + 제안)

- **Decision**: `PUT /api/members/{handle}/follow`(팔로우 상태로), `DELETE /api/members/{handle}/follow`(해제 상태로) → 200 `{following: boolean, followerCount: integer}`. 판정 순서:
  1. 비로그인 → 401 `LOGIN_REQUIRED`
  2. 탈퇴 유예 회원 → 403 `ACCOUNT_WITHDRAWN`(001 게이트 필터)
  3. `AccountStatusGuard.requireActive(me, ACCOUNT_WRITE)` → 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED`. 이메일 인증 전은 통과(F-5, 42 §10-1). `ACCOUNT_WRITE`는 001이 "친구 요청"을 넣은 종류로, 인증이 필요 없는 회원 사이 관계 쓰기를 뜻한다(제안 — 001 `ActionKind` 주석에 "팔로우" 추가)
  4. 대상 `MemberQueryService.findReadableBlogOwner(handle)` 없음 → 404 `NOT_FOUND`(없는 주소·탈퇴 유예·익명 처리, 본문 고정). 대문자 주소도 그대로 조회해 404(화면 경로는 005 셸이 301)
  5. 대상 = 나 → 400 `CANNOT_FOLLOW_SELF` "자기 자신은 팔로우할 수 없어요"(원문 코드, 문구 제안)
  6. `RateLimiter.acquireOrThrow("ratelimit:follow:" + me, 30, 1분)` → 429 `TOO_MANY_REQUESTS` + `Retry-After`(Clarifications Q1, README "429는 맨 끝"). 팔로우·언팔로우 합산, Redis 장애면 통과
  7. 트랜잭션(R4)
- 관리자도 같다(FR-008). 정지된 **대상**은 블로그가 보이므로 팔로우할 수 있다(42 P-7, spec Assumptions).
- **Rationale**: 24 §3, 02 §5-1(상태 지정은 PUT/DELETE), 42 §3, README "정해진 것" 판정 순서, Clarifications Q1.
- **Alternatives considered**: `POST`/`DELETE` — 02 §5-1 규약과 다르다. 판정 순서에서 요청 제한을 맨 앞에 — README 규칙과 어긋나고 404 확인 전에 횟수를 쓴다.

## R4. 저장 트랜잭션과 이벤트 (확정)

- **Decision**: `FollowService.follow(me, handle)`(`@Transactional`):
  - `INSERT INTO follow (follower_id, followee_id, created_at) VALUES (:me, :target, :now) ON CONFLICT DO NOTHING RETURNING follower_id` → 행이 오면 `MemberFollowed(me, target, now)` 발행
  - `unfollow`: `DELETE FROM follow WHERE follower_id = :me AND followee_id = :target RETURNING follower_id` → 행이 오면 `MemberUnfollowed(me, target, now)`
  - 같은 트랜잭션에서 `followerCount(target)`(R5)를 세서 응답
  - 동시에 20번 와도 PK로 한 행만 생기고 `RETURNING`이 한 번만 행을 돌려준다(SC-001)
  - 이벤트는 `shared.event`의 불변 record, 구독은 커밋 후(011)
- **Rationale**: 24 §4, 20 §3-4·EV-4.
- **Alternatives considered**: `SELECT` 후 `INSERT` — 경합 때 중복 키 예외나 이벤트 두 번.

## R5. 수 세기와 블로그 머리말 (확정 + 제안)

- **Decision**:
  ```sql
  -- 팔로워 수 (ix_follow_followee + ix_member_withdraw_purge)
  SELECT count(*) FROM follow f
   WHERE f.followee_id = :target
     AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.follower_id
                      AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL);
  -- 팔로잉 수 (ix_follow_follower)
  SELECT count(*) FROM follow f
   WHERE f.follower_id = :target
     AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.followee_id
                      AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL);
  ```
  익명 처리된 회원의 관계는 015 정리 때 지워지므로 조건에 `deleted_at IS NULL`만 둬도 된다(spec 원문 SQL 그대로). 정지 회원은 빼지 않는다.
- 블로그 머리말 `GET /api/members/{handle}`(005 `BlogHeaderView`)에 `followerCount`, `followingCount`, `followedByMe`(비회원·내 블로그면 false)를 더한다. `BlogQueryService.getHeader`가 interaction `FollowQueryService.headerStats(ownerId, viewer)`(SQL: 수 2번 + 팔로우 여부 1번 — 비회원이면 2번)를 부른다. 기존 칸은 바꾸지 않는다
- SC-007: 팔로워 1만 명 데이터로 `EXPLAIN (ANALYZE)`가 `ix_follow_followee`를 쓰고 10ms 이내인지 통합 테스트(`FollowCountPerformanceIT`)로 확인한다. 기준을 넘으면 24 §5대로 카운터 도입을 팀에 알린다
- **Rationale**: F-6, 24 §5(측정값), 005 data-model "010이 팔로워 수 등 필드를 추가할 수 있다".
- **Alternatives considered**: 수를 별도 API로 — 머리말 요청이 2번이 된다. Redis 캐시 — 유예·복구 즉시 반영(SC-006)이 어렵다.

## R6. 팔로워·팔로잉 목록 (확정 + 제안)

- **Decision**: `GET /api/members/{handle}/followers?cursor=`, `GET /api/members/{handle}/following?cursor=` → `{items: [{handle, nickname, profileImageUrl, bio, followedByMe, isMe}], nextCursor}`. 비회원도 볼 수 있다(F-3). 없는 주소·탈퇴 유예·익명 처리 → 404
  ```sql
  -- 팔로워 목록 (following은 follower_id/followee_id를 바꾼 같은 모양)
  SELECT f.created_at, m.id, m.handle, m.nickname, m.bio,
         COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key
    FROM follow f
    JOIN member m ON m.id = f.follower_id AND m.status <> 'WITHDRAWN'
    LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
         AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
   WHERE f.followee_id = :target
     -- 커서가 있을 때만: AND (f.created_at, f.follower_id) < (:cursorAt, :cursorId)
   ORDER BY f.created_at DESC, f.follower_id DESC
   LIMIT :pageSizePlusOne;
  -- 보는 사람의 팔로우 여부 (로그인했을 때만)
  SELECT followee_id FROM follow WHERE follower_id = :viewer AND followee_id = ANY(:ids);
  ```
  - `m.status <> 'WITHDRAWN'`은 유예·익명 처리를 함께 뺀다(익명 처리 회원 관계는 이미 지워졌지만 정리 실패로 남아 있어도 안전). 수 SQL(R5)과 같은 회원이 빠진다
  - 페이지 20개(`blog.follow.list-page-size`), 클라이언트 `size`는 무시. 21개 읽어 다음 페이지 판정(005와 같은 방식)
  - 커서: 001 `CursorCodec`, `ListScope.of("followers:" + handle)` / `"following:" + handle`, 키 `[created_at 마이크로초, 회원 번호]`. 다른 목록의 커서 → 400 `INVALID_CURSOR`
  - 소개는 원문 그대로 주고 화면이 첫 줄만 보여 준다(줄바꿈 기준, 텍스트 노드)
  - `isMe`: 보는 사람 자신이면 true(버튼 없음)
  - `Cache-Control: private, no-cache`(보는 사람마다 `followedByMe`가 다름, 005 `CacheControlPolicy.NO_CACHE`)
- **Rationale**: 24 §2-2·§3·§5, FR-014~FR-016·FR-023, 10 §4(커서).
- **Alternatives considered**: 회원 번호만 읽고 표시 정보를 따로 — Complexity Tracking 참고.

## R7. 피드 (확정 + 제안)

- **Decision**: `GET /api/feed?cursor=` → `{items: PostCard[], nextCursor, hasFollowing}`. 로그인 필요(401), 유예 회원은 게이트 403, 인증 전·정지 남은 세션도 읽기는 허용(읽기라 가드를 부르지 않음 — 005 홈과 같음).
  - 005 `PostCardQueryRepository`의 `CardFilter`(008이 만듦: `authorId`, `tagId`)에 `followerId`를 더해, 있으면 `AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :followerId AND f.followee_id = p.author_id)`
  - 노출 조건은 004 `VisibilityFilter.forViewer(Viewer.anonymous(), null)` — 전체 공개 목록 조건(공개·발행·휴지통 아님·숨김 아님·작성자 탈퇴 유예 아님). 친구 공개 글은 "공개"가 아니라서 빠진다(F-7). 피드 주인 자신의 글은 팔로우 관계가 없으므로 나오지 않는다
  - 정렬 `first_public_at DESC, id DESC`, 9개(`blog.list.page-size`), 10개 읽기, 커서는 005 `PostListCursor`에 `ListScope.of("feed")`
  - `hasFollowing`: 첫 페이지(`cursor` 없음)이고 결과가 비었을 때만 `SELECT EXISTS (SELECT 1 FROM follow WHERE follower_id = :me)`로 채운다(그 밖에는 true로 두어 SQL을 아낀다). 화면은 `items`가 비고 `hasFollowing = false`면 "팔로우한 사람이 없어요…", `true`면 "팔로우한 사람의 공개 글이 아직 없어요"
  - 같은 공용 조건이라 인덱스는 `ix_post_feed`(전체 공개 시간순) 또는 `ix_post_blog`(작성자별)를 플래너가 고른다. 글 1만 건·팔로우 수백 명에서 두 경우 모두 `EXPLAIN`으로 200ms 이내를 확인한다(`FeedApiIT`)
  - `Cache-Control: private, no-cache`
- 언팔로우 직후 다음 요청부터 빠진다(조회 때 관계를 읽음, SC-005). 이미 받은 카드는 화면이 그대로 둔다(FR-020)
- **Rationale**: 24 §2-3·§5, F-4·F-7, 06 R-2a(H1), 10 §4, FR-017~FR-023.
- **Alternatives considered**: 원문의 `follow f JOIN post p` — 같은 결과지만 005·008과 다른 SQL을 하나 더 두게 된다. 피드 미리 만들기(팬아웃) — 24 §5가 규모 조건이 생길 때로 미룸.

## R8. 005 자리 채우기 (확정)

- **Decision**:
  - 서버: `interaction.application.AuthorFollowStatusQueryAdapter implements AuthorFollowStatusQuery`(`SELECT EXISTS … PK`). 005 `PostReadingPorts`의 기본 Bean은 `@ConditionalOnMissingBean`이라 이 Bean이 있으면 물러난다. 글 상세 `viewer.followingAuthor`가 실제 값이 된다(005 research R-30)
  - 화면: 005 `AuthorCard`의 `followButton` 자리에 `FollowButton`(내 글이면 005가 자리를 만들지 않음), `BlogPage` 머리말의 "공개 글 N" 줄을 `FollowCounts`("공개 글 24 · 팔로워 12 · 팔로잉 30", 숫자는 목록 링크)로 바꾸고 옆에 `FollowButton`(`isMe`면 없음)
  - `TODO(010)`·"010 팔로우·피드 기능 소유" 표시를 모두 정리한다
- **Rationale**: 005 tasks T033·T039, 005 research R-30, FR-009·FR-011.

## R9. 화면 (확정 + 제안)

- **Decision**:
  - `FollowButton`(`features/follow`): 상태 `[팔로우]`(채운 버튼) / `[팔로잉 ✓]`(테두리 버튼, `aria-pressed="true"`) — 마우스를 올리거나 초점을 받으면 글자만 `[언팔로우]`. 누르는 즉시 상태와 팔로워 수를 바꾸고, `useFollowToggle`이 0.3초 동안 마지막 상태만 보낸다(009 `useLikeToggle`과 같은 방식). 응답의 `following`·`followerCount`로 맞추고, 실패하면 누르기 전 상태로 되돌린 뒤 "잠시 후 다시 시도해 주세요"(`role="status"`). 비회원이면 004 `useAuthGate`로 로그인 화면(돌아온 뒤 자동으로 팔로우하지 않음, H6). 색만이 아니라 글자와 ✓로 구분(FR-010)
  - `/feed`(`FeedPage`): 비로그인은 `/login?returnTo=/feed`로. 005 `PostCardGrid`·`LoadMoreButton`·`useCursorList({listKey: 'feed', restore: true})`(30분 복원, FR-022), 빈 상태 문구 두 가지, 로딩·실패는 홈과 같다
  - `/@{handle}/followers`·`/@{handle}/following`(`FollowListPage`): 제목 "{닉네임}님의 팔로워"/"팔로잉", 항목 `FollowListItem`(사진·`닉네임 @주소`(블로그 링크)·소개 첫 줄·`FollowButton`), 20개씩 [더 보기], 빈 문구 "아직 팔로워가 없어요"/"아직 팔로우한 사람이 없어요", 404면 005 공통 404 화면
  - 머리말 [피드]: 001 `SessionBar`(임시 공통 머리말)에 로그인했을 때만 `/feed` 링크
  - 서버 페이지 셸: 005 `PageShellController`에 `/@{handle}/followers`·`/@{handle}/following`을 더해 블로그 셸과 같은 규칙(대문자 → 301 소문자, 없는 블로그·유예·익명 → 404 + 공통 404 HTML)으로 첫 응답 상태를 맞춘다. `/feed`는 일반 SPA 경로
- **Rationale**: 24 §2, FR-009~FR-022, 2026-10-07 H6·H7.
- **Alternatives considered**: 응답을 기다린 뒤 바꾸기 — FR-010·SC-009(즉시).

## R10. 다른 기능과의 연결 (확정)

- **Decision**:
  - 011: `MemberFollowed` → 새 팔로워 묶음 알림(같은 사람 7일 1번), `MemberUnfollowed` → 안 읽은 묶음에서 빼기, `PostWentPublic` → 팔로워에게 새 글 알림. 새 글 알림의 팔로워 목록은 011이 `follow`를 직접 읽는 `INSERT … SELECT`로 만든다(25 §4-1, 011 plan 몫). 이 기능은 `FollowQueryService.followerIdsOf(memberId)`를 공개해 두되 011이 쓰지 않아도 된다
  - 015: `FollowWithdrawalPurgeStep`(order 65) — `DELETE FROM follow WHERE follower_id = :m OR followee_id = :m`, `MANDATORY`, 이벤트 없음. 유예 중 제외는 R5·R6·R7 조건으로 자동
  - 015 `blog.withdraw.purge.redis-key-templates`에 `ratelimit:follow:{memberId}`를 한 줄 더한다
  - 015보다 먼저 구현하면 015 tasks T009(`WithdrawalPurgeStep` 인터페이스)를 이 기능이 먼저 만든다
- **Rationale**: 24 §6·§8, 25 §4, 44 §4, 015 contracts/purge-steps.md §2.

## R11. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/follow.csv`(004 하네스 형식, owner `010`). 004 하네스의 `targetState`는 글 상태라서, 팔로우 행위는 그 글의 **작성자**를 대상 회원으로 쓴다: `PUBLISHED_PUBLIC` = 정상 회원, `AUTHOR_WITHDRAWN` = 탈퇴 유예 회원, `NONEXISTENT` = 없는 주소. 행위자 `AUTHOR`는 자기 자신이 대상이다.

  | action | ANONYMOUS | UNVERIFIED | MEMBER | AUTHOR(자기) | ADMIN | SUSPENDED | WITHDRAWN |
  |---|---|---|---|---|---|---|---|
  | `follow.put` (정상 대상) | 401 | 200 | 200 | 400 `CANNOT_FOLLOW_SELF` | 200 | 403 `ACCOUNT_SUSPENDED` | 403 `ACCOUNT_WITHDRAWN` |
  | `follow.put` (유예·없음 대상) | 401 | 404 | 404 | — | 404 | 403 | 403 |
  | `follow.delete` | `follow.put`과 같음 (자기 자신은 400) | | | | | | |
  | `follow.followers`·`follow.following` (정상 대상) | 200 | 200 | 200 | 200 | 200 | 200(읽기) | 403 |
  | 같은 목록 (유예·없음 대상) | 404 | 404 | 404 | — | 404 | 404 | 403 |
  | `feed.read` (`NONE`) | 401 | 200 | 200 | 200 | 200 | 200(읽기) | 403 |

- 정지 행위자는 "로그인 뒤 DB에서 정지로 바꾼 남은 세션"이다(004 하네스 정의). 읽기는 가드를 부르지 않으므로 200이다.
- **Rationale**: 헌법 III, 42 §10-1·P-12, 004 ANALYSIS R6.

## R12. 설정값 (제안)

```yaml
blog:
  follow:
    rate-limit:
      limit: 30          # 팔로우·언팔로우 합산 (Clarifications Q1)
      window: 1m
    list-page-size: 20   # 팔로워·팔로잉 목록 (24 §2-2)
```

피드 카드 수는 005 `blog.list.page-size`(9)를 그대로 쓴다.

## R13. 요청 제한 키 이름 (확정)

- **Decision**: `ratelimit:follow:{memberId}` — 002 이후 기능의 `ratelimit:` 접두어 규칙. 001의 `rl:` 접두어 키와 섞여 있는 문제는 ANALYSIS-tier-bc에 적는다.
