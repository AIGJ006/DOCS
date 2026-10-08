package com.team.blog.account.infra;

import com.team.blog.account.domain.AgreementType;
import com.team.blog.account.domain.MemberAgreement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code member_agreement} 저장소. */
public interface MemberAgreementRepository
        extends JpaRepository<MemberAgreement, MemberAgreement.Key> {

    List<MemberAgreement> findAllByMemberId(long memberId);

    /** 동의·재동의: 종류별 한 행을 넣거나 버전·시각을 바꾼다 (FR-012, data-model §2-3). */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO member_agreement (member_id, type, version, agreed_at)
                    VALUES (:memberId, :type, :version, :agreedAt)
                    ON CONFLICT (member_id, type)
                    DO UPDATE SET version = EXCLUDED.version, agreed_at = EXCLUDED.agreed_at
                    """,
            nativeQuery = true)
    int upsert(
            @Param("memberId") long memberId,
            @Param("type") String type,
            @Param("version") String version,
            @Param("agreedAt") Instant agreedAt);

    /** 종류 하나의 동의 행 (013 AI 동의). */
    Optional<MemberAgreement> findByMemberIdAndType(long memberId, AgreementType type);

    /** 동의 철회 = 행 삭제 (013, 51 §4). 지운 행 수 (없으면 0). */
    @Modifying
    @Query(
            value = "DELETE FROM member_agreement WHERE member_id = :memberId AND type = :type",
            nativeQuery = true)
    int deleteByMemberIdAndType(@Param("memberId") long memberId, @Param("type") String type);
}
