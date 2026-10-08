package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 탈퇴 접수·복구 메일 (015 T026·T036, FR-016·019, research R14). */
class WithdrawalMailIT extends IntegrationTestBase {

    private void withdraw(Cookie session, String json) throws Exception {
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/withdraw")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json),
                                session))
                .andExpect(status().isOk());
    }

    @Test
    void 접수_메일에_복구_기한() throws Exception {
        long me = members().member().email("bye@example.com").create();
        withdraw(
                TestLogin.loginAs(mockMvc, me),
                "{\"confirmed\":true,\"password\":\"" + MemberFixtures.DEFAULT_PASSWORD + "\"}");
        SignupRequests.awaitMailCount(mailSender, "bye@example.com", 1);
        Timestamp withdrawnAt =
                jdbc.queryForObject(
                        "SELECT withdrawn_at FROM member WHERE id = ?", Timestamp.class, me);
        String deadline =
                DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                        .format(
                                withdrawnAt
                                        .toInstant()
                                        .plus(Duration.ofDays(30))
                                        .atZone(ZoneId.of("Asia/Seoul")));
        assertThat(mailSender.lastTextFor("bye@example.com").orElseThrow())
                .contains("탈퇴 신청이 접수됐어요")
                .contains(deadline + "까지 로그인하면 복구할 수 있어요")
                .contains("본인이 신청하지 않았다면");
    }

    @Test
    void 이메일_없는_소셜_계정은_0통() throws Exception {
        long me = members().member().provider("GITHUB").create();
        long withMail = members().member().provider("GOOGLE").email("g@gmail.com").create();
        withdraw(TestLogin.loginAs(mockMvc, me), "{\"confirmed\":true,\"confirmText\":\"탈퇴\"}");
        withdraw(
                TestLogin.loginAs(mockMvc, withMail),
                "{\"confirmed\":true,\"confirmText\":\"탈퇴\"}");
        SignupRequests.awaitMailCount(mailSender, "g@gmail.com", 1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT email FROM auth_identity WHERE member_id = ?",
                                String.class,
                                me))
                .isNull();
    }

    @Test
    void 메일이_실패해도_신청은_200() throws Exception {
        long me = members().member().email("fail@example.com").create();
        mailSender.failNext();
        withdraw(
                TestLogin.loginAs(mockMvc, me),
                "{\"confirmed\":true,\"password\":\"" + MemberFixtures.DEFAULT_PASSWORD + "\"}");
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, me))
                .isEqualTo("WITHDRAWN");
    }

    @Test
    void 복구_메일() throws Exception {
        long me = members().member().email("back@example.com").status("WITHDRAWN").create();
        mockMvc.perform(TestLogin.withCsrf(post("/api/me/restore"), TestLogin.loginAs(mockMvc, me)))
                .andExpect(status().isOk());
        SignupRequests.awaitMailCount(mailSender, "back@example.com", 1);
        assertThat(mailSender.lastTextFor("back@example.com").orElseThrow())
                .contains("계정이 복구됐어요")
                .contains("블로그와 글이 다시 보여요");
    }

    @Test
    void 이미_활동_중이면_복구_메일_없음() throws Exception {
        long me = members().member().email("active@example.com").create();
        mockMvc.perform(TestLogin.withCsrf(post("/api/me/restore"), TestLogin.loginAs(mockMvc, me)))
                .andExpect(status().isOk());
        Thread.sleep(300);
        assertThat(mailSender.countTo("active@example.com")).isZero();
    }
}
