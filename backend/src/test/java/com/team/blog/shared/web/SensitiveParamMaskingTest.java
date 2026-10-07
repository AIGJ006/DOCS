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
