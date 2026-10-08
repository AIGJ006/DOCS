package com.team.blog.discovery.infra;

import com.team.blog.discovery.application.search.SearchQuery;
import com.team.blog.discovery.application.search.SearchStage;
import com.team.blog.discovery.application.search.SearchWord;
import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 글 검색 단계 SQL (012 T013, research R7·R8, contracts §5).
 *
 * <p>단어 {@code w}마다 이름 붙은 매개변수 {@code :w{i}}({@code %w%}, LIKE 이스케이프)·{@code :g{i}}(소문자 태그 패턴)를 쓴다:
 *
 * <ul>
 *   <li>{@code T(w)} = {@code p.title ILIKE :w ESCAPE '\'}
 *   <li>{@code G(w)} = 태그 이름 {@code LIKE :g ESCAPE '\'} ({@code EXISTS}, 태그 이름은 소문자로 저장 — 008
 *       {@code ck_tag_name})
 *   <li>{@code C(w)} = {@code p.content_md ILIKE :w ESCAPE '\'} — 3글자 이상 단어만(FR-019). 원문이라 코드 블록 안
 *       글자도 찾는다(FR-023)
 * </ul>
 *
 * 관련도순 ① {@code ∧T} ② {@code ∧(T∨G) ∧ ¬①} ③ {@code ∧(T∨G∨C) ∧ ¬②}, 최신순 {@code ∧(T∨G∨C)}. 단계 안 정렬은
 * {@code first_public_at DESC, id DESC}이고 커서는 행 값 비교다.
 *
 * <p>실행은 단계마다 ① 최근 {@code recent-window}개 공개 글 안에서 찾고, 모자라고 창이 가득 찼으면(더 오래된 글이 있으면) ② 가장 긴 단어의
 * trigram 후보(제목 GIN ∪ 태그 ∪ 본문 GIN, 단계에 필요한 것만 {@code UNION})에서 전체 조건으로 찾는다. 창 안 결과는 창 밖 결과보다 항상 앞이라
 * ①이 다 채우면 ②는 필요 없다. 태그 {@code EXISTS}를 본문 {@code ILIKE}와 {@code OR}로 섞으면 본문 인덱스를 못 써서 후보를 {@code
 * UNION}으로 나눴다(33 §8). 노출 조건은 004 {@link VisibilityFilter} 하나만 쓴다(비회원 기준 — 블로그 주인이 봐도 공개 글만).
 *
 * <p><b>원칙 II 읽기 예외 (plan Complexity Tracking 2행)</b>: 태그 단계와 단어별 "제목 또는 태그" 조건이 같은 SQL 안에 있어야
 * 단계·정렬· 커서가 한 문장에서 정해지므로 {@code post_tag}·{@code tag}를 읽기 전용으로 읽는다(005 카드 SQL의 {@code
 * member}·{@code image} 예외와 같은 종류).
 */
@Repository
public class PostSearchRepository {

    private static final String ORDER = " ORDER BY p.first_public_at DESC, p.id DESC";

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public PostSearchRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /** 찾은 글 하나. */
    public record Hit(SearchStage stage, long id, OffsetDateTime firstPublicAt) {}

    /** 단계 안 이어 보기 위치 (이 위치보다 뒤만). */
    public record After(OffsetDateTime firstPublicAt, long id) {}

    /** 주변 문장 재료. */
    public record SnippetSource(long id, String title, String contentMd) {}

    /** 한 단계 실행 결과. */
    public record StageResult(List<Hit> hits, int sqlCount, boolean usedCandidates) {}

    /**
     * 한 단계에서 {@code limit}개까지 찾는다.
     *
     * @param authorId 블로그 안 검색이면 블로그 주인, 아니면 {@code null}
     * @param after 이 위치보다 뒤만 (단계 처음부터면 {@code null})
     * @param recentWindow 먼저 찾아보는 최근 공개 글 수
     */
    public StageResult find(
            SearchQuery query,
            SearchStage stage,
            Long authorId,
            After after,
            int limit,
            int recentWindow) {
        SqlCondition visibility = visibilityFilter.forViewer(Viewer.anonymous(), authorId);
        Map<String, Object> params = new LinkedHashMap<>(visibility.params());
        String stageSql = stageCondition(query, stage, params);
        String cursorSql = "";
        if (after != null) {
            cursorSql = " AND (p.first_public_at, p.id) < (:cursorAt, :cursorId)";
            params.put("cursorAt", after.firstPublicAt().withOffsetSameInstant(ZoneOffset.UTC));
            params.put("cursorId", after.id());
        }
        params.put("limit", limit);
        params.put("recentWindow", recentWindow);

        String recentSql =
                "WITH recent AS (SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE "
                        + visibility.sql()
                        + ORDER
                        + " LIMIT :recentWindow)"
                        + " SELECT w.cnt, h.id, h.first_public_at"
                        + " FROM (SELECT count(*) AS cnt FROM recent) w"
                        + " LEFT JOIN LATERAL (SELECT p.id, p.first_public_at FROM post p"
                        + " JOIN recent r ON r.id = p.id WHERE "
                        + stageSql
                        + cursorSql
                        + ORDER
                        + " LIMIT :limit) h ON true"
                        + " ORDER BY h.first_public_at DESC NULLS LAST, h.id DESC";
        List<Hit> hits = new ArrayList<>();
        long[] windowCount = new long[1];
        jdbc.sql(recentSql)
                .params(params)
                .query(
                        rs -> {
                            windowCount[0] = rs.getLong("cnt");
                            long id = rs.getLong("id");
                            if (!rs.wasNull()) {
                                hits.add(
                                        new Hit(
                                                stage,
                                                id,
                                                utc(
                                                        rs.getObject(
                                                                "first_public_at",
                                                                OffsetDateTime.class))));
                            }
                        });
        if (hits.size() >= limit || windowCount[0] < recentWindow) {
            return new StageResult(hits, 1, false);
        }
        SearchWord longest = query.longest();
        params.put("lp", pattern(longest.text()));
        StringBuilder candidates =
                new StringBuilder("SELECT id FROM post WHERE title ILIKE :lp ESCAPE '\\'");
        if (stage != SearchStage.TITLE) {
            params.put("lg", pattern(longest.text().toLowerCase(Locale.ROOT)));
            candidates.append(
                    " UNION SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                            + " WHERE t.name LIKE :lg ESCAPE '\\'");
        }
        if ((stage == SearchStage.ANYWHERE || stage == SearchStage.ANY) && longest.inContent()) {
            candidates.append(" UNION SELECT id FROM post WHERE content_md ILIKE :lp ESCAPE '\\'");
        }
        String candidateSql =
                "WITH cand AS ("
                        + candidates
                        + ") SELECT p.id, p.first_public_at FROM post p JOIN cand c ON c.id = p.id"
                        + " JOIN member m ON m.id = p.author_id WHERE "
                        + visibility.sql()
                        + " AND "
                        + stageSql
                        + cursorSql
                        + ORDER
                        + " LIMIT :limit";
        List<Hit> all =
                jdbc.sql(candidateSql)
                        .params(params)
                        .query(
                                (rs, n) ->
                                        new Hit(
                                                stage,
                                                rs.getLong("id"),
                                                utc(
                                                        rs.getObject(
                                                                "first_public_at",
                                                                OffsetDateTime.class))))
                        .list();
        return new StageResult(all, 2, true);
    }

    /** 주변 문장 재료 (SQL 1번). 빈 목록이면 SQL 없이 빈 목록. */
    public List<SnippetSource> snippetSources(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, title, content_md FROM post WHERE id = ANY(:ids)")
                .param("ids", ids.toArray(Long[]::new))
                .query(
                        (rs, n) ->
                                new SnippetSource(
                                        rs.getLong("id"),
                                        rs.getString("title"),
                                        rs.getString("content_md")))
                .list();
    }

    /** 단계 조건 (별칭 {@code p}). 단어 매개변수를 {@code params}에 넣는다. */
    static String stageCondition(SearchQuery query, SearchStage stage, Map<String, Object> params) {
        List<SearchWord> words = query.words();
        for (int i = 0; i < words.size(); i++) {
            params.put("w" + i, pattern(words.get(i).text()));
            params.put("g" + i, pattern(words.get(i).text().toLowerCase(Locale.ROOT)));
        }
        String cond1 = all(words.size(), PostSearchRepository::title);
        String cond2 = all(words.size(), i -> "(" + title(i) + " OR " + tag(i) + ")");
        String cond3 =
                all(
                        words.size(),
                        i ->
                                "("
                                        + title(i)
                                        + " OR "
                                        + tag(i)
                                        + (words.get(i).inContent() ? " OR " + content(i) : "")
                                        + ")");
        return switch (stage) {
            case TITLE -> cond1;
            case TITLE_OR_TAG -> cond2 + " AND NOT (" + cond1 + ")";
            case ANYWHERE -> cond3 + " AND NOT (" + cond2 + ")";
            case ANY -> cond3;
        };
    }

    private static String all(int n, IntFunction<String> part) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            parts.add(part.apply(i));
        }
        return "(" + String.join(" AND ", parts) + ")";
    }

    private static String title(int i) {
        return "p.title ILIKE :w" + i + " ESCAPE '\\'";
    }

    private static String content(int i) {
        return "p.content_md ILIKE :w" + i + " ESCAPE '\\'";
    }

    private static String tag(int i) {
        return "EXISTS (SELECT 1 FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                + " WHERE pt.post_id = p.id AND t.name LIKE :g"
                + i
                + " ESCAPE '\\')";
    }

    /** {@code %단어%} — {@code \}·{@code %}·{@code _}를 글자로 (FR-021). */
    static String pattern(String word) {
        return "%" + escapeLike(word) + "%";
    }

    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static OffsetDateTime utc(OffsetDateTime at) {
        return at == null ? null : at.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
