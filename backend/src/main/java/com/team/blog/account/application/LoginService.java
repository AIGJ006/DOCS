package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공 처리 (FR-057, 07 §6, R-04·R-25). 갱신하기 전 {@code last_login_at}을 돌려주고 {@code last_login_at =
 * now}로 바꾼다. 세션 속성({@code previousLoginAt}·{@code provider})은 호출한 보안 처리기가 담는다.
 *
 * <p>정지 확인·만료 해제, 재동의 표시(세션), 실패 카운터 초기화는 US5에서 이 메서드에 더한다.
 */
@Service
public class LoginService {

    private final AuthIdentityRepository authIdentities;
    private final MemberRepository members;
    private final AgreementService agreementService;
    private final SuspensionService suspensionService;
    private final Clock clock;

    public LoginService(
            AuthIdentityRepository authIdentities,
            MemberRepository members,
            AgreementService agreementService,
            SuspensionService suspensionService,
            Clock clock) {
        this.authIdentities = authIdentities;
        this.members = members;
        this.agreementService = agreementService;
        this.suspensionService = suspensionService;
        this.clock = clock;
    }

    /**
     * 비밀번호(또는 소셜 인증)가 맞은 뒤의 판정. 기한 지난 정지는 해제하고, 열린 정지가 남아 있으면 {@code AccountStateException}(403
     * {@code ACCOUNT_SUSPENDED})을 던진다 — 호출한 쪽이 인증을 되돌린다.
     */
    @Transactional
    public LoginOutcome onSuccess(long memberId) {
        suspensionService.requireNotSuspended(memberId);
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        Member member =
                members.findById(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        Instant previous = identity.recordLogin(clock.instant());
        boolean reagreement = !agreementService.needsReagreement(memberId).isEmpty();
        return new LoginOutcome(previous, member.getStatus(), reagreement);
    }
}
