package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.WithdrawalProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** {@code blog.withdraw.*} 바인딩 (015 T004, research R16). */
class WithdrawalPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WithdrawalProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void 기본값() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    WithdrawalProperties p = ctx.getBean(WithdrawalProperties.class);
                    assertThat(p.gracePeriod()).isEqualTo(Duration.ofDays(30));
                    assertThat(p.suspendedPurgeAfter()).isEqualTo(Duration.ofDays(365));
                    assertThat(p.confirmText()).isEqualTo("탈퇴");
                    assertThat(p.purge().cron()).isEqualTo("0 0 3 * * *");
                    assertThat(p.purge().batchSize()).isEqualTo(100);
                    assertThat(p.purge().requiredOrders())
                            .containsExactly(10, 20, 30, 40, 50, 60, 65, 70, 80, 90);
                    assertThat(p.purge().redisKeyTemplates())
                            .contains(
                                    "auth:pw-change-fail:{memberId}", "auth:login-fail:{emailHash}")
                            .hasSize(11);
                });
    }

    @Test
    void 기간은_30d_365d_형식을_받는다() {
        runner.withPropertyValues(
                        "blog.withdraw.grace-period=7d", "blog.withdraw.suspended-purge-after=10d")
                .run(
                        ctx -> {
                            WithdrawalProperties p = ctx.getBean(WithdrawalProperties.class);
                            assertThat(p.gracePeriod()).isEqualTo(Duration.ofDays(7));
                            assertThat(p.suspendedPurgeAfter()).isEqualTo(Duration.ofDays(10));
                        });
    }

    @Test
    void 음수나_0인_기간은_거부한다() {
        runner.withPropertyValues("blog.withdraw.grace-period=-1d")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.withdraw.grace-period=0s")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.withdraw.suspended-purge-after=-5d")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 빈_확인_문구와_0_이하_묶음_크기는_거부한다() {
        runner.withPropertyValues("blog.withdraw.confirm-text= ")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.withdraw.purge.batch-size=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
