package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 관리 목록 한 줄의 조회 결과 (006 T041, research R18). 본문 컬럼은 없다(FR-012). 반응 숫자는 {@code post}의 비정규화 컬럼이다(41
 * M-8).
 */
public record ManagePostRow(
        long id,
        String title,
        PostStatus status,
        Visibility visibility,
        boolean editing,
        boolean hidden,
        Instant updatedAt,
        Instant publishedAt,
        Instant editedAt,
        Instant deletedAt,
        long viewCount,
        int likeCount,
        int commentCount) {}
