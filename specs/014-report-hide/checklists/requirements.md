# Specification Quality Checklist: 신고·관리자 숨김·회원 정지 (Report, Hide & Suspension)

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
- **NEEDS CLARIFICATION 1개 남음** (FR-020): 신고 없이 관리자가 직접 숨길 수 있는지(42 §5-2·§6·§11은 관리자 [숨김] 행동·버튼을 두지만 43은 신고 처리 화면에서만 정의).
- 관련 미결이 다른 spec에 있음: 정지 중 탈퇴 경로·영구 정지 계정 개인정보 보유 기간(13 §3-2 "미정", → specs/015-withdraw), 트렌딩에서 숨긴 댓글 제외(→ specs/012-trending-search).
- 응답 코드(401/403/404/400)는 원문의 권한 정책(42 §4) 자체가 요구사항이라 FR에 남겼다. 저장 구조·API 경로·이벤트 이름·배치는 Implementation Notes에만 두었다.
- 원문 간 충돌·정리 (2026-10-07 회의·51 우선):
  - 43 단일 `report` 표·`UQ(reporter_id, target_type, target_id)` → `report_case` + `report`(E1), 대기 사건 하나 + 처리 뒤 재신고는 새 사건(E4). 42 §8의 "같은 대상 재신고 200"은 대기 사건 기준으로 해석.
  - 43 `member.suspended_until`·`suspended_reason` → `member_suspension` 이력(E3).
  - 43 §5 "CLOSED_NO_TARGET 사건의 스냅샷은 30일 뒤 삭제" → 13 §2-5(H5): 처리된 모든 사건을 `handled_at` 30일 뒤 비움, `report.detail`도 함께. `report.closed_at` 삭제.
  - 43 §7 `MemberSuspended(memberId, until, reason)` → 20 §3-5 `(memberId, until, suspendedAt)`(EV-3).
  - 42 §11 "[신고] 회원·관리자에게만 보임" → 2026-10-07 회의 H6: 비회원·인증 전에도 보이고 누르면 안내.
  - 43 §2 스냅샷의 "블로그 주소·공개 범위"는 51 `report_case`에 칸이 없음 → Assumptions에 기록.
  - 42 P-12 "복구 화면으로 이동(SSR)"의 SSR 표현은 H7로 삭제 대상(이 spec에는 영향 없음).
