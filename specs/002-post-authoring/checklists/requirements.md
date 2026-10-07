# Specification Quality Checklist: 글 작성·임시저장·발행 (자동 저장, 발행·수정, 본문 정화)

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

- NEEDS CLARIFICATION 없음: 04·05·12에서 이 기능 범위의 미결정 사항은 없었다. 남은 미결(비공개 글 사진 서명 URL 재검토, NHN MinIO 설정 확인)은 specs/003-image-upload, 저장소의 보존 데이터·캐시 분리(M30)는 아키텍처 정리 소관이라 Assumptions에만 적었다.
- 구현 세부 판단: Redis·IndexedDB·Lua·API 경로·멱등 키 헤더·라이브러리 버전·CSP 헤더 문자열·테이블 이름은 `Implementation Notes (for /speckit-plan)`에만 두었다. 본문의 HTTP 상태 번호(400·404·409·413·422·429), Markdown·CommonMark+GFM(사용자가 쓰는 문법), 링크 `rel` 값(`noopener noreferrer nofollow ugc`, 팀 공통 완료 기준 C-POST-1 문구)은 관찰 가능한 요구사항으로 유지했다.
- 원문 간 충돌·차이(Assumptions 마지막 항목에 처리 기준 기록):
  - 05 §4(2026-10-02) 태그 규칙 "소문자·앞뒤 공백·1~30자" ↔ 22(2026-10-04·10-06) 정규화 규칙(NFKC·띄어쓰기→하이픈·허용 문자 외 거부·금칙어) → 최근 문서(22) 기준, specs/008-tag로 위임.
  - 01 결정 기록 빈 임시글 정리 "만든 지 24시간 + 빈 글" ↔ 04 §2-5 "마지막 수정 후 24시간 + 서버 보관분 없음" 추가 조건 → 충돌이 아닌 구체화로 보고 04 채택(FR-050).
  - 20 §3-1은 `PostWentPublic`을 "같은 트랜잭션에서 함께 발행"으로, 05 §7은 이벤트를 "커밋 후"로 적었다 → 발행은 트랜잭션 안, 후속 처리는 커밋 후(02 §1 이벤트 원칙)로 같은 뜻이라 FR-039에 "발행이 확정된 뒤 알림"으로 기술.
- 원문 누락(기본값으로 처리): 자동 저장 단계의 제목·본문 길이 검사(저장 컬럼 제한 100자·100,000자를 적용한다고 가정), 미리보기의 이메일 인증 필요 여부(로그인만 요구로 가정).
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
