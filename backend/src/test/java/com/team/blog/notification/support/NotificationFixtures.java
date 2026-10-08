package com.team.blog.notification.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림·묶음·끄기 행을 직접 넣는 테스트 도구 (011 T014). 시각({@code updated_at}·{@code created_at})을 정할 수 있다. 이벤트를 거치지
 * 않으므로 공통 제외 규칙을 보지 않는다.
 */
public final class NotificationFixtures {

    private final JdbcTemplate jdbc;

    public NotificationFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Builder single(long receiverId, String type) {
        return new Builder(receiverId, type);
    }

    /**
     * 묶음 알림 ({@code LIKE}·{@code FOLLOW}) + 묶인 사람들 ({@code actors[i]}의 {@code created_at} = {@code
     * at + i초}).
     */
    public long group(
            long receiverId, String type, Long postId, Instant at, boolean read, long... actors) {
        String key = "LIKE".equals(type) ? "LIKE:post:" + postId : "FOLLOW";
        long id =
                jdbc.queryForObject(
                        "INSERT INTO notification (receiver_id, type, post_id, group_key, last_actor_id,"
                                + " actor_count, read_at, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        receiverId,
                        type,
                        postId,
                        key,
                        actors.length == 0 ? null : actors[actors.length - 1],
                        actors.length,
                        read ? Timestamp.from(at) : null,
                        Timestamp.from(at),
                        Timestamp.from(at.plusSeconds(Math.max(actors.length - 1, 0))));
        for (int i = 0; i < actors.length; i++) {
            jdbc.update(
                    "INSERT INTO notification_actor (notification_id, actor_id, created_at) VALUES (?, ?, ?)",
                    id,
                    actors[i],
                    Timestamp.from(at.plusSeconds(i)));
        }
        return id;
    }

    public void mute(long memberId, String... types) {
        for (String type : types) {
            jdbc.update(
                    "INSERT INTO notification_mute (member_id, type) VALUES (?, ?)",
                    memberId,
                    type);
        }
    }

    public long count(long receiverId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE receiver_id = ?", Long.class, receiverId);
    }

    public long count(long receiverId, String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE receiver_id = ? AND type = ?",
                Long.class,
                receiverId,
                type);
    }

    public List<Map<String, Object>> rows(long receiverId) {
        return jdbc.queryForList(
                "SELECT * FROM notification WHERE receiver_id = ? ORDER BY id", receiverId);
    }

    public List<Long> actors(long notificationId) {
        return jdbc.queryForList(
                "SELECT actor_id FROM notification_actor WHERE notification_id = ? ORDER BY created_at",
                Long.class,
                notificationId);
    }

    public final class Builder {
        private final long receiverId;
        private final String type;
        private Long postId;
        private Long commentId;
        private Long reportId;
        private String result;
        private Long actorId;
        private int actorCount = 1;
        private Instant at = Instant.now();
        private boolean read;

        private Builder(long receiverId, String type) {
            this.receiverId = receiverId;
            this.type = type;
            if ("REPORT_RESOLVED".equals(type) || "CONTENT_HIDDEN".equals(type)) {
                actorCount = 0;
            }
            if ("REPORT_RESOLVED".equals(type)) {
                result = "ACTION_TAKEN";
            }
        }

        public Builder post(long postId) {
            this.postId = postId;
            return this;
        }

        public Builder comment(long commentId) {
            this.commentId = commentId;
            return this;
        }

        public Builder report(Long reportId, String result) {
            this.reportId = reportId;
            this.result = result;
            return this;
        }

        public Builder actor(long actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder at(Instant at) {
            this.at = at;
            return this;
        }

        public Builder read() {
            this.read = true;
            return this;
        }

        public long create() {
            return jdbc.queryForObject(
                    "INSERT INTO notification (receiver_id, type, post_id, comment_id, report_id, result,"
                            + " last_actor_id, actor_count, read_at, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class,
                    receiverId,
                    type,
                    postId,
                    commentId,
                    reportId,
                    result,
                    actorId,
                    actorCount,
                    read ? Timestamp.from(at) : null,
                    Timestamp.from(at),
                    Timestamp.from(at));
        }
    }
}
