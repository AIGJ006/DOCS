package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import org.junit.jupiter.api.Test;

/**
 * 대체글 렌더링 회귀 (003 T066 US5, FR-035). 대체글이 비어도 {@code alt=""} 속성은 빠지지 않는다 — 화면 읽기 프로그램이 장식 사진으로 건너뛰게
 * 한다. 카드 썸네일 alt가 글 제목인 것은 005 {@code PostCard.test.tsx}, 프로필 사진 alt 빈 값은 001·005 화면
 * 부품(AuthorCard·SiteHeader)이 맡는다.
 */
class AltRenderingTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();

    private static String html(String markdown) {
        return RENDERER.render(markdown, new ImageContext(TestRenderers.AUTHOR)).html();
    }

    @Test
    void 대체글_없는_내_사진도_alt_빈_속성이_남는다() {
        String url = TestRenderers.url(TestRenderers.OWNED_KEY);

        String rendered = html("![](" + url + ")");

        assertThat(rendered).contains("<img src=\"" + url + "\"").contains("alt=\"\"");
    }

    @Test
    void 공백뿐인_대체글도_alt_속성이_남는다() {
        String rendered = html("![   ](" + TestRenderers.url(TestRenderers.OWNED_KEY) + ")");

        assertThat(rendered).containsPattern("<img [^>]*alt=\"\\s*\"");
    }

    @Test
    void 대체글은_이스케이프되어_그대로_나온다() {
        String rendered =
                html(
                        "![책상 위 \"노트북\" 5 < 6 & 7]("
                                + TestRenderers.url(TestRenderers.OWNED_KEY)
                                + ")");

        assertThat(rendered).contains("alt=\"책상 위 &#34;노트북&#34; 5 &lt; 6 &amp; 7\"");
    }
}
