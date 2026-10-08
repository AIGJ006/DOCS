package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 유예 중 노출 (015 T017, US1 #7, FR-010·011·012). 노출 제외는 004 공용 조건(작성자 {@code withdrawn_at IS NULL}),
 * 댓글 가림은 007, 팔로워 제외는 010이 구현하고 여기서는 실제 신청 API를 거친 결과만 확인한다.
 */
class WithdrawalGraceIT extends IntegrationTestBase {

    private static final String NOT_FOUND_BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    private long me;
    private long other;
    private long myPost;
    private long othersPost;
    private long myComment;

    @BeforeEach
    void setUp() throws Exception {
        me = members().member().handle("leaving01").create();
        other = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        myPost = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        othersPost = posts.create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        myComment = new CommentFixtures(jdbc).on(othersPost, me).content("떠나는 댓글").create();
        long tag =
                jdbc.queryForObject(
                        "INSERT INTO tag (name) VALUES ('떠남') RETURNING id", Long.class);
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, 0)", myPost, tag);
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, 0)",
                othersPost,
                tag);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", othersPost, me);
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", othersPost);
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/withdraw")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"confirmed\":true,\"password\":\""
                                                        + MemberFixtures.DEFAULT_PASSWORD
                                                        + "\"}"),
                                TestLogin.loginAs(mockMvc, me)))
                .andExpect(status().isOk());
    }

    private List<Number> ids(String url, Cookie viewer) throws Exception {
        var request = get(url);
        if (viewer != null) {
            request.cookie(viewer);
        }
        String body =
                mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(body, "$.items[*].id");
    }

    @Test
    void 블로그와_글_상세는_다른_사람에게_404_본문_고정() throws Exception {
        Cookie viewer = TestLogin.loginAs(mockMvc, other);
        for (String url :
                List.of(
                        "/api/members/leaving01",
                        "/api/members/leaving01/posts",
                        "/api/posts/" + myPost)) {
            mockMvc.perform(get(url))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            result ->
                                    org.skyscreamer.jsonassert.JSONAssert.assertEquals(
                                            NOT_FOUND_BODY,
                                            result.getResponse().getContentAsString(),
                                            true));
            mockMvc.perform(get(url).cookie(viewer)).andExpect(status().isNotFound());
        }
    }

    @Test
    void 홈과_태그_목록에서_빠진다() throws Exception {
        assertThat(ids("/api/posts", null))
                .extracting(Number::longValue)
                .contains(othersPost)
                .doesNotContain(myPost);
        assertThat(ids("/api/tags/떠남/posts", null))
                .extracting(Number::longValue)
                .contains(othersPost)
                .doesNotContain(myPost);
    }

    @Test
    void 남의_글의_내_댓글은_탈퇴한_사용자로_가려지고_수는_그대로() throws Exception {
        String body =
                mockMvc.perform(get("/api/posts/{id}/comments", othersPost))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(
                        (List<Object>)
                                JsonPath.read(body, "$.items[?(@.id == " + myComment + ")].state"))
                .containsExactly("WITHDRAWN_AUTHOR");
        assertThat(body).doesNotContain("떠나는 댓글");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT comment_count FROM post WHERE id = ?",
                                Integer.class,
                                othersPost))
                .isEqualTo(1);
    }

    @Test
    void 좋아요_행과_수는_그대로() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_like WHERE member_id = ?",
                                Long.class,
                                me))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT like_count FROM post WHERE id = ?",
                                Integer.class,
                                othersPost))
                .isEqualTo(1);
    }

    @Test
    void 허용_목록_밖은_403() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mockMvc.perform(get("/api/auth/csrf").cookie(session))
                .andExpect(status().is2xxSuccessful());
        for (String url :
                List.of("/api/me/settings", "/api/me/withdrawal", "/api/posts/" + othersPost)) {
            mockMvc.perform(get(url).cookie(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"))
                    .andExpect(jsonPath("$.details.action").value("RESTORE"));
        }
    }

    @Test
    @Disabled("010 머지 후 — 팔로워·팔로잉 수에서 유예 회원 제외(FR-012)")
    void 팔로워_수에서_빠진다() {}
}
