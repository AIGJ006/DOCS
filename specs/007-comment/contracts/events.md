# Contract: 댓글 이벤트와 다른 기능용 공개 Service

**Feature**: 007-comment | 원문: [docs/20-domain-events.md](../../../docs/20-domain-events.md) §3-2·§4, [docs/21-comment.md](../../../docs/21-comment.md) §9·§11

## 1. 도메인 이벤트 (`shared.event`, record)

규칙은 `DomainEvent` 주석을 따른다: 업무 트랜잭션 안에서 `publishEvent`, 구독은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`, 유실 허용, 필드는 ID·enum·`Instant`만(내용 없음).

### 1-1. `CommentCreated`

| 필드 | 타입 | 설명 |
|---|---|---|
| `commentId` | long | |
| `postId` | long | |
| `postAuthorId` | long | 글 작성자 |
| `authorId` | long | 댓글 작성자 |
| `parentId` | Long | 답글이면 최상위 번호 |
| `parentAuthorId` | Long | 답글이면 최상위 작성자 |
| `replyToMemberId` | Long | 답글의 답글이고 대상이 남일 때 |
| `createdAt` | Instant | |

- 발행: 새 댓글 INSERT 성공 시. 10초 중복으로 기존 댓글을 돌려줄 때는 발행하지 않는다.
- 구독: 011 알림(누가 어떤 알림을 받는지는 011 소관), 012 트렌딩(있으면).

### 1-2. `CommentDeleted`

| 필드 | 타입 | 설명 |
|---|---|---|
| `commentId` | long | |
| `postId` | long | |
| `postAuthorId` | long | |
| `authorId` | long | 지운 댓글의 작성자 |
| `parentId` | Long | |
| `deletedAt` | Instant | |

- 발행: 본인 삭제 성공 시(자리로 남겨도 발행). 빈 자리 정리로 최상위 행이 함께 지워지면 그 최상위에 대해서도 한 번 더 발행한다.
- 발행하지 않음: 글 완전 삭제의 CASCADE(006 `PostPurged`가 대신), 탈퇴 정리(015 `MemberPurged`가 대신), 숨김(014 `ContentHidden`).
- 구독: 011 알림(그 댓글로 생긴 알림 삭제 — 행 DELETE면 FK CASCADE가 이미 지우므로 자리로 남긴 경우를 위해 필요).

수정은 이벤트가 없다(FR-040).

## 2. 다른 기능이 부르는 공개 Service (interaction 모듈)

### 2-1. `CommentModerationService` — 014가 부름

```java
/** 숨김. 이미 숨김이면 아무것도 안 함(멱등). 삭제된 자리면 NotFoundException. 정상 → 카운터 −1. */
void hide(long commentId, long adminId, String reason, Instant now);
/** 해제. 숨김이 아니면 아무것도 안 함. 숨김 → 카운터 +1. */
void unhide(long commentId);
/** 신고 스냅샷용 내용 (014가 신고 접수 때 복사). 삭제된 자리면 빈 값. */
Optional<CommentSnapshot> snapshot(long commentId);
```

- 각 메서드는 자기 트랜잭션(`REQUIRED`)이고 그 행 `FOR UPDATE`. 014는 호출 뒤 `ContentHidden`을 발행한다.
- `reason`은 V1 `hidden_reason varchar(30)` 값(014가 정하는 코드).

### 2-2. `CommentPurgeService` — 015 `WithdrawalPurgeStep` order 20이 부름

```java
/** 탈퇴 30일 정리 (21 §11 SQL 2-a~2-d). 반환: 지운 행 수·자리로 남긴 수 (로그용). */
PurgeResult purgeByAuthor(long memberId);
```

한 트랜잭션(`MANDATORY` — 015 단계 트랜잭션 안) 순서:

1. **2-a** 글별 감소량: `SELECT post_id, count(*) FROM comment WHERE author_id = :m AND deleted_at IS NULL AND hidden_at IS NULL GROUP BY post_id` → `PostCounterService.adjustCommentCounts`
2. **2-b** 남의 답글이 있는 내 최상위: `UPDATE comment SET content = '', deleted_at = now() WHERE author_id = :m AND parent_id IS NULL AND EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = comment.id AND r.author_id <> :m)`
3. **2-c** 나머지 내 댓글 삭제: `DELETE FROM comment WHERE author_id = :m AND NOT (parent_id IS NULL AND deleted_at IS NOT NULL) RETURNING parent_id` — 내 최상위 자리(2-b에서 만든 것과 그 전에 내가 지워 둔 것)만 남기고 지운다. 답글 없는 내 최상위를 지우면 FK CASCADE 대상이 없다(답글이 있었다면 2-b에서 자리가 됨)
4. **2-d** 빈 자리 정리: `DELETE FROM comment c WHERE c.parent_id IS NULL AND c.deleted_at IS NOT NULL AND (c.author_id = :m OR c.id = ANY(:returnedParentIds)) AND NOT EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id)` — 범위는 내 최상위 자리와 3에서 지운 답글의 부모로 제한(카운터 변화 없음)

- 순서 의존: 015는 order 10(그 회원의 글 완전 삭제 — 006 `PostPurgeService`) 다음에 이 단계를 부른다. 그 회원 글의 댓글은 이미 CASCADE로 사라졌다.
- 남의 답글에 남은 `reply_to_member_id`는 그대로 두고, 회원이 익명 처리되면 `replyTo = {withdrawn: true}`로 보인다(FR-039).
- 결과 불변식(SC-002): 정리 후 모든 글에서 `comment_count = 정상 댓글 수`. 테스트 `CommentConcurrencyIT#탈퇴_정리_뒤_댓글_수가_실제와_같다`.

### 2-3. `CommentQueryService` — 006 `ReportPostPurgeStep`이 부름

```java
/** 그 글의 댓글 번호 전부 (답글·자리 포함, 번호 순). */
List<Long> commentIdsOfPost(long postId);
```

006 T058이 만든 최소 구현을 **006 머지 후** 이 기능이 넘겨받는다. 서명과 결과는 바꾸지 않는다.

## 3. post·account 모듈에 더하는 공개 API

| 모듈 | 서명 | 트랜잭션 | 쓰는 곳 |
|---|---|---|---|
| post | `PostCounterService.adjustCommentCount(long postId, int delta)` | `MANDATORY` | 작성·삭제·숨김·해제 |
| post | `PostCounterService.adjustCommentCounts(Map<Long, Integer> deltas)` | `MANDATORY` | 탈퇴 정리 |
| account | `MemberQueryService.findDisplays(Collection<Long> ids)` → `Map<Long, MemberDisplay>` | 읽기 | 목록·답글·작성 응답 |
