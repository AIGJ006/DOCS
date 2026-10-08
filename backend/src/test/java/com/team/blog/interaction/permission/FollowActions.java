package com.team.blog.interaction.permission;

import com.team.blog.interaction.support.FollowApi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 팔로우·피드(010) 권한 실행기 (T043, research R11, {@code permission/follow.csv}). 004 하네스의 대상은 글이라서 팔로우 행동은
 * 그 글의 <b>작성자</b>를 대상 회원으로 쓴다: {@code PUBLISHED_PUBLIC} = 정상 회원, {@code AUTHOR_WITHDRAWN} = 탈퇴 유예
 * 회원, {@code NONEXISTENT} = 없는 주소. 행위자 {@code AUTHOR}는 자기 자신이 대상이다. 팔로우 행동은 거부되면 {@code follow} 행
 * 수가 그대로인지도 본다.
 */
public final class FollowActions {

    static final String OWNER = "010";

    private FollowActions() {}

    /** 대상 글 작성자의 주소. 글이 없으면 어떤 회원과도 맞지 않는 주소. */
    static String targetHandle(JdbcTemplate jdbc, Long postId) {
        if (postId == null) {
            return "nobody_here";
        }
        return jdbc.query(
                "SELECT m.handle FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ?",
                rs -> rs.next() ? rs.getString(1) : "nobody_here",
                postId);
    }

    private static long followRows(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM follow", Long.class);
    }

    abstract static class Base implements PermissionAction {

        final JdbcTemplate jdbc;

        Base(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String owner() {
            return OWNER;
        }
    }

    /** {@code follow.put}: {@code PUT /api/members/{handle}/follow}. */
    @Profile("test")
    @Component
    public static class Put extends Base {
        public Put(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "follow.put";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            long before = followRows(jdbc);
            ActionResult result =
                    ActionResult.of(
                            new FollowApi(mockMvc).follow(session, targetHandle(jdbc, postId)));
            if (result.status() >= 400 && followRows(jdbc) != before) {
                throw new AssertionError("거부된 팔로우 요청이 follow 행을 바꿨다");
            }
            return result;
        }
    }

    /** {@code follow.delete}: {@code DELETE /api/members/{handle}/follow}. */
    @Profile("test")
    @Component
    public static class Delete extends Base {
        public Delete(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "follow.delete";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            long before = followRows(jdbc);
            ActionResult result =
                    ActionResult.of(
                            new FollowApi(mockMvc).unfollow(session, targetHandle(jdbc, postId)));
            if (result.status() >= 400 && followRows(jdbc) != before) {
                throw new AssertionError("거부된 언팔로우 요청이 follow 행을 바꿨다");
            }
            return result;
        }
    }

    /** {@code follow.followers}: {@code GET /api/members/{handle}/followers}. */
    @Profile("test")
    @Component
    public static class Followers extends Base {
        public Followers(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "follow.followers";
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(
                    new FollowApi(mockMvc).followers(session, targetHandle(jdbc, postId), null));
        }
    }

    /** {@code follow.following}: {@code GET /api/members/{handle}/following}. */
    @Profile("test")
    @Component
    public static class Following extends Base {
        public Following(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "follow.following";
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(
                    new FollowApi(mockMvc).following(session, targetHandle(jdbc, postId), null));
        }
    }

    /** {@code feed.read}: {@code GET /api/feed} (대상 없음). */
    @Profile("test")
    @Component
    public static class Feed extends Base {
        public Feed(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "feed.read";
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(new FollowApi(mockMvc).feed(session, null));
        }
    }
}
