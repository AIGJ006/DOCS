package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.application.LikeService;
import com.team.blog.interaction.application.ViewOutcome;
import com.team.blog.interaction.application.ViewRecordService;
import com.team.blog.interaction.application.ViewRecordService.ViewRequest;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;

/**
 * Redis 메모리 부족(OOM) 경로 (009 T044, research R13). 공용 테스트 Redis의 {@code maxmemory}를 잠깐 낮추고 {@code
 * noeviction}으로 쓰기를 거부하게 만든 뒤 서비스를 직접 부른다(003 {@code ImageRedisOomIT}와 같은 방식 — 세션도 Redis라 HTTP 요청은
 * 로그인이 먼저 흔들린다). 좋아요는 요청 제한 쓰기에서 503 {@code AutosaveUnavailableException}, 조회 기록은 건너뛰고 204와 같은
 * 결과({@code SKIPPED_REDIS}).
 */
class LikeViewRedisOomIT extends IntegrationTestBase {

    @Autowired LikeService likes;
    @Autowired ViewRecordService views;

    private void config(String name, String value) {
        redis.execute(
                (RedisCallback<Void>)
                        c -> {
                            c.serverCommands().setConfig(name, value);
                            return null;
                        });
    }

    private String config(String name) {
        Properties p =
                redis.execute((RedisCallback<Properties>) c -> c.serverCommands().getConfig(name));
        return p.getProperty(name);
    }

    @Test
    void OOM이면_좋아요는_503이고_조회_기록은_건너뛴다() {
        long author = members().member().create();
        long reader = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Viewer viewer = new Viewer(reader, Role.USER, MemberStatus.ACTIVE, true);
        ViewRequest request =
                new ViewRequest(
                        "Mozilla/5.0", null, null, UUID.randomUUID().toString(), "198.51.100.9");
        String maxmemory = config("maxmemory");
        String policy = config("maxmemory-policy");
        Throwable thrown;
        ViewOutcome outcome;
        try {
            config("maxmemory-policy", "noeviction");
            config("maxmemory", "1");
            thrown = catchThrowable(() -> likes.like(viewer, postId));
            outcome = views.record(Viewer.anonymous(), postId, request);
        } finally {
            config("maxmemory", maxmemory);
            config("maxmemory-policy", policy);
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown.getClass().getSimpleName()).isEqualTo("AutosaveUnavailableException");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_like", Long.class)).isZero();
        assertThat(outcome).isEqualTo(ViewOutcome.SKIPPED_REDIS);
    }
}
