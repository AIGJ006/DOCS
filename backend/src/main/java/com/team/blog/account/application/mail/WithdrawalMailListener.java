package com.team.blog.account.application.mail;

import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AccountMailProperties;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.shared.event.MemberRestored;
import com.team.blog.shared.event.MemberWithdrawn;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴 접수·복구 메일 (015 T026·T036, research R14, FR-016·019, contracts/purge-steps.md §5). 커밋 뒤 {@code
 * mailExecutor}에서 보낸다 — 메일이 실패해도 신청·복구는 이미 성공이다(constitution V). 이메일이 없는 소셜 계정은 보내지 않는다. 실패는 경고
 * 로그만(주소 없이 회원 번호만). 기한은 서비스 시간대로 "2026년 11월 7일 오후 3:20".
 */
@Component
public class WithdrawalMailListener {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalMailListener.class);

    /** 화면 {@code formatDeadline}과 같은 모양. */
    static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN);

    private final AuthIdentityRepository authIdentities;
    private final MailTemplates templates;
    private final JavaMailSender mailSender;
    private final AccountMailProperties mailProperties;
    private final WithdrawalPolicy policy;
    private final ZoneId zone;

    public WithdrawalMailListener(
            AuthIdentityRepository authIdentities,
            MailTemplates templates,
            JavaMailSender mailSender,
            AccountMailProperties mailProperties,
            WithdrawalPolicy policy,
            ZoneId zone) {
        this.authIdentities = authIdentities;
        this.templates = templates;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.policy = policy;
        this.zone = zone;
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawn(MemberWithdrawn event) {
        String deadline = DEADLINE.format(policy.deadline(event.withdrawnAt()).atZone(zone));
        send(
                event.memberId(),
                "withdrawal-requested",
                Map.of("deadline", deadline, "link", mailProperties.linkBaseUrl() + "/login"),
                "탈퇴 접수");
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRestored(MemberRestored event) {
        send(
                event.memberId(),
                "account-restored",
                Map.of("link", mailProperties.linkBaseUrl() + "/settings"),
                "복구");
    }

    private void send(long memberId, String template, Map<String, String> values, String kind) {
        try {
            Optional<String> email =
                    authIdentities.findByMemberId(memberId).map(AuthIdentity::getEmail);
            if (email.isEmpty()) {
                return;
            }
            MailTemplates.Mail mail = templates.render(template, values);
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper =
                        new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(mailProperties.from());
                helper.setTo(email.get());
                helper.setSubject(mail.subject());
                helper.setText(mail.body(), false);
            } catch (MessagingException e) {
                throw new IllegalStateException("메일을 만들지 못했습니다", e);
            }
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.warn(
                    "{} 메일을 보내지 못했습니다: memberId={}, error={}",
                    kind,
                    memberId,
                    e.getClass().getSimpleName());
        }
    }
}
