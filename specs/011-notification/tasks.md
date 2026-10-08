---

description: "Task list for 011-notification (도메인 이벤트·인앱 알림)"
---

# Tasks: 도메인 이벤트·인앱 알림

**Input**: Design documents from `/specs/011-notification/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, notification-sql.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다. 비동기 리스너 결과는 Awaitility로 기다린다(고정 sleep 금지).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 새 `notification` 모듈과 알림 테이블 3개(V1)를 소유한다. 다른 기능의 이벤트를 구독하고, 처리 시점 확인용 공개 조회 메서드를 004·007·009·010 파일에 하나씩 더한다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `AsyncConfig`(`eventExecutor`)·`CoreProperties`, `@LoginRequired`, `AccountStatusGuard`(`ACCOUNT_WRITE`), `WithdrawnAccountGateFilter`, `MemberQueryService.findAccessInfo`, `CursorCodec`·`ListScope`, `CacheControlPolicy.NO_STORE`, CSRF, ShedLock(`SchedulingConfig`), `SessionBar`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`SqlCounter`·`MutableClock`
- 선행: specs/002·004 — `PostWentPublic`(있음), `PostReadService`·`PostAccessPolicy`·`PostView`·`Viewer`, 권한 하네스(`support/permission/`)
- 선행: specs/005 — 글 상세 `?comment=` 이동, `useCursorList`·`LoadMoreButton`, `F/features/time/dateFormat.ts` `relativeText`
- 선행(이벤트 — 없으면 그 종류의 알림만 늦게 붙인다): specs/007 `CommentCreated`·`CommentDeleted`·`CommentQueryService`(US1 댓글·US4), specs/009 `PostLiked`·`PostUnliked`(US1 좋아요·US3), specs/010 `MemberFollowed`·`MemberUnfollowed`·`FollowQueryService`·`/@{handle}/followers`(US1 팔로우·US3)
- 선행(둘 중 먼저 하는 쪽이 만듦): specs/015 T009 `WithdrawalPurgeStep` 인터페이스, specs/014 `ReportResolved`·`ContentHidden` 이벤트 record와 `F/features/moderation/reasonLabels.ts`

**006 머지 후**

- `F/App.tsx`에 `/notifications` 경로를 더하는 작업(T032). 006이 같은 파일에 `/manage/posts` 경로와 상세 `deleteControl`을 더한다. 그 밖에 겹치는 파일은 없다(`AsyncConfig`·`CoreProperties`·`PostReadService`·`SessionBar`는 006 브랜치와 내용이 같음)

**후속 (다른 스펙이 이 기능을 사용)**

- 014-report-hide: 신고 처리·숨김 이벤트를 내면 US5 알림이 붙는다. 숨김 알림 이동(글 상세 작성자 안내)은 005 T055 화면
- 015-withdraw: `NotificationWithdrawalPurgeStep`(order 70, 이 기능의 T056)이 015 `required-orders`에 들어 있어야 정리 작업이 돈다
- 012-trending-search: 이벤트를 구독하지 않는다(트렌딩·검색·sitemap은 요청 때 공용 조건으로 거름 — 012 research). 실행기 설정 변경(대기열 1,000·20초)은 `eventExecutor`를 쓰는 다른 리스너에도 그대로 적용된다

**팀 결정 대기 (기본안으로 진행)**

- `notification` 모듈을 새로 두고 헌법 II 모듈 목록에 `moderation`·`notification`을 더하는 것(research R2) — 확인 작업 T003
- "내 답글에 다시 단 답글"의 답글 알림을 최상위 작성자에게 보내는 것(research R6, 007 이벤트 필드 한계) — 확인 작업 T004

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 모듈 뼈대, 설정값, 팀 확인 질문

- [X] T001 선행 확인: V1 `notification`(CHECK 4개·`uq_notification_unread_group`·인덱스 6개)·`notification_actor`·`notification_mute`, `B/shared/config/AsyncConfig.java`·`CoreProperties.java`, `B/post/application/PostReadService.java`, `B/shared/event/`에 007·009·010·014 이벤트 record가 있는지, `B/interaction/application/`의 `CommentQueryService`·`LikeQueryService`·`FollowQueryService`, `B/shared/application/withdraw/WithdrawalPurgeStep.java`(015), `F/features/auth/SessionBar.tsx`, 001 설정 화면(T122)이 있는지 기록한다 (구현 메모: main ce11144 기준: V1 알림 테이블 3개·CHECK·uq_notification_unread_group·인덱스 6개 있음. AsyncConfig·CoreProperties·PostReadService 있음(isReadable 없음). shared.event에 007 CommentCreated·CommentDeleted, 009 PostLiked·PostUnliked 있음, 010 MemberFollowed·MemberUnfollowed·014 ReportResolved·ContentHidden 없음(010은 010-follow 브랜치에서 진행 중). CommentQueryService 있음(isActive 없음), LikeQueryService 없음, FollowQueryService 없음(010). WithdrawalPurgeStep(015) 있음, 015 임시 InterimNotificationWithdrawalPurgeStep(order 70) 있음. 001 SessionBar는 공통 머리말 components/SiteHeader.tsx(site-header-actions)로 바뀜. 001 설정 화면 pages/SettingsPage.tsx 있음. MutableClock은 테스트 소스에 없음 — 시각이 필요한 시험은 행의 시각을 직접 넣는다)
- [X] T002 [P] 모듈 뼈대 `B/notification/package-info.java`(모듈 규칙: 알림 테이블은 이 모듈만, 다른 모듈은 공개 Service로만)와 설정값 `B/notification/application/NotificationProperties.java`(`@ConfigurationProperties("blog.notification")` + `@Validated`, data-model §6 키), `R/application.yml`에 research R17 기본값, 테스트 `T/notification/unit/NotificationPropertiesBindingTest.java` (구현 메모: NotificationProperties는 cleanup.cron을 포함해 data-model §6 키 전부, application.yml에 같은 기본값)
- [X] T003 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다: ① 새 `notification` 모듈(R2) ② 헌법 II 모듈 목록에 `moderation`(006)·`notification` 추가. 답이 오기 전에는 기본안으로 진행한다 (구현 메모: specs/ANALYSIS-tier-bc.md R2(→ 팀 결정 2)에 이미 올라가 있어 문서는 고치지 않았다. 기본안(새 notification 모듈)으로 진행)
- [X] T004 팀 확인 질문(007 담당과 함께): "내 답글에 다시 단 답글"은 `reply_to_member_id`가 NULL이라 최상위 작성자가 답글 알림을 받는다(R6). 그대로 둘지, 007 `CommentCreated`에 `replyToCommentAuthorId`를 더할지. 답이 오기 전에는 그대로 둔다 (구현 메모: ANALYSIS-tier-bc.md R8(→ 팀 결정 5)에 이미 있음. 답이 없어 기본안 그대로(replyToMemberId가 NULL이면 parentAuthorId에게 REPLY))

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 실행기 설정, 이벤트 형식 검사, 도메인 값, 공통 제외 규칙, 저장소 바탕, 테스트 도구

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] 테스트 `T/shared/config/EventExecutorShutdownIT.java`: 대기열 크기 = `blog.async.event.queue-capacity`(1,000), 가득 차면 버리고 WARN(`OutputCaptureExtension`), 종료 대기 = `await-termination`(20초 — 테스트는 1초로 줄여 확인), 시간 안에 못 끝낸 작업 수 WARN(FR-003). 실패 확인 (구현 메모: 실행기 단위 확인은 AsyncConfig.executor(패키지 공개)로 작은 실행기를 만들어 1초 종료 대기로 확인, 설정값은 공용 통합 컨텍스트의 eventExecutor·CoreProperties로 확인)
- [X] T006 `B/shared/config/CoreProperties.java` `Pool`에 `Duration awaitTermination`(기본 `10s`, `@DefaultValue`)을 더하고, `B/shared/config/AsyncConfig.java`가 `setAwaitTerminationSeconds`에 그 값을 쓰며 실행기를 `ReportingTaskExecutor`(같은 파일 안 정적 하위 클래스 — `shutdown()` 뒤 남은 `queue.size() + activeCount` WARN)로 만든다. `R/application.yml` `blog.async.event.queue-capacity: 1000`, `await-termination: 20s`. 001 `CorePropertiesBindingTest`에 새 키를 더한다(001 소유 파일 — 001 담당에게 알림) (T005 통과) (구현 메모: ReportingTaskExecutor는 AsyncConfig 안 정적 하위 클래스. 남은 수 = queue.size + activeCount, 0이면 로그 없음. setAwaitTerminationMillis 사용)
- [X] T007 [P] 테스트 `T/shared/event/DomainEventShapeTest.java`(SC-011): `shared.event` 패키지의 `DomainEvent` 구현 record를 클래스 경로에서 모두 찾아, 필드 타입이 `long`·`Long`·`Instant`·enum만인지 검사한다(`String`·컬렉션·엔티티 금지). 지금 있는 3개(`PostPublished`·`PostEdited`·`PostWentPublic`)로 통과해야 하고, 다른 기능이 이벤트를 더하면 자동으로 검사된다 (구현 메모: main의 shared.event 이벤트 15개(007·009·015 포함) 모두 통과. 스캔은 ClassPathScanningCandidateComponentProvider)
- [X] T008 [P] 도메인 값 `B/notification/domain/NotificationType.java`(7종, `isOperational`·`isGrouped`·`needsReadablePost`), `MutableType.java`(5종), `GroupKey.java`(`like(postId)`·`follow()`), 테스트 `T/notification/unit/NotificationTypeTest.java`(V1 `ck_notification_type`·`ck_notification_mute_type` 값과 같은지 `information_schema`로 비교하는 통합 테스트 1개 포함) (구현 메모: V1 CHECK 비교 통합 시험은 surefire가 DB 없이 돌아 따로 T/notification/integration/NotificationTypeSchemaIT로 뺐다. NotificationType.mutable()을 더함)
- [X] T009 [P] 004 `B/post/application/PostReadService.java`에 `boolean isReadable(long postId, Viewer viewer)`(없는 글 false, 판정은 `requireReadable`과 같음)를 더하고 `T/post/application/PostReadServiceIsReadableTest.java`(공개·비공개·휴지통·숨김·작성자 유예 × 작성자·남·비회원) (004 소유 파일 — 004 담당에게 알림) (구현 메모: DB 글 상태가 필요해 시험 이름을 PostReadServiceIsReadableIT(통합)로 했다. 글 7상태 × 작성자·남·비회원 + 없는 글. 004 소유 파일 — 보고에 적음)
- [X] T010 [P] `B/notification/infra/NotificationMuteRepository.java`(`isMuted(memberId, type)`, `mutedTypes(memberId)`, `replace(memberId, Set<MutableType>, now)` — contracts §9 SQL) (구현 메모: = ANY(:muted)는 JdbcClient가 목록을 펼쳐 NOT IN (:muted)로, 빈 목록이면 전부 삭제)
- [X] T011 `B/notification/application/NotificationEligibility.java`(contracts §2 ①~⑤: `MemberQueryService.findAccessInfo`로 받는 사람·행동자 유예 판정과 받는 사람 `Viewer` 만들기, `NotificationMuteRepository.isMuted`(운영 알림 제외), `PostReadService.isReadable`) + 단위 테스트 `T/notification/unit/NotificationEligibilityTest.java`(각 조건이 따로 막는지, 순서대로 멈추는지) (T009·T010 다음) (구현 메모: check()가 걸린 규칙(Rejection)을 돌려주고 allows()는 그 boolean. NEW_POST는 사람마다 읽기 판정 안 함(R8))
- [X] T012 `B/notification/infra/NotificationRepository.java` 바탕: `insertSingle(...)`(contracts §3), `countUnread(receiverId)`, `deleteCommentNotifications(commentId)`(§7-1). `JdbcClient`만 쓴다 (T008 다음)
- [X] T013 `B/notification/application/NotificationWriter.java` 뼈대(`@Service`, 메서드마다 `@Transactional(propagation = REQUIRES_NEW)`, `Clock`)와 리스너 공통 도우미 `B/notification/application/listener/ListenerSupport.java`(`runSafely(String eventName, long targetId, Runnable)` — `RuntimeException`을 잡아 ID만 WARN) (T011·T012 다음) (구현 메모: ListenerSupport.runSafely는 예외 클래스 이름만 남긴다(메시지에 글자가 섞일 수 있어 스택도 남기지 않음))
- [X] T014 [P] 테스트 도구 `T/notification/support/NotificationFixtures.java`(알림·묶음·끄기 행 직접 넣기, `updated_at` 지정), `NotificationAwait.java`(Awaitility로 "받는 사람의 알림 수가 N이 될 때까지", 최대 5초), `EventPublisherHelper.java`(테스트에서 트랜잭션 안 `publishEvent` 후 커밋) (구현 메모: 추가: ExecutorBlocker(실행기 막기), NotificationTestBase(시작·끝에 실행기가 빌 때까지 기다리고 알림 테이블을 비움 — 앞 테스트 이벤트가 늦게 처리돼 새 행에 붙는 것 방지). '생기지 않음'은 NotificationAwait.idle()(실행기 빔)로 확인. Awaitility를 pom 시험 의존성에 더함(버전은 Boot 관리))

**Checkpoint**: 실행기·이벤트 형식·제외 규칙·저장 바탕 준비 완료 — User Story 작업 시작 가능

---

## Phase 3: User Story 1 - 내 글·댓글·블로그에 생긴 반응을 알림으로 받는다 (Priority: P1) 🎯 MVP

**Goal**: 댓글·답글·좋아요·새 팔로워·새 글 알림이 공통 제외 규칙을 지켜 만들어지고, 알림 처리 실패가 원래 행동을 막지 않는다.

**Independent Test**: A의 공개 글에 B가 댓글·좋아요, B가 A를 팔로우, A가 팔로우한 C가 글을 처음 공개 → A에게 각 알림. A가 자기 글에 댓글·좋아요 → 0건.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T015 [P] [US1] `T/notification/integration/CommentNotificationIT.java` — US1 #1~#3: 최상위 댓글 → 글 작성자 `COMMENT`(`post_id`·`comment_id`·`last_actor_id`·`actor_count 1`), 답글 → 답글 대상 `REPLY` + 글 작성자 `COMMENT`, 답글의 답글 → 그 답글 작성자 `REPLY`·최상위 작성자 없음, 대상이 글 작성자면 `REPLY` 하나. 처리 전에 댓글이 지워졌으면 0 (007 머지 후) (구현 메모: 007 댓글 API로 만들고 처리 결과를 DB로 확인. '처리 전 삭제'는 ExecutorBlocker로 실행기를 막고 확인)
- [X] T016 [P] [US1] `T/notification/integration/NotificationExclusionIT.java` — SC-001·SC-004, US1 #4·#5·#7: 내 글에 내 댓글·좋아요 0, 이벤트를 발행한 뒤 처리 전에 글을 비공개로 바꾸면 댓글·좋아요·새 글 알림 0(테스트용으로 실행기를 잠시 막아 순서를 만듦), 받는 사람 유예 0, 행동자 유예 0, 좋아요 취소가 먼저 처리되면 0, 정지 회원도 받는다 (구현 메모: 댓글·좋아요의 받는 사람은 글 작성자라 자기 비공개 글은 읽을 수 있어(R4 ⑤) '처리 전 비공개' 대신 '처리 전 휴지통'으로 0을 확인하고, 비공개는 답글 대상(남)·새 글 팔로워로 0을 확인. 007이 유예 회원 댓글에 답글을 막아 받는 사람 유예·행동자 유예는 행을 직접 넣고 이벤트를 발행)
- [X] T017 [P] [US1] `T/notification/integration/NewPostNotificationIT.java` — US1 #6, SC-012: 공개 발행 → 팔로워 3명 각 1개(`last_actor_id = 작성자`), 비공개 발행 뒤 처음 공개 → 1번, 다시 비공개 → 공개 → 다시 발행 → 더 없음, 유예 팔로워·`NEW_POST` 끈 팔로워 제외, 같은 `PostWentPublic` 두 번 발행 → 1개, 팔로워 1만 명 1초 이내(`@Tag("slow")`) (010 머지 후) (구현 메모: 팔로우는 follow 행을 직접 넣음(010 API와 무관). 1만 명은 NotificationWriter.addNewPost를 직접 불러 1초 이내 확인)
- [X] T018 [P] [US1] `T/notification/integration/NotificationFailureIsolationIT.java` — US1 #8, SC-009: `@MockitoSpyBean NotificationWriter`가 예외를 던지게 한 상태에서 댓글 작성·좋아요·팔로우·발행 API가 모두 성공(상태 코드·DB 행), WARN 로그에 이벤트 종류와 ID만(제목·닉네임 없음) (구현 메모: @MockitoSpyBean 대신 시험 소스 @Profile("test") @Component BeanPostProcessor NotificationWriterFailureSwitch로 예외를 켬(새 컨텍스트 없음). 010 머지 후 팔로우 API도 포함)

### Implementation for User Story 1

- [X] T019 [P] [US1] 처리 시점 확인용 공개 조회(각 소유 기능 파일에 메서드 1개씩, 먼저 하는 쪽이 더함): 007 `B/interaction/application/CommentQueryService.java` `isActive(commentId)`, 009 `LikeQueryService.isLiked(postId, memberId)`(파일이 없으면 만든다), 010 `FollowQueryService.isFollowing(followerId, followeeId)`(없으면 더한다). 각 단위 테스트 1개씩 (각 기능 머지 후) (구현 메모: CommentQueryService.isActive 추가, LikeQueryService(새 파일, 009 소유 패키지)에 isLiked, FollowQueryService.isFollowing은 010 머지로 이미 있음. 시험은 DB가 필요해 InteractionStateQueryIT 하나로)
- [X] T020 [US1] `NotificationRepository`에 묶음 저장(contracts §4 ①~④: `existsActor`, `upsertUnreadGroup`, `insertActor`, `bumpGroup`, ③이 0행이고 0명이면 지우기)과 새 글 일괄 저장 `insertNewPostForFollowers(postId, authorId, now)`(§6)를 더한다 (T012 다음) (구현 메모: existsActor·upsertUnreadGroup·insertActor·bumpGroup·deleteIfEmpty·insertNewPostForFollowers. 새 글 SQL에 r.deleted_at IS NULL(익명 처리 회원)을 더함)
- [X] T021 [US1] `NotificationWriter`에 `addComment(CommentCreated)`(research R6 받는 사람 계산 → 사람마다 `NotificationEligibility` → `insertSingle`), `addLike(receiverId, actorId, postId)`, `addFollow(receiverId, actorId)`(+ `isLiked`·`isFollowing` 확인, FOLLOW는 아직 중복 기간 없이 — US3에서 더함), `addNewPost(postId, authorId)`(비회원 기준 `isReadable` 확인 후 §6)를 구현한다 (T015~T017 실패 확인, T019·T020 다음) (구현 메모: FOLLOW는 중복 기간(follow-dedup-window)까지 함께 넣음(T036 일부를 앞당김))
- [X] T022 [US1] 리스너 `B/notification/application/listener/CommentNotificationListener.java`(`CommentCreated`), `LikeNotificationListener.java`(`PostLiked`), `FollowNotificationListener.java`(`MemberFollowed`), `NewPostNotificationListener.java`(`PostWentPublic`) — 모두 `@Async("eventExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)` + `ListenerSupport.runSafely` (T015~T018 통과, 이벤트별로 각 기능 머지 후) (구현 메모: 010이 main에 머지돼(9f86ee2) 팔로우 리스너까지 연결)

**Checkpoint**: 반응이 생기면 알림 행이 만들어진다(API·화면 전)

---

## Phase 4: User Story 2 - 알림을 확인하고 읽음·삭제 처리한다 (Priority: P1)

**Goal**: 종 아이콘 배지·30초 확인·펼침 10개·전체 페이지 20개·읽음·모두 읽음·삭제. 남의 알림은 관리자도 404.

**Independent Test**: 알림 3개를 가진 회원으로 배지·펼침·누르기·[모두 읽음]·삭제를 하고, 다른 회원·관리자로 그 번호에 요청해 404를 확인한다.

### Tests for User Story 2 ⚠️

- [X] T023 [P] [US2] `T/notification/integration/NotificationApiIT.java` — US2 #1·#4~#8: `unread-count` `{count}` + `Cache-Control: private, no-store`, 목록 정렬(`updated_at` 최신 → 번호 큰 순)·`size` 10/20·기본 20·`size=15` 400 `VALIDATION_FAILED`·커서로 25개 끝까지 중복·누락 0·다른 목록 커서 400 `INVALID_CURSOR`, 목록 SQL 1번(`SqlCounter`), 읽음 204·다시 204, 모두 읽음 12개 `{updated: 12}` 뒤 수 0, 삭제 204·다시 404, 숫자가 아닌 번호 404, 인증 전 회원 모두 가능, 비회원 401, 유예 403 `ACCOUNT_WITHDRAWN`, CSRF 없음 403 (구현 메모: SQL 1번은 HTTP 요청의 필터 SQL(세션 회원 상태 확인 등)을 빼려고 NotificationQueryService.page를 SqlCounter 안에서 직접 불러 확인)
- [X] T024 [P] [US2] 권한 매트릭스 `TR/permission/notification.csv`(research R15 표, owner `011`)와 `T/notification/integration/NotificationPermissionMatrixIT.java`(004 하네스, 남의 알림 대상 픽스처는 `NotificationFixtures`) — SC-007 (구현 메모: 54행(행동 9 × 행위자 6, targetState NONE). 남의 알림은 notification.read.others·notification.delete.others 행동으로 나눔. 행위자 번호는 테스트 로그인이 남기는 Redis 키 member:active-touch:{id}로 찾음)
- [ ] T025 [P] [US2] 화면 테스트 `F/features/notification/__tests__/useUnreadCount.test.ts`(가짜 타이머: 마운트 즉시 1번, 30초마다, `hidden`이면 멈춤·`visible`이면 즉시 1번, 실패하면 마지막 값 유지, 로그아웃이면 부르지 않음), `NotificationBell.test.tsx`(배지 0 숨김·3·100 → "99+", 이름 "안 읽은 알림 3개"·0개 "알림"), `NotificationDropdown.test.tsx`(열 때마다 요청, "불러오는 중…"·빈 "새 알림이 없어요"·실패 "알림을 불러오지 못했어요 [다시 시도]" + 배지 그대로, Esc로 닫고 초점 복귀), `NotificationItem.test.tsx`(안 읽음 ● + `<strong>`, 누르면 읽음 요청 후 `url`로 이동, `url` null이면 이동 없이 읽음 표시)
- [ ] T026 [P] [US2] `F/pages/__tests__/NotificationsPage.test.tsx` — 20개씩 [더 보기], 같은 번호 건너뜀(FR-029), [×] 누르면 목록에서 빠짐, [모두 읽음], 비회원은 로그인 화면으로

### Implementation for User Story 2

- [X] T027 [P] [US2] `B/notification/application/NotificationCursor.java`(`ListScope.of("notifications")`, 키 `[updated_at 마이크로초, id]`, 001 `CursorCodec`)
- [X] T028 [US2] `B/notification/infra/NotificationListQueryRepository.java` — research R10 SQL 1번(커서가 있을 때만 조건, `size + 1`개), 행 record `NotificationRow`. 클래스 주석에 plan Complexity Tracking 1행(원칙 II 읽기 예외)을 적는다 (T027 다음) (구현 메모: NotificationRow에 comment_deleted_at·last_actor_id도 읽음)
- [X] T029 [US2] `B/notification/application/NotificationItemAssembler.java`(행 → data-model §5 `NotificationItem`: 행동자, `othersCount`, `PostView` + `PostAccessPolicy.canRead`로 `post`·`url`, 종류별 이동 주소 FR-024, 프로필 주소는 media `ImageUrlResolver`)와 `NotificationQueryService.java`(`unreadCount`, `page(viewer, cursor, size)` — `size`는 `dropdown-size`·`page-size`만 허용) (T028 다음) (구현 메모: 표시 규칙(US4 T041 범위: 탈퇴 행동자·미리보기·읽을 수 없는 글·숨김)까지 이 단계에서 함께 구현. 응답 모델은 NotificationItem 안 중첩 record(ActorMember/ActorWithdrawn{withdrawn:true}, PostReadable/PostUnavailable{unavailable:true}))
- [X] T030 [US2] `B/notification/application/NotificationCommandService.java`(`markRead`·`markAllRead`·`delete` — contracts §9, 0행이면 `NotFoundException`, `AccountStatusGuard.requireActive(me, ACCOUNT_WRITE)`)와 `B/notification/web/NotificationController.java`(5개 API, `@LoginRequired`, 경로 변수 숫자가 아니면 404, 목록·수 응답에 `CacheControlPolicy.NO_STORE`) (T023·T024 통과) (구현 메모: size는 문자열로 받아 숫자가 아니어도 400 VALIDATION_FAILED(INVALID_SIZE). 모두 읽음 응답도 no-store)
- [ ] T031 [P] [US2] `F/api/notifications.ts`(`getUnreadCount`, `listNotifications({size, cursor})`, `markRead(id)`, `markAllRead()`, `deleteNotification(id)`, 001 `client` + CSRF)와 타입 `F/api/types/notification.ts`(openapi `NotificationItem`과 같은 모양)
- [ ] T032 [US2] (**006 머지 후** — `App.tsx`) 화면 `F/features/notification/useUnreadCount.ts`, `NotificationBell.tsx`, `NotificationDropdown.tsx`, `NotificationItem.tsx`, `notificationText.ts`(research R16 문구 — 이 단계는 댓글·답글·좋아요·팔로우·새 글 하나짜리 문장), `F/pages/NotificationsPage.tsx`(005 `useCursorList`·`LoadMoreButton`, [×] `aria-label="알림 삭제"`), `F/App.tsx` `/notifications` 경로, 001 `F/features/auth/SessionBar.tsx`에 로그인했을 때만 `NotificationBell`(001 담당에게 알림). 시각은 005 `relativeText` + `<time dateTime>` (T025·T026 통과)

**Checkpoint**: 알림을 보고 읽고 지울 수 있다 — US1과 함께 MVP

---

## Phase 5: User Story 3 - 좋아요·팔로우 알림은 묶이고 도배되지 않는다 (Priority: P2)

**Goal**: 같은 글 좋아요·새 팔로워가 안 읽은 알림 하나로 묶이고, 반복·취소로 알림이 늘지 않는다.

**Independent Test**: 10명 동시 좋아요 → 알림 1·"외 9명", 한 사람 취소·재클릭 5번 → 늘지 않음, 팔로우·언팔로우 반복 → 늘지 않음.

### Tests for User Story 3 ⚠️

- [X] T033 [P] [US3] `T/notification/integration/LikeGroupingIT.java` — SC-002, US3 #1~#4·#6: 10명 동시(`ExecutorService` + `CountDownLatch`로 이벤트 10개 동시 처리) → 안 읽은 `LIKE` 1개·`actor_count 10`·`notification_actor` 10행, 한 사람 취소·재클릭 5번 → 알림 1·인원 그대로, B·C 묶음에서 B 취소 → 인원 1·`last_actor_id = C`·`updated_at` 그대로, 남은 사람 0 → 행 삭제, 읽은 묶음에서 취소 → 그대로, 읽은 뒤 새 사람 → 새 묶음, 사람이 더해지면 목록 맨 위 (009 머지 후) (구현 메모: 10명 동시는 NotificationWriter.addLike를 ExecutorService+CountDownLatch로 동시에 직접 부름)
- [X] T034 [P] [US3] `T/notification/integration/FollowGroupingIT.java` — SC-003, US3 #5: 7일 안 언팔로우 → 팔로우 반복 3번 → 새 팔로워 알림 1·인원 1, `MutableClock`으로 8일 뒤 다시 팔로우 → 인원 +1(또는 새 묶음), 언팔로우만으로는 알림 0, 안 읽은 묶음에서 언팔로우하면 빠짐 (010 머지 후) (구현 메모: MutableClock이 없어 '8일 뒤'는 notification_actor.created_at을 8일 전으로 옮겨 확인. 팔로우·언팔로우는 010 API)
- [ ] T035 [P] [US3] 화면 테스트 `F/features/notification/__tests__/notificationText.test.ts` — 묶음 문장 "**김민서**님 외 3명이 「제목」을 좋아해요"·"…외 N명이 회원님을 팔로우해요", `othersCount 0`이면 하나짜리 문장

### Implementation for User Story 3

- [X] T036 [US3] `NotificationRepository.removeFromUnreadGroup(receiverId, groupKey, actorId)`(contracts §5 네 문장, 잠금 순서 알림 → 사람)와 `NotificationWriter`의 FOLLOW 중복 기간(`follow-dedup-window` 7일, §4 ①), `removeLike`·`removeFollow` (T033·T034 실패 확인) (구현 메모: removeLike·removeFollow는 처리 시점에 다시 좋아요·팔로우 상태면 빼지 않음(취소 뒤 재클릭 순서 뒤바뀜 대비). 다시 계산 UPDATE는 recount(ids)로 묶어 탈퇴 정리와 함께 씀)
- [ ] T037 [US3] `LikeNotificationListener`에 `PostUnliked`, `FollowNotificationListener`에 `MemberUnfollowed` 처리를 더하고, `notificationText.ts`에 묶음 문장 (T033~T035 통과)

**Checkpoint**: 묶음·중복 방지·취소 반영 완료

---

## Phase 6: User Story 4 - 볼 수 없게 된 글과 탈퇴한 사람이 알림에서 안전하게 보인다 (Priority: P2)

**Goal**: 알림은 보여 줄 때 다시 판단한다. 볼 수 없는 글은 제목·미리보기 없이, 탈퇴 유예·익명 처리된 사람은 "탈퇴한 사용자"로. 댓글 삭제·숨김이면 그 댓글 알림을 지운다.

**Independent Test**: A의 댓글 알림 글을 비공개로 바꾸고, 댓글 작성자가 닉네임을 바꾼 뒤 탈퇴 신청했을 때 표시를 확인한다.

### Tests for User Story 4 ⚠️

- [X] T038 [P] [US4] `T/notification/integration/NotificationDisplayIT.java` — US4 #1~#4, SC-004: 비공개·휴지통·숨김·작성자 유예 각각 `post: {unavailable: true}`·`comment null`·`url null`, 다시 공개 → 지금 제목, 행동자 닉네임 변경 반영, 행동자 유예·익명 처리 `actor: {withdrawn: true}`, 댓글 수정 → 새 미리보기(공백 한 칸, 앞 50자 + "…", 이모지·한글 코드 포인트 경계), 마크다운 기호 그대로, 받는 사람이 작성자면 자기 비공개 글 제목이 보임 (구현 메모: 행동자 익명 처리는 member.deleted_at을 직접 넣어 확인. 이모지 경계·공백 한 칸·마크다운 기호 그대로 확인)
- [X] T039 [P] [US4] `CommentNotificationIT`에 US4 #5 더하기: 댓글 삭제(답글이 있어 자리로 남는 경우 포함) → 그 댓글 `COMMENT`·`REPLY` 0, 다른 댓글 알림은 그대로, 글 완전 삭제 → 그 글 알림 0(CASCADE, 006 머지 후 `PostPurgeService`로) (구현 메모: 006이 main에 있어 글 완전 삭제는 PostPurgeService.purge를 트랜잭션 안에서 직접 불러 CASCADE 확인)
- [ ] T040 [P] [US4] 화면 테스트 `notificationText.test.ts`에 "볼 수 없는 글이에요"(미리보기 없음)·"탈퇴한 사용자"(굵게 하지 않음)를 더한다

### Implementation for User Story 4

- [X] T041 [US4] `NotificationItemAssembler`에 표시 규칙(research R10 — 행동자 탈퇴, 미리보기 자르기 `preview-length`, 읽을 수 없으면 `post.unavailable`·`url null`)을 마무리한다 (T038 실패 확인) (구현 메모: T029에서 함께 구현해 T038이 처음부터 통과(실패 확인 단계 없음))
- [ ] T042 [US4] `CommentNotificationListener`에 `CommentDeleted` → `NotificationWriter.removeCommentNotifications(commentId)`(§7-1)를 더하고, `notificationText.ts`·`NotificationItem.tsx`에 볼 수 없는 글·탈퇴한 사용자 표시 (T038~T040 통과)

**Checkpoint**: 볼 수 없게 된 글의 제목·내용이 알림에 남지 않는다

---

## Phase 7: User Story 5 - 신고 처리 결과와 숨김을 운영 알림으로 받는다 (Priority: P2)

**Goal**: 신고자는 처리 결과를, 숨겨진 글·댓글 작성자는 숨김과 사유를 받는다. 운영 알림은 끌 수 없고 신고자·관리자 정보가 없다.

**Independent Test**: 신고를 "조치함"·"문제없음"으로 처리하고 글·댓글을 숨긴 뒤 알림 문구·이동·포함 정보를 검사한다(014가 없으면 이벤트를 직접 발행).

### Tests for User Story 5 ⚠️

- [X] T043 [P] [US5] `T/notification/integration/ModerationNotificationIT.java` — US5 #1~#6, SC-010 (`EventPublisherHelper`로 `ReportResolved`·`ContentHidden` 직접 발행): 신고 결과 두 가지(`report.result`, `actor`·`post`·`url` null), 글 숨김 → 작성자 `CONTENT_HIDDEN`·제목·`hidden {POST, stillHidden true, reason SPAM}`, 댓글 숨김 → `post null`·댓글 위치 `url`·그 댓글의 `COMMENT`·`REPLY` 삭제, 해제 뒤 `stillHidden false`·`reason null`, 응답 JSON 어디에도 신고자·관리자 번호 없음, 5종 모두 끈 회원도 받음, 받는 사람 유예면 0, 신고 행 삭제 → `report_id` NULL·알림 남음 (구현 메모: 014가 없어 이벤트 직접 발행, 숨김 상태·신고 행은 SQL로 직접 넣음)
- [ ] T044 [P] [US5] 화면 테스트 `notificationText.test.ts`에 신고 결과 두 문장, 글 숨김 "회원님의 글「제목」이(가) 운영 정책에 따라 숨겨졌어요 (사유: 스팸·광고)", 해제 "…숨겨졌었어요 (지금은 다시 보여요)", 댓글 숨김(제목 없음)

### Implementation for User Story 5

- [X] T045 [US5] 이벤트 record `B/shared/event/ReportResolved.java`(`reportId, reporterId, targetType, targetId, result, resolvedAt`)·`ContentHidden.java`(`targetType, targetId, ownerId, postId, hiddenAt`)와 enum(`ReportTargetType` POST·COMMENT, `ReportResult` ACTION_TAKEN·NO_VIOLATION) — 014가 먼저 만들었으면 그대로 쓴다(먼저 하는 쪽이 만듦, 014 spec Implementation Notes 필드). `DomainEventShapeTest` 통과 확인 (구현 메모: 014보다 먼저 만듦: shared.event에 ReportResolved·ContentHidden record와 enum ReportTargetType·ReportResult(014 data-model §6 필드 그대로). ContentUnhidden·MemberSuspended는 알림이 구독하지 않아 만들지 않음(014 몫))
- [X] T046 [US5] `NotificationWriter.addReportResolved`·`addContentHidden`(받는 사람 유예만 확인, 댓글이면 §7-1 먼저)과 `B/notification/application/listener/ModerationNotificationListener.java`, `NotificationItemAssembler`의 `report`·`hidden`(대상의 지금 `hidden_at`·`hidden_reason`) (T043 실패 확인, T045 다음) (구현 메모: 숨김 표시(hidden.stillHidden·reason)는 T029 NotificationItemAssembler에 이미 있음)
- [ ] T047 [US5] `F/features/moderation/reasonLabels.ts`(014 Clarifications 6개 코드 → 이름, 014와 공유 — 먼저 하는 쪽이 만듦)와 `notificationText.ts` 운영 알림 문장 (T043·T044 통과)

**Checkpoint**: 운영 알림 2종 동작(014 연결은 014 머지 후 quickstart §4로 확인)

---

## Phase 8: User Story 6 - 알림 종류를 골라 끈다 (Priority: P3)

**Goal**: 설정 "알림" 칸에서 5종을 켜고 끈다. 기본은 모두 켜짐, 운영 알림은 끌 수 없다.

**Independent Test**: 좋아요 알림을 끈 회원의 글에 좋아요 → 새 알림 없음, 기존 좋아요 알림은 남음.

### Tests for User Story 6 ⚠️

- [X] T048 [P] [US6] `T/notification/integration/NotificationSettingsIT.java` — US6 #1·#2: 새 회원 5종 true, `LIKE: false` 저장 뒤 새 좋아요 알림 0·기존 알림 그대로·다른 종류는 생김, 다시 켜면 생김, 키 빠짐·문자열 값 400 `VALIDATION_FAILED`, 모르는 키 400, 인증 전 회원 가능, 남은 세션의 정지 `PUT` 403
- [ ] T049 [P] [US6] 화면 테스트 `F/features/notification/__tests__/NotificationSettingsSection.test.tsx` — 스위치 5개(`role="switch"`, 이름), 바꾸면 `PUT`(빠르게 두 번 바꾸면 마지막 상태), 실패하면 되돌림 + "잠시 후 다시 시도해 주세요", 안내 "운영 알림(신고 결과·숨김)은 끌 수 없어요"

### Implementation for User Story 6

- [X] T050 [US6] `B/notification/application/NotificationSettingsService.java`(`get`·`put`, `ACCOUNT_WRITE`)와 `B/notification/web/NotificationSettingsController.java`(`GET`·`PUT /api/me/notification-settings`, 본문 record 5개 `@NotNull Boolean`, 모르는 키 거부 — `@JsonIgnoreProperties(ignoreUnknown = false)`) (T048 통과) (구현 메모: @JsonIgnoreProperties 대신 본문을 Map으로 받아 Service가 5개 키·boolean·모르는 키를 칸별 오류로 모아 400(빠짐 REQUIRED, 타입 INVALID_VALUE, 모르는 키 UNKNOWN_FIELD). 응답·GET도 no-store)
- [ ] T051 [US6] `F/api/notifications.ts`에 `getNotificationSettings`·`putNotificationSettings`, `F/features/notification/NotificationSettingsSection.tsx`, 001 설정 화면에 "알림" 칸 붙이기(001 T122가 없으면 `/settings` 자리에 칸만 — 001 T122 머지 후 옮김, 001 담당에게 알림) (T049 통과)

**Checkpoint**: 알림 종류 끄기 완료

---

## Phase 9: User Story 7 - 오래된 알림과 탈퇴 회원의 알림을 정리한다 (Priority: P3)

**Goal**: 매일 90일·사람당 1,000개 정리, 탈퇴 30일 정리 때 받은 것·보낸 것 정리.

**Independent Test**: 91일 된 알림과 1,001개째 알림을 만든 뒤 정리를 실행하고, 탈퇴 정리 뒤 그 회원이 받은·행동한 알림이 남는지 확인한다.

### Tests for User Story 7 ⚠️

- [X] T052 [P] [US7] `T/notification/integration/NotificationCleanupIT.java` — US7 #1·#2, SC-008: 91일 된 알림 삭제·89일 남음, 2,500개를 묶음 1,000씩 지움, 오늘 1,001개를 받은 회원 → 최신 1,000개(경계 `(updated_at, id)` 같은 시각 포함), 오늘 받지 않은 회원은 1,200개여도 이번 정리 대상 아님, ShedLock 이름 `notificationCleanup`, 지운 수 INFO 로그 (구현 메모: Clock 대체가 없어 updated_at을 SQL로 과거로 옮겨 검증)
- [X] T053 [P] [US7] `T/notification/integration/NotificationWithdrawalPurgeIT.java` — US7 #3·#4: 유예 중 알림 그대로·복구 후 그대로, order 70 실행 뒤 받은 알림 0, B·C·D 좋아요 묶음에서 B 정리 → 인원 2·`last_actor_id` 다시 계산(읽은 묶음 포함), B 혼자였던 묶음 삭제, B가 남긴 댓글·새 글 알림(하나짜리) 0, B의 끄기 설정 0, 015 단계 트랜잭션(`MANDATORY`) 밖에서 부르면 예외 (구현 메모: Bean 목록 검사(T057)도 이 파일에 둠)

### Implementation for User Story 7

- [X] T054 [US7] `B/notification/application/NotificationCleanupJob.java`(`@Scheduled(cron = "${blog.notification.cleanup.cron}", zone = "${blog.time-zone}")` + `@SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT1H")`, ① 90일 묶음 반복 ② 1,000개 — contracts §10, 단계마다 `TransactionTemplate`)과 `NotificationRepository.deleteOlderThan`·`trimPerMember` (T052 통과)
- [X] T055 [US7] (015가 아직 없으면) `B/shared/application/withdraw/WithdrawalPurgeStep.java`(`int order(); void purge(long memberId);`, 015 contracts/purge-steps.md §1 — 015 tasks T009와 같은 파일, 먼저 하는 쪽이 만듦) (구현 메모: 015가 main에 있어 기존 파일을 그대로 씀, 새로 만들지 않음)
- [X] T056 [US7] `B/notification/application/NotificationWithdrawalPurgeStep.java`(`order() = 70`, `@Transactional(propagation = MANDATORY)`, contracts §11 ①~④ — ②의 네 문장은 따로 실행)와 `NotificationRepository`의 탈퇴 정리 메서드 (T053 통과, T055 다음) (구현 메모: 015 임시 단계 InterimNotificationWithdrawalPurgeStep 삭제)
- [X] T057 [US7] 015 `required-orders` 기본값에 70이 있는지 확인하고(015 research R16), 없으면 015 담당에게 알린다. `NotificationWithdrawalPurgeStep`이 Bean으로 등록되는지 `WithdrawalPurgeStep` 목록 테스트(015 `WithdrawPurgeJob` 테스트가 있으면 거기에) 1개 (구현 메모: required-orders 기본값에 70이 이미 있음, 등록 테스트는 NotificationWithdrawalPurgeIT)

**Checkpoint**: 모든 User Story 완료

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: 종단 확인, 다른 기능 인계, 문서

- [ ] T058 [P] Playwright `E/notification.spec.ts` — quickstart §3 1~11(두 브라우저 문맥: A·B), 375px 폭 가로 스크롤 없음, 종·[×] 44px 이상, 펼침 목록 키보드(Tab·Esc)
- [ ] T059 [P] 이벤트 필드 맞춤 확인: 007 `CommentCreated`·`CommentDeleted`, 009 `PostLiked`·`PostUnliked`, 010 `MemberFollowed`·`MemberUnfollowed`, 014 `ReportResolved`·`ContentHidden`이 data-model §3 표와 같은지 확인하고 다르면 이 기능 문서를 고친다(필드는 각 기능 소유)
- [ ] T060 [P] 001 `B/shared/security/ActionKind.java` `ACCOUNT_WRITE` 주석에 "알림 읽음·삭제·설정"을 더한다(001 담당에게 알림)
- [ ] T061 014·015 인계 확인: 014가 `reasonLabels.ts`·이벤트 record를 이 기능 것으로 쓰는지(T045·T047), 015 정리 단계 표 order 70 행이 T056과 같은지 기록한다
- [ ] T062 quickstart.md §2 명령 전체 실행, §3 수동 확인, §4 다른 기능 확인(있는 기능만) 결과를 기록한다

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 바로 시작
- **Foundational (Phase 2)**: Setup 다음 — 모든 User Story를 막는다
- **US1 (Phase 3)**: Foundational 다음. 종류별로 이벤트를 내는 기능(007·009·010) 머지 후
- **US2 (Phase 4)**: Foundational 다음. 화면 확인은 US1 알림이 있어야 자연스럽지만 `NotificationFixtures`로 따로 테스트 가능
- **US3 (Phase 5)**: US1의 묶음 저장(T020·T021) 다음
- **US4 (Phase 6)**: US2의 목록 조회(T028·T029) 다음
- **US5 (Phase 7)**: Foundational·US2(T029) 다음. 014 없이 이벤트 직접 발행으로 진행
- **US6 (Phase 8)**: Foundational(T010) 다음. 끈 종류 판정은 이미 T011에 있음
- **US7 (Phase 9)**: Foundational 다음. 탈퇴 정리 확인은 015가 있으면 함께
- **Polish (Phase 10)**: 원하는 User Story가 끝난 뒤

### User Story Dependencies

- **US1 (P1)**: 다른 스토리와 독립(행 생성까지)
- **US2 (P1)**: 독립(픽스처 알림으로 테스트). US1과 합쳐 MVP
- **US3 (P2)**: US1 묶음 저장 위에 쌓는다
- **US4 (P2)**: US2 목록 표시 위에 쌓는다
- **US5 (P2)**: US2 표시 위에 쌓는다. 이벤트는 014(없으면 이 기능이 record를 만듦)
- **US6 (P3)**: 독립
- **US7 (P3)**: 독립

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다
- 같은 파일을 고치는 작업은 순서대로 한다: `NotificationRepository`(T012 → T020 → T036 → T054 → T056), `NotificationWriter`(T013 → T021 → T036 → T042 → T046), `NotificationItemAssembler`(T029 → T041 → T046), `notificationText.ts`(T032 → T037 → T042 → T047), `CommentNotificationIT`(T015 → T039), `notificationText.test.ts`(T035 → T040 → T044), `F/api/notifications.ts`(T031 → T051), 리스너(`CommentNotificationListener` T022 → T042, `LikeNotificationListener`·`FollowNotificationListener` T022 → T037), 다른 기능 파일(`AsyncConfig`·`CoreProperties` T006, `PostReadService` T009, `SessionBar`·`App.tsx` T032, `ActionKind` T060)

### Parallel Opportunities

- Phase 2의 T005·T007·T008·T009·T010·T014는 서로 다른 파일
- 각 스토리의 테스트 작업([P])은 동시에 쓸 수 있다
- US1 구현 뒤 US2·US6·US7은 서로 다른 팀원이 동시에 할 수 있다

---

## Parallel Example: User Story 1

```bash
# US1 테스트를 함께 쓴다
Task: "CommentNotificationIT in T/notification/integration/CommentNotificationIT.java"
Task: "NotificationExclusionIT in T/notification/integration/NotificationExclusionIT.java"
Task: "NewPostNotificationIT in T/notification/integration/NewPostNotificationIT.java"
Task: "NotificationFailureIsolationIT in T/notification/integration/NotificationFailureIsolationIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. Phase 1·2 완료 (실행기 설정·이벤트 형식 검사 포함)
2. US1: 댓글 알림부터(007 머지 후), 좋아요·팔로우·새 글은 각 기능 머지 후 이어 붙임
3. US2: API와 종 아이콘·펼침·전체 페이지
4. **STOP and VALIDATE**: quickstart §3 1~3, 8~9

### Incremental Delivery

1. US1 + US2 → MVP
2. US3(묶음·취소) → US4(보여 줄 때 다시 판단) → US5(운영 알림, 014와 함께)
3. US6(끄기) → US7(정리·탈퇴 정리, 015와 함께)

### Parallel Team Strategy

1. 함께 Phase 1·2
2. 그 뒤: 개발자 A US1 → US3, 개발자 B US2 → US4 → US5, 개발자 C US6 → US7

---

## Notes

- [P] = 다른 파일, 의존 없음
- 리스너는 결과를 기다리지 않으므로 통합 테스트는 `NotificationAwait`로 기다린다. "생기지 않음"은 다른 이벤트 하나를 뒤에 보내 그것이 처리된 뒤 확인한다(고정 sleep 금지)
- 이벤트 record·공개 조회 메서드는 소유 기능 파일이다. 먼저 하는 쪽이 만들고 다른 쪽은 그대로 쓴다
- 각 작업 또는 논리 묶음마다 커밋한다
