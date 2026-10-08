package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageCleanupJob;
import com.team.blog.media.application.ImageProperties;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.media.infra.storage.ImageStorage;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 버려진 사진 정리 (003 T078 US7, research R13, FR-041·042). 기준 시각을 정해 {@link
 * ImageCleanupJob#cleanup(Instant, Duration)}을 부른다 — {@code Clock} Bean을 바꾸는 새 컨텍스트를 만들지 않는다. 저장소
 * 삭제 실패는 실제 저장소를 감싼 가짜를 넣은 작업 객체를 직접 만들어 재현한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class ImageCleanupJobIT extends StorageIntegrationTestBase {

    private static final Duration LONG = Duration.ofMinutes(30);

    @Autowired ImageCleanupJob job;
    @Autowired ImageRepository images;
    @Autowired ImageStorage storage;
    @Autowired ImageProperties properties;

    private Instant now;
    private long me;

    @BeforeEach
    void setUp() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        me = members().member().create();
    }

    private ImageFixtures fixtures() {
        return new ImageFixtures(jdbc);
    }

    /** 저장소에 실제 파일까지 둔 사진. */
    private long stored(ImageFixtures.Builder builder) {
        String key = builder.storageKey();
        long id = builder.create();
        MinioContainerSupport.putDirect(key, "image/webp", new byte[] {1, 2, 3});
        String thumb =
                jdbc.queryForObject(
                        "SELECT thumb_storage_key FROM image WHERE id = ?", String.class, id);
        if (thumb != null) {
            MinioContainerSupport.putDirect(thumb, "image/webp", new byte[] {4});
        }
        return id;
    }

    private boolean row(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM image WHERE id = ?", Long.class, id) > 0;
    }

    private String keyOf(long id) {
        return jdbc.queryForObject("SELECT storage_key FROM image WHERE id = ?", String.class, id);
    }

    @Test
    void US7_1_TEMP_24시간_지나면_완료_전후_모두_파일과_행이_지워진다() {
        ImageFixtures.Builder done =
                fixtures().image(me).createdAt(now.minus(Duration.ofHours(25)));
        ImageFixtures.Builder notDone =
                fixtures().image(me).incomplete().createdAt(now.minus(Duration.ofHours(25)));
        ImageFixtures.Builder young =
                fixtures().image(me).createdAt(now.minus(Duration.ofHours(23)));
        String doneKey = done.storageKey();
        long doneId = stored(done);
        long notDoneId = stored(notDone);
        long youngId = stored(young);

        ImageCleanupJob.Result result = job.cleanup(now, LONG);

        assertThat(row(doneId)).isFalse();
        assertThat(row(notDoneId)).isFalse();
        assertThat(MinioContainerSupport.exists(doneKey)).isFalse();
        assertThat(MinioContainerSupport.exists(ImageFixtures.thumbOf(doneKey, "webp"))).isFalse();
        assertThat(row(youngId)).isTrue();
        assertThat(MinioContainerSupport.exists(keyOf(youngId))).isTrue();
        assertThat(result.deleted()).isEqualTo(2);
    }

    @Test
    void US7_2_연결_해제_7일_지나면_지워지고_6일은_남는다() {
        long old =
                stored(
                        fixtures()
                                .image(me)
                                .detachedAt(now.minus(Duration.ofDays(7).plusMinutes(1))));
        long recent = stored(fixtures().image(me).detachedAt(now.minus(Duration.ofDays(6))));
        long attached =
                stored(fixtures().image(me).attached().createdAt(now.minus(Duration.ofDays(30))));

        job.cleanup(now, LONG);

        assertThat(row(old)).isFalse();
        assertThat(row(recent)).isTrue();
        assertThat(row(attached)).isTrue();
    }

    @Test
    void US7_3_현재_프로필_사진은_오래돼도_남는다() {
        long current =
                stored(
                        fixtures()
                                .image(me)
                                .profile()
                                .attached()
                                .createdAt(now.minus(Duration.ofDays(400))));
        long previous =
                stored(fixtures().image(me).profile().detachedAt(now.minus(Duration.ofDays(8))));

        job.cleanup(now, LONG);

        assertThat(row(current)).isTrue();
        assertThat(MinioContainerSupport.exists(keyOf(current))).isTrue();
        assertThat(row(previous)).isFalse();
    }

    @Test
    void US7_4_저장소_삭제에_실패한_사진은_행이_남고_다음_실행에서_지워진다() {
        ImageFixtures.Builder failing =
                fixtures().image(me).createdAt(now.minus(Duration.ofDays(2)));
        String failingKey = failing.storageKey();
        long failingId = stored(failing);
        long okId = stored(fixtures().image(me).createdAt(now.minus(Duration.ofDays(2))));
        ImageCleanupJob broken =
                new ImageCleanupJob(
                        images,
                        new FailingStorage(storage, failingKey),
                        properties,
                        Clock.fixed(now, ZoneOffset.UTC));

        ImageCleanupJob.Result first = broken.cleanup(now, LONG);

        assertThat(row(failingId)).isTrue();
        assertThat(row(okId)).isFalse();
        assertThat(first.failed()).isEqualTo(1);

        job.cleanup(now.plus(Duration.ofDays(1)), LONG);

        assertThat(row(failingId)).isFalse();
        assertThat(MinioContainerSupport.exists(failingKey)).isFalse();
    }

    @Test
    void 정리_사이_다시_연결된_사진은_행이_남는다() {
        ImageFixtures.Builder builder =
                fixtures().image(me).detachedAt(now.minus(Duration.ofDays(8)));
        long id = stored(builder);
        // 파일을 지운 직후(행 삭제 전) 다시 연결된다
        ImageStorage reattachOnDelete =
                new DelegatingStorage(storage) {
                    @Override
                    public Set<String> deleteAll(Collection<String> keys) {
                        Set<String> failed = super.deleteAll(keys);
                        jdbc.update("UPDATE image SET detached_at = NULL WHERE id = ?", id);
                        return failed;
                    }
                };
        ImageCleanupJob racing =
                new ImageCleanupJob(
                        images, reattachOnDelete, properties, Clock.fixed(now, ZoneOffset.UTC));

        ImageCleanupJob.Result result = racing.cleanup(now, LONG);

        assertThat(row(id)).isTrue();
        assertThat(result.kept()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
    }

    @Test
    void 후보가_1001개면_두_묶음으로_모두_지운다() {
        Instant old = now.minus(Duration.ofDays(3));
        jdbc.update(
                """
                INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type, size_bytes,
                                   width, height, status, purpose, created_at)
                SELECT ?, 'images/2026/10/' || gen_random_uuid() || '.webp', NULL, 'image/webp', 10,
                       10, 10, 'TEMP', 'POST', CAST(? AS timestamptz)
                  FROM generate_series(1, 1001)
                """,
                me,
                old.toString());

        ImageCleanupJob.Result result = job.cleanup(now, LONG);

        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(1001);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image", Long.class)).isZero();
    }

    @Test
    void 지워진_사진의_post_image_행도_함께_지워진다() throws Exception {
        long post =
                new EditorApi(mockMvc)
                        .createPostId(com.team.blog.support.TestLogin.loginAs(mockMvc, me));
        long id = stored(fixtures().image(me).detachedAt(now.minus(Duration.ofDays(8))));
        jdbc.update("INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", post, id);

        job.cleanup(now, LONG);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_image WHERE image_id = ?",
                                Long.class,
                                id))
                .isZero();
    }

    @Test
    void 로그는_한_줄이고_키와_회원_번호가_없다(CapturedOutput output) {
        ImageFixtures.Builder builder =
                fixtures().image(me).createdAt(now.minus(Duration.ofDays(2)));
        String key = builder.storageKey();
        stored(builder);

        job.cleanup(now, LONG);

        List<String> lines =
                output.getAll()
                        .lines()
                        .filter(l -> l.contains("사진 정리"))
                        .collect(Collectors.toList());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
                .doesNotContain(key)
                .doesNotContain("images/")
                .doesNotContain("uploader")
                .doesNotContain("memberId")
                .contains("삭제 1");
    }

    /** 실제 저장소를 그대로 부르는 가짜의 바탕. */
    static class DelegatingStorage implements ImageStorage {
        private final ImageStorage real;

        DelegatingStorage(ImageStorage real) {
            this.real = real;
        }

        @Override
        public UploadTarget prepareUpload(String key, String contentType, long size, Duration ttl) {
            return real.prepareUpload(key, contentType, size, ttl);
        }

        @Override
        public Optional<StoredObject> head(String key) {
            return real.head(key);
        }

        @Override
        public byte[] readHead(String key, int maxBytes) {
            return real.readHead(key, maxBytes);
        }

        @Override
        public InputStream openStream(String key) {
            return real.openStream(key);
        }

        @Override
        public Set<String> deleteAll(Collection<String> keys) {
            return real.deleteAll(keys);
        }

        @Override
        public void put(String key, String contentType, long size, InputStream content) {
            real.put(key, contentType, size, content);
        }
    }

    /** 키 하나의 삭제만 실패로 돌려준다. */
    static class FailingStorage extends DelegatingStorage {
        private final String failingKey;

        FailingStorage(ImageStorage real, String failingKey) {
            super(real);
            this.failingKey = failingKey;
        }

        @Override
        public Set<String> deleteAll(Collection<String> keys) {
            Set<String> others = new LinkedHashSet<>(keys);
            others.remove(failingKey);
            Set<String> failed = new LinkedHashSet<>(super.deleteAll(others));
            if (keys.contains(failingKey)) {
                failed.add(failingKey);
            }
            return failed;
        }
    }
}
