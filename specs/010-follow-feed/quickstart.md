# Quickstart: 010-follow-feed 검증 시나리오

**Feature**: `010-follow-feed` | **Date**: 2026-10-08

기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드는 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), SQL·이벤트·공개 Service는 [contracts/follow-sql.md](./contracts/follow-sql.md), 테이블·코드·설정값은 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS
- 선행 기능: 001(로그인·CSRF·`AccountStatusGuard`·`RateLimiter`·`CursorCodec`·`MemberQueryService`), 004(`VisibilityFilter`·권한 하네스·`useAuthGate`), 005(카드 목록·블로그 머리말·글 상세·`PostReadingPorts`·페이지 셸·`useCursorList`)
- 있으면 함께 확인: 008(`CardFilter`), 011(새 팔로워·새 글 알림), 015(탈퇴 유예·정리)

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run
(cd frontend && npm ci && npm run dev)
```

- Flyway 로그에 이 기능의 새 마이그레이션이 없다
- `application.yml`에 `blog.follow.*` 기본값

## 2. 자동 테스트

```bash
./mvnw -pl backend verify -Dit.test='Follow*IT,FeedApiIT,BlogHeaderFollowIT'
(cd frontend && npx vitest run src/features/follow src/pages/__tests__/FeedPage.test.tsx src/pages/__tests__/FollowListPage.test.tsx)
(cd frontend && npx playwright test e2e/follow-feed.spec.ts)
```

| 테스트 | 확인하는 것 |
|---|---|
| `FollowApiIT` | US1 #1~#8: 팔로우·언팔로우 응답 `{following, followerCount}`, 이미 그 상태면 200·이벤트 없음, 자기 자신 400·행 0, 비회원 401, 인증 전 200, 없는 주소·유예 회원 404(본문 고정), 유예 회원 본인 403, 남은 세션의 정지 403, 31번째 429 + `Retry-After`, Redis 정지 중 통과, 관리자 동일 |
| `FollowConcurrencyIT` | SC-001: 같은 팔로우 동시 20번 → 행 1, `MemberFollowed` 1번. 팔로우·언팔로우 섞어 50번 → 마지막 상태 하나 |
| `FollowListApiIT` | US3 #1~#5: 비회원 조회, 항목 칸, 최근 팔로우 순·같은 시각이면 회원 번호 큰 순, 20개 [더 보기] 끝까지 중복·누락 0, 유예 회원 빠짐·복구하면 돌아옴, 빈 목록, 없는·유예 주소 404, 다른 목록 커서 400, SQL 2번(`SqlCounter`) |
| `FollowCountPerformanceIT` | SC-007: 팔로워 1만 명(유예 50명 섞음) 수 = 9,950, `EXPLAIN`이 `ix_follow_followee`, 10ms 이내 |
| `FeedApiIT` | US2 #1~#7: 팔로우한 두 사람의 공개 발행 글만, 비공개·임시·휴지통·숨김·유예 작성자 글 0(SC-003), 9개씩 끝까지 중복·누락 0(SC-004), 언팔로우 직후 0(SC-005), `hasFollowing` 두 경우, 비회원 401, 카드 SQL 1번, 글 1만 건 `EXPLAIN` 200ms 이내 |
| `BlogHeaderFollowIT` | 머리말 `followerCount`·`followingCount`·`followedByMe`(비회원·내 블로그 false), 005 기존 칸 그대로 |
| `FollowWithdrawalIT` | US4 #1~#3: 유예 중 수·목록·피드에서 빠짐, 복구 후 전과 같음, 015 단계 order 65 실행 뒤 양방향 0행(SC-008) |
| `FollowPermissionMatrixIT` | `follow.csv` (research R11 표) |

## 3. 수동 확인 (브라우저)

1. 회원 A·B·C를 만들고 B·C로 공개 글 몇 개, B로 비공개 글 1개를 쓴다
2. A로 B 블로그 → "공개 글 N · 팔로워 0 · 팔로잉 0"과 [팔로우] → 누르면 바로 [팔로잉 ✓], 팔로워 1
3. [팔로잉 ✓]에 마우스를 올리면 [언팔로우] → 누르면 확인 창 없이 [팔로우], 다시 눌러 팔로우
4. 개발자 도구에서 네트워크를 끊고 [팔로우] → 버튼이 원래대로 돌아오고 "잠시 후 다시 시도해 주세요"
5. A로 C 글 상세 → 작성자 카드 [팔로우] → 누른 뒤 새로 고쳐도 [팔로잉 ✓]
6. 머리말 [피드] → `/feed`에 B·C의 공개 글만 최신순, B 비공개 글 없음. [더 보기] 끝까지
7. 피드에서 글 하나를 열고 뒤로 → 카드·스크롤 위치 그대로
8. B 블로그의 "팔로워 1" → `/@b/followers`에 A, 항목에 사진·`닉네임 @a`·소개 첫 줄·버튼(A 자신은 버튼 없음)
9. 로그아웃 상태로 `/@b/followers` → 보인다. [팔로우] → 로그인 화면 → 로그인 뒤 원래 페이지로(자동 팔로우 안 됨). `/feed` → 로그인 화면, 머리말에 [피드] 없음
10. 아무도 팔로우하지 않은 D로 `/feed` → "팔로우한 사람이 없어요. 홈에서 읽고 싶은 블로그를 찾아보세요 [홈]"
11. `/@없는주소/followers` → 404 화면(첫 응답 상태도 404), `/@B대문자/followers` → 301 소문자
12. 375px 폭에서 피드·목록·머리말 가로 스크롤 없음, 버튼 44px 이상

## 4. 다른 기능 확인 (있을 때)

- 011: A가 B를 팔로우하면 B에게 새 팔로워 알림 1개, 7일 안에 다시 팔로우해도 새 알림 없음. B가 글을 처음 공개하면 A에게 새 글 알림
- 015: A가 탈퇴 신청 → B 머리말 팔로워 수 −1, B 팔로워 목록에서 A 빠짐 → A 복구 → 돌아옴. 정리 뒤 `SELECT count(*) FROM follow WHERE follower_id = :a OR followee_id = :a` = 0
- 008: 태그 목록·블로그 태그 목록이 `CardFilter` 변경 뒤에도 그대로
