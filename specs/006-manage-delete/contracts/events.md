# Events & Jobs Contract: 006-manage-delete

**기준**:

- [docs/20-domain-events.md](../../../docs/20-domain-events.md): §1 EV-1~EV-7, §3-1, §4-2
- [docs/13-delete-withdraw.md](../../../docs/13-delete-withdraw.md): §2-5
- [docs/25-notification.md](../../../docs/25-notification.md): NT-6

## 1. 도메인 이벤트 (post 모듈이 발행)

공통 규칙 (20 §2):

- 형식은 Java `record`이고 패키지는 `com.team.blog.shared.event`다. 필드는 `long`/`Long` ID와 `Instant`만 둔다. 제목·본문 같은 글자는 넣지 않는다(EV-3).
- Service가 상태를 바꾼 **같은 트랜잭션 안에서** `ApplicationEventPublisher`로 발행한다. 처리는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`에서 일어난다(EV-1). 롤백되면 이벤트도 버려진다.
- **상태가 실제로 바뀐 경우에만** 한 번 발행한다(EV-4). 유실을 허용하므로(EV-2) 데이터 삭제는 이벤트에 맡기지 않는다.
- 순서는 보장하지 않는다. 리스너는 처리할 때 최신 상태를 다시 조회한다.

| 이벤트 | 필드 | 발행 시점 | 발행하지 않는 경우 |
|---|---|---|---|
| `PostTrashed` | `long postId, long authorId, Instant trashedAt` | `DELETE /api/posts/{postId}`가 `deleted_at`을 NULL에서 값으로 바꿨을 때 | 이미 휴지통인 글을 다시 삭제(변화 없음), 빈 임시글 즉시 삭제 |
| `PostRestored` | `long postId, long authorId, Instant restoredAt` | `POST /api/posts/{postId}/restore`가 `deleted_at`을 NULL로 바꿨을 때 | 404 응답(휴지통 아님·남의 글) |
| `PostPurged` | `long postId, long authorId` | `post` 행을 완전히 지웠을 때. 해당하는 경우: 영구 삭제, 30일 휴지통 비우기 배치, 탈퇴 30일 정리(015, `PostWithdrawalPurgeStep` order 10) | 빈 임시글 즉시 삭제(13 D-2), 002의 빈 임시글 정리 배치(04 §2-5). 아무에게도 보인 적 없는 글이라 발행하지 않는다(20 §3-1) |

### 구독자 (참고: 이 기능은 구독하지 않는다)

| 구독자 | 이벤트 | 처리 | 소관 |
|---|---|---|---|
| 검색 색인·sitemap | `PostTrashed`·`PostRestored`·`PostPurged` | 색인에서 빼거나 다시 넣는다. 다시 넣을 때는 공용 조건으로 다시 판정한다 | 012·005 plan (20 §10) |
| 알림 | — (구독 안 함) | `PostTrashed`: 지우지 않는다. 보여 줄 때 "볼 수 없는 글이에요"로 표시한다. `PostPurged`: `notification.post_id` FK CASCADE가 지운다. `PostRestored`: 아무것도 하지 않는다 | 011 (20 §4-2, 25 NT-6) |
| 트렌딩·피드·태그 목록 | — | 공용 조건 `deleted_at IS NULL`로 자동 제외 | 각 plan |

## 2. 완전 삭제 확장점 `PostPurgeStep` (동기, 같은 트랜잭션)

데이터를 지우는 일은 이벤트로 하지 않는다. post 모듈이 정의한 인터페이스를 각 모듈이 구현하고, `PostPurgeService`가 `order()` 순으로 **DELETE 전에** 호출한다(research R10).

```java
// com.team.blog.post.application.spi
public interface PostPurgeStep {
    int order();                    // 10 단위, 새 단계는 사이 값
    void beforePurge(long postId);  // 호출한 쪽 트랜잭션 안에서 실행. 예외가 나면 그 글의 완전 삭제 전체가 롤백된다
}
```

| order | 구현 (모듈) | 처리 | 근거 |
|---|---|---|---|
| 10 | `ReportPostPurgeStep` (신고 모듈, 014 소관 — 006이 먼저 구현하면 006 tasks에서 생성) | `report_case`에서 `status = 'PENDING'`이고 대상이 (`post_id = :id` 또는 `comment_id = ANY(그 글의 댓글 ID)`)인 사건을 `CLOSED_NO_TARGET`, `handled_at = now()`, `handled_by = NULL`로 바꾼다. 댓글 ID는 interaction 모듈의 `CommentQueryService.commentIdsOfPost`로 받는다. 이벤트는 없다 | 13 §2-5 0단계, 43 §5 |
| 20 | `ImagePostPurgeStep` (media) | 그 글에만 연결된 사진에 `image.detached_at = now()`를 기록한다. 다른 글(휴지통 글 포함)에도 연결된 사진은 그대로 둔다 | 13 §2-5 1단계, 04 §4-4 |
| — | `PostPurgeService` (post) | `DELETE FROM post WHERE id = :id`를 실행한다. CASCADE·SET NULL은 [data-model §1-3](../data-model.md) 참고 | 13 §2-5 2단계 |
| 커밋 후 | `PostPurgeService` | Redis `autosave:post:{id}` 삭제, `autosave:dirty`에서 제거 | 02 §4-1 |

## 3. 배치: 휴지통 비우기 `TrashPurgeJob`

| 항목 | 값 |
|---|---|
| 실행 | `@Scheduled(cron = "${blog.post.trash.purge-cron}", zone = "Asia/Seoul")`. 기본 `0 30 3 * * *`(매일 03:30, 시각은 제안) |
| 중복 실행 방지 | ShedLock `@SchedulerLock(name = "trashPurgeJob", lockAtMostFor = "PT40M")`. 잠금 저장소는 팀 결정이다(research R13) |
| 대상 | `SELECT id FROM post WHERE deleted_at IS NOT NULL AND deleted_at < now() - :retention ORDER BY deleted_at, id LIMIT :batchSize`(기본 30일·100개) |
| 처리 | 글 하나마다 새 트랜잭션에서 처리한다: `SELECT … FOR UPDATE`로 잠근 뒤 조건을 다시 확인한다. 복구됐거나 이미 지워졌으면 건너뛴다. 조건이 맞으면 `PostPurgeService.purge(id)`를 실행하고 `PostPurged`를 발행한다 |
| 반복 | 대상이 없거나 `purge-max-duration`(기본 30분)에 이를 때까지 묶음을 반복한다 |
| 실패 | 그 글만 롤백하고 오류 로그를 남긴 뒤 다음 글로 넘어간다. 다음 날 다시 시도한다. FK 위반은 23001과 23503을 모두 FK 위반으로 처리한다 |
| 관측 | 실행마다 처리 수·건너뜀 수·실패 수·소요 시간을 INFO 로그로 남긴다 |
