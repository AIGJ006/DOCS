package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.team.blog.post.application.PostReadService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 발행 (002 T039, US1 #2·#3·#4·#6, FR-026~032, A-6). 사진 판별은 실제 media 어댑터(DB {@code image})를 쓴다. */
class PublishIT extends IntegrationTestBase {

    /** application.yml 기본 {@code blog.image.public-base-url}. */
    private static final String BASE = "http://localhost:9000/blog";

    private static final String KEY_A = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final String THUMB_A =
            "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";
    private static final String KEY_B = "images/2026/10/1c7f2a6d-3b4e-4a8f-8d2b-6e9f8a7b2c3d.webp";
    private static final String KEY_OTHER =
            "images/2026/10/2d8a3b7e-4c5f-4b9a-9e3c-7fa09b8c3d4e.webp";

    @Autowired PostReadService postReadService;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    private long image(long uploader, String key, String thumb) {
        return jdbc.queryForObject(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes) VALUES (?, ?, ?, 'image/webp', 1000) RETURNING id",
                Long.class,
                uploader,
                key,
                thumb);
    }

    private Map<String, Object> postRow(long postId) {
        return jdbc.queryForMap("SELECT * FROM post WHERE id = ?", postId);
    }

    private static Instant instant(Object ts) {
        return ts == null ? null : ((Timestamp) ts).toInstant();
    }

    @Test
    void 임시글을_전체_공개로_발행하면_누구나_읽는다() throws Exception {
        long me = members().member().handle("alice").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody(
                                        "JPA N+1 정리",
                                        "## 문제\n\n<script>alert(1)</script> 본문",
                                        List.of("Spring", "jpa", "spring"),
                                        "PUBLIC",
                                        0));

        assertThat(status(result)).as(EditorApi.body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.url")).isEqualTo("/@alice/posts/" + postId);
        assertThat((String) read(result, "$.publishedAt")).isNotBlank();
        assertThat((String) read(result, "$.firstPublicAt"))
                .isEqualTo(read(result, "$.publishedAt"));
        assertThat((Object) read(result, "$.editedAt")).isNull();
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(1);

        Map<String, Object> row = postRow(postId);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("visibility")).isEqualTo("PUBLIC");
        assertThat(row.get("title")).isEqualTo("JPA N+1 정리");
        assertThat((String) row.get("content_html"))
                .contains("<h3 id=\"h-문제\">문제</h3>")
                .doesNotContain("<script");
        assertThat(row.get("excerpt")).isNotNull();
        assertThat(row.get("render_version")).isEqualTo(RenderVersion.CURRENT);
        assertThat(row.get("edit_version")).isEqualTo(1L);
        assertThat(
                        jdbc.queryForList(
                                "SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                                        + " WHERE pt.post_id = ? ORDER BY pt.position",
                                String.class,
                                postId))
                .containsExactly("spring", "jpa");

        assertThatCode(() -> postReadService.requireReadable(postId, Viewer.anonymous()))
                .doesNotThrowAnyException();
    }

    @Test
    void 빈_제목_공백_본문_업로드_대기_사진이면_세_항목을_모두_돌려주고_글은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult blank =
                api().publish(session, postId, publishBody(" ", "  \n ", List.of(), "PUBLIC", 0));
        assertThat(status(blank)).isEqualTo(400);
        assertThat((String) read(blank, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((List<String>) read(blank, "$.errors[*].code"))
                .containsExactly("TITLE_REQUIRED", "CONTENT_REQUIRED");

        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody(
                                        "", "![대기](local:7f3e)", List.of("🔥hot"), "PUBLIC", 0));
        assertThat(status(result)).isEqualTo(400);
        assertThat((List<String>) read(result, "$.errors[*].field"))
                .containsExactly("title", "contentMd", "tags[0]");
        assertThat((List<String>) read(result, "$.errors[*].code"))
                .containsExactly("TITLE_REQUIRED", "PENDING_IMAGES", "INVALID_TAG");

        Map<String, Object> row = postRow(postId);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("edit_version")).isEqualTo(0L);
    }

    @Test
    void 나만_보기로_발행한_뒤_전체_공개로_다시_발행하면_그때가_최초_공개() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult first =
                api().publish(session, postId, publishBody("제목", "본문", List.of(), "PRIVATE", 0));
        assertThat(status(first)).isEqualTo(200);
        assertThat((Object) read(first, "$.firstPublicAt")).isNull();
        Instant publishedAt = instant(postRow(postId).get("published_at"));

        MvcResult second =
                api().publish(session, postId, publishBody("제목", "본문 2", List.of(), "PUBLIC", 1));
        assertThat(status(second)).isEqualTo(200);
        Map<String, Object> row = postRow(postId);
        Instant firstPublic = instant(row.get("first_public_at"));
        assertThat(firstPublic).isAfter(publishedAt);
        assertThat(instant(row.get("published_at"))).isEqualTo(publishedAt);
        assertThat(instant(row.get("edited_at"))).isEqualTo(firstPublic);
        assertThat((String) read(second, "$.editedAt")).isNotBlank();

        api().publish(session, postId, publishBody("제목", "본문 3", List.of(), "PRIVATE", 2));
        api().publish(session, postId, publishBody("제목", "본문 4", List.of(), "PUBLIC", 3));
        Map<String, Object> after = postRow(postId);
        assertThat(instant(after.get("first_public_at"))).isEqualTo(firstPublic);
        assertThat(after.get("edit_version")).isEqualTo(4L);
    }

    @Test
    void 기준_버전이_다르면_409와_서버_내용이고_글은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures()
                        .posts()
                        .post(me)
                        .title("서버 제목")
                        .contentMd("서버 본문")
                        .editVersion(3)
                        .create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result =
                api().publish(session, postId, publishBody("내 제목", "내 본문", List.of(), "PUBLIC", 2));

        assertThat(status(result)).isEqualTo(409);
        assertThat((String) read(result, "$.code")).isEqualTo("VERSION_CONFLICT");
        assertThat((String) read(result, "$.details.server.title")).isEqualTo("서버 제목");
        assertThat((String) read(result, "$.details.server.contentMd")).isEqualTo("서버 본문");
        assertThat(((Number) read(result, "$.details.server.version")).longValue()).isEqualTo(3);
        assertThat((String) read(result, "$.details.server.savedAt")).isNotBlank();
        assertThat(fixtures().snapshot(postId)).contains(before);
    }

    @Test
    void Redis_보관분_버전이_DB보다_크면_그_버전이_현재_버전() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        fixtures().putAutosave(postId, me, "레디스 제목", "레디스 본문", 5, Instant.now(), true);

        MvcResult stale =
                api().publish(session, postId, publishBody("제목", "본문", List.of(), "PUBLIC", 0));
        assertThat(status(stale)).isEqualTo(409);
        assertThat(((Number) read(stale, "$.details.server.version")).longValue()).isEqualTo(5);
        assertThat((String) read(stale, "$.details.server.title")).isEqualTo("레디스 제목");

        MvcResult ok =
                api().publish(session, postId, publishBody("제목", "본문", List.of(), "PUBLIC", 5));
        assertThat(status(ok)).isEqualTo(200);
        assertThat(((Number) read(ok, "$.version")).longValue()).isEqualTo(6);
        assertThat(postRow(postId).get("edit_version")).isEqualTo(6L);
        // 커밋 후 정리: 확인한 버전(5) 이하인 보관분은 지운다(⑨)
        assertThat(fixtures().autosaveHash(postId)).isEmpty();
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void 남이_올린_사진은_연결하지_않고_링크로_바꾸며_원래_주인의_연결은_그대로() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        long otherImage = image(other, KEY_OTHER, null);
        long otherPost = fixtures().posts().post(other).published("PUBLIC").create();
        jdbc.update(
                "INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", otherPost, otherImage);
        jdbc.update("UPDATE image SET status = 'ATTACHED' WHERE id = ?", otherImage);

        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody(
                                        "제목",
                                        "![남의 사진](" + BASE + "/" + KEY_OTHER + ")",
                                        List.of(),
                                        "PUBLIC",
                                        0));

        assertThat(status(result)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_image WHERE post_id = ?",
                                Long.class,
                                postId))
                .isZero();
        assertThat((String) postRow(postId).get("content_html"))
                .doesNotContain("<img")
                .contains("[이미지] 남의 사진");
        assertThat(postRow(postId).get("thumbnail_url")).isNull();
        assertThat(
                        jdbc.queryForList(
                                "SELECT post_id FROM post_image WHERE image_id = ?",
                                Long.class,
                                otherImage))
                .containsExactly(otherPost);
    }

    @Test
    void 작성자_사진은_연결하고_다시_발행_때_빠진_사진은_연결을_끊는다() throws Exception {
        long me = members().member().create();
        long imageA = image(me, KEY_A, THUMB_A);
        long imageB = image(me, KEY_B, null);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        String both = "![a](" + BASE + "/" + KEY_A + ")\n\n![b](" + BASE + "/" + KEY_B + ")";
        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody("제목", both, List.of(), "PUBLIC", 0))))
                .isEqualTo(200);

        assertThat(
                        jdbc.queryForList(
                                "SELECT image_id FROM post_image WHERE post_id = ? ORDER BY"
                                        + " image_id",
                                Long.class,
                                postId))
                .containsExactly(imageA, imageB);
        assertThat(
                        jdbc.queryForList(
                                "SELECT status FROM image WHERE id IN (?, ?)",
                                String.class,
                                imageA,
                                imageB))
                .containsOnly("ATTACHED");
        assertThat(postRow(postId).get("thumbnail_url")).isEqualTo(BASE + "/" + THUMB_A);

        String onlyA = "![a](" + BASE + "/" + KEY_A + ")";
        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody("제목", onlyA, List.of(), "PUBLIC", 1))))
                .isEqualTo(200);

        assertThat(
                        jdbc.queryForList(
                                "SELECT image_id FROM post_image WHERE post_id = ?",
                                Long.class,
                                postId))
                .containsExactly(imageA);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                imageB))
                .isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                imageA))
                .isNull();
    }

    @Test
    void 렌더링_부하_제한을_넘으면_400_CONTENT_TOO_COMPLEX이고_글은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody("제목", ">".repeat(25) + " 깊다", List.of(), "PUBLIC", 0));

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("contentMd");
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("CONTENT_TOO_COMPLEX");
        assertThat(postRow(postId).get("status")).isEqualTo("DRAFT");
    }

    @Test
    void 남의_글_발행은_404이고_글은_그대로() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        long postId = fixtures().posts().post(owner).title("t").contentMd("m").create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result =
                api().publish(
                                TestLogin.loginAs(mockMvc, me),
                                postId,
                                publishBody("제목", "본문", List.of(), "PUBLIC", 0));

        assertThat(status(result)).isEqualTo(404);
        assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        assertThat(fixtures().snapshot(postId)).contains(before);
    }

    @Test
    void 요청_키가_없거나_UUID가_아니면_400() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        Map<String, Object> body = publishBody("제목", "본문", List.of(), "PUBLIC", 0);

        MvcResult missing = api().publish(session, postId, body, null);
        assertThat(status(missing)).isEqualTo(400);
        assertThat((String) read(missing, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        MvcResult invalid = api().publish(session, postId, body, "not-a-uuid");
        assertThat(status(invalid)).isEqualTo(400);
        assertThat((String) read(invalid, "$.code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThat(postRow(postId).get("status")).isEqualTo("DRAFT");
    }

    @Test
    void 비회원은_요청_키보다_먼저_401() throws Exception {
        MvcResult result =
                api().publish(null, 1L, publishBody("제목", "본문", List.of(), "PUBLIC", 0), null);
        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }
}
