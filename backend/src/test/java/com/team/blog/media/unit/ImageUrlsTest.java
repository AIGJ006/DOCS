package com.team.blog.media.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageUrls;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 주소 → 저장 키 판별 한 곳 (003 T008, research R10, FR-024). 002 {@code ImageReferenceResolverAdapterIT}의
 * 판별 경우를 옮겼다.
 */
class ImageUrlsTest {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String OLD_BASE = "https://old.example/";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final String THUMB =
            "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";
    private static final String OLD = "images/2025/01/11111111-2222-4333-8444-555555555555.png";

    private final ImageUrls urls = ImageUrls.of(BASE, List.of(OLD_BASE));

    @Test
    void 지금_공개_주소와_키_모양이_맞을_때만_키() {
        assertThat(urls.keyOf(BASE + "/" + KEY)).contains(KEY);
        assertThat(urls.keyOf(BASE + "/" + OLD)).contains(OLD);
        assertThat(urls.keyOf("https://other.example/blog/" + KEY)).isEmpty();
        assertThat(urls.keyOf(BASE + "/files/" + KEY)).isEmpty();
        assertThat(urls.keyOf(BASE + "/images/2026/13/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp"))
                .isEmpty();
        assertThat(urls.keyOf(BASE + "/images/2026/10/not-a-uuid.webp")).isEmpty();
        assertThat(urls.keyOf(BASE + "/" + KEY.replace(".webp", ".svg"))).isEmpty();
        assertThat(urls.keyOf(BASE + "blog/" + KEY)).isEmpty();
        assertThat(urls.keyOf(null)).isEmpty();
        assertThat(urls.keyOf("")).isEmpty();
    }

    @Test
    void 옛_주소_목록도_판별한다() {
        assertThat(urls.keyOf(OLD_BASE + KEY)).contains(KEY);
        assertThat(urls.isOurs(OLD_BASE + KEY)).isTrue();
    }

    @Test
    void 쿼리나_조각이_붙으면_우리_사진이_아니다() {
        assertThat(urls.keyOf(BASE + "/" + KEY + "?x=1")).isEmpty();
        assertThat(urls.keyOf(BASE + "/" + KEY + "#a")).isEmpty();
        assertThat(urls.isOurs(BASE + "/" + KEY + "?X-Amz-Signature=abc")).isFalse();
    }

    @Test
    void 대소문자가_다르면_아니다() {
        assertThat(urls.keyOf("HTTP://LOCALHOST:9000/blog/" + KEY)).isEmpty();
        assertThat(urls.keyOf(BASE + "/" + KEY.toUpperCase())).isEmpty();
        assertThat(urls.keyOf(BASE + "/" + KEY.replace(".webp", ".WEBP"))).isEmpty();
    }

    @Test
    void 공개_주소_끝의_슬래시는_정리한다() {
        ImageUrls slashed = ImageUrls.of(BASE + "//", List.of("https://old.example"));
        assertThat(slashed.keyOf(BASE + "/" + KEY)).contains(KEY);
        assertThat(slashed.keyOf("https://old.example/" + KEY)).contains(KEY);
        assertThat(slashed.keyOf(BASE + "//" + KEY)).isEmpty();
    }

    @Test
    void 썸네일_키도_판별한다() {
        assertThat(urls.keyOf(BASE + "/" + THUMB)).contains(THUMB);
        assertThat(ImageUrls.isThumbnailKey(THUMB)).isTrue();
        assertThat(ImageUrls.isThumbnailKey(KEY)).isFalse();
    }

    @Test
    void 정적_판별은_목록을_받는다() {
        assertThat(ImageUrls.parseKey(OLD_BASE + KEY, List.of(BASE, OLD_BASE))).contains(KEY);
        assertThat(ImageUrls.parseKey(OLD_BASE + KEY, List.of(BASE))).isEmpty();
    }
}
