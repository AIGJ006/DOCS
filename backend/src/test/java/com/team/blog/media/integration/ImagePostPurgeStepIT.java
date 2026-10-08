package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImagePostPurgeStep;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 완전 삭제 전 사진 연결 해제 (003 T046, 006 T060에서 넘겨받음, contracts/storage.md §3-1). 지울 글에만 연결된 사진만 {@code
 * detached_at}을 채우고, 다른 글(정상·휴지통)과 함께 쓰는 사진은 그대로 둔다. 같은 트랜잭션이 롤백되면 되돌아간다.
 */
class ImagePostPurgeStepIT extends IntegrationTestBase {

    @Autowired ImagePostPurgeStep step;
    @Autowired TransactionTemplate tx;

    private void link(long postId, long imageId) {
        jdbc.update("INSERT INTO post_image (post_id, image_id) VALUES (?, ?)", postId, imageId);
    }

    private boolean detached(long imageId) {
        return jdbc.queryForObject(
                "SELECT detached_at IS NOT NULL FROM image WHERE id = ?", Boolean.class, imageId);
    }

    @Test
    void 그_글에만_연결된_사진만_detached_at을_채운다() {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long target = posts.create(author, PostFixtures.State.TRASHED);
        long other = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long otherTrashed = posts.create(author, PostFixtures.State.TRASHED);
        ImageFixtures images = new ImageFixtures(jdbc);
        long onlyHere = images.image(author).attached().create();
        long sharedWithLive = images.image(author).attached().create();
        long sharedWithTrash = images.image(author).attached().create();
        long elsewhere = images.image(author).attached().create();
        link(target, onlyHere);
        link(target, sharedWithLive);
        link(other, sharedWithLive);
        link(target, sharedWithTrash);
        link(otherTrashed, sharedWithTrash);
        link(other, elsewhere);

        tx.executeWithoutResult(status -> step.beforePurge(target));

        assertThat(detached(onlyHere)).isTrue();
        assertThat(detached(sharedWithLive)).isFalse();
        assertThat(detached(sharedWithTrash)).isFalse();
        assertThat(detached(elsewhere)).isFalse();
    }

    @Test
    void 트랜잭션이_롤백되면_되돌아간다() {
        long author = members().member().create();
        long target = new PostFixtures(jdbc).create(author, PostFixtures.State.TRASHED);
        long image = new ImageFixtures(jdbc).image(author).attached().create();
        link(target, image);

        tx.executeWithoutResult(
                status -> {
                    step.beforePurge(target);
                    status.setRollbackOnly();
                });

        assertThat(detached(image)).isFalse();
    }

    @Test
    void 이미_연결_해제된_사진의_시각은_바꾸지_않는다() {
        long author = members().member().create();
        long target = new PostFixtures(jdbc).create(author, PostFixtures.State.TRASHED);
        long image =
                new ImageFixtures(jdbc)
                        .image(author)
                        .detachedAt(java.time.Instant.parse("2026-10-01T00:00:00Z"))
                        .create();
        link(target, image);

        tx.executeWithoutResult(status -> step.beforePurge(target));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at = TIMESTAMPTZ '2026-10-01T00:00:00Z' FROM image"
                                        + " WHERE id = ?",
                                Boolean.class,
                                image))
                .isTrue();
    }
}
