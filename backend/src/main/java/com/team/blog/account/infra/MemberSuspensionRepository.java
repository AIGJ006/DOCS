package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberSuspension;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code member_suspension} 저장소. account 모듈 밖에서는 {@code SuspensionService}를 거친다(R-31). */
public interface MemberSuspensionRepository extends JpaRepository<MemberSuspension, Long> {

    /** 열린 정지 중 가장 최근 것 ({@code ix_member_suspension_member}). */
    @Query(
            value =
                    "SELECT * FROM member_suspension WHERE member_id = :memberId AND lifted_at IS NULL"
                            + " ORDER BY started_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<MemberSuspension> findOpenByMemberId(@Param("memberId") long memberId);
}
