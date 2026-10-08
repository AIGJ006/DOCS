package com.team.blog.post.infra;

import com.team.blog.post.domain.ManageCursor;
import com.team.blog.post.domain.ManageTab;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 내 글 관리 목록·탭별 글 수 (006 T041, research R18). 휴지통 글을 함께 다뤄야 해서 {@code Post} 엔티티의 삭제 제외 조건({@code
 * SQLRestriction})을 쓰지 않고 네이티브 SQL로 읽는다.
 *
 * <ul>
 *   <li>임시글·발행 글: {@code ix_post_manage (author_id, status, updated_at DESC) WHERE deleted_at IS
 *       NULL}, 정렬 {@code updated_at DESC, id DESC}
 *   <li>휴지통: {@code ix_post_trash (author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL}, 정렬
 *       {@code deleted_at DESC, id DESC}
 *   <li>글 수: 두 부분 인덱스를 각각 타도록 {@code UNION ALL} 두 갈래를 한 문장으로 보낸다
 * </ul>
 *
 * <p>{@code content_md}·{@code content_html}은 SELECT하지 않는다(FR-012). 실행할 문장은 {@link
 * #listQuery}·{@link #countQuery}가 만들고 테스트는 같은 문장으로 {@code EXPLAIN}을 본다.
 */
@Repository
public class ManagePostQueryRepository {

    /** 실행할 문장과 이름 붙은 인자. */
    public record SqlQuery(String sql, Map<String, Object> params) {}

    private static final String SELECT =
            "SELECT p.id, p.title, p.status, p.visibility, (d.post_id IS NOT NULL) AS editing,"
                    + " (p.hidden_at IS NOT NULL) AS hidden, p.updated_at, p.published_at,"
                    + " p.edited_at, p.deleted_at, p.view_count, p.like_count, p.comment_count"
                    + " FROM post p LEFT JOIN post_draft d ON d.post_id = p.id";

    private static final String COUNT =
            "SELECT p.status AS k, count(*) AS n FROM post p"
                    + " WHERE p.author_id = :me AND p.deleted_at IS NULL GROUP BY p.status"
                    + " UNION ALL"
                    + " SELECT 'TRASH' AS k, count(*) AS n FROM post p"
                    + " WHERE p.author_id = :me AND p.deleted_at IS NOT NULL";

    private static final RowMapper<ManagePostRow> ROW =
            (rs, n) ->
                    new ManagePostRow(
                            rs.getLong("id"),
                            rs.getString("title"),
                            PostStatus.valueOf(rs.getString("status")),
                            Visibility.valueOf(rs.getString("visibility")),
                            rs.getBoolean("editing"),
                            rs.getBoolean("hidden"),
                            instant(rs, "updated_at"),
                            instant(rs, "published_at"),
                            instant(rs, "edited_at"),
                            instant(rs, "deleted_at"),
                            rs.getLong("view_count"),
                            rs.getInt("like_count"),
                            rs.getInt("comment_count"));

    private final NamedParameterJdbcTemplate jdbc;

    public ManagePostQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 탭의 한 페이지를 읽는다.
     *
     * @param visibility 발행 글 탭의 공개 범위 필터 ({@code null}이면 전체). 다른 탭에서는 무시한다
     * @param after 이어 볼 위치 ({@code null}이면 첫 페이지)
     * @param limit 읽을 줄 수 (다음 페이지 판단용으로 보통 페이지 크기 + 1)
     */
    public List<ManagePostRow> list(
            long me, ManageTab tab, Visibility visibility, ManageCursor after, int limit) {
        SqlQuery query =
                listQuery(me, tab, visibility == null ? null : visibility.name(), after, limit);
        return jdbc.query(query.sql(), query.params(), ROW);
    }

    /** 탭별 글 수 (휴지통 글 제외 상태별 + 휴지통). 숨긴 글을 포함하고 공개 범위 필터와 무관하다(FR-004). */
    public Map<ManageTab, Long> count(long me) {
        SqlQuery query = countQuery(me);
        Map<ManageTab, Long> counts = new EnumMap<>(ManageTab.class);
        for (ManageTab tab : ManageTab.values()) {
            counts.put(tab, 0L);
        }
        jdbc.query(
                query.sql(),
                query.params(),
                rs -> {
                    ManageTab tab =
                            switch (rs.getString("k")) {
                                case "DRAFT" -> ManageTab.DRAFTS;
                                case "PUBLISHED" -> ManageTab.PUBLISHED;
                                case "TRASH" -> ManageTab.TRASH;
                                default -> null;
                            };
                    if (tab != null) {
                        counts.put(tab, rs.getLong("n"));
                    }
                });
        return counts;
    }

    /**
     * 목록 문장. {@code visibility}는 {@link Visibility} 이름({@code PUBLIC}·{@code PRIVATE}) 또는 {@code
     * null}이며 발행 글 탭에서만 조건이 된다.
     */
    public SqlQuery listQuery(
            long me, ManageTab tab, String visibility, ManageCursor after, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("me", me);
        params.put("limit", limit);
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE p.author_id = :me");
        String keyColumn;
        if (tab == ManageTab.TRASH) {
            sql.append(" AND p.deleted_at IS NOT NULL");
            keyColumn = "p.deleted_at";
        } else {
            sql.append(" AND p.status = :status AND p.deleted_at IS NULL");
            params.put(
                    "status",
                    (tab == ManageTab.DRAFTS ? PostStatus.DRAFT : PostStatus.PUBLISHED).name());
            if (tab == ManageTab.PUBLISHED && visibility != null) {
                sql.append(" AND p.visibility = :vis");
                params.put("vis", visibility);
            }
            keyColumn = "p.updated_at";
        }
        if (after != null) {
            sql.append(" AND (").append(keyColumn).append(", p.id) < (:t, :id)");
            params.put("t", OffsetDateTime.ofInstant(after.key(), ZoneOffset.UTC));
            params.put("id", after.id());
        }
        sql.append(" ORDER BY ").append(keyColumn).append(" DESC, p.id DESC LIMIT :limit");
        return new SqlQuery(sql.toString(), params);
    }

    /** 글 수 문장 (한 문장, research R18). */
    public SqlQuery countQuery(long me) {
        return new SqlQuery(COUNT, Map.of("me", me));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
