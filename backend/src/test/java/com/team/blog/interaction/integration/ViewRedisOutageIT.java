package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.application.ViewFlushJob;
import com.team.blog.interaction.support.LikeApi;
import com.team.blog.interaction.support.ViewFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Redis 장애 중 조회 (009 T027, US3 #7, SC-008, FR-029, 01 H8). */
class ViewRedisOutageIT extends IntegrationTestBase {

    @Autowired ViewFlushJob flushJob;

    @Test
    void 장애_중에도_상세는_열리고_조회_기록은_204_복구_뒤_정상() throws Exception {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        String handle =
                jdbc.queryForObject("SELECT handle FROM member WHERE id = ?", String.class, author);
        LikeApi api = new LikeApi(mockMvc);
        Cookie vid = new Cookie("vid", UUID.randomUUID().toString());

        try (RedisOutage outage = RedisOutage.start()) {
            MvcResult detail = new ReadingApi(mockMvc).detail(null, postId);
            assertThat(ReadingApi.status(detail)).isEqualTo(200);
            MvcResult shell = mockMvc.perform(get("/@{h}/posts/{id}", handle, postId)).andReturn();
            assertThat(shell.getResponse().getStatus()).isEqualTo(200);
            assertThat(LikeApi.status(api.view(null, postId, Map.of(), vid))).isEqualTo(204);
            assertThat(LikeApi.status(api.view(null, postId))).as("쿠키 없는 비회원").isEqualTo(204);
            flushJob.flush();
        }
        assertThat(new ViewFixtures(jdbc, redis).viewCount(postId)).isZero();

        // docker pause는 연결을 끊지 않아, 시간 초과로 건너뛴 명령이 복구 순간 늦게 실행될 수 있다(실제 장애에서는 사라짐).
        // 그래서 복구 직후 한 번 반영해 기준을 잡고, 그 뒤 새 방문자 한 명이 정확히 1만 더하는지 본다.
        flushJob.flush();
        long baseline = new ViewFixtures(jdbc, redis).viewCount(postId);
        assertThat(baseline).as("장애 중 방문자 두 명보다 많이 세지 않는다").isBetween(0L, 2L);
        Cookie fresh = new Cookie("vid", UUID.randomUUID().toString());
        assertThat(LikeApi.status(api.view(null, postId, Map.of(), fresh))).isEqualTo(204);
        assertThat(LikeApi.status(api.view(null, postId, Map.of(), fresh))).isEqualTo(204);
        flushJob.flush();
        assertThat(new ViewFixtures(jdbc, redis).viewCount(postId)).isEqualTo(baseline + 1);
    }
}
