package com.team.blog.account.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.error.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 비밀번호 정책 (FR-013·014·015, R-14, US1 #7). */
class PasswordPolicyTest {

    private static final String EMAIL = "kim755030@naver.com";

    private final PasswordPolicy policy = new PasswordPolicy(PolicyTestLists.commonPasswords());

    @ParameterizedTest(name = "[{index}] {0} → 통과")
    @ValueSource(strings = {"Blog#2026a", "Abcdefg1!", "Abcdefg1!Abcdefg", "aA1~`'\"\\|<>"})
    void accepts(String password) {
        assertThat(policy.violations(password, password, EMAIL)).isEmpty();
        assertThat(policy.evaluate(password, password, EMAIL))
                .allSatisfy(r -> assertThat(r.satisfied()).isTrue());
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Ab1!xyz | PASSWORD_INVALID_LENGTH", // 7자
                "Abcdefg1!Abcdefg1 | PASSWORD_INVALID_LENGTH", // 17자 (US1 #7)
                "abc12345 | PASSWORD_MISSING_CHAR_TYPE", // 특수문자·대문자 없음 (quickstart 4-2)
                "ABCDEFG1! | PASSWORD_MISSING_CHAR_TYPE", // 소문자 없음
                "Abcdefgh! | PASSWORD_MISSING_CHAR_TYPE", // 숫자 없음
                "Abcdefg12 | PASSWORD_MISSING_CHAR_TYPE", // 특수문자 없음
                "Abcd 123! | PASSWORD_INVALID_CHAR", // 공백
                "Abcd한123! | PASSWORD_INVALID_CHAR", // 한글
                "Kim755030!x | PASSWORD_CONTAINS_EMAIL", // 이메일 앞부분 포함 (대소문자 무시)
                "Password1! | PASSWORD_TOO_COMMON",
                "qwer1234! | PASSWORD_MISSING_CHAR_TYPE", // 대문자가 없어 흔한 목록 전에 걸림
                "QWER1234! | PASSWORD_MISSING_CHAR_TYPE", // 소문자 없음
            })
    void rejectsWithCode(String password, String expectedCode) {
        assertThat(policy.violations(password, password, EMAIL))
                .extracting(FieldError::code)
                .contains(expectedCode);
    }

    @Test
    void eightAndSixteenCharactersAreAllowed() {
        assertThat(codes("Abcdef1!", "Abcdef1!")).isEmpty();
        assertThat(codes("Abcdef1!Abcdef1!", "Abcdef1!Abcdef1!")).isEmpty();
    }

    @Test
    void commonPasswordIsCaseInsensitiveExactMatch() {
        assertThat(codes("Qwer1234!", "Qwer1234!")).containsExactly("PASSWORD_TOO_COMMON");
        assertThat(codes("qWER1234!", "qWER1234!")).containsExactly("PASSWORD_TOO_COMMON");
        assertThat(codes("Qwer1234!z", "Qwer1234!z")).isEmpty(); // 완전 일치만
    }

    @Test
    void confirmMismatchGoesToPasswordConfirmField() {
        List<FieldError> errors = policy.violations("Blog#2026a", "Blog#2026b", EMAIL);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("passwordConfirm");
        assertThat(errors.get(0).code()).isEqualTo("PASSWORD_CONFIRM_MISMATCH");
    }

    @Test
    void emailLocalPartUsesTextBeforePlusAndOnlyWhenThreeOrMoreChars() {
        assertThat(codes("Xkimx1!aB", "Xkimx1!aB", "kim+blog@naver.com"))
                .containsExactly("PASSWORD_CONTAINS_EMAIL");
        assertThat(codes("Blog#2026ab", "Blog#2026ab", "ab@naver.com")).isEmpty();
        assertThat(codes("Blog#2026ab", "Blog#2026ab", null)).isEmpty(); // 이메일 없음(소셜)
    }

    @Test
    void reportsAllViolationsInRuleOrderAndNeverEchoesThePassword() {
        String secret = "kim755030 ";
        List<FieldError> errors = policy.violations(secret, "other", EMAIL);
        assertThat(errors)
                .extracting(FieldError::code)
                .containsExactly(
                        "PASSWORD_MISSING_CHAR_TYPE",
                        "PASSWORD_INVALID_CHAR",
                        "PASSWORD_CONTAINS_EMAIL",
                        "PASSWORD_CONFIRM_MISMATCH");
        assertThat(errors).allSatisfy(e -> assertThat(e.message()).doesNotContain(secret));
    }

    @Test
    void evaluateReturnsOneResultPerRuleForTheChecklist() {
        List<PasswordRuleResult> results = policy.evaluate("abc", "abc", EMAIL);
        assertThat(results)
                .extracting(PasswordRuleResult::rule)
                .containsExactly(PasswordRule.values());
        assertThat(results)
                .filteredOn(r -> r.rule() == PasswordRule.LENGTH)
                .singleElement()
                .satisfies(r -> assertThat(r.satisfied()).isFalse());
        assertThat(results)
                .filteredOn(r -> r.rule() == PasswordRule.LETTER_CASE)
                .singleElement()
                .satisfies(r -> assertThat(r.satisfied()).isFalse());
        assertThat(results)
                .filteredOn(r -> r.rule() == PasswordRule.ALLOWED_CHARS)
                .singleElement()
                .satisfies(r -> assertThat(r.satisfied()).isTrue());
    }

    @Test
    void lengthMessageStatesMaximumSixteen() {
        assertThat(policy.violations("a", "a", EMAIL).get(0).message()).contains("최대 16자");
    }

    @Test
    void nullPasswordIsInvalidLength() {
        assertThat(codes(null, null)).startsWith("PASSWORD_INVALID_LENGTH");
    }

    private List<String> codes(String password, String confirm) {
        return codes(password, confirm, EMAIL);
    }

    private List<String> codes(String password, String confirm, String email) {
        return policy.violations(password, confirm, email).stream().map(FieldError::code).toList();
    }
}
