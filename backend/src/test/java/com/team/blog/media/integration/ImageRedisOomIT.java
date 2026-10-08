package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.team.blog.media.application.ImageUploadService;
import com.team.blog.media.application.PresignCommand;
import com.team.blog.support.StorageIntegrationTestBase;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;

/**
 * Redis 메모리 부족(OOM) 때 presign (003 T095, research R9). 공용 테스트 Redis의 {@code maxmemory}를 잠깐 낮추고
 * {@code noeviction}으로 쓰기를 거부하게 만든 뒤 서비스를 직접 부른다(세션도 Redis라 HTTP 요청은 로그인이 먼저 흔들린다). 끝나면 설정을 되돌린다.
 */
class ImageRedisOomIT extends StorageIntegrationTestBase {

    @Autowired ImageUploadService uploads;

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
    void OOM이면_presign은_503_AUTOSAVE_UNAVAILABLE이고_TEMP_행은_보상_삭제된다() {
        long me = members().member().create();
        PresignCommand command =
                new PresignCommand("POST", "image/webp", 412_345L, "image/webp", 38_211L, null);
        String maxmemory = config("maxmemory");
        String policy = config("maxmemory-policy");
        Throwable thrown;
        try {
            config("maxmemory-policy", "noeviction");
            config("maxmemory", "1");
            thrown = catchThrowable(() -> uploads.presign(me, command));
        } finally {
            config("maxmemory", maxmemory);
            config("maxmemory-policy", policy);
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown.getClass().getSimpleName()).isEqualTo("AutosaveUnavailableException");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Long.class)).isZero();
    }
}
