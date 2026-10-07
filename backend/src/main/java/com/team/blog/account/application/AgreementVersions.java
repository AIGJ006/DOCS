package com.team.blog.account.application;

/**
 * 가입·재동의 요청이 보낸 동의 문서 버전. 현재 버전과 같아야 한다(FR-010).
 *
 * @param termsVersion 이용약관 버전 (null이면 동의하지 않음)
 * @param privacyVersion 개인정보 처리방침 버전 (null이면 동의하지 않음)
 */
public record AgreementVersions(String termsVersion, String privacyVersion) {}
