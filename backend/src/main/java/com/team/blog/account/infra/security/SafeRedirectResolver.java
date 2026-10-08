package com.team.blog.account.infra.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 로그인 후 이동 주소 검사 (R-33, FR-039). 사이트 안 상대 경로만 허용하고 아니면 {@code /}.
 *
 * <ul>
 *   <li>{@code /}로 시작한다.
 *   <li>{@code //}·{@code /\}로 시작하지 않는다(프로토콜 상대 주소·역슬래시 우회).
 *   <li>제어 문자·{@code \}가 없다.
 *   <li>URL 디코딩한 값도 위 조건을 만족한다(디코딩이 더 바뀌지 않을 때까지, 최대 3번).
 * </ul>
 *
 * 화면 쪽 {@code features/auth/safeRedirect.ts}가 같은 규칙을 쓴다.
 */
@Component
public class SafeRedirectResolver {

    public static final String FALLBACK = "/";

    static final int MAX_LENGTH = 2048;
    private static final int MAX_DECODE = 3;

    /** 검사를 통과하면 그 값, 아니면 {@code /}. */
    public String resolve(String candidate) {
        return isSafe(candidate) ? candidate : FALLBACK;
    }

    public boolean isSafe(String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return false;
        }
        String value = candidate;
        for (int i = 0; i <= MAX_DECODE; i++) {
            if (!passes(value)) {
                return false;
            }
            String decoded;
            try {
                decoded = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (decoded.equals(value)) {
                return true;
            }
            value = decoded;
        }
        return false;
    }

    private static boolean passes(String value) {
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '\\' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
