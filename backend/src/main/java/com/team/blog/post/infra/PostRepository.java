package com.team.blog.post.infra;

import com.team.blog.post.domain.Post;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code post} 저장소 (002 소유, 004·006이 메서드를 더한다). 엔티티 조회는 {@code @SQLRestriction("deleted_at IS
 * NULL")}로 휴지통 글을 뺀다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 내 글을 행 잠금으로 읽는다 ({@code SELECT … FOR UPDATE}, A-6 ③). 남의 글·휴지통 글·없는 글은 빈 결과 → 호출하는 쪽이 404
     * (A-15). 트랜잭션 안에서만 부른다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT p FROM Post p WHERE p.id = :id AND p.authorId = :authorId AND p.deletedAt IS NULL")
    Optional<Post> findForUpdateByIdAndAuthorId(
            @Param("id") long id, @Param("authorId") long authorId);
}
