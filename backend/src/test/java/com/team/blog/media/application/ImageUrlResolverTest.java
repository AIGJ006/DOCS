package com.team.blog.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 공개 버킷 직접 주소 = public-base-url + "/" + key (최종 규칙, 003이 소유). */
class ImageUrlResolverTest {

    @ParameterizedTest
    @CsvSource({
        "http://localhost:9000/blog,  profile/1/a.png,  http://localhost:9000/blog/profile/1/a.png",
        "http://localhost:9000/blog/, profile/1/a.png,  http://localhost:9000/blog/profile/1/a.png",
        "http://localhost:9000/blog,  /profile/1/a.png, http://localhost:9000/blog/profile/1/a.png",
        "http://localhost:9000/blog//, //post/2/b.webp, http://localhost:9000/blog/post/2/b.webp",
        "https://img.example.com,     post/2/b.webp,    https://img.example.com/post/2/b.webp",
    })
    void 공개_주소와_키를_슬래시_하나로_잇는다(String base, String key, String expected) {
        assertThat(ImageUrlResolver.of(base).publicUrl(key)).isEqualTo(expected);
    }

    @Test
    void 키가_null이면_null() {
        assertThat(ImageUrlResolver.of("http://localhost:9000/blog").publicUrl(null)).isNull();
    }

    @Test
    void 빈_키는_거부한다() {
        assertThatThrownBy(() -> ImageUrlResolver.of("http://localhost:9000/blog").publicUrl(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
