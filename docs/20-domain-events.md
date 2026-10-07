# 도메인 이벤트 목록 (알림 연결용)

> 작성일 2026-10-04 · 수정 2026-10-06 (검증 L12·G11 반영) · 작성 강성찬 · 관련: 인앱 알림(Tier C), C-CMT-1(댓글), C-LIKE-1(좋아요), 팔로우, 신고·숨김, 회원 탈퇴
> 공유 대상: **김민서**(좋아요·트렌딩·검색), **나민서**(신고·숨김·탈퇴). 각자 발행하는 이벤트의 이름과 담는 내용을 이 문서에 맞춰 주세요.
> 기준: [02 아키텍처](./02-architecture.md) §1 "이벤트" 원칙, [05 발행](./05-publish.md) §7 ⑩, [06 공개 범위](./06-visibility.md) §4

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| EV-1 | 전달 방식 | **커밋 후 리스너**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` | 02 문서 방식 그대로. 커밋된 사실만 전달되고, 리스너가 실패해도 원래 요청은 성공한다 (공통 원칙 5) |
| EV-2 | 유실 | **알림은 유실을 허용한다.** 커밋 직후 서버가 꺼지면 알림 하나가 빠질 수 있다 | outbox 테이블·배치를 공통에 넣지 않아 단순하다. 유실이 안 되는 처리가 필요한 사람(김민서 `outbox_event`)은 개인 확장으로 붙인다 |
| EV-3 | 담는 내용 | **ID + 받는 사람을 정하는 데 필요한 ID + 시각.** 제목·본문·댓글 내용·닉네임 같은 글자는 넣지 않는다 | 화면에 보여줄 때 다시 조회하므로 그 사이 비공개·삭제·닉네임 변경이 반영된다. 개인 정보가 이벤트·로그에 남지 않는다 |
| EV-4 | 발행 조건 | **상태가 실제로 바뀐 경우에만 한 번** 발행한다 | 같은 좋아요를 두 번 보내 `ON CONFLICT DO NOTHING`으로 아무것도 바뀌지 않았으면 이벤트도 없다. 알림 중복을 원천에서 막는다 |
| EV-5 | 새 글 알림 시점 | **처음 전체 공개될 때 한 번** (`first_public_at`이 처음 기록되는 순간). 이를 위해 `PostWentPublic` 이벤트를 **추가**한다 | 공개로 바로 발행하든, 비공개로 발행했다가 나중에 공개하든 한 번만 간다. 다시 발행·공개 범위 껐다 켜기로는 다시 가지 않는다 (05 P-3과 같은 규칙) |
| EV-6 | 반응 수 | 좋아요·댓글 수는 **이벤트로 세지 않는다.** 지금처럼 같은 트랜잭션에서 카운터를 바꾼다 (03 E-6) | 이벤트는 유실될 수 있다(EV-2). 카운터가 틀어지면 안 된다 |
| EV-7 | 이름 규칙 | `{대상}{과거분사}` PascalCase, Java `record`, `shared/event` 패키지 | 과제 예시(`CommentCreated`, `PostLiked`, `MemberFollowed`, `ReportResolved`)와 같은 형식 |

---

## 2. 발행·구독 규칙

### 2-1. 발행하는 쪽 (각 기능의 Service)

```java
// 상태를 바꾼 뒤, 같은 트랜잭션 안에서 발행만 한다. 처리는 커밋 후에 일어난다.
boolean inserted = likeRepository.insertIfAbsent(postId, memberId); // ON CONFLICT DO NOTHING
if (inserted) {
    postRepository.incrementLikeCount(postId);                      // 카운터는 여기서 (EV-6)
    events.publishEvent(new PostLiked(postId, post.authorId(), memberId, now));
}
```

| 규칙 | 내용 |
|---|---|
| 위치 | Service 계층. Controller·Repository·엔티티에서 발행하지 않는다 |
| 조건 | 상태가 실제로 바뀐 경우에만 (EV-4) |
| 롤백 | 트랜잭션이 롤백되면 이벤트는 버려진다 (AFTER_COMMIT) |
| 형식 | 불변 `record`, 필드는 `long`/`Long` ID·enum·`Instant`만 (EV-3) |

### 2-2. 받는 쪽 (리스너)

```java
@Async("eventExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)   // 커밋 후에는 새 트랜잭션이 필요하다
public void on(CommentCreated e) { notificationService.onCommentCreated(e); }
```

| 규칙 | 내용 |
|---|---|
| 실행 | 별도 스레드 풀 `eventExecutor` (코어 2, 최대 4, 대기열 1,000). 대기열이 가득 차면 **버리고 경고 로그** (EV-2) |
| 서버 종료 | 배포·재시작 때 대기열의 이벤트를 버리지 않도록 **처리 중인 것과 대기열을 끝낸 뒤 종료**한다: `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(20)` + `server.shutdown=graceful`. 20초 안에 못 끝낸 것은 버리고 남은 개수를 경고 로그로 남긴다 (EV-2 유실 허용 범위) |
| 실패 | 예외는 리스너 안에서 잡아 로그만 남긴다. 원래 요청에는 영향이 없다 |
| 다시 확인 | 처리할 때 최신 상태를 다시 조회한다. 그 사이 글이 비공개·삭제됐거나 받는 사람이 탈퇴 신청했으면 알림을 만들지 않는다 |
| 순서 | 보장하지 않는다. 리스너는 순서에 기대지 않는다 (예: `PostUnliked`가 `PostLiked`보다 먼저 처리돼도 문제없게) |
| 모듈 경계 | 리스너는 자기 모듈의 Service만 호출한다. 다른 모듈의 Repository를 직접 쓰지 않는다 (02 §1) |

---

## 3. 이벤트 목록

**굵은 이름**은 이번에 새로 제안하는 이벤트다. 나머지는 기존 문서에 이미 있다.

### 3-1. 글 (공통 `post` 모듈)

| 이벤트 | 언제 | 담는 내용 | 출처 |
|---|---|---|---|
| `PostPublished` | 최초 발행 | `postId, authorId, visibility, publishedAt` | 05 §7 ⑩ |
| `PostEdited` | 다시 발행 | `postId, authorId, editedAt` | 05 §7 ⑩ |
| `PostVisibilityChanged` | 공개 범위 변경 (값이 실제로 바뀐 경우) | `postId, authorId, from, to, changedAt` | 06 §4 |
| **`PostWentPublic`** | `first_public_at`이 **처음** 기록될 때 (공개 발행, 또는 비공개 글을 처음 공개로 바꿀 때) | `postId, authorId, firstPublicAt` | 신규 (EV-5) |
| **`PostTrashed`** | 휴지통으로 이동 | `postId, authorId, trashedAt` | 신규 (13 §2) |
| **`PostRestored`** | 휴지통에서 복구 | `postId, authorId, restoredAt` | 신규 (13 §2) |
| **`PostPurged`** | 완전 삭제 (영구 삭제, 30일 경과, 탈퇴 정리) | `postId, authorId` | 신규 (13 §2-5) |

- `PostWentPublic`은 `PostPublished`·`PostVisibilityChanged`와 **같은 트랜잭션에서 함께** 발행된다. 엔티티 메서드가 "이번에 `first_public_at`을 처음 채웠는지"를 돌려주면 Service가 그 값으로 판단한다.
- 빈 임시글 바로 삭제(13 D-2)와 빈 임시글 정리 배치(04 §2-5)는 아무에게도 보인 적 없는 글이라 이벤트를 발행하지 않는다.

### 3-2. 댓글 (강성찬 `interaction`)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| `CommentCreated` | 댓글·답글 작성 | `commentId, postId, postAuthorId, authorId, parentId, parentAuthorId, replyToMemberId, createdAt` — 최상위 댓글은 `parentId`·`parentAuthorId`가 `null`, 답글에 답한 경우만 `replyToMemberId`가 있다 ([21 §2](./21-comment.md)) |
| **`CommentDeleted`** | 본인 삭제 (답글이 있어 "삭제된 댓글"로 남는 경우 포함) | `commentId, postId, postAuthorId, authorId, parentId, deletedAt` |

- 댓글 수정은 이벤트를 발행하지 않는다 (구독할 곳이 없다).
- 관리자 숨김은 `ContentHidden`(§3-5)으로 따로 발행한다.

### 3-3. 좋아요 (김민서 `interaction`)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| `PostLiked` | `post_like` 행이 **실제로 생겼을 때** | `postId, postAuthorId, memberId, likedAt` |
| **`PostUnliked`** | `post_like` 행이 **실제로 지워졌을 때** | `postId, postAuthorId, memberId, unlikedAt` |

### 3-4. 팔로우 (강성찬 `follow`)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| `MemberFollowed` | `follow` 행이 실제로 생겼을 때 | `followerId, followeeId, followedAt` |
| **`MemberUnfollowed`** | `follow` 행이 실제로 지워졌을 때 | `followerId, followeeId, unfollowedAt` |

팔로우 대상은 **회원**이다 (블로그 = 회원, 01 Q5). 나민서님의 `blog`는 회원과 1:1이라 `blog.member_id`로 바꿔서 쓰면 된다.

### 3-5. 신고·숨김 (나민서 `moderation`)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| `ReportResolved` | 관리자가 신고를 처리함 | `reportId, reporterId, targetType, targetId, result, resolvedAt` — `targetType`: `POST` / `COMMENT` (공통 신고 대상은 글·댓글뿐, 43 H-1·V1 `ck_report_case_target`. 회원 신고 `MEMBER`는 개인 확장에서 값을 더한다), `result`: `ACTION_TAKEN` / `NO_VIOLATION` |
| **`ContentHidden`** | 관리자가 글·댓글을 숨김 | `targetType, targetId, ownerId, postId, hiddenAt` — 댓글이면 `postId`는 그 댓글의 글 |
| **`ContentUnhidden`** | 숨김 해제 | `targetType, targetId, ownerId, postId, unhiddenAt` |
| **`MemberSuspended`** | 관리자가 회원을 정지함 | `memberId, until, suspendedAt` — `until`은 영구 정지면 `null`. **사유는 넣지 않는다** (EV-3: 사유는 관리자가 쓴 글자다). 사유가 필요한 구독자는 정지 이력을 다시 조회한다 |

- 신고 한 건을 처리해 숨김까지 했으면 `ReportResolved`와 `ContentHidden`이 **둘 다** 발행된다. 받는 사람이 다르다 (신고자 / 작성자).
- 이벤트에 **신고자 ID를 작성자 쪽 알림으로 넘기지 않는다.** `ContentHidden`에는 `reporterId`가 없다.
- `MemberSuspended`는 알림을 만들지 않는다 (정지 회원은 로그인할 수 없어 로그인 화면에서 안내, 43 §6). 정지 해제는 이벤트가 없다 (기간이 지나면 저절로 풀리고, 구독할 곳이 없다). **세션 삭제는 이 이벤트로 하지 않는다** — 이벤트는 유실될 수 있으므로(EV-2) 정지 Service가 직접 지운다 (42 P-7, 43 §6).

### 3-6. 회원 (나민서 탈퇴 / 공통 `account`)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| **`MemberWithdrawn`** | 탈퇴 신청 (30일 유예 시작) | `memberId, withdrawnAt` |
| **`MemberRestored`** | 유예 중 복구 | `memberId, restoredAt` |

30일 뒤 익명 처리(13 §3-3)는 **이벤트가 아니라 한 트랜잭션 안의 정리 단계**로 처리한다. 이벤트는 유실될 수 있어서(EV-2) 개인 정보 삭제에는 쓰지 않는다. 정리 단계는 [다른 담당자와 맞출 것](#10-다른-담당자와-맞출-것)에 적었다.

### 3-7. 친구 공개 규격 적용자만 (06 §6)

| 이벤트 | 언제 | 담는 내용 |
|---|---|---|
| **`FriendRequested`** | 친구 요청 (행이 새로 생겼을 때) | `requesterId, receiverId, requestedAt` |
| **`FriendAccepted`** | 수락 (상대가 이미 보낸 요청에 맞요청해 바로 수락된 경우 포함) | `requesterId, accepterId, acceptedAt` |

거절·요청 취소·친구 끊기는 이벤트를 발행하지 않는다. 상대에게 알리지 않는다는 06 §6-2 원칙을 이벤트 단계에서 지킨다.

---

## 4. 알림 종류와 이벤트

알림 화면·묶기·보관 기간은 [25 알림 문서](./25-notification.md)에서 정한다. 여기서는 어떤 이벤트가 어떤 알림이 되는지만 정한다.

| 알림 종류 | 만드는 이벤트 | 받는 사람 | 묶기 |
|---|---|---|---|
| `COMMENT` 내 글에 댓글 | `CommentCreated` | 글 작성자 | 하나씩 |
| `REPLY` 내 댓글에 답글 | `CommentCreated` (`parentId` 있음) | 부모 댓글 작성자 | 하나씩 |
| `LIKE` 내 글 좋아요 | `PostLiked` | 글 작성자 | **글마다 하나로** ("김민서님 외 3명이 글을 좋아해요") |
| `FOLLOW` 새 팔로워 | `MemberFollowed` | 팔로우받은 사람 | 안 읽은 동안 하나로 ("김민서님 외 2명이 팔로우해요") |
| `NEW_POST` 팔로우한 사람의 새 글 | `PostWentPublic` | 작성자의 팔로워 전원 | 하나씩 |
| `REPORT_RESOLVED` 신고 처리 결과 | `ReportResolved` | 신고한 사람 | 하나씩 |
| `CONTENT_HIDDEN` 내 글·댓글이 숨겨짐 | `ContentHidden` | 글·댓글 작성자 | 하나씩 |
| (규격 적용자) `FRIEND_REQUEST` / `FRIEND_ACCEPTED` | `FriendRequested` / `FriendAccepted` | 받은 사람 / 요청한 사람 | 하나씩 |

**공통 제외 규칙 (모든 알림):**
1. 본인 행동은 알리지 않는다 (`actorId == receiverId`).
2. 받는 사람이 탈퇴 신청 상태면 만들지 않는다.
3. 글과 관련된 알림(`COMMENT`·`REPLY`·`LIKE`·`NEW_POST`)은 처리 시점에 받는 사람이 그 글을 읽을 수 없으면 만들지 않는다 (`PostAccessPolicy.canRead`, 06 R-1). 예: 글이 그 사이 비공개가 됨.
4. 행동한 사람이 탈퇴 신청 상태면 만들지 않는다.
5. 받는 사람이 그 종류를 껐으면 만들지 않는다 (운영 알림 `REPORT_RESOLVED`·`CONTENT_HIDDEN`은 끌 수 없음, [25 §7](./25-notification.md)).

### 4-1. 댓글 알림의 받는 사람

답글의 **대상**은 `replyToMemberId`가 있으면 그 회원, 없으면 최상위 댓글 작성자(`parentAuthorId`)다. 공통은 답글 1단계라 답글에 답하면 같은 최상위 아래에 달리고 대상이 따로 저장된다 ([21 CM-2](./21-comment.md)).

| 상황 | 글 작성자 | 답글의 대상 |
|---|---|---|
| 남의 글에 최상위 댓글 | `COMMENT` | — |
| 최상위 댓글에 답글 (대상 = 최상위 작성자) | `COMMENT` | `REPLY` |
| 답글에 답글 (대상 = 그 답글의 작성자) | `COMMENT` | `REPLY` (최상위 작성자에게는 가지 않음) |
| 대상이 글 작성자 | `REPLY` 하나만 | (같은 사람) |
| 내 글에 내가 댓글 | 없음 (본인) | — |
| 내 댓글에 내가 답글 | `COMMENT` (글 작성자가 남이면) | 없음 (본인) |

한 사람이 한 댓글로 알림을 두 개 받지 않는다. 겹치면 `REPLY`를 남긴다 (더 직접적인 관계).

### 4-2. 취소·삭제 이벤트가 알림에 주는 영향

| 이벤트 | 알림 처리 |
|---|---|
| `CommentDeleted` | 그 댓글로 생긴 `COMMENT`·`REPLY` 알림 삭제 |
| `PostUnliked` | **안 읽은** 묶음 알림에서 그 사람을 뺀다. 남은 사람이 없으면 알림 삭제. 읽은 묶음은 그대로 |
| `MemberUnfollowed` | 아직 안 읽은 `FOLLOW` 알림에서 그 사람을 뺀다 |
| `PostTrashed` / `PostVisibilityChanged`(공개 → 비공개) | 지우지 않는다. **화면에 보여줄 때 다시 확인**해서 볼 수 없는 글이면 "볼 수 없는 글이에요"로 표시 (25 문서) |
| `PostPurged` | 그 글을 가리키는 알림 삭제. 알림의 `post_id` FK `ON DELETE CASCADE`로 DB가 함께 지우므로 리스너는 필요 없다 (25 NT-6) |
| `ContentHidden` | 숨겨진 댓글로 생긴 알림 삭제, 숨겨진 글은 화면에서 "볼 수 없는 글이에요" |
| `PostRestored` / `ContentUnhidden` | 아무것도 하지 않는다 (지운 알림은 되살리지 않음) |

---

## 5. 구독하는 곳 (예상)

각 담당자가 확정한다. 이벤트를 추가로 구독해도 발행하는 쪽 코드는 바뀌지 않는다.

| 이벤트 | 알림 (강) | 트렌딩 (김) | 검색 색인·sitemap (김) | 개인 확장 |
|---|:-:|:-:|:-:|---|
| `PostPublished` | | | | 강 잔디 |
| `PostEdited` | | | ✓ | |
| `PostVisibilityChanged` | | ✓ | ✓ | |
| `PostWentPublic` | ✓ `NEW_POST` | ✓ | ✓ | 강 "첫 공개 응원" |
| `PostTrashed` / `PostRestored` / `PostPurged` | (FK `CASCADE`, 구독 안 함) | ✓ | ✓ | |
| `CommentCreated` / `CommentDeleted` | ✓ | ✓ | | 강 잔디 |
| `PostLiked` / `PostUnliked` | ✓ | ✓ | | |
| `MemberFollowed` / `MemberUnfollowed` | ✓ | | | |
| `ReportResolved` / `ContentHidden` / `ContentUnhidden` | ✓ | ✓ (숨김) | ✓ (숨김) | |
| `MemberWithdrawn` / `MemberRestored` | | ✓ | ✓ | |

트렌딩이 숫자를 셀 때는 이벤트보다 `post.like_count`·`comment_count`를 기준으로 하는 것을 권장한다 (EV-6).

---

## 6. 하지 않는 것

| 하지 않음 | 이유 |
|---|---|
| 이벤트로 반응 수 세기 | EV-6 |
| 이벤트로 개인 정보 삭제(탈퇴 익명 처리) | 유실될 수 있다. 13 §3-3은 한 트랜잭션의 정리 단계로 |
| 이메일·푸시 발송 | 공통은 앱 안 알림만 (01 Tier C) |
| 외부 메시지 브로커(Kafka 등) | 하나의 배포 단위 (02 MSA 금지) |
| 이벤트에 글자(제목·내용·닉네임) 넣기 | EV-3 |

---

## 7. 공통 완료 기준

| # | 기준 | 확인 방법 |
|---|---|---|
| 1 | 상태가 실제로 바뀐 경우에만 이벤트가 한 번 발행된다 | 같은 좋아요 요청 20건을 동시에 → `PostLiked` 1건 (`@RecordApplicationEvents`) |
| 2 | 트랜잭션이 롤백되면 리스너가 실행되지 않는다 | 저장 중 예외 → 알림 0건 |
| 3 | 리스너가 실패해도 원래 요청은 성공한다 | 알림 리스너가 예외를 던지게 해도 댓글 작성 201 |
| 4 | 이벤트에 제목·본문·댓글 내용·닉네임이 없다 | `record` 필드 검사 테스트 (ArchUnit 또는 리플렉션) |
| 5 | `PostWentPublic`은 글마다 한 번만 발행된다 | 공개 → 비공개 → 공개, 다시 발행 → 1건 |
| 6 | 본인 행동으로는 알림이 생기지 않는다 | 내 글에 내 댓글·좋아요 → 알림 0건 |

---

## 8. ERD 변경 제안

없음. 알림·팔로우 테이블은 [24](./24-follow-feed.md)·[25](./25-notification.md) 문서에서 제안한다.

---

## 9. 결정 기록 추가분

| 날짜 | 안건 | 결정 |
|---|---|---|
| 2026-10-04 | 답글 깊이 (Q6) | **공통 1단계** (01 제안 그대로). 깊이 확장은 개인 (강성찬: 무제한 + 3단계 이후 더보기) |
| 2026-10-04 | 팔로우 대상 | **회원** (블로그 = 회원). 나민서 `blog`는 1:1이라 `member_id`로 연결 |
| 2026-10-04 | 공통 알림 종류 | 댓글, 답글, 좋아요(글마다 묶음), 새 팔로워(안 읽은 동안 묶음), 팔로우한 사람의 새 글, 신고 처리 결과, 내 글·댓글 숨김 |
| 2026-10-04 | 이벤트 전달 | 커밋 후 리스너 + 비동기. 알림은 유실 허용, 반응 수·개인 정보 삭제는 이벤트에 의존하지 않음 |
| 2026-10-04 | 이벤트 내용 | ID + 받는 사람을 정하는 ID + 시각. 글자는 넣지 않음 |
| 2026-10-04 | 새 글 알림 시점 | 처음 전체 공개될 때 한 번. `PostWentPublic` 이벤트 추가 |
| 2026-10-04 | 이벤트 이름 | `{대상}{과거분사}`, 이 문서 §3의 목록 |
| 2026-10-06 | 신고·정지 이벤트 (검증 L12) | `ReportResolved.targetType`은 `POST`·`COMMENT`만 (`MEMBER`는 개인 확장). `MemberSuspended(memberId, until, suspendedAt)` 추가 — 43의 요청에서 `reason`은 빼고 받음 (EV-3) |
| 2026-10-06 | 이벤트 실행기 종료 (검증 G11) | 서버 종료 때 대기열을 최대 20초 처리한 뒤 종료, 못 끝낸 것은 개수만 경고 로그 |

---

## 10. 다른 담당자와 맞출 것

| 상대 | 맞출 것 | 이 문서의 제안 |
|---|---|---|
| 김민서 (좋아요) | `PostLiked`·`PostUnliked`를 발행하는 위치와 조건 | 행이 실제로 생기거나 지워졌을 때만 (§3-3). 03 §4의 `WITH ins AS (…) RETURNING`으로 판단 가능 |
| 김민서 (트렌딩) | 댓글 수를 언제 올리고 내리는지 | 21 댓글 문서에서 확정. 트렌딩은 `comment_count` 컬럼을 기준으로 하고, 이벤트는 "다시 계산할 글" 신호로만 쓰기를 권장 |
| 김민서 (검색·sitemap) | 색인 갱신에 구독할 이벤트 | `PostWentPublic`, `PostEdited`, `PostVisibilityChanged`, `PostTrashed`·`Restored`·`Purged`, `ContentHidden`·`Unhidden` |
| 나민서 (신고·숨김) | `ReportResolved`·`ContentHidden`·`ContentUnhidden` 이름과 필드, `result` 값 | §3-5. 신고자 ID는 작성자 쪽으로 넘기지 않음 |
| 나민서 (신고·숨김) | 43 §7 `MemberSuspended`·`ReportResolved.targetType` 요청 | **반영함.** 단 `MemberSuspended`는 `reason` 대신 `suspendedAt` (EV-3). 43 §7 표의 담는 값을 `memberId, until, suspendedAt`으로 고쳐 주세요 |
| 공통 (02 아키텍처, 배포) | 서버 종료 순서 | `server.shutdown=graceful`과 `eventExecutor` 종료 대기 20초 (§2-2). 배포 설정에 함께 넣는다 |
| 나민서 (탈퇴) | `MemberWithdrawn`·`MemberRestored` 발행 여부 | §3-6. 30일 뒤 익명 처리는 이벤트가 아니라 정리 단계 |
| 나민서 (탈퇴) | 13 §3-3 정리 단계에 각 모듈이 단계를 끼워 넣는 방법 | 모듈마다 `WithdrawalPurgeStep` 인터페이스를 구현하고 `order()` 순서대로 한 트랜잭션에서 실행. 강성찬 몫: 댓글(13 §3-3 2번), 팔로우 행 삭제, 알림 삭제(받은 것·보낸 것) |
| 공통 (`post` 모듈) | `PostWentPublic`, `PostTrashed`·`Restored`·`Purged` 추가 | 05 §7 ⑩·06 §4·13 §2의 기존 흐름에 **이벤트 발행만 추가**. 기존 결정 변경 없음 |
