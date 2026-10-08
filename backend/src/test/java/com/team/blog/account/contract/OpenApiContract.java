package com.team.blog.account.contract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * OpenAPI 3.1 계약의 응답 스키마로 JSON 값을 검사하는 작은 검사기 (001 T146).
 *
 * <p>공통 pom에 {@code swagger-request-validator}가 없고(오프라인 저장소에도 없음) pom을 바꾸지 않기 위해, 005
 * ReadingContractConformanceIntegrationTest처럼 계약 YAML을 SnakeYAML로 읽는다. 경로·메서드·상태 코드로 응답 스키마를 찾아 다음을
 * 본다: {@code $ref}(schemas·responses), {@code allOf}·{@code oneOf}·{@code anyOf}, {@code type}(배열
 * 표기·{@code 'null'} 포함), {@code required}, 계약에 없는 필드(추가 필드 금지 — {@code additionalProperties: true}만
 * 허용), {@code enum}, 배열 {@code items}, {@code format: date-time·date}, 정수 {@code minimum·maximum}.
 */
final class OpenApiContract {

    private final Map<String, Object> root;

    private OpenApiContract(Map<String, Object> root) {
        this.root = root;
    }

    static OpenApiContract load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(in);
            return new OpenApiContract(root);
        }
    }

    /** 응답 본문(Jackson으로 읽은 Map·List·값)이 계약과 다른 곳 목록. 비어 있으면 일치. */
    List<String> violations(String pathTemplate, String method, int status, Object body) {
        Map<String, Object> schema = responseSchema(pathTemplate, method, status);
        List<String> out = new ArrayList<>();
        check(schema, body, "$", out);
        return out;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> responseSchema(String pathTemplate, String method, int status) {
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> item = (Map<String, Object>) paths.get(pathTemplate);
        if (item == null) {
            throw new IllegalArgumentException("계약에 없는 경로: " + pathTemplate);
        }
        Map<String, Object> operation =
                (Map<String, Object>) item.get(method.toLowerCase(Locale.ROOT));
        if (operation == null) {
            throw new IllegalArgumentException("계약에 없는 메서드: " + method + " " + pathTemplate);
        }
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        Map<String, Object> response = (Map<String, Object>) responses.get(String.valueOf(status));
        if (response == null) {
            throw new IllegalArgumentException(
                    "계약에 없는 상태 코드: " + method + " " + pathTemplate + " " + status);
        }
        response = resolve(response);
        Map<String, Object> content = (Map<String, Object>) response.get("content");
        if (content == null) {
            throw new IllegalArgumentException("본문이 없는 응답: " + pathTemplate + " " + status);
        }
        Map<String, Object> json = (Map<String, Object>) content.get("application/json");
        return (Map<String, Object>) json.get("schema");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolve(Map<String, Object> node) {
        Map<String, Object> current = node;
        while (current.get("$ref") instanceof String ref) {
            if (!ref.startsWith("#/")) {
                throw new IllegalArgumentException("지원하지 않는 $ref: " + ref);
            }
            Object target = root;
            for (String part : ref.substring(2).split("/")) {
                target = ((Map<String, Object>) target).get(part);
            }
            current = (Map<String, Object>) target;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void check(Map<String, Object> rawSchema, Object value, String at, List<String> out) {
        Map<String, Object> schema = resolve(rawSchema);
        if (schema.get("allOf") instanceof List<?> all) {
            for (Object part : all) {
                check((Map<String, Object>) part, value, at, out);
            }
        }
        for (String key : List.of("oneOf", "anyOf")) {
            if (schema.get(key) instanceof List<?> alternatives) {
                boolean matched = false;
                for (Object alternative : alternatives) {
                    List<String> trial = new ArrayList<>();
                    check((Map<String, Object>) alternative, value, at, trial);
                    if (trial.isEmpty()) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    out.add(at + ": " + key + " 중 맞는 스키마가 없음 (값 " + value + ")");
                }
            }
        }
        List<String> types = types(schema);
        if (!types.isEmpty() && types.stream().noneMatch(t -> isType(t, value))) {
            out.add(at + ": 타입 " + types + "이어야 하는데 " + describe(value));
            return;
        }
        if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(value)) {
            out.add(at + ": enum " + allowed + "에 없는 값 " + value);
        }
        if (value instanceof String text && schema.get("format") instanceof String format) {
            checkFormat(format, text, at, out);
        }
        if (value instanceof Number number && !(value instanceof Double)) {
            long n = number.longValue();
            if (schema.get("minimum") instanceof Number min && n < min.longValue()) {
                out.add(at + ": 최솟값 " + min + "보다 작음 " + n);
            }
            if (schema.get("maximum") instanceof Number max && n > max.longValue()) {
                out.add(at + ": 최댓값 " + max + "보다 큼 " + n);
            }
        }
        if (value instanceof Map<?, ?> object) {
            Map<String, Object> properties =
                    (Map<String, Object>) schema.getOrDefault("properties", Map.of());
            if (schema.get("required") instanceof List<?> required) {
                for (Object name : required) {
                    if (!object.containsKey(name)) {
                        out.add(at + ": 필수 필드 " + name + " 없음");
                    }
                }
            }
            boolean additionalAllowed =
                    Boolean.TRUE.equals(schema.get("additionalProperties"))
                            || (properties.isEmpty() && !schema.containsKey("required"));
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String name = String.valueOf(entry.getKey());
                Object property = properties.get(name);
                if (property == null) {
                    if (!additionalAllowed) {
                        out.add(at + ": 계약에 없는 필드 " + name);
                    }
                    continue;
                }
                check((Map<String, Object>) property, entry.getValue(), at + "." + name, out);
            }
        }
        if (value instanceof List<?> list && schema.get("items") instanceof Map<?, ?> items) {
            for (int i = 0; i < list.size(); i++) {
                check((Map<String, Object>) items, list.get(i), at + "[" + i + "]", out);
            }
        }
    }

    private static List<String> types(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof String single) {
            return List.of(single);
        }
        if (type instanceof List<?> many) {
            return many.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static boolean isType(String type, Object value) {
        return switch (type) {
            case "null" -> value == null;
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "integer" ->
                    value instanceof Integer
                            || value instanceof Long
                            || value instanceof java.math.BigInteger;
            case "number" -> value instanceof Number;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> throw new IllegalArgumentException("모르는 타입: " + type);
        };
    }

    private static void checkFormat(String format, String text, String at, List<String> out) {
        try {
            switch (format) {
                case "date-time" -> OffsetDateTime.parse(text);
                case "date" -> LocalDate.parse(text);
                default -> {
                    // 다른 형식(int64·uri 등)은 보지 않는다.
                }
            }
        } catch (DateTimeParseException e) {
            out.add(at + ": 형식 " + format + "이 아님 " + text);
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " " + value;
    }
}
