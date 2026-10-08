# Tier B/C 교차 분석 보고서 (003·007~016)

- 분석일: 2026-10-08
- 대상: `specs/003-image-upload`, `007-comment`, `008-tag`, `009-like-view`, `010-follow-feed`, `011-notification`, `012-trending-search`, `013-ai-tag-suggest`, `014-report-hide`, `015-withdraw`, `016-dark-mode`의 spec·plan·research·data-model·contracts·quickstart·tasks
- 기준: `.specify/memory/constitution.md`, `README.md` "정해진 것", [ANALYSIS-tier-a.md](ANALYSIS-tier-a.md)의 결정과 남은 항목, 실제 코드(`backend/`·`frontend/`, Flyway V1·V2), 구현 중인 006(`/home/claude/wt-006`, 읽기만)
- 방법: `/speckit-analyze` 검사 항목(중복, 모호, 미명세, constitution 정렬, 커버리지 공백, 불일치)을 기능 안과 기능 사이에 적용했다. 기능 사이 검사는 이벤트 필드·구독자, 이유 코드·문구, 404 본문, 요청 제한 키, 설정 키, 배치 시각·ShedLock 이름, 권한 CSV, "먼저 하는 쪽이 만든다" 파일, `F/App.tsx` 같은 006 겹침 파일, 마이그레이션 번호를 표로 맞춰 보았다. 고칠 수 있는 문서 오류는 바로 고쳤고(§1-1), 작업 번호(T###)는 바꾸지 않았다. 새 작업은 없다.
- 모든 clarify 질문(11개 기능 42줄)은 2026-10-08 민서가 추천 답으로 확정했다(`(민서 확정 2026-10-08)`). `[NEEDS CLARIFICATION]`은 0개다.

## 1. 발견 표

### 1-1. 이번 분석에서 해결됨

| ID | 범주 | 심각도 | 위치 | 요약 | 처리 |
|---|---|---|---|---|---|
| I1 | 불일치 | HIGH | 013 plan·research R4·data-model §5·contracts(openapi·providers §8)·tasks T003·T008·T017·T021·quickstart ↔ 003 spec Clarifications | 013은 하루 20회 한도를 `TOO_MANY_REQUESTS` + `details.kind = AI_DAILY_LIMIT`로 바꿨는데, 003은 민서 확정 답으로 하루 한도를 별도 코드 `DAILY_UPLOAD_LIMIT`로 둔다. "하루 한도"를 두 방식으로 내게 된다. | **해결됨**: 확정된 003 규칙("빈도 제한은 `TOO_MANY_REQUESTS`, 하루 한도·용량처럼 뜻이 다른 것은 별도 코드")을 따라 013을 spec 원래대로 429 `AI_DAILY_LIMIT` + `details {resetAt}`로 되돌렸다. 쓰지 않게 된 openapi `TooManyRequests` 응답을 지웠고, `TagSuggestReasonCode`에 코드를 더했다. 013 T003 ①은 "해결됨"으로 표시. |
| D1 | 중복 | MEDIUM | 014 plan·research R2·contracts/moderation-sql.md §2·§6·tasks T014 ↔ 007 T010 | 014가 001 `MemberQueryService`에 `handlesOf`·`nicknamesOf`를 새로 더하는데, 007이 같은 일을 하는 `findDisplays(ids) → MemberDisplay(id, handle, nickname, withdrawn)`를 이미 더한다. 007은 014의 선행이다. | **해결됨**: 014는 `findDisplays`를 쓰고 `findAdminView`만 더한다. |
| I2 | 불일치 | MEDIUM | 007 contracts/events.md §1 | ① "탈퇴 정리(015 `MemberPurged`가 대신)" — 015에는 `MemberPurged`가 없다(정리 단계 order 20·70·80이 이벤트 없이 정리). ② `CommentDeleted` 구독자에 014(`OrphanCaseCloser`)가 없다. ③ `CommentCreated` 구독자에 "012 트렌딩(있으면)" — 012는 구독하지 않는다(012 R14). | **해결됨**: 세 문장을 고쳤다. 011 plan 설계 후 확인 4, 014 plan 설계 후 확인 3의 "고친다"를 "고쳤다"로. |
| I3 | 불일치 | MEDIUM | 015 data-model §이벤트, contracts/purge-steps.md §4, research R12, tasks 후속 목록 | `MemberWithdrawn`·`MemberRestored` 구독자로 012를 적었다. 012는 요청 때 공용 조건으로 거르고 구독하지 않는다. | **해결됨**: 015 네 곳에서 012를 지우고 이유를 적었다. 012 plan·research의 "고친다"를 "고쳤다"로. |
| I4 | 불일치 | MEDIUM | 011 research R13·data-model §설정·tasks T054 | 정리 배치 시간대를 `blog.notification.cleanup.zone`(`Asia/Seoul`)으로 따로 두었다. Tier A R11은 모든 배치가 `zone = "${blog.time-zone}"`. | **해결됨**: 011을 `${blog.time-zone}`으로 바꾸고 `cleanup.zone` 키를 지웠다. |
| I5 | 의존 누락 | MEDIUM | 010 tasks T029·T038, 015 tasks T031·T039, 014 tasks T049·T054 | `F/App.tsx`(006이 `/manage/posts`를 더하는 파일)를 고치는 작업과 006 `ConfirmDialog`·`useToast`를 쓰는 작업에 "006 머지 후" 표시가 없었다. 010은 "006과 겹치는 파일 없음"이라고 적었다. | **해결됨**: 작업과 Cross-feature Dependencies에 "006 머지 후"를 더했다. |
| I6 | 불일치 | LOW | 004 tasks T051, 004 research R-29 ↔ 015 화면 `/account/restore` | ANALYSIS-tier-a R13이 남긴 것. 004 문서가 아직 `/restore`였다(T051은 미구현). | **해결됨**: 004 두 곳을 `/account/restore`로. 015 research R12 주석도 맞췄다. |
| I7 | 불일치 | LOW | 011 plan Constitution II | "006 `moderation`과 같은 상황" — 모듈 소유자는 014(006은 임시 패키지만). | **해결됨** |
| G1 | 커버리지 | LOW | 003 FR-017 | "사용 공간은 정리 작업이 파일을 지울 때 돌아온다(글을 지워도 바로 안 돌아옴)"를 확인하는 테스트가 없었다. | **해결됨**: T055 `StorageUsageApiIT`에 경우 추가. |
| G2 | 커버리지 | LOW | 007 FR-007 | "댓글에는 금칙어 필터를 적용하지 않는다"를 확인하는 테스트가 없었다. | **해결됨**: T026 `CommentWriteIT`에 `금칙어가_있어도_거부하지_않는다`. |
| G3 | 커버리지 | LOW | 012 FR-023 | "코드 블록 안의 글자도 찾는다"를 확인하는 테스트가 없었다. | **해결됨**: T010 `PostSearchIT`에 경우 추가. |
| G4 | 커버리지 | LOW | 014 FR-017 | "신고 수와 관계없이 자동으로 숨기지 않는다"를 확인하는 테스트가 없었다. | **해결됨**: T016 `ReportApiIT`·quickstart 표에 "신고 10건이어도 숨김 없음". |

plan을 쓰는 동안 이미 고친 것(이번 표에 넣지 않음): 015 research R10 문장, 011 tasks의 012 관련 문장, 013 R13 설정 칸·Redis 키 수·`AGREEMENT_VERSION_MISMATCH` 칸 오류, 014 이유 코드 수·관리자 API 수·`CommentItem` 경로, 016 토큰 수·색 줄 수.

### 1-2. 남은 발견

| ID | 범주 | 심각도 | 위치 | 요약 | 권고 |
|---|---|---|---|---|---|
| R1 | 불일치(공용 코드) | MEDIUM | 002 `RedisGuard`·`AutosaveUnavailableException` ↔ 003 R9·T095, 007 R17·T057, 009 R13·T044, 013 plan 설계 후 확인 3 | Redis 메모리 부족(OOM)이면 `RedisGuard`가 어느 경로에서든 503 `AUTOSAVE_UNAVAILABLE` "잠시 후 다시 저장할게요"를 던진다. 사진 업로드·댓글·좋아요·AI 추천에 맞지 않는 문구다. 각 기능은 화면이 code를 무시하거나(003·007·009) 예외를 바꿔(013) 피해 간다. | 002 소유 `RedisGuard`가 자동 저장 경로 밖에서는 공통 503 `TEMPORARILY_UNAVAILABLE`("잠시 후 다시 시도해 주세요", `CommonReasonCode`에 이미 있음)을 던지게 한다(→ 팀 결정 1). |
| R2 | Constitution 정렬 | MEDIUM | 헌법 II 모듈 목록(account·post·tag·media·interaction·discovery·shared) ↔ 011 `notification`, 014 `moderation` | 두 기능이 목록에 없는 새 모듈을 만든다(006이 `moderation` 패키지를 임시로 이미 만들었다). 위반이 아니라 목록 갱신이 필요하다. | 헌법 II 목록에 두 모듈을 더하는 헌법 개정(→ 팀 결정 2, 011 T003·014 T003). |
| R3 | 불일치 | LOW | 001 `EmailVerificationService`(`rl:verify-resend:`)·로그인(`rl:login:ip:`) ↔ 002·Tier B/C 전부(`ratelimit:`) | 요청 제한 Redis 키 접두어가 둘이다. Tier B/C 10개 키는 모두 `ratelimit:{행동}:{주체}`로 맞췄다. | 001 키를 `ratelimit:`로 옮기거나 둘을 인정한다(→ 팀 결정 3). 001 소유 코드라 이 문서 작업에서는 고치지 않았다. |
| R4 | 불일치(Tier A R10 이어짐) | LOW | 설정 키 | Tier B/C는 모두 `blog.<기능>`(`blog.image`·`comment`·`tag`·`like`·`view`·`follow`·`notification`·`trending`·`search`·`ai`·`moderation`·`withdraw`)이고 시간대는 공통 `blog.time-zone` 하나다. Tier A R10은 아직 정해지지 않았다. | `blog.<기능>`으로 확정(→ 팀 결정 3). |
| R5 | 불일치(코드) | LOW | 002 `PostReasonCode.RATE_LIMITED` ↔ 007 Clarifications Q3 | 확정된 "429는 `TOO_MANY_REQUESTS` 하나"에 002 코드가 아직 맞지 않는다. | 007 T058(002 담당 확인 후)로 처리. |
| R6 | 불일치(문구) | LOW | 004 spec 비회원 댓글 문구 ↔ 007 FR-023 | "로그인하고 댓글 쓰기"와 21 §3-1 문구가 다르다. | 007 T059로 004 담당에게 제안. |
| R7 | 미명세(원문에 없는 제안) | MEDIUM | 010 R3, 012 R9·R10, 013 R3·R4, 014 data-model §5, 015 R3 | plan이 원문에 없는 코드·필드·API를 더했다: 010 `CANNOT_FOLLOW_SELF`·팔로우 `ACCOUNT_WRITE`·interaction 모듈 / 012 `SNAPSHOT_EXPIRED`(410)·`SEARCH_QUERY_TOO_SHORT`·`snippet {text, marks}`(헌법 IV 때문에 `snippetHtml` 대신)·사람 검색 정렬 / 013 상태 API·AI 동의 조회 API·`AI_UNAVAILABLE` `details.reason` 4종 / 014 새 이유 코드 7개·`REPORT_DETAIL_REQUIRED`를 칸 오류로·직접 숨김은 관리자 화면에서만 / 015 신청 본문 `confirmed`·코드 2개·잠금에 `PASSWORD_CHANGE_TEMPORARILY_LOCKED` 재사용. | 각 기능 tasks의 팀 확인 작업(010 T003, 012 T003, 013 T003, 014 T004, 015 T002)으로 승인받는다(→ 팀 결정 4). 답이 오기 전에는 기본안으로 진행. |
| R8 | 불일치(spec ↔ 이벤트) | MEDIUM | 011 R6·T004 ↔ 007 `CommentCreated.replyToMemberId` | "내 답글에 다시 단 답글"은 `replyToMemberId`가 NULL이라 최상위 작성자가 답글 알림을 받는다. spec US1 #2와 이 한 경우만 다르다. | 그대로 둘지, 007 이벤트에 필드를 더할지(→ 팀 결정 5). |
| R9 | Constitution 정렬(방향) | MEDIUM | 014 R10·T005, R8·T005 | ① 정지 때 세션 삭제가 실패(Redis 장애)하면 정지하지 않고 503 — 헌법 V "장애 때 통과"와 반대 방향이지만 보안 조치라 반쯤 된 상태를 남기지 않는다. ② 본인이 지워 자리로 남은 댓글의 대기 신고도 "대상 없음"으로 닫는다(원문은 완전 삭제만). | 기본안 승인(→ 팀 결정 6). |
| R10 | 소유 파일 변경 | LOW | 014 T041 ↔ 005 `AuthorStatusBanner` | FR-022 문장("…숨겨진 글이에요 (사유: …). 다른 사람에게는…")을 만들려면 005의 문장 조립(`HIDDEN_NOTICE` + `hiddenReasonSlot`)을 바꿔야 한다. | 005 담당과 맞춘다. 005 회귀 테스트 함께 실행. |
| R11 | 미명세(운영 문서) | MEDIUM | 009 T002·T055, 013 T004·T038, 015 T003 ② | 처리방침에 들어갈 문단이 셋이다(방문자 쿠키, 외부 AI 전송, 영구 정지 1년 자동 정리). 공개 뒤 버전을 올리면 001 로그인 재동의가 전원에게 뜬다. | 첫 공개 전 처리방침 첫 판에 세 문단을 함께 넣는다(→ 팀 결정 7). |
| R12 | 미명세(운영 환경) | MEDIUM | 003 T002, 013 T005, 008 T002 | ① 운영 NHN 저장소 점검 11가지(결과에 따라 `upload-mode = PROXY`, 1~9·11번 실패면 운영 배포 보류) ② Ollama용 메모리 2~4GB·`num-thread`·Gemini 하루 한도 시간대(`quota-zone`)·모델 이름 ③ 008보다 운영 배포가 먼저인지(먼저면 조건부 정리 마이그레이션 T075). | 배포 담당 확인(→ 팀 결정 8). 로컬 개발·구현은 막지 않는다. |
| R13 | 미명세 | LOW | 015 T003 ①·R9 | `blog.withdraw.purge.required-orders` 기본값이 10개 단계 전부라, 010·011·014(65·70·80)가 머지되기 전에는 30일 정리가 시작하지 않는다(안전한 쪽). | 운영 배포 때는 그때 머지된 단계만 적고, 기능이 머지될 때마다 더한다(→ 팀 결정 9). |
| R14 | 미명세(다크 모드 규격) | MEDIUM | 016 plan 설계 후 확인 1~4·6, T003·T004 | ① 원문 `--color-border`(배경 대비 1.30/1.63)는 입력칸 3:1을 못 넘는다 → 입력칸·버튼 윤곽은 `--color-text-muted` ② 11개 밖의 색(대화 상자 뒤, 경고 상자, 비교 화면, 알림 줄, 코드 강조, 아바타 8색)은 이름이 공통이 아닌 "보조 토큰" ③ 브랜드 제안값 ④ highlight.js github 색 4개가 이 저장소 코드 배경에서 4.5:1 미만 → Primer 새 값 ⑤ 새 개발 의존성 `@axe-core/playwright` ⑥ 공통 저장소 `VITE_DARK_MODE` 기본 `true`. | 기본안 승인(→ 팀 결정 10). ⑤는 답이 오기 전까지 016 T036을 미룬다. |
| R15 | 미명세 | LOW | 007 R9·T036 | 글이 비공개·휴지통·숨김이 된 뒤에도 내 댓글은 지울 수 있게 했다(원문에 없음, `CONTENT_CLEANUP`). | 기본안 승인(→ 팀 결정 4에 포함). |
| R16 | 공용 파일 변경 | LOW | 003 T004 `docker-compose.yml` | MinIO 콘솔 포트 9001을 닫고 `minio-init`·CORS를 더한다. 세 사람의 로컬 환경이 함께 바뀐다. | 머지 때 팀에 알린다. |
| R17 | 미명세(규칙) | LOW | 008 T075, 012 T046 | 이번 11개 기능은 새 테이블·컬럼이 없다(모두 V1으로 충분). 조건부 마이그레이션 둘(태그 재정규화, `ix_comment_post_author`)만 "V3 이후 다음 번호"로 적혀 있다. | §4-7 번호 규칙(제안)을 따른다. |

## 2. 요구사항 ↔ 작업 커버리지

연결 기준: 작업 설명의 FR·SC 번호, 인수 시나리오(US*-#)와 quickstart §2 테스트 표 행, 그리고 FR별 핵심어 검색으로 남은 것을 하나씩 확인했다. 다른 기능이 구현하는 FR은 "위임"으로 센다.

| 기능 | FR 수 | 작업에 연결된 FR | 커버리지 | SC 수 | 작업 수 | 비고 |
|---|---|---|---|---|---|---|
| 003 사진 업로드 | 43 | 43 | 100% | 13 | 101 | G1 해결. FR-026~028(운영 저장소)은 T002 결과에 따라 T099~T100 |
| 008 태그 | 41 | 41 | 100% | 8 | 75 | FR-040(검색창 `#태그`)은 012 |
| 007 댓글 | 40 | 40 | 100% | 8 | 63 | G2 해결. FR-022 신고 버튼은 014가 켬 |
| 009 좋아요·조회수 | 34 | 34 | 100% | 11 | 49 | FR-025(1초 뒤 기록)는 005 `useViewBeacon`이 이미 함 — T035 확인 |
| 015 탈퇴·복구 | 33 | 33 | 100% (위임 3) | 7 | 66 | FR-011·012·014는 007·010·011이 구현, 015 `WithdrawalGraceIT`가 확인 |
| 010 팔로우·피드 | 27 | 27 | 100% | 9 | 50 | — |
| 011 알림 | 40 | 40 | 100% (위임 1) | 13 | 62 | FR-001(사건 발행)은 각 발행 기능, 011은 `DomainEventShapeTest`로 확인 |
| 012 트렌딩·검색 | 40 | 40 | 100% | 7 | 47 | G3 해결. sitemap(FR-040) 포함 |
| 013 AI 태그 추천 | 35 | 35 | 100% | 8 | 56 | I1 해결 |
| 014 신고·숨김·정지 | 41 | 41 | 100% | 8 | 65 | G4 해결 |
| 016 다크 모드 | 21 (FR-022 삭제) | 21 | 100% (위임 1) | 6 | 41 | FR-016(아바타 8색)은 001 T120이 값을 씀. 다크 모드를 만들지 않는 서비스는 공통 묶음(Phase 2)만 |
| **합계** | **395** | **395** | **100%** | **98** | **675** | Tier A(504) + Tier B/C(675) = 1,179 |

성능·보안 SC는 모두 측정 작업이 있다: 003 SC-004(동시 10건 용량)·SC-005(005 썸네일 전송량, T098 — Tier A R7 해결), 008 SC-007(1만 건 300ms), 009 SC-001·002(동시 좋아요), 011 SC(새 글 1만 명), 012 SC-001(10만 건 p95 500ms)·SC-002(트렌딩 p95 200ms), 013 SC-006(Ollama 30초, `@Tag("ollama")`), 016 SC-001(첫 그리기)·SC-004(axe).

## 3. Constitution 정렬

- 원칙 I(공통 기반은 바꾸지 않음): V1·V2를 고치는 작업 없음. 다른 기능 소유 파일은 "추가만" 또는 이름만(016 토큰) 바꾼다. 003 `docker-compose.yml` 변경은 R16.
- 원칙 II(모듈 경계): 새 모듈 2개(R2). 읽기 전용 예외는 각 plan Complexity Tracking에 적었다(010 2건, 011 2건, 012 2건 — 005 카드 SQL과 같은 종류). D1로 같은 일을 하는 공개 메서드 중복을 없앴다.
- 원칙 III(404 동일): 10개 openapi의 404 예시가 모두 `{code: NOT_FOUND, message: 볼 수 없는 페이지예요, errors: [], details: null}`이다. 권한 CSV 10개(`image`·`comment`·`tag-list`·`like-view`·`follow`·`notification`·`search`·`moderation`·`withdraw`, 013은 `post-write.csv`에 행 추가 — 006 머지 후).
- 원칙 IV(사용자 콘텐츠 실행 안 됨): 012 `snippet {text, marks}`, 014 스냅샷 텍스트 노드, 016 인라인 스크립트 없음(CSP 그대로).
- 원칙 V(부가 기능 실패): 모든 Redis 요청 제한은 장애 때 통과. 예외는 014 정지(R9 ①, 보안 조치).
- 원칙 VII(설정값): 모든 수치가 `blog.<기능>.*`, 시간대는 `blog.time-zone`(I4 해결).
- 원칙 VI·VIII: 위반 없음. 데이터 규칙은 Testcontainers 통합 테스트로 확인한다.

## 4. 기능 사이 공통 규칙 (이번에 맞춘 것)

### 4-1. 오류 응답

- 본문 `{code, message, errors, details}`, `errors`는 항상 배열, 메시지 끝 마침표 없음, 404 본문 고정(README 2026-10-07). 11개 기능 data-model·openapi에서 어긋난 곳 없음.
- 요청 빈도 제한(1분·1시간)은 429 `TOO_MANY_REQUESTS` "잠시 후 다시 시도해 주세요" + `Retry-After` 하나(007 Q3). 하루 한도·용량처럼 뜻이 다른 한도는 별도 코드: 003 `DAILY_UPLOAD_LIMIT`(429)·`STORAGE_QUOTA_EXCEEDED`(409), 013 `AI_DAILY_LIMIT`(429)(003 Q, I1).
- 판정 순서: 로그인(401) → 계정 상태(403) → 볼 수 있나(404) → 형식·업무 규칙(400/409/422) → 요청 횟수(429). 잘못된 요청은 횟수에 세지 않는다(007 Q2).
- 칸 규칙 위반은 400 `VALIDATION_FAILED` + `errors[].code`(001 `AGREEMENT_VERSION_MISMATCH`, 002 `TOO_MANY_TAGS` 방식). 014 `REPORT_DETAIL_REQUIRED`, 013 동의 버전 불일치가 이 방식.
- 상태 지정은 `PUT`/`DELETE`(O8): 009 좋아요, 010 팔로우, 011 읽음(`PATCH` → `PUT`), 013 AI 동의, 014 숨김. 내용 일부 수정은 `PATCH`(001 프로필, 007 댓글 수정).

### 4-2. 이벤트 (`shared.event`, `AFTER_COMMIT` + `@Async("eventExecutor")`, 필드는 ID·enum·`Instant`만)

| 이벤트 | 발행 | 구독 |
|---|---|---|
| `CommentCreated` | 007 | 011 |
| `CommentDeleted` | 007 | 011, 014 |
| `PostLiked` | 009 | 011 |
| `MemberFollowed`·`MemberUnfollowed` | 010 | 011 |
| `PostWentPublic` | 002·004(있음) | 011 |
| `ReportResolved`·`ContentHidden` | 014 | 011 |
| `ContentUnhidden`·`MemberSuspended` | 014 | 없음 |
| `MemberWithdrawn`·`MemberRestored` | 015 | account 메일 |
| `PostPurged` | 006 | (011은 FK CASCADE) |

012는 아무것도 구독하지 않는다(요청 때 공용 조건 — 012 R14). 두 기능이 같은 record를 쓰는 곳은 필드를 대조했다(011 ↔ 007·009·010·014 모두 같음).

### 4-3. 먼저 하는 쪽이 만든다 (같은 파일, 두 기능)

| 파일 | 기능 |
|---|---|
| `B/post/application/PostCounterService.java` | 007 T009 ↔ 009 T008 |
| `B/shared/text/InvisibleCharacters.java` | 008 T010 ↔ 007 T011 |
| `B/discovery/.../CardFilter` | 008 T014 ↔ 010 T011 |
| `B/shared/application/withdraw/WithdrawalPurgeStep.java` | 015 T009 ↔ 010 T040 · 011 T055 · 014 T061 |
| `B/shared/event/ReportResolved.java`·`ContentHidden.java`, enum `ReportTargetType`·`ReportResult` | 014 T007 ↔ 011 T045 |
| `F/features/moderation/reasonLabels.ts` | 014 T015 ↔ 011 T047 |
| 처리 시점 확인 메서드(`CommentQueryService.isActive`, `LikeQueryService.isLiked`, `FollowQueryService`) | 각 소유 기능 ↔ 011 T019 |
| `F/components/DefaultAvatar.tsx` 원 색 | 001 T120 ↔ 016 T014 |

### 4-4. 006 머지 후 (006 브랜치와 같은 파일)

| 기능 | 작업 | 겹치는 것 |
|---|---|---|
| 003 | T046 | `ImagePostPurgeStep`(006 임시 구현 넘겨받기) |
| 007 | T021·T032 | `CommentQueryService`(006 T058) |
| 008 | T037 | `F/App.tsx` |
| 010 | T029·T038 | `F/App.tsx` |
| 011 | T032·T039 | `F/App.tsx`, `PostPurgeService`(테스트에서 글 완전 삭제) |
| 012 | T029 | `F/App.tsx` |
| 013 | T020 | `post-write.csv` |
| 014 | T009·T025·T039·T040·T049·T054 | `ReportPostPurgeStep`·`moderation/package-info.java`, `F/App.tsx`, `ConfirmDialog`·`useToast` |
| 015 | T031·T039·T050 | `F/App.tsx`, `PostPurgeService.purgeAllByAuthor` |
| 016 | T016·T031 | `dialogs.css`·`managePosts.css`, `F/App.tsx` |
| 009 | — | 없음 |

### 4-5. 탈퇴 정리 단계와 배치

- 정리 단계 order: 10 글(015, 006 호출) · 20 댓글(015, 007 호출) · 30 좋아요(015, 009 호출) · 40 사진(015, 003 호출) · 50 로그인 수단 · 60 친구 · 65 팔로우(010) · 70 알림(011) · 80 신고(014) · 90 회원 익명화(015). 015 contracts/purge-steps.md §2와 각 기능 tasks가 같다.
- 배치 시각(KST, `zone = "${blog.time-zone}"`, ShedLock 이름): 03:00 `withdrawPurge`(015) · 03:30 사진·휴지통·임시 글 정리(002·003 `imageCleanup`·006) · 04:10·04:20 조회수(009) · 04:30 `notificationCleanup`(011) · 04:45 `reportSnapshotCleanup`(014) · 10분마다 `trendingSnapshot`(012). 겹치는 이름·시각 없음.

### 4-6. 화면 색

- 모든 화면은 색 값을 직접 쓰지 않고 토큰만 쓴다(016 FR-011). 016 T008 `noRawColors.test.ts`가 `frontend/src` 전체를 검사하므로 007~015의 새 화면도 처음부터 토큰을 쓴다. 공통 토큰 이름 11개는 세 서비스 같음(016 Q3).

### 4-7. 마이그레이션 번호 (제안)

- 이번 11개 기능은 새 테이블·컬럼이 없다. V1·V2는 고치지 않는다.
- V3 이후는 머지 순서대로 다음 번호를 쓴다. 두 브랜치가 같은 번호를 잡았으면 늦게 머지하는 쪽이 번호를 올린다. 지금 조건부로 계획된 것: 008 T075(태그 재정규화 — 운영 배포가 먼저일 때만), 012 T046(`ix_comment_post_author` — 측정 뒤 필요할 때만, `CREATE INDEX CONCURRENTLY`는 Flyway 트랜잭션 밖).

### 4-8. 요청 제한 키와 권한 CSV

- 요청 제한: `ratelimit:image:{memberId}`, `ratelimit:comment:{memberId}`·`comment-edit`, `ratelimit:tag-suggest:{memberId}`(008 자동완성), `ratelimit:like:{memberId}`, `ratelimit:view:{visitorKey}`, `ratelimit:follow:{memberId}`, `ratelimit:search:{visitorKey}`, `ratelimit:report:{memberId}:1m`·`:1d`. 013 AI 키는 모두 `ai:` 접두어(이름이 비슷한 `tag-suggest`와 겹치지 않음).
- 권한 CSV: 기능마다 `TR/permission/<기능>.csv` 한 파일(열 `actor,targetState,action,expectedStatus,expectedCode,owner`). `post-write.csv`는 006·013만 고친다.

## 5. 지표

- 요구사항(FR) 395 / 성공 기준(SC) 98 / 작업 675 / FR 커버리지 100%(위임 5건 포함)
- 기능별 작업 수: 003 101 · 008 75 · 007 63 · 009 49 · 015 66 · 010 50 · 011 62 · 012 47 · 013 56 · 014 65 · 016 41
- 발견 29건: 해결 12(CRITICAL 0 · HIGH 1 · MEDIUM 5 · LOW 6), 남음 17(CRITICAL 0 · HIGH 0 · MEDIUM 8 · LOW 9)

## 6. 팀 결정 필요

1. **Redis OOM 응답(R1)**: 002 `RedisGuard`가 자동 저장 밖에서는 `TEMPORARILY_UNAVAILABLE`을 던지게 바꿀지(추천), 기능마다 계속 피해 갈지. (009 T044 확인: 좋아요는 요청 제한 쓰기에서 503 `AUTOSAVE_UNAVAILABLE`을 받고 DB는 바뀌지 않으며, 화면은 code를 보지 않고 누르기 전 상태로 되돌린 뒤 "좋아요를 반영하지 못했어요"를 보인다. 조회 기록은 OOM이어도 건너뛰고 204 — `LikeViewRedisOomIT`, `useLikeToggle.test.ts`.)
2. **헌법 II 모듈 목록(R2)**: `moderation`(014)·`notification`(011)을 더하는 헌법 개정.
3. **키 이름 규칙(R3·R4, Tier A R10)**: 요청 제한 Redis 키 `ratelimit:`로 통일(001 `rl:` 옮김), 설정 키 `blog.<기능>` 확정.
4. **원문에 없는 제안 승인(R7·R15)**: 010 T003(모듈·`ACCOUNT_WRITE`·문구), 012 T003(새 코드 2개·`snippet`·정렬), 013 T003 ②③(`details.reason`·상태 API·동의 조회 API), 014 T004(새 코드 7개·칸 오류·직접 숨김 위치), 015 T002(`confirmed`·코드 2개·잠금 코드), 007 R9(비공개가 된 글의 내 댓글 삭제 허용).
5. **답글의 답글 알림(R8)**: 그대로 둘지, 007 `CommentCreated`에 필드를 더할지(011 T004, 007 담당과 함께).
6. **신고·정지 기본안(R9)**: 정지 중 세션 삭제 실패 시 503, 자리로 남은 댓글의 대기 신고도 "대상 없음"(014 T005).
7. **처리방침 문단과 버전(R11)**: 방문자 쿠키(009)·외부 AI 전송(013)·영구 정지 1년 자동 정리(015)를 첫 공개 전 첫 판에 함께 넣을지(추천). 이미 공개됐다면 버전을 한 번만 올린다.
8. **운영 환경 확인(R12)**: 003 NHN 저장소 점검 11가지, 013 Ollama 메모리·Gemini 설정, 008 운영 배포 선후.
9. **탈퇴 정리 필수 단계 기본값(R13)**: 015 T003 ①.
10. **다크 모드 규격(R14)**: 입력칸 테두리 토큰, 보조 토큰 비공통, 브랜드 값, 코드 강조 4색, `@axe-core/playwright`, `VITE_DARK_MODE` 기본 `true`(016 T003·T004).

## 7. 다음 행동

- CRITICAL·HIGH 남은 항목은 없다. 각 기능은 선행 기능(대부분 001 Phase 1·2, 004, 005)이 끝나면 `/speckit-implement`를 시작해도 된다. 팀 결정 항목은 기본안으로 진행하고, 답이 바뀌면 해당 기능 Clarifications·research 한 줄과 tasks의 확인 작업에서 고친다.
- 권장 구현 순서(의존 기준): 003·008 → 007·009 → 010·015 → 011·014 → 012·013, 016 공통 묶음은 언제든(Tier A 화면 이름 바꾸기라 빠를수록 충돌이 적다).
- 006 머지 뒤 §4-4 작업을 다시 확인한다.
