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

    // ---- US2 (T020) ----

    @Test
    void 볼_수_없는_자기_글은_400이_아니라_404() throws Exception {
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        long draft = posts().create(author, PostFixtures.State.DRAFT);
        long trashed = posts().create(author, PostFixtures.State.TRASHED);

        for (long id : new long[] {draft, trashed}) {
            MvcResult result = api().like(authorSession, id);
            assertThat(status(result)).as("글 " + id).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
        MvcResult own = api().like(authorSession, postId);
        assertThat(status(own)).isEqualTo(400);
        assertThat((String) read(own, "$.code")).isEqualTo("CANNOT_LIKE_OWN_POST");
        assertThat((String) read(own, "$.message")).isEqualTo("내 글에는 좋아요를 누를 수 없어요");
        assertThat((List<?>) read(own, "$.errors")).isEmpty();
    }

    @Test
    void 좋아요_취소_합쳐_61번째는_429와_Retry_After() throws Exception {
        for (int i = 0; i < 60; i++) {
            MvcResult result =
                    i % 2 == 0 ? api().like(session, postId) : api().unlike(session, postId);
            assertThat(status(result)).as("요청 " + (i + 1)).isEqualTo(200);
        }

        MvcResult result = api().like(session, postId);

        assertThat(status(result)).isEqualTo(429);
        assertThat((String) read(result, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After")))
                .isBetween(1, 60);
        assertThat(likeCount(postId)).isZero();
        assertThat(likeRows(postId)).isZero();
    }

    @Test
    void 앞_단계에서_걸린_요청은_세지_않는다() throws Exception {
        long hidden = posts().create(author, PostFixtures.State.HIDDEN);
        for (int i = 0; i < 70; i++) {
            assertThat(status(api().like(session, hidden))).isEqualTo(404);
        }
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        for (int i = 0; i < 70; i++) {
            assertThat(status(api().like(authorSession, postId))).isEqualTo(400);
        }

        assertState(api().like(session, postId), true, 1);
        assertThat(redis.hasKey("ratelimit:like:" + author)).isFalse();
    }

    @Test
    void 관리자도_일반_회원과_같다() throws Exception {
        long admin = members().member().role("ADMIN").create();
        Cookie adminSession = TestLogin.loginAs(mockMvc, admin);

        assertState(api().like(adminSession, postId), true, 1);
        long privatePost = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        assertThat(status(api().like(adminSession, privatePost))).isEqualTo(404);
        long adminPost = posts().create(admin, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat(status(api().like(adminSession, adminPost))).isEqualTo(400);
    }

    @Test
    void 정지_회원_남은_세션은_403() throws Exception {
        members().suspend(me, java.time.Instant.now().plus(java.time.Duration.ofDays(7)), "시험");

        MvcResult result = api().like(session, postId);

        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(likeCount(postId)).isZero();
    }

    @Test
    void 인증_전_회원은_403_EMAIL_NOT_VERIFIED() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        MvcResult result = api().like(TestLogin.loginAs(mockMvc, unverified), postId);
        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
        MvcResult anonymous = api().like(null, postId);
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat((String) read(anonymous, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void 좋아요한_글이_비공개가_되어도_보존되고_다시_공개하면_그대로() throws Exception {
        assertState(api().like(session, postId), true, 1);
        ReadingApi reading = new ReadingApi(mockMvc);

        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", postId);
        assertThat(ReadingApi.status(reading.detail(session, postId))).isEqualTo(404);
        assertThat(status(api().unlike(session, postId))).isEqualTo(404);
        assertThat(likeCount(postId)).isEqualTo(1);
        assertThat(likeRows(postId)).isEqualTo(1);

        jdbc.update("UPDATE post SET deleted_at = now() - interval '1 hour' WHERE id = ?", postId);
        jdbc.update(
                "UPDATE post SET deleted_at = NULL, visibility = 'PUBLIC' WHERE id = ?", postId);

        MvcResult detail = reading.detail(session, postId);
        assertThat(((Number) ReadingApi.read(detail, "$.likeCount")).intValue()).isEqualTo(1);
        assertThat((Boolean) ReadingApi.read(detail, "$.viewer.likedByMe")).isTrue();
    }
}
