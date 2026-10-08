package com.team.blog.support.permission;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * "요청에 작성자 번호를 넣어도 무시된다" 변형용 시험 필터 (004 T045, 42 §12 #2, FR-026). {@link #armed(long, Runnable)} 동안
 * 들어온 요청에 다른 회원의 번호를 {@code authorId}·{@code memberId}·{@code ownerId}·{@code userId}로 끼워 넣는다 —
 * JSON 객체 본문에는 필드로, 모든 요청에는 쿼리 매개변수로. 행동 실행기({@link PermissionAction})를 고치지 않고 모든 쓰기 행에 같은 변형을 건다.
 *
 * <p>테스트 소스의 {@code @Profile("test") @Component}라 모든 통합 테스트 컨텍스트에 함께 등록되고(새 컨텍스트 없음), 켜지 않으면 요청을
 * 그대로 넘긴다. MockMvc는 요청을 같은 스레드에서 처리하므로 스레드 지역 값으로 켠다.
 */
@Profile("test")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OwnerFieldInjector implements Filter {

    /** 끼워 넣는 이름 (FR-026 금지 필드). */
    public static final List<String> FIELDS = List.of("authorId", "memberId", "ownerId", "userId");

    private static final ThreadLocal<Long> FOREIGN = new ThreadLocal<>();

    /** {@code foreignMemberId}를 끼워 넣는 동안 {@code body}를 실행한다. */
    public static <T> T armed(long foreignMemberId, ThrowingSupplier<T> body) throws Exception {
        FOREIGN.set(foreignMemberId);
        try {
            return body.get();
        } finally {
            FOREIGN.remove();
        }
    }

    /** 예외를 던질 수 있는 공급자. */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Long foreign = FOREIGN.get();
        if (foreign == null || !(request instanceof HttpServletRequest http)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new Injected(http, foreign), response);
    }

    /** 본문·매개변수에 다른 회원 번호를 더한 요청. */
    static final class Injected extends HttpServletRequestWrapper {

        private static final String CONTENT_LENGTH = "Content-Length";

        private final byte[] body;
        private final Map<String, String[]> parameters;

        Injected(HttpServletRequest request, long foreign) throws IOException {
            super(request);
            this.body = injectBody(request.getInputStream().readAllBytes(), foreign);
            Map<String, String[]> params = new LinkedHashMap<>(request.getParameterMap());
            for (String field : FIELDS) {
                params.put(field, new String[] {Long.toString(foreign)});
            }
            this.parameters = Collections.unmodifiableMap(params);
        }

        static byte[] injectBody(byte[] original, long foreign) {
            String text = new String(original, StandardCharsets.UTF_8);
            String trimmed = text.strip();
            if (!trimmed.startsWith("{")) {
                return original;
            }
            StringBuilder fields = new StringBuilder();
            for (String field : FIELDS) {
                fields.append('"').append(field).append("\":").append(foreign).append(',');
            }
            String rest = trimmed.substring(1).strip();
            String injected =
                    rest.startsWith("}")
                            ? "{" + fields.substring(0, fields.length() - 1) + rest
                            : "{" + fields + rest;
            return injected.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            return CONTENT_LENGTH.equalsIgnoreCase(name)
                    ? Integer.toString(body.length)
                    : super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            return CONTENT_LENGTH.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(Integer.toString(body.length)))
                    : super.getHeaders(name);
        }

        @Override
        public int getIntHeader(String name) {
            return CONTENT_LENGTH.equalsIgnoreCase(name) ? body.length : super.getIntHeader(name);
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public java.util.Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }
    }
}
