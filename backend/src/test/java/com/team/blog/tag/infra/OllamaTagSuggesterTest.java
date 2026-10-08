package com.team.blog.tag.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.infra.ai.AiClientConfig;
import com.team.blog.tag.infra.ai.OllamaTagSuggester;
import com.team.blog.tag.infra.ai.PromptBuilder;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Ollama 클라이언트 (013 T022, contracts/providers.md §4). */
class OllamaTagSuggesterTest {

    static final String URL = "http://ollama.test/api/chat";

    private final JsonMapper json = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private OllamaTagSuggester suggester;

    static TagSuggestProperties.Ollama settings(String baseUrl, Duration timeout, boolean on) {
        return new TagSuggestProperties.Ollama(
                on, baseUrl, "qwen2.5:1.5b", 4, Duration.ofSeconds(1), timeout, 2000, 1);
    }

    static TagSuggestInput input() {
        return new TagSuggestInput(
                "Spring 트랜잭션과 JPA 이야기", false, List.of("spring", "react", "jpa"), List.of());
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        server = MockRestServiceServer.bindTo(builder).build();
        suggester =
                new OllamaTagSuggester(
                        builder.build(),
                        settings("http://ollama.test", Duration.ofSeconds(30), true),
                        new PromptBuilder(),
                        json);
    }

    private static String chat(String content) {
        return "{\"model\":\"qwen2.5:1.5b\",\"message\":{\"role\":\"assistant\",\"content\":"
                + JsonMapper.builder().build().writeValueAsString(content)
                + "},\"done\":true}";
    }

    @Test
    void 요청_모양_format_num_thread_stream_false_num_predict() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:1.5b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.format.type").value("object"))
                .andExpect(jsonPath("$.format.properties.tags.type").value("array"))
                .andExpect(jsonPath("$.format.properties.tags.items.type").value("string"))
                .andExpect(jsonPath("$.format.properties.tags.maxItems").value(5))
                .andExpect(jsonPath("$.format.required[0]").value("tags"))
                .andExpect(jsonPath("$.options.num_thread").value(4))
                .andExpect(jsonPath("$.options.num_predict").value(100))
                .andExpect(jsonPath("$.options.temperature").value(0.2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(PromptBuilder.SYSTEM))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(
                        jsonPath("$.messages[1].content")
                                .value(
                                        "인기 태그: spring, jpa\n이미 붙인 태그: 없음\n제목과 본문:\nSpring 트랜잭션과 JPA 이야기"))
                .andRespond(
                        withSuccess(
                                chat("{\"tags\":[\"spring\",\"transaction\"]}"),
                                MediaType.APPLICATION_JSON));

        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Success(List.of("spring", "transaction")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{\"tags\":\"spring\"}",
                "{\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}",
                "{\"tags\":[null]}"
            })
    void 형식이_깨지면_MALFORMED(String content) {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(chat(content), MediaType.APPLICATION_JSON));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.MALFORMED));
    }

    @Test
    void 서버_오류_연결_실패() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("refused")));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.CONNECT));
    }

    @Test
    void 설정한_시간_제한을_넘으면_TIMEOUT() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slow.createContext(
                "/",
                exchange -> {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        slow.start();
        try {
            String base = "http://localhost:" + slow.getAddress().getPort();
            TagSuggestProperties.Ollama s = settings(base, Duration.ofMillis(300), true);
            OllamaTagSuggester real =
                    new OllamaTagSuggester(
                            AiClientConfig.restClient(base, s.connectTimeout(), s.timeout()),
                            s,
                            new PromptBuilder(),
                            json);
            assertThat(real.suggest(input()))
                    .isEqualTo(new SuggestOutcome.Failed(FailureKind.TIMEOUT));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void 꺼져_있으면_라우터가_고르지_않는다() {
        assertThat(suggester.isConfigured()).isTrue();
        assertThat(
                        new OllamaTagSuggester(
                                        RestClient.create(),
                                        settings("http://x", Duration.ofSeconds(1), false),
                                        new PromptBuilder(),
                                        json)
                                .isConfigured())
                .isFalse();
    }
}
