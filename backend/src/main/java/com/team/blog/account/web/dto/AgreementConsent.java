package com.team.blog.account.web.dto;

import com.team.blog.account.application.AgreementVersions;

/** 동의한 문서 버전 (contracts {@code AgreementConsent}). 현재 버전과 같아야 한다. */
public record AgreementConsent(String termsVersion, String privacyVersion) {

    public AgreementVersions toVersions() {
        return new AgreementVersions(termsVersion, privacyVersion);
    }
}
