package com.team.blog.discovery.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 005 읽기 행동 실행기 (004 하네스 {@code PermissionAction}, tasks T027).
 *
 * <ul>
 *   <li>{@code read-detail-api} = {@code GET /api/posts/{postId}}
 *   <li>{@code read-detail-page} = {@code GET /@{작성자 handle}/posts/{postId}}
 * </ul>
 *
 * 둘 다 읽기 행동이라 요청 전후 글 값 비교는 하지 않는다({@code isWrite() == false}).
 */
public final class ReadingPermissionActions {

    /** {@code post-read.csv}의 {@code owner} 열 값 — 러너가 이 기능 행만 고르는 기준. */
    public static final String OWNER = "005";

    private ReadingPermissionActions() {}

    /** 상세 API. */
    @Profile("test")
    @Component
    public static class ReadDetailApiAction implements PermissionAction {

        @Override
        public String name() {
            return "read-detail-api";
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            MockHttpServletRequestBuilder request = get("/api/posts/{postId}", postId);
            if (session != null) {
                request.cookie(session);
            }
            return ActionResult.of(mockMvc.perform(request).andReturn());
        }
    }

    /**
     * 상세 화면 경로. 글이 없는 행({@code NONEXISTENT})은 작성자 handle을 알 수 없으므로 글 번호로 작성자를 찾을 수 없을 때 픽스처 회원 하나의
     * 주소를 쓴다 — 없는 글은 어느 주소로 열어도 같은 404다(FR-026 ③).
     */
    @Profile("test")
    @Component
    public static class ReadDetailPageAction implements PermissionAction {

        private final JdbcTemplate jdbc;

        public ReadDetailPageAction(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String name() {
            return "read-detail-page";
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            MockHttpServletRequestBuilder request =
                    get("/@{handle}/posts/{postId}", handleOf(postId), postId);
            if (session != null) {
                request.cookie(session);
            }
            return ActionResult.of(mockMvc.perform(request).andReturn());
        }

        private String handleOf(Long postId) {
            String handle =
                    jdbc.query(
                            "SELECT m.handle FROM post p JOIN member m ON m.id = p.author_id"
                                    + " WHERE p.id = ?",
                            rs -> rs.next() ? rs.getString(1) : null,
                            postId);
            if (handle != null) {
                return handle;
            }
            String any =
                    jdbc.query(
                            "SELECT handle FROM member ORDER BY id LIMIT 1",
                            rs -> rs.next() ? rs.getString(1) : null);
            return any != null ? any : "nobody_here";
        }
    }
}
