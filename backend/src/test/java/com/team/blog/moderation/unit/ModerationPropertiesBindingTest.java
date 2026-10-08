package com.team.blog.moderation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.SuspensionDuration;
import com.team.blog.moderation.application.ModerationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** {@code blog.moderation.*} 바인딩 (014 T002, research R14). */
class ModerationPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModerationProperties.class)
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
                    ModerationProperties p = ctx.getBean(ModerationProperties.class);
                    assertThat(p.snapshotContentChars()).isEqualTo(2000);
                    assertThat(p.detailMaxChars()).isEqualTo(200);
                    assertThat(p.rateLimit().perMinute()).isEqualTo(5);
                    assertThat(p.rateLimit().perDay()).isEqualTo(50);
                    assertThat(p.retention()).isEqualTo(Duration.ofDays(30));
                    assertThat(p.cleanupCron()).isEqualTo("0 45 4 * * *");
                    assertThat(p.cleanupBatchSize()).isEqualTo(1000);
                    assertThat(p.adminPageSize()).isEqualTo(20);
                    assertThat(p.suspensionDurations())
                            .containsExactly(
                                    SuspensionDuration.P1D,
                                    SuspensionDuration.P7D,
                                    SuspensionDuration.P30D,
                                    SuspensionDuration.PERMANENT);
                    assertThat(p.suspensionReasonMaxChars()).isEqualTo(200);
                });
    }

    @Test
    void 기간_목록과_제한을_바꿀_수_있다() {
        runner.withPropertyValues(
                        "blog.moderation.suspension-durations=P7D,PERMANENT",
                        "blog.moderation.rate-limit.per-minute=3",
                        "blog.moderation.rate-limit.per-day=3")
                .run(
                        ctx -> {
                            ModerationProperties p = ctx.getBean(ModerationProperties.class);
                            assertThat(p.suspensionDurations())
                                    .containsExactly(
                                            SuspensionDuration.P7D, SuspensionDuration.PERMANENT);
                            assertThat(p.rateLimit().perMinute()).isEqualTo(3);
                        });
    }

    @Test
    void 분당_제한이_하루_제한보다_크면_거부한다() {
        runner.withPropertyValues(
                        "blog.moderation.rate-limit.per-minute=60",
                        "blog.moderation.rate-limit.per-day=50")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 열_길이를_넘는_값과_빈_기간_목록은_거부한다() {
        runner.withPropertyValues("blog.moderation.snapshot-content-chars=2001")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.moderation.detail-max-chars=201")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.moderation.suspension-durations=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
