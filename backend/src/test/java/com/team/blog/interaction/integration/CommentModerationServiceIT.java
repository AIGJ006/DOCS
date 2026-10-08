package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 014가 부르는 숨김·해제 (007 T045, contracts/events.md §2-1). */
class CommentModerationServiceIT extends IntegrationTestBase {

    @Autowired CommentModerationService moderation;

    @Test
    void 숨김은_수_빼기_1_멱등_해제는_더하기_1_멱등() {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long id = comments.on(postId, author).create();
        assertThat(comments.commentCount(postId)).isEqualTo(1);

        moderation.hide(id, admin, "SPAM", Instant.now());
        moderation.hide(id, admin, "SPAM", Instant.now());
        assertThat(comments.commentCount(postId)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_by FROM comment WHERE id = ?", Long.class, id))
                .isEqualTo(admin);

        moderation.unhide(id);
        moderation.unhide(id);
        assertThat(comments.commentCount(postId)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden_at IS NULL FROM comment WHERE id = ?",
                                Boolean.class,
                                id))
                .isTrue();
    }

    @Test
    void 삭제된_자리와_없는_댓글은_404() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long placeholder = new CommentFixtures(jdbc).on(postId, author).deleted().create();

        assertThatThrownBy(() -> moderation.hide(placeholder, author, "SPAM", Instant.now()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> moderation.hide(9_999_999L, author, "SPAM", Instant.now()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 스냅샷은_내용을_복사한다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long id = comments.on(postId, author).content("신고될 내용").create();
        long placeholder = comments.on(postId, author).deleted().create();

        assertThat(moderation.snapshot(id)).get().extracting(s -> s.content()).isEqualTo("신고될 내용");
        assertThat(moderation.snapshot(placeholder))
                .get()
                .extracting(s -> s.content())
                .isEqualTo("");
        assertThat(moderation.snapshot(9_999_999L)).isEmpty();
    }

    @Test
    void 스냅샷은_삭제_숨김_작성자_탈퇴_상태를_싣는다() {
        long author = members().member().create();
        long leaving = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long normal = comments.on(postId, author).content("보통").create();
        long hidden = comments.on(postId, author).hidden().create();
        long placeholder = comments.on(postId, author).deleted().create();
        long byLeaving = comments.on(postId, leaving).create();
        posts.withdraw(leaving);

        CommentModerationService.CommentSnapshot s = moderation.snapshot(normal).orElseThrow();
        assertThat(s.postId()).isEqualTo(postId);
        assertThat(s.authorId()).isEqualTo(author);
        assertThat(s.deleted()).isFalse();
        assertThat(s.hidden()).isFalse();
        assertThat(s.authorWithdrawn()).isFalse();
        assertThat(moderation.snapshot(hidden).orElseThrow().hidden()).isTrue();
        assertThat(moderation.snapshot(placeholder).orElseThrow().deleted()).isTrue();
        assertThat(moderation.snapshot(byLeaving).orElseThrow().authorWithdrawn()).isTrue();
    }

    @Test
    void hiddenOf는_있는_댓글만_숨김_여부를_돌려준다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long normal = comments.on(postId, author).create();
        long hidden = comments.on(postId, author).hidden().create();

        assertThat(moderation.hiddenOf(java.util.List.of(normal, hidden, 9_999_999L)))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(normal, false, hidden, true));
        assertThat(moderation.hiddenOf(java.util.List.of())).isEmpty();
    }
}
