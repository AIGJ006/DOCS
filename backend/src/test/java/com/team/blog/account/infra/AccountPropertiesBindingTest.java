package com.team.blog.account.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** application.yml의 계정 기본값이 data-model §6 표와 같은지 확인한다. */
class AccountPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AccountProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void data_model_6절_기본값() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    AccountProperties p = ctx.getBean(AccountProperties.class);
                    assertThat(p.auth().sessionTimeout()).isEqualTo(Duration.ofDays(14));
                    assertThat(p.auth().login().maxFailures()).isEqualTo(5);
                    assertThat(p.auth().login().lockDuration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(p.auth().login().ipLimitPerMinute()).isEqualTo(20);
                    assertThat(p.auth().verify().tokenTtl()).isEqualTo(Duration.ofHours(24));
                    assertThat(p.auth().verify().resendInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(p.auth().verify().resendDailyLimit()).isEqualTo(10);
                    assertThat(p.auth().reset().tokenTtl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(p.auth().reset().emailInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(p.auth().reset().emailDailyLimit()).isEqualTo(10);
                    assertThat(p.auth().reset().ipLimitPerHour()).isEqualTo(20);
                    assertThat(p.auth().passwordChange().maxFailures()).isEqualTo(5);
                    assertThat(p.auth().passwordChange().lockDuration())
                            .isEqualTo(Duration.ofMinutes(15));
                    assertThat(p.auth().social().pendingTtl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(p.auth().social().photoHosts())
                            .isEqualTo(
                                    List.of(
                                            "lh3.googleusercontent.com",
                                            "avatars.githubusercontent.com"));
                    assertThat(p.availability().ipLimitPerMinute()).isEqualTo(30);
                    assertThat(p.member().nicknameChangeInterval()).isEqualTo(Duration.ofDays(30));
                    assertThat(p.member().bio().maxLength()).isEqualTo(200);
                    assertThat(p.member().bio().maxLines()).isEqualTo(4);
                    assertThat(p.member().lastActive().touchInterval())
                            .isEqualTo(Duration.ofHours(1));
                    assertThat(p.agreement().terms().version()).isEqualTo("2026-10-07");
                    assertThat(p.agreement().terms().effectiveDate())
                            .isEqualTo(LocalDate.of(2026, 10, 7));
                    assertThat(p.agreement().privacy().version()).isEqualTo("2026-10-07");
                    assertThat(p.policy().reservedHandles())
                            .isEqualTo("classpath:policy/reserved-handles.txt");
                    assertThat(p.policy().commonPasswords())
                            .isEqualTo("classpath:policy/common-passwords.txt");
                });
    }

    @Test
    void 약관_버전은_환경_변수로_바꾼다() {
        runner.withPropertyValues("BLOG_AGREEMENT_TERMS_VERSION=2027-01-01")
                .run(
                        ctx ->
                                assertThat(
                                                ctx.getBean(AccountProperties.class)
                                                        .agreement()
                                                        .terms()
                                                        .version())
                                        .isEqualTo("2027-01-01"));
    }

    @Test
    void 소개_최대_길이는_DB_CHECK_200을_넘을_수_없다() {
        runner.withPropertyValues("blog.member.bio.max-length=300")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
