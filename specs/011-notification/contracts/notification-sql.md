# Contract: 알림 구독·저장·조회·정리 SQL

**Feature**: 011-notification | 원문: [docs/20-domain-events.md](../../../docs/20-domain-events.md) §2·§4·§5, [docs/25-notification.md](../../../docs/25-notification.md) §4·§5·§6·§8 | 근거는 [research.md](../research.md)

## 1. 구독 표

모든 리스너: `@Async("eventExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)`. 리스너는 `NotificationWriter`(`@Transactional(REQUIRES_NEW)`)를 부르고 예외를 잡아 WARN만 남긴다(R3).

| 이벤트 | 리스너 | 받는 사람 | 처리 | 제외 규칙(§2) |
|---|---|---|---|---|
| `CommentCreated` | `CommentNotificationListener` | 답글 대상 `R = replyToMemberId ?? parentAuthorId`(답글일 때), 글 작성자 | §3 `REPLY` → `R`, `COMMENT` → 글 작성자(`== R`이면 안 함) | ①~⑤ + 댓글 정상 |
| `CommentDeleted` | `CommentNotificationListener` | — | §7-1 | 없음 |
| `PostLiked` | `LikeNotificationListener` | `postAuthorId` | §4 `LIKE:post:{postId}` | ①~⑤ + 좋아요 있음 |
| `PostUnliked` | `LikeNotificationListener` | `postAuthorId` | §5 | 없음 |
| `MemberFollowed` | `FollowNotificationListener` | `followeeId` | §4 `FOLLOW`(7일) | ①~④ + 팔로우 있음 |
| `MemberUnfollowed` | `FollowNotificationListener` | `followeeId` | §5 | 없음 |
| `PostWentPublic` | `NewPostNotificationListener` | 작성자의 팔로워 전원 | §6 | 비회원 기준 읽기 + SQL 안에서 ②④ |
| `ReportResolved` | `ModerationNotificationListener` | `reporterId` | §3 `REPORT_RESOLVED` | ② |
| `ContentHidden` | `ModerationNotificationListener` | `ownerId` | 댓글이면 §7-1 후 §3 `CONTENT_HIDDEN` | ② |

구독하지 않는 이벤트:

| 이벤트 | 이유 |
|---|---|
| `PostPublished`·`PostEdited` | 알림 없음(새 글 알림은 `PostWentPublic`만) |
| `PostVisibilityChanged`·`PostTrashed`·`PostRestored` | 보여 줄 때 다시 판단(FR-018) |
| `PostPurged` | `notification.post_id` FK CASCADE |
| `ContentUnhidden` | 알림 없음, 숨김 알림은 보여 줄 때 "지금은 다시 보여요"(FR-023) |
| `MemberSuspended` | 알림 없음(FR-008) |
| `MemberWithdrawn`·`MemberRestored` | 생성 때 상태 확인 + 보여 줄 때 "탈퇴한 사용자"(FR-038) |
| `FriendRequested`·`FriendAccepted` | 친구 알림은 선택 기능(기본 비활성, FR-040) |

## 2. 공통 제외 규칙 (`NotificationEligibility`)

순서대로, 하나라도 걸리면 저장하지 않는다.

| # | 조건 | 확인 방법 | 적용 종류 |
|---|---|---|---|
| ① | 받는 사람 = 행동한 사람 | 값 비교 | 행동자가 있는 종류 |
| ② | 받는 사람 탈퇴 유예(또는 없음) | `MemberQueryService.findAccessInfo(receiverId)` 비었거나 `WITHDRAWN` | 전부 |
| ③ | 행동한 사람 탈퇴 유예 | 같은 방법 | 행동자가 있는 종류 |
| ④ | 받는 사람이 그 종류를 끔 | `SELECT EXISTS (SELECT 1 FROM notification_mute WHERE member_id = :r AND type = :t)` | 운영 알림 제외 |
| ⑤ | 받는 사람이 글을 읽을 수 없음 | `PostReadService.isReadable(postId, receiverViewer)` | `COMMENT`·`REPLY`·`LIKE` |
| + | 원래 상태가 사라짐 | `CommentQueryService.isActive` / `LikeQueryService.isLiked` / `FollowQueryService.isFollowing` | 각 종류 |

## 3. 하나짜리 저장

```sql
INSERT INTO notification (receiver_id, type, post_id, comment_id, report_id, result,
                          last_actor_id, actor_count, group_key, created_at, updated_at)
VALUES (:receiverId, :type, :postId, :commentId, :reportId, :result,
        :actorId, :actorCount, NULL, :now, :now)
```

| type | post_id | comment_id | report_id | result | last_actor_id | actor_count |
|---|---|---|---|---|---|---|
| `COMMENT`·`REPLY` | 글 | 댓글 | NULL | NULL | 댓글 작성자 | 1 |
| `REPORT_RESOLVED` | NULL | NULL | 신고 | 결과 | NULL | 0 |
| `CONTENT_HIDDEN`(글) | 글 | NULL | NULL | NULL | NULL | 0 |
| `CONTENT_HIDDEN`(댓글) | `postId` | 댓글 | NULL | NULL | NULL | 0 |

`:now`는 처리 시각(`Clock`). 이벤트 시각이 아니다(목록 정렬이 "알림이 생긴 순서"가 되게).

## 4. 묶음 저장 (`LIKE`·`FOLLOW`, 한 트랜잭션)

```sql
-- ① 중복 (LIKE: 그 글 묶음 전체 / FOLLOW: 7일)
SELECT 1
  FROM notification_actor na
  JOIN notification n ON n.id = na.notification_id
 WHERE na.actor_id = :actorId AND n.receiver_id = :receiverId AND n.group_key = :groupKey
   [AND na.created_at > :now - :followDedupWindow]          -- FOLLOW만
 LIMIT 1;
-- 있으면 끝

-- ② 안 읽은 묶음 확보 + 잠금
INSERT INTO notification (receiver_id, type, post_id, group_key, actor_count, created_at, updated_at)
VALUES (:receiverId, :type, :postIdOrNull, :groupKey, 0, :now, :now)
ON CONFLICT (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
DO UPDATE SET updated_at = notification.updated_at
RETURNING id;

-- ③ 사람 넣기
INSERT INTO notification_actor (notification_id, actor_id, created_at)
VALUES (:id, :actorId, :now)
ON CONFLICT DO NOTHING
RETURNING actor_id;

-- ④ ③에서 행이 왔을 때만
UPDATE notification
   SET actor_count = actor_count + 1, last_actor_id = :actorId, updated_at = :now
 WHERE id = :id;
```

- ②에서 새로 만든 행이 ③에서 0행이 되는 경우는 없다(새 묶음에는 사람이 없음). 그래도 0명 행이 남지 않도록 ③이 0행이고 `actor_count = 0`이면 그 행을 지운다.
- 동시 처리는 ②의 행 잠금으로 줄을 선다(R7).

## 5. 묶음에서 빼기 (`PostUnliked`·`MemberUnfollowed`)

```sql
SELECT id FROM notification
 WHERE receiver_id = :receiverId AND group_key = :groupKey AND read_at IS NULL
 FOR UPDATE;                                         -- 없으면 끝 (읽은 묶음은 그대로)

DELETE FROM notification_actor WHERE notification_id = :id AND actor_id = :actorId;   -- 0행이면 끝

UPDATE notification n
   SET actor_count   = (SELECT count(*) FROM notification_actor x WHERE x.notification_id = n.id),
       last_actor_id = (SELECT x.actor_id FROM notification_actor x WHERE x.notification_id = n.id
                         ORDER BY x.created_at DESC, x.actor_id DESC LIMIT 1)
 WHERE n.id = :id;                                   -- updated_at은 그대로

DELETE FROM notification WHERE id = :id AND actor_count = 0;
```

## 6. 새 글 (`PostWentPublic`)

```sql
-- 먼저 PostReadService.isReadable(:postId, Viewer.anonymous()) 가 false면 끝
INSERT INTO notification (receiver_id, type, post_id, last_actor_id, actor_count, created_at, updated_at)
SELECT f.follower_id, 'NEW_POST', :postId, :authorId, 1, :now, :now
  FROM follow f
  JOIN member r ON r.id = f.follower_id
 WHERE f.followee_id = :authorId
   AND r.withdrawn_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM notification_mute m
                    WHERE m.member_id = f.follower_id AND m.type = 'NEW_POST')
   AND NOT EXISTS (SELECT 1 FROM notification x
                    WHERE x.receiver_id = f.follower_id AND x.type = 'NEW_POST' AND x.post_id = :postId);
```

- `ix_follow_followee`로 팔로워를 찾는다. 작성자 자신은 `ck_follow_self`로 팔로워가 될 수 없다.
- 작성자 탈퇴 유예는 비회원 기준 읽기 판정이 이미 막는다.

## 7. 대상 변화

### 7-1. 댓글 삭제·숨김

```sql
DELETE FROM notification WHERE comment_id = :commentId AND type IN ('COMMENT', 'REPLY');
```

`ix_notification_comment`. 댓글 숨김 알림(`CONTENT_HIDDEN`)은 남긴다.

### 7-2. 행 삭제 (구독 없음)

글 완전 삭제·댓글 행 삭제는 FK CASCADE, 신고 행 삭제는 `report_id = NULL`(SET NULL).

## 8. 목록과 안 읽은 수

```sql
-- 안 읽은 수 (ix_notification_unread)
SELECT count(*) FROM notification WHERE receiver_id = :me AND read_at IS NULL;
```

목록 SQL 1번은 research R10의 문장이다. 이 계약의 요점:

- `FROM notification n JOIN member me … LEFT JOIN member a … LEFT JOIN image ai … LEFT JOIN post p … LEFT JOIN member pm … LEFT JOIN comment c …` 한 번. 첫 조건 `n.receiver_id = :me`.
- 정렬 `n.updated_at DESC, n.id DESC`, `LIMIT :size + 1`, 커서 `(n.updated_at, n.id) < (:t, :id)`(커서가 있을 때만).
- 읽기 판정은 행으로 `PostView`를 만들어 `PostAccessPolicy.canRead`(추가 SQL 없음).
- 댓글은 `left(c.content, :previewScan)`만 읽는다.

## 9. 읽음·삭제·설정

```sql
-- 읽음 (0행이면 404)
UPDATE notification SET read_at = COALESCE(read_at, :now)
 WHERE id = :id AND receiver_id = :me
RETURNING id;

-- 모두 읽음 (바뀐 행 수 = updated)
UPDATE notification SET read_at = :now
 WHERE receiver_id = :me AND read_at IS NULL AND updated_at <= :now;

-- 삭제 (0행이면 404)
DELETE FROM notification WHERE id = :id AND receiver_id = :me;

-- 설정 보기
SELECT type FROM notification_mute WHERE member_id = :me;

-- 설정 저장 (한 트랜잭션, :muted = 끈 종류 배열)
DELETE FROM notification_mute WHERE member_id = :me AND NOT (type = ANY(:muted));
INSERT INTO notification_mute (member_id, type, created_at)
SELECT :me, t, :now FROM unnest(CAST(:muted AS varchar[])) AS t
ON CONFLICT DO NOTHING;
```

## 10. 매일 정리 (`NotificationCleanupJob`, 04:30, ShedLock `notificationCleanup`)

```sql
-- ① 90일 (0행이 될 때까지 반복, 묶음마다 트랜잭션)
DELETE FROM notification
 WHERE id IN (SELECT id FROM notification WHERE updated_at < :cutoff ORDER BY updated_at LIMIT :batch);

-- ② 사람당 1,000개 (최근 하루 받은 사람만)
DELETE FROM notification n
USING (
  SELECT r.receiver_id, k.updated_at AS cut_at, k.id AS cut_id
    FROM (SELECT DISTINCT receiver_id FROM notification WHERE updated_at > :since) r
    CROSS JOIN LATERAL (
      SELECT x.updated_at, x.id FROM notification x
       WHERE x.receiver_id = r.receiver_id
       ORDER BY x.updated_at DESC, x.id DESC
      OFFSET :keep - 1 LIMIT 1
    ) k
) c
WHERE n.receiver_id = c.receiver_id
  AND (n.updated_at, n.id) < (c.cut_at, c.cut_id);
```

`:cutoff = now - retention`, `:since = now - recent-window`, `:keep = max-per-member`. 지운 수를 INFO로 남긴다.

## 11. 탈퇴 정리 단계 (`NotificationWithdrawalPurgeStep`, 015 order 70, `MANDATORY`)

```sql
-- ① 받은 알림
DELETE FROM notification WHERE receiver_id = :m;

-- ② 남의 묶음에서 빼기 (네 문장을 따로 — 한 문장 CTE는 UPDATE가 DELETE 전 상태를 봄)
SELECT id FROM notification
 WHERE id IN (SELECT notification_id FROM notification_actor WHERE actor_id = :m)
 ORDER BY id FOR UPDATE;                                         -- → :ids
DELETE FROM notification_actor WHERE actor_id = :m;
UPDATE notification n
   SET actor_count   = (SELECT count(*) FROM notification_actor x WHERE x.notification_id = n.id),
       last_actor_id = (SELECT x.actor_id FROM notification_actor x WHERE x.notification_id = n.id
                         ORDER BY x.created_at DESC, x.actor_id DESC LIMIT 1)
 WHERE n.id = ANY(:ids);
DELETE FROM notification WHERE id = ANY(:ids) AND actor_count = 0;

-- ③ 내가 행동한 하나짜리 (ix_notification_last_actor)
DELETE FROM notification WHERE last_actor_id = :m AND group_key IS NULL;

-- ④ 끄기 설정
DELETE FROM notification_mute WHERE member_id = :m;
```

## 12. 다른 기능과의 약속

| 기능 | 약속 |
|---|---|
| 002·004 | `PostWentPublic`은 글마다 한 번(이미 구현). 필드를 바꾸면 알린다 |
| 004 | `PostReadService.isReadable(postId, viewer)`를 이 기능이 더한다(판정은 `requireReadable`과 같음) |
| 007 | `CommentCreated`·`CommentDeleted`를 contracts/events.md §1 필드로 발행. 자리로 남긴 삭제도 발행. 숨김은 `CommentDeleted`를 내지 않는다(014 `ContentHidden`이 대신). `CommentQueryService.isActive` |
| 009 | `PostLiked`·`PostUnliked`는 실제로 바뀐 경우에만(SC-013). `LikeQueryService.isLiked` |
| 010 | `MemberFollowed`·`MemberUnfollowed`는 실제로 바뀐 경우에만. `FollowQueryService.isFollowing`. 내 팔로워 목록 화면 `/@{handle}/followers` |
| 014 | `ReportResolved`(신고 한 건마다, `CLOSED_NO_TARGET`은 없음)·`ContentHidden`(신고자 ID 없음). `hidden_reason`은 6개 코드 |
| 015 | order 70 단계 이름·순서(contracts/purge-steps.md §2). 10·20 다음 |
| 005 | 글 상세가 `?comment=`를 `around`로 넘기고 `canonical`에 넣지 않는다(이미 구현). `relativeText` 재사용 |
