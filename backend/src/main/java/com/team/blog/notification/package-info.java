/**
 * notification 모듈: 도메인 이벤트를 받아 인앱 알림을 만들고 보여 준다 (011, 20 §5·25).
 *
 * <p><b>모듈 규칙</b>
 *
 * <ul>
 *   <li>알림 테이블 3개({@code notification}·{@code notification_actor}·{@code notification_mute})는 이 모듈만
 *       읽고 쓴다. 다른 모듈은 {@code shared.event}의 이벤트로만 알리고, 알림을 바꾸는 공개 Service를 부르지 않는다.
 *   <li>이 모듈이 다른 모듈의 상태를 확인할 때는 공개 Service만 쓴다: account {@code MemberQueryService.findAccessInfo},
 *       post {@code PostReadService.isReadable}, interaction {@code
 *       CommentQueryService.isActive}·{@code LikeQueryService.isLiked}·{@code
 *       FollowQueryService.isFollowing}.
 *   <li>예외 2건(plan Complexity Tracking): 목록 SQL({@code NotificationListQueryRepository})과 새 글 일괄
 *       저장이 {@code member}·{@code image}·{@code post}·{@code comment}·{@code follow}를 읽기 전용으로
 *       JOIN한다.
 *   <li>리스너는 커밋 뒤 비동기({@code @Async("eventExecutor")} + {@code AFTER_COMMIT})이고, 실패해도 원래 행동에 영향이
 *       없다(예외를 잡아 ID만 WARN).
 * </ul>
 */
package com.team.blog.notification;
