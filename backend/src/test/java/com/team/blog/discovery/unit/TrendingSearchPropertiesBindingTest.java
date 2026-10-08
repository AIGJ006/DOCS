package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.search.SearchProperties;
import com.team.blog.discovery.application.trending.TrendingProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * application.yml의 {@code blog.trending.*}·{@code blog.search.*} 기본값과 검증 (012 T002, research R17).
 */
class TrendingSearchPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({TrendingProperties.class, SearchProperties.class})
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void research_R17_트렌딩_기본값() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    TrendingProperties p = ctx.getBean(TrendingProperties.class);
                    assertThat(p.window()).isEqualTo(Duration.ofDays(7));
                    assertThat(p.refreshCron()).isEqualTo("0 */10 * * * *");
                    assertThat(p.refreshOnStartup()).isTrue();
                    assertThat(p.snapshotSize()).isEqualTo(100);
                    assertThat(p.snapshotTtl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(p.perAuthor()).isEqualTo(3);
                    assertThat(p.weightLike()).isEqualTo(3.0);
                    assertThat(p.weightCommenter()).isEqualTo(2.0);
                    assertThat(p.weightView()).isEqualTo(0.1);
                    assertThat(p.offsetHours()).isEqualTo(2.0);
                    assertThat(p.gravity()).isEqualTo(1.5);
                    assertThat(p.readChunk()).isEqualTo(18);
                });
    }

    @Test
    void research_R17_검색_기본값() {
        runner.run(
                ctx -> {
                    SearchProperties p = ctx.getBean(SearchProperties.class);
                    assertThat(p.maxLength()).isEqualTo(50);
                    assertThat(p.maxWords()).isEqualTo(5);
                    assertThat(p.recentWindow()).isEqualTo(3000);
                    assertThat(p.snippetRadius()).isEqualTo(40);
                    assertThat(p.peopleLimit()).isEqualTo(20);
                    assertThat(p.rateLimit().limit()).isEqualTo(30);
                    assertThat(p.rateLimit().window()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    void 잘못된_값은_시작_실패() {
        runner.withPropertyValues("blog.trending.snapshot-size=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.trending.gravity=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.search.max-words=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.search.rate-limit.limit=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 예약_실행은_대시로_끈다() {
        runner.withPropertyValues("blog.trending.refresh-cron=-")
                .run(
                        ctx ->
                                assertThat(ctx.getBean(TrendingProperties.class).refreshCron())
                                        .isEqualTo("-"));
    }
}
