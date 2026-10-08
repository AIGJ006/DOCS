package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.FollowProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** application.yml의 {@code blog.follow.*} 기본값과 검증 (010 T002, research R12). */
class FollowPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FollowProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void research_R12_기본값() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    FollowProperties p = ctx.getBean(FollowProperties.class);
                    assertThat(p.rateLimit().limit()).isEqualTo(30);
                    assertThat(p.rateLimit().window()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(p.listPageSize()).isEqualTo(20);
                });
    }

    @Test
    void 요청_제한_0은_시작_실패() {
        runner.withPropertyValues("blog.follow.rate-limit.limit=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 목록_크기는_1에서_100() {
        runner.withPropertyValues("blog.follow.list-page-size=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.follow.list-page-size=101")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.follow.list-page-size=100")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
