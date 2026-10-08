package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.ids;
import static com.team.blog.discovery.support.SearchApi.read;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.trending.TrendingQueryService;
import com.team.blog.discovery.application.trending.TrendingSnapshotJob;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.discovery.support.SearchApi;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.discovery.support.TrendingRedisHelper;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 트렌딩 API (012 T024, US2 #5·#6, FR-010~013, SC-002·SC-004·SC-007). */
class TrendingApiIT extends IntegrationTestBase {

    @Autowired TrendingQueryService trending;
    @Autowired TrendingSnapshotJob job;

    private final List<Long> authors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        authors.clear();
    }

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    private TrendingRedisHelper helper() {
        return new TrendingRedisHelper(redis);
    }

    /** 반응이 있는 공개 글 n개 (작성자 n/3명, 좋아요 수가 클수록 앞). 순위 순 번호. */
    private List<Long> posts(int n) {
        SearchFixtures fx = new SearchFixtures(jdbc);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i % 3 == 0) {
                authors.add(members().member().create());
            }
            ids.add(
                    fx.post(authors.get(authors.size() - 1))
                            .age(Duration.ofHours(2))
                            .title("트렌딩 " + i)
                            .likes(1000 - i)
                            .create());
        }
        return ids;
    }

    private List<Long> readAll(String firstCursor, List<Long> seen) throws Exception {
        String cursor = firstCursor;
        while (cursor != null) {
            MvcResult page = api().trending(cursor);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            assertThat(ids(page)).isNotEmpty().hasSizeLessThanOrEqualTo(9);
            seen.addAll(ids(page));
            cursor = read(page, "$.nextCursor");
        }
        return seen;
    }

    @Test
    void US2_5_새_스냅샷이_생겨도_처음_순위를_끝까지_중복_누락_0() throws Exception {
        List<Long> ranked = posts(25);
        helper().write("202610080000", ranked);

        MvcResult first = api().trending(null);
        assertThat(status(first)).isEqualTo(200);
        assertThat(ids(first)).containsExactlyElementsOf(ranked.subList(0, 9));
        // 10분 뒤 순위가 뒤집힌 새 스냅샷
        List<Long> reversed = new ArrayList<>(ranked);
        java.util.Collections.reverse(reversed);
        helper().write("202610080010", reversed);

        List<Long> seen = readAll(read(first, "$.nextCursor"), new ArrayList<>(ids(first)));

        assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(ranked);
        // 새로 연 첫 페이지는 새 순위
        assertThat(ids(api().trending(null))).containsExactlyElementsOf(reversed.subList(0, 9));
    }

    @Test
    void US2_6_안_본_글_2개가_비공개_휴지통이면_건너뛰고_9개() throws Exception {
        List<Long> ranked = posts(20);
        helper().write("202610080000", ranked);
        MvcResult first = api().trending(null);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", ranked.get(9));
        jdbc.update(
                "UPDATE post SET deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                ranked.get(11));

        MvcResult second = api().trending(read(first, "$.nextCursor"));

        List<Long> expected = new ArrayList<>(ranked.subList(9, 20));
        expected.remove(ranked.get(9));
        expected.remove(ranked.get(11));
        assertThat(ids(second)).containsExactlyElementsOf(expected);
        assertThat((Object) read(second, "$.nextCursor")).isNull();
    }

    @Test
    void 백개_끝에서_nextCursor_null() throws Exception {
        List<Long> ranked = posts(100);
        job.refresh(Instant.now());

        MvcResult first = api().trending(null);
        List<Long> seen = readAll(read(first, "$.nextCursor"), new ArrayList<>(ids(first)));

        assertThat(seen).hasSize(100).containsExactlyElementsOf(ranked);
    }

    @Test
    void 만료된_스냅샷의_커서는_410_본문_고정() throws Exception {
        helper().write("202610080000", posts(12));
        String cursor = read(api().trending(null), "$.nextCursor");
        helper().expire("202610080000");

        MvcResult result = api().trending(cursor);

        assertThat(status(result)).isEqualTo(410);
        assertThat(body(result))
                .isEqualTo(
                        "{\"code\":\"SNAPSHOT_EXPIRED\",\"message\":\"순위가 새로 바뀌었어요\","
                                + "\"errors\":[],\"details\":null}");
    }

    @Test
    void 다른_목록의_커서는_400() throws Exception {
        posts(12);
        String homeCursor =
                ReadingApi.read(new ReadingApi(mockMvc).home(null, null), "$.nextCursor");
        MvcResult result = api().trending(homeCursor);
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        assertThat(status(api().trending("@@@"))).isEqualTo(400);
    }

    @Test
    void SC007_Redis_정지면_즉시_계산한_첫_9개와_nextCursor_null() throws Exception {
        List<Long> ranked = posts(12);
        helper().write("202610080000", ranked);
        String cursor = read(api().trending(null), "$.nextCursor");

        MvcResult first;
        MvcResult withCursor;
        try (RedisOutage outage = RedisOutage.start()) {
            first = api().trending(null);
            withCursor = api().trending(cursor);
        }

        for (MvcResult result : List.of(first, withCursor)) {
            assertThat(status(result)).as(body(result)).isEqualTo(200);
            assertThat(ids(result)).containsExactlyElementsOf(ranked.subList(0, 9));
            assertThat((Object) read(result, "$.nextCursor")).isNull();
        }
    }

    @Test
    void current가_없으면_즉시_계산() throws Exception {
        List<Long> ranked = posts(4);
        MvcResult result = api().trending(null);
        assertThat(ids(result)).containsExactlyElementsOf(ranked);
        assertThat((Object) read(result, "$.nextCursor")).isNull();
    }

    @Test
    void 빈_순위면_빈_목록() throws Exception {
        helper().write("202610080000", List.of());
        MvcResult result = api().trending(null);
        assertThat(status(result)).isEqualTo(200);
        assertThat(ids(result)).isEmpty();
        assertThat((Object) read(result, "$.nextCursor")).isNull();
    }

    @Test
    void 비회원_200_탈퇴_유예_회원_403() throws Exception {
        helper().write("202610080000", posts(3));
        assertThat(status(api().trending(null))).isEqualTo(200);
        long withdrawn = members().member().status("WITHDRAWN").create();
        MvcResult denied = api().as(TestLogin.loginAs(mockMvc, withdrawn)).trending(null);
        assertThat(status(denied)).isEqualTo(403);
        assertThat((String) read(denied, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
        long member = members().member().create();
        assertThat(status(api().as(TestLogin.loginAs(mockMvc, member)).trending(null)))
                .isEqualTo(200);
    }

    @Test
    void 카드는_홈과_같은_모양() throws Exception {
        List<Long> ranked = posts(3);
        helper().write("202610080000", ranked);
        List<Map<String, Object>> trendingCards = read(api().trending(null), "$.items");
        List<Map<String, Object>> homeCards =
                ReadingApi.read(new ReadingApi(mockMvc).home(null, null), "$.items");
        assertThat(homeCards).containsAll(trendingCards);
    }

    @Test
    void SC002_카드_SQL_1번이고_p95_200ms() throws Exception {
        List<Long> ranked = posts(100);
        helper().write("202610080000", ranked);
        CursorPage<PostCardView> page;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            page = trending.page(null);
            assertThat(scope.count()).isEqualTo(1);
        }
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            trending.page(page.nextCursor());
            assertThat(scope.count()).isEqualTo(1);
        }
        for (int i = 0; i < 10; i++) {
            api().trending(null); // 데우기
        }
        long[] millis = new long[50];
        String cursor = page.nextCursor();
        for (int i = 0; i < millis.length; i++) {
            long started = System.nanoTime();
            MvcResult result = api().trending(i % 2 == 0 ? null : cursor);
            millis[i] = (System.nanoTime() - started) / 1_000_000;
            assertThat(status(result)).isEqualTo(200);
        }
        Arrays.sort(millis);
        long p95 = millis[(int) Math.ceil(millis.length * 0.95) - 1];
        System.out.println("trending p95=" + p95 + "ms");
        assertThat(p95).isLessThan(200);
    }
}
