package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.domain.ImagePurpose;
import com.team.blog.media.domain.ImageStatus;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 사진 행 저장소 (003 T010, data-model §1-1·§2). */
class ImageRepositoryIT extends IntegrationTestBase {

    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration WEEK = Duration.ofDays(7);

    @Autowired ImageRepository images;
    @Autowired TransactionTemplate tx;

    private ImageFixtures fixtures() {
        return new ImageFixtures(jdbc);
    }

    @Test
    void insertTemp는_TEMP_완료전_신고_크기로_넣는다() {
        long me = members().member().create();
        Instant now = Instant.parse("2026-10-08T05:00:00Z");
        String key = ImageFixtures.newKey("webp");

        long id =
                images.insertTemp(
                        me,
                        ImagePurpose.POST,
                        key,
                        ImageFixtures.thumbOf(key, "webp"),
                        "image/webp",
                        412345,
                        38211,
                        now);

        ImageRepository.ImageRow row = images.findOwned(id, me).orElseThrow();
        assertThat(row.status()).isEqualTo(ImageStatus.TEMP);
        assertThat(row.purpose()).isEqualTo(ImagePurpose.POST);
        assertThat(row.width()).isNull();
        assertThat(row.completed()).isFalse();
        assertThat(row.sizeBytes()).isEqualTo(412345);
        assertThat(row.thumbSizeBytes()).isEqualTo(38211);
        assertThat(row.createdAt()).isEqualTo(now);
    }

    @Test
    void lockOwned는_남의_것과_없는_것을_빈_값으로() {
        long me = members().member().create();
        long other = members().member().create();
        long mine = fixtures().image(me).incomplete().create();
        long others = fixtures().image(other).create();

        tx.executeWithoutResult(
                s -> {
                    assertThat(images.lockOwned(mine, me)).isPresent();
                    assertThat(images.lockOwned(others, me)).isEmpty();
                    assertThat(images.lockOwned(999_999, me)).isEmpty();
                });
    }

    @Test
    void markCompleted는_크기와_가로_세로를_기록하고_deleteById는_지운다() {
        long me = members().member().create();
        long id = fixtures().image(me).incomplete().create();

        images.markCompleted(id, 400_000, 30_000, 1920, 1080);
        ImageRepository.ImageRow row = images.findOwned(id, me).orElseThrow();
        assertThat(row.completed()).isTrue();
        assertThat(row.sizeBytes()).isEqualTo(400_000);
        assertThat(row.thumbSizeBytes()).isEqualTo(30_000);
        assertThat(row.width()).isEqualTo(1920);
        assertThat(row.height()).isEqualTo(1080);

        assertThat(images.deleteById(id)).isEqualTo(1);
        assertThat(images.findOwned(id, me)).isEmpty();
    }

    @Test
    void 사용량은_내_모든_사진의_원본과_썸네일_합이고_남의_것은_뺀다() {
        long me = members().member().create();
        long other = members().member().create();
        fixtures().image(me).incomplete().size(1000, 100).create(); // TEMP
        fixtures().image(me).attached().size(2000, 200).create(); // 연결
        fixtures().image(me).detachedAt(Instant.now()).size(3000, 300).create(); // 연결 해제 대기
        fixtures().image(me).profile().size(4000, null).attached().create(); // 프로필
        fixtures().image(other).size(9_000_000, 900_000).create();

        assertThat(images.sumUsageBytes(me)).isEqualTo(1000 + 100 + 2000 + 200 + 3000 + 300 + 4000);
        assertThat(images.sumUsageBytes(members().member().create())).isZero();
    }

    @Test
    void 정리_후보는_TEMP_24시간과_연결해제_7일이고_현재_프로필은_빠진다() {
        long me = members().member().create();
        Instant now = Instant.parse("2026-10-08T18:30:00Z");
        long oldTemp =
                fixtures()
                        .image(me)
                        .incomplete()
                        .createdAt(now.minus(25, ChronoUnit.HOURS))
                        .create();
        long oldTempDone = fixtures().image(me).createdAt(now.minus(25, ChronoUnit.HOURS)).create();
        fixtures().image(me).createdAt(now.minus(23, ChronoUnit.HOURS)).create(); // 23시간
        long oldDetached = fixtures().image(me).detachedAt(now.minus(8, ChronoUnit.DAYS)).create();
        fixtures().image(me).detachedAt(now.minus(6, ChronoUnit.DAYS)).create(); // 6일
        fixtures()
                .image(me)
                .profile()
                .attached()
                .createdAt(now.minus(400, ChronoUnit.DAYS))
                .create();
        fixtures().image(me).attached().createdAt(now.minus(30, ChronoUnit.DAYS)).create();

        List<ImageRepository.CleanupCandidate> candidates =
                images.cleanupCandidates(now, DAY, WEEK, 1000);

        assertThat(candidates)
                .extracting(ImageRepository.CleanupCandidate::id)
                .containsExactly(oldTemp, oldTempDone, oldDetached);
        assertThat(images.cleanupCandidates(now, DAY, WEEK, 2)).hasSize(2);
    }

    @Test
    void 조건을_다시_확인해_그사이_다시_연결된_사진은_남긴다() {
        long me = members().member().create();
        Instant now = Instant.parse("2026-10-08T18:30:00Z");
        long a = fixtures().image(me).detachedAt(now.minus(8, ChronoUnit.DAYS)).create();
        long b = fixtures().image(me).detachedAt(now.minus(8, ChronoUnit.DAYS)).create();
        jdbc.update("UPDATE image SET detached_at = NULL WHERE id = ?", b); // 다시 연결됨

        assertThat(images.deleteIfStillEligible(List.of(a, b), now, DAY, WEEK)).containsExactly(a);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Long.class)).isEqualTo(1);
        assertThat(images.deleteIfStillEligible(List.of(), now, DAY, WEEK)).isEmpty();
    }

    @Test
    void 작성자의_완료된_사진만_한_번의_조회로_찾는다() {
        long me = members().member().create();
        long other = members().member().create();
        var done = fixtures().image(me);
        done.create();
        var pending = fixtures().image(me).incomplete();
        pending.create();
        var others = fixtures().image(other);
        others.create();

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            var found =
                    images.findCompletedOwned(
                            List.of(done.storageKey(), pending.storageKey(), others.storageKey()),
                            me);
            assertThat(found)
                    .extracting(ImageRepository.OwnedKeys::storageKey)
                    .containsExactly(done.storageKey());
            assertThat(scope.count()).isEqualTo(1);
        }
        assertThat(images.findCompletedOwned(List.of(), me)).isEmpty();
    }

    @Test
    void 썸네일_키로_원본_키를_찾는다() {
        long me = members().member().create();
        String key = ImageFixtures.newKey("gif");
        String thumb = ImageFixtures.thumbOf(key, "jpg");
        fixtures().image(me).key(key).thumbKey(thumb).contentType("image/gif").create();

        assertThat(images.findByThumbKey(thumb)).contains(key);
        assertThat(images.findByThumbKey(ImageFixtures.newKey("webp"))).isEmpty();
    }
}
