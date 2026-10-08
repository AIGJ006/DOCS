package com.team.blog.media.domain;

import java.util.Optional;

/**
 * 받는 사진 형식 4가지 (V1 {@code ck_image_type}, research R6·R7). 형식은 확장자·{@code Content-Type}이 아니라 파일
 * 앞부분(매직 바이트)으로 판별한다(US1 #3). SVG는 받지 않는다(헌법 IV).
 */
public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    /** 저장 키 확장자 ({@code jpg}·{@code png}·{@code gif}·{@code webp}). */
    public String extension() {
        return extension;
    }

    /** MIME 형식(소문자, 정확히 일치)으로 찾는다. 모르는 값·{@code null}이면 빈 값. */
    public static Optional<ImageFormat> ofMimeType(String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
        for (ImageFormat format : values()) {
            if (format.mimeType.equals(mimeType)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /**
     * 매직 바이트로 판별한다: JPEG {@code FF D8 FF}, PNG {@code 89 50 4E 47 0D 0A 1A 0A}, GIF {@code
     * GIF87a}/{@code GIF89a}, WebP {@code RIFF????WEBP}.
     */
    public static Optional<ImageFormat> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(head, 0, 'G', 'I', 'F', '8')
                && head.length >= 6
                && (head[4] == '7' || head[4] == '9')
                && head[5] == 'a') {
            return Optional.of(GIF);
        }
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
