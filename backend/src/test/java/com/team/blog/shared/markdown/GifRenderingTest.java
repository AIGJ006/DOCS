package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import org.junit.jupiter.api.Test;

/**
 * 작성자 GIF 표시 (003 T070 US6, research R11, FR-039). 작성자 GIF는 정지 장면(썸네일)을 원본 링크로 감싸 내보낸다 — 화면의 {@code
 * gifPlayer}가 눌렀을 때 원본으로 바꾸고, 스크립트가 없으면 링크가 새 탭에서 원본을 연다.
 */
class GifRenderingTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();

    private static final String PLAY_TITLE = "움직이는 이미지 재생";

    private static RenderedContent render(String markdown) {
        return RENDERER.render(markdown, new ImageContext(TestRenderers.AUTHOR));
    }

    @Test
    void 작성자_GIF는_썸네일을_원본_링크로_감싼다() {
        String original = TestRenderers.url(TestRenderers.OWNED_GIF_KEY);
        String thumb = TestRenderers.url(TestRenderers.OWNED_GIF_THUMB_KEY);

        RenderedContent rendered = render("![춤추는 고양이](" + original + ")");

        assertThat(rendered.html().strip())
                .isEqualTo(
                        "<p><a rel=\"noopener noreferrer nofollow ugc\" href=\""
                                + original
                                + "\" title=\""
                                + PLAY_TITLE
                                + "\" target=\"_blank\">"
                                + "<img src=\""
                                + thumb
                                + "\" alt=\"춤추는 고양이\" loading=\"lazy\" decoding=\"async\" /></a></p>");
        assertThat(HtmlSafetyChecker.problems(rendered.html())).isEmpty();
        assertThat(rendered.ownedImageKeys()).containsExactly(TestRenderers.OWNED_GIF_KEY);
        assertThat(rendered.thumbnailUrl()).isEqualTo(thumb);
        assertThat(rendered.excerpt()).isNullOrEmpty();
    }

    @Test
    void 썸네일_확장자가_jpg여도_그_키_그대로_쓴다() {
        String html = render("![](" + TestRenderers.url(TestRenderers.OWNED_GIF_KEY) + ")").html();

        assertThat(html)
                .contains("src=\"" + TestRenderers.url(TestRenderers.OWNED_GIF_THUMB_KEY) + "\"")
                .doesNotContain("_thumb.webp")
                .contains("alt=\"\"");
    }

    @Test
    void 옛_주소로_적힌_GIF도_지금_주소로_감싼다() {
        String html =
                render("![](" + TestRenderers.legacyUrl(TestRenderers.OWNED_GIF_KEY) + ")").html();

        assertThat(html)
                .contains("href=\"" + TestRenderers.url(TestRenderers.OWNED_GIF_KEY) + "\"")
                .contains("src=\"" + TestRenderers.url(TestRenderers.OWNED_GIF_THUMB_KEY) + "\"");
    }

    @Test
    void 썸네일_없는_옛_GIF는_img_그대로() {
        String url = TestRenderers.url(TestRenderers.OWNED_GIF_NO_THUMB_KEY);

        String html = render("![옛](" + url + ")").html();

        assertThat(html.strip())
                .isEqualTo(
                        "<p><img src=\""
                                + url
                                + "\" alt=\"옛\" loading=\"lazy\" decoding=\"async\" /></p>");
    }

    @Test
    void 남의_GIF는_이미지_링크() {
        String url = TestRenderers.url(TestRenderers.OTHERS_GIF_KEY);

        String html = render("![남의 움짤](" + url + ")").html();

        assertThat(html).doesNotContain("<img").doesNotContain(PLAY_TITLE).contains("[이미지] 남의 움짤");
    }

    @Test
    void 이미_링크_안에_있는_GIF는_감싸지_않는다() {
        String original = TestRenderers.url(TestRenderers.OWNED_GIF_KEY);

        String html = render("[![](" + original + ")](https://example.com/page)").html();

        assertThat(html)
                .contains("href=\"https://example.com/page\"")
                .contains("<img src=\"" + original + "\"")
                .doesNotContain(PLAY_TITLE);
    }

    @Test
    void GIF가_아닌_작성자_사진은_그대로() {
        String html = render("![](" + TestRenderers.url(TestRenderers.OWNED_KEY) + ")").html();

        assertThat(html).doesNotContain("<a ").doesNotContain(PLAY_TITLE);
    }

    @Test
    void 렌더링_규칙_버전은_2다() {
        assertThat(RenderVersion.CURRENT).isEqualTo(2);
        assertThat(render("본문").renderVersion()).isEqualTo(2);
    }
}
