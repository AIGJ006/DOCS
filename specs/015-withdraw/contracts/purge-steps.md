# 계약: 탈퇴 30일 정리 단계·작업·이벤트

**Feature**: 015-withdraw | **Date**: 2026-10-08

이 문서는 모듈 사이 약속이다. 각 모듈은 자기 단계 클래스를 이 계약대로 구현하고, 015의 정리 작업은 단계 내부를 모른다. 근거: 44 §4, 13 §3-3, 20 §3-6, research R8~R11.

## 1. 확장점

```java
package com.team.blog.shared.application.withdraw;

public interface WithdrawalPurgeStep {
    int order();                 // 10 단위, 새 단계는 사이 값. 같은 값이 둘이면 애플리케이션 시작 실패
    void purge(long memberId);   // 호출한 쪽 트랜잭션 안(MANDATORY). 외부 호출(파일·메일·Redis) 금지. 예외 → 그 회원 전체 롤백
}
```

규칙

- 단계는 **그 회원이 아직 `member` 행을 가진 상태**에서 불린다(order 90 전까지 닉네임 등도 그대로).
- 단계는 멱등이어야 한다. 앞선 날 다른 단계에서 실패해 롤백된 뒤 다시 불려도 같은 결과여야 한다.
- 단계는 이벤트를 내지 않는다. 예외: order 10이 부르는 006 `PostPurgeService`의 `PostPurged`(글마다)는 006 규칙대로 나간다.
- 단계는 로그에 회원 번호와 처리 건수만 남긴다(이메일·닉네임 금지).

## 2. 단계 표

| order | 클래스 (패키지) | 처리 | 부르는 것 / SQL | 만드는 tasks |
|---|---|---|---|---|
| 10 | `PostWithdrawalPurgeStep` (`post.application`) | 내 글 전부(휴지통·임시·숨김 포함) 완전 삭제. 그 글과 그 글 댓글의 대기 신고 종료, 그 글에서만 쓴 사진 연결 해제, 남이 쓴 댓글·좋아요·알림은 CASCADE | 006 `PostPurgeService.purgeAllByAuthor(memberId)` (006 research R25) | 015 |
| 20 | `CommentWithdrawalPurgeStep` (`interaction.application`) | 남의 글의 내 댓글: 남의 답글이 있는 최상위는 자리만, 나머지 삭제, 빈 자리 정리, `comment_count` 감소 | 007 `CommentPurgeService.purgeByAuthor(memberId)` (007 contracts/events.md §2-2) | 015 |
| 30 | `LikeWithdrawalPurgeStep` (`interaction.application`) | 내가 누른 좋아요 삭제, `like_count` 감소 | 009 `LikePurgeService.purgeByMember(memberId)` (009 contracts/view-pipeline.md §6) | 015 |
| 40 | `ImageWithdrawalPurgeStep` (`media.application`) | 내가 올린 사진 전부(현재 프로필·TEMP 포함) `detached_at = now() - 7일` → 다음 사진 정리(03:30)가 파일·행 삭제 | 003 `ImagePurgeService.detachAllByUploader(memberId)` (003 contracts/storage.md §3-2) | 015 |
| 50 | `AuthIdentityWithdrawalPurgeStep` (`account.application.purge`) | 로그인 수단 삭제 → 같은 이메일·소셜 계정 재가입 가능 | `DELETE FROM auth_identity WHERE member_id = :m` | 015 |
| 60 | `FriendshipWithdrawalPurgeStep` (`account.application.purge`) | 친구 관계(요청 중·수락) 어느 쪽이든 삭제. 항상 실행(2026-10-07 M1) | `DELETE FROM friendship WHERE member_a_id = :m OR member_b_id = :m` | 015 |
| 65 | `FollowWithdrawalPurgeStep` (010 모듈) | 팔로우 양방향 삭제 | `DELETE FROM follow WHERE follower_id = :m OR followee_id = :m` (24 §6) | 010 |
| 70 | `NotificationWithdrawalPurgeStep` (011 모듈) | ① 받은 알림 삭제 ② 남의 묶음 알림에서 나를 빼고 다시 계산(0명이면 삭제 — DELETE와 UPDATE는 따로) ③ 내가 행동한 하나짜리 알림 삭제(`last_actor_id = :m AND group_key IS NULL`) ④ 내 알림 끄기 설정 삭제 | 25 §8, 011 FR-039 | 011 |
| 80 | `ReportWithdrawalPurgeStep` (`moderation`) | 내 콘텐츠의 대기 사건 종료, 내가 쓴 신고의 설명 비우기(사유는 유지) | `UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL WHERE target_author_id = :m AND status = 'PENDING'` + `UPDATE report SET detail = NULL WHERE reporter_id = :m` (13 §3-3 6-3) | 014 |
| 90 | `MemberWithdrawalPurgeStep` (`account.application.purge`) | 회원 익명화. 주소·동의·정지 이력은 남김 | 아래 §2-1 | 015 |

### 2-1. order 90 SQL

```sql
UPDATE member
   SET nickname = NULL, bio = NULL, nickname_changed_at = NULL, last_active_at = NULL,
       deleted_at = :now, updated_at = :now
 WHERE id = :m AND status = 'WITHDRAWN' AND deleted_at IS NULL;
-- 영향 행 1이 아니면 IllegalStateException → 롤백
```

- `ck_member_nickname_null`은 같은 문장에서 `deleted_at`이 채워지므로 통과한다.
- `report.detail`을 NULL로 바꿀 때 `ck_report_detail`(`reason <> 'OTHER' OR length(btrim(detail)) > 0`)은 NULL 비교가 NULL이 되어 통과한다(51 "원문 유지와 표기 판단"). 014 테스트에서 `reason = OTHER` 행으로 확인한다.

### 2-2. 순서 불변식 (테스트로 확인)

- 10이 20·30·70보다 먼저: 내 글의 남 댓글·좋아요·알림이 먼저 CASCADE로 사라진다.
- 20·30 뒤 모든 글에서 `comment_count = 정상 댓글 수`, `like_count = 좋아요 행 수`(SC-004).
- 90이 마지막. 그 뒤 `member` 행에 `handle`·`role`·`status`·`withdrawn_at`·`deleted_at`·`default_visibility`·`last_active_visible`·`created_at`·`updated_at`만 값이 있다(SC-005).

## 3. 정리 작업

```text
WithdrawPurgeJob  (cron blog.withdraw.purge.cron, 기본 03:00 KST, ShedLock "withdrawPurge", lockAtMostFor 1h)
 ├─ 등록된 단계 order 집합 ⊇ required-orders ?  아니면 ERROR "탈퇴 정리 단계 누락 orders=[…]" 후 종료
 ├─ targets = 유예 30일 지난 회원(최대 batch-size, withdrawn_at 순)
 │          + 영구 정지 1년 지난 회원(최대 batch-size, role = USER)
 └─ for each: try runner.purgeOne(id, reason) catch → ERROR "탈퇴 정리 실패 memberId={} step={}" (다음 날 다시 대상)

WithdrawalPurgeRunner.purgeOne(memberId, reason)   @Transactional(REQUIRES_NEW)
 1. SELECT … FROM member WHERE id = :m FOR UPDATE; 조건 재확인 (아니면 그대로 끝 — 그 사이 복구 등)
 2. reason = SUSPENDED_PERMANENT 이면 UPDATE member SET status='WITHDRAWN', withdrawn_at=:now …
 3. emailHash = sha256(auth_identity.email) (없으면 null)
 4. steps.sortedBy(order).forEach(s -> s.purge(m))   — 예외는 WithdrawalPurgeStepException(order, 이름)으로 감싸 다시 던짐
 5. afterCommit: WithdrawalRedisCleaner.clean(m, emailHash)
```

대상 SQL

```sql
-- 유예 30일 경과 (ix_member_withdraw_purge)
SELECT id FROM member
 WHERE status = 'WITHDRAWN' AND deleted_at IS NULL AND withdrawn_at < :now - :grace
 ORDER BY withdrawn_at, id LIMIT :batchSize;

-- 영구 정지 1년 경과 (research R11)
SELECT m.id FROM member m
 WHERE m.status = 'SUSPENDED' AND m.deleted_at IS NULL AND m.role = 'USER'
   AND EXISTS (SELECT 1 FROM member_suspension s
                WHERE s.member_id = m.id AND s.lifted_at IS NULL AND s.ends_at IS NULL
                  AND s.started_at < :now - :suspendedPurgeAfter)
 ORDER BY m.id LIMIT :batchSize;
```

## 4. 커밋 후 Redis 정리 (`WithdrawalRedisCleaner`)

| 대상 | 방법 | 실패 |
|---|---|---|
| 세션 | 001 `SessionTerminator.terminateAll(m, empty)` | WARN (익명 회원 세션은 이미 비로그인 취급) |
| 인증·재설정 토큰 | 001 `AuthTokenStore.revokeLatest(VERIFY\|RESET, m)` (이 기능이 추가) | WARN |
| 실패·요청 횟수 | `blog.withdraw.purge.redis-key-templates`의 `{memberId}`·`{emailHash}` 치환 → `DEL` | WARN (모두 TTL 있음) |

`SCAN`으로 키 공간을 훑지 않는다.

## 5. 신청·복구 이벤트와 메일

| 이벤트 | 발행 시점 | 필드 | 구독 |
|---|---|---|---|
| `MemberWithdrawn` | 신청 트랜잭션(세션 삭제 성공 뒤) | `memberId`, `withdrawnAt` | account `WithdrawalMailListener` → `mail/withdrawal-requested.txt`(이메일 있는 계정만) |
| `MemberRestored` | 복구 트랜잭션 | `memberId`, `restoredAt` | account `WithdrawalMailListener` → `mail/account-restored.txt` |

- 구독은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`(메일은 `mailExecutor`). 012는 구독하지 않는다(012 research R14 — 트렌딩·검색·sitemap이 요청 때 공용 조건으로 거름). 실패는 WARN만.
- 메일 본문: 접수 "탈퇴 신청이 접수됐어요. {기한}까지 로그인하면 복구할 수 있어요. 본인이 신청하지 않았다면 로그인해서 복구하고 비밀번호를 바꿔 주세요" / 복구 "계정이 복구됐어요. 블로그와 글이 다시 보여요". 기한은 `blog.time-zone` 기준 "2026년 11월 7일 오후 3:20".
- 정리 작업·영구 정지 자동 정리는 이벤트도 메일도 없다.

## 6. 다른 기능이 지켜야 할 것

| 기능 | 약속 |
|---|---|
| 003 | `ImagePurgeService.detachAllByUploader`는 `MANDATORY`, 파일을 직접 지우지 않는다 |
| 006 | `PostPurgeService.purgeAllByAuthor`는 `MANDATORY`, 글마다 `PostPurged` |
| 007 | `CommentPurgeService.purgeByAuthor`는 `MANDATORY`, 정리 뒤 카운터 불변식 |
| 009 | `LikePurgeService.purgeByMember`는 `MANDATORY`, 이벤트 없음 |
| 010·011·014 | 각자 tasks에서 §2 표의 단계 클래스를 만든다. 015보다 먼저 구현하면 §1 인터페이스(015 tasks T009)를 먼저 만든다 — 한 파일 |
| 회원 번호 Redis 키를 새로 만드는 기능 | `blog.withdraw.purge.redis-key-templates`에 한 줄 더한다 |
