# Implementation Plan: 좋아요와 조회수

**Branch**: `009-like-view` (작업 브랜치 `tier-b-specs`) | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-like-view/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

이메일 인증한 회원은 읽을 수 있는 남의 글에 좋아요를 누르고 취소하며, 동시에 여러 번 눌러도 1인 1글 1건이고 좋아요 수는 언제나 실제 건수와 같다(C-LIKE-1). 글 상세가 보이는 상태로 1초 지나면 조회가 기록되고, 같은 방문자는 설정한 기간 안에 정해진 횟수까지만 세며, 모은 조회는 1분 안에 누적·일별 조회수에 반영된다(C-VIEW-1). 일별 조회수는 90일 보관한다.

기술 접근 (상세 근거는 [research.md](./research.md)):

- **스키마 변경 없음.** V1 `post_like`(PK `(post_id, member_id)`, 글 CASCADE·회원 RESTRICT), `post_view_daily`(PK `(post_id, view_date)`, `views > 0`), `post.like_count`·`view_count`(`ck_post_counts`)를 그대로 쓴다.
- **좋아요 = 상태 지정 + 한 트랜잭션.** `PUT`/`DELETE /api/posts/{postId}/like`. 트랜잭션에서 `INSERT … ON CONFLICT DO NOTHING`(또는 `DELETE`)의 영향 행 수가 1일 때만 007이 만든 post 공개 Service `PostCounterService.adjustLikeCount(±1)`를 부르고, 같은 트랜잭션에서 최신 수를 읽어 돌려준다. 실제로 바뀐 경우에만 `PostLiked`/`PostUnliked`를 발행한다(R1·R2).
- **판정 순서**(README 2026-10-07): 401 → 403(`AccountStatusGuard` `CONTENT_WRITE`) → 404(004 `PostReadService.requireReadable` + `PUBLISHED`) → 400 `CANNOT_LIKE_OWN_POST` → 429 `TOO_MANY_REQUESTS`(좋아요·취소 합쳐 1분 60번, 001 `RateLimiter`, 트랜잭션 밖). 관리자도 일반 회원과 같다.
- **조회 = Redis 집계 → 1분 반영.** `POST /api/posts/{postId}/views`는 항상 204. 방문자 키(`m:{회원}` / `v:{쿠키}` / `h:{SHA-256(IP + UA + 오늘의 비밀값)}`)로 Lua 한 번에 중복 판정(`INCR` + 첫 회 `EXPIRE`)과 `HINCRBY view:pending:{yyyyMMdd}`를 한다. 작성자 본인·관리자·봇·미리 불러오기·볼 수 없는 글(404)은 세지 않는다. Redis 장애면 건너뛰고 204(R4~R7).
- **방문자 쿠키는 상세를 열 때 미리 준다.** 조회 기록 요청에서 처음 쿠키를 주면 첫 조회와 두 번째 조회의 방문자 키가 달라져 한 번 더 센다. 그래서 상세 API·글 상세 페이지 셸 응답에 `vid` 쿠키가 없으면 붙이는 필터를 둔다(R5).
- **반영은 묶음 이름 바꾸기 + 글마다 트랜잭션.** 1분마다(ShedLock) `view:pending:*`를 `view:processing:{날짜}:{uuid}`로 `RENAME`하고, 남아 있던 처리 중 묶음을 먼저 처리한다. 글마다 한 트랜잭션에서 `PostCounterService.addViews`(`updated_at` 그대로) + `post_view_daily` upsert, 커밋 뒤 `HDEL`. 완전 삭제된 글은 건너뛴다(R8).
- **보정·보관 배치.** 매일 새벽 좋아요 수 보정(`like_count <> 실제 건수`만 고침, 0건이 아니면 WARN)과 일별 조회 90일 보관 정리(R9).
- **화면.** 005 `ReactionBar`의 `likeButton` 자리에 `LikeButton`(즉시 반영, 0.3초 동안 마지막 상태만 전송, 실패하면 되돌림 + "좋아요를 반영하지 못했어요")을 넣고, 005 `PostLikeStatusQuery` 기본 구현(항상 false)을 실제 조회로 바꾼다. 조회 기록은 005 `useViewBeacon`이 이미 보낸다(R10).
- **다른 기능에 주는 것.** `PostLiked`·`PostUnliked` 이벤트(011·012), `LikePurgeService.purgeByMember`(015 `WithdrawalPurgeStep` order 30이 부름).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 6 + React 18 (화면)

**Primary Dependencies**:

- 서버(기존): Spring Boot 4.1.1(Web MVC, Security, Session Data Redis, Validation), `JdbcClient`, `StringRedisTemplate` + Lua(`RedisScript`), Flyway, ShedLock JDBC(V2), Resilience4j(`RedisGuard`), 001 `RateLimiter`·`AccountStatusGuard`·`ClientIp`, 004 `PostReadService`, 007 `PostCounterService`
- 새 의존성 없음
- 화면: React 18, 005 `ReactionBar`·`useViewBeacon`·`recordPostView`, 001 로그인 `returnTo`

**Storage**:

- PostgreSQL: `post_like`, `post_view_daily`(interaction 모듈 소유), `post.like_count`·`view_count`(post 모듈 `PostCounterService`로만)
- Redis: 좋아요 제한 `ratelimit:like:{memberId}`, 조회 제한 `ratelimit:view:{visitorKey}`, 중복 판정 `view:seen:{postId}:{visitorKey}`(TTL = 기간), 모음 `view:pending:{yyyyMMdd}`(Hash postId → n), 처리 중 `view:processing:{yyyyMMdd}:{uuid}`, 하루 비밀값 `view:salt:{yyyyMMdd}`(TTL 26시간)
- 브라우저 쿠키: `vid`(UUID, 1년, `HttpOnly`·`Secure`·`SameSite=Lax`, Path `/`)

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc. 화면은 Vitest + Testing Library(가짜 타이머), 종단 확인은 Playwright. 헌법 VIII에 따라 동시성(20번·50명·섞기)·반영 중단 복구·장애 통과는 실제 DB·Redis 통합 테스트로 확인한다

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO + Mailpit), 최신 데스크톱·모바일 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA)

**Performance Goals**:

- 좋아요 p95 100ms 이내(제한 Redis 1번 + 트랜잭션 SQL 3~4번), 조회 기록 p95 30ms 이내(Lua 1번, DB 조회는 글 확인 1번)
- 조회 반영 1분 주기, 한 번에 글 1만 개까지 1분 안(글마다 트랜잭션 1개)
- 상세 응답 속도는 조회 몰림과 무관(조회 기록이 상세 경로에 없음)

**Constraints**:

- 좋아요 기록과 수 변경은 같은 트랜잭션. 트랜잭션 안에서 Redis를 쓰지 않는다
- 조회수 기록(Redis 키·DB·조회수 로그)에 원래 IP와 방문자 구분 값을 남기지 않는다(FR-034, SC-010)
- 조회 반영은 `post.updated_at`을 바꾸지 않는다(FR-030)
- 좋아요 응답은 볼 수 없는 글을 드러내지 않는다(404 본문 고정)
- 화면은 375px 폭부터 가로 스크롤 없음

**Scale/Scope**:

- 글 1만 건, 회원 수천 명, 인기 글에 분당 조회 수천 건 기준
- API 3개(좋아요 지정·취소, 조회 기록), 배치 3개(조회 반영 1분, 좋아요 보정 매일, 일별 보관 정리 매일)
- 이벤트 2종, 화면 컴포넌트 1개(`LikeButton`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | V1 테이블·컬럼·인덱스 그대로. 조회 중복 기준은 설정값(공통 24시간 1회, 강성찬 30분 5회는 설정만) |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS** | `post_like`·`post_view_daily`는 interaction 소유. `post.like_count`·`view_count`는 post 공개 Service `PostCounterService`(007이 만들고 이 기능이 메서드를 더함)로만 바꾼다. 글 확인은 004 `PostReadService` |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 판정 순서 고정, 볼 수 없는 글은 같은 404, 자기 글은 400. 조회 기록도 볼 수 없는 글은 404. 권한 매트릭스에 `post.like`·`post.unlike`·`post.view` 행(R12) |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 사용자 입력 없음(숫자 경로와 쿠키 UUID만, UUID 형식이 아니면 무시) |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 조회 기록 실패·Redis 장애는 204로 건너뛰고 상세는 정상. 좋아요 제한은 Redis 장애 때 통과. 화면 좋아요 실패는 되돌림 + 안내만 |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 반영 중단에도 모은 조회를 잃지 않는다(처리 중 묶음 재처리). 일별 조회는 90일 뒤 삭제, 좋아요는 글 완전 삭제·탈퇴 정리 때만 삭제 |
| VII. 수치는 설정값으로 | **PASS** | `blog.like.rate-limit`, `blog.view.dedupe-window`·`max-per-window`·`rate-limit`·`bot-user-agent-words`·`flush-interval`·`daily-retention`, 배치 시각 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | 동시 20번·50명·섞기 3회(SC-001·002), 반영 중단 복구(SC-009), 장애 중 상세 정상(SC-008), IP 미저장 검사(SC-010) |

**Gate 결과 (Phase 0 전)**: 위반 없음. Complexity Tracking은 필요 없다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. `vid` 쿠키를 상세 API·페이지 셸 응답에 붙이는 필터는 005 경로에 응답 헤더만 더하고 005 코드를 바꾸지 않는다(R5).
  2. spec Implementation Notes의 Redis 키 `rate:like:{memberId}`는 001·002 규칙(`ratelimit:` 접두어)에 맞춰 `ratelimit:like:{memberId}`로 쓴다.
  3. 007보다 이 기능이 먼저 구현되면 `PostCounterService`를 이 기능이 만들고 007이 댓글 메서드를 더한다(어느 쪽이 먼저든 한 클래스).

## Project Structure

### Documentation (this feature)

```text
specs/009-like-view/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 좋아요 지정·취소, 조회 기록 REST 계약
│   └── view-pipeline.md # 방문자 키·Lua·반영 배치·보정·보관, 이벤트, 탈퇴 정리 Service
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/team/blog/
├── interaction/
│   ├── web/
│   │   ├── LikeController.java                 # PUT·DELETE /api/posts/{postId}/like
│   │   ├── ViewController.java                 # POST /api/posts/{postId}/views → 204
│   │   └── VisitorIdCookieFilter.java          # GET /api/posts/{id}·/@{h}/posts/{id} 응답에 vid 쿠키
│   ├── application/
│   │   ├── LikeService.java                    # like / unlike (판정 순서·트랜잭션·이벤트)
│   │   ├── LikeStatusQueryAdapter.java         # 005 PostLikeStatusQuery 구현 Bean
│   │   ├── LikeReconcileJob.java               # 매일 새벽 좋아요 수 보정 (ShedLock)
│   │   ├── LikePurgeService.java               # purgeByMember — 015 order 30이 부름
│   │   ├── ViewRecordService.java              # 제외 판정 → 방문자 키 → Redis 기록
│   │   ├── VisitorKeyResolver.java             # m:/v:/h: (하루 비밀값)
│   │   ├── ViewFlushJob.java                   # 1분 반영 (ShedLock)
│   │   ├── ViewDailyRetentionJob.java          # 매일 새벽 90일 정리 (ShedLock)
│   │   ├── LikeProperties.java / ViewProperties.java
│   ├── domain/LikeReasonCode.java              # CANNOT_LIKE_OWN_POST (400)
│   └── infra/
│       ├── LikeRepository.java                 # insertIfAbsent / deleteIfPresent / exists / reconcile
│       ├── ViewDailyRepository.java            # upsert / deleteOlderThan
│       └── RedisViewStore.java                 # Lua 기록·RENAME·HSCAN·HDEL·비밀값
├── post/application/PostCounterService.java    # + adjustLikeCount / likeCount / addViews (007이 만든 클래스)
└── shared/event/PostLiked.java, PostUnliked.java

backend/src/main/resources/
├── application.yml                             # blog.like.*, blog.view.*
├── policy/view-bot-user-agents.txt             # 봇·미리보기 단어 목록
└── redis/view-record.lua                       # 중복 판정 + 모음

backend/src/test/
├── resources/permission/like-view.csv          # post.like·post.unlike·post.view
└── java/com/team/blog/interaction/
    ├── unit/VisitorKeyResolverTest.java, ViewExclusionTest.java
    └── integration/
        ├── LikeApiIT.java                      # US1·US2
        ├── LikeConcurrencyIT.java              # SC-001·SC-002
        ├── LikeReconcileJobIT.java             # FR-005·SC-003
        ├── ViewRecordIT.java                   # US3: 중복·제외·204 동일·429·관리자
        ├── ViewFlushJobIT.java                 # US3 #8, US4: 반영·중단 복구·삭제된 글·updated_at
        ├── ViewRedisOutageIT.java              # SC-008
        ├── ViewPrivacyIT.java                  # SC-010
        ├── ViewDailyRetentionJobIT.java        # SC-011
        └── LikeViewPermissionMatrixIT.java     # 004 하네스

frontend/src/
├── api/likes.ts                                # putLike / deleteLike
├── features/like/
│   ├── useLikeToggle.ts                        # 즉시 반영·0.3초 마지막 상태·되돌림
│   └── likeMessages.ts
├── components/LikeButton.tsx                   # ♡/♥, aria-pressed, 비회원·인증 전 안내
├── components/formatCount.ts                   # 1,234 / 1.2만 (ReactionBar의 식을 꺼내 공용)
├── components/ReactionBar.tsx                  # likeButton 자리에 LikeButton, 좋아요 수 형식 공용 함수
└── pages/PrivacyPage.tsx                       # "조회수 중복 방지용 무작위 식별자" 문단 (001 소유 화면)
```

**Structure Decision**: 02 §3 package-by-feature 구조를 그대로 쓴다. 좋아요·조회 코드는 `interaction` 모듈에 두고, 다른 모듈에는 `PostCounterService` 메서드(post)와 이벤트 2종(shared)만 더한다. 화면은 005 `ReactionBar` 자리를 채우고 `useViewBeacon`은 바꾸지 않는다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (위반 없음).
