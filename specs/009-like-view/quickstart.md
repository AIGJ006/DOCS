# Quickstart: 009-like-view 검증 시나리오

**Feature**: `009-like-view` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 집계·배치·Service는 [contracts/view-pipeline.md](./contracts/view-pipeline.md), 테이블·키는 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·CSRF·`AccountStatusGuard`·`RateLimiter`·`ClientIp`), 004(`PostReadService`·권한 하네스), 005(상세·`ReactionBar`·`useViewBeacon`·`PostLikeStatusQuery`), 007 또는 이 기능의 `PostCounterService`
- 있으면 함께 확인: 011(좋아요 알림), 012(트렌딩), 015(탈퇴 정리)

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.like.*`·`blog.view.*` 기본값

## 2. 자동 테스트

```bash
./mvnw -pl backend test -Dtest='VisitorKeyResolverTest,ViewExclusionTest'
./mvnw -pl backend verify -Dit.test='Like*IT,View*IT,LikeViewPermissionMatrixIT'
(cd frontend && npx vitest run src/features/like src/components/__tests__/LikeButton.test.tsx src/components/__tests__/formatCount.test.ts)
(cd frontend && npx playwright test e2e/like-view.spec.ts)
```

| 확인 | 테스트 | 근거 |
|---|---|---|
| 좋아요·취소·멱등 | `LikeApiIT#좋아요_취소_다시_요청은_변화_없음` | US1 #1·#2, FR-002 |
| 동시 20번 → 1건·사건 1번 | `LikeConcurrencyIT#같은_회원_동시_20번은_1건_이벤트_1번` | SC-001, US1 #3 |
| 50명 동시·섞기 3회 | `LikeConcurrencyIT#50명_동시는_50`, `#좋아요_취소_섞기_3회_모두_수가_건수와_같다` | SC-002, US1 #4 |
| 판정 순서·거부 시 수 불변 | `LikeViewPermissionMatrixIT`(`like-view.csv`), `LikeApiIT#볼_수_없는_자기_글은_400이_아니라_404` | US2, SC-004 |
| 1분 61번째 429 | `LikeApiIT#좋아요_취소_합쳐_61번째는_429` | US2 #5 |
| 보정 0건·어긋나면 고침 | `LikeReconcileJobIT` | FR-005, SC-003 |
| 중복 판정·동시 50번·설정 변경 | `ViewRecordIT#24시간_1회`, `#동시_50번은_1번`, `#30분_5회_설정` | US3 #1~#3, SC-006 |
| 제외(작성자·관리자·봇·prefetch)와 같은 204 | `ViewRecordIT#센_것과_안_센_것의_응답이_같다` | US3 #4·#6, FR-024·031 |
| `vid` 발급 시점 | `ViewRecordIT#상세를_먼저_열면_첫_조회와_새로고침이_같은_방문자` | research R5 |
| Redis 장애 중 상세 정상·204 | `ViewRedisOutageIT` | US3 #7, SC-008 |
| 반영·중단 복구 | `ViewFlushJobIT` | US3 #8, US4 #1·#2, SC-007·009 |
| IP 0건 | `ViewPrivacyIT` | SC-010 |
| 90일 정리 | `ViewDailyRetentionJobIT` | US4 #3, SC-011 |

## 3. 수동 확인 (브라우저)

1. 회원 A로 B의 공개 글 → ♡ 누름 → 바로 ♥ + 수 +1. 개발자 도구 네트워크에 `PUT …/like` 하나
2. 빠르게 5번 연타(홀수) → 0.3초 뒤 요청 1개, 최종 ♥
3. 네트워크를 끊고 누름 → 되돌아가고 "좋아요를 반영하지 못했어요"
4. 비로그인 → ♡ 누름 → "로그인하고 좋아요를 눌러 보세요 [로그인]" → 로그인 → 같은 글로 돌아오고 ♡ 그대로(자동으로 안 눌림)
5. 인증 전 회원 → "이메일 인증 후 누를 수 있어요"
6. 내 글 → 버튼 없이 ♥ + 수
7. 키보드 Tab으로 버튼에 가서 Space → 눌림. 화면 낭독기에 "좋아요 취소 (13)" 
8. 비로그인 새 창(쿠키 지움)으로 글 열기 → 응답 헤더에 `Set-Cookie: vid=` → 1초 뒤 `POST …/views` 204 → 새로고침 5번 → 1분 뒤 조회 +1만
9. 다른 탭에서 글을 열고 그 탭을 보지 않음 → 요청 없음 → 탭을 보면 1초 뒤 한 번
10. `docker compose stop redis` → 글 상세 정상(비회원 기준), 조회 요청 204 → `docker compose start redis`

## 4. 개인정보 확인 (SC-010)

```bash
docker compose exec redis redis-cli --scan --pattern 'view:*'      # h: 해시·v: UUID·m: 번호만, IP 모양 없음
docker compose exec redis redis-cli --scan --pattern 'ratelimit:view:*'
docker compose exec postgres psql -U blog -c "select * from post_view_daily limit 5"   # 글·날짜·합계만
grep -E '([0-9]{1,3}\.){3}[0-9]{1,3}' backend/logs/app.log | grep -i view    # 조회수 로그에 IP 0건
```

## 5. 다른 기능 확인 (있을 때)

- 006: 글 영구 삭제 → `post_like`·`post_view_daily` 0행, 모아 둔 조회는 다음 반영에서 건너뜀
- 011: 좋아요 → 취소 → 좋아요 → 작성자 알림 1번
- 015: 탈퇴 30일 정리 → 그 회원 좋아요 0행, 글들의 `like_count`가 실제 건수와 같다(다음 날 보정 0건)
