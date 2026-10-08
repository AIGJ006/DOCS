package com.team.blog.account.application.port;

/**
 * 탈퇴 안내 숫자 중 댓글 쪽 (015 data-model §7, research R7). interaction 모듈 {@code
 * AuthoredCommentStatsAdapter}가 구현한다. 기본 구현은 두지 않는다.
 */
public interface AuthoredCommentStats {

    /** 남의 글에 쓴 내 댓글 중 지워지지 않은 것의 수 (내 글의 댓글·지운 댓글 제외). */
    long countOnOthersPosts(long memberId);
}
