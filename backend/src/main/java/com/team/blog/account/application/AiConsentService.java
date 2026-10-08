package com.team.blog.account.application;

import com.team.blog.account.domain.AgreementType;
import com.team.blog.account.domain.MemberAgreement;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.MemberAgreementRepository;
import com.team.blog.shared.error.ValidationException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 외부 전송 동의 (013 T019, research R10, data-model §1). {@code member_agreement}의 {@code type =
 * 'AI'} 행만 읽고 쓴다.
 *
 * <ul>
 *   <li>현재 버전은 {@code blog.agreement.ai.version}. 저장된 버전과 다르면 동의하지 않은 것으로 본다(다시 동의).
 *   <li>동의 = 001 {@code upsert}(같은 PK 행의 버전·시각 갱신), 철회 = 행 삭제(없어도 성공).
 *   <li>001 {@code AgreementService.REQUIRED}(가입·로그인 재동의 — TERMS·PRIVACY)는 바꾸지 않는다(Clarifications
 *       Q2).
 * </ul>
 */
@Service
public class AiConsentService {

    static final String FIELD = "version";

    private final MemberAgreementRepository repository;
    private final AccountProperties.Agreement settings;
    private final Clock clock;

    public AiConsentService(
            MemberAgreementRepository repository, AccountProperties properties, Clock clock) {
        this.repository = repository;
        this.settings = properties.agreement();
        this.clock = clock;
    }

    /** 현재 AI 동의 문구 버전. */
    public String currentVersion() {
        return settings.ai().version();
    }

    @Transactional(readOnly = true)
    public AiConsentView view(long memberId) {
        return toView(find(memberId));
    }

    /** 현재 버전에 동의했는가. */
    @Transactional(readOnly = true)
    public boolean isConsented(long memberId) {
        return find(memberId).map(a -> currentVersion().equals(a.getVersion())).orElse(false);
    }

    /**
     * 동의 (다시 동의 포함). 화면이 본 문구 버전이 현재와 다르면 400.
     *
     * @throws ValidationException {@code errors[0] {field: version, code:
     *     AGREEMENT_VERSION_MISMATCH}}
     */
    @Transactional
    public AiConsentView agree(long memberId, String version) {
        if (version == null || !version.equals(currentVersion())) {
            AccountReasonCode code = AccountReasonCode.AGREEMENT_VERSION_MISMATCH;
            throw ValidationException.of(FIELD, code.code(), code.defaultMessage());
        }
        repository.upsert(memberId, AgreementType.AI.name(), version, clock.instant());
        return toView(find(memberId));
    }

    /** 철회 = 행 삭제. 없어도 성공. */
    @Transactional
    public AiConsentView revoke(long memberId) {
        repository.deleteByMemberIdAndType(memberId, AgreementType.AI.name());
        return new AiConsentView(false, null, currentVersion(), null);
    }

    private Optional<MemberAgreement> find(long memberId) {
        return repository.findByMemberIdAndType(memberId, AgreementType.AI);
    }

    private AiConsentView toView(Optional<MemberAgreement> row) {
        return row.map(
                        a ->
                                new AiConsentView(
                                        currentVersion().equals(a.getVersion()),
                                        a.getVersion(),
                                        currentVersion(),
                                        a.getAgreedAt()))
                .orElseGet(() -> new AiConsentView(false, null, currentVersion(), null));
    }
}
