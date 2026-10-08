package com.team.blog.media.application;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 링크 미리보기 대표 이미지(<b>원본</b>) 찾기 (005 T062, research R-26, 40 §5·10 §6). <b>임시 구현 — 003(사진 업로드)에서
 * 교체한다.</b>
 *
 * <p>51 {@code post}에는 카드용 {@code thumbnail_url}(640px 썸네일)만 있고 원본 주소 컬럼이 없다. 스키마를 바꾸지 않고(원칙 I)
 * {@code thumbnail_url}에서 저장 키를 떼어 {@code image.thumb_storage_key}(UNIQUE {@code
 * uq_image_thumb_key})로 한 번 찾아 그 행의 {@code storage_key} 주소를 준다.
 *
 * <ul>
 *   <li>일치하는 사진이 없으면(썸네일 없는 옛 사진 — 이미 원본) {@code thumbnail_url}을 그대로 쓴다.
 *   <li>{@code thumbnail_url}이 {@code null}이면 서비스 기본 이미지({@code blog.seo.default-og-image-url}).
 *   <li>저장소 공개 주소({@code blog.image.public-base-url})로 시작하지 않는 주소는 조회하지 않고 그대로 쓴다.
 * </ul>
 *
 * 글 상세 화면 첫 응답에서만 부른다 — 상세 API는 대표 이미지를 주지 않는다.
 */
@Component
public class OgImageResolver {

    private final JdbcClient jdbc;
    private final ImageUrlResolver imageUrls;
    private final String keyPrefix;
    private final String defaultImageUrl;

    public OgImageResolver(
            JdbcClient jdbc,
            ImageUrlResolver imageUrls,
            @Value("${blog.seo.default-og-image-url}") String defaultImageUrl) {
        this.jdbc = jdbc;
        this.imageUrls = imageUrls;
        // 공개 주소 접두어 "{public-base-url}/" — 주소 규칙은 ImageUrlResolver 하나만 따른다
        String probe = imageUrls.publicUrl("k");
        this.keyPrefix = probe.substring(0, probe.length() - 1);
        this.defaultImageUrl = defaultImageUrl;
    }

    /**
     * @param thumbnailUrl {@code post.thumbnail_url} ({@code null} 가능)
     * @return 원본 이미지 절대 주소 (없으면 기본 이미지)
     */
    public String originalImageUrl(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return defaultImageUrl;
        }
        if (!thumbnailUrl.startsWith(keyPrefix) || thumbnailUrl.length() == keyPrefix.length()) {
            return thumbnailUrl;
        }
        String thumbKey = thumbnailUrl.substring(keyPrefix.length());
        Optional<String> original =
                jdbc.sql("SELECT storage_key FROM image WHERE thumb_storage_key = :key")
                        .param("key", thumbKey)
                        .query(String.class)
                        .optional();
        return original.map(imageUrls::publicUrl).orElse(thumbnailUrl);
    }
}
