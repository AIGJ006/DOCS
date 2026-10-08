package com.team.blog.media.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageUrls;
import com.team.blog.media.domain.ImageFormat;
import com.team.blog.media.domain.StorageKeys;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 저장 키·형식 매핑 (003 T007, research R7, FR-009). */
class StorageKeysTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static StorageKeys at(String instant) {
        return new StorageKeys(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), SEOUL);
    }

    @Test
    void 키_모양은_images_연_월_uuid_확장자이고_썸네일은_같은_uuid() {
        StorageKeys.Pair pair =
                at("2026-10-08T05:00:00Z").newPair(ImageFormat.WEBP, ImageFormat.WEBP);

        assertThat(pair.original())
                .matches(
                        "images/2026/10/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.webp");
        String uuid =
                pair.original().substring("images/2026/10/".length(), pair.original().length() - 5);
        assertThat(pair.thumb()).isEqualTo("images/2026/10/" + uuid + "_thumb.webp");
        assertThat(ImageUrls.isStorageKey(pair.original())).isTrue();
        assertThat(ImageUrls.isStorageKey(pair.thumb())).isTrue();
    }

    @Test
    void 날짜는_서비스_시간대_기준이다() {
        // UTC 10월 31일 15:00 = 서울 11월 1일 0시
        assertThat(at("2026-10-31T15:00:00Z").newPair(ImageFormat.JPEG, null).original())
                .startsWith("images/2026/11/");
        assertThat(at("2026-10-31T14:59:59Z").newPair(ImageFormat.JPEG, null).original())
                .startsWith("images/2026/10/");
    }

    @Test
    void 썸네일_형식은_따로_따르고_없으면_썸네일_키도_없다() {
        StorageKeys keys = at("2026-10-08T05:00:00Z");
        StorageKeys.Pair gif = keys.newPair(ImageFormat.GIF, ImageFormat.JPEG);
        assertThat(gif.original()).endsWith(".gif");
        assertThat(gif.thumb()).endsWith("_thumb.jpg");

        StorageKeys.Pair profile = keys.newPair(ImageFormat.WEBP, null);
        assertThat(profile.thumb()).isNull();
    }

    @Test
    void 매번_다른_uuid() {
        StorageKeys keys = at("2026-10-08T05:00:00Z");
        assertThat(keys.newPair(ImageFormat.PNG, null).original())
                .isNotEqualTo(keys.newPair(ImageFormat.PNG, null).original());
    }

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, JPEG, jpg",
        "image/png, PNG, png",
        "image/gif, GIF, gif",
        "image/webp, WEBP, webp"
    })
    void MIME에서_확장자로(String mime, ImageFormat format, String ext) {
        assertThat(ImageFormat.ofMimeType(mime)).contains(format);
        assertThat(format.extension()).isEqualTo(ext);
        assertThat(format.mimeType()).isEqualTo(mime);
    }

    @Test
    void 받지_않는_MIME() {
        assertThat(ImageFormat.ofMimeType("image/svg+xml")).isEqualTo(Optional.empty());
        assertThat(ImageFormat.ofMimeType("IMAGE/JPEG")).isEmpty();
        assertThat(ImageFormat.ofMimeType(null)).isEmpty();
    }

    @Test
    void 원래_파일_이름이_들어갈_자리가_없다() {
        // 키를 만드는 공개 메서드는 형식만 받는다(문자열 인자가 없다)
        for (Method method : StorageKeys.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(Arrays.asList(method.getParameterTypes())).doesNotContain(String.class);
            }
        }
    }
}
