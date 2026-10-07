package com.team.blog.account.application.policy;

import static com.team.blog.account.application.AccountReasonCode.NICKNAME_BANNED_WORD;
import static com.team.blog.account.application.AccountReasonCode.NICKNAME_DUPLICATE;
import static com.team.blog.account.application.AccountReasonCode.NICKNAME_INVALID_FORMAT;
import static com.team.blog.account.application.AccountReasonCode.NICKNAME_LETTER_REQUIRED;
import static com.team.blog.account.application.AccountReasonCode.NICKNAME_RESERVED;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AccountReasonCode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 닉네임 검사 (FR-022~027, 09 §2~§7, SC-007). 기존 회원: 1번 = {@code Kim}. */
class NicknamePolicyTest {

    private static final Map<Long, String> EXISTING = Map.of(1L, "Kim");

    private final NicknamePolicy policy =
            new NicknamePolicy(
                    PolicyTestLists.reservedNicknames(),
                    PolicyTestLists.bannedWordFilter(),
                    (nickname, excludeMemberId) ->
                            EXISTING.entrySet().stream()
                                    .filter(e -> !Objects.equals(e.getKey(), excludeMemberId))
                                    .anyMatch(
                                            e ->
                                                    e.getValue()
                                                            .toLowerCase(Locale.ROOT)
                                                            .equals(
                                                                    nickname.toLowerCase(
                                                                            Locale.ROOT))));

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "ㅋㅋ | NICKNAME_INVALID_FORMAT", // 자음·모음만 (N-1)
                "ㅗ | NICKNAME_INVALID_FORMAT",
                "김 민서 | NICKNAME_INVALID_FORMAT", // 공백 (N-2)
                "김민서! | NICKNAME_INVALID_FORMAT", // 특수문자
                "김민서😀 | NICKNAME_INVALID_FORMAT", // 이모지
                "김 | NICKNAME_INVALID_FORMAT", // 2자 미만
                "가나다라마바사아자차카 | NICKNAME_INVALID_FORMAT", // 11자
                "12345 | NICKNAME_LETTER_REQUIRED", // 숫자만 (N-3)
                "관리자김 | NICKNAME_RESERVED", // 예약어 포함 (N-6, 09 #5)
                "admin123 | NICKNAME_RESERVED",
                "Official | NICKNAME_RESERVED",
                "운영팀장 | NICKNAME_RESERVED",
                "ADM1N | NICKNAME_RESERVED", // 예약어도 금칙어와 같은 변형으로 (1→i)
                "시1발 | NICKNAME_BANNED_WORD", // 숫자 끼워 넣기 (09 #4)
                "sh1t | NICKNAME_BANNED_WORD", // 숫자로 글자 바꾸기
                "kim | NICKNAME_DUPLICATE", // Kim이 있음 (N-4, 09 #3)
                "KIM | NICKNAME_DUPLICATE",
            })
    void rejectsWithStepCode(String raw, AccountReasonCode expected) {
        NicknameCheck check = policy.check(raw, null);
        assertThat(check.failure()).isEqualTo(expected);
        assertThat(check.valid()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0} → 통과")
    @ValueSource(strings = {"시발점", "김민서", "KIM2", "devKim", "minseo", "김2"})
    void accepts(String raw) {
        NicknameCheck check = policy.check(raw, null);
        assertThat(check.failure()).isNull();
        assertThat(check.valid()).isTrue();
    }

    @Test
    void messagesMatchDocumentAndHideTheBannedWord() {
        assertThat(NICKNAME_INVALID_FORMAT.defaultMessage())
                .isEqualTo("한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)");
        assertThat(NICKNAME_LETTER_REQUIRED.defaultMessage()).isEqualTo("한글이나 영문을 1자 이상 넣어 주세요");
        assertThat(NICKNAME_RESERVED.defaultMessage()).isEqualTo("사용할 수 없는 닉네임이에요");
        assertThat(NICKNAME_BANNED_WORD.defaultMessage()).isEqualTo("사용할 수 없는 단어가 들어 있어요");
        assertThat(NICKNAME_DUPLICATE.defaultMessage()).isEqualTo("이미 사용 중인 닉네임이에요");
        NicknameCheck banned = policy.check("시1발", null);
        assertThat(banned.toFieldError("nickname").message()).doesNotContain("시발");
    }

    @Test
    void trimsAndNormalizesToNfc() {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("김민서");
        NicknameCheck check = policy.check("  " + nfd + " ", null);
        assertThat(check.valid()).isTrue();
        assertThat(check.normalized()).isEqualTo("김민서"); // 09 #2: 정규화한 값을 저장
    }

    @Test
    void duplicateExcludesSelf() {
        assertThat(policy.check("kim", 1L).valid()).isTrue(); // 대소문자만 바꾸기 (09 §8)
        assertThat(policy.check("kim", 2L).failure()).isEqualTo(NICKNAME_DUPLICATE);
    }

    @Test
    void nullOrBlankIsInvalidFormat() {
        assertThat(policy.check(null, null).failure()).isEqualTo(NICKNAME_INVALID_FORMAT);
        assertThat(policy.check("   ", null).failure()).isEqualTo(NICKNAME_INVALID_FORMAT);
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {
                "Kim Min-seo | KimMinseo",
                "김민서 (Minseo) | 김민서Minseo",
                "Christopher Columbus | Christophe", // 10자로 자름
                "A | NULL", // 2자 미만
                "Kim | NULL", // 이미 사용 중
                "관리자 | NULL", // 예약어
                "!!! | NULL",
            })
    void suggestsFromSocialName(String socialName, String expected) {
        assertThat(policy.suggestFromSocialName(socialName)).isEqualTo(expected);
    }

    @Test
    void suggestFromNullSocialName() {
        assertThat(policy.suggestFromSocialName(null)).isNull();
    }
}
