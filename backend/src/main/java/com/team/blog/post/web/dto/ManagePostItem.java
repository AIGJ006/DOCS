package com.team.blog.post.web.dto;

import com.team.blog.post.application.ManagePostList;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.ManagePostRow;
import java.time.Instant;

/**
 * 관리 목록 한 줄 (006 T040, contracts {@code ManagePostItem}, data-model §2-1). 본문 필드·작성자 번호는
 * 없다(FR-012). {@code deletedAt}·{@code purgeAt}은 휴지통 탭에서만 값이고 그 밖에는 {@code null}로 보낸다.
 */
public record ManagePostItem(
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
        Instant purgeAt,
        long viewCount,
        int likeCount,
        int commentCount) {

    public static ManagePostItem from(ManagePostList.Item item) {
        ManagePostRow row = item.row();
        return new ManagePostItem(
                row.id(),
                row.title(),
                row.status(),
                row.visibility(),
                row.editing(),
                row.hidden(),
                row.updatedAt(),
                row.publishedAt(),
                row.editedAt(),
                row.deletedAt(),
                item.purgeAt(),
                row.viewCount(),
                row.likeCount(),
                row.commentCount());
    }
}
