package com.team.blog.post.web;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 자동 저장 요청 크기 제한 (002 T080, FR-011, B-2). {@code PUT /api/posts/{id}/autosave}에만 적용한다.
 *
 * <ul>
 *   <li>{@code Content-Length}가 {@code blog.autosave.max-request-bytes}를 넘으면 본문을 읽지 않고 바로 413
 *       {@code PAYLOAD_TOO_LARGE}.
 *   <li>길이를 모르면(chunked) 최대치 + 1바이트까지만 읽으면서 세어 넘으면 413, 아니면 읽은 본문을 그대로 넘긴다.
 * </ul>
 *
 * 보안 필터 체인 뒤에 등록한다(CSRF 403 판정 후). 로그인하지 않은 요청은 건드리지 않아 컨트롤러가 401을 준다.
 */
public class AutosaveRequestSizeFilter extends OncePerRequestFilter {

    private static final Pattern AUTOSAVE_PATH = Pattern.compile("^/api/posts/[^/]+/autosave/?$");

    private final long maxBytes;
    private final ErrorResponseWriter errors;

    public AutosaveRequestSizeFilter(long maxBytes, ErrorResponseWriter errors) {
        this.maxBytes = maxBytes;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"PUT".equalsIgnoreCase(request.getMethod())
                || !AUTOSAVE_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        long length = request.getContentLengthLong();
        if (length > maxBytes) {
            errors.write(response, PostReasonCode.PAYLOAD_TOO_LARGE);
            return;
        }
        if (length >= 0) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = readAtMost(request.getInputStream(), maxBytes + 1);
        if (body.length > maxBytes) {
            errors.write(response, PostReasonCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static byte[] readAtMost(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while (total < limit
                && (n = in.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) != -1) {
            out.write(buffer, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    /** 이미 읽은 본문을 다시 내주는 요청. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
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
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
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

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
