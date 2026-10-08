package com.team.blog.account.application;

import com.team.blog.account.infra.LoginStampRepository;
import com.team.blog.account.infra.LoginStampRepository.LoginStamp;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공 처리 (FR-057, 07 §6, R-04·R-25). 갱신하기 전 {@code last_login_at}을 돌려주고 {@code last_login_at =
 * now}로 바꾼다. 세션 속성({@code previousLoginAt}·{@code provider})은 호출한 보안 처리기가 담는다.
 *
 * <p>정지 확인·만료 해제({@link SuspensionService#requireNotSuspended}), 재동의 판정({@link
 * AgreementService#needsReagreement})도 여기서 한다. 세션 {@code reagreementRequired} 표시와 실패 카운터 초기화(Redis
 * 쓰기 — 트랜잭션 밖)는 호출한 보안 처리기가 한다. 이메일 로그인·소셜 로그인 둘 다 이 메서드를 거친다.
 *
 * <p>SQL은 평소 3번이다: 열린 정지 1 + {@code last_login_at} 갱신(갱신 전 값·회원 상태를 함께 돌려받음) 1 + 동의 1(T145).
 */
@Service
public class LoginService {

    private final LoginStampRepository loginStamps;
    private final EntityManager entityManager;
    private final AgreementService agreementService;
    private final SuspensionService suspensionService;
    private final Clock clock;

    public LoginService(
            LoginStampRepository loginStamps,
            EntityManager entityManager,
            AgreementService agreementService,
            SuspensionService suspensionService,
            Clock clock) {
        this.loginStamps = loginStamps;
        this.entityManager = entityManager;
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
        // 기한 지난 정지를 방금 해제했으면(회원 상태 ACTIVE) 그 변경을 먼저 내보내야 아래 문장이 바뀐 상태를 읽는다. 바뀐 것이 없으면 SQL 없음.
        entityManager.flush();
        LoginStamp stamp =
                loginStamps
                        .record(memberId, clock.instant())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        boolean reagreement = !agreementService.needsReagreement(memberId).isEmpty();
        return new LoginOutcome(stamp.previousLoginAt(), stamp.status(), reagreement);
    }
}
