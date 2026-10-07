# 인앱 알림 설계

> 작성일 2026-10-04 · 수정 2026-10-06 (검증 L2·L11·L26·L29·L34·04 L-43, 회의 O2 반영) · 작성 강성찬 · 관련: 인앱 알림(Tier C). 연관: [20 이벤트](./20-domain-events.md)(알림 종류·받는 사람·묶기의 원칙), [21 댓글](./21-comment.md), [24 팔로우](./24-follow-feed.md), [06 공개 범위](./06-visibility.md), [13 탈퇴](./13-delete-withdraw.md)
> 이미 정해진 것(01 Tier C, 20): **앱 안 알림만**, 본인 행동은 알리지 않음, 이벤트 기반(커밋 후 비동기 리스너, 유실 허용), 이벤트에는 ID만, 알림 종류 7가지, 좋아요는 글마다 묶음, 새 팔로워는 안 읽은 동안 묶음, 새 글 알림은 처음 전체 공개될 때 한 번.

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| NT-1 | 화면 갱신 | **페이지를 열 때 + 30초마다 안 읽은 수만 확인** (탭이 가려지면 멈춤) | SSR·SPA 모두 같은 방식. 연결을 붙잡지 않아 서버가 여러 대여도 추가 장치가 없다 |
| NT-2 | 읽음 | **알림을 누르면 그 알림만 읽음** + [모두 읽음] 버튼 | 안 누른 알림은 표시가 남아 놓치지 않는다 |
| NT-3 | 보관 | **90일, 사람당 최대 1,000개.** 매일 새벽 정리 | |
| NT-4 | 끄기 | **종류별 켜기·끄기** (댓글·답글·좋아요·새 팔로워·새 글). 운영 알림(신고 처리 결과·숨김)은 끌 수 없다 | 부담을 줄이는 서비스 방향. 운영 알림은 본인에게 꼭 필요한 정보 |
| NT-5 | 화면에 보여줄 내용 | 알림에는 ID만 저장하고, **보여줄 때 다시 조회**한다. 받는 사람이 그 글을 지금 볼 수 없으면 제목·내용 없이 **"볼 수 없는 글이에요"** | 20 EV-3. 비공개로 바꾼 글의 제목이 알림에 남지 않는다 |
| NT-6 | 저장 구조 | 대상마다 **nullable FK 컬럼**(`post_id`, `comment_id`)을 두고 `ON DELETE CASCADE` | 03 §5 자리의 `target_type`/`target_id`(다형 참조) 대신. 글·댓글이 완전히 지워지면 알림도 DB가 함께 지운다 (20 §4-2 `PostPurged`를 리스너 없이 처리) |
| NT-7 | 묶음 구조 | 묶는 알림(좋아요·팔로우)은 `group_key` + 행동한 사람 목록(`notification_actor`) | "외 N명" 계산, 취소한 사람 빼기, 같은 사람 중복 막기 |
| NT-8 | 좋아요 중복 | **같은 사람이 같은 글에 누른 좋아요는 한 번만 알린다** (보관 기간 안에서) | 취소했다 다시 눌러도 알림이 늘지 않는다 (20에서 고른 묶음 방식의 조건) |
| NT-9 | 팔로우·친구 요청 중복 | **같은 사람의 `FOLLOW`는 7일에 한 번.** 친구 공개 규격 적용자는 **같은 사람의 `FRIEND_REQUEST`도 7일에 한 번** | 팔로우에 제한이 없으므로(24 F-5) 알림 쪽에서 도배를 막는다. 친구 요청도 취소·재요청을 반복하면 알림이 매번 생긴다 (검증 L26) |

---

## 2. 알림 종류와 문구

| 종류 | 문구 (예) | 누르면 이동 | 끌 수 있음 |
|---|---|---|---|
| `COMMENT` | **김민서**님이 「JPA N+1 정리」에 댓글을 남겼어요: "좋은 글이네요…" | 글 상세의 그 댓글 (`/@주소/posts/{글}?comment={id}#comment-{id}`, 21 §6) | ✓ |
| `REPLY` | **나민서**님이 회원님의 댓글에 답글을 남겼어요: "batch size를…" | 그 답글 (같은 형식) | ✓ |
| `LIKE` | **김민서**님 외 3명이 「JPA N+1 정리」을(를) 좋아해요 | 글 상세 | ✓ |
| `FOLLOW` | **나민서**님 외 2명이 회원님을 팔로우해요 | 내 팔로워 목록 (`/@내주소/followers`) | ✓ |
| `NEW_POST` | **김민서**님이 새 글을 올렸어요: 「Spring Security 정리」 | 글 상세 | ✓ |
| `REPORT_RESOLVED` | 결과 `ACTION_TAKEN`: "신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요" / `NO_VIOLATION`: "신고하신 내용을 검토했지만 운영 정책 위반은 아니었어요" | 없음 (신고 대상의 내용·작성자를 보여주지 않음) | ✗ |
| `CONTENT_HIDDEN` | 회원님의 글「…」이(가) 운영 정책에 따라 숨겨졌어요 (사유: 스팸·광고) / 회원님의 댓글이 운영 정책에 따라 숨겨졌어요 (사유: 욕설·혐오) | 숨겨진 글·댓글 (작성자 본인은 볼 수 있음, 21 CM-9) | ✗ |
| (규격 적용자) `FRIEND_REQUEST`·`FRIEND_ACCEPTED` | ○○님이 친구 요청을 보냈어요 / ○○님이 친구 요청을 수락했어요 | 받은 친구 요청 / 그 사람의 블로그 | ✓ |

| 보여줄 때 규칙 | 내용 |
|---|---|
| 이름 | 지금 닉네임. 행동한 사람이 탈퇴 신청했거나 익명 처리됐으면 "탈퇴한 사용자" |
| 글 제목 | 지금 제목. 받는 사람이 그 글을 **지금** 읽을 수 없으면(비공개·휴지통·숨김·작성자 탈퇴 신청) 제목·댓글 내용을 빼고 "볼 수 없는 글이에요", 이동 링크 없음 (NT-5). 예외: 글 숨김 `CONTENT_HIDDEN`은 받는 사람이 그 글의 작성자라 숨겨진 상태여도 제목을 보여준다. 댓글 숨김 `CONTENT_HIDDEN`은 글 제목을 보여주지 않는다 |
| 댓글 미리보기 | 앞 50자, 글자만(HTML 이스케이프). 그 사이 수정됐으면 지금 내용 |
| 시각 | `updated_at` 기준, 10 L-5와 같은 형식 (1시간 안 "N분 전", 24시간 안 "N시간 전", 그 뒤 `2026.10.02`) |
| 관리자·신고자 | **어느 알림에도 보여주지 않는다** |
| 숨김 사유 (`CONTENT_HIDDEN`) | 보여줄 때 대상의 `hidden_reason`(43 H-2 사유 코드)을 읽어 "(사유: 스팸·광고)"로 붙인다. 이벤트·알림 행에는 사유를 저장하지 않는다 (20 EV-3). **그 사이 숨김이 해제됐으면** 사유 없이 "…숨겨졌었어요 (지금은 다시 보여요)" (검증 L11) |

---

## 3. 화면

```
상단 메뉴                                   🔔 3        ← 안 읽은 알림 수 (99개 넘으면 99+)
                                       ┌──────────────────────────────────┐
                                       │ 알림                  [모두 읽음] │
                                       │ ● 김민서님 외 3명이 「JPA…」을     │
                                       │   좋아해요 · 3분 전                │
                                       │ ● 나민서님이 회원님의 댓글에 …      │
                                       │   볼 수 없는 글이에요 · 5시간 전    │   ← 그 사이 비공개가 된 글
                                       │   김민서님이 새 글을 올렸어요 …     │   ← 읽은 알림은 ● 없음
                                       │              [모든 알림 보기]      │
                                       └──────────────────────────────────┘
```

| 항목 | 규칙 |
|---|---|
| 종 아이콘 | 로그인한 경우만. 안 읽은 수 배지, 화면 낭독기용 이름 "안 읽은 알림 3개" |
| 펼침 목록 | 최근 10개 + [모든 알림 보기] → `/notifications` |
| 전체 페이지 (`/notifications`) | 20개씩 [더 보기], 각 알림에 [×](삭제) |
| 안 읽음 표시 | ● + 굵은 글자 (색만으로 구분하지 않음) |
| 누르면 | 그 알림을 읽음으로 바꾸고 이동 |
| 빈 상태 | "새 알림이 없어요" |
| 펼침 목록 불러오는 중 | 목록 자리에 "불러오는 중…" (종을 누를 때마다 다시 불러온다) |
| 펼침 목록 실패 | "알림을 불러오지 못했어요 [다시 시도]". 배지 숫자는 그대로 둔다 |
| 전체 페이지 [더 보기] | 10 §4-4와 같다: "불러오는 중…" 비활성화 / "불러오지 못했어요 [다시 시도]" |
| 안 읽은 수 확인 실패 | 조용히 넘어가고 다음 30초에 다시. 배지는 마지막 값 유지 |
| 설정 | `/settings`의 "알림" 칸 (§7) |

---

## 4. 저장 (이벤트 → 알림)

리스너는 20 §2-2 규칙(커밋 후·비동기·새 트랜잭션·실패해도 원래 요청 성공)을 따른다. 알림을 만들기 전에 항상 아래를 확인한다.

```
① 본인 행동이면 끝               (actorId == receiverId)
② 받는 사람이 탈퇴 신청 상태면 끝
③ 행동한 사람이 탈퇴 신청 상태면 끝
④ 받는 사람이 그 종류를 껐으면 끝   (notification_mute, 운영 알림은 확인하지 않음)
⑤ COMMENT·REPLY·LIKE·NEW_POST는 받는 사람이 지금 그 글을 읽을 수 있어야 함 (PostAccessPolicy.canRead, 20 §4 3번)
⑥ 종류별 저장 (아래)
```

### 4-1. 하나씩 저장하는 알림 (`COMMENT`, `REPLY`, `NEW_POST`, `REPORT_RESOLVED`, `CONTENT_HIDDEN`)

| 종류 | 저장 값 |
|---|---|
| `COMMENT`·`REPLY` | `post_id`, `comment_id`, `last_actor_id` = 댓글 작성자, `actor_count` = 1. 받는 사람은 20 §4-1 표 |
| `NEW_POST` | `post_id`, `last_actor_id` = 글 작성자. 팔로워 전원에게 **한 문장으로** 넣는다 (아래) |
| `REPORT_RESOLVED` | `report_id`, `result`, 행동한 사람 없음 |
| `CONTENT_HIDDEN` | `post_id`(글 숨김) 또는 `post_id` + `comment_id`(댓글 숨김), 행동한 사람 없음 |

```sql
-- NEW_POST: 팔로워에게 한 번에 (①~④를 조건으로)
INSERT INTO notification (receiver_id, type, post_id, last_actor_id, actor_count)
SELECT f.follower_id, 'NEW_POST', :postId, :authorId, 1
FROM follow f
JOIN member r ON r.id = f.follower_id
WHERE f.followee_id = :authorId
  AND r.withdrawn_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM notification_mute nm WHERE nm.member_id = f.follower_id AND nm.type = 'NEW_POST');
```

`PostWentPublic`은 글이 `PUBLIC`이 된 순간이므로 ⑤는 항상 통과한다. 다만 리스너가 실행될 때 글이 이미 비공개로 바뀌었으면 넣지 않는다 (처리 시점에 한 번 확인).

### 4-2. 묶는 알림 (`LIKE`, `FOLLOW`)

| 종류 | `group_key` | 묶이는 범위 | 중복 막기 |
|---|---|---|---|
| `LIKE` | `LIKE:post:{postId}` | 그 글에 대한 **안 읽은** 좋아요 알림 하나 | 같은 사람이 이 글로 이미 알림에 들어간 적이 있으면(읽었든 안 읽었든) 넣지 않는다 (NT-8) |
| `FOLLOW` | `FOLLOW` | 나에 대한 **안 읽은** 팔로우 알림 하나 | 같은 사람이 7일 안에 들어간 적이 있으면 넣지 않는다 (NT-9) |

```
① 중복 확인: notification_actor에 이 사람이 있는지 (위 표의 범위)
② 안 읽은 묶음을 찾거나 만든다:
   INSERT … ON CONFLICT (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
   DO UPDATE SET updated_at = notification.updated_at   ← 아무것도 바꾸지 않고 id만 받는다
   RETURNING id
③ INSERT INTO notification_actor (notification_id, actor_id) … ON CONFLICT DO NOTHING RETURNING actor_id
④ ③에서 행이 생겼으면: actor_count + 1, last_actor_id = 이 사람, updated_at = now()  → 목록 맨 위로
```

- 읽은 뒤에 새 좋아요가 오면 **새 묶음**이 생긴다 (②의 유일 인덱스가 안 읽은 묶음만 대상).
- 같은 글의 좋아요가 동시에 여러 개 와도 ②의 유일 인덱스와 ③의 PK 덕분에 묶음은 하나, 사람은 한 번씩만 들어간다.

### 4-3. 취소·삭제 이벤트 (20 §4-2)

| 이벤트 | 처리 |
|---|---|
| `PostUnliked` | **안 읽은** `LIKE` 묶음에서 그 사람의 `notification_actor` 행을 지우고 `actor_count − 1`, `last_actor_id`는 남은 사람 중 가장 최근. 0명이 되면 알림 삭제. 읽은 묶음은 그대로 |
| `MemberUnfollowed` | **안 읽은** `FOLLOW` 묶음에서 같은 방식으로 뺀다 |
| `CommentDeleted` | 행이 지워진 댓글은 FK `CASCADE`로 알림도 지워진다. "삭제된 댓글"로 자리만 남은 경우(21 CM-8)는 리스너가 `comment_id`로 알림을 지운다 |
| `ContentHidden` (댓글) | 그 댓글로 생긴 `COMMENT`·`REPLY` 알림 삭제 |
| `PostPurged` | FK `CASCADE` (리스너 불필요) |
| `PostTrashed`·`PostVisibilityChanged` | 아무것도 하지 않는다. 보여줄 때 "볼 수 없는 글이에요" (NT-5) |
| `PostRestored`·`ContentUnhidden` | 아무것도 하지 않는다. 지운 알림을 되살리지 않고, 남아 있는 알림은 보여줄 때 다시 제목이 보인다 |

---

## 5. 조회·읽음 API

| 요청 | 동작 | 응답 |
|---|---|---|
| `GET /api/notifications/unread-count` | 안 읽은 수 | `{ "count": 3 }`, `Cache-Control: no-store` |
| `GET /api/notifications?cursor=…&size=10` | 목록 (펼침 목록 10, 전체 페이지 20) | 아래 |
| `PATCH /api/notifications/{id}/read` | 하나 읽음 | `204` |
| `POST /api/notifications/read-all` | 모두 읽음 (지금 시각까지의 안 읽은 알림) | `{ "updated": 12 }` |
| `DELETE /api/notifications/{id}` | 하나 삭제 | `204` |

```json
{ "items": [
    { "id": 901, "type": "LIKE", "read": false, "updatedAt": "2026-10-04T12:03:00Z",
      "actor": { "nickname": "김민서", "handle": "kim755030", "profileImageUrl": null },
      "othersCount": 3,
      "post": { "title": "JPA N+1 정리", "url": "/@eueu2/posts/42" },     ← 읽을 수 없으면 "post": { "unavailable": true }
      "url": "/@eueu2/posts/42" } ],
  "nextCursor": "…" }
```

| 규칙 | 내용 |
|---|---|
| 권한 | 받는 사람 본인만. 남의 알림 ID는 404 |
| 계정 상태 (42 §3·§4, 검증 04 L-43) | 비회원 401 `LOGIN_REQUIRED`. 탈퇴 유예 회원 403 `ACCOUNT_WITHDRAWN` (복구 화면). **이메일 인증 전에도 쓸 수 있다** — 알림 보기·읽음·삭제·설정은 남에게 보이는 쓰기가 아니라서 `EMAIL_NOT_VERIFIED`를 쓰지 않는다 |
| 정렬 | `updated_at DESC, id DESC` (묶음에 사람이 더해지면 맨 위로) |
| 커서 | `(updated_at, id)`. 보는 사이 묶음이 위로 올라가면 같은 알림이 다시 올 수 있으므로 **브라우저가 이미 있는 ID는 건너뛴다** (10 §4-3 이중 안전장치와 같음) |
| 쿼리 수 | 목록 1번(알림 + 행동한 사람 + 글 제목 JOIN) + 읽기 권한 판정은 그 결과로 한꺼번에. 알림마다 따로 조회하지 않는다 |
| 폴링 | 30초마다 `unread-count`만. `visibilitychange`로 탭이 가려지면 멈추고, 다시 보이면 바로 한 번 |
| SSR | 페이지를 그릴 때 배지 숫자를 함께 그리고, 같은 작은 스크립트로 30초 폴링 |

---

## 6. 보관·정리

| 배치 | 규칙 |
|---|---|
| 90일 | 매일 새벽, `updated_at < now() - 90일`인 알림을 1,000개씩 삭제 (ShedLock) |
| 1,000개 | 같은 배치에서, **최근 하루 동안 알림이 생긴 받는 사람만** 골라 각자 최신 1,000개를 넘는 오래된 알림을 지운다 (아래 SQL). 알림이 늘지 않은 사람은 볼 필요가 없으므로 전체 표를 창 함수로 훑지 않는다 (검증 L34) |
| `notification_actor` | 알림이 지워지면 `CASCADE` |

```sql
-- 1,000개 정리: 오늘 알림이 생긴 사람마다 1,000번째보다 오래된 것 삭제 (1,000개 미만이면 하위 쿼리가 NULL → 지우지 않음)
DELETE FROM notification n
USING (SELECT DISTINCT receiver_id FROM notification
       WHERE updated_at > now() - interval '1 day') r          -- ix_notification_cleanup
WHERE n.receiver_id = r.receiver_id
  AND (n.updated_at, n.id) < (SELECT x.updated_at, x.id FROM notification x   -- ix_notification_list
                              WHERE x.receiver_id = r.receiver_id
                              ORDER BY x.updated_at DESC, x.id DESC OFFSET 999 LIMIT 1);
```

버티는 규모: 비용은 "하루에 알림을 받은 사람 수 × 1,000번째 위치 찾기"에 비례하고 전체 알림 수와는 무관하다.

---

## 7. 알림 설정

```
┌ 알림 ───────────────────────────────────────┐
│ 내 글에 달린 댓글            [켜짐]            │
│ 내 댓글에 달린 답글          [켜짐]            │
│ 좋아요                       [꺼짐]            │
│ 새 팔로워                    [켜짐]            │
│ 팔로우한 사람의 새 글         [켜짐]            │
│ 운영 알림(신고 결과·숨김)은 끌 수 없어요         │
└──────────────────────────────────────────────┘
```

| 항목 | 규칙 |
|---|---|
| 기본값 | 모두 켜짐 |
| 저장 | **끈 종류만** `notification_mute`에 행으로 (행이 없으면 켜짐) |
| API | `GET /api/me/notification-settings` → `{ "COMMENT": true, "REPLY": true, "LIKE": false, "FOLLOW": true, "NEW_POST": true }`, `PUT` 같은 형식 |
| 끄면 | 그 종류의 **새 알림**만 생기지 않는다. 이미 받은 알림은 그대로 |
| 특정 사람의 새 글만 끄기 | 개인 확장 |

---

## 8. 회원 탈퇴

| 시점 | 처리 |
|---|---|
| 유예 중 | 그 사람에게 새 알림을 만들지 않고(§4 ②), 그 사람의 행동으로도 만들지 않는다(③). 이미 있는 알림에서 그 사람은 "탈퇴한 사용자"로 보인다 |
| 복구 | 그대로 돌아온다 |
| 30일 뒤 (13 §3-3에 단계 추가) | ① 내가 받은 알림 전부 삭제 ② 남의 묶음 알림에서 내 `notification_actor` 행 삭제 → `actor_count`·`last_actor_id` 다시 계산, 0명이면 알림 삭제 ③ 내가 행동한 하나짜리 알림(`last_actor_id = 나 AND group_key IS NULL`, `ix_notification_last_actor`)은 삭제. 내 글·댓글에 걸린 알림은 앞 단계(글·댓글 삭제)에서 `CASCADE`로 이미 지워진다 |
| 구현 주의 | ②를 `WITH … DELETE … UPDATE` 한 문장으로 쓰면 UPDATE가 DELETE 전 상태를 봐서 다시 계산이 안 된다. **DELETE와 UPDATE를 따로** 실행한다 (검증 02 부록 E 재현) |

강성찬이 `NotificationWithdrawalPurgeStep`으로 구현하고, 13 §3-3에서 **글·댓글 단계 다음**에 실행한다.

---

## 9. 공통 완료 기준

| # | 기준 | 확인 방법 |
|---|---|---|
| 1 | 본인 행동으로는 알림이 생기지 않는다 | 내 글에 내 댓글·좋아요 |
| 2 | 여러 사람이 좋아요를 누르면 안 읽은 알림 하나에 "외 N명"으로 묶이고, 취소하면 빠지고, 같은 사람이 취소·좋아요를 반복해도 알림이 늘지 않는다 | 동시 좋아요 10건, 취소·재클릭 5회 |
| 3 | 같은 사람이 팔로우·언팔로우를 반복해도 `FOLLOW` 알림은 7일에 한 번이다 | |
| 4 | 받는 사람이 볼 수 없는 글에 대한 알림은 만들어지지 않고, 나중에 볼 수 없게 되면 제목·내용 없이 "볼 수 없는 글이에요"로 보인다 | 06 §8 권한 매트릭스에 "알림" 열 |
| 5 | 종류를 끄면 그 종류의 새 알림이 생기지 않고, 운영 알림은 끌 수 없다 | |
| 6 | 안 읽은 수가 실제와 같고, 누른 알림·[모두 읽음]이 읽음으로 바뀐다 | |
| 7 | 남의 알림은 읽기·읽음 처리·삭제가 모두 404다 | |
| 8 | 90일이 지나거나 1,000개를 넘은 알림은 정리된다 | |
| 9 | 글이 완전히 삭제되거나 댓글이 지워지면 관련 알림도 사라진다 | |
| 10 | 신고 처리·숨김 알림에 신고자·관리자 정보가 없다 | 응답 필드 검사 |
| 11 | 알림 저장에 실패해도 댓글·좋아요·팔로우·발행은 성공한다 | 20 §7 3번 |

---

## 10. ERD 변경 제안

03 §5의 `notification` 자리를 아래로 확정한다 (Tier C → V2 마이그레이션). **`target_type`/`target_id` 대신 대상별 FK 컬럼**을 쓴다 (NT-6).

```sql
-- V1 반영됨 (2026-10-07): 이 SQL은 erd/V1__common_schema.sql에 들어 있다. 기록용으로 남긴다.
-- V2__notification.sql
CREATE TABLE notification (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receiver_id   bigint       NOT NULL REFERENCES member (id),
    type          varchar(30)  NOT NULL,
    post_id       bigint       REFERENCES post (id)    ON DELETE CASCADE,
    comment_id    bigint       REFERENCES comment (id) ON DELETE CASCADE,
    report_id     bigint,                 -- FK fk_notification_report → report(id) ON DELETE SET NULL은 통합 V1에 반영됨 (회의 O2)
    result        varchar(20),            -- REPORT_RESOLVED 결과
    last_actor_id bigint       REFERENCES member (id),
    actor_count   integer      NOT NULL DEFAULT 0,
    group_key     varchar(100),           -- 묶는 알림만 (LIKE, FOLLOW)
    read_at       timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_type   CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST',
                                                      'REPORT_RESOLVED', 'CONTENT_HIDDEN')),
    CONSTRAINT ck_notification_group  CHECK ((type IN ('LIKE', 'FOLLOW')) = (group_key IS NOT NULL)),
    CONSTRAINT ck_notification_result CHECK ((type = 'REPORT_RESOLVED') = (result IS NOT NULL)
                                             AND (result IS NULL OR result IN ('ACTION_TAKEN', 'NO_VIOLATION'))),
    CONSTRAINT ck_notification_count  CHECK (actor_count >= 0)
);
-- 안 읽은 묶음은 받는 사람·종류마다 하나
CREATE UNIQUE INDEX uq_notification_unread_group ON notification (receiver_id, group_key)
    WHERE read_at IS NULL AND group_key IS NOT NULL;
CREATE INDEX ix_notification_list    ON notification (receiver_id, updated_at DESC, id DESC);
CREATE INDEX ix_notification_unread  ON notification (receiver_id) WHERE read_at IS NULL;
CREATE INDEX ix_notification_post    ON notification (post_id)    WHERE post_id IS NOT NULL;     -- CASCADE 속도
CREATE INDEX ix_notification_comment ON notification (comment_id) WHERE comment_id IS NOT NULL;  -- CASCADE 속도
CREATE INDEX ix_notification_cleanup ON notification (updated_at);
-- 탈퇴 정리 ③: 그 회원이 행동한 하나짜리 알림 (검증 L2)
CREATE INDEX ix_notification_last_actor ON notification (last_actor_id) WHERE group_key IS NULL;

-- 묶는 알림에 들어간 사람
CREATE TABLE notification_actor (
    notification_id bigint      NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    actor_id        bigint      NOT NULL REFERENCES member (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, actor_id)
);
CREATE INDEX ix_notification_actor_actor ON notification_actor (actor_id, created_at DESC);

-- 끈 알림 종류 (행이 없으면 켜짐)
CREATE TABLE notification_mute (
    member_id  bigint      NOT NULL REFERENCES member (id),
    type       varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, type),
    CONSTRAINT ck_notification_mute_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST'))
);
```

친구 공개 규격 적용자는 `ck_notification_type`·`ck_notification_mute_type`에 `FRIEND_REQUEST`, `FRIEND_ACCEPTED`를 추가한다 (CHECK 교체만, 03 E-10).

---

## 11. 결정 기록 추가분

| 날짜 | 안건 | 결정 |
|---|---|---|
| 2026-10-04 | 알림 화면 갱신 | 페이지를 열 때 + 30초마다 안 읽은 수만 확인(탭이 가려지면 멈춤). 실시간 연결 없음 |
| 2026-10-04 | 알림 읽음 | 누르면 그 알림만 + [모두 읽음] |
| 2026-10-04 | 알림 보관 | 90일, 사람당 1,000개, 매일 정리 |
| 2026-10-04 | 알림 끄기 | 종류별(댓글·답글·좋아요·새 팔로워·새 글), 운영 알림은 끌 수 없음 |
| 2026-10-04 | 알림 표시 | 보여줄 때 다시 조회. 볼 수 없는 글은 "볼 수 없는 글이에요", 탈퇴자는 "탈퇴한 사용자", 신고자·관리자는 표시하지 않음 |
| 2026-10-04 | 알림 저장 구조 | 대상별 FK(`post_id`, `comment_id`, CASCADE) + 묶음은 `group_key` + `notification_actor`. 03 §5의 `target_type`/`target_id` 대체 |
| 2026-10-04 | 알림 중복 | 같은 사람·같은 글 좋아요는 한 번, 같은 사람의 팔로우는 7일에 한 번 |
| 2026-10-06 | 친구 요청 알림 중복 (검증 L26) | 규격 적용자: 같은 사람의 `FRIEND_REQUEST`도 7일에 한 번 |
| 2026-10-06 | 숨김 알림 사유 (검증 L11) | 보여줄 때 `hidden_reason`을 읽어 "(사유: …)". 해제됐으면 사유 없이 "지금은 다시 보여요" |
| 2026-10-06 | 알림 1,000개 정리 (검증 L34) | 하루 동안 알림이 생긴 사람만 대상 |
| 2026-10-06 | 알림 API 계정 상태 (검증 04 L-43) | 401 / 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`. 이메일 인증 전에도 사용 가능 |
| 2026-10-06 | `notification.report_id` FK (회의 O2) | 통합 V1의 `fk_notification_report`로 처리됨. 별도 문서(46번)는 만들지 않는다 |
| 2026-10-06 | 인덱스 (검증 L2) | `ix_notification_last_actor (last_actor_id) WHERE group_key IS NULL` 추가 |

---

## 12. 다른 담당자와 맞출 것

| 상대 | 맞출 것 | 이 문서의 제안 |
|---|---|---|
| 김민서 (좋아요) | `PostLiked`·`PostUnliked` | 20 §3-3 그대로. 알림 쪽 묶음·중복 처리는 강성찬이 한다 |
| 나민서 (신고·숨김) | `REPORT_RESOLVED` 문구와 `result` 값 | `ACTION_TAKEN` / `NO_VIOLATION` 두 가지. 신고 대상의 내용·작성자는 알림에 넣지 않음 |
| 나민서 (신고·숨김) | `notification.report_id` FK | **해결** — 통합 V1 `fk_notification_report` (회의 O2) |
| 나민서 (신고·숨김) | 숨김 알림의 사유 | 43 H-10 "(사유)"대로 보여줄 때 `hidden_reason`을 읽는다. 해제 때 `hidden_reason`이 지워져도 알림은 "지금은 다시 보여요"로 (L11) |
| 나민서 (내 글 관리) | 숨김 알림을 누르면 가는 곳 | 41:173은 "내 글 관리", 이 문서는 숨겨진 글·댓글 자체 (작성자는 사유와 원문을 그 자리에서 본다). **글 상세로 통일 제안** |
| 나민서 (글 상세) | 알림에서 댓글로 바로 이동 | 알림 링크는 `/@주소/posts/{글}?comment={id}#comment-{id}`. 글 상세는 `comment` 값을 댓글 영역에 넘기고(SSR·SPA 같음), `canonical`에는 넣지 않는다. 그 위치까지 불러오는 방법은 21 §6 `around` |
| 나민서 (탈퇴) | 13 §3-3에 단계 추가 | "알림 정리"(§8), 글·댓글 단계 다음 |
| 나민서 (탈퇴) | 44 §4 order 70 행 | "받은 알림 삭제, 묶음에서 나를 빼고 다시 계산"에 **"내가 행동한 하나짜리 알림 삭제"**(§8 ③)를 더해 주세요 (20 §10 "받은 것·보낸 것"과 같음) |
| 나민서 (권한 매트릭스) | 알림 행 | 받는 사람 본인만 읽기·읽음·삭제, 관리자도 남의 알림을 볼 수 없음 |
| 공통 (03 ERD) | `notification` 구조 | `target_type`/`target_id` → 대상별 FK (NT-6) |
| 화요일 안건 1 | 화면 구성 방식 | SSR·SPA 어느 쪽이든 §5 폴링 방식이 같다 |
