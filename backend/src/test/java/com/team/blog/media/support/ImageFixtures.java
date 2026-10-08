package com.team.blog.media.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@code image} 행 픽스처 (003). 기본값: 글 사진, 완료(가로 1920·세로 1440), TEMP, 지금 만듦. */
public final class ImageFixtures {

    private final JdbcTemplate jdbc;

    public ImageFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Builder image(long uploaderId) {
        return new Builder(uploaderId);
    }

    /** 새 원본 키 ({@code images/2026/10/{uuid}.{ext}}). */
    public static String newKey(String ext) {
        return "images/2026/10/" + UUID.randomUUID() + "." + ext;
    }

    /** 원본 키의 썸네일 키. */
    public static String thumbOf(String key, String ext) {
        return key.substring(0, key.lastIndexOf('.')) + "_thumb." + ext;
    }

    public final class Builder {
        private final long uploaderId;
        private String key = newKey("webp");
        private String thumbKey;
        private boolean thumb = true;
        private String contentType = "image/webp";
        private int size = 400_000;
        private Integer thumbSize = 40_000;
        private Integer width = 1920;
        private Integer height = 1440;
        private String status = "TEMP";
        private String purpose = "POST";
        private Instant detachedAt;
        private Instant createdAt;

        private Builder(long uploaderId) {
            this.uploaderId = uploaderId;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder thumbKey(String thumbKey) {
            this.thumbKey = thumbKey;
            this.thumb = thumbKey != null;
            return this;
        }

        public Builder noThumb() {
            this.thumb = false;
            this.thumbKey = null;
            this.thumbSize = null;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder size(int size, Integer thumbSize) {
            this.size = size;
            this.thumbSize = thumbSize;
            return this;
        }

        /** 완료 전 (width·height NULL). */
        public Builder incomplete() {
            this.width = null;
            this.height = null;
            return this;
        }

        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder attached() {
            this.status = "ATTACHED";
            return this;
        }

        public Builder profile() {
            this.purpose = "PROFILE";
            this.contentType = "image/webp";
            this.width = 256;
            this.height = 256;
            return noThumb();
        }

        public Builder detachedAt(Instant detachedAt) {
            this.status = "ATTACHED";
            this.detachedAt = detachedAt;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public String storageKey() {
            return key;
        }

        public long create() {
            String thumbStorage =
                    thumb ? (thumbKey != null ? thumbKey : thumbOf(key, "webp")) : null;
            return jdbc.queryForObject(
                    """
                    INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,
                                       size_bytes, thumb_size_bytes, width, height, status, purpose,
                                       detached_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS timestamptz), coalesce(CAST(? AS timestamptz), now()))
                    RETURNING id
                    """,
                    Long.class,
                    uploaderId,
                    key,
                    thumbStorage,
                    contentType,
                    size,
                    thumb ? thumbSize : null,
                    width,
                    height,
                    status,
                    purpose,
                    detachedAt == null ? null : Timestamp.from(detachedAt),
                    createdAt == null ? null : Timestamp.from(createdAt));
        }
    }
}
