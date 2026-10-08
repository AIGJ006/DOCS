package com.team.blog.discovery.web;

import com.team.blog.discovery.application.SitemapService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /sitemap.xml} (012 T039, openapi {@code getSitemap}). {@code
 * application/xml;charset=UTF-8}, {@code Cache-Control: no-cache}. {@code /api/**} 밖이라 001 탈퇴 유예
 * 게이트를 거치지 않는다.
 *
 * <p>(구현 메모) research R13의 {@code StreamingResponseBody} 대신 응답 스트림에 바로 흘려 쓴다 — 비동기 디스패치 없이 요청 스레드에서
 * 쓰고(같은 효과: 메모리에 전체를 모으지 않음), 시험에서 SQL 수를 같은 스레드로 셀 수 있다.
 */
@RestController
public class SitemapController {

    private final SitemapService sitemap;

    public SitemapController(SitemapService sitemap) {
        this.sitemap = sitemap;
    }

    @GetMapping("/sitemap.xml")
    public void sitemap(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/xml;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        Writer out =
                new BufferedWriter(
                        new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8),
                        16 * 1024);
        sitemap.write(out);
    }
}
