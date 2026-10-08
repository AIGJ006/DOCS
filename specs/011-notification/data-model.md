# Data Model: 도메인 이벤트·인앱 알림

**Feature**: 011-notification | **Date**: 2026-10-08 | 스키마 변경 없음(V1)

## 1. 테이블 (V1, notification 모듈 소유)

### 1-1. `notification`

| 컬럼 | 타입 | NULL | 이 기능의 쓰임 |
|---|---|---|---|
| `id` | bigint IDENTITY | 아님 | 알림 번호. 주소 `/api/notifications/{id}` |
| `receiver_id` | bigint FK → member RESTRICT | 아님 | 받는 회원. 모든 조회·변경 SQL의 첫 조건 |
| `type` | varchar(30) | 아님 | `NotificationType` 7종 |
| `post_id` | bigint FK → post CASCADE | 허용 | `COMMENT`·`REPLY`·`LIKE`·`NEW_POST`·`CONTENT_HIDDEN` |
| `comment_id` | bigint FK → comment CASCADE | 허용 | `COMMENT`·`REPLY`(새 댓글), `CONTENT_HIDDEN`(댓글 숨김) |
| `report_id` | bigint FK → report SET NULL | 허용 | `REPORT_RESOLVED` |
| `result` | varchar(20) | 허용 | `REPORT_RESOLVED`일 때만 `ACTION_TAKEN`·`NO_VIOLATION` |
| `last_actor_id` | bigint FK → member RESTRICT | 허용 | 하나짜리: 행동자. 묶음: 남은 사람 중 가장 최근. 운영 알림: NULL |
| `actor_count` | integer ≥ 0 | 아님 | 하나짜리 1, 묶음 = `notification_actor` 행 수, 운영 알림 0 |
| `group_key` | varchar(100) | 허용 | `LIKE:post:{postId}`·`FOLLOW`. 그 밖 NULL |
| `read_at` | timestamptz | 허용 | 읽은 시각. NULL = 안 읽음 |
| `created_at` | timestamptz | 아님 | 처음 만든 시각 |
| `updated_at` | timestamptz | 아님 | 목록 정렬·시각 표시·90일 정리 기준. 묶음에 사람이 더해질 때만 바뀜 |

종류별 채우는 값:

| type | post_id | comment_id | report_id | result | last_actor_id | actor_count | group_key | 끌 수 있음 |
|---|---|---|---|---|---|---|---|---|
| `COMMENT` | 글 | 새 댓글 | — | — | 댓글 작성자 | 1 | — | 예 |
| `REPLY` | 글 | 새 답글 | — | — | 답글 작성자 | 1 | — | 예 |
| `LIKE` | 글 | — | — | — | 최근 사람 | n | `LIKE:post:{id}` | 예 |
| `FOLLOW` | — | — | — | — | 최근 사람 | n | `FOLLOW` | 예 |
| `NEW_POST` | 글 | — | — | — | 글 작성자 | 1 | — | 예 |
| `REPORT_RESOLVED` | — | — | 신고 | 결과 | — | 0 | — | 아니오 |
| `CONTENT_HIDDEN` | 글 | 숨긴 댓글(댓글이면) | — | — | — | 0 | — | 아니오 |

불변식:

- 받는 사람·묶음 키마다 안 읽은 묶음은 최대 1개(`uq_notification_unread_group`).
- 묶음의 `actor_count` = 그 알림의 `notification_actor` 행 수, `last_actor_id` = 그중 `created_at`이 가장 늦은 사람. 0명이 되면 행을 지운다.
- 글·댓글 행이 지워지면 알림도 지워진다(FK CASCADE). 신고 행이 지워지면 `report_id`만 NULL.

### 1-2. `notification_actor`

| 컬럼 | 타입 | 쓰임 |
|---|---|---|
| `notification_id` | bigint FK → notification CASCADE | 묶음 알림 (PK 1) |
| `actor_id` | bigint FK → member RESTRICT | 묶인 사람 (PK 2) |
| `created_at` | timestamptz | 들어간 시각. FOLLOW 7일 중복 판정, 마지막 행동자 다시 계산 |

묶음(`LIKE`·`FOLLOW`)에만 쓴다. 하나짜리 알림에는 행이 없다.

### 1-3. `notification_mute`

| 컬럼 | 타입 | 쓰임 |
|---|---|---|
| `member_id` | bigint FK → member RESTRICT | 회원 (PK 1) |
| `type` | varchar(30) CHECK 5종 | 끈 종류 (PK 2) |
| `created_at` | timestamptz | 끈 시각 |

행이 있으면 꺼짐. 기본(행 없음)은 모두 켜짐.

## 2. 도메인 값 (notification.domain)

```java
public enum NotificationType {
    COMMENT, REPLY, LIKE, FOLLOW, NEW_POST, REPORT_RESOLVED, CONTENT_HIDDEN;
    public boolean isOperational() { return this == REPORT_RESOLVED || this == CONTENT_HIDDEN; }
    public boolean isGrouped() { return this == LIKE || this == FOLLOW; }
    public boolean needsReadablePost() { return this == COMMENT || this == REPLY || this == LIKE || this == NEW_POST; }
}

/** 끌 수 있는 5종 (V1 ck_notification_mute_type). 설정 API의 키 이름과 같다. */
public enum MutableType { COMMENT, REPLY, LIKE, FOLLOW, NEW_POST }

public record GroupKey(String value) {
    public static GroupKey like(long postId) { return new GroupKey("LIKE:post:" + postId); }
    public static GroupKey follow() { return new GroupKey("FOLLOW"); }
}
```

## 3. 구독하는 이벤트 (shared.event, 다른 기능이 만듦)

| 이벤트 | 필드 | 만드는 곳 | 이 기능의 처리 |
|---|---|---|---|
| `CommentCreated` | `commentId, postId, postAuthorId, authorId, parentId, parentAuthorId, replyToMemberId, createdAt` | 007 | `COMMENT`·`REPLY` 저장 |
| `CommentDeleted` | `commentId, postId, postAuthorId, authorId, parentId, deletedAt` | 007 | 그 댓글의 `COMMENT`·`REPLY` 삭제 |
| `PostLiked` | `postId, postAuthorId, memberId, likedAt` | 009 | `LIKE` 묶음에 더함 |
| `PostUnliked` | `postId, postAuthorId, memberId, unlikedAt` | 009 | 안 읽은 `LIKE` 묶음에서 뺌 |
| `MemberFollowed` | `followerId, followeeId, followedAt` | 010 | `FOLLOW` 묶음에 더함 |
| `MemberUnfollowed` | `followerId, followeeId, unfollowedAt` | 010 | 안 읽은 `FOLLOW` 묶음에서 뺌 |
| `PostWentPublic` | `postId, authorId, firstPublicAt` | 002·004 (있음) | 팔로워 전원 `NEW_POST` |
| `ReportResolved` | `reportId, reporterId, targetType, targetId, result, resolvedAt` | 014 | 신고자 `REPORT_RESOLVED` |
| `ContentHidden` | `targetType, targetId, ownerId, postId, hiddenAt` | 014 | 작성자 `CONTENT_HIDDEN` (+ 댓글이면 그 댓글 알림 삭제) |

구독하지 않는 이벤트와 이유는 [contracts/notification-sql.md](./contracts/notification-sql.md) §1.

## 4. 다른 모듈에 더하는 공개 조회

| 모듈 | 서명 | 판정 | 소유 기능 |
|---|---|---|---|
| post | `PostReadService.isReadable(long postId, Viewer viewer)` → `boolean` | `requireReadable`과 같음, 없는 글 false | 004 파일에 메서드 추가 |
| interaction | `CommentQueryService.isActive(long commentId)` → `boolean` | 행 있음 + `deleted_at IS NULL` + `hidden_at IS NULL` | 007 (먼저 하는 쪽이 더함) |
| interaction | `LikeQueryService.isLiked(long postId, long memberId)` → `boolean` | `post_like` 행 있음 | 009 (먼저 하는 쪽이 더함) |
| interaction | `FollowQueryService.isFollowing(long followerId, long followeeId)` → `boolean` | `follow` 행 있음 | 010 |
| account | `MemberQueryService.findAccessInfo(long)` (있음) | 상태로 탈퇴 유예 판정 | 001 |

## 5. 응답 모델

```java
public record UnreadCount(long count) {}

public record NotificationPage(List<NotificationItem> items, String nextCursor) {}

public record NotificationItem(
        long id,
        NotificationType type,
        boolean read,
        Instant updatedAt,
        ActorView actor,        // 운영 알림 null
        int othersCount,        // 묶음 actor_count − 1, 그 밖 0
        PostRef post,           // 글이 없는 종류·댓글 숨김 null
        CommentRef comment,     // COMMENT·REPLY만, 글을 읽을 수 없으면 null
        ReportRef report,       // REPORT_RESOLVED만
        HiddenRef hidden,       // CONTENT_HIDDEN만
        String url) {}          // 이동할 곳 없으면 null

public sealed interface ActorView {
    record Member(String handle, String nickname, String profileImageUrl) implements ActorView {}
    record Withdrawn() implements ActorView {}   // JSON {"withdrawn": true}
}
public sealed interface PostRef {
    record Readable(String title, String url) implements PostRef {}
    record Unavailable() implements PostRef {}   // JSON {"unavailable": true}
}
public record CommentRef(long id, String preview) {}
public record ReportRef(String result) {}        // ACTION_TAKEN | NO_VIOLATION
public record HiddenRef(String targetType, boolean stillHidden, String reason) {} // reason은 숨김 중일 때만

public record NotificationSettings(boolean COMMENT, boolean REPLY, boolean LIKE, boolean FOLLOW, boolean NEW_POST) {}
```

- `id`는 JSON 숫자(64비트 IDENTITY지만 수천 명 규모라 JS 안전 범위 안).
- `updatedAt`은 UTC ISO-8601.

## 6. 설정값 (`NotificationProperties`, `blog.notification.*`)

| 키 | 기본 | 쓰는 곳 |
|---|---|---|
| `retention` | `90d` | 정리 ① |
| `max-per-member` | `1000` | 정리 ② |
| `cleanup.cron` | `0 30 4 * * *` (시간대는 공통 `blog.time-zone` — Tier A R11) | `NotificationCleanupJob` |
| `cleanup.batch-size` | `1000` | 정리 ① 한 번에 지우는 수 |
| `cleanup.recent-window` | `1d` | 정리 ② 대상 고르기 |
| `follow-dedup-window` | `7d` | FOLLOW 중복 판정 |
| `preview-length` / `preview-scan` | `50` / `400` | 댓글 미리보기 |
| `dropdown-size` / `page-size` | `10` / `20` | 목록 `size` 허용값 |

공용 실행기: `blog.async.event.queue-capacity: 1000`, `blog.async.event.await-termination: 20s`(`CoreProperties.Pool`에 새 필드, 기본 10s).

## 7. 상태 변화

```text
(없음) ──이벤트·제외 규칙 통과──▶ 안 읽음 ──누름/모두 읽음──▶ 읽음
   안 읽음 묶음 ──사람 더해짐──▶ 안 읽음(updated_at 갱신, 맨 위)
   안 읽음 묶음 ──취소·언팔로우──▶ 안 읽음(인원 −1) / 0명이면 삭제
   읽음 묶음 ──취소──▶ 그대로 (새 좋아요는 새 묶음)
   어느 상태든 ──[×]·90일·1,000개 초과·댓글 삭제·숨김·글/댓글 행 삭제·탈퇴 정리──▶ 삭제
```
