package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.discovery.application.BlogQueryService;
import com.team.blog.discovery.application.LinkPreviewMetaFactory;
import com.team.blog.post.application.PostQueryService;
import com.team.blog.post.application.PostUrls;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.shared.web.shell.LinkPreviewMeta;
import com.team.blog.shared.web.shell.SpaShellRenderer;
import com.team.blog.tag.application.TagQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

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
 *   <li>④ 주소의 블로그가 작성자와 다르면 바른 주소로 {@code 301}(쿼리 유지) — ③ 뒤에만 하므로 볼 수 없는 글의 작성자는 드러나지 않는다.
 *   <li>⑤ 작성자 본인의 임시글이면 {@code 302 /write/{postId}}.
 *   <li>⑥ 그 밖에는 React 셸 {@code 200} + 공개 글 미리보기 메타({@link LinkPreviewMetaFactory}). 작성자가 보는 비공개·숨김
 *       글은 공통 문구 + {@code noindex}, {@code private, no-store}.
 * </ol>
 *
 * 이 경로는 조회수를 바꾸지 않는다.
 */
@RestController
public class PageShellController {

    private static final MediaType HTML_UTF8 =
            new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final PostQueryService postQueryService;
    private final BlogQueryService blogQueryService;
    private final LinkPreviewMetaFactory metaFactory;
    private final SpaShellRenderer shell;
    private final NotFoundPageRenderer notFoundPage;
    private final TagQueryService tagQueryService;

    public PageShellController(
            PostQueryService postQueryService,
            BlogQueryService blogQueryService,
            LinkPreviewMetaFactory metaFactory,
            SpaShellRenderer shell,
            NotFoundPageRenderer notFoundPage,
            TagQueryService tagQueryService) {
        this.postQueryService = postQueryService;
        this.blogQueryService = blogQueryService;
        this.metaFactory = metaFactory;
        this.shell = shell;
        this.notFoundPage = notFoundPage;
        this.tagQueryService = tagQueryService;
    }

    /**
     * 태그 페이지 주소의 첫 응답 (008 T035, contracts/normalization.md §4, research R13): ① 주소 값 정규화가 실패하면(형식
     * 틀림·빈 값) 공통 404 화면 → ② 결과가 주소 값과 다르면 {@code 301 /tags/{encodePathSegment(결과)}}(쿼리 유지) → ③ 그
     * 밖에는 글이 없어도 200 셸. 메타에 공개 글 수를 넣지 않는다 — 빈 태그와 비공개 전용 태그의 응답이 같아야 한다(SC-005).
     */
    @GetMapping("/tags/{name}")
    public ResponseEntity<byte[]> tagShell(@PathVariable String name, HttpServletRequest request) {
        Optional<String> normalized = tagQueryService.normalizeQuery(name);
        if (normalized.isEmpty()) {
            return notFoundPage.render();
        }
        String canonical = normalized.get();
        if (!canonical.equals(name)) {
            return movedPermanently("/tags/" + tagPathSegment(canonical), request);
        }
        return html(shell.render(metaFactory.forTag(canonical)), CacheControlPolicy.NO_CACHE);
    }

    /** 전체 태그 목록 주소 (008 T051). */
    @GetMapping("/tags")
    public ResponseEntity<byte[]> tagIndexShell() {
        return html(shell.render(metaFactory.forTagIndex()), CacheControlPolicy.NO_CACHE);
    }

    /**
     * 태그 이름 → 주소 경로 한 칸. 화면 {@code tagPath.ts}와 같은 모양({@code c#} → {@code c%23}, {@code c++}는 그대로,
     * 한글은 UTF-8 퍼센트 인코딩)이 되도록 {@link UriUtils#encodePathSegment}를 쓴다.
     */
    static String tagPathSegment(String name) {
        return UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }

    /**
     * 블로그 주소의 첫 응답 (005 T050, FR-022, research R-27): ① 대문자 handle → 301 소문자(쿼리 유지) → (008) {@code
     * ?tag=} 형식 오류면 공통 404 화면, 정규화 결과와 다르면 그 값만 바꿔 301 → ② 없는 블로그·탈퇴 유예·익명 처리 → 공통 404 화면 → ③ 200 셸
     * + 블로그 미리보기 메타.
     */
    @GetMapping("/@{handle}")
    public ResponseEntity<byte[]> blogShell(
            @PathVariable String handle, HttpServletRequest request) {
        String normalized = MemberQueryService.normalizeHandle(handle);
        if (!handle.equals(normalized)) {
            return movedPermanently("/@" + normalized, request);
        }
        // 008 태그 필터 (contracts/normalization.md §4): handle 301 다음 → 형식 오류 404 화면 → 다르면 301
        String tag = request.getParameter("tag");
        if (tag != null) {
            Optional<String> canonicalTag = tagQueryService.normalizeQuery(tag);
            if (canonicalTag.isEmpty()) {
                return notFoundPage.render();
            }
            if (!canonicalTag.get().equals(tag)) {
                return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                        .header(
                                HttpHeaders.LOCATION,
                                "/@"
                                        + handle
                                        + "?"
                                        + replaceQueryValue(
                                                request.getQueryString(),
                                                "tag",
                                                queryValue(canonicalTag.get())))
                        .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                        .build();
            }
        }
        BlogOwner owner;
        try {
            owner = blogQueryService.requireOwner(handle);
        } catch (NotFoundException e) {
            return notFoundPage.render();
        }
        LinkPreviewMeta meta = metaFactory.forBlog(owner);
        // 012 블로그 안 검색 결과(?q=)는 검색 엔진 수집 금지 (FR-038, research R15)
        if (request.getParameter("q") != null) {
            meta = LinkPreviewMetaFactory.noindex(meta);
        }
        return html(shell.render(meta), CacheControlPolicy.NO_CACHE);
    }

    /** 검색 결과 화면 주소의 첫 응답 (012 T021, FR-038): 200 셸 + {@code noindex}. 검색어와 상관없이 같은 응답이다. */
    @GetMapping("/search")
    public ResponseEntity<byte[]> searchShell() {
        return html(shell.render(metaFactory.forSearch()), CacheControlPolicy.NO_CACHE);
    }

    /**
     * 팔로워·팔로잉 목록 주소의 첫 응답 (010 T036, research R9): 블로그 셸과 같은 규칙 — ① 대문자 handle → 301 소문자(쿼리 유지) → ②
     * 없는 블로그·탈퇴 유예·익명 처리 → 공통 404 화면 → ③ 200 셸 + 블로그 미리보기 메타.
     */
    @GetMapping({"/@{handle}/followers", "/@{handle}/following"})
    public ResponseEntity<byte[]> followListShell(
            @PathVariable String handle, HttpServletRequest request) {
        String normalized = MemberQueryService.normalizeHandle(handle);
        String tail = request.getRequestURI().endsWith("/following") ? "/following" : "/followers";
        if (!handle.equals(normalized)) {
            return movedPermanently("/@" + normalized + tail, request);
        }
        BlogOwner owner;
        try {
            owner = blogQueryService.requireOwner(handle);
        } catch (NotFoundException e) {
            return notFoundPage.render();
        }
        return html(shell.render(metaFactory.forBlog(owner)), CacheControlPolicy.NO_CACHE);
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
        // ④ 주소의 블로그가 작성자와 다르면 바른 주소로 (볼 수 있는 글에만 — ③ 뒤라 작성자가 드러나지 않는다)
        if (!handle.equals(row.handle())) {
            return movedPermanently(PostUrls.of(row.handle(), row.id()), request);
        }
        // ⑤ 작성자 본인의 임시글 → 에디터 (FR-026 ⑤, 40 R-4)
        if (PostQueryService.isAuthorDraft(row, viewer)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, PostQueryService.editorPath(row.id()))
                    .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                    .build();
        }
        String cacheControl =
                CacheControlPolicy.forPost(row.status(), row.visibility(), row.isHidden());
        return html(shell.render(postMeta(row)), cacheControl);
    }

    /**
     * ⑥의 메타. 공개 글은 제목·요약·대표 이미지(원본) 미리보기, 작성자가 보는 비공개·숨김 글은 공통 문구 + {@code noindex}(FR-044·045,
     * research R-18). OG 원본 조회는 이 경로에서만 한다(API는 하지 않는다).
     */
    private LinkPreviewMeta postMeta(PostDetailRow row) {
        if (row.visibility() != Visibility.PUBLIC || row.isHidden()) {
            return metaFactory.unavailable();
        }
        return metaFactory.forPublicPost(row);
    }

    /**
     * 쿼리 값 인코딩 (화면 {@code tagQueryValue}와 같은 모양). {@code +}는 쿼리에서 공백으로 읽히므로 {@code %2B}로, {@code
     * #}는 {@code %23}으로 보낸다. 정규화된 태그 이름에는 공백이 없다.
     */
    static String queryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 원래 쿼리 문자열에서 {@code name}의 첫 값만 바꾸고 나머지 값·순서는 그대로 둔다. */
    static String replaceQueryValue(String rawQuery, String name, String encodedValue) {
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String part : rawQuery.split("&", -1)) {
            if (!out.isEmpty()) {
                out.append('&');
            }
            int eq = part.indexOf('=');
            String rawName = eq < 0 ? part : part.substring(0, eq);
            if (!replaced && name.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                out.append(rawName).append('=').append(encodedValue);
                replaced = true;
            } else {
                out.append(part);
            }
        }
        return out.toString();
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
