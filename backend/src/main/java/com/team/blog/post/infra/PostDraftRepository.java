package com.team.blog.post.infra;

import com.team.blog.post.domain.PostDraft;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code post_draft} 저장소 (002 소유). 작업본 쓰기(버전 조건 UPSERT)는 네이티브 SQL(T077)이 맡는다. */
public interface PostDraftRepository extends JpaRepository<PostDraft, Long> {

    /** 작업본 삭제 (다시 발행·변경 취소). 지운 행 수. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostDraft d WHERE d.postId = :postId")
    int deleteByPostId(@Param("postId") long postId);

    /** 작업본의 마지막 저장 시각만 (본문은 읽지 않는다 — 005 상세의 "수정 중" 안내). */
    @Query("SELECT d.updatedAt FROM PostDraft d WHERE d.postId = :postId")
    Optional<Instant> findUpdatedAtByPostId(@Param("postId") long postId);

    /** 묶음 조회 — 작업본이 있는 글 번호만 (1쿼리). */
    @Query("SELECT d.postId FROM PostDraft d WHERE d.postId IN :postIds")
    List<Long> findPostIdsIn(@Param("postIds") Collection<Long> postIds);
}
