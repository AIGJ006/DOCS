package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.bytes;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 글 주소 처리 순서 ④와 공개 글 링크 미리보기 메타 (005 T060, US5 #1~#5, FR-026·027·043~045, Q-9·Q-10).
 *
 * <p>테스트 설정의 {@code blog.site.base-url}은 {@code http://localhost:8080}, 기본 OG 이미지는 {@code
 * http://localhost:8080/og-default.png}, 저장소 공개 주소는 {@code http://localhost:9000/blog}다.
 */
class PageShellIntegrationTest extends IntegrationTestBase {

    private static final Pattern INLINE_SCRIPT =
            Pattern.compile("<script[^>]*>\\s*[^<\\s]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESCRIPTION =
            Pattern.compile("<meta name=\"description\" content=\"([^\"]*)\">");

    private static final String BASE = "http://localhost:8080";
    private static final String DEFAULT_OG_IMAGE = "http://localhost:8080/og-default.png";
    private static final String NOINDEX = "<meta name=\"robots\" content=\"noindex\">";

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    // ---- ④ 블로그 주소 불일치 ----

    @Test
    void 볼_수_있는_글을_다른_블로그_주소로_열면_바른_주소로_301_쿼리_유지() throws Exception {
        long postId = fixture.postOf("A", "republished");

        MvcResult result = api().getAs(null, "/@na_ms/posts/{id}?comment=120", postId);

        assertThat(status(result)).isEqualTo(301);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/@kim755030/posts/" + postId + "?comment=120");
    }

    @Test
    void 볼_수_없는_글을_다른_블로그_주소로_열면_이동하지_않고_404() throws Exception {
        long privateId = fixture.postOf("A", "private1");
        byte[] notFound = notFoundPageRenderer.render().getBody();

        for (Cookie session : new Cookie[] {null, fixture.loginAs(mockMvc, "B")}) {
            MvcResult result = api().getAs(session, "/@na_ms/posts/{id}", privateId);
            assertThat(status(result)).isEqualTo(404);
            assertThat(result.getResponse().getHeader("Location")).isNull();
            assertThat(bytes(result)).isEqualTo(notFound);
        }
        // 작성자 본인은 볼 수 있는 글이므로 바른 주소로 옮긴다
        MvcResult mine =
                api().getAs(fixture.loginAs(mockMvc, "A"), "/@na_ms/posts/{id}", privateId);
        assertThat(status(mine)).isEqualTo(301);
        assertThat(mine.getResponse().getHeader("Location"))
                .isEqualTo("/@kim755030/posts/" + privateId);
    }

    @Test
    void 없는_블로그_주소로_열어도_볼_수_있는_글이면_바른_주소로_301() throws Exception {
        long postId = fixture.postOf("A", "thumb");

        MvcResult result = api().getAs(null, "/@nobody_here/posts/{id}", postId);

        assertThat(status(result)).isEqualTo(301);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/@kim755030/posts/" + postId);
    }

    @Test
    void 글_번호에_숫자가_아닌_글자가_섞이면_404() throws Exception {
        MvcResult result = api().getAs(null, "/@kim755030/posts/12x");

        assertThat(status(result)).isEqualTo(404);
        assertThat(bytes(result)).isEqualTo(notFoundPageRenderer.render().getBody());
    }

    // ---- 공개 글 미리보기 메타 ----

    @Test
    void 공개_글_첫_응답에_제목_요약_정규_주소_대표_이미지_시각이_들어간다() throws Exception {
        long postId = fixture.postOf("A", "republished");

        MvcResult result = api().getAs(null, "/@kim755030/posts/{id}?utm_source=x", postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        String html = body(result);
        assertThat(html).contains("<title>JPA N+1 정리 - 김민서</title>");
        assertThat(html)
                .contains(
                        "<meta name=\"description\" content=\"지연 로딩으로 연관 엔티티를 조회할 때 생기는 N+1 문제를"
                                + " 정리했다\">");
        assertThat(html)
                .contains(
                        "<link rel=\"canonical\" href=\""
                                + BASE
                                + "/@kim755030/posts/"
                                + postId
                                + "\">");
        assertThat(html).doesNotContain("utm_source");
        assertThat(html).contains("<meta property=\"og:type\" content=\"article\">");
        assertThat(html).contains("<meta property=\"og:title\" content=\"JPA N+1 정리\">");
        assertThat(html)
                .contains(
                        "<meta property=\"og:description\" content=\"지연 로딩으로 연관 엔티티를 조회할 때 생기는 N+1"
                                + " 문제를 정리했다\">");
        // 대표 사진이 없는 글은 기본 이미지
        assertThat(html)
                .contains("<meta property=\"og:image\" content=\"" + DEFAULT_OG_IMAGE + "\">");
        assertThat(html)
                .contains(
                        "<meta property=\"article:published_time\""
                                + " content=\"2026-09-28T10:00:00.000001Z\">");
        assertThat(html)
                .contains(
                        "<meta property=\"article:modified_time\" content=\"2026-10-03T05:03:00Z\">");
        assertThat(html).doesNotContain(NOINDEX);
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
    }

    @Test
    void 대표_이미지는_첫_사진의_원본이고_다시_발행하지_않은_글에는_수정_시각이_없다() throws Exception {
        MvcResult result =
                api().getAs(null, "/@kim755030/posts/{id}", fixture.postOf("A", "thumb"));

        String html = body(result);
        assertThat(html)
                .contains(
                        "<meta property=\"og:image\""
                                + " content=\"http://localhost:9000/blog/images/2026/09/3f2a9c1e.png\">");
        assertThat(html).doesNotContain("_thumb.webp\">").doesNotContain("article:modified_time");
    }

    @Test
    void 요약이_없으면_설명_태그를_만들지_않는다() throws Exception {
        String html =
                body(api().getAs(null, "/@kim755030/posts/{id}", fixture.postByTitle("A12 글")));

        assertThat(html).contains("<title>A12 글 - 김민서</title>");
        assertThat(html).doesNotContain("name=\"description\"").doesNotContain("og:description");
    }

    @Test
    void 설명은_요약_앞_160자로_자른다() throws Exception {
        long postId = fixture.postOf("A", "thumb");
        // 200자: 이모지(서로게이트 쌍)를 섞어 글자 수를 코드포인트로 센다
        String excerpt = "가😀".repeat(100);
        jdbc.update("UPDATE post SET excerpt = ? WHERE id = ?", excerpt, postId);

        String html = body(api().getAs(null, "/@kim755030/posts/{id}", postId));

        Matcher description = DESCRIPTION.matcher(html);
        assertThat(description.find()).isTrue();
        String content = description.group(1);
        assertThat(content.codePointCount(0, content.length())).isEqualTo(160);
        assertThat(excerpt).startsWith(content);
    }

    @Test
    void 제목과_요약은_속성_값으로_이스케이프되어_실행되지_않는다() throws Exception {
        MvcResult result = api().getAs(null, "/@kim755030/posts/{id}", fixture.postOf("A", "xss"));

        assertThat(status(result)).isEqualTo(200);
        String html = body(result);
        assertThat(html)
                .contains(
                        "<title>제목 &quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt; - 김민서</title>");
        assertThat(html)
                .contains(
                        "<meta property=\"og:title\""
                                + " content=\"제목 &quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\">");
        assertThat(html)
                .contains(
                        "<meta name=\"description\""
                                + " content=\"요약 &quot;&gt;&lt;img src=x onerror=alert(1)&gt;\">");
        assertThat(html).doesNotContain("<script>alert").doesNotContain("<img src=x");
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("인라인 스크립트").isFalse();
    }

    @Test
    void 볼_수_없는_글과_작성자가_보는_비공개_글은_공통_문구와_noindex만() throws Exception {
        MvcResult hidden =
                api().getAs(null, "/@kim755030/posts/{id}", fixture.postOf("A", "hidden"));
        assertThat(status(hidden)).isEqualTo(404);
        assertThat(body(hidden)).contains(NOINDEX).doesNotContain("숨겨진 글");

        MvcResult mine =
                api().getAs(
                                fixture.loginAs(mockMvc, "A"),
                                "/@kim755030/posts/{id}",
                                fixture.postOf("A", "private2"));
        assertThat(status(mine)).isEqualTo(200);
        assertThat(body(mine))
                .contains("<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">")
                .contains(NOINDEX)
                .doesNotContain("비공개 글 2")
                .doesNotContain("og:image")
                .doesNotContain("article:published_time");
        assertThat(cacheControl(mine)).isEqualTo("private, no-store");
    }

    @Test
    void 어떤_응답에도_인라인_스크립트가_없다() throws Exception {
        for (String name : new String[] {"republished", "code", "thumb", "xss", "editing"}) {
            String html =
                    body(api().getAs(null, "/@kim755030/posts/{id}", fixture.postOf("A", name)));
            assertThat(INLINE_SCRIPT.matcher(html).find()).as(name).isFalse();
        }
    }
}
