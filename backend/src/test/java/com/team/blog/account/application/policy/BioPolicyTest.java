package com.team.blog.account.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AccountReasonCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 소개 규칙 (FR-048, R-18). */
class BioPolicyTest {

    private final BioPolicy policy =
            new BioPolicy(200, 4, new BannedWordFilter(List.of("욕설"), List.of()));

    @Test
    @DisplayName("코드 포인트 기준 200자: 이모지 200개 허용, 201개 BIO_TOO_LONG")
    void lengthInCodePoints() {
        String emoji = "😀";
        assertThat(policy.check(emoji.repeat(200)).failure()).isNull();
        assertThat(policy.check(emoji.repeat(201)).failure())
                .isEqualTo(AccountReasonCode.BIO_TOO_LONG);
        assertThat(policy.check("가".repeat(200)).failure()).isNull();
        assertThat(policy.check("가".repeat(201)).failure())
                .isEqualTo(AccountReasonCode.BIO_TOO_LONG);
    }

    @Test
    @DisplayName("\\r\\n·\\r → \\n, 연속 빈 줄은 하나로 정리한 뒤 줄 수를 센다")
    void lines() {
        assertThat(policy.check("a\r\nb\rc").normalized()).isEqualTo("a\nb\nc");
        assertThat(policy.check("a\n\n\n\nb").normalized()).isEqualTo("a\n\nb");
        assertThat(policy.check("1\n2\n3\n4").failure()).isNull();
        assertThat(policy.check("1\n\n\n\n2\n3").failure()).isNull();
        assertThat(policy.check("1\n2\n3\n4\n5").failure())
                .isEqualTo(AccountReasonCode.BIO_TOO_MANY_LINES);
        assertThat(AccountReasonCode.BIO_TOO_MANY_LINES.defaultMessage())
                .isEqualTo("소개는 4줄까지 쓸 수 있어요");
    }

    @Test
    @DisplayName("금칙어 → BIO_BANNED_WORD, 예약어 검사는 없다")
    void bannedWords() {
        assertThat(policy.check("이건 욕설이에요").failure()).isEqualTo(AccountReasonCode.BIO_BANNED_WORD);
        assertThat(policy.check("admin 입니다").failure()).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백 제거·NFC, 빈 문자열·null → null")
    void trimAndNfc() {
        assertThat(policy.check("  안녕  ").normalized()).isEqualTo("안녕");
        String decomposed = "가";
        assertThat(policy.check(decomposed).normalized()).isEqualTo("가");
        assertThat(policy.check("   ").normalized()).isNull();
        assertThat(policy.check("").normalized()).isNull();
        assertThat(policy.check(null).normalized()).isNull();
        assertThat(policy.check(null).failure()).isNull();
    }

    @Test
    @DisplayName("오류 문구")
    void messages() {
        assertThat(AccountReasonCode.BIO_TOO_LONG.defaultMessage()).isEqualTo("소개는 200자까지 쓸 수 있어요");
        assertThat(AccountReasonCode.BIO_BANNED_WORD.defaultMessage())
                .isEqualTo("사용할 수 없는 단어가 들어 있어요");
    }
}
