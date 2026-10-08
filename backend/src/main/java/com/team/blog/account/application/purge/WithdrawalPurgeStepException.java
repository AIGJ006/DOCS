package com.team.blog.account.application.purge;

/** 탈퇴 정리 단계 하나가 실패함 (015 T055). 그 회원의 정리 트랜잭션 전체가 롤백되고 다음 날 다시 대상이 된다. */
public class WithdrawalPurgeStepException extends RuntimeException {

    private final int order;
    private final String stepName;

    public WithdrawalPurgeStepException(
            long memberId, int order, String stepName, RuntimeException cause) {
        super("탈퇴 정리 단계 실패: memberId=" + memberId + " order=" + order + " step=" + stepName, cause);
        this.order = order;
        this.stepName = stepName;
    }

    public int order() {
        return order;
    }

    public String stepName() {
        return stepName;
    }
}
