package com.team.blog.interaction.domain;

import com.team.blog.shared.text.InvisibleCharacters;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 댓글 내용 정리·판정 (007 FR-004·FR-005, research R3). 댓글은 HTML로 바꾸지 않고 글자 그대로 저장·표시한다.
 *
 * <ol>
 *   <li>NFC
 *   <li>보이지 않는 글자({@link InvisibleCharacters}) 제거 — 줄바꿈(LF·CR)은 남기고 탭은 공백 하나로
 *   <li>줄바꿈 통일: CRLF·CR → LF
 *   <li>앞뒤 공백·줄바꿈 제거
 *   <li>공백만 있는 줄은 빈 줄로 보고, 연달아 있는 빈 줄은 하나로({@code \n{3,}} → {@code \n\n})
 * </ol>
 *
 * 길이는 코드 포인트로 센다(이모지 1자, Java {@code length()} 금지). 금칙어 검사는 하지 않는다(FR-007).
 */
public final class CommentText {

    private static final Pattern BLANK_LINE = Pattern.compile("(?m)^[\\p{Zs}\\t ]+$");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\n{3,}");

    private CommentText() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(nfc.length());
        nfc.codePoints()
                .forEach(
                        cp -> {
                            if (cp == '\n' || cp == '\r') {
                                out.appendCodePoint(cp);
                            } else if (cp == '\t') {
                                out.append(' ');
                            } else if (!InvisibleCharacters.isInvisible(cp)) {
                                out.appendCodePoint(cp);
                            }
                        });
        String text = out.toString().replace("\r\n", "\n").replace('\r', '\n').strip();
        text = BLANK_LINE.matcher(text).replaceAll("");
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    /** 정리한 내용의 규칙 위반 (공백뿐 → 필수, 코드 포인트 수 &gt; max → 너무 김). */
    public static Optional<CommentReasonCode> check(String normalized, int max) {
        if (normalized == null || isBlank(normalized)) {
            return Optional.of(CommentReasonCode.COMMENT_REQUIRED);
        }
        if (length(normalized) > max) {
            return Optional.of(CommentReasonCode.COMMENT_TOO_LONG);
        }
        return Optional.empty();
    }

    /** 코드 포인트 수. */
    public static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean isBlank(String value) {
        return value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }
}
