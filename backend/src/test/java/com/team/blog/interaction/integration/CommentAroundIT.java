package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.body;
import static com.team.blog.interaction.support.CommentApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 알림에서 특정 댓글로 바로 가기 (007 T050, US5, FR-025, research R11). */
class CommentAroundIT extends IntegrationTestBase {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private long author;
    private long reader;
    private long postId;
    private final List<Long> roots = new ArrayList<>();
    private final List<Long> replies = new ArrayList<>();

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    @BeforeEach
    void setUp() {
        roots.clear();
        replies.clear();
        author = members().member().create();
        reader = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures c = new CommentFixtures(jdbc);
        for (int i = 0; i < 60; i++) {
            roots.add(c.on(postId, reader).at(BASE.plusSeconds(i * 100L)).create());
        }
        long r45 = roots.get(44);
        for (int i = 0; i < 8; i++) {
            replies.add(
                    c.on(postId, author).parent(r45).at(BASE.plusSeconds(4400 + i + 1)).create());
        }
    }

    private MvcResult around(Object id) throws Exception {
        return api().list(null, postId, "around=" + id);
    }

    @Test
    void 대상_최상위부터_20개와_prevCursor() throws Exception {
        MvcResult page = around(roots.get(44));
        List<Integer> ids = read(page, "$.items[*].id");
        assertThat(ids).hasSize(16);
        assertThat(ids.get(0)).isEqualTo(roots.get(44).intValue());
        assertThat((String) read(page, "$.prevCursor")).isNotNull();
        assertThat(((Number) read(page, "$.focusCommentId")).longValue()).isEqualTo(roots.get(44));
        assertThat((Object) read(page, "$.nextCursor")).isNull();
    }

    @Test
    void prevCursor로_앞_20개() throws Exception {
        String prev = read(around(roots.get(44)), "$.prevCursor");
        MvcResult before = api().listAfter(null, postId, prev);
        List<Integer> ids = read(before, "$.items[*].id");
        assertThat(ids)
                .containsExactlyElementsOf(
                        roots.subList(24, 44).stream().map(Long::intValue).toList());
        String again = read(before, "$.prevCursor");
        assertThat(again).isNotNull();
        List<Integer> first = read(api().listAfter(null, postId, again), "$.items[*].id");
        assertThat(first)
                .containsExactlyElementsOf(
                        roots.subList(4, 24).stream().map(Long::intValue).toList());
    }

    @Test
    void 네_번째_이후_답글이면_대상까지_펼친다() throws Exception {
        long target = replies.get(4);
        MvcResult page = around(target);
        List<Integer> shown = read(page, "$.items[0].replies[*].id");
        assertThat(shown)
                .containsExactlyElementsOf(
                        replies.subList(0, 5).stream().map(Long::intValue).toList());
        assertThat((Integer) read(page, "$.items[0].replyCount")).isEqualTo(8);
        assertThat((String) read(page, "$.items[0].repliesNextCursor")).isNotNull();
        assertThat(((Number) read(page, "$.focusCommentId")).longValue()).isEqualTo(target);
        List<Integer> rest =
                read(
                        api().replies(
                                        null,
                                        roots.get(44),
                                        read(page, "$.items[0].repliesNextCursor")),
                        "$.items[*].id");
        assertThat(rest)
                .containsExactlyElementsOf(
                        replies.subList(5, 8).stream().map(Long::intValue).toList());
    }

    @Test
    void 상한을_넘는_답글은_처음_3개만() throws Exception {
        CommentFixtures c = new CommentFixtures(jdbc);
        long root = roots.get(10);
        List<Long> many = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            many.add(
                    c.on(postId, author)
                            .parent(root)
                            .at(BASE.plusSeconds(1000 + i * 0L).plusMillis(i + 1))
                            .create());
        }
        MvcResult page = around(many.get(103));
        assertThat((List<Object>) read(page, "$.items[0].replies")).hasSize(3);
        assertThat(((Number) read(page, "$.focusCommentId")).longValue()).isEqualTo(root);
    }

    @Test
    void 다른_글_삭제_숨김_없는_댓글이면_첫_페이지와_같다() throws Exception {
        CommentFixtures c = new CommentFixtures(jdbc);
        long otherPost = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long elsewhere = c.on(otherPost, reader).create();
        long hidden = c.on(postId, reader).hidden().at(BASE.plusSeconds(9000)).create();
        long placeholder = c.on(postId, reader).deleted().at(BASE.plusSeconds(9001)).create();
        c.on(postId, author).parent(placeholder).at(BASE.plusSeconds(9002)).create();
        String first = body(api().list(null, postId));

        for (Object id : new Object[] {elsewhere, hidden, placeholder, 9_999_999L}) {
            MvcResult page = around(id);
            assertThat(body(page)).as("around=" + id).isEqualTo(first);
        }
    }

    @Test
    void 숫자가_아닌_around는_무시() throws Exception {
        String first = body(api().list(null, postId));
        assertThat(body(around("abc"))).isEqualTo(first);
        assertThat(body(around("-1"))).isEqualTo(first);
        assertThat(body(around("1e3"))).isEqualTo(first);
    }
}
