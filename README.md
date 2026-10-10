# 팀 공통 블로그 플랫폼 — 스펙 저장소

강성찬·나민서·김민서의 블로그 플랫폼 공통 설계를 [GitHub Spec Kit](https://github.com/github/spec-kit)(v1.1.1) 구조로 정리한 저장소입니다.

| 경로 | 내용 |
|---|---|
| [`docs/`](docs/) | 원문 설계 문서 01~51 (수정하지 않은 원본) |
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | 프로젝트 원칙 8개 (docs 01·02 기반) |
| [`specs/NNN-기능/spec.md`](specs/) | 기능별 스펙 (사용자 스토리, FR, 성공 기준, 가정, 구현 메모) |
| `specs/NNN-기능/checklists/requirements.md` | 스펙 품질 체크리스트 |
| `.claude/skills/speckit-*` | Claude Code용 Spec Kit 명령 |

## 기능 스펙

| # | 스펙 | 원문 | 단계 | 진행 |
|---|---|---|---|---|
| 001 | [account-auth](specs/001-account-auth/spec.md) 로그인·블로그 주소·닉네임·프로필·친구·최근 활동 | 07, 08, 09, 11 | Tier A | tasks ✓ |
| 002 | [post-authoring](specs/002-post-authoring/spec.md) 글 작성·자동 저장·발행·본문 정화 | 04, 05, 12 | Tier A | tasks ✓ |
| 003 | [image-upload](specs/003-image-upload/spec.md) 이미지 업로드 | 04, 23 | Tier B | tasks ✓ |
| 004 | [visibility-permission](specs/004-visibility-permission/spec.md) 공개 범위·권한 매트릭스 | 06, 42 | Tier A | tasks ✓ |
| 005 | [post-reading](specs/005-post-reading/spec.md) 전체 글 목록·개인 블로그·글 상세 | 10, 40 | Tier A | tasks ✓ |
| 006 | [manage-delete](specs/006-manage-delete/spec.md) 내 글 관리·삭제·휴지통 | 41, 13 | Tier A | tasks ✓ |
| 007 | [comment](specs/007-comment/spec.md) 댓글·답글 | 21 | Tier B | tasks ✓ |
| 008 | [tag](specs/008-tag/spec.md) 태그·태그별 글 목록 | 22 | Tier B | tasks ✓ |
| 009 | [like-view](specs/009-like-view/spec.md) 좋아요·조회수 | 30, 31 | Tier B | tasks ✓ |
| 010 | [follow-feed](specs/010-follow-feed/spec.md) 팔로우·피드 | 24 | Tier C | tasks ✓ |
| 011 | [notification](specs/011-notification/spec.md) 도메인 이벤트·인앱 알림 | 20, 25 | Tier C | tasks ✓ |
| 012 | [trending-search](specs/012-trending-search/spec.md) 트렌딩·검색 | 32, 33 | Tier C | tasks ✓ |
| 013 | [ai-tag-suggest](specs/013-ai-tag-suggest/spec.md) AI 태그 추천 | 34 | Tier C | tasks ✓ |
| 014 | [report-hide](specs/014-report-hide/spec.md) 신고·관리자 숨김 | 43 | Tier C | tasks ✓ |
| 015 | [withdraw](specs/015-withdraw/spec.md) 회원 탈퇴·복구 | 44, 13 | Tier C | tasks ✓ |
| 016 | [dark-mode](specs/016-dark-mode/spec.md) 다크 모드 | 45 | Tier C | tasks ✓ |
| 017 | [category](specs/017-category/spec.md) 2단계 카테고리 (나민서 개인 확장) | 01 §2-4, 02 §7 | 개인 확장 | 구현 ✓ |

ERD(03, 51)는 각 스펙의 Implementation Notes에서 참조하며, `/speckit-plan` 단계의 `data-model.md`로 옮겨 갑니다. 카테고리·주제 등은 공통이 아닌 개인 확장(01 §2-4)이라 공통 스펙에 없습니다.

## 정해진 것

| 날짜 | 스펙 | 결정 |
|---|---|---|
| 2026-10-07 | 004 FR-037, 009 FR-009·010 | 자기 글 좋아요는 400 `CANNOT_LIKE_OWN_POST`. 판정 순서는 로그인 → 계정 상태 → 볼 수 있나 → 자기 글 → 요청 횟수 (42 §3) |
| 2026-10-07 | 001 FR-009 | 운영 메일은 설정값으로 바꿀 수 있는 SMTP, 운영 계정은 배포 때 결정 |
| 2026-10-07 | 001 FR-019·024 | 예약어 목록은 설정값, 서비스 이름이 정해지면 추가 (2026-10-10 `baselog` 추가) |
| 2026-10-07 | 001 FR-033 | 같은 이메일 계정이 있으면 안내 + [기존 계정으로 로그인]·[새 계정 만들기] |
| 2026-10-07 | 공통 오류 본문 | `errors`는 항상 배열(없으면 `[]`), 메시지 끝 마침표 없음. 404는 모든 경우 `{code:"NOT_FOUND", message:"볼 수 없는 페이지예요", errors:[], details:null}` (ANALYSIS R1) |
| 2026-10-07 | 006 FR-004 | 탭 옆 글 수 표시, 검색·일괄 처리는 범위 밖 |
| 2026-10-08 | 공통 429 (003·007·008·009·010) | 요청 빈도 제한은 429 `TOO_MANY_REQUESTS` "잠시 후 다시 시도해 주세요" 하나. 하루 한도·용량처럼 뜻이 다른 한도만 별도 코드(003 `DAILY_UPLOAD_LIMIT`·`STORAGE_QUOTA_EXCEEDED`, 013 `AI_DAILY_LIMIT`). 002 `RATE_LIMITED`는 바꾼다(007 T058). ANALYSIS-tier-a R4 해결 (민서 확정 2026-10-08) |
| 2026-10-08 | 공통 판정 순서 (007 Q2) | 로그인 → 계정 상태 → 볼 수 있나 → 형식·업무 규칙 → 요청 횟수(429는 맨 끝). 잘못된 요청은 횟수에 세지 않는다 (민서 확정 2026-10-08) |
| 2026-10-08 | 012 FR-040 | sitemap은 012가 맡고 요청마다 공용 노출 조건으로 만든다(캐시 없음). ANALYSIS-tier-a R9 해결 (민서 확정 2026-10-08) |
| 2026-10-08 | 003 FR-025 | 친구 공개 글의 사진도 비공개 글 사진과 같이 "주소를 알면 보임", 서명된 주소 없음 (민서 확정 2026-10-08) |
| 2026-10-08 | 003 FR-026~028 | 구현 전에 운영 저장소 점검 11가지로 업로드 방식 확정. 그 전까지 직접 업로드가 기본안, 서버 경유가 대체안 (민서 확정 2026-10-08) |
| 2026-10-08 | 007 FR-016 | 첫 댓글 20개는 화면이 댓글 API로 따로 받는다. 서버는 상세 HTML·응답에 넣지 않는다 (민서 확정 2026-10-08) |
| 2026-10-08 | 007 FR-022 | 014 전까지 댓글·글의 [신고] 버튼은 숨긴다 (민서 확정 2026-10-08) |
| 2026-10-08 | 008 FR-029 | 전체 태그 목록은 저장하지 않고 요청마다 계산(1만 건 300ms 측정) (민서 확정 2026-10-08) |
| 2026-10-08 | 009 FR-031 | 관리자 조회는 조회수에 세지 않는다(204, 기록 없음) (민서 확정 2026-10-08) |
| 2026-10-08 | 009 FR-034 | "IP를 남기지 않는다"는 조회수 기록에만 해당. 방문자 쿠키 안내는 첫 공개 전 처리방침 첫 판에 (민서 확정 2026-10-08) |
| 2026-10-08 | 010 FR-027 | 팔로우·언팔로우는 회원당 1분 30번(설정값), Redis 장애 때 통과 (민서 확정 2026-10-08) |
| 2026-10-08 | 011 FR-024 | 숨김 알림은 숨겨진 글 상세(댓글이면 그 댓글 위치)로 보낸다. 이동할 곳이 없는 알림은 읽음으로만 바꾼다 (민서 확정 2026-10-08) |
| 2026-10-08 | 012 FR-005 | 트렌딩 댓글 작성자 수에서 숨긴 댓글은 뺀다 (민서 확정 2026-10-08) |
| 2026-10-08 | 013 FR-009·FR-030 | AI 동의 문구가 바뀌면 AI 버튼을 누를 때 다시 묻는다. 비공개·친구 공개 글은 외부 AI로 보내지 않고 자체 AI만 (민서 확정 2026-10-08) |
| 2026-10-08 | 014 FR-019·FR-020 | 관리자는 신고 없이도 직접 숨길 수 있고 사건 기록을 남긴다. 다른 회원 신고가 하나라도 있으면 자기 신고가 섞인 사건도 처리할 수 있다 (민서 확정 2026-10-08) |
| 2026-10-08 | 015 FR-008 | 정지 중에는 탈퇴할 수 없고, 영구 정지 1년 뒤 자동 정리(설정값). 기한이 지난 복구는 거부. 탈퇴 비밀번호 실패는 비밀번호 변경과 합산 (민서 확정 2026-10-08) |
| 2026-10-08 | 016 FR-001·FR-011·US4 | 다크 모드는 규격만 공통, 구현은 원하는 서비스만(나민서 MUST). 색 토큰 이름 11개는 세 사람 공통(값은 각자), Tier A `--card-*`는 이 이름으로 바꾼다. 스크립트 꺼짐 기준은 삭제 (민서 확정 2026-10-08) |

## 팀이 정해야 할 것

spec의 `[NEEDS CLARIFICATION]`은 모두 위 "정해진 것"으로 확정되었습니다(0개). 남은 것은 plan이 원문에 없는 내용을 제안했거나 운영 확인이 필요한 항목이며, 각 기능 tasks의 확인 작업으로 남아 있습니다. 자세한 근거는 [specs/ANALYSIS-tier-bc.md](specs/ANALYSIS-tier-bc.md) §6.

| 항목 | 질문 | 확인 작업 |
|---|---|---|
| Redis OOM 응답 | 002 `RedisGuard`가 자동 저장 밖에서는 공통 503 `TEMPORARILY_UNAVAILABLE`을 던지게 바꿀까 | 003 T095, 007 T057, 009 T044 |
| 헌법 II 모듈 목록 | `moderation`(014)·`notification`(011)을 모듈 목록에 더할까 | 011 T003, 014 T003 |
| 키 이름 규칙 | 요청 제한 Redis 키를 `ratelimit:`로 통일(001 `rl:` 옮김), 설정 키 `blog.<기능>` 확정 (Tier A R10) | — |
| 원문에 없는 제안 | 010 모듈·`ACCOUNT_WRITE`·문구, 012 새 코드 2개·`snippet {text, marks}`, 013 `details.reason`·상태·동의 조회 API, 014 새 코드 7개·칸 오류·직접 숨김 위치, 015 `confirmed`·코드 2개·잠금 코드 | 010 T003, 012 T003, 013 T003, 014 T004, 015 T002 |
| 답글의 답글 알림 | "내 답글에 다시 단 답글"을 최상위 작성자에게 알릴까 | 011 T004 |
| 신고·정지 기본안 | 정지 중 세션 삭제 실패 시 503, 자리 댓글의 대기 신고도 "대상 없음" | 014 T005 |
| 처리방침 | 방문자 쿠키·외부 AI 전송·영구 정지 자동 정리 문단을 첫 공개 전 첫 판에 함께 | 009 T002, 013 T004, 015 T003 |
| 운영 환경 | NHN 저장소 점검 11가지, Ollama 메모리·Gemini 설정, 008보다 운영 배포가 먼저인지 | 003 T002, 013 T005, 008 T002 |
| 탈퇴 정리 필수 단계 | 운영 기본값을 머지된 단계만으로 | 015 T003 |
| 다크 모드 규격 | 입력칸 테두리 토큰, 보조 토큰 비공통, 브랜드 값, 코드 강조 4색, `@axe-core/playwright`, `VITE_DARK_MODE` 기본값 | 016 T003·T004 |

원문끼리 충돌한 부분은 더 최근 결정(2026-10-07 회의 > 10-06 > 이전)과 51·02를 따랐고, 각 스펙의 Assumptions와 체크리스트 Notes에 기록했습니다.

## Tier A plan에서 정한 공통 설계 (팀 확인 필요)

각 plan의 `research.md`에 "제안(팀 확인 필요)"로 표시된 항목 중 여러 기능에 걸치는 것만 모았습니다.

- 공개 범위 변경은 `PUT /api/posts/{postId}/visibility` (06 §4의 PATCH 대신 O8 규약, 004 R-20)
- CSRF: `XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더, 첫 진입 때 `GET /api/auth/csrf` (M17 기본안, 001)
- 세션: Spring Session 인덱스 저장소로 "회원의 모든 세션 삭제" 지원, Redis 장애 시 비로그인 처리 (001·004)
- 커서: 불투명 Base64URL 안에 목록 구분 필드를 넣어 다른 목록의 커서를 400으로 거부 (005 R-24, 006 R16)
- 배치 잠금: ShedLock JDBC + `shedlock` 테이블 추가 제안, 정리 배치는 03:30 KST (002·006)
- 완전 삭제 확장점 `PostPurgeStep`: 신고·사진 모듈이 각자 구현 (006 R10, 015의 `WithdrawalPurgeStep`과 같은 방식)
- 자동·수동 저장은 Redis 키가 있어도 DB에서 작성자·휴지통 여부를 확인 (04 §2-3 보완, 002·006)
- 상세 API `GET /api/posts/{postId}`와 블로그 머리말 API `GET /api/members/{handle}` 추가 (원문에 없음, 005)

## Tier B/C plan에서 정한 공통 설계 (팀 확인 필요)

각 plan의 `research.md`에 "제안"으로 표시된 것 중 여러 기능에 걸치는 것만 모았습니다. 기능 사이 규칙 전체는 [ANALYSIS-tier-bc.md](specs/ANALYSIS-tier-bc.md) §4.

- 칸 규칙 위반은 400 `VALIDATION_FAILED` + `errors[].code`(001 `AGREEMENT_VERSION_MISMATCH` 방식): 014 `REPORT_DETAIL_REQUIRED`, 013 동의 버전 불일치
- 상태 지정은 `PUT`/`DELETE`: 좋아요(009), 팔로우(010), 알림 읽음(011, 원문 `PATCH` 대신), AI 동의(013), 숨김(014)
- 이벤트: `shared.event` record, `AFTER_COMMIT` + `@Async("eventExecutor")`, 필드는 ID·enum·`Instant`만. 012는 구독하지 않고 요청 때 공용 조건으로 거른다
- 두 기능이 같은 파일을 쓰면 "먼저 하는 쪽이 만든다"(`PostCounterService`, `InvisibleCharacters`, `CardFilter`, `WithdrawalPurgeStep`, 신고 이벤트 record, `reasonLabels.ts`)
- 탈퇴 정리 단계 order 10~90(015 contracts/purge-steps.md), 배치 시각 03:00·03:30·04:10·04:20·04:30·04:45 KST(`blog.time-zone`)
- 새 테이블·컬럼 없음. V3 이후 마이그레이션은 머지 순서대로 다음 번호(조건부: 008 T075, 012 T046)
- 화면 색은 토큰만(016 `noRawColors.test.ts`가 007~015 새 화면도 검사)

## 알려진 누락

- 원문이 참조하는 `erd/V1__common_schema.sql`, `erd/erdcloud-export.sql`은 아직 이 저장소에 없습니다.

## 다음 단계

```bash
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v1.1.1   # 처음 한 번
```

Tier A(001·002·004·005·006)는 tasks와 analyze까지 끝났습니다(작업 504개, FR 커버리지 97.5%). 교차 분석 결과와 팀 결정이 필요한 8가지는 [specs/ANALYSIS-tier-a.md](specs/ANALYSIS-tier-a.md)에 있습니다. 구현 순서는 001 Phase 1·2(공통 기반) → 004·002 Foundational → 각 기능의 P1 스토리입니다.

Tier B/C(003·007~016)도 clarify·plan·tasks·analyze까지 끝났습니다(작업 675개, FR 395개 커버리지 100%). 교차 분석과 팀 결정 10가지는 [specs/ANALYSIS-tier-bc.md](specs/ANALYSIS-tier-bc.md)에 있습니다. 권장 구현 순서는 003·008 → 007·009 → 010·015 → 011·014 → 012·013이고, 016 공통 묶음(색 토큰 이름 바꾸기)은 일찍 할수록 충돌이 적습니다. 새 기능을 더할 때는:

1. `/speckit-clarify` — 위 질문을 정리해 spec에 반영
2. `/speckit-plan` — constitution 검사, data-model·API 계약 작성
3. `/speckit-tasks` → `/speckit-analyze`
4. `/speckit-implement` → `/speckit-converge` ("Converged"까지 반복)
