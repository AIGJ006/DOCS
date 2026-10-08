package com.team.blog.account.application.port;

/**
 * 탈퇴 안내 숫자 중 글 쪽 (015 data-model §7, research R7). post 모듈 {@code AuthoredPostStatsAdapter}가 구현한다 —
 * account가 {@code post} 테이블을 직접 읽지 않는다(constitution II). 기본 구현은 두지 않는다(틀린 0을 보여 주느니 시작 실패).
 */
public interface AuthoredPostStats {

    /** 그 회원의 글 수(임시·발행·휴지통·숨김 전부)와 그 글들이 받은 좋아요 합 (SQL 1번). */
    PostStats statsOf(long memberId);

    /**
     * @param postCount 내 글 전부
     * @param receivedLikeCount 내 글 {@code like_count} 합
     */
    record PostStats(long postCount, long receivedLikeCount) {}
}
