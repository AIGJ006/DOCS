package com.team.blog.notification.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.LikeApi;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 알림을 일으키는 원래 행동 (댓글·좋아요·발행·공개 범위) 도우미 (011 테스트 전용). 실제 API를 부른다. */
public final class NotificationActions {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    public NotificationActions(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    public Cookie login(long memberId) {
        return TestLogin.loginAs(mockMvc, memberId);
    }

    public long comment(long author, long postId, String content, Long replyTo) throws Exception {
        MvcResult result = new CommentApi(mockMvc).create(login(author), postId, content, replyTo);
        assertThat(CommentApi.status(result)).as(CommentApi.body(result)).isEqualTo(201);
        return CommentApi.id(result);
    }

    public void deleteComment(long author, long commentId) throws Exception {
        MvcResult result = new CommentApi(mockMvc).delete(login(author), commentId);
        assertThat(CommentApi.status(result)).as(CommentApi.body(result)).isEqualTo(204);
    }

    public void like(long member, long postId) throws Exception {
        MvcResult result = new LikeApi(mockMvc).like(login(member), postId);
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    public void unlike(long member, long postId) throws Exception {
        MvcResult result = new LikeApi(mockMvc).unlike(login(member), postId);
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    /** 새 글을 만들어 그 공개 범위로 발행한다. */
    public long publish(long author, String title, String visibility) throws Exception {
        Cookie session = login(author);
        EditorApi editor = new EditorApi(mockMvc);
        long postId = editor.createPostId(session);
        MvcResult result =
                editor.publish(
                        session,
                        postId,
                        EditorApi.publishBody(title, "본문", List.of(), visibility, 0));
        assertThat(result.getResponse().getStatus()).as(EditorApi.body(result)).isEqualTo(200);
        return postId;
    }

    /** 이미 발행한 글을 다시 발행한다 (기준 버전은 지금 값). */
    public void republish(long author, long postId, String title, String visibility)
            throws Exception {
        long base =
                jdbc.queryForObject(
                        "SELECT edit_version FROM post WHERE id = ?", Long.class, postId);
        MvcResult result =
                new EditorApi(mockMvc)
                        .publish(
                                login(author),
                                postId,
                                EditorApi.publishBody(title, "본문", List.of(), visibility, base));
        assertThat(result.getResponse().getStatus()).as(EditorApi.body(result)).isEqualTo(200);
    }

    public void changeVisibility(long author, long postId, String visibility) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                org.springframework.test.web.servlet.request
                                                        .MockMvcRequestBuilders.put(
                                                        "/api/posts/{id}/visibility", postId),
                                                login(author))
                                        .contentType("application/json")
                                        .content(EditorApi.json(Map.of("visibility", visibility))))
                        .andReturn();
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    /** 010 팔로우 API. */
    public void follow(long follower, long followee) throws Exception {
        MvcResult result =
                new com.team.blog.interaction.support.FollowApi(mockMvc)
                        .follow(login(follower), handle(followee));
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    /** 010 언팔로우 API. */
    public void unfollow(long follower, long followee) throws Exception {
        MvcResult result =
                new com.team.blog.interaction.support.FollowApi(mockMvc)
                        .unfollow(login(follower), handle(followee));
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    public String handle(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }

    /** 팔로우 행을 직접 넣는다 (010 API 없이 새 글 받는 사람 만들기). */
    public void followRow(long follower, long followee) {
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id) VALUES (?, ?)", follower, followee);
    }

    public void likeRow(long postId, long member) {
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, member);
        // 좋아요 수도 맞춘다 (취소 API가 수를 1 줄일 때 CHECK에 걸리지 않게)
        jdbc.update("UPDATE post SET like_count = like_count + 1 WHERE id = ?", postId);
    }
}
