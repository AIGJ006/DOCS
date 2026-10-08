# Quickstart: 015-withdraw 검증 시나리오

**Feature**: `015-withdraw` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 정리 단계·작업·이벤트는 [contracts/purge-steps.md](./contracts/purge-steps.md), 상태 전이·코드·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·`SessionTerminator`·`AccountStatusGuard`·`WithdrawnAccountGateFilter`·비밀번호 변경 T097·설정 화면 T122·메일), 004(공용 노출 조건·권한 하네스·`useAuthGate`), 006(`PostPurgeService.purgeAllByAuthor`), 003·007·009(정리 Service)
- 정리 작업 전체 확인에는 010·011·014의 단계(65·70·80)가 필요하다. 없으면 정리 작업은 "단계 누락" ERROR만 남기고 돌지 않는다(정상 동작). 일부만으로 시험하려면 로컬 설정에서 `blog.withdraw.purge.required-orders`를 줄인다

## 1. 기동

```bash
docker compose up -d postgres redis minio mailpit
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.withdraw.*` 기본값, 시작 로그에 "탈퇴 정리 단계 orders=[10, 20, 30, 40, 50, 60, …, 90]"

## 2. 자동 테스트

```bash
./mvnw -pl backend test -Dtest='WithdrawRequestValidationTest,RestoreDeadlineTest,WithdrawalPurgeStepOrderTest'
./mvnw -pl backend verify -Dit.test='Withdraw*IT,Restore*IT,SuspendedPurgeIT,RejoinAfterPurgeIT,WithdrawalMailIT'
(cd frontend && npx vitest run src/features/withdraw src/pages/__tests__/WithdrawPage.test.tsx src/pages/__tests__/RestorePage.test.tsx)
(cd frontend && npx playwright test e2e/withdraw.spec.ts)
```

| 테스트 | 확인하는 것 |
|---|---|
| `WithdrawalApiIT` | US1 #1~#6: 안내 숫자(글 24·댓글 18·좋아요 126), 체크 없음 400, 비밀번호 5번 → 429(비밀번호 변경과 합산), "탈퇴" 불일치 400, 관리자 409, 인증 전 회원 200, 정지 세션 403, 다른 기기 세션 401, 이 요청 세션도 끊김, Redis 정지 중 503 + 상태 그대로, `MemberWithdrawn` 1번 |
| `WithdrawalGraceIT` | US1 #7, US2 #2: 블로그·글 404, 홈·태그·블로그 목록에 없음, 허용 목록 4개 외 403, 좋아요 행·댓글 행 그대로(007·009 있을 때), 팔로워 수 제외(010 있을 때) |
| `RestoreApiIT` | US2 #1·#3·#4: `GET /api/me`의 `restoreDeadline`·`restoreExpired`, 복구 후 블로그·글·댓글·좋아요 수·팔로워 수가 전과 같음(SC-002), ACTIVE 재호출 200 변화 없음, 기한 지남 409(t+1ms), 기한 정각 200, `MemberRestored` 1번 |
| `WithdrawPurgeJobIT` | US3 #1~#6: 개인 정보 잔존 0(SC-005), 남은 댓글 자리·카운터 불변식(SC-004), 단계 강제 실패 → 그 회원 전부 그대로·다른 회원은 정리됨(SC-003), 다음 실행에 재시도, 필수 단계 누락이면 아무도 처리 안 함, 동의·정지 이력 행 그대로, 사진 `detached_at` 표시, 옛 주소 404, 이벤트 없음(`PostPurged` 제외) |
| `WithdrawPurgeConcurrencyIT` | 복구 요청과 정리 작업 동시 20회: 둘 중 하나만 반영, 반쯤 정리된 회원 0 |
| `SuspendedPurgeIT` | 영구 정지 1년+1일 → 정리됨(상태 WITHDRAWN, 정지 행 그대로), 1년-1일·기간 정지·관리자 → 그대로 |
| `RejoinAfterPurgeIT` | US4: 같은 이메일 가입 → 새 계정, 주소 `옛주소_2` 제안, 옛 닉네임 즉시 사용 가능, 유예 중이면 `EMAIL_WITHDRAWAL_PENDING` |
| `WithdrawalMailIT` | 접수·복구 메일 각 1통(기한 포함), 이메일 없는 소셜 계정 0통, 메일 실패해도 200 |
| `WithdrawPermissionMatrixIT` | `withdraw.csv` 18행 |

## 3. 수동 확인 (브라우저)

1. 회원 A(이메일 가입)로 글 3개·남의 글 댓글 2개를 만들고 B로 A의 글에 좋아요 → A로 `/settings` 맨 아래 [회원 탈퇴] → `/settings/withdraw`에 주소·글 3·댓글 2·좋아요 1·기한(지금+30일)이 보인다
2. 체크·비밀번호 없이 [탈퇴하기] 비활성, 비밀번호 칸에서 Enter를 눌러도 제출되지 않는다, 버튼에 처음 포커스가 없다
3. 틀린 비밀번호 5번 → "잠시 후 다시 시도해 주세요(약 15분)", `/settings`의 비밀번호 변경도 잠겨 있다
4. 맞는 비밀번호로 신청 → `/withdrawn` 완료 화면에 기한, 개발자 도구 IndexedDB의 `draft:{A}:*`가 없다, 다른 브라우저의 A 세션은 새로 고치면 로그아웃 상태
5. B 브라우저에서 A 블로그 → 404, 홈 목록에 A 글 없음, A가 B 글에 쓴 댓글은 "탈퇴한 사용자의 댓글이에요"(007 있을 때)
6. A로 다시 로그인 → `/account/restore`만 보인다("{기한}까지 복구할 수 있어요 (30일 남음)"), 주소창에 `/write/new`를 쳐도 복구 화면으로 돌아온다
7. [로그아웃] → 다시 로그인해도 복구 화면(유예 계속). [복구하기] → 홈 + "다시 오신 걸 환영해요", 블로그·글·좋아요 수가 그대로, Mailpit(`http://localhost:8025`)에 접수·복구 메일 2통
8. 다시 탈퇴 후 DB에서 `withdrawn_at`을 31일 전으로 바꾸고 로그인 → "복구 기한이 지났어요" + [로그아웃]만
9. 정리 작업 수동 실행: DB에서 A의 `withdrawn_at`을 31일 전으로 바꾸고, 로컬 설정에서 `blog.withdraw.purge.cron`을 몇 분 뒤 시각으로(필요하면 `required-orders`도 줄여) 재기동 → A의 `member` 행에 닉네임·소개가 없고 `deleted_at`이 있음, `auth_identity` 없음, B 글 `like_count` 0, 03:30 사진 정리 뒤 MinIO에 A 사진 없음
10. A 이메일로 가입 → 주소 칸에 `옛주소_2` 제안, 옛 닉네임 사용 가능
11. 소셜 가입 회원 C로 탈퇴 → "탈퇴" 입력 칸만 보이고, "탈퇴 " (뒤 공백)는 통과, "탈퇴함"은 거부
12. 375px 폭에서 세 화면 가로 스크롤 없음, [탈퇴하기]·[복구하기] 44px 이상

## 4. 개인 정보 확인 (SC-005)

```sql
SELECT id, handle, nickname, bio, nickname_changed_at, last_active_at, deleted_at FROM member WHERE id = :a;
SELECT count(*) FROM auth_identity WHERE member_id = :a;          -- 0
SELECT count(*) FROM image WHERE uploader_id = :a AND detached_at IS NULL;   -- 0 (정리 직후), 사진 정리 뒤 행 0
SELECT count(*) FROM member_agreement WHERE member_id = :a;       -- 전과 같음
SELECT count(*) FROM member_suspension WHERE member_id = :a;      -- 전과 같음
```

- 정리 작업 로그에 이메일·닉네임이 없고 회원 번호·단계·건수만 있다
- Redis에 `auth:pw-change-fail:{a}`·`auth:verify-latest:{a}`가 없다

## 5. 다른 기능 확인 (있을 때)

- 010: 유예 중 A가 B의 팔로워 목록·수에서 빠졌다가 복구 후 돌아온다, 정리 뒤 `follow`에 A 행 없음
- 011: 유예 중 A의 행동으로 알림이 생기지 않는다, 정리 뒤 A가 받은 알림·A가 행동한 하나짜리 알림 없음, 묶음 인원 수 −1
- 012: `MemberWithdrawn`·`MemberRestored` 뒤 트렌딩·검색에서 A 글이 빠졌다가 돌아온다
- 014: 유예 중 A 콘텐츠 신고는 대기, 정리 뒤 `CLOSED_NO_TARGET`, A가 쓴 신고의 설명 NULL. 유예 중 A는 정지할 수 없다
