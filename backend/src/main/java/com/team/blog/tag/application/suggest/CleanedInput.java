package com.team.blog.tag.application.suggest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 정리된 입력 (013 research R5, contracts/providers.md §1). 길이는 코드 포인트 수다.
 *
 * @param text 정리된 글자 (제목 + 본문, NFC, 공백 하나로)
 * @param length 코드 포인트 수
 */
public record CleanedInput(String text, int length) {

    public CleanedInput {
        Objects.requireNonNull(text, "text");
    }

    public static CleanedInput of(String text) {
        return new CleanedInput(text, text.codePointCount(0, text.length()));
    }

    /** 재사용 열쇠 — 전체 글자(자르기 전)의 SHA-256 16진수 64자. */
    public String sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 앞 {@code maxChars} 코드 포인트. 이미 짧으면 그대로. */
    public CleanedInput truncate(int maxChars) {
        if (length <= maxChars) {
            return this;
        }
        int end = text.offsetByCodePoints(0, maxChars);
        return new CleanedInput(text.substring(0, end), maxChars);
    }

    /** {@code maxChars}를 넘는가. */
    public boolean longerThan(int maxChars) {
        return length > maxChars;
    }
}
