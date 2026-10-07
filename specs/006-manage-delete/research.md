# Research: 내 글 관리와 글 삭제·휴지통

**Feature**: `006-manage-delete` | **Date**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

각 항목의 형식은 Decision / Rationale / Alternatives considered이다. 각 항목은 다음 세 가지 중 하나로 표시했다.

- **확정**: 원문 또는 팀 결정에 이미 있는 것
- **제안(팀 확인 필요)**: 원문에 없어 이 plan이 합리적 기본값을 고른 것
- **미결 — 기본안**: spec에 `[NEEDS CLARIFICATION]`으로 남은 것. 이번에는 해당 항목이 없다(R0)

---

## R0. 남은 NEEDS CLARIFICATION

- **Decision**: 없음. 체크리스트에 남아 있던 FR-004(탭 옆 글 수, 검색·일괄 처리 제외)는 spec `## Clarifications` 2026-10-07에서 "글 수를 보여 주고, 검색·일괄 처리는 범위 밖"으로 **확정**되었다. README "정해진 것" 표에도 같은 내용이 있다.
- **Rationale**: spec 맨 위 Clarifications와 README 결정 표는 확정 사항이다.
- **Alternatives considered**: 없음.

## R1. 글 삭제 방식 — 휴지통 30일 후 자동 완전 삭제 (확정)

- **Decision**: 삭제하면 휴지통으로 옮기고(`deleted_at = now()`), 30일이 지나면 매일 새벽 배치가 완전히 지운다. 휴지통 글은 [영구 삭제]로 즉시 지울 수 있다.
- **Rationale**: 13 D-1과 01 Q3(2026-10-02)에서 결정되었다. 실수로 지운 글을 되살릴 기회를 주면서 데이터가 쌓이지 않게 한다.
- **Alternatives considered**: 즉시 완전 삭제(되살릴 수 없음), 영구 소프트 삭제(데이터가 계속 쌓임). 둘 다 13에서 기각되었다.

## R2. 삭제 표시는 `status`와 분리된 `deleted_at` (확정)

- **Decision**: `post.deleted_at`(nullable) 하나만 쓴다. 휴지통으로 옮겨도 `status`·`visibility`·`first_public_at`·`published_at`·`edited_at`·`hidden_at`은 바꾸지 않는다. 복구는 `deleted_at = NULL`이다.
- **Rationale**: 13 §2-1·D-4에 따라 복구하면 원래 상태와 원래 목록 위치로 돌아가야 한다. 공개 목록은 `first_public_at`으로 정렬하므로, 이 값을 건드리지 않으면 위치가 유지된다(SC-002).
- **Alternatives considered**: `status = 'TRASHED'` 값을 추가하는 방식. 원래 상태를 따로 저장해야 하고 `ck_post_status`도 바꿔야 해서 헌법 I에 어긋난다.

## R3. 휴지통으로 옮기기·복구 때 `updated_at`을 바꾸지 않는다 (제안(팀 확인 필요))

- **Decision**: 휴지통 이동·복구·멱등 재삭제는 `updated_at`을 갱신하지 않는다. 휴지통으로 옮기기 직전 자동 저장 반영(R7)이 실제로 내용을 바꾼 경우에만 그 반영이 `updated_at`을 갱신한다.
- **Rationale**: 임시글·발행 글 탭은 `updated_at` 최신순으로 정렬한다(41 M-4). 복구할 때 `updated_at`이 바뀌면 복구한 글이 탭 맨 위로 올라가 "원래 위치로"(FR-027, US2-2)를 어기게 된다. 13·41은 이 컬럼을 언급하지 않는다.
- **Alternatives considered**: 행이 바뀔 때마다 `updated_at = now()`로 두는 방식(06 §4의 공개 범위 변경은 이렇게 한다). 공개 범위 변경은 "수정 행위"라 위로 올라가는 것이 맞지만, 삭제·복구는 내용 변경이 아니어서 이 방식을 쓰지 않는다.

## R4. 소유 검사와 행 잠금 (확정)

- **Decision**: 모든 쓰기는 하나의 트랜잭션에서 다음 쿼리로 시작한다. 결과가 없으면 `NotFoundException`(404 `NOT_FOUND`)을 던진다. 상태 조건(휴지통 여부)은 잠근 뒤 Java에서 판정한다.
  ```sql
  SELECT id, status, title, content_md, deleted_at, edit_version
  FROM post
  WHERE id = :postId AND author_id = :me
  FOR UPDATE
  ```
  - `@SQLRestriction`을 우회해야 하므로 이 쿼리는 `TrashPostRepository`의 네이티브 SQL로 둔다.
  - 빈 임시글을 판정하는 경우에만 `content_md`를 읽는다. 목록 쿼리는 본문을 읽지 않는다.
- **Rationale**: 근거는 13 §2-4("모든 요청은 `author_id = 현재 사용자` 조건으로 행 잠금 후 처리"), 02 §5(소유 검사), 42 §3 ③·④(조회 한 번으로 함께 처리)다. 같은 글에 동시에 온 삭제·복구·영구 삭제·배치를 직렬화한다(FR-036).
- **Alternatives considered**:
  - 낙관적 잠금(`edit_version`): 삭제는 편집 버전과 무관하다. 자동 저장과 충돌해 409를 낼 이유가 없다.
  - 조건부 UPDATE 한 문장(`UPDATE … WHERE deleted_at IS NULL`): 복구에는 충분하다. 휴지통 이동은 자동 저장 반영·빈 글 판정 단계가 있어 잠금이 필요하다. 일관성을 위해 세 동작 모두 잠금 방식으로 맞춘다.

## R5. 판정 순서와 계정 상태 (확정)

- **Decision**: 판정 순서는 42 §3을 따른다. 처음 걸린 단계의 응답을 준다.
  1. 로그인 확인 → 비회원은 401 `LOGIN_REQUIRED`
  2. 계정 상태 확인 → 001 `AccountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP)`(001 T037). 탈퇴 유예 회원은 403 `ACCOUNT_WITHDRAWN`. **이메일 인증 전 회원은 삭제·복구·영구 삭제·관리 목록을 허용한다**(42 §5-2·§10). 정지 회원은 새로 로그인할 수 없지만(P-7), 정지 직후 남은 세션의 요청은 403 `ACCOUNT_SUSPENDED`(H7)
  3. 볼 수 있는가 + 4. 권한이 있는가 → `author_id = :me` 조회 한 번으로 판단하며, 없으면 404
  4. 업무 규칙 → 복구·영구 삭제인데 휴지통 글이 아니면 404(13 §2-4, 42 §5-2 "휴지통 글만, 아니면 404")
- **Rationale**: README 결정 표(2026-10-07)에 "판정 순서는 로그인 → 계정 상태 → 볼 수 있나 → 자기 글 → 요청 횟수(42 §3)"로 확정되어 있다. 이 기능에는 요청 횟수 제한이 없다.
- **Alternatives considered**: 인증 전 회원의 삭제를 막는 방식. 42 결정 기록(2026-10-03)에 "지우는 것은 남에게 해를 주지 않음"으로 기각되었다.

## R6. 멱등성 — 이미 휴지통인 글을 다시 삭제할 때 (확정 + 세부 제안)

- **Decision**: 이미 휴지통에 있는 글에 `DELETE /api/posts/{postId}`가 오면 아무것도 바꾸지 않는다. 응답은 200 `{ trashed: true, purgeAt }`이고, **`purgeAt`은 기존 `deleted_at + 30일`이다(재설정하지 않는다)**. 이벤트도 발행하지 않는다. 앞부분은 확정(13 §2-4, FR-025)이고 `purgeAt` 유지는 제안(팀 확인 필요)이다.
- **Rationale**: 13 §2-4의 "이미 휴지통이면 그대로"를 따른다. 다시 누를 때마다 30일이 늘어나면 FR-025("아무것도 바꾸지 않고")에 어긋난다. 이벤트는 상태가 실제로 바뀐 경우에만 발행한다(20 EV-4).
- **Alternatives considered**: 409를 돌려주는 방식. 다른 탭에서 이미 지운 경우 사용자에게 실패로 보이므로 쓰지 않는다. 13은 성공으로 정했다.

## R7. 휴지통으로 옮기기 직전 자동 저장 반영 (확정 + 장애·경합 처리 제안)

- **Decision**: `PostTrashService.trash()`의 처리 순서는 다음과 같다.
  1. 행 잠금
  2. Redis `autosave:post:{postId}`를 읽는다. `memberId`가 일치하고 `version > 현재 DB 버전`이면 002의 반영 규칙과 같은 SQL로 DB에 반영한다(임시글은 `post`, 발행 글은 `post_draft` UPSERT, 04 §2-4)
  3. 빈 임시글인지 판정한다(R8)
  4. `deleted_at = now()` 또는 완전 삭제
  5. 커밋
  6. AFTER_COMMIT에서 Redis 키를 지운다. 반영한 버전 이하일 때만 지우는 조건부 Lua를 쓴다(002의 발행 후 삭제와 같은 스크립트). 그리고 `autosave:dirty`에서 postId를 뺀다.
- 제안(팀 확인 필요):
  - **Redis 장애 시**: 2단계를 건너뛰고 경고 로그를 남긴 뒤 삭제를 진행한다. 남은 버퍼는 Redis가 돌아오면 002의 1분 반영 배치가 DB에 넣는다.
  - **002 plan에 요청할 사항 두 가지**:
    - (a) 반영 배치 SQL에 `deleted_at IS NULL` 조건을 **넣지 않는다**. 휴지통에 들어가기 전에 받아들인 내용도 휴지통 글에 보존하기 위해서다(FR-022·FR-024).
    - (b) 자동 저장·수동 저장 API는 Redis 키가 있어도 **DB에서 `deleted_at IS NULL`을 확인한다**. 휴지통 글이면 404를 준다(FR-023, 엣지 케이스 "휴지통으로 옮긴 뒤 다른 탭의 자동 저장 → 404"). 04 §2-3 ①은 "Redis memberId로 소유자 확인, 키가 없을 때만 DB"로 되어 있어, Redis 키가 남아 있으면 휴지통 글에도 저장이 받아들여질 수 있다.
  - 완전 삭제된 글의 Redis 버퍼를 반영 배치가 반영하려 하면 FK 위반(23503)이 난다. 이때는 키를 버리도록 002에 알린다.
- **Rationale**: 13 §2-3, 04 §2-4, 02 §4-1("Redis 자동 저장 키는 커밋 후 삭제"), 헌법 V·VI. Redis는 트랜잭션 밖 저장소다. 키를 커밋 전에 지우면 롤백될 때 내용을 잃는다.
- **Alternatives considered**:
  - Redis 장애 시 삭제를 거부(503)하는 방식. 부가 저장소 장애로 사용자의 삭제를 막게 되어 헌법 V에 어긋난다.
  - 휴지통 이동 전에 Redis 키만 지우는 방식. 최대 30초분의 내용을 잃는다.

## R8. 빈 임시글 판정 (확정 + 판정식 제안)

- **Decision**: `status = 'DRAFT'`이고 R7 반영 **뒤의** 값이 다음을 만족하면 휴지통을 거치지 않고 `PostPurgeService`로 바로 완전 삭제한다.
  ```sql
  length(btrim(title, E' \t\r\n')) = 0 AND length(btrim(content_md, E' \t\r\n')) = 0
  ```
  응답은 `{ purged: true }`이고 이벤트는 없다. 발행 글은 이 판정을 하지 않는다. 판정식(공백만 있으면 빈 것으로 봄)은 제안(팀 확인 필요)이다. spec Assumptions에 따라 002의 빈 임시글 정리 배치(04 §2-5)와 **같은 함수**(002 `EmptyDraftPolicy.isEmpty(title, contentMd)`, 002 T020 — 공백 문자 집합 `" \t\r\n"`)를 쓴다. PostgreSQL 인자 없는 `btrim`은 U+0020만 지우므로 SQL은 `btrim(x, E' \t\r\n')`로 쓴다.
- **Rationale**: 13 D-2, FR-020, 20 §3-1(아무에게도 보인 적 없는 글이라 이벤트 없음). 반영 뒤에 판정해야, 다른 탭에서 방금 입력한 내용이 있는 글을 빈 글로 잘못 지우지 않는다.
- **Alternatives considered**: 원문 그대로 빈 문자열(`''`)만 빈 것으로 보는 방식. 공백만 있는 글이 휴지통에 쌓인다. 04 배치와 기준이 다르면 결과가 엇갈린다.

## R9. 지운 글이 새지 않게 (확정)

- **Decision**:
  1. `Post` 엔티티에 Hibernate `@SQLRestriction("deleted_at IS NULL")`을 건다.
  2. 공개 목록은 `VisibilityFilter` 공용 조건만 쓴다(`deleted_at IS NULL AND hidden_at IS NULL AND 작성자 withdrawn_at IS NULL`, 004 소관).
  3. `PostAccessPolicy.canRead`는 삭제 여부를 먼저 확인한다(004 소관).
  4. 휴지통 조회·잠금·완전 삭제만 `TrashPostRepository`의 네이티브 SQL로 둔다.
  5. 06 §8·42 §5-1 권한 매트릭스 통합 테스트에 "휴지통 글" 행을 추가한다. 비회원·다른 회원·작성자·관리자 각각에 대해 상세·홈·블로그·블로그 글 수·태그·검색·sitemap·댓글·좋아요를 확인한다.
- **Rationale**: 13 §2-6, 06 R-1·R-2a, 02 §4-2. 이 plan은 3·5의 테스트 행 추가와 4의 우회 경로를 책임진다. 1~3의 공용 코드는 004 plan이 만든다.
- **Alternatives considered**: 쿼리마다 `deleted_at IS NULL`을 손으로 붙이는 방식. 하나라도 빠뜨리면 새므로 기각한다(06 R-2).

## R10. 완전 삭제 순서와 모듈 경계 — `PostPurgeStep` 확장점 (순서 확정, 구조는 제안(팀 확인 필요))

- **Decision**: `PostPurgeService.purge(postId)`는 호출한 쪽의 트랜잭션(`Propagation.REQUIRED`) 안에서 다음을 실행한다.
  1. Spring 빈 `List<PostPurgeStep>`을 `order()` 순으로 `beforePurge(postId)` 호출한다.
     - order 10: 신고 모듈 `ReportPostPurgeStep`. 대기 중 사건을 `CLOSED_NO_TARGET`으로 바꾼다.
     - order 20: media `ImagePostPurgeStep`. 그 글에서만 쓰던 사진에 `detached_at`을 기록한다.
  2. `DELETE FROM post WHERE id = :postId`를 실행한다. 이때 FK가 다음과 같이 처리된다.
     - CASCADE: `post_tag`·`comment`(답글은 복합 부모 FK)·`post_like`·`post_image`·`post_draft`·`post_view_daily`·`notification`
     - SET NULL: `report_case.post_id`·`comment_id`
  3. 영구 삭제·배치이면 `PostPurged(postId, authorId)`를 등록한다(빈 임시글이면 생략).
  4. AFTER_COMMIT에 Redis 자동 저장 키를 지운다.

  인터페이스는 다음과 같다.
  ```java
  int order();
  void beforePurge(long postId);
  ```
  015의 `WithdrawalPurgeStep`(44 §4)과 같은 방식이다.
- **Rationale**:
  - 순서는 13 §2-5와 같다. 사진 연결 정보(`post_image`)와 사건의 대상 ID(`post_id`)는 DELETE와 함께 CASCADE·SET NULL로 사라지므로 **DELETE 전에** 처리해야 한다.
  - 헌법 II·02 §1에 따라 post 모듈이 `report_case`·`image` 테이블에 직접 쓰지 않는다.
  - 이벤트는 유실될 수 있어(20 EV-2) 데이터 삭제에 쓰지 않는다.
- **Alternatives considered**:
  - (a) 13 §2-5의 SQL 세 문장을 post 모듈에서 그대로 실행하는 방식. 가장 단순하지만 다른 모듈 테이블을 직접 갱신해 헌법 II에 어긋난다.
  - (b) `PostPurged` 이벤트를 받은 리스너가 신고·사진을 처리하는 방식. 커밋 후에는 `post_id`가 이미 NULL이라 대상을 찾을 수 없고, 유실될 수도 있다.

## R11. 신고 사건 종료 단계의 소유와 구현 시점 (제안(팀 확인 필요))

- **Decision**:
  - `ReportPostPurgeStep`(order 10)은 신고 모듈(014, Tier C)이 소유한다. 다만 C-POST-5 #3은 Tier A 완료 기준이다. 014보다 006을 먼저 구현하면, **006 tasks에서 신고 모듈 패키지에 이 단계 하나만 먼저 만든다.** `report_case` 테이블은 V1에 있으므로 스키마 변경은 없다.
  - SQL은 13 §2-5 0단계 그대로다:
    ```sql
    UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL
    WHERE status = 'PENDING' AND (post_id = :id OR comment_id = ANY(:commentIds))
    ```
  - 댓글 ID 목록은 interaction 모듈의 공개 메서드 `CommentQueryService.commentIdsOfPost(postId)`로 받는다. 댓글 기능(007) 전에는 빈 목록이다.
  - 사건 종료는 이벤트를 발행하지 않는다(014 Implementation Notes: `CLOSED_NO_TARGET`은 이벤트 없음).
- **Rationale**:
  - 이 방식이 Tier 순서(헌법 워크플로)와 모듈 경계(헌법 II)를 함께 지킬 수 있다.
  - 댓글 대상 사건은 `ck_report_case_target_ref`에 따라 `post_id`가 NULL이다. 그래서 글 ID만으로는 찾을 수 없고 댓글 ID가 필요하다.
  - 신고 모듈의 패키지 이름은 02 §3 목록에 없다(20 §3-5는 `moderation`으로 적음). 014 plan이 정한다.
- **Alternatives considered**: 014 구현 때까지 이 단계를 비워 두는 방식. 신고 기능이 없으면 사건 행도 생기지 않으므로 실제 피해는 없다. 다만 통합 테스트(US4-5)를 006에서 검증할 수 없다.

## R12. 사진 연결 해제 SQL (확정 + 표현 제안)

- **Decision**: `ImagePostPurgeStep`(media 소유, order 20)은 13 §2-5 1단계와 같은 결과를 내는 SQL을 쓴다. `NOT IN` 대신 `NOT EXISTS`를 쓰고 `ix_post_image_image`를 탄다. 이 표현 방식은 제안이다.
  ```sql
  UPDATE image i SET detached_at = now()
  WHERE i.detached_at IS NULL
    AND EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = i.id AND pi.post_id = :id)
    AND NOT EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = i.id AND pi.post_id <> :id)
  ```
  파일 삭제는 003의 정리 배치가 7일 뒤 트랜잭션 밖에서 한다(04 §4-4). 사진이 휴지통에 있는 다른 글에도 연결되어 있으면 유지한다(그 글도 아직 복구될 수 있다).
- **Rationale**: FR-032, 13 §2-5. `NOT IN (subquery)`는 NULL이 끼면 의미가 바뀌고 계획이 나빠질 수 있다.
- **Alternatives considered**: 즉시 파일 삭제. 트랜잭션 안에서 외부 호출을 하게 되어(02 §4-1) 기각한다.

## R13. 휴지통 비우기 배치 (확정 + 세부 제안)

- **Decision**:
  - **확정**: 매일 새벽 실행하고, 대상은 `deleted_at < now() - interval '30 days'`이다. 100개씩 처리하며 ShedLock으로 한 서버에서만 실행한다(13 §2-5, FR-030).
  - **제안(팀 확인 필요)**:
    - 실행 시각: `blog.post.trash.purge-cron = "0 30 3 * * *"`, zone `Asia/Seoul`
    - 보관 기간: `blog.post.trash.retention = P30D`
    - 묶음 크기: `blog.post.trash.purge-batch-size = 100`
    - 처리 방식: ID 100개를 `ORDER BY deleted_at, id`로 읽는다. **글 하나마다 별도 트랜잭션**으로 처리한다. 잠근 뒤 다시 확인해, 그 사이 복구되었거나 영구 삭제되었으면 건너뛴다. 대상이 없을 때까지 반복하되 한 번 실행의 상한 시간(`max-duration` 기본 30분)을 둔다.
    - 실패 처리: 한 글이 실패하면 로그를 남기고 다음 글로 넘어간다. 실패한 글은 다음 날 다시 시도한다.
    - **ShedLock 잠금 저장소**: JDBC(`JdbcTemplateLockProvider`)와 `shedlock` 테이블을 쓰고, 이 테이블은 공통 시작 템플릿(O9)의 Flyway 마이그레이션에 추가한다. 51 V1에는 이 테이블이 없다. 002·003·014·015의 배치도 같은 저장소를 쓰도록 팀이 정한다.
- **Rationale**:
  - 글마다 트랜잭션을 나누면 실패한 글 하나가 묶음 전체를 되돌리지 않는다. 사용자 요청과의 잠금 경합도 짧아진다.
  - 30일 정리는 "30일이 지난 뒤 첫 실행"이면 충분하다(spec 엣지 케이스).
  - `ix_post_trash`는 `(author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL`이다. 배치처럼 작성자 조건 없이 조회하면 첫 열을 쓰지 못해 부분 인덱스 전체를 훑는다. 그래도 인덱스에는 휴지통 행(최근 30일 남짓의 삭제분)만 있어 작다. 51 §3도 이 인덱스의 용도를 "휴지통 목록·30일 정리"로 적었다. 실측에서 느리면 `(deleted_at) WHERE deleted_at IS NOT NULL` 인덱스를 **추가 제안**으로 올린다(지금은 추가하지 않음).
- **Alternatives considered**:
  - 100개를 한 트랜잭션으로 처리하는 방식. 한 글의 FK·잠금 문제로 묶음 전체가 실패한다.
  - Redis ShedLock 제공자. 테이블이 필요 없지만, Redis 장애 때 배치가 전부 멈추고 잠금이 유실될 수 있다. 팀 결정에 따라 바꿀 수 있다.

## R14. 도메인 이벤트 (확정)

- **Decision**: 이벤트 세 가지를 `shared/event`에 record로 둔다. 서비스가 같은 트랜잭션 안에서 발행하고, 처리는 커밋 후에 일어난다.
  - `PostTrashed(postId, authorId, trashedAt)`
  - `PostRestored(postId, authorId, restoredAt)`
  - `PostPurged(postId, authorId)`

  다음 경우에는 발행하지 않는다.
  - 빈 임시글 즉시 삭제
  - 빈 임시글 정리 배치(002)
  - 멱등 재삭제(상태 변화 없음)

  필드 상세는 [contracts/events.md](./contracts/events.md)에 있다.
- **Rationale**: 20 §3-1·EV-3(ID와 시각만)·EV-4(바뀐 경우만)·EV-1(AFTER_COMMIT). 알림은 구독하지 않는다. `PostPurged`는 FK CASCADE로 처리된다(25 NT-6). 검색 색인·sitemap이 구독한다(20 §10, 012·005 소관).
- **Alternatives considered**: outbox 테이블. 20 EV-2가 공통에서 제외했다.

## R15. 관리 목록 API와 휴지통 목록 경로 하나로 정하기 (제안(팀 확인 필요))

- **Decision**:
  - 기본 경로는 `GET /api/me/posts?tab=drafts|published|trash&visibility=public|private&cursor=…`(41 §5)다.
  - `GET /api/me/trash?cursor=…`(13 §2-4, 02 §5-1 예시)는 **같은 Service 메서드와 같은 응답 형식**을 쓰는 별칭으로 남긴다. 이 경우 `tab=trash`로 고정한다.
  - 13의 휴지통 항목 필드(`id, title, deletedAt, purgeAt`)는 41 항목 필드의 부분 집합이라 그대로 호환된다.
- **Rationale**: spec Implementation Notes에 따르면 원문 두 곳(41 §5와 13 §2-4·02 §5-1)이 다른 경로를 적었다. 원문을 고치지 않고 둘 다 만족시키면서 조회 코드는 하나로 둔다. 화면은 기본 경로만 쓴다.
- **Alternatives considered**:
  - `/api/me/trash`만 쓰는 방식. 관리 화면이 탭마다 다른 API를 쓰게 되고, `counts`를 어디서 줄지 애매해진다.
  - `/api/me/posts`만 쓰는 방식. 02 §5-1의 예시 경로가 404가 된다. 팀 확인 결과 둘 중 하나로 정하면 별칭을 지운다.

## R16. 페이지·커서 (확정 + 필드 제안)

- **Decision**:
  - 한 번에 20개를 보낸다. 서버는 21개를 조회해 다음 페이지가 있는지 판단하고, 클라이언트가 보낸 `size`는 무시한다(41 M-6·§5).
  - 커서는 10 §4-2·02 §5-1(O8) 형식이다. JSON을 Base64URL(패딩 없음)로 감싼 불투명 값이고, 시각은 epoch **마이크로초**다.
    - 임시글·발행 글: `{"v":1,"l":"manage:drafts:all|manage:published:all|manage:published:public|manage:published:private","k":[updatedAtµs, id]}`
    - 휴지통: `{"v":1,"l":"manage:trash","k":[deletedAtµs, id]}`
    - 목록 구분은 001 공용 `CursorCodec`+`ListScope`(001 T021)의 `l` 필드 하나로 한다(`l = manage:{tab}[:{filter}]`). 처음 제안한 `t`·`f` 필드는 쓰지 않는다(Tier A 교차 분석 2026-10-07).
  - 다음은 모두 400 `INVALID_CURSOR`다: 풀리지 않는 값, 모르는 `v`, 필드 누락·타입 오류, `l`이 요청의 탭·필터와 다른 커서.
  - 키 비교는 `(updated_at, id) < (:t, :id)` 행 비교를 쓴다.
- **Rationale**: 10 §4-2에 따르면 기능에 필요한 값은 같은 JSON에 필드를 더한다. 다른 목록의 커서는 `INVALID_CURSOR`로 거부한다. 탭이나 필터를 바꾼 뒤 옛 커서가 섞이지 않게 커서 안에 탭·필터를 넣는다.
- **Alternatives considered**: 오프셋 페이지. 자동 저장으로 `updated_at`이 바뀌면 중복·누락이 생겨 기각한다(41 §5).

## R17. 자동 저장으로 순서가 바뀔 때의 중복 방지 (확정)

- **Decision**: 서버 커서는 값 기반이다. 화면(`useManagePosts`)은 이미 화면에 있는 ID를 건너뛴다.
- **Rationale**: 41 §5 "커서와 자동 저장", 10 §4-3의 이중 안전장치와 같은 방식이다. 다른 탭에서 자동 저장해 `updated_at`이 바뀐 글은 다음 [더 보기]에 한 번 더 올 수 있는데, 이를 화면에서 걸러 SC-006을 지킨다.
- **Alternatives considered**: 스냅샷 시각을 커서에 넣는 방식(`updated_at <= :snap`). 방금 수정한 글이 목록에서 사라지는 문제가 생긴다.

## R18. 목록·개수 쿼리와 인덱스 (확정 + 개수 쿼리 표현 제안)

- **Decision**: 목록 쿼리는 다음과 같다. 본문 컬럼은 SELECT하지 않는다.
  ```sql
  SELECT p.id, p.title, p.status, p.visibility, (d.post_id IS NOT NULL) AS editing,
         (p.hidden_at IS NOT NULL) AS hidden, p.updated_at, p.published_at, p.edited_at,
         p.deleted_at, p.view_count, p.like_count, p.comment_count
  FROM post p LEFT JOIN post_draft d ON d.post_id = p.id
  WHERE p.author_id = :me AND p.status = :status AND p.deleted_at IS NULL
    [AND p.visibility = :vis]
    [AND (p.updated_at, p.id) < (:t, :id)]
  ORDER BY p.updated_at DESC, p.id DESC
  LIMIT 21
  ```
  - 인덱스: 임시글·발행 글은 `ix_post_manage`를 쓴다. 휴지통은 `WHERE p.author_id = :me AND p.deleted_at IS NOT NULL … ORDER BY p.deleted_at DESC, p.id DESC`로 `ix_post_trash`를 쓴다.
  - 공개/비공개 필터는 `ix_post_manage`로 좁힌 뒤 거른다(41 ERD 변경 없음).
  - **개수 쿼리**는 첫 요청에만 실행하며, `UNION ALL` 두 갈래를 **한 문장**으로 보낸다. 각 갈래가 자기 부분 인덱스를 쓰도록 하기 위해서다(표현 방식은 제안).
    ```sql
    SELECT p.status AS k, count(*) FROM post p WHERE p.author_id = :me AND p.deleted_at IS NULL GROUP BY p.status
    UNION ALL
    SELECT 'TRASH', count(*) FROM post p WHERE p.author_id = :me AND p.deleted_at IS NOT NULL
    ```
- **Rationale**: 41 §6(목록 1번 + 개수 1번, `post_draft` LEFT JOIN), 51 §3 인덱스 정의, 헌법 비기능 최소선(N+1 금지).
  - 51에는 조건 없는 `post(author_id)` 인덱스가 없다. 그래서 `WHERE author_id = :me GROUP BY …` 한 갈래로 쓰면 두 부분 인덱스 중 어느 것도 쓸 수 없다.
  - 반응 숫자(`view_count`·`like_count`·`comment_count`)는 `post`에 비정규화된 컬럼이라 다른 모듈을 조회하지 않는다(41 M-8, 헌법 II).
  - 응답 시간 300ms는 Testcontainers에서 회원 1명·글 1만 건을 시드하고 EXPLAIN·시간으로 확인한다.
- **Alternatives considered**: 개수를 `member`에 미리 저장하는 방식. 공통 ERD를 변경해야 하고(헌법 I) 동기화 비용이 든다.

## R19. 응답 본문 (확정 + 일부 제안)

- **Decision**:
  - **확정**: `DELETE /api/posts/{postId}`는 200 `{ "trashed": true, "purgeAt": … }` 또는 200 `{ "purged": true }`를 준다(13 §2-4, FR-026).
  - **확정**: 복구·영구 삭제는 200을 주고, 대상이 휴지통에 없으면 404다(13 §2-4).
  - **제안(팀 확인 필요)**:
    - 복구 응답 본문은 `{ "restored": true, "status": "DRAFT|PUBLISHED", "visibility": … }`로 한다. 화면이 "복구했어요 [발행 글 탭에서 보기]"의 이동할 탭을 고르는 데 쓴다.
    - 영구 삭제 응답 본문은 `{ "purged": true }`로 한다.
- **Rationale**: 원문은 상태 코드만 정했다. 줄 단위로 화면을 갱신하려면(FR-013) 결과 상태가 필요하다.
- **Alternatives considered**: 204 No Content. 화면이 복구된 글의 상태를 알려면 다시 조회해야 한다.

## R20. 오류 코드 (확정 + 하나 제안)

- **Decision**: 공통 오류 본문 `{code, message, errors, details}`(02 §5-1 O8)를 쓴다.
  - 401 `LOGIN_REQUIRED`
  - 403 `ACCOUNT_WITHDRAWN`
  - 404 `NOT_FOUND`(세분화하지 않음, 42 §4)
  - 400 `INVALID_CURSOR`
  - 400 `INVALID_VISIBILITY`: 필터 값이 `public|private`가 아닐 때. 06 §4의 이유 코드를 재사용한다
  - **제안(팀 확인 필요)**: 400 `INVALID_TAB`(모르는 `tab` 값). `errors: [{field:"tab", …}]`도 함께 준다. `tab`이 없으면 `drafts`로 본다.
  - `visibility`는 `tab=published`일 때만 쓰고, 다른 탭에서는 무시한다.
  - CSRF 토큰이 없거나 맞지 않을 때의 403 처리는 001 plan(M17) 소관이다.
- **Rationale**: 42 §4 응답 코드 표, 02 §5-1 검증 오류 규칙.
- **Alternatives considered**: 모르는 탭이면 조용히 `drafts`로 보이는 방식. 클라이언트 버그를 숨기게 된다.

## R21. 관리 화면에서 부르는 다른 spec의 API (확정, 소관 분리)

- **Decision**: 이 기능은 아래 API를 화면에서 호출만 한다. 계약은 각 spec의 contracts가 정의한다.
  - [공개 범위 ▾]: `PUT /api/posts/{postId}/visibility`. **004 plan 결정(2026-10-07)**: 06 §4의 `PATCH` 대신 O8 규약(상태 지정은 PUT·DELETE)에 맞춘다.
    - 같은 값을 다시 보내면 변화 없이 200이다.
    - 잘못된 값은 소유 확인(404) 뒤 400 `INVALID_VISIBILITY`다.
    - 정의는 004 계약이 소유하고 006은 참조만 한다.
  - [변경 취소]: 작업본 삭제(04 §2-5) — 002
  - [새 글]: `DRAFT` 행 생성(04 §2-5) — 002
- **Rationale**: spec FR-014~016이 "→ specs/002·004"로 위임했다. 메서드는 004 plan의 결정을 따른다. spec Implementation Notes의 `PATCH` 표기는 이 결정 이전의 것이다.
- **Alternatives considered**: 없음.

## R22. FK 오류 처리 (확정)

- **Decision**: 완전 삭제 중 FK 위반이 나면 23001(`restrict_violation`)과 23503(`foreign_key_violation`)을 모두 FK 위반으로 처리해 로그를 남긴다. 배치이면 그 글만 건너뛴다. 51 기준으로 `post`·`comment`를 가리키는 FK는 모두 CASCADE 또는 SET NULL이라 정상 흐름에서는 생기지 않는다.
- **Rationale**: 01 결정 기록(2026-10-06), 51 §3 FK 표.
- **Alternatives considered**: 없음.

## R23. "N일 뒤 완전 삭제" 표시 계산 (제안(팀 확인 필요))

- **Decision**: 서버는 `purgeAt = deleted_at + retention`(ISO-8601 UTC)을 준다. 화면은 오늘과 `purgeAt`의 **한국 시간 날짜 차이**로 "N일 뒤 완전 삭제"를 표시한다. 예: 10월 2일 삭제 → 11월 1일 예정. 10월 5일에 보면 27일이다. 0 이하이면 "곧 완전 삭제"로 표시한다(배치가 아직 돌지 않은 경우).
- **Rationale**: 41 §3-3·FR-011의 예시 숫자와 맞는다. 실제 삭제는 그 뒤 첫 새벽 배치에서 일어난다(spec Assumptions).
- **Alternatives considered**: 시간 차이를 24시간 단위로 올림하는 방식. 자정 근처에서 하루씩 어긋난다.

## R24. 내 글 관리 화면 (확정)

- **Decision**:
  - React 라우트는 `/manage/posts?tab=drafts|published|trash&visibility=public|private`이고 기본 탭은 `drafts`다.
  - API가 401을 주면 로그인 화면으로 보낸다. 이때 돌아올 주소를 `/manage/posts?…`로 넘긴다.
  - 처리 결과는 그 줄에만 반영한다. 실패하면 줄 아래에 이유를 보이고, 404이면 목록을 다시 불러온다.
  - 탭 글 수는 첫 응답 `counts`를 쓰고, 이후 처리 결과에 따라 화면에서 ±1 한다(FR-004 "다시 계산하지 않음").
  - 한 줄 목록이며 카드를 쓰지 않는다. 375px에서 버튼은 줄바꿈한다.
  - JS가 없을 때의 폼 전송 대체 규칙은 넣지 않는다(2026-10-07 H7, spec Assumptions).
- **Rationale**: 41 §2~§4, 02 §5(비회원 401 → React 로그인 안내), 헌법 화면 제약.
- **Alternatives considered**: 서버 렌더링 화면. H7로 React가 확정되었다.

## R25. 탈퇴 정리에서 재사용 (확정)

- **Decision**: 015의 `PostWithdrawalPurgeStep`(order 10)은 그 회원의 글 ID 전부(휴지통 포함)를 읽어 `PostPurgeService.purge()`를 호출자 트랜잭션 안에서 반복한다. 글마다 `PostPurged`를 발행한다. 이 클래스는 post 모듈에 두며, 015와 이 plan이 공유한다.
- **Rationale**: 44 §4 표 order 10 "공통 post", 13 §3-3 1번("§2-5와 같이 완전 삭제"), 20 §3-1(`PostPurged`는 탈퇴 정리도 포함).
- **Alternatives considered**: 015가 별도 SQL로 지우는 방식. 신고 종료·사진 연결 해제 규칙이 두 군데로 갈라진다.

## R26. 기술 스택 (확정)

- **Decision**: 공통 기술 결정 그대로다.
  - 서버: Java 21, Spring Boot 3.x 이상(버전 팀 확정), Maven, Spring Data JPA, Spring Security, Spring Session Data Redis, Flyway, PostgreSQL(pg_trgm), Redis
  - 화면: React
  - 테스트·실행: JUnit 5, Testcontainers, Spring Security Test, Docker Compose
- **Rationale**: 01 Q8, 02 §2, 헌법 기술 제약.
- **Alternatives considered**: 없음(팀 결정).
