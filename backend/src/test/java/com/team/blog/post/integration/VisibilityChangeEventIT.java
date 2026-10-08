package com.team.blog.post.integration;

import static com.team.blog.post.support.VisibilityApi.read;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.application.PostVisibilityService;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.support.VisibilityApi;
import com.team.blog.post.support.VisibilityEventProbe;
import com.team.blog.shared.event.PostVisibilityChanged;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 범위 변경 이벤트 (004 T029, contracts/events.md, research R-11·R-25, FR-021). 커밋 후 리스너({@link
 * VisibilityEventProbe})로 받은 이벤트를 센다.
 */
class VisibilityChangeEventIT extends IntegrationTestBase {

    @Autowired private VisibilityEventProbe probe;
    @Autowired private PostVisibilityService visibilityService;
    @Autowired private TransactionTemplate transactionTemplate;

    private PostFixtures posts;
    private VisibilityApi api;
    private long author;
    private Cookie session;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
        api = new VisibilityApi(mockMvc);
        author = members().member().create();
        session = TestLogin.loginAs(mockMvc, author);
        probe.arm();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private Instant updatedAt(long postId) {
        return jdbc.queryForObject(
                        "SELECT updated_at FROM post WHERE id = ?", Timestamp.class, postId)
                .toInstant();
    }

    @Test
    void 발행_글_값이_바뀌면_PostVisibilityChanged_한_번_changedAt은_updated_at() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThat(status(api.change(session, postId, "PRIVATE"))).isEqualTo(200);

        assertThat(probe.of(PostVisibilityChanged.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.postId()).isEqualTo(postId);
                            assertThat(e.authorId()).isEqualTo(author);
                            assertThat(e.from()).isEqualTo(Visibility.PUBLIC);
                            assertThat(e.to()).isEqualTo(Visibility.PRIVATE);
                            assertThat(e.changedAt()).isEqualTo(updatedAt(postId));
                        });
        assertThat(probe.of(PostWentPublic.class)).isEmpty();
    }

    @Test
    void 처음_공개되면_PostWentPublic도_한_번() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);

        MvcResult result = api.change(session, postId, "PUBLIC");

        assertThat(status(result)).isEqualTo(200);
        Instant firstPublicAt = Instant.parse(read(result, "$.firstPublicAt"));
        assertThat(probe.of(PostVisibilityChanged.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.from()).isEqualTo(Visibility.PRIVATE);
                            assertThat(e.to()).isEqualTo(Visibility.PUBLIC);
                            assertThat(e.changedAt()).isEqualTo(firstPublicAt);
                        });
        assertThat(probe.of(PostWentPublic.class))
                .singleElement()
                .isEqualTo(new PostWentPublic(postId, author, firstPublicAt));
    }

    @Test
    void 다시_공개_같은_값_임시글은_PostWentPublic이_없다() throws Exception {
        long published = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long draft = posts.post(author).title("임시").create();

        assertThat(status(api.change(session, published, "PRIVATE"))).isEqualTo(200);
        assertThat(status(api.change(session, published, "PUBLIC"))).isEqualTo(200);
        assertThat(status(api.change(session, published, "PUBLIC"))).isEqualTo(200);
        assertThat(status(api.change(session, draft, "PRIVATE"))).isEqualTo(200);
        assertThat(status(api.change(session, draft, "PUBLIC"))).isEqualTo(200);

        assertThat(probe.of(PostVisibilityChanged.class))
                .extracting(PostVisibilityChanged::postId, PostVisibilityChanged::to)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(published, Visibility.PRIVATE),
                        org.assertj.core.groups.Tuple.tuple(published, Visibility.PUBLIC));
        assertThat(probe.of(PostWentPublic.class)).isEmpty();
    }

    @Test
    void 롤백되면_이벤트가_없다() {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        Viewer viewer = new Viewer(author, Role.USER, MemberStatus.ACTIVE, true);

        transactionTemplate.executeWithoutResult(
                tx -> {
                    visibilityService.change(viewer, postId, "PUBLIC");
                    tx.setRollbackOnly();
                });

        assertThat(probe.all()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT visibility FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("PRIVATE");
    }

    @Test
    void 리스너가_예외를_던져도_변경은_성공한다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        probe.failAfterRecording();

        MvcResult result = api.change(session, postId, "PUBLIC");

        assertThat(status(result)).isEqualTo(200);
        assertThat(probe.of(PostVisibilityChanged.class)).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT visibility FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("PUBLIC");
    }
}
