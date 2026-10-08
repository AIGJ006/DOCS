# Data Model: 댓글·답글

**Feature**: `007-comment` | **Date**: 2026-10-08 | **Plan**: [plan.md](./plan.md)

**기준**: `backend/src/main/resources/db/migration/V1__common_schema.sql`(51 통합 ERD를 옮긴 공통 스키마)의 `comment`와 [docs/21-comment.md](../../docs/21-comment.md) §15.

**스키마 변경**: **없음.** V1의 컬럼·제약·인덱스만 쓴다(헌법 I). 새 마이그레이션 번호를 쓰지 않는다.

---

## 1. 이 기능이 쓰는 엔티티

### 1-1. `comment` — 댓글 (interaction 모듈 소유, 읽기·쓰기)

| 컬럼 | 타입 | NULL | 이 기능에서의 쓰임 |
|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | 응답 `id`, 바로 가기 `#comment-{id}` |
| `post_id` | bigint FK → post CASCADE | 불가 | 글 완전 삭제 때 남의 댓글까지 함께 삭제(006) |
| `author_id` | bigint FK → member RESTRICT | 불가 | 수정·삭제 조건 `= :me`. 탈퇴 정리 전까지 회원 행이 남아 있어야 한다 |
| `parent_id` | bigint NULL | 허용 | 최상위면 NULL, 답글이면 **항상 최상위** 번호. 복합 FK `(post_id, parent_id) → comment(post_id, id) ON DELETE CASCADE` — 다른 글의 댓글을 부모로 둘 수 없다 |
| `reply_to_member_id` | bigint FK → member RESTRICT | 허용 | 답글의 답글일 때 대상 회원(R4). `ck_comment_reply_to`: 부모가 있을 때만 |
| `content` | varchar(1000) | 불가 | 정리한 내용(코드 포인트 1~1000). 자리로 남기면 `''`(`ck_comment_content`가 `deleted_at` 있을 때 빈 값 허용) |
| `created_at` | timestamptz | 불가 | 정렬·커서 `(created_at, id)`, 중복 창 10초 |
| `updated_at` | timestamptz | 불가 | 내용을 고칠 때만 바뀜. `updated_at > created_at`이면 "수정됨"(`ck_comment_edited`) |
| `deleted_at` | timestamptz | 허용 | "삭제된 자리" 표시. 답글 있는 최상위 삭제·탈퇴 정리 자리에서만 값이 있다(그 밖의 삭제는 행 DELETE) |
| `hidden_at`·`hidden_by`·`hidden_reason` | timestamptz·bigint FK→member·varchar(30) | 허용 | 관리자 숨김(014가 `CommentModerationService`로 기록) |

인덱스와 쓰임:

| 인덱스 | 쓰는 쿼리 |
|---|---|
| `ix_comment_root (post_id, created_at, id) WHERE parent_id IS NULL` | 최상위 목록·커서·around 앞 확인 |
| `ix_comment_reply (parent_id, created_at, id) WHERE parent_id IS NOT NULL` | 답글 미리보기(LATERAL LIMIT 3)·답글 수·펼치기 |
| `ix_comment_author (author_id)` | 수정·삭제 조건, 10초 중복 조회, 탈퇴 정리 |
| `uq_comment_post_id (post_id, id)` | 복합 FK 대상 |

다른 테이블의 FK: `report_case.comment_id … ON DELETE SET NULL`(신고는 스냅샷이 있어 남음, 014), `notification.comment_id … ON DELETE CASCADE`(행 삭제 때 알림도 삭제, 011).

### 1-2. `post.comment_count` (post 모듈 소유, `PostCounterService`로만 증감)

| 사건 | 증감 | 같은 트랜잭션 |
|---|---|---|
| 작성(최상위·답글) | +1 | 작성 |
| 정상 댓글 삭제(자리로 남김·행 삭제 모두) | −1 | 삭제 |
| 숨김 댓글 삭제 | 0 | 삭제 |
| 빈 자리 정리(최상위 자리 DELETE) | 0 | 그 답글 삭제 |
| 관리자 숨김 / 해제 | −1 / +1 | 014 호출 |
| 탈퇴 유예 시작 / 복구 | 0 / 0 | — |
| 탈퇴 30일 정리 | −(그 글의, 그 회원의 정상 댓글 수) | 015 order 20 |

불변식: `comment_count = count(*) WHERE post_id = p AND deleted_at IS NULL AND hidden_at IS NULL`(SC-002). `ck_post_counts`가 0 미만을 막는다.

## 2. 상태 전이 (한 댓글)

```text
            작성
             │
             ▼
          [정상] ──수정──▶ [정상(수정됨)]
           │  │  ▲
     숨김(014)│  │해제(014)
           ▼  │  │
          [숨김] ─────────┐
             │            │ 작성자 삭제
   작성자 삭제 │            ▼
             ▼     답글 있는 최상위 → [삭제된 자리] ─마지막 답글 삭제─▶ (행 삭제)
         (행 삭제)  그 밖           → (행 삭제)

  작성자 탈퇴 유예: 표시만 [탈퇴 작성자] (데이터 그대로, 복구하면 원래대로)
  탈퇴 30일 정리: 남의 답글 있는 최상위 → [삭제된 자리 + 익명 작성자] = "탈퇴한 사용자의 댓글이에요", 나머지 → (행 삭제)
```

- "삭제된 자리"는 되살릴 수 없다(FR-033). 숨김 → 해제는 014만.
- 숨김인 최상위에 답글이 있으면 답글은 그대로 보인다(FR-037).

## 3. 표시 상태 `CommentState` (응답, 저장 아님)

| 우선 | 상태 | 조건 | `author` | `content` | 화면 문구 |
|---|---|---|---|---|---|
| 1 | `WITHDRAWN_AUTHOR` | 작성자 `status = WITHDRAWN` 또는 `deleted_at IS NOT NULL` | null | null | 작성자 "탈퇴한 사용자"(회색 아이콘), "탈퇴한 사용자의 댓글이에요" |
| 2 | `DELETED` | `comment.deleted_at IS NOT NULL` | null | null | "삭제된 댓글이에요" |
| 3 | `HIDDEN` | `hidden_at IS NOT NULL` | 작성자 본인만 | 작성자 본인만 | 남: "운영 정책에 따라 숨겨진 댓글이에요" / 본인: 원문 + "숨겨졌어요 (나만 보여요)" |
| 4 | `NORMAL` | 그 밖 | 있음 | 있음 | 작성자·내용 |

## 4. 응답 모델

### 4-1. `CommentView`

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | long | |
| `state` | `NORMAL`·`DELETED`·`HIDDEN`·`WITHDRAWN_AUTHOR` | §3 |
| `content` | string \| null | §3 규칙 |
| `createdAt` | string(date-time) | |
| `edited` | boolean | `updated_at > created_at` |
| `author` | `{handle, nickname, profileImageUrl, isPostAuthor}` \| null | §3 규칙 |
| `replyTo` | `{handle, nickname}` \| `{withdrawn: true}` \| null | 답글의 답글만 |
| `mine` | boolean | 보는 사람이 작성자인가(상태와 무관, 숨김 본인 판단에 씀). 비회원은 false |
| `parentId` | long \| null | |
| `replyCount` | int | 최상위만, 그 아래 답글 수(숨김 포함) |
| `replies` | `CommentView[]` | 최상위만, 처음 3개(또는 around 대상까지) |
| `repliesNextCursor` | string \| null | 최상위만 |

### 4-2. 목록

| 이름 | 모양 |
|---|---|
| `CommentPage` | `{ items: CommentView[], nextCursor, prevCursor, focusCommentId }` — `prevCursor`·`focusCommentId`는 `around`일 때만 값 |
| `ReplyPage` | `{ items: CommentView[], nextCursor }` |

### 4-3. 이유 코드 `CommentReasonCode` (interaction.domain, `ReasonCode` 구현)

| code | status | message | 언제 |
|---|---|---|---|
| `COMMENT_REQUIRED` | 400 | 댓글 내용을 입력해 주세요 | 정리 결과가 빈 값 (field `content`) |
| `COMMENT_TOO_LONG` | 400 | 댓글은 1000자까지 쓸 수 있어요 | 코드 포인트 1000 초과 (field `content`) |
| `REPLY_TARGET_UNAVAILABLE` | 400 | 답글을 달 수 없는 댓글이에요 | 대상이 없음·다른 글·삭제·숨김·작성자 탈퇴 (field `replyToCommentId`) |
| `COMMENT_HIDDEN` | 409 | 숨겨진 댓글은 수정할 수 없어요 | 숨긴 내 댓글 수정 |

공통 코드 사용: `LOGIN_REQUIRED`(401), `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`·`CSRF_REJECTED`(403), `NOT_FOUND`(404, 본문 고정), `INVALID_CURSOR`(400), `TOO_MANY_REQUESTS`(429 + `Retry-After`).

내용 오류는 공통 `VALIDATION_FAILED` 본문의 `errors[]`에 칸 오류로 담는다(`{code: VALIDATION_FAILED, errors: [{field: content, code: COMMENT_TOO_LONG, message}]}`). `COMMENT_HIDDEN`은 칸 오류가 아니라 최상위 `code`다.

## 5. 다른 모듈 공개 API 추가

| 모듈 | 추가 | 모양 |
|---|---|---|
| post | `PostCounterService` (신규) | `adjustCommentCount(long postId, int delta)`, `adjustCommentCounts(Map<Long, Integer> deltas)` — `MANDATORY` 트랜잭션 |
| account | `MemberQueryService.findDisplays(Collection<Long> ids)` | `Map<Long, MemberDisplay>`; `MemberDisplay(long id, String handle, String nickname, boolean withdrawn)` — 익명 처리된 회원도 `withdrawn = true`로 돌려줌(handle·nickname null) |
| shared.event | `CommentCreated`, `CommentDeleted` | contracts/events.md |

## 6. Redis 키

| 키 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|
| `ratelimit:comment:{memberId}` | 고정 창 횟수 (001 `RateLimiter`) | 1분 | 작성 1분 10개 |
| `ratelimit:comment-edit:{memberId}` | 같음 | 1분 | 수정 1분 20번 |

중복 방지 키(원문 `cmt:dedupe:*`)는 두지 않는다(research R7, PostgreSQL advisory lock).

## 7. 설정값 (`blog.comment.*`, 기본값)

research R15 그대로.
