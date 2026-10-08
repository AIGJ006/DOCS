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
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 블로그 주소 첫 응답 {@code GET /@{handle}} (005 T045, FR-022·043, research R-27). */
class BlogPageShellIntegrationTest extends IntegrationTestBase {

    private static final Pattern INLINE_SCRIPT =
            Pattern.compile("<script[^>]*>\\s*[^<\\s]", Pattern.CASE_INSENSITIVE);

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 대문자_주소는_소문자로_301하고_쿼리를_유지한다() throws Exception {
        MvcResult result = api().getAs(null, "/@Kim755030?x=1");

        assertThat(status(result)).isEqualTo(301);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/@kim755030?x=1");
    }

    @Test
    void 없는_블로그는_공통_404_화면() throws Exception {
        // 익명 처리된 회원 (51 ck_member_deleted·ck_member_withdrawn)
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now(),"
                        + " deleted_at = now(), nickname = NULL WHERE id = ?",
                fixture.memberId("D"));

        for (String handle :
                new String[] {
                    "nobody_here", PostReadingFixture.C_HANDLE, PostReadingFixture.D_HANDLE
                }) {
            MvcResult result = api().getAs(null, "/@{h}", handle);

            assertThat(status(result)).as(handle).isEqualTo(404);
            assertThat(bytes(result)).as(handle).isEqualTo(notFoundPageRenderer.render().getBody());
            assertThat(cacheControl(result)).as(handle).isEqualTo("private, no-store");
        }
    }

    @Test
    void 블로그_첫_응답에_미리보기_메타가_들어간다() throws Exception {
        MvcResult result = api().getAs(null, "/@kim755030");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/html");
        String html = body(result);
        assertThat(html).contains("<title>김민서 (@kim755030)</title>");
        assertThat(html).contains("<meta property=\"og:type\" content=\"profile\">");
        assertThat(html)
                .contains("<link rel=\"canonical\" href=\"http://localhost:8080/@kim755030\">");
        assertThat(html)
                .contains(
                        "<meta property=\"og:image\" content=\"http://localhost:9000/blog/"
                                + PostReadingFixture.A_PROFILE_ORIGINAL_KEY
                                + "\">");
        assertThat(html).contains("스프링 백엔드를 공부합니다.");
        assertThat(html).contains("<div id=\"root\">");
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("인라인 스크립트").isFalse();
    }

    @Test
    void 소개가_없으면_description_태그가_없고_기본_og_이미지를_쓴다() throws Exception {
        MvcResult result = api().getAs(null, "/@{h}", PostReadingFixture.B_HANDLE);

        String html = body(result);
        assertThat(status(result)).isEqualTo(200);
        assertThat(html).contains("<title>나민서 (@na_ms)</title>");
        assertThat(html).doesNotContain("<meta name=\"description\"");
        assertThat(html)
                .contains(
                        "<meta property=\"og:image\""
                                + " content=\"http://localhost:8080/og-default.png\">");
    }

    @Test
    void 소개는_앞_160자만_쓰고_값은_이스케이프한다() throws Exception {
        // 소개 200자: 앞 27자가 공격 문자열, 그 뒤 "가" 140자, 161자부터 "나" 33자
        String bio = "\"><script>alert(1)</script>" + "가".repeat(133) + "나".repeat(40);
        jdbc.update("UPDATE member SET bio = ? WHERE handle = ?", bio, PostReadingFixture.A_HANDLE);

        MvcResult result = api().getAs(null, "/@kim755030");
        String html = body(result);

        assertThat(status(result)).isEqualTo(200);
        assertThat(html).contains("<title>김민서 (@kim755030)</title>");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("인라인 스크립트").isFalse();
        // description은 소개 앞 160자 → 뒤쪽 "나"는 들어가지 않는다
        assertThat(html).doesNotContain("나".repeat(10));
    }
}
