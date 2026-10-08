package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.discovery.application.BlogQueryService;
import com.team.blog.discovery.application.ReadingProperties;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.post.application.PostQueryService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.shared.web.shell.LinkPreviewMeta;
import com.team.blog.shared.web.shell.SpaShellRenderer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 주소의 첫 응답 (005 T038·T050, FR-026·FR-043, research R-25).
 *
 * <p>{@code /@{handle}/...}은 {@code SpaForwardingController}의 일반 화면 경로보다 구체적인 매핑이라 먼저 선택된다 — 서버가
 * 404 상태와 링크 미리보기 메타를 넣어야 하는 화면이다.
 *
 * <p>글 상세 주소의 처리 순서 (FR-026):
 *
 * <ol>
 *   <li>① {@code handle}에 대문자가 있으면 소문자 주소로 {@code 301}(쿼리 문자열 유지) — 볼 수 없는 글이어도 먼저 적용한다.
 *   <li>② 글 번호가 숫자가 아니면 공통 404 화면.
 *   <li>③ 상세 API와 같은 조회·판정으로 볼 수 없으면 같은 404 화면.
 *   <li>⑥ 그 밖에는 React 셸 {@code 200}.
 * </ol>
 *
 * ④(handle 불일치 301)는 US5 T064, ⑤(작성자 임시글 302)는 US4 T057에서 더한다. 이 경로는 조회수를 바꾸지 않는다.
 */
@RestController
public class PageShellController {

    private static final MediaType HTML_UTF8 =
            new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final PostQueryService postQueryService;
    private final BlogQueryService blogQueryService;
    private final ProfileImageQuery profileImages;
    private final ImageUrlResolver imageUrls;
    private final ReadingProperties properties;
    private final SpaShellRenderer shell;
    private final NotFoundPageRenderer notFoundPage;

    public PageShellController(
            PostQueryService postQueryService,
            BlogQueryService blogQueryService,
            ProfileImageQuery profileImages,
            ImageUrlResolver imageUrls,
            ReadingProperties properties,
            SpaShellRenderer shell,
            NotFoundPageRenderer notFoundPage) {
        this.postQueryService = postQueryService;
        this.blogQueryService = blogQueryService;
        this.profileImages = profileImages;
        this.imageUrls = imageUrls;
        this.properties = properties;
        this.shell = shell;
        this.notFoundPage = notFoundPage;
    }

    /**
     * 블로그 주소의 첫 응답 (005 T050, FR-022, research R-27): ① 대문자 handle → 301 소문자(쿼리 유지) → ② 없는 블로그·탈퇴
     * 유예·익명 처리 → 공통 404 화면 → ③ 200 셸 + 블로그 미리보기 메타.
     */
    @GetMapping("/@{handle}")
    public ResponseEntity<byte[]> blogShell(
            @PathVariable String handle, HttpServletRequest request) {
        String normalized = MemberQueryService.normalizeHandle(handle);
        if (!handle.equals(normalized)) {
            return movedPermanently("/@" + normalized, request);
        }
        BlogOwner owner;
        try {
            owner = blogQueryService.requireOwner(handle);
        } catch (NotFoundException e) {
            return notFoundPage.render();
        }
        return html(shell.render(blogMeta(owner)), CacheControlPolicy.NO_CACHE);
    }

    /**
     * 블로그 미리보기 메타 (40 §5): {@code <title>{닉네임} (@{handle})}, description은 소개 앞 {@code
     * blog.seo.description-length}자, canonical은 {@code blog.site.base-url}+{@code /@handle}, {@code
     * og:type=profile}, {@code og:image}는 프로필 사진 <b>원본</b>(없으면 기본 이미지).
     */
    private LinkPreviewMeta blogMeta(BlogOwner owner) {
        String title = owner.nickname() + " (@" + owner.handle() + ")";
        String description = shorten(owner.bio(), properties.seo().descriptionLength());
        String originalKey =
                profileImages.currentKeys(owner.id()).map(keys -> keys.original()).orElse(null);
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
     * 소개 → 미리보기 설명. 줄바꿈·연속 공백은 한 칸으로 모으고 앞 {@code max}자만 쓴다. 값이 없으면 {@code null}(태그를 만들지 않는다).
     *
     * <p>(구현 메모) spec은 "소개 앞 160자"만 정했다. 메타 속성 값에 줄바꿈을 그대로 넣지 않으려고 공백으로 모은다.
     */
    static String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.isEmpty()) {
            return null;
        }
        return flat.length() <= max ? flat : flat.substring(0, max);
    }

    @GetMapping("/@{handle}/posts/{postId}")
    public ResponseEntity<byte[]> postDetailShell(
            @PathVariable String handle,
            @PathVariable String postId,
            Viewer viewer,
            HttpServletRequest request) {
        String normalized = MemberQueryService.normalizeHandle(handle);
        if (!handle.equals(normalized)) {
            return movedPermanently("/@" + normalized + "/posts/" + postId, request);
        }
        PostDetailRow row;
        try {
            row = postQueryService.requireReadableRow(postId, viewer);
        } catch (PostNotFoundException e) {
            return notFoundPage.render();
        }
        return html(
                shell.render(LinkPreviewMeta.empty()),
                CacheControlPolicy.forPost(row.status(), row.visibility(), row.isHidden()));
    }

    /** 원래 쿼리 문자열을 유지한 영구 이동 (research R-32). */
    private static ResponseEntity<byte[]> movedPermanently(
            String path, HttpServletRequest request) {
        String query = request.getQueryString();
        String location = query == null || query.isEmpty() ? path : path + "?" + query;
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .build();
    }

    private static ResponseEntity<byte[]> html(String body, String cacheControl) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .contentType(HTML_UTF8)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
