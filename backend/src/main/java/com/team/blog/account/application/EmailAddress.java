package com.team.blog.account.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 이메일 정규화·형식 (FR-003·004, R-10). 앞뒤 공백 제거 + 소문자, 최대 254자, ASCII 일반 형식({@code @} 하나, 도메인에 {@code .}
 * 하나 이상). 국제화 도메인·따옴표 지역부는 받지 않는다.
 */
public final class EmailAddress {

    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT =
            Pattern.compile(
                    "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
                            + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                            + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private EmailAddress() {}

    /** 앞뒤 공백 제거 + 소문자 (null은 빈 문자열). */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** 정규화한 값이 형식에 맞는가. */
    public static boolean isValid(String normalized) {
        return normalized != null
                && !normalized.isEmpty()
                && normalized.length() <= MAX_LENGTH
                && FORMAT.matcher(normalized).matches();
    }
}
