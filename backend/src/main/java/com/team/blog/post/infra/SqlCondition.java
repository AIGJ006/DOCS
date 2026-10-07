package com.team.blog.post.infra;

import java.util.Map;

/**
 * SQL WHERE 조각과 이름 있는 파라미터 ({@link VisibilityFilter} 결과). 파라미터 맵은 바꿀 수 없다.
 *
 * @param sql 조각 (앞뒤에 {@code AND}로 이어 붙인다)
 * @param params {@code NamedParameterJdbcTemplate}·{@code JdbcClient}·JPA 네이티브 쿼리에 넣을 값
 */
public record SqlCondition(String sql, Map<String, Object> params) {

    public SqlCondition {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
