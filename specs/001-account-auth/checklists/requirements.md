# Specification Quality Checklist: 계정·인증 (가입·로그인·블로그 주소·닉네임·프로필·친구·최근 활동)

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

- **NEEDS CLARIFICATION 해결 (2026-10-07 clarify)**: FR-009 운영 메일은 설정값 SMTP, FR-019·024 예약어 목록은 설정값, FR-033 같은 이메일 계정 안내. spec의 Clarifications 절 참고.
- 구현 세부 판단: 프레임워크·저장소·API 경로·오류 이유 코드·테이블 이름은 `Implementation Notes (for /speckit-plan)`에만 두었다. 본문에 남긴 HTTP 상태 번호(401·403·404·409·301)와 NFC 정규화·WCAG 같은 표준 이름은 팀 공통 요구사항(C-READ-2 "404", 42 응답 코드 표)이 관찰 가능한 결과로 정의한 것이라 요구사항으로 유지했다.
- 원문 간 충돌(Assumptions 마지막 항목에 처리 기준 기록):
  - 42 P-5·P-7·§4(2026-10-03) "정지는 로그인 단계에서만 막힘" ↔ 2026-10-07 H7 "남은 세션의 쓰기 403 `ACCOUNT_SUSPENDED`" → 최근 결정(H7) 채택, FR-038.
  - 51 §4 "마지막 로그인을 보여 준다" ↔ 01 C-ACT-1·2026-10-07 "직전 로그인 표시"·07 §6·11 §6-4 "갱신 전 값(직전 로그인)" → 직전 로그인 채택, FR-057·FR-058.
  - 20 §3-7 친구 요청·수락 이벤트를 "친구 공개 규격 적용자만"으로 둠 ↔ 2026-10-07 M1 "친구 맺기는 공통, 친구 알림만 선택" → M1 채택(알림 쪽은 specs/011-notification).
  - 42 P-12·§3·§4의 "SSR 화면(302·로그인 화면 이동)" 문구 ↔ 2026-10-07 Q2·H7 React + REST → REST 응답 + 화면 안내로 해석.
- 원문 누락(기본값으로 처리, Assumptions 기록): 친구 요청에 이메일 인증이 필요한지와 친구 요청 횟수 제한(42 권한 매트릭스에 친구 항목 없음) → 팔로우와 같이 로그인만 요구·제한 없음으로 가정. 최근 활동 "오늘/어제"의 기준 시간대 → 한국 시간으로 가정.
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
