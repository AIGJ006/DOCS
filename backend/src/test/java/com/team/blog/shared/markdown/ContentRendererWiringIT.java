package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.RenderedContent;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spring 조립 확인: 렌더러 Bean 하나 + 실제 media 어댑터(DB {@code image}) + 설정값의 공개 주소 (002 T031). */
class ContentRendererWiringIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final String THUMB =
            "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";

    @Autowired ContentRenderer renderer;

    @Test
    void 작성자_사진은_img_남이면_링크() {
        long author = members().member().create();
        long other = members().member().create();
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, width, height) VALUES (?, ?, ?, 'image/webp', 1000, 640, 480)",
                author,
                KEY,
                THUMB);

        String md = "# 제목\n\n![사진](" + BASE + "/" + KEY + ")";
        RenderedContent mine = renderer.render(md, new ImageContext(author));
        assertThat(mine.html())
                .contains("<h2 id=\"h-제목\">제목</h2>")
                .contains("<img src=\"" + BASE + "/" + KEY + "\" alt=\"사진\"");
        assertThat(mine.ownedImageKeys()).containsExactly(KEY);
        assertThat(mine.thumbnailUrl()).isEqualTo(BASE + "/" + THUMB);
        assertThat(mine.excerpt()).isEqualTo("제목");

        RenderedContent others = renderer.render(md, new ImageContext(other));
        assertThat(others.html()).doesNotContain("<img").contains("[이미지] 사진");
        assertThat(others.thumbnailUrl()).isNull();
    }

    @Test
    void 완료_전_사진은_내_사진이어도_링크() {
        // 003 R5: complete를 통과하지 않은 사진(width NULL)은 작성자 사진으로 보지 않는다
        long author = members().member().create();
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes) VALUES (?, ?, ?, 'image/webp', 1000)",
                author,
                KEY,
                THUMB);

        RenderedContent rendered =
                renderer.render("![사진](" + BASE + "/" + KEY + ")", new ImageContext(author));

        assertThat(rendered.html()).doesNotContain("<img").contains("[이미지] 사진");
        assertThat(rendered.ownedImageKeys()).isEmpty();
        assertThat(rendered.thumbnailUrl()).isNull();
    }
}
