package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.discovery.support.SearchFixtures.BulkPost;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** sitemap (012 T038, FR-040, research R13, contracts §8). */
class SitemapIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:8080";
    private long author;

    @BeforeEach
    void setUp() {
        author = members().member().handle("map_a").create();
    }

    private SearchFixtures fx() {
        return new SearchFixtures(jdbc);
    }

    private MvcResult sitemap(Cookie session) throws Exception {
        MockHttpServletRequestBuilder request = get("/sitemap.xml");
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static List<String> locs(String xml) {
        List<String> locs = new ArrayList<>();
        Matcher m = Pattern.compile("<loc>([^<]*)</loc>").matcher(xml);
        while (m.find()) {
            locs.add(m.group(1));
        }
        return locs;
    }

    @Test
    void 공개_글_블로그_첫_화면_주소만() throws Exception {
        Instant at = Instant.parse("2026-10-07T03:00:00.123456Z");
        long visible = fx().post(author).firstPublicAt(at).create();
        long edited =
                fx().post(author)
                        .firstPublicAt(at.minus(Duration.ofDays(2)))
                        .editedAt(Instant.parse("2026-10-08T01:02:03Z"))
                        .create();
        for (State state :
                List.of(State.PUBLISHED_PRIVATE, State.DRAFT, State.TRASHED, State.HIDDEN)) {
            fx().post(author).state(state).create();
        }
        long gone = members().member().handle("map_gone").create();
        long goneAuthorPost = fx().post(gone).state(State.AUTHOR_WITHDRAWN).create();
        long privateOnly = members().member().handle("map_private").create();
        fx().post(privateOnly).state(State.PUBLISHED_PRIVATE).create();
        members().member().handle("map_empty").create();

        MvcResult result = sitemap(null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/xml");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
        String xml = body(result);
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(locs(xml))
                .containsExactly(
                        BASE + "/",
                        BASE + "/@map_a/posts/" + visible,
                        BASE + "/@map_a/posts/" + edited,
                        BASE + "/@map_a");
        assertThat(xml).doesNotContain("/posts/" + goneAuthorPost).doesNotContain("map_private");
        assertThat(xml)
                .contains(
                        "<loc>"
                                + BASE
                                + "/@map_a/posts/"
                                + visible
                                + "</loc><lastmod>2026-10-07T03:00:00Z</lastmod>")
                .contains("/posts/" + edited + "</loc><lastmod>2026-10-08T01:02:03Z</lastmod>")
                .contains("/@map_a</loc><lastmod>2026-10-08T01:02:03Z</lastmod>");
        // 올바른 XML
        DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(
                        new java.io.ByteArrayInputStream(
                                result.getResponse().getContentAsByteArray()));
    }

    @Test
    void 휴지통으로_보낸_직후_다음_요청에서_빠진다() throws Exception {
        long id = fx().post(author).create();
        assertThat(body(sitemap(null))).contains("/posts/" + id);

        jdbc.update(
                "UPDATE post SET deleted_at = ? WHERE id = ?", Timestamp.from(Instant.now()), id);

        String xml = body(sitemap(null));
        assertThat(xml).doesNotContain("/posts/" + id).doesNotContain("/@map_a<");
    }

    @Test
    void 탈퇴_유예_회원의_요청도_200() throws Exception {
        long withdrawn = members().member().status("WITHDRAWN").create();
        assertThat(sitemap(TestLogin.loginAs(mockMvc, withdrawn)).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void 주소는_XML_이스케이프() {
        assertThat(
                        com.team.blog.discovery.application.SitemapServiceAccess.escape(
                                "http://a.b/?x=1&y=<2>\"'"))
                .isEqualTo("http://a.b/?x=1&amp;y=&lt;2&gt;&quot;&apos;");
    }

    @Test
    void 글_1만개_2초_이내_1000개씩_읽는다() throws Exception {
        List<BulkPost> rows = new ArrayList<>();
        Instant base = Instant.now().minus(Duration.ofDays(30));
        List<Long> authors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            authors.add(members().member().create());
        }
        for (int i = 0; i < 10_000; i++) {
            rows.add(
                    new BulkPost(
                            authors.get(i % authors.size()),
                            "글 " + i,
                            "본문 " + i,
                            base.plusSeconds(i)));
        }
        fx().bulk(rows);
        sitemap(null); // 데우기

        long started = System.nanoTime();
        MvcResult result;
        int sql;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            result = sitemap(null);
            sql = scope.count();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;

        System.out.println("sitemap 10000 posts took=" + millis + "ms sql=" + sql);
        assertThat(locs(body(result))).hasSize(1 + 10_000 + 20);
        // 글 1,000개씩 11번(마지막 빈 확인 포함) + 블로그 1번
        assertThat(sql).isBetween(11, 12);
        assertThat(millis).isLessThan(2000);
    }
}
