package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.interaction.support.CommentApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 댓글 조회 성능 측정 (007 T054, SC-008, quickstart §4). 기본 빌드에서는 돌지 않는다 — {@code ./mvnw verify
 * -Dit.test=CommentPerformanceIT -Dblog.perf=true}로 켠다(008 {@code TagPerformanceIT}와 같은 방식).
 *
 * <p>시드: 회원 2천 명, 글 1만 건, 댓글 약 10만 건(큰 글 하나에 최상위 2천 + 그중 하나에 답글 5천 + 다른 최상위 몇 개에 답글 3천, 나머지 글마다 최상위
 * 9개). 요청마다 20번 보내 p95·SQL 수를 출력하고 최상위·답글 미리보기 SQL의 {@code EXPLAIN}을 출력한다.
 */
@Tag("perf")
@EnabledIfSystemProperty(named = "blog.perf", matches = "true")
class CommentPerformanceIT extends IntegrationTestBase {

    private static final int RUNS = 20;

    @Test
    void 네_요청의_p95와_실행_계획() throws Exception {
        seed();
        long big = jdbc.queryForObject("SELECT min(id) FROM post", Long.class);
        long hugeRoot =
                jdbc.queryForObject(
                        "SELECT parent_id FROM comment WHERE parent_id IS NOT NULL GROUP BY parent_id"
                                + " ORDER BY count(*) DESC LIMIT 1",
                        Long.class);
        long deepReply =
                jdbc.queryForObject(
                        "SELECT id FROM comment WHERE parent_id = ? ORDER BY created_at DESC, id DESC"
                                + " LIMIT 1 OFFSET 4950",
                        Long.class,
                        hugeRoot);
        CommentApi api = new CommentApi(mockMvc);
        String late = walk(api, big, 95);
        String repliesCursor =
                JsonPath.read(
                        CommentApi.body(api.list(null, big, "around=" + hugeRoot)),
                        "$.items[0].repliesNextCursor");

        List<String> report = new ArrayList<>();
        report.add(measure("첫 페이지 GET /api/posts/{큰 글}/comments", () -> api.list(null, big)));
        report.add(measure("마지막 페이지 근처 (96쪽)", () -> api.listAfter(null, big, late)));
        report.add(measure("답글 5천 최상위의 답글 펼치기", () -> api.replies(null, hugeRoot, repliesCursor)));
        report.add(
                measure(
                        "around (깊은 답글 — 상한 100 넘음)",
                        () -> api.list(null, big, "around=" + deepReply)));

        System.out.println("==== 007 CommentPerformanceIT ====");
        report.forEach(System.out::println);
        System.out.println("---- EXPLAIN 최상위 ----");
        jdbc.queryForList(
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM comment WHERE post_id = ? AND parent_id"
                                + " IS NULL ORDER BY created_at, id LIMIT 21",
                        String.class,
                        big)
                .forEach(System.out::println);
        System.out.println("---- EXPLAIN 답글 미리보기 ----");
        jdbc.queryForList(
                        "EXPLAIN (ANALYZE, BUFFERS) SELECT r.root_id, c.id FROM unnest(ARRAY(SELECT id"
                                + " FROM comment WHERE post_id = ? AND parent_id IS NULL ORDER BY"
                                + " created_at, id LIMIT 20)) AS r(root_id) CROSS JOIN LATERAL (SELECT"
                                + " count(*) FROM comment WHERE parent_id = r.root_id) cnt CROSS JOIN"
                                + " LATERAL (SELECT id FROM comment WHERE parent_id = r.root_id ORDER BY"
                                + " created_at, id LIMIT 3) c",
                        String.class,
                        big)
                .forEach(System.out::println);
    }

    private void seed() {
        jdbc.update(
                "INSERT INTO member (handle, nickname, role, status) SELECT 'perf' || g, '성능' || g,"
                        + " 'USER', 'ACTIVE' FROM generate_series(1, 2000) g");
        jdbc.update(
                "INSERT INTO post (author_id, title, content_md, content_html, excerpt, status,"
                        + " visibility, published_at, first_public_at, created_at, updated_at)"
                        + " SELECT (SELECT min(id) FROM member) + g % 2000, '글 ' || g, '본문',"
                        + " '<p>본문</p>', '본문', 'PUBLISHED', 'PUBLIC', now() - make_interval(mins"
                        + " => g), now() - make_interval(mins => g), now(), now() FROM"
                        + " generate_series(1, 10000) g");
        long big = jdbc.queryForObject("SELECT min(id) FROM post", Long.class);
        long firstMember = jdbc.queryForObject("SELECT min(id) FROM member", Long.class);
        jdbc.update(
                "INSERT INTO comment (post_id, author_id, content, created_at, updated_at) SELECT ?,"
                        + " ? + g % 2000, '최상위 ' || g, now() - make_interval(secs => 200000 - g),"
                        + " now() - make_interval(secs => 200000 - g) FROM generate_series(1, 2000) g",
                big, firstMember);
        long firstRoot =
                jdbc.queryForObject(
                        "SELECT min(id) FROM comment WHERE post_id = ? AND parent_id IS NULL",
                        Long.class,
                        big);
        jdbc.update(
                "INSERT INTO comment (post_id, author_id, parent_id, content, created_at, updated_at)"
                        + " SELECT ?, ? + g % 2000, ?, '답글 ' || g, now() - make_interval(secs => 100000"
                        + " - g), now() - make_interval(secs => 100000 - g) FROM generate_series(1,"
                        + " 5000) g",
                big, firstMember, firstRoot + 10);
        jdbc.update(
                "INSERT INTO comment (post_id, author_id, parent_id, content, created_at, updated_at)"
                        + " SELECT ?, ? + g % 2000, ? + g % 30, '답글 ' || g, now() - make_interval(secs"
                        + " => 50000 - g), now() - make_interval(secs => 50000 - g) FROM"
                        + " generate_series(1, 3000) g",
                big, firstMember, firstRoot + 100);
        jdbc.update(
                "INSERT INTO comment (post_id, author_id, content) SELECT p.id, ? + (p.id + i) % 2000,"
                        + " '댓글' FROM post p CROSS JOIN generate_series(1, 9) i WHERE p.id <> ?",
                firstMember, big);
        jdbc.update(
                "UPDATE post p SET comment_count = c.n FROM (SELECT post_id, count(*) n FROM comment"
                        + " GROUP BY post_id) c WHERE c.post_id = p.id");
        jdbc.execute("ANALYZE comment; ANALYZE post; ANALYZE member; ANALYZE image");
    }

    private String walk(CommentApi api, long postId, int pages) throws Exception {
        String cursor = null;
        for (int i = 0; i < pages; i++) {
            String next =
                    JsonPath.read(
                            CommentApi.body(api.listAfter(null, postId, cursor)), "$.nextCursor");
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
        assertThat(call.run().getResponse().getStatus()).as(label).isEqualTo(200);
        List<Double> millis = new ArrayList<>();
        int sql = 0;
        for (int i = 0; i < RUNS; i++) {
            try (SqlCounter.Scope scope = SqlCounter.start()) {
                long start = System.nanoTime();
                MvcResult result = call.run();
                millis.add((System.nanoTime() - start) / 1_000_000.0);
                sql = scope.count();
                assertThat(result.getResponse().getStatus()).as(label).isEqualTo(200);
            }
        }
        millis.sort(Double::compare);
        double p95 = millis.get((int) Math.ceil(RUNS * 0.95) - 1);
        double p50 = millis.get(RUNS / 2);
        return String.format("%s: p50=%.1fms p95=%.1fms SQL=%d", label, p50, p95, sql);
    }
}
