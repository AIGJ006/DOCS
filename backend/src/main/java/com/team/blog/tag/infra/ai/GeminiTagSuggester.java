package com.team.blog.tag.infra.ai;

import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.QuotaKind;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.application.suggest.TagSuggester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Google Gemini {@code generateContent} (013 T024·T046, contracts/providers.md §3). 키는 {@code
 * x-goog-api-key} 헤더로만 보낸다. 출력은 {@code responseSchema}로 {@code {tags: [string]}}를 강제한다.
 *
 * <p>429는 본문 {@code error.details[]}의 {@code QuotaFailure.violations[].quotaId}로 하루·분당 한도를 가른다. 응답
 * 본문은 해석만 하고 어떤 로그에도 넣지 않는다(외부 오류 본문에 입력이 되돌아올 수 있음, R11). 키 오류 등 400·401·403은 상태 코드만 ERROR로 남긴다.
 */
public class GeminiTagSuggester implements TagSuggester {

    private static final Logger log = LoggerFactory.getLogger(GeminiTagSuggester.class);

    private final RestClient client;
    private final TagSuggestProperties.Gemini settings;
    private final PromptBuilder prompts;
    private final JsonMapper json;

    public GeminiTagSuggester(
            RestClient client,
            TagSuggestProperties.Gemini settings,
            PromptBuilder prompts,
            JsonMapper json) {
        this.client = client;
        this.settings = settings;
        this.prompts = prompts;
        this.json = json;
    }

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return settings.hasApiKey();
    }

    @Override
    public SuggestOutcome suggest(TagSuggestInput input) {
        String body = json.writeValueAsString(requestBody(prompts.build(Provider.GEMINI, input)));
        try {
            return client.post()
                    .uri("/v1beta/models/{model}:generateContent", settings.model())
                    .header("x-goog-api-key", settings.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange(
                            (request, response) ->
                                    interpret(
                                            response.getStatusCode().value(),
                                            AiResponses.readBody(response.getBody())));
        } catch (RestClientException e) {
            FailureKind kind = AiResponses.failureOf(e);
            log.warn("gemini 호출 실패: {}", kind);
            return new SuggestOutcome.Failed(kind);
        }
    }

    ObjectNode requestBody(PromptBuilder.Prompt prompt) {
        ObjectNode root = json.createObjectNode();
        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text", prompt.system());
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt.user());
        ObjectNode config = root.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", AiResponses.tagsSchema(json, true));
        config.put("maxOutputTokens", 100);
        config.put("temperature", 0.2);
        return root;
    }

    private SuggestOutcome interpret(int status, String body) {
        if (status == 200) {
            return AiResponses.readJson(json, body)
                    .map(root -> root.path("candidates").path(0).path("content").path("parts"))
                    .map(parts -> parts.path(0).path("text"))
                    .filter(JsonNode::isString)
                    .flatMap(text -> AiResponses.parseTags(json, text.asString()))
                    .<SuggestOutcome>map(SuggestOutcome.Success::new)
                    .orElseGet(() -> new SuggestOutcome.Failed(FailureKind.MALFORMED));
        }
        if (status == 429) {
            return new SuggestOutcome.QuotaExceeded(quotaKind(body));
        }
        if (status == 400 || status == 401 || status == 403) {
            log.error("gemini 요청 거부: status={}", status);
        } else {
            log.warn("gemini 오류 응답: status={}", status);
        }
        return new SuggestOutcome.Failed(FailureKind.SERVER_ERROR);
    }

    private QuotaKind quotaKind(String body) {
        JsonNode details =
                AiResponses.readJson(json, body)
                        .map(r -> r.path("error").path("details"))
                        .orElse(null);
        if (details == null || !details.isArray()) {
            return QuotaKind.UNKNOWN;
        }
        boolean perMinute = false;
        for (JsonNode detail : details) {
            String type = detail.path("@type").asString("");
            if (!type.endsWith("QuotaFailure")) {
                continue;
            }
            for (JsonNode violation : detail.path("violations")) {
                String quotaId = violation.path("quotaId").asString("");
                if (quotaId.contains("PerDay")) {
                    return QuotaKind.PER_DAY;
                }
                if (quotaId.contains("PerMinute")) {
                    perMinute = true;
                }
            }
        }
        return perMinute ? QuotaKind.PER_MINUTE : QuotaKind.UNKNOWN;
    }
}
