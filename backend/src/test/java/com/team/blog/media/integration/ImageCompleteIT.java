package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.support.ImageApi;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 업로드 완료 확인 (003 T022 US1·T069 US6, contracts/openapi.yaml {@code completeImageUpload}, research
 * R5·R24). 브라우저처럼 presign 응답의 주소로 저장소에 직접 PUT한 뒤 complete를 부른다.
 */
class ImageCompleteIT extends StorageIntegrationTestBase {

    @Autowired CoreProperties core;

    private ImageApi api;
    private long me;
    private Cookie session;

    @BeforeEach
    void setUp() {
        api = new ImageApi(mockMvc);
        me = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
    }

    static byte[] fixture(String name) {
        try (InputStream in = ImageCompleteIT.class.getResourceAsStream("/images/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** presign 결과 (행 번호·키). */
    record Ticket(long imageId, String key, String thumbKey, JsonNode body) {}

    Ticket presign(String contentType, byte[] original, String thumbType, byte[] thumb)
            throws Exception {
        String body =
                thumb == null
                        ? ImageApi.profileBody(original.length)
                        : ImageApi.postBody(contentType, original.length, thumbType, thumb.length);
        MvcResult result = api.presign(session, body);
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(201);
        JsonNode json = ImageApi.json(result);
        long id = json.path("imageId").asLong();
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT storage_key, thumb_storage_key FROM image WHERE id = ?", id);
        return new Ticket(
                id, (String) row.get("storage_key"), (String) row.get("thumb_storage_key"), json);
    }

    /** presign → 원본·썸네일 PUT. */
    Ticket upload(String contentType, byte[] original, String thumbType, byte[] thumb)
            throws Exception {
        Ticket ticket = presign(contentType, original, thumbType, thumb);
        assertThat(put(ticket.body().path("upload"), original)).isEqualTo(200);
        if (thumb != null) {
            assertThat(put(ticket.body().path("thumbUpload"), thumb)).isEqualTo(200);
        }
        return ticket;
    }

    static int put(JsonNode target, byte[] bytes) {
        Map<String, String> headers = new LinkedHashMap<>();
        target.path("headers")
                .properties()
                .forEach(e -> headers.put(e.getKey(), e.getValue().asString()));
        return StorageHttp.put(target.path("url").asString(), headers, bytes);
    }

    private long rows(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Long.class, id);
    }

    private void assertRejected(MvcResult result, Ticket ticket, String reason) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(400);
        JsonNode body = ImageApi.json(result);
        assertThat(body.path("code").asString()).isEqualTo("IMAGE_REJECTED");
        if (reason != null) {
            assertThat(body.path("details").path("reason").asString()).isEqualTo(reason);
        }
        assertThat(body.path("errors").isArray()).isTrue();
        assertGone(ticket);
    }

    private void assertGone(Ticket ticket) {
        assertThat(rows(ticket.imageId())).as("행").isZero();
        assertThat(MinioContainerSupport.exists(ticket.key())).as("원본 객체").isFalse();
        if (ticket.thumbKey() != null) {
            assertThat(MinioContainerSupport.exists(ticket.thumbKey())).as("썸네일 객체").isFalse();
        }
    }

    @Test
    void 정상_WebP는_200이고_행에_실제_크기와_가로세로가_적힌다() throws Exception {
        byte[] original = fixture("photo-1920.webp");
        byte[] thumb = fixture("thumb-640.webp");
        Ticket ticket = upload("image/webp", original, "image/webp", thumb);

        MvcResult result = api.complete(session, ticket.imageId());

        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
        JsonNode body = ImageApi.json(result);
        String base = core.image().publicBaseUrl();
        assertThat(body.path("imageId").asLong()).isEqualTo(ticket.imageId());
        assertThat(body.path("url").asString()).isEqualTo(base + "/" + ticket.key());
        assertThat(body.path("thumbUrl").asString()).isEqualTo(base + "/" + ticket.thumbKey());
        assertThat(body.path("contentType").asString()).isEqualTo("image/webp");
        assertThat(body.path("width").asInt()).isEqualTo(1920);
        assertThat(body.path("height").asInt()).isEqualTo(1440);
        assertThat(body.path("sizeBytes").asLong()).isEqualTo(original.length);

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT width, height, size_bytes, thumb_size_bytes, status FROM image WHERE id = ?",
                        ticket.imageId());
        assertThat(row.get("width")).isEqualTo(1920);
        assertThat(row.get("height")).isEqualTo(1440);
        assertThat(((Number) row.get("size_bytes")).longValue()).isEqualTo(original.length);
        assertThat(((Number) row.get("thumb_size_bytes")).longValue()).isEqualTo(thumb.length);
        assertThat(row.get("status")).isEqualTo("TEMP");
    }

    @Test
    void 사파리_대체_JPEG와_PNG도_통과한다() throws Exception {
        Ticket jpeg =
                upload(
                        "image/jpeg",
                        fixture("photo-1920.jpg"),
                        "image/jpeg",
                        fixture("thumb-640.jpg"));
        MvcResult jpegResult = api.complete(session, jpeg.imageId());
        assertThat(jpegResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(ImageApi.json(jpegResult).path("thumbUrl").asString()).endsWith("_thumb.jpg");

        Ticket png =
                upload(
                        "image/png",
                        fixture("photo-800.png"),
                        "image/webp",
                        fixture("thumb-640.webp"));
        MvcResult pngResult = api.complete(session, png.imageId());
        assertThat(pngResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(ImageApi.json(pngResult).path("width").asInt()).isEqualTo(800);
        assertThat(ImageApi.json(pngResult).path("url").asString()).endsWith(".png");
    }

    @Test
    void 프로필_사진은_256x256만_통과한다() throws Exception {
        Ticket ok = upload("image/webp", fixture("profile-256.webp"), null, null);
        MvcResult result = api.complete(session, ok.imageId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(ImageApi.json(result).path("thumbUrl").isNull()).isTrue();

        Ticket wrong = upload("image/webp", fixture("profile-255x256.webp"), null, null);
        assertRejected(api.complete(session, wrong.imageId()), wrong, "PROFILE_SIZE_INVALID");
    }

    @Test
    void US1_3_확장자_위장은_TYPE_MISMATCH이고_두_객체와_행이_지워진다() throws Exception {
        Ticket ticket =
                upload(
                        "image/jpeg",
                        fixture("png-named.jpg"),
                        "image/webp",
                        fixture("thumb-640.webp"));

        assertRejected(api.complete(session, ticket.imageId()), ticket, "TYPE_MISMATCH");
    }

    @Test
    void 썸네일_641px는_THUMBNAIL_INVALID() throws Exception {
        Ticket ticket =
                upload(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-641.webp"));

        assertRejected(api.complete(session, ticket.imageId()), ticket, "THUMBNAIL_INVALID");
    }

    @Test
    void 원본_4097px는_DIMENSION_EXCEEDED() throws Exception {
        Ticket ticket =
                upload(
                        "image/jpeg",
                        fixture("wide-4097.jpg"),
                        "image/webp",
                        fixture("thumb-640.webp"));

        assertRejected(api.complete(session, ticket.imageId()), ticket, "DIMENSION_EXCEEDED");
    }

    @Test
    void 잘린_파일은_CORRUPT() throws Exception {
        Ticket ticket =
                upload(
                        "image/jpeg",
                        fixture("truncated.jpg"),
                        "image/webp",
                        fixture("thumb-640.webp"));

        assertRejected(api.complete(session, ticket.imageId()), ticket, "CORRUPT");
    }

    @Test
    void 파일을_올리지_않으면_IMAGE_NOT_UPLOADED이고_행이_지워진다() throws Exception {
        Ticket ticket =
                presign(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-640.webp"));
        // 썸네일만 올림 — 원본이 없다
        assertThat(put(ticket.body().path("thumbUpload"), fixture("thumb-640.webp")))
                .isEqualTo(200);

        MvcResult result = api.complete(session, ticket.imageId());

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(ImageApi.json(result).path("code").asString()).isEqualTo("IMAGE_NOT_UPLOADED");
        assertGone(ticket);
    }

    @Test
    void 다른_회원의_사진과_없는_사진은_404() throws Exception {
        Ticket ticket =
                upload(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-640.webp"));
        Cookie other = TestLogin.loginAs(mockMvc, members().member().create());

        MvcResult others = api.complete(other, ticket.imageId());
        MvcResult missing = api.complete(session, ticket.imageId() + 1000);

        for (MvcResult result : List.of(others, missing)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(ImageApi.json(result).path("code").asString()).isEqualTo("NOT_FOUND");
            assertThat(ImageApi.json(result).path("message").asString()).isEqualTo("볼 수 없는 페이지예요");
        }
        assertThat(rows(ticket.imageId())).isOne();
        assertThat(MinioContainerSupport.exists(ticket.key())).isTrue();
    }

    @Test
    void 두_번_부르면_두_번째도_같은_200이다() throws Exception {
        Ticket ticket =
                upload(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-640.webp"));

        MvcResult first = api.complete(session, ticket.imageId());
        MvcResult second = api.complete(session, ticket.imageId());

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void 동시에_두_번_불러도_500이_없다() throws Exception {
        Ticket ticket =
                upload(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-640.webp"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> api.complete(session, ticket.imageId()).getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT width FROM image WHERE id = ?",
                                Integer.class,
                                ticket.imageId()))
                .isEqualTo(1920);
    }

    @Test
    void 저장소가_멈추면_503이고_행은_남는다() throws Exception {
        Ticket ticket =
                upload(
                        "image/webp",
                        fixture("photo-1920.webp"),
                        "image/webp",
                        fixture("thumb-640.webp"));
        MinioContainerSupport.pause();
        MvcResult result;
        try {
            result = api.complete(session, ticket.imageId());
        } finally {
            MinioContainerSupport.resume();
        }

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(ImageApi.json(result).path("code").asString())
                .isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(rows(ticket.imageId())).isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT width FROM image WHERE id = ?",
                                Integer.class,
                                ticket.imageId()))
                .isNull();

        // 저장소가 돌아오면 다시 부를 수 있다
        assertThat(api.complete(session, ticket.imageId()).getResponse().getStatus())
                .isEqualTo(200);
    }
}
