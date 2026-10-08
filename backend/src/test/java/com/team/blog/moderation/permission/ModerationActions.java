package com.team.blog.moderation.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 신고·관리자 숨김(014) 권한 실행기 (T019·T026·T033·T043, research R13, {@code permission/moderation.csv}과 004
 * {@code post-write.csv}의 {@code post.hide}·{@code post.unhide}).
 *
 * <p>댓글 행동은 대상 글에 <b>글 작성자가 쓴</b> 댓글 하나를 넣고 그 댓글을 대상으로 한다 — 행위자 {@code AUTHOR}가 곧 댓글 작성자라 글 행동과 같은
 * "자기 것" 칸이 된다. 글이 없으면 없는 댓글 번호를 쓴다.
 *
 * <p>거부되면(4xx) 신고 행동은 {@code report_case}·{@code report} 행 수가, 숨김 행동은 대상의 {@code hidden_*}와 사건 행 수가
 * 그대로인지 본다.
 */
public final class ModerationActions {

    static final String OWNER = "014";
    static final long NONEXISTENT_COMMENT = 9_000_000_000L;

    private ModerationActions() {}

    abstract static class Base implements PermissionAction {

        final JdbcTemplate jdbc;

        Base(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String owner() {
            return OWNER;
        }

        boolean comment() {
            return false;
        }

        /** 대상 번호 (글 번호 또는 새로 넣은 댓글 번호). */
        long target(Long postId) {
            if (!comment()) {
                return postId == null ? 0 : postId;
            }
            if (postId == null
                    || jdbc.queryForObject(
                                    "SELECT count(*) FROM post WHERE id = ?", Long.class, postId)
                            == 0) {
                return NONEXISTENT_COMMENT;
            }
            long author =
                    jdbc.queryForObject(
                            "SELECT author_id FROM post WHERE id = ?", Long.class, postId);
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '신고"
                                    + " 권한 시험') RETURNING id",
                            Long.class,
                            postId,
                            author);
            jdbc.update("UPDATE post SET comment_count = comment_count + 1 WHERE id = ?", postId);
            return id;
        }

        List<Map<String, Object>> state() {
            return jdbc.queryForList(
                    "SELECT (SELECT count(*) FROM report_case) AS cases, (SELECT count(*) FROM"
                            + " report) AS reports, (SELECT count(*) FROM post WHERE hidden_at IS"
                            + " NOT NULL) AS hidden_posts, (SELECT count(*) FROM comment WHERE"
                            + " hidden_at IS NOT NULL) AS hidden_comments");
        }

        abstract MockHttpServletRequestBuilder request(long target);

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            long target = target(postId);
            List<Map<String, Object>> before = state();
            ActionResult result =
                    ActionResult.of(
                            mockMvc.perform(TestLogin.withCsrf(request(target), session))
                                    .andReturn());
            if (result.status() >= 400 && !state().equals(before)) {
                throw new AssertionError("거부된 " + name() + " 요청이 신고·숨김 행을 바꿨다");
            }
            return result;
        }
    }

    abstract static class Report extends Base {
        Report(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        MockHttpServletRequestBuilder request(long target) {
            return post("/api/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            "{\"targetType\":\""
                                    + (comment() ? "COMMENT" : "POST")
                                    + "\",\"targetId\":"
                                    + target
                                    + ",\"reason\":\"SPAM\"}");
        }
    }

    abstract static class Hide extends Base {
        Hide(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        MockHttpServletRequestBuilder request(long target) {
            return put(path(target))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"SPAM\"}");
        }

        String path(long target) {
            return (comment() ? "/api/admin/comments/" : "/api/admin/posts/") + target + "/hidden";
        }
    }

    abstract static class Unhide extends Hide {
        Unhide(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        MockHttpServletRequestBuilder request(long target) {
            return delete(path(target));
        }
    }

    /** {@code report.post}: {@code POST /api/reports} (글). */
    @Profile("test")
    @Component
    public static class ReportPost extends Report {
        public ReportPost(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "report.post";
        }
    }

    /** {@code report.comment}: {@code POST /api/reports} (댓글). */
    @Profile("test")
    @Component
    public static class ReportComment extends Report {
        public ReportComment(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "report.comment";
        }

        @Override
        boolean comment() {
            return true;
        }
    }

    /** {@code admin.post.hide}: {@code PUT /api/admin/posts/{id}/hidden}. */
    @Profile("test")
    @Component
    public static class AdminHidePost extends Hide {
        public AdminHidePost(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "admin.post.hide";
        }
    }

    /** {@code admin.post.unhide}: {@code DELETE /api/admin/posts/{id}/hidden}. */
    @Profile("test")
    @Component
    public static class AdminUnhidePost extends Unhide {
        public AdminUnhidePost(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "admin.post.unhide";
        }
    }

    /** {@code admin.comment.hide}: {@code PUT /api/admin/comments/{id}/hidden}. */
    @Profile("test")
    @Component
    public static class AdminHideComment extends Hide {
        public AdminHideComment(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "admin.comment.hide";
        }

        @Override
        boolean comment() {
            return true;
        }
    }

    /** 004 {@code post-write.csv}의 {@code post.hide} — {@code admin.post.hide}와 같은 요청. */
    @Profile("test")
    @Component
    public static class PostHide extends Hide {
        public PostHide(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "post.hide";
        }
    }

    /** 004 {@code post-write.csv}의 {@code post.unhide} — {@code admin.post.unhide}와 같은 요청. */
    @Profile("test")
    @Component
    public static class PostUnhide extends Unhide {
        public PostUnhide(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "post.unhide";
        }
    }
}
