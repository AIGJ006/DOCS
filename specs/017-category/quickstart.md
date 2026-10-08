# Quickstart: 017-category 검증 시나리오

**Feature**: `017-category` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내와 구현 기록이다. 규칙은 [spec.md](./spec.md)·[contracts/openapi.yaml](./contracts/openapi.yaml), 테이블은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(계정 상태 가드), 004(`VisibilityFilter`), 005(블로그 목록·글 상세), 006(내 글 관리), 008(블로그 태그 줄), 015(탈퇴 정리 단계), 016(색 토큰)

## 1. 기동

```bash
git pull
docker compose up -d --build
```

- Flyway 로그에 `V3__category`가 적용된다 (`category` 테이블, `post.category_id`)

## 2. 자동 테스트

```bash
(cd backend && ./mvnw verify)                              # 카테고리 IT 6개 포함 전체
(cd frontend && npx vitest run src/features/category src/pages/__tests__/ManageCategoriesPage.test.tsx)
```

| 테스트 | 확인하는 것 |
|---|---|
| `CategoryNameTest` | NFC·공백 정리, 1~30자, 대소문자 무시 키 |
| `MyCategoryApiIT` | 만들기·이름 바꾸기·상위 옮기기·순서·삭제, 2단계 제한, 이름 중복 409, 하위 있는 삭제 409, 100개 제한, 남의 것 404, 판정 순서 401→403→404 |
| `PostCategoryApiIT` | 글 카테고리 즉시 저장·분류 없음, 남의 카테고리 400, 남의 글 404, "수정됨" 없음 |
| `BlogCategoryIT` | 블로그 카테고리 나무와 글 수(상위 = 자기 + 하위, 비공개·임시 글 제외), `?category=` 필터(하위 포함), 없는·남의 번호 404 |
| `CategoryPageShellIT` | `/@handle?category=없는번호` 페이지 404 |
| `PostDetailCategoryIT` | 글 상세 `category` 경로, 조회 실패 시 `null` |
| `CategoryWithdrawalPurgeIT` | 탈퇴 정리 order 15에서 카테고리 삭제, 멱등 |
| 화면 `categoryTree`·`CategorySelect`·`BlogCategoryNav`·`ManageCategoriesPage` | 나무 펼치기, 위/아래 이동, 선택 실패 시 되돌림, 고른 카테고리 `aria-current` |

## 3. 손으로 확인

1. 테스트 계정으로 로그인 → 내 글 관리 → [카테고리 관리] → "개발" 만들기, 상위 "개발"로 "Spring" 만들기
2. 글쓰기 화면 제목 위 카테고리에서 "　└ Spring" 고르기 → 새로 고쳐도 유지
3. 발행 후 내 블로그 → 오른쪽 카테고리에 "개발 (1)", "Spring (1)" → "개발" 누르면 그 글이 보임
4. 글 상세 제목 위에 "개발 › Spring"
5. 폭 375px에서 블로그 카테고리가 목록 위 접히는 상자로 바뀜, 가로 스크롤 없음
6. "개발" 삭제 → 하위가 있다는 안내로 막힘. "Spring" 삭제 → 글은 분류 없음

## 4. 구현 기록 (2026-10-08)

- 백엔드: 전체 `./mvnw verify` 통과 (최신 main = 016 다크 모드 머지 후)
- 화면: 112개 파일 845개 테스트 통과, `eslint`·`prettier`·`tsc`·`vite build` 깨끗함 (016 색 직접 쓰기 검사 포함)
- Flyway: main은 V1·V2까지라 V3 충돌 없음
- 남김: SC-005(글 1만 건에서 카테고리 목록 300ms)는 측정 작업 없이 `ix_post_category` 인덱스로 대비했고, 측정은 3단계 전체 점검(느린 쿼리)에서 함께 한다 (analysis A5)
