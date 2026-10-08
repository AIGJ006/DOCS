package com.team.blog.tag.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.tag.application.BlogTagsView;
import com.team.blog.tag.application.TagQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 태그 줄 (008 T058, US5 #1·#4). 주인은 001 {@link MemberQueryService#findReadableBlogOwner}로 찾는다 —
 * 005 {@code BlogQueryService}를 부르면 tag → discovery 의존이 생겨 001을 직접 쓴다(같은 404 규칙: 없음·탈퇴 유예·익명 처리·대문자
 * 주소).
 */
@RestController
public class BlogTagController {

    private final MemberQueryService members;
    private final TagQueryService tagQueryService;

    public BlogTagController(MemberQueryService members, TagQueryService tagQueryService) {
        this.members = members;
        this.tagQueryService = tagQueryService;
    }

    @GetMapping("/api/members/{handle}/tags")
    public ResponseEntity<BlogTagsView> blogTags(@PathVariable String handle, Viewer viewer) {
        BlogOwner owner =
                members.findReadableBlogOwner(handle)
                        .orElseThrow(
                                () -> new NotFoundException("blog owner not found: " + handle));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(tagQueryService.blogTags(viewer, owner.id()));
    }
}
