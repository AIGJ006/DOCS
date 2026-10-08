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
 * <p>실행은 단계마다 ① 최근 {@code recent-window}개 공개 글 안에서 찾고(창 경계를 최신순 색인 조건으로 두어 limit개를 채우면 멈춘다), 모자라고
 * 창이 가득 찼으면(더 오래된 글이 있으면) ② trigram 후보({@link #candidates} — 제목·본문 GIN과 태그 이름)를 번호 배열로 만든 뒤 그 안에서만
 * 전체 조건으로 찾는다. 창 안 결과는 창 밖 결과보다 항상 앞이라 ①이 다 채우면 ②는 필요 없다. 태그 {@code EXISTS}를 본문 {@code ILIKE}와
 * {@code OR}로 섞으면 본문 인덱스를 못 써서 후보를 {@code UNION}으로 나눴다(33 §8). 노출 조건은 004 {@link VisibilityFilter}
 * 하나만 쓴다(비회원 기준 — 블로그 주인이 봐도 공개 글만).
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
        params.put("edgeOffset", Math.max(0, recentWindow - 1));

        // 창 경계 = 최근 recentWindow번째 공개 글의 (first_public_at, id). 없으면 창이 가득 차지 않은 것(전체가 창 안).
        // 경계를 색인 조건으로 두어 ix_post_feed·ix_post_blog를 최신순으로 훑다가 limit개를 채우면 멈추고, 아무리 드물어도
        // 창 밖으로는 나가지 않는다(창 안 글만 단계 조건을 따진다).
        String recentSql =
                "WITH edge AS (SELECT p.first_public_at, p.id FROM post p JOIN member m ON m.id ="
                        + " p.author_id WHERE "
                        + visibility.sql()
                        + ORDER
                        + " OFFSET :edgeOffset LIMIT 1)"
                        + " SELECT e.id AS edge_id, h.id, h.first_public_at"
                        + " FROM (SELECT 1) one LEFT JOIN edge e ON true"
                        + " LEFT JOIN LATERAL (SELECT p.id, p.first_public_at FROM post p"
                        + " JOIN member m ON m.id = p.author_id WHERE "
                        + visibility.sql()
                        + " AND (p.first_public_at, p.id) >= (COALESCE(e.first_public_at,"
                        + " '-infinity'::timestamptz), COALESCE(e.id, 0))"
                        + " AND "
                        + stageSql
                        + cursorSql
                        + ORDER
                        + " LIMIT :limit) h ON true"
                        + " ORDER BY h.first_public_at DESC NULLS LAST, h.id DESC";
        List<Hit> hits = new ArrayList<>();
        boolean[] windowFull = new boolean[1];
        jdbc.sql(recentSql)
                .params(params)
                .query(
                        rs -> {
                            rs.getLong("edge_id");
                            windowFull[0] = !rs.wasNull();
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
        if (hits.size() >= limit || !windowFull[0]) {
            return new StageResult(hits, 1, false);
        }
        // 후보 번호를 먼저 배열로 만들고(글 기본 키 색인 조건) 그 안에서만 단계 조건을 따진다. 후보와 최신순 색인을 섞으면
        // 실행 계획이 최신순 색인을 훑으며 모든 글에 본문 ILIKE를 거는 쪽으로 갈 수 있어서다(T011 측정).
        String candidateSql =
                "SELECT p.id, p.first_public_at FROM post p JOIN member m ON m.id = p.author_id"
                        + " WHERE p.id = ANY(ARRAY("
                        + candidates(query, stage)
                        + ")) AND "
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

    /**
     * 후보 글 번호 SQL ({@code :w{i}}·{@code :g{i}}는 {@link #stageCondition}이 넣는다). 단계 조건을 만족하는 글을 모두 담는
     * 상위 집합이고, 제목·본문 GIN과 태그 이름으로만 찾는다:
     *
     * <ul>
     *   <li>①: 모든 단어가 제목에 — {@code title ILIKE w0 AND title ILIKE w1 …} (GIN 한 번에 교집합)
     *   <li>②: 가장 긴 단어가 제목 또는 태그에
     *   <li>③·최신순: 2글자 단어가 있으면 그 단어가 제목 또는 태그에(2글자는 본문에서 찾지 않으므로). 아니면 모든 단어가 본문에({@code content_md
     *       ILIKE w0 AND …} — GIN이 교집합을 만든 뒤에만 다시 확인해 드문 조합이 싸다) ∪ 어느 한 단어라도 제목·태그에
     * </ul>
     */
    static String candidates(SearchQuery query, SearchStage stage) {
        List<SearchWord> words = query.words();
        switch (stage) {
            case TITLE -> {
                return "SELECT id FROM post p WHERE "
                        + all(words.size(), PostSearchRepository::title);
            }
            case TITLE_OR_TAG -> {
                return titleOrTag(words.indexOf(query.longest()));
            }
            default -> {
                for (int i = 0; i < words.size(); i++) {
                    if (!words.get(i).inContent()) {
                        return titleOrTag(i);
                    }
                }
                List<String> parts = new ArrayList<>();
                parts.add(
                        "SELECT id FROM post p WHERE "
                                + all(words.size(), PostSearchRepository::content));
                for (int i = 0; i < words.size(); i++) {
                    parts.add(titleOrTag(i));
                }
                return String.join(" UNION ", parts);
            }
        }
    }

    private static String titleOrTag(int i) {
        return "SELECT id FROM post p WHERE "
                + title(i)
                + " UNION SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                + " WHERE t.name LIKE :g"
                + i
                + " ESCAPE '\\'";
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
