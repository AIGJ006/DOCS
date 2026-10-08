package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 상태 전이·정리 대상 조회·익명화 (015 T013, data-model §2, contracts/purge-steps.md §2-1·§3). 상태와 신청 시각은 한
 * 문장에서 함께 바꾼다({@code ck_member_withdrawn}). 닉네임 비우기와 익명 처리 시각 기록도 한 문장이다({@code
 * ck_member_nickname_null}).
 */
@Repository
public class WithdrawalMemberRepository {

    /** 잠근 회원 행의 상태 (FOR UPDATE). */
    public record LockedMember(
            long id, MemberStatus status, Role role, Instant withdrawnAt, Instant deletedAt) {

        public boolean isDeleted() {
            return deletedAt != null;
        }
    }

    private final JdbcClient jdbc;

    public WithdrawalMemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code SELECT … FOR UPDATE}. 호출한 트랜잭션이 끝날 때까지 복구·신청·정리가 같은 행을 기다린다. */
    public Optional<LockedMember> lockForUpdate(long memberId) {
        return jdbc.sql(
                        """
                        SELECT id, status, role, withdrawn_at, deleted_at
                          FROM member WHERE id = :m FOR UPDATE
                        """)
                .param("m", memberId)
                .query(
                        (rs, n) ->
                                new LockedMember(
                                        rs.getLong("id"),
                                        MemberStatus.valueOf(rs.getString("status")),
                                        Role.valueOf(rs.getString("role")),
                                        instant(rs.getTimestamp("withdrawn_at")),
                                        instant(rs.getTimestamp("deleted_at"))))
                .optional();
    }

    /** 신청: ACTIVE인 행만 WITHDRAWN + 신청 시각. @return 바뀐 행 수 (0 또는 1) */
    public int markWithdrawn(long memberId, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now
                         WHERE id = :m AND status = 'ACTIVE'
                        """)
                .param("now", at(now))
                .param("m", memberId)
                .update();
    }

    /** 복구: 익명 처리 전 WITHDRAWN 행만 ACTIVE로. @return 바뀐 행 수 (0 또는 1) */
    public int markRestored(long memberId, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL, updated_at = :now
                         WHERE id = :m AND status = 'WITHDRAWN' AND deleted_at IS NULL
                        """)
                .param("now", at(now))
                .param("m", memberId)
                .update();
    }

    /** 영구 정지 자동 정리: SUSPENDED → WITHDRAWN(신청 시각 = 지금). 정지 이력 행은 그대로 둔다 (research R11). */
    public int markSuspendedAsWithdrawn(long memberId, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE member SET status = 'WITHDRAWN', withdrawn_at = :now, updated_at = :now
                         WHERE id = :m AND status = 'SUSPENDED'
                        """)
                .param("now", at(now))
                .param("m", memberId)
                .update();
    }

    /**
     * 유예가 끝난 정리 대상 ({@code ix_member_withdraw_purge}): {@code withdrawn_at < cutoff}(= now -
     * grace), 오래된 순.
     */
    public List<Long> findPurgeTargets(Instant cutoff, int batchSize) {
        return jdbc.sql(
                        """
                        SELECT id FROM member
                         WHERE status = 'WITHDRAWN' AND deleted_at IS NULL AND withdrawn_at < :cutoff
                         ORDER BY withdrawn_at, id LIMIT :limit
                        """)
                .param("cutoff", at(cutoff))
                .param("limit", batchSize)
                .query(Long.class)
                .list();
    }

    /**
     * 영구 정지 1년 경과 대상 (research R11): {@code status = SUSPENDED}, 일반 회원, 열린(해제 안 된) 종료 없는 정지가 {@code
     * cutoff}(= now - suspendedPurgeAfter)보다 먼저 시작됨.
     */
    public List<Long> findSuspendedPurgeTargets(Instant cutoff, int batchSize) {
        return jdbc.sql(
                        """
                        SELECT m.id FROM member m
                         WHERE m.status = 'SUSPENDED' AND m.deleted_at IS NULL AND m.role = 'USER'
                           AND EXISTS (SELECT 1 FROM member_suspension s
                                        WHERE s.member_id = m.id AND s.lifted_at IS NULL
                                          AND s.ends_at IS NULL AND s.started_at < :cutoff)
                         ORDER BY m.id LIMIT :limit
                        """)
                .param("cutoff", at(cutoff))
                .param("limit", batchSize)
                .query(Long.class)
                .list();
    }

    /** 잠근 뒤 다시 확인: 이 회원에게 {@code cutoff} 전에 시작한 열린 영구 정지가 있는가. */
    public boolean hasOpenPermanentSuspensionBefore(long memberId, Instant cutoff) {
        return Boolean.TRUE.equals(
                jdbc.sql(
                                """
                                SELECT EXISTS (SELECT 1 FROM member_suspension s
                                                WHERE s.member_id = :m AND s.lifted_at IS NULL
                                                  AND s.ends_at IS NULL AND s.started_at < :cutoff)
                                """)
                        .param("m", memberId)
                        .param("cutoff", at(cutoff))
                        .query(Boolean.class)
                        .single());
    }

    /** 로그인 수단의 이메일 (커밋 후 Redis 정리용 해시 계산 — order 50이 지우기 전에 읽는다). 없으면 빈 값. */
    public Optional<String> findLoginEmail(long memberId) {
        return jdbc.sql(
                        "SELECT email FROM auth_identity WHERE member_id = :m AND email IS NOT NULL")
                .param("m", memberId)
                .query(String.class)
                .optional();
    }

    /**
     * 회원 익명화 (order 90, contracts §2-1). 주소·역할·상태·신청 시각·설정·가입일은 남긴다.
     *
     * @throws IllegalStateException 바뀐 행이 1이 아님 (이미 익명 처리됐거나 WITHDRAWN이 아님) → 그 회원 정리 전체 롤백
     */
    public void anonymize(long memberId, Instant now) {
        int updated =
                jdbc.sql(
                                """
                                UPDATE member
                                   SET nickname = NULL, bio = NULL, nickname_changed_at = NULL,
                                       last_active_at = NULL, deleted_at = :now, updated_at = :now
                                 WHERE id = :m AND status = 'WITHDRAWN' AND deleted_at IS NULL
                                """)
                        .param("now", at(now))
                        .param("m", memberId)
                        .update();
        if (updated != 1) {
            throw new IllegalStateException(
                    "회원 익명화 대상이 아닙니다: memberId=" + memberId + " updated=" + updated);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
