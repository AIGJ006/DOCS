# Specification Quality Checklist: 회원 탈퇴·복구 (Account Withdrawal & Restore)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-07
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain
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

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- **NEEDS CLARIFICATION 1개 남음** (FR-008): 정지 중 회원의 탈퇴 요청 경로와 영구 정지 계정의 개인정보 보유 기간 — 13 §3-2가 "미정 (검증 M11, 43 §6과 함께 정한다)"으로 남김.
- 원문에 규정이 없어 Assumptions로 둔 것: 복구 기한이 지났지만 정리 작업 전인 짧은 사이의 로그인(유예 상태 규칙을 그대로 적용), 관리자 권한 해제 방법(범위 밖).
- 30일 정리 단계의 구현 단위(정리 단계 인터페이스·order 값·배치·SQL)는 Implementation Notes에만 두었다. FR-025는 사용자·데이터 관점의 순서와 결과만 적었다.
- 원문 간 충돌·정리 (2026-10-07 결정·51 우선):
  - 44 §4 order 90 "`ai_consent_at`·`suspended_until`·`suspended_reason` null" ↔ 13 §3-3 7·07 §3-1·51 E2/E3/E6: 동의·정지는 별도 기록으로 옮겨졌고 익명 처리 때 **남긴다** → 13 기준으로 FR-028.
  - 44 §4 order 40 "올린 사진 `detached_at` 기록(정리 배치가 7일 뒤 삭제)" ↔ 2026-10-07 "탈퇴 회원 사진": `now() - 7일`로 기록해 다음 정리 배치에서 삭제 → 13 기준.
  - 44 §4 order 80 "대기 신고 `CLOSED_NO_TARGET`" ↔ 13 §3-3 6-3: 신고 분리(E1)로 `report_case` 기준 + 내가 쓴 신고 `detail` 비움 추가 → 13 기준.
  - 44 §4 order 70 ↔ 25 §8: 25가 "내가 행동한 하나짜리 알림 삭제(③)"를 44에 추가 요청, 13 §3-3 6-2는 25 §8을 참조 → ③ 포함.
  - 44 §3 "SSR 302 / REST 403" ↔ 2026-10-07 회의 H7(React + REST, SSR 대체 규칙 삭제) → REST 403 + 화면 이동으로 해석.
  - 13 §3-3 6번은 원래 "(친구 공개 적용자)"였으나 2026-10-07 회의 M1로 항상 실행 → FR-025 6.
