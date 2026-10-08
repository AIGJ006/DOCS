package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.QuotaKind;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.infra.ai.AiClientConfig;
import com.team.blog.tag.infra.ai.GeminiTagSuggester;
import com.team.blog.tag.infra.ai.OllamaTagSuggester;
import com.team.blog.tag.infra.ai.PromptBuilder;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그에 남기지 않는 것 (013 T052, SC-008, research R11, contracts/providers.md §9). 제목·본문·태그 이름·외부 응답 본문·키는
 * 어떤 로그 줄에도 없고, 요청마다 INFO 한 줄만 정해진 모양으로 남는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class TagSuggestLoggingIT extends IntegrationTestBase {

    private static final String KEY = "test-gemini-key-LOGCHECK-7f3a";
    private static final String TITLE_MARK = "제목표식팔칠육";
    private static final String BODY_MARK = "본문표식구삼이";
    private static final String TAG_MARK = "tagmarkzq";
    private static final String RESPONSE_MARK = "응답본문표식오사";
    private static final Pattern LINE =
            Pattern.compile(
                    "ai tag-suggest post=\\d+ provider=(GEMINI|OLLAMA|NONE) cached=(true|false)"
                            + " outcome=(OK|EMPTY|FAILED|BUSY|LIMIT|DISABLED|CONSENT|TOO_SHORT)"
                            + " inputChars=\\d+ took=\\d+ms");

    @Autowired private FakeAi fakeAi;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    private static Map<String, Object> marked(String suffix, boolean refresh) {
        return AiSuggestApi.body(
                TITLE_MARK + " JPA",
                AiSuggestApi.LONG_BODY + "\n\n" + BODY_MARK + " " + suffix,
                List.of(TAG_MARK),
                refresh);
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void assertClean(CapturedOutput output) {
        String all = output.getAll();
        assertThat(all)
                .doesNotContain(TITLE_MARK)
                .doesNotContain(BODY_MARK)
                .doesNotContain(TAG_MARK)
                .doesNotContain(RESPONSE_MARK)
                .doesNotContain(KEY);
    }

    @Test
    void 요청마다_INFO_한_줄이고_제목_본문_태그는_없다(CapturedOutput output) throws Exception {
        long me = members().member().create();
        api.consent(me);
        long postId = new PostFixtures(jdbc).create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        int before = count(LINE, output.getAll());

        fakeAi.gemini().respondTags(TAG_MARK, "spring", "jpa-" + TAG_MARK);
        MvcResult ok = api.suggest(session, postId, marked("하나", false));
        assertThat(status(ok)).as(body(ok)).isEqualTo(200);
        assertThat(body(ok)).contains("spring");

        MvcResult cached = api.suggest(session, postId, marked("하나", false));
        assertThat(status(cached)).isEqualTo(200);

        fakeAi.gemini().respond(new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        assertThat(status(api.suggest(session, postId, marked("둘", true)))).isEqualTo(503);

        fakeAi.ollama().respond(new SuggestOutcome.Failed(FailureKind.MALFORMED));
        fakeAi.gemini()
                .configured(true)
                .respond(new SuggestOutcome.QuotaExceeded(QuotaKind.PER_DAY));
        redis.delete("ai:gemini:cooldown");
        assertThat(status(api.suggest(session, postId, marked("셋", true)))).isEqualTo(503);

        String all = output.getAll();
        assertThat(count(LINE, all) - before).isEqualTo(4);
        assertThat(all).contains("cached=true outcome=OK");
        assertClean(output);
    }

    @Test
    void 공급자_오류_응답과_예외에도_키와_본문이_없다(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int[] status = {500};
        server.createContext(
                "/",
                exchange -> {
                    try (exchange) {
                        exchange.getRequestBody().readAllBytes();
                        if (status[0] == 0) {
                            sleep(1500);
                            exchange.sendResponseHeaders(200, -1);
                            return;
                        }
                        byte[] bytes =
                                ("{\"error\":{\"message\":\""
                                                + RESPONSE_MARK
                                                + " "
                                                + KEY
                                                + "\",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\","
                                                + "\"violations\":[{\"quotaId\":\"GenerateRequestsPerDay\"}]}]},"
                                                + "\"text\":\""
                                                + RESPONSE_MARK
                                                + "\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status[0], bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (IOException ignored) {
                        // 시간 초과로 클라이언트가 끊음
                    }
                });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            JsonMapper json = JsonMapper.builder().build();
            TagSuggestProperties.Gemini g =
                    new TagSuggestProperties.Gemini(
                            base,
                            "gemini-flash-lite",
                            KEY,
                            450,
                            ZoneId.of("America/Los_Angeles"),
                            Duration.ofSeconds(1),
                            Duration.ofMillis(500),
                            Duration.ofSeconds(60),
                            3,
                            8000);
            TagSuggestProperties.Ollama o =
                    new TagSuggestProperties.Ollama(
                            true,
                            base,
                            "qwen2.5:1.5b",
                            4,
                            Duration.ofSeconds(1),
                            Duration.ofMillis(500),
                            2000,
                            1);
            GeminiTagSuggester gemini =
                    new GeminiTagSuggester(
                            AiClientConfig.restClient(base, g.connectTimeout(), g.timeout()),
                            g,
                            new PromptBuilder(),
                            json);
            OllamaTagSuggester ollama =
                    new OllamaTagSuggester(
                            AiClientConfig.restClient(base, o.connectTimeout(), o.timeout()),
                            o,
                            new PromptBuilder(),
                            json);
            TagSuggestInput input =
                    new TagSuggestInput(
                            TITLE_MARK + " " + BODY_MARK,
                            false,
                            List.of(TAG_MARK),
                            List.of(TAG_MARK));

            for (int code : new int[] {500, 400, 403, 429, 200, 0}) {
                status[0] = code;
                assertThat(gemini.suggest(input)).as("gemini %d", code).isNotNull();
                assertThat(ollama.suggest(input)).as("ollama %d", code).isNotNull();
            }
        } finally {
            server.stop(0);
        }
        assertThat(output.getAll()).contains("gemini");
        assertClean(output);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
