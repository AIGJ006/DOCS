# Implementation Plan: 도메인 이벤트·인앱 알림

**Branch**: `011-notification` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-notification/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

회원은 내 글에 댓글, 내 댓글에 답글, 내 글 좋아요(글마다 묶음), 새 팔로워(안 읽은 동안 묶음), 팔로우한 사람의 새 글, 신고 처리 결과, 내 글·댓글 숨김을 앱 안 알림으로 받는다. 상단 종 아이콘은 30초마다 안 읽은 수를 확인하고(탭이 보일 때만), 펼치면 최근 10개, 전체 알림 페이지는 20개씩 이어 보며, 누르면 그 알림만 읽음이 되고 관련 위치로 이동한다. 알림은 글자를 저장하지 않고 보여 줄 때 다시 확인해 볼 수 없게 된 글은 "볼 수 없는 글이에요"로, 탈퇴 유예 회원은 "탈퇴한 사용자"로 보인다. 알림 처리는 원래 행동이 확정된 뒤 따로 돌아 실패해도 원래 행동을 막지 않고, 90일·사람당 1,000개로 매일 정리되며, 탈퇴 30일 정리 때 받은 것·보낸 것을 지운다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `notification`·`notification_actor`·`notification_mute`와 그 CHECK·부분 UNIQUE·인덱스를 그대로 쓴다. 공통 알림 종류는 V1 CHECK의 7종이고 친구 알림 2종은 넣지 않는다(선택 기능, 기본 비활성 — 01 결정 M1)(R1).
- **새 모듈 `notification`.** 알림 테이블 3개는 이 모듈만 쓴다. 다른 모듈은 이벤트로만 알리고, 알림 모듈은 처리 시점 확인을 001 `MemberQueryService`·004 `PostReadService`·007/009/010 interaction 공개 조회로 한다(R2, 제안 — 팀 확인 T003).
- **이벤트 구독 = 커밋 뒤 비동기 + 자기 트랜잭션.** 리스너는 `@Async("eventExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`이고, 실제 저장은 `@Transactional(REQUIRES_NEW)`인 `NotificationWriter` 메서드가 한다. 리스너가 예외를 잡아 WARN만 남겨 원래 행동에 영향이 없다(FR-002). 처리 순서에 기대지 않도록 저장 전에 지금 상태(좋아요·팔로우가 아직 있는지, 댓글이 정상인지, 글을 읽을 수 있는지, 두 사람이 탈퇴 유예가 아닌지, 끈 종류인지)를 다시 확인한다(FR-006·FR-011)(R3·R4).
- **실행기 설정.** `eventExecutor` 대기열 500 → 1,000, 종료 대기 10초 → 20초(설정값 `blog.async.event.await-termination`), 종료 때 못 끝낸 작업 수를 WARN으로 남긴다. 001 소유 `AsyncConfig`·`CoreProperties.Pool`에 필드를 더한다(R5).
- **종류별 저장.** 댓글·답글은 하나짜리(받는 사람 계산: 답글 대상 = `replyToMemberId ?? parentAuthorId`, 글 작성자와 겹치면 답글만). 좋아요·팔로우는 묶음 4단계(중복 확인 → 안 읽은 묶음 `INSERT … ON CONFLICT … DO UPDATE … RETURNING id` → `notification_actor` `ON CONFLICT DO NOTHING RETURNING` → 인원·마지막 행동자·갱신 시각). 취소·언팔로우는 안 읽은 묶음을 먼저 잠그고 사람을 뺀 뒤 다시 계산, 0명이면 삭제. 새 글은 `follow` 기준 `INSERT … SELECT` 한 문장(R6·R7·R8).
- **대상 변화.** 댓글 삭제·숨김이면 그 댓글의 `COMMENT`·`REPLY` 알림을 지운다. 글·댓글 행 삭제는 V1 FK CASCADE가 지우므로 `PostPurged`·`PostTrashed`·`PostRestored`·`PostVisibilityChanged`·`ContentUnhidden`은 구독하지 않는다(FR-017·FR-018)(R9).
- **보여 줄 때 다시 판단.** 목록은 `notification` + 행동자 `member` + 프로필 `image` + `post` + 글 작성자 `member` + `comment` + 받는 사람 `member`를 한 번에 읽는 SQL 1번이고, 읽기 판정은 읽은 행으로 `PostView`를 만들어 004 `PostAccessPolicy.canRead`를 메모리에서 부른다(FR-032). 응답은 글자 조각(닉네임·제목·미리보기·사유 코드)과 이동 주소만 주고 문장은 화면이 조립한다(R10).
- **API 7개.** `GET /api/notifications/unread-count`(`no-store`), `GET /api/notifications?cursor&size=10|20`, `PUT /api/notifications/{id}/read`(원문 PATCH → PUT, clarify 011 "이미 정해진 것"), `POST /api/notifications/read-all` → `{updated}`, `DELETE /api/notifications/{id}`, `GET`·`PUT /api/me/notification-settings`. 남의 알림·없는 번호는 관리자도 같은 404. 바꾸는 요청은 `AccountStatusGuard` `ACCOUNT_WRITE`(인증 전 통과)(R11).
- **정리.** `NotificationCleanupJob`(매일 04:30, ShedLock `notificationCleanup`): 90일 지난 알림 1,000개씩 삭제, 최근 하루 알림을 받은 사람만 최신 1,000개 초과분 삭제. 탈퇴 정리 `NotificationWithdrawalPurgeStep`(015 order 70): 받은 알림 삭제 → 남의 묶음에서 빼고 다시 계산(DELETE와 UPDATE를 나눠 실행) → 내가 행동한 하나짜리 삭제 → 끄기 설정 삭제(R13·R14).
- **화면.** 001 임시 머리말 `SessionBar`에 `NotificationBell`(배지 99+, 화면 낭독기 이름, 30초 폴링 + `visibilitychange`), 펼침 `NotificationDropdown`(열 때마다 10개), `/notifications` 페이지(20개씩, [×] 삭제), 설정 화면 "알림" 칸 `NotificationSettingsSection`. 시각 문구는 005 `relativeText`를 그대로 쓴다(R16).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation, Scheduling), `JdbcClient`, Flyway, ShedLock JDBC, 001 `AsyncConfig`·`AccountStatusGuard`·`MemberQueryService.findAccessInfo`·`CursorCodec`·`ListScope`·`CacheControlPolicy`, 004 `PostReadService`·`PostAccessPolicy`·`PostView`·`Viewer`, media `ImageUrlResolver`, 015 `WithdrawalPurgeStep`
- 이벤트(다른 기능이 만듦): 002/004 `PostWentPublic`(있음), 007 `CommentCreated`·`CommentDeleted`, 009 `PostLiked`·`PostUnliked`, 010 `MemberFollowed`·`MemberUnfollowed`, 014 `ReportResolved`·`ContentHidden`
- 새 의존성 없음
- 화면: React 18 + react-router 7, 005 `useCursorList`·`LoadMoreButton`·`relativeText`, 001 `SessionBar`·`useSession`·설정 화면(001 T122)

**Storage**:

- PostgreSQL: `notification`·`notification_actor`·`notification_mute`(notification 소유). 읽기만: `member`(받는 사람·행동자·글 작성자), `image`(현재 프로필 사진), `post`(제목·상태·숨김 사유), `comment`(미리보기·숨김 사유), `follow`(새 글 받는 사람)
- Redis: 쓰지 않는다(요청 제한 없음 — 원문에 없음)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc, `@RecordApplicationEvents`, Awaitility(비동기 리스너 대기). 화면은 Vitest + Testing Library(가짜 타이머로 30초 폴링), 종단 확인은 Playwright. 헌법 VIII에 따라 동시 10명 좋아요 묶음(SC-002), 7일 팔로우 중복(SC-003), 볼 수 없는 글 표시(SC-004), 정리 배치(SC-008), 알림 실패 격리(SC-009)는 실제 DB 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 안 읽은 수 p95 20ms 이내(`ix_notification_unread` 부분 인덱스 count 1번) — 회원 수천 명이 30초마다 부르는 요청
- 목록 p95 100ms 이내(SQL 1번, `ix_notification_list`)
- 새 글 알림: 팔로워 1만 명에 `INSERT … SELECT` 1문장 1초 이내(비동기)
- 정리 배치: 비용이 그날 알림을 받은 사람 수에 비례(FR-037)

**Constraints**:

- 이벤트에는 ID·enum·`Instant`만(FR-004, SC-011). 리스너는 자기 모듈 Service만 부른다
- 알림 처리 실패·지연이 원래 요청에 전파되지 않는다(FR-002). 유실 허용(FR-003)
- 목록은 알림마다 따로 조회하지 않는다(FR-032)
- 남의 알림은 관리자도 404, 주소에 회원 번호 없음(FR-033)
- 화면은 375px 폭부터 가로 스크롤 없음, 안 읽음은 ● + 굵은 글자(색만으로 구분 안 함)

**Scale/Scope**:

- 회원 수천 명, 한 사람 알림 최대 1,000개, 인기 회원 팔로워 1만 명 기준
- API 7개, 구독 이벤트 9종, 정리 작업 1개 + 탈퇴 정리 단계 1개, 화면 컴포넌트 4개 + 페이지 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 알림 테이블 3개 그대로. 친구 알림은 적용자가 자기 마이그레이션으로 CHECK를 넓힌다(03 E-10) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (예외 2건 — Complexity Tracking)** | 알림 테이블은 notification만 쓴다. 처리 시점 확인은 공개 Service로만. 예외: 목록 SQL이 `member`·`image`·`post`·`comment`를, 새 글 일괄 저장이 `follow`·`member`를 읽기 전용으로 읽는다. 새 모듈 `notification`은 헌법 II 모듈 목록에 없다(014 `moderation`과 같은 상황 — 006이 임시로 만든 패키지를 014가 넘겨받음, 팀 확인 T003) |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 모든 SQL에 `receiver_id = :me`(세션 회원). 남의 번호·없는 번호·숫자가 아닌 번호는 같은 404. 관리자 예외 없음. 권한 매트릭스 `notification.csv`(R15) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 닉네임·제목·댓글 미리보기는 텍스트 노드로만. 미리보기는 서버가 앞 50자를 잘라 주고 화면은 마크다운으로 해석하지 않는다 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 알림은 커밋 뒤 비동기·별도 트랜잭션·예외 삼킴. 대기열 초과는 버리고 WARN. 안 읽은 수 확인 실패는 화면이 조용히 넘어간다 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 휴지통·비공개·숨김은 알림을 지우지 않고 표시만 바꾼다. 90일·1,000개 정리와 탈퇴 30일 정리만 지운다 |
| VII. 수치는 설정값으로 | **PASS** | `blog.notification.*`(보관 90일, 사람당 1,000개, 정리 묶음 1,000, 정리 시각, 팔로우 중복 7일, 미리보기 50자, 목록 크기 10·20), `blog.async.event.queue-capacity`(1,000)·`await-termination`(20초) |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 동시 좋아요 10명, 취소·재클릭 5번, 팔로우 7일, 새 글 1만 명, 정리 배치, 탈퇴 정리 다시 계산, 리스너 실패 격리 |

**Gate 결과 (Phase 0 전)**: 원칙 II 읽기 예외 2건을 Complexity Tracking에 적었다. 005 카드 SQL과 같은 종류의 예외다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. spec Implementation Notes의 `PATCH /api/notifications/{id}/read`는 clarify 011 "이미 정해진 것"대로 `PUT`으로 바꿨다(멱등 — 이미 읽음이어도 204).
  2. 리스너 메서드에 `@Transactional(REQUIRES_NEW)`를 직접 붙이면 예외를 잡는 위치가 트랜잭션 안이 되어 일부만 저장될 수 있다. 리스너(예외 잡기)와 `NotificationWriter`(트랜잭션)를 나눈다. 결과는 Implementation Notes와 같다(R3).
  3. 007 `CommentCreated`의 `replyToMemberId`는 "내 답글에 다시 단 답글"이면 NULL이라, 이 경우 최상위 작성자가 답글 알림을 받는다. spec US1 #2의 "답글에 답한 경우 최상위 작성자는 받지 않는다"와 이 한 경우만 다르다(R6, 팀 확인 T004).
  4. 007 contracts/events.md의 "탈퇴 정리(015 `MemberPurged`가 대신)"는 015에 `MemberPurged`가 없어 틀린 문장이다. 탈퇴 정리 때 알림은 이 기능의 order 70 단계가 지운다(Tier B/C analyze에서 007 문서를 고쳤다 — ANALYSIS-tier-bc).
  5. 015가 아직 없으면 `WithdrawalPurgeStep` 인터페이스(015 tasks T009)를 이 기능이 먼저 만든다.
  6. 014가 없으면 US5(신고 결과·숨김 알림)는 이벤트가 오지 않아 테스트할 수 없다. 리스너와 표시는 만들고 통합 테스트는 이벤트를 직접 발행해 확인한다.

## Project Structure

### Documentation (this feature)

```text
specs/011-notification/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml           # 알림 API 7개
│   └── notification-sql.md    # 구독 표, 저장·묶음·취소·새 글 SQL, 목록 SQL, 정리·탈퇴 정리 SQL
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── notification/                                   # (신규 모듈)
│   ├── package-info.java
│   ├── web/
│   │   ├── NotificationController.java             # unread-count, 목록, read, read-all, delete
│   │   └── NotificationSettingsController.java     # GET·PUT /api/me/notification-settings
│   ├── application/
│   │   ├── listener/
│   │   │   ├── CommentNotificationListener.java    # CommentCreated, CommentDeleted
│   │   │   ├── LikeNotificationListener.java       # PostLiked, PostUnliked
│   │   │   ├── FollowNotificationListener.java     # MemberFollowed, MemberUnfollowed
│   │   │   ├── NewPostNotificationListener.java    # PostWentPublic
│   │   │   └── ModerationNotificationListener.java # ReportResolved, ContentHidden
│   │   ├── NotificationWriter.java                 # REQUIRES_NEW 저장 (하나짜리·묶음·취소·새 글·삭제)
│   │   ├── NotificationEligibility.java            # 공통 제외 규칙 ①~⑤
│   │   ├── NotificationQueryService.java           # 안 읽은 수, 목록(PostView + canRead)
│   │   ├── NotificationCommandService.java         # 읽음, 모두 읽음, 삭제
│   │   ├── NotificationSettingsService.java
│   │   ├── NotificationCursor.java                 # ListScope "notifications", (updated_at, id)
│   │   ├── NotificationItemAssembler.java          # 행 → 응답 항목(주소·미리보기·표시 규칙)
│   │   ├── NotificationCleanupJob.java             # 04:30, ShedLock notificationCleanup
│   │   ├── NotificationWithdrawalPurgeStep.java    # 015 order 70
│   │   └── NotificationProperties.java             # blog.notification.*
│   ├── domain/
│   │   ├── NotificationType.java                   # 7종 (V1 CHECK와 같은 순서)
│   │   ├── MutableType.java                        # 끌 수 있는 5종
│   │   └── GroupKey.java                           # LIKE:post:{id}, FOLLOW
│   └── infra/
│       ├── NotificationRepository.java             # 저장·묶음·취소·읽음·삭제·정리
│       ├── NotificationListQueryRepository.java    # 목록 SQL 1번 (Complexity Tracking)
│       └── NotificationMuteRepository.java
├── post/application/PostReadService.java           # + isReadable(postId, viewer) (004 소유 파일)
├── interaction/application/CommentQueryService.java # + isActive(commentId) (007 소유)
├── interaction/application/LikeQueryService.java    # + isLiked(postId, memberId) (009 소유, 없으면 이 기능이 더함)
├── interaction/application/FollowQueryService.java  # isFollowing(followerId, followeeId) (010)
├── shared/config/AsyncConfig.java, CoreProperties.java  # 대기열·종료 대기 설정값, 남은 작업 수 WARN (001 소유)
└── shared/application/withdraw/WithdrawalPurgeStep.java # 015 T009 (없으면 이 기능이 만듦)

backend/src/main/resources/application.yml          # blog.notification.*, blog.async.event.*

backend/src/test/
├── resources/permission/notification.csv           # 7개 행위 × 7 행위자
└── java/com/team/blog/
    ├── shared/event/DomainEventShapeTest.java      # SC-011 (글자 필드 없음)
    ├── shared/config/EventExecutorShutdownIT.java  # 20초 마무리·남은 수 WARN
    └── notification/integration/
        ├── CommentNotificationIT.java              # US1 #1~#3, US4 #5
        ├── NotificationExclusionIT.java            # 본인·유예·끔·읽을 수 없음 (SC-001·SC-004)
        ├── LikeGroupingIT.java                     # US3, SC-002 (동시 10명·5번 반복)
        ├── FollowGroupingIT.java                   # SC-003 (7일)
        ├── NewPostNotificationIT.java              # US1 #6, 1만 명
        ├── NotificationApiIT.java                  # US2
        ├── NotificationDisplayIT.java              # US4 (볼 수 없는 글·탈퇴한 사용자·미리보기)
        ├── ModerationNotificationIT.java           # US5 (이벤트 직접 발행)
        ├── NotificationSettingsIT.java             # US6
        ├── NotificationCleanupIT.java              # US7, SC-008
        ├── NotificationWithdrawalPurgeIT.java      # US7 #4 (다시 계산)
        ├── NotificationFailureIsolationIT.java     # SC-009
        └── NotificationPermissionMatrixIT.java     # 004 하네스

frontend/src/
├── api/notifications.ts                            # unreadCount / list / markRead / markAllRead / remove / settings
├── features/notification/
│   ├── useUnreadCount.ts                           # 30초 폴링 + visibilitychange, 실패 시 마지막 값
│   ├── NotificationBell.tsx                        # 배지 99+, aria-label "안 읽은 알림 N개"
│   ├── NotificationDropdown.tsx                    # 열 때마다 10개, 불러오는 중·실패·빈 상태
│   ├── NotificationItem.tsx                        # ● + 굵게, 누르면 읽음 + 이동
│   ├── notificationText.ts                         # 종류별 문장 조립 (서버는 조각만 줌)
│   ├── reasonLabels.ts                             # 숨김 사유 코드 → 이름 (014와 공유, 먼저 하는 쪽이 만듦)
│   └── NotificationSettingsSection.tsx             # 설정 "알림" 칸
├── pages/NotificationsPage.tsx                     # /notifications
├── features/auth/SessionBar.tsx                    # 종 아이콘 자리 (001 소유 임시 머리말)
└── App.tsx                                         # /notifications 경로
```

**Structure Decision**: 02 §3 package-by-feature 구조에 `notification` 모듈을 새로 둔다(006 `moderation`과 같은 방식). 알림 테이블은 이 모듈만 쓰고, 처리 시점 확인은 다른 모듈의 공개 Service로만 한다. 다른 모듈 파일은 공개 조회 메서드 1개씩만 더한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `notification.infra.NotificationListQueryRepository`가 `member`(행동자·글 작성자·받는 사람)·`image`(현재 프로필 사진)·`post`(제목·상태·공개 범위·휴지통·숨김·사유)·`comment`(미리보기·숨김·사유)를 읽기 전용 JOIN한다(원칙 II 읽기 예외) | FR-032가 "알림·행동한 사람·글 제목을 한 번에 가져오고 읽기 판정도 그 결과로 한꺼번에"를 요구한다. 읽기 판정에 필요한 글 상태 5개와 작성자 탈퇴 여부가 같은 행에 있어야 `PostAccessPolicy.canRead`를 메모리에서 부를 수 있다 | 알림만 읽고 001·004·007·media 공개 조회로 채우면 페이지마다 SQL이 5번 이상이 되고(FR-032 위반), 글 번호 목록으로 묶어 조회해도 4번이다 |
| 새 글 알림 `INSERT … SELECT`가 `follow`·`member`(받는 사람 탈퇴 유예)를 읽는다 | 25 §4-1이 팔로워 전원을 한 문장으로 넣도록 정했다. 팔로워 1만 명이면 회원 번호를 가져와 다시 넣는 방식은 왕복과 메모리가 크다 | 010 `FollowQueryService.followerIdsOf`로 번호를 받아 배치 INSERT하면 SQL 2번 + 1만 개 배열 전송이고, 받는 사람 탈퇴 유예·끈 종류 확인을 메모리에서 다시 해야 한다 |
