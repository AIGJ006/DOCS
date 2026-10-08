package com.team.blog.media.infra;

import com.team.blog.media.domain.ImagePurpose;
import com.team.blog.media.domain.ImageStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code image} 행 저장소 (003 T016, data-model §1-1·§2). media 모듈 밖에서는 쓰지 않는다. 완료 표시는 {@code width IS
 * NOT NULL}이다(research R5). 시각은 호출자가 {@code Clock}으로 넘긴다(테스트에서 고정).
 */
@Repository
public class ImageRepository {

    /**
     * 사진 행.
     *
     * @param width 가로 ({@code null} = 완료 전)
     */
    public record ImageRow(
            long id,
            long uploaderId,
            String storageKey,
            String thumbStorageKey,
            String contentType,
            int sizeBytes,
            Integer thumbSizeBytes,
            Integer width,
            Integer height,
            ImageStatus status,
            ImagePurpose purpose,
            Instant detachedAt,
            Instant createdAt) {

        public boolean completed() {
            return width != null;
        }
    }

    /** 정리 대상 (원본·썸네일 키). */
    public record CleanupCandidate(long id, String storageKey, String thumbStorageKey) {}

    /** 작성자 사진 판별 결과 (원본 키 → 썸네일 키). */
    public record OwnedKeys(String storageKey, String thumbStorageKey) {}

    private static final String COLUMNS =
            "id, uploader_id, storage_key, thumb_storage_key, content_type, size_bytes,"
                    + " thumb_size_bytes, width, height, status, purpose, detached_at, created_at";

    /** 정리 대상 조건 (research R13). 현재 프로필 사진은 ATTACHED·detached_at NULL이라 걸리지 않는다. */
    private static final String ELIGIBLE =
            "((status = 'TEMP' AND created_at < :tempBefore)"
                    + " OR (detached_at IS NOT NULL AND detached_at < :detachedBefore))";

    private final JdbcClient jdbc;

    public ImageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** presign: TEMP·완료 전(width NULL)·신고 크기로 넣는다. */
    public long insertTemp(
            long uploaderId,
            ImagePurpose purpose,
            String storageKey,
            String thumbStorageKey,
            String contentType,
            int sizeBytes,
            Integer thumbSizeBytes,
            Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,
                                           size_bytes, thumb_size_bytes, status, purpose, created_at)
                        VALUES (:uploader, :key, :thumbKey, :contentType, :size, :thumbSize,
                                'TEMP', :purpose, :now)
                        RETURNING id
                        """)
                .param("uploader", uploaderId)
                .param("key", storageKey)
                .param("thumbKey", thumbStorageKey)
                .param("contentType", contentType)
                .param("size", sizeBytes)
                .param("thumbSize", thumbSizeBytes)
                .param("purpose", purpose.name())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single();
    }

    /** 내가 올린 사진을 잠근다 ({@code FOR UPDATE}). 남의 것·없는 것은 빈 값. */
    public Optional<ImageRow> lockOwned(long id, long uploaderId) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM image WHERE id = :id AND uploader_id = :uploader FOR UPDATE")
                .param("id", id)
                .param("uploader", uploaderId)
                .query(ImageRepository::row)
                .optional();
    }

    /** 내가 올린 사진 (잠금 없음). */
    public Optional<ImageRow> findOwned(long id, long uploaderId) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + " FROM image WHERE id = :id AND uploader_id = :uploader")
                .param("id", id)
                .param("uploader", uploaderId)
                .query(ImageRepository::row)
                .optional();
    }

    /** complete 통과: 실제 크기·가로·세로를 기록한다. */
    public void markCompleted(
            long id, int sizeBytes, Integer thumbSizeBytes, int width, int height) {
        jdbc.sql(
                        """
                        UPDATE image SET size_bytes = :size, thumb_size_bytes = :thumbSize,
                                         width = :width, height = :height
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("size", sizeBytes)
                .param("thumbSize", thumbSizeBytes)
                .param("width", width)
                .param("height", height)
                .update();
    }

    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM image WHERE id = :id").param("id", id).update();
    }

    /** 내 사용량: TEMP·연결·연결 해제 대기·프로필 사진의 원본 + 썸네일 합 (FR-014, {@code ix_image_uploader}). */
    public long sumUsageBytes(long uploaderId) {
        return jdbc.sql(
                        """
                        SELECT coalesce(sum(size_bytes::bigint + coalesce(thumb_size_bytes, 0)), 0)
                          FROM image WHERE uploader_id = :uploader
                        """)
                .param("uploader", uploaderId)
                .query(Long.class)
                .single();
    }

    /**
     * 정리 후보 (트랜잭션 없이 SELECT, research R13): TEMP {@code tempTtl} 경과 또는 연결 해제 {@code detachedTtl}
     * 경과. {@code ORDER BY id LIMIT}.
     */
    public List<CleanupCandidate> cleanupCandidates(
            Instant now, Duration tempTtl, Duration detachedTtl, int limit) {
        return jdbc.sql(
                        "SELECT id, storage_key, thumb_storage_key FROM image WHERE "
                                + ELIGIBLE
                                + " ORDER BY id LIMIT :limit")
                .param("tempBefore", Timestamp.from(now.minus(tempTtl)))
                .param("detachedBefore", Timestamp.from(now.minus(detachedTtl)))
                .param("limit", limit)
                .query(
                        (rs, n) ->
                                new CleanupCandidate(
                                        rs.getLong("id"),
                                        rs.getString("storage_key"),
                                        rs.getString("thumb_storage_key")))
                .list();
    }

    /**
     * 저장소 삭제가 끝난 사진 행을 지운다. 그사이 다시 연결된 사진(조건이 풀림)은 남긴다. {@code post_image}는 CASCADE.
     *
     * @return 지운 행의 번호
     */
    public List<Long> deleteIfStillEligible(
            Collection<Long> ids, Instant now, Duration tempTtl, Duration detachedTtl) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("DELETE FROM image WHERE id IN (:ids) AND " + ELIGIBLE + " RETURNING id")
                .param("ids", List.copyOf(new LinkedHashSet<>(ids)))
                .param("tempBefore", Timestamp.from(now.minus(tempTtl)))
                .param("detachedBefore", Timestamp.from(now.minus(detachedTtl)))
                .query(Long.class)
                .list();
    }

    /**
     * 작성자의 완료된 사진만 (research R5·R10): {@code storage_key IN (:keys) AND uploader_id = :owner AND
     * width IS NOT NULL}, 키 수와 상관없이 조회 1번.
     */
    public List<OwnedKeys> findCompletedOwned(Collection<String> keys, long ownerId) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(
                        """
                        SELECT storage_key, thumb_storage_key FROM image
                         WHERE storage_key IN (:keys) AND uploader_id = :owner AND width IS NOT NULL
                        """)
                .param("keys", List.copyOf(new LinkedHashSet<>(keys)))
                .param("owner", ownerId)
                .query(
                        (rs, n) ->
                                new OwnedKeys(
                                        rs.getString("storage_key"),
                                        rs.getString("thumb_storage_key")))
                .list();
    }

    /** 썸네일 키 → 원본 키 ({@code uq_image_thumb_key}). */
    public Optional<String> findByThumbKey(String thumbStorageKey) {
        return jdbc.sql("SELECT storage_key FROM image WHERE thumb_storage_key = :key")
                .param("key", thumbStorageKey)
                .query(String.class)
                .optional();
    }

    private static ImageRow row(ResultSet rs, int rowNum) throws SQLException {
        Timestamp detached = rs.getTimestamp("detached_at");
        return new ImageRow(
                rs.getLong("id"),
                rs.getLong("uploader_id"),
                rs.getString("storage_key"),
                rs.getString("thumb_storage_key"),
                rs.getString("content_type"),
                rs.getInt("size_bytes"),
                (Integer) rs.getObject("thumb_size_bytes"),
                (Integer) rs.getObject("width"),
                (Integer) rs.getObject("height"),
                ImageStatus.valueOf(rs.getString("status")),
                ImagePurpose.valueOf(rs.getString("purpose")),
                detached == null ? null : detached.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
