# Specification Analysis Report: 017-category

**Date**: 2026-10-08 | **Artifacts**: spec.md, plan.md, research.md, data-model.md, contracts/openapi.yaml, tasks.md | **Constitution**: 1.0.0

## Findings

| ID | Category | Severity | Location(s) | Summary | Recommendation / 처리 |
|----|----------|----------|-------------|---------|----------------|
| A1 | Coverage | MEDIUM | spec FR-013·FR-014 | 하위가 있는 카테고리 [삭제] 안내 문구가 화면 테스트에 없음 | T018에 "하위 있는 삭제 문구" 포함 — 처리됨 |
| A2 | Consistency | LOW | spec Edge Cases "자기 자신을 상위로" ↔ contracts PATCH 400 | 자기 자신을 상위로 고르면 어떤 코드인지 | `CATEGORY_DEPTH_EXCEEDED`로 통일(spec Edge Cases와 같음) — T010에서 확인 |
| A3 | Underspec | LOW | FR-030 ↔ R7 | `tag`와 `category`를 함께 보내면? | 서버는 둘 다 적용(AND), 화면은 함께 보내지 않음(FR-033) — research R7에 기록 |
| A4 | Constitution | — | plan Complexity Tracking | 원칙 II 읽기 예외 1건(카테고리별 공개 글 수 SQL) | 008 태그 글 수와 같은 종류. 조건 문구는 `VisibilityFilter` 한 곳 — 허용 |
| A5 | Coverage | MEDIUM | SC-005 (1만 건 300ms) | 성능 측정 작업 없음 | `ix_post_category` 부분 인덱스로 블로그 목록과 같은 접근 경로. 측정은 3단계 "전체 점검(느린 쿼리)"에서 한꺼번에 — 남김 |
| A6 | Inconsistency | LOW | data-model `name_key varchar(60)` ↔ spec 30자 | 키가 이름보다 길 수 있음 | 소문자화로 코드 포인트가 늘어나는 문자(예: `İ`) 때문 — 의도된 차이 |
| A7 | Cross-feature | MEDIUM | `PostDetailView`, `CardFilter`, `BlogPage.tsx`, `EditorPage.tsx` | 011~016 스레드가 같은 파일을 고칠 수 있음 | T046: 머지 직전 최신 main을 합치고 양쪽 기능을 살려 충돌 해결, Flyway V 번호 재확인 |

## Coverage Summary

| Requirement | Has Task? | Task IDs |
|---|---|---|
| FR-001~006 | ✓ | T001, T005~T007, T011 |
| FR-007~015 | ✓ | T010~T018 |
| FR-020~023 | ✓ | T020~T025 |
| FR-028~034 | ✓ | T030~T036 |
| FR-040~041 | ✓ | T040~T043 |
| FR-050 | ✓ | T044~T045 |
| SC-001~004, 006 | ✓ | T010, T020, T030, T016·T036(375px CSS) |
| SC-005 | 부분 | A5 |

## Metrics

- Functional Requirements: 31 · Success Criteria: 6 · Tasks: 38
- Requirement coverage: 100% (FR), SC 5/6
- Ambiguity: 0 · Duplication: 0 · CRITICAL: 0

## Next Actions

CRITICAL·HIGH 없음 → `/speckit-implement` 진행.
