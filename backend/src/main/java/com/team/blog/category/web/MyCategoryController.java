package com.team.blog.category.web;

import com.team.blog.category.application.CategoryQueryService;
import com.team.blog.category.application.CategoryService;
import com.team.blog.category.application.CategoryView;
import com.team.blog.category.application.MyCategoriesView;
import com.team.blog.category.domain.CategoryReasonCode;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.web.CacheControlPolicy;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 카테고리 관리 (017 contracts {@code listMyCategories}·{@code createCategory}·{@code
 * updateCategory}·{@code deleteCategory}·{@code reorderCategories}). 현재 사용자는 세션에서만 꺼낸다. 번호가 숫자가 아니면
 * 없는 카테고리와 같은 404다. 응답은 항상 {@code Cache-Control: private, no-store}.
 */
@RestController
public class MyCategoryController {

    private final CategoryService categoryService;
    private final CategoryQueryService queryService;

    public MyCategoryController(
            CategoryService categoryService, CategoryQueryService queryService) {
        this.categoryService = categoryService;
        this.queryService = queryService;
    }

    /**
     * @param parentId 없으면 최상위
     */
    public record CreateCategoryRequest(String name, Long parentId) {}

    /**
     * @param parentId 없으면 최상위의 순서
     */
    public record ReorderRequest(Long parentId, List<Long> ids) {}

    @GetMapping("/api/me/categories")
    public ResponseEntity<MyCategoriesView> list(@CurrentUser Long memberId) {
        return noStore(HttpStatus.OK, queryService.myTree(memberId));
    }

    @PostMapping("/api/me/categories")
    public ResponseEntity<CategoryView> create(
            @CurrentUser Long memberId, @RequestBody CreateCategoryRequest request) {
        return noStore(
                HttpStatus.CREATED,
                categoryService.create(memberId, request.name(), request.parentId()));
    }

    /** 보낸 칸만 바꾼다 — {@code parentId: null}은 최상위로, 칸이 없으면 상위 그대로. */
    @PatchMapping("/api/me/categories/{categoryId}")
    public ResponseEntity<CategoryView> update(
            @CurrentUser Long memberId,
            @PathVariable String categoryId,
            @RequestBody Map<String, Object> body) {
        Object rawName = body.get("name");
        String name = rawName == null ? null : rawName instanceof String s ? s : "";
        boolean changeParent = body.containsKey("parentId");
        Long parentId = changeParent ? toId(body.get("parentId")) : null;
        return noStore(
                HttpStatus.OK,
                categoryService.update(
                        memberId, parseId(categoryId), name, changeParent, parentId));
    }

    @DeleteMapping("/api/me/categories/{categoryId}")
    public ResponseEntity<Void> delete(
            @CurrentUser Long memberId, @PathVariable String categoryId) {
        categoryService.delete(memberId, parseId(categoryId));
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .build();
    }

    @PutMapping("/api/me/categories/order")
    public ResponseEntity<MyCategoriesView> reorder(
            @CurrentUser Long memberId, @RequestBody ReorderRequest request) {
        return noStore(
                HttpStatus.OK,
                categoryService.reorder(memberId, request.parentId(), request.ids()));
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(body);
    }

    /** {@code null}은 최상위, 정수가 아니면 내 카테고리가 아닌 것과 같은 400. */
    private static Long toId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }
        CategoryReasonCode code = CategoryReasonCode.INVALID_CATEGORY;
        throw new BusinessRuleException(
                code, code.defaultMessage(), List.of(code.fieldError("parentId")), null);
    }

    /** 숫자가 아니거나 범위 밖이면 0 — 어떤 카테고리와도 맞지 않아 404가 된다. */
    static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            return id >= 1 ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
