package com.team.blog.post.support;

import com.team.blog.support.MemberFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 006 테스트의 딸린 행 픽스처. 댓글(007)·좋아요(009)·사진(003)·신고(014)·알림(011) 기능이 아직 없어 SQL로 직접 넣는다(tasks T016·T053
 * "행은 SQL 픽스처로 직접 넣음").
 */
public final class TrashFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbc;

    public TrashFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MemberFixtures members() {
        return new MemberFixtures(jdbc);
    }

    /** 댓글(또는 답글). */
    public long comment(long postId, long authorId, Long parentId) {
        return jdbc.queryForObject(
                "INSERT INTO comment (post_id, author_id, parent_id, content) VALUES (?, ?, ?, ?)"
                        + " RETURNING id",
                Long.class,
                postId,
                authorId,
                parentId,
                "댓글 " + SEQ.incrementAndGet());
    }

    public void like(long postId, long memberId) {
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, memberId);
    }

    /** 태그를 (없으면 만들고) 글에 붙인다. 태그 번호를 돌려준다. */
    public long tag(long postId, String name, int position) {
        jdbc.update("INSERT INTO tag (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
        long tagId = jdbc.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, ?)",
                postId,
                tagId,
                position);
        return tagId;
    }

    /** 사진 하나를 만들어 주어진 글들에 연결한다. */
    public long image(long uploaderId, long... postIds) {
        long imageId =
                jdbc.queryForObject(
                        "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes,"
                                + " status) VALUES (?, ?, 'image/png', 100, 'ATTACHED') RETURNING id",
                        Long.class,
                        uploaderId,
                        "posts/test/" + SEQ.incrementAndGet() + ".png");
        for (long postId : postIds) {
            jdbc.update(
                    "INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", postId, imageId);
        }
        return imageId;
    }

    public void viewDaily(long postId, LocalDate date, int views) {
        jdbc.update(
                "INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, ?)",
                postId,
                date,
                views);
    }

    /** 댓글 알림 (+ 행위자 한 명). 알림 번호를 돌려준다. */
    public long commentNotification(long receiverId, long postId, Long commentId, long actorId) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO notification (receiver_id, type, post_id, comment_id,"
                                + " last_actor_id, actor_count) VALUES (?, 'COMMENT', ?, ?, ?, 1)"
                                + " RETURNING id",
                        Long.class,
                        receiverId,
                        postId,
                        commentId,
                        actorId);
        jdbc.update(
                "INSERT INTO notification_actor (notification_id, actor_id) VALUES (?, ?)",
                id,
                actorId);
        return id;
    }

    /** 글 대상 신고 사건 + 신고 한 건. 사건 번호를 돌려준다. */
    public long postReport(long postId, long targetAuthorId, long reporterId, String status) {
        long caseId =
                jdbc.queryForObject(
                        "INSERT INTO report_case (target_type, post_id, target_author_id,"
                                + " snapshot_title, status) VALUES ('POST', ?, ?, '신고된 글', ?)"
                                + " RETURNING id",
                        Long.class,
                        postId,
                        targetAuthorId,
                        status);
        report(caseId, reporterId);
        return caseId;
    }

    /** 댓글 대상 신고 사건 + 신고 한 건. */
    public long commentReport(long commentId, long targetAuthorId, long reporterId, String status) {
        long caseId =
                jdbc.queryForObject(
                        "INSERT INTO report_case (target_type, comment_id, target_author_id,"
                                + " snapshot_content, status) VALUES ('COMMENT', ?, ?, '신고된 댓글',"
                                + " ?) RETURNING id",
                        Long.class,
                        commentId,
                        targetAuthorId,
                        status);
        report(caseId, reporterId);
        return caseId;
    }

    private void report(long caseId, long reporterId) {
        jdbc.update(
                "INSERT INTO report (case_id, reporter_id, reason) VALUES (?, ?, 'SPAM')",
                caseId,
                reporterId);
    }

    /** 반응 수 칸을 직접 맞춘다 (비정규화 숫자). */
    public void counts(long postId, long views, int likes, int comments) {
        jdbc.update(
                "UPDATE post SET view_count = ?, like_count = ?, comment_count = ? WHERE id = ?",
                views,
                likes,
                comments,
                postId);
    }

    /** 그 글에 딸린 행 수 (보존·삭제 확인용). */
    public Map<String, Long> related(long postId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table :
                new String[] {
                    "comment",
                    "post_like",
                    "post_tag",
                    "post_image",
                    "post_draft",
                    "post_view_daily",
                    "notification"
                }) {
            counts.put(
                    table,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM " + table + " WHERE post_id = ?",
                            Long.class,
                            postId));
        }
        return counts;
    }

    /** 글 행 전체 칸 (없으면 빈 맵). */
    public Map<String, Object> row(long postId) {
        return jdbc.queryForList("SELECT * FROM post WHERE id = ?", postId).stream()
                .findFirst()
                .map(m -> (Map<String, Object>) new LinkedHashMap<>(m))
                .orElse(Map.of());
    }

    public boolean exists(long postId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM post WHERE id = ?)", Boolean.class, postId));
    }

    public Instant deletedAt(long postId) {
        Timestamp ts =
                jdbc.queryForObject(
                        "SELECT deleted_at FROM post WHERE id = ?", Timestamp.class, postId);
        return ts == null ? null : ts.toInstant();
    }

    /** 휴지통으로 옮긴 시각을 직접 정한다. */
    public void trashedAt(long postId, Instant deletedAt) {
        jdbc.update(
                "UPDATE post SET deleted_at = ? WHERE id = ?",
                deletedAt == null ? null : Timestamp.from(deletedAt),
                postId);
    }

    public String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }
}
