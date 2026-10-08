package com.team.blog.interaction.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>임시 구현</b> — 탈퇴 정리 order 65: 팔로우 양방향 삭제 (015 contracts/purge-steps.md §2, 010 follow-sql §7).
 *
 * <p>이 단계의 주인은 <b>010-follow-feed</b>(010 tasks T041 {@code FollowWithdrawalPurgeStep})다. 010이 머지되기
 * 전에도 015 정리 작업의 필수 단계(order 65)가 비지 않도록 015가 같은 SQL로 채워 둔다. 010이 진짜 클래스를 만들면 {@link
 * ConditionalOnMissingClass}로 이 Bean은 등록되지 않는다 — 그때 이 파일을 지운다.
 */
@Component
@ConditionalOnMissingClass("com.team.blog.interaction.application.FollowWithdrawalPurgeStep")
public class InterimFollowWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log =
            LoggerFactory.getLogger(InterimFollowWithdrawalPurgeStep.class);

    private final JdbcClient jdbc;

    public InterimFollowWithdrawalPurgeStep(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 65;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int deleted =
                jdbc.sql("DELETE FROM follow WHERE follower_id = :m OR followee_id = :m")
                        .param("m", memberId)
                        .update();
        log.info("탈퇴 팔로우 정리(임시): memberId={} deleted={}", memberId, deleted);
    }
}
