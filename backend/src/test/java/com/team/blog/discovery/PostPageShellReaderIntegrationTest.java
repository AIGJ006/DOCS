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

/** 상세 화면 첫 응답 {@code GET /@{handle}/posts/{postId}} — ①②③⑥ (005 T029, FR-026·037·043). */
class PostPageShellReaderIntegrationTest extends IntegrationTestBase {

    /** 본문이 있는 script 태그 (인라인 스크립트). */
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
    void 공개_글_주소는_React_셸을_200으로_준다() throws Exception {
        long postId = fixture.postOf("A", "republished");

        MvcResult result = api().getAs(null, "/@kim755030/posts/{id}", postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/html");
        assertThat(body(result)).contains("<div id=\"root\">");
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        assertThat(INLINE_SCRIPT.matcher(body(result)).find()).as("인라인 스크립트").isFalse();
        assertThat(fixture.viewCount(postId)).isEqualTo(12345L);
    }

    @Test
    void 대문자_주소는_소문자_주소로_301하고_쿼리를_유지한다() throws Exception {
        long postId = fixture.postOf("A", "republished");

        MvcResult result = api().getAs(null, "/@Kim755030/posts/{id}?comment=120", postId);

        assertThat(status(result)).isEqualTo(301);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/@kim755030/posts/" + postId + "?comment=120");
    }

    @Test
    void 숫자가_아닌_글_번호는_공통_404_화면() throws Exception {
        MvcResult result = api().getAs(null, "/@kim755030/posts/abc");

        assertThat(status(result)).isEqualTo(404);
        assertThat(bytes(result)).isEqualTo(notFoundPageRenderer.render().getBody());
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 볼_수_없는_글도_같은_404_화면() throws Exception {
        for (String name : new String[] {"private1", "draft", "trashed", "hidden"}) {
            MvcResult result =
                    api().getAs(null, "/@kim755030/posts/{id}", fixture.postOf("A", name));
            assertThat(status(result)).as(name).isEqualTo(404);
            assertThat(bytes(result)).as(name).isEqualTo(notFoundPageRenderer.render().getBody());
        }
        // 탈퇴 신청 작성자의 공개 글
        MvcResult withdrawn =
                api().getAs(
                                null,
                                "/@{h}/posts/{id}",
                                PostReadingFixture.C_HANDLE,
                                fixture.postOf("C", "c1"));
        assertThat(status(withdrawn)).isEqualTo(404);
        assertThat(bytes(withdrawn)).isEqualTo(notFoundPageRenderer.render().getBody());
    }

    @Test
    void 대문자_주소_301은_볼_수_없는_글에도_먼저_적용된다() throws Exception {
        // ①이 ②③보다 먼저다 — 소문자 주소에서 404를 받는다(FR-026 ①)
        MvcResult result =
                api().getAs(null, "/@KIM755030/posts/{id}", fixture.postOf("A", "private1"));

        assertThat(status(result)).isEqualTo(301);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/@kim755030/posts/" + fixture.postOf("A", "private1"));
    }

    @Test
    void 작성자가_보는_자기_비공개_글은_200_셸() throws Exception {
        MvcResult result =
                api().getAs(
                                fixture.loginAs(mockMvc, "A"),
                                "/@kim755030/posts/{id}",
                                fixture.postOf("A", "private1"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(body(result)).contains("<div id=\"root\">");
    }

    @Test
    void 글_주소는_정적_SPA_대체_경로보다_먼저_선택된다() throws Exception {
        // SpaForwardingController가 먼저 잡으면 없는 글도 200이 된다
        MvcResult result = api().getAs(null, "/@kim755030/posts/99999999");

        assertThat(status(result)).isEqualTo(404);
    }
}
