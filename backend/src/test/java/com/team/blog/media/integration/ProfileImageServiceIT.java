package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.team.blog.media.application.ProfileImageService;
import com.team.blog.media.support.ImageApi;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 프로필 사진 (003 T037 US2, research R14, 001 FR-049): PROFILE presign·complete(256×256) 뒤 001 {@code
 * PATCH /api/me/profile {profileImageId}}가 {@link ProfileImageService#attach}를 부른다.
 */
class ProfileImageServiceIT extends StorageIntegrationTestBase {

    @Autowired ProfileImageService profileImages;

    private long upload(Cookie session, String fixture, boolean complete) throws Exception {
        ImageApi api = new ImageApi(mockMvc);
        byte[] bytes = ImageCompleteIT.fixture(fixture);
        MvcResult presign = api.presign(session, ImageApi.profileBody(bytes.length));
        assertThat(presign.getResponse().getStatus()).isEqualTo(201);
        long id = ImageApi.json(presign).path("imageId").asLong();
        String key =
                jdbc.queryForObject("SELECT storage_key FROM image WHERE id = ?", String.class, id);
        MinioContainerSupport.putDirect(key, "image/webp", bytes);
        if (complete) {
            MvcResult done = api.complete(session, id);
            assertThat(done.getResponse().getStatus())
                    .as(done.getResponse().getContentAsString())
                    .isEqualTo(200);
        }
        return id;
    }

    private MvcResult setProfileImage(Cookie session, Long imageId) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(patch("/api/me/profile"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"profileImageId\":" + imageId + "}"))
                .andReturn();
    }

    private long currentCount(long memberId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM image WHERE uploader_id = ? AND purpose = 'PROFILE'"
                        + " AND status = 'ATTACHED' AND detached_at IS NULL",
                Long.class,
                memberId);
    }

    @Test
    void 완료된_256_WebP를_붙이고_바꾸면_이전_사진에_detached_at() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long first = upload(session, "profile-256.webp", true);
        long second = upload(session, "profile-256.webp", true);

        assertThat(setProfileImage(session, first).getResponse().getStatus()).isEqualTo(200);
        assertThat(profileImages.currentImageId(me)).contains(first);
        assertThat(currentCount(me)).isOne();

        MvcResult replaced = setProfileImage(session, second);
        assertThat(replaced.getResponse().getStatus()).isEqualTo(200);
        assertThat(profileImages.currentImageId(me)).contains(second);
        assertThat(currentCount(me)).isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at IS NOT NULL FROM image WHERE id = ?",
                                Boolean.class,
                                first))
                .isTrue();

        assertThat(setProfileImage(session, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(profileImages.currentImageId(me)).isEmpty();
    }

    @Test
    void 남의_사진_글_사진_완료_전_사진은_400_INVALID_PROFILE_IMAGE() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        Cookie other = TestLogin.loginAs(mockMvc, members().member().create());
        long othersPhoto = upload(other, "profile-256.webp", true);
        long incomplete = upload(session, "profile-256.webp", false);
        ImageApi api = new ImageApi(mockMvc);
        byte[] original = ImageCompleteIT.fixture("photo-1920.webp");
        byte[] thumb = ImageCompleteIT.fixture("thumb-640.webp");
        long postPhoto =
                ImageApi.json(
                                api.presign(
                                        session,
                                        ImageApi.postBody(
                                                "image/webp",
                                                original.length,
                                                "image/webp",
                                                thumb.length)))
                        .path("imageId")
                        .asLong();
        jdbc.update("UPDATE image SET width = 1920, height = 1440 WHERE id = ?", postPhoto);

        for (long id : List.of(othersPhoto, incomplete, postPhoto, 999_999L)) {
            MvcResult result = setProfileImage(session, id);
            assertThat(result.getResponse().getStatus()).as("사진 %d", id).isEqualTo(400);
            JsonNode body = ImageApi.json(result);
            assertThat(body.path("errors").get(0).path("field").asString())
                    .isEqualTo("profileImageId");
            assertThat(body.path("errors").get(0).path("code").asString())
                    .isEqualTo("INVALID_PROFILE_IMAGE");
        }
        assertThat(profileImages.currentImageId(me)).isEmpty();
    }

    @Test
    void 가로세로가_256이_아니면_complete_400_PROFILE_SIZE_INVALID() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long id = upload(session, "profile-255x256.webp", false);

        MvcResult result = new ImageApi(mockMvc).complete(session, id);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(ImageApi.json(result).path("details").path("reason").asString())
                .isEqualTo("PROFILE_SIZE_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Long.class, id))
                .isZero();
    }
}
