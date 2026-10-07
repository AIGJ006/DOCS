# Specification Quality Checklist: 내 글 관리와 글 삭제·휴지통

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
- **NEEDS CLARIFICATION 1개 남음** (FR-004): 41 문서가 "초안 (항목 확인 중)"이고 결정 기록에 M-5~M-8만 있어, 탭 옆 글 수(M-9)와 검색·일괄 처리 제외(M-10)가 팀 확정인지 확인 필요. `/speckit-clarify`에서 해소한다.
- 구현 세부: 본문에는 프레임워크·테이블·API 경로가 없다. 원문의 기술 결정(API, 인덱스, 커서, 완전 삭제 SQL 순서, 배치, 이벤트)은 팀 지침에 따라 마지막 `## Implementation Notes (for /speckit-plan)` 절에만 모았다. 사용자가 보는 화면 주소(`/manage/posts`)와 응답 결과(404 등), 이유 코드(`ACCOUNT_WITHDRAWN`)는 관찰 가능한 결과라 본문에 남겼다.
- 공통 완료 기준 반영: C-POST-5 #1~#5 → US1·US2·US4, FR-017~FR-035 / C-MANAGE-1 #1~#8 → US3·US5, FR-001~FR-016 / C-OWN-1 → FR-017·FR-028·SC-004.
- 원문 간 차이: 휴지통 목록 조회 경로가 41 §5와 13 §2-4·02 §5-1에 다르게 적혀 있음(Implementation Notes에 기록). 41 §4의 JS 없을 때 폼 전송 규칙은 2026-10-07 H7로 제외. 공개 범위 변경의 PATCH 방식과 O8 "상태 지정은 PUT·DELETE" 규약의 관계는 004 plan 소관.
- 13의 회원 탈퇴 데이터 처리(§3)는 → specs/015-withdraw 담당이라 이 spec에는 "탈퇴 정리 때 글 완전 삭제 규칙을 재사용"만 적었다.
