# Specification Quality Checklist: AI 태그 추천 (AI Tag Suggestion)

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
- **NEEDS CLARIFICATION 2개 남음** (영향 큰 순):
  1. FR-030 — 비공개·친구 공개 글을 외부 AI로 보낼지 (34 후속 제안 F-1 "미결").
  2. FR-009 — AI 동의 문구 버전이 바뀌었을 때 재동의 시점: 로그인 때 막는 재동의(51·07 §3-1, H9) vs AI를 쓸 때(34 §7-2).
- "Google Gemini(무료 등급)"는 구현 선택이 아니라 동의 창에서 사용자에게 공개해야 하는 전송 대상이라 본문(FR-007)에 남겼다. 공급자 구현·모델·저장소 키·API·오류 코드는 Implementation Notes에만 두었다.
- 원문 간 충돌·정리:
  - 34 §7·ERD의 `member.ai_consent_at` → 2026-10-07 회의 E2·H9와 51에서 `member_agreement`(type `AI`, `version`)로 대체. 51·07을 따름.
  - 34 §9의 "403: 남의 글" → 02 §5·42 P-5/§4(403은 계정 상태만, 남의 것은 404)를 따름 (02 우선 규칙).
  - 44 §4 order 90의 "`ai_consent_at` null"은 13 §3-3 7번·07 §3-1(2026-10-07, 동의 이력은 익명 처리 때도 유지)과 충돌 → specs/015-withdraw에서 13·07 기준으로 처리.
  - Redis 장애 시 AI 503은 02 §2-1(H8)에서 확정되어 FR·Edge Cases에 반영.
