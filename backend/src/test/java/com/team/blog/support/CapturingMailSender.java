package com.team.blog.support;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 통합 테스트용 메일 발송기(@Primary JavaMailSender). SMTP로 보내지 않고 받는 사람별로 보관한다.
 *
 * <p>{@link #lastTokenFor(String)}는 가장 최근 메일 본문의 {@code token=} 값을 꺼낸다(인증·재설정 링크 확인용).
 */
public class CapturingMailSender extends JavaMailSenderImpl {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final Map<String, List<MimeMessage>> byRecipient = new ConcurrentHashMap<>();

    /** 다음 발송 한 번을 실패시킨다 (015 메일 실패 시험). {@link #clear()}가 되돌린다. */
    private final java.util.concurrent.atomic.AtomicBoolean failNext =
            new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages)
            throws MailException {
        if (failNext.getAndSet(false)) {
            throw new org.springframework.mail.MailSendException("시험용 발송 실패");
        }
        for (MimeMessage message : mimeMessages) {
            try {
                Address[] recipients = message.getRecipients(Message.RecipientType.TO);
                if (recipients == null) {
                    continue;
                }
                for (Address address : recipients) {
                    String email = address.toString().toLowerCase(Locale.ROOT);
                    byRecipient
                            .computeIfAbsent(email, k -> new CopyOnWriteArrayList<>())
                            .add(message);
                }
            } catch (MessagingException e) {
                throw new MailPreparationException(e);
            }
        }
    }

    /** 받는 사람에게 보낸 메일 (보낸 순서). */
    public List<MimeMessage> messagesTo(String email) {
        return Collections.unmodifiableList(
                byRecipient.getOrDefault(email.toLowerCase(Locale.ROOT), List.of()));
    }

    /** 받는 사람에게 보낸 메일 수. */
    public int countTo(String email) {
        return messagesTo(email).size();
    }

    /** 가장 최근 메일의 본문 글자. */
    public Optional<String> lastTextFor(String email) {
        List<MimeMessage> messages = messagesTo(email);
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(textOf(messages.get(messages.size() - 1)));
    }

    /** 가장 최근 메일 본문의 {@code token=} 값. */
    public Optional<String> lastTokenFor(String email) {
        return lastTextFor(email).map(TOKEN::matcher).filter(Matcher::find).map(m -> m.group(1));
    }

    /** 다음 발송 한 번을 {@code MailSendException}으로 실패시킨다. */
    public void failNext() {
        failNext.set(true);
    }

    public void clear() {
        failNext.set(false);
        byRecipient.clear();
    }

    private static String textOf(MimeMessage message) {
        try {
            return contentText(message.getContent());
        } catch (IOException | MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String contentText(Object content) throws MessagingException, IOException {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                parts.add(contentText(part.getContent()));
            }
            return String.join("\n", parts);
        }
        return String.valueOf(content);
    }
}
