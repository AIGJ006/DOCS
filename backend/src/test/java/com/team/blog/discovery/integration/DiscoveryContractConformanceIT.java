package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.discovery.application.trending.TrendingSnapshotJob;
import com.team.blog.discovery.support.SearchApi;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.discovery.support.TrendingRedisHelper;
import com.team.blog.support.IntegrationTestBase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * 트렌딩·검색 응답이 계약({@code specs/012-trending-search/contracts/openapi.yaml})과 맞는가 (012 T044). 005
 * {@code ReadingContractConformanceIntegrationTest}와 같은 방식 — 계약 YAML을 SnakeYAML로 읽어 필수 필드·없는 필드·기본
 * 타입을 안쪽 객체까지 보고, 여기서는 {@code enum}({@code notice})도 본다. {@code snippet.marks}는 정수 두 개짜리 배열의 배열이어야
 * 한다.
 */
class DiscoveryContractConformanceIT extends IntegrationTestBase {

    private static final Path CONTRACT =
            Path.of("..", "specs", "012-trending-search", "contracts", "openapi.yaml");

    private static Map<String, Object> schemas;

    @Autowired TrendingSnapshotJob job;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void readContract() throws IOException {
        assertThat(CONTRACT).as("계약 파일 (backend 디렉터리 기준)").exists();
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            schemas = new LinkedHashMap<>((Map<String, Object>) components.get("schemas"));
        }
    }

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    private long seed() {
        long author = members().member().nickname("계약확인").create();
        jdbc.update("UPDATE member SET bio = ? WHERE id = ?", "첫 줄\n둘째 줄", author);
        long reader = members().member().create();
        // 트렌딩은 작성자당 3개라 작성자를 넷으로 나눈다 (12개 → nextCursor 있음)
        long[] authors = {
            author,
            members().member().create(),
            members().member().create(),
            members().member().create()
        };
        SearchFixtures posts = new SearchFixtures(jdbc);
        long last = 0;
        for (int i = 0; i < 12; i++) {
            last =
                    posts.post(authors[i % authors.length])
                            .title("트랜잭션 정리 " + i)
                            .content("본문에 트랜잭션 <script>alert(1)</script> 이야기 " + i)
                            .tags("spring")
                            .age(Duration.ofHours(i + 1))
                            .likes(i + 1)
                            .commenters(reader)
                            .create();
        }
        return last;
    }

    @Test
    void 글_검색은_PostSearchPage() throws Exception {
        seed();

        MvcResult first = api().posts("트랜잭션 정리");
        assertConforms(first, "PostSearchPage");
        assertThat((String) JsonPath.read(body(first), "$.notice"))
                .isEqualTo("TWO_CHAR_TITLE_TAG_ONLY");
        List<List<Integer>> marks = JsonPath.read(body(first), "$.items[0].snippet.marks");
        assertThat(marks).isNotEmpty().allSatisfy(range -> assertThat(range).hasSize(2));
        String cursor = JsonPath.read(body(first), "$.nextCursor");
        assertConforms(api().posts("트랜잭션 정리", null, cursor, null), "PostSearchPage");
        assertConforms(api().posts("트랜잭션", "latest", null, null), "PostSearchPage");
        assertConforms(api().posts("없는낱말이에요"), "PostSearchPage");
    }

    @Test
    void 사람_검색은_PeopleSearchResult() throws Exception {
        seed();

        assertConforms(api().people("계약확인"), "PeopleSearchResult");
        assertConforms(api().people("없는사람이에요"), "PeopleSearchResult");
    }

    @Test
    void 트렌딩은_스냅샷이든_즉시_계산이든_PostCardPage() throws Exception {
        seed();

        assertConforms(api().trending(null), "PostCardPage");
        job.refresh(Instant.now());
        MvcResult first = api().trending(null);
        assertConforms(first, "PostCardPage");
        assertConforms(api().trending(JsonPath.read(body(first), "$.nextCursor")), "PostCardPage");
    }

    @Test
    void 오류는_ErrorResponse() throws Exception {
        seed();
        String snapshot = job.refresh(Instant.now());
        String cursor = JsonPath.read(body(api().trending(null)), "$.nextCursor");
        new TrendingRedisHelper(redis).expire(snapshot); // 보던 순위가 만료됨

        MvcResult expired = api().trending(cursor);
        assertThat(status(expired)).isEqualTo(410);
        assertConforms(expired, "ErrorResponse");

        MvcResult tooShort = api().posts("a b");
        assertThat(status(tooShort)).isEqualTo(400);
        assertConforms(tooShort, "ErrorResponse");

        MvcResult badCursor = api().posts("트랜잭션", null, "abc%%", null);
        assertThat(status(badCursor)).isEqualTo(400);
        assertConforms(badCursor, "ErrorResponse");

        MvcResult noBlog = api().posts("트랜잭션", null, null, "nobody_here");
        assertThat(status(noBlog)).isEqualTo(404);
        assertConforms(noBlog, "ErrorResponse");
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
        if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(value)) {
            problems.add(path + ": enum 밖 값 " + value + " ∉ " + allowed);
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
