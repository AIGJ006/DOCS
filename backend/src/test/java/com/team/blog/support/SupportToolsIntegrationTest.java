package com.team.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 다른 기능(002·004·005·006)이 함께 쓰는 테스트 도구가 V1 CHECK를 만족하는 데이터를 만드는지 확인한다. */
class SupportToolsIntegrationTest extends IntegrationTestBase {

    @Test
    void 회원_픽스처는_상태별_회원을_만든다() {
        long active =
                members()
                        .member()
                        .handle("kim755030")
                        .nickname("김민서")
                        .email(" Kim@Naver.com ")
                        .create();
        long unverified = members().member().emailVerified(false).create();
        long google = members().member().provider("GOOGLE").providerUserId("sub-1").create();
        long admin = members().member().role("ADMIN").create();
        long withdrawn = members().member().status("WITHDRAWN").create();
        long deleted = members().member().deleted().create();

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT m.handle, m.nickname, a.provider, a.provider_user_id, a.email,"
                                + " a.password_hash, a.email_verified_at FROM member m"
                                + " JOIN auth_identity a ON a.member_id = m.id WHERE m.id = ?",
                        active);
        assertThat(row.get("handle")).isEqualTo("kim755030");
        assertThat(row.get("provider_user_id")).isEqualTo("kim@naver.com");
        assertThat(row.get("email_verified_at")).isNotNull();
        assertThat(
                        new BCryptPasswordEncoder()
                                .matches(
                                        MemberFixtures.DEFAULT_PASSWORD,
                                        (String) row.get("password_hash")))
                .isTrue();

        assertThat(verifiedAt(unverified)).isNull();
        assertThat(handleOf(google)).startsWith("go-");
        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, admin))
                .isEqualTo("ADMIN");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT withdrawn_at IS NOT NULL FROM member WHERE id = ? AND status = 'WITHDRAWN'",
                                Boolean.class,
                                withdrawn))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NOT NULL AND nickname IS NULL FROM member WHERE id = ?",
                                Boolean.class,
                                deleted))
                .isTrue();
    }

    @Test
    void 정지는_이력_행과_회원_상태를_함께_바꾼다() {
        long m = members().member().create();
        members().suspend(m, Instant.now().plus(Duration.ofDays(7)), "스팸");
        long expired = members().member().create();
        members().suspend(expired, Instant.now().minus(Duration.ofHours(1)), "지난 정지");
        long permanent = members().member().create();
        members().suspend(permanent, null, "영구");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member WHERE status = 'SUSPENDED'",
                                Long.class))
                .isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_suspension WHERE lifted_at IS NULL",
                                Long.class))
                .isEqualTo(3);
    }

    @Test
    void 메일_캡처는_받는_사람별로_토큰을_꺼낸다() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("Kim@Naver.com");
        message.setSubject("이메일 인증");
        message.setText("링크: http://localhost:8080/verify-email?token=abc_DEF-123 (24시간)");
        mailSender.send(message);

        assertThat(mailSender.countTo("kim@naver.com")).isEqualTo(1);
        assertThat(mailSender.lastTokenFor("kim@naver.com")).contains("abc_DEF-123");
        assertThat(mailSender.lastTokenFor("other@naver.com")).isEmpty();
    }

    private Object verifiedAt(long memberId) {
        return jdbc.queryForObject(
                "SELECT email_verified_at FROM auth_identity WHERE member_id = ?",
                Object.class,
                memberId);
    }

    private String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }
}
