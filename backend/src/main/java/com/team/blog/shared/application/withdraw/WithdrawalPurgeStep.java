package com.team.blog.shared.application.withdraw;

/**
 * 탈퇴 30일 정리 확장점 (015 contracts/purge-steps.md §1, data-model §6, 44 §4). 각 모듈이 자기 테이블의 정리를 이 인터페이스로
 * 끼워 넣고, 015 {@code WithdrawalPurgeRunner}가 회원 한 명마다 한 트랜잭션 안에서 {@link #order()} 오름차순으로 부른다. 015의
 * 정리 작업은 단계 내부를 모른다.
 *
 * <table>
 *   <caption>단계 표 (contracts §2)</caption>
 *   <tr><th>order</th><th>클래스 (모듈)</th><th>처리</th></tr>
 *   <tr><td>10</td><td>{@code PostWithdrawalPurgeStep} (post)</td><td>내 글 전부 완전 삭제 (006 {@code PostPurgeService})</td></tr>
 *   <tr><td>20</td><td>{@code CommentWithdrawalPurgeStep} (interaction)</td><td>남의 글의 내 댓글 (007)</td></tr>
 *   <tr><td>30</td><td>{@code LikeWithdrawalPurgeStep} (interaction)</td><td>내가 누른 좋아요 (009)</td></tr>
 *   <tr><td>40</td><td>{@code ImageWithdrawalPurgeStep} (media)</td><td>내 사진 연결 해제 (003)</td></tr>
 *   <tr><td>50</td><td>{@code AuthIdentityWithdrawalPurgeStep} (account)</td><td>로그인 수단 삭제</td></tr>
 *   <tr><td>60</td><td>{@code FriendshipWithdrawalPurgeStep} (account)</td><td>친구 관계 삭제</td></tr>
 *   <tr><td>65</td><td>{@code FollowWithdrawalPurgeStep} (010)</td><td>팔로우 양방향 삭제</td></tr>
 *   <tr><td>70</td><td>{@code NotificationWithdrawalPurgeStep} (011)</td><td>알림 정리</td></tr>
 *   <tr><td>80</td><td>{@code ReportWithdrawalPurgeStep} (014)</td><td>신고 사건 종료·설명 비우기</td></tr>
 *   <tr><td>90</td><td>{@code MemberWithdrawalPurgeStep} (account)</td><td>회원 익명화 — 마지막</td></tr>
 * </table>
 *
 * <p>규칙
 *
 * <ul>
 *   <li>단계는 그 회원이 아직 {@code member} 행을 가진 상태에서 불린다(order 90 전까지 닉네임 등도 그대로).
 *   <li>단계는 멱등이어야 한다. 앞선 날 다른 단계가 실패해 롤백된 뒤 다시 불려도 같은 결과여야 한다.
 *   <li>단계는 이벤트를 내지 않는다. 예외: order 10이 부르는 006 {@code PostPurgeService}의 {@code PostPurged}(글마다).
 *   <li>단계는 로그에 회원 번호와 처리 건수만 남긴다(이메일·닉네임 금지).
 * </ul>
 *
 * <p>010·011·014가 015보다 먼저 구현되면 이 파일을 그 기능이 먼저 만든다(한 파일, 015 tasks T009).
 */
public interface WithdrawalPurgeStep {

    /** 실행 순서 (10 단위, 새 단계는 사이 값). 같은 값이 둘이면 애플리케이션 시작이 실패한다(FR-029). */
    int order();

    /**
     * 호출한 쪽 트랜잭션({@code MANDATORY}) 안에서 그 회원의 데이터를 정리한다. 외부 호출(파일 삭제·메일·Redis)은 하지 않는다. 예외를 던지면 그
     * 회원의 정리 전체가 롤백된다.
     */
    void purge(long memberId);
}
