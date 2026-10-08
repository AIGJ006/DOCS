package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.WithdrawCommand;
import com.team.blog.account.application.WithdrawConfirmText;
import com.team.blog.account.web.dto.WithdrawRequest;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** 탈퇴 신청 입력 규칙 (015 T018, research R3). */
class WithdrawRequestValidationTest {

    private static final String EXPECTED = "탈퇴";

    @Test
    void 앞뒤_공백을_빼고_비교한다() {
        assertThat(WithdrawConfirmText.matches("탈퇴", EXPECTED)).isTrue();
        assertThat(WithdrawConfirmText.matches("탈퇴 ", EXPECTED)).isTrue();
        assertThat(WithdrawConfirmText.matches("  탈퇴\t", EXPECTED)).isTrue();
        assertThat(WithdrawConfirmText.matches("　탈퇴", EXPECTED)).isTrue();
    }

    @Test
    void NFC로_정규화한_뒤_비교한다() {
        String decomposed = Normalizer.normalize("탈퇴", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("탈퇴");
        assertThat(WithdrawConfirmText.matches(decomposed, EXPECTED)).isTrue();
    }

    @Test
    void 다른_문구는_거부한다() {
        assertThat(WithdrawConfirmText.matches("탈퇴함", EXPECTED)).isFalse();
        assertThat(WithdrawConfirmText.matches("탈 퇴", EXPECTED)).isFalse();
        assertThat(WithdrawConfirmText.matches("", EXPECTED)).isFalse();
        assertThat(WithdrawConfirmText.matches(null, EXPECTED)).isFalse();
    }

    @Test
    void 스무_자를_넘으면_거부한다() {
        String padded = "탈퇴" + " ".repeat(19);
        assertThat(padded.length()).isEqualTo(21);
        assertThat(WithdrawConfirmText.matches(padded, EXPECTED)).isFalse();
        assertThat(WithdrawConfirmText.matches("탈퇴" + " ".repeat(18), EXPECTED)).isTrue();
    }

    @Test
    void 확인_체크는_true일_때만() {
        assertThat(new WithdrawCommand(true, null, null).isConfirmed()).isTrue();
        assertThat(new WithdrawCommand(false, null, null).isConfirmed()).isFalse();
        assertThat(new WithdrawCommand(null, null, null).isConfirmed()).isFalse();
    }

    @Test
    void 비밀번호는_문자열로_나오지_않는다() {
        WithdrawRequest request = new WithdrawRequest(true, "Blog#2026a", "탈퇴");
        assertThat(request.toString()).doesNotContain("Blog#2026a");
        assertThat(request.toCommand().toString()).doesNotContain("Blog#2026a");
        assertThat(request.toCommand().password()).isEqualTo("Blog#2026a");
    }
}
