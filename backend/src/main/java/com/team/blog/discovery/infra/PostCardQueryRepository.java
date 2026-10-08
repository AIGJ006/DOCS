package com.team.blog.discovery.infra;

import com.team.blog.discovery.application.CardFilter;
import com.team.blog.discovery.application.PostListCursor.CursorKey;
import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 홈·블로그 글 카드 조회 (005 T010, 10 §7, C-READ-1 #6, research R-06·R-07). 글 + 작성자 + 프로필 사진을 <b>SQL 한
 * 번</b>으로 읽고 본문 컬럼({@code content_md}·{@code content_html})은 읽지 않는다.
 *
 * <ul>
 *   <li>노출 조건은 004 {@link VisibilityFilter#forViewer(Viewer, Long)}의 조각 하나만 쓴다(별칭 {@code p}·{@code
 *       m} 계약) — 부분 인덱스 {@code ix_post_feed}·{@code ix_post_blog}의 술어가 그대로 들어간다. 작성자 본인 예외 없음(06
 *       V-8).
 *   <li>프로필 사진 JOIN 조건은 {@code uq_image_profile_current}의 술어와 같다(회원당 최대 1행).
 *   <li>커서는 행 값 비교 {@code (p.first_public_at, p.id) < (:cursorAt, :cursorId)} — 같은 마이크로초 글도 빠지지
 *       않는다.
 * </ul>
 *
 * <p>member·image JOIN은 plan Complexity Tracking의 원칙 II 예외이므로 discovery 모듈에서 그 테이블을 읽는 곳은 이 클래스뿐이다.
 * 008 태그별 목록·블로그 태그 필터의 {@code post_tag} 조건({@code EXISTS}, {@link CardFilter#tagId()})도 같은 예외다(008
 * plan Complexity Tracking) — 카드 SQL 한 번에 커서·정렬을 그대로 쓰려고 같은 문장 안에 둔다. 팔로우 피드(010)의 {@code follow}
 * 조건({@code EXISTS}, {@link CardFilter#followerId()} — PK {@code (follower_id, followee_id)})도 같은
 * 예외다(010 plan Complexity Tracking).
 */
@Repository
public class PostCardQueryRepository {

    private static final String SELECT =
            """
            SELECT p.id, p.title, p.excerpt, p.thumbnail_url, p.first_public_at,
                   p.comment_count, p.like_count, m.handle, m.nickname,
                   COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key
              FROM post p
              JOIN member m ON m.id = p.author_id
              LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
                   AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
             WHERE\s""";

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public PostCardQueryRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /**
     * @param viewer 보는 사람 (공통 규칙에서는 결과에 영향 없음)
     * @param filter 블로그 주인·태그 조건 (전체 목록이면 {@link CardFilter#all()})
     * @param after 이 위치보다 이전 글만 (첫 페이지면 {@code null})
     * @param limit 읽을 행 수 (보통 page-size + 1)
     */
    public List<PostCardRow> findCards(
            Viewer viewer, CardFilter filter, CursorKey after, int limit) {
        CardQuery query = cardQuery(viewer, filter, after, limit);
        return jdbc.sql(query.sql())
                .params(query.params())
                .query(PostCardQueryRepository::toRow)
                .list();
    }

    /**
     * 번호로 카드 읽기 (012 T005, research R2·R5·R8). 홈과 같은 SELECT + 비회원 기준 공용 조건({@link
     * VisibilityFilter#forViewer}) + {@code p.id = ANY(:ids)} — 트렌딩 스냅샷·검색 결과의 글 중 <b>지금</b> 볼 수 있는
     * 것만 돌려준다(FR-012). 순서는 보장하지 않으므로 부른 쪽이 번호 순서대로 맞춘다. 빈 목록이면 SQL 없이 빈 목록.
     */
    public List<PostCardRow> findCardsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        SqlCondition condition = visibilityFilter.forViewer(Viewer.anonymous(), null);
        Map<String, Object> params = new LinkedHashMap<>(condition.params());
        params.put("ids", ids.toArray(Long[]::new));
        return jdbc.sql(SELECT + condition.sql() + " AND p.id = ANY(:ids)")
                .params(params)
                .query(PostCardQueryRepository::toRow)
                .list();
    }

    /**
     * {@link #findCards}가 실행하는 SQL과 매개변수 (005 T072 — 인덱스 사용을 {@code EXPLAIN}으로 확인할 때 같은 문장을 쓴다).
     */
    public CardQuery cardQuery(Viewer viewer, CardFilter filter, CursorKey after, int limit) {
        SqlCondition condition = visibilityFilter.forViewer(viewer, filter.authorId());
        StringBuilder sql = new StringBuilder(SELECT).append(condition.sql());
        Map<String, Object> params = new LinkedHashMap<>(condition.params());
        if (filter.tagId() != null) {
            sql.append(
                    " AND EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id"
                            + " AND pt.tag_id = :tagId)");
            params.put("tagId", filter.tagId());
        }
        if (filter.followerId() != null) {
            sql.append(
                    " AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :followerId"
                            + " AND f.followee_id = p.author_id)");
            params.put("followerId", filter.followerId());
        }
        if (after != null) {
            sql.append(" AND (p.first_public_at, p.id) < (:cursorAt, :cursorId)");
            params.put("cursorAt", after.firstPublicAt().withOffsetSameInstant(ZoneOffset.UTC));
            params.put("cursorId", after.id());
        }
        sql.append(" ORDER BY p.first_public_at DESC, p.id DESC LIMIT :limit");
        params.put("limit", limit);
        return new CardQuery(sql.toString(), params);
    }

    /**
     * 카드 조회 문장.
     *
     * @param sql 이름 붙은 매개변수({@code :name})를 쓴 SQL
     * @param params 매개변수 값
     */
    public record CardQuery(String sql, Map<String, Object> params) {}

    private static PostCardRow toRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime at = rs.getObject("first_public_at", OffsetDateTime.class);
        return new PostCardRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("excerpt"),
                rs.getString("thumbnail_url"),
                at == null ? null : at.withOffsetSameInstant(ZoneOffset.UTC),
                rs.getInt("comment_count"),
                rs.getInt("like_count"),
                rs.getString("handle"),
                rs.getString("nickname"),
                rs.getString("profile_key"));
    }
}
