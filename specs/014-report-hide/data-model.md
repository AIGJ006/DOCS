# Data Model: 신고·관리자 숨김·회원 정지

**Feature**: 014-report-hide | **Date**: 2026-10-08 | 스키마 변경 없음(V1), 새 테이블·컬럼 없음

## 1. 테이블 (V1, 이 기능이 쓰는 방식)

### 1-1. `report_case` (moderation 소유)

| 컬럼 | 타입 | NULL | 이 기능의 규칙 |
|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | |
| `target_type` | varchar(20) | 불가 | `POST` \| `COMMENT` |
| `post_id` | bigint FK→post `SET NULL` | 허용 | 글 대상이면 채움. 글이 지워지면 NULL |
| `comment_id` | bigint FK→comment `SET NULL` | 허용 | 댓글 대상이면 채움(`ck_report_case_target_ref` — 글 대상이면 NULL) |
| `target_author_id` | bigint FK→member `RESTRICT` | 불가 | 사건을 만들 때의 대상 작성자. 탈퇴 익명 처리 뒤에도 남음 |
| `snapshot_title` | varchar(100) | 허용 | 글 제목. 댓글은 NULL. 처리 30일 뒤 NULL |
| `snapshot_content` | varchar(2000) | 허용 | 글 `content_md` 앞 2,000 코드 포인트 / 댓글 내용 전체. 처리 30일 뒤 NULL |
| `status` | varchar(20) | 불가 | 아래 상태 표 |
| `handled_by` | bigint FK→member | 허용 | 처리 관리자. 자동 종료면 NULL |
| `handled_at` | timestamptz | 허용 | 처리·자동 종료 시각. 30일 정리 기준 |
| `created_at` | timestamptz | 불가 | 첫 신고 시각(직접 숨김이면 숨긴 시각) |

상태:

| 상태 | 들어오는 경우 | 나가는 경우 |
|---|---|---|
| `PENDING` | 첫 신고(R4) | 처리(→ `HIDDEN`·`REJECTED`), 직접 숨김(→ `HIDDEN`), 대상 없음(→ `CLOSED_NO_TARGET`) |
| `HIDDEN` | 처리 숨기기, 직접 숨김(대기 사건 또는 새 사건) | 없음(해제해도 그대로) |
| `REJECTED` | 처리 문제없음 | 없음 |
| `CLOSED_NO_TARGET` | 글 완전 삭제, 댓글 삭제, 탈퇴 정리, 고아 정리, 처리 시도 때 대상 없음 | 없음 |

`PENDING`이 아닌 사건에는 새 신고가 붙지 않는다 — 다음 신고는 새 사건(E4). 부분 UNIQUE가 대상마다 `PENDING` 하나를 보장한다.

### 1-2. `report` (moderation 소유)

| 컬럼 | 타입 | NULL | 이 기능의 규칙 |
|---|---|---|---|
| `id` | bigint IDENTITY | 불가 | 011 `notification.report_id`가 가리킴(`SET NULL`) |
| `case_id` | bigint FK→report_case `RESTRICT` | 불가 | |
| `reporter_id` | bigint FK→member `RESTRICT` | 불가 | `uq_report_case_reporter (case_id, reporter_id)` |
| `reason` | varchar(30) | 불가 | `SPAM`·`ABUSE`·`SEXUAL`·`PRIVACY`·`COPYRIGHT`·`OTHER` |
| `detail` | varchar(200) | 허용 | 기타일 때만 저장(필수, Service 검사). 처리 30일 뒤·신고자 탈퇴 정리 때 NULL |
| `created_at` | timestamptz | 불가 | |

### 1-3. 다른 모듈 테이블 (공개 Service로만)

| 테이블 | 컬럼 | 쓰는 Service | 규칙 |
|---|---|---|---|
| `post` | `hidden_at`, `hidden_by`, `hidden_reason` | post `PostModerationService` | 숨김 = 세 칸 기록, 해제 = 세 칸 NULL. 다른 칸·카운터는 그대로 |
| `comment` | 같음 | 007 `CommentModerationService` | 숨김 = 세 칸 + 글 `comment_count − 1`, 해제 = NULL + `+1`(007) |
| `member` | `status` | 001 `SuspensionService` | 정지 `SUSPENDED`, 해제 `ACTIVE`. `WITHDRAWN`은 정지 불가 |
| `member_suspension` | 8개 컬럼 | 001 `SuspensionService` | 열린 정지(`lifted_at IS NULL`)는 회원당 하나(Service), `ends_at` NULL = 영구 |

## 2. Redis 키

| 키 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|
| `ratelimit:report:{memberId}:1m` | 001 `RateLimiter` 카운터 | 1분 | 신고 5번 |
| `ratelimit:report:{memberId}:1d` | 같음 | 1일 | 신고 50번 |

Redis 장애면 둘 다 통과(001 `RateLimiter` 동작). 정지 때 세션 삭제는 001 Spring Session 인덱스(`SessionTerminator`).

## 3. 값 객체 (moderation.domain·application)

```java
public enum ReportReason { SPAM, ABUSE, SEXUAL, PRIVACY, COPYRIGHT, OTHER }   // hidden_reason도 같은 코드
public enum CaseStatus { PENDING, HIDDEN, REJECTED, CLOSED_NO_TARGET }
public enum ResolutionAction { HIDE, REJECT }

/** 신고·직접 숨김 대상. 볼 수 있는지 확인을 마친 것만 만든다. */
public record ReportTarget(
        ReportTargetType type,      // shared.event
        long targetId,
        long postId,                // 댓글이면 그 글
        long authorId,
        String snapshotTitle,       // 댓글 null
        String snapshotContent) {}

/** 현재 상태 이름 (research R7 표). */
public enum TargetState { PUBLIC, PRIVATE, TRASHED, HIDDEN, AUTHOR_WITHDRAWN, GONE,     // 글
                          VISIBLE, DELETED, POST_NOT_VISIBLE }                          // 댓글 (HIDDEN·AUTHOR_WITHDRAWN·GONE 공유)

public enum SuspensionDuration { P1D, P7D, P30D, PERMANENT }
```

다른 모듈이 돌려주는 값:

```java
// post.application
public record PostSnapshot(long postId, long authorId, String title, String contentHead) {}   // contentHead ≤ 2,000 코드 포인트
// interaction.application (007 CommentModerationService.snapshot — 필드는 이 기능이 정함, 007과 맞춤)
public record CommentSnapshot(long commentId, long postId, long authorId, String content,
                              boolean deleted, boolean hidden, boolean authorWithdrawn) {}
// account.application
public record AdminMemberInfo(long id, String handle, String nickname, Role role, MemberStatus status,
                              Instant createdAt, Instant withdrawnAt) {}
public record SuspensionRecord(long id, String reason, Instant startedAt, Instant endsAt,
                               String suspendedByHandle, Instant liftedAt, String liftedByHandle) {}
```

`CommentSnapshot`이라는 이름은 007 권한 하네스의 테스트 도구(`T/interaction/integration/permission`, 댓글 행 수 비교)에도 있다. 패키지가 달라 충돌하지 않지만 읽을 때 헷갈리므로 테스트 쪽 이름을 `CommentRowsSnapshot`으로 바꾸기를 007에 제안한다(ANALYSIS-tier-bc).

## 4. 응답 모델 (moderation.web.dto)

```java
public record ReportAccepted(boolean accepted) {}                         // 항상 true

public record CaseListItem(
        long caseId,
        ReportTargetType targetType,
        String title,                 // 글: snapshot_title, 댓글: snapshot_content 앞 40자 (정리 뒤 null)
        String authorHandle,          // 익명 처리된 회원이면 null
        int reportCount,
        Map<ReportReason, Integer> reasonCounts,
        Instant lastReportedAt,       // 직접 숨김 사건이면 null
        CaseStatus status,
        Instant handledAt,
        String handledByNickname,     // 자동 종료면 null → 화면 "자동"
        boolean targetHiddenNow) {}   // 처리됨 탭의 [숨김 해제] 표시

public record CaseDetail(
        long caseId,
        ReportTargetType targetType,
        Long postId, Long commentId,  // 대상이 사라졌으면 null
        String snapshotTitle, String snapshotContent,
        TargetState currentState,
        CaseStatus status, Instant createdAt, Instant handledAt, String handledByNickname,
        int reportCount,
        Map<ReportReason, Integer> reasonCounts,
        List<OtherDetail> otherDetails,          // 기타 설명 (신고자 정보 없음)
        boolean reportedByMe,
        boolean onlyMyReport,
        AuthorInfo author) {
    public record OtherDetail(String detail, Instant reportedAt) {}
    public record AuthorInfo(String handle, String nickname, Instant joinedAt, int hiddenCount,
                             boolean suspendedNow, int suspensionCount) {}
}

public record HiddenState(boolean hidden, Long caseId) {}                 // PUT·DELETE …/hidden

public record AdminMemberView(
        String handle, String nickname, Role role, MemberStatus status, Instant joinedAt,
        int hiddenCount,
        SuspensionRecord openSuspension,          // 없으면 null
        List<SuspensionRecord> history) {}        // 최근 순, 최대 50
```

작성자에게 가는 어떤 응답(005 상세 `authorView`, 006 관리 목록, 011 알림)에도 신고 수·신고자가 없다(SC-004). 005 상세의 `authorView.hiddenReason`은 사유 코드 하나뿐이다.

## 5. 이유 코드 (`ModerationReasonCode`, 제안)

| 코드 | 상태 | 메시지 | 언제 |
|---|---|---|---|
| `CANNOT_REPORT_OWN` | 400 | 내 글이나 댓글은 신고할 수 없어요 | 자기 콘텐츠 신고(원문) |
| `REPORT_DETAIL_REQUIRED` | 400 (`VALIDATION_FAILED`의 칸 오류, `field: detail`) | 기타 사유를 적어 주세요 | 기타 + 설명 없음(원문은 최상위 코드 — 팀 확인 T004) |
| `REPORT_ALREADY_HANDLED` | 409 | 이미 처리된 신고예요 | 처리된 사건 다시 처리, 대상 없음(`details {status}`) |
| `CANNOT_MODERATE_OWN` | 400 | 내 글이나 댓글은 처리할 수 없어요 | 관리자가 자기 콘텐츠 숨김·해제·처리 |
| `CANNOT_HANDLE_OWN_REPORT` | 400 | 내가 혼자 신고한 건은 처리할 수 없어요 | 신고자가 그 관리자뿐 |
| `CANNOT_SUSPEND_ADMIN` | 400 | 관리자는 정지할 수 없어요 | 대상이 관리자(자기 포함) |
| `CANNOT_SUSPEND_WITHDRAWN` | 400 | 탈퇴 신청한 회원은 정지할 수 없어요 | 대상이 탈퇴 유예 |
| `ALREADY_SUSPENDED` | 409 | 이미 정지된 회원이에요 | 열린 정지가 있음 |

공통(재사용): 401 `LOGIN_REQUIRED`, 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_SUSPENDED`·`ACCOUNT_WITHDRAWN`·`CSRF_REJECTED`, 404 `NOT_FOUND`(고정 본문 — 관리자 경로의 일반 회원 포함), 400 `VALIDATION_FAILED`·`INVALID_CURSOR`, 429 `TOO_MANY_REQUESTS`, 503 `TEMPORARILY_UNAVAILABLE`(정지 중 세션 삭제 실패).

## 6. 이벤트 (shared.event)

```java
public enum ReportTargetType { POST, COMMENT }
public enum ReportResult { ACTION_TAKEN, NO_VIOLATION }
public record ReportResolved(long reportId, long reporterId, ReportTargetType targetType, long targetId,
                             ReportResult result, Instant resolvedAt) implements DomainEvent {}
public record ContentHidden(ReportTargetType targetType, long targetId, long ownerId, long postId,
                            Instant hiddenAt) implements DomainEvent {}
public record ContentUnhidden(ReportTargetType targetType, long targetId, long ownerId, long postId,
                              Instant unhiddenAt) implements DomainEvent {}
public record MemberSuspended(long memberId, Instant until, Instant suspendedAt) implements DomainEvent {}   // until null = 영구
```

## 7. 설정값

| 접두어 | 클래스 | 키 |
|---|---|---|
| `blog.moderation` | `ModerationProperties` | `snapshot-content-chars`(2000), `detail-max-chars`(200), `rate-limit.per-minute`(5)·`per-day`(50), `retention`(30d), `cleanup-cron`(`0 45 4 * * *`), `cleanup-batch-size`(1000), `admin-page-size`(20), `suspension-durations`, `suspension-reason-max-chars`(200) |

## 8. 처리 흐름 요약

```text
신고:   401 → 403 → 400 형식 → 404 → 400 자기 것 → 400 기타 설명 → 429 → 사건(있으면 잠금, 없으면 만들기+스냅샷) → 신고(중복 무시) → 200
처리:   관리자 → 400 형식 → 사건 잠금(404) → 409 처리됨 → 409 대상 없음(닫음) → 400 자기 콘텐츠 → 400 혼자 신고 → 숨김? → 사건 닫기 → 이벤트
직접:   관리자 → 대상 볼 수 있나(404) → 400 자기 콘텐츠 → 이미 숨김이면 200 → 숨김 → 대기 사건 닫기 또는 새 사건 HIDDEN → ContentHidden
해제:   관리자 → 대상 있음(404) → 400 자기 콘텐츠 → 숨김 아니면 200 → 해제 → ContentUnhidden
정지:   관리자 → 회원(404) → 잠금 → 400 관리자/유예 → 409 정지 중 → 이력 + SUSPENDED → 세션 삭제(실패 503 롤백) → MemberSuspended
```
