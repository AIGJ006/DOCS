package com.team.blog.interaction.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 팔로우 관계 ({@code follow}, interaction 모듈 소유, 010 contracts/follow-sql.md §1~§3·§7, research
 * R1·R4~R6).
 *
 * <ul>
 *   <li>PK {@code (follower_id, followee_id)}가 쌍마다 한 행을 지킨다 — 동시에 같은 쌍을 넣으면 두 번째는 PK 대기 뒤 0행이다.
 *       {@code RETURNING}이 행을 돌려줄 때만 "실제로 바뀜"이다(이벤트 판단).
 *   <li>자기 팔로우는 서버가 먼저 400으로 거부하고, 그래도 들어오면 {@code ck_follow_self}가 막는다.
 *   <li>수는 볼 때 센다(F-6) — 탈퇴 유예 회원은 {@code NOT EXISTS}(부분 인덱스 {@code ix_member_withdraw_purge})로 뺀다.
 *       정지 회원은 빼지 않는다.
 * </ul>
 *
 * <p><b>원칙 II 읽기 예외</b> (plan Complexity Tracking): 목록·수 SQL이 {@code member}(주소·닉네임·소개·상태)와 {@code
 * image}(현재 프로필 사진, {@code uq_image_profile_current} 술어)를 읽기 전용으로 JOIN한다. 탈퇴 유예 회원 제외가 페이지 경계(20개)와
 * 커서에 들어가야 하고 FR-023이 목록을 "SQL 1번 + 팔로우 여부 1번"으로 정했기 때문이다. interaction 모듈에서 그 두 테이블을 읽는 팔로우 코드는 이
 * 클래스뿐이다.
 */
@Repository
public class FollowRepository {

    /** 탈퇴 유예(익명 처리 전) 회원인가 — 수 SQL의 제외 조건. 별칭 {@code m}. */
    private static final String WITHDRAWN_PENDING =
            "m.status = 'WITHDRAWN' AND m.deleted_at IS NULL";

    private final JdbcClient jdbc;

    public FollowRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 팔로우 상태로 (없으면 넣는다).
     *
     * @return 실제로 새로 생겼으면 {@code true}, 이미 있었으면 {@code false}
     */
    public boolean insertIfAbsent(long followerId, long followeeId, Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO follow (follower_id, followee_id, created_at)
                        VALUES (:me, :target, :now)
                        ON CONFLICT DO NOTHING
                        RETURNING follower_id
                        """)
                .param("me", followerId)
                .param("target", followeeId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /**
     * 팔로우 해제 상태로 (있으면 지운다).
     *
     * @return 실제로 지웠으면 {@code true}, 없었으면 {@code false}
     */
    public boolean deleteIfPresent(long followerId, long followeeId) {
        return jdbc.sql(
                        """
                        DELETE FROM follow WHERE follower_id = :me AND followee_id = :target
                        RETURNING follower_id
                        """)
                .param("me", followerId)
                .param("target", followeeId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 팔로우 중인가 (PK 조회 1번). */
    public boolean exists(long followerId, long followeeId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM follow"
                                + " WHERE follower_id = :me AND followee_id = :target)")
                .param("me", followerId)
                .param("target", followeeId)
                .query(Boolean.class)
                .single();
    }

    /** 이 회원을 팔로우하는 사람 수 — 탈퇴 유예 회원 제외 ({@code ix_follow_followee}). */
    public long countFollowers(long memberId) {
        return jdbc.sql(
                        "SELECT count(*) FROM follow f WHERE f.followee_id = :target"
                                + " AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.follower_id AND "
                                + WITHDRAWN_PENDING
                                + ")")
                .param("target", memberId)
                .query(Long.class)
                .single();
    }

    /** 이 회원이 팔로우하는 사람 수 — 탈퇴 유예 회원 제외 ({@code ix_follow_follower}). */
    public long countFollowing(long memberId) {
        return jdbc.sql(
                        "SELECT count(*) FROM follow f WHERE f.follower_id = :target"
                                + " AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.followee_id AND "
                                + WITHDRAWN_PENDING
                                + ")")
                .param("target", memberId)
                .query(Long.class)
                .single();
    }

    /** 팔로우한 사람이 한 명이라도 있는가 (피드 빈 화면 문구, 유예 회원도 셈 — 행이 있으면 true). */
    public boolean hasFollowing(long memberId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM follow WHERE follower_id = :me)")
                .param("me", memberId)
                .query(Boolean.class)
                .single();
    }

    /** 이 회원의 팔로워 번호 전부 — 탈퇴 유예 회원 제외 (다른 기능용 공개, contracts §6). */
    public List<Long> followerIdsOf(long memberId) {
        return jdbc.sql(
                        "SELECT f.follower_id FROM follow f WHERE f.followee_id = :target"
                                + " AND NOT EXISTS (SELECT 1 FROM member m WHERE m.id = f.follower_id AND "
                                + WITHDRAWN_PENDING
                                + ") ORDER BY f.follower_id")
                .param("target", memberId)
                .query(Long.class)
                .list();
    }

    /**
     * 팔로워 목록 한 페이지 (최근 팔로우 순, 같으면 회원 번호 큰 순). 탈퇴 신청 회원({@code status = WITHDRAWN})은 빠진다.
     *
     * @param afterAt 이 위치 이후만 (첫 페이지면 {@code null})
     * @param limit 읽을 행 수 (page-size + 1)
     */
    public List<FollowRow> pageFollowers(long memberId, Instant afterAt, Long afterId, int limit) {
        return page("follower_id", "followee_id", memberId, afterAt, afterId, limit);
    }

    /** 팔로잉 목록 한 페이지. {@link #pageFollowers}와 같은 규칙, 항목은 이 회원이 팔로우한 사람. */
    public List<FollowRow> pageFollowing(long memberId, Instant afterAt, Long afterId, int limit) {
        return page("followee_id", "follower_id", memberId, afterAt, afterId, limit);
    }

    /**
     * @param itemColumn 항목 회원 컬럼 (팔로워 목록이면 {@code follower_id})
     * @param ownerColumn 목록 주인 컬럼 (팔로워 목록이면 {@code followee_id})
     */
    private List<FollowRow> page(
            String itemColumn,
            String ownerColumn,
            long memberId,
            Instant afterAt,
            Long afterId,
            int limit) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT f.created_at, m.id, m.handle, m.nickname, m.bio,
                               COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key
                          FROM follow f
                        """);
        sql.append("  JOIN member m ON m.id = f.")
                .append(itemColumn)
                .append(" AND m.status <> 'WITHDRAWN'\n")
                .append(
                        """
                          LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
                               AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
                        """)
                .append(" WHERE f.")
                .append(ownerColumn)
                .append(" = :target");
        if (afterAt != null) {
            sql.append(" AND (f.created_at, f.")
                    .append(itemColumn)
                    .append(") < (:cursorAt, :cursorId)");
        }
        sql.append(" ORDER BY f.created_at DESC, f.")
                .append(itemColumn)
                .append(" DESC LIMIT :limit");
        JdbcClient.StatementSpec spec =
                jdbc.sql(sql.toString()).param("target", memberId).param("limit", limit);
        if (afterAt != null) {
            spec =
                    spec.param("cursorAt", afterAt.atOffset(ZoneOffset.UTC))
                            .param("cursorId", afterId);
        }
        return spec.query(FollowRepository::toRow).list();
    }

    /**
     * 보는 사람이 이 회원들 중 누구를 팔로우하는가 (PK, 1번). contracts의 {@code = ANY(:ids)}와 같은 뜻으로, 001 관례대로 {@code
     * IN (:ids)}(한 페이지 20개 이하)를 쓴다.
     *
     * @return {@code ids} 중 팔로우 중인 회원 번호
     */
    public Set<Long> followedAmong(long viewerId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.sql(
                                "SELECT followee_id FROM follow WHERE follower_id = :viewer"
                                        + " AND followee_id IN (:ids)")
                        .param("viewer", viewerId)
                        .param("ids", ids)
                        .query(Long.class)
                        .list());
    }

    /**
     * 그 회원의 팔로우 관계를 양방향 모두 지운다 (015 탈퇴 정리 order 65, contracts §7). 이벤트는 내지 않는다.
     *
     * @return 지운 행 수
     */
    public int deleteAllOf(long memberId) {
        return jdbc.sql("DELETE FROM follow WHERE follower_id = :m OR followee_id = :m")
                .param("m", memberId)
                .update();
    }

    private static FollowRow toRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime at = rs.getObject("created_at", OffsetDateTime.class);
        return new FollowRow(
                at.toInstant(),
                rs.getLong("id"),
                rs.getString("handle"),
                rs.getString("nickname"),
                rs.getString("bio"),
                rs.getString("profile_key"));
    }

    /**
     * 목록 한 줄.
     *
     * @param createdAt 팔로우한 시각 (커서 키)
     * @param memberId 항목 회원 번호 (커서 키)
     * @param handle 블로그 주소
     * @param nickname 닉네임
     * @param bio 소개 원문 (없으면 {@code null})
     * @param profileKey 작은 프로필 사진 저장소 키 (썸네일, 없으면 원본; 사진이 없으면 {@code null})
     */
    public record FollowRow(
            Instant createdAt,
            long memberId,
            String handle,
            String nickname,
            String bio,
            String profileKey) {}
}
