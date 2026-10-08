# Specification Quality Checklist: 도메인 이벤트·인앱 알림

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-07
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **2026-10-08 clarify 반영**: NEEDS CLARIFICATION 표시를 모두 spec Clarifications(Session 2026-10-08, 민서 확정)의 답으로 고쳤다. 아래 "남음" 기록은 clarify 전 상태다.
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- NEEDS CLARIFICATION 1개 남음: FR-024 — 숨김 알림(`CONTENT_HIDDEN`)을 누르면 글 상세(25 §2·§12 "통일 제안")로 갈지, 내 글 관리 화면의 해당 글(41 "다른 담당자와 맞출 것")로 갈지.
- 20의 이벤트 원칙은 본문에서 "사건"이라는 업무 용어로 옮겼고, 이벤트 이름·필드·리스너·스레드 풀 설정은 마지막 `Implementation Notes (for /speckit-plan)` 절에만 두었다. FR-003의 "대기열 1,000건·20초"는 원문 결정 수치를 관찰 가능한 동작으로 옮긴 것이다.
- 원문 충돌과 처리:
  - 탈퇴 30일 알림 정리 범위: 13 §3-3 6-2와 44 §4 order 70은 "받은 알림 삭제 + 묶음에서 빼고 다시 계산"까지만 적었고, 25 §8(2026-10-06)·§12와 20 §10은 "내가 행동한 하나짜리 알림 삭제"(③)를 더해 달라고 요청했다 → 알림 담당 문서와 20("받은 것·보낸 것")을 따라 ③을 포함했다 (FR-039). 13·44 쪽 반영 여부는 → specs/015-withdraw에서 확인 필요.
  - 이벤트 필드: 30 §6의 `PostLiked{postId, likerId, authorId, likedAt}`·`PostUnliked{postId, likerId}`와 20 §3-3(2026-10-06)이 다르다 → 20을 따름. 43 §7의 `MemberSuspended(memberId, until, reason)`은 20(2026-10-06, 검증 L12)이 `reason` 대신 `suspendedAt`으로 정함 → 20을 따름.
  - 25 §5 "SSR: 배지 함께 그리기"는 01 H7(2026-10-07)로 대체 → Assumptions에 기록.
  - 25 §10 SQL 주석 "확정되면 FK 추가"류 문장은 51·회의 O2로 `fk_notification_report`(SET NULL) 반영 완료 → Implementation Notes에 51 기준으로 적었다.
