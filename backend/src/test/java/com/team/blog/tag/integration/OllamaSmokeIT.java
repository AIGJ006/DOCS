package com.team.blog.tag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.infra.ai.AiClientConfig;
import com.team.blog.tag.infra.ai.OllamaTagSuggester;
import com.team.blog.tag.infra.ai.PromptBuilder;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 자체 AI 측정 (013 T053, SC-006). 기본 빌드에서는 건너뛴다 — 모델이 떠 있는 곳에서만:
 *
 * <pre>
 * ./mvnw verify -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OllamaSmokeIT \
 *   -Dollama.smoke=true -Dollama.base-url=http://localhost:11434 [-Dollama.model=qwen2.5:1.5b] [-Dollama.num-thread=4]
 * </pre>
 *
 * 2,000자 입력이 30초 안에 태그 1개 이상. 첫 호출은 모델 적재 시간이 들어가므로 한 번 데운 뒤 잰다. 걸린 시간을 표준 출력에 남긴다(quickstart §2
 * 기록용).
 */
@Tag("ollama")
@EnabledIfSystemProperty(named = "ollama.smoke", matches = "true")
class OllamaSmokeIT {

    private static final String PARAGRAPH =
            "Spring Boot 애플리케이션에서 JPA 엔티티의 지연 로딩과 N+1 쿼리 문제를 다룬다. fetch join과 batch size,"
                    + " 엔티티 그래프로 쿼리 수를 줄이는 방법을 비교하고, 트랜잭션 범위와 영속성 컨텍스트가 어떻게 동작하는지"
                    + " PostgreSQL 실행 계획과 함께 살펴본다. Redis 캐시를 붙였을 때의 효과와 주의할 점도 정리한다. ";

    @Test
    void 이천_자_입력이_30초_안에_태그_하나_이상() {
        String baseUrl = System.getProperty("ollama.base-url", "http://localhost:11434");
        String model = System.getProperty("ollama.model", "qwen2.5:1.5b");
        int numThread = Integer.parseInt(System.getProperty("ollama.num-thread", "4"));
        TagSuggestProperties.Ollama settings =
                new TagSuggestProperties.Ollama(
                        true,
                        baseUrl,
                        model,
                        numThread,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(120),
                        2000,
                        1);
        OllamaTagSuggester ollama =
                new OllamaTagSuggester(
                        AiClientConfig.restClient(
                                baseUrl, settings.connectTimeout(), settings.timeout()),
                        settings,
                        new PromptBuilder(),
                        JsonMapper.builder().build());

        StringBuilder text = new StringBuilder("JPA N+1 문제 정리\n");
        while (text.codePointCount(0, text.length()) < 2000) {
            text.append(PARAGRAPH);
        }
        String input = text.substring(0, text.offsetByCodePoints(0, 2000));
        TagSuggestInput request =
                new TagSuggestInput(
                        input, true, List.of("spring", "jpa", "react", "docker"), List.of());

        long warmStart = System.nanoTime();
        SuggestOutcome warm = ollama.suggest(request);
        long warmMs = (System.nanoTime() - warmStart) / 1_000_000;

        long start = System.nanoTime();
        SuggestOutcome outcome = ollama.suggest(request);
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf(
                "OllamaSmokeIT model=%s numThread=%d warm=%dms(%s) measured=%dms outcome=%s%n",
                model, numThread, warmMs, warm.getClass().getSimpleName(), tookMs, outcome);

        assertThat(outcome).isInstanceOf(SuggestOutcome.Success.class);
        assertThat(((SuggestOutcome.Success) outcome).rawTags()).isNotEmpty();
        assertThat(Duration.ofMillis(tookMs)).isLessThanOrEqualTo(Duration.ofSeconds(30));
    }
}
