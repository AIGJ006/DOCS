package com.team.blog.shared.web.cursor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 불투명 목록 커서 코덱 (02 §5-1, 005 R-24, 006 R16).
 *
 * <p>형식: 패딩 없는 Base64URL( JSON {@code {"v":1,"l":"<목록 구분>","k":[<정렬 키>…], <추가 필드>…}} ). 예: {@code
 * {"v":1,"l":"home","k":[1790755200123456,37]}} (시각은 epoch 마이크로초 정수). 클라이언트는 해석하지 않고 그대로 돌려준다.
 *
 * <p>Base64·JSON이 아님, {@code v} ≠ 1, {@code l}이 요청 목록과 다름, {@code k}가 없거나 비었거나 배열이 아님, 512자 초과는 모두
 * {@link InvalidCursorException}(400 {@code INVALID_CURSOR}). 005·006·007·008·010·012와 001 친구 목록이
 * 함께 쓴다.
 */
@Component
public class CursorCodec {

    public static final int VERSION = 1;
    public static final int MAX_LENGTH = 512;

    private static final Set<String> RESERVED = Set.of("v", "l", "k");

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 커서를 만든다. {@code extra}의 키는 {@code v}·{@code l}·{@code k}가 아니어야 한다. */
    public String encode(ListScope scope, List<?> key, Map<String, ?> extra) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("커서 키가 비었습니다");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("v", VERSION);
        json.put("l", scope.value());
        json.put("k", key);
        if (extra != null) {
            extra.forEach(
                    (name, value) -> {
                        if (RESERVED.contains(name)) {
                            throw new IllegalArgumentException("예약된 커서 필드: " + name);
                        }
                        json.put(name, value);
                    });
        }
        byte[] bytes = mapper.writeValueAsBytes(json);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 커서를 푼다. {@code expected} 목록의 커서가 아니면 {@link InvalidCursorException}. */
    public CursorPayload decode(String cursor, ListScope expected) {
        if (cursor == null || cursor.isEmpty()) {
            throw new InvalidCursorException("empty cursor");
        }
        if (cursor.length() > MAX_LENGTH) {
            throw new InvalidCursorException("cursor too long: " + cursor.length());
        }
        if (cursor.indexOf('=') >= 0) {
            throw new InvalidCursorException("padded cursor");
        }
        JsonNode root;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            root = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JacksonException e) {
            throw new InvalidCursorException("undecodable cursor");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidCursorException("cursor is not an object");
        }
        JsonNode v = root.get("v");
        if (v == null || !v.isIntegralNumber() || v.asInt() != VERSION) {
            throw new InvalidCursorException("unknown cursor version");
        }
        JsonNode l = root.get("l");
        if (l == null || !l.isString() || !l.asString().equals(expected.value())) {
            throw new InvalidCursorException("cursor list scope mismatch");
        }
        JsonNode k = root.get("k");
        if (k == null || !k.isArray() || k.isEmpty()) {
            throw new InvalidCursorException("cursor key missing");
        }
        List<Object> keys = new ArrayList<>(k.size());
        for (JsonNode item : k) {
            keys.add(scalar(item));
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if (!RESERVED.contains(field.getKey())) {
                extra.put(field.getKey(), mapper.treeToValue(field.getValue(), Object.class));
            }
        }
        return new CursorPayload(
                VERSION, expected.value(), keys, Collections.unmodifiableMap(extra));
    }

    private static Object scalar(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            if (!node.canConvertToLong()) {
                throw new InvalidCursorException("cursor key out of range");
            }
            return node.asLong();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        throw new InvalidCursorException("cursor key is not a scalar");
    }
}
