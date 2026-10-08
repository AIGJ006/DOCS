package com.team.blog.discovery.infra;

import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * sitemap 재료 (012 T039, research R13, FR-040). 노출 조건은 004 {@link VisibilityFilter}(비회원 기준) 하나만 — 친구
 * 공개·비공개·휴지통·숨김·작성자 탈퇴 유예 글은 자동으로 빠진다. 캐시하지 않는다.
 *
 * <ul>
 *   <li>글: {@code p.id > :after ORDER BY p.id LIMIT :batch}로 나눠 읽는다(메모리에 전체를 모으지 않음).
 *   <li>블로그: 공개 글이 1개 이상인 활동 회원을 {@code GROUP BY} 한 번으로 읽어 한 줄씩 넘긴다.
 * </ul>
 *
 * 008 태그 페이지는 넣지 않는다(팀 결정 — 008 T074).
 */
@Repository
public class SitemapRepository {

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public SitemapRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /** 공개 글 한 줄. {@code lastmod} = 재발행 일자 ?? 최초 공개 일자. */
    public record PostEntry(long id, String handle, OffsetDateTime lastmod) {}

    /** 블로그 한 줄. {@code lastmod} = 그 블로그 공개 글의 최신 lastmod. */
    public record BlogEntry(String handle, OffsetDateTime lastmod) {}

    /** {@code after}보다 큰 번호의 공개 글 {@code batch}개 (번호 순). */
    public List<PostEntry> posts(long after, int batch) {
        SqlCondition condition = visibilityFilter.forViewer(Viewer.anonymous(), null);
        Map<String, Object> params = new LinkedHashMap<>(condition.params());
        params.put("after", after);
        params.put("batch", batch);
        return jdbc.sql(
                        "SELECT p.id, m.handle, COALESCE(p.edited_at, p.first_public_at) AS lastmod"
                                + " FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + condition.sql()
                                + " AND p.id > :after ORDER BY p.id LIMIT :batch")
                .params(params)
                .query(
                        (rs, n) ->
                                new PostEntry(
                                        rs.getLong("id"),
                                        rs.getString("handle"),
                                        utc(rs.getObject("lastmod", OffsetDateTime.class))))
                .list();
    }

    /** 공개 글이 있는 블로그를 주소 순으로 하나씩 넘긴다 (SQL 1번). */
    public void forEachBlog(Consumer<BlogEntry> consumer) {
        SqlCondition condition = visibilityFilter.forViewer(Viewer.anonymous(), null);
        jdbc.sql(
                        "SELECT m.handle, max(COALESCE(p.edited_at, p.first_public_at)) AS lastmod"
                                + " FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + condition.sql()
                                + " GROUP BY m.handle ORDER BY m.handle")
                .params(condition.params())
                .query(
                        rs -> {
                            consumer.accept(
                                    new BlogEntry(
                                            rs.getString("handle"),
                                            utc(rs.getObject("lastmod", OffsetDateTime.class))));
                        });
    }

    private static OffsetDateTime utc(OffsetDateTime at) {
        return at == null ? null : at.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
