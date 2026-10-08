package com.team.blog.post.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.post.support.VisibilityApi;
import com.team.blog.post.web.dto.VisibilityChangeRequest;
import com.team.blog.post.web.dto.VisibilityChangeResponse;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * 계약 일치 (004 T077). {@code specs/004-visibility-permission/contracts/openapi.yaml}의 {@code PUT
 * /api/posts/{postId}/visibility}(경로·메서드·요청/응답 스키마·응답 코드 200/400/401/403/404·예시 본문·헤더)가 실제 서버와 같은지
 * 본다.
 *
 * <p>(구현 메모) springdoc이 pom에 없어 {@code /v3/api-docs}와 비교하지 않고, 005 {@code
 * ReadingContractConformanceIntegrationTest}처럼 계약 YAML을 직접 읽어 핸들러 매핑·DTO 구성 요소·실제 응답과 맞춘다.
 */
class VisibilityOpenApiContractIT extends IntegrationTestBase {

    private static final Path CONTRACT =
            Path.of("..", "specs", "004-visibility-permission", "contracts", "openapi.yaml");
    private static final String PATH = "/api/posts/{postId}/visibility";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired private VisibilityRegistry registry;

    private Map<String, Object> root;
    private Map<String, Object> operation;
    private VisibilityApi api;
    private PostFixtures posts;

    @BeforeEach
    void load() throws IOException {
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            root = new Yaml().load(in);
        }
        operation = map(map(map(root, "paths"), PATH), "put");
        api = new VisibilityApi(mockMvc);
        posts = new PostFixtures(jdbc);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object parent, String key) {
        Object value = ((Map<String, Object>) parent).get(key);
        assertThat(value).as("계약에 " + key + "가 있다").isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /** {@code $ref: '#/components/...'}를 따라간다. */
    private Map<String, Object> resolve(Map<String, Object> node) {
        Object ref = node.get("$ref");
        if (!(ref instanceof String pointer)) {
            return node;
        }
        Object current = root;
        for (String part : pointer.substring(2).split("/")) {
            current = map(current, part);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) current;
        return resolve(resolved);
    }

    private Map<String, Object> response(String code) {
        return resolve(map(map(operation, "responses"), code));
    }

    private Map<String, Object> jsonSchema(Map<String, Object> content) {
        return resolve(map(map(map(content, "content"), "application/json"), "schema"));
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static Map<String, Object> json(MvcResult result) {
        return JsonPath.parse(VisibilityApi.body(result)).json();
    }

    private boolean mapped(String method, String path) {
        try {
            HandlerExecutionChain chain =
                    handlerMapping.getHandler(new MockHttpServletRequest(method, path));
            return chain != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void 경로_메서드_응답_코드가_같다() {
        assertThat(operation.get("operationId")).isEqualTo("setPostVisibility");
        assertThat(map(operation, "responses").keySet())
                .containsExactlyInAnyOrder("200", "400", "401", "403", "404");
        assertThat(mapped("PUT", "/api/posts/1/visibility")).as("PUT 매핑").isTrue();
        assertThat(map(map(root, "paths"), PATH).keySet()).containsExactly("put");
    }

    @Test
    void 요청_응답_스키마가_DTO와_같다() {
        Map<String, Object> request = jsonSchema(map(operation, "requestBody"));
        assertThat(map(request, "properties").keySet())
                .isEqualTo(components(VisibilityChangeRequest.class));
        assertThat(request.get("required")).isEqualTo(List.of("visibility"));

        Map<String, Object> ok = jsonSchema(response("200"));
        assertThat(map(ok, "properties").keySet())
                .isEqualTo(components(VisibilityChangeResponse.class));
        assertThat(Set.copyOf((List<?>) ok.get("required")))
                .isEqualTo(components(VisibilityChangeResponse.class));
    }

    @Test
    void 공개_범위_값_목록과_기본_허용값이_같다() {
        Map<String, Object> visibility = map(map(map(root, "components"), "schemas"), "Visibility");
        assertThat(Set.copyOf((List<?>) visibility.get("enum")))
                .isEqualTo(
                        Arrays.stream(Visibility.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet()));
        assertThat(Set.copyOf((List<?>) visibility.get("x-enabled-by-default")))
                .isEqualTo(
                        registry.allowedValues().stream()
                                .map(Enum::name)
                                .collect(Collectors.toSet()));
    }

    @Test
    void 응답_200은_계약의_키와_헤더() throws Exception {
        long author = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, author);
        long postId = posts.create(author, State.PUBLISHED_PRIVATE);

        MvcResult result = api.change(session, postId, "PUBLIC");

        assertThat(VisibilityApi.status(result)).isEqualTo(200);
        assertThat(json(result).keySet())
                .isEqualTo(map(jsonSchema(response("200")), "properties").keySet());
        assertThat(VisibilityApi.cacheControl(result))
                .isEqualTo(
                        map(map(map(response("200"), "headers"), "Cache-Control"), "schema")
                                .get("const"));
        assertThat((String) VisibilityApi.read(result, "$.firstPublicAt"))
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z");
    }

    @Test
    void 응답_400_401_404는_계약_예시와_본문이_같다() throws Exception {
        long author = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, author);
        long postId = posts.create(author, State.PUBLISHED_PUBLIC);

        assertThat(json(api.change(session, postId, "FRIENDS")))
                .isEqualTo(example(response("400")));
        assertThat(json(api.change(null, postId, "PRIVATE"))).isEqualTo(example(response("401")));

        MvcResult notFound = api.change(session, posts.nonexistentId(), "PRIVATE");
        assertThat(VisibilityApi.status(notFound)).isEqualTo(404);
        assertThat(json(notFound)).isEqualTo(example(response("404")));
        assertThat(VisibilityApi.cacheControl(notFound))
                .isEqualTo(
                        map(map(map(response("404"), "headers"), "Cache-Control"), "schema")
                                .get("const"));
    }

    @Test
    void 응답_403은_세_계정_상태_예시와_본문이_같다() throws Exception {
        Map<String, Object> examples =
                map(map(map(response("403"), "content"), "application/json"), "examples");

        long unverified = members().member().emailVerified(false).create();
        assertThat(json(api.change(TestLogin.loginAs(mockMvc, unverified), 1L, "PRIVATE")))
                .isEqualTo(map(examples, "emailNotVerified").get("value"));

        long withdrawn = members().member().create();
        Cookie withdrawnSession = TestLogin.loginAs(mockMvc, withdrawn);
        posts.withdraw(withdrawn);
        assertThat(json(api.change(withdrawnSession, 1L, "PRIVATE")))
                .isEqualTo(map(examples, "withdrawn").get("value"));

        long suspended = members().member().create();
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, java.time.Instant.now().plusSeconds(86_400), "계약 시험");
        assertThat(json(api.change(suspendedSession, 1L, "PRIVATE")))
                .isEqualTo(map(examples, "suspended").get("value"));
    }

    private static Object example(Map<String, Object> response) {
        return map(map(map(response, "content"), "application/json"), "example");
    }
}
