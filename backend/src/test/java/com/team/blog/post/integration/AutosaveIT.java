package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 자동 저장 {@code PUT /api/posts/{id}/autosave} (002 T064, US3 #1, FR-006·016·019·020·025, B-3
 * ①·B-11).
 */
class AutosaveIT extends IntegrationTestBase {

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    /** 요청 제한(5초에 1번)을 풀어 연속 요청을 시험한다. */
    private void resetRateLimit(long memberId) {
        redis.delete("ratelimit:autosave:" + memberId);
    }

    @Test
    void 받아들이면_버전_1과_저장_시각이고_Redis에만_쓴다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result = api().autosave(session, postId, saveBody("제목", "본문", 0));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(1);
        Instant savedAt = Instant.parse(read(result, "$.savedAt"));
        Map<Object, Object> hash = fixtures().autosaveHash(postId);
        assertThat(hash)
                .containsOnlyKeys("memberId", "title", "contentMd", "version", "savedAt")
                .containsEntry("memberId", String.valueOf(me))
                .containsEntry("title", "제목")
                .containsEntry("contentMd", "본문")
                .containsEntry("version", "1");
        assertThat(Instant.parse((String) hash.get("savedAt"))).isEqualTo(savedAt);
        long ttl = redis.getExpire(AuthoringFixtures.autosaveKey(postId), TimeUnit.SECONDS);
        assertThat(Duration.ofSeconds(ttl))
                .isBetween(Duration.ofHours(24).minusMinutes(1), Duration.ofHours(24));
        assertThat(fixtures().isDirty(postId)).isTrue();
        assertThat(fixtures().snapshot(postId)).contains(before);
    }

    @Test
    void 저장할_때마다_TTL을_늘린다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        api().autosave(session, postId, saveBody("제목", "본문", 0));
        redis.expire(AuthoringFixtures.autosaveKey(postId), Duration.ofMinutes(5));
        resetRateLimit(me);

        assertThat(status(api().autosave(session, postId, saveBody("제목", "본문 2", 1))))
                .isEqualTo(200);
        assertThat(redis.getExpire(AuthoringFixtures.autosaveKey(postId), TimeUnit.HOURS))
                .isGreaterThanOrEqualTo(23);
    }

    @Test
    void 같은_기준_버전으로_다시_보내면_409와_서버_내용() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        api().autosave(session, postId, saveBody("탭 A", "A 본문", 0));
        resetRateLimit(me);

        MvcResult result = api().autosave(session, postId, saveBody("탭 B", "B 본문", 0));

        assertThat(status(result)).isEqualTo(409);
        assertThat((String) read(result, "$.code")).isEqualTo("VERSION_CONFLICT");
        assertThat(((Number) read(result, "$.details.server.version")).longValue()).isEqualTo(1);
        assertThat((String) read(result, "$.details.server.title")).isEqualTo("탭 A");
        assertThat((String) read(result, "$.details.server.contentMd")).isEqualTo("A 본문");
        assertThat((String) read(result, "$.details.server.savedAt")).isNotBlank();
        assertThat(fixtures().autosaveHash(postId)).containsEntry("title", "탭 A");
    }

    @Test
    void 늦게_온_옛_기준_버전_요청은_새_버전을_덮지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        api().autosave(session, postId, saveBody("v1", "v1", 0));
        resetRateLimit(me);
        api().autosave(session, postId, saveBody("v2", "v2", 1));
        resetRateLimit(me);

        MvcResult late = api().autosave(session, postId, saveBody("옛 내용", "옛 내용", 0));

        assertThat(status(late)).isEqualTo(409);
        assertThat(fixtures().autosaveHash(postId))
                .containsEntry("version", "2")
                .containsEntry("title", "v2");
    }

    @Test
    void 남의_글_없는_글_휴지통_글은_404_Redis_키가_있어도() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long others = fixtures().posts().post(owner).title("t").create();
        long trashed = fixtures().posts().post(me).published("PUBLIC").trashed().create();
        fixtures().putAutosave(trashed, me, "보관", "보관", 1, Instant.now(), true);

        for (long postId : List.of(others, fixtures().posts().nonexistentId(), trashed)) {
            resetRateLimit(me);
            MvcResult result = api().autosave(session, postId, saveBody("제목", "본문", 1));
            assertThat(status(result)).as("postId=" + postId).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(fixtures().autosaveHash(trashed)).containsEntry("title", "보관");
        assertThat(fixtures().autosaveHash(others)).isEmpty();
    }

    @Test
    void 제목_101자_본문_100001자는_400() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult title = api().autosave(session, postId, saveBody("가".repeat(101), "본문", 0));
        assertThat(status(title)).isEqualTo(400);
        assertThat((String) read(title, "$.errors[0].code")).isEqualTo("TITLE_TOO_LONG");

        resetRateLimit(me);
        MvcResult content = api().autosave(session, postId, saveBody("제목", "a".repeat(100_001), 0));
        assertThat(status(content)).isEqualTo(400);
        assertThat((String) read(content, "$.errors[0].code")).isEqualTo("CONTENT_TOO_LONG");
        assertThat(fixtures().autosaveHash(postId)).isEmpty();
    }

    @Test
    void 업로드_대기_사진이_있어도_저장하고_태그는_무시한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        Map<String, Object> request = saveBody("제목", "![대기](local:7f3e)", 0);
        request.put("tags", List.of("spring"));

        MvcResult result = api().autosave(session, postId, request);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(fixtures().autosaveHash(postId))
                .containsEntry("contentMd", "![대기](local:7f3e)")
                .doesNotContainKey("tags");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_tag", Long.class)).isZero();
    }

    @Test
    void 발행_글의_자동_저장도_Redis에만_쓰고_발행본은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result = api().autosave(session, postId, saveBody("고친 제목", "고친 본문", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(2);
        assertThat(fixtures().snapshot(postId)).contains(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_draft WHERE post_id = ?",
                                Long.class,
                                postId))
                .isZero();
    }
}
