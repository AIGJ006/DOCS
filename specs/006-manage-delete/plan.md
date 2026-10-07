# Implementation Plan: 내 글 관리와 글 삭제·휴지통

**Branch**: `006-manage-delete` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-manage-delete/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

로그인한 회원이 자기 글만 [임시글]·[발행 글]·[휴지통] 세 탭으로 훑어보고(C-MANAGE-1), 글을 휴지통으로 옮기고·복구하고·영구 삭제하며, 휴지통에 30일 넘게 있는 글을 매일 새벽 배치가 완전히 지우게 한다(C-POST-5).

기술 접근 (상세 근거는 [research.md](./research.md)):

- **삭제 = 별도 컬럼.** `post.deleted_at`만 쓰고 `status`·`visibility`·`first_public_at`·`hidden_at`은 건드리지 않는다. 복구는 `deleted_at = NULL` 한 번이라 원래 탭·원래 위치로 돌아간다 (13 §2-1, D-4).
- **처리 방식.** 삭제·복구·영구 삭제는 `post` module의 `PostTrashService`가 `author_id = 현재 사용자` 조건으로 `SELECT … FOR UPDATE`를 건 뒤 처리한다. 남의 글과 없는 글은 같은 404로 응답한다 (13 §2-4, 42 §3).
- **휴지통으로 옮기기 전.** Redis에 있는 자동 저장분을 DB(`post` 또는 `post_draft`)에 먼저 반영하고, 그 뒤에 빈 임시글인지 판정한다. Redis 키는 커밋 후에 지운다 (13 §2-3, 04 §2-4).
- **완전 삭제.** `PostPurgeService` 하나를 영구 삭제, 30일 배치, 탈퇴 정리(015, order 10)가 함께 쓴다. 처리 순서는 13 §2-5를 따른다: ① 대기 중 신고 사건 종료 → ② 그 글에서만 쓰던 사진의 연결 해제 기록 → ③ `DELETE FROM post`(나머지는 FK CASCADE·SET NULL이 처리). ①과 ②는 다른 모듈의 테이블이다. 그래서 각 모듈이 `PostPurgeStep` 확장점을 구현하고 같은 트랜잭션 안에서 실행한다 (헌법 II).
- **관리 목록.** `GET /api/me/posts`가 목록 쿼리 1번과 (첫 요청에만) 개수 쿼리 1번으로 응답한다. 쿼리는 `ix_post_manage`와 `ix_post_trash` 인덱스를 쓰고 본문 컬럼은 읽지 않는다. 한 번에 20개를 보내며 21개를 조회해 다음 페이지가 있는지 판단한다. 커서는 불투명 Base64URL이다 (41 §5·§6, 10 §4-2).
- **새지 않게.** `Post` 엔티티에 `@SQLRestriction("deleted_at IS NULL")`을 건다. 공개 목록은 `VisibilityFilter`, 상세는 `PostAccessPolicy.canRead`가 삭제 여부를 가장 먼저 본다(004 소관). 권한 매트릭스 테스트에 "휴지통 글" 행을 추가한다 (13 §2-6).
- **이벤트.** 커밋 후 `PostTrashed`·`PostRestored`·`PostPurged`를 발행한다. 상태가 실제로 바뀐 경우에만 발행하고, 빈 임시글을 바로 지울 때는 발행하지 않는다 (20 §3-1, EV-4).

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript/JavaScript + React (화면)

**Primary Dependencies**:

- 서버: Spring Boot 3.x 이상(팀 확정), Maven, Spring Data JPA(Hibernate `@SQLRestriction`), Spring Security, Spring Session Data Redis, Flyway, Spring `@Scheduled` + ShedLock
- 같은 공통 스택에 있으나 이 기능은 쓰지 않음: MinIO(AWS SDK v2), commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer
- 화면: React (IndexedDB/localforage는 002 소관이며 이 기능은 쓰지 않음)

**Storage**:

- PostgreSQL(pg_trgm): `post`, `post_draft`, CASCADE로 함께 지워지는 `post_tag`·`comment`·`post_like`·`post_image`·`post_view_daily`·`notification`, `image.detached_at`, `report_case`
- Redis: 자동 저장 버퍼 `autosave:post:{postId}`·`autosave:dirty` (002 소유, 이 기능은 읽고 지우기만 함), Spring Session

**Testing**: JUnit 5, Testcontainers(PostgreSQL, Redis), Spring Security Test, MockMvc. 권한 매트릭스와 동시성은 통합 테스트로 확인한다 (헌법 VIII)

**Target Platform**: Linux 서버(Docker Compose: app + PostgreSQL + Redis + MinIO), 최신 모바일·데스크톱 브라우저

**Project Type**: web-service (모듈러 모놀리스 REST API + React SPA, 같은 도메인에서 서빙)

**Performance Goals**:

- 내 글 관리 목록 서버 응답 300ms 이내(글 1만 건, SC-005)
- 목록 쿼리는 1번, 첫 요청에만 개수 쿼리를 1번 더 실행한다. 글 수에 비례해 쿼리가 늘지 않는다(N+1 금지)

**Constraints**:

- 본문 컬럼(`content_md`·`content_html`)을 읽지 않는다
- 현재 사용자는 세션에서만 꺼낸다
- 볼 수 없거나 내 것이 아닌 글은 404로 응답한다
- 같은 글에 대한 요청은 행 잠금으로 직렬화한다
- 트랜잭션 안에서 외부 호출을 하지 않는다(사진 파일 삭제는 003 정리 배치가 맡는다)
- 화면은 375px 폭부터 가로 스크롤이 없어야 한다

**Scale/Scope**:

- 회원 1명의 글 최대 약 1만 건 기준
- 화면 1개(`/manage/posts`, 탭 3개), API 5개(목록 2·삭제·복구·영구 삭제), 배치 1개(휴지통 비우기)
- 도메인 이벤트 3종

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 공통 기반은 바꾸지 않고, 개인 확장은 추가만 | **PASS** | 51의 `post.deleted_at`·`hidden_at`·`ix_post_manage`·`ix_post_trash`와 FK 삭제 동작을 그대로 쓰며 컬럼·인덱스 변경이 없다. ShedLock 잠금 저장소는 51에 없다. JDBC 방식을 고르면 공통 시작 템플릿에 **새 테이블을 추가**한다(제안, research R13). 기존 테이블을 바꾸는 것이 아니므로 이 원칙에 맞다 |
| II. 모듈러 모놀리스, 모듈 경계 | **PASS (설계 조건부)** | 관리 목록은 `post` 테이블의 비정규화 숫자만 읽어 다른 모듈을 조회하지 않는다. 완전 삭제 때 `report_case`(신고 모듈)와 `image`·`post_image`(media)는 각 모듈이 구현한 `PostPurgeStep`이 처리한다. post 모듈은 그 테이블을 직접 건드리지 않는다. `comment`·`post_like`·`notification`이 지워지는 것은 51 ERD에 정한 FK `ON DELETE CASCADE` 동작이다. Repository를 직접 쓰는 것이 아니다 |
| III. 권한 두 겹, 404 (NON-NEGOTIABLE) | **PASS** | 모든 요청은 Service에서 `author_id = :me`로 다시 검사한다. 목록 API는 사용자를 가리키는 파라미터를 받지 않는다. 남의 글과 없는 글은 같은 404 `NOT_FOUND`로 응답한다. 휴지통 글은 작성자에게도 상세가 404다. 판정 순서는 42 §3을 따른다 |
| IV. 사용자 콘텐츠는 실행되지 않는다 | **PASS** | 목록은 제목만 주고 본문을 주지 않는다. React는 제목을 글자로만 렌더링한다(`dangerouslySetInnerHTML` 금지) |
| V. 부가 기능 실패가 쓰기·읽기를 막지 않는다 | **PASS** | 이벤트는 AFTER_COMMIT 비동기로 처리하며, 실패해도 삭제는 성공한다. 휴지통으로 옮길 때 Redis가 장애면 자동 저장 반영을 건너뛰고 삭제를 진행한다(경고 로그). 남은 버퍼는 002의 반영 배치가 나중에 DB에 넣는다(research R7) |
| VI. 데이터는 잃지 않고, 정책대로 지운다 | **PASS** | 휴지통 30일 뒤 자동 완전 삭제를 그대로 구현한다. 휴지통으로 옮기기 직전에 자동 저장분을 반영하고, 휴지통에 있는 동안 반응·작업본을 보존한다. 스키마 변경은 Flyway로만 한다(이 기능은 마이그레이션 없음, ShedLock 테이블만 제안) |
| VII. 수치는 설정값으로 | **PASS** | 보관 기간 30일, 페이지 크기 20, 배치 묶음 100, 배치 실행 시각을 `application.yml`의 `blog.post.trash.*`·`blog.manage.*`로 둔다 |
| VIII. 실제 DB로 통합 테스트 | **PASS** | Testcontainers PostgreSQL로 다음을 확인한다: 권한 매트릭스 "휴지통 글" 행, 남의 글 요청 전후 DB 값이 같은지, 동시 삭제·복구·배치 직렬화, 완전 삭제 후 연관 행 0건, EXPLAIN 인덱스 사용. 인수 시나리오는 테스트 이름과 1:1로 대응한다 |

**Gate 결과 (Phase 0 전)**: 위반 없음. Complexity Tracking은 필요 없다.

**설계 후 재확인 (Phase 1 후)**:

- data-model·contracts를 만든 뒤에도 위반은 없다.
- 새로 확인한 점:
  1. `GET /api/me/trash`를 별칭으로 남겨도 같은 Service·쿼리를 쓰므로 II·III에 영향이 없다.
  2. `PostPurgeStep` 확장점은 015의 `WithdrawalPurgeStep`(44 §4)과 같은 방식이어서 모듈 경계를 지킨다.
  3. 응답 스키마에 본문 필드가 없어 FR-012를 계약 수준에서 보장한다.
  4. 002의 자동 저장 API와 반영 배치에는 다음 조건이 필요하다(research R7). 다른 plan에 알릴 사항이지 위반은 아니다.
     - 자동 저장 API: 휴지통 글이면 404를 주도록 DB `deleted_at`을 확인한다.
     - 반영 배치: `deleted_at` 조건을 걸지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/006-manage-delete/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # 내 글 관리·삭제·복구·영구 삭제 REST 계약
│   └── events.md        # PostTrashed·PostRestored·PostPurged, 휴지통 비우기 배치
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/team/blog/
│   ├── post/
│   │   ├── web/
│   │   │   ├── ManagePostController.java       # GET /api/me/posts, GET /api/me/trash
│   │   │   └── PostTrashController.java        # DELETE /api/posts/{postId}, POST …/restore, DELETE …/permanent
│   │   ├── application/
│   │   │   ├── ManagePostQueryService.java     # 탭 목록·개수·커서
│   │   │   ├── PostTrashService.java           # trash / restore / purgePermanently (행 잠금·멱등)
│   │   │   ├── PostPurgeService.java           # 완전 삭제 공용 루틴 (영구 삭제·배치·탈퇴 order 10)
│   │   │   ├── TrashPurgeJob.java              # 매일 새벽 휴지통 비우기 (@Scheduled + ShedLock)
│   │   │   ├── PostWithdrawalPurgeStep.java    # 015의 WithdrawalPurgeStep order 10 → PostPurgeService 재사용
│   │   │   └── spi/PostPurgeStep.java          # 완전 삭제 전 다른 모듈이 끼워 넣는 단계 (order, beforePurge)
│   │   ├── domain/
│   │   │   ├── Post.java                       # deletedAt, moveToTrash(now), restore(), isEmptyDraft()
│   │   │   ├── ManageTab.java                  # DRAFTS / PUBLISHED / TRASH
│   │   │   └── ManageCursor.java               # (시각 µs, id, tab, vis) 값 객체
│   │   └── infra/
│   │       ├── PostRepository.java             # 엔티티 조회 (@SQLRestriction 적용)
│   │       ├── TrashPostRepository.java        # 휴지통 포함 조회·잠금·삭제 (네이티브 SQL)
│   │       └── ManagePostQueryRepository.java  # 목록·개수 네이티브/JPQL 투영 (본문 제외)
│   ├── media/application/
│   │   └── ImagePostPurgeStep.java             # PostPurgeStep order 20: 그 글에서만 쓰던 사진 detached_at 기록
│   ├── interaction/application/
│   │   └── CommentQueryService.java            # (공개 메서드 추가) commentIdsOfPost(postId) — 신고 종료용
│   └── shared/
│       ├── event/                              # PostTrashed, PostRestored, PostPurged (record)
│       ├── web/cursor/CursorCodec.java         # (001 T021 소유) {"v":1,"l":"manage:{tab}[:{filter}]","k":[…]} Base64URL — ListScope 공용
│       └── error/                              # NotFoundException→404, INVALID_CURSOR 등 (공통)
│   # 신고 모듈(014 소관, 패키지 이름은 014 plan이 정함)에 ReportPostPurgeStep(order 10)을 둔다 — research R11
├── src/main/resources/
│   ├── application.yml                         # blog.post.trash.*, blog.manage.page-size
│   └── db/migration/                           # 이 기능의 새 마이그레이션 없음 (ShedLock 테이블은 공통 템플릿 제안)
└── src/test/java/com/team/blog/
    ├── post/unit/                              # Post 도메인(빈 임시글 판정, 상태 전이), ManageCursor
    └── post/integration/
        ├── ManagePostApiIT.java                # US3: 탭·개수·정렬·필터·[더 보기]·본인 글만
        ├── PostTrashApiIT.java                 # US1·US2·US4: 삭제·복구·영구 삭제·404·멱등
        ├── PostPurgeIT.java                    # 완전 삭제 연쇄·신고 종료·사진·태그 유지
        ├── TrashPurgeJobIT.java                # 30일 경과·묶음 처리·복구와 경합
        ├── TrashConcurrencyIT.java             # 동시 삭제/복구/영구 삭제/배치 직렬화
        └── TrashedPostPermissionMatrixIT.java  # 06 §8·42 §5 "휴지통 글" 행 (상세·목록·태그·검색·sitemap·글 수)

frontend/src/
├── pages/ManagePostsPage.tsx                   # /manage/posts?tab=&visibility= (로그인 필요)
├── features/manage-posts/
│   ├── ManageTabs.tsx                          # 탭 + 글 수 (첫 응답 counts, 이후 화면에서 ±1)
│   ├── DraftRow.tsx / PublishedRow.tsx / TrashRow.tsx
│   ├── useManagePosts.ts                       # 커서 [더 보기], 이미 있는 ID 건너뛰기
│   └── confirmDialogs.ts                       # 휴지통·영구 삭제·공개로 바꾸기·변경 취소 확인 문구
├── components/ConfirmDialog.tsx, Toast.tsx
└── api/managePosts.ts                          # 이 기능 API + 002/004 API 호출(새 글·변경 취소·공개 범위)

docker-compose.yml                              # app + PostgreSQL + Redis + MinIO (공통 템플릿)
```

**Structure Decision**: 02 §3의 package-by-feature 구조(`account·post·tag·media·interaction·discovery·shared`, 각 모듈은 `web/application/domain/infra`)를 그대로 쓴다. 이 기능의 코드는 대부분 `post` 모듈에 있다. 다른 모듈에는 확장점 구현체 하나씩만 추가한다: media의 `ImagePostPurgeStep`, interaction의 공개 조회 메서드, 신고 모듈의 `ReportPostPurgeStep`. 공개 범위 변경(004, `PUT /api/posts/{postId}/visibility` — 004 plan 결정), 새 글·변경 취소(002), 공개 목록·상세 404(004·005)는 각 spec의 코드를 화면에서 호출만 한다.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

해당 없음 (위반 없음).
