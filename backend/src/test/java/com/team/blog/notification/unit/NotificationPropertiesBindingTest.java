package com.team.blog.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.application.NotificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** {@code blog.notification.*} 바인딩 (011 T002, data-model §6). */
class NotificationPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
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
                    NotificationProperties p = ctx.getBean(NotificationProperties.class);
                    assertThat(p.retention()).isEqualTo(Duration.ofDays(90));
                    assertThat(p.maxPerMember()).isEqualTo(1000);
                    assertThat(p.cleanup().cron()).isEqualTo("0 30 4 * * *");
                    assertThat(p.cleanup().batchSize()).isEqualTo(1000);
                    assertThat(p.cleanup().recentWindow()).isEqualTo(Duration.ofDays(1));
                    assertThat(p.followDedupWindow()).isEqualTo(Duration.ofDays(7));
                    assertThat(p.previewLength()).isEqualTo(50);
                    assertThat(p.previewScan()).isEqualTo(400);
                    assertThat(p.dropdownSize()).isEqualTo(10);
                    assertThat(p.pageSize()).isEqualTo(20);
                });
    }

    @Test
    void 값을_바꿀_수_있다() {
        runner.withPropertyValues(
                        "blog.notification.retention=30d",
                        "blog.notification.cleanup.batch-size=50",
                        "blog.notification.follow-dedup-window=1d")
                .run(
                        ctx -> {
                            NotificationProperties p = ctx.getBean(NotificationProperties.class);
                            assertThat(p.retention()).isEqualTo(Duration.ofDays(30));
                            assertThat(p.cleanup().batchSize()).isEqualTo(50);
                            assertThat(p.followDedupWindow()).isEqualTo(Duration.ofDays(1));
                        });
    }

    @Test
    void 잘못된_값은_거부한다() {
        runner.withPropertyValues("blog.notification.max-per-member=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.notification.cleanup.cron=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
