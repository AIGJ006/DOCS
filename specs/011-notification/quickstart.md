# Quickstart: 011-notification 검증 시나리오

**Feature**: `011-notification` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 구독·SQL은 [contracts/notification-sql.md](./contracts/notification-sql.md), 테이블·응답 모델·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·CSRF·`AsyncConfig`·`AccountStatusGuard`·`CursorCodec`·`MemberQueryService`·임시 머리말), 002·004(`PostWentPublic`·`PostReadService`·`PostAccessPolicy`·권한 하네스), 005(글 상세 `?comment=` 이동·`useCursorList`·`relativeText`)
- 이벤트를 내는 기능: 007(댓글), 009(좋아요), 010(팔로우), 014(신고·숨김). 없는 기능의 알림은 통합 테스트에서 이벤트를 직접 발행해 확인한다
- 있으면 함께 확인: 015(탈퇴 정리 order 70)

## 1. 기동

```bash
docker compose up -d postgres redis minio mailpit
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.notification.*`, `blog.async.event.queue-capacity: 1000`, `await-termination: 20s`

## 2. 자동 테스트

```bash
./mvnw -pl backend verify -Dit.test='*Notification*IT,LikeGroupingIT,FollowGroupingIT,EventExecutorShutdownIT' -Dtest=DomainEventShapeTest
(cd frontend && npx vitest run src/features/notification src/pages/__tests__/NotificationsPage.test.tsx)
(cd frontend && npx playwright test e2e/notification.spec.ts)
```

| 테스트 | 확인하는 것 |
|---|---|
| `DomainEventShapeTest` | SC-011: `shared.event`의 모든 record 필드가 `long`·`Long`·enum·`Instant`뿐 |
| `EventExecutorShutdownIT` | 대기열 1,000, 가득 차면 버리고 WARN, 종료 때 최대 20초 기다리고 남은 수 WARN(FR-003) |
| `CommentNotificationIT` | US1 #1~#3: 최상위 댓글 → 글 작성자 `COMMENT`, 답글 → 답글 대상 `REPLY` + 글 작성자 `COMMENT`, 대상이 글 작성자면 `REPLY` 하나. US4 #5: 댓글 삭제(자리로 남김 포함)·숨김 → 그 댓글 알림 0 |
| `NotificationExclusionIT` | SC-001·SC-004, US1 #4·#5·#7: 본인 행동 0, 처리 전 비공개로 바뀐 글 0, 받는 사람·행동자 유예 0, 끈 종류 0(운영 알림은 생김), 취소가 먼저 처리돼도 남는 알림 0 |
| `LikeGroupingIT` | SC-002, US3 #1~#4·#6: 동시 10명 → 안 읽은 묶음 1·인원 10, 한 사람 취소·재클릭 5번 → 알림 1, 취소하면 빠지고 마지막 행동자 다시 계산·0명이면 삭제, 읽은 묶음은 그대로·새 좋아요는 새 묶음, 사람이 더해지면 맨 위 |
| `FollowGroupingIT` | SC-003, US3 #5: 7일 안 언팔로우·팔로우 반복 → 새 알림 없음, 7일 뒤(시계 이동) 다시 들어감, 언팔로우 자체는 알림 없음 |
| `NewPostNotificationIT` | US1 #6, SC-012: 공개 발행 → 팔로워 전원 1개씩, 비공개 발행 뒤 처음 공개 → 1번, 다시 비공개·공개·다시 발행 → 더 없음, 유예 팔로워·끈 팔로워 제외, 같은 사건 두 번 → 한 번, 팔로워 1만 명 1초 이내 |
| `NotificationApiIT` | US2 #1·#4~#8: 안 읽은 수와 `no-store`, 목록 정렬·10·20개·커서 끝까지 중복·누락 0, size 15 → 400, 다른 목록 커서 400, 읽음 204(두 번째도 204), 모두 읽음 `{updated: 12}`, 삭제 204, 인증 전 회원 모두 가능, 비회원 401, 유예 403, SQL 1번(`SqlCounter`) |
| `NotificationDisplayIT` | US4 #1~#4: 비공개·휴지통·숨김·작성자 유예 → `post.unavailable`·`url null`·미리보기 없음, 다시 공개 → 제목, 닉네임 변경 반영, 유예 행동자 `{withdrawn:true}`, 댓글 수정 → 새 미리보기 50자 |
| `ModerationNotificationIT` | US5 #1~#6 (이벤트 직접 발행): 신고 결과 두 가지(`actor`·`post`·`url` null), 글 숨김 → 작성자에게 제목·사유, 댓글 숨김 → 글 제목 없음·댓글 알림 삭제, 해제 뒤 `stillHidden false`·사유 없음, 응답에 신고자·관리자 번호 0(SC-010), 모두 꺼도 생김 |
| `NotificationSettingsIT` | US6: 새 회원 5종 true, 좋아요 끈 뒤 새 좋아요 알림 0·기존 알림 그대로, 키 빠짐 400 |
| `NotificationCleanupIT` | US7 #1·#2, SC-008: 91일 된 알림 삭제·89일은 남음, 하루에 1,001개 받은 회원 → 1,000개, 오늘 받지 않은 회원은 1,200개여도 이번 정리 대상 아님(비용 확인) |
| `NotificationWithdrawalPurgeIT` | US7 #3·#4: 유예 중 알림 그대로·복구 후 그대로, order 70 실행 뒤 받은 알림 0, 남의 좋아요 묶음 인원 −1·마지막 행동자 다시 계산·혼자였던 묶음 삭제, 내가 남긴 댓글 알림 0, 끄기 설정 0 |
| `NotificationFailureIsolationIT` | SC-009: `NotificationWriter`가 예외를 던지게 바꾼 상태에서 댓글·좋아요·팔로우·발행 API 모두 성공, WARN 로그에 이벤트 종류·ID |
| `NotificationPermissionMatrixIT` | `notification.csv` (research R15 표) — 남의 알림 읽음·삭제는 관리자도 404(SC-007) |

## 3. 수동 확인 (브라우저)

1. 회원 A·B·C를 만든다. A로 공개 글을 하나 쓴다
2. B로 A 글에 댓글 → A로 로그인한 탭에서 30초 안에 종 배지 "1", 화면 낭독기 이름 "안 읽은 알림 1개"
3. 종을 누르면 "불러오는 중…" 뒤 "**B**님이 「글 제목」에 댓글을 남겼어요: "…""(● + 굵게). 누르면 글 상세의 그 댓글로 이동하고 강조, 배지 사라짐
4. B·C로 A 글에 좋아요 → A의 알림 "**C**님 외 1명이 「글 제목」을 좋아해요" 하나. C가 좋아요 취소 → "**B**님이 「글 제목」을 좋아해요"
5. B로 A 팔로우 → 언팔로우 → 팔로우 → A의 새 팔로워 알림 하나. 누르면 `/@a/followers`
6. C가 A를 팔로우한 뒤 A가 새 글을 공개 발행 → C에게 "**A**님이 새 글을 올렸어요: 「제목」"
7. A가 6의 글을 비공개로 바꾼다 → C의 새 글 알림이 "볼 수 없는 글이에요"로 바뀌고 누르면 이동 없이 읽음만 된다. A가 받은 알림은 A가 작성자라 제목이 그대로 보인다. 다시 공개하면 C의 알림에 제목이 돌아온다
8. 다른 탭으로 옮겨 1분 기다린 뒤 개발자 도구 네트워크 탭 → 그동안 `unread-count` 요청 없음, 돌아오면 바로 1번
9. 펼침 목록에서 [모두 읽음] → 배지 사라짐. [모든 알림 보기] → `/notifications`에서 20개씩 [더 보기], [×]로 삭제
10. 설정 → "알림" 칸에서 좋아요를 끄고 B로 다른 글에 좋아요 → 새 알림 없음, 이전 좋아요 알림은 그대로. "운영 알림(신고 결과·숨김)은 끌 수 없어요" 안내
11. 네트워크를 끊고 종을 누르면 "알림을 불러오지 못했어요 [다시 시도]", 배지는 그대로
12. 375px 폭에서 펼침 목록·알림 페이지 가로 스크롤 없음, 종·[×] 44px 이상

## 4. 다른 기능 확인 (있을 때)

- 014: 관리자가 A 글을 "스팸·광고"로 숨김 → A에게 "회원님의 글「제목」이(가) 운영 정책에 따라 숨겨졌어요 (사유: 스팸·광고)", 누르면 글 상세(작성자 숨김 안내). 신고자에게 "신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요"
- 015: B가 탈퇴 신청 → A의 B 관련 알림이 "탈퇴한 사용자"로 보이고 B에게 새 알림이 생기지 않음 → 복구하면 원래대로. 30일 정리 뒤 `SELECT count(*) FROM notification WHERE receiver_id = :b OR (last_actor_id = :b AND group_key IS NULL)` = 0
- 006: A가 글을 영구 삭제 → 그 글의 알림 0(CASCADE)
