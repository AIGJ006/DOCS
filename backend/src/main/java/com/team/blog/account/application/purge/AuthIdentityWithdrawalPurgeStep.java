package com.team.blog.account.application.purge;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 50: 로그인 수단 삭제 (015 T047, contracts/purge-steps.md §2). 지운 뒤에는 같은 이메일·같은 소셜 계정으로 새로
 * 가입할 수 있다(US4).
 */
@Component
public class AuthIdentityWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log =
            LoggerFactory.getLogger(AuthIdentityWithdrawalPurgeStep.class);

    private final JdbcClient jdbc;

    public AuthIdentityWithdrawalPurgeStep(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int deleted =
                jdbc.sql("DELETE FROM auth_identity WHERE member_id = :m")
                        .param("m", memberId)
                        .update();
        log.info("탈퇴 로그인 수단 정리: memberId={} deleted={}", memberId, deleted);
    }
}
