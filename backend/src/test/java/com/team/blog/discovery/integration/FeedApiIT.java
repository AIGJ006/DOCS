package com.team.blog.discovery.integration;

import static com.team.blog.interaction.support.FollowApi.body;
import static com.team.blog.interaction.support.FollowApi.read;
import static com.team.blog.interaction.support.FollowApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.discovery.application.CardFilter;
import com.team.blog.discovery.application.FeedQueryService;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardQueryRepository.CardQuery;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/** 팔로잉 피드 API (010 T025 US2, contracts {@code getFeed}, research R7). */
class FeedApiIT extends IntegrationTestBase {

    @Autowired FeedQueryService feedQueryService;
    @Autowired PostCardQueryRepository cards;
    @Autowired JdbcClient jdbcClient;

    private long me;
    private long b;
    private long c;
    private Cookie session;
    private Instant base;

    @BeforeEach
    void setUp() {
        me = members().member().create();
        b = members().member().handle("feed_b").create();
        c = members().member().handle("feed_c").create();
        session = TestLogin.loginAs(mockMvc, me);
        base = Instant.now().minus(Duration.ofDays(5)).truncatedTo(ChronoUnit.MICROS);
    }

    private FollowApi api() {
        return new FollowApi(mockMvc);
    }

    private FollowFixtures follows() {
        return new FollowFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private long publicPost(long author, Instant at, String title) {
        return posts().post(author).published("PUBLIC").firstPublicAt(at).title(title).create();
    }

    private static List<Long> ids(MvcResult result) {
        List<Number> ids = read(result, "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static String nextCursor(MvcResult result) {
        return read(result, "$.nextCursor");
    }

    @Test
    void US2_1_팔로우한_사람_공개글만_최신순() throws Exception {
        follows().follow(me, b);
        follows().follow(me, c);
        long b1 = publicPost(b, base, "B 1");
        long same1 = publicPost(c, base.plusSeconds(10), "C 같은 시각 1");
        long same2 = publicPost(b, base.plusSeconds(10), "B 같은 시각 2");
        long stranger = members().member().create();
        publicPost(stranger, base.plusSeconds(20), "남의 글");
        publicPost(me, base.plusSeconds(30), "내 글");

        MvcResult result = api().feed(session, null);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(ids(result)).containsExactly(same2, same1, b1);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
        assertThat((Boolean) read(result, "$.hasFollowing")).isTrue();
        assertThat(nextCursor(result)).isNull();
        // 카드 칸은 005 홈 카드와 같다
        MvcResult home = new ReadingApi(mockMvc).home(null, null);
        List<Map<String, Object>> homeCards = ReadingApi.read(home, "$.items");
        List<Map<String, Object>> feedCards = read(result, "$.items");
        for (Map<String, Object> card : feedCards) {
            assertThat(homeCards).contains(card);
        }
    }

    @Test
    void US2_2_비공개_임시_휴지통_숨김_유예작성자_제외() throws Exception {
        long withdrawnAuthor = members().member().create();
        follows().follow(me, b);
        follows().follow(me, withdrawnAuthor);
        long visible = publicPost(b, base, "보임");
        posts().post(b).published("PRIVATE").title("비공개").create();
        posts().post(b).title("임시").create();
        posts().post(b).published("PUBLIC").firstPublicAt(base).trashed().title("휴지통").create();
        posts().post(b).published("PUBLIC").firstPublicAt(base).hidden().title("숨김").create();
        long pending = publicPost(withdrawnAuthor, base.plusSeconds(5), "유예 작성자");
        follows().withdraw(withdrawnAuthor);

        assertThat(ids(api().feed(session, null))).containsExactly(visible);

        follows().restore(withdrawnAuthor);
        assertThat(ids(api().feed(session, null))).containsExactly(pending, visible);
    }

    @Test
    void US2_3_끝까지_넘기기_중복누락_0() throws Exception {
        follows().follow(me, b);
        follows().follow(me, c);
        Set<Long> expected = new LinkedHashSet<>();
        for (int i = 0; i < 30; i++) {
            // 세 개씩 같은 최초 공개 시각 — 같은 시각 안에서도 빠지지 않아야 한다
            expected.add(publicPost(i % 2 == 0 ? b : c, base.plusSeconds(i / 3), "글 " + i));
        }

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MvcResult page = api().feed(session, cursor);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            List<Long> pageIds = ids(page);
            assertThat(pageIds).as("빈 페이지").isNotEmpty().hasSizeLessThanOrEqualTo(9);
            seen.addAll(pageIds);
            cursor = nextCursor(page);
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(4);
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void US2_4_언팔로우_직후_빠짐() throws Exception {
        follows().follow(me, c);
        api().follow(session, "feed_b");
        long bPost = publicPost(b, base, "B");
        long cPost = publicPost(c, base.plusSeconds(1), "C");
        assertThat(ids(api().feed(session, null))).containsExactly(cPost, bPost);

        api().unfollow(session, "feed_b");

        assertThat(ids(api().feed(session, null))).containsExactly(cPost);
    }

    @Test
    void US2_5_팔로우_없음_hasFollowing_false() throws Exception {
        publicPost(b, base, "B");
        MvcResult result = api().feed(session, null);
        assertThat(ids(result)).isEmpty();
        assertThat((Boolean) read(result, "$.hasFollowing")).isFalse();
        assertThat(nextCursor(result)).isNull();
    }

    @Test
    void US2_6_글_없음_hasFollowing_true() throws Exception {
        follows().follow(me, b);
        posts().post(b).published("PRIVATE").title("비공개만").create();
        MvcResult result = api().feed(session, null);
        assertThat(ids(result)).isEmpty();
        assertThat((Boolean) read(result, "$.hasFollowing")).isTrue();
    }

    @Test
    void US2_7_비회원_401() throws Exception {
        MvcResult result = api().feed(null, null);
        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void 유예_회원은_403_인증전과_정지_남은_세션은_읽기_허용() throws Exception {
        long withdrawn = members().member().status("WITHDRAWN").create();
        MvcResult denied = api().feed(TestLogin.loginAs(mockMvc, withdrawn), null);
        assertThat(status(denied)).isEqualTo(403);
        assertThat((String) read(denied, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");

        long unverified = members().member().emailVerified(false).create();
        assertThat(status(api().feed(TestLogin.loginAs(mockMvc, unverified), null))).isEqualTo(200);
        members().suspend(me, Instant.now().plus(Duration.ofDays(7)), "시험");
        assertThat(status(api().feed(session, null))).isEqualTo(200);
    }

    @Test
    void 다른_목록_커서는_400_INVALID_CURSOR() throws Exception {
        for (int i = 0; i < 12; i++) {
            publicPost(b, base.plusSeconds(i), "홈 " + i);
        }
        String homeCursor = ReadingApi.nextCursor(new ReadingApi(mockMvc).home(null, null));
        assertThat(homeCursor).isNotNull();

        MvcResult result = api().feed(session, homeCursor);

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        assertThat(status(api().feed(session, "깨진커서"))).isEqualTo(400);
    }

    @Test
    void 카드_SQL_1번_빈_첫페이지만_팔로우_여부_1번_더() {
        follows().follow(me, b);
        follows().profileImage(b, "profiles/b.png", "profiles/b_thumb.webp");
        publicPost(b, base, "B");
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            FeedQueryService.FeedPage page = feedQueryService.page(me, null);
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).author().profileImageUrl()).endsWith("b_thumb.webp");
            assertThat(scope.count()).isEqualTo(1);
        }
        long lonely = members().member().create();
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(feedQueryService.page(lonely, null).hasFollowing()).isFalse();
            assertThat(scope.count()).isEqualTo(2);
        }
    }

    @Test
    void 클라이언트_size는_무시한다() throws Exception {
        follows().follow(me, b);
        for (int i = 0; i < 12; i++) {
            publicPost(b, base.plusSeconds(i), "글 " + i);
        }
        MvcResult result =
                mockMvc.perform(get("/api/feed").param("size", "50").cookie(session)).andReturn();
        assertThat(ids(result)).hasSize(9);
        assertThat(nextCursor(result)).isNotNull();
    }

    @Test
    void 글_1만건_팔로우_300명에서_200ms_이내() {
        jdbc.update(
                "INSERT INTO member (handle, nickname, role, status)"
                        + " SELECT 'fa' || lpad(g::text, 3, '0'), '작가' || g, 'USER', 'ACTIVE'"
                        + " FROM generate_series(1, 500) g");
        jdbc.update(
                """
                INSERT INTO post (author_id, title, content_md, content_html, excerpt, status,
                                  visibility, edit_version, published_at, first_public_at,
                                  created_at, updated_at)
                SELECT a.id, '글 ' || g, '본문', '<p>본문</p>', '요약', 'PUBLISHED',
                       CASE WHEN g % 10 = 1 THEN 'PRIVATE' ELSE 'PUBLIC' END, 1, t.at,
                       CASE WHEN g % 10 <> 1 THEN t.at END, t.at, t.at
                  FROM generate_series(1, 10000) g
                  JOIN member a ON a.handle = 'fa' || lpad(((g % 500) + 1)::text, 3, '0')
                 CROSS JOIN LATERAL (SELECT TIMESTAMPTZ '2026-01-01 00:00:00+00'
                       + g * interval '17 minutes' AS at) t
                """);
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id)"
                        + " SELECT ?, id FROM member WHERE handle LIKE 'fa%' ORDER BY id LIMIT 300",
                me);
        jdbc.execute("ANALYZE member");
        jdbc.execute("ANALYZE post");
        jdbc.execute("ANALYZE follow");

        CardQuery query = cards.cardQuery(Viewer.anonymous(), CardFilter.followedBy(me), null, 10);
        explainAnalyze(query); // 웜업
        double millis = explainAnalyze(query);

        assertThat(millis).as("피드 첫 페이지 EXPLAIN ANALYZE 실행 시간(ms)").isLessThan(200);
        assertThat(feedQueryService.page(me, null).items()).hasSize(9);
    }

    private double explainAnalyze(CardQuery query) {
        String json =
                jdbcClient
                        .sql("EXPLAIN (ANALYZE, FORMAT JSON) " + query.sql())
                        .params(query.params())
                        .query(String.class)
                        .single();
        Number millis = JsonPath.read(json, "$[0]['Execution Time']");
        return millis.doubleValue();
    }
}
