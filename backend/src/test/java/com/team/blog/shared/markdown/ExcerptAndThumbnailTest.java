package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import org.junit.jupiter.api.Test;

/** 요약·썸네일 (FR-027·FR-028, research A-12·B-6). */
class ExcerptAndThumbnailTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();
    private static final ImageContext CTX = new ImageContext(TestRenderers.AUTHOR);

    private static RenderedContent render(String md) {
        return RENDERER.render(md, CTX);
    }

    @Test
    void 코드_블록_이미지_표는_빼고_제목_문단_목록의_글자만() {
        String md =
                """
                # 원인

                N+1 문제가 **생겼다**.

                ```java
                select * from post;
                ```

                ![사진](https://example.com/a.png)

                | a | b |
                |---|---|
                | 표 | 칸 |

                - 첫째
                - 둘째
                """;
        assertThat(render(md).excerpt()).isEqualTo("원인 N+1 문제가 생겼다. 첫째 둘째");
    }

    @Test
    void 인라인_코드와_링크는_글자로_직접_쓴_HTML도_글자_그대로() {
        assertThat(render("`List` 를 [공식 문서](https://spring.io)에서 <b>봤다</b>").excerpt())
                .isEqualTo("List 를 공식 문서에서 <b>봤다</b>");
    }

    @Test
    void 줄바꿈과_연속_공백은_공백_하나로() {
        assertThat(render("첫 줄\n둘째   줄  \n셋째 줄\n\n\n다음 문단").excerpt())
                .isEqualTo("첫 줄 둘째 줄 셋째 줄 다음 문단");
    }

    @Test
    void 작성자_사진과_외부_사진_링크는_요약에서_빠진다() {
        String md =
                "앞 ![작성자]("
                        + TestRenderers.url(TestRenderers.OWNED_KEY)
                        + ") ![외부](https://x.io/a.png) 뒤";
        assertThat(render(md).excerpt()).isEqualTo("앞 뒤");
    }

    @Test
    void 요약은_200자에서_단어_중간이면_그_단어_앞에서_자른다() {
        String word = "가나다라마바사"; // 7자
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 400) {
            sb.append(word).append(' ');
        }
        String excerpt = render(sb.toString()).excerpt();
        // 8자 단위(단어 7 + 공백 1): 200자째는 25번째 단어의 끝 다음 공백 → 단어 25개
        assertThat(excerpt.length()).isLessThanOrEqualTo(200);
        assertThat(excerpt).endsWith(word).doesNotEndWith(" ");
        assertThat(excerpt.split(" ")).allMatch(word::equals);

        String shifted = "xy" + sb; // 단어 경계가 두 칸 밀림 → 200자째가 단어 중간
        String cut = render(shifted).excerpt();
        assertThat(cut.length()).isLessThanOrEqualTo(200);
        assertThat(cut).endsWith(word);
        assertThat(cut.length()).isLessThan(200);
    }

    @Test
    void 공백_없는_긴_글자는_200자에서_자른다() {
        assertThat(render("가".repeat(300)).excerpt()).isEqualTo("가".repeat(200));
    }

    @Test
    void 정확히_200자면_그대로_둔다() {
        assertThat(render("나".repeat(200)).excerpt()).isEqualTo("나".repeat(200));
    }

    @Test
    void 코드만_있는_글은_빈_요약() {
        assertThat(render("```\ncode only\n```").excerpt()).isEmpty();
        assertThat(render("").excerpt()).isEmpty();
    }

    @Test
    void 썸네일은_첫_작성자_사진의_썸네일_주소() {
        String md =
                "![외부](https://x.io/a.png)\n\n![남]("
                        + TestRenderers.url(TestRenderers.OTHERS_KEY)
                        + ")\n\n![첫]("
                        + TestRenderers.legacyUrl(TestRenderers.OWNED_KEY)
                        + ")\n\n![둘]("
                        + TestRenderers.url(TestRenderers.OWNED_NO_THUMB_KEY)
                        + ")";
        RenderedContent rendered = render(md);
        assertThat(rendered.thumbnailUrl())
                .isEqualTo(TestRenderers.url(TestRenderers.OWNED_THUMB_KEY));
        assertThat(rendered.ownedImageKeys())
                .containsExactly(TestRenderers.OWNED_KEY, TestRenderers.OWNED_NO_THUMB_KEY);
    }

    @Test
    void 썸네일이_없는_옛_사진이면_원본_주소() {
        String md = "![옛](" + TestRenderers.url(TestRenderers.OWNED_NO_THUMB_KEY) + ")";
        assertThat(render(md).thumbnailUrl())
                .isEqualTo(TestRenderers.url(TestRenderers.OWNED_NO_THUMB_KEY));
    }

    @Test
    void 작성자_사진이_없으면_썸네일_없음() {
        String md =
                "![외부](https://x.io/a.png) ![남]("
                        + TestRenderers.url(TestRenderers.OTHERS_KEY)
                        + ")";
        RenderedContent rendered = render(md);
        assertThat(rendered.thumbnailUrl()).isNull();
        assertThat(rendered.ownedImageKeys()).isEmpty();
    }
}
