package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.TrashablePost;
import com.team.blog.post.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 휴지통 글을 포함하는 조회·잠금·삭제 (006 T010, research R4, 13 §2-6). {@link com.team.blog.post.domain.Post}
 * 엔티티의 {@code @SQLRestriction}을 우회해야 하는 곳은 모두 이 네이티브 SQL만 쓴다. 시각은 마이크로초 {@link Instant}로 다룬다.
 *
 * <p>잠금 조회({@code FOR UPDATE})는 호출한 쪽 트랜잭션 안에서 불러야 한다.
 */
@Repository
public class TrashPostRepository {

    private static final String LOCK_SELECT =
            "SELECT id, author_id, status, visibility, title, content_md, deleted_at, edit_version"
                    + " FROM post";

    private static final RowMapper<TrashablePost> ROW =
            (rs, n) ->
                    new TrashablePost(
                            rs.getLong("id"),
                            rs.getLong("author_id"),
                            PostStatus.valueOf(rs.getString("status")),
                            Visibility.valueOf(rs.getString("visibility")),
                            rs.getString("title"),
                            rs.getString("content_md"),
                            instant(rs, "deleted_at"),
                            rs.getLong("edit_version"));

    private final NamedParameterJdbcTemplate jdbc;

    public TrashPostRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 글(휴지통 포함)을 잠근다. 남의 글·없는 글이면 빈 값 — 호출한 쪽이 같은 404로 응답한다. */
    public Optional<TrashablePost> lockOwned(long postId, long me) {
        return jdbc
                .query(
                        LOCK_SELECT + " WHERE id = :postId AND author_id = :me FOR UPDATE",
                        Map.of("postId", postId, "me", me),
                        ROW)
                .stream()
                .findFirst();
    }

    /** 작성자 조건 없이 잠근다 (휴지통 비우기 배치의 재확인용). */
    public Optional<TrashablePost> lockById(long postId) {
        return jdbc
                .query(
                        LOCK_SELECT + " WHERE id = :postId FOR UPDATE",
                        Map.of("postId", postId),
                        ROW)
                .stream()
                .findFirst();
    }

    /** 휴지통으로 옮긴다. {@code deleted_at}만 바꾸고 {@code updated_at} 등은 그대로 둔다(research R2·R3). */
    public void markTrashed(long id, Instant now) {
        jdbc.update(
                "UPDATE post SET deleted_at = :now WHERE id = :id",
                Map.of("now", odt(now), "id", id));
    }

    /** 휴지통에서 꺼낸다. {@code deleted_at}만 비운다(research R3). */
    public void clearTrashed(long id) {
        jdbc.update("UPDATE post SET deleted_at = NULL WHERE id = :id", Map.of("id", id));
    }

    /** 행을 지운다. 딸린 행은 FK {@code ON DELETE CASCADE}·{@code SET NULL}이 처리한다(data-model §1-3). */
    public int deleteById(long id) {
        return jdbc.update("DELETE FROM post WHERE id = :id", Map.of("id", id));
    }

    /** {@code cutoff} 전에 휴지통에 들어간 글 번호 (오래된 순, 최대 {@code limit}개). */
    public List<Long> findExpiredIds(Instant cutoff, int limit) {
        return findExpiredIds(cutoff, limit, List.of());
    }

    /**
     * {@link #findExpiredIds(Instant, int)}에서 {@code excluded} 글을 뺀다 — 휴지통 비우기 배치가 같은 실행에서 실패한 글을
     * 다시 고르지 않게 한다(T062).
     */
    public List<Long> findExpiredIds(Instant cutoff, int limit, Collection<Long> excluded) {
        Map<String, Object> params = new HashMap<>();
        params.put("cutoff", odt(cutoff));
        params.put("limit", limit);
        String exclude = "";
        if (!excluded.isEmpty()) {
            exclude = " AND id NOT IN (:excluded)";
            params.put("excluded", List.copyOf(excluded));
        }
        return jdbc.queryForList(
                "SELECT id FROM post WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff"
                        + exclude
                        + " ORDER BY deleted_at, id LIMIT :limit",
                params,
                Long.class);
    }

    /** 그 회원의 글 번호 전부 (휴지통 포함, 015 탈퇴 정리용 — research R25). */
    public List<Long> findIdsByAuthorIncludingTrashed(long authorId) {
        return jdbc.queryForList(
                "SELECT id FROM post WHERE author_id = :authorId ORDER BY id",
                Map.of("authorId", authorId),
                Long.class);
    }

    private static OffsetDateTime odt(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
