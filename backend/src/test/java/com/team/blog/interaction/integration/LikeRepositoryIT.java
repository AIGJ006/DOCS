package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.infra.LikeRepository;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 좋아요 저장소 (009 T005, research R1·R11). */
class LikeRepositoryIT extends IntegrationTestBase {

    @Autowired LikeRepository likes;

    private long postId;
    private long member;

    @BeforeEach
    void setUp() {
        long author = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        member = members().member().create();
    }

    private long rows() {
        return jdbc.queryForObject("SELECT count(*) FROM post_like", Long.class);
    }

    @Test
    void insertIfAbsent는_처음_1_다시_0() {
        assertThat(likes.insertIfAbsent(postId, member)).isOne();
        assertThat(likes.insertIfAbsent(postId, member)).isZero();
        assertThat(rows()).isOne();
    }

    @Test
    void deleteIfPresent는_있으면_1_없으면_0() {
        likes.insertIfAbsent(postId, member);
        assertThat(likes.deleteIfPresent(postId, member)).isOne();
        assertThat(likes.deleteIfPresent(postId, member)).isZero();
        assertThat(rows()).isZero();
    }

    @Test
    void exists는_그_회원의_좋아요만_본다() {
        long other = members().member().create();
        likes.insertIfAbsent(postId, member);
        assertThat(likes.exists(postId, member)).isTrue();
        assertThat(likes.exists(postId, other)).isFalse();
    }

    @Test
    void deleteAllByMember는_그_회원의_좋아요를_지우고_글_번호를_돌려준다() {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long second = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long other = members().member().create();
        likes.insertIfAbsent(postId, member);
        likes.insertIfAbsent(second, member);
        likes.insertIfAbsent(postId, other);

        assertThat(likes.deleteAllByMember(member)).containsExactlyInAnyOrder(postId, second);

        assertThat(rows()).isOne();
        assertThat(likes.exists(postId, other)).isTrue();
        assertThat(likes.deleteAllByMember(member)).isEmpty();
    }
}
