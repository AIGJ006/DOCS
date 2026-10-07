package com.team.blog.shared.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 공통 404 화면 HTML (06 §3-1, FR-013, research R-26). 볼 수 없는 글·없는 글·없는 블로그의 화면 주소(005 {@code
 * PageShellController})가 모두 이것을 쓴다.
 *
 * <p>React 빌드 셸({@code classpath:static/index.html})의 {@code <!--app-head-->} 자리(005 셸 규약)에 고정
 * 메타(링크 미리보기 문구 "볼 수 없는 글이에요" / "친구 공개·비공개 글이거나 삭제된 글입니다." + {@code robots noindex})를 넣는다. 셸이
 * 없으면(테스트·개발) 고정 최소 HTML을 쓴다. 요청 값(주소·글 번호·이유)은 넣지 않으므로 어떤 404든 바이트가 같다. 응답은 404 + {@code
 * Cache-Control: private, no-store} + {@code text/html; charset=UTF-8}.
 */
@Component
public class NotFoundPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(NotFoundPageRenderer.class);

    static final String PLACEHOLDER = "<!--app-head-->";

    static final String HEAD =
            "<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">"
                    + "<meta property=\"og:description\" content=\"친구 공개·비공개 글이거나 삭제된 글입니다.\">"
                    + "<meta name=\"robots\" content=\"noindex\">";

    static final String FALLBACK =
            "<!doctype html><html lang=\"ko\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + HEAD
                    + "<title>볼 수 없는 페이지예요</title></head><body><main>"
                    + "<h1>볼 수 없는 페이지예요</h1><p><a href=\"/\">홈으로</a></p>"
                    + "</main></body></html>";

    private static final MediaType HTML_UTF8 =
            new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final byte[] body;

    public NotFoundPageRenderer(@Value("classpath:static/index.html") Resource shell) {
        this.body = build(shell).getBytes(StandardCharsets.UTF_8);
    }

    /** 404 응답. 매번 같은 바이트·같은 헤더. */
    public ResponseEntity<byte[]> render() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.notFound())
                .contentType(HTML_UTF8)
                .body(body.clone());
    }

    private static String build(Resource shell) {
        String html = read(shell);
        if (html == null) {
            return FALLBACK;
        }
        if (html.contains(PLACEHOLDER)) {
            return html.replace(PLACEHOLDER, HEAD);
        }
        int headEnd = html.indexOf("</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + HEAD + html.substring(headEnd);
        }
        return FALLBACK;
    }

    private static String read(Resource shell) {
        if (shell == null || !shell.exists()) {
            return null;
        }
        try (InputStream in = shell.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("React 셸을 읽지 못해 최소 404 HTML을 씁니다: {}", e.getMessage());
            return null;
        }
    }
}
