package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 설정 조회·변경 (FR-046·053·061, 11 §6). 새 글 기본 공개 범위는 004 {@link VisibilityRegistry}에 등록된 값만
 * 허용한다(아니면 400 {@code INVALID_VISIBILITY} — DB {@code ck_member_default_visibility}와 같은 집합). 변경은
 * {@code ACCOUNT_WRITE}(인증 전도 통과).
 */
@Service
public class AccountSettingsService {

    static final String DEFAULT_VISIBILITY_FIELD = "defaultVisibility";

    private final MemberRepository members;
    private final AuthIdentityRepository authIdentities;
    private final AccountStatusGuard statusGuard;
    private final VisibilityRegistry visibilityRegistry;
    private final Clock clock;

    public AccountSettingsService(
            MemberRepository members,
            AuthIdentityRepository authIdentities,
            AccountStatusGuard statusGuard,
            VisibilityRegistry visibilityRegistry,
            Clock clock) {
        this.members = members;
        this.authIdentities = authIdentities;
        this.statusGuard = statusGuard;
        this.visibilityRegistry = visibilityRegistry;
        this.clock = clock;
    }

    /**
     * @param previousLogin 세션의 직전 로그인(없으면 null = 첫 로그인)
     */
    @Transactional(readOnly = true)
    public MySettings get(long memberId, PreviousLogin previousLogin) {
        Member member = member(memberId, false);
        return toSettings(member, identity(memberId), previousLogin);
    }

    @Transactional
    public MySettings update(long memberId, SettingsUpdate update, PreviousLogin previousLogin) {
        if (update == null || update.isEmpty()) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        Member member = member(memberId, true);
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        String visibility = null;
        if (update.defaultVisibilityPresent()) {
            visibility =
                    visibilityRegistry
                            .require(update.defaultVisibility(), DEFAULT_VISIBILITY_FIELD)
                            .name();
        }
        if (update.lastActiveVisiblePresent() && update.lastActiveVisible() == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        Instant now = clock.instant();
        if (visibility != null) {
            member.changeDefaultVisibility(visibility, now);
        }
        if (update.lastActiveVisiblePresent()) {
            member.changeLastActiveVisible(update.lastActiveVisible(), now);
        }
        return toSettings(member, identity(memberId), previousLogin);
    }

    private Member member(long memberId, boolean forUpdate) {
        return (forUpdate ? members.findByIdForUpdate(memberId) : members.findById(memberId))
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
    }

    private AuthIdentity identity(long memberId) {
        return authIdentities
                .findByMemberId(memberId)
                .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
    }

    private static MySettings toSettings(
            Member member, AuthIdentity identity, PreviousLogin previousLogin) {
        return new MySettings(
                identity.getEmail(),
                identity.getProvider().name(),
                previousLogin,
                member.getDefaultVisibility(),
                member.isLastActiveVisible(),
                identity.isLocal());
    }
}
