package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 민감 값 로그 가림 (FR-015, R-11). */
class SensitiveParamMaskingTest {

    @Test
    void masksQueryAndFormValues() {
        assertThat(SensitiveParamMasking.mask("/verify-email?token=AbC_d-123&x=1"))
                .isEqualTo("/verify-email?token=***&x=1");
        assertThat(SensitiveParamMasking.mask("email=a%40b.com&password=Blog%232026a&redirect=/"))
                .isEqualTo("email=a%40b.com&password=***&redirect=/");
        assertThat(
                        SensitiveParamMasking.mask(
                                "currentPassword=x&newPassword=y&newPasswordConfirm=y"))
                .isEqualTo("currentPassword=***&newPassword=***&newPasswordConfirm=***");
    }

    @Test
    void masksSearchQuery() {
        // 012 T017: 검색어 원문은 접근 로그·오류 로그에 남기지 않는다 (FR-039)
        assertThat(SensitiveParamMasking.mask("/api/search/posts?q=%ED%8A%B8%EB%9E%9C&sort=latest"))
                .isEqualTo("/api/search/posts?q=***&sort=latest");
        assertThat(SensitiveParamMasking.mask("/api/search/posts?sort=latest&q=spring+boot"))
                .isEqualTo("/api/search/posts?sort=latest&q=***");
        assertThat(SensitiveParamMasking.mask("/@kim?q=abc&tag=jpa"))
                .isEqualTo("/@kim?q=***&tag=jpa");
        // 이름이 q로 끝나는 다른 값은 그대로
        assertThat(SensitiveParamMasking.mask("seq=3&faq=1")).isEqualTo("seq=3&faq=1");
    }

    @Test
    void masksJsonValues() {
        assertThat(
                        SensitiveParamMasking.mask(
                                "{\"email\":\"a@b.com\",\"password\":\"Blog#2026a\","
                                        + "\"passwordConfirm\": \"Blog#2026a\",\"token\":\"t\\\"x\"}"))
                .isEqualTo(
                        "{\"email\":\"a@b.com\",\"password\":\"***\",\"passwordConfirm\":\"***\","
                                + "\"token\":\"***\"}");
    }

    @Test
    void masksAiSuggestValues() {
        // 013 T029: 외부 AI 키, 추천 요청의 제목·본문 (research R11)
        assertThat(SensitiveParamMasking.mask("x-goog-api-key: AIzaTestKey123"))
                .isEqualTo("x-goog-api-key: ***");
        assertThat(
                        SensitiveParamMasking.mask(
                                "[x-goog-api-key:\"AIzaTestKey123\", Accept:\"*/*\"]"))
                .isEqualTo("[x-goog-api-key: ***, Accept:\"*/*\"]");
        assertThat(
                        SensitiveParamMasking.mask(
                                "{\"title\":\"비밀 제목\",\"contentMd\":\"본문 \\\"인용\\\"\",\"refresh\":false}"))
                .isEqualTo("{\"title\":\"***\",\"contentMd\":\"***\",\"refresh\":false}");
        assertThat(SensitiveParamMasking.mask("x-goog-api-key=abc&title=hello"))
                .isEqualTo("x-goog-api-key=***&title=***");
    }

    @Test
    void leavesOtherNamesAlone() {
        assertThat(SensitiveParamMasking.mask("csrftoken=abc&mytoken=1&handle=kim"))
                .isEqualTo("csrftoken=abc&mytoken=1&handle=kim");
        assertThat(SensitiveParamMasking.mask(null)).isNull();
    }

    @Test
    void filterStoresMaskedQueryForAccessLog() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/verify-email");
        request.setQueryString("token=secret-value");
        new SensitiveParamMasking()
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(request.getAttribute(SensitiveParamMasking.MASKED_QUERY_ATTRIBUTE))
                .isEqualTo("?token=***");
    }

    @Test
    void logbackConverterMasksMessage() {
        LoggerContext context = new LoggerContext();
        LoggingEvent event =
                new LoggingEvent(
                        "fqcn",
                        context.getLogger("test"),
                        Level.WARN,
                        "요청 {} 실패",
                        null,
                        new Object[] {"/reset-password?token=abc"});
        assertThat(new MaskedMessageConverter().convert(event))
                .isEqualTo("요청 /reset-password?token=*** 실패");
    }
}
