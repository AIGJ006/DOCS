package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.infra.AutosaveEntry;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 발행·변경 취소 커밋 후 Redis 정리 {@code autosave-release.lua} (002 T016, research B-3 ④·⑤, data-model §4).
 * 입력: 확인한 버전 v0, 새 DB 버전 v1.
 */
class RedisAutosaveStoreReleaseIT extends IntegrationTestBase {

    private static final long POST = 42L;
    private static final long MEMBER = 7L;
    private static final Instant SAVED_AT = Instant.parse("2026-10-02T05:03:12Z");

    @Autowired RedisAutosaveStore store;

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void find는_Hash를_AutosaveEntry로_읽는다() {
        fixtures().putAutosave(POST, MEMBER, "제목", "## 본문\n둘째 줄", 14, SAVED_AT, true);
        assertThat(store.find(POST))
                .contains(new AutosaveEntry(MEMBER, "제목", "## 본문\n둘째 줄", 14, SAVED_AT));
        assertThat(store.find(POST + 1)).isEmpty();
    }

    @Test
    void Redis_버전이_확인한_버전_이하면_키를_지우고_dirty에서_뺀다() {
        AuthoringFixtures f = fixtures();
        f.putAutosave(POST, MEMBER, "t", "m", 5, SAVED_AT, true);
        f.putAutosave(POST + 1, MEMBER, "t", "m", 4, SAVED_AT, true);

        store.release(POST, 5, 6);
        store.release(POST + 1, 5, 6);

        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(POST))).isFalse();
        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(POST + 1))).isFalse();
        assertThat(f.isDirty(POST)).isFalse();
        assertThat(f.isDirty(POST + 1)).isFalse();
    }

    @Test
    void 발행_중에_다른_탭이_저장했으면_지우지_않고_버전을_새_DB_버전_더하기_1로_다시_매긴다() {
        AuthoringFixtures f = fixtures();
        f.putAutosave(POST, MEMBER, "끼어든 내용", "본문", 6, SAVED_AT, true);

        store.release(POST, 5, 6);

        assertThat(f.autosaveHash(POST))
                .containsEntry("version", "7")
                .containsEntry("title", "끼어든 내용")
                .containsEntry("memberId", String.valueOf(MEMBER));
        assertThat(f.isDirty(POST)).isTrue();
        assertThat(store.find(POST)).get().extracting(AutosaveEntry::version).isEqualTo(7L);
    }

    @Test
    void 다시_매길_때_TTL은_유지된다() {
        AuthoringFixtures f = fixtures();
        f.putAutosave(POST, MEMBER, "t", "m", 9, SAVED_AT, true);
        redis.expire(AuthoringFixtures.autosaveKey(POST), java.time.Duration.ofHours(24));

        store.release(POST, 5, 6);

        assertThat(redis.getExpire(AuthoringFixtures.autosaveKey(POST))).isPositive();
    }

    @Test
    void 키가_없으면_아무_일도_없다() {
        store.release(POST, 5, 6);
        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(POST))).isFalse();
        assertThat(fixtures().isDirty(POST)).isFalse();
    }

    @Test
    void delete는_버전과_상관없이_지운다() {
        AuthoringFixtures f = fixtures();
        f.putAutosave(POST, MEMBER, "t", "m", 99, SAVED_AT, true);
        store.delete(POST);
        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(POST))).isFalse();
        assertThat(f.isDirty(POST)).isFalse();
    }

    @Test
    void Redis가_멈추면_조회는_빈_결과_정리는_예외_없이_건너뛴다() {
        fixtures().putAutosave(POST, MEMBER, "t", "m", 3, SAVED_AT, true);
        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(store.find(POST)).isEmpty();
            store.release(POST, 3, 4);
            store.delete(POST);
        }
        // 시간 초과로 포기한 명령은 Redis가 돌아온 뒤 늦게 실행될 수 있다(멈춘 동안 쌓인 요청). 그래서 복구 뒤 상태는 단언하지 않는다.
        assertThat(redis.opsForValue().get("ping-after-outage")).isNull();
    }
}
