package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.FieldError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 비밀번호 정책 (FR-013~015, 07 §4, R-14). 가입·재설정·변경이 함께 쓴다.
 *
 * <p>비밀번호 원문은 결과·예외·로그 어디에도 넣지 않는다(FR-015). 오류 문구는 고정 문구다.
 */
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 16;

    /** 지정 특수문자 (FR-013). */
    public static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

    private static final int MIN_EMAIL_LOCAL_LENGTH = 3;

    private final Set<String> commonPasswords;

    public PasswordPolicy(Collection<String> commonPasswords) {
        this.commonPasswords =
                Set.copyOf(commonPasswords.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList());
    }

    /** 규칙별 결과({@link PasswordRule} 순서). */
    public List<PasswordRuleResult> evaluate(String password, String confirm, String email) {
        String pw = password == null ? "" : password;
        List<PasswordRuleResult> results = new ArrayList<>(PasswordRule.values().length);
        for (PasswordRule rule : PasswordRule.values()) {
            results.add(new PasswordRuleResult(rule, satisfied(rule, pw, confirm, email)));
        }
        return results;
    }

    /**
     * 위반한 규칙의 칸 오류(같은 코드는 한 번만, 규칙 순서). 확인 불일치는 {@code passwordConfirm} 칸, 나머지는 {@code password} 칸.
     */
    public List<FieldError> violations(String password, String confirm, String email) {
        return violations(password, confirm, email, "password", "passwordConfirm");
    }

    /** 칸 이름을 바꿔 쓸 때(비밀번호 변경의 {@code newPassword} 등). */
    public List<FieldError> violations(
            String password,
            String confirm,
            String email,
            String passwordField,
            String confirmField) {
        Set<AccountReasonCode> codes = new LinkedHashSet<>();
        for (PasswordRuleResult result : evaluate(password, confirm, email)) {
            if (!result.satisfied()) {
                codes.add(result.rule().failureCode());
            }
        }
        return codes.stream()
                .map(
                        code ->
                                new FieldError(
                                        code == AccountReasonCode.PASSWORD_CONFIRM_MISMATCH
                                                ? confirmField
                                                : passwordField,
                                        code.code(),
                                        code.defaultMessage()))
                .toList();
    }

    private boolean satisfied(PasswordRule rule, String pw, String confirm, String email) {
        return switch (rule) {
            case LENGTH -> pw.length() >= MIN_LENGTH && pw.length() <= MAX_LENGTH;
            case LETTER_CASE ->
                    pw.chars().anyMatch(c -> c >= 'A' && c <= 'Z')
                            && pw.chars().anyMatch(c -> c >= 'a' && c <= 'z');
            case DIGIT -> pw.chars().anyMatch(c -> c >= '0' && c <= '9');
            case SPECIAL -> pw.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0);
            case ALLOWED_CHARS -> pw.chars().allMatch(PasswordPolicy::isAllowed);
            case NOT_EMAIL -> !containsEmailLocalPart(pw, email);
            case NOT_COMMON -> !commonPasswords.contains(pw.toLowerCase(Locale.ROOT));
            case CONFIRM -> Objects.equals(pw, confirm == null ? "" : confirm);
        };
    }

    private static boolean isAllowed(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || SPECIAL_CHARS.indexOf(c) >= 0;
    }

    /** 이메일 {@code @} 앞부분({@code +} 앞까지, 소문자)이 3자 이상이면 그 문자열이 비밀번호(소문자)에 들어 있는가. */
    private static boolean containsEmailLocalPart(String pw, String email) {
        if (email == null || pw.isEmpty()) {
            return false;
        }
        String local = email.strip().toLowerCase(Locale.ROOT);
        int at = local.indexOf('@');
        if (at >= 0) {
            local = local.substring(0, at);
        }
        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus);
        }
        return local.length() >= MIN_EMAIL_LOCAL_LENGTH
                && pw.toLowerCase(Locale.ROOT).contains(local);
    }
}
