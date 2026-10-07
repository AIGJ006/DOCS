package com.team.blog.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.shared.web.SecurityHeadersFilter;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 오류 본문 {code, message, errors, details} (02 §5-1 O8, README "정해진 것" 2026-10-07: errors는 항상 배열,
 * 메시지 끝 마침표 없음, 404는 모든 경우 같은 본문).
 */
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.ErrorProbeController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = SecurityHeadersFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
class GlobalExceptionHandlerTest {

    static final String NOT_FOUND_BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    @Autowired MockMvc mockMvc;

    enum TestReason implements ReasonCode {
        POST_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 고쳤어요"),
        CANNOT_LIKE_OWN_POST(HttpStatus.BAD_REQUEST, "자기 글에는 좋아요를 누를 수 없어요");

        private final HttpStatus status;
        private final String message;

        TestReason(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public String defaultMessage() {
            return message;
        }
    }

    static class PostNotFoundException extends NotFoundException {
        PostNotFoundException(long id) {
            super("post " + id + " not visible");
        }
    }

    record Body(@NotBlank String title, @Size(max = 3) String tag) {}

    @RestController
    static class ErrorProbeController {

        @GetMapping("/probe/not-found/{kind}")
        void notFound(@PathVariable String kind) {
            switch (kind) {
                case "plain" -> throw new NotFoundException();
                case "message" -> throw new NotFoundException("남의 비공개 글 42");
                default -> throw new PostNotFoundException(7);
            }
        }

        @GetMapping("/probe/validation")
        void validation() {
            throw new ValidationException(
                    List.of(
                            new FieldError("nickname", "NICKNAME_DUPLICATE", "이미 사용 중인 닉네임이에요"),
                            new FieldError("handle", "HANDLE_RESERVED", "사용할 수 없는 주소예요.")),
                    Map.of("handleSuggestion", "admin_2"));
        }

        @PostMapping("/probe/bean-validation")
        void beanValidation(@Valid @RequestBody Body body) {}

        @GetMapping("/probe/account-state")
        void accountState() {
            throw new AccountStateException(
                    CommonReasonCode.ACCOUNT_SUSPENDED, Map.of("reason", "스팸"));
        }

        @GetMapping("/probe/business/{kind}")
        void business(@PathVariable String kind) {
            throw new BusinessRuleException(
                    "conflict".equals(kind)
                            ? TestReason.POST_CONFLICT
                            : TestReason.CANNOT_LIKE_OWN_POST);
        }

        @GetMapping("/probe/too-many")
        void tooMany() {
            throw new TooManyRequestsException(42);
        }

        @GetMapping("/probe/unavailable")
        void unavailable() {
            throw new TemporarilyUnavailableException();
        }

        @GetMapping("/probe/cursor")
        void cursor() {
            throw new InvalidCursorException("bad cursor");
        }

        @GetMapping("/probe/login-required")
        void loginRequired() {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("db password=secret at com.team.Internal");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "message", "subclass"})
    void NotFoundException은_메시지와_하위_클래스에_상관없이_같은_404_본문(String kind) throws Exception {
        mockMvc.perform(get("/probe/not-found/" + kind))
                .andExpect(status().isNotFound())
                .andExpect(
                        content()
                                .json(
                                        NOT_FOUND_BODY,
                                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void 없는_경로도_같은_404_본문() throws Exception {
        mockMvc.perform(get("/probe/없는/경로"))
                .andExpect(status().isNotFound())
                .andExpect(
                        content()
                                .json(
                                        NOT_FOUND_BODY,
                                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void ValidationException은_400_VALIDATION_FAILED와_칸_오류() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/probe/validation"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.errors.length()").value(2))
                        .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                        .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_DUPLICATE"))
                        .andExpect(jsonPath("$.errors[0].message").value("이미 사용 중인 닉네임이에요"))
                        .andExpect(jsonPath("$.errors[1].message").value("사용할 수 없는 주소예요"))
                        .andExpect(jsonPath("$.details.handleSuggestion").value("admin_2"))
                        .andReturn();
        assertFourKeys(result);
    }

    @Test
    void Bean_Validation_실패는_400_VALIDATION_FAILED() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/probe/bean-validation")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"title\":\" \",\"tag\":\"toolong\"}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.errors.length()").value(2))
                        .andExpect(
                                jsonPath("$.errors[?(@.field == 'title')].code").value("NOT_BLANK"))
                        .andExpect(jsonPath("$.errors[?(@.field == 'tag')].code").value("SIZE"))
                        .andExpect(jsonPath("$.details").doesNotExist())
                        .andReturn();
        assertFourKeys(result);
    }

    @Test
    void 읽을_수_없는_JSON은_400_MALFORMED_REQUEST() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/probe/bean-validation")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"title\":"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                        .andExpect(jsonPath("$.errors").isArray())
                        .andExpect(jsonPath("$.errors").isEmpty())
                        .andReturn();
        assertFourKeys(result);
    }

    @Test
    void AccountStateException은_403() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/probe/account-state"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                        .andExpect(jsonPath("$.message").value("정지된 계정이에요"))
                        .andExpect(jsonPath("$.details.reason").value("스팸"))
                        .andReturn();
        assertFourKeys(result);
    }

    @Test
    void BusinessRuleException은_이유_코드의_상태_코드() throws Exception {
        mockMvc.perform(get("/probe/business/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_CONFLICT"))
                .andExpect(jsonPath("$.errors").isEmpty());
        mockMvc.perform(get("/probe/business/own"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_LIKE_OWN_POST"));
    }

    @Test
    void TooManyRequestsException은_429와_Retry_After() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/probe/too-many"))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(header().string("Retry-After", "42"))
                        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                        .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요"))
                        .andReturn();
        assertFourKeys(result);
    }

    @Test
    void TemporarilyUnavailableException은_503과_Retry_After_30() throws Exception {
        mockMvc.perform(get("/probe/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요"));
    }

    @Test
    void InvalidCursorException은_400_INVALID_CURSOR() throws Exception {
        mockMvc.perform(get("/probe/cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void LOGIN_REQUIRED는_401() throws Exception {
        mockMvc.perform(get("/probe/login-required"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요해요"));
    }

    @Test
    void 그_밖의_예외는_500_INTERNAL_ERROR이고_내부_정보를_내보내지_않는다() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/probe/boom"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                        .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret", "IllegalStateException", "com.team", "at ");
        assertFourKeys(result);
    }

    /** code·message·errors·details 4개 키가 항상 있고, errors는 배열, 메시지 끝에 마침표가 없다. */
    private static void assertFourKeys(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        tools.jackson.databind.JsonNode json =
                new tools.jackson.databind.json.JsonMapper().readTree(body);
        assertThat(json.propertyNames()).containsExactly("code", "message", "errors", "details");
        assertThat(json.get("errors").isArray()).isTrue();
        assertThat(json.get("message").asString()).doesNotEndWith(".");
        for (tools.jackson.databind.JsonNode error : json.get("errors")) {
            assertThat(error.get("message").asString()).doesNotEndWith(".");
        }
    }
}
