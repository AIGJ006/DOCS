package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.interaction.application.LikeQueryService;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 알림 처리 시점 확인용 공개 조회 (011 T019): {@code CommentQueryService.isActive}, {@code
 * LikeQueryService.isLiked}.
 */
class InteractionStateQueryIT extends IntegrationTestBase {

    @Autowired private CommentQueryService commentQueries;
    @Autowired private LikeQueryService likeQueries;

    @Test
    void 댓글은_행이_있고_삭제_숨김이_아닐_때만_정상() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long normal = comments.on(postId, author).create();
        long deleted = comments.on(postId, author).deleted().create();
        long hidden = comments.on(postId, author).hidden().create();

        assertThat(commentQueries.isActive(normal)).isTrue();
        assertThat(commentQueries.isActive(deleted)).isFalse();
        assertThat(commentQueries.isActive(hidden)).isFalse();
        assertThat(commentQueries.isActive(normal + 1_000)).isFalse();
    }

    @Test
    void 좋아요_행이_있으면_참() {
        long author = members().member().create();
        long liker = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, State.PUBLISHED_PUBLIC);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, liker);

        assertThat(likeQueries.isLiked(postId, liker)).isTrue();
        assertThat(likeQueries.isLiked(postId, author)).isFalse();
    }
}
