package com.team.blog.category.application;

import com.team.blog.category.infra.CategoryRepository;
import com.team.blog.category.infra.CategoryRepository.CategoryRow;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.security.Viewer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 읽기 (017 research R6·R7). 나무는 카테고리 SQL 1번 + 글 수 SQL 1번으로 만든다 — 카테고리 수와 무관(SC-004). */
@Service
@Transactional(readOnly = true)
public class CategoryQueryService {

    private final CategoryRepository categories;
    private final CategoryProperties properties;
    private final PostQueryRepository postQueries;

    public CategoryQueryService(
            CategoryRepository categories,
            CategoryProperties properties,
            PostQueryRepository postQueries) {
        this.categories = categories;
        this.properties = properties;
        this.postQueries = postQueries;
    }

    /** 내 카테고리 나무 + 내 글 수(휴지통 제외, 상태 무관) (FR-007). */
    public MyCategoriesView myTree(long memberId) {
        return new MyCategoriesView(
                properties.maxCount(),
                tree(categories.findAll(memberId), categories.ownPostCounts(memberId)));
    }

    /**
     * 블로그 카테고리 목록 (FR-028·FR-029): 카테고리 1번 + 카테고리별 노출 글 수 1번 + 전체 글 수 1번(005 머리말과 같은 {@code
     * countListedByAuthor}). 글 0인 카테고리도 있다. 카테고리가 없으면 글 수 SQL을 부르지 않는다.
     */
    public BlogCategoriesView blogTree(Viewer viewer, long ownerId) {
        List<CategoryRow> rows = categories.findAll(ownerId);
        if (rows.isEmpty()) {
            return new BlogCategoriesView(0, List.of());
        }
        return new BlogCategoriesView(
                postQueries.countListedByAuthor(viewer, ownerId),
                tree(rows, categories.listedPostCounts(viewer, ownerId)));
    }

    /** 블로그 카테고리 필터 값 → 자기 + 하위 번호 (FR-031·FR-032). 형식 오류(숫자 아님·1 미만)·없는 번호·다른 블로그의 카테고리는 빈 값이다. */
    public Optional<List<Long>> findSubtreeIds(long ownerId, String rawCategoryId) {
        Long id = parseId(rawCategoryId);
        if (id == null) {
            return Optional.empty();
        }
        List<Long> ids = categories.subtreeIds(ownerId, id);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
    }

    static Long parseId(String raw) {
        if (raw == null
                || raw.isEmpty()
                || raw.length() > 18
                || !raw.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        long id = Long.parseLong(raw);
        return id >= 1 ? id : null;
    }

    /** 최상위 순서대로, 각 아래에 하위 순서대로. 최상위 글 수 = 자기 + 하위 합. */
    static List<CategoryNode> tree(List<CategoryRow> rows, Map<Long, Long> counts) {
        Map<Long, List<CategoryRow>> childrenOf = new LinkedHashMap<>();
        for (CategoryRow row : rows) {
            if (!row.isTopLevel()) {
                childrenOf.computeIfAbsent(row.parentId(), k -> new ArrayList<>()).add(row);
            }
        }
        List<CategoryNode> nodes = new ArrayList<>();
        for (CategoryRow row : rows) {
            if (!row.isTopLevel()) {
                continue;
            }
            List<CategoryNode> children = new ArrayList<>();
            long sum = counts.getOrDefault(row.id(), 0L);
            for (CategoryRow child : childrenOf.getOrDefault(row.id(), List.of())) {
                long n = counts.getOrDefault(child.id(), 0L);
                sum += n;
                children.add(new CategoryNode(child.id(), child.name(), n, List.of()));
            }
            nodes.add(new CategoryNode(row.id(), row.name(), sum, List.copyOf(children)));
        }
        return List.copyOf(nodes);
    }
}
