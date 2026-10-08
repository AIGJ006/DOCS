package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.TrashablePost;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.post.infra.TrashPostRepository;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 휴지통 전용 네이티브 저장소 (006 T004, research R2·R3·R4, FR-039). */
class TrashPostRepositoryIT extends IntegrationTestBase {

    @Autowired TrashPostRepository trashPosts;
    @Autowired PostRepository posts;
    @Autowired TransactionTemplate tx;

    private PostFixtures fixtures() {
        return new PostFixtures(jdbc);
    }

    /** {@code deleted_at}을 뺀 모든 칸. */
    private Map<String, Object> rowWithoutDeletedAt(long postId) {
        Map<String, Object> row =
                new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM post WHERE id = ?", postId));
        row.remove("deleted_at");
        return row;
    }

    private Instant deletedAt(long postId) {
        Timestamp ts =
                jdbc.queryForObject(
                        "SELECT deleted_at FROM post WHERE id = ?", Timestamp.class, postId);
        return ts == null ? null : ts.toInstant();
    }

    @Test
    void lockOwned는_휴지통_글도_돌려준다() {
        long me = members().member().create();
        long trashed = fixtures().post(me).published("PUBLIC").title("지운 글").trashed().create();

        Optional<TrashablePost> found = lockOwned(trashed, me);

        assertThat(found).isPresent();
        TrashablePost post = found.get();
        assertThat(post.id()).isEqualTo(trashed);
        assertThat(post.authorId()).isEqualTo(me);
        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.title()).isEqualTo("지운 글");
        assertThat(post.isTrashed()).isTrue();
        assertThat(post.deletedAt()).isEqualTo(deletedAt(trashed));
        assertThat(post.editVersion()).isEqualTo(1L);
    }

    @Test
    void lockOwned는_남의_글과_없는_글이면_빈_값() {
        long me = members().member().create();
        long other = members().member().create();
        long othersPost = fixtures().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        long othersTrashed = fixtures().create(other, PostFixtures.State.TRASHED);

        assertThat(lockOwned(othersPost, me)).isEmpty();
        assertThat(lockOwned(othersTrashed, me)).isEmpty();
        assertThat(lockOwned(fixtures().nonexistentId(), me)).isEmpty();
        assertThat(lockById(fixtures().nonexistentId())).isEmpty();
        assertThat(lockById(othersTrashed)).isPresent();
    }

    @Test
    void PostRepository_findById는_휴지통_글을_돌려주지_않는다() {
        long me = members().member().create();
        long trashed = fixtures().create(me, PostFixtures.State.TRASHED);
        long normal = fixtures().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThat(posts.findById(trashed)).isEmpty();
        assertThat(posts.findById(normal)).isPresent();
    }

    @Test
    void markTrashed는_deleted_at만_바꾼다() {
        long me = members().member().create();
        long id =
                fixtures()
                        .post(me)
                        .published("PUBLIC")
                        .updatedAt(Instant.parse("2026-10-01T00:00:00.123456Z"))
                        .hidden()
                        .create();
        Map<String, Object> before = rowWithoutDeletedAt(id);
        Instant now = Instant.parse("2026-10-07T05:03:12.654321Z");

        tx.executeWithoutResult(s -> trashPosts.markTrashed(id, now));

        assertThat(deletedAt(id)).isEqualTo(now);
        assertThat(rowWithoutDeletedAt(id)).isEqualTo(before);
    }

    @Test
    void clearTrashed는_deleted_at만_비운다() {
        long me = members().member().create();
        long id = fixtures().create(me, PostFixtures.State.TRASHED);
        Map<String, Object> before = rowWithoutDeletedAt(id);

        tx.executeWithoutResult(s -> trashPosts.clearTrashed(id));

        assertThat(deletedAt(id)).isNull();
        assertThat(rowWithoutDeletedAt(id)).isEqualTo(before);
    }

    @Test
    void deleteById는_행을_지운다() {
        long me = members().member().create();
        long id = fixtures().create(me, PostFixtures.State.TRASHED);

        tx.executeWithoutResult(s -> trashPosts.deleteById(id));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ?", Long.class, id))
                .isZero();
    }

    @Test
    void findExpiredIds는_기준_이전_휴지통_글만_오래된_순으로_최대_limit개() {
        long me = members().member().create();
        Instant cutoff = Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        long normal = fixtures().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long recent = trashedAt(me, cutoff.plusSeconds(1));
        long exactly = trashedAt(me, cutoff);
        long older = trashedAt(me, cutoff.minus(Duration.ofDays(2)));
        long old = trashedAt(me, cutoff.minus(Duration.ofDays(1)));
        long sameTimeA = trashedAt(me, cutoff.minus(Duration.ofDays(3)));
        long sameTimeB = trashedAt(me, cutoff.minus(Duration.ofDays(3)));

        List<Long> all = trashPosts.findExpiredIds(cutoff, 100);
        List<Long> two = trashPosts.findExpiredIds(cutoff, 2);

        assertThat(all).containsExactly(sameTimeA, sameTimeB, older, old);
        assertThat(all).doesNotContain(normal, recent, exactly);
        assertThat(two).containsExactly(sameTimeA, sameTimeB);
    }

    @Test
    void findIdsByAuthorIncludingTrashed는_그_회원의_정상_휴지통_글_모두() {
        long me = members().member().create();
        long other = members().member().create();
        long a = fixtures().create(me, PostFixtures.State.DRAFT);
        long b = fixtures().create(me, PostFixtures.State.TRASHED);
        long c = fixtures().create(me, PostFixtures.State.PUBLISHED_PRIVATE);
        fixtures().create(other, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThat(trashPosts.findIdsByAuthorIncludingTrashed(me)).containsExactly(a, b, c);
    }

    private Optional<TrashablePost> lockOwned(long postId, long me) {
        Optional<TrashablePost> found = tx.execute(s -> trashPosts.lockOwned(postId, me));
        return found;
    }

    private Optional<TrashablePost> lockById(long postId) {
        Optional<TrashablePost> found = tx.execute(s -> trashPosts.lockById(postId));
        return found;
    }

    private long trashedAt(long authorId, Instant deletedAt) {
        long id = fixtures().create(authorId, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update("UPDATE post SET deleted_at = ? WHERE id = ?", Timestamp.from(deletedAt), id);
        return id;
    }
}
