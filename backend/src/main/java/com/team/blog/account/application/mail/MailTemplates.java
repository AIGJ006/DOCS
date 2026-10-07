package com.team.blog.account.application.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 메일 글자 템플릿 ({@code classpath:mail/{name}.txt}). 첫 줄은 {@code Subject: 제목}, 빈 줄 다음부터 본문이며 {@code
 * {{이름}}} 자리에 값을 넣는다. 본문은 일반 글자(HTML 아님)라 값을 이스케이프하지 않는다.
 */
@Component
public class MailTemplates {

    private static final String SUBJECT_PREFIX = "Subject:";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MailTemplates(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** 제목과 본문. */
    public record Mail(String subject, String body) {}

    public Mail render(String name, Map<String, String> values) {
        String text = cache.computeIfAbsent(name, this::load);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        String normalized = text.replace("\r\n", "\n");
        int firstBreak = normalized.indexOf('\n');
        String firstLine = firstBreak < 0 ? normalized : normalized.substring(0, firstBreak);
        if (!firstLine.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalStateException("메일 템플릿 첫 줄은 Subject: 여야 합니다: " + name);
        }
        String subject = firstLine.substring(SUBJECT_PREFIX.length()).strip();
        String body = firstBreak < 0 ? "" : normalized.substring(firstBreak + 1).stripLeading();
        return new Mail(subject, body);
    }

    private String load(String name) {
        try (var in =
                resourceLoader.getResource("classpath:mail/" + name + ".txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("메일 템플릿을 읽지 못했습니다: " + name, e);
        }
    }
}
