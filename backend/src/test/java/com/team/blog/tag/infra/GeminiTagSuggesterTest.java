package com.team.blog.tag.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.QuotaKind;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.infra.ai.AiClientConfig;
import com.team.blog.tag.infra.ai.GeminiTagSuggester;
import com.team.blog.tag.infra.ai.PromptBuilder;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Gemini 클라이언트 (013 T022·T045, contracts/providers.md §3). 외부 응답은 MockRestServiceServer로 흉내 낸다. */
class GeminiTagSuggesterTest {

    static final String KEY = "test-gemini-key-not-real";
    static final String URL = "http://gemini.test/v1beta/models/gemini-flash-lite:generateContent";

    private final JsonMapper json = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private GeminiTagSuggester suggester;

    static TagSuggestProperties.Gemini settings(String baseUrl, Duration timeout) {
        return new TagSuggestProperties.Gemini(
                baseUrl,
                "gemini-flash-lite",
                KEY,
                450,
                ZoneId.of("America/Los_Angeles"),
                Duration.ofSeconds(1),
                timeout,
                Duration.ofSeconds(60),
                3,
                8000);
    }

    static TagSuggestInput input() {
        return new TagSuggestInput(
                "JPA N+1 문제를 정리한 글", false, List.of("spring", "jpa"), List.of("jpa"));
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        suggester =
                new GeminiTagSuggester(
                        builder.build(),
                        settings("http://gemini.test", Duration.ofSeconds(10)),
                        new PromptBuilder(),
                        json);
    }

    private static String success(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + JsonMapper.builder().build().writeValueAsString(text)
                + "}]}}]}";
    }

    @Test
    void 요청_모양_키는_헤더_responseSchema_maxOutputTokens() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(
                        jsonPath("$.systemInstruction.parts[0].text").value(PromptBuilder.SYSTEM))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(
                        jsonPath("$.contents[0].parts[0].text")
                                .value(
                                        "인기 태그: spring, jpa\n이미 붙인 태그: jpa\n제목과 본문:\nJPA N+1 문제를 정리한 글"))
                .andExpect(
                        jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andExpect(
                        jsonPath("$.generationConfig.responseSchema.properties.tags.type")
                                .value("ARRAY"))
                .andExpect(
                        jsonPath("$.generationConfig.responseSchema.properties.tags.items.type")
                                .value("STRING"))
                .andExpect(
                        jsonPath("$.generationConfig.responseSchema.properties.tags.maxItems")
                                .value(5))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required[0]").value("tags"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(100))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
                .andRespond(
                        withSuccess(
                                success("{\"tags\":[\"spring\",\"hibernate\"]}"),
                                MediaType.APPLICATION_JSON));

        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Success(List.of("spring", "hibernate")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{\"tags\":\"spring\"}",
                "{\"tags\":[1,2]}",
                "{\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}",
                "{\"other\":[]}"
            })
    void 형식이_깨지면_MALFORMED(String text) {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(success(text), MediaType.APPLICATION_JSON));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.MALFORMED));
    }

    @Test
    void candidates가_없으면_MALFORMED() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"promptFeedback\":{}}", MediaType.APPLICATION_JSON));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.MALFORMED));
    }

    @Test
    void 서버_오류와_키_오류는_SERVER_ERROR() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
    }

    @Test
    void 연결_실패는_CONNECT_읽기_시간_초과는_TIMEOUT() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("refused")));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.CONNECT));
        server.reset();
        server.expect(requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.Failed(FailureKind.TIMEOUT));
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
            TagSuggestProperties.Gemini s = settings(base, Duration.ofMillis(300));
            GeminiTagSuggester real =
                    new GeminiTagSuggester(
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

    // ---- T045: 429 종류 ----

    private void respond429(String body) {
        server.expect(requestTo(URL))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body));
    }

    private static String quotaFailure(String quotaId) {
        return """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[
                  {"@type":"type.googleapis.com/google.rpc.Help","links":[]},
                  {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[
                    {"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests",
                     "quotaId":"%s"}]}]}}
                """
                .formatted(quotaId);
    }

    @Test
    void 하루_한도_429는_PER_DAY() {
        respond429(quotaFailure("GenerateRequestsPerDayPerProjectPerModel-FreeTier"));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.QuotaExceeded(QuotaKind.PER_DAY));
    }

    @Test
    void 분당_한도_429는_PER_MINUTE() {
        respond429(quotaFailure("GenerateRequestsPerMinutePerProjectPerModel-FreeTier"));
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.QuotaExceeded(QuotaKind.PER_MINUTE));
    }

    @Test
    void 종류를_모르는_429는_UNKNOWN() {
        respond429("{\"error\":{\"code\":429,\"message\":\"Resource has been exhausted\"}}");
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.QuotaExceeded(QuotaKind.UNKNOWN));
        server.reset();
        respond429("not json");
        assertThat(suggester.suggest(input()))
                .isEqualTo(new SuggestOutcome.QuotaExceeded(QuotaKind.UNKNOWN));
    }

    @Test
    void 키가_비면_라우터가_고르지_않는다() {
        TagSuggestProperties.Gemini s = settings("http://gemini.test", Duration.ofSeconds(1));
        TagSuggestProperties.Gemini empty =
                new TagSuggestProperties.Gemini(
                        s.baseUrl(),
                        s.model(),
                        "",
                        s.dailyLimit(),
                        s.quotaZone(),
                        s.connectTimeout(),
                        s.timeout(),
                        s.cooldown(),
                        s.unknown429ExhaustCount(),
                        s.maxInputChars());
        assertThat(suggester.isConfigured()).isTrue();
        assertThat(
                        new GeminiTagSuggester(
                                        RestClient.create(), empty, new PromptBuilder(), json)
                                .isConfigured())
                .isFalse();
    }
}
