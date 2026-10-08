package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.LikeApi.body;
import static com.team.blog.interaction.support.LikeApi.read;
import static com.team.blog.interaction.support.LikeApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.application.ViewFlushJob;
import com.team.blog.interaction.infra.RedisViewStore;
import com.team.blog.interaction.support.LikeApi;
import com.team.blog.interaction.support.ViewFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 조회 기록 {@code POST /api/posts/{postId}/views} (009 T025, US3 #1~#4·#6, FR-022~026·031, SC-006,
 * research R4·R5). 반영은 {@link ViewFlushJob#flush()}를 직접 불러 확인한다(시험 프로필은 배치가 스스로 돌지 않는다).
 */
class ViewRecordIT extends IntegrationTestBase {

    @Autowired ViewFlushJob flushJob;
    @Autowired RedisViewStore store;

    private long author;
    private long postId;

    @BeforeEach
    void setUp() {
        author = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private LikeApi api() {
        return new LikeApi(mockMvc);
    }

    private ViewFixtures views() {
        return new ViewFixtures(jdbc, redis);
    }

    private static Cookie vid(String value) {
        return new Cookie("vid", value);
    }

    private MvcResult anonymousView(String vid) throws Exception {
        return api().view(null, postId, Map.of(), vid(vid));
    }

    @Test
    void 이십사시간_1회() throws Exception {
        String v1 = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            assertThat(status(anonymousView(v1))).isEqualTo(204);
        }
        assertThat(status(anonymousView(UUID.randomUUID().toString()))).isEqualTo(204);
        assertThat(status(anonymousView(UUID.randomUUID().toString()))).isEqualTo(204);
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        for (int i = 0; i < 3; i++) {
            assertThat(status(api().view(member, postId))).isEqualTo(204);
        }

        flushJob.flush();

        assertThat(views().viewCount(postId)).isEqualTo(4);
        assertThat(views().dailyTotal(postId)).isEqualTo(4);
    }

    @Test
    void 동시_50번은_1번() throws Exception {
        String v = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                Callable<Integer> call = () -> status(anonymousView(v));
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return call.call();
                                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                assertThat(f.get(60, TimeUnit.SECONDS)).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }

        flushJob.flush();

        assertThat(views().viewCount(postId)).isEqualTo(1);
    }

    @Test
    void 삼십분_5회_설정() throws Exception {
        // 설정값(blog.view.dedupe-window·max-per-window)은 저장소 호출의 인자로 그대로 간다. 시험 전용 컨텍스트를 늘리지 않으려고
        // 같은 Lua를 짧은 기간(1초)·5회로 직접 부른다.
        LocalDate today = LocalDate.now();
        int counted = 0;
        for (int i = 0; i < 7; i++) {
            if (store.record(postId, "m:777", today, Duration.ofSeconds(1), 5)
                    == RedisViewStore.RecordOutcome.COUNTED) {
                counted++;
            }
        }
        assertThat(counted).isEqualTo(5);

        Thread.sleep(1_200);
        assertThat(store.record(postId, "m:777", today, Duration.ofSeconds(1), 5))
                .as("기간이 지나면 다시 센다")
                .isEqualTo(RedisViewStore.RecordOutcome.COUNTED);

        flushJob.flush();
        assertThat(views().viewCount(postId)).isEqualTo(6);
    }

    @Test
    void 작성자는_세지_않는다() throws Exception {
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        assertThat(status(api().view(authorSession, postId))).isEqualTo(204);
        long privatePost =
                new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        assertThat(status(api().view(authorSession, privatePost))).isEqualTo(204);

        flushJob.flush();

        assertThat(views().viewCount(postId)).isZero();
        assertThat(views().viewCount(privatePost)).isZero();
        assertThat(views().keys("view:seen:*")).isEmpty();
    }

    @Test
    void 관리자는_세지_않는다() throws Exception {
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());
        assertThat(status(api().view(admin, postId))).isEqualTo(204);

        flushJob.flush();

        assertThat(views().viewCount(postId)).isZero();
        assertThat(views().keys("view:seen:*")).isEmpty();
    }

    @Test
    void 봇과_prefetch는_세지_않는다() throws Exception {
        String v = UUID.randomUUID().toString();
        assertThat(
                        status(
                                api().view(
                                                null,
                                                postId,
                                                Map.of("User-Agent", "Mozilla/5.0 Googlebot/2.1"),
                                                vid(v))))
                .isEqualTo(204);
        assertThat(status(api().view(null, postId, Map.of("Sec-Purpose", "prefetch"), vid(v))))
                .isEqualTo(204);
        assertThat(status(api().view(null, postId, Map.of("Purpose", "prefetch"), vid(v))))
                .isEqualTo(204);

        flushJob.flush();

        assertThat(views().viewCount(postId)).isZero();
        assertThat(views().keys("view:seen:*")).isEmpty();
    }

    @Test
    void 센_것과_안_센_것의_응답이_같다() throws Exception {
        String v = UUID.randomUUID().toString();
        MvcResult counted = anonymousView(v);
        MvcResult duplicate = anonymousView(v);
        MvcResult bot =
                api().view(null, postId, Map.of("User-Agent", "Slackbot-LinkExpanding"), vid(v));
        MvcResult prefetch = api().view(null, postId, Map.of("Sec-Purpose", "prefetch"), vid(v));
        MvcResult author = api().view(TestLogin.loginAs(mockMvc, this.author), postId);
        MvcResult admin =
                api().view(
                                TestLogin.loginAs(
                                        mockMvc, members().member().role("ADMIN").create()),
                                postId);

        for (MvcResult other : List.of(duplicate, bot, prefetch)) {
            assertThat(status(other)).isEqualTo(status(counted)).isEqualTo(204);
            assertThat(body(other)).isEqualTo(body(counted)).isEmpty();
            assertThat(headers(other)).isEqualTo(headers(counted));
        }
        for (MvcResult other : List.of(author, admin)) {
            assertThat(status(other)).isEqualTo(204);
            assertThat(body(other)).isEmpty();
            assertThat(other.getResponse().getHeader("Cache-Control"))
                    .isEqualTo(counted.getResponse().getHeader("Cache-Control"));
        }
    }

    private static Map<String, List<String>> headers(MvcResult result) {
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : result.getResponse().getHeaderNames()) {
            List<String> values = new ArrayList<>(result.getResponse().getHeaders(name));
            Collections.sort(values);
            headers.put(name, values);
        }
        return headers;
    }

    @Test
    void 볼_수_없는_글은_404() throws Exception {
        PostFixtures posts = new PostFixtures(jdbc);
        for (long id :
                new long[] {
                    posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE),
                    posts.create(author, PostFixtures.State.DRAFT),
                    posts.create(author, PostFixtures.State.HIDDEN),
                    posts.nonexistentId()
                }) {
            MvcResult result =
                    api().view(
                                    null,
                                    id,
                                    Map.of("User-Agent", "Googlebot"),
                                    vid(UUID.randomUUID().toString()));
            assertThat(status(result)).as("글 " + id).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(status(api().view(null, "abc"))).isEqualTo(404);
    }

    @Test
    void 같은_방문자_61번째는_429() throws Exception {
        String v = UUID.randomUUID().toString();
        for (int i = 0; i < 60; i++) {
            assertThat(status(anonymousView(v))).as("요청 " + (i + 1)).isEqualTo(204);
        }
        MvcResult result = anonymousView(v);
        assertThat(status(result)).isEqualTo(429);
        assertThat((String) read(result, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(status(anonymousView(UUID.randomUUID().toString())))
                .as("다른 방문자는 따로 센다")
                .isEqualTo(204);
    }

    @Test
    void 상세를_먼저_열면_첫_조회와_새로고침이_같은_방문자() throws Exception {
        ReadingApi reading = new ReadingApi(mockMvc);
        MvcResult detail = reading.detail(null, postId);
        Cookie issued = detail.getResponse().getCookie("vid");
        assertThat(issued).as("비회원 상세 응답에 vid 쿠키").isNotNull();
        assertThat(UUID.fromString(issued.getValue())).isNotNull();
        assertThat(issued.isHttpOnly()).isTrue();
        assertThat(issued.getPath()).isEqualTo("/");
        assertThat(issued.getMaxAge()).isEqualTo(365 * 24 * 3600);
        assertThat(detail.getResponse().getHeaders("Set-Cookie"))
                .filteredOn(h -> h.startsWith("vid="))
                .singleElement()
                .asString()
                .contains("SameSite=Lax");

        // 1초 뒤 첫 기록 + 새로고침(상세 다시 열기 + 기록) — 쿠키가 있으면 다시 주지 않는다
        assertThat(status(anonymousView(issued.getValue()))).isEqualTo(204);
        MvcResult again =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/posts/{id}", postId)
                                        .cookie(vid(issued.getValue())))
                        .andReturn();
        assertThat(again.getResponse().getCookie("vid")).isNull();
        assertThat(status(anonymousView(issued.getValue()))).isEqualTo(204);

        // 회원에게는 주지 않고, 볼 수 없는 글이어도 비회원이면 준다(존재를 드러내지 않음)
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        assertThat(reading.detail(member, postId).getResponse().getCookie("vid")).isNull();
        long privatePost =
                new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult hidden = reading.detail(null, privatePost);
        assertThat(ReadingApi.status(hidden)).isEqualTo(404);
        assertThat(hidden.getResponse().getCookie("vid")).isNotNull();

        flushJob.flush();
        assertThat(views().viewCount(postId)).isEqualTo(1);
    }

    @Test
    void 쿠키를_막은_비회원은_같은_날_IP_UA로_한_번() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(status(api().view(null, postId, Map.of("User-Agent", "Mozilla/5.0 X"))))
                    .isEqualTo(204);
        }
        flushJob.flush();
        assertThat(views().viewCount(postId)).isEqualTo(1);
    }
}
