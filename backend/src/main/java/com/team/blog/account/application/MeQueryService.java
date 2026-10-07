package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 요약 ({@code GET /api/me}). 재동의 전·탈퇴 유예 중에도 허용된다. 회원이 없거나 익명 처리(015)됐으면 비로그인(401)과 같다. 프로필 사진
 * 주소는 media의 {@link ProfileImageQuery} + {@link ImageUrlResolver}로 만든다(모듈 경계, constitution II).
 */
@Service
public class MeQueryService {

    private final MemberRepository members;
    private final AuthIdentityRepository authIdentities;
    private final AgreementService agreementService;
    private final ProfileImageQuery profileImageQuery;
    private final ImageUrlResolver imageUrlResolver;

    public MeQueryService(
            MemberRepository members,
            AuthIdentityRepository authIdentities,
            AgreementService agreementService,
            ProfileImageQuery profileImageQuery,
            ImageUrlResolver imageUrlResolver) {
        this.members = members;
        this.authIdentities = authIdentities;
        this.agreementService = agreementService;
        this.profileImageQuery = profileImageQuery;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Transactional(readOnly = true)
    public MeSummary summary(long memberId) {
        Member member =
                members.findById(memberId)
                        .filter(m -> !m.isDeleted())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        String profileImageUrl =
                profileImageQuery
                        .currentKeys(memberId)
                        .map(ProfileImageKeys::display)
                        .map(imageUrlResolver::publicUrl)
                        .orElse(null);
        return new MeSummary(
                member.getHandle(),
                member.getNickname(),
                member.getRole().name(),
                member.getStatus().name(),
                identity.getProvider().name(),
                identity.isEmailVerified(),
                !agreementService.needsReagreement(memberId).isEmpty(),
                profileImageUrl);
    }
}
