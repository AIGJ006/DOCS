# Implementation Plan: 팔로우·팔로잉 피드

**Branch**: `010-follow-feed` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-follow-feed/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

로그인한 회원은 다른 회원을 승인 없이 바로 팔로우·언팔로우하고(이메일 인증 불필요, 1분 30번 제한), 팔로우한 사람들의 공개·발행 글만 모은 별도 피드 페이지를 홈과 같은 카드·정렬·9개씩 [더 보기]로 본다. 누구나 블로그 상단의 팔로워·팔로잉 수와 20개씩 이어지는 목록을 볼 수 있고, 탈퇴 유예 회원은 수·목록·피드에서 빠졌다가 복구하면 돌아온다. 30일 뒤 익명 처리 때 팔로우 관계는 양방향 모두 지운다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `follow(follower_id, followee_id, created_at)` PK `(follower_id, followee_id)`, `ck_follow_self`, `ix_follow_followee (followee_id, created_at DESC)`, `ix_follow_follower (follower_id, created_at DESC)`를 그대로 쓴다(R1).
- **모듈.** 팔로우 관계·수·목록은 `interaction` 모듈(회원 사이 상호작용 — 댓글·좋아요와 같은 곳), 피드는 홈·블로그 목록과 같은 `discovery` 모듈에 둔다. 새 모듈 패키지는 만들지 않는다(R2, 제안).
- **팔로우 = 상태 지정 + 한 트랜잭션.** `PUT`/`DELETE /api/members/{handle}/follow` → `{following, followerCount}`. 판정 순서 401 → 403(001 게이트·`AccountStatusGuard` `ACCOUNT_WRITE` — 인증 전 통과, 남은 세션의 정지 403) → 404(`MemberQueryService.findReadableBlogOwner`: 없음·탈퇴 유예·익명 처리) → 400 `CANNOT_FOLLOW_SELF` → 429 `TOO_MANY_REQUESTS`(팔로우·언팔로우 합쳐 회원당 1분 30번, `ratelimit:follow:{memberId}`, 001 `RateLimiter`, Redis 장애면 통과). 트랜잭션에서 `INSERT … ON CONFLICT DO NOTHING RETURNING` / `DELETE … RETURNING`의 행이 올 때만 `MemberFollowed`/`MemberUnfollowed`를 발행하고, 같은 트랜잭션에서 최신 팔로워 수를 센다(R3·R4).
- **수는 볼 때 센다.** 팔로워 수 = `ix_follow_followee` + 탈퇴 유예 회원 제외 `NOT EXISTS`(`ix_member_withdraw_purge`), 팔로잉 수도 같은 방식. 저장 카운터는 두지 않는다(F-6, 1만 명 약 5ms). 블로그 머리말(005 `BlogHeaderView`)에 `followerCount`·`followingCount`·`followedByMe`를 더한다(R5).
- **목록 = SQL 1번 + 팔로우 여부 1번.** `GET /api/members/{handle}/followers|following?cursor=` — `follow` + `member` + 현재 프로필 사진 JOIN 한 번(탈퇴 신청 회원 제외, 최근 팔로우 순·같으면 회원 번호 큰 순, 21개 읽어 20개), 보는 사람의 팔로우 여부 `followee_id = ANY(:ids)` 한 번. 커서는 001 `CursorCodec`(`followers:{handle}`·`following:{handle}`, 키 `(created_at 마이크로초, 회원 번호)`)(R6).
- **피드 = 카드 SQL 1번.** `GET /api/feed?cursor=` — 005 카드 조회(`PostCardQueryRepository`)에 008이 넓힌 `CardFilter`에 `followerId`를 더해 `AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :followerId AND f.followee_id = p.author_id)`. 노출 조건은 004 `VisibilityFilter` 그대로(공개·발행·휴지통 아님·숨김 아님·작성자 탈퇴 유예 아님 — 친구 공개 글은 자동으로 빠짐). 정렬·9개·커서(`ListScope` `feed`)는 홈과 같다. 첫 페이지 응답에 `hasFollowing`을 실어 빈 화면 문구 두 가지를 고른다(R7).
- **005 자리 채우기.** 글 상세의 `viewer.followingAuthor`는 005 `PostReadingPorts`의 기본 구현(항상 false)을 이 기능의 `AuthorFollowStatusQueryAdapter`가 대신하고, `AuthorCard.followButton`·블로그 머리말에 `FollowButton`을 넣는다(R8).
- **화면.** `FollowButton`(누르는 즉시 바뀜, [팔로잉 ✓]에 마우스·초점이면 [언팔로우], 확인 창 없음, 실패하면 되돌림 + "잠시 후 다시 시도해 주세요", 비회원은 004 `useAuthGate` 로그인 안내), `/feed`(로그인 전용, 30분 복원), `/@{handle}/followers`·`/@{handle}/following`(누구나), 머리말 [피드](로그인했을 때만)(R9).
- **다른 기능에 주는 것.** `MemberFollowed`·`MemberUnfollowed`(011 새 팔로워 알림), `FollowQueryService.followerIdsOf`(011 새 글 알림은 011이 직접 SQL로 처리 — 25 §4-1), `FollowWithdrawalPurgeStep`(015 order 65)(R10).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation), `JdbcClient`, Flyway, 001 `RateLimiter`·`AccountStatusGuard`·`MemberQueryService.findReadableBlogOwner`·`CursorCodec`·`ListScope`, 004 `VisibilityFilter`, 005 `PostCardQueryRepository`·`PostListService`·`PostCardAssembler`·`BlogQueryService`·`PostReadingPorts`·`PageShellController`, 008 `CardFilter`, media `ProfileImageQuery`·`ImageUrlResolver`
- 새 의존성 없음
- 화면: React 18 + react-router 7, 005 `PostCardGrid`·`LoadMoreButton`·`useCursorList`·`listRestore`·`AuthorCard.followButton`·`BlogPage`, 004 `useAuthGate`, 001 `SessionBar`

**Storage**:

- PostgreSQL: `follow`(interaction 소유). 읽기만: `member`(목록 표시·탈퇴 유예 제외), `image`(현재 프로필 사진), `post`(피드 — discovery 카드 SQL)
- Redis: 요청 제한 `ratelimit:follow:{memberId}`(1분)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc, `@RecordApplicationEvents`. 화면은 Vitest + Testing Library, 종단 확인은 Playwright. 헌법 VIII에 따라 동시 20번 팔로우(SC-001), 피드 노출 조건·끝까지 넘기기(SC-003·004), 탈퇴 유예 제외(SC-006), 1만 명 팔로워 수 계산(SC-007)은 실제 DB 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 팔로우·언팔로우 p95 100ms 이내(제한 Redis 1번 + 회원 조회 1번 + 트랜잭션 SQL 2번)
- 팔로워 수: 팔로워 1만 명에 10ms 이내(원문 측정 약 5ms, SC-007), 블로그 머리말 SQL은 005의 3번 + 수 2번 + 팔로우 여부 1번
- 피드 첫 페이지 p95 200ms 이내(카드 SQL 1번 + 프로필 사진 1번), 목록 페이지 SQL 2번(FR-023)

**Constraints**:

- 피드·목록은 카드·항목마다 따로 조회하지 않는다(FR-023)
- 팔로우 관계 변경은 실제로 바뀐 경우에만 이벤트(EV-4). 트랜잭션 안에서 Redis를 쓰지 않는다(요청 제한은 트랜잭션 밖)
- 볼 수 없는 대상(없음·탈퇴 유예·익명 처리)은 같은 404 본문
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 회원 수천 명, 글 1만 건, 한 사람이 팔로우하는 수 수백 명, 인기 회원 팔로워 1만 명 기준
- API 6개(팔로우 지정·해제, 팔로워·팔로잉 목록, 피드) + 블로그 머리말 확장 1개, 이벤트 2종, 화면 페이지 3개 + 버튼 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 `follow` 그대로. 나민서 "블로그 구독"은 같은 관계를 화면 이름만 바꿔 쓴다(24 §7) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (예외 2건 — Complexity Tracking)** | `follow`는 interaction만 쓴다. 블로그 주인은 001 `MemberQueryService`, 피드 노출 조건은 004 `VisibilityFilter`. 예외: 목록 SQL이 `member`·`image`를, 피드 카드 SQL이 `follow`를 읽기 전용 JOIN한다 |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 판정 순서 고정(401 → 403 → 404 → 400 → 429), 없음·탈퇴 유예·익명 처리 대상은 같은 404. 피드는 서버가 로그인 확인(화면만 막지 않음). 권한 매트릭스 `follow.csv`(R11) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 목록의 닉네임·소개는 텍스트 노드로만, 소개는 첫 줄만 표시 |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 요청 제한은 Redis 장애 때 통과. 글 상세의 팔로우 여부 조회 실패는 005 `guard`가 false로 바꾼다. 블로그 머리말의 수 조회는 DB 조회라 따로 막지 않는다 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 탈퇴 유예 중 관계를 지우지 않고 화면에서만 뺀다. 익명 처리 때만 양방향 삭제(015 order 65) |
| VII. 수치는 설정값으로 | **PASS** | `blog.follow.rate-limit`(30/1m), `blog.follow.list-page-size`(20), 피드 카드 수는 005 `blog.list.page-size`(9) 공유 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 동시 20번, 피드 노출·끝까지 넘기기, 유예 제외·복구, 1만 명 수 계산 `EXPLAIN` |

**Gate 결과 (Phase 0 전)**: 원칙 II 읽기 예외 2건을 Complexity Tracking에 적었다. 005·008과 같은 종류의 예외다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 새 위반은 없다.
- 새로 확인한 점:
  1. spec Implementation Notes의 피드 SQL(`follow f JOIN post p … m.profile_image_url`)은 2026-10-07 결정으로 `hidden_at IS NULL`이 더해지고 `profile_image_url` 컬럼이 없어졌다. 이 계획은 005 카드 SQL(`image` JOIN)을 그대로 쓰므로 두 가지가 자동으로 맞는다.
  2. 피드 조건을 `follow` JOIN이 아니라 `EXISTS`로 쓴다. 005·008과 같은 카드 SQL 하나를 넓히는 방식이라 정렬·커서·노출 조건이 갈라지지 않는다(R7).
  3. 008의 `CardFilter`가 아직 없으면 이 기능이 만들고 008이 `tagId`를 더한다(둘 중 먼저 하는 쪽이 만듦, 한 클래스).
  4. 015가 아직 없으면 `WithdrawalPurgeStep` 인터페이스(015 tasks T009)를 이 기능이 먼저 만든다.

## Project Structure

### Documentation (this feature)

```text
specs/010-follow-feed/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 팔로우 지정·해제, 팔로워·팔로잉 목록, 피드, 블로그 머리말 확장
│   └── follow-sql.md    # 팔로우 저장·수·목록·피드 SQL, 이벤트, 다른 기능이 부르는 Service, 탈퇴 정리 단계
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── interaction/
│   ├── web/
│   │   ├── FollowController.java               # PUT·DELETE /api/members/{handle}/follow
│   │   └── FollowListController.java           # GET /api/members/{handle}/followers|following
│   ├── application/
│   │   ├── FollowService.java                  # follow / unfollow (판정 순서·트랜잭션·이벤트)
│   │   ├── FollowQueryService.java             # 수·목록·팔로우 여부 (다른 모듈에 공개)
│   │   ├── FollowListCursor.java               # followers:{handle} / following:{handle}
│   │   ├── AuthorFollowStatusQueryAdapter.java # 005 AuthorFollowStatusQuery 구현 Bean
│   │   ├── FollowWithdrawalPurgeStep.java      # 015 order 65
│   │   └── FollowProperties.java               # blog.follow.*
│   ├── domain/FollowReasonCode.java            # CANNOT_FOLLOW_SELF (400)
│   └── infra/FollowRepository.java             # insertIfAbsent / deleteIfPresent / counts / page / followedAmong
├── discovery/
│   ├── web/FeedController.java                 # GET /api/feed
│   ├── web/PageShellController.java            # + /@{handle}/followers·/following 301/404
│   ├── application/FeedQueryService.java       # CardFilter(followerId) + hasFollowing
│   ├── application/BlogHeaderView.java         # + followerCount, followingCount, followedByMe
│   ├── application/BlogQueryService.java       # 머리말에 FollowQueryService 결과
│   └── infra/PostCardQueryRepository.java      # CardFilter + followerId EXISTS 조건
└── shared/event/MemberFollowed.java, MemberUnfollowed.java

backend/src/main/resources/application.yml      # blog.follow.*

backend/src/test/
├── resources/permission/follow.csv             # follow.put·follow.delete·follow.followers·follow.following·feed.read
└── java/com/team/blog/
    ├── interaction/integration/
    │   ├── FollowApiIT.java                    # US1
    │   ├── FollowConcurrencyIT.java            # SC-001
    │   ├── FollowListApiIT.java                # US3
    │   ├── FollowCountPerformanceIT.java       # SC-007 (1만 명, EXPLAIN)
    │   ├── FollowWithdrawalIT.java             # US4 (유예·복구·정리)
    │   └── FollowPermissionMatrixIT.java       # 004 하네스
    └── discovery/integration/
        ├── FeedApiIT.java                      # US2
        └── BlogHeaderFollowIT.java             # 머리말 수·팔로우 여부

frontend/src/
├── api/follows.ts                              # follow / unfollow / listFollowers / listFollowing / getFeed
├── features/follow/
│   ├── useFollowToggle.ts                      # 즉시 반영·마지막 상태 전송·되돌림
│   ├── FollowButton.tsx                        # [팔로우] / [팔로잉 ✓] ↔ [언팔로우]
│   ├── FollowCounts.tsx                        # 공개 글 · 팔로워 · 팔로잉 (링크)
│   ├── FollowListItem.tsx
│   └── followMessages.ts
├── pages/FeedPage.tsx                          # /feed
├── pages/FollowListPage.tsx                    # /@{handle}/followers, /@{handle}/following
├── pages/BlogPage.tsx                          # 머리말에 FollowCounts·FollowButton (005 소유 파일)
├── pages/PostDetailPage.tsx                    # AuthorCard followButton (005 소유 파일)
├── features/auth/SessionBar.tsx                # [피드] 링크 (001 소유 임시 머리말)
└── App.tsx                                     # 경로 3개
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 팔로우 관계는 `interaction`에, 피드는 홈·블로그 목록과 같은 `discovery`에 둔다. 005·001 소유 화면 파일(`BlogPage`·`PostDetailPage`·`SessionBar`)은 자리 채우기만 하고 구조를 바꾸지 않는다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `interaction.infra.FollowRepository`의 목록·수 SQL이 `member`(닉네임·주소·소개·상태)와 `image`(현재 프로필 사진)를 읽기 전용 JOIN한다(원칙 II 읽기 예외) | FR-023이 목록을 "SQL 1번 + 팔로우 여부 1번"으로 정했고, 탈퇴 유예 회원 제외가 페이지 경계(20개)와 커서에 들어가야 해서 같은 SQL 안에 있어야 한다. 사진 조건은 005 카드 SQL과 같은 `uq_image_profile_current` 술어다 | 회원 번호만 읽고 001 `MemberQueryService`·media `ProfileImageQuery`로 채우면 SQL이 4번이 되고(FR-023 위반), 유예 회원을 나중에 빼면 한 페이지가 20개보다 적어지거나 커서가 틀어진다 |
| `discovery.infra.PostCardQueryRepository`가 `follow`를 읽는다 | 피드 카드를 카드 SQL 한 번으로 만들려면 `EXISTS (follow)` 조건이 같은 SQL에 있어야 한다. 005가 `member`·`image`, 008이 `post_tag`를 같은 예외로 읽는다 | interaction이 팔로우한 회원 번호 목록을 먼저 주고 `author_id = ANY(:ids)`로 읽는 방식은 SQL이 2번이고(FR-023 "한 번의 조회"), 팔로우 수천 명이면 큰 배열을 매번 보낸다 |
