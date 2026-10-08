package com.team.blog.media.application;

import com.team.blog.media.infra.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 링크 미리보기 대표 이미지(<b>원본</b>) 찾기 (005 T062, 003 T040 최종, research R15, FR-040).
 *
 * <p>51 {@code post}에는 카드용 {@code thumbnail_url}(640px 썸네일)만 있고 원본 주소 컬럼이 없다. 스키마를 바꾸지 않고(원칙 I)
 * {@code thumbnail_url}에서 저장 키를 떼어({@link ImageUrls#keyOf} — 지금 공개 주소와 옛 주소 모두) {@code
 * image.thumb_storage_key}(UNIQUE {@code uq_image_thumb_key})로 한 번 찾아 그 행의 원본 키를 <b>지금</b> 공개 주소로
 * 준다.
 *
 * <ul>
 *   <li>GIF는 원본 GIF 주소다(썸네일은 첫 장면이라 움직이지 않는다).
 *   <li>일치하는 사진이 없으면(썸네일 없는 옛 사진 — 이미 원본) {@code thumbnail_url}을 그대로 쓴다.
 *   <li>{@code thumbnail_url}이 {@code null}이면 서비스 기본 이미지({@code blog.seo.default-og-image-url}).
 *   <li>우리 저장소 주소가 아니면 조회하지 않고 그대로 쓴다.
 * </ul>
 *
 * 글 상세 화면 첫 응답에서만 부른다 — 상세 API는 대표 이미지를 주지 않는다.
 */
@Component
public class OgImageResolver {

    private final ImageUrls urls;
    private final ImageRepository images;
    private final ImageUrlResolver imageUrls;
    private final String defaultImageUrl;

    @Autowired
    public OgImageResolver(
            ImageUrls urls,
            ImageRepository images,
            ImageUrlResolver imageUrls,
            @Value("${blog.seo.default-og-image-url}") String defaultImageUrl) {
        this.urls = urls;
        this.images = images;
        this.imageUrls = imageUrls;
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
        return urls.keyOf(thumbnailUrl)
                .map(
                        key ->
                                images.findByThumbKey(key)
                                        .map(imageUrls::publicUrl)
                                        .orElse(thumbnailUrl))
                .orElse(thumbnailUrl);
    }
}
