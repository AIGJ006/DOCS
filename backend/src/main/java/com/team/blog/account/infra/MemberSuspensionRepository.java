package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberSuspension;
import java.util.List;
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

    /** 정지 이력 최근 순 (014 관리자 회원 화면, 최대 {@code limit}개). */
    @Query(
            value =
                    "SELECT * FROM member_suspension WHERE member_id = :memberId"
                            + " ORDER BY started_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<MemberSuspension> findHistory(@Param("memberId") long memberId, @Param("limit") int limit);

    /** 정지 이력 수 (014 처리 화면 작성자 카드). */
    long countByMemberId(long memberId);

    /** 열린 정지 모두 (해제 때 함께 닫는다 — 회원당 하나가 규칙이지만 남은 것이 있어도 모두 닫는다). */
    @Query(
            value =
                    "SELECT * FROM member_suspension WHERE member_id = :memberId AND lifted_at IS NULL",
            nativeQuery = true)
    List<MemberSuspension> findAllOpen(@Param("memberId") long memberId);
}
