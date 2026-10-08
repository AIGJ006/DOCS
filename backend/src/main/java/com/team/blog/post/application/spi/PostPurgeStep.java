package com.team.blog.post.application.spi;

/**
 * 완전 삭제 확장점 (006 contracts/events.md §2, research R10). 다른 모듈이 자기 테이블의 정리를 {@code DELETE FROM post}
 * <b>전에</b> 끼워 넣는다 — post 모듈은 다른 모듈의 테이블을 직접 쓰지 않는다(constitution II). {@code PostPurgeService}가
 * {@link #order()} 오름차순으로 부른다.
 *
 * <table>
 *   <caption>등록된 단계</caption>
 *   <tr><th>order</th><th>구현</th><th>처리</th></tr>
 *   <tr><td>10</td><td>신고 모듈 {@code ReportPostPurgeStep}</td><td>대기 중 신고 사건을 {@code CLOSED_NO_TARGET}으로 종료</td></tr>
 *   <tr><td>20</td><td>media {@code ImagePostPurgeStep}</td><td>그 글에서만 쓰던 사진에 {@code detached_at} 기록</td></tr>
 * </table>
 *
 * <p>새 단계는 10 단위 사이 값으로 끼운다. 015의 {@code WithdrawalPurgeStep}과 같은 방식이다.
 */
public interface PostPurgeStep {

    /** 실행 순서 (작을수록 먼저, 10 단위). */
    int order();

    /**
     * 글 행을 지우기 직전에 호출한다. 호출한 쪽의 트랜잭션 안에서 실행되며, 예외를 던지면 그 글의 완전 삭제 전체가 롤백된다. 트랜잭션 안이므로 외부 호출(파일 삭제
     * 등)을 하지 않는다.
     */
    void beforePurge(long postId);
}
