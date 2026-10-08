package com.team.blog.category.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.category.application.BlogCategoriesView;
import com.team.blog.category.application.CategoryQueryService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 카테고리 목록 (017 US3, contracts {@code listBlogCategories}). 주인은 001 {@link
 * MemberQueryService#findReadableBlogOwner}로 찾는다 — 없음·탈퇴 유예·익명 처리·대문자 주소는 같은 404(008 블로그 태그 줄과 같음).
 */
@RestController
public class BlogCategoryController {

    private final MemberQueryService members;
    private final CategoryQueryService queries;

    public BlogCategoryController(MemberQueryService members, CategoryQueryService queries) {
        this.members = members;
        this.queries = queries;
    }

    @GetMapping("/api/members/{handle}/categories")
    public ResponseEntity<BlogCategoriesView> blogCategories(
            @PathVariable String handle, Viewer viewer) {
        BlogOwner owner =
                members.findReadableBlogOwner(handle)
                        .orElseThrow(
                                () -> new NotFoundException("blog owner not found: " + handle));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(queries.blogTree(viewer, owner.id()));
    }
}
