package com.team.blog.account.application.policy;

import static com.team.blog.account.application.AccountReasonCode.HANDLE_BANNED_WORD;
import static com.team.blog.account.application.AccountReasonCode.HANDLE_INVALID_FORMAT;
import static com.team.blog.account.application.AccountReasonCode.HANDLE_PREFIX_MISMATCH;
import static com.team.blog.account.application.AccountReasonCode.HANDLE_RESERVED;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 블로그 주소 검사 (FR-016·019, 08 §2·§5, R-15). */
class HandlePolicyTest {

    private final HandlePolicy policy =
            new HandlePolicy(PolicyTestLists.reservedHandles(), PolicyTestLists.bannedWordFilter());

    @ParameterizedTest(name = "[{index}] {0} ({1}) → 통과")
    @CsvSource({
        "kim755030, LOCAL",
        "go-kim755030, GOOGLE",
        "gi-kim_min, GITHUB",
        "gokim, LOCAL",
        "abc, LOCAL",
        "a23456789012345678901234567890123456, LOCAL", // 본문 36자
        "gi-a23456789012345678901234567890123456, GITHUB", // 전체 39자
    })
    void accepts(String handle, Provider provider) {
        assertThat(policy.validate(handle, provider)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0} → 형식 오류")
    @ValueSource(
            strings = {
                "GOkim",
                "Kim755030",
                "kim-min",
                "xx-kim",
                "go-ki",
                "ab",
                "_kim",
                "kim_",
                "go-_kim",
                "a234567890123456789012345678901234567", // 본문 37자
                "",
                "김민서",
                "kim 755",
            })
    void rejectsInvalidFormat(String handle) {
        assertThat(policy.validate(handle, Provider.LOCAL)).containsExactly(HANDLE_INVALID_FORMAT);
    }

    @Test
    void nullIsInvalidFormat() {
        assertThat(policy.validate(null, Provider.LOCAL)).containsExactly(HANDLE_INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] {0} ({1}) → 접두어 불일치")
    @CsvSource({
        "go-kim755030, LOCAL",
        "gi-kim755030, GOOGLE",
        "go-kim755030, GITHUB",
        "kim755030, GOOGLE",
        "kim755030, GITHUB",
    })
    void rejectsPrefixMismatch(String handle, Provider provider) {
        assertThat(policy.validate(handle, provider)).containsExactly(HANDLE_PREFIX_MISMATCH);
    }

    @ParameterizedTest(name = "[{index}] {0} ({1}) → 예약어")
    @CsvSource({
        "admin, LOCAL",
        "go-admin, GOOGLE", // 접두어를 뺀 본문 기준 (08 #5)
        "gi-settings, GITHUB",
        "teamblog, LOCAL",
        "baselog, LOCAL", // 서비스 이름 (2026-10-10)
        "null, LOCAL",
    })
    void rejectsReservedBody(String handle, Provider provider) {
        assertThat(policy.validate(handle, provider)).containsExactly(HANDLE_RESERVED);
    }

    @Test
    void reservedMeansExactBodyNotContains() {
        assertThat(policy.validate("admin_2", Provider.LOCAL)).isEmpty();
        assertThat(policy.validate("myblog", Provider.LOCAL)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0} → 금칙어")
    @ValueSource(strings = {"sh1t_blog", "my_shit", "fack123"})
    void rejectsBannedWord(String handle) {
        assertThat(policy.validate(handle, Provider.LOCAL)).containsExactly(HANDLE_BANNED_WORD);
    }

    @Test
    void reportsPrefixMismatchAndReservedTogether() {
        assertThat(policy.validate("go-admin", Provider.LOCAL))
                .containsExactly(HANDLE_PREFIX_MISMATCH, HANDLE_RESERVED);
    }

    @Test
    void bodyAndPrefixHelpers() {
        assertThat(HandlePolicy.body("go-kim")).isEqualTo("kim");
        assertThat(HandlePolicy.body("gi-kim")).isEqualTo("kim");
        assertThat(HandlePolicy.body("gokim")).isEqualTo("gokim");
        assertThat(HandlePolicy.matchesFormat("go-kim755030")).isTrue();
        assertThat(HandlePolicy.matchesFormat("Kim755030")).isFalse();
    }
}
