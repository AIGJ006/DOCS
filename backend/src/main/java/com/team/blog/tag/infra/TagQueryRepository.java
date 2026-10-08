package com.team.blog.tag.infra;

import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 태그 집계 조회 (008 T031·T043·T050·T058, research R5·R8·R11). 공개 글 수·목록 조건은 004 {@link
 * VisibilityFilter}의 조각 하나만 쓴다(별칭 {@code p}·{@code m} 계약) — 목록마다 WHERE를 직접 쓰지 않는다.
 *
 * <p><b>원칙 II 예외 (008 plan Complexity Tracking)</b>: 공개 글 수 집계는 {@code post_tag}와 글의 공개 조건({@code
 * post.status}·{@code visibility}·{@code deleted_at}·{@code hidden_at}, {@code
 * member.withdrawn_at})을 한 SQL로 묶어야 300ms 안에 끝나므로 tag 모듈에서 {@code post}·{@code member}를 읽는 곳은 이 클래스
 * 하나로 둔다. 조건 문구는 {@code VisibilityFilter}가 주므로 규칙이 갈라지지 않는다.
 *
 * <p>태그별 목록·머리말 글 수·전체 태그 목록·자동완성 글 수는 <b>로그인한 사람에게도 익명 조건</b>({@code forViewer(Viewer.anonymous(),
 * null)})을 쓴다 — 태그 화면은 보는 사람과 상관없이 전체 공개 글만 보인다(FR-022, 친구 공개 적용자라도 친구 글 제외). 블로그 태그 줄만 블로그 목록과 같은
 * {@code forViewer(viewer, ownerId)}다.
 */
@Repository
public class TagQueryRepository {

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public TagQueryRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /** 이름으로 태그 번호 (유일 인덱스 {@code uq_tag_name}). */
    public Optional<Long> findIdByName(String name) {
        return jdbc.sql("SELECT id FROM tag WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .optional();
    }

    /** 그 태그가 붙은 전체 공개 글 수. 태그가 없으면 0 — 아무도 안 쓴 태그와 비공개 글에만 쓰인 태그가 같은 값이다(SC-005). SQL 1번. */
    public long countPublic(String name) {
        SqlCondition condition = publicCondition();
        Map<String, Object> params = new LinkedHashMap<>(condition.params());
        params.put("name", name);
        return jdbc.sql(
                        "SELECT count(*) FROM tag t"
                                + " JOIN post_tag pt ON pt.tag_id = t.id"
                                + " JOIN post p ON p.id = pt.post_id"
                                + " JOIN member m ON m.id = p.author_id"
                                + " WHERE t.name = :name AND "
                                + condition.sql())
                .params(params)
                .query(Long.class)
                .single();
    }

    /**
     * 자동완성 후보를 앞부분 일치로 고를 때 집계 범위를 묶는 상한 (research R11). 설정값이 아니라 안전장치다 — 같은 앞글자로 시작하는 태그가 이보다 많을
     * 때만 결과 순서에 영향이 생긴다(이름 순 앞 200개 안에서 고른다).
     */
    static final int SUGGEST_CANDIDATE_LIMIT = 200;

    /**
     * 자동완성 (research R11 SQL 한 번). {@code prefix}는 정규화된 검색어다. 후보는 ① {@code me}가 쓴 모든 글(상태·공개 범위·휴지통
     * 무관)의 태그 ② 공개 글 수 1 이상인 태그이고, 순서는 내 태그 → 공개 글 수 많은 순 → 이름 순. 남의 비공개 글에만 쓰인 태그는 어느 쪽에도 없어 빠진다.
     */
    public List<TagSuggestionRow> suggest(String prefix, long me, int limit) {
        SqlCondition condition = publicCondition();
        Map<String, Object> params = new LinkedHashMap<>(condition.params());
        params.put("prefix", likePrefix(prefix));
        params.put("me", me);
        params.put("candidates", SUGGEST_CANDIDATE_LIMIT);
        params.put("limit", limit);
        return jdbc.sql(
                        "WITH cand AS (SELECT id, name FROM tag WHERE name LIKE :prefix ESCAPE '\\'"
                                + " ORDER BY name LIMIT :candidates),"
                                + " mine AS (SELECT DISTINCT pt.tag_id FROM post_tag pt"
                                + " JOIN post p ON p.id = pt.post_id"
                                + " WHERE p.author_id = :me AND pt.tag_id IN (SELECT id FROM cand)),"
                                + " pub AS (SELECT pt.tag_id, count(*) AS c FROM post_tag pt"
                                + " JOIN post p ON p.id = pt.post_id"
                                + " JOIN member m ON m.id = p.author_id"
                                + " WHERE "
                                + condition.sql()
                                + " AND pt.tag_id IN (SELECT id FROM cand)"
                                + " GROUP BY pt.tag_id)"
                                + " SELECT c.name, COALESCE(pub.c, 0) AS post_count,"
                                + " (mine.tag_id IS NOT NULL) AS mine"
                                + " FROM cand c LEFT JOIN pub ON pub.tag_id = c.id"
                                + " LEFT JOIN mine ON mine.tag_id = c.id"
                                + " WHERE mine.tag_id IS NOT NULL OR pub.c > 0"
                                + " ORDER BY mine DESC, post_count DESC, c.name ASC"
                                + " LIMIT :limit")
                .params(params)
                .query(
                        (rs, rowNum) ->
                                new TagSuggestionRow(
                                        rs.getString("name"),
                                        rs.getLong("post_count"),
                                        rs.getBoolean("mine")))
                .list();
    }

    /** {@code LIKE} 앞부분 일치 패턴: {@code \\}·{@code _}·{@code %}를 이스케이프하고 {@code %}를 붙인다. */
    static String likePrefix(String prefix) {
        StringBuilder out = new StringBuilder(prefix.length() + 1);
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '\\' || c == '_' || c == '%') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.append('%').toString();
    }

    /** 태그 화면 공용 조건: 보는 사람과 상관없이 익명 조건 (클래스 주석). */
    private SqlCondition publicCondition() {
        return visibilityFilter.forViewer(Viewer.anonymous(), null);
    }
}
