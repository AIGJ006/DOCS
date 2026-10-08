# Specification Quality Checklist: 글 좋아요·조회수

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
- NEEDS CLARIFICATION 1개 남음: FR-031(및 Edge Cases 마지막 항목) — 관리자 조회를 조회수에서 제외할지. 40 R-8·42가 31에 요청했지만 31 W-3에 반영되지 않았다.
- 구현 용어(테이블·API 경로·Redis 키·이벤트 이름·설정 키)는 브리프 지침대로 마지막 `Implementation Notes (for /speckit-plan)` 절에만 두었다. 본문의 "401/403/404/429" 같은 응답 종류는 원문 결정(404 정책·계정 상태 403)을 옮긴 관찰 가능한 결과라 남겼다.
- 원문 충돌과 처리:
  - 자기 글 좋아요 응답: 30 K-1·§3(2026-10-05)은 403, 42 P-9(2026-10-06 수정)는 400 `CANNOT_LIKE_OWN_POST` → 더 최근인 42를 따라 400 (FR-009).
  - 판정 순서: 30 §2는 "읽을 수 있나(404)"를 이메일 인증·자기 글보다 먼저, 42 §3(2026-10-06)은 계정 상태(403)를 먼저 → 42를 따름 (FR-010). 그래서 인증 전 회원이 볼 수 없는 글에 누르면 404가 아니라 403이다.
  - 비회원·인증 전 회원의 버튼 표시: 42 §11·40 #6은 "회원에게만", 01 결정 H6(2026-10-07)은 30 K-2 채택으로 "모두에게 보이고 누르면 안내" → H6을 따름 (FR-013).
  - 이벤트 필드: 30 §6 `PostLiked{postId, likerId, authorId, likedAt}`/`PostUnliked{postId, likerId}` vs 20 §3-3(2026-10-06) `PostLiked(postId, postAuthorId, memberId, likedAt)`/`PostUnliked(postId, postAuthorId, memberId, unlikedAt)` → 20을 따름 (Implementation Notes).
  - SSR 대체 경로(30 §3 폼 POST, 31 "JS 없는 독자"): 01 H7(2026-10-07)로 삭제 → Assumptions에 기록.
  - 01 H8은 "31 §10-3 약속은 '비회원 기준'으로"라고 적었으나 31 §10에는 하위 절이 없다. 완료 기준 3번("Redis가 멈춰도 글 상세는 정상")을 가리키는 것으로 해석했다 (FR-029).
