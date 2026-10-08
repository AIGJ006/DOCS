package com.team.blog.interaction.support;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/** 010 팔로우 시험 데이터 (테스트 전용). 서버 흐름 없이 {@code follow}·회원 상태를 SQL로 바로 바꾼다. */
public final class FollowFixtures {

    private final JdbcTemplate jdbc;

    public FollowFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code follower}가 {@code followee}를 {@code at}에 팔로우한 행. */
    public void follow(long follower, long followee, Instant at) {
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id, created_at) VALUES (?, ?, ?)",
                follower,
                followee,
                Timestamp.from(at));
    }

    public void follow(long follower, long followee) {
        follow(follower, followee, Instant.now());
    }

    /** 탈퇴 유예로 (015 없이 상태만 — US4 #1). */
    public void withdraw(long memberId) {
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?",
                memberId);
    }

    /** 탈퇴 유예에서 복구 (015 없이 상태만 — US4 #2). */
    public void restore(long memberId) {
        jdbc.update(
                "UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", memberId);
    }

    /** 현재 프로필 사진 ({@code uq_image_profile_current}). */
    public void profileImage(long memberId, String key, String thumbKey) {
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, status, purpose) VALUES (?, ?, ?, 'image/png', 100,"
                        + " 'ATTACHED', 'PROFILE')",
                memberId,
                key,
                thumbKey);
    }

    public long rows(long follower, long followee) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM follow WHERE follower_id = ? AND followee_id = ?",
                Long.class,
                follower,
                followee);
    }

    public long rowsOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM follow WHERE follower_id = ? OR followee_id = ?",
                Long.class,
                memberId,
                memberId);
    }

    public String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }
}
