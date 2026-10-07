package com.team.blog.post.domain;

import java.text.Normalizer;
import java.util.Optional;

/**
 * 발행 제목 정리 (FR-004, docs/12 §7-4·§9-3). 제목은 HTML로 렌더링하지 않고 글자 그대로 저장·표시하므로, 보이지 않는 글자와 방향 제어 문자로
 * 화면을 속이는 것만 막는다. 자동 저장·수동 저장은 입력 그대로 두고 발행 때만 정리한다.
 */
public final class TitleNormalizer {

    private TitleNormalizer() {}

    /** NFC → 폭 0·방향 제어(U+200B~200F, U+2060~2069, U+FEFF, U+202A~202E)·제어 문자 제거 → 앞뒤 공백 제거. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(nfc.length());
        nfc.codePoints().filter(cp -> !isRemoved(cp)).forEach(out::appendCodePoint);
        return out.toString().strip();
    }

    /** 정리한 제목의 규칙 위반 (0자 → {@code TITLE_REQUIRED}, 코드 포인트 수 > max → {@code TITLE_TOO_LONG}). */
    public static Optional<PostReasonCode> check(String normalized, int max) {
        if (normalized == null || normalized.isEmpty()) {
            return Optional.of(PostReasonCode.TITLE_REQUIRED);
        }
        if (normalized.codePointCount(0, normalized.length()) > max) {
            return Optional.of(PostReasonCode.TITLE_TOO_LONG);
        }
        return Optional.empty();
    }

    private static boolean isRemoved(int cp) {
        return (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x2060 && cp <= 0x2069)
                || cp == 0xFEFF
                || (cp >= 0x202A && cp <= 0x202E)
                || Character.isISOControl(cp);
    }
}
