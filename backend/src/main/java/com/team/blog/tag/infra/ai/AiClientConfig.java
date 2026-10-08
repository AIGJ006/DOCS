package com.team.blog.tag.infra.ai;

import com.team.blog.tag.application.suggest.TagSuggestProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 외부 AI 클라이언트 (013 T024, research R7). 공급자마다 {@link RestClient}를 따로 만들고 연결·응답 시간 제한을 각자 둔다. 공용
 * {@code RestClient.Builder}(관측·로그 설정이 붙을 수 있음)를 쓰지 않고 새로 만든다 — 요청·응답 본문은 어떤 로그에도 남기지 않는다(R11).
 */
@Configuration(proxyBeanMethods = false)
public class AiClientConfig {

    /** 기준 주소·시간 제한을 둔 새 클라이언트 (시험도 이것으로 만든다). */
    public static RestClient restClient(String baseUrl, Duration connectTimeout, Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    GeminiTagSuggester geminiTagSuggester(
            TagSuggestProperties properties, PromptBuilder prompts, JsonMapper json) {
        TagSuggestProperties.Gemini g = properties.gemini();
        return new GeminiTagSuggester(
                restClient(g.baseUrl(), g.connectTimeout(), g.timeout()), g, prompts, json);
    }

    @Bean
    OllamaTagSuggester ollamaTagSuggester(
            TagSuggestProperties properties, PromptBuilder prompts, JsonMapper json) {
        TagSuggestProperties.Ollama o = properties.ollama();
        return new OllamaTagSuggester(
                restClient(o.baseUrl(), o.connectTimeout(), o.timeout()), o, prompts, json);
    }
}
