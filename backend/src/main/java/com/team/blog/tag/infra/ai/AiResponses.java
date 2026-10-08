package com.team.blog.tag.infra.ai;

import com.team.blog.tag.application.suggest.FailureKind;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 두 공급자가 함께 쓰는 요청·응답 처리 (contracts/providers.md §3·§4). */
final class AiResponses {

    /** 응답이 줄 수 있는 최대 태그 수 (스키마 {@code maxItems}). 넘으면 지시를 어긴 응답으로 본다. */
    static final int MAX_TAGS = 5;

    /** 응답 본문을 이만큼만 읽는다. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private AiResponses() {}

    /** {@code {"tags": [string], maxItems 5}} 스키마 (Gemini는 대문자 타입 이름). */
    static ObjectNode tagsSchema(JsonMapper json, boolean geminiTypes) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", geminiTypes ? "OBJECT" : "object");
        ObjectNode tags = schema.putObject("properties").putObject("tags");
        tags.put("type", geminiTypes ? "ARRAY" : "array");
        tags.putObject("items").put("type", geminiTypes ? "STRING" : "string");
        tags.put("maxItems", MAX_TAGS);
        schema.putArray("required").add("tags");
        return schema;
    }

    /** 모델이 돌려준 글자 {@code {"tags": [...]}}. 문자열 배열이 아니거나 5개를 넘으면 빈 값. */
    static Optional<List<String>> parseTags(JsonMapper json, String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        JsonNode tags = root == null ? null : root.get("tags");
        if (tags == null || !tags.isArray() || tags.size() > MAX_TAGS) {
            return Optional.empty();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode tag : tags) {
            if (!tag.isString()) {
                return Optional.empty();
            }
            out.add(tag.asString());
        }
        return Optional.of(out);
    }

    /** 본문 JSON (읽지 못하면 빈 값). */
    static Optional<JsonNode> readJson(JsonMapper json, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(json.readTree(body));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    static String readBody(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        return new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
    }

    /** 연결 단계 예외의 종류. */
    static FailureKind failureOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return FailureKind.TIMEOUT;
            }
            if (t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException) {
                return FailureKind.CONNECT;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return FailureKind.SERVER_ERROR;
    }
}
