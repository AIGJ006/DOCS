# 계약: 팔로우 저장·수·목록·피드 SQL, 이벤트, 공개 Service

**Feature**: 010-follow-feed | **Date**: 2026-10-08

근거: 24 §4·§5·§6, 20 §3-4, research R4~R10.

## 1. 저장 (`FollowRepository`, interaction.infra)

```sql
-- 팔로우 상태로 (행이 오면 새로 생김 → MemberFollowed)
INSERT INTO follow (follower_id, followee_id, created_at)
VALUES (:me, :target, :now)
ON CONFLICT DO NOTHING
RETURNING follower_id;

-- 팔로우 해제 상태로 (행이 오면 지워짐 → MemberUnfollowed)
DELETE FROM follow WHERE follower_id = :me AND followee_id = :target
RETURNING follower_id;
```

- 두 문장 모두 `FollowService`의 `@Transactional` 안. 이어서 §2 팔로워 수를 같은 트랜잭션에서 센다.
- 자기 팔로우는 서버가 먼저 400으로 거부하고, 그래도 들어오면 `ck_follow_self`가 막는다(서버 판정이 먼저라 실제로는 도달하지 않는다).

## 2. 수 (`FollowRepository.countFollowers` / `countFollowing`)

```sql
SELECT count(*) FROM follow f
 WHERE f.followee_id = :target
   AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.follower_id
                    AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL);

SELECT count(*) FROM follow f
 WHERE f.follower_id = :target
   AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.followee_id
                    AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL);
```

- `NOT EXISTS`는 부분 인덱스 `ix_member_withdraw_purge`로 유예 회원만 본다(24 §5 측정: 1천 명 약 1ms, 1만 명 약 5ms, 10만 명 약 30ms).

## 3. 목록 (`FollowRepository.pageFollowers` / `pageFollowing`)

```sql
SELECT f.created_at, m.id, m.handle, m.nickname, m.bio,
       COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key
  FROM follow f
  JOIN member m ON m.id = f.follower_id AND m.status <> 'WITHDRAWN'
  LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
       AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
 WHERE f.followee_id = :target
   [AND (f.created_at, f.follower_id) < (:cursorAt, :cursorId)]
 ORDER BY f.created_at DESC, f.follower_id DESC
 LIMIT :limit;   -- page-size + 1

-- 보는 사람의 팔로우 여부 (로그인했을 때만, 항목이 있을 때만)
SELECT followee_id FROM follow WHERE follower_id = :viewer AND followee_id = ANY(:ids);
```

- 팔로잉 목록은 `f.follower_id = :target`, JOIN `m.id = f.followee_id`, 정렬·커서 둘째 키 `f.followee_id`.
- 사진 키 → 주소는 media `ImageUrlResolver.publicUrl`.

## 4. 피드 (`PostCardQueryRepository`, discovery.infra)

```sql
-- 005 카드 SELECT … WHERE {VisibilityFilter.forViewer(Viewer.anonymous(), null)}
   AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :followerId AND f.followee_id = p.author_id)
   [AND (p.first_public_at, p.id) < (:cursorAt, :cursorId)]
 ORDER BY p.first_public_at DESC, p.id DESC
 LIMIT :limit;   -- 9 + 1

-- 첫 페이지가 비었을 때만
SELECT EXISTS (SELECT 1 FROM follow WHERE follower_id = :me);
```

- `CardFilter(authorId, tagId, followerId)` — 008이 만든 레코드에 `followerId`를 더한다. 008이 아직이면 이 기능이 `CardFilter(authorId, followerId)`로 만들고 008이 `tagId`를 더한다.
- 노출 조건 문구는 004 `VisibilityFilter`가 준다. 이 기능은 조건을 다시 쓰지 않는다.

## 5. 이벤트 (`shared.event`)

```java
public record MemberFollowed(long followerId, long followeeId, Instant followedAt) implements DomainEvent {}
public record MemberUnfollowed(long followerId, long followeeId, Instant unfollowedAt) implements DomainEvent {}
```

- 실제로 바뀐 경우에만, 트랜잭션 안에서 발행(구독자는 커밋 후).
- 구독: 011 새 팔로워 알림(같은 사람 7일 1번 — 011 규칙), 안 읽은 묶음에서 빼기.

## 6. 다른 기능이 부르는 Service (`FollowQueryService`, interaction.application)

| 서명 | 쓰는 곳 |
|---|---|
| `HeaderStats headerStats(long ownerId, Viewer viewer)` → `{followerCount, followingCount, followedByMe}` | 005 `BlogQueryService.getHeader` |
| `boolean isFollowing(long followerId, long followeeId)` (PK `EXISTS`) | `AuthorFollowStatusQueryAdapter`(005 포트 구현) |
| `List<Long> followerIdsOf(long memberId)` (유예 회원 제외) | 다른 기능용으로 공개만 해 둔다(011은 25 §4-1대로 직접 `INSERT … SELECT`) |

## 7. 탈퇴 정리 단계 (015 계약 구현)

```java
@Component
class FollowWithdrawalPurgeStep implements WithdrawalPurgeStep {
    public int order() { return 65; }
    @Transactional(propagation = MANDATORY)
    public void purge(long memberId) {
        // DELETE FROM follow WHERE follower_id = :m OR followee_id = :m
    }
}
```

- 이벤트 없음, 지운 행 수만 INFO 로그.
- 015 `blog.withdraw.purge.redis-key-templates`에 `ratelimit:follow:{memberId}`를 더한다.
