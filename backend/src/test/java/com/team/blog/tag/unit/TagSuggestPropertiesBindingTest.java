package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.tag.application.suggest.SuggestPostLimits;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * {@code blog.ai.tag-suggest.*}·{@code blog.agreement.ai.*} 기본값 (013 T002, research R15, data-model
 * §6). AI 동의 버전 기본값은 화면 {@code aiConsentText.ts}의 상수와 같아야 한다(짝 시험: {@code
 * AiConsentDialog.test.tsx}).
 */
class TagSuggestPropertiesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
        TagSuggestProperties.class,
        SuggestPostLimits.class,
        AccountProperties.class
    })
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Config.class);

    @Test
    void research_R15_기본값() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    TagSuggestProperties p = ctx.getBean(TagSuggestProperties.class);
                    assertThat(p.enabled()).isTrue();
                    assertThat(p.promptVersion()).isEqualTo(1);
                    assertThat(p.minInputChars()).isEqualTo(100);
                    assertThat(p.maxSuggestions()).isEqualTo(5);
                    assertThat(p.dailyLimitPerMember()).isEqualTo(20);
                    assertThat(p.cache().exactTtl()).isEqualTo(Duration.ofDays(30));
                    assertThat(p.cache().postTtl()).isEqualTo(Duration.ofDays(7));
                    assertThat(p.cache().similarityThreshold()).isEqualTo(0.9);
                    assertThat(p.cache().postInputChars()).isEqualTo(8000);
                    assertThat(p.popular().size()).isEqualTo(50);
                    assertThat(p.popular().ttl()).isEqualTo(Duration.ofDays(1));
                    assertThat(p.gemini().baseUrl())
                            .isEqualTo("https://generativelanguage.googleapis.com");
                    assertThat(p.gemini().model()).isEqualTo("gemini-flash-lite");
                    assertThat(p.gemini().dailyLimit()).isEqualTo(450);
                    assertThat(p.gemini().quotaZone()).isEqualTo(ZoneId.of("America/Los_Angeles"));
                    assertThat(p.gemini().timeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(p.gemini().cooldown()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(p.gemini().unknown429ExhaustCount()).isEqualTo(3);
                    assertThat(p.gemini().maxInputChars()).isEqualTo(8000);
                    assertThat(p.ollama().enabled()).isTrue();
                    assertThat(p.ollama().baseUrl()).isEqualTo("http://localhost:11434");
                    assertThat(p.ollama().model()).isEqualTo("qwen2.5:1.5b");
                    assertThat(p.ollama().numThread()).isEqualTo(4);
                    assertThat(p.ollama().timeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(p.ollama().maxInputChars()).isEqualTo(2000);
                    assertThat(p.ollama().maxConcurrency()).isEqualTo(1);

                    SuggestPostLimits limits = ctx.getBean(SuggestPostLimits.class);
                    assertThat(limits.maxTags()).isEqualTo(10);
                    assertThat(limits.titleMax()).isEqualTo(100);
                    assertThat(limits.contentMax()).isEqualTo(100_000);
                });
    }

    @Test
    void api_key는_빈_값이면_Gemini를_쓰지_않는다() {
        runner.withPropertyValues("GEMINI_API_KEY=")
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            TagSuggestProperties.Gemini g =
                                    ctx.getBean(TagSuggestProperties.class).gemini();
                            assertThat(g.apiKey()).isEmpty();
                            assertThat(g.hasApiKey()).isFalse();
                        });
        runner.withPropertyValues("GEMINI_API_KEY=test-not-a-real-key")
                .run(
                        ctx ->
                                assertThat(
                                                ctx.getBean(TagSuggestProperties.class)
                                                        .gemini()
                                                        .hasApiKey())
                                        .isTrue());
    }

    @Test
    void num_thread는_1_이상() {
        runner.withPropertyValues("blog.ai.tag-suggest.ollama.num-thread=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void similarity_threshold는_0에서_1_사이() {
        runner.withPropertyValues("blog.ai.tag-suggest.cache.similarity-threshold=1.5")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.ai.tag-suggest.cache.similarity-threshold=-0.1")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("blog.ai.tag-suggest.cache.similarity-threshold=0")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void AI_동의_버전_기본값은_2026_10_08() {
        runner.run(
                ctx -> {
                    AccountProperties.Agreement.Document ai =
                            ctx.getBean(AccountProperties.class).agreement().ai();
                    assertThat(ai.version()).isEqualTo("2026-10-08");
                    assertThat(ai.effectiveDate()).isEqualTo(LocalDate.of(2026, 10, 8));
                });
        runner.withPropertyValues("BLOG_AGREEMENT_AI_VERSION=2027-01-01")
                .run(
                        ctx ->
                                assertThat(
                                                ctx.getBean(AccountProperties.class)
                                                        .agreement()
                                                        .ai()
                                                        .version())
                                        .isEqualTo("2027-01-01"));
    }
}
