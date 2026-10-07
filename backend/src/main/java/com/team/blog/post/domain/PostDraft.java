package com.team.blog.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 발행 글 작업본 ({@code post_draft}, 002 data-model §1-2). 발행 글 하나에 0~1행이고 PK가 곧 {@code post_id}(FK →
 * post ON DELETE CASCADE)다.
 *
 * <p>쓰기는 버전 조건이 붙은 네이티브 SQL({@code INSERT … ON CONFLICT … WHERE post_draft.edit_version <
 * EXCLUDED.edit_version}, T077)로 하므로 이 엔티티는 읽기와 삭제에 쓴다. {@code edit_version}은 항상 {@code
 * post.edit_version}보다 크다.
 */
@Entity
@Table(name = "post_draft")
public class PostDraft {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Column(nullable = false, length = 100)
    private String title;

    /** 고치는 중인 본문, ≤ 100,000자({@code ck_post_draft_content}). */
    @Column(name = "content_md", nullable = false, columnDefinition = "text")
    private String contentMd;

    @Column(name = "edit_version", nullable = false)
    private long editVersion;

    /** 수정을 시작한 시각. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 마지막 저장 시각 (에디터의 {@code savedAt}). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PostDraft() {}

    public long postId() {
        return postId;
    }

    public String title() {
        return title;
    }

    public String contentMd() {
        return contentMd;
    }

    public long editVersion() {
        return editVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
