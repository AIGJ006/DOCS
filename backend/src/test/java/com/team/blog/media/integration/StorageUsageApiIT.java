package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.media.support.ImageApi;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 내 사진 저장 공간 (003 T055 US4, contracts/openapi.yaml {@code getMyStorageUsage}, research R25,
 * FR-014·017).
 */
class StorageUsageApiIT extends IntegrationTestBase {

    private MvcResult usage(Cookie session) throws Exception {
        var request = get("/api/me/storage");
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String dailyKey(long memberId) {
        return "img:daily:"
                + memberId
                + ":"
                + LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @Test
    void 내_TEMP_연결_연결해제_프로필_사진의_합계이고_남의_사진은_빠진다() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        ImageFixtures images = new ImageFixtures(jdbc);
        images.image(me).size(400_000, 40_000).incomplete().create();
        images.image(me).size(300_000, 30_000).attached().create();
        images.image(me).size(200_000, 20_000).detachedAt(Instant.now()).create();
        images.image(me).profile().size(50_000, null).attached().create();
        images.image(other).size(9_000_000, 900_000).create();
        redis.opsForValue().set(dailyKey(me), "7");

        MvcResult result = usage(TestLogin.loginAs(mockMvc, me));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
        JsonNode body = ImageApi.json(result);
        assertThat(body.path("usedBytes").asLong())
                .isEqualTo(400_000 + 40_000 + 300_000 + 30_000 + 200_000 + 20_000 + 50_000);
        assertThat(body.path("quotaBytes").asLong()).isEqualTo(1_073_741_824L);
        assertThat(body.path("todayCount").asInt()).isEqualTo(7);
        assertThat(body.path("dailyLimit").asInt()).isEqualTo(200);
        JsonNode limits = body.path("limits");
        assertThat(limits.path("maxUploadBytes").asLong()).isEqualTo(10_485_760);
        assertThat(limits.path("maxThumbBytes").asLong()).isEqualTo(1_048_576);
        assertThat(limits.path("maxSourceBytes").asLong()).isEqualTo(52_428_800);
        assertThat(limits.path("longSide").asInt()).isEqualTo(1920);
        assertThat(limits.path("thumbMaxWidth").asInt()).isEqualTo(640);
        assertThat(limits.path("gifMaxSide").asInt()).isEqualTo(1920);
        assertThat(limits.path("gifMaxFrames").asInt()).isEqualTo(300);
    }

    @Test
    void 글을_휴지통에_넣거나_완전히_지워도_정리_전까지_합계는_그대로() throws Exception {
        long me = members().member().create();
        long post = new PostFixtures(jdbc).create(me, PostFixtures.State.TRASHED);
        long image = new ImageFixtures(jdbc).image(me).size(500_000, 50_000).attached().create();
        jdbc.update("INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", post, image);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        assertThat(ImageApi.json(usage(session)).path("usedBytes").asLong()).isEqualTo(550_000);

        jdbc.update("UPDATE image SET detached_at = now() WHERE id = ?", image);
        jdbc.update("DELETE FROM post WHERE id = ?", post);

        assertThat(ImageApi.json(usage(session)).path("usedBytes").asLong()).isEqualTo(550_000);
    }

    @Test
    void 오늘_올린_것이_없으면_0() throws Exception {
        long me = members().member().create();
        assertThat(ImageApi.json(usage(TestLogin.loginAs(mockMvc, me))).path("todayCount").asInt())
                .isZero();
    }

    @Test
    void 비회원_401_인증_전_회원은_200_탈퇴_유예는_403() throws Exception {
        assertThat(usage(null).getResponse().getStatus()).isEqualTo(401);

        long unverified = members().member().emailVerified(false).create();
        assertThat(usage(TestLogin.loginAs(mockMvc, unverified)).getResponse().getStatus())
                .isEqualTo(200);

        long withdrawn = members().member().status("WITHDRAWN").create();
        MvcResult result = usage(TestLogin.loginAs(mockMvc, withdrawn));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(ImageApi.json(result).path("code").asString()).isEqualTo("ACCOUNT_WITHDRAWN");
    }

    @Test
    void Redis_장애면_todayCount는_null(
            @org.springframework.beans.factory.annotation.Autowired
                    com.team.blog.media.application.StorageUsageQuery query) {
        long me = members().member().create();
        try (var outage = com.team.blog.support.RedisOutage.start()) {
            // 세션도 Redis에 있어 HTTP로는 401이 된다 — 조회 서비스를 직접 부른다
            assertThat(query.usage(me).todayCount()).isNull();
        }
    }
}
