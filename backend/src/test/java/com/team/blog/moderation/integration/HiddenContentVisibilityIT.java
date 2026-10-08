package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 숨긴 글·댓글이 어디서 사라지는가 (014 T030·T042, SC-001, US2·US3). */
class HiddenContentVisibilityIT extends IntegrationTestBase {

    private ReportApi api;
    private ReadingApi reading;
    private PostFixtures posts;
    private long author;
    private String handle;
    private Cookie authorSession;
    private Cookie memberSession;
    private Cookie admin;

    @BeforeEach
    void setUp() {
        api = new ReportApi(mockMvc);
        reading = new ReadingApi(mockMvc);
        posts = new PostFixtures(jdbc);
        handle = "hidewriter";
        author = members().member().handle(handle).create();
        authorSession = TestLogin.loginAs(mockMvc, author);
        memberSession = TestLogin.loginAs(mockMvc, members().member().create());
        admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());
    }

    @Test
    void 숨긴_글은_작성자_외_모두에게_404이고_목록에서_빠진다() throws Exception {
        long postId =
                posts.post(author).title("숨길글제목유일XYZ").contentMd("본문").published("PUBLIC").create();
        long other = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long blogCountBefore = postCount(null);
        jdbc.update("INSERT INTO tag (name) VALUES ('숨김태그') ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position) SELECT ?, id, 0 FROM tag WHERE name = '숨김태그'",
                postId);

        assertThat(status(api.hide(admin, "POST", postId, "SPAM"))).isEqualTo(200);

        for (Cookie viewer : Arrays.asList(null, memberSession, admin)) {
            MvcResult detail = reading.detail(viewer, postId);
            assertThat(ReadingApi.status(detail)).isEqualTo(404);
            assertThat(ReadingApi.ids(reading.home(viewer, null)))
                    .doesNotContain(postId)
                    .contains(other);
            assertThat(ReadingApi.ids(reading.blogPosts(viewer, handle, null)))
                    .doesNotContain(postId);
        }
        assertThat(ReadingApi.ids(reading.home(authorSession, null))).doesNotContain(postId);
        assertThat(ReadingApi.ids(reading.blogPosts(authorSession, handle, null)))
                .doesNotContain(postId);
        assertThat(postCount(null)).isEqualTo(blogCountBefore - 1);

        MvcResult search = reading.getAs(null, "/api/search/posts?q={q}", "숨길글제목유일XYZ");
        if (ReadingApi.status(search) == 200) {
            assertThat(ReadingApi.body(search)).doesNotContain("숨길글제목유일XYZ");
        }
        MvcResult tagPosts = reading.getAs(null, "/api/tags/{n}/posts", "숨김태그");
        if (ReadingApi.status(tagPosts) == 200) {
            assertThat(ReadingApi.ids(tagPosts)).doesNotContain(postId);
        }
        MvcResult trending = reading.getAs(null, "/api/posts/trending");
        if (ReadingApi.status(trending) == 200) {
            assertThat(ReadingApi.body(trending)).doesNotContain("숨길글제목유일XYZ");
        }
        MvcResult sitemap = reading.getAs(null, "/sitemap.xml");
        assertThat(
                        new String(
                                sitemap.getResponse().getContentAsByteArray(),
                                StandardCharsets.UTF_8))
                .doesNotContain("/posts/" + postId + "<");

        MvcResult mine = reading.detail(authorSession, postId);
        assertThat(ReadingApi.status(mine)).isEqualTo(200);
        assertThat((Boolean) ReadingApi.read(mine, "$.authorView.hidden")).isTrue();
        assertThat((String) ReadingApi.read(mine, "$.authorView.hiddenReason")).isEqualTo("SPAM");

        MvcResult manage = reading.getAs(authorSession, "/api/me/posts?tab=published");
        if (ReadingApi.status(manage) == 200) {
            List<Boolean> flags =
                    ReadingApi.read(manage, "$.items[?(@.id == " + postId + ")].hidden");
            assertThat(flags).containsExactly(true);
        }
    }

    private long postCount(Cookie viewer) throws Exception {
        MvcResult header = reading.blogHeader(viewer, handle);
        return ((Number) ReadingApi.read(header, "$.publicPostCount")).longValue();
    }

    @Test
    void 숨긴_댓글은_남에게_정해진_문구_작성자에게_원문_답글은_그대로() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long commenter = members().member().create();
        Cookie commenterSession = TestLogin.loginAs(mockMvc, commenter);
        CommentFixtures comments = new CommentFixtures(jdbc);
        long root = comments.on(postId, commenter).content("숨길 원문").create();
        comments.on(postId, members().member().create()).parent(root).content("남은 답글").create();
        int before = comments.commentCount(postId);

        assertThat(status(api.hide(admin, "COMMENT", root, "ABUSE"))).isEqualTo(200);
        assertThat(comments.commentCount(postId)).isEqualTo(before - 1);

        CommentApi commentApi = new CommentApi(mockMvc);
        for (Cookie viewer : Arrays.asList(memberSession, authorSession)) {
            MvcResult page = commentApi.list(viewer, postId);
            assertThat((String) read(page, "$.items[0].state")).isEqualTo("HIDDEN");
            assertThat((Object) read(page, "$.items[0].content")).isNull();
            assertThat((Object) read(page, "$.items[0].author")).isNull();
            assertThat((String) read(page, "$.items[0].replies[0].content")).isEqualTo("남은 답글");
        }
        MvcResult own = commentApi.list(commenterSession, postId);
        assertThat((String) read(own, "$.items[0].content")).isEqualTo("숨길 원문");
        assertThat((Boolean) read(own, "$.items[0].mine")).isTrue();

        assertThat(CommentApi.status(commentApi.edit(commenterSession, root, "고침")))
                .isBetween(400, 409);
        assertThat(CommentApi.status(commentApi.create(memberSession, postId, "답글", root)))
                .isBetween(400, 409);
        assertThat(CommentApi.status(commentApi.delete(commenterSession, root)))
                .isBetween(200, 204);
    }
}
