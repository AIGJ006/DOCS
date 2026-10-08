package com.team.blog.interaction.support;

import com.team.blog.support.MemberFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 댓글 시드 (007 테스트 전용). SQL로 직접 넣고, 삭제·숨김이 아닌 댓글이면 {@code post.comment_count}도 1 올려 불변식(SC-002)을 지킨다.
 *
 * <pre>{@code
 * long root = comments().on(postId, author).at(t).create();
 * long reply = comments().on(postId, other).parent(root).replyTo(member).create();
 * long hidden = comments().on(postId, author).hidden().create();
 * }</pre>
 */
public final class CommentFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbc;

    public CommentFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Builder on(long postId, long authorId) {
        return new Builder(postId, authorId);
    }

    /** 실제 정상 댓글 수 (불변식 확인용). */
    public long normalCount(long postId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM comment WHERE post_id = ? AND deleted_at IS NULL AND"
                        + " hidden_at IS NULL",
                Long.class,
                postId);
    }

    public int commentCount(long postId) {
        return jdbc.queryForObject(
                "SELECT comment_count FROM post WHERE id = ?", Integer.class, postId);
    }

    public final class Builder {
        private final long postId;
        private final long authorId;
        private Long parentId;
        private Long replyTo;
        private String content;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean deleted;
        private boolean hidden;

        private Builder(long postId, long authorId) {
            this.postId = postId;
            this.authorId = authorId;
        }

        public Builder parent(long parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder replyTo(long memberId) {
            this.replyTo = memberId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder at(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder edited(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /** 삭제된 자리 (내용 비움). */
        public Builder deleted() {
            this.deleted = true;
            return this;
        }

        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        public long create() {
            Instant created = createdAt != null ? createdAt : Instant.now();
            Instant updated = updatedAt != null ? updatedAt : created;
            String text =
                    deleted ? "" : (content != null ? content : "댓글 " + SEQ.incrementAndGet());
            Long admin = hidden ? new MemberFixtures(jdbc).member().role("ADMIN").create() : null;
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO comment (post_id, author_id, parent_id, reply_to_member_id,"
                                    + " content, created_at, updated_at, deleted_at, hidden_at,"
                                    + " hidden_by, hidden_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                                    + " ?, ?) RETURNING id",
                            Long.class,
                            postId,
                            authorId,
                            parentId,
                            replyTo,
                            text,
                            Timestamp.from(created),
                            Timestamp.from(updated),
                            deleted ? Timestamp.from(created) : null,
                            hidden ? Timestamp.from(created) : null,
                            admin,
                            hidden ? "SPAM" : null);
            if (!deleted && !hidden) {
                jdbc.update(
                        "UPDATE post SET comment_count = comment_count + 1 WHERE id = ?", postId);
            }
            return id;
        }
    }
}
