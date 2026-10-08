# Data Model: 팔로우·팔로잉 피드

**Feature**: 010-follow-feed | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

새 테이블·컬럼·인덱스는 없다(V1 그대로, research R1).

## 1. 이 기능이 쓰는 엔티티

### 1-1. `follow` — 팔로우 관계 (interaction 모듈 소유)

| 컬럼 | 타입 | 규칙 |
|---|---|---|
| `follower_id` | bigint | 팔로우하는 회원. FK `member(id) ON DELETE RESTRICT` |
| `followee_id` | bigint | 팔로우받는 회원. FK `member(id) ON DELETE RESTRICT` |
| `created_at` | timestamptz | 팔로우한 시각. 목록 정렬 키 |

- PK `(follower_id, followee_id)` — 쌍마다 하나(SC-001)
- `ck_follow_self CHECK (follower_id <> followee_id)` — 자기 팔로우 저장 불가(SC-002)
- `ix_follow_followee (followee_id, created_at DESC)` — 팔로워 목록·수
- `ix_follow_follower (follower_id, created_at DESC)` — 팔로잉 목록·수
- 탈퇴 유예 중에는 지우지 않는다. 익명 처리 때 015 order 65가 양방향 삭제

### 1-2. 읽기만 하는 것

| 테이블 | 쓰임 | 모듈 경계 |
|---|---|---|
| `member` | 대상 확인(001 `MemberQueryService.findReadableBlogOwner`), 목록 표시(닉네임·주소·소개), 탈퇴 유예 제외(`status`·`deleted_at`) | 대상 확인은 공개 Service, 목록·수 SQL은 Complexity Tracking 예외 |
| `image` | 목록 항목의 현재 프로필 사진(`uq_image_profile_current` 술어) | Complexity Tracking 예외 |
| `post` | 피드 카드(005 카드 SQL) | discovery 소유 조회, 노출 조건은 004 `VisibilityFilter` |

## 2. 상태 전이 (회원 한 쌍)

```text
           PUT (행 생김 → MemberFollowed)
  없음 ──────────────────────────────▶ 팔로우 중
   ▲                                     │
   └─────────────────────────────────────┘
           DELETE (행 지워짐 → MemberUnfollowed)

  PUT on 팔로우 중 / DELETE on 없음 → 200, 변화 없음, 이벤트 없음
  한쪽이 탈퇴 유예 → 행은 그대로, 수·목록·피드에서만 빠짐 → 복구하면 돌아옴
  한쪽이 익명 처리 → 015 order 65가 행 삭제 (이벤트 없음)
```

## 3. 응답

### 3-1. `FollowState` — `PUT`/`DELETE /api/members/{handle}/follow`

| 필드 | 타입 | 설명 |
|---|---|---|
| `following` | boolean | 지금 내가 팔로우 중인가 |
| `followerCount` | integer | 대상의 최신 팔로워 수(탈퇴 유예 회원 제외) |

### 3-2. `FollowListItem` — 팔로워·팔로잉 목록 항목

| 필드 | 타입 | 설명 |
|---|---|---|
| `handle` | string | 블로그 주소 |
| `nickname` | string | 닉네임 |
| `profileImageUrl` | string? | 작은 프로필 사진(없으면 null → 기본 아이콘) |
| `bio` | string? | 소개 원문(화면은 첫 줄만) |
| `followedByMe` | boolean | 보는 사람이 이 회원을 팔로우 중인가(비회원은 false) |
| `isMe` | boolean | 보는 사람 자신인가(버튼 없음) |

목록 응답 `{items: FollowListItem[], nextCursor: string | null}`.

### 3-3. `FeedPage` — `GET /api/feed`

`{items: PostCard[], nextCursor: string | null, hasFollowing: boolean}`. `PostCard`는 005 카드 형식 그대로. `hasFollowing`은 첫 페이지가 비었을 때만 실제로 확인하고 그 밖에는 true(research R7).

### 3-4. `BlogHeader` 확장 — `GET /api/members/{handle}` (005)

| 추가 필드 | 타입 | 설명 |
|---|---|---|
| `followerCount` | integer | 탈퇴 유예 회원 제외 |
| `followingCount` | integer | 탈퇴 유예 회원 제외 |
| `followedByMe` | boolean | 비회원·내 블로그면 false |

### 3-5. 글 상세 (005)

`viewer.followingAuthor`(005 필드)를 이 기능의 `AuthorFollowStatusQueryAdapter`가 채운다. 형식 변화 없음.

## 4. 커서

| 목록 | `ListScope` 값 | 정렬 키 `k` |
|---|---|---|
| 팔로워 | `followers:{handle}` | `[created_at 마이크로초, follower_id]` |
| 팔로잉 | `following:{handle}` | `[created_at 마이크로초, followee_id]` |
| 피드 | `feed` | `[first_public_at 마이크로초, post id]` (005 `PostListCursor`) |

다른 목록의 커서·깨진 커서 → 400 `INVALID_CURSOR`(001 `CursorCodec`).

## 5. 이유 코드 (`FollowReasonCode`, interaction.domain)

| 코드 | 상태 | 문구 | 근거 |
|---|---|---|---|
| `CANNOT_FOLLOW_SELF` | 400 | 자기 자신은 팔로우할 수 없어요 | 24 §3 코드, 문구 제안 |

공통 코드: `LOGIN_REQUIRED`(401), `ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED`(403), `NOT_FOUND`(404 본문 고정), `INVALID_CURSOR`(400), `TOO_MANY_REQUESTS`(429 + `Retry-After`).

## 6. 이벤트 (`shared.event`)

| 이벤트 | 필드 | 발행 조건 | 구독 |
|---|---|---|---|
| `MemberFollowed` | `long followerId, long followeeId, Instant followedAt` | `INSERT … RETURNING`이 행을 돌려줌 | 011 새 팔로워 알림 |
| `MemberUnfollowed` | `long followerId, long followeeId, Instant unfollowedAt` | `DELETE … RETURNING`이 행을 돌려줌 | 011 안 읽은 묶음에서 빼기 |

## 7. Redis

| 키 | 쓰임 | TTL |
|---|---|---|
| `ratelimit:follow:{memberId}` | 팔로우·언팔로우 합산 1분 30번 | 1분 |

## 8. 설정값 (`blog.follow.*`, `FollowProperties`)

| 키 | 기본 | 설명 |
|---|---|---|
| `rate-limit.limit` | 30 | 1분당 요청 수 |
| `rate-limit.window` | 1m | 창 |
| `list-page-size` | 20 | 팔로워·팔로잉 목록 한 번에 |
