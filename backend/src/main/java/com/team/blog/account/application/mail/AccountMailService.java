package com.team.blog.account.application.mail;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AccountMailProperties;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.redis.AuthTokenStore;
import com.team.blog.account.infra.redis.TokenType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계정 메일 발송 (R-29, FR-009, contracts/events.md §2). 가입·재발송이 커밋된 뒤 {@code mailExecutor}에서 보낸다 — 메일이
 * 실패해도 가입·재발송 요청은 성공이고(constitution V) 사용자는 다시 보낼 수 있다. 실패는 경고 로그만 남긴다(주소·토큰은 남기지 않음).
 *
 * <p>인증 토큰은 <b>발송 시점에</b> 만든다(이벤트·로그에 토큰이 실리지 않음, FR-015). SMTP는 {@code spring.mail.*}.
 */
@Service
public class AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);

    private final AuthIdentityRepository authIdentities;
    private final MemberRepository members;
    private final AuthTokenStore tokenStore;
    private final MailTemplates templates;
    private final JavaMailSender mailSender;
    private final AccountMailProperties mailProperties;
    private final AccountProperties.Verify verifySettings;
    private final AccountProperties.Reset resetSettings;

    public AccountMailService(
            AuthIdentityRepository authIdentities,
            MemberRepository members,
            AuthTokenStore tokenStore,
            MailTemplates templates,
            JavaMailSender mailSender,
            AccountMailProperties mailProperties,
            AccountProperties accountProperties) {
        this.authIdentities = authIdentities;
        this.members = members;
        this.tokenStore = tokenStore;
        this.templates = templates;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.verifySettings = accountProperties.auth().verify();
        this.resetSettings = accountProperties.auth().reset();
    }

    /** 인증 메일: 새 토큰(이전 링크 무효) → {@code {linkBaseUrl}/verify-email?token=…} (24시간 안내). */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVerificationMailRequested(VerificationMailRequested event) {
        long memberId = event.memberId();
        try {
            Optional<AuthIdentity> identity = authIdentities.findByMemberId(memberId);
            if (identity.isEmpty()
                    || identity.get().getEmail() == null
                    || identity.get().isEmailVerified()) {
                return;
            }
            String nickname = members.findById(memberId).map(Member::getNickname).orElse("");
            String token = tokenStore.issue(TokenType.VERIFY, memberId);
            MailTemplates.Mail mail =
                    templates.render(
                            "verify-email",
                            Map.of(
                                    "nickname",
                                    nickname,
                                    "link",
                                    mailProperties.linkBaseUrl() + "/verify-email?token=" + token,
                                    "validHours",
                                    String.valueOf(verifySettings.tokenTtl().toHours())));
            send(identity.get().getEmail(), mail);
        } catch (RuntimeException e) {
            log.warn(
                    "인증 메일을 보내지 못했습니다: memberId={}, error={}",
                    memberId,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * 비밀번호 찾기 메일 (FR-042, R-11). 그 이메일의 계정을 모두 찾아, 이메일 가입 계정이 있으면 30분 재설정 링크(+ 같은 이메일의 소셜 계정 안내),
     * 소셜 계정만 있으면 "비밀번호가 없어요" 안내만 보낸다. 계정이 없으면 보내지 않는다.
     */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordResetMailRequested(PasswordResetMailRequested event) {
        try {
            List<AuthIdentity> identities = authIdentities.findAllByEmail(event.email());
            if (identities.isEmpty()) {
                return;
            }
            Optional<AuthIdentity> local =
                    identities.stream().filter(AuthIdentity::isLocal).findFirst();
            String socialProviders =
                    identities.stream()
                            .filter(identity -> !identity.isLocal())
                            .map(identity -> label(identity.getProvider()))
                            .distinct()
                            .sorted()
                            .collect(Collectors.joining("·"));
            MailTemplates.Mail mail;
            if (local.isPresent()) {
                String token = tokenStore.issue(TokenType.RESET, local.get().getMemberId());
                String notice =
                        socialProviders.isEmpty()
                                ? ""
                                : "\n이 이메일로 가입한 "
                                        + socialProviders
                                        + " 계정도 있어요. 그 계정은 "
                                        + socialProviders
                                        + "로 로그인하세요.\n";
                mail =
                        templates.render(
                                "password-reset",
                                Map.of(
                                        "link",
                                        mailProperties.linkBaseUrl()
                                                + "/reset-password?token="
                                                + token,
                                        "validMinutes",
                                        String.valueOf(resetSettings.tokenTtl().toMinutes()),
                                        "socialNotice",
                                        notice));
            } else {
                mail =
                        templates.render(
                                "password-reset-social-only",
                                Map.of(
                                        "providers",
                                        socialProviders,
                                        "link",
                                        mailProperties.linkBaseUrl() + "/login"));
            }
            send(event.email(), mail);
        } catch (RuntimeException e) {
            log.warn("비밀번호 찾기 메일을 보내지 못했습니다: error={}", e.getClass().getSimpleName());
        }
    }

    /** 비밀번호 변경 알림 (FR-045): "비밀번호가 변경됐어요. 본인이 아니라면 [비밀번호 재설정]". */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordChangedMailRequested(PasswordChangedMailRequested event) {
        long memberId = event.memberId();
        try {
            Optional<AuthIdentity> identity = authIdentities.findByMemberId(memberId);
            if (identity.isEmpty() || identity.get().getEmail() == null) {
                return;
            }
            String nickname = members.findById(memberId).map(Member::getNickname).orElse("");
            MailTemplates.Mail mail =
                    templates.render(
                            "password-changed",
                            Map.of(
                                    "nickname",
                                    nickname,
                                    "link",
                                    mailProperties.linkBaseUrl() + "/forgot-password"));
            send(identity.get().getEmail(), mail);
        } catch (RuntimeException e) {
            log.warn(
                    "비밀번호 변경 알림을 보내지 못했습니다: memberId={}, error={}",
                    memberId,
                    e.getClass().getSimpleName());
        }
    }

    private static String label(Provider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case GITHUB -> "GitHub";
            case LOCAL -> "이메일";
        };
    }

    private void send(String to, MailTemplates.Mail mail) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from());
            helper.setTo(to);
            helper.setSubject(mail.subject());
            helper.setText(mail.body(), false);
        } catch (MessagingException e) {
            throw new IllegalStateException("메일을 만들지 못했습니다", e);
        }
        mailSender.send(message);
    }
}
