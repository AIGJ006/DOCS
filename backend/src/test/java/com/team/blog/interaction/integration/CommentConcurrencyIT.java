package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.application.CommentPurgeService;
import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** 댓글 동시성 (007 T028·T037·T047, SC-002·SC-003, FR-014). */
class CommentConcurrencyIT extends IntegrationTestBase {

    @Autowired CommentModerationService moderation;
    @Autowired CommentPurgeService purge;
    @Autowired TransactionTemplate tx;

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    private CommentFixtures comments() {
        return new CommentFixtures(jdbc);
    }

    /** 모든 작업을 한 신호로 함께 출발시킨다. */
    private <T> List<T> together(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return task.call();
                                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_요청_5건_동시면_댓글은_1개() throws Exception {
        long author = members().member().create();
        long me = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);

        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> api().create(session, postId, "동시에 같은 댓글"));
        }
        List<MvcResult> results = together(tasks);

        assertThat(results.stream().map(CommentApi::status).sorted().toList())
                .containsExactly(200, 200, 200, 200, 201);
        assertThat(results.stream().map(CommentApi::id).distinct()).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM comment WHERE post_id = ?",
                                Long.class,
                                postId))
                .isEqualTo(1);
        assertThat(comments().commentCount(postId)).isEqualTo(1);
    }

    @Test
    void 삭제와_답글이_동시면_하나씩() throws Exception {
        long author = members().member().create();
        long a = members().member().create();
        long b = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie sessionA = TestLogin.loginAs(mockMvc, a);
        Cookie sessionB = TestLogin.loginAs(mockMvc, b);
        int replied = 0;
        int rejected = 0;
        for (int i = 0; i < 100; i++) {
            redis.delete("ratelimit:comment:" + b);
            long root = comments().on(postId, a).content("최상위 " + i).create();
            String replyText = "답글 " + i;
            List<MvcResult> results =
                    together(
                            List.of(
                                    () -> api().delete(sessionA, root),
                                    () -> api().create(sessionB, postId, replyText, root)));
            assertThat(status(results.get(0))).as("삭제 " + i).isEqualTo(204);
            MvcResult reply = results.get(1);
            List<java.util.Map<String, Object>> rows =
                    jdbc.queryForList("SELECT deleted_at FROM comment WHERE id = ?", root);
            if (status(reply) == 201) {
                replied++;
                assertThat(rows).as("답글이 먼저면 자리로 남는다 " + i).hasSize(1);
                assertThat(rows.get(0).get("deleted_at")).isNotNull();
            } else {
                rejected++;
                assertThat(status(reply)).as("답글 " + i).isEqualTo(400);
                assertThat((String) read(reply, "$.errors[0].code"))
                        .isEqualTo("REPLY_TARGET_UNAVAILABLE");
                assertThat(rows).as("삭제가 먼저면 행이 없다 " + i).isEmpty();
            }
        }
        assertThat(replied + rejected).isEqualTo(100);
        assertThat(comments().commentCount(postId)).isEqualTo((int) comments().normalCount(postId));
    }

    @Test
    void 동시_작성_20건과_삭제_숨김_해제_탈퇴_정리_뒤_수가_같다() throws Exception {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long leaving = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long p1 = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long p2 = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long root1 = comments().on(p1, leaving).content("탈퇴자 최상위").create();
        long root2 = comments().on(p2, author).content("글쓴이 최상위").create();
        comments().on(p2, leaving).parent(root2).content("탈퇴자 답글").create();
        long hiddenLater = comments().on(p1, author).content("숨길 댓글").create();
        long unhideLater = comments().on(p2, author).content("숨겼다 풀 댓글").hidden().create();

        List<Long> writers = new ArrayList<>();
        List<Cookie> sessions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long w = members().member().create();
            writers.add(w);
            sessions.add(TestLogin.loginAs(mockMvc, w));
        }
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Cookie s = sessions.get(i);
            long target = i % 2 == 0 ? p1 : p2;
            Long replyTo = i % 3 == 0 ? (target == p1 ? root1 : root2) : null;
            String content = "동시 " + i;
            tasks.add(() -> status(api().create(s, target, content, replyTo)));
        }
        tasks.add(() -> status(api().delete(authorSession, root2)));
        tasks.add(
                () -> {
                    moderation.hide(hiddenLater, admin, "SPAM", Instant.now());
                    return 0;
                });
        tasks.add(
                () -> {
                    moderation.unhide(unhideLater);
                    return 0;
                });
        together(tasks);
        tx.executeWithoutResult(s -> purge.purgeByAuthor(leaving));

        for (long postId : new long[] {p1, p2}) {
            assertThat(comments().commentCount(postId))
                    .as("글 " + postId)
                    .isEqualTo((int) comments().normalCount(postId));
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM comment WHERE author_id = ?",
                                Long.class,
                                leaving))
                .isLessThanOrEqualTo(1);
    }
}
