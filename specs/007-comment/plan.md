# Implementation Plan: 댓글·답글

**Branch**: `007-comment` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-comment/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

글을 읽을 수 있는 사람은 그 글의 댓글을 오래된 순으로 읽고(최상위 20개씩, 답글 처음 3개 + 20개씩 펼치기), 이메일 인증한 회원은 최상위 아래 한 단계 답글 구조로 댓글을 쓰며, 작성자 본인만 고치고 지운다(C-CMT-1). 글이 비공개·휴지통·숨김이 되거나 작성자가 탈퇴를 신청하면 댓글은 지워지지 않고 함께 감춰지고, 관리자 숨김·탈퇴 작성자의 댓글은 정해진 문구로 대신 보인다. 알림 링크로 들어오면 그 댓글까지 펼치고 강조한다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `comment`(복합 FK `fk_comment_parent (post_id, parent_id)` CASCADE, `ck_comment_content`·`ck_comment_reply_to`, `ix_comment_root`·`ix_comment_reply`·`ix_comment_author`)와 `post.comment_count`를 그대로 쓴다. 1단계 깊이는 Service가 지킨다(답글에 답하면 부모를 그 답글의 최상위로 바꿈).
- **읽기 권한은 004 `PostReadService.requireReadable` 하나.** 댓글 보기·쓰기·수정 모두 이것으로 글을 확인하고, 추가로 "발행됨"(임시글은 작성자에게도 404)과 쓰기·수정의 "숨김 아님"을 본다. 판정 순서는 401 → 403(`AccountStatusGuard`) → 404(글) → 400(내용·대상) → 429(맨 끝, Clarifications Q2)이다(R2).
- **댓글 수는 post 모듈 API로 같은 트랜잭션에서.** interaction이 `post` 테이블을 직접 고치지 않도록 `post.application.PostCounterService.adjustCommentCount(postId, delta)`(`@Transactional(propagation = MANDATORY)`, `UPDATE … SET comment_count = comment_count + :delta`)를 새로 둔다. 작성 +1, 삭제 −1(이미 숨김이면 0), 숨김 −1, 해제 +1, 탈퇴 정리 −n(R5).
- **동시성은 최상위 행 잠금 하나로.** 답글 작성은 최상위 행을 `FOR SHARE`, 삭제는 최상위 행을 `FOR UPDATE`(답글 삭제도 최상위 먼저 → 답글 순서)로 잡아 "삭제와 답글이 동시에 오면 하나씩"(FR-014)을 지킨다(R6).
- **10초 중복 방지는 DB에서.** 원문의 Redis `SET NX`(처리 중 상태를 기다려야 함) 대신, 작성 트랜잭션 첫머리에 `pg_advisory_xact_lock(memberId, hash)`를 걸고 "같은 글·같은 작성자·같은 내용·같은 대상, 10초 안"인 댓글을 찾아 있으면 그 댓글을 200으로 돌려준다. 동시 5건이 정확히 1개만 만든다(SC-003, R7). 요청 제한은 001 `RateLimiter`(트랜잭션 밖, 작성 1분 10개·수정 1분 20번, Redis 장애 시 통과).
- **목록은 페이지당 SQL 4번 고정.** ① 최상위 21개(`ix_comment_root`, 커서 `(created_at, id)`) ② 그 최상위들의 처음 답글 3개 + 답글 수(`unnest` + `LATERAL … LIMIT 3`, `ix_comment_reply`) ③ 작성자·대상 회원 표시 정보(001 `MemberQueryService.findDisplays`, 새 메서드) ④ 프로필 사진(001 `ProfileImageQuery.currentKeysOf`). member·image를 직접 JOIN하지 않아 원칙 II 예외가 없다(R8).
- **표시 상태는 서버가 정한다.** `CommentState` = 탈퇴 작성자 > 삭제된 자리 > 숨김 > 정상(FR-019). 탈퇴·삭제·남이 보는 숨김은 응답에 `author`·`content`를 아예 담지 않는다(FR-020, SC-007). 화면은 상태별 문구만 고른다.
- **화면은 상세와 동시에 댓글 API를 부른다**(Clarifications Q1). 005 `CommentSectionSlot`을 `CommentSection`으로 채우고, `PostDetailPage`가 주소의 글 번호로 상세와 댓글 첫 페이지를 함께 요청한다. 내가 쓴 댓글은 지금 목록 끝에 붙이고 번호로 한 번만 그린다(Clarifications Q5). [신고]는 014 전까지 숨긴다(Clarifications Q4).
- **다른 기능에 주는 것.** `CommentCreated`·`CommentDeleted` 이벤트(011), `CommentModerationService.hide/unhide`(014가 부름, 댓글 수 일관성은 이 기능이 책임), `CommentPurgeService.purgeByAuthor`(015의 `WithdrawalPurgeStep` order 20이 부름), 006이 만든 `CommentQueryService.commentIdsOfPost` 소유 이전(**006 머지 후**).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation), `JdbcClient`, Flyway, Resilience4j(`RedisGuard`), 001 `RateLimiter`·`AccountStatusGuard`·`CursorCodec`·`ProfileImageQuery`·`ImageUrlResolver`, 004 `PostReadService`·`CacheControlPolicy`, 002 `TitleNormalizer`와 같은 보이지 않는 글자 목록(008 `shared.text.InvisibleCharacters`)
- 새 의존성 없음
- 화면: React 18, react-router 7, 005 `RelativeTime`·`DefaultAvatar`·`CommentSectionSlot` 자리

**Storage**:

- PostgreSQL: `comment`(V1, 변경 없음), `post.comment_count`(post 모듈 API로만 증감), 읽기만 `member`·`image`(001 공개 Service 경유)
- Redis: 요청 제한 `ratelimit:comment:{memberId}`(1분 10개), `ratelimit:comment-edit:{memberId}`(1분 20번). 중복 방지 키는 두지 않는다(R7)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc. 화면은 Vitest + Testing Library, 종단 확인은 Playwright. 헌법 VIII에 따라 권한 매트릭스·동시성·댓글 수 일관성·XSS는 실제 DB 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 댓글 한 페이지(최상위 20 + 답글 각 3) 서버 응답 300ms 이내(글 1만 건, 댓글 10만 건, SC-008)
- 페이지당 SQL 4번 고정(댓글·답글 수에 비례하지 않음). 답글 펼치기는 SQL 3번(답글 21개 + 회원 + 사진)
- 작성·수정·삭제 p95 200ms 이내(요청 제한 Redis 1번 + 트랜잭션)

**Constraints**:

- 현재 사용자는 세션에서만 꺼낸다. 남의 댓글·없는 댓글·볼 수 없는 글은 같은 404 본문
- 트랜잭션 안에서 Redis를 쓰지 않는다(`RedisGuard` 규칙). 이벤트는 커밋 후 처리
- 댓글은 글자로만 렌더링한다(`dangerouslySetInnerHTML` 금지, `white-space: pre-line`, `<br>` 삽입 금지)
- 글자 수는 코드 포인트(`codePointCount`·`char_length`). Java `length()` 금지
- 공개가 아닌 글의 댓글 응답은 `Cache-Control: private, no-store`
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 글 1만 건, 댓글 10만 건, 한 글 최대 수천 댓글 기준
- API 6개(목록·답글 펼치기·작성·수정·삭제 + `around`), 화면 컴포넌트 1묶음(댓글 영역)
- 이벤트 2종, 다른 기능용 공개 Service 3개(`CommentModerationService`·`CommentPurgeService`·`CommentQueryService`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 `comment`를 그대로 쓰고 마이그레이션이 없다. 무제한 깊이(강성찬)는 `blog.comment.max-depth` 설정값 자리만 두고 공통값 1에서 동작을 바꾸지 않는다(21 §13-1은 개인 확장 plan 소관) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | 댓글 쓰기·조회는 `interaction` 모듈. 글 확인은 004 `PostReadService`, 댓글 수는 새 post 공개 Service `PostCounterService`, 작성자 표시는 001 `MemberQueryService.findDisplays`(새 메서드)·`ProfileImageQuery`로만 한다. member·image·post를 직접 읽거나 쓰지 않는다(R5·R8) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 수정·삭제 SQL에 `author_id = :me` 조건, 남의 댓글(글 작성자·관리자 포함)과 없는 댓글은 같은 404. 읽을 수 없는 글은 내용 검사보다 먼저 404(FR-009, US2 #11). 권한 매트릭스 하네스에 댓글 행동 4개를 더한다(R13) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 댓글은 HTML로 바꾸지 않고 저장·응답·표시 모두 글자 그대로. 12 §9-1 공격 문자열 테스트(SC-005) |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 댓글 불러오기 실패는 본문을 막지 않는다(Clarifications Q1). 요청 제한은 Redis 장애 때 통과, 중복 방지는 DB라 Redis와 무관. 이벤트는 AFTER_COMMIT 비동기 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 비공개·휴지통·숨김·탈퇴 유예 동안 댓글을 보존한다. 지운 댓글은 되살리지 않고(FR-033), 자리로 남길 때 내용을 비운다(FR-031). 탈퇴 30일 정리는 015가 이 기능의 `CommentPurgeService`로 실행 |
| VII. 수치는 설정값으로 | **PASS** | `blog.comment.*`: 페이지 20, 답글 미리보기 3, 답글 페이지 20, 내용 1000자, 중복 창 10초, 작성 1분 10개, 수정 1분 20번, 깊이 1, `around` 펼침 상한 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | Testcontainers로 권한 매트릭스(SC-001·SC-006), 동시 5건(SC-003), 삭제·답글 경합(FR-014), 댓글 수 일관성 혼합 시나리오(SC-002), 같은 시각 페이지 경계(SC-004), XSS(SC-005), 숨김 원문 누출(SC-007) |

**Gate 결과 (Phase 0 전)**: 위반 없음. Complexity Tracking은 필요 없다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. 001 소유 `MemberQueryService`에 `findDisplays(ids)`를 더하는 것은 공개 API 추가라 원칙 II에 맞는다. 001 담당에게 알린다.
  2. `PostCounterService`는 post 모듈에 새로 두며 009 좋아요(`adjustLikeCount`)도 같은 클래스에 더한다(009 plan과 맞춤).
  3. 004 spec의 비회원 문구("로그인하고 댓글 쓰기")와 이 spec FR-023 문구가 다르다 — 이 spec(21 §3-1) 쪽으로 맞추고 ANALYSIS-tier-bc에 적는다.

## Project Structure

### Documentation (this feature)

```text
specs/007-comment/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 댓글 목록·답글·작성·수정·삭제 REST 계약
│   └── events.md        # CommentCreated·CommentDeleted, 다른 기능용 공개 Service(숨김·탈퇴 정리·조회)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── interaction/
│   ├── web/
│   │   ├── CommentController.java              # GET/POST /api/posts/{postId}/comments, GET /api/comments/{rootId}/replies
│   │   └── CommentCommandController.java       # PATCH·DELETE /api/comments/{commentId}
│   ├── application/
│   │   ├── CommentService.java                 # create / edit / delete (판정 순서·잠금·카운터·이벤트)
│   │   ├── CommentQueryService.java            # (006이 만든 파일을 넘겨받음) page / replies / around / commentIdsOfPost
│   │   ├── CommentViewAssembler.java           # 상태 판정 + 작성자 표시 + 내용 감추기 (FR-019·020)
│   │   ├── CommentModerationService.java       # hide / unhide — 014가 부름 (카운터 ±1)
│   │   ├── CommentPurgeService.java            # purgeByAuthor(memberId) — 015 WithdrawalPurgeStep order 20이 부름
│   │   ├── CommentCursor.java                  # (created_at µs, id) ↔ 001 CursorCodec, scope comments:{postId}/replies:{rootId}
│   │   └── CommentProperties.java              # @ConfigurationProperties("blog.comment")
│   ├── domain/
│   │   ├── CommentText.java                    # 정리(NFC·보이지 않는 글자·줄바꿈·빈 줄) + 길이 판정
│   │   ├── CommentState.java                   # NORMAL / DELETED / HIDDEN / WITHDRAWN_AUTHOR
│   │   └── CommentReasonCode.java              # COMMENT_REQUIRED·COMMENT_TOO_LONG·REPLY_TARGET_UNAVAILABLE·COMMENT_HIDDEN
│   └── infra/
│       ├── CommentRepository.java              # JdbcClient: insert·lock·update·delete·dedupe 조회
│       └── CommentQueryRepository.java         # 최상위·답글(LATERAL)·around SQL
├── post/application/PostCounterService.java    # (신규, post 공개) adjustCommentCount / adjustCommentCounts
├── account/application/MemberQueryService.java # + findDisplays(ids) → MemberDisplay(id, handle, nickname, withdrawn)
├── account/application/MemberDisplay.java      # (신규 record)
└── shared/event/CommentCreated.java, CommentDeleted.java

backend/src/test/
├── resources/permission/comment.csv            # 댓글 행동 4개 × 행위자 × 글 상태
└── java/com/team/blog/interaction/
    ├── unit/CommentTextTest.java, CommentStateTest.java, CommentCursorTest.java
    └── integration/
        ├── CommentReadIT.java                  # US1: 순서·페이지·답글 3개·대상 표시·자리·배지·404
        ├── CommentWriteIT.java                 # US2: 작성·답글 구조·검증·중복·제한·판정 순서
        ├── CommentEditDeleteIT.java            # US3: 수정·삭제·자리·빈 자리 정리·숨김 409
        ├── CommentVisibilityIT.java            # US4: 글 상태·숨김·탈퇴 표시
        ├── CommentAroundIT.java                # US5: around·prevCursor·답글 펼침
        ├── CommentConcurrencyIT.java           # SC-002·SC-003·FR-014
        ├── CommentXssIT.java                   # SC-005
        └── CommentPermissionMatrixIT.java      # 004 하네스 comment.csv

frontend/src/
├── api/comments.ts                             # list / replies / create / edit / remove
├── features/comments/
│   ├── useCommentThread.ts                     # 상태: 최상위 순서 + id 맵(한 번만 그리기), 답글 펼치기, around
│   ├── CommentSection.tsx                      # 머리말·입력칸(로그인·인증 안내)·목록·[댓글 더 보기]·[이전 댓글 보기]
│   ├── CommentItem.tsx                         # 상태별 표시·배지·버튼(신고 숨김)·수정 칸
│   ├── CommentForm.tsx                         # 글자 수·등록 중·오류 유지
│   ├── ReplyList.tsx                           # 답글 3개 + [답글 N개 더 보기]
│   ├── commentMessages.ts                      # code → 문구
│   └── comments.css                            # pre-line, 강조 애니메이션
├── features/post-detail/CommentSectionSlot.tsx # 자리 → CommentSection 연결
└── pages/PostDetailPage.tsx                    # 상세와 댓글 첫 페이지 동시 요청
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 댓글 코드는 `interaction` 모듈(006이 만든 `CommentQueryService` 포함)에 모으고, 다른 모듈에는 공개 API만 더한다: post의 `PostCounterService`(신규), account의 `MemberQueryService.findDisplays`(메서드 추가), shared의 이벤트 2종. 화면은 005 `CommentSectionSlot` 자리를 채운다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (위반 없음).
