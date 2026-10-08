package com.team.blog.post.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link PostReadService#isReadable} (011 T009, data-model §4) — 글 상태 × 작성자·남·비회원에서 {@link
 * PostReadService#requireReadable}과 같은 판정이고 없는 글은 {@code false}.
 */
class PostReadServiceIsReadableIT extends IntegrationTestBase {

    @Autowired private PostReadService service;

    @ParameterizedTest
    @EnumSource(State.class)
    void requireReadable과_같은_판정(State state) {
        PostFixtures posts = new PostFixtures(jdbc);
        long author = members().member().create();
        long other = members().member().create();
        long postId = posts.create(author, state);
        boolean authorWithdrawn = state == State.AUTHOR_WITHDRAWN;

        Viewer authorViewer =
                new Viewer(
                        author,
                        Role.USER,
                        authorWithdrawn ? MemberStatus.WITHDRAWN : MemberStatus.ACTIVE,
                        true);
        Viewer otherViewer = new Viewer(other, Role.USER, MemberStatus.ACTIVE, true);

        for (Viewer viewer : new Viewer[] {authorViewer, otherViewer, Viewer.anonymous()}) {
            assertThat(service.isReadable(postId, viewer))
                    .as(state + " / " + viewer.id())
                    .isEqualTo(requireReadable(postId, viewer));
        }
        assertThat(service.isReadable(posts.nonexistentId(), authorViewer)).isFalse();

        boolean expectedForOthers = state == State.PUBLISHED_PUBLIC || state == State.EDITING;
        assertThat(service.isReadable(postId, otherViewer)).isEqualTo(expectedForOthers);
        assertThat(service.isReadable(postId, Viewer.anonymous())).isEqualTo(expectedForOthers);
        assertThat(service.isReadable(postId, authorViewer)).isEqualTo(state != State.TRASHED);
    }

    private boolean requireReadable(long postId, Viewer viewer) {
        try {
            service.requireReadable(postId, viewer);
            return true;
        } catch (PostNotFoundException e) {
            return false;
        }
    }
}
