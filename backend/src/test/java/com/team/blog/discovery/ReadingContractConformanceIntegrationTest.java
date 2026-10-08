package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * 응답이 계약({@code specs/005-post-reading/contracts/openapi.yaml})과 맞는가 (005 T076).
 *
 * <p>공통 pom에 OpenAPI 검증 라이브러리가 없어 계약 YAML을 SnakeYAML로 읽고 응답 JSON을 스키마와 견준다: {@code required} 필드가 모두
 * 있는지, 계약에 없는 필드가 없는지, 기본 타입(문자열·정수·불리언·배열·객체·{@code null} 허용)이 맞는지를 안쪽 객체까지 본다({@code $ref}·
 * {@code allOf} 풀이). 형식({@code date-time}·{@code uri})과 {@code enum}·길이는 보지 않는다.
 */
class ReadingContractConformanceIntegrationTest extends IntegrationTestBase {

    private static final Path CONTRACT =
            Path.of("..", "specs", "005-post-reading", "contracts", "openapi.yaml");

    /** 010이 머리말에 더한 세 칸 ({@code BlogHeaderFollowFields}, {@code x-extends getBlogHeader}). */
    private static final Path FOLLOW_CONTRACT =
            Path.of("..", "specs", "010-follow-feed", "contracts", "openapi.yaml");

    /** 017이 상세에 더한 한 칸 ({@code PostDetailCategoryFields}, 분류 없음이면 {@code null}). */
    private static final Path CATEGORY_CONTRACT =
            Path.of("..", "specs", "017-category", "contracts", "openapi.yaml");

    private static Map<String, Object> schemas;

    private PostReadingFixture fixture;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void readContract() throws IOException {
        assertThat(CONTRACT).as("계약 파일 (backend 디렉터리 기준)").exists();
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            schemas = new LinkedHashMap<>((Map<String, Object>) components.get("schemas"));
        }
        // 010 확장: BlogHeader = 005 BlogHeader + 010 BlogHeaderFollowFields
        try (InputStream in = Files.newInputStream(FOLLOW_CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> follow =
                    (Map<String, Object>)
                            ((Map<String, Object>) components.get("schemas"))
                                    .get("BlogHeaderFollowFields");
            schemas.put("BlogHeader005", schemas.get("BlogHeader"));
            schemas.put("BlogHeaderFollowFields", follow);
            schemas.put(
                    "BlogHeader",
                    Map.of(
                            "allOf",
                            List.of(
                                    Map.of("$ref", "#/components/schemas/BlogHeader005"),
                                    Map.of(
                                            "$ref",
                                            "#/components/schemas/BlogHeaderFollowFields"))));
        }
        // 017 확장: PostDetail = 005 PostDetail + 017 PostDetailCategoryFields
        try (InputStream in = Files.newInputStream(CATEGORY_CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> category = (Map<String, Object>) components.get("schemas");
            schemas.put("PostDetail005", schemas.get("PostDetail"));
            schemas.put("PostDetailCategoryFields", category.get("PostDetailCategoryFields"));
            schemas.put("PostDetailCategory", category.get("PostDetailCategory"));
            schemas.put(
                    "PostDetail",
                    Map.of(
                            "allOf",
                            List.of(
                                    Map.of("$ref", "#/components/schemas/PostDetail005"),
                                    Map.of(
                                            "$ref",
                                            "#/components/schemas/PostDetailCategoryFields"))));
        }
    }

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 홈_목록은_PostCardPage() throws Exception {
        MvcResult result = api().home(null, null);

        assertConforms(result, "PostCardPage");
    }

    @Test
    void 블로그_머리말과_목록은_BlogHeader_PostCardPage() throws Exception {
        Cookie a = fixture.loginAs(mockMvc, "A");

        assertConforms(api().blogHeader(a, PostReadingFixture.A_HANDLE), "BlogHeader");
        assertConforms(api().blogHeader(null, PostReadingFixture.B_HANDLE), "BlogHeader");
        assertConforms(api().blogPosts(null, PostReadingFixture.A_HANDLE, null), "PostCardPage");
    }

    @Test
    void 상세는_독자_작성자_임시글_모두_PostDetail() throws Exception {
        Cookie a = fixture.loginAs(mockMvc, "A");

        assertConforms(api().detail(null, fixture.postOf("A", "republished")), "PostDetail");
        assertConforms(
                api().detail(fixture.loginAs(mockMvc, "B"), fixture.postOf("A", "code")),
                "PostDetail");
        assertConforms(api().detail(a, fixture.postOf("A", "editing")), "PostDetail");
        assertConforms(api().detail(a, fixture.postOf("A", "private1")), "PostDetail");
        assertConforms(api().detail(a, fixture.postOf("A", "draft")), "PostDetail");
    }

    @Test
    void 오류는_ErrorResponse() throws Exception {
        MvcResult badCursor = api().home(null, "abc%%");
        assertThat(status(badCursor)).isEqualTo(400);
        assertConforms(badCursor, "ErrorResponse");

        MvcResult notFound = api().detail(null, fixture.postOf("A", "private1"));
        assertThat(status(notFound)).isEqualTo(404);
        assertConforms(notFound, "ErrorResponse");
    }

    // ---- 검증기 ----

    private static void assertConforms(MvcResult result, String schemaName) {
        assertThat(status(result) / 100).as(body(result)).isIn(2, 4);
        Object json = JsonPath.read(body(result), "$");
        List<String> problems = new ArrayList<>();
        check(json, ref(schemaName), "$", problems);
        assertThat(problems).as(schemaName + " ← " + body(result)).isEmpty();
    }

    private static Map<String, Object> ref(String name) {
        return schema(Map.of("$ref", "#/components/schemas/" + name));
    }

    /** {@code $ref}·{@code allOf}를 풀어 한 객체 스키마로 합친다. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(Map<String, Object> raw) {
        if (raw.containsKey("$ref")) {
            String name = ((String) raw.get("$ref")).replace("#/components/schemas/", "");
            Map<String, Object> target = (Map<String, Object>) schemas.get(name);
            assertThat(target).as("계약에 없는 스키마 " + name).isNotNull();
            return schema(target);
        }
        if (raw.containsKey("allOf")) {
            Map<String, Object> merged = new LinkedHashMap<>(raw);
            merged.remove("allOf");
            Map<String, Object> properties = new LinkedHashMap<>();
            Set<String> required = new LinkedHashSet<>();
            for (Object part : (List<Object>) raw.get("allOf")) {
                Map<String, Object> resolved = schema((Map<String, Object>) part);
                properties.putAll(
                        (Map<String, Object>) resolved.getOrDefault("properties", Map.of()));
                required.addAll((List<String>) resolved.getOrDefault("required", List.of()));
                resolved.forEach(merged::putIfAbsent);
            }
            merged.put("type", "object");
            merged.put("properties", properties);
            merged.put("required", List.copyOf(required));
            return merged;
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static void check(
            Object value, Map<String, Object> rawSchema, String path, List<String> problems) {
        Map<String, Object> schema = schema(rawSchema);
        List<String> types = types(schema);
        if (value == null) {
            if (!types.isEmpty() && !types.contains("null")) {
                problems.add(path + ": null은 허용되지 않음 " + types);
            }
            return;
        }
        String actual = typeOf(value);
        if (!types.isEmpty()
                && !types.contains(actual)
                && !(actual.equals("integer") && types.contains("number"))) {
            problems.add(path + ": 타입 " + actual + " ≠ 계약 " + types);
            return;
        }
        if (value instanceof Map<?, ?> object) {
            Map<String, Object> properties =
                    (Map<String, Object>) schema.getOrDefault("properties", Map.of());
            for (String required : (List<String>) schema.getOrDefault("required", List.of())) {
                if (!object.containsKey(required)) {
                    problems.add(path + ": 필수 필드 없음 " + required);
                }
            }
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object property = properties.get(key);
                if (property == null) {
                    if (!properties.isEmpty() || !schema.containsKey("additionalProperties")) {
                        problems.add(path + ": 계약에 없는 필드 " + key);
                    }
                    continue;
                }
                check(entry.getValue(), (Map<String, Object>) property, path + "." + key, problems);
            }
        } else if (value instanceof List<?> array && schema.containsKey("items")) {
            Map<String, Object> items = (Map<String, Object>) schema.get("items");
            for (int i = 0; i < array.size(); i++) {
                check(array.get(i), items, path + "[" + i + "]", problems);
            }
        }
    }

    private static List<String> types(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type == null) {
            return schema.containsKey("properties") ? List.of("object") : List.of();
        }
        if (type instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(type));
    }

    private static String typeOf(Object value) {
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "integer";
        }
        if (value instanceof Number) {
            return "number";
        }
        return "string";
    }
}
