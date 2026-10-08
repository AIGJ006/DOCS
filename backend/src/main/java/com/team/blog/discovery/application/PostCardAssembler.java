package com.team.blog.discovery.application;

import com.team.blog.discovery.infra.PostCardRow;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.post.application.PostUrls;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 카드 행 → 응답 형태 (005 T022·T048 공용). 주소 만들기는 002 {@code PostUrls}와 001 {@code ImageUrlResolver}를 쓴다 —
 * 저장소 키를 직접 이어 붙이지 않는다.
 */
@Component
public class PostCardAssembler {

    private final ImageUrlResolver imageUrls;

    public PostCardAssembler(ImageUrlResolver imageUrls) {
        this.imageUrls = imageUrls;
    }

    public PostCardView toView(PostCardRow row) {
        return new PostCardView(
                row.id(),
                PostUrls.of(row.handle(), row.id()),
                row.title(),
                row.excerpt(),
                row.thumbnailUrl(),
                row.firstPublicAt() == null ? null : row.firstPublicAt().toInstant(),
                row.commentCount(),
                row.likeCount(),
                new PostCardView.Author(
                        row.handle(), row.nickname(), imageUrls.publicUrl(row.profileKey())));
    }

    public List<PostCardView> toViews(List<PostCardRow> rows) {
        return rows.stream().map(this::toView).toList();
    }
}
