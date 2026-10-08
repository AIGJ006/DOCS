# Data Model: 좋아요와 조회수

**Feature**: `009-like-view` | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

**기준**: `backend/src/main/resources/db/migration/V1__common_schema.sql`의 `post_like`·`post_view_daily`·`post`(카운터)와 [docs/30-like.md](../../docs/30-like.md)·[docs/31-view-count.md](../../docs/31-view-count.md).

**스키마 변경**: **없음.** V1의 컬럼·제약·인덱스만 쓴다(헌법 I). 새 마이그레이션 번호를 쓰지 않는다.

---

## 1. 이 기능이 쓰는 엔티티

### 1-1. `post_like` — 좋아요 (interaction 모듈 소유)

| 컬럼 | 타입 | NULL | 쓰임 |
|---|---|---|---|
| `post_id` | bigint FK → post CASCADE | 불가 | 글 완전 삭제 때 함께 삭제(FR-020) |
| `member_id` | bigint FK → member RESTRICT | 불가 | 누른 회원. 탈퇴 정리 때 이 기능의 `LikePurgeService`가 먼저 지운다 |
| `created_at` | timestamptz | 불가 | 누른 시각(트렌딩 점수식 B 대비) |

PK `(post_id, member_id)` — 1인 1글 1건(FR-001). 인덱스 `ix_post_like_member (member_id, created_at DESC)` — 탈퇴 정리, (개인 확장) 내가 좋아요한 글.

### 1-2. `post_view_daily` — 일별 조회수 (interaction 모듈 소유)

| 컬럼 | 타입 | NULL | 쓰임 |
|---|---|---|---|
| `post_id` | bigint FK → post CASCADE | 불가 | |
| `view_date` | date | 불가 | 조회가 일어난 날짜(서비스 시간대 Asia/Seoul) |
| `views` | integer | 불가 | 그날 합계(`ck_post_view_daily_views > 0`) |

PK `(post_id, view_date)` — upsert 대상. 인덱스 `ix_post_view_daily_date (view_date, post_id) INCLUDE (views)` — 90일 정리, 012 점수식 B.

### 1-3. `post` 카운터 (post 모듈 소유, `PostCounterService`로만)

| 컬럼 | 바뀌는 때 | 방법 |
|---|---|---|
| `like_count` integer | 좋아요가 실제로 생김 +1 / 실제로 지워짐 −1 / 탈퇴 정리 −n / 보정 배치 = 실제 건수 | 좋아요와 같은 트랜잭션 |
| `view_count` bigint | 1분 반영 +n | 글마다 트랜잭션, `updated_at` 그대로 |

`ck_post_counts`가 음수를 막는다. 불변식: `like_count = count(post_like WHERE post_id = p)`(SC-002·003).

## 2. Redis 키

| 키 | 형 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|---|
| `ratelimit:like:{memberId}` | 문자열 | 고정 창 횟수(001 `RateLimiter`) | 1분 | 좋아요·취소 합쳐 60번 |
| `ratelimit:view:{visitorKey}` | 문자열 | 같음 | 1분 | 조회 기록 60번 |
| `view:seen:{postId}:{visitorKey}` | 문자열 | 기간 안 조회 횟수 | `dedupe-window`(첫 조회부터) | 중복 판정 |
| `view:pending:{yyyyMMdd}` | Hash | postId → 모은 조회 수 | 48시간(안전망) | 1분 반영 대상 |
| `view:processing:{yyyyMMdd}:{uuid}` | Hash | 같음 | 없음(반영 뒤 비면 사라짐) | 반영 중 묶음, 중단 복구 |
| `view:salt:{yyyyMMdd}` | 문자열 | 32바이트 난수(Base64) | 26시간 | 쿠키 없는 비회원 해시 |

`visitorKey`: `m:{memberId}` / `v:{vid UUID}` / `h:{base64url(SHA-256(IP\nUA\nsalt))}`. 원래 IP는 어떤 키·값에도 없다(SC-010).

## 3. 쿠키

| 이름 | 값 | 속성 | 발급 |
|---|---|---|---|
| `vid` | UUID v4 | `Max-Age=31536000; Path=/; HttpOnly; Secure; SameSite=Lax` | 비회원이 상세 API·글 상세 페이지 셸을 받을 때 없으면(`VisitorIdCookieFilter`) |

## 4. 응답·오류

### 4-1. 좋아요 응답 `LikeState`

```json
{ "liked": true, "likeCount": 13 }
```

### 4-2. 이유 코드 `LikeReasonCode` (interaction.domain)

| code | status | message |
|---|---|---|
| `CANNOT_LIKE_OWN_POST` | 400 | 내 글에는 좋아요를 누를 수 없어요 |

공통 코드: `LOGIN_REQUIRED` 401, `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`·`CSRF_REJECTED` 403, `NOT_FOUND` 404, `TOO_MANY_REQUESTS` 429(+ `Retry-After`).

## 5. 이벤트 (`shared.event`)

| 이벤트 | 필드 | 발행 |
|---|---|---|
| `PostLiked` | `postId, postAuthorId, memberId, likedAt` | 좋아요가 실제로 생긴 트랜잭션 |
| `PostUnliked` | `postId, postAuthorId, memberId, unlikedAt` | 실제로 지워진 트랜잭션 |

이미 그 상태였던 요청·탈퇴 정리·글 완전 삭제는 발행하지 않는다.

## 6. 설정값

research R14 그대로(`blog.like.*`, `blog.view.*`).

## 7. 상태 전이 (좋아요 한 쌍)

```text
(없음) ──PUT──▶ [좋아요] ──DELETE──▶ (없음)
   ▲  PUT(이미 있음): 변화 없음, 이벤트 없음        DELETE(이미 없음): 변화 없음
   └── 글 완전 삭제(CASCADE)·탈퇴 30일 정리(LikePurgeService) ──
글이 비공개·휴지통·숨김이 되어도 그대로 보존(보이지만 않음, FR-019)
```
