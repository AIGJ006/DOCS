# Events & Batch Contracts: 글 작성·임시저장·발행

**Feature**: `002-post-authoring` | **기준**: [docs/20 §1·§2·§3-1](../../../docs/20-domain-events.md), [docs/05 §7 ⑩](../../../docs/05-publish.md), [docs/04 §2-4·§2-5](../../../docs/04-draft-and-image.md), [docs/12 §7-7](../../../docs/12-content-sanitize.md)

## 1. 도메인 이벤트 (발행하는 쪽: `post` 모듈 `PublishService`)

공통 규칙(20 EV-1·EV-3·EV-4·EV-7):

- `shared/domain/event` 패키지의 불변 Java `record`. 필드는 `long` ID·enum·`Instant`만. 제목·본문 같은 글자는 넣지 않는다.
- 발행 트랜잭션 안에서 `ApplicationEventPublisher.publishEvent`로 **발행만** 한다. 처리는 구독하는 쪽의 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`. 롤백되면 버려진다.
- 상태가 실제로 바뀐 경우에만 한 번. 리스너 실패는 발행 응답에 영향이 없다(원칙 V). 유실 허용(EV-2).
- 멱등 키로 저장된 응답을 다시 돌려줄 때(같은 키 재전송)는 이벤트를 다시 발행하지 않는다.

| 이벤트 | 언제 | 필드 | 출처 |
|---|---|---|---|
| `PostPublished` | 최초 발행(`published_at`이 이번에 채워짐) | `long postId`, `long authorId`, `Visibility visibility`, `Instant publishedAt` | 20 §3-1, 05 §7 ⑩ |
| `PostEdited` | 다시 발행(이미 `published_at`이 있음) | `long postId`, `long authorId`, `Instant editedAt` | 20 §3-1, 05 §7 ⑩ |
| `PostWentPublic` | 이번 발행에서 `first_public_at`을 **처음** 채움(공개로 최초 발행, 또는 비공개였던 글을 다시 발행하며 처음 공개) | `long postId`, `long authorId`, `Instant firstPublicAt` | 20 §3-1 EV-5 |

- 한 번의 발행에서 나오는 조합: `PostPublished` 단독 / `PostPublished` + `PostWentPublic` / `PostEdited` 단독 / `PostEdited` + `PostWentPublic`.
- `Post.publish(...)`가 `PublishResult{firstPublish, wentPublic}`을 돌려주고 Service가 그 값으로 고른다(20 §3-1).
- 공개 범위만 바꾸는 `PostVisibilityChanged`(+ `PostWentPublic`)는 004가 발행한다. 휴지통·복구·완전 삭제 이벤트는 006.

### 발행하지 않는 것

| 동작 | 이유 |
|---|---|
| 새 글 만들기, 자동 저장, 수동 저장, 변경 취소 | 독자에게 보이는 상태가 바뀌지 않는다 |
| 빈 임시글 정리 배치 | 아무에게도 보인 적 없는 글(20 §3-1) |
| 다시 렌더링 배치 | 내용·공개 상태가 바뀌지 않는다(`edited_at`·`edit_version` 유지) |

### 예상 구독자 (참고, 각 스펙이 확정)

| 이벤트 | 구독 |
|---|---|
| `PostWentPublic` | 011 알림 `NEW_POST`, 012 트렌딩·검색 색인·sitemap |
| `PostEdited` | 012 검색 색인·sitemap |
| `PostPublished` | 개인 확장(강성찬 잔디 등) |

## 2. 커밋 후 동기 처리 (이벤트 아님, `post` 모듈 내부)

발행·변경 취소 트랜잭션이 커밋된 뒤 같은 요청 스레드에서 처리한다(05 J-5: 트랜잭션 안에서 Redis를 건드리지 않음).

| 순서 | 처리 | 실패 시 |
|---|---|---|
| ⑨ | `autosave-release.lua`(확인한 버전 v0, 새 버전 v1): Redis `version ≤ v0` → 삭제, 크면 `v1 + 1`로 다시 매기고 dirty 유지 (research B-3) | 경고 로그. 키는 TTL·다음 반영이 정리(내용 손실 없음) |
| ⑪ | 멱등 키 값을 `{hash, DONE, response}`로 교체(남은 TTL 유지) | 경고 로그. 같은 키 재시도는 행 잠금 + 버전 확인으로 409(중복 발행 없음) |

트랜잭션이 실패하면 ⑨는 하지 않고, 멱등 키는 삭제해 같은 키로 다시 시도할 수 있게 한다.

## 3. 배치 (`@Scheduled` + ShedLock)

| 작업 | 주기 | 대상·조건 | 처리 | 실패·장애 |
|---|---|---|---|---|
| `AutosaveFlushJob` (`autosave-flush`) | 1분(`blog.autosave.flush-interval`) | `SMEMBERS autosave:dirty` → 각 `autosave:post:{id}` | 임시글: `UPDATE post SET title, content_md, edit_version = :v, updated_at = now() WHERE id = :id AND status = 'DRAFT' AND edit_version < :v`. 발행 글: `post_draft` UPSERT `WHERE post_draft.edit_version < EXCLUDED.edit_version`, 단 `post.edit_version < :v`일 때만. **`deleted_at` 조건을 넣지 않는다**(휴지통 직전 받은 내용 보존, 13 D-3). 글이 완전 삭제돼 행이 없거나 UPSERT가 FK 위반(23503)이면 그 Redis 키·dirty 항목을 버린다. 반영 뒤 사진 연결은 003 FR-022. 반영 후 `SREM` (키가 그 사이 바뀌었으면 다음 회차에 다시). Redis 키는 지우지 않는다 | 글 하나 실패는 로그 후 다음 글. Redis 장애면 그 회차 건너뜀(자동 저장이 DB 직접 저장 중) |
| `EmptyDraftCleanupJob` (`empty-draft-cleanup`) | 매일 03:30 Asia/Seoul (제안) | `status = 'DRAFT' AND btrim(title) = '' AND btrim(content_md) = '' AND created_at < now() - 24h AND updated_at < now() - 24h AND deleted_at IS NULL` | 후보를 `FOR UPDATE SKIP LOCKED`로 100개씩 잡고 Redis `EXISTS autosave:post:{id}`가 0인 글만 `DELETE`(CASCADE, 휴지통 없음) | Redis 장애면 그날 건너뜀(보관분 유무를 확인할 수 없음). 이벤트 없음 |
| `RerenderJob` (`post-rerender`) | 10분마다 확인 (제안) | `status = 'PUBLISHED' AND render_version < RENDER_VERSION` PK 순 100개 | 글 작성자 기준으로 `ContentRenderer` 다시 실행 → `UPDATE post SET content_html, excerpt, render_version WHERE id = :id AND edit_version = :읽은 버전 AND render_version < :cur` (`edited_at`·`edit_version`·`updated_at` 유지) | 렌더링 실패(`CONTENT_TOO_COMPLEX` 포함)는 그 글을 건너뛰고 경고 로그 + 남은 건수 지표. 0건이면 즉시 종료 |

## 4. 다른 모듈에 제공하는 공개 메서드

| 메서드 | 호출하는 쪽 | 계약 |
|---|---|---|
| `AutosaveService.flushNow(postId)` | 006 휴지통 이동 직전(13 §2 "자동 저장") | 그 글의 Redis 보관분을 위 반영 규칙(`deleted_at` 조건 없음)으로 즉시 DB에 쓰고, 커밋 후 키 삭제는 호출한 쪽 트랜잭션의 커밋 후 처리에 등록 |
| `EmptyDraftPolicy.isEmpty(title, contentMd)` | 006 빈 임시글 바로 삭제(13 D-2) | `btrim` 기준 둘 다 비었는지 (research B-9). 006과 같은 함수를 쓴다 |
| `ContentRenderer.render(contentMd, ImageContext)` | 012 등 다시 렌더링이 필요한 기능 | 발행·미리보기·다시 렌더링과 같은 결과 |
