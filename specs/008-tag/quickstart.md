# Quickstart: 008-tag 검증 시나리오

**Feature**: `008-tag` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 정규화 표·주소·페이지 셸 응답은 [contracts/normalization.md](./contracts/normalization.md), 테이블·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·`RateLimiter`·`BannedWordFilter`), 002(발행·`TagService` 임시 구현·`PublishDialog`), 004(`VisibilityFilter`·권한 하네스), 005(카드·커서·블로그·`PageShellController`·`TagList`)
- Tier A 임시 규칙으로 만든 태그가 남은 개발 DB는 지우고 다시 만든다(Clarifications Q3): `docker compose down -v && docker compose up -d postgres redis`

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.tag.*` 기본값(data-model §5)

## 2. 자동 테스트

```bash
./mvnw -pl backend test -Dtest='TagNormalizerTest,PublishValidatorTest'
./mvnw -pl backend verify -Dit.test='Tag*IT,BlogTagIT,PublishIT,PublishQueryCountIT,PublishTransactionIT,PublishIdempotencyIT'
(cd frontend && npx vitest run src/features/tag src/components/editor src/pages/__tests__/TagPage.test.tsx src/pages/__tests__/BlogPage.test.tsx)
(cd frontend && npx playwright test e2e/tag.spec.ts)
```

| 확인 | 테스트 | 근거 |
|---|---|---|
| 예시 표 전부 일치 (서버·화면 같은 CSV) | `TagNormalizerTest#예시_표`, `normalizeTag.test.ts` | SC-001, FR-009 |
| 문제 태그 모두 한 번에, 개수 초과와 함께 | `PublishValidatorTest#개수_초과와_칸_오류를_함께_모은다`, `TagPublishIT#문제_태그를_모두_한_번에_알려준다` | SC-006, US1 #4·#5 |
| 순서·중복 제거 | `TagPublishIT#입력_순서대로_처음_것만_남긴다` | US1 #6, FR-010·011 |
| 동시 10건 → 태그 1개 | `TagPublishIT#같은_새_태그로_동시에_10건_발행해도_태그는_하나` | SC-003, US1 #8 |
| 태그만 바꿔도 수정됨 | `TagPublishIT#태그만_바꿔_다시_발행하면_수정됨` | US1 #9 |
| 002 교체 점검표 회귀 | `PublishIT`, `PublishQueryCountIT#발행_SQL_수는_태그_사진_수에_비례하지_않는다`, `PublishTransactionIT`, `PublishIdempotencyIT` | 002 T122 |
| 공개 조건 행렬 | `TagPermissionMatrixIT`(`tag-list.csv`) | SC-004, US2 #2 |
| 빈 태그 = 비공개 전용 태그 | `TagPostListIT#비공개_전용_태그와_없는_태그의_응답이_같다` | SC-005, US2 #3 |
| 주소 왕복·301·404 (실제 보안 필터) | `TagPageShellIT#허용_문자_태그_주소가_모두_왕복된다`, `#정규화되지_않은_주소는_301`, `#형식이_틀린_이름은_404` | SC-002, US2 #4~#6 |
| 자동완성 범위·순서·권한·제한 | `TagSuggestIT#남의_비공개_전용_태그는_제안하지_않는다`, `#내_태그가_먼저`, `#비회원은_401_인증_전은_허용`, `#1분_61번째는_429`, `#Redis_장애면_제한_없이_응답` | US3, FR-031~036 |
| 전체 목록 순서·0개 제외·즉시 반영 | `TagIndexIT#공개_글_수_순_상위_100`, `#비공개로_바꾸면_다음_요청에서_빠진다` | US4, FR-029 |
| 블로그 태그 줄·필터·301 | `BlogTagIT`, `TagPageShellIT#블로그_필터_대문자는_301` | US5 |
| 1만 건 300ms | `TagPerformanceIT` (§4) | SC-007, FR-029 |

## 3. 수동 확인 (브라우저)

1. 회원 A로 로그인 → 새 글 → [발행] 창 → 태그 칸에 `Spring Boot` Enter → 칩 `#spring-boot`가 바로 보인다. `#SPRING  BOOT` Enter → 새 칩이 생기지 않고 기존 칩이 잠깐 강조된다
2. `🔥hot` Enter → 칩이 생기지 않고 "쓸 수 없는 글자가 있어요"가 보인다. 입력은 지워지지 않는다
3. `C#`, `C++`, `Node.JS`, `.NET`, `스프링  부트`를 넣고 칩 하나에 초점 → Alt+← → 순서가 바뀌고 화면 읽기 프로그램 안내가 나온다. "6 / 10"
4. 발행 → 글 상세 아래 태그가 입력 순서로 보인다 → `#c#` 클릭 → 주소창 `/tags/c%23`, 머리말 "#c# · 공개 글 1"
5. 주소창에 `/tags/Spring%20Boot` → `/tags/spring-boot`로 바뀐다(개발자 도구 네트워크: 301). `/tags/%F0%9F%94%A5` → 찾을 수 없음 화면(404)
6. 회원 B로 비공개 글에 `이직준비` 발행 → 회원 A로 태그 칸에 `이직` 입력 → 제안 목록이 뜨지 않는다. 회원 B는 `이직` → `이직준비`가 "내 태그"로 보인다
7. 비로그인으로 `/tags/이직준비`와 `/tags/아무도안쓴태그` → 둘 다 "아직 이 태그로 공개된 글이 없어요"
8. `/tags` → 공개 글 수 많은 순, `이직준비`는 없다
9. 회원 A 블로그 `/@{a}` → 위쪽 태그 줄(글 수 많은 순 10개 + [태그 더 보기]) → `#jpa` → "#jpa 글 N개 [필터 해제]", 주소 `/@{a}?tag=jpa`. 주소를 `?tag=JPA`로 바꾸면 301로 돌아온다
10. 한글 입력기로 `스프링`을 치는 동안 네트워크 탭에 `/api/tags/suggest` 요청이 없고, 조합이 끝나고 0.3초 뒤 한 번 나간다
11. 375px 폭(개발자 도구)에서 발행 창 칩 10개·태그 페이지·블로그 태그 줄에 가로 스크롤이 없다

## 4. SC-007·FR-029 측정

`TagPerformanceIT`가 시드를 만들고 결과를 출력한다. 결과를 아래 표에 적는다(같은 기계에서 3번, 가운데 값).

- 시드: 회원 200명, 글 1만 건(공개 8천·비공개 1천·임시 5백·휴지통 3백·숨김 2백), 태그 2천 개, 연결 3만 건(태그 쏠림: 상위 20개가 연결의 40%)

| 측정 | 대상 | 기준 | 결과 |
|---|---|---|---|
| 전체 태그 목록 p95 (20회) | `GET /api/tags` | 300ms | |
| 가장 많이 쓰인 태그 목록 첫 페이지 p95 | `GET /api/tags/{top}/posts` | 300ms | |
| 그 태그 마지막 페이지 근처 p95 | 커서 이어서 | 300ms | |
| 자동완성 한 글자 p95 | `GET /api/tags/suggest?q=s` | 300ms | |
| 블로그 태그 줄 (글 1천 건 블로그) | `GET /api/members/{h}/tags` | 300ms | |
| EXPLAIN | 태그별 목록 | `ix_post_tag_tag` 사용, `post` 순차 읽기 없음 | |

전체 태그 목록이 300ms를 넘으면 research R8대로 "공개에서 빠질 때 지우는 캐시" 작업(tasks T073)을 연다.

## 5. 다른 기능 확인 (있을 때)

- 006: 글을 영구 삭제하면 `post_tag` 행이 없어지고 `tag` 행은 남는다. `/tags/{그 태그}` → 200 빈 목록
- 014: 관리자 숨김 글의 태그가 다음 요청부터 `/tags`·태그별 목록에서 빠진다
- 015: 탈퇴 신청 회원의 글 태그가 다음 요청부터 빠지고, 철회하면 돌아온다
