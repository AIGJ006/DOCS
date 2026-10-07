package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;
import java.util.List;

/**
 * 에디터에 여는 내용 (contracts {@code WorkingCopy}, B-1).
 *
 * @param editing 발행 글에 작업본 또는 더 새 Redis 보관분이 있어 "수정 중"인가
 * @param version 현재 버전 = max(Redis, post_draft, post)
 * @param tags 현재 발행본의 태그 (발행 설정 초기값)
 * @param url 발행 글이면 {@code /@{handle}/posts/{id}}, 아니면 {@code null}
 */
public record WorkingCopy(
        long postId,
        PostStatus status,
        boolean editing,
        String title,
        String contentMd,
        long version,
        Instant savedAt,
        Visibility visibility,
        List<String> tags,
        String url) {}
