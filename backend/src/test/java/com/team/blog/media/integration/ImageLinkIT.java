package com.team.blog.media.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.support.ImageApi;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 내 사진은 나만 내 글에 연결된다 (003 T036 US2, FR-022·FR-023, 23 §6-1). 사진은 실제 presign·저장소·complete 흐름으로 만든다.
 */
class ImageLinkIT extends StorageIntegrationTestBase {

    @Autowired CoreProperties core;

    private EditorApi editor() {
        return new EditorApi(mockMvc);
    }

    /** 사진 하나를 올리고(완료 확인까지 하거나 말거나) 공개 주소와 행 번호를 돌려준다. */
    private Map<String, Object> upload(Cookie session, boolean complete) throws Exception {
        ImageApi api = new ImageApi(mockMvc);
        byte[] original = ImageCompleteIT.fixture("photo-1920.webp");
        byte[] thumb = ImageCompleteIT.fixture("thumb-640.webp");
        MvcResult presign =
                api.presign(
                        session,
                        ImageApi.postBody(
                                "image/webp", original.length, "image/webp", thumb.length));
        long id = ImageApi.json(presign).path("imageId").asLong();
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT storage_key, thumb_storage_key FROM image WHERE id = ?", id);
        String key = (String) row.get("storage_key");
        MinioContainerSupport.putDirect(key, "image/webp", original);
        MinioContainerSupport.putDirect((String) row.get("thumb_storage_key"), "image/webp", thumb);
        if (complete) {
            assertThat(api.complete(session, id).getResponse().getStatus()).isEqualTo(200);
        }
        return Map.of("id", id, "key", key, "url", core.image().publicBaseUrl() + "/" + key);
    }

    private Map<String, Object> postRow(long postId) {
        return jdbc.queryForMap(
                "SELECT content_html, thumbnail_url FROM post WHERE id = ?", postId);
    }

    @Test
    void US2_1_남의_사진은_연결되지_않고_링크로_보인다() throws Exception {
        Cookie a = TestLogin.loginAs(mockMvc, members().member().create());
        Cookie b = TestLogin.loginAs(mockMvc, members().member().create());
        Map<String, Object> photo = upload(a, true);
        long aPost = editor().createPostId(a);
        assertThat(
                        status(
                                editor().publish(
                                                a,
                                                aPost,
                                                publishBody(
                                                        "A 글",
                                                        "![](" + photo.get("url") + ")",
                                                        List.of(),
                                                        "PUBLIC",
                                                        0))))
                .isEqualTo(200);
        Map<String, Object> before =
                jdbc.queryForMap(
                        "SELECT status, detached_at FROM image WHERE id = ?", photo.get("id"));

        long bPost = editor().createPostId(b);
        MvcResult published =
                editor().publish(
                                b,
                                bPost,
                                publishBody(
                                        "B 글",
                                        "![남의 사진](" + photo.get("url") + ")",
                                        List.of(),
                                        "PUBLIC",
                                        0));

        assertThat(status(published)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_image WHERE post_id = ?",
                                Long.class,
                                bPost))
                .isZero();
        assertThat((String) postRow(bPost).get("content_html"))
                .doesNotContain("<img")
                .contains("[이미지] 남의 사진");
        assertThat((String) postRow(aPost).get("content_html"))
                .contains("<img src=\"" + photo.get("url") + "\"");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT status, detached_at FROM image WHERE id = ?",
                                photo.get("id")))
                .isEqualTo(before);
        assertThat(before.get("status")).isEqualTo("ATTACHED");
    }

    @Test
    void US2_2_남의_업로드_번호로_complete하면_관리자도_404() throws Exception {
        Cookie a = TestLogin.loginAs(mockMvc, members().member().create());
        Map<String, Object> photo = upload(a, false);
        ImageApi api = new ImageApi(mockMvc);

        for (Cookie other :
                List.of(
                        TestLogin.loginAs(mockMvc, members().member().create()),
                        TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create()))) {
            MvcResult result = api.complete(other, (long) photo.get("id"));
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            JsonNode body = ImageApi.json(result);
            assertThat(body.path("code").asString()).isEqualTo("NOT_FOUND");
            assertThat(body.path("message").asString()).isEqualTo("볼 수 없는 페이지예요");
            assertThat(body.path("errors").size()).isZero();
            assertThat(body.path("details").isNull()).isTrue();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT width FROM image WHERE id = ?",
                                Integer.class,
                                photo.get("id")))
                .isNull();
    }

    @Test
    void 완료_전_내_사진_주소는_연결되지_않는다() throws Exception {
        Cookie a = TestLogin.loginAs(mockMvc, members().member().create());
        Map<String, Object> photo = upload(a, false);
        long post = editor().createPostId(a);

        editor().publish(
                        a,
                        post,
                        publishBody("제목", "![](" + photo.get("url") + ")", List.of(), "PUBLIC", 0));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_image WHERE post_id = ?",
                                Long.class,
                                post))
                .isZero();
        assertThat((String) postRow(post).get("content_html")).doesNotContain("<img");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM image WHERE id = ?",
                                String.class,
                                photo.get("id")))
                .isEqualTo("TEMP");
    }

    @Test
    void 완료_전_사진은_수동_저장_연결에서도_빠진다() throws Exception {
        long me = members().member().create();
        Cookie a = TestLogin.loginAs(mockMvc, me);
        Map<String, Object> done = upload(a, true);
        Map<String, Object> notDone = upload(a, false);
        long post = editor().createPostId(a);

        // 렌더러를 거치지 않는 연결 경로 자체에서도 완료 전 사진은 빠진다 (R5)
        imageService.attachPostImages(
                post, me, List.of((String) done.get("key"), (String) notDone.get("key")));

        assertThat(
                        jdbc.queryForList(
                                "SELECT image_id FROM post_image WHERE post_id = ?",
                                Long.class,
                                post))
                .containsExactly((Long) done.get("id"));
    }

    @Autowired com.team.blog.media.application.ImageService imageService;

    @Test
    void US2_3_비공개_글의_사진도_익명_GET은_200이고_익명_목록은_403() throws Exception {
        Cookie a = TestLogin.loginAs(mockMvc, members().member().create());
        Map<String, Object> photo = upload(a, true);
        long post = editor().createPostId(a);
        editor().publish(
                        a,
                        post,
                        publishBody(
                                "비공개", "![](" + photo.get("url") + ")", List.of(), "PRIVATE", 0));

        String direct = MinioContainerSupport.publicBaseUrl() + "/" + photo.get("key");
        assertThat(StorageHttp.get(direct).statusCode()).isEqualTo(200);
        assertThat(
                        StorageHttp.get(MinioContainerSupport.endpoint() + "/blog?list-type=2")
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        StorageHttp.get(
                                        MinioContainerSupport.endpoint()
                                                + "/blog?list-type=2&prefix=images/")
                                .statusCode())
                .isEqualTo(403);
    }
}
