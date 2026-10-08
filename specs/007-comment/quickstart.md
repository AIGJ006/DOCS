# Quickstart: 007-comment 검증 시나리오

**Feature**: `007-comment` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 이벤트·공개 Service는 [contracts/events.md](./contracts/events.md), 테이블·상태·응답 모델은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·CSRF·`AccountStatusGuard`·`RateLimiter`·`CursorCodec`·`ProfileImageQuery`), 004(`PostReadService`·권한 하네스), 005(글 상세·`CommentSectionSlot`·`RelativeTime`), **006 머지**(`CommentQueryService` 소유 이전)
- 있으면 함께 확인: 011(알림 이벤트), 014(숨김), 015(탈퇴 정리)

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.comment.*` 기본값(research R15)

## 2. 자동 테스트

```bash
./mvnw -pl backend test -Dtest='CommentTextTest,CommentStateTest,CommentCursorTest'
./mvnw -pl backend verify -Dit.test='Comment*IT'
(cd frontend && npx vitest run src/features/comments src/pages/__tests__/PostDetailPage.test.tsx)
(cd frontend && npx playwright test e2e/comment.spec.ts)
```

| 확인 | 테스트 | 근거 |
|---|---|---|
| 순서·20개·[더 보기]·중복 누락 없음(같은 시각 경계) | `CommentReadIT#최상위_오래된_순_20개씩_중복_누락_없음` | US1 #1, SC-004 |
| 답글 3개 + 20개씩 | `CommentReadIT#답글은_3개_뒤로_20개씩` | US1 #2 |
| 대상 표시·탈퇴 대상 | `CommentReadIT#답글의_답글은_대상_닉네임`, `#대상이_탈퇴하면_탈퇴한_사용자에게` | US1 #3 |
| 볼 수 없는 글 404 | `CommentPermissionMatrixIT`(`comment.csv`), `CommentReadIT#임시_비공개_휴지통_없는_글은_같은_404` | US1 #5, SC-001 |
| 답글 구조·대상 기록 | `CommentWriteIT#답글에_답하면_같은_최상위_아래_대상_기록`, `#내_답글에_답하면_대상_없음` | US2 #2·#3 |
| 판정 순서 (읽을 수 없는 글 + 빈 내용 → 404) | `CommentWriteIT#읽을_수_없는_글은_내용_검사보다_404가_먼저` | US2 #11, FR-009 |
| 정리·길이·XSS | `CommentTextTest`, `CommentXssIT#공격_문자열은_글자_그대로` | US2 #6~#8, SC-005 |
| 10초 중복·동시 5건 | `CommentConcurrencyIT#같은_요청_5건_동시면_댓글은_1개` | US2 #9, SC-003 |
| 1분 10개 | `CommentWriteIT#11번째는_429와_Retry_After`, `#앞_단계에서_걸린_요청은_세지_않는다` | US2 #10, Q2 |
| 답글 불가 대상 | `CommentWriteIT#삭제_숨김_탈퇴_다른_글_댓글에는_답글_불가` | US2 #12 |
| 남의 댓글 수정·삭제 404, 내용 그대로 | `CommentEditDeleteIT#글_작성자와_관리자도_남의_댓글은_404` | US3 #3, SC-006 |
| 자리 남기기·빈 자리 정리 | `CommentEditDeleteIT#답글_있는_최상위는_자리로`, `#마지막_답글을_지우면_자리도_사라진다` | US3 #4~#6 |
| 숨긴 댓글 수정 409·삭제 가능 | `CommentEditDeleteIT#숨긴_댓글은_409_삭제는_가능` | US3 #7 |
| 삭제와 답글 경합 | `CommentConcurrencyIT#삭제와_답글이_동시면_하나씩` | FR-014 |
| 댓글 수 일관성 혼합 | `CommentConcurrencyIT#동시_작성_20건과_삭제_숨김_해제_탈퇴_정리_뒤_수가_같다` | SC-002 |
| 글 상태·숨김·탈퇴 표시 | `CommentVisibilityIT` | US4, SC-007 |
| around | `CommentAroundIT` | US5 |
| 페이지 SQL 4번·300ms | `CommentReadIT#페이지_SQL은_4번`(001 `SqlCounter`), §4 | SC-008 |

## 3. 수동 확인 (브라우저)

1. 비로그인으로 공개 글 열기 → 댓글 영역 "로그인하고 댓글을 남겨 보세요 [로그인]". 개발자 도구 네트워크에서 상세 API와 `/api/posts/{id}/comments`가 **동시에** 나간다
2. 인증 전 회원 → "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]"
3. 인증한 회원 A → 댓글 `<script>alert(1)</script>` + 줄바꿈 2줄 + `**굵게**` 등록 → 글자 그대로, 줄바꿈 유지, 머리말 "댓글 1"
4. 회원 B → A 댓글에 [답글] → 다시 B의 답글에 A가 [답글] → "@B닉네임에게" 표시, 모두 같은 최상위 아래
5. 댓글 25개인 글 → 20개 + [댓글 더 보기] → 내가 새 댓글 등록(목록 끝에 바로 보임) → [댓글 더 보기] → 내 댓글이 두 번 보이지 않는다
6. A가 답글 있는 자기 최상위 삭제 → "삭제된 댓글이에요" 자리, 답글은 그대로 → 답글 작성자 B가 마지막 답글 삭제 → 자리도 사라짐
7. 글 작성자로 남의 댓글 → [수정]·[삭제] 버튼 없음. [신고] 버튼도 없음(014 전)
8. 글을 비공개로 바꾸기 → 다른 회원 창에서 새로 고침 → 글·댓글 모두 찾을 수 없음 → 다시 공개 → 댓글 돌아옴
9. 알림 링크 모양 `/@{h}/posts/{id}?comment={45번째 최상위의 5번째 답글}#comment-{id}` 직접 열기 → 그 답글까지 펼쳐지고 스크롤·강조, 위에 [이전 댓글 보기]
10. 375px 폭에서 답글·긴 닉네임·긴 단어(URL 200자)가 가로 스크롤을 만들지 않는다(`overflow-wrap: anywhere`)

## 4. SC-008 측정

측정: 2026-10-08, `CommentPerformanceIT`(`-Dblog.perf=true`, Testcontainers PostgreSQL 18, MockMvc). 시드: 글 1만 건, 댓글 10만 건(한 글에 최상위 2천 + 답글이 많은 최상위 몇 개 — 하나는 답글 5천), 회원 2천 명.

| 측정 | 요청 | 기준 | 결과 |
|---|---|---|---|
| 첫 페이지 p95 (20회) | `GET /api/posts/{큰 글}/comments` | 300ms, SQL 4번 | p50 8.8ms · p95 13.4ms, SQL 5번(글 판정 1 + 댓글 4) |
| 마지막 페이지 근처 | 커서 이어서 | 300ms | 96쪽: p50 9.3ms · p95 17.5ms, SQL 5번 |
| 답글 5천 최상위의 답글 펼치기 | `GET /api/comments/{root}/replies` | 300ms | p50 8.3ms · p95 11.8ms, SQL 5번 |
| around (깊은 답글) | `?around=` | 300ms | 상한 100을 넘는 답글: p50 9.3ms · p95 11.4ms, SQL 11번 |
| EXPLAIN | 최상위·LATERAL 답글 | `ix_comment_root`·`ix_comment_reply` 사용 | 둘 다 Index Only Scan (최상위 0.05ms, 미리보기 0.16ms) |

## 5. 다른 기능 확인 (있을 때)

- 006: 글 영구 삭제 → 그 글 댓글 0행(CASCADE). `CommentQueryService.commentIdsOfPost`로 신고 종료가 그대로 동작(006 `PostPurgeIT`)
- 011: 댓글 작성 → 알림 생성, 자리로 남긴 삭제 → 그 알림 삭제
- 014: 숨김 → 다른 회원에게 "운영 정책에 따라 숨겨진 댓글이에요", 작성자에게 원문 + "숨겨졌어요 (나만 보여요)", 댓글 수 −1
- 015: 탈퇴 신청 → 그 회원 댓글 "탈퇴한 사용자의 댓글이에요", 수 그대로 → 30일 정리 → 남의 답글 있는 최상위만 자리, 수가 실제와 같다
