package com.team.blog.account.application;

import com.team.blog.account.application.policy.HandlePolicy;
import com.team.blog.account.application.policy.HandleSuggester;
import com.team.blog.account.application.policy.NicknamePolicy;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Role;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 판정과 가입 대기 정보 (R-06~R-08·R-20, FR-030·031·033).
 *
 * <ul>
 *   <li>{@link #login}: {@code (provider, providerUserId)}로 연결된 계정이 있으면 {@link
 *       LoginService#onSuccess}(정지 판정 포함), 없으면 {@link PendingSocialSignup}을 만든다(계정은 만들지 않는다). 세션에
 *       담는 것은 보안 처리기가 한다.
 *   <li>{@link #draft}: 마무리 화면의 미리 채운 값. 대기 정보가 없거나 10분이 지났으면 410 {@code SOCIAL_SIGNUP_EXPIRED}.
 *   <li>{@link #photoUrl}: 사진 주소는 HTTPS + 허용 호스트(`blog.auth.social.photo-hosts`)일 때만, 크기
 *       매개변수(Google {@code =s256-c}, GitHub {@code &s=256})를 붙여 화면에 넘긴다. 이메일을 직접 입력해야 하는 계정은 가입 직후
 *       인증 전이라 사진을 올릴 수 없으므로 넘기지 않는다(R-20). 서버는 이 주소를 요청·저장하지 않는다.
 * </ul>
 */
@Service
public class SocialLoginService {

    private static final Pattern GOOGLE_SIZE = Pattern.compile("=s\\d+(-c)?$");
    private static final String GOOGLE_HOST = "lh3.googleusercontent.com";
    private static final String GITHUB_HOST = "avatars.githubusercontent.com";

    private final AuthIdentityRepository authIdentities;
    private final MemberRepository members;
    private final LoginService loginService;
    private final HandleSuggester handleSuggester;
    private final NicknamePolicy nicknamePolicy;
    private final Duration pendingTtl;
    private final List<String> photoHosts;
    private final Clock clock;

    public SocialLoginService(
            AuthIdentityRepository authIdentities,
            MemberRepository members,
            LoginService loginService,
            HandleSuggester handleSuggester,
            NicknamePolicy nicknamePolicy,
            AccountProperties properties,
            Clock clock) {
        this.authIdentities = authIdentities;
        this.members = members;
        this.loginService = loginService;
        this.handleSuggester = handleSuggester;
        this.nicknamePolicy = nicknamePolicy;
        this.pendingTtl = properties.auth().social().pendingTtl();
        this.photoHosts =
                properties.auth().social().photoHosts().stream()
                        .map(h -> h.strip().toLowerCase(Locale.ROOT))
                        .toList();
        this.clock = clock;
    }

    /** 콜백 판정. 정지 계정이면 {@code AccountStateException}(403 {@code ACCOUNT_SUSPENDED})이 그대로 올라간다. */
    public SocialLoginResult login(SocialProfile profile) {
        Optional<AuthIdentity> linked =
                authIdentities.findByProviderAndProviderUserId(
                        profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            long memberId = linked.get().getMemberId();
            LoginOutcome outcome = loginService.onSuccess(memberId);
            return new SocialLoginResult.LoggedIn(memberId, roleOf(memberId), outcome);
        }
        return new SocialLoginResult.PendingSignup(
                PendingSocialSignup.of(profile, clock.instant()));
    }

    /** 마무리 화면의 미리 채운 값. */
    public SocialSignupDraft draft(PendingSocialSignup pending) {
        requireFresh(pending);
        String prefix = pending.provider().handlePrefix();
        String suggested = handleSuggester.suggest(pending.email(), pending.provider());
        return new SocialSignupDraft(
                pending.provider(),
                prefix,
                suggested.substring(HandlePolicy.prefixOf(suggested).length()),
                nicknamePolicy.suggestFromSocialName(pending.displayName()),
                pending.emailRequired() ? null : pending.email(),
                pending.emailRequired(),
                photoUrl(pending),
                existingAccountNotice(pending),
                pending.createdAt().plus(pendingTtl));
    }

    /** 대기 정보가 없거나 10분이 지났으면 410. */
    public PendingSocialSignup requireFresh(PendingSocialSignup pending) {
        if (pending == null || isExpired(pending)) {
            throw new ApiException(AccountReasonCode.SOCIAL_SIGNUP_EXPIRED);
        }
        return pending;
    }

    public boolean isExpired(PendingSocialSignup pending) {
        return !pending.createdAt().plus(pendingTtl).isAfter(clock.instant());
    }

    /** 화면에 넘길 사진 주소. 넘길 수 없으면 null. */
    public String photoUrl(PendingSocialSignup pending) {
        if (pending.emailRequired() || pending.pictureUrl() == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(pending.pictureUrl().strip());
        } catch (URISyntaxException e) {
            return null;
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !photoHosts.contains(host)
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawFragment() != null) {
            return null;
        }
        String base = "https://" + host + (uri.getRawPath() == null ? "" : uri.getRawPath());
        String query = uri.getRawQuery();
        if (GOOGLE_HOST.equals(host)) {
            java.util.regex.Matcher size = GOOGLE_SIZE.matcher(base);
            String path = size.find() ? size.replaceFirst("=s256-c") : base + "=s256-c";
            return query == null ? path : path + "?" + query;
        }
        if (GITHUB_HOST.equals(host)) {
            String kept =
                    query == null
                            ? ""
                            : String.join(
                                    "&",
                                    java.util.Arrays.stream(query.split("&"))
                                            .filter(p -> !p.isEmpty() && !p.startsWith("s="))
                                            .toList());
            return base + "?" + (kept.isEmpty() ? "" : kept + "&") + "s=256";
        }
        return query == null ? base : base + "?" + query;
    }

    /** 제공자가 확인한 이메일과 같은 이메일의 다른 수단 계정이 있다 (FR-033, R-08). */
    private boolean existingAccountNotice(PendingSocialSignup pending) {
        if (pending.emailRequired()) {
            return false;
        }
        return authIdentities.findAllByEmail(pending.email()).stream()
                .anyMatch(
                        identity ->
                                identity.getProvider() != pending.provider()
                                        || !identity.getProviderUserId()
                                                .equals(pending.providerUserId()));
    }

    private Role roleOf(long memberId) {
        return members.findById(memberId)
                .map(Member::getRole)
                .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
    }
}
