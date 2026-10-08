package com.team.blog.tag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.tag.support.TagApi;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 태그 조회 성능 측정 (008 T063, SC-007, FR-029, quickstart §4). 기본 빌드에서는 돌지 않는다 — {@code ./mvnw verify
 * -Dit.test=TagPerformanceIT -Dblog.perf=true}로 켠다(pom을 바꾸지 않으려고 {@code @Tag("perf")} + 시스템 속성 조건을
 * 함께 쓴다).
 *
 * <p>시드: 회원 200명(한 명은 글 1천 건 블로그), 글 1만 건(공개 8천·비공개 1천·임시 5백·휴지통 3백·숨김 2백), 태그 2천 개, 연결 약 3만 건(상위
 * 20개 태그가 연결의 40%). 각 요청을 20번 보내 p95를 출력하고 태그별 목록 SQL의 {@code EXPLAIN (ANALYZE, BUFFERS)}를 출력한다.
 */
@Tag("perf")
@EnabledIfSystemProperty(named = "blog.perf", matches = "true")
class TagPerformanceIT extends IntegrationTestBase {

    private static final int RUNS = 20;

    @Autowired private VisibilityFilter visibilityFilter;
    @Autowired private NamedParameterJdbcTemplate named;

    @Test
    void 다섯_요청의_p95와_실행_계획() throws Exception {
        long admin = members().member().role("ADMIN").create();
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            memberIds.add(members().member().create());
        }
        long bigBlog = memberIds.get(0); // 번호가 가장 작은 일반 회원 = 시드의 rn 1
        seed(memberIds, admin);
        Cookie session = com.team.blog.support.TestLogin.loginAs(mockMvc, memberIds.get(1));
        TagApi api = new TagApi(mockMvc);

        String topTag =
                jdbc.queryForObject(
                        "SELECT t.name FROM tag t JOIN post_tag pt ON pt.tag_id = t.id"
                                + " GROUP BY t.name ORDER BY count(*) DESC, t.name LIMIT 1",
                        String.class);
        String bigHandle =
                jdbc.queryForObject(
                        "SELECT handle FROM member WHERE id = ?", String.class, bigBlog);

        List<String> report = new ArrayList<>();
        report.add(measure("전체 태그 목록 GET /api/tags", () -> api.top(null)));
        report.add(measure("태그 목록 첫 페이지 /api/tags/" + topTag, () -> api.posts(null, topTag, null)));
        String lateCursor = walkCursor(api, topTag, 300);
        report.add(measure("태그 목록 뒤쪽 페이지 (300쪽 근처)", () -> api.posts(null, topTag, lateCursor)));
        // 자동완성은 1분 60번 제한이 있어 측정마다 키를 지운다
        report.add(
                measure(
                        "자동완성 한 글자 q=s",
                        () -> {
                            flushRateLimit();
                            return api.suggest(session, "s");
                        }));
        report.add(measure("블로그 태그 줄 (글 1천 건)", () -> api.blogTags(null, bigHandle)));

        System.out.println("==== 008 TagPerformanceIT ====");
        report.forEach(System.out::println);
        System.out.println("---- EXPLAIN 태그별 목록 ----");
        explainTagList(topTag).forEach(System.out::println);
    }

    private void seed(List<Long> memberIds, long admin) {
        named.update(
                "WITH ms AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM member"
                        + " WHERE id <> :admin)"
                        + " INSERT INTO post (author_id, title, content_md, content_html, excerpt, status,"
                        + " visibility, published_at, first_public_at, created_at, updated_at,"
                        + " deleted_at, hidden_at, hidden_by, hidden_reason)"
                        + " SELECT (SELECT id FROM ms WHERE rn = CASE WHEN g <= 1000 THEN 1"
                        + "   ELSE 2 + g % 199 END),"
                        + " '글 ' || g, '본문', '<p>본문</p>', '본문',"
                        + " CASE WHEN g % 100 BETWEEN 90 AND 94 THEN 'DRAFT' ELSE 'PUBLISHED' END,"
                        + " CASE WHEN g % 100 BETWEEN 80 AND 89 THEN 'PRIVATE' ELSE 'PUBLIC' END,"
                        + " CASE WHEN g % 100 BETWEEN 90 AND 94 THEN NULL"
                        + "      ELSE now() - make_interval(mins => g) END,"
                        + " CASE WHEN g % 100 BETWEEN 80 AND 94 THEN NULL"
                        + "      ELSE now() - make_interval(mins => g) END,"
                        + " now(), now(),"
                        + " CASE WHEN g % 100 BETWEEN 95 AND 97 THEN now() END,"
                        + " CASE WHEN g % 100 >= 98 THEN now() END,"
                        + " CASE WHEN g % 100 >= 98 THEN :admin END,"
                        + " CASE WHEN g % 100 >= 98 THEN 'SPAM' END"
                        + " FROM generate_series(1, 10000) g",
                Map.of("admin", admin));
        jdbc.update(
                "INSERT INTO tag (name) SELECT CASE WHEN n % 4 = 0 THEN 's' ELSE 'k' END"
                        + " || '-tag-' || n FROM generate_series(1, 2000) n");
        jdbc.update(
                "WITH ps AS (SELECT id, row_number() OVER (ORDER BY id) AS g FROM post),"
                        + " ts AS (SELECT id, row_number() OVER (ORDER BY id) AS n FROM tag)"
                        + " INSERT INTO post_tag (post_id, tag_id, position)"
                        + " SELECT ps.id, ts.id, i FROM ps CROSS JOIN generate_series(0, 2) i"
                        + " JOIN ts ON ts.n = CASE WHEN (ps.g * 3 + i) % 10 < 4"
                        + "   THEN 1 + (ps.g + i) % 20"
                        + "   ELSE 21 + (ps.g * 7 + i * 13) % 1980 END"
                        + " ON CONFLICT DO NOTHING");
        jdbc.execute("ANALYZE post; ANALYZE post_tag; ANALYZE tag; ANALYZE member");
    }

    private void flushRateLimit() {
        redisKeys("ratelimit:tag-suggest:*");
    }

    private void redisKeys(String pattern) {
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String walkCursor(TagApi api, String tag, int pages) throws Exception {
        String cursor = null;
        for (int i = 0; i < pages; i++) {
            MvcResult result = api.posts(null, tag, cursor);
            String next =
                    JsonPath.read(
                            new String(
                                    result.getResponse().getContentAsByteArray(),
                                    StandardCharsets.UTF_8),
                            "$.nextCursor");
            if (next == null) {
                break;
            }
            cursor = next;
        }
        return cursor;
    }

    @FunctionalInterface
    private interface Call {
        MvcResult run() throws Exception;
    }

    private String measure(String label, Call call) throws Exception {
        assertThat(call.run().getResponse().getStatus()).as(label).isEqualTo(200); // 데우기
        List<Double> millis = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            MvcResult result = call.run();
            millis.add((System.nanoTime() - start) / 1_000_000.0);
            assertThat(result.getResponse().getStatus()).as(label).isEqualTo(200);
        }
        millis.sort(Double::compare);
        double p95 = millis.get((int) Math.ceil(RUNS * 0.95) - 1);
        double p50 = millis.get(RUNS / 2);
        return String.format("%s: p50=%.1fms p95=%.1fms", label, p50, p95);
    }

    private List<String> explainTagList(String tag) {
        SqlCondition condition = visibilityFilter.forViewer(Viewer.anonymous(), null);
        Map<String, Object> params = new java.util.LinkedHashMap<>(condition.params());
        params.put("tag", tag);
        return named.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS) SELECT p.id FROM post p"
                        + " JOIN member m ON m.id = p.author_id WHERE "
                        + condition.sql()
                        + " AND EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id"
                        + " AND pt.tag_id = (SELECT id FROM tag WHERE name = :tag))"
                        + " ORDER BY p.first_public_at DESC, p.id DESC LIMIT 10",
                params,
                String.class);
    }
}
