package com.team.blog.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.OgImageResolver;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 링크 미리보기 대표 이미지(원본) 찾기 (005 T061, research R-26). 테스트 설정의 저장소 공개 주소는 {@code
 * http://localhost:9000/blog}, 기본 이미지는 {@code http://localhost:8080/og-default.png}다.
 */
class OgImageResolverIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog/";

    @Autowired private OgImageResolver resolver;

    @BeforeEach
    void image() {
        long uploader = members().member().create();
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, thumb_size_bytes, width, height, status, purpose)"
                        + " VALUES (?, 'images/2026/10/abcd.gif', 'images/2026/10/abcd_thumb.webp',"
                        + " 'image/gif', 300000, 30000, 1200, 800, 'ATTACHED', 'POST')",
                uploader);
    }

    @Test
    void 썸네일_키와_같은_사진의_원본_주소를_준다() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(resolver.originalImageUrl(BASE + "images/2026/10/abcd_thumb.webp"))
                    .isEqualTo(BASE + "images/2026/10/abcd.gif");
            assertThat(scope.count()).as("uq_image_thumb_key 조회 1번").isEqualTo(1);
        }
    }

    @Test
    void 일치하는_사진이_없으면_썸네일_주소를_그대로_쓴다() {
        // 썸네일 없는 옛 사진 — thumbnail_url이 이미 원본이다
        assertThat(resolver.originalImageUrl(BASE + "images/2026/10/old.png"))
                .isEqualTo(BASE + "images/2026/10/old.png");
    }

    @Test
    void 대표_사진이_없으면_기본_이미지() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(resolver.originalImageUrl(null))
                    .isEqualTo("http://localhost:8080/og-default.png");
            assertThat(scope.count()).isZero();
        }
    }

    @Test
    void 저장소_주소가_아니면_조회하지_않고_그대로_쓴다() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(resolver.originalImageUrl("https://cdn.example.com/a_thumb.webp"))
                    .isEqualTo("https://cdn.example.com/a_thumb.webp");
            assertThat(scope.count()).isZero();
        }
    }
}
