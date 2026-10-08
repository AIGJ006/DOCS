package com.team.blog.account.application.purge;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 60: 친구 관계 삭제 (015 T048, contracts/purge-steps.md §2). 내가 요청한·받은·수락한 행을 어느 쪽 칸이든 모두
 * 지운다. 친구 기능이 아직 없어도 항상 실행한다(2026-10-07 M1 — 테이블은 V1에 있다).
 */
@Component
public class FriendshipWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log = LoggerFactory.getLogger(FriendshipWithdrawalPurgeStep.class);

    private final JdbcClient jdbc;

    public FriendshipWithdrawalPurgeStep(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int deleted =
                jdbc.sql("DELETE FROM friendship WHERE member_a_id = :m OR member_b_id = :m")
                        .param("m", memberId)
                        .update();
        log.info("탈퇴 친구 관계 정리: memberId={} deleted={}", memberId, deleted);
    }
}
