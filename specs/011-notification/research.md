# Research: 도메인 이벤트·인앱 알림

**Feature**: 011-notification | **Date**: 2026-10-08

spec Implementation Notes와 원문(20·25·51)에서 정한 것은 "확정", 이 계획이 새로 정한 것은 "제안"으로 표시한다.

## R1. 스키마 (확정)

- **Decision**: V1 그대로 쓴다. 마이그레이션 없음.
  - `notification`: 받는 사람 `receiver_id`, `type`(7종 CHECK `ck_notification_type`), `post_id`·`comment_id`(FK `ON DELETE CASCADE`), `report_id`(FK `ON DELETE SET NULL`), `result`(`REPORT_RESOLVED`일 때만, `ck_notification_result`), `last_actor_id`, `actor_count`(≥ 0), `group_key`(`LIKE`·`FOLLOW`일 때만, `ck_notification_group`), `read_at`, `created_at`, `updated_at`.
  - 부분 UNIQUE `uq_notification_unread_group (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL` — 안 읽은 묶음은 받는 사람·묶음 키마다 하나.
  - 인덱스: `ix_notification_list (receiver_id, updated_at DESC, id DESC)`, `ix_notification_unread (receiver_id) WHERE read_at IS NULL`, `ix_notification_post`, `ix_notification_comment`, `ix_notification_cleanup (updated_at)`, `ix_notification_last_actor (last_actor_id) WHERE group_key IS NULL`.
  - `notification_actor (notification_id, actor_id)` PK, `created_at`, 알림 삭제 시 CASCADE, `ix_notification_actor_actor (actor_id, created_at DESC)`.
  - `notification_mute (member_id, type)` PK, 5종 CHECK. 행이 없으면 켜짐.
- 친구 알림(`FRIEND_REQUEST`·`FRIEND_ACCEPTED`)은 공통 CHECK에 없다. 적용자가 자기 마이그레이션으로 두 CHECK를 넓히고 001 `FriendRequested`·`FriendAccepted`를 구독한다(FR-040, 03 E-10). 이 계획은 구독하지 않는다.
- **Rationale**: 01 회의 M1(테이블은 통합 V1에 이미 있음), 헌법 I.
- **Alternatives considered**: `target_type`/`target_id` 한 쌍(03 §5) — 51이 대상별 FK로 바꿨다(NT-6). 글 삭제 때 알림이 함께 사라지는 것을 FK가 보장한다.

## R2. 모듈 배치 (제안 — 팀 확인)

- **Decision**: 새 최상위 모듈 `com.team.blog.notification`(web·application·domain·infra). 알림 테이블 3개의 유일한 소유자다.
  - 다른 모듈은 이벤트(`shared.event`)로만 알린다. 알림 모듈이 다른 모듈을 부를 때는 공개 Service만 쓴다: 001 `MemberQueryService.findAccessInfo`, 004 `PostReadService.isReadable`(새 메서드), 007 `CommentQueryService.isActive`(새), 009 `LikeQueryService.isLiked`(새 또는 기존 조회), 010 `FollowQueryService.isFollowing`.
- **Rationale**: 02 §3에는 notification 패키지가 따로 없지만, 20 §5 구독 표의 알림 리스너가 7개 이벤트 종류를 받고 화면 API가 7개라 interaction에 넣으면 모듈이 지나치게 커진다. 006이 `moderation` 모듈을 새로 둔 선례가 있다.
- **Alternatives considered**: `interaction`에 넣기 — 댓글·좋아요·팔로우와 같은 모듈이 되어 경계 검사가 쉬워지지만, 신고 결과·숨김 알림(014 moderation)까지 interaction이 받게 된다.
- **팀 확인(T003)**: 헌법 II의 모듈 목록(account·post·tag·media·interaction·discovery·shared)에 `moderation`(006)과 `notification`을 더할지.

## R3. 리스너 구조와 트랜잭션 (확정 + 제안)

- **Decision**:
  ```java
  @Async("eventExecutor")
  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void on(PostLiked e) {
      try {
          writer.addLike(e.postAuthorId(), e.memberId(), e.postId(), e.likedAt());
      } catch (RuntimeException ex) {
          log.warn("알림 저장 실패: event={}, postId={}", "PostLiked", e.postId(), ex);
      }
  }
  ```
  - 저장은 `NotificationWriter`의 `@Transactional(propagation = REQUIRES_NEW)` 메서드가 한다. 리스너는 예외를 잡아 WARN만 남긴다. `fallbackExecution`은 기본값(false) — 트랜잭션 밖 발행은 버려진다(모든 발행이 업무 트랜잭션 안이라 해당 없음).
  - 로그에는 ID만 남긴다(이벤트에도 글자가 없다).
- **Rationale**: 20 §1·§2, spec Implementation Notes. 리스너 메서드에 `@Transactional`을 직접 붙이고 그 안에서 예외를 잡으면, 앞 SQL은 커밋되고 뒤 SQL만 빠진 채 끝날 수 있다. 나누면 실패한 알림 하나가 통째로 롤백된다.
- **Alternatives considered**: 공용 `AsyncUncaughtExceptionHandler`(001 `AsyncConfig`)에만 맡기기 — 예외는 잡히지만 로그에 이벤트 종류·대상 ID가 없다.

## R4. 공통 제외 규칙 (확정)

- **Decision**: `NotificationEligibility.check(type, receiverId, actorId, postId)`가 저장 전에 순서대로 확인한다. 하나라도 걸리면 저장하지 않는다(로그 없음 — 정상 경로).
  1. 본인 행동: `receiverId == actorId`
  2. 받는 사람 탈퇴 유예: `MemberQueryService.findAccessInfo(receiverId)`가 비었거나 `status = WITHDRAWN`
  3. 행동한 사람 탈퇴 유예: 같은 방법(`actorId`가 있을 때)
  4. 끈 종류: `notification_mute`에 `(receiverId, type)` 행 — 운영 알림 2종(`REPORT_RESOLVED`·`CONTENT_HIDDEN`)은 보지 않는다
  5. 글 읽기: `COMMENT`·`REPLY`·`LIKE`면 `PostReadService.isReadable(postId, receiverViewer)` — `receiverViewer = new Viewer(receiverId, role, status, emailVerified)`(2에서 읽은 값). `NEW_POST`는 받는 사람이 많아 R8의 비회원 기준 판정 1번으로 대신한다
- 처리 시점 최신 상태 확인(FR-006): 위 5개에 더해 종류별로 원래 상태가 아직 있는지 본다 — 댓글 `CommentQueryService.isActive(commentId)`(삭제·숨김 아님), 좋아요 `LikeQueryService.isLiked`, 팔로우 `FollowQueryService.isFollowing`. 없으면 저장하지 않는다. 그래서 "취소가 좋아요보다 먼저 처리"돼도 남는 알림이 없다(Edge Case 1).
- 정지 회원(`SUSPENDED`)은 따로 막지 않는다(spec Assumptions, 42 P-7).
- **Rationale**: 25 §4, 20 §4 공통 제외 규칙, FR-011.
- `PostReadService.isReadable(long postId, Viewer viewer)`는 004 `requireReadable`과 같은 판정을 예외 없이 `boolean`으로 돌려주는 새 공개 메서드다(없는 글 false).

## R5. 실행기 설정과 종료 (확정 + 제안)

- **Decision**:
  - `blog.async.event.queue-capacity: 1000`(지금 500), 코어 2·최대 4 그대로.
  - `CoreProperties.Pool`에 `Duration awaitTermination`(기본 `10s`)을 더하고 `blog.async.event.await-termination: 20s`. `AsyncConfig.executor`가 `setAwaitTerminationSeconds(pool.awaitTermination().toSeconds())`를 쓴다. 메일 실행기는 10초 그대로.
  - 종료 때 남은 작업 수: `AsyncConfig`가 만드는 실행기를 `ThreadPoolTaskExecutor` 하위 클래스(`ReportingTaskExecutor`)로 바꾸고 `shutdown()`에서 `super.shutdown()` 뒤 종료되지 않았으면 `queue.size() + activeCount`를 WARN으로 남긴다("비동기 실행기 종료 대기 시간 안에 끝내지 못한 작업: executor=blog-event-, remaining=N").
  - `server.shutdown: graceful`은 이미 있다. `spring.lifecycle.timeout-per-shutdown-phase`(기본 30초)는 20초보다 길어 그대로 둔다.
- **Rationale**: 20 §2-2 EV-2, FR-003, 검증 G11.
- **Alternatives considered**: 종료 대기 하드코딩 20초 — 헌법 VII.
- 001 소유 파일(`AsyncConfig`·`CoreProperties`)을 고친다. 기본값을 그대로 두면 다른 기능 동작은 바뀌지 않는다.

## R6. 댓글·답글 받는 사람 (확정 + 제안)

- **Decision**: `CommentCreated(commentId, postId, postAuthorId, authorId, parentId, parentAuthorId, replyToMemberId, createdAt)`로
  - `parentId == null`: `COMMENT` → `postAuthorId`.
  - `parentId != null`: 답글 대상 `R = replyToMemberId != null ? replyToMemberId : parentAuthorId`. `REPLY` → `R`. `COMMENT` → `postAuthorId`, 단 `postAuthorId == R`이면 만들지 않는다(답글 하나만, FR-010).
  - 각각 공통 제외 규칙(R4)을 따로 적용한다(예: `R`이 본인이면 `REPLY`만 빠지고 `COMMENT`는 간다).
  - 저장: `type`, `post_id`, `comment_id`, `last_actor_id = authorId`, `actor_count = 1`, `group_key = NULL`. `notification_actor`는 쓰지 않는다(하나짜리).
- **알려진 차이(팀 확인 T004)**: 007 R4는 "내 답글에 다시 단 답글"의 `reply_to_member_id`를 NULL로 둔다. 이때 이 규칙은 최상위 작성자에게 답글 알림을 보낸다. spec US1 #2 문장("답글에 답한 경우 최상위 댓글 작성자는 받지 않는다")은 남의 답글에 답한 경우를 말한다고 보고 이대로 둔다. 바꾸려면 007 이벤트에 `replyToCommentAuthorId`를 더해야 한다.
- **Rationale**: 20 §4-1, 007 contracts/events.md §1-1.

## R7. 좋아요·팔로우 묶음 저장 (확정)

- **Decision**: 한 트랜잭션(`REQUIRES_NEW`)에서
  1. 중복 확인: `SELECT 1 FROM notification_actor na JOIN notification n ON n.id = na.notification_id WHERE na.actor_id = :actor AND n.receiver_id = :receiver AND n.group_key = :groupKey [AND na.created_at > :now - :followDedupWindow] LIMIT 1` — LIKE는 그 글의 묶음 전체(읽음·안 읽음, 보관 기간 안), FOLLOW는 7일(`blog.notification.follow-dedup-window: 7d`). 있으면 끝.
  2. 안 읽은 묶음 확보: `INSERT INTO notification (receiver_id, type, post_id, group_key, actor_count) VALUES (…, 0) ON CONFLICT (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL DO UPDATE SET updated_at = notification.updated_at RETURNING id` — 새로 만들든 기존 것이든 그 행을 잠근다.
  3. `INSERT INTO notification_actor (notification_id, actor_id, created_at) VALUES (:id, :actor, :now) ON CONFLICT DO NOTHING RETURNING actor_id`
  4. 3에서 행이 왔으면 `UPDATE notification SET actor_count = actor_count + 1, last_actor_id = :actor, updated_at = :now WHERE id = :id`
- group_key: `LIKE:post:{postId}`, `FOLLOW`(`GroupKey` 값 객체). LIKE는 `post_id`도 채운다(FK CASCADE로 글 삭제 때 함께 사라짐). FOLLOW는 `post_id` NULL.
- 동시성: 2의 `ON CONFLICT DO UPDATE`가 행 잠금을 잡아 같은 묶음의 처리는 줄을 선다. 10명 동시 → 묶음 1개·인원 10(SC-002).
- **Rationale**: 25 §4-2, FR-013~FR-015.

## R8. 취소·언팔로우와 새 글 (확정 + 제안)

- **Decision (취소)**: `PostUnliked`·`MemberUnfollowed`
  1. `SELECT id FROM notification WHERE receiver_id = :r AND group_key = :gk AND read_at IS NULL FOR UPDATE` — 없으면 끝(읽은 묶음은 그대로)
  2. `DELETE FROM notification_actor WHERE notification_id = :id AND actor_id = :actor` — 0행이면 끝
  3. `UPDATE notification SET actor_count = (SELECT count(*) …), last_actor_id = (SELECT actor_id … ORDER BY created_at DESC, actor_id DESC LIMIT 1) WHERE id = :id` — `updated_at`은 바꾸지 않는다(목록 위로 올리지 않음)
  4. `DELETE FROM notification WHERE id = :id AND actor_count = 0`
  - 잠금 순서는 저장(R7)과 같다(알림 행 → 사람 행). 교착을 피한다.
  - 언팔로우는 알리지 않는다(FR-016). 좋아요 취소 처리의 공통 제외 규칙은 보지 않는다(빼는 일은 언제나 안전).
- **Decision (새 글)**: `PostWentPublic(postId, authorId, firstPublicAt)`
  1. 비회원 기준 `PostReadService.isReadable(postId, Viewer.anonymous())` — 처리 시점에 이미 비공개·휴지통·숨김·작성자 탈퇴 유예면 끝(FR-012). 공개 글만 이 사건이 생기므로 비회원 기준이 곧 "팔로워 전원이 읽을 수 있음"이다.
  2. 한 문장 `INSERT INTO notification (receiver_id, type, post_id, last_actor_id, actor_count, created_at, updated_at) SELECT f.follower_id, 'NEW_POST', :postId, :authorId, 1, :now, :now FROM follow f JOIN member r ON r.id = f.follower_id WHERE f.followee_id = :authorId AND r.withdrawn_at IS NULL AND NOT EXISTS (SELECT 1 FROM notification_mute m WHERE m.member_id = f.follower_id AND m.type = 'NEW_POST') AND NOT EXISTS (SELECT 1 FROM notification x WHERE x.receiver_id = f.follower_id AND x.type = 'NEW_POST' AND x.post_id = :postId)`
  - 마지막 `NOT EXISTS`는 같은 사건이 두 번 와도(재시도·중복 발행) 한 번만 넣기 위한 안전장치다(`ix_notification_post`).
- **Rationale**: 25 §4-1·§4-3, 20 §4-2, FR-016.

## R9. 대상 변화 (확정)

- **Decision**:
  | 사건 | 처리 |
  |---|---|
  | `CommentDeleted` | `DELETE FROM notification WHERE comment_id = :commentId AND type IN ('COMMENT','REPLY')` — 자리로 남은 댓글도 지운다. 행 DELETE면 CASCADE가 이미 지웠으므로 0행 |
  | `ContentHidden(COMMENT)` | 위와 같은 삭제 + 작성자(`ownerId`)에게 `CONTENT_HIDDEN`(`post_id`, `comment_id`) |
  | `ContentHidden(POST)` | 작성자에게 `CONTENT_HIDDEN`(`post_id`). 그 글의 다른 알림은 그대로(보여 줄 때 "볼 수 없는 글이에요") |
  | `ReportResolved` | 신고자에게 `REPORT_RESOLVED`(`report_id`, `result`). `post_id`·`comment_id`는 채우지 않는다(대상 정보 없음, 글 삭제와 무관하게 남음) |
  | `PostPurged`·댓글 행 삭제 | 구독하지 않음 — FK CASCADE |
  | `PostTrashed`·`PostRestored`·`PostVisibilityChanged`·`ContentUnhidden`·`MemberWithdrawn`·`MemberRestored`·`MemberSuspended` | 구독하지 않음 — 보여 줄 때 다시 판단(FR-018·FR-038) |
- 운영 알림 2종은 `last_actor_id = NULL`, `actor_count = 0`(관리자·신고자 정보 없음, FR-022). 끈 종류를 보지 않는다. 받는 사람 탈퇴 유예는 본다.
- 같은 대상을 숨김 → 해제 → 다시 숨김하면 숨김 알림이 두 개 생긴다(원문에 규칙 없음, 각 숨김이 별개 사건).
- **Rationale**: 25 §4-3, 006 contracts/events.md 알림 행, 014 spec Implementation Notes.

## R10. 목록 조회와 표시 (확정 + 제안)

- **Decision**: `NotificationListQueryRepository.page(receiverId, cursor, size)` SQL 1번(`size + 1`개 읽기):
  ```sql
  SELECT n.id, n.type, n.post_id, n.comment_id, n.result, n.actor_count, n.read_at, n.updated_at,
         a.handle AS actor_handle, a.nickname AS actor_nickname, a.withdrawn_at AS actor_withdrawn_at,
         a.deleted_at AS actor_deleted_at, COALESCE(ai.thumb_storage_key, ai.storage_key) AS actor_profile_key,
         p.title, p.author_id, p.status, p.visibility, p.deleted_at AS post_deleted_at,
         p.hidden_at AS post_hidden_at, p.hidden_reason AS post_hidden_reason,
         pm.handle AS post_author_handle, pm.withdrawn_at AS post_author_withdrawn_at,
         left(c.content, :previewScan) AS comment_head, c.hidden_at AS comment_hidden_at,
         c.hidden_reason AS comment_hidden_reason, me.handle AS receiver_handle
  FROM notification n
  JOIN member me ON me.id = n.receiver_id
  LEFT JOIN member a ON a.id = n.last_actor_id
  LEFT JOIN image ai ON ai.uploader_id = a.id AND ai.purpose = 'PROFILE'
                    AND ai.status = 'ATTACHED' AND ai.detached_at IS NULL   -- 005 카드 SQL과 같은 술어
  LEFT JOIN post p ON p.id = n.post_id
  LEFT JOIN member pm ON pm.id = p.author_id
  LEFT JOIN comment c ON c.id = n.comment_id
  WHERE n.receiver_id = :me [AND (n.updated_at, n.id) < (:t, :id)]
  ORDER BY n.updated_at DESC, n.id DESC
  LIMIT :size + 1
  ```
  - 커서가 있을 때만 조건을 붙인다(010 R6과 같은 이유 — JDBC NULL 타입).
  - 읽기 판정: 글이 있는 행마다 `new PostView(post_id, author_id, status, visibility, post_deleted_at, post_hidden_at, post_author_withdrawn_at)`를 만들어 `PostAccessPolicy.canRead(view, viewer)`(viewer = 세션 회원). 추가 SQL 없음.
  - 표시 규칙(`NotificationItemAssembler`):
    - 행동자: `actor_withdrawn_at` 또는 `actor_deleted_at`이 있으면 `{withdrawn: true}`, 아니면 `{handle, nickname, profileImageUrl}`. 운영 알림은 `actor = null`.
    - `othersCount = max(actor_count - 1, 0)`(묶음만, 하나짜리는 0).
    - 글: 읽을 수 있으면 `{title, url: "/@{post_author_handle}/posts/{post_id}"}`, 아니면 `{unavailable: true}`. 댓글 숨김 알림은 `post = null`(제목을 보여 주지 않음, FR-020).
    - 댓글 미리보기: 읽을 수 있을 때만 `comment_head`의 공백을 한 칸으로 줄이고 앞 50자(코드 포인트, `blog.notification.preview-length`) + 넘으면 "…". 서식으로 해석하지 않는다.
    - 숨김: `hidden = {targetType, stillHidden, reason}` — `stillHidden = 대상 hidden_at IS NOT NULL`, `reason`은 숨김 중일 때만 코드(`SPAM` 등).
    - 신고 결과: `report = {result}`.
    - 이동 주소 `url`(FR-024): 댓글·답글 `/@{h}/posts/{p}?comment={c}#comment-{c}`, 좋아요·새 글 `/@{h}/posts/{p}`, 팔로우 `/@{receiver_handle}/followers`, 글 숨김 글 주소, 댓글 숨김 댓글 위치 주소, 신고 결과 `null`. 글을 읽을 수 없으면 `null`.
  - 문장은 화면 `notificationText.ts`가 조립한다(서버 응답에 완성 문장 없음 — 닉네임·제목이 바뀌어도 문구 규칙은 한 곳).
- **Rationale**: 25 §2·§5, FR-019~FR-024·FR-032.
- **Alternatives considered**: 서버가 완성 문장을 주기 — 굵은 닉네임·제목 따옴표 같은 꾸밈을 화면에서 다시 쪼개야 한다.

## R11. API와 판정 순서 (확정 + 제안)

- **Decision**:
  | API | 판정 | 성공 |
  |---|---|---|
  | `GET /api/notifications/unread-count` | 401 → 403(001 게이트: 탈퇴 유예) | 200 `{count}` + `Cache-Control: private, no-store` |
  | `GET /api/notifications?cursor&size` | 401 → 403 → 400 `VALIDATION_FAILED`(`size`가 10·20 밖, 기본 20) → 400 `INVALID_CURSOR` | 200 `{items, nextCursor}` (`no-store`) |
  | `PUT /api/notifications/{id}/read` | 401 → 403(게이트, `ACCOUNT_WRITE` — 남은 세션 정지) → 404 | 204 (이미 읽음도 204) |
  | `POST /api/notifications/read-all` | 401 → 403 | 200 `{updated}` |
  | `DELETE /api/notifications/{id}` | 401 → 403 → 404 | 204 |
  | `GET /api/me/notification-settings` | 401 → 403 | 200 `{COMMENT, REPLY, LIKE, FOLLOW, NEW_POST}` |
  | `PUT /api/me/notification-settings` | 401 → 403 → 400 `VALIDATION_FAILED`(5개 모두 boolean 필수) | 200 같은 본문 |
  - 읽음: `UPDATE notification SET read_at = COALESCE(read_at, :now) WHERE id = :id AND receiver_id = :me RETURNING id` — 행이 없으면 404. 남의 번호·없는 번호·숫자가 아닌 번호는 같은 404 본문.
  - 모두 읽음: `UPDATE notification SET read_at = :now WHERE receiver_id = :me AND read_at IS NULL AND updated_at <= :now` → 바뀐 행 수(FR-031).
  - 삭제: `DELETE … WHERE id = :id AND receiver_id = :me` — 0행이면 404.
  - 설정 저장: `DELETE FROM notification_mute WHERE member_id = :me AND NOT (type = ANY(:muted))` + `INSERT … SELECT unnest(:muted) ON CONFLICT DO NOTHING`.
  - 바꾸는 요청은 CSRF(`X-XSRF-TOKEN`)를 요구한다(001). 이메일 인증 전 회원도 모두 쓸 수 있다(FR-034).
  - 안 읽은 수 요청도 세션 유지·최근 활동을 갱신한다(Clarifications Q2, 001 공통 그대로 — 예외 없음).
  - 요청 제한은 두지 않는다(원문에 없음, 30초 폴링은 화면이 지킨다).
- **Rationale**: 25 §5·§7, 42 §10-3, FR-033·FR-034, clarify 011 "읽음 처리는 PUT".

## R12. 커서 (확정)

- **Decision**: 001 `CursorCodec`, `ListScope.of("notifications")`, 키 `[updated_at 마이크로초, id]`. 다른 목록 커서·풀 수 없는 값은 400 `INVALID_CURSOR`. 펼침 목록(10개)은 커서를 쓰지 않는다.
- 보는 사이 묶음이 위로 올라가면 그 알림은 다음 페이지에 다시 오지 않는다(커서보다 위). 화면은 그래도 번호로 중복을 건너뛴다(FR-029, 005 `useCursorList`의 중복 제거).
- **Rationale**: 25 §5, 01 O8.

## R13. 정리 작업 (확정 + 제안)

- **Decision**: `NotificationCleanupJob` — `@Scheduled(cron = "${blog.notification.cleanup.cron}", zone = "${blog.notification.cleanup.zone}")`, 기본 `0 30 4 * * *` Asia/Seoul, `@SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT1H")`.
  1. 90일: `DELETE FROM notification WHERE id IN (SELECT id FROM notification WHERE updated_at < :cutoff ORDER BY updated_at LIMIT :batch)` 를 0행이 될 때까지 반복(묶음마다 트랜잭션, `ix_notification_cleanup`).
  2. 1,000개: 최근 하루 알림을 받은 사람만 고른다.
     ```sql
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
     WHERE n.receiver_id = c.receiver_id AND (n.updated_at, n.id) < (c.cut_at, c.cut_id)
     ```
     `:keep`보다 적은 사람은 `LATERAL`이 0행이라 빠진다. 비용은 그날 받은 사람 수 × `ix_notification_list` 탐색(FR-037).
  - 지운 개수를 INFO로 남긴다.
- 배치 시각: 015 03:00, 002·003·006 03:30, 009 04:10·04:20 다음인 04:30(겹치지 않게).
- **Rationale**: 25 §6, 검증 L34.

## R14. 탈퇴 정리 단계 (확정)

- **Decision**: `NotificationWithdrawalPurgeStep implements WithdrawalPurgeStep`, `order() = 70`(015 contracts/purge-steps.md §2). 015 단계 트랜잭션 안(`MANDATORY`)에서
  1. 받은 알림: `DELETE FROM notification WHERE receiver_id = :m`
  2. 남의 묶음에서 빼기 — 세 문장을 따로 실행(한 문장 `WITH … DELETE … UPDATE`는 UPDATE가 DELETE 전 상태를 봄, 검증 02 부록 E):
     - `SELECT id FROM notification WHERE id IN (SELECT notification_id FROM notification_actor WHERE actor_id = :m) ORDER BY id FOR UPDATE` → `ids`(저장·취소와 같은 잠금 순서)
     - `DELETE FROM notification_actor WHERE actor_id = :m`
     - `UPDATE notification n SET actor_count = (SELECT count(*) FROM notification_actor x WHERE x.notification_id = n.id), last_actor_id = (SELECT x.actor_id FROM notification_actor x WHERE x.notification_id = n.id ORDER BY x.created_at DESC, x.actor_id DESC LIMIT 1) WHERE n.id = ANY(:ids)`
     - `DELETE FROM notification WHERE id = ANY(:ids) AND actor_count = 0`
  3. 내가 행동한 하나짜리: `DELETE FROM notification WHERE last_actor_id = :m AND group_key IS NULL`(`ix_notification_last_actor`)
  4. 끄기 설정: `DELETE FROM notification_mute WHERE member_id = :m`
  - 10(글)·20(댓글) 다음이라 내 글·내 댓글에 달린 알림은 이미 CASCADE로 사라졌다.
- 복구하면 기존 알림이 그대로 돌아온다(유예 중에는 지우지 않음, 표시만 "탈퇴한 사용자").
- **Rationale**: 25 §8, 44 §4, FR-039, 015 FR-025 8번.

## R15. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/notification.csv`(004 하네스 형식, owner `011`). 대상은 "행위자 A의 알림"과 "다른 회원 B의 알림" 두 가지.
  | action | ANONYMOUS | UNVERIFIED | MEMBER(자기 알림) | 다른 회원(B의 알림) | ADMIN(B의 알림) | SUSPENDED | WITHDRAWN |
  |---|---|---|---|---|---|---|---|
  | `notification.unread-count` | 401 | 200 | 200 | — | 200(자기 수) | 200(읽기) | 403 `ACCOUNT_WITHDRAWN` |
  | `notification.list` | 401 | 200 | 200 | — | 200(자기 것만) | 200(읽기) | 403 |
  | `notification.read` | 401 | 204 | 204 | 404 | 404 | 403 `ACCOUNT_SUSPENDED` | 403 |
  | `notification.read-all` | 401 | 200 | 200 | — | 200(자기 것만) | 403 | 403 |
  | `notification.delete` | 401 | 204 | 204 | 404 | 404 | 403 | 403 |
  | `notification.settings.get` | 401 | 200 | 200 | — | 200 | 200(읽기) | 403 |
  | `notification.settings.put` | 401 | 200 | 200 | — | 200 | 403 | 403 |
- 정지 행위자는 "로그인 뒤 DB에서 정지로 바꾼 남은 세션"(004 하네스 정의). 읽기는 가드를 부르지 않는다.
- **Rationale**: 헌법 III, 42 §10-3·§12 기준 9, FR-033·FR-034.

## R16. 화면 (확정 + 제안)

- **Decision**:
  - `useUnreadCount`: 로그인했을 때만. 마운트 즉시 1번, 30초(`NOTIFICATION_POLL_MS = 30_000`)마다. `document.visibilityState === 'hidden'`이면 타이머를 멈추고, `visible`이 되면 즉시 1번 + 타이머 재개. 실패는 조용히 무시하고 마지막 값 유지. 읽음·모두 읽음·삭제 뒤에는 로컬 값을 바로 줄이고 다음 확인에서 맞춘다.
  - `NotificationBell`: `<button aria-label="안 읽은 알림 N개" aria-haspopup="true" aria-expanded>` + 배지(0이면 숨김, 100 이상 "99+"). 0개면 이름 "알림".
  - `NotificationDropdown`: 열 때마다 `GET /api/notifications?size=10`. "불러오는 중…" → 목록 / "새 알림이 없어요" / "알림을 불러오지 못했어요 [다시 시도]"(배지는 그대로). 아래 [모두 읽음]·[모든 알림 보기](`/notifications`). Esc·바깥 누르기로 닫고 초점을 종으로 돌린다.
  - `NotificationItem`: 안 읽음은 ● + `<strong>`. 누르면 `PUT …/read`(실패해도 이동은 함) 뒤 `url`이 있으면 이동, 없으면 그 자리에서 읽음 표시만 바꾼다(Clarifications Q3).
  - `notificationText.ts` 문구(25 §2):
    - 댓글 "**{닉네임}**님이 「{제목}」에 댓글을 남겼어요: "{미리보기}""
    - 답글 "**{닉네임}**님이 회원님의 댓글에 답글을 남겼어요: "{미리보기}""
    - 좋아요 "**{닉네임}**님이 「{제목}」을 좋아해요" / 묶음 "**{닉네임}**님 외 {N}명이 「{제목}」을 좋아해요"
    - 팔로우 "**{닉네임}**님이 회원님을 팔로우해요" / 묶음 "**{닉네임}**님 외 {N}명이 회원님을 팔로우해요"
    - 새 글 "**{닉네임}**님이 새 글을 올렸어요: 「{제목}」"
    - 신고 결과 "신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요" / "신고하신 내용을 검토했지만 운영 정책 위반은 아니었어요"
    - 글 숨김 "회원님의 글「{제목}」이(가) 운영 정책에 따라 숨겨졌어요 (사유: {사유})" / 해제됨 "…숨겨졌었어요 (지금은 다시 보여요)"
    - 댓글 숨김 "회원님의 댓글이 운영 정책에 따라 숨겨졌어요 (사유: {사유})" / 해제됨 같은 방식
    - 볼 수 없는 글: 제목 자리에 "볼 수 없는 글이에요", 미리보기 없음. 행동자 탈퇴: 닉네임 자리에 "탈퇴한 사용자"(굵게 하지 않음).
  - 시각: 005 `relativeText(updatedAt)`("방금 전"·"N분 전"·"N시간 전"·`2026.10.02`) + `<time dateTime>`. spec FR-021에 "방금 전"은 없지만 005 공용 규칙(1분 미만)을 따른다.
  - `/notifications`: 005 `useCursorList` + `LoadMoreButton`(20개), 항목마다 [×](`aria-label="알림 삭제"`), 지우면 목록에서 바로 뺀다. 상단에 [모두 읽음]. 로그인 전용(비회원은 로그인 화면으로).
  - 설정 "알림" 칸: 5개 스위치(`role="switch"`), 바꿀 때마다 `PUT`(마지막 상태 전송), 아래 "운영 알림(신고 결과·숨김)은 끌 수 없어요". 001 설정 화면(T122)이 없으면 `/settings` 자리에 칸만 붙인다(001 T122 머지 후 옮김).
  - 사유 이름: 014 Clarifications의 6개(스팸·광고 / 욕설·혐오 / 음란·선정 / 개인정보 노출 / 저작권 침해 / 기타). `reasonLabels.ts`는 014와 공유(먼저 하는 쪽이 만듦).
- **Rationale**: 25 §2·§3·§7, FR-025~FR-031·FR-035, 헌법 IV(텍스트 노드).

## R17. 설정값 (제안)

```yaml
blog:
  async:
    event:
      queue-capacity: 1000       # 지금 500 (FR-003)
      await-termination: 20s     # 새 필드, 메일은 기본 10s
  notification:
    retention: 90d               # FR-037
    max-per-member: 1000
    cleanup:
      cron: "0 30 4 * * *"
      zone: Asia/Seoul
      batch-size: 1000
      recent-window: 1d          # 1,000개 정리 대상 고르기
    follow-dedup-window: 7d      # FR-014
    preview-length: 50           # FR-021 (코드 포인트)
    preview-scan: 400            # SQL에서 앞부분만 읽는 길이
    dropdown-size: 10
    page-size: 20
```

- 화면의 30초는 `frontend/src/features/notification/useUnreadCount.ts` 상수(서버 설정이 아님).
