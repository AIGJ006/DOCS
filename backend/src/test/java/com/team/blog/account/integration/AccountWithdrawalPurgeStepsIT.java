package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.purge.AuthIdentityWithdrawalPurgeStep;
import com.team.blog.account.application.purge.FriendshipWithdrawalPurgeStep;
import com.team.blog.account.application.purge.MemberWithdrawalPurgeStep;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import com.team.blog.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** account 모듈 탈퇴 정리 단계 50·60·90 (015 T046, contracts/purge-steps.md §2·§2-1). */
class AccountWithdrawalPurgeStepsIT extends IntegrationTestBase {

    @Autowired AuthIdentityWithdrawalPurgeStep authIdentityStep;
    @Autowired FriendshipWithdrawalPurgeStep friendshipStep;
    @Autowired MemberWithdrawalPurgeStep memberStep;
    @Autowired TransactionTemplate tx;

    private void inTx(WithdrawalPurgeStep step, long memberId) {
        tx.executeWithoutResult(s -> step.purge(memberId));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** 친구 행: member_a_id < member_b_id. */
    private void friendship(long x, long y, long requestedBy, boolean accepted) {
        jdbc.update(
                "INSERT INTO friendship (member_a_id, member_b_id, requested_by, status,"
                        + " accepted_at) VALUES (?, ?, ?, ?, "
                        + (accepted ? "now()" : "NULL")
                        + ")",
                Math.min(x, y),
                Math.max(x, y),
                requestedBy,
                accepted ? "ACCEPTED" : "PENDING");
    }

    @Test
    void order_50_로그인_수단_삭제_뒤_같은_이메일로_새_계정() {
        long me = members().member().email("again@example.com").status("WITHDRAWN").create();
        long other = members().member().create();

        inTx(authIdentityStep, me);

        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", me)).isZero();
        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", other))
                .isEqualTo(1);
        long again = members().member().email("again@example.com").create();
        assertThat(again).isNotEqualTo(me);
        assertThat(authIdentityStep.order()).isEqualTo(50);
    }

    @Test
    void order_60_내_친구_관계는_어느_쪽이든_삭제_남의_관계는_그대로() {
        long me = members().member().status("WITHDRAWN").create();
        long a = members().member().create();
        long b = members().member().create();
        long c = members().member().create();
        friendship(me, a, me, false); // 내가 요청
        friendship(me, b, b, false); // 내가 받음
        friendship(me, c, c, true); // 수락
        friendship(a, b, a, true); // 남의 관계

        inTx(friendshipStep, me);

        assertThat(
                        count(
                                "SELECT count(*) FROM friendship WHERE member_a_id = ? OR"
                                        + " member_b_id = ?",
                                me,
                                me))
                .isZero();
        assertThat(count("SELECT count(*) FROM friendship")).isEqualTo(1);
        assertThat(friendshipStep.order()).isEqualTo(60);
    }

    @Test
    void order_90_익명화와_1행이_아니면_예외() {
        long me = members().member().handle("anon90").status("WITHDRAWN").create();

        inTx(memberStep, me);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", me);
        assertThat(row.get("handle")).isEqualTo("anon90");
        assertThat(row.get("nickname")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThatThrownBy(() -> inTx(memberStep, me)).hasMessageContaining("익명화 대상이 아닙니다");
        long active = members().member().create();
        assertThatThrownBy(() -> inTx(memberStep, active)).hasMessageContaining("익명화 대상이 아닙니다");
        assertThat(memberStep.order()).isEqualTo(90);
    }

    @Test
    void 모든_단계는_트랜잭션_밖에서_부르면_예외(@Autowired List<WithdrawalPurgeStep> steps) {
        long me = members().member().status("WITHDRAWN").create();
        assertThat(steps).hasSizeGreaterThanOrEqualTo(10);
        for (WithdrawalPurgeStep step : steps) {
            assertThatThrownBy(() -> step.purge(me))
                    .as(step.getClass().getName())
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThat(count("SELECT count(*) FROM auth_identity WHERE member_id = ?", me))
                .isEqualTo(1);
    }
}
