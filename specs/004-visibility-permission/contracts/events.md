# Events: 공개 범위와 권한

**기준**: [docs/20-domain-events.md](../../../docs/20-domain-events.md) §1(EV-1~EV-7)·§2·§3-1, [docs/06-visibility.md](../../../docs/06-visibility.md) §4

이 기능이 발행하는 서버 내부 도메인 이벤트다. 외부 브로커는 쓰지 않는다. 배치는 없다.

## 공통 규칙 (20 §1·§2)

| 항목 | 규칙 |
|---|---|
| 형식 | 불변 Java `record`, 패키지 `com.team.blog.shared.event`, 필드는 `long`/`Long` id·enum·`Instant`만 (EV-3, EV-7) |
| 발행 위치 | `PostVisibilityService`(Service 계층), 상태를 바꾼 같은 트랜잭션 안에서 `ApplicationEventPublisher.publishEvent` |
| 전달 | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")`. 롤백되면 버려진다 (EV-1) |
| 조건 | 값이 **실제로 바뀐 경우에만 한 번** (EV-4). 같은 값을 다시 보내면 이벤트가 없다 |
| 유실 | 허용 (EV-2). 구독자는 처리할 때 최신 상태를 다시 조회한다 |
| 실패 격리 | 리스너가 실패해도 공개 범위 변경 응답은 성공한다 (Constitution V) |

## 발행하는 이벤트

### `PostVisibilityChanged`

| 필드 | 타입 | 설명 |
|---|---|---|
| `postId` | `long` | 글 번호 |
| `authorId` | `long` | 작성자 번호 |
| `from` | `Visibility` | 이전 값 |
| `to` | `Visibility` | 새 값 |
| `changedAt` | `Instant` | 변경 시각 (`updated_at`과 같은 값) |

- **언제**: `PUT /api/posts/{postId}/visibility`로 **발행된 글**의 공개 범위가 실제로 바뀌었을 때. 임시글의 값 변경에는 발행하지 않는다(research R-25, 팀 확인 필요).
- **예상 구독자**(20 §5): 트렌딩(012), 검색 색인·sitemap(012). 알림(011)은 구독하지 않고, 알림을 화면에 보여 줄 때 다시 확인한다(20 §4-2).
- 다시 발행하면서 공개 범위를 바꾸는 경우(FR-022)의 이벤트는 발행 기능(002)이 `PostEdited`와 함께 정한다. 이 문서는 공개 범위 변경 엔드포인트만 다룬다.

### `PostWentPublic`

| 필드 | 타입 | 설명 |
|---|---|---|
| `postId` | `long` | 글 번호 |
| `authorId` | `long` | 작성자 번호 |
| `firstPublicAt` | `Instant` | 이번에 처음 기록된 최초 공개 일자 |

- **언제**: 이번 공개 범위 변경으로 `first_public_at`이 **처음** 채워졌을 때(비공개로 발행한 글을 처음 공개로 바꿀 때). `PostVisibilityChanged`와 같은 트랜잭션에서 함께 발행된다(20 §3-1). 엔티티 메서드 `Post.changeVisibility(to, now)`가 "처음 채웠는지"를 돌려주고, Service가 그 값으로 발행 여부를 정한다.
- 공개 → 비공개 → 공개처럼 다시 켤 때는 발행하지 않는다(EV-5, 05 P-3).
- **예상 구독자**: 알림 `NEW_POST`(011), 트렌딩·검색(012), 개인 확장 "첫 공개 응원"(강).

## 구독하는 이벤트

없음. 정지(`MemberSuspended`)·탈퇴(`MemberWithdrawn`) 때 세션을 지우는 일은 이벤트가 아니라 해당 Service가 직접 한다(20 §3-5: 이벤트는 유실될 수 있음).
