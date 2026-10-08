package com.team.blog.discovery.application;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.OgImageResolver;
import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.post.application.PostUrls;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.shared.web.shell.LinkPreviewMeta;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 첫 응답의 링크 미리보기·검색 엔진 메타 (005 T063, FR-044·045, 40 §5, research R-18·R-27). 값의 HTML 이스케이프는 {@code
 * SpaShellRenderer}가 한다.
 *
 * <ul>
 *   <li>{@link #forPublicPost}: {@code <title>{제목} - {닉네임}}, description = 요약 앞 {@code
 *       blog.seo.description-length}자(코드포인트), canonical = {@code blog.site.base-url} + 글 주소(쿼리 없음),
 *       {@code og:type=article}, {@code og:image} = 첫 사진 <b>원본</b>(없으면 기본 이미지, {@link
 *       OgImageResolver}), {@code article:published_time} = 최초 공개 일자, {@code article:modified_time}
 *       = 재발행 일자(있을 때만).
 *   <li>{@link #unavailable()}: 공통 문구 + {@code noindex} (볼 수 없는 글·작성자가 보는 비공개·숨김 글).
 *   <li>{@link #forBlog}: {@code <title>{닉네임} (@{handle})}, 소개 앞 160자, {@code og:type=profile}, 프로필
 *       사진 <b>원본</b>(없으면 기본 이미지).
 * </ul>
 */
@Component
public class LinkPreviewMetaFactory {

    private final ReadingProperties properties;
    private final OgImageResolver ogImages;
    private final ProfileImageQuery profileImages;
    private final ImageUrlResolver imageUrls;

    public LinkPreviewMetaFactory(
            ReadingProperties properties,
            OgImageResolver ogImages,
            ProfileImageQuery profileImages,
            ImageUrlResolver imageUrls) {
        this.properties = properties;
        this.ogImages = ogImages;
        this.profileImages = profileImages;
        this.imageUrls = imageUrls;
    }

    /** 공개 글 (볼 수 있고 {@code PUBLIC}·숨김 아님인 글에만 쓴다). OG 원본 조회 1번. */
    public LinkPreviewMeta forPublicPost(PostDetailRow row) {
        String description = shorten(row.excerpt(), properties.seo().descriptionLength());
        return new LinkPreviewMeta(
                row.title() + " - " + row.nickname(),
                description,
                properties.site().baseUrl() + PostUrls.of(row.handle(), row.id()),
                "article",
                row.title(),
                description,
                ogImages.originalImageUrl(row.thumbnailUrl()),
                iso(row.firstPublicAt()),
                iso(row.editedAt()),
                false);
    }

    /**
     * 공통 문구 + {@code noindex} (06 §3-1, FR-045). 004 {@code NotFoundPageRenderer}와 같은 문구 상수를 쓴다.
     */
    public LinkPreviewMeta unavailable() {
        return LinkPreviewMeta.unavailable();
    }

    /** 블로그 주소 (research R-27). 프로필 사진 조회 1번. */
    public LinkPreviewMeta forBlog(BlogOwner owner) {
        String title = owner.nickname() + " (@" + owner.handle() + ")";
        String description = shorten(owner.bio(), properties.seo().descriptionLength());
        String originalKey =
                profileImages.currentKeys(owner.id()).map(ProfileImageKeys::original).orElse(null);
        String image =
                originalKey == null
                        ? properties.seo().defaultOgImageUrl()
                        : imageUrls.publicUrl(originalKey);
        return new LinkPreviewMeta(
                title,
                description,
                properties.site().baseUrl() + "/@" + owner.handle(),
                "profile",
                title,
                description,
                image,
                null,
                null,
                false);
    }

    /**
     * 요약·소개 → 미리보기 설명. 줄바꿈·연속 공백은 한 칸으로 모으고 앞 {@code max}자(코드포인트 — 이모지를 반으로 자르지 않는다)만 쓴다. 값이 없으면
     * {@code null}(태그를 만들지 않는다).
     *
     * <p>(구현 메모) spec은 "앞 160자"만 정했다. 메타 속성 값에 줄바꿈을 그대로 넣지 않으려고 공백으로 모은다.
     */
    static String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.isEmpty()) {
            return null;
        }
        if (flat.codePointCount(0, flat.length()) <= max) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, max));
    }

    private static String iso(Instant value) {
        return value == null ? null : value.toString();
    }
}
