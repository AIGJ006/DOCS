package com.team.blog.account.application;

import com.team.blog.account.application.mail.VerificationMailRequested;
import com.team.blog.account.application.policy.HandlePolicy;
import com.team.blog.account.application.policy.HandleSuggester;
import com.team.blog.account.application.policy.NicknameCheck;
import com.team.blog.account.application.policy.NicknamePolicy;
import com.team.blog.account.application.policy.PasswordPolicy;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.infra.db.UniqueViolations;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입 (FR-002~005·010·013·016~026, R-09).
 *
 * <p>이메일 가입 순서: 모든 칸을 검사해 오류를 모은다(이메일 → 주소 → 비밀번호 → 닉네임 → 동의) → 하나라도 있으면 400 {@code
 * VALIDATION_FAILED}로 아무것도 만들지 않는다 → 한 트랜잭션에서 {@code member} → {@code auth_identity}(LOCAL, BCrypt)
 * → {@code member_agreement} 2행 → 커밋 후 인증 메일. 동시 가입은 DB UNIQUE가 막고, 위반은 선조회와 같은 칸 오류로 바꾼다({@code
 * uq_auth_identity}·{@code uq_member_handle}·{@code uq_member_nickname}).
 *
 * <p>가입하면 바로 로그인되는데 세션은 Redis에 있으므로, Redis가 응답하지 않으면 계정을 만들기 전에 503으로 거부한다(R-30).
 */
@Service
public class SignupService {

    static final String HANDLE_SUGGESTION = "handleSuggestion";

    private final MemberRepository members;
    private final AuthIdentityRepository authIdentities;
    private final AgreementService agreementService;
    private final HandlePolicy handlePolicy;
    private final HandleSuggester handleSuggester;
    private final NicknamePolicy nicknamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final RedisGuard redisGuard;
    private final Clock clock;

    public SignupService(
            MemberRepository members,
            AuthIdentityRepository authIdentities,
            AgreementService agreementService,
            HandlePolicy handlePolicy,
            HandleSuggester handleSuggester,
            NicknamePolicy nicknamePolicy,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            RedisGuard redisGuard,
            Clock clock) {
        this.members = members;
        this.authIdentities = authIdentities;
        this.agreementService = agreementService;
        this.handlePolicy = handlePolicy;
        this.handleSuggester = handleSuggester;
        this.nicknamePolicy = nicknamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.redisGuard = redisGuard;
        this.clock = clock;
    }

    /** 이메일 가입. 성공하면 인증 전 회원이 생기고 커밋 후 인증 메일이 간다. 로그인 처리는 호출한 쪽(웹)이 한다. */
    public SignedUpMember signupWithEmail(EmailSignupCommand command) {
        String email = EmailAddress.normalize(command.email());
        String handle = command.handle() == null ? "" : command.handle().strip();
        List<FieldError> errors = new ArrayList<>();
        String suggestion = null;

        if (!EmailAddress.isValid(email)) {
            errors.add(fieldError("email", AccountReasonCode.EMAIL_INVALID_FORMAT));
        } else if (authIdentities
                .findByProviderAndProviderUserId(Provider.LOCAL, email)
                .isPresent()) {
            errors.add(fieldError("email", AccountReasonCode.EMAIL_ALREADY_REGISTERED));
        }

        List<AccountReasonCode> handleFailures = handlePolicy.validate(handle, Provider.LOCAL);
        if (!handleFailures.isEmpty()) {
            errors.add(fieldError("handle", handleFailures.getFirst()));
        } else if (members.existsByHandle(handle)) {
            suggestion = handleSuggester.nextAvailable(handle);
            errors.add(handleDuplicate("handle", "이미 사용 중인 주소예요", suggestion));
        }

        errors.addAll(
                passwordPolicy.violations(command.password(), command.passwordConfirm(), email));

        NicknameCheck nickname = nicknamePolicy.check(command.nickname(), null);
        if (!nickname.valid()) {
            errors.add(nickname.toFieldError("nickname"));
        }

        errors.addAll(agreementService.consentErrors(command.agreements()));

        if (!errors.isEmpty()) {
            throw new ValidationException(
                    errors, suggestion == null ? null : Map.of(HANDLE_SUGGESTION, suggestion));
        }
        requireSessionStore();

        String passwordHash = passwordEncoder.encode(command.password());
        try {
            return transaction.execute(
                    status ->
                            createLocalMember(email, handle, nickname.normalized(), passwordHash));
        } catch (DataIntegrityViolationException e) {
            throw duplicateOf(e, handle);
        }
    }

    private SignedUpMember createLocalMember(
            String email, String handle, String nickname, String passwordHash) {
        Instant now = clock.instant();
        Member member = members.saveAndFlush(Member.join(handle, nickname, now));
        long memberId = member.getId();
        authIdentities.saveAndFlush(AuthIdentity.local(memberId, email, passwordHash, now));
        agreementService.recordOnSignup(memberId, now);
        events.publishEvent(new VerificationMailRequested(memberId));
        return new SignedUpMember(
                memberId, handle, nickname, false, member.getRole(), Provider.LOCAL);
    }

    /**
     * 소셜 가입 마무리 (FR-008·010·030, R-07·R-09). 대기 정보가 없거나 만료면 410(호출한 쪽이 확인). {@code handleBody}에 가입
     * 수단 접두어를 붙여 검사하고, 닉네임·동의, ({@code emailRequired}이면) 이메일 형식을 검사한다. 한 트랜잭션에서 {@code
     * member}·{@code auth_identity}(제공자가 확인한 이메일이면 가입 시각으로 인증, 직접 입력이면 인증 전 + 인증 메일)·동의 2행을 만든다. 같은
     * 소셜 계정이 그 사이에 가입을 마쳤으면({@code uq_auth_identity}) 그 계정을 돌려준다(이미 연결된 계정으로 로그인).
     */
    public SignedUpMember completeSocialSignup(
            SocialSignupCommand command, PendingSocialSignup pending) {
        Provider provider = pending.provider();
        String body = command.handleBody() == null ? "" : command.handleBody().strip();
        String handle = provider.handlePrefix() + body;
        List<FieldError> errors = new ArrayList<>();
        String suggestion = null;

        String email =
                pending.emailRequired() ? EmailAddress.normalize(command.email()) : pending.email();
        if (pending.emailRequired() && !EmailAddress.isValid(email)) {
            errors.add(fieldError("email", AccountReasonCode.EMAIL_INVALID_FORMAT));
        }

        List<AccountReasonCode> handleFailures = handlePolicy.validate(handle, provider);
        if (!handleFailures.isEmpty()) {
            errors.add(fieldError("handleBody", handleFailures.getFirst()));
        } else if (members.existsByHandle(handle)) {
            suggestion = handleSuggester.nextAvailable(handle);
            errors.add(handleDuplicate("handleBody", "이미 사용 중인 주소예요", suggestion));
        }

        NicknameCheck nickname = nicknamePolicy.check(command.nickname(), null);
        if (!nickname.valid()) {
            errors.add(nickname.toFieldError("nickname"));
        }
        errors.addAll(agreementService.consentErrors(command.agreements()));

        if (!errors.isEmpty()) {
            throw new ValidationException(
                    errors, suggestion == null ? null : Map.of(HANDLE_SUGGESTION, suggestion));
        }
        requireSessionStore();

        boolean verified = !pending.emailRequired();
        try {
            return transaction.execute(
                    status ->
                            createSocialMember(
                                    pending, handle, nickname.normalized(), email, verified));
        } catch (DataIntegrityViolationException e) {
            if ("uq_auth_identity".equals(UniqueViolations.constraintName(e).orElse(""))) {
                return alreadyLinked(pending).orElseThrow(() -> e);
            }
            throw duplicateOf(e, handle, "handleBody");
        }
    }

    private SignedUpMember createSocialMember(
            PendingSocialSignup pending,
            String handle,
            String nickname,
            String email,
            boolean verified) {
        Instant now = clock.instant();
        Member member = members.saveAndFlush(Member.join(handle, nickname, now));
        long memberId = member.getId();
        authIdentities.saveAndFlush(
                AuthIdentity.social(
                        memberId,
                        pending.provider(),
                        pending.providerUserId(),
                        email,
                        verified,
                        now));
        agreementService.recordOnSignup(memberId, now);
        if (!verified) {
            events.publishEvent(new VerificationMailRequested(memberId));
        }
        return new SignedUpMember(
                memberId, handle, nickname, verified, member.getRole(), pending.provider());
    }

    private java.util.Optional<SignedUpMember> alreadyLinked(PendingSocialSignup pending) {
        return authIdentities
                .findByProviderAndProviderUserId(pending.provider(), pending.providerUserId())
                .flatMap(
                        identity ->
                                members.findById(identity.getMemberId())
                                        .map(
                                                m ->
                                                        new SignedUpMember(
                                                                m.getId(),
                                                                m.getHandle(),
                                                                m.getNickname(),
                                                                identity.isEmailVerified(),
                                                                m.getRole(),
                                                                identity.getProvider())));
    }

    /** 동시 가입에서 진 쪽: 제약 이름 → 칸 오류 (R-09). 트랜잭션이 끝난 뒤라 대안 주소를 새로 조회할 수 있다. */
    private RuntimeException duplicateOf(DataIntegrityViolationException e, String handle) {
        return duplicateOf(e, handle, "handle");
    }

    private RuntimeException duplicateOf(
            DataIntegrityViolationException e, String handle, String handleField) {
        String constraint = UniqueViolations.constraintName(e).orElse("");
        return switch (constraint) {
            case "uq_auth_identity" ->
                    ValidationException.of(
                            "email",
                            AccountReasonCode.EMAIL_ALREADY_REGISTERED.code(),
                            AccountReasonCode.EMAIL_ALREADY_REGISTERED.defaultMessage());
            case "uq_member_handle" -> {
                String suggestion = handleSuggester.nextAvailable(handle);
                yield new ValidationException(
                        List.of(handleDuplicate(handleField, "방금 다른 분이 이 주소를 사용했어요", suggestion)),
                        Map.of(HANDLE_SUGGESTION, suggestion));
            }
            case "uq_member_nickname" ->
                    ValidationException.of(
                            "nickname",
                            AccountReasonCode.NICKNAME_DUPLICATE.code(),
                            "방금 다른 분이 이 닉네임을 사용했어요");
            default -> e;
        };
    }

    private void requireSessionStore() {
        if (!redisGuard.isAvailable()) {
            throw new TemporarilyUnavailableException();
        }
    }

    private static FieldError handleDuplicate(String field, String lead, String suggestion) {
        return new FieldError(
                field,
                AccountReasonCode.HANDLE_DUPLICATE.code(),
                lead + ". `" + suggestion + "`는 어떠세요?");
    }

    private static FieldError fieldError(String field, AccountReasonCode code) {
        return new FieldError(field, code.code(), code.defaultMessage());
    }
}
