package com.team.blog.account.application;

import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.account.infra.WithdrawalMemberRepository.LockedMember;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.event.MemberRestored;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 유예 중 복구 (015 T034, research R5, FR-018·019·021a). 회원 행을 잠그고 상태별로 나눈다.
 *
 * <ul>
 *   <li>없음·익명 처리됨 → 401 {@code LOGIN_REQUIRED} (정리가 먼저 커밋된 경우)
 *   <li>ACTIVE → 변화·이벤트 없이 끝 (두 번 누름)
 *   <li>SUSPENDED → 403 {@code ACCOUNT_SUSPENDED} (방어용)
 *   <li>WITHDRAWN: {@code now > withdrawn_at + grace}이면 409 {@code RESTORE_PERIOD_EXPIRED}, 아니면
 *       ACTIVE로 되돌리고 {@link MemberRestored}
 * </ul>
 *
 * 정리 작업도 같은 행을 잠그고 다시 확인하므로 복구와 정리는 겹치지 않는다. 지금 세션은 그대로 쓴다.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    private final WithdrawalMemberRepository withdrawals;
    private final WithdrawalPolicy policy;
    private final ApplicationEventPublisher events;

    public RestoreService(
            WithdrawalMemberRepository withdrawals,
            WithdrawalPolicy policy,
            ApplicationEventPublisher events) {
        this.withdrawals = withdrawals;
        this.policy = policy;
        this.events = events;
    }

    /**
     * @return 실제로 복구했으면 true (이미 활동 중이면 false)
     */
    @Transactional
    public boolean restore(long memberId) {
        LockedMember member =
                withdrawals
                        .lockForUpdate(memberId)
                        .filter(m -> !m.isDeleted())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        switch (member.status()) {
            case ACTIVE -> {
                return false;
            }
            case SUSPENDED -> throw new AccountStateException(CommonReasonCode.ACCOUNT_SUSPENDED);
            case WITHDRAWN -> {}
        }
        Instant now = policy.now();
        if (!policy.isRestorable(member.withdrawnAt(), now)) {
            throw new BusinessRuleException(AccountReasonCode.RESTORE_PERIOD_EXPIRED);
        }
        if (withdrawals.markRestored(memberId, now) != 1) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        events.publishEvent(new MemberRestored(memberId, now));
        log.info("탈퇴 복구 memberId={}", memberId);
        return true;
    }
}
