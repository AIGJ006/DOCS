package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 소개 검사 (FR-048, 11 §3, R-18). 글자만 저장하고 출력 때 이스케이프한다(HTML을 고치지 않는다).
 *
 * <pre>
 * ① 정리: 앞뒤 공백 제거 → NFC → {@code \r\n}·{@code \r}을 {@code \n}으로 → 연속 빈 줄을 하나로 → 비었으면 null
 * ② 길이: 코드 포인트 {@code max-length}(200) 이하 — DB {@code ck_member_bio}의 char_length와 같은 기준
 * ③ 줄 수: {@code \n}으로 나눈 줄 {@code max-lines}(4) 이하
 * ④ 금칙어 (예약어 검사는 하지 않는다)
 * </pre>
 */
public class BioPolicy {

    private static final Pattern BLANK_LINES = Pattern.compile("\n[ \t]*\n(?:[ \t]*\n)+");

    private final int maxLength;
    private final int maxLines;
    private final BannedWordFilter bannedWordFilter;

    public BioPolicy(int maxLength, int maxLines, BannedWordFilter bannedWordFilter) {
        this.maxLength = maxLength;
        this.maxLines = maxLines;
        this.bannedWordFilter = bannedWordFilter;
    }

    public BioCheck check(String raw) {
        String normalized = normalize(raw);
        return new BioCheck(normalized, firstFailure(normalized));
    }

    /** ① 정리. 비었으면 null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        value = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        value = BLANK_LINES.matcher(value).replaceAll("\n\n");
        return value.isEmpty() ? null : value;
    }

    private AccountReasonCode firstFailure(String value) {
        if (value == null) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            return AccountReasonCode.BIO_TOO_LONG;
        }
        if (value.split("\n", -1).length > maxLines) {
            return AccountReasonCode.BIO_TOO_MANY_LINES;
        }
        if (bannedWordFilter.containsBanned(value)) {
            return AccountReasonCode.BIO_BANNED_WORD;
        }
        return null;
    }
}
