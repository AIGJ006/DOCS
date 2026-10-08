# Research: 댓글·답글

**Feature**: 007-comment | **Date**: 2026-10-08

표기: (확정) = spec·Clarifications·Tier A 결정에 이미 있음, (제안) = 이 plan이 정한 기본안(팀 확인 필요), (미결) = 팀 결정 대기 — 기본안으로 진행.

---

## R1. 첫 댓글을 받는 방식 (확정)

- **Decision**: 화면이 글 상세 API와 댓글 목록 API를 동시에 부른다. `PostDetailPage`가 주소의 글 번호로 두 요청을 함께 시작하고, 댓글은 005 `CommentSectionSlot` 자리에서 그린다. 서버는 상세 HTML·상세 응답에 댓글을 넣지 않는다. 댓글 요청이 실패하면 댓글 영역에만 "불러오지 못했어요 [다시 시도]"를 보이고 본문은 그대로다.
- **Rationale**: Clarifications Q1, 005 R-33, 원칙 V.
- **Alternatives considered**: 상세 응답에 포함·SSR — Q1에서 제외.

## R2. 판정 순서와 글 확인 (확정 + 제안)

- **Decision**: 모든 쓰기는 이 순서로 검사하고 처음 걸린 단계로 응답한다.
  1. 401 `LOGIN_REQUIRED` — `@LoginRequired`
  2. 403 — `AccountStatusGuard.requireActive(me, kind)`: 작성·수정은 `CONTENT_WRITE`(인증 전 403 `EMAIL_NOT_VERIFIED`), 삭제는 `CONTENT_CLEANUP`(인증 전 통과). 탈퇴 유예 `ACCOUNT_WITHDRAWN`, 정지 `ACCOUNT_SUSPENDED`
  3. 404 — 글: `PostReadService.requireReadable(postId, viewer)` + `status = PUBLISHED`(작성자 본인의 임시글도 404) + 쓰기·수정이면 `hidden_at IS NULL`. 수정·삭제는 먼저 댓글(`id`, `author_id = :me`)을 찾고 그 글로 같은 확인을 한다(삭제는 글 확인 없음 — R9)
  4. 400 — 내용(`COMMENT_REQUIRED`·`COMMENT_TOO_LONG`), 그다음 대상(`REPLY_TARGET_UNAVAILABLE`). 수정은 409 `COMMENT_HIDDEN`이 이 자리
  5. 429 `TOO_MANY_REQUESTS` + `Retry-After` — `RateLimiter.acquireOrThrow`(트랜잭션 밖)
- 앞 단계에서 걸린 요청은 횟수에 세지 않는다(Clarifications Q2). 4번 대상 확인은 DB 조회가 필요해 트랜잭션 밖에서 한 번 미리 보고(빠른 거부), 트랜잭션 안에서 잠금과 함께 다시 확인한다(경합 처리, R6).
- **Rationale**: README 2026-10-07 판정 순서, 007 Q2. `PostReadService`가 없음·휴지통·비공개·임시·숨김·탈퇴 유예를 구분 없이 404로 준다(004).
- **Alternatives considered**: 21 원문(요청 제한을 계정 상태 다음) — Q2에서 제외.

## R3. 내용 정리 규칙 (확정 + 제안)

- **Decision**: `CommentText.normalize(raw)`:
  1. NFC
  2. `InvisibleCharacters.isInvisible`(008이 `shared.text`로 옮기는 12 §7-4 목록) 중 **줄바꿈(U+000A)·캐리지 리턴(U+000D)은 남기고** 나머지 제거. 탭(U+0009)은 공백 하나로 바꾼다
  3. 줄바꿈 통일: `\r\n`·`\r` → `\n`
  4. 앞뒤 공백·줄바꿈 제거(`strip()`)
  5. 빈 줄 묶음: 공백만 있는 줄을 빈 줄로 보고 연속 빈 줄 2개 이상 → 1개(`\n{3,}` → `\n\n`)
  - 판정: 결과가 빈 값 → `COMMENT_REQUIRED`, `codePointCount > 1000` → `COMMENT_TOO_LONG`. DB `varchar(1000)`은 PostgreSQL에서 문자 수(코드 포인트) 기준이라 1000 코드 포인트가 들어간다
  - 수정: 정리 결과가 저장된 내용과 같으면 아무것도 바꾸지 않고 200 + 지금 댓글(FR-028, US3 #2)
  - 금칙어 검사 없음(FR-007)
- **Rationale**: FR-004의 순서. 008이 보이지 않는 글자 목록을 공용으로 옮기므로 같은 것을 쓴다(008 머지 전이면 007이 먼저 옮기고 008이 그것을 쓴다 — 어느 쪽이 먼저든 한 번만).
- **Alternatives considered**: 탭 제거 — 코드 붙여넣기 들여쓰기가 붙어 버려 공백 치환.

## R4. 답글 구조와 대상 (확정 + 제안)

- **Decision**:
  - `replyToCommentId`가 없으면 최상위. 있으면 대상 댓글 T를 같은 글에서 찾는다(`post_id = :postId` — 다른 글이면 없음과 같음).
  - T가 정상(삭제·숨김·작성자 탈퇴 유예·익명 처리 아님)이 아니면 `REPLY_TARGET_UNAVAILABLE`.
  - 부모 = T가 최상위면 T, 답글이면 T의 부모(최상위).
  - 대상 회원 = T가 답글이고 T 작성자 ≠ 나면 T 작성자, 아니면 NULL(최상위에 바로 단 답글, 내 답글에 다시 단 답글 — FR-002).
  - T가 정상인 답글이면 그 최상위가 "삭제된 자리"·숨김이어도 답글을 달 수 있다(최상위 자체가 대상이 아니므로). 최상위 행은 지워지지 않았으므로 FK가 성립한다.
  - 깊이 설정 `blog.comment.max-depth`는 1만 허용한다(`@Max(1)` — 개인 확장이 켜면 자기 plan에서 넓힘).
- **Rationale**: 21 CM-1·CM-2·§5. 대상은 댓글이 아니라 회원이라 대상 답글이 지워져도 "@닉네임에게"가 남는다(FR-003).
- **Alternatives considered**: 삭제된 자리 아래 새 답글 금지 — 21 §5는 "대상 댓글"만 정상이면 된다고 한다.

## R5. 댓글 수 갱신 (확정 + 제안)

- **Decision**: post 모듈에 공개 Service `PostCounterService`를 새로 둔다.
  - `adjustCommentCount(long postId, int delta)` — `@Transactional(propagation = MANDATORY)`, `UPDATE post SET comment_count = comment_count + :delta WHERE id = :postId`. 0 아래로 가면 `ck_post_counts` 위반으로 트랜잭션 전체가 실패한다(일관성 깨짐을 숨기지 않음).
  - `adjustCommentCounts(Map<Long, Integer> deltas)` — 탈퇴 정리용, `UPDATE post p SET comment_count = p.comment_count - d.n FROM (unnest(:ids, :ns)) d …` 한 번.
  - 증감 규칙(FR-035): 작성 +1 / 삭제(정상이던 것) −1 / 삭제(숨김이던 것) 0 / 빈 자리 제거 0 / 숨김 −1 / 해제 +1 / 탈퇴 정리 −(정상이던 그 회원 댓글 수). 탈퇴 **유예** 중에는 바꾸지 않는다(FR-038).
- **Rationale**: 05 J-2(같은 트랜잭션). interaction이 post 테이블을 직접 고치지 않는다(원칙 II). `@Modifying` JPA 대신 `JdbcClient`(post 모듈이 이미 씀). 009 `like_count`도 같은 Service에 더한다.
- **Alternatives considered**: 매번 `count(*)` — 카드·상세가 매번 세야 해서 제외. 이벤트로 비동기 증감 — 같은 트랜잭션 요구(SC-002) 위반.

## R6. 잠금 순서와 경합 (확정 + 제안)

- **Decision**:
  - 작성(답글): 트랜잭션에서 ① 최상위 행 `SELECT … FOR SHARE` ② 대상 T(최상위와 다르면) `FOR SHARE`로 다시 읽어 R4 조건 재확인 ③ INSERT ④ 카운터 +1.
  - 삭제(최상위): 그 행 `FOR UPDATE` → 답글 수 확인 → 자리로 남기거나 DELETE.
  - 삭제(답글): 최상위 `FOR UPDATE` → 답글 행 `FOR UPDATE` → DELETE → 최상위가 자리(`deleted_at IS NOT NULL`)이고 남은 답글 0이면 최상위도 DELETE.
  - 잠금은 항상 "최상위 → 답글" 순서라 교착이 없다. 결과: 답글이 먼저 커밋되면 삭제가 답글 1개를 보고 자리로 남기고, 삭제가 먼저면 답글은 ① 다음 재확인에서 대상(최상위)이 없거나 자리라 `REPLY_TARGET_UNAVAILABLE`(FR-014).
  - 숨김(014 호출)·해제도 그 행 `FOR UPDATE`.
- **Rationale**: 21 §5·§8. 공유 잠금끼리는 막지 않아 같은 최상위에 답글 여러 개는 동시에 들어간다.

## R7. 10초 중복 방지 (제안 — 원문 방식 변경)

- **Decision**: Redis 키 대신 DB에서 한다. 작성 트랜잭션 첫머리:
  1. `SELECT pg_advisory_xact_lock(:lockKey)` — `lockKey` = SHA-256(`memberId|postId|정리한 내용|replyToCommentId`)의 앞 8바이트(long)
  2. `SELECT id FROM comment WHERE author_id = :me AND post_id = :postId AND content = :content AND <대상 조건> AND created_at > now() - :window AND deleted_at IS NULL ORDER BY id DESC LIMIT 1`(`ix_comment_author`)
  3. 있으면 INSERT 없이 그 댓글을 200으로 돌려준다(이벤트·카운터 없음). 없으면 계속.
  - `<대상 조건>`은 같은 `parent_id`와 같은 `reply_to_member_id`(NULL 비교는 `IS NOT DISTINCT FROM`).
  - 요청 제한은 트랜잭션 전에 세므로 중복 요청도 1번으로 센다(두 번 누르기 수준이라 영향 작음).
- **Rationale**: 원문(21 CM-12)의 `SET NX EX 10`은 동시 5건 중 4건이 "처리 중"을 만나 기다리거나 실패해야 하는데, 트랜잭션 안 Redis 쓰기 금지(`RedisGuard`) 때문에 커밋 뒤에 값을 채워야 한다. advisory lock은 같은 요청끼리만 줄을 세우고, 커밋 뒤 두 번째 요청이 같은 행을 보므로 5건이 정확히 1개를 만든다(SC-003). Redis 장애와도 무관하다.
- **Alternatives considered**: Redis `SET NX` + 처리 중 대기 — 복잡하고 장애 때 중복 허용. 고유 인덱스 — 10초 창을 인덱스로 표현할 수 없다.

## R8. 목록 SQL과 작성자 표시 (제안)

- **Decision**: 페이지당 SQL 4번.
  1. 최상위: `SELECT id, author_id, parent_id, reply_to_member_id, content, created_at, updated_at, deleted_at, hidden_at FROM comment WHERE post_id = :postId AND parent_id IS NULL [AND (created_at, id) > (:t, :id)] ORDER BY created_at, id LIMIT :pageSize + 1`(`ix_comment_root`).
  2. 답글 미리보기: `SELECT r.root_id, c.*, cnt.n FROM unnest(:rootIds) AS r(root_id) CROSS JOIN LATERAL (SELECT … FROM comment WHERE parent_id = r.root_id ORDER BY created_at, id LIMIT :preview) c CROSS JOIN LATERAL (SELECT count(*) AS n FROM comment WHERE parent_id = r.root_id) cnt`(`ix_comment_reply`). 답글 없는 최상위는 결과가 없으므로 `n = 0`으로 채운다.
  3. 회원 표시: 001 `MemberQueryService.findDisplays(Collection<Long> ids)` → `MemberDisplay(id, handle, nickname, withdrawn)`(`withdrawn` = `status = 'WITHDRAWN' OR deleted_at IS NOT NULL`). 작성자와 대상 회원을 합쳐 한 번.
  4. 프로필 사진: 001 `ProfileImageQuery.currentKeysOf(ids)` + `ImageUrlResolver`(정상 상태로 보일 작성자만).
  - 답글 펼치기 `GET /api/comments/{rootId}/replies?cursor=`: 최상위 확인(그 글 읽기 권한 R2) → 답글 21개(첫 요청은 4번째부터 — 커서 없이 부르면 `OFFSET :preview`가 아니라 "미리보기 마지막 답글 이후" 커서를 목록 응답이 `repliesNextCursor`로 준다) → 회원 → 사진.
  - "삭제된 자리"인 최상위는 답글이 0이면 존재하지 않으므로(R6 정리) 목록에 빈 자리가 나오지 않는다.
- **Rationale**: 원문 SQL의 `m.profile_image_url`은 삭제된 컬럼이고, member·image JOIN은 원칙 II 예외가 된다. 공개 Service 두 번 호출로 SQL 수가 고정된다(SC-008). `row_number() OVER` 대신 `LATERAL … LIMIT 3`은 답글이 수천 개인 최상위에서도 앞 3개만 읽는다.
- **Alternatives considered**: member JOIN(005 카드처럼 예외) — 댓글은 작성자가 여러 명이라 예외를 넓히는 셈이 되어 제외.

## R9. 수정·삭제 규칙 (확정)

- **Decision**:
  - 수정: 댓글을 `id AND author_id = :me`로 `FOR UPDATE` → 없으면 404 → 자리(`deleted_at`)면 404 → 그 글 확인(R2-3, 숨김 글이면 404) → 숨김이면 409 `COMMENT_HIDDEN` → 내용 검사 → 요청 제한(트랜잭션 전에 이미 셈 — 아래) → 같으면 변화 없음, 다르면 `content`, `updated_at = now()`.
    - 요청 제한(수정 1분 20번)은 트랜잭션 밖이라 "앞 단계 통과 뒤"를 지키려고 Service가 ① 트랜잭션 없이 댓글·글·내용을 미리 확인 ② `acquireOrThrow` ③ 트랜잭션에서 잠금과 재확인 순서로 한다(작성도 같은 모양).
  - 삭제: 댓글을 `id AND author_id = :me`로 찾음 → 없으면 404. **글 읽기 확인을 하지 않는다** — 글이 비공개·휴지통·숨김이어도 내 댓글은 지울 수 있게 할지는 원문에 없어 **(제안)** 지울 수 있게 한다(내 데이터 정리, `CONTENT_CLEANUP`). 단 글 작성자 탈퇴 유예처럼 글이 안 보이는 경우 화면에서 버튼이 없으므로 API로만 가능하다. 자리(`deleted_at`)인 댓글은 404(이미 지움).
  - 응답: 수정 200 + 댓글 하나, 삭제 204.
  - 삭제 성공 후 `CommentDeleted` 발행(자리로 남겨도), 수정은 이벤트 없음(FR-040).
- **Rationale**: 21 §7·§8, 42 §6. 삭제 때 글 확인을 빼는 이유: 비공개로 바꾼 남의 글에 남긴 내 댓글을 지울 방법이 없으면 FR-030 "언제든"과 어긋난다.
- **Alternatives considered**: 삭제도 글 읽기 권한 필요 — 위 이유로 제안에서 제외(팀 확인 항목).

## R10. 표시 상태와 응답 감추기 (확정)

- **Decision**: `CommentState` 판정(우선순위, FR-019):
  1. `WITHDRAWN_AUTHOR` — 작성자 `withdrawn`(유예·익명 처리)
  2. `DELETED` — `deleted_at IS NOT NULL`
  3. `HIDDEN` — `hidden_at IS NOT NULL`
  4. `NORMAL`
  - 응답: `WITHDRAWN_AUTHOR`·`DELETED`는 `author = null`, `content = null`. `HIDDEN`은 보는 사람이 작성자면 `content`·`author` 포함 + `mine = true`, 아니면 둘 다 null(글 주인·관리자 포함, SC-007). `edited`(= `updated_at > created_at`)·`createdAt`·`replyCount`는 모든 상태에 둔다.
  - `replyTo`: 대상 회원이 `withdrawn`이면 `{withdrawn: true}`(화면 "@탈퇴한 사용자에게"), 아니면 `{handle, nickname}`.
  - `isPostAuthor`: 정상·본인 숨김 댓글에서 작성자 = 글 작성자.
  - 탈퇴 정리(015) 뒤 남긴 자리는 `deleted_at` + 작성자 익명 처리라 1번 상태로 "탈퇴한 사용자의 댓글이에요"가 된다.
- **Rationale**: FR-020 "아예 담지 않는다". 화면이 실수해도 원문이 새지 않는다.

## R11. 커서와 바로 가기 (제안)

- **Decision**:
  - 커서: 001 `CursorCodec`, 목록 구분 `comments:{postId}`·`replies:{rootId}`, 키 `[epoch 마이크로초, id]`(005 `PostListCursor`와 같은 시각 처리). 이전 방향은 `{"d":"prev"}`를 덧붙인 별도 커서 `prevCursor`로 `GET /api/posts/{postId}/comments?cursor={prevCursor}` 한 경로에서 처리한다(방향은 커서 안에 있음).
  - `?around={commentId}`: 그 글의 댓글이고 정상(또는 보는 사람의 숨김 댓글)이면 최상위 R을 정하고 `(created_at, id) >= R` 첫 20개 + R 앞에 최상위가 있으면 `prevCursor`. 대상이 답글이고 4번째 이후면 R의 답글을 대상까지 돌려준다(상한 `blog.comment.around-max-replies` 100 — 넘으면 처음 3개만, 화면은 최상위로 스크롤). 대상이 없거나 조건 밖이면 오류 없이 첫 페이지(`around` 무시, 응답 모양 같음 — US5 #3).
  - 응답에 `focusCommentId`(실제로 펼친 대상, 무시했으면 null)를 둔다. 화면은 그 요소 `id="comment-{id}"`로 스크롤하고 2초 강조.
- **Rationale**: 21 §6, 40 §2. 무시 여부가 응답 모양으로 드러나지 않게 `focusCommentId`만 다르다(그 댓글이 숨김·삭제인지 드러내지 않음 — null은 "없음"과 같다).

## R12. 이벤트와 다른 기능용 Service (확정 + 제안)

- **Decision**: contracts/events.md.
  - `CommentCreated(commentId, postId, postAuthorId, authorId, parentId, parentAuthorId, replyToMemberId, createdAt)` — 커밋 후 011이 받는다. 중복 방지로 기존 댓글을 돌려줄 때는 발행하지 않는다.
  - `CommentDeleted(commentId, postId, postAuthorId, authorId, parentId, deletedAt)` — 자리로 남겨도, 빈 자리 정리로 최상위가 함께 지워질 때는 그 최상위에 대해서도(작성자 = 최상위 작성자) 발행.
  - `CommentModerationService.hide(commentId, adminId, reason, now)`/`unhide(commentId)` — 014가 부르는 공개 Service. 상태 확인·`hidden_*` 기록·카운터 ±1을 한 트랜잭션에서. `ContentHidden` 이벤트는 014가 발행.
  - `CommentPurgeService.purgeByAuthor(memberId)` — 21 §11 SQL 2-a~2-d를 한 트랜잭션에서(글별 카운터 감소 → 남의 답글 있는 내 최상위를 자리로(`content = ''`, `deleted_at = now()`) → 나머지 내 댓글 DELETE → 빈 자리 정리). 015의 `CommentWithdrawalPurgeStep`(order 20)이 부르고, 단계 클래스는 015 tasks가 만든다(003 `ImagePurgeService`와 같은 나눔).
  - `CommentQueryService.commentIdsOfPost(postId)` — 006 T058이 만든 메서드를 그대로 두고 소유만 넘겨받는다(**006 머지 후**).
- **Rationale**: 20 §3-2. 댓글 수 일관성(SC-002)을 이 기능 테스트가 숨김·해제·탈퇴 정리까지 섞어 증명하려면 그 SQL이 이 모듈에 있어야 한다.

## R13. 권한 매트릭스 행 (제안)

- **Decision**: `TR/permission/comment.csv`(004 하네스 형식, owner `007`). 행동:
  - `comment.list`(읽기, `isWrite=false`): 글 상태별 200/404
  - `comment.create`(쓰기): ANONYMOUS 401, UNVERIFIED 403 `EMAIL_NOT_VERIFIED`, SUSPENDED 403, WITHDRAWN 403, MEMBER·AUTHOR·ADMIN은 읽을 수 있는 발행 글 201, 그 밖 404. `HIDDEN` 글은 작성자도 404
  - `comment.update`·`comment.delete`(쓰기): 대상 댓글은 하네스 준비 단계에서 **MEMBER 행위자 계정**으로 SQL 삽입. MEMBER 200/204, AUTHOR(글 작성자)·ADMIN·UNVERIFIED(남) 404, ANONYMOUS 401. 글 상태가 `PUBLISHED_PRIVATE`·`TRASHED`면 update 404, delete 204(R9 제안)
  - 거부된 쓰기는 하네스가 `PostSnapshot` 전후 비교 + 댓글 행 전후 비교(이 기능이 `CommentSnapshot`을 더함)
- **Rationale**: ANALYSIS-tier-a R6(004 FR-036 위임), SC-001·SC-006.

## R14. 화면 상태 관리 (확정 + 제안)

- **Decision**: `useCommentThread(postId, aroundId)`:
  - 상태: `rootIds: number[]`(화면 순서), `byId: Map<number, CommentView>`, `repliesOf: Map<rootId, {ids, nextCursor, total}>`, `nextCursor`, `prevCursor`, 요청 상태.
  - [댓글 더 보기] 결과는 `byId`에 없는 id만 `rootIds` 끝에 붙인다. 내가 쓴 최상위 댓글은 응답을 받는 즉시 끝에 붙이고, 나중에 같은 id가 페이지로 와도 한 번만 그린다(Clarifications Q5). 답글도 그 최상위 `ids` 끝에.
  - [이전 댓글 보기]는 `prevCursor`로 받아 앞에 붙인다.
  - 머리말 수는 상세 응답의 `commentCount`에서 시작해 작성 +1·삭제 −1을 화면에서 반영한다(서버 값과 같은 규칙).
  - 입력칸: 비회원 "로그인하고 댓글을 남겨 보세요 [로그인]", 인증 전 "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"(001 재발송 API). 글자 수 표시는 `[...text].length`(코드 포인트).
  - [신고] 버튼은 그리지 않는다(Clarifications Q4). 014가 켤 때 `CommentItem`의 `actions` 자리에 넣는다.
- **Rationale**: FR-016·FR-024, 중복 없이 이어 붙이기(SC-004는 서버, 화면은 id로 한 번만).

## R15. 설정값 (제안)

- **Decision**: `@ConfigurationProperties("blog.comment")` `CommentProperties`:
  ```yaml
  blog:
    comment:
      page-size: 20
      reply-preview: 3
      reply-page-size: 20
      content-max: 1000          # DB varchar(1000)보다 클 수 없음 (@Max(1000))
      dedupe-window: 10s
      max-depth: 1               # 공통 1 (@Max(1))
      around-max-replies: 100
      rate-limit:
        create: { limit: 10, window: 1m }
        edit: { limit: 20, window: 1m }
  ```
- **Rationale**: 원칙 VII. 이름은 002 `rate-limit.limit/window` 모양.

## R16. 요청 과다 코드 정리 (확정 — 별도 작업)

- **Decision**: 이 기능은 001 `TooManyRequestsException`(code `TOO_MANY_REQUESTS`)만 쓴다. 002 `PostReasonCode.RATE_LIMITED`(자동 저장·미리보기)를 `TOO_MANY_REQUESTS`로 바꾸는 작업은 002 소유 파일이라 이 기능 tasks에 "002 담당 확인 후" 작업으로 둔다(Clarifications Q3).
- **Rationale**: 007 Q3 결정이 003·008·009에도 적용된다. 화면 `RateLimitedException` 처리 코드가 두 코드를 함께 보지 않게 한다.

## R17. Redis 메모리 부족 503 (미결 — 기본안)

- **Decision**: 지금 `RedisGuard.call`은 OOM이면 대체 경로를 쓰지 않고 `AutosaveUnavailableException`(503 `AUTOSAVE_UNAVAILABLE` "잠시 후 다시 저장할게요")을 던진다. `RateLimiter.tryAcquire`도 이 경로라 Redis OOM 때 댓글 작성·수정이 503을 받는다. 기본안: 이 기능은 그대로 두고 화면은 code와 상관없이 503이면 "잠시 후 다시 시도해 주세요"를 보이며 입력을 지우지 않는다. 공용 수정(요청 제한은 OOM에도 허용, 또는 공통 코드 `TEMPORARILY_UNAVAILABLE`)은 002 소유 결정으로 ANALYSIS-tier-bc 팀 결정 항목에 올린다. `shared.infra.redis.RedisGuard`가 `post.application.exception`을 import하는 모듈 방향 문제도 함께 적는다.
- **Rationale**: 002 T032 구현 메모. 댓글만 따로 고치면 공용 동작이 갈라진다.
