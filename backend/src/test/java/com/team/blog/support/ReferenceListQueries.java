package com.team.blog.support;

import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 공용 목록 조건({@link VisibilityFilter})으로 만든 대표 쿼리 (004 T040, 06 R-2·R-2b, research R-04·R-27). 홈·블로그
 * 목록·블로그 글 수와, 태그·검색·sitemap이 함께 쓸 "공용 조건만 건 목록"을 실제 SQL로 실행한다. 005 화면 API(카드 조회)와 별개로 판정 장치 자체가 새지
 * 않음을 확인하는 기준이며, {@code ListIndexUsageIT}가 같은 문장을 {@code EXPLAIN}한다.
 */
public final class ReferenceListQueries {

    /** 홈 대표 쿼리 페이지 크기. */
    public static final int PAGE = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final VisibilityFilter filter;

    public ReferenceListQueries(JdbcTemplate jdbc, VisibilityFilter filter) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.filter = filter;
    }

    /** 홈 대표 쿼리 문장 ({@code ORDER BY p.first_public_at DESC, p.id DESC LIMIT 10}). */
    public Statement homeStatement(Viewer viewer) {
        SqlCondition c = filter.forViewer(viewer, null);
        return new Statement(
                "SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE "
                        + c.sql()
                        + " ORDER BY p.first_public_at DESC, p.id DESC LIMIT "
                        + PAGE,
                c.params());
    }

    /** 블로그 대표 쿼리 문장 (블로그 주인 조건 추가, 같은 정렬). */
    public Statement blogStatement(Viewer viewer, long authorId) {
        SqlCondition c = filter.forViewer(viewer, authorId);
        return new Statement(
                "SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE "
                        + c.sql()
                        + " ORDER BY p.first_public_at DESC, p.id DESC LIMIT "
                        + PAGE,
                c.params());
    }

    /** 홈 첫 페이지 글 번호 (최신 공개순). */
    public List<Long> home(Viewer viewer) {
        return ids(homeStatement(viewer));
    }

    /** 블로그 첫 페이지 글 번호. */
    public List<Long> blog(Viewer viewer, long authorId) {
        return ids(blogStatement(viewer, authorId));
    }

    /** 블로그 글 수 ({@code COUNT(*)}, 같은 공용 조건). */
    public long blogCount(Viewer viewer, long authorId) {
        SqlCondition c = filter.forViewer(viewer, authorId);
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + c.sql(),
                        c.params(),
                        Long.class);
        return count == null ? 0 : count;
    }

    /** 공용 조건만 건 전체 목록 (태그·검색·sitemap·피드가 같은 조건을 쓴다 — 페이지 제한 없음). */
    public List<Long> listedEverywhere(Viewer viewer) {
        SqlCondition c = filter.forViewer(viewer, null);
        return jdbc.queryForList(
                "SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE "
                        + c.sql()
                        + " ORDER BY p.id",
                c.params(),
                Long.class);
    }

    private List<Long> ids(Statement statement) {
        return jdbc.queryForList(statement.sql(), statement.params(), Long.class);
    }

    /** 실행할 SQL과 이름 있는 파라미터. */
    public record Statement(String sql, Map<String, Object> params) {}
}
