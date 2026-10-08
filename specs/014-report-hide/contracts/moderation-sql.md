# 계약: 신고·숨김·정지 SQL, 다른 모듈 공개 Service, 이벤트

**Feature**: 014-report-hide | 근거: research R3~R11, docs/43 §2~§7, docs/13 §2-5·§3-3, docs/20 §3-5

이 문서는 HTTP API 밖의 계약이다. moderation 모듈은 `report_case`·`report`만 직접 쓰고, 글·댓글·회원은 아래 §6의 공개 Service로만 바꾼다.

## §1. 신고 접수 (한 트랜잭션)

```sql
-- 1) 대기 사건 만들기 시도 (글). 댓글이면 comment_id 쪽 부분 UNIQUE
INSERT INTO report_case (target_type, post_id, target_author_id, snapshot_title, snapshot_content)
VALUES ('POST', :postId, :authorId, :title, :contentHead)
ON CONFLICT (post_id) WHERE status = 'PENDING' AND post_id IS NOT NULL DO NOTHING
RETURNING id;

-- 2) 없으면 기존 대기 사건 잠금 (처리와 순서 맞춤)
SELECT id FROM report_case WHERE post_id = :postId AND status = 'PENDING' FOR UPDATE;

-- 3) 신고 (같은 회원 중복은 무시)
INSERT INTO report (case_id, reporter_id, reason, detail)
VALUES (:caseId, :me, :reason, :detail)
ON CONFLICT (case_id, reporter_id) DO NOTHING;
```

- 1과 2 사이에 사건이 처리되면 2가 비어 있다 → 1을 한 번 더(최대 2번).
- 스냅샷은 1이 행을 만들었을 때만 넣는다(값은 1을 부르기 전에 §6 `snapshot`으로 읽음 — 1이 DO NOTHING이면 버림).
- 요청 제한은 1 전에: `RateLimiter.acquireOrThrow("ratelimit:report:" + me + ":1m", 5, 1분)`, `…:1d`, 50, 1일.

## §2. 대기 목록·처리됨 목록

```sql
-- 대기 (20 + 1개, 커서 [count, last, id])
WITH c AS (
  SELECT rc.id, rc.target_type, rc.snapshot_title, rc.snapshot_content, rc.target_author_id,
         count(r.id) AS cnt, max(r.created_at) AS last_reported_at
    FROM report_case rc
    JOIN report r ON r.case_id = rc.id
   WHERE rc.status = 'PENDING'
     AND (rc.post_id IS NOT NULL OR rc.comment_id IS NOT NULL)        -- 고아 사건은 목록에서 뺌 (배치가 닫음)
   GROUP BY rc.id
)
SELECT * FROM c
 WHERE (:cursorCnt IS NULL OR (cnt, last_reported_at, id) < (:cursorCnt, :cursorLast, :cursorId))
 ORDER BY cnt DESC, last_reported_at DESC, id DESC
 LIMIT :size + 1;

-- 사유별 수 (한 페이지 사건들)
SELECT case_id, reason, count(*) FROM report WHERE case_id = ANY(:ids) GROUP BY case_id, reason;

-- 처리됨 (커서 [handled_at, id])
SELECT id, target_type, snapshot_title, snapshot_content, target_author_id, status, handled_at, handled_by,
       post_id, comment_id
  FROM report_case
 WHERE status <> 'PENDING'
   AND (:cursorAt IS NULL OR (handled_at, id) < (:cursorAt, :cursorId))
 ORDER BY handled_at DESC, id DESC
 LIMIT :size + 1;
```

- 작성자 주소·처리 관리자 닉네임은 007이 더한 `MemberQueryService.findDisplays(ids)` 한 번(작성자·관리자 번호를 합쳐서, 익명 처리된 회원은 handle null). 처리됨 항목의 "지금 숨김인가"는 §6 `PostModerationService.hiddenOf(postIds)`·007 `CommentModerationService.hiddenOf(commentIds)` 한 번씩.
- 한 페이지 SQL: 목록 1 + 사유별 1 + 회원 1~2 + (처리됨) 숨김 여부 1~2.

## §3. 처리 (한 트랜잭션)

```sql
SELECT id, target_type, post_id, comment_id, target_author_id, status FROM report_case WHERE id = :caseId FOR UPDATE;
-- status <> PENDING → 409
-- post_id IS NULL AND comment_id IS NULL → §5 닫기 + 409

SELECT reporter_id FROM report WHERE case_id = :caseId;                -- 혼자 신고 판정, 이벤트 대상
-- HIDE면 §6 hide(...)

UPDATE report_case SET status = :status, handled_by = :admin, handled_at = now() WHERE id = :caseId;

SELECT id, reporter_id FROM report WHERE case_id = :caseId;            -- ReportResolved 신고마다
```

## §4. 직접 숨김

```sql
-- 대기 사건 있으면 잠금
SELECT id FROM report_case WHERE post_id = :postId AND status = 'PENDING' FOR UPDATE;
-- 있으면 §3과 같이 HIDDEN으로 닫고 ReportResolved(ACTION_TAKEN) 신고마다
-- 없으면 신고 없는 사건을 바로 HIDDEN으로
INSERT INTO report_case (target_type, post_id, target_author_id, snapshot_title, snapshot_content, status, handled_by, handled_at)
VALUES ('POST', :postId, :authorId, :title, :contentHead, 'HIDDEN', :admin, now())
RETURNING id;
```

- 부분 UNIQUE는 `PENDING`에만 걸려 있어 `HIDDEN` 행은 같은 대상에 여러 개 있어도 된다(숨김 → 해제 → 다시 숨김).

## §5. 대상 없음 자동 종료

```sql
UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL
 WHERE status = 'PENDING' AND <조건>;
```

| 호출 | `<조건>` | 트랜잭션 |
|---|---|---|
| `ReportPostPurgeStep.beforePurge(postId)` (006 `PostPurgeStep` order 10) | `post_id = :postId`, 그다음 `comment_id IN (:commentIds)` 1,000개씩(007 `CommentQueryService.commentIdsOfPost`) | 006 완전 삭제 트랜잭션 안, `DELETE FROM post` 전 |
| `OrphanCaseCloser.onCommentDeleted(CommentDeleted e)` | `comment_id = :commentId OR (post_id IS NULL AND comment_id IS NULL)` | 커밋 뒤 비동기(`eventExecutor`), 자기 트랜잭션 |
| `ReportWithdrawalPurgeStep.purge(memberId)` (015 order 80) | `target_author_id = :memberId` + `UPDATE report SET detail = NULL WHERE reporter_id = :memberId` | 015 정리 트랜잭션 안(`MANDATORY`) |
| `ReportSnapshotCleanupJob` 1단계 | `post_id IS NULL AND comment_id IS NULL` | 자기 트랜잭션 |

이벤트·알림 없음. 처리 관리자 NULL.

## §6. 다른 모듈 공개 Service

```java
// post.application.PostModerationService (post 모듈, 이 기능이 새로 만듦)
Optional<PostSnapshot> snapshot(long postId);                   // 행이 있으면 (휴지통 포함)
boolean hide(long postId, long adminId, String reason, Instant now);   // FOR UPDATE, 이미 숨김이면 false. 행 없음 → PostNotFoundException
boolean unhide(long postId);                                    // 숨김이 아니면 false
TargetState currentState(long postId);                          // research R7 표, 비회원 기준 판정 재사용
Map<Long, Boolean> hiddenOf(Collection<Long> postIds);

// interaction.application.CommentModerationService (007 소유 — 아래 두 개를 맞춤)
Optional<CommentSnapshot> snapshot(long commentId);             // CommentSnapshot 필드는 data-model §3
Map<Long, Boolean> hiddenOf(Collection<Long> commentIds);       // 추가 요청
// hide / unhide는 007 contracts/events.md §2-1 그대로

// account.application.SuspensionService (001 T106 시그니처, 이 기능이 구현)
SuspensionRecord suspend(long memberId, String reason, SuspensionDuration duration, long adminId, Instant now);
void lift(long memberId, long adminId, Instant now);
List<SuspensionRecord> history(long memberId, int limit);
Optional<SuspensionRecord> findOpen(long memberId);              // 001 T106 그대로

// account.application.MemberQueryService (001 소유, 추가만)
// 묶음 조회는 007 findDisplays(Collection<Long>) → Map<Long, MemberDisplay> 재사용
Optional<AdminMemberInfo> findAdminView(String handle);         // 소문자 정규화, 익명 처리면 empty
```

## §7. 정지 (001 `SuspensionService.suspend`)

```sql
SELECT id, role, status FROM member WHERE id = :memberId AND deleted_at IS NULL FOR UPDATE;
-- role = ADMIN → 400 CANNOT_SUSPEND_ADMIN / status = WITHDRAWN → 400 CANNOT_SUSPEND_WITHDRAWN
SELECT id FROM member_suspension WHERE member_id = :memberId AND lifted_at IS NULL LIMIT 1;   -- 있으면 409
INSERT INTO member_suspension (member_id, reason, started_at, ends_at, suspended_by)
VALUES (:memberId, :reason, :now, :endsAt, :adminId) RETURNING id;
UPDATE member SET status = 'SUSPENDED' WHERE id = :memberId;
-- SessionTerminator.terminateAll(memberId, empty) — 실패하면 예외 → 롤백 → 503
-- afterCommit: terminateAll 한 번 더 (실패는 WARN)
```

해제:

```sql
UPDATE member_suspension SET lifted_at = :now, lifted_by = :adminId
 WHERE member_id = :memberId AND lifted_at IS NULL;
UPDATE member SET status = 'ACTIVE' WHERE id = :memberId AND status = 'SUSPENDED';
```

## §8. 보관 정리 (`ReportSnapshotCleanupJob`, 04:45 KST, ShedLock `reportSnapshotCleanup`)

```sql
-- 1) 고아 사건 닫기 (§5)
-- 2) 신고 설명 비우기 (1,000건씩)
UPDATE report SET detail = NULL
 WHERE id IN (SELECT r.id FROM report r JOIN report_case rc ON rc.id = r.case_id
               WHERE r.detail IS NOT NULL AND rc.status <> 'PENDING' AND rc.handled_at < now() - :retention
               LIMIT :batch);
-- 3) 스냅샷 비우기 (1,000건씩)
UPDATE report_case SET snapshot_title = NULL, snapshot_content = NULL
 WHERE id IN (SELECT id FROM report_case
               WHERE status <> 'PENDING' AND handled_at < now() - :retention
                 AND (snapshot_title IS NOT NULL OR snapshot_content IS NOT NULL)
               LIMIT :batch);
```

`ck_report_detail`은 `detail` NULL을 통과시킨다(research R1). 로그: `report cleanup orphans={n} details={n} snapshots={n} took={ms}`.

## §9. 이벤트

| 이벤트 | 발행 시점 | 수 |
|---|---|---|
| `ReportResolved(reportId, reporterId, targetType, targetId, result, resolvedAt)` | 처리·직접 숨김이 대기 사건을 닫을 때 | 신고마다 1 |
| `ContentHidden(targetType, targetId, ownerId, postId, hiddenAt)` | 새로 숨겼을 때 | 1 |
| `ContentUnhidden(targetType, targetId, ownerId, postId, unhiddenAt)` | 해제했을 때 | 1 |
| `MemberSuspended(memberId, until, suspendedAt)` | 정지 | 1 |

- 업무 트랜잭션 안에서 `publishEvent`, 구독은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`(`DomainEvent` 규칙).
- 구독: 011이 `ReportResolved`·`ContentHidden`. `ContentUnhidden`·`MemberSuspended`는 지금 구독자 없음.
- 이 기능이 구독하는 것: 007 `CommentDeleted`(§5).
