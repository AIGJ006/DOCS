# Quickstart: 014-report-hide 검증 시나리오

**Feature**: `014-report-hide` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), SQL·공개 Service·이벤트는 [contracts/moderation-sql.md](./contracts/moderation-sql.md), 값 객체·이유 코드·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(`AccountStatusGuard`·`RateLimiter`·`SessionTerminator`·`SuspensionService` 시그니처·로그인 정지 처리 T108·T110), 004(`PostReadService`·관리자 경로 규칙 US7·`useAuthGate`·`PostActions`·`AdminRouteGate`·권한 하네스), 005(`ReactionBar`·`AuthorStatusBanner`), 006(`PostPurgeStep`·`ReportPostPurgeStep` 임시 구현·`ConfirmDialog`), 007(`CommentModerationService`·`CommentDeleted`·`CommentItem`)
- 있으면 함께 확인: 011(신고 결과·숨김 알림), 012(트렌딩·검색에서 숨긴 글 제외), 015(탈퇴 정리 order 80)
- 관리자 계정은 DB에서 직접 만든다(Clarifications Q5): `UPDATE member SET role = 'ADMIN' WHERE handle = 'admin_kim';`

## 1. 기동

```bash
docker compose up -d postgres redis minio mailpit
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- 기동 로그에 ShedLock 작업 `reportSnapshotCleanup`(04:45) 등록

## 2. 자동 테스트

```bash
./mvnw -pl backend verify -Dit.test='Report*IT,Admin*IT,CaseResolutionIT,DirectHideIT,HiddenContentVisibilityIT,UnhideRestoresIT,SuspensionIT,OrphanCaseIT,ModerationPermissionMatrixIT' -Dtest='ReportReasonTest'
(cd frontend && npx vitest run src/features/moderation src/features/admin src/pages/admin)
(cd frontend && npx playwright test e2e/report-hide.spec.ts)
```

- E2E는 이메일 인증을 마친 회원 셋(작성자·독자·관리자)이 있어야 한다: `E2E_EMAIL`(작성자)·`E2E_READER_EMAIL`·`E2E_ADMIN_EMAIL`(DB에서 `role = 'ADMIN'`)·`E2E_PASSWORD`. 관리자 닉네임에 "관리자"를 넣으면 가입이 `NICKNAME_RESERVED`로 막힌다

| 테스트 | 확인하는 것 |
|---|---|
| `ReportApiIT` | US1 #1~#7: 접수 200·사건 1·신고 1·스냅샷(글 제목 + 앞 2,000자, 댓글 내용), 같은 회원 두 번 → 신고 1건(SC-002), 기타 + 빈 설명 → 400 `REPORT_DETAIL_REQUIRED` 칸 오류, 자기 글·댓글 400, 비회원 401·인증 전 403, 비공개·휴지통·숨김·작성자 유예 글과 그 댓글·삭제된 댓글 404 고정 본문, 6번째(1분) 429·51번째(하루) 429 + `Retry-After`, Redis 정지 중 제한 없이 통과, 처리된 뒤 신고 → 새 사건, 접수로 알림·이벤트 0, 신고 10건이 쌓여도 자동 숨김 없음(FR-017) |
| `ReportConcurrencyIT` | 다른 회원 20명이 같은 글을 동시에 첫 신고 → 대기 사건 1·신고 20, 같은 회원 동시 2번 → 신고 1, 처리와 신고가 동시 → 신고는 처리된 사건 또는 새 사건 중 하나에만(100번 반복, 교착 0) |
| `AdminAccessIT` | US2 #1, SC-007: 일반 회원 `/admin/reports`·`/admin/xyz`·`/api/admin/reports`·`/api/admin/xyz` 모두 같은 404 본문, 비회원 같은 401, 관리자 200 (004 `AdminPathIT`와 같은 표) |
| `CaseResolutionIT` | US2 #2~#5, SC-003: 대기 탭 정렬(신고 수 → 최근), 상세에 현재 원문 없음·"현재: 비공개", 신고 7건 숨기기 → 7건 모두 닫힘·`HIDDEN`·`handled_by/at`, 이벤트 `ReportResolved` 7 + `ContentHidden` 1, 반려 → `REJECTED`·`NO_VIOLATION` 7·대상 그대로, 두 관리자 동시 처리 → 하나 200·하나 409(아무것도 안 바뀜), 자기 글 사건 400, 자기 혼자 신고 400·다른 회원 신고가 있으면 200(Q2), 대상이 지워진 사건 처리 → 409 `CLOSED_NO_TARGET` |
| `DirectHideIT` | FR-020: 신고 없는 공개 글 숨김 → 새 사건 `HIDDEN`(신고 0)·`ContentHidden` 1, 대기 사건이 있는 글 → 그 사건 `HIDDEN` + `ReportResolved` 신고마다, 남의 비공개 글 404, 이미 숨김 → 200 이벤트 0, 댓글 숨김 → 댓글 수 −1, 해제 → +1·사건 상태 그대로·`ContentUnhidden`·알림 0 |
| `HiddenContentVisibilityIT` | US2 #6·#7, SC-001: 숨긴 글이 작성자 외(비회원·회원·관리자)에게 상세 404, 홈·블로그·태그·검색·트렌딩·sitemap·블로그 글 수에서 빠짐, 작성자 상세 200 + `authorView.hiddenReason = SPAM`, 작성자 관리 목록 `hidden: true`, 작성자에게도 공개 목록에 없음. 숨긴 댓글(US3 #1~#3): 다른 사람·글 주인에게 문구만·작성자 정보 없음, 작성자에게 원문, 답글 그대로, [수정]·[답글] 거부·[삭제] 허용 |
| `UnhideRestoresIT` | US4 #1~#4, SC-006: 좋아요 12·댓글 3 글을 숨김 → 행 그대로 → 해제 → 좋아요 12·댓글 3·홈 목록 같은 위치, 숨긴 글을 작성자가 고치고 다시 발행·공개 범위 변경·휴지통·복구해도 숨김 그대로 |
| `SuspensionIT` | US5 #1~#6, SC-005: 세션 3개 회원 7일 정지 → 다음 요청부터 3개 모두 비로그인, 맞는 비밀번호 로그인 403 `ACCOUNT_SUSPENDED` `details {endsAt, reason}`, 영구 `endsAt: null`, 기한 지난 뒤 로그인 → 자동 해제(`lifted_by` NULL)·성공, 정지 회원 글 그대로 보임, 관리자 정지 400, 사유 없음 400, 탈퇴 유예 400, 이미 정지 409, 동시에 두 관리자 정지 → 열린 정지 1, Redis 정지 중 정지 → 503·DB 변화 없음, 해제 → `lifted_by`·`ACTIVE`, 알림 0 |
| `OrphanCaseIT` | FR-032: 글 영구 삭제 → 그 글·그 글 댓글 대기 사건 `CLOSED_NO_TARGET`·스냅샷 남음·이벤트 0, 댓글 본인 삭제(행 삭제·자리) → 닫힘, 탈퇴 정리 order 80 → 그 회원 콘텐츠 사건 닫힘·그 회원 신고 설명 NULL, 배치가 고아 사건 닫음, 휴지통 이동만으로는 대기 유지 |
| `ReportSnapshotCleanupIT` | FR-033, SC-008: 처리 31일 된 사건 스냅샷·설명 NULL·사건·상태·사유·일자 남음, 29일 된 것 그대로, 대기 사건 그대로, ShedLock 이름 |
| `ReporterAnonymityIT` | SC-004: 작성자가 받는 상세 `authorView`·관리 목록·알림 응답(011 있으면)에 신고자 번호·닉네임·신고 수가 없다 |
| `ModerationPermissionMatrixIT` | `moderation.csv`(research R13 표) |

## 3. 수동 확인 (브라우저)

1. 회원 A로 공개 글 하나·댓글 하나, 회원 B·C(인증)와 인증 전 회원 D를 만든다. 관리자 K를 DB로 지정한다
2. B로 A의 글 → [신고] → "스팸·광고" → "신고가 접수됐어요. 검토 후 처리할게요". 다시 신고해도 같은 문구
3. B로 [신고] → "기타" + 설명 비움 → [신고하기]가 막히고 "기타 사유를 적어 주세요"
4. A로 자기 글 → [신고] 버튼이 없다. D로 [신고] → 이메일 인증 안내. 로그아웃 상태로 [신고] → 로그인 안내
5. C로 같은 글 신고, B로 A의 댓글 신고
6. B로 `/admin/reports` → 없는 페이지. 로그아웃 상태 → 로그인 화면
7. K로 `/admin/reports` → 대기 탭에 글(신고 2건, 스팸 2)·댓글(신고 1건). 글 항목 → 스냅샷, "현재: 공개", 작성자 `@a`·가입일·숨겨진 0
8. A가 글을 비공개로 바꾼 뒤 K가 새로 고침 → "현재: 비공개", 스냅샷만 보이고 원문 링크가 없다
9. K → 사유 "스팸·광고" [숨기기] → 확인 → 처리됨 탭으로 이동. 다른 창에서 같은 사건을 다시 처리 → "이미 처리된 신고예요"
10. 비회원·B로 그 글 주소 → 없는 페이지, 홈·A 블로그·검색에 없음. A로 → "운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요", 내 글 관리 [숨김] 배지
11. (011 있으면) B·C 알림 "조치했어요", A 알림 "운영 정책에 따라 숨겨졌어요 (사유: 스팸·광고)" — 어디에도 B·C 이름이 없다
12. K → 처리됨 탭 → 그 글 [숨김 해제] → 좋아요 수·댓글·홈 위치 그대로, A에게 새 알림 없음
13. K → 댓글 사건 [숨기기] → B로 그 글 → "운영 정책에 따라 숨겨진 댓글이에요", 댓글 수 1 감소. A(댓글 작성자)로 → 원문 + "숨겨졌어요 (나만 보여요)"
14. K → `/admin/members/a` → 7일, 사유 "스팸 반복" → [정지] → 확인. A의 열린 창에서 새로 고치면 로그아웃. A로 로그인 → "정지된 계정이에요 (10월 15일 14:00까지). 사유: 스팸 반복"
15. K → [정지 해제] → A 로그인 성공
16. K가 자기 글 사건을 처리하려 하면 "내 글이나 댓글은 처리할 수 없어요"
17. 375px 폭에서 신고 창·관리자 목록·상세·회원 화면 가로 스크롤 없음

## 4. 다른 기능 확인 (있을 때)

- 006: 신고 대기 중인 글을 휴지통 → 대기 유지("현재: 휴지통") → 영구 삭제 → 처리됨 탭에 "대상 없음"(처리 관리자 "자동")
- 012: 숨긴 글이 트렌딩 [더 보기]·검색·sitemap에서 빠짐
- 015: 신고 대기 중 작성자가 탈퇴 신청 → 대기 유지 → 30일 정리 뒤 "대상 없음", 신고자가 탈퇴하면 그 신고의 설명이 비워짐
