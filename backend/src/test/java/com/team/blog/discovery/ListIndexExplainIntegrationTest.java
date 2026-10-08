package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.nextCursor;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.CardFilter;
import com.team.blog.discovery.application.PostListCursor.CursorKey;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardQueryRepository.CardQuery;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 목록 인덱스 사용과 응답 시간 (005 T072, SC-001, 06 R-2b, data-model §2).
 *
 * <p>글 1만 건(작성자 50명, 공개·비공개·임시·휴지통·숨김 섞음)과 작성자마다 프로필 사진 1장 + 글 사진 20장을 넣고 {@code ANALYZE} 한 뒤,
 * 홈·블로그 카드 조회가 실제로 실행하는 문장({@link PostCardQueryRepository#cardQuery})의 {@code EXPLAIN (FORMAT
 * JSON)}에 부분 인덱스가 나오는지 본다. 응답 시간은 MockMvc로 서버 처리 시간을 잰다(웜업 뒤 중앙값).
 */
class ListIndexExplainIntegrationTest extends IntegrationTestBase {

    private static final int POSTS = 10_000;
    private static final int AUTHORS = 50;
    private static final long BUDGET_MILLIS = 300;

    @Autowired private PostCardQueryRepository cards;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void seed() {
        jdbc.update(
                "INSERT INTO member (handle, nickname, role, status)"
                        + " SELECT 'author' || lpad(g::text, 2, '0'), '작가' || lpad(g::text, 2, '0'),"
                        + " 'USER', 'ACTIVE' FROM generate_series(1, ?) g",
                AUTHORS);
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, thumb_size_bytes, width, height, status, purpose)"
                        + " SELECT m.id, 'profiles/' || m.handle || '.png', 'profiles/' || m.handle"
                        + " || '_thumb.webp', 'image/png', 1000, 100, 100, 100, 'ATTACHED', 'PROFILE'"
                        + " FROM member m WHERE m.handle LIKE 'author%'");
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, thumb_size_bytes, width, height, status, purpose)"
                        + " SELECT m.id, 'images/' || m.handle || '/' || g || '.png', 'images/'"
                        + " || m.handle || '/' || g || '_thumb.webp', 'image/png', 1000, 100, 100,"
                        + " 100, 'ATTACHED', 'POST' FROM member m, generate_series(1, 20) g"
                        + " WHERE m.handle LIKE 'author%'");
        // g % 20 = 0 임시글, g % 10 = 1 비공개, g % 50 = 7 휴지통, g % 100 = 3 숨김 — 나머지는 공개·노출
        jdbc.update(
                """
                INSERT INTO post (author_id, title, content_md, content_html, excerpt, status,
                                  visibility, edit_version, published_at, first_public_at,
                                  created_at, updated_at, deleted_at, hidden_at, hidden_by,
                                  hidden_reason)
                SELECT a.id, '글 ' || g, '본문 ' || g, '<p>본문 ' || g || '</p>', '요약 ' || g,
                       CASE WHEN g % 20 = 0 THEN 'DRAFT' ELSE 'PUBLISHED' END,
                       CASE WHEN g % 10 = 1 THEN 'PRIVATE' ELSE 'PUBLIC' END,
                       1,
                       CASE WHEN g % 20 = 0 THEN NULL ELSE t.at END,
                       CASE WHEN g % 20 <> 0 AND g % 10 <> 1 THEN t.at END,
                       t.at, t.at,
                       CASE WHEN g % 50 = 7 THEN t.at + interval '1 day' END,
                       CASE WHEN g % 100 = 3 THEN t.at + interval '1 day' END,
                       CASE WHEN g % 100 = 3 THEN a.id END,
                       CASE WHEN g % 100 = 3 THEN 'SPAM' END
                  FROM generate_series(1, ?) g
                  JOIN member a ON a.handle = 'author' || lpad(((g % ?) + 1)::text, 2, '0')
                 CROSS JOIN LATERAL (
                       SELECT TIMESTAMPTZ '2026-01-01 00:00:00+00'
                              + g * interval '17 minutes' + g * interval '1 microsecond' AS at) t
                """,
                POSTS, AUTHORS);
        jdbc.execute("ANALYZE member");
        jdbc.execute("ANALYZE image");
        jdbc.execute("ANALYZE post");
    }

    private String explain(CardQuery query) {
        return jdbcClient
                .sql("EXPLAIN (FORMAT JSON) " + query.sql())
                .params(query.params())
                .query(String.class)
                .single();
    }

    private long authorId(String handle) {
        return jdbc.queryForObject("SELECT id FROM member WHERE handle = ?", Long.class, handle);
    }

    @Test
    void 홈_목록은_ix_post_feed와_uq_image_profile_current를_탄다() {
        String first = explain(cards.cardQuery(Viewer.anonymous(), CardFilter.all(), null, 10));
        assertThat(first).contains("\"ix_post_feed\"").contains("\"uq_image_profile_current\"");

        CursorKey after = new CursorKey(OffsetDateTime.parse("2026-03-01T00:00:00Z"), 5_000L);
        String next = explain(cards.cardQuery(Viewer.anonymous(), CardFilter.all(), after, 10));
        assertThat(next).contains("\"ix_post_feed\"");
    }

    @Test
    void 블로그_목록은_ix_post_blog를_탄다() {
        long author = authorId("author07");

        String plan =
                explain(cards.cardQuery(Viewer.anonymous(), CardFilter.author(author), null, 10));

        assertThat(plan).contains("\"ix_post_blog\"").contains("\"uq_image_profile_current\"");
    }

    @Test
    void 목록과_상세의_서버_처리_시간은_300ms_이내다() throws Exception {
        ReadingApi api = new ReadingApi(mockMvc);
        MvcResult home = api.home(null, null);
        assertThat(status(home)).isEqualTo(200);
        String cursor = nextCursor(home);
        long postId =
                jdbc.queryForObject(
                        "SELECT max(id) FROM post WHERE status = 'PUBLISHED' AND visibility ="
                                + " 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL",
                        Long.class);

        long homeMillis = medianMillis(() -> api.home(null, cursor));
        long blogMillis = medianMillis(() -> api.blogPosts(null, "author07", null));
        long detailMillis = medianMillis(() -> api.detail(null, postId));

        assertThat(homeMillis).as("GET /api/posts").isLessThanOrEqualTo(BUDGET_MILLIS);
        assertThat(blogMillis)
                .as("GET /api/members/{handle}/posts")
                .isLessThanOrEqualTo(BUDGET_MILLIS);
        assertThat(detailMillis).as("GET /api/posts/{id}").isLessThanOrEqualTo(BUDGET_MILLIS);
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
