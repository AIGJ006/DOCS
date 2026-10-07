package com.team.blog.shared.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서블릿 필터·Spring Security 처리기(컨트롤러 밖)에서 공통 오류 본문을 쓴다. 컨트롤러 안에서는 예외를 던지고 {@link
 * GlobalExceptionHandler}에 맡긴다.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ReasonCode reason) throws IOException {
        write(response, reason, null);
    }

    public void write(HttpServletResponse response, ReasonCode reason, Map<String, Object> details)
            throws IOException {
        write(response, reason.status().value(), ErrorResponse.of(reason, null, null, details));
    }

    public void write(HttpServletResponse response, ApiException ex) throws IOException {
        ex.headers().forEach(response::setHeader);
        write(response, ex.status().value(), ex.toResponse());
    }

    private void write(HttpServletResponse response, int status, ErrorResponse body)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}
