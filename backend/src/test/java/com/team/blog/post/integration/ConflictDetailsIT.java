package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 충돌 응답의 서버 내용 (002 T096, FR-020·024, SC-007, US5 #5). 자동 저장·수동 저장·발행의 409 {@code details.server}는
 * 현재 버전 출처(Redis 보관분 → 작업본 → 글)의 제목·본문·버전·저장 시각이고, 409는 서버 내용을 바꾸지 않는다. 덮어쓰기는 사용자가 서버 버전을 기준으로 다시
 * 보낼 때만 된다.
 */
class ConflictDetailsIT extends IntegrationTestBase {

    private static final Instant REDIS_SAVED_AT = Instant.parse("2026-10-07T05:03:12.123456Z");

    enum Action {
        AUTOSAVE,
        SAVE,
        PUBLISH
    }

    enum Source {
        REDIS,
        WORKING_COPY,
        POST
    }

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    private MvcResult perform(Action action, Cookie session, long postId, String text, long base)
            throws Exception {
        return switch (action) {
            case AUTOSAVE -> {
                redis.keys("ratelimit:autosave:*").forEach(redis::delete);
                yield api().autosave(session, postId, saveBody(text, text, base));
            }
            case SAVE -> api().save(session, postId, saveBody(text, text, base));
            case PUBLISH ->
                    api().publish(
                                    session,
                                    postId,
                                    publishBody(text, text, List.of(), "PUBLIC", base));
        };
    }

    /** 현재 버전 출처를 준비하고 기대하는 서버 내용을 돌려준다. */
    private Expected arrange(Source source, long me) {
        return switch (source) {
            case REDIS -> {
                long postId = fixtures().posts().post(me).published("PUBLIC").create();
                fixtures().putAutosave(postId, me, "Redis 제목", "Redis 본문", 4, REDIS_SAVED_AT, true);
                yield new Expected(postId, "Redis 제목", "Redis 본문", 4, REDIS_SAVED_AT);
            }
            case WORKING_COPY -> {
                long postId = fixtures().publishedWithWorkingCopy(me, "작업본 제목", "작업본 본문");
                Instant savedAt =
                        jdbc.queryForObject(
                                        "SELECT updated_at FROM post_draft WHERE post_id = ?",
                                        Timestamp.class,
                                        postId)
                                .toInstant();
                yield new Expected(postId, "작업본 제목", "작업본 본문", 2, savedAt);
            }
            case POST -> {
                long postId =
                        fixtures()
                                .posts()
                                .post(me)
                                .title("글 제목")
                                .contentMd("글 본문")
                                .editVersion(3)
                                .create();
                Instant savedAt =
                        jdbc.queryForObject(
                                        "SELECT updated_at FROM post WHERE id = ?",
                                        Timestamp.class,
                                        postId)
                                .toInstant();
                yield new Expected(postId, "글 제목", "글 본문", 3, savedAt);
            }
        };
    }

    record Expected(long postId, String title, String contentMd, long version, Instant savedAt) {}

    private Map<String, Object> state(long postId) {
        Map<String, Object> post =
                jdbc.queryForMap(
                        "SELECT title, content_md, status, edit_version, updated_at FROM post WHERE"
                                + " id = ?",
                        postId);
        List<Map<String, Object>> draft =
                jdbc.queryForList(
                        "SELECT title, content_md, edit_version, updated_at FROM post_draft"
                                + " WHERE post_id = ?",
                        postId);
        return Map.of("post", post, "draft", draft, "redis", fixtures().autosaveHash(postId));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Action.class)
    void 충돌_응답의_서버_내용은_현재_버전_출처이고_서버는_바뀌지_않는다(Action action) throws Exception {
        for (Source source : Source.values()) {
            long me = members().member().create();
            Cookie session = TestLogin.loginAs(mockMvc, me);
            Expected expected = arrange(source, me);
            Map<String, Object> before = state(expected.postId());

            MvcResult result = perform(action, session, expected.postId(), "내 편집", 1);

            String row = action + " × " + source;
            assertThat(status(result)).as(row + " " + body(result)).isEqualTo(409);
            assertThat((String) read(result, "$.code")).as(row).isEqualTo("VERSION_CONFLICT");
            assertThat((String) read(result, "$.details.server.title"))
                    .as(row)
                    .isEqualTo(expected.title());
            assertThat((String) read(result, "$.details.server.contentMd"))
                    .as(row)
                    .isEqualTo(expected.contentMd());
            assertThat(((Number) read(result, "$.details.server.version")).longValue())
                    .as(row)
                    .isEqualTo(expected.version());
            assertThat(Instant.parse(read(result, "$.details.server.savedAt")))
                    .as(row)
                    .isEqualTo(expected.savedAt());
            assertThat(state(expected.postId())).as(row + " 서버 내용").isEqualTo(before);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Action.class)
    void 서버_버전을_기준으로_다시_보내면_덮어쓴다(Action action) throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        Expected expected = arrange(Source.REDIS, me);
        assertThat(status(perform(action, session, expected.postId(), "내 편집", 1))).isEqualTo(409);

        MvcResult result = perform(action, session, expected.postId(), "내 편집", 4);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(api().workingCopy(session, expected.postId()), "$.title"))
                .isEqualTo("내 편집");
    }

    @org.junit.jupiter.api.Test
    void 새_임시글로_따로_저장해도_원래_글은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        Expected original = arrange(Source.WORKING_COPY, me);
        Map<String, Object> before = state(original.postId());

        MvcResult created =
                api().createPost(session, Map.of("title", "따로 저장", "contentMd", "내 편집 본문"));

        assertThat(status(created)).as(body(created)).isEqualTo(201);
        long newId = ((Number) read(created, "$.postId")).longValue();
        assertThat(newId).isNotEqualTo(original.postId());
        assertThat((String) read(created, "$.status")).isEqualTo("DRAFT");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT title, content_md, author_id FROM post WHERE id = ?",
                                newId))
                .containsEntry("title", "따로 저장")
                .containsEntry("content_md", "내 편집 본문")
                .containsEntry("author_id", me);
        assertThat(state(original.postId())).isEqualTo(before);
    }
}
