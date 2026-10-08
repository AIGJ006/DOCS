package com.team.blog.category.infra;

import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 카테고리 저장·조회 (017 data-model §1, research R2~R4·R6·R7·R9). 상위가 없음(최상위)은 SQL에서 {@code
 * COALESCE(parent_id, 0)}과 {@code parentKey = 0}으로 비교한다 — {@code IS NOT DISTINCT FROM :null}은
 * PostgreSQL이 매개변수 형을 정하지 못한다.
 *
 * <p><b>원칙 II 예외 (017 plan Complexity Tracking)</b>: 카테고리별 글 수({@link #ownPostCounts}·{@link
 * #listedPostCounts})와 글 상세 경로({@link #pathOfPost})는 {@code post}(와 노출 조건의 {@code member})를 읽기 전용으로
 * 읽는다. 노출 조건 문구는 004 {@link VisibilityFilter} 한 곳이 준다(별칭 {@code p}·{@code m}). {@code post} 쓰기는 하지
 * 않는다.
 */
@Repository
public class CategoryRepository {

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public CategoryRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /**
     * 카테고리 한 줄.
     *
     * @param parentId 상위 (최상위면 {@code null})
     */
    public record CategoryRow(long id, Long parentId, String name, int position) {

        public boolean isTopLevel() {
            return parentId == null;
        }
    }

    /** 글 상세 경로 한 줄 (상위가 없으면 상위 칸이 {@code null}). */
    public record PathRow(long id, String name, Long parentId, String parentName) {}

    /** 같은 회원의 카테고리 쓰기를 한 줄로 세운다 (research R2). 없는 회원이면 {@code false}. */
    public boolean lockMember(long memberId) {
        return jdbc.sql("SELECT id FROM member WHERE id = :id FOR UPDATE")
                .param("id", memberId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 그 회원의 카테고리 전부 — 최상위 먼저가 아니라 {@code position, id} 순. 나무는 Service가 만든다. */
    public List<CategoryRow> findAll(long memberId) {
        return jdbc.sql(
                        "SELECT id, parent_id, name, position FROM category WHERE member_id = :m"
                                + " ORDER BY position, id")
                .param("m", memberId)
                .query(
                        (rs, n) ->
                                new CategoryRow(
                                        rs.getLong("id"),
                                        rs.getObject("parent_id", Long.class),
                                        rs.getString("name"),
                                        rs.getInt("position")))
                .list();
    }

    /** 그 회원의 카테고리 하나. 남의 것·없는 것은 비어 있다. */
    public Optional<CategoryRow> findOwned(long memberId, long id) {
        return jdbc.sql(
                        "SELECT id, parent_id, name, position FROM category"
                                + " WHERE id = :id AND member_id = :m")
                .param("id", id)
                .param("m", memberId)
                .query(
                        (rs, n) ->
                                new CategoryRow(
                                        rs.getLong("id"),
                                        rs.getObject("parent_id", Long.class),
                                        rs.getString("name"),
                                        rs.getInt("position")))
                .optional();
    }

    public int countByMember(long memberId) {
        return jdbc.sql("SELECT count(*) FROM category WHERE member_id = :m")
                .param("m", memberId)
                .query(Integer.class)
                .single();
    }

    public int countChildren(long id) {
        return jdbc.sql("SELECT count(*) FROM category WHERE parent_id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    /** 같은 상위 안에 그 키의 이름이 있는가 ({@code excludeId}는 자기 자신을 뺄 때). */
    public boolean existsSiblingName(long memberId, Long parentId, String nameKey, Long excludeId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM category WHERE member_id = :m"
                                + " AND COALESCE(parent_id, 0) = :parentKey AND name_key = :key"
                                + " AND id <> :exclude)")
                .param("m", memberId)
                .param("parentKey", parentKey(parentId))
                .param("key", nameKey)
                .param("exclude", excludeId == null ? 0L : excludeId)
                .query(Boolean.class)
                .single();
    }

    /** 같은 상위 안 번호들 ({@code position, id} 순). */
    public List<Long> siblingIds(long memberId, Long parentId) {
        return jdbc.sql(
                        "SELECT id FROM category WHERE member_id = :m"
                                + " AND COALESCE(parent_id, 0) = :parentKey ORDER BY position, id")
                .param("m", memberId)
                .param("parentKey", parentKey(parentId))
                .query(Long.class)
                .list();
    }

    /** 같은 상위의 맨 아래 자리. */
    public int nextPosition(long memberId, Long parentId) {
        return jdbc.sql(
                        "SELECT COALESCE(max(position) + 1, 0) FROM category WHERE member_id = :m"
                                + " AND COALESCE(parent_id, 0) = :parentKey")
                .param("m", memberId)
                .param("parentKey", parentKey(parentId))
                .query(Integer.class)
                .single();
    }

    public long insert(long memberId, Long parentId, String name, String nameKey, int position) {
        return jdbc.sql(
                        "INSERT INTO category (member_id, parent_id, name, name_key, position)"
                                + " VALUES (:m, CAST(:parent AS bigint), :name, :key, :position)"
                                + " RETURNING id")
                .param("m", memberId)
                .param("parent", parentId)
                .param("name", name)
                .param("key", nameKey)
                .param("position", position)
                .query(Long.class)
                .single();
    }

    public void update(long id, Long parentId, String name, String nameKey, int position) {
        jdbc.sql(
                        "UPDATE category SET parent_id = CAST(:parent AS bigint), name = :name,"
                                + " name_key = :key, position = :position,"
                                + " updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", id)
                .param("parent", parentId)
                .param("name", name)
                .param("key", nameKey)
                .param("position", position)
                .update();
    }

    public void updatePosition(long id, int position) {
        jdbc.sql(
                        "UPDATE category SET position = :position, updated_at = CURRENT_TIMESTAMP"
                                + " WHERE id = :id AND position <> :position")
                .param("id", id)
                .param("position", position)
                .update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM category WHERE id = :id").param("id", id).update();
    }

    /** 탈퇴 정리 (research R10): 하위 → 최상위 순서로 지운다. 지운 수. */
    public int deleteAllByMember(long memberId) {
        int children =
                jdbc.sql("DELETE FROM category WHERE member_id = :m AND parent_id IS NOT NULL")
                        .param("m", memberId)
                        .update();
        int roots =
                jdbc.sql("DELETE FROM category WHERE member_id = :m").param("m", memberId).update();
        return children + roots;
    }

    /** 카테고리별 내 글 수 (휴지통 제외, 상태·공개 범위 무관). 글이 없는 카테고리는 맵에 없다. SQL 1번. */
    public Map<Long, Long> ownPostCounts(long memberId) {
        return countMap(
                "SELECT p.category_id, count(*) AS n FROM post p WHERE p.author_id = :m"
                        + " AND p.deleted_at IS NULL AND p.category_id IS NOT NULL"
                        + " GROUP BY p.category_id",
                Map.of("m", memberId));
    }

    /**
     * 카테고리별 블로그 목록 노출 글 수 (research R6) — 블로그 목록과 같은 {@code forViewer(viewer, ownerId)}. 주인이 봐도 자기
     * 비공개·임시 글은 없다(06 V-8). SQL 1번.
     */
    public Map<Long, Long> listedPostCounts(Viewer viewer, long ownerId) {
        SqlCondition condition = visibilityFilter.forViewer(viewer, ownerId);
        return countMap(
                "SELECT p.category_id, count(*) AS n FROM post p"
                        + " JOIN member m ON m.id = p.author_id WHERE "
                        + condition.sql()
                        + " AND p.category_id IS NOT NULL GROUP BY p.category_id",
                condition.params());
    }

    /** 그 블로그의 카테고리면 자기 + 하위 번호, 아니면 빈 목록 (research R7). SQL 1번. */
    public List<Long> subtreeIds(long ownerId, long id) {
        return jdbc.sql(
                        "SELECT id FROM category WHERE member_id = :m"
                                + " AND (id = :id OR parent_id = :id) ORDER BY id")
                .param("m", ownerId)
                .param("id", id)
                .query(Long.class)
                .list();
    }

    /** 글의 카테고리 경로 (research R9). 분류 없음·없는 글은 비어 있다. */
    public Optional<PathRow> pathOfPost(long postId) {
        return jdbc.sql(
                        "SELECT c.id, c.name, pc.id AS parent_id, pc.name AS parent_name"
                                + " FROM post p JOIN category c ON c.id = p.category_id"
                                + " LEFT JOIN category pc ON pc.id = c.parent_id"
                                + " WHERE p.id = :postId")
                .param("postId", postId)
                .query(
                        (rs, n) ->
                                new PathRow(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getObject("parent_id", Long.class),
                                        rs.getString("parent_name")))
                .optional();
    }

    private Map<Long, Long> countMap(String sql, Map<String, Object> params) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.sql(sql)
                .params(new LinkedHashMap<>(params))
                .query(
                        rs -> {
                            counts.put(rs.getLong("category_id"), rs.getLong("n"));
                        });
        return counts;
    }

    private static long parentKey(Long parentId) {
        return parentId == null ? 0L : parentId;
    }
}
