package com.team.blog.post.support;

import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 002 테스트 데이터 (T009). 004 {@link PostFixtures}(JdbcTemplate INSERT)를 그대로 쓰고 002에 필요한 것만 더한다. 회원은
 * {@code IntegrationTestBase.members()}로 만든다.
 */
public final class AuthoringFixtures {

    public static final String DIRTY_KEY = "autosave:dirty";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final PostFixtures posts;

    public AuthoringFixtures(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.posts = new PostFixtures(jdbc);
    }

    public PostFixtures posts() {
        return posts;
    }

    /** 작업본({@code post_draft}) 있는 발행 글 = 수정 중. 작업본 버전은 글 버전 + 1. */
    public long publishedWithWorkingCopy(long authorId, String draftTitle, String draftContentMd) {
        return posts.post(authorId).published("PUBLIC").draft(draftTitle, draftContentMd).create();
    }

    /** 반응 수를 지정한 발행 글. */
    public long publishedWithReactions(long authorId, long views, int likes, int comments) {
        long id = posts.post(authorId).published("PUBLIC").create();
        jdbc.update(
                "UPDATE post SET view_count = ?, like_count = ?, comment_count = ? WHERE id = ?",
                views,
                likes,
                comments,
                id);
        return id;
    }

    /** {@code render_version}을 지정한 발행 글 (다시 렌더링 배치 대상). */
    public long publishedWithRenderVersion(long authorId, int renderVersion, String contentMd) {
        long id = posts.post(authorId).published("PUBLIC").contentMd(contentMd).create();
        jdbc.update("UPDATE post SET render_version = ? WHERE id = ?", renderVersion, id);
        return id;
    }

    /** 만든 시각·수정 시각을 과거로 둔 임시글 (빈 임시글 정리 대상 판정). */
    public long oldDraft(long authorId, String title, String contentMd, Instant createdAt) {
        return posts.post(authorId)
                .title(title)
                .contentMd(contentMd)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .create();
    }

    /**
     * Redis {@code autosave:post:{id}} Hash를 직접 넣는다. {@code dirty}면 {@code autosave:dirty}에도 넣는다.
     */
    public void putAutosave(
            long postId,
            long memberId,
            String title,
            String contentMd,
            long version,
            Instant savedAt,
            boolean dirty) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("memberId", String.valueOf(memberId));
        hash.put("title", title);
        hash.put("contentMd", contentMd);
        hash.put("version", String.valueOf(version));
        hash.put("savedAt", savedAt.toString());
        redis.opsForHash().putAll(autosaveKey(postId), hash);
        if (dirty) {
            redis.opsForSet().add(DIRTY_KEY, String.valueOf(postId));
        }
    }

    public static String autosaveKey(long postId) {
        return "autosave:post:" + postId;
    }

    public Map<Object, Object> autosaveHash(long postId) {
        return redis.opsForHash().entries(autosaveKey(postId));
    }

    public boolean isDirty(long postId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(DIRTY_KEY, String.valueOf(postId)));
    }

    /** 요청 전후 비교용 스냅샷 (SC-008: 거부된 요청은 아무것도 바꾸지 않는다). */
    public Optional<Snapshot> snapshot(long postId) {
        return jdbc
                .query(
                        "SELECT title, content_md, status, edit_version, updated_at FROM post"
                                + " WHERE id = ?",
                        (rs, n) ->
                                new Snapshot(
                                        rs.getString("title"),
                                        rs.getString("content_md"),
                                        rs.getString("status"),
                                        rs.getLong("edit_version"),
                                        toInstant(rs.getTimestamp("updated_at"))),
                        postId)
                .stream()
                .findFirst();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    public record Snapshot(
            String title, String contentMd, String status, long editVersion, Instant updatedAt) {}
}
