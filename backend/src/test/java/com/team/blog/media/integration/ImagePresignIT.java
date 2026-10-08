package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.media.application.ImageUploadService;
import com.team.blog.media.application.PresignCommand;
import com.team.blog.media.support.ImageApi;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.StorageIntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 업로드 준비 (003 T021 US1·T054 US4, contracts/openapi.yaml {@code presignImageUpload}, research R8).
 * 판정 순서: 401 → 403 → 400 → 409 → 429(하루) → 429(1분).
 */
class ImagePresignIT extends StorageIntegrationTestBase {

    private static final String KEY_SHAPE =
            "images/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String WEBP_POST =
            ImageApi.postBody("image/webp", 412_345, "image/webp", 38_211);

    @Autowired ImageUploadService uploads;

    private ImageApi api;

    @BeforeEach
    void setUp() {
        api = new ImageApi(mockMvc);
    }

    private long imageRows() {
        return jdbc.queryForObject("SELECT count(*) FROM image", Long.class);
    }

    private static String code(MvcResult result) {
        return ImageApi.json(result).path("code").asString();
    }

    @Test
    void US1_6_비회원은_401() throws Exception {
        MvcResult result = api.presign(null, WEBP_POST);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(code(result)).isEqualTo("LOGIN_REQUIRED");
        assertThat(imageRows()).isZero();
    }

    @Test
    void 인증_전_회원은_403_EMAIL_NOT_VERIFIED() throws Exception {
        long me = members().member().emailVerified(false).create();
        MvcResult result = api.presign(TestLogin.loginAs(mockMvc, me), WEBP_POST);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(code(result)).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(imageRows()).isZero();
    }

    @Test
    void 탈퇴_유예는_403_ACCOUNT_WITHDRAWN_정지는_403_ACCOUNT_SUSPENDED() throws Exception {
        long withdrawn = members().member().status("WITHDRAWN").create();
        assertThat(code(api.presign(TestLogin.loginAs(mockMvc, withdrawn), WEBP_POST)))
                .isEqualTo("ACCOUNT_WITHDRAWN");

        long suspended = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(7, ChronoUnit.DAYS), "시험");
        MvcResult result = api.presign(session, WEBP_POST);
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(code(result)).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(imageRows()).isZero();
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        long me = members().member().create();
        MvcResult result =
                mockMvc.perform(
                                post("/api/images/presign")
                                        .cookie(TestLogin.loginAs(mockMvc, me))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(WEBP_POST))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(code(result)).isEqualTo("CSRF_REJECTED");
    }

    @Test
    void US1_4_형식과_크기와_썸네일_칸은_400_VALIDATION_FAILED() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());

        assertFieldError(
                api.presign(session, ImageApi.postBody("image/svg+xml", 1000, "image/webp", 100)),
                "contentType",
                "UNSUPPORTED_IMAGE_TYPE");
        assertFieldError(
                api.presign(
                        session, ImageApi.postBody("image/webp", 10_485_761, "image/webp", 100)),
                "size",
                "IMAGE_TOO_LARGE");
        assertFieldError(
                api.presign(
                        session, ImageApi.postBody("image/webp", 1000, "image/webp", 1_048_577)),
                "thumbSize",
                "THUMBNAIL_TOO_LARGE");
        assertFieldError(
                api.presign(session, ImageApi.postBody("image/webp", 1000, "image/png", 100)),
                "thumbContentType",
                "UNSUPPORTED_IMAGE_TYPE");
        assertFieldError(
                api.presign(
                        session,
                        "{\"purpose\":\"POST\",\"contentType\":\"image/jpeg\",\"size\":1000}"),
                "thumbContentType",
                "THUMBNAIL_REQUIRED");
        assertFieldError(
                api.presign(
                        session,
                        "{\"purpose\":\"PROFILE\",\"contentType\":\"image/webp\",\"size\":1000,"
                                + "\"thumbContentType\":\"image/webp\",\"thumbSize\":100}"),
                "thumbContentType",
                "THUMBNAIL_NOT_ALLOWED");
        assertFieldError(
                api.presign(
                        session,
                        "{\"purpose\":\"PROFILE\",\"contentType\":\"image/png\",\"size\":1000}"),
                "contentType",
                "UNSUPPORTED_IMAGE_TYPE");
        assertThat(imageRows()).isZero();
    }

    @Test
    void 파일_이름_칸을_보내면_400이고_저장하지_않는다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        String body =
                "{\"purpose\":\"POST\",\"contentType\":\"image/webp\",\"size\":1000,"
                        + "\"thumbContentType\":\"image/webp\",\"thumbSize\":100,"
                        + "\"fileName\":\"IMG_0001.jpg\"}";

        MvcResult result = api.presign(session, body);

        assertFieldError(result, "fileName", "UNKNOWN_FIELD");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("IMG_0001");
        assertThat(imageRows()).isZero();
    }

    @Test
    void 정상이면_201_TEMP_완료전_행과_업로드_주소_두_개() throws Exception {
        long me = members().member().create();
        MvcResult result = api.presign(TestLogin.loginAs(mockMvc, me), WEBP_POST);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = ImageApi.json(result);
        long imageId = body.path("imageId").asLong();
        var row =
                jdbc.queryForMap(
                        "SELECT uploader_id, storage_key, thumb_storage_key, content_type, size_bytes,"
                                + " thumb_size_bytes, width, height, status, purpose FROM image WHERE id = ?",
                        imageId);
        assertThat(row.get("uploader_id")).isEqualTo(me);
        assertThat(row.get("status")).isEqualTo("TEMP");
        assertThat(row.get("purpose")).isEqualTo("POST");
        assertThat(row.get("width")).isNull();
        assertThat(row.get("size_bytes")).isEqualTo(412_345);
        assertThat(row.get("thumb_size_bytes")).isEqualTo(38_211);
        String key = (String) row.get("storage_key");
        String thumb = (String) row.get("thumb_storage_key");
        assertThat(key).matches(KEY_SHAPE + "\\.webp");
        assertThat(thumb).isEqualTo(key.replace(".webp", "_thumb.webp"));

        JsonNode upload = body.path("upload");
        assertThat(upload.path("method").asString()).isEqualTo("PUT");
        assertThat(upload.path("url").asString())
                .startsWith(MinioContainerSupport.endpoint() + "/blog/" + key + "?");
        assertThat(upload.path("headers").path("Content-Type").asString()).isEqualTo("image/webp");
        assertThat(upload.path("headers").path("Cache-Control").asString())
                .isEqualTo("public, max-age=31536000, immutable");
        assertThat(body.path("thumbUpload").path("url").asString())
                .startsWith(MinioContainerSupport.endpoint() + "/blog/" + thumb + "?");
        assertThat(Instant.parse(body.path("expiresAt").asString()))
                .isBetween(Instant.now().plusSeconds(240), Instant.now().plusSeconds(310));
    }

    @Test
    void 사파리_대체_JPEG와_프로필은_썸네일_형식을_따로_받는다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());

        JsonNode jpeg =
                ImageApi.json(
                        api.presign(
                                session,
                                ImageApi.postBody("image/gif", 3_100_000, "image/jpeg", 52_000)));
        String key =
                jdbc.queryForObject(
                        "SELECT thumb_storage_key FROM image WHERE id = ?",
                        String.class,
                        jpeg.path("imageId").asLong());
        assertThat(key).endsWith("_thumb.jpg");

        MvcResult profile = api.presign(session, ImageApi.profileBody(23_000));
        assertThat(profile.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = ImageApi.json(profile);
        assertThat(body.path("thumbUpload").isNull()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT purpose FROM image WHERE id = ?",
                                String.class,
                                body.path("imageId").asLong()))
                .isEqualTo("PROFILE");
    }

    @Test
    void US1_7_1분에_21번째는_429_TOO_MANY_REQUESTS이고_거부된_요청의_행은_남지_않는다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        for (int i = 0; i < 20; i++) {
            assertThat(api.presign(session, WEBP_POST).getResponse().getStatus()).isEqualTo(201);
        }

        MvcResult denied = api.presign(session, WEBP_POST);

        assertThat(denied.getResponse().getStatus()).isEqualTo(429);
        assertThat(code(denied)).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(denied.getResponse().getHeader("Retry-After")))
                .isBetween(1, 60);
        assertThat(imageRows()).isEqualTo(20);
    }

    @Test
    void Redis_장애_중에는_요청_제한을_건너뛴다() {
        // 세션도 Redis에 있어 장애 중 HTTP 요청은 비회원(401)이 된다 — 제한 건너뛰기는 서비스에서 직접 확인한다
        long me = members().member().create();
        PresignCommand command =
                new PresignCommand("POST", "image/webp", 412_345L, "image/webp", 38_211L, null);
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 21; i++) {
                assertThat(uploads.presign(me, command).imageId()).as("%d번째", i + 1).isPositive();
            }
        }
        assertThat(imageRows()).isEqualTo(21);
    }

    // ------------------------------------------------------------------ US4 한도 (T054)

    private static final long GB = 1_073_741_824L;

    /** 그 회원이 이미 {@code bytes}만큼 쓰고 있게 한다 (10MB 행 여러 개 + 나머지 한 행). */
    private void use(long memberId, long bytes) {
        long tenMb = 10_000_000L;
        long rows = bytes / tenMb;
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height)"
                        + " SELECT ?, 'images/2026/10/' || gen_random_uuid() || '.webp', 'image/webp',"
                        + " ?, 100, 100 FROM generate_series(1, ?)",
                memberId,
                (int) tenMb,
                (int) rows);
        long rest = bytes - rows * tenMb;
        if (rest > 0) {
            jdbc.update(
                    "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width,"
                            + " height) VALUES (?, 'images/2026/10/' || gen_random_uuid() || '.webp',"
                            + " 'image/webp', ?, 100, 100)",
                    memberId,
                    (int) rest);
        }
    }

    private String dailyKey(long memberId) {
        return "img:daily:"
                + memberId
                + ":"
                + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
                        .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private Integer daily(long memberId) {
        String v = redis.opsForValue().get(dailyKey(memberId));
        return v == null ? null : Integer.valueOf(v);
    }

    @Test
    void US4_1_용량을_넘으면_409_STORAGE_QUOTA_EXCEEDED와_details() throws Exception {
        long me = members().member().create();
        use(me, GB - 400_000);
        long before = imageRows();

        MvcResult result = api.presign(TestLogin.loginAs(mockMvc, me), WEBP_POST);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = ImageApi.json(result);
        assertThat(body.path("code").asString()).isEqualTo("STORAGE_QUOTA_EXCEEDED");
        assertThat(body.path("details").path("usedBytes").asLong()).isEqualTo(GB - 400_000);
        assertThat(body.path("details").path("quotaBytes").asLong()).isEqualTo(GB);
        assertThat(imageRows()).isEqualTo(before);
        assertThat(daily(me)).as("409면 하루 장수를 세지 않는다").isNull();
    }

    @Test
    void US4_2_동시_10건이어도_합계가_한도를_넘지_않는다() throws Exception {
        long me = members().member().create();
        use(me, GB - 35_000_000);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        String tenMb = ImageApi.postBody("image/webp", 10_000_000, "image/webp", 1_000_000);

        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(10);
        java.util.List<Integer> statuses;
        try {
            java.util.List<java.util.concurrent.Callable<Integer>> calls =
                    java.util.Collections.nCopies(
                            10, () -> api.presign(session, tenMb).getResponse().getStatus());
            statuses = new java.util.ArrayList<>();
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).doesNotContain(500).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(3);
        long used =
                jdbc.queryForObject(
                        "SELECT sum(size_bytes::bigint + coalesce(thumb_size_bytes, 0)) FROM image"
                                + " WHERE uploader_id = ?",
                        Long.class,
                        me);
        assertThat(used).isLessThanOrEqualTo(GB);
    }

    @Test
    void US4_3_하루_201번째는_429_DAILY_UPLOAD_LIMIT와_다음_0시까지_Retry_After() throws Exception {
        long me = members().member().create();
        redis.opsForValue().set(dailyKey(me), "200");
        java.time.ZonedDateTime now =
                java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        long untilMidnight =
                java.time.Duration.between(
                                now, now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()))
                        .toSeconds();

        MvcResult result = api.presign(TestLogin.loginAs(mockMvc, me), WEBP_POST);

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(code(result)).isEqualTo("DAILY_UPLOAD_LIMIT");
        assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After")))
                .isBetween(untilMidnight - 5, untilMidnight + 1);
        assertThat(daily(me)).isEqualTo(200);
        assertThat(imageRows()).as("거부된 요청의 행은 보상 삭제").isZero();
    }

    @Test
    void 판정_순서는_칸_오류_400_다음_용량_409_다음_하루_429() throws Exception {
        long me = members().member().create();
        use(me, GB);
        redis.opsForValue().set(dailyKey(me), "200");
        Cookie session = TestLogin.loginAs(mockMvc, me);

        assertThat(
                        code(
                                api.presign(
                                        session,
                                        ImageApi.postBody(
                                                "image/svg+xml", 1000, "image/webp", 100))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(api.presign(session, WEBP_POST))).isEqualTo("STORAGE_QUOTA_EXCEEDED");
    }

    @Test
    void 일분_제한에_걸리면_하루_장수가_늘지_않는다() throws Exception {
        long me = members().member().create();
        redis.opsForValue().set("ratelimit:image:" + me, "20", java.time.Duration.ofSeconds(50));
        redis.opsForValue().set(dailyKey(me), "5");

        MvcResult result = api.presign(TestLogin.loginAs(mockMvc, me), WEBP_POST);

        assertThat(code(result)).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(daily(me)).isEqualTo(5);
    }

    @Test
    void 완료_확인이_실패해도_하루_장수는_돌려주지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long imageId = ImageApi.json(api.presign(session, WEBP_POST)).path("imageId").asLong();
        assertThat(daily(me)).isEqualTo(1);

        assertThat(code(api.complete(session, imageId))).isEqualTo("IMAGE_NOT_UPLOADED");

        assertThat(daily(me)).isEqualTo(1);
    }

    private static void assertFieldError(MvcResult result, String field, String code)
            throws Exception {
        assertThat(result.getResponse().getStatus()).as(field).isEqualTo(400);
        JsonNode body = ImageApi.json(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("errors").valueStream())
                .as(result.getResponse().getContentAsString())
                .anySatisfy(
                        e -> {
                            assertThat(e.path("field").asString()).isEqualTo(field);
                            assertThat(e.path("code").asString()).isEqualTo(code);
                        });
    }
}
