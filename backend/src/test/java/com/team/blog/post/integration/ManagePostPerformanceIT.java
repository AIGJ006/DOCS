package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.ManagePostQueryService;
import com.team.blog.post.domain.ManageCursor;
import com.team.blog.post.domain.ManageTab;
import com.team.blog.post.infra.ManagePostQueryRepository;
import com.team.blog.post.infra.ManagePostQueryRepository.SqlQuery;
import com.team.blog.post.support.TrashApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리 목록 성능·인덱스 (006 T035, SC-005, research R18). 회원 1명에게 글 1만 건(임시·발행·휴지통 섞음)과 다른 회원 글 1만 건을 {@code
 * generate_series}로 넣고 {@code VACUUM ANALYZE} 한 뒤(운영 테이블은 autovacuum이 가시성 맵을 채운다 — 갓 넣은 행뿐이면 개수 쿼리가
 * index-only scan 대신 seq scan을 고른다), 실제 실행 문장({@link ManagePostQueryRepository#listQuery}· {@link
 * ManagePostQueryRepository#countQuery})의 {@code EXPLAIN}·서버 처리 시간·SQL 수를 본다.
 */
class ManagePostPerformanceIT extends IntegrationTestBase {

    private static final long BUDGET_MILLIS = 300;

    @Autowired ManagePostQueryRepository repository;
    @Autowired ManagePostQueryService service;
    @Autowired JdbcClient jdbcClient;

    private long me;

    @BeforeEach
    void seed() {
        me = members().member().create();
        long other = members().member().create();
        for (long author : new long[] {me, other}) {
            // g % 10 < 3 임시글, g % 7 = 0 비공개, g % 25 = 3 휴지통, g % 9 = 0 작업본 — 나머지는 공개 발행
            jdbc.update(
                    """
                    INSERT INTO post (author_id, title, content_md, content_html, status,
                                      visibility, edit_version, published_at, first_public_at,
                                      created_at, updated_at, deleted_at)
                    SELECT ?, '글 ' || g, repeat('본문 ', 200), '',
                           CASE WHEN g % 10 < 3 THEN 'DRAFT' ELSE 'PUBLISHED' END,
                           CASE WHEN g % 7 = 0 THEN 'PRIVATE' ELSE 'PUBLIC' END,
                           1,
                           CASE WHEN g % 10 < 3 THEN NULL ELSE t.at END,
                           CASE WHEN g % 10 >= 3 AND g % 7 <> 0 THEN t.at END,
                           t.at, t.at + (g % 13) * interval '1 hour',
                           CASE WHEN g % 25 = 3 THEN t.at + interval '2 days' END
                      FROM generate_series(1, 10000) g
                     CROSS JOIN LATERAL (
                           SELECT TIMESTAMPTZ '2025-01-01 00:00:00+00'
                                  + g * interval '7 minutes' + g * interval '1 microsecond' AS at) t
                    """,
                    author);
            jdbc.update(
                    "INSERT INTO post_draft (post_id, title, content_md, edit_version)"
                            + " SELECT id, title, content_md, 2 FROM post"
                            + " WHERE author_id = ? AND status = 'PUBLISHED' AND id % 9 = 0",
                    author);
        }
        jdbc.execute("VACUUM ANALYZE post");
        jdbc.execute("VACUUM ANALYZE post_draft");
    }

    private String explain(SqlQuery query) {
        return jdbcClient
                .sql("EXPLAIN (FORMAT JSON) " + query.sql())
                .params(query.params())
                .query(String.class)
                .single();
    }

    @Test
    void 임시글_발행글은_ix_post_manage_휴지통은_ix_post_trash를_탄다() {
        ManageCursor after =
                new ManageCursor(
                        ManageTab.PUBLISHED, "all", Instant.parse("2025-03-01T00:00:00Z"), 5_000L);
        ManageCursor trashAfter =
                new ManageCursor(
                        ManageTab.TRASH, null, Instant.parse("2025-03-01T00:00:00Z"), 5_000L);

        assertThat(explain(repository.listQuery(me, ManageTab.DRAFTS, null, null, 21)))
                .contains("\"ix_post_manage\"");
        assertThat(explain(repository.listQuery(me, ManageTab.PUBLISHED, null, null, 21)))
                .contains("\"ix_post_manage\"");
        assertThat(explain(repository.listQuery(me, ManageTab.PUBLISHED, "PRIVATE", after, 21)))
                .contains("\"ix_post_manage\"");
        assertThat(explain(repository.listQuery(me, ManageTab.TRASH, null, null, 21)))
                .contains("\"ix_post_trash\"");
        assertThat(explain(repository.listQuery(me, ManageTab.TRASH, null, trashAfter, 21)))
                .contains("\"ix_post_trash\"");
        String count = explain(repository.countQuery(me));
        assertThat(count).contains("\"ix_post_manage\"").contains("\"ix_post_trash\"");
    }

    @Test
    void 탭_3개_첫_페이지와_두_번째_페이지가_300ms_이내() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, me);
        TrashApi api = new TrashApi(mockMvc);
        for (String tab : List.of("drafts", "published", "trash")) {
            MvcResult first = api.list(session, "tab=" + tab);
            assertThat(status(first)).isEqualTo(200);
            String cursor = read(first, "$.nextCursor");
            assertThat(cursor).as(tab).isNotNull();
            String next =
                    "tab=" + tab + "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);

            long firstMillis = medianMillis(() -> api.list(session, "tab=" + tab));
            long nextMillis = medianMillis(() -> api.list(session, next));

            assertThat(firstMillis).as(tab + " 첫 페이지").isLessThanOrEqualTo(BUDGET_MILLIS);
            assertThat(nextMillis).as(tab + " 두 번째 페이지").isLessThanOrEqualTo(BUDGET_MILLIS);
        }
    }

    @Test
    void 첫_요청은_목록_개수_2번_커서_요청은_목록_1번() {
        // 서비스 첫머리의 001 계정 상태 확인(AccountStatusGuard) 1번을 함께 센다
        int guard = 1;
        for (ManageTab tab : ManageTab.values()) {
            String cursor;
            try (SqlCounter.Scope scope = SqlCounter.start()) {
                cursor = service.list(me, tab, null, null).nextCursor();
                assertThat(scope.count()).as(tab + " 첫 요청").isEqualTo(guard + 2);
            }
            try (SqlCounter.Scope scope = SqlCounter.start()) {
                service.list(me, tab, null, cursor);
                assertThat(scope.count()).as(tab + " 커서 요청").isEqualTo(guard + 1);
            }
        }
    }

    private interface Call {
        MvcResult run() throws Exception;
    }

    private static long medianMillis(Call call) throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(status(call.run())).isEqualTo(200);
        }
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            long started = System.nanoTime();
            MvcResult result = call.run();
            samples.add((System.nanoTime() - started) / 1_000_000);
            assertThat(status(result)).isEqualTo(200);
        }
        Collections.sort(samples);
        return samples.get(samples.size() / 2);
    }
}
