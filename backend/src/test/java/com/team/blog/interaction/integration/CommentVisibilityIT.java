package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.body;
import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 글 상태·숨김·탈퇴에 따라 댓글 감추기 (007 T044, US4, FR-019·020·036·038, SC-007). */
class CommentVisibilityIT extends IntegrationTestBase {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    private long author;
    private long writer;
    private long postId;

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    private CommentFixtures comments() {
        return new CommentFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        author = members().member().create();
        writer = members().member().nickname("댓글쓴이").create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    @Test
    void 비공개로_바꾸면_남에게_404_다시_공개하면_그대로() throws Exception {
        comments().on(postId, writer).content("남은 댓글").create();
        Cookie reader = TestLogin.loginAs(mockMvc, members().member().create());
        String before = body(api().list(reader, postId));

        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", postId);
        assertThat(status(api().list(reader, postId))).isEqualTo(404);
        assertThat(status(api().list(TestLogin.loginAs(mockMvc, author), postId))).isEqualTo(200);

        jdbc.update("UPDATE post SET visibility = 'PUBLIC' WHERE id = ?", postId);
        assertThat(body(api().list(reader, postId))).isEqualTo(before);
    }

    @Test
    void 숨긴_댓글은_남과_글_주인에게_문구만() throws Exception {
        long root = comments().on(postId, writer).content("숨긴원문XYZ").hidden().at(BASE).create();
        comments()
                .on(postId, author)
                .parent(root)
                .content("보이는 답글")
                .at(BASE.plusSeconds(1))
                .create();
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());
        for (Cookie viewer :
                java.util.Arrays.asList(
                        null,
                        TestLogin.loginAs(mockMvc, author),
                        admin,
                        TestLogin.loginAs(mockMvc, members().member().create()))) {
            MvcResult page = api().list(viewer, postId);
            assertThat(body(page)).doesNotContain("숨긴원문XYZ").doesNotContain("댓글쓴이");
            assertThat((String) read(page, "$.items[0].state")).isEqualTo("HIDDEN");
            assertThat((Object) read(page, "$.items[0].author")).isNull();
            assertThat((String) read(page, "$.items[0].replies[0].content")).isEqualTo("보이는 답글");
        }
    }

    @Test
    void 숨긴_댓글은_작성자에게_원문() throws Exception {
        comments().on(postId, writer).content("숨긴원문XYZ").hidden().create();
        MvcResult page = api().list(TestLogin.loginAs(mockMvc, writer), postId);

        assertThat((String) read(page, "$.items[0].state")).isEqualTo("HIDDEN");
        assertThat((String) read(page, "$.items[0].content")).isEqualTo("숨긴원문XYZ");
        assertThat((Boolean) read(page, "$.items[0].mine")).isTrue();
        assertThat((String) read(page, "$.items[0].author.nickname")).isEqualTo("댓글쓴이");
    }

    @Test
    void 탈퇴_유예_작성자_댓글은_문구와_수_그대로() throws Exception {
        comments().on(postId, writer).content("떠날 사람 댓글").create();
        new PostFixtures(jdbc).withdraw(writer);

        MvcResult page = api().list(null, postId);
        assertThat((String) read(page, "$.items[0].state")).isEqualTo("WITHDRAWN_AUTHOR");
        assertThat(body(page)).doesNotContain("떠날 사람 댓글");
        assertThat(comments().commentCount(postId)).isEqualTo(1);

        jdbc.update(
                "UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", writer);
        MvcResult restored = api().list(null, postId);
        assertThat((String) read(restored, "$.items[0].state")).isEqualTo("NORMAL");
        assertThat((String) read(restored, "$.items[0].content")).isEqualTo("떠날 사람 댓글");
    }

    @Test
    void 휴지통_글의_댓글은_보존되고_복구하면_돌아온다() throws Exception {
        long id = comments().on(postId, writer).create();
        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", postId);
        Cookie writerSession = TestLogin.loginAs(mockMvc, writer);

        assertThat(status(api().list(null, postId))).isEqualTo(404);
        assertThat(status(api().create(writerSession, postId, "휴지통 글에"))).isEqualTo(404);

        jdbc.update("UPDATE post SET deleted_at = NULL WHERE id = ?", postId);
        List<Integer> ids = read(api().list(null, postId), "$.items[*].id");
        assertThat(ids).containsExactly((int) id);
    }

    @Test
    void 글_작성자_탈퇴_유예면_보기_쓰기_404() throws Exception {
        comments().on(postId, writer).create();
        new PostFixtures(jdbc).withdraw(author);
        Cookie writerSession = TestLogin.loginAs(mockMvc, writer);

        assertThat(status(api().list(writerSession, postId))).isEqualTo(404);
        assertThat(status(api().create(writerSession, postId, "쓰기"))).isEqualTo(404);
    }

    @Test
    void 숨긴_글은_작성자만_보고_쓰기는_404() throws Exception {
        long hiddenPost = new PostFixtures(jdbc).create(author, PostFixtures.State.HIDDEN);
        comments().on(hiddenPost, writer).create();
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);

        assertThat(status(api().list(authorSession, hiddenPost))).isEqualTo(200);
        assertThat(status(api().list(TestLogin.loginAs(mockMvc, writer), hiddenPost)))
                .isEqualTo(404);
        assertThat(status(api().create(authorSession, hiddenPost, "쓰기"))).isEqualTo(404);
    }
}
