package com.team.blog.post.domain;

import java.util.Map;

/**
 * 공개 범위 규칙 하나가 목록 조건에 더하는 조각 (06 §7 R-3). 별칭은 {@code p}=post, {@code m}=member(작성자)이며 {@code
 * VisibilityFilter}가 공통 조건과 합친다. {@link #excluded()}이면 그 공개 범위 글은 목록에 넣지 않는다.
 *
 * @param sql SQL 조각 (예: {@code p.visibility = 'PUBLIC'}). 제외면 {@code null}
 * @param params 이름 있는 파라미터
 */
public record ListCondition(String sql, Map<String, Object> params) {

    private static final ListCondition EXCLUDED = new ListCondition(null, Map.of());

    public ListCondition {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** 파라미터 없는 조각. */
    public static ListCondition of(String sql) {
        return new ListCondition(sql, Map.of());
    }

    /** 목록에 넣지 않음. */
    public static ListCondition excluded() {
        return EXCLUDED;
    }

    public boolean isExcluded() {
        return sql == null;
    }
}
