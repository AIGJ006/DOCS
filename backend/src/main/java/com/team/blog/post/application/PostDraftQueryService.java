package com.team.blog.post.application;

import com.team.blog.post.domain.PostDraft;
import com.team.blog.post.infra.PostDraftRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 작업본 읽기 공개 API (002 T022). 다른 모듈(005 상세의 "수정 중" 표시, 006 내 글 관리)은 이 Service만 쓴다. */
@Service
@Transactional(readOnly = true)
public class PostDraftQueryService {

    private final PostDraftRepository drafts;

    public PostDraftQueryService(PostDraftRepository drafts) {
        this.drafts = drafts;
    }

    /** 작업본의 마지막 저장 시각({@code post_draft.updated_at}). 작업본이 없으면 empty. */
    public Optional<Instant> findSavedAt(long postId) {
        return drafts.findById(postId).map(PostDraft::updatedAt);
    }

    /** 주어진 글 중 작업본이 있는 글 번호 (묶음 1쿼리, N+1 금지). */
    public Set<Long> postIdsWithDraft(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new HashSet<>(drafts.findPostIdsIn(postIds)));
    }
}
