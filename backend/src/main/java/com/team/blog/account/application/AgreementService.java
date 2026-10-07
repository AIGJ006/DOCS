package com.team.blog.account.application;

import com.team.blog.account.domain.AgreementType;
import com.team.blog.account.domain.MemberAgreement;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.MemberAgreementRepository;
import com.team.blog.shared.error.FieldError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관·처리방침 동의 (FR-010~012, 07 §3-1, R-24). 현재 버전·시행일은 설정값 {@code blog.agreement.*}이고 문서 본문은 화면의 정적
 * 페이지({@code /terms}, {@code /privacy})다.
 */
@Service
public class AgreementService {

    /** 가입 때 받아야 하는 필수 동의 (AI는 013). */
    public static final List<AgreementType> REQUIRED =
            List.of(AgreementType.TERMS, AgreementType.PRIVACY);

    static final String FIELD = "agreements";

    private final AccountProperties.Agreement settings;
    private final MemberAgreementRepository repository;

    public AgreementService(AccountProperties properties, MemberAgreementRepository repository) {
        this.settings = properties.agreement();
        this.repository = repository;
    }

    public CurrentAgreements current() {
        return new CurrentAgreements(
                new CurrentAgreements.Document(
                        settings.terms().version(), settings.terms().effectiveDate(), "/terms"),
                new CurrentAgreements.Document(
                        settings.privacy().version(),
                        settings.privacy().effectiveDate(),
                        "/privacy"));
    }

    /** 현재 버전 (TERMS·PRIVACY). */
    public String currentVersion(AgreementType type) {
        return switch (type) {
            case TERMS -> settings.terms().version();
            case PRIVACY -> settings.privacy().version();
            case AI -> throw new IllegalArgumentException("AI 동의는 013이 관리합니다");
        };
    }

    /**
     * 보낸 동의 버전 검사. 하나라도 없으면 {@code AGREEMENT_REQUIRED}, 현재 버전과 다르면 {@code
     * AGREEMENT_VERSION_MISMATCH} (칸 이름 {@code agreements}). 통과하면 빈 목록.
     */
    public List<FieldError> consentErrors(AgreementVersions consent) {
        if (consent == null
                || isBlank(consent.termsVersion())
                || isBlank(consent.privacyVersion())) {
            return List.of(error(AccountReasonCode.AGREEMENT_REQUIRED));
        }
        if (!consent.termsVersion().equals(currentVersion(AgreementType.TERMS))
                || !consent.privacyVersion().equals(currentVersion(AgreementType.PRIVACY))) {
            return List.of(error(AccountReasonCode.AGREEMENT_VERSION_MISMATCH));
        }
        return List.of();
    }

    /** 가입 트랜잭션 안에서 TERMS·PRIVACY 두 행을 현재 버전으로 기록한다(FR-010 — 동의 기록 없이 계정이 생기지 않음). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOnSignup(long memberId, Instant now) {
        for (AgreementType type : REQUIRED) {
            repository.upsert(memberId, type.name(), currentVersion(type), now);
        }
    }

    /** 저장된 버전이 현재 버전과 다르거나 없는 필수 동의 종류 (로그인 때 재동의 판정, FR-012). */
    @Transactional(readOnly = true)
    public List<AgreementType> needsReagreement(long memberId) {
        Map<AgreementType, String> agreed =
                repository.findAllByMemberId(memberId).stream()
                        .collect(
                                Collectors.toMap(
                                        MemberAgreement::getType, MemberAgreement::getVersion));
        List<AgreementType> needed = new ArrayList<>();
        for (AgreementType type : REQUIRED) {
            if (!Objects.equals(agreed.get(type), currentVersion(type))) {
                needed.add(type);
            }
        }
        return List.copyOf(needed);
    }

    private static FieldError error(AccountReasonCode code) {
        return new FieldError(FIELD, code.code(), code.defaultMessage());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
