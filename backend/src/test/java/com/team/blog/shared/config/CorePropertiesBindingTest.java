package com.team.blog.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class CorePropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CoreProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class, TimeConfig.class);

    @Test
    void 기본값() {
        runner.withPropertyValues("blog.image.public-base-url=http://localhost:9000/blog")
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            CoreProperties p = ctx.getBean(CoreProperties.class);
                            assertThat(p.timeZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
                            assertThat(p.scheduling().poolSize()).isEqualTo(2);
                            assertThat(p.async().event().coreSize()).isEqualTo(2);
                            assertThat(p.async().event().queueCapacity()).isEqualTo(500);
                            // 011 T006: 종료 대기 (기본 10초, application.yml의 이벤트 실행기는 20초)
                            assertThat(p.async().event().awaitTermination())
                                    .isEqualTo(java.time.Duration.ofSeconds(10));
                            assertThat(p.async().mail().awaitTermination())
                                    .isEqualTo(java.time.Duration.ofSeconds(10));
                            assertThat(p.image().legacyBaseUrls()).isEmpty();
                            assertThat(p.image().publicOrigin()).isEqualTo("http://localhost:9000");
                            assertThat(ctx.getBean(ZoneId.class))
                                    .isEqualTo(ZoneId.of("Asia/Seoul"));
                            assertThat(ctx.getBean(java.time.Clock.class).getZone())
                                    .isEqualTo(java.time.ZoneOffset.UTC);
                        });
    }

    @Test
    void 공개_주소의_출처는_기본_포트를_생략한다() {
        runner.withPropertyValues(
                        "blog.image.public-base-url=https://cdn.example.com/blog/",
                        "blog.time-zone=UTC")
                .run(
                        ctx -> {
                            CoreProperties p = ctx.getBean(CoreProperties.class);
                            assertThat(p.image().publicOrigin())
                                    .isEqualTo("https://cdn.example.com");
                            assertThat(p.timeZone()).isEqualTo(ZoneId.of("UTC"));
                        });
    }

    @Test
    void 종료_대기를_바꿀_수_있다() {
        runner.withPropertyValues(
                        "blog.image.public-base-url=http://localhost:9000/blog",
                        "blog.async.event.await-termination=20s")
                .run(
                        ctx ->
                                assertThat(
                                                ctx.getBean(CoreProperties.class)
                                                        .async()
                                                        .event()
                                                        .awaitTermination())
                                        .isEqualTo(java.time.Duration.ofSeconds(20)));
    }

    @Test
    void 잘못된_시간대는_거부한다() {
        runner.withPropertyValues(
                        "blog.image.public-base-url=http://localhost:9000/blog",
                        "blog.time-zone=Mars/Phobos")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 저장소_공개_주소는_필수다() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.image.public-base-url=not-a-url")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
