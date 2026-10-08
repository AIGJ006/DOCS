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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 작성자가 여는 자기 글 화면 첫 응답 (005 T053, US4 #2·#5, US5 #5, Q-9 셋째 줄, FR-045).
 *
 * <ul>
 *   <li>⑤ 작성자 본인의 임시글 → {@code 302 /write/{id}} (남에게는 404)
 *   <li>작성자가 보는 비공개·숨김 글 → 200이지만 공통 문구 + {@code noindex}, {@code private, no-store}
 *   <li>휴지통 글은 작성자에게도 404
 * </ul>
 */
class AuthorPageShellIntegrationTest extends IntegrationTestBase {

    private static final String UNAVAILABLE_OG_TITLE =
            "<meta property=\"og:title\" content=\"볼 수 없는 글이에요\">";
    private static final String UNAVAILABLE_OG_DESCRIPTION =
            "<meta property=\"og:description\" content=\"친구 공개·비공개 글이거나 삭제된 글입니다.\">";
    private static final String NOINDEX = "<meta name=\"robots\" content=\"noindex\">";

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    private PostReadingFixture fixture;
    private Cookie author;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
        author = fixture.loginAs(mockMvc, "A");
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    private MvcResult page(Cookie session, String name) throws Exception {
        return api().getAs(session, "/@kim755030/posts/{id}", fixture.postOf("A", name));
    }

    @Test
    void 작성자의_임시글_주소는_에디터로_302() throws Exception {
        long draftId = fixture.postOf("A", "draft");

        MvcResult result = page(author, "draft");

        assertThat(status(result)).as(body(result)).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/write/" + draftId);
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 남이_임시글_주소를_열면_404() throws Exception {
        byte[] notFound = notFoundPageRenderer.render().getBody();

        for (Cookie session : new Cookie[] {null, fixture.loginAs(mockMvc, "B")}) {
            MvcResult result = page(session, "draft");
            assertThat(status(result)).isEqualTo(404);
            assertThat(bytes(result)).isEqualTo(notFound);
        }
    }

    @Test
    void 작성자가_보는_비공개_글은_공통_문구와_noindex() throws Exception {
        MvcResult result = page(author, "private1");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(body(result))
                .contains("<div id=\"root\">")
                .contains(UNAVAILABLE_OG_TITLE)
                .contains(UNAVAILABLE_OG_DESCRIPTION)
                .contains(NOINDEX)
                .doesNotContain("비공개 글 1")
                .doesNotContain("비공개 요약")
                .doesNotContain("rel=\"canonical\"");
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 작성자가_보는_숨긴_글도_공통_문구와_noindex() throws Exception {
        MvcResult result = page(author, "hidden");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(body(result))
                .contains(UNAVAILABLE_OG_TITLE)
                .contains(NOINDEX)
                .doesNotContain("숨겨진 글")
                .doesNotContain("숨김 요약");
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 작성자의_휴지통_글은_404() throws Exception {
        MvcResult result = page(author, "trashed");

        assertThat(status(result)).isEqualTo(404);
        assertThat(bytes(result)).isEqualTo(notFoundPageRenderer.render().getBody());
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 작성자가_보는_자기_공개_글은_수집_금지가_아니다() throws Exception {
        MvcResult result = page(author, "republished");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(body(result)).doesNotContain(NOINDEX);
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
    }
}
