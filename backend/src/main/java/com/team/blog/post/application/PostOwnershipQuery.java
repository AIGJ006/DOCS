package com.team.blog.post.application;

import com.team.blog.post.infra.PostEditRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * post 공개 조회: 내 글인가 (013 T007, research R2). 다른 모듈은 {@code post} 테이블을 직접 읽지 않고 이것을 부른다(헌법 II).
 *
 * <p>조건은 002 {@code PostEditRepository.isOwned}와 같다 — {@code author_id = :me AND deleted_at IS
 * NULL}. 상태(임시·발행)와 관리자 숨김은 보지 않는다(작성자는 숨김 글도 편집할 수 있다). 트랜잭션 없이 SQL 한 번.
 */
@Service
public class PostOwnershipQuery {

    private final PostEditRepository repository;

    public PostOwnershipQuery(PostEditRepository repository) {
        this.repository = repository;
    }

    /** 내 휴지통 밖 글이면 번호와 공개 범위. 남의 글·없는 글·휴지통 글은 빈 값. */
    public Optional<OwnedPost> findOwned(long postId, long memberId) {
        return repository
                .findOwnedVisibility(postId, memberId)
                .map(visibility -> new OwnedPost(postId, visibility));
    }
}
