# 댓글·답글 설계

> 작성일 2026-10-04 · 수정 2026-10-06 (검증 M28·L19·L29 반영) · 작성 강성찬 · 관련 요구사항: C-CMT-1(댓글 + 답글). 연관: [06 공개 범위](./06-visibility.md) §3, [09 닉네임](./09-nickname.md) §9, [12 본문 정화](./12-content-sanitize.md) §7-8, [13 삭제·탈퇴](./13-delete-withdraw.md) §3, [20 이벤트](./20-domain-events.md)
> 이미 정해진 것: 회원만 작성(이메일 인증 후, 07 L-2), 본인만 수정·삭제(01), 1000자(03), 글자만·줄바꿈 유지(12 §7-8), `닉네임 @블로그주소` 표시(09 N-9), 글을 읽을 수 있는 사람만 보기·쓰기(06 §3), 비공개로 바꾸면 함께 숨김(06 V-7), 탈퇴자 댓글 처리(13 D-7), 글 완전 삭제 시 함께 삭제(13 D-5).

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| CM-1 | 답글 깊이 | **공통 1단계.** `parent_id`는 항상 최상위 댓글을 가리킨다 | 01 Q6 제안 그대로. 깊이 확장은 개인 (§13) |
| CM-2 | 답글에 다시 답할 때 | **같은 최상위 댓글 아래에 달고 대상을 표시**한다 ("@나민서에게"). 대상은 `comment.reply_to_member_id`에 저장 | 1단계 구조를 지키면서 대화 상대를 알 수 있다 (유튜브 방식) |
| CM-3 | 최상위 댓글 목록 | **오래된 순, 20개씩 [댓글 더 보기]**, 커서 방식 | 대화 흐름대로 읽힌다. 댓글이 많아도 글 상세가 가볍다 |
| CM-4 | 답글 목록 | **처음 3개 + [답글 N개 더 보기]** (한 번에 20개씩) | 답글이 많은 댓글 하나가 화면을 덮지 않는다 |
| CM-5 | 형식 | 글자만, 줄바꿈 유지, 1~1000자. **주소는 링크로 바꾸지 않는다** | 12 §7-8 확정. 링크는 소개(11 R-2)와 같은 이유(스팸)로 글자 그대로 |
| CM-6 | 금칙어 | **필터 없음.** 문제 댓글은 신고로 처리 | 대화에서는 오탐(`시발점`)이 잦다. 09 §4-2의 범위 그대로 |
| CM-7 | 수정 | **언제든 수정, "수정됨" 표시.** 이력은 남기지 않는다 | 답글과 어긋난 것을 독자가 알 수 있다 |
| CM-8 | 삭제 | 본인만. 답글이 있는 최상위 댓글은 **"삭제된 댓글이에요"로 자리만 남기고 내용을 비운다.** 그 밖에는 행을 지운다 | 01 확정 + 13 D-7과 같은 방식(내용을 남기지 않음) |
| CM-9 | 관리자 숨김 표시 | 다른 사람에게는 **"운영 정책에 따라 숨겨진 댓글이에요"**, 작성자 본인에게는 **원문 + "숨겨졌어요 (나만 보여요)"** | 답글 흐름을 유지하고, 작성자는 왜 숨겨졌는지 확인할 수 있다 |
| CM-10 | 글 작성자의 권한 | **남의 댓글을 지울 수 없다** (본인만 삭제). 문제 댓글은 신고 | 01 "본인만 수정·삭제" 그대로. 글 작성자 권한은 개인 확장 |
| CM-11 | 댓글 수 (`post.comment_count`) | **내용이 보이는 댓글 수** = 삭제되지 않고 숨겨지지 않은 댓글(최상위 + 답글). 탈퇴 유예 중인 댓글은 센다 (13 §3-3) | 10 §2 "삭제되지 않은 댓글 수"를 숨김까지 포함해 정확히 정의. 트렌딩(김민서)과 공유 |
| CM-12 | 연타·도배 | 같은 사람이 같은 글에 같은 내용을 **10초 안에 다시 보내면 처음 댓글을 돌려준다.** 작성은 사용자당 1분에 10개 | 버튼만 막으면 네트워크 재전송·탭 두 개는 막지 못한다 |
| CM-13 | 글 작성자 표시 | 글 작성자가 쓴 댓글에 **[작성자]** 배지 | 대화에서 누가 글쓴이인지 바로 보인다 |

---

## 2. 구조

```
최상위 댓글 A (parent_id = null)
 ├ 답글 B  (parent_id = A)
 ├ 답글 C  (parent_id = A, reply_to_member_id = B의 작성자)   ← B에 답함 → "@B에게"
 └ 답글 D  (parent_id = A)                                    ← A에 답함 → 대상 표시 없음
최상위 댓글 E
```

| 컬럼 | 의미 |
|---|---|
| `parent_id` | 속한 최상위 댓글. 최상위는 `null`. **답글을 가리키지 않는다** (CM-1) |
| `reply_to_member_id` | 답글에 답했을 때 그 답글의 작성자. 최상위 댓글에 바로 단 답글은 `null` (최상위 작성자가 당연한 대상이라 표시하지 않음) |

`reply_to_member_id`는 댓글이 아니라 **회원**을 가리킨다. 대상 답글이 나중에 지워져도 "누구에게 한 말인지"는 남고, 회원 행은 탈퇴해도 익명 껍데기로 남기 때문에(13 D-10) 깨지지 않는다.

1단계 구조에서는 **답글에 자식이 생기지 않는다.** 그래서 "삭제된 댓글"로 자리를 남기는 경우는 최상위 댓글뿐이다.

---

## 3. 화면

```
댓글 42
┌─────────────────────────────────────────────┐
│ 댓글을 남겨 보세요                              │
│                                    0 / 1000 │
└─────────────────────────────────────────────┘                    [등록]

(김) 김민서 @kim755030 [작성자] · 3시간 전
     JPA fetch join 쓸 때 페이징은 어떻게 하나요?
     [답글]  [수정] [삭제]                        ← 수정·삭제는 본인에게만, 남에게는 [신고]
   ↳ (나) 나민서 @na_ms · 2시간 전
         batch size를 쓰면 돼요
         [답글] [신고]
   ↳ (강) 강성찬 @eueu2 · 1시간 전 · 수정됨
         @나민서에게
         좋은 방법이네요. 링크도 있나요?
   ↳ (회색) 운영 정책에 따라 숨겨진 댓글이에요
   [답글 5개 더 보기]

(회색) 삭제된 댓글이에요
   ↳ (박) 박지훈 @park · 어제
         저도 궁금해요

[댓글 더 보기]
```

### 3-1. 상태별 표시

위에서부터 먼저 해당하는 것 하나를 적용한다.

| 순서 | 상태 | 판별 | 다른 사람에게 | 작성자 본인에게 |
|---|---|---|---|---|
| 1 | 탈퇴한 사람의 댓글 | 작성자 `withdrawn_at` 또는 `deleted_at` 있음 | 작성자 "탈퇴한 사용자"(회색 아이콘), 내용 "탈퇴한 사용자의 댓글이에요" (13 §3-4) | (로그인해도 복구 화면만 보인다) |
| 2 | 삭제된 자리 | `deleted_at` 있음 | 작성자 정보 없이 "삭제된 댓글이에요" | 같음 |
| 3 | 관리자 숨김 | `hidden_at` 있음 | 작성자 정보 없이 "운영 정책에 따라 숨겨진 댓글이에요" | 원문 + "숨겨졌어요 (나만 보여요)" (CM-9) |
| 4 | 정상 | — | `닉네임 @블로그주소`, 내용 | 같음 + [수정] [삭제] |

| 요소 | 규칙 |
|---|---|
| 작성자 | 프로필 이미지(없으면 기본 아이콘, 11 §4-3) + `닉네임 @블로그주소` (09 N-9). 누르면 `/@블로그주소` |
| 시각 | `created_at`, 10 L-5와 같은 형식 ("3시간 전", `2026.10.02`) |
| 수정됨 | `updated_at > created_at`이면 "· 수정됨" |
| 대상 표시 | `reply_to_member_id`가 있으면 내용 위에 "@닉네임에게" (지금 닉네임. 탈퇴했으면 "탈퇴한 사용자에게") |
| 내용 | HTML 이스케이프 + CSS `white-space: pre-line`(줄바꿈 유지). `<br>`을 직접 끼워 넣지 않는다 |
| 버튼 | [답글]: 정상 상태의 댓글에만. [수정]·[삭제]: 본인 정상 댓글. [신고]: 남의 정상 댓글 |
| 비회원 | 입력칸 자리에 "로그인하고 댓글을 남겨 보세요 [로그인]" |
| 이메일 미인증 | 입력칸 자리에 "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]" (07 §3) |
| 빈 상태 | "첫 댓글을 남겨 보세요" |
| [댓글 더 보기]·[답글 N개 더 보기]·[이전 댓글 보기] 불러오는 중 | 버튼을 "불러오는 중…"으로 바꾸고 비활성화 (10 §4-4와 같음) |
| 불러오기 실패 | 버튼 자리에 "불러오지 못했어요 [다시 시도]" — 같은 커서로 다시 요청. 이미 보이는 댓글은 그대로 둔다 |
| 등록·수정·삭제 중 | 해당 버튼을 비활성화하고 "등록 중…" 등으로 바꾼다 (두 번 누르기 방지는 CM-12가 서버에서도 막음). 실패하면 입력한 내용을 그대로 두고 오류 문구 (§4) |
| 위치 표시 | 댓글 요소마다 `id="comment-{id}"`. 알림에서 `?comment={id}#comment-{id}`로 들어오면 그 댓글까지 불러와 스크롤하고 잠깐 강조한다 (§6 `around`) |

---

## 4. 입력 규칙

| 항목 | 규칙 |
|---|---|
| 정리 순서 | ① NFC 정규화 ② 보이지 않는 글자·방향 제어 문자·제어 문자 제거(**줄바꿈 `\n`은 남김**, 제거 대상은 12 §7-4와 같음) ③ `\r\n` → `\n` ④ 앞뒤 공백 제거 ⑤ 연달아 있는 빈 줄은 하나로 |
| 길이 | 정리한 뒤 **1~1000자.** 글자 수는 유니코드 코드 포인트 기준 (PostgreSQL `char_length`, Java `codePointCount`) — Java `length()`는 이모지를 2로 세서 DB와 어긋난다 |
| 형식 | 글자만. Markdown·HTML은 쓴 그대로 글자로 보인다 |
| 링크 | 자동 링크 없음 (CM-5) |
| 금칙어 | 없음 (CM-6) |
| 이모지 | 허용 |

| 오류 코드 | 화면 문구 |
|---|---|
| `COMMENT_REQUIRED` | "댓글 내용을 입력해 주세요" |
| `COMMENT_TOO_LONG` | "댓글은 1000자까지 쓸 수 있어요" |
| `REPLY_TARGET_UNAVAILABLE` | "답글을 달 수 없는 댓글이에요" (삭제·숨김·탈퇴, 다른 글의 댓글) |
| `COMMENT_HIDDEN` | "숨겨진 댓글은 수정할 수 없어요" |
| `RATE_LIMITED` | "잠시 후 다시 시도해 주세요" (429 + `Retry-After`) |

---

## 5. 작성

```
POST /api/posts/{postId}/comments
{ "content": "좋은 방법이네요", "replyToCommentId": 123 }    ← 최상위 댓글이면 replyToCommentId 생략
```

[42 §3 판정 순서](./42-permission-matrix.md)를 따른다: 계정 → 대상을 볼 수 있나(404) → 업무 규칙(400). 요청 제한은 대상과 무관하게 같은 결과를 주므로 계정 검사 바로 다음에 둔다.

| 순서 | 검사 | 실패 | 42 §3 |
|---|---|---|---|
| 1 | 로그인 | 401 (SSR은 로그인 화면) | ① |
| 2 | 계정 상태: 탈퇴 유예 / 이메일 인증 (07 L-2) | 403 `ACCOUNT_WITHDRAWN` / `EMAIL_NOT_VERIFIED` | ② |
| 3 | 요청 제한: 사용자당 1분에 10개 (Redis 카운터) | 429 | ②와 ③ 사이 (대상을 드러내지 않음) |
| 4 | 글: `PUBLISHED`이고, 휴지통이 아니고, `PostAccessPolicy.canRead(post, me)` | 404 (임시글·비공개·휴지통·없는 글 모두 같은 404, 06 R-4) | ③ |
| 5 | 내용 (§4) | 400 | ⑤ |
| 6 | 대상 댓글 (`replyToCommentId`가 있을 때): 같은 글, 정상 상태(삭제·숨김·작성자 탈퇴 아님) | 400 `REPLY_TARGET_UNAVAILABLE` | ⑤ |
| 7 | 중복 (CM-12): Redis `SET cmt:dedupe:{memberId}:{postId}:{sha256(내용+대상)} {commentId} NX EX 10` | 이미 있으면 그 댓글을 200으로 돌려준다 (새로 만들지 않음) | — |

읽을 수 없는 글에 빈 내용을 보내면 400이 아니라 404다 (글이 있는지 먼저 드러내지 않는다).

**대상 댓글에 따라 저장하는 값:**

| `replyToCommentId`가 가리키는 것 | `parent_id` | `reply_to_member_id` |
|---|---|---|
| 없음 | `null` (최상위) | `null` |
| 최상위 댓글 A | A | `null` |
| 답글 B (B의 `parent_id` = A) | A | B의 작성자 (단, 내가 내 답글에 답하면 `null`) |

**트랜잭션:**
```
① SELECT … FROM comment WHERE id = :parentId FOR SHARE      ← 최상위 댓글이 동시에 삭제되는 것과 순서를 맞춤 (§7)
② INSERT INTO comment (…)
③ UPDATE post SET comment_count = comment_count + 1 WHERE id = :postId   ← @Modifying (05 J-2)
④ CommentCreated 발행 (20 §3-2, 처리는 커밋 후)
```
응답: `201` + 화면에 그릴 댓글 하나 (§6과 같은 형식).

---

## 6. 조회

```
GET /api/posts/{postId}/comments?cursor={cursor}        ← 최상위 20개 + 각각의 처음 답글 3개
GET /api/comments/{rootId}/replies?cursor={cursor}      ← 답글 4번째부터 20개씩
```

```json
{
  "items": [
    { "id": 120, "state": "NORMAL", "content": "JPA fetch join 쓸 때…", "createdAt": "…", "edited": false,
      "author": { "handle": "kim755030", "nickname": "김민서", "profileImageUrl": null, "isPostAuthor": true },
      "replyTo": null,
      "mine": false,
      "replies": [ { "id": 121, "state": "NORMAL", "replyTo": null, … },
                   { "id": 124, "state": "NORMAL", "replyTo": { "handle": "na_ms", "nickname": "나민서" }, … } ],
      "replyCount": 8, "repliesNextCursor": "…" }
  ],
  "nextCursor": "…"
}
```

| 규칙 | 내용 |
|---|---|
| 권한 | 글을 읽을 수 없으면 404 (06 §3) |
| `state` | `NORMAL` / `DELETED` / `HIDDEN` / `WITHDRAWN_AUTHOR` (§3-1). `DELETED`·`WITHDRAWN_AUTHOR`와 남이 보는 `HIDDEN`은 `content`·`author`를 보내지 않는다 |
| 정렬 | `created_at ASC, id ASC` (오래된 순) |
| 커서 | 마지막 항목의 `(created_at, id)`를 Base64URL로. `WHERE (created_at, id) > (:t, :id)` |
| 개수 | 최상위 21개를 조회해 20개만 보내고 다음이 있는지 판단 (10 L-2와 같은 방식). 답글도 같음 |
| `replyCount` | 그 최상위 아래 답글 행 수 (숨김 포함, 화면에 자리가 보이므로) |
| 댓글 수 표시 | 머리말 "댓글 42"는 `post.comment_count` (CM-11) |
| SSR | 첫 20개는 글 상세 HTML에 포함. [댓글 더 보기]는 `?commentCursor=…` 링크로도 동작 (JS가 없을 때) |
| 캐시 | 글과 같음: `PUBLIC`이 아닌 글은 `Cache-Control: private, no-store` (06 R-5) |
| 특정 댓글부터 (`?around={commentId}`) | 알림에서 들어올 때(25 §2). 그 댓글의 최상위부터 20개를 보내고, 앞에 댓글이 더 있으면 `prevCursor`를 함께 보낸다([이전 댓글 보기], `(created_at, id) < …` 역순 21개 → 뒤집어서). 대상이 4번째 이후 답글이면 그 최상위의 답글을 대상까지 펼쳐 보낸다. 그 글의 댓글이 아니거나 삭제·숨김·없는 댓글이면 **오류 없이 처음 페이지**를 보낸다 (있는지 여부를 드러내지 않음) |

**쿼리 (페이지당 2번, N+1 없음):**

```sql
-- ① 최상위 21개 + 작성자
SELECT c.*, m.handle, m.nickname, m.profile_image_url, m.withdrawn_at, m.deleted_at AS member_deleted_at
FROM comment c JOIN member m ON m.id = c.author_id
WHERE c.post_id = :postId AND c.parent_id IS NULL
  AND (c.created_at, c.id) > (:t, :id)
ORDER BY c.created_at, c.id
LIMIT 21;

-- ② 위 최상위들의 처음 답글 3개 + 답글 수 + 작성자·대상 회원
SELECT * FROM (
  SELECT c.*, m.handle, m.nickname, m.profile_image_url, m.withdrawn_at,
         rt.handle AS reply_to_handle, rt.nickname AS reply_to_nickname,
         row_number() OVER (PARTITION BY c.parent_id ORDER BY c.created_at, c.id) AS rn,
         count(*)     OVER (PARTITION BY c.parent_id)                           AS reply_count
  FROM comment c
  JOIN member m        ON m.id  = c.author_id
  LEFT JOIN member rt  ON rt.id = c.reply_to_member_id
  WHERE c.parent_id = ANY(:rootIds)
) t WHERE rn <= 3
ORDER BY parent_id, created_at, id;
```

---

## 7. 수정

```
PATCH /api/comments/{commentId}   { "content": "…" }
```

| 항목 | 규칙 |
|---|---|
| 누가 | 작성자 본인만. 남의 댓글이면 404 (06 R-4) |
| 대상 | 정상 상태만. 삭제된 자리는 404, 숨겨진 댓글은 409 `COMMENT_HIDDEN` |
| 글 | 지금 그 글을 읽을 수 있어야 한다 (작성 때와 같은 4번 검사) |
| 바뀌는 것 | `content`, `updated_at = now()`. **`updated_at`은 내용을 바꿀 때만 바꾼다** (삭제는 `deleted_at`만) → "수정됨" 판단에 쓴다 |
| 바뀌지 않는 것 | `parent_id`, `reply_to_member_id`, `created_at`, 댓글 수 |
| 같은 내용 | 정리 후 같으면 아무것도 바꾸지 않는다 ("수정됨"이 붙지 않음) |
| 이력·알림 | 남기지 않는다, 보내지 않는다 |
| 요청 제한 | 사용자당 1분에 20번 |

---

## 8. 삭제

```
DELETE /api/comments/{commentId}
```

| 경우 | 처리 | 댓글 수 |
|---|---|---|
| 답글이 1개 이상 있는 최상위 댓글 | `content = ''`, `deleted_at = now()` → "삭제된 댓글이에요" | −1 (숨김 상태였으면 그대로) |
| 답글이 없는 최상위 댓글 | 행 삭제 | −1 (숨김 상태였으면 그대로) |
| 답글 | 행 삭제. 그 최상위가 "삭제된 자리"이고 남은 답글이 없으면 **최상위 자리도 행 삭제** | −1 (숨김 상태였으면 그대로). 자리 삭제는 변화 없음 |

| 항목 | 규칙 |
|---|---|
| 누가 | 작성자 본인만 (CM-10). 남의 댓글이면 404. 숨겨진 내 댓글도 지울 수 있다 |
| 동시에 답글이 달리는 경우 | 삭제는 최상위 행을 `FOR UPDATE`, 답글 작성은 `FOR SHARE`(§5 ①)로 잠가 순서를 정한다. 답글이 먼저 들어오면 자리로 남고, 삭제가 먼저면 답글은 400 `REPLY_TARGET_UNAVAILABLE` |
| 이벤트 | `CommentDeleted` (20 §3-2) → 그 댓글로 생긴 알림 삭제 |
| 복구 | 없다 |

---

## 9. 관리자 숨김 (나민서 신고·숨김과 연결)

숨기기·해제 API와 권한은 나민서님 문서가 정한다. 댓글 쪽 규칙은 아래와 같다.

| 항목 | 규칙 |
|---|---|
| 저장 | `comment.hidden_at`·`hidden_by`·`hidden_reason` (43 ERD 변경 제안) |
| 표시 | §3-1 3번 (CM-9) |
| 댓글 수 | 숨기면 −1, 해제하면 +1 (CM-11) |
| 숨겨진 댓글 | 수정 불가(409), 답글 불가(400), 작성자 본인 삭제는 가능 |
| 숨겨진 최상위의 답글 | 그대로 보인다 |
| 알림 | 숨긴 댓글로 생긴 알림은 지우고(20 §4-2), 작성자에게 `CONTENT_HIDDEN` |

---

## 10. 글 상태에 따른 댓글

| 글 | 댓글 보기 | 댓글 쓰기 | 댓글 행 |
|---|---|---|---|
| 임시글 | 404 | 404 (임시글에는 댓글이 없다) | — |
| 발행·공개 | 누구나 | 이메일 인증한 회원 | |
| 발행·비공개 | 글 작성자만 | 글 작성자만 | 유지 (06 V-7, 다시 공개하면 돌아옴) |
| 수정 중 (`post_draft` 있음) | 발행본 기준 그대로 | 그대로 | |
| 휴지통 | 404 | 404 | 유지 (13 D-3) |
| 완전 삭제 | — | — | 함께 삭제 (CASCADE, 13 D-5) |
| 글 작성자 탈퇴 유예 중 | 404 (글이 안 보임) | 404 | 유지 |
| 관리자가 글을 숨김 | 글 상세 규칙을 따른다 (나민서) | 404 | 유지 |

---

## 11. 회원 탈퇴

13 §3에서 정해진 규칙을 1단계 구조에 맞춰 적는다.

| 시점 | 처리 |
|---|---|
| 유예 중 | 행은 그대로. 화면에서만 "탈퇴한 사용자의 댓글이에요" (§3-1 1번). **댓글 수는 그대로** (13 §3-3 기준) |
| 복구 | 그대로 돌아온다 |
| 30일 뒤 (13 §3-3 2번) | 내 최상위 댓글 중 **답글이 있는 것**: `content = ''`, `deleted_at = now()`. **그 밖의 내 댓글**(답글 없는 최상위, 모든 답글): 행 삭제. 내 답글이 지워져 "삭제된 자리"만 남은 최상위는 그 자리도 삭제. 지운 만큼 각 글의 `comment_count` 감소 |
| 대상 표시 | 다른 사람의 답글에 남은 `reply_to_member_id`는 그대로 → "탈퇴한 사용자에게" |

정리 단계 순서는 13 §3-3을 따른다: ① 내 글 완전 삭제(그 글의 댓글은 CASCADE) → ② 남의 글에 쓴 내 댓글(위 규칙). ①을 먼저 해야 이미 지워질 댓글을 다시 처리하지 않는다.

```sql
-- ② 한 회원의 댓글 정리 (회원 1명 = 트랜잭션 1개, 13 §3-3)
-- 2-a. 내 댓글 중 숨김·삭제가 아닌 것의 글별 개수만큼 comment_count 감소
UPDATE post p SET comment_count = p.comment_count - x.n
FROM (SELECT post_id, count(*) AS n FROM comment
      WHERE author_id = :me AND deleted_at IS NULL AND hidden_at IS NULL GROUP BY post_id) x
WHERE p.id = x.post_id;
-- 2-b. 답글이 있는 내 최상위 → 자리만
UPDATE comment c SET content = '', deleted_at = now()
WHERE c.author_id = :me AND c.parent_id IS NULL
  AND EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id AND r.author_id <> :me);
-- 2-c. 나머지 내 댓글 삭제 (내 답글만 달린 내 최상위도 여기서 함께 지워진다: CASCADE)
DELETE FROM comment WHERE author_id = :me AND deleted_at IS NULL;
-- 2-d. 답글이 모두 사라진 "삭제된 자리" 정리
DELETE FROM comment c WHERE c.parent_id IS NULL AND c.deleted_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id);
```

2-d는 이 회원이 지운 답글 때문에 비게 된 자리만 정리하면 되므로, 실제 구현은 2-c에서 지운 답글의 `parent_id` 목록으로 범위를 좁힌다.

---

## 12. 요청 제한

| 동작 | 제한 | 초과 시 |
|---|---|---|
| 작성 | 사용자당 1분에 10개 | 429 + `Retry-After` |
| 같은 내용 연타 | 같은 글·같은 내용·같은 대상은 10초 안에 하나만 (CM-12) | 처음 댓글을 200으로 |
| 수정 | 사용자당 1분에 20번 | 429 |
| 조회 | 별도 제한 없음 (공통 IP 제한은 화요일 안건 2) | |

---

## 13. 개인 확장: 강성찬 무제한 깊이

공통 스키마 그대로 쓴다. 개인 확장에서만 아래가 달라진다.

| 항목 | 공통 | 강성찬 |
|---|---|---|
| `parent_id` | 최상위만 | 바로 위 댓글 (답글도 가능) |
| `reply_to_member_id` | 답글의 답글에 사용 | 쓰지 않음 (구조가 곧 대상) |
| 표시 | 1단계 + 처음 3개 | 3단계까지 펼치고 더 깊은 답글은 "답글 N개 더보기", 한 댓글의 답글도 처음 3개 |
| 삭제된 자리 | 최상위만 | 자식이 있는 모든 댓글 |

공통 완료 기준(§14)은 개인 확장을 켜도 그대로 만족해야 한다 (01 원칙 2). 깊이 검사(§14 3번)만 설정값 `blog.comment.max-depth`(공통 1)로 바꿔 쓴다.

### 13-1. 깊이를 늘리면 바꿔야 하는 곳 (검증 M28)

부모 FK `fk_comment_parent`는 `ON DELETE CASCADE`다. 깊이 1에서는 답글에 자식이 없어서 행을 지워도 남의 댓글에 닿지 않는다. **깊이를 늘리면 답글 행을 지울 때 그 아래 남의 답글까지 CASCADE로 사라진다** (13 D-7 위반). FK는 그대로 두고(V1 변경 없음), 깊이 확장을 켜는 사람이 아래를 바꾼다.

| 곳 | 공통 (깊이 1) | 깊이 확장 |
|---|---|---|
| 본인 삭제 (§8) | 답글이 있는 **최상위**만 자리로 남김 | **자식이 하나라도 있는 모든 댓글**을 자리로 남긴다. 행 삭제는 자식이 없는 댓글만 → CASCADE가 남의 댓글에 닿지 않는다 |
| 빈 자리 정리 (§8) | 마지막 답글이 지워지면 최상위 자리 삭제 | 자리를 지운 뒤 그 부모도 "자식 없는 자리"가 되면 **위로 반복** |
| 탈퇴 2-b (§11) | `parent_id IS NULL AND EXISTS(남의 답글)` | 최상위 조건을 빼고, **자손 중 남의 댓글이 있는** 내 댓글을 자리로 (아래 SQL) |
| 탈퇴 2-c·2-d | 나머지 삭제, 빈 최상위 자리 정리 | 2-b 뒤에는 남은 내 댓글의 자손이 모두 내 것이라 삭제해도 된다. 빈 자리 정리는 위로 반복 |
| 목록 (§6) | 최상위 + 처음 답글 3개, SQL 2번 | `WITH RECURSIVE`로 3단계까지 한 번에, 더 깊은 답글은 [답글 N개 더보기] |
| 답글 API | `GET /api/comments/{rootId}/replies` (최상위 아래 전부) | `GET /api/comments/{parentId}/replies` (바로 아래 자식만) |
| 작성 검사 (§5 6번) | 대상이 같은 글·정상 상태 | + 대상까지의 깊이 < `blog.comment.max-depth` |
| `reply_to_member_id` | 답글의 답글에 사용 | 쓰지 않음 (`null`) |
| 댓글 수 (CM-11) | 같음 | 같음 (자리는 세지 않음) |

```sql
-- 깊이 확장용 탈퇴 2-b: 자손 중 남의 댓글이 있는 내 댓글 → 자리만
WITH RECURSIVE sub (root_id, id, author_id) AS (
    SELECT c.id, c.id, c.author_id FROM comment c WHERE c.author_id = :me AND c.deleted_at IS NULL
    UNION ALL
    SELECT s.root_id, ch.id, ch.author_id FROM sub s JOIN comment ch ON ch.parent_id = s.id
)
UPDATE comment SET content = '', deleted_at = now()
WHERE id IN (SELECT root_id FROM sub WHERE author_id <> :me);
```

완료 기준 추가 (깊이 확장을 켤 때): **3단계 깊이에서 가운데 댓글 작성자가 삭제하거나 탈퇴해도 그 아래 남의 답글은 남는다.**

---

## 14. 공통 완료 기준 (C-CMT-1)

| # | 기준 | 확인 방법 |
|---|---|---|
| 1 | 비회원·이메일 미인증 회원은 댓글을 쓸 수 없다 | 401 / 403 |
| 2 | 읽을 수 없는 글(임시·비공개·휴지통·없는 글)의 댓글은 보기·쓰기·수정 모두 404이고 응답이 같다 | 06 §8 권한 매트릭스에 "댓글" 열 추가 |
| 3 | 저장된 답글의 `parent_id`는 항상 최상위 댓글이다. 답글에 답하면 같은 최상위 아래에 달리고 대상이 표시된다 | 답글 → 답글 요청 후 DB 확인 |
| 4 | 남의 댓글 수정·삭제 요청은 404이고 댓글은 바뀌지 않는다. 글 작성자도 마찬가지다 | 다른 회원·글 작성자로 요청 |
| 5 | 답글이 있는 최상위 댓글을 지우면 "삭제된 댓글이에요"로 남고 내용은 DB에서 비워진다. 마지막 답글이 지워지면 자리도 사라진다 | |
| 6 | 내용의 HTML·Markdown은 실행·변환되지 않고 글자로 보이며, 줄바꿈은 유지된다 | 12 §9-1 공격 문자열을 댓글로 |
| 7 | `comment_count`는 항상 "삭제·숨김이 아닌 댓글 행 수"와 같다 | 동시 작성 20건, 삭제·숨김·해제·탈퇴 정리 후 `COUNT(*)`와 비교 |
| 8 | 같은 내용을 연달아 보내도 댓글은 하나만 생긴다 | 같은 요청 5건 동시 → 1행 |
| 9 | [댓글 더 보기]로 이어 붙여도 중복·누락이 없고, 답글은 처음 3개 + 더 보기로 보인다 | 커서 경계에 같은 `created_at` 댓글 배치 |
| 10 | 댓글 목록 한 페이지는 SQL 2번으로 그린다 | 쿼리 수 측정 |
| 11 | 숨겨진 댓글은 남에게 내용이 보이지 않고, 작성자 본인에게만 원문이 보인다 | |

---

## 15. ERD 변경 제안

03의 `comment` 위에 아래 `ALTER`로 적용한다 (`scripts/check-ddl.sh`가 03 → 20~49 순서로 적용). 적용 결과는 팀 기준 SQL `V1__common_schema.sql`의 `comment`와 같다.

| # | 변경 | 이유 |
|---|---|---|
| 1 | `comment.reply_to_member_id` 추가 (nullable FK → `member`) | CM-2 답글의 답글 대상 |
| 2 | `comment.hidden_at`·`hidden_by`·`hidden_reason` — **43이 추가** (이 문서 SQL에는 넣지 않음) | CM-9 관리자 숨김. 이름·방식은 [43 신고·숨김](./43-report-hide.md)에서 확정됨 |
| 3 | 같은 글의 댓글만 부모로: `UNIQUE (post_id, id)` + 부모 FK를 `(post_id, parent_id) → comment (post_id, id)`로 | 지금 FK는 다른 글의 댓글을 부모로 지정하는 것을 막지 못한다. 애플리케이션 검사와 이중으로 막는다 |
| 4 | 인덱스: 최상위 목록용·답글 목록용 부분 인덱스, 작성자별 인덱스 | §6 쿼리, 탈퇴 정리(§11). 기존 `ix_comment_post`·`ix_comment_parent`는 대체 |
| 5 | `updated_at`은 내용을 수정할 때만 바꾼다 (스키마 변경 없음, 규칙만) | "수정됨" 판단 |

```sql
-- V1 반영됨 (2026-10-07): 이 SQL은 erd/V1__common_schema.sql에 들어 있다. 기록용으로 남긴다.
-- 03 의 comment 위에 적용 (CM-2·CM-3). 숨김 컬럼(hidden_at·hidden_by·hidden_reason)은 43 이 추가한다
ALTER TABLE comment
    ADD COLUMN reply_to_member_id bigint REFERENCES member (id),                                    -- 추가 (CM-2)
    ADD CONSTRAINT uq_comment_post_id  UNIQUE (post_id, id),                                         -- 추가
    ADD CONSTRAINT ck_comment_reply_to CHECK (reply_to_member_id IS NULL OR parent_id IS NOT NULL),  -- 추가
    ADD CONSTRAINT ck_comment_edited   CHECK (updated_at >= created_at);                             -- 추가 (내용 수정 때만 갱신)
-- 변경: 부모는 같은 글의 댓글만. 03 의 parent_id 단독 FK 를 복합 FK 로 바꾼다
ALTER TABLE comment
    DROP CONSTRAINT comment_parent_id_fkey,
    ADD CONSTRAINT fk_comment_parent FOREIGN KEY (post_id, parent_id)
        REFERENCES comment (post_id, id) ON DELETE CASCADE;
-- 인덱스: 03 의 ix_comment_post·ix_comment_parent 를 대체
DROP INDEX ix_comment_post;
DROP INDEX ix_comment_parent;
-- 최상위 목록 (오래된 순, 커서)
CREATE INDEX ix_comment_root   ON comment (post_id, created_at, id)   WHERE parent_id IS NULL;
-- 답글 목록
CREATE INDEX ix_comment_reply  ON comment (parent_id, created_at, id) WHERE parent_id IS NOT NULL;
-- 탈퇴 정리·내 댓글
CREATE INDEX ix_comment_author ON comment (author_id);
```

- 1단계 깊이(부모가 최상위인지)는 다른 행을 봐야 해서 CHECK로 표현할 수 없다. Service에서 검사하고 §14 3번 테스트로 보장한다.
- 복합 FK `(post_id, parent_id)`는 `parent_id`가 `null`이면 검사하지 않는다 (PostgreSQL 기본 `MATCH SIMPLE`). 최상위 댓글은 그대로 저장된다.

---

## 16. 결정 기록 추가분

| 날짜 | 안건 | 결정 |
|---|---|---|
| 2026-10-04 | 답글의 답글 | 같은 최상위 아래에 달고 "@닉네임에게" 표시, `comment.reply_to_member_id` |
| 2026-10-04 | 댓글 목록 | 최상위 오래된 순 20개씩 [더 보기](커서), 답글 처음 3개 + [답글 N개 더 보기] |
| 2026-10-04 | 댓글 형식 | 글자만·줄바꿈 유지·1~1000자(코드 포인트), 자동 링크 없음, 금칙어 필터 없음(신고로) |
| 2026-10-04 | 댓글 수정 | 언제든, "수정됨" 표시(`updated_at > created_at`), 이력 없음 |
| 2026-10-04 | 댓글 삭제 | 본인만(글 작성자도 남의 댓글 삭제 불가). 답글 있는 최상위는 내용을 비우고 자리만, 그 밖은 행 삭제, 빈 자리는 정리 |
| 2026-10-04 | 숨긴 댓글 표시 | 남에게 "운영 정책에 따라 숨겨진 댓글이에요", 작성자에게 원문 + "숨겨졌어요(나만 보여요)" |
| 2026-10-04 | 댓글 수 | 삭제·숨김이 아닌 댓글 수(탈퇴 유예 중은 포함). 작성 +1, 삭제 −1, 숨김 −1, 해제 +1, 탈퇴 정리 −n |
| 2026-10-04 | 댓글 연타·도배 | 같은 내용 10초 안 중복 방지, 작성 1분 10개, 수정 1분 20번 |
| 2026-10-06 | 댓글 작성 검사 순서 (검증 L19) | 42 §3에 맞춤: 401 → 403(계정) → 429 → 404(글) → 400(내용·대상 댓글) |
| 2026-10-06 | 깊이 확장 시 삭제 (검증 M28) | 부모 FK CASCADE는 그대로. 깊이 확장을 켜면 자식이 있는 모든 댓글은 자리로 남기고, 탈퇴 정리는 자손 기준으로 (§13-1) |

---

## 17. 다른 담당자와 맞출 것

| 상대 | 맞출 것 | 이 문서의 제안 |
|---|---|---|
| 김민서 (트렌딩) | 댓글 수를 언제 올리고 내리는지 | CM-11. `post.comment_count`를 기준으로 쓰기를 권장. 작성 +1, 삭제 −1, 숨김 −1, 해제 +1, 탈퇴 정리 −n. 같은 트랜잭션에서 바뀌므로 항상 정확 |
| 나민서 (신고·숨김) | 숨긴 댓글과 삭제한 댓글의 표시 차이 | §3-1: 삭제 "삭제된 댓글이에요", 숨김 "운영 정책에 따라 숨겨진 댓글이에요"(작성자에게는 원문), 탈퇴 "탈퇴한 사용자의 댓글이에요" |
| 나민서 (신고·숨김) | `comment.hidden_at` 컬럼 이름·소유, 숨긴 댓글의 수정·답글 불가 | §9, §15 2번 |
| 나민서 (신고·숨김) | 작성자가 신고된 댓글을 지우면 검토할 내용이 사라짐 | **신고할 때 댓글 내용을 신고 행에 복사**해 두기를 제안 (댓글은 CM-8에 따라 내용을 남기지 않음) |
| 나민서 (권한 매트릭스) | 댓글 행 | 비회원: 공개 글의 댓글 보기만 / 회원(인증): 작성·신고 / 작성자 본인: 수정·삭제 / 글 작성자: 일반 회원과 같음 / 관리자: 숨김만(수정·삭제 불가) |
| 나민서 (글 상세) | 댓글 영역 | 첫 20개를 글 상세 HTML에 포함(SSR), 머리말에 `comment_count`. 글 상세 주소의 `?comment={id}`를 댓글 영역에 넘겨 §6 `around`로 그린다 (`canonical`에는 넣지 않음) |
| 나민서 (탈퇴) | 13 §3-3 정리 순서 | ① 내 글 → ② 내 댓글(§11 SQL) 순서. 강성찬이 `CommentWithdrawalPurgeStep`으로 구현 |
| 20 이벤트 문서 | `CommentCreated`에 대상 회원 | `replyToMemberId` 필드 추가 (20 §3-2에 반영) |
| 나민서 (권한 매트릭스) | 42 "맞출 것"의 확인 요청: 인증 전 회원의 본인 댓글 삭제 | **허용** (삭제는 남에게 보이는 쓰기가 아니다). 다만 인증 전 회원은 댓글을 쓸 수 없어(07 L-2) 실제로 지울 댓글이 생기지 않는다 |
| 나민서 (권한 매트릭스) | 42 §3에 요청 제한 위치 | "요청 제한(429)은 ②와 ③ 사이 — 대상과 무관해 존재를 드러내지 않는다" 한 줄 (21 §5가 이 순서를 따름, 검증 L19) |
