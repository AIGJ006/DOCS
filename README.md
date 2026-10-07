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

| # | 스펙 | 원문 | 단계 |
|---|---|---|---|
| 001 | [account-auth](specs/001-account-auth/spec.md) 로그인·블로그 주소·닉네임·프로필·친구·최근 활동 | 07, 08, 09, 11 | Tier A |
| 002 | [post-authoring](specs/002-post-authoring/spec.md) 글 작성·자동 저장·발행·본문 정화 | 04, 05, 12 | Tier A |
| 003 | [image-upload](specs/003-image-upload/spec.md) 이미지 업로드 | 04, 23 | Tier B |
| 004 | [visibility-permission](specs/004-visibility-permission/spec.md) 공개 범위·권한 매트릭스 | 06, 42 | Tier A |
| 005 | [post-reading](specs/005-post-reading/spec.md) 전체 글 목록·개인 블로그·글 상세 | 10, 40 | Tier A |
| 006 | [manage-delete](specs/006-manage-delete/spec.md) 내 글 관리·삭제·휴지통 | 41, 13 | Tier A |
| 007 | [comment](specs/007-comment/spec.md) 댓글·답글 | 21 | Tier B |
| 008 | [tag](specs/008-tag/spec.md) 태그·태그별 글 목록 | 22 | Tier B |
| 009 | [like-view](specs/009-like-view/spec.md) 좋아요·조회수 | 30, 31 | Tier B |
| 010 | [follow-feed](specs/010-follow-feed/spec.md) 팔로우·피드 | 24 | Tier C |
| 011 | [notification](specs/011-notification/spec.md) 도메인 이벤트·인앱 알림 | 20, 25 | Tier C |
| 012 | [trending-search](specs/012-trending-search/spec.md) 트렌딩·검색 | 32, 33 | Tier C |
| 013 | [ai-tag-suggest](specs/013-ai-tag-suggest/spec.md) AI 태그 추천 | 34 | Tier C |
| 014 | [report-hide](specs/014-report-hide/spec.md) 신고·관리자 숨김 | 43 | Tier C |
| 015 | [withdraw](specs/015-withdraw/spec.md) 회원 탈퇴·복구 | 44, 13 | Tier C |
| 016 | [dark-mode](specs/016-dark-mode/spec.md) 다크 모드 | 45 | Tier C |

ERD(03, 51)는 각 스펙의 Implementation Notes에서 참조하며, `/speckit-plan` 단계의 `data-model.md`로 옮겨 갑니다. 카테고리·주제 등은 공통이 아닌 개인 확장(01 §2-4)이라 공통 스펙에 없습니다.

## 정해진 것

| 날짜 | 스펙 | 결정 |
|---|---|---|
| 2026-10-07 | 004 FR-037, 009 FR-009·010 | 자기 글 좋아요는 400 `CANNOT_LIKE_OWN_POST`. 판정 순서는 로그인 → 계정 상태 → 볼 수 있나 → 자기 글 → 요청 횟수 (42 §3) |
| 2026-10-07 | 001 FR-009 | 운영 메일은 설정값으로 바꿀 수 있는 SMTP, 운영 계정은 배포 때 결정 |
| 2026-10-07 | 001 FR-019·024 | 예약어 목록은 설정값, 서비스 이름이 정해지면 추가 |
| 2026-10-07 | 001 FR-033 | 같은 이메일 계정이 있으면 안내 + [기존 계정으로 로그인]·[새 계정 만들기] |
| 2026-10-07 | 006 FR-004 | 탭 옆 글 수 표시, 검색·일괄 처리는 범위 밖 |

## 팀이 정해야 할 것 (`[NEEDS CLARIFICATION]`)

| 스펙 | 질문 |
|---|---|
| 003 FR-025 | 친구 공개 글의 사진 접근 방식 |
| 007 FR-016 | 첫 댓글 20개를 상세 HTML에 서버가 넣을지 (H7 이후) |
| 008 FR-029 | 전체 태그 목록 10분 재계산 지연 허용 여부 |
| 009 FR-031 | 관리자 조회를 조회수에서 뺄지 |
| 010 FR-027 | 팔로우 요청에 공통 IP 요청 제한 적용 여부 |
| 011 FR-024 | 숨김 알림 클릭 시 이동 위치 (글 상세 vs 내 글 관리) |
| 012 FR-005 | 트렌딩 댓글 작성자 수에서 숨긴 댓글 제외 여부 |
| 013 FR-009 | AI 동의 문구 변경 시 재동의 시점 (로그인 vs AI 사용 시) |
| 013 FR-030 | 비공개·친구 공개 글을 외부 AI로 보낼지 (34 F-1) |
| 014 FR-020 | 신고 없이 관리자가 직접 숨길 수 있는지 |
| 015 FR-008 | 정지 회원 탈퇴 경로·영구 정지 계정 정보 보유 기간 (M11) |
| 016 FR-001 | 다크 모드 공통 구현 vs 선택 구현 |
| 016 FR-011 | 색 토큰 이름 공통 규격 여부 |
| 016 US4 | JS 꺼짐 시 기기 테마 유지 규칙 유지 여부 |

원문끼리 충돌한 부분은 더 최근 결정(2026-10-07 회의 > 10-06 > 이전)과 51·02를 따랐고, 각 스펙의 Assumptions와 체크리스트 Notes에 기록했습니다.

## 알려진 누락

- 원문이 참조하는 `erd/V1__common_schema.sql`, `erd/erdcloud-export.sql`은 아직 이 저장소에 없습니다.

## 다음 단계

```bash
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v1.1.1   # 처음 한 번
```

Claude Code에서 기능 하나를 골라 진행합니다 (Tier A부터 권장):

1. `/speckit-clarify` — 위 질문을 정리해 spec에 반영
2. `/speckit-plan` — constitution 검사, data-model·API 계약 작성
3. `/speckit-tasks` → `/speckit-analyze`
4. `/speckit-implement` → `/speckit-converge` ("Converged"까지 반복)
