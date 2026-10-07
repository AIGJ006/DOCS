package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.PostDraftQueryService;
import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostDraftRepository;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code post}·{@code post_draft} 매핑과 DB 제약 (002 T011, data-model §1, 05 J-2·J-3). */
class PostPersistenceIT extends IntegrationTestBase {

    @Autowired PostRepository postRepository;
    @Autowired PostDraftRepository postDraftRepository;
    @Autowired TransactionTemplate tx;
    @Autowired PostDraftQueryService draftQueries;

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void 새_임시글은_버전_0_DRAFT_렌더버전_1_빈_HTML로_저장된다() {
        long author = members().member().create();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Post saved =
                tx.execute(
                        s ->
                                postRepository.save(
                                        Post.newDraft(
                                                author, Visibility.PRIVATE, "제목", "본문", now)));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM post WHERE id = ?", saved.id());
        assertThat(row.get("edit_version")).isEqualTo(0L);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("render_version")).isEqualTo(1);
        assertThat(row.get("content_html")).isEqualTo("");
        assertThat(row.get("visibility")).isEqualTo("PRIVATE");
        assertThat(row.get("title")).isEqualTo("제목");
        assertThat(row.get("content_md")).isEqualTo("본문");
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("first_public_at")).isNull();
        assertThat(row.get("excerpt")).isNull();
        assertThat(row.get("thumbnail_url")).isNull();
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant()).isEqualTo(now);
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toInstant()).isEqualTo(now);

        Post loaded = postRepository.findById(saved.id()).orElseThrow();
        assertThat(loaded.authorId()).isEqualTo(author);
        assertThat(loaded.status()).isEqualTo(PostStatus.DRAFT);
        assertThat(loaded.isDraft()).isTrue();
        assertThat(loaded.isPublished()).isFalse();
        assertThat(loaded.editVersion()).isZero();
    }

    @Test
    void 제목을_바꿔_저장해도_SQL로_바뀐_반응_수를_덮지_않는다() {
        long author = members().member().create();
        long id = fixtures().publishedWithReactions(author, 0, 0, 0);

        tx.executeWithoutResult(
                s -> {
                    Post post = postRepository.findById(id).orElseThrow();
                    // 엔티티를 읽은 뒤 다른 경로(좋아요·조회수)가 반응 수를 바꾼다
                    jdbc.update(
                            "UPDATE post SET view_count = 77, like_count = 5, comment_count = 3"
                                    + " WHERE id = ?",
                            id);
                    ReflectionTestUtils.setField(post, "title", "바뀐 제목");
                    postRepository.flush();
                });

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT title, view_count, like_count, comment_count FROM post WHERE id ="
                                + " ?",
                        id);
        assertThat(row.get("title")).isEqualTo("바뀐 제목");
        assertThat(row.get("view_count")).isEqualTo(77L);
        assertThat(row.get("like_count")).isEqualTo(5);
        assertThat(row.get("comment_count")).isEqualTo(3);
    }

    @Test
    void 반응_수는_엔티티로_읽힌다() {
        long author = members().member().create();
        long id = fixtures().publishedWithReactions(author, 10, 2, 1);
        Post post = postRepository.findById(id).orElseThrow();
        assertThat(post.viewCount()).isEqualTo(10L);
        assertThat(post.likeCount()).isEqualTo(2);
        assertThat(post.commentCount()).isEqualTo(1);
        assertThat(post.isPublished()).isTrue();
        assertThat(post.visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void 발행인데_빈_제목이면_DB가_거부한다() {
        long author = members().member().create();
        long id = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        assertThatThrownBy(() -> jdbc.update("UPDATE post SET title = '   ' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_post_published");
    }

    @Test
    void 발행_공개인데_최초_공개_시각이_없으면_DB가_거부한다() {
        long author = members().member().create();
        long id = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE post SET visibility = 'PUBLIC' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_post_public_at");
    }

    @Test
    void 다시_발행_시각이_최초_발행보다_앞서면_DB가_거부한다() {
        long author = members().member().create();
        long id = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE post SET edited_at = published_at - interval '1"
                                                + " minute' WHERE id = ?",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_post_edited_at");
    }

    @Test
    void 본문_100001자는_DB가_거부한다() {
        long author = members().member().create();
        long id = new PostFixtures(jdbc).create(author, PostFixtures.State.DRAFT);
        jdbc.update("UPDATE post SET content_md = ? WHERE id = ?", "가".repeat(100_000), id);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE post SET content_md = ? WHERE id = ?",
                                        "가".repeat(100_001),
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_post_content");
    }

    @Test
    void 글을_완전_삭제하면_작업본도_사라진다() {
        long author = members().member().create();
        long id = fixtures().publishedWithWorkingCopy(author, "고친 제목", "고친 본문");
        assertThat(postDraftRepository.findById(id))
                .get()
                .satisfies(
                        d -> {
                            assertThat(d.postId()).isEqualTo(id);
                            assertThat(d.title()).isEqualTo("고친 제목");
                            assertThat(d.contentMd()).isEqualTo("고친 본문");
                            assertThat(d.editVersion()).isEqualTo(2L);
                            assertThat(d.createdAt()).isNotNull();
                            assertThat(d.updatedAt()).isNotNull();
                        });
        jdbc.update("DELETE FROM post WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_draft", Long.class)).isZero();
    }

    @Test
    void 작업본_삭제() {
        long author = members().member().create();
        long id = fixtures().publishedWithWorkingCopy(author, "t", "m");
        tx.executeWithoutResult(s -> postDraftRepository.deleteByPostId(id));
        assertThat(postDraftRepository.findById(id)).isEmpty();
        assertThat(postRepository.findById(id)).isPresent();
    }

    @Test
    void 잠금_조회는_내_글만_찾고_남의_글_휴지통_글은_빈_결과다() {
        long author = members().member().create();
        long other = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long mine = posts.create(author, PostFixtures.State.EDITING);
        long trashed = posts.create(author, PostFixtures.State.TRASHED);

        tx.executeWithoutResult(
                s -> {
                    assertThat(postRepository.findForUpdateByIdAndAuthorId(mine, author))
                            .get()
                            .extracting(Post::id)
                            .isEqualTo(mine);
                    assertThat(postRepository.findForUpdateByIdAndAuthorId(mine, other)).isEmpty();
                    assertThat(postRepository.findForUpdateByIdAndAuthorId(trashed, author))
                            .isEmpty();
                    assertThat(
                                    postRepository.findForUpdateByIdAndAuthorId(
                                            posts.nonexistentId(), author))
                            .isEmpty();
                });
    }

    @Test
    void 휴지통_글은_엔티티_조회에서_빠진다() {
        long author = members().member().create();
        long trashed = new PostFixtures(jdbc).create(author, PostFixtures.State.TRASHED);
        assertThat(postRepository.findById(trashed)).isEmpty();
    }

    @Test
    void 작업본_저장_시각과_작업본_있는_글_묶음_조회() {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long editing = posts.create(author, PostFixtures.State.EDITING);
        long published = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long draft = posts.create(author, PostFixtures.State.DRAFT);
        Instant savedAt = Instant.parse("2026-10-02T05:03:12Z");
        jdbc.update(
                "UPDATE post_draft SET updated_at = ? WHERE post_id = ?",
                java.sql.Timestamp.from(savedAt),
                editing);

        assertThat(draftQueries.findSavedAt(editing)).contains(savedAt);
        assertThat(draftQueries.findSavedAt(published)).isEmpty();

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(draftQueries.postIdsWithDraft(List.of(editing, published, draft)))
                    .containsExactly(editing);
            assertThat(scope.count()).isEqualTo(1);
        }
        assertThat(draftQueries.postIdsWithDraft(List.of())).isEmpty();
    }
}
