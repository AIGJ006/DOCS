---

description: "Task list for 015-withdraw (회원 탈퇴·복구)"
---

# Tasks: 회원 탈퇴·복구

**Input**: Design documents from `/specs/015-withdraw/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ (openapi.yaml, purge-steps.md), quickstart.md

**Tests**: 포함한다. 헌법 원칙 VIII(권한·데이터 규칙은 Testcontainers 통합 테스트)과 plan.md Constitution Check VIII에 따라, 각 User Story Phase에서 테스트 작업을 구현 작업보다 먼저 두고 실패를 확인한 뒤 구현한다. 인수 시나리오(US*-N)와 quickstart.md §2 표의 테스트 이름을 각 작업에 적었다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Cross-feature Dependencies

이 기능은 account 모듈의 회원 상태 전이(신청·복구·익명 처리)와 30일 정리 작업을 소유한다. 유예 중 차단(001 게이트 필터)과 노출 제외(004 공용 조건)는 이미 있고, 정리 단계의 실제 데이터 처리는 각 기능의 정리 Service가 한다.

**선행 (이 기능 시작 전에 끝나 있어야 함)**

- 선행: specs/001 Phase 1·2 — `SessionTerminator`(T038), `AccountStatusGuard`(T037, `ACCOUNT_WRITE`), `WithdrawnAccountGateFilter`(T042a), `MemberQueryService.findAccessInfo`, `AuthTokenStore`(T058), `AccountMailService`·`MailTemplates`, `support/IntegrationTestBase`·`TestLogin`·`MemberFixtures`·`RedisOutage`·`CapturingMailSender`
- 선행: specs/001 US4 T097 `PasswordChangeService`(실패 기록 `auth:pw-change-fail:{memberId}`), US5 T106 `MemberSuspension`·`SuspensionService`, T108 로그인 응답 `accountStatus: "WITHDRAWN"`, US6 T122 `SettingsPage`(회원 탈퇴 자리)
- 선행: specs/004 — 공용 노출 조건(`withdrawn_at IS NULL`), 권한 하네스(`support/permission/`)
- 선행: specs/006 T063 `PostPurgeService.purgeAllByAuthor` — 정리 order 10
- 선행: V2 `shedlock`

**정리 단계별 선행 (그 단계 작업만 막음)**

- specs/007 T048 `CommentPurgeService.purgeByAuthor` → T051(order 20), 007 머지 전에는 `AuthoredCommentStatsAdapter`(T022)도 못 만든다
- specs/009 T043 `LikePurgeService.purgeByMember` → T052(order 30)
- specs/003 T082 `ImagePurgeService.detachAllByUploader` → T053(order 40)
- specs/010·011·014 → 정리 단계 65·70·80은 그 기능 tasks가 만든다. 없으면 정리 작업은 "단계 누락"으로 돌지 않는다(research R9) — 신청·복구(US1·US2)는 영향 없음

**006 머지 후**

- T050 `PostWithdrawalPurgeStep`(post 모듈, 006 `PostPurgeService` 호출)
- `F/App.tsx`에 `/settings/withdraw`·`/withdrawn` 경로와 `RestoreGate`를 더하는 작업(T031·T039). 006이 같은 파일에 `/manage/posts` 경로를 더한다(ANALYSIS-tier-bc)
- 006이 이미 만든 `ReportPostPurgeStep`(moderation)·`ImagePostPurgeStep`(media)과는 다른 확장점이라 파일이 겹치지 않는다

**후속 (다른 스펙이 이 기능을 사용)**

- 010-follow-feed: `FollowWithdrawalPurgeStep`(order 65), 유예 회원 팔로워·팔로잉 제외
- 011-notification: `NotificationWithdrawalPurgeStep`(order 70), 유예 회원 관련 알림 생성 제외, `MemberWithdrawn`·`MemberRestored`는 구독하지 않음(20 §5)
- 012-trending-search: 구독하지 않는다 — 트렌딩·검색·sitemap이 요청 때 공용 조건(`withdrawn_at IS NULL`)으로 유예 작성자를 거른다(012 research R14)
- 014-report-hide: `ReportWithdrawalPurgeStep`(order 80), 유예 회원 정지 불가(FR-041)
- 010·011·014가 015보다 먼저 구현되면 T009(`WithdrawalPurgeStep` 인터페이스)를 그 기능이 먼저 만든다 — 한 파일

**팀 결정 대기 (기본안으로 진행)**

- 신청 본문의 `confirmed` 칸, 새 이유 코드 문구 2개, 잠금 코드 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` 재사용(research R3) — 확인 작업 T002
- 정리 작업 필수 단계 기본값(010·011·014 전에는 정리가 돌지 않음, research R9)과 영구 정지 1년 정리의 처리방침 문구 — 확인 작업 T003

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `B/` = `backend/src/main/java/com/team/blog/`, `T/` = `backend/src/test/java/com/team/blog/`, `R/` = `backend/src/main/resources/`, `TR/` = `backend/src/test/resources/`, `F/` = `frontend/src/`, `E/` = `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 선행 확인, 팀 확인 질문, 설정값

- [X] T001 선행 확인: V1 `member`의 `ck_member_withdrawn`·`ck_member_deleted`·`ck_member_nickname_null`·`uq_member_nickname`·`ix_member_withdraw_purge`, `auth_identity`(`uq_auth_identity_member`), `friendship`, `member_suspension`, V2 `shedlock`과 `B/account/application/SessionTerminator.java`, `B/account/infra/security/WithdrawnAccountGateFilter.java`(허용 목록에 `POST /api/me/restore`), `B/account/application/AccountStatusGuardService.java`, `PasswordChangeService`(001 T097)·`SuspensionService`(001 T106)·`SettingsPage`(001 T122)·006 `PostPurgeService.purgeAllByAuthor`·003/007/009 정리 Service가 있는지 기록하고, 004 권한 하네스가 행마다 회원 픽스처를 새로 만드는지 확인한다(research R15) (구현 메모: 확인 결과: V1 제약·인덱스 6개·auth_identity·friendship·member_suspension·V2 shedlock 있음. SessionTerminator·WithdrawnAccountGateFilter(허용 목록에 POST /api/me/restore)·AccountStatusGuardService·PasswordChangeService·SuspensionService·SettingsPage·006 PostPurgeService.purgeAllByAuthor·003 ImagePurgeService.detachAllByUploader·007 CommentPurgeService.purgeByAuthor 있음. 009 LikePurgeService·010·011·014는 아직 없음 → 정리 단계 30·65·70·80은 소유 기능이 생길 때까지 015 임시 구현(T052·T063 메모). 004 하네스는 테스트마다 DB를 비우고 행마다 작성자·행위자를 새로 만든다(R15 확인))
- [X] T002 팀 확인 질문을 ANALYSIS-tier-bc "팀 결정" 항목으로 올린다(research R3): ① 신청 본문 `confirmed` 칸 추가 ② `WITHDRAW_CONFIRM_REQUIRED` "안내 내용을 확인하고 체크해 주세요"·`CONFIRM_TEXT_MISMATCH` "'탈퇴'를 정확히 입력해 주세요" 문구 ③ 탈퇴 잠금에도 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` 코드를 쓰는 것. 답이 오기 전에는 기본안으로 진행한다 (구현 메모: 질문 없이 기본안으로 확정: ① confirmed 칸 추가 ② 문구 두 개 data-model §4 그대로 ③ 잠금 코드 PASSWORD_CHANGE_TEMPORARILY_LOCKED 재사용. ANALYSIS-tier-bc는 공통 문서라 고치지 않고 팀 결정 4번 항목으로 보고에 적음)
- [X] T003 팀 확인 질문을 올린다(research R9·R11): ① `blog.withdraw.purge.required-orders` 운영 기본값(010·011·014 전에는 정리가 돌지 않음) ② 영구 정지 1년 자동 정리를 개인정보 처리방침에 적을지(001 `PrivacyPage` 문단·버전 규칙). 결과를 이 파일 Notes에 적는다 (구현 메모: 질문 없이 가정: ① required-orders 기본값은 10단계 전부 그대로 두고, 아직 없는 009·010·011·014 단계는 015가 임시 구현(Javadoc에 소유 기능)으로 채워 정리 작업이 돌게 함 — 소유 기능이 진짜 단계를 만들면 임시 구현을 지움 ② 영구 정지 1년 자동 정리 문단은 처리방침 첫 판에 함께 넣는 쪽(ANALYSIS-tier-bc 팀 결정 7 추천안)으로 가정하고 PrivacyPage는 이 기능에서 고치지 않음. Notes에 적음)
- [X] T004 [P] 설정값: `B/account/application/WithdrawalProperties.java`(`@ConfigurationProperties("blog.withdraw")` + `@Validated`: `gracePeriod`, `suspendedPurgeAfter`, `confirmText`(공백 불가), `purge.cron`·`batchSize`(1 이상)·`requiredOrders`·`redisKeyTemplates`), `R/application.yml`에 research R16 기본값, 테스트 `T/account/unit/WithdrawalPropertiesBindingTest.java`(기본값·음수 기간 거부) (구현 메모: 기간 두 개는 0 이하이면 생성자에서 거부(바인딩 실패). 목록 기본값은 @DefaultValue 배열)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 기한 계산, 확장점, 이벤트, 이유 코드, 상태 전이 저장소, 비밀번호 확인 공용화

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational ⚠️

- [X] T005 [P] 단위 테스트 `T/account/unit/RestoreDeadlineTest.java`: `WithdrawalPolicy.deadline(withdrawnAt) = withdrawnAt + 30일`, `isRestorable(withdrawnAt, now)`는 `now ≤ deadline`, `isPurgeTarget(withdrawnAt, now)`는 `withdrawnAt < now - 30일` — t-1ms·t·t+1ms에서 두 값이 겹치지도 비지도 않음(research R5, FR-021a)
- [X] T006 [P] 단위 테스트 `T/account/unit/WithdrawalPurgeStepOrderTest.java`: `WithdrawalStepRegistry`가 단계를 `order()` 오름차순으로 돌려주고, 같은 order가 둘이면 생성 때 `IllegalStateException`, `missingRequired([10,20,90])`가 빠진 값만 돌려줌(research R9, FR-029)
- [X] T007 [P] 통합 테스트 `T/account/integration/WithdrawalMemberRepositoryIT.java`: `markWithdrawn`(ACTIVE만 1행, 두 값 함께 — `ck_member_withdrawn`), `markRestored`(WITHDRAWN·`deleted_at` NULL만), `findPurgeTargets`(유예 30일 경과만, `withdrawn_at` 순, `batchSize`), `findSuspendedPurgeTargets`(열린 영구 정지 1년 경과·`role = USER`만, 기간 정지·해제된 정지·1년 미만·관리자 제외), `lockForUpdate`, `markSuspendedAsWithdrawn`, `anonymize`(1행이 아니면 예외, `ck_member_nickname_null` 통과) (data-model §2) (구현 메모: @Repository 예외 변환 때문에 1행이 아닐 때 예외는 InvalidDataAccessApiUsageException(원인 IllegalStateException)으로 나온다 — 어느 쪽이든 롤백)
- [X] T008 [P] 통합 테스트 `T/account/integration/CurrentPasswordVerifierIT.java`: 틀린 비밀번호 5번 뒤 6번째는 맞아도 429 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` + `Retry-After`, 비밀번호 변경 3번 + 탈퇴 2번 실패로도 잠김(Clarifications Q3), 맞으면 기록 삭제, 빈 비밀번호는 세지 않음, Redis 정지 중에는 비교만 하고 통과(001 규칙) (구현 메모: 공용 Bean을 직접 부르는 시험 + 비밀번호 변경 HTTP로 429·Retry-After 확인. 탈퇴 API로 합산하는 경로는 WithdrawalApiIT US1_3. 빈 비밀번호는 이제 비밀번호 변경에서도 세지 않는다(research R3 규칙, 001 PasswordChangeIntegrationTest 통과))

### Implementation for Foundational

- [X] T009 [P] 확장점 `B/shared/application/withdraw/WithdrawalPurgeStep.java`(contracts/purge-steps.md §1 Javadoc 그대로)와 `package-info.java`. 010·011·014가 먼저 구현하면 그 기능이 이 작업을 먼저 한다
- [X] T010 [P] 이벤트 `B/shared/event/MemberWithdrawn.java`(`long memberId, Instant withdrawnAt`), `B/shared/event/MemberRestored.java`(`long memberId, Instant restoredAt`) — 001 `DomainEvent` 규칙(불변 record, 글자 필드 없음)
- [X] T011 [P] `B/account/application/AccountReasonCode.java`에 `ADMIN_CANNOT_WITHDRAW`(409), `WITHDRAW_CONFIRM_REQUIRED`(400), `CONFIRM_TEXT_MISMATCH`(400), `RESTORE_PERIOD_EXPIRED`(409), `EMAIL_WITHDRAWAL_PENDING`(400 칸 오류)를 data-model §4 문구로 더한다(끝 마침표 없음)
- [X] T012 [P] `B/account/application/WithdrawalPolicy.java`(`deadline`·`isRestorable`·`isPurgeTarget`·`isSuspendedPurgeTarget`, `WithdrawalProperties`·`Clock` 사용)를 구현한다 (T005 통과)
- [X] T013 `B/account/infra/WithdrawalMemberRepository.java`(`JdbcClient`, data-model §2 SQL과 contracts/purge-steps.md §3 대상 SQL)를 구현한다 (T007 통과) (구현 메모: 대상 조회는 기준 시각(cutoff = now - 기간)을 받는다. 영구 정지 재확인용 hasOpenPermanentSuspensionBefore, Redis 정리용 findLoginEmail을 더했다)
- [X] T014 `B/account/application/CurrentPasswordVerifier.java`(`verify(memberId, rawPassword)`: 잠금 확인 → BCrypt 비교 → 실패 기록 증가·성공 시 삭제, `blog.auth.password-change.*`)를 만들고 001 `PasswordChangeService`가 이 클래스를 부르게 바꾼다(001 T097 코드에서 꺼내 옮김 — 001 비밀번호 변경 테스트를 함께 돌리고 001 담당에게 알림) (T008 통과) (구현 메모: 서명은 verify(AuthIdentity, rawPassword, field) — 두 호출자가 이미 로그인 수단을 읽고 칸 이름이 다르다(currentPassword/password). 001 PasswordChangeService가 이것을 부르게 바꿨고 001 비밀번호 변경 시험 6개 통과)
- [X] T015 `B/account/application/purge/WithdrawalStepRegistry.java`(`List<WithdrawalPurgeStep>` 주입·정렬·중복 order 시작 실패·`missingRequired`, 시작 때 INFO "탈퇴 정리 단계 orders=[…]")를 구현한다 (T006 통과)

**Checkpoint**: 기한·확장점·저장소 준비 완료 — user story 시작 가능

---

## Phase 3: User Story 1 - 잃게 되는 것을 확인하고 탈퇴 신청하기 (Priority: P1) 🎯 MVP

**Goal**: 안내 숫자를 보고 체크·본인 확인을 거쳐 신청하면 모든 기기에서 로그아웃되고 블로그·글이 가려진다

**Independent Test**: 글 24개·남의 글 댓글 18개·받은 좋아요 126개인 회원으로 안내 숫자, 체크·본인 확인 없는 거부, 신청 후 다른 기기 세션 401과 블로그 404를 확인한다

### Tests for User Story 1 ⚠️

- [X] T016 [P] [US1] 통합 테스트 `T/account/integration/WithdrawalApiIT.java`: `US1_1_안내_숫자`(`GET /api/me/withdrawal` → 글 24(휴지통·임시 포함)·댓글 18(내 글 댓글·지운 댓글 제외)·좋아요 126·기한 = now + 30일·`verification`, `Cache-Control: no-store`) · `US1_2_체크_없으면_400`(`WITHDRAW_CONFIRM_REQUIRED`, 상태 그대로) · `US1_3_비밀번호_5번_틀리면_15분`(429 + `Retry-After`) · `US1_4_소셜_탈퇴_문구_불일치`(400 `CONFIRM_TEXT_MISMATCH`, "탈퇴 "는 통과, 실패 횟수 안 셈) · `US1_5_관리자_409` · `US1_6_신청_즉시_모든_세션_끊김`(두 기기 세션 모두 401, 이 요청 세션도 다음 요청 401, `status = WITHDRAWN`·`withdrawn_at` 기록, 응답 `restoreDeadline`, `MemberWithdrawn` 1번 — `@RecordApplicationEvents`) · 인증 전 회원 200(42 §9) · 남은 세션의 정지 회원 403 `ACCOUNT_SUSPENDED` · 유예 회원 `GET /api/me/withdrawal`·재신청 403 `ACCOUNT_WITHDRAWN` · 동시 두 번 신청 → 하나만 200 · Redis 정지 중 → 503 `TEMPORARILY_UNAVAILABLE`이고 `status = ACTIVE` 그대로 · 사유 칸을 보내도 저장 안 됨(FR-004) · 로그에 비밀번호·이메일 없음(`OutputCaptureExtension`) (구현 메모: Redis 정지 503은 HTTP로는 세션을 읽지 못해 401이 되므로 WithdrawalService를 직접 불러 확인(상태 ACTIVE 그대로·이벤트 0). 동시 신청은 다른 쪽이 401 또는 403. 이벤트는 @RecordApplicationEvents(새 컨텍스트 없음))
- [X] T017 [P] [US1] 통합 테스트 `T/account/integration/WithdrawalGraceIT.java`(US1 #7): 신청 직후 다른 사람에게 블로그·글 상세 404(본문 고정), 홈·블로그·태그(008 있을 때) 목록에 없음, 004 `VisibilityFilter` 경유 확인. 007·009·010이 있으면 댓글 "탈퇴한 사용자의 댓글이에요"·좋아요 행 그대로·팔로워 수 제외, 없으면 해당 메서드 `@Disabled("0NN 머지 후")` (구현 메모: 007 댓글 가림은 응답 state=WITHDRAWN_AUTHOR로 확인. 010 팔로워 수는 @Disabled("010 머지 후"))
- [X] T018 [P] [US1] 단위 테스트 `T/account/unit/WithdrawRequestValidationTest.java`: `confirmText` 앞뒤 공백 제거·NFC 정규화 후 비교, 방법에 맞지 않는 칸 무시, `confirmText` 20자 초과 400 (구현 메모: 규칙은 account.application.WithdrawConfirmText(앞뒤 공백 제거·NFC·20자 초과는 불일치 400 CONFIRM_TEXT_MISMATCH))
- [ ] T019 [P] [US1] 화면 테스트 `F/pages/__tests__/WithdrawPage.test.tsx`·`F/features/withdraw/__tests__/formatDeadline.test.ts`: 숫자·기한 표시("2026년 11월 7일 오후 3:20"), 체크·입력 전 [탈퇴하기] 비활성, 버튼에 처음 포커스 없음·Enter 제출 막힘(FR-007), `verification`별 칸, 오류 code별 문구 위치, 429면 버튼 비활성, 성공 시 `clearLocalAccountData(memberId)` 호출 후 `/withdrawn`으로 이동(`state.restoreDeadline`), 사유 입력 칸 없음

### Implementation for User Story 1

- [X] T020 [P] [US1] 포트 `B/account/application/port/AuthoredPostStats.java`(`PostStats statsOf(long memberId)` → `{postCount, receivedLikeCount}`)·`B/account/application/port/AuthoredCommentStats.java`(`long countOnOthersPosts(long memberId)`)를 만든다(data-model §7, 기본 구현 없음)
- [X] T021 [P] [US1] `B/post/application/AuthoredPostStatsAdapter.java`: `SELECT count(*), coalesce(sum(like_count), 0) FROM post WHERE author_id = :m`(휴지통·임시·숨김 포함) (T020 다음)
- [X] T022 [P] [US1] `B/interaction/application/AuthoredCommentStatsAdapter.java`: research R7 SQL(`ix_comment_author`) — **007 머지 후** (T020 다음)
- [X] T023 [US1] `B/account/application/WithdrawalPreviewService.java`(`requireActive(ACCOUNT_WRITE)` → 회원·로그인 수단 1번 조회 → 포트 2개 → `WithdrawalPreview`)를 구현한다 (T021·T022 다음) (구현 메모: WithdrawalPreview record는 account.application에 두고(MeSummary처럼 그대로 응답 본문) web/dto에는 WithdrawRequest·WithdrawResult·RestoreResult만 둠)
- [X] T024 [US1] `B/account/application/WithdrawalService.java`를 구현한다(research R3·R4): 트랜잭션 밖 판정(가드 → 관리자 409 → `confirmed` 400 → `CurrentPasswordVerifier` 또는 확인 문구) → `@Transactional` 안에서 `findByIdForUpdate` 재확인 → `markWithdrawn` → `SessionTerminator.terminateAll(me, empty)` → `MemberWithdrawn` 발행 → `restoreDeadline` 반환. 로그는 회원 번호만
- [X] T025 [US1] `B/account/web/WithdrawalController.java`와 `B/account/web/dto/WithdrawRequest.java`(`additionalProperties` 무시하지 않음 — 모르는 칸은 무시하되 저장 안 함)·`WithdrawalPreview.java`·`WithdrawResult.java`: `GET /api/me/withdrawal`, `POST /api/me/withdraw` — 서비스 성공 뒤 `request.getSession(false).invalidate()`·`SecurityContextHolder.clearContext()`(research R4 — `deleteById`만으로는 지금 세션이 다시 저장됨), 응답 `Cache-Control: no-store` (T016·T018 통과)
- [X] T026 [US1] `B/account/application/mail/WithdrawalMailListener.java`(`MemberWithdrawn` → `auth_identity.email`이 있으면 `R/mail/withdrawal-requested.txt`, `AFTER_COMMIT` + `@Async("mailExecutor")`, 실패 WARN)와 `MailTemplates` 등록, 테스트 `T/account/integration/WithdrawalMailIT.java`의 접수 메일 부분(이메일 없는 소셜 계정 0통, 메일 실패해도 신청 200 — `CapturingMailSender`) (구현 메모: MailTemplates는 이름으로 classpath:mail/*.txt를 읽어 따로 등록할 곳이 없다. 시험용으로 support/CapturingMailSender에 failNext() 추가)
- [ ] T027 [P] [US1] 화면 API `F/api/withdrawal.ts`(`getWithdrawalPreview`, `withdraw`, `restore` — 001 `client.ts`)와 `F/api/me.ts` `MeSummary` 타입에 `restoreDeadline`·`restoreExpired`
- [ ] T028 [P] [US1] `F/features/auth/logout.ts`에 `clearLocalAccountData(memberId)`(등록된 `clearMemberDrafts`만 실행, ①전송·③로그아웃 요청 없음)를 더하고 `logout()`도 이것을 쓰게 한다 — 001 `logout.test.ts` 함께 실행
- [ ] T029 [P] [US1] `F/features/withdraw/formatDeadline.ts`(KST 날짜·시각, 남은 날 올림 계산)·`F/features/withdraw/withdrawMessages.ts`(44 §2·§3 문구, 끝 마침표 없음) (T019 통과)
- [ ] T030 [US1] `F/features/withdraw/WithdrawForm.tsx`와 `F/pages/WithdrawPage.tsx`(`/settings/withdraw`, 비로그인은 `/login?redirect=/settings/withdraw`): research R12 화면, 숫자·주소는 텍스트 노드로만, 빨간 [탈퇴하기] `autoFocus` 없음·폼 `onSubmit` 막음 (T019 통과)
- [ ] T031 [US1] (**006 머지 후** — `App.tsx`) `F/pages/WithdrawnPage.tsx`(`/withdrawn`, `state` 없으면 기한 줄 생략) + `F/App.tsx`에 `/settings/withdraw`·`/withdrawn` 경로, 001 `F/pages/SettingsPage.tsx` 맨 아래 [회원 탈퇴] 링크(001 T122 자리 — 001 담당에게 알림)

**Checkpoint**: 탈퇴 신청이 끝까지 동작한다(US1 단독 데모 가능)

---

## Phase 4: User Story 2 - 유예 기간 안에 복구하기 (Priority: P1)

**Goal**: 유예 회원은 로그인하면 복구 화면만 보고, [복구하기]로 탈퇴 전과 똑같이 돌아온다. 기한이 지나면 거부한다

**Independent Test**: 유예 회원으로 로그인해 어떤 주소로 가도 복구 화면만 보이는지, [복구하기] 후 블로그·글·댓글·좋아요 수·팔로워 수가 전과 같은지 확인한다

### Tests for User Story 2 ⚠️

- [X] T032 [P] [US2] 통합 테스트 `T/account/integration/RestoreApiIT.java`: `US2_1_로그인하면_복구_상태`(로그인 응답 `accountStatus: "WITHDRAWN"`, `GET /api/me`의 `restoreDeadline`·`restoreExpired: false`) · `US2_2_다른_요청은_403`(허용 목록 4개만 통과) · `US2_3_복구하면_그대로`(`POST /api/me/restore` 200, 블로그·글 상세·목록·좋아요 수·댓글 수가 신청 전 응답과 같음 — SC-002, 010 있으면 팔로워 수 포함, `MemberRestored` 1번) · `US2_4_로그아웃만_하면_유예_계속` · `US2_5_비밀번호_재설정_허용`(001 재설정 흐름 → 로그인 → 여전히 유예) · `US2_6_같은_이메일_가입_안내`(`POST /api/auth/signup` → 400 `VALIDATION_FAILED` + email 칸 `EMAIL_WITHDRAWAL_PENDING`) · FR-021a 기한 정각 200, +1ms 409 `RESTORE_PERIOD_EXPIRED`이고 `GET /api/me`의 `restoreExpired: true` · ACTIVE 회원 복구 200 변화·이벤트 없음 · 익명 처리된 회원 세션 401 (구현 메모: Redis 정지 503은 HTTP로는 세션을 못 읽어 401이 되므로 RestoreService를 직접 불러 확인. 좋아요 수 보존은 009 머지 후 글 상세 likeCount로 확인)
- [ ] T033 [P] [US2] 화면 테스트 `F/pages/__tests__/RestorePage.test.tsx`·`F/features/withdraw/__tests__/RestoreGate.test.tsx`: 두 상태 문구("({N}일 남음)" 계산 포함), [복구하기] 성공 → `/` + "다시 오신 걸 환영해요", 409면 기한 지남 상태로, [로그아웃], `status = WITHDRAWN`이면 `/write/new`·`/settings` 등은 `/account/restore`로 `replace`, `/terms`·`/privacy`·`/reset-password`는 그대로, 로그인 응답 `accountStatus: "WITHDRAWN"`이면 `/account/restore`로

### Implementation for User Story 2

- [X] T034 [US2] `B/account/application/RestoreService.java`(research R5: 상태별 분기, `findByIdForUpdate` → `WithdrawalPolicy.isRestorable` → `markRestored` → `MemberRestored`)와 `WithdrawalController`에 `POST /api/me/restore`(`Cache-Control: no-store`)를 더한다 (구현 메모: 복구 대상 아님(행 없음·익명 처리됨) 401, ACTIVE면 200 status ACTIVE(멱등, 이벤트 없음), SUSPENDED 403 ACCOUNT_SUSPENDED, 기한 지남 409 RESTORE_PERIOD_EXPIRED)
- [X] T035 [US2] 001 `B/account/application/MeSummary.java`·`MeQueryService.java`에 `restoreDeadline`·`restoreExpired`를 더하고(추가만, research R6) 001 `MeController` 테스트를 함께 돌린다 (구현 메모: 001 contracts/openapi.yaml MeSummary에 두 칸 추가(AccountApiContractTest 통과))
- [X] T036 [US2] `WithdrawalMailListener`에 `MemberRestored` → `R/mail/account-restored.txt`를 더하고 `WithdrawalMailIT`에 복구 메일 부분을 더한다 (구현 메모: T022에서 onWithdrawn과 함께 구현. WithdrawalMailIT 복구 메일 2사례)
- [X] T037 [US2] 001 `B/account/application/SignupService.java`: `LOCAL` 로그인 수단이 있고 그 회원이 `status = WITHDRAWN AND deleted_at IS NULL`이면 email 칸 오류를 `EMAIL_WITHDRAWAL_PENDING`으로(research R13) — 001 가입 테스트 함께 실행 (구현 메모: LOCAL 로그인 수단(findByProviderAndProviderUserId)이 있으면 그 회원을 읽어 WITHDRAWN·deleted_at NULL일 때 EMAIL_WITHDRAWAL_PENDING. 소셜 가입 쪽은 기존 동작 유지)
- [ ] T038 [P] [US2] `F/pages/RestorePage.tsx`(`/account/restore`, `GET /api/me` 기반 두 상태, [복구하기] 뒤 `SessionProvider` 새로 고침·토스트). 004 `F/features/auth-gate/authGate.ts`의 `RESTORE_PATH`(지금 `/restore`)도 `/account/restore`로 바꾼다
- [ ] T039 [US2] (**006 머지 후** — `App.tsx`) `F/features/withdraw/RestoreGate.tsx`와 `F/App.tsx` 연결(`SessionProvider` 아래, 허용 경로 목록은 research R12), 001 `F/pages/LoginPage.tsx`에서 `accountStatus === 'WITHDRAWN'`이면 `/account/restore`로 (T033 통과)
- [ ] T040 [P] [US2] `F/pages/SignupPage.tsx`: email 칸 `EMAIL_WITHDRAWAL_PENDING`이면 문구 + [로그인] 링크(001 `fieldErrors.ts` 매핑) — 001 `SignupPage.test.tsx`에 사례 추가
- [ ] T041 [US2] 004 `useAuthGate`의 `ACCOUNT_WITHDRAWN` 이동 경로를 `/account/restore`로 맞춘다(ANALYSIS-tier-a R13). 004 화면 코드(T051)가 아직 없으면 004 tasks T051 설명의 `/restore`를 `/account/restore`로 고치는 문서 수정만 한다

**Checkpoint**: 신청과 복구가 모두 동작한다(US1 + US2 = 권장 MVP)

---

## Phase 5: User Story 3 - 30일 뒤 개인 정보 정리 (Priority: P2)

**Goal**: 유예 30일이 지난 회원(과 영구 정지 1년 회원)을 매일 새벽 회원 한 명씩 한 트랜잭션으로 정리하고 익명 기록으로 바꾼다

**Independent Test**: 글·댓글(답글 있음/없음)·좋아요·사진·친구·팔로우·알림·신고를 가진 회원을 유예 31일 상태로 만들어 정리를 실행하고, 한 단계를 일부러 실패시켜 전부 취소되는지도 확인한다

### Tests for User Story 3 ⚠️

- [X] T042 [P] [US3] 통합 테스트 `T/account/integration/WithdrawPurgeJobIT.java`(테스트 설정 `required-orders`를 그때 있는 단계로 맞춤): `US3_1_개인정보_없음_주소_남음`(contracts §2-2 불변식 — 닉네임·소개·최근 활동·닉네임 변경 시각 NULL, `deleted_at` 기록, `handle` 그대로, `auth_identity` 0행, 동의·정지 이력 행 수 그대로) · `US3_2_답글_달린_댓글만_자리`(007 있을 때)와 모든 글의 `comment_count`·`like_count` = 실제 행 수(SC-004) · `US3_3_한_단계_실패하면_전부_취소`(테스트용 order 85 단계가 예외 → 그 회원 모든 테이블이 정리 전과 같음, 다른 대상 회원은 정리됨, 다음 실행에서 정상 단계면 정리됨 — SC-003) · `US3_4_신고`(014 있을 때) · `US3_5_사진_표시`(003 있을 때 `detached_at ≤ now - 7일`) · `US3_6_옛_주소_404` · 단계 실행 순서가 order 순(테스트 단계가 호출 순서 기록) · 필수 단계가 빠지면 아무 회원도 처리 안 하고 ERROR 로그 · 그 사이 복구한 회원은 건너뜀 · `withdrawn_at`이 정확히 30일 전이면 대상 아님 · 이벤트는 `PostPurged`(글마다)만 · 로그에 이메일·닉네임 없음 (구현 메모: 시계 Bean을 바꾸지 않으려고 withdrawn_at을 과거로 옮기고 run(now)을 직접 부름. 테스트 단계는 T/account/support/WithdrawalPurgeProbe(@Profile test, order 85). 필수 단계 누락은 required-orders만 바꾼 WithdrawPurgeJob을 직접 만들어 확인(새 컨텍스트 없음). 010·011·014 사례는 015 임시 단계로 확인)
- [X] T043 [P] [US3] 통합 테스트 `T/account/integration/WithdrawPurgeConcurrencyIT.java`: 기한 정각 근처 회원에 대해 `POST /api/me/restore`와 `purgeOne`을 동시에 20회 → 매번 둘 중 하나만 반영(복구 200 + 정리 건너뜀, 또는 정리 + 복구 401/409), 반쯤 정리된 회원 0 (구현 메모: 기한 정각 ±50ms로 신청 시각을 옮겨 20회. 정리가 이기면 복구 401/409, 복구 쪽이면 정리 SKIPPED)
- [X] T044 [P] [US3] 통합 테스트 `T/account/integration/SuspendedPurgeIT.java`(FR-008): 열린 영구 정지 1년+1일 → `status = WITHDRAWN`·`withdrawn_at = now`·`deleted_at` 기록·정지 행 그대로(`lifted_at` NULL), 1년-1일·기간 정지(`ends_at` 있음)·해제된 정지·관리자 → 그대로, 이벤트·메일 없음
- [X] T045 [P] [US3] 통합 테스트 `T/account/integration/WithdrawalRedisCleanerIT.java`: 커밋 뒤 세션·`auth:verify-latest`·`auth:reset-latest`와 가리키던 토큰·`redis-key-templates` 키(`{emailHash}` 포함)가 없음, 롤백되면 키가 그대로, Redis 정지 중에도 정리 트랜잭션은 커밋되고 WARN만 (구현 메모: redis-key-templates에 rl:reset:email:{emailHash}·member:active-touch:{memberId} 두 줄을 더함(001이 회원·이메일 해시로 만드는 키))
- [X] T046 [P] [US3] 단계별 통합 테스트 `T/account/integration/AccountWithdrawalPurgeStepsIT.java`: order 50(`auth_identity` 삭제, 같은 이메일 재가입 가능), 60(내가 요청한·받은·수락한 친구 행 모두 삭제, 남의 친구 관계 그대로), 90(contracts §2-1, 1행이 아니면 예외), 모두 `MANDATORY`(트랜잭션 밖 호출 예외) (구현 메모: MANDATORY 확인은 등록된 단계 전체(임시 단계·테스트 단계 포함)를 트랜잭션 밖에서 불러 IllegalTransactionStateException)

### Implementation for User Story 3

- [X] T047 [P] [US3] `B/account/application/purge/AuthIdentityWithdrawalPurgeStep.java`(order 50)
- [X] T048 [P] [US3] `B/account/application/purge/FriendshipWithdrawalPurgeStep.java`(order 60, 항상 실행 — M1)
- [X] T049 [P] [US3] `B/account/application/purge/MemberWithdrawalPurgeStep.java`(order 90, `WithdrawalMemberRepository.anonymize`) (T046 통과) (구현 메모: anonymize 시각은 WithdrawalPolicy.now())
- [X] T050 [P] [US3] `B/post/application/PostWithdrawalPurgeStep.java`(order 10 → 006 `PostPurgeService.purgeAllByAuthor`, 지운 글 수 INFO) — **006 머지 후**
- [X] T051 [P] [US3] `B/interaction/application/CommentWithdrawalPurgeStep.java`(order 20 → 007 `CommentPurgeService.purgeByAuthor`) — **007 머지 후**, 007 T060 인계 확인과 짝
- [X] T052 [P] [US3] `B/interaction/application/LikeWithdrawalPurgeStep.java`(order 30 → 009 `LikePurgeService.purgeByMember`) — **009 머지 후**, 009 T045 인계 확인과 짝 (구현 메모: 009 머지(f1e7fb6) 뒤 LikePurgeService.purgeByMember에 위임)
- [X] T053 [P] [US3] `B/media/application/ImageWithdrawalPurgeStep.java`(order 40 → 003 `ImagePurgeService.detachAllByUploader`) — **003 머지 후**, 003 T094 인계 확인과 짝
- [X] T054 [US3] 001 `B/account/infra/redis/AuthTokenStore.java`에 `revokeLatest(TokenType, memberId)`(최신 포인터가 가리키는 토큰 키와 포인터 삭제)를 더하고 `B/account/application/purge/WithdrawalRedisCleaner.java`(contracts §4, `SCAN` 없음, 실패 WARN)를 구현한다 (T045 통과) (구현 메모: revokeLatest는 RedisGuard.runWrite(커밋 뒤에만 부름)로 최신 포인터 GETDEL 후 토큰 키 삭제. 클리너는 세션·토큰·키 묶음을 각각 시도하고 실패하면 WARN)
- [X] T055 [US3] `B/account/application/purge/WithdrawalPurgeRunner.java`(`purgeOne(memberId, reason)` `REQUIRES_NEW`: 잠금·재확인 → 영구 정지면 상태 전이 → 이메일 해시 → 단계 실행(`WithdrawalPurgeStepException(order, 이름)`로 감쌈) → `afterCommit` Redis 정리)를 구현한다 (구현 메모: Reason GRACE_EXPIRED·SUSPENDED_PERMANENT, Outcome PURGED·SKIPPED. 이메일 해시는 001 LoginFailureCounter와 같은 정규화 이메일 SHA-256 hex. order 65·70·80은 015 임시 구현(interaction.InterimFollowWithdrawalPurgeStep, notification.application.InterimNotificationWithdrawalPurgeStep, moderation.application.InterimReportWithdrawalPurgeStep — 각 Javadoc에 주인 기능, @ConditionalOnMissingClass로 진짜 단계 클래스가 생기면 빠짐). 010은 FollowWithdrawalPurgeStep을 만들지 않고 FollowPurgeService.purgeByMember만 두기로 함 → 010 머지 뒤 임시 65를 FollowWithdrawalPurgeStep(위임)으로 바꿔야 함. 011 SQL의 = ANY(:ids)는 JdbcClient가 목록을 펼쳐 IN (:ids)로 씀)
- [X] T056 [US3] `B/account/application/purge/WithdrawPurgeJob.java`(`@Scheduled` + `@SchedulerLock(name = "withdrawPurge", lockAtMostFor = "PT1H")`, 필수 단계 확인 → 두 종류 대상 → 회원별 `purgeOne` + 실패 ERROR·다음 회원 → 처리·실패 수 INFO)를 구현한다 (T042·T043·T044 통과) (구현 메모: run(Instant) 오버로드는 잠금 없이 테스트·운영 도구용, 예약 실행은 scheduled())

**Checkpoint**: 정리 작업이 단계가 다 있을 때 끝까지 동작한다

---

## Phase 6: User Story 4 - 같은 이메일로 다시 가입하기 (Priority: P3)

**Goal**: 익명 처리 뒤 같은 이메일·소셜 계정으로 새 계정을 만들 수 있고, 옛 주소는 다시 쓸 수 없다

**Independent Test**: 정리가 끝난 회원의 이메일로 가입해 새 계정과 `옛주소_2` 제안을 확인한다

### Tests for User Story 4 ⚠️

- [X] T057 [P] [US4] 통합 테스트 `T/account/integration/RejoinAfterPurgeIT.java`: `US4_1_같은_이메일_새_계정`(새 `member.id`, 옛 행 그대로) · `US4_2_옛_주소는_다른_값`(가입 화면 주소 확인 API·가입 요청에서 `kim755030` 중복 → `kim755030_2` 제안 — 001 `HandleSuggester`) · `US4_3_옛_닉네임_즉시_사용`(다른 회원이 익명 처리 직후 그 닉네임으로 변경 가능 — `uq_member_nickname` NULL 무시) · 같은 소셜 계정(`provider_user_id`)으로 다시 로그인 → 새 가입 흐름 (구현 메모: 소셜 재로그인은 SocialSignupIntegrationTest의 OAuth 흐름을 줄여 옮김)

### Implementation for User Story 4

- [X] T058 [US4] T057가 실패하는 항목만 001 담당과 함께 고친다(설계상 001 코드 변경 없이 통과해야 한다 — research R13). 결과를 Notes에 적는다 (구현 메모: T057 4건 모두 001 코드 변경 없이 통과)

**Checkpoint**: 모든 user story 완료

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 권한 매트릭스, 종단 확인, 문서·인계, 회귀

- [X] T059 [P] 권한 매트릭스: `TR/permission/withdraw.csv`(research R15 표 18행, owner `015`)와 `T/account/permission/WithdrawalActions.java`(`me.withdrawal`·`me.withdraw`(픽스처 기본 비밀번호 또는 "탈퇴")·`me.restore`), `T/account/permission/WithdrawPermissionMatrixIT.java`(004 `AbstractPermissionMatrixIT` 상속) (구현 메모: 하네스가 행마다 새 행위자를 만들어 me.withdraw 성공 행이 다른 행에 영향 없음. 18행 모두 R15 표대로 통과)
- [ ] T060 [P] 종단 확인 `E/withdraw.spec.ts`(Playwright): quickstart §3의 1~8·10~12번(정리 작업 9번은 통합 테스트로 대신)
- [ ] T061 [P] 375px·접근성: 세 화면 가로 스크롤 없음, 버튼 44px 이상, 체크박스·본인 확인 칸 `label`, 오류 `role="alert"`, [탈퇴하기]가 탭 순서상 마지막
- [ ] T062 [P] 문서 반영: 001 `contracts/openapi.yaml` `MeSummary`에 두 칸·가입 email 칸 오류 `EMAIL_WITHDRAWAL_PENDING`, 001 data-model §2-1에 "탈퇴 전이는 015", 004 tasks T051 경로(T041가 문서 수정만 한 경우 확인)
- [ ] T063 [P] 인계 확인: `specs/010-follow-feed/tasks.md`·`specs/011-notification/tasks.md`·`specs/014-report-hide/tasks.md`에 order 65·70·80 단계 작업이 contracts/purge-steps.md §2 표(클래스 이름·SQL·`MANDATORY`)대로 있는지, Redis 키를 새로 만드는 기능이 `redis-key-templates`에 줄을 더했는지 확인한다
- [ ] T064 운영 확인: 정리 작업 시각(03:00)이 003·006(03:30)·009(04:10·04:20)과 겹치지 않는지, ShedLock 이름 `withdrawPurge`가 다른 작업과 다른지 확인하고 T003 결과(필수 단계 기본값)를 `R/application.yml` 주석에 적는다
- [ ] T065 quickstart.md §1~§5를 처음부터 끝까지 실행하고 결과를 기록한다
- [ ] T066 전체 회귀: `./mvnw -pl backend verify`(001·004·006 테스트 포함)와 `npm test`·`npm run build`·`npm run lint`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Cross-feature 선행**: specs/001(T097·T106·T108·T122 포함)·004·006 T063이 끝나야 Phase 1 확인(T001)을 통과한다
- **Setup (Phase 1)**: T002·T003은 답을 기다리는 동안 다른 작업을 막지 않는다
- **Foundational (Phase 2)**: Setup 후 — 모든 user story를 막는다
- **User Stories (Phase 3+)**: 모두 Foundational 완료 후 시작
- **Polish (Phase 7)**: 원하는 스토리 완료 후. T063은 010·011·014 tasks가 쓰인 뒤

### User Story Dependencies

- **US1 (P1)**: Foundational 이후. T022은 007 머지 후(그 전에는 안내 API가 시작되지 않음 — 포트 Bean 없음)
- **US2 (P1)**: US1의 `WithdrawalController`·`WithdrawalMailListener`(T025·T026) 후 — 같은 파일. 신청이 있어야 복구를 시험할 수 있다
- **US3 (P2)**: Foundational 이후 서버 쪽은 독립. 단계 T050~T053은 각 기능 머지 후, 010·011·014 단계가 없으면 정리 작업은 필수 단계 누락으로 돌지 않는다
- **US4 (P3)**: US3 정리(T056) 후

### Within Each User Story

- 테스트 작업을 먼저 쓰고 실패를 확인한 뒤 구현한다
- 포트·저장소 → Service → Controller → 메일 → 화면 API → 컴포넌트 → 경로
- 같은 파일을 고치는 작업은 순서대로 한다: `WithdrawalController`(T025 → T034), `WithdrawalMailListener`(T026 → T036), `WithdrawalMailIT`(T026 → T036), `F/App.tsx`(T031 → T039), `AccountReasonCode`(T011, 001과 공유), 001 파일(`PasswordChangeService` T014, `MeSummary` T035, `SignupService` T037, `AuthTokenStore` T054, `logout.ts` T028)

### Parallel Opportunities

- Phase 2: 테스트 T005·T006·T007·T008 병렬, 구현 T009~T012 병렬
- US1: 테스트 T016~T019 병렬, T020 뒤 T021·T022 병렬, 화면 T027~T029 병렬
- US2: 테스트 T032·T033 병렬, T038·T040 병렬
- US3: 테스트 T042~T046 병렬, 단계 T047~T053 병렬(서로 다른 모듈 파일)
- US3 서버 작업은 US1·US2 화면 작업과 병렬
- Polish: T059~T063 병렬

---

## Parallel Example: User Story 3

```bash
# User Story 3 테스트를 함께 작성:
Task: "WithdrawPurgeJobIT in backend/src/test/java/com/team/blog/account/integration/"
Task: "WithdrawPurgeConcurrencyIT"
Task: "SuspendedPurgeIT"
Task: "WithdrawalRedisCleanerIT"
Task: "AccountWithdrawalPurgeStepsIT"

# 단계 클래스(서로 다른 모듈):
Task: "AuthIdentityWithdrawalPurgeStep / FriendshipWithdrawalPurgeStep / MemberWithdrawalPurgeStep (account)"
Task: "PostWithdrawalPurgeStep (post, 006 머지 후)"
Task: "CommentWithdrawalPurgeStep / LikeWithdrawalPurgeStep (interaction)"
Task: "ImageWithdrawalPurgeStep (media)"
```

## Parallel Example: Foundational

```bash
Task: "RestoreDeadlineTest", "WithdrawalPurgeStepOrderTest", "WithdrawalMemberRepositoryIT", "CurrentPasswordVerifierIT"
Task: "WithdrawalPurgeStep 인터페이스", "MemberWithdrawn/MemberRestored", "AccountReasonCode 추가", "WithdrawalPolicy"
```

---

## Implementation Strategy

### MVP First (User Story 1 → US2)

1. Phase 1 확인(T001)·팀 확인 질문(T002·T003)·설정(T004)
2. Phase 2 Foundational
3. Phase 3 US1 → **STOP and VALIDATE**: 신청 즉시 세션 0·노출 0(SC-001)
4. Phase 4 US2 → 복구·기한. 여기까지가 **권장 MVP**(신청과 되돌리기가 함께 있어야 13 D-6이 성립)
5. Deploy/demo if ready — 정리 작업은 아직 없어도 유예 회원은 계속 유예 상태로 안전하게 남는다

### Incremental Delivery

1. Setup + Foundational → 기한·확장점·저장소
2. US1 → 신청 → 데모
3. US2 → 복구 → 데모
4. US3 → 정리 작업(003·006·007·009 단계부터, 010·011·014 단계가 들어오면 필수 단계가 채워져 돌기 시작)
5. US4 → 재가입 확인
6. Polish → 권한 매트릭스·종단 확인·인계

### Parallel Team Strategy

1. 팀이 Setup + Foundational을 함께 끝낸다
2. Foundational 이후:
   - Developer A(나민서): US1·US2 서버(`WithdrawalService`·`RestoreService`·메일) → US3 작업·Runner
   - Developer B: US1·US2 화면(`WithdrawPage`·`RestorePage`·`RestoreGate`)
   - Developer C(강성찬·김민서): 자기 기능의 단계 클래스(T051~T053)와 010·011 단계
3. 001 소유 파일(T014·T028·T035·T037·T054)은 001 담당에게 알리고 001 회귀 테스트를 함께 돌린다

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 001·004·006 소유 파일을 고치는 작업은 그 기능의 회귀 테스트를 함께 돌리고 담당에게 알린다
- 탈퇴 사유·비밀번호·이메일·닉네임을 로그에 남기지 않는다(코드 리뷰 점검 항목)
- 정리 단계 안에서 파일 삭제·메일·Redis 호출을 하지 않는다(코드 리뷰 점검 항목)
- T003 결과: (팀 답을 받으면 여기에 적는다)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
