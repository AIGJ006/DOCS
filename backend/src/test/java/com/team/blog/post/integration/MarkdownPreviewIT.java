package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 서버 렌더러 미리보기 {@code POST /api/markdown/preview} (002 T056, FR-047, QS §4-6, B-12). */
class MarkdownPreviewIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    @Test
    void QS_4_6_기대값() throws Exception {
        long me = members().member().create();
        MvcResult result =
                api().preview(
                                TestLogin.loginAs(mockMvc, me),
                                "<script>alert(1)</script>\n\n[x](JaVaScRiPt:alert(1))"
                                        + " [s](https://spring.io)\n\n"
                                        + "![e](https://evil.example/a.png)\n\n# 제목");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        String html = read(result, "$.html");
        assertThat(html)
                .contains("&lt;script&gt;")
                .doesNotContainIgnoringCase("javascript")
                .contains("[이미지] e</a>")
                .doesNotContain("<img")
                .contains("<h2 id=\"h-제목\">제목</h2>")
                .doesNotContain("<h1");
        Matcher spring =
                Pattern.compile("<a ([^>]*href=\"https://spring\\.io\"[^>]*)>s</a>").matcher(html);
        assertThat(spring.find()).as(html).isTrue();
        assertThat(spring.group(1))
                .contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer nofollow ugc\"");
    }

    @Test
    void 비회원은_401() throws Exception {
        MvcResult result = api().preview(null, "본문");
        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void 인증_전_회원도_200() throws Exception {
        long me = members().member().emailVerified(false).create();
        MvcResult result = api().preview(TestLogin.loginAs(mockMvc, me), "**굵게**");
        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.html")).contains("<strong>굵게</strong>");
    }

    @Test
    void 일분에_61번째는_429_TOO_MANY_REQUESTS와_Retry_After() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (int i = 0; i < 60; i++) {
            assertThat(status(api().preview(session, "본문 " + i))).isEqualTo(200);
        }
        MvcResult result = api().preview(session, "본문 61");
        assertThat(status(result)).isEqualTo(429);
        assertThat((String) read(result, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After")))
                .isBetween(1, 60);
    }

    @Test
    void 인용_25단계는_400_CONTENT_TOO_COMPLEX() throws Exception {
        long me = members().member().create();
        MvcResult result = api().preview(TestLogin.loginAs(mockMvc, me), ">".repeat(25) + " 깊다");
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("CONTENT_TOO_COMPLEX");
    }

    @Test
    void 본문_100001자는_400_CONTENT_TOO_LONG() throws Exception {
        long me = members().member().create();
        MvcResult result = api().preview(TestLogin.loginAs(mockMvc, me), "a".repeat(100_001));
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("CONTENT_TOO_LONG");
    }

    @Test
    void 사진_판별_기준은_로그인한_본인() throws Exception {
        long owner = members().member().create();
        long other = members().member().create();
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height)"
                        + " VALUES (?, ?, 'image/webp', 1000, 640, 480)",
                owner,
                KEY);
        String md = "![사진](" + BASE + "/" + KEY + ")";

        String mine = read(api().preview(TestLogin.loginAs(mockMvc, owner), md), "$.html");
        String others = read(api().preview(TestLogin.loginAs(mockMvc, other), md), "$.html");

        assertThat(mine).contains("<img src=\"" + BASE + "/" + KEY + "\"");
        assertThat(others).doesNotContain("<img").contains("[이미지] 사진");
    }
}
