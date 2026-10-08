package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 글의 댓글 번호 목록 (006 T059, research R11 — 006 신고 종료 단계용 최소 구현, 007이 소유). */
class CommentIdsOfPostIT extends IntegrationTestBase {

    @Autowired CommentQueryService comments;

    @Test
    void 댓글과_답글_번호를_모두_주고_다른_글_댓글은_뺀다() {
        long author = members().member().create();
        long reader = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        TrashFixtures fixtures = new TrashFixtures(jdbc);
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long otherPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long comment = fixtures.comment(postId, reader, null);
        long reply = fixtures.comment(postId, author, comment);
        long second = fixtures.comment(postId, author, null);
        fixtures.comment(otherPost, reader, null);

        assertThat(comments.commentIdsOfPost(postId))
                .containsExactlyInAnyOrder(comment, reply, second);
    }

    @Test
    void 댓글이_없으면_빈_목록() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThat(comments.commentIdsOfPost(postId)).isEmpty();
    }
}
