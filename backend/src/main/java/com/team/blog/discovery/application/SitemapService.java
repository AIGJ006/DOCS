package com.team.blog.discovery.application;

import com.team.blog.discovery.infra.SitemapRepository;
import com.team.blog.discovery.infra.SitemapRepository.PostEntry;
import com.team.blog.post.application.PostUrls;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 검색 엔진용 사이트 지도 (012 T039, FR-040, research R13, contracts §8). 요청 때마다 공용 노출 조건으로 만든다(캐시 없음 — 이벤트를
 * 구독할 것이 없다). 첫 화면 → 공개 글(1,000개씩) → 공개 글이 있는 블로그 순으로 흘려 쓴다. 주소는 XML 이스케이프한다.
 *
 * <p>URL이 sitemap 규약 한도 50,000개를 넘으면 앞 50,000개만 쓰고 WARN — 색인 파일 나누기는 그때 정한다(글 1만 건 규모에서는 해당 없음).
 */
@Service
public class SitemapService {

    private static final Logger log = LoggerFactory.getLogger(SitemapService.class);

    public static final int MAX_URLS = 50_000;
    static final int BATCH = 1_000;
    private static final DateTimeFormatter W3C = DateTimeFormatter.ISO_INSTANT;

    private final SitemapRepository repository;
    private final ReadingProperties properties;

    public SitemapService(SitemapRepository repository, ReadingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void write(Writer out) throws IOException {
        String base = properties.site().baseUrl();
        int[] written = {0};
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(out, base + "/", null);
        written[0]++;
        long after = 0;
        boolean truncated = false;
        while (!truncated) {
            List<PostEntry> batch = repository.posts(after, BATCH);
            for (PostEntry entry : batch) {
                if (written[0] >= MAX_URLS) {
                    truncated = true;
                    break;
                }
                url(out, base + PostUrls.of(entry.handle(), entry.id()), entry.lastmod());
                written[0]++;
            }
            if (batch.size() < BATCH) {
                break;
            }
            after = batch.get(batch.size() - 1).id();
        }
        if (!truncated) {
            boolean[] cut = {false};
            repository.forEachBlog(
                    blog -> {
                        if (written[0] >= MAX_URLS) {
                            cut[0] = true;
                            return;
                        }
                        try {
                            url(out, base + "/@" + blog.handle(), blog.lastmod());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        written[0]++;
                    });
            truncated = cut[0];
        }
        out.write("</urlset>\n");
        out.flush();
        if (truncated) {
            log.warn("sitemap URL이 {}개를 넘어 앞부분만 썼습니다", MAX_URLS);
        }
    }

    private static void url(Writer out, String loc, OffsetDateTime lastmod) throws IOException {
        out.write("  <url><loc>");
        out.write(escape(loc));
        out.write("</loc>");
        if (lastmod != null) {
            out.write("<lastmod>");
            out.write(W3C.format(lastmod.toInstant().truncatedTo(ChronoUnit.SECONDS)));
            out.write("</lastmod>");
        }
        out.write("</url>\n");
    }

    /** XML 글자 이스케이프 ({@code & < > " '}). */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
