package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.LikeApi.body;
import static com.team.blog.interaction.support.LikeApi.read;
import static com.team.blog.interaction.support.LikeApi.status;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.saveBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.LikeApi;
import com.team.blog.interaction.support.LikeEventProbe;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 좋아요 API (009 T012 US1, T020 US2, contracts/openapi.yaml {@code likePost}·{@code unlikePost}). */
class LikeApiIT extends IntegrationTestBase {

    @Autowired LikeEventProbe probe;

    private long author;
    private long postId;
    private long me;
    private Cookie session;

    @BeforeEach
    void setUp() {
        author = members().member().create();
        postId = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        me = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
        probe.arm();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private LikeApi api() {
        return new LikeApi(mockMvc);
    }

    private int likeCount(long id) {
        return jdbc.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class, id);
    }

    private long likeRows(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_like WHERE post_id = ?", Long.class, id);
    }

    private static void assertState(MvcResult result, boolean liked, int count) {
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.liked")).isEqualTo(liked);
        assertThat(((Number) read(result, "$.likeCount")).intValue()).isEqualTo(count);
    }

    // ---- US1 ----

    @Test
    void 좋아요하면_200_liked_true와_수_더하기_1() throws Exception {
        MvcResult result = api().like(session, postId);

        assertState(result, true, 1);
        assertThat(likeCount(postId)).isEqualTo(1);
        assertThat(likeRows(postId)).isEqualTo(1);
        assertThat(probe.events()).hasSize(1);
        PostLiked event = (PostLiked) probe.events().get(0);
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.postAuthorId()).isEqualTo(author);
        assertThat(event.memberId()).isEqualTo(me);
        assertThat(event.likedAt()).isNotNull();
    }

    @Test
    void 좋아요_취소_다시_요청은_변화_없음() throws Exception {
        assertState(api().like(session, postId), true, 1);
        assertState(api().like(session, postId), true, 1);
        assertThat(probe.liked()).isEqualTo(1);

        assertState(api().unlike(session, postId), false, 0);
        assertState(api().unlike(session, postId), false, 0);
        assertThat(probe.unliked()).isEqualTo(1);
        assertThat(likeCount(postId)).isZero();
        assertThat(likeRows(postId)).isZero();
    }

    @Test
    void 취소하면_수_빼기_1() throws Exception {
        long other = members().member().create();
        api().like(TestLogin.loginAs(mockMvc, other), postId);
        api().like(session, postId);

        assertState(api().unlike(session, postId), false, 1);
        assertThat(likeCount(postId)).isEqualTo(1);
    }

    @Test
    void 응답_수는_다른_사람_변화까지_반영() throws Exception {
        for (int i = 0; i < 3; i++) {
            api().like(TestLogin.loginAs(mockMvc, members().member().create()), postId);
        }
        assertState(api().like(session, postId), true, 4);
        assertState(api().unlike(session, postId), false, 3);
    }

    @Test
    void 글을_수정_발행해도_좋아요_그대로() throws Exception {
        assertState(api().like(session, postId), true, 1);
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        EditorApi editor = new EditorApi(mockMvc);
        assertThat(status(editor.save(authorSession, postId, saveBody("고친 제목", "고친 본문", 1))))
                .isEqualTo(200);
        MvcResult published =
                editor.publish(
                        authorSession,
                        postId,
                        publishBody("고친 제목", "고친 본문", List.of(), "PUBLIC", 2));
        assertThat(status(published)).as(body(published)).isEqualTo(200);

        assertThat(likeCount(postId)).isEqualTo(1);
        assertThat(likeRows(postId)).isEqualTo(1);
        assertState(api().like(session, postId), true, 1);
    }

    @Test
    void 상세의_likedByMe가_눌린_상태() throws Exception {
        ReadingApi reading = new ReadingApi(mockMvc);
        assertThat((Boolean) ReadingApi.read(reading.detail(session, postId), "$.viewer.likedByMe"))
                .isFalse();

        api().like(session, postId);

        MvcResult detail = reading.detail(session, postId);
        assertThat((Boolean) ReadingApi.read(detail, "$.viewer.likedByMe")).isTrue();
        assertThat(((Number) ReadingApi.read(detail, "$.likeCount")).intValue()).isEqualTo(1);
        Cookie other = TestLogin.loginAs(mockMvc, members().member().create());
        assertThat((Boolean) ReadingApi.read(reading.detail(other, postId), "$.viewer.likedByMe"))
                .isFalse();
    }

    @Test
    void 숫자가_아닌_글_번호는_404() throws Exception {
        MvcResult result = api().like(session, "abc");
        assertThat(status(result)).isEqualTo(404);
        assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
    }
}
