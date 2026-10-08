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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 수동 저장 {@code PUT /api/posts/{id}/working-copy} (002 T066, FR-008·009·015, D-3, B-3 ②). */
class ManualSaveIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void 임시글을_같은_요청_안에서_DB에_반영한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult result = api().save(session, postId, saveBody("수동 제목", "수동 본문", 0));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(1);
        Instant savedAt = Instant.parse(read(result, "$.savedAt"));
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version, updated_at FROM post WHERE id = ?",
                        postId);
        assertThat(row.get("title")).isEqualTo("수동 제목");
        assertThat(row.get("content_md")).isEqualTo("수동 본문");
        assertThat(row.get("edit_version")).isEqualTo(1L);
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(savedAt);
    }

    @Test
    void Redis_보관분이_만료돼도_에디터를_열면_같은_내용() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        api().save(session, postId, saveBody("남는 제목", "남는 본문", 0));

        redis.delete(AuthoringFixtures.autosaveKey(postId));
        MvcResult opened = api().workingCopy(session, postId);

        assertThat((String) read(opened, "$.title")).isEqualTo("남는 제목");
        assertThat((String) read(opened, "$.contentMd")).isEqualTo("남는 본문");
        assertThat(((Number) read(opened, "$.version")).longValue()).isEqualTo(1);
    }

    @Test
    void 발행_글은_작업본을_만들고_발행본은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result = api().save(session, postId, saveBody("고친 제목", "고친 본문", 1));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(fixtures().snapshot(postId)).contains(before);
        Map<String, Object> draft =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version FROM post_draft WHERE post_id = ?",
                        postId);
        assertThat(draft)
                .containsEntry("title", "고친 제목")
                .containsEntry("content_md", "고친 본문")
                .containsEntry("edit_version", 2L);
        assertThat((Boolean) read(api().workingCopy(session, postId), "$.editing")).isTrue();
    }

    @Test
    void 수동_저장_뒤_다른_탭의_같은_기준_버전_자동_저장은_409() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        api().save(session, postId, saveBody("수동", "수동", 0));

        MvcResult other = api().autosave(session, postId, saveBody("다른 탭", "다른 탭", 0));

        assertThat(status(other)).isEqualTo(409);
        assertThat(((Number) read(other, "$.details.server.version")).longValue()).isEqualTo(1);
    }

    @Test
    void 수동_저장_때_작성자_사진을_글에_연결한다() throws Exception {
        long me = members().member().create();
        long imageId =
                jdbc.queryForObject(
                        "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height)"
                                + " VALUES (?, ?, 'image/webp', 1000, 640, 480) RETURNING id",
                        Long.class,
                        me,
                        KEY);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        api().save(session, postId, saveBody("사진", "![a](" + BASE + "/" + KEY + ")", 0));

        assertThat(
                        jdbc.queryForList(
                                "SELECT image_id FROM post_image WHERE post_id = ?",
                                Long.class,
                                postId))
                .containsExactly(imageId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM image WHERE id = ?", String.class, imageId))
                .isEqualTo("ATTACHED");
    }

    @Test
    void 기준_버전이_다르면_409이고_DB는_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures().posts().post(me).title("서버").contentMd("서버").editVersion(4).create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result = api().save(session, postId, saveBody("내 것", "내 것", 2));

        assertThat(status(result)).isEqualTo(409);
        assertThat(((Number) read(result, "$.details.server.version")).longValue()).isEqualTo(4);
        assertThat(fixtures().snapshot(postId)).contains(before);
    }

    @Test
    void 남의_글_휴지통_글은_404() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long others = fixtures().posts().post(owner).create();
        long trashed = fixtures().posts().post(me).published("PUBLIC").trashed().create();

        for (long postId : List.of(others, trashed)) {
            MvcResult result = api().save(session, postId, saveBody("제목", "본문", 1));
            assertThat(status(result)).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
    }
}
