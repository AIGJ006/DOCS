package com.team.blog.account.infra;

import com.team.blog.account.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code member} 저장소. account 모듈 밖에서는 쓰지 않는다(다른 모듈은 {@code MemberQueryService}를 거친다). */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** {@code SELECT ... FOR UPDATE} — 프로필 사진 교체·닉네임 변경처럼 회원 행을 기준으로 직렬화할 때. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") long id);

    boolean existsByHandle(String handle);

    Optional<Member> findByHandle(String handle);
}
