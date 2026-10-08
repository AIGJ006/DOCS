package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.media.application.ImageCleanupJob;
import com.team.blog.media.application.ImagePurgeService;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴 정리 (003 T080 US7, contracts/storage.md §3-2, FR-043). {@code detachAllByUploader(m)} 뒤 m의 모든
 * 사진이 다음 정리 배치에서 지워질 조건이 되고, 다른 회원 사진은 그대로다.
 */
class ImagePurgeServiceIT extends IntegrationTestBase {

    @Autowired ImagePurgeService purge;
    @Autowired ImageCleanupJob cleanup;
    @Autowired TransactionTemplate tx;

    private long count(long uploader) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM image WHERE uploader_id = ?", Long.class, uploader);
    }

    @Test
    void 탈퇴_회원의_모든_사진이_다음_정리에서_지워지고_다른_회원_사진은_그대로다() {
        long m = members().member().create();
        long other = members().member().create();
        ImageFixtures f = new ImageFixtures(jdbc);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        f.image(m).profile().attached().create(); // 현재 프로필
        f.image(m).create(); // 방금 올린 TEMP
        f.image(m).incomplete().create(); // 완료 전 TEMP
        f.image(m).attached().create(); // 글에 연결
        f.image(m).detachedAt(now.minus(Duration.ofDays(1))).create(); // 이미 해제 (1일 전)
        long oldDetached = f.image(m).detachedAt(now.minus(Duration.ofDays(30))).create(); // 오래전 해제
        f.image(other).attached().create();
        f.image(other).create();
        Instant before =
                jdbc.queryForObject(
                        "SELECT detached_at FROM image WHERE id = ?", Instant.class, oldDetached);

        Integer changed = tx.execute(s -> purge.detachAllByUploader(m));

        assertThat(changed).isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM image WHERE uploader_id = ?"
                                        + " AND detached_at <= now() - interval '7 days'",
                                Long.class,
                                m))
                .isEqualTo(6);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Instant.class,
                                oldDetached))
                .isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM image WHERE uploader_id = ? AND detached_at IS NOT NULL",
                                Long.class,
                                other))
                .isZero();

        cleanup.cleanup(Instant.now(), Duration.ofMinutes(30));

        assertThat(count(m)).isZero();
        assertThat(count(other)).isEqualTo(2);
    }

    @Test
    void 트랜잭션_밖에서_부르면_거부한다() {
        assertThatThrownBy(() -> purge.detachAllByUploader(1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
