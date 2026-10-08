package com.team.blog.tag.infra.ai;

import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.Provider;
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
 * 자체 AI Ollama {@code /api/chat} (013 T024, contracts/providers.md §4). {@code format}에 JSON 스키마를
 * 주고 {@code stream: false}로 한 번에 받는다. {@code num_thread}는 배포 서버 성능 코어 수 이하(기본값은 매우 느림, 34 §8-1). 동시
 * 처리 제한은 라우터({@code ai:ollama:inflight})가 이 앞에서 한다.
 */
public class OllamaTagSuggester implements TagSuggester {

    private static final Logger log = LoggerFactory.getLogger(OllamaTagSuggester.class);

    private final RestClient client;
    private final TagSuggestProperties.Ollama settings;
    private final PromptBuilder prompts;
    private final JsonMapper json;

    public OllamaTagSuggester(
            RestClient client,
            TagSuggestProperties.Ollama settings,
            PromptBuilder prompts,
            JsonMapper json) {
        this.client = client;
        this.settings = settings;
        this.prompts = prompts;
        this.json = json;
    }

    @Override
    public Provider provider() {
        return Provider.OLLAMA;
    }

    @Override
    public boolean isConfigured() {
        return settings.enabled();
    }

    @Override
    public SuggestOutcome suggest(TagSuggestInput input) {
        String body = json.writeValueAsString(requestBody(prompts.build(Provider.OLLAMA, input)));
        try {
            return client.post()
                    .uri("/api/chat")
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
            log.warn("ollama 호출 실패: {}", kind);
            return new SuggestOutcome.Failed(kind);
        }
    }

    ObjectNode requestBody(PromptBuilder.Prompt prompt) {
        ObjectNode root = json.createObjectNode();
        root.put("model", settings.model());
        root.put("stream", false);
        root.set("format", AiResponses.tagsSchema(json, false));
        ObjectNode options = root.putObject("options");
        options.put("num_thread", settings.numThread());
        options.put("num_predict", 100);
        options.put("temperature", 0.2);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        messages.addObject().put("role", "user").put("content", prompt.user());
        return root;
    }

    private SuggestOutcome interpret(int status, String body) {
        if (status == 200) {
            return AiResponses.readJson(json, body)
                    .map(root -> root.path("message").path("content"))
                    .filter(JsonNode::isString)
                    .flatMap(text -> AiResponses.parseTags(json, text.asString()))
                    .<SuggestOutcome>map(SuggestOutcome.Success::new)
                    .orElseGet(() -> new SuggestOutcome.Failed(FailureKind.MALFORMED));
        }
        log.warn("ollama 오류 응답: status={}", status);
        return new SuggestOutcome.Failed(FailureKind.SERVER_ERROR);
    }
}
