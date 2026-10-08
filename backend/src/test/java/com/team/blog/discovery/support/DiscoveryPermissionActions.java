package com.team.blog.discovery.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 012 목록 행동 실행기 (004 하네스 {@code PermissionAction}, tasks T040, research R16). 모두 읽기 행동이다.
 *
 * <ul>
 *   <li>{@code trending.list}: 대상 글에 좋아요 수를 주고 {@code GET /api/posts/trending}(스냅샷이 없어 즉시 계산)에 그 글이
 *       있는가
 *   <li>{@code search.posts}: 대상 글 제목에 고유 낱말을 넣고 {@code GET /api/search/posts?q=}에 그 글이 있는가
 *   <li>{@code search.posts.blog}: 같은 검색을 {@code blog=작성자 주소}로
 *   <li>{@code search.people}: 대상 글 작성자 주소로 {@code GET /api/search/people?q=}에 그 회원이 있는가
 *   <li>{@code sitemap}: {@code GET /sitemap.xml}에 그 글 주소가 있는가 (게이트 밖 경로)
 * </ul>
 */
public final class DiscoveryPermissionActions {

    public static final String OWNER = "012";

    /** 대상 글 제목에만 넣는 낱말. 행마다 DB가 비워지므로 고정이어도 된다. */
    static final String WORD = "권한매트릭스낱말";

    private DiscoveryPermissionActions() {}

    abstract static class ListAction implements PermissionAction {

        protected final JdbcTemplate jdbc;

        ListAction(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        String authorHandle(long postId) {
            return jdbc.queryForObject(
                    "SELECT m.handle FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ?",
                    String.class,
                    postId);
        }

        static MvcResult call(
                MockMvc mockMvc, MockHttpServletRequestBuilder request, Cookie session)
                throws Exception {
            if (session != null) {
                request.cookie(session);
            }
            return mockMvc.perform(request).andReturn();
        }

        static String body(MvcResult result) {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        /** 200이면 {@code jsonPath} 값 목록에 {@code expected}가 있는지, 아니면 상태·오류 코드. */
        static ActionResult listed(MvcResult result, String jsonPath, Object expected) {
            if (result.getResponse().getStatus() != 200) {
                return ActionResult.of(result);
            }
            List<Object> values = JsonPath.read(body(result), jsonPath);
            boolean included =
                    values.stream()
                            .anyMatch(v -> String.valueOf(v).equals(String.valueOf(expected)));
            return ActionResult.listed(200, included);
        }
    }

    @Profile("test")
    @Component
    public static class TrendingListAction extends ListAction {

        public TrendingListAction(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "trending.list";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            jdbc.update("UPDATE post SET like_count = 5 WHERE id = ?", postId);
            return listed(
                    call(mockMvc, get("/api/posts/trending"), session), "$.items[*].id", postId);
        }
    }

    @Profile("test")
    @Component
    public static class SearchPostsAction extends ListAction {

        public SearchPostsAction(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "search.posts";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            jdbc.update("UPDATE post SET title = ? WHERE id = ?", WORD + " 정리", postId);
            return listed(
                    call(mockMvc, get("/api/search/posts").queryParam("q", WORD), session),
                    "$.items[*].id",
                    postId);
        }
    }

    @Profile("test")
    @Component
    public static class SearchBlogPostsAction extends ListAction {

        public SearchBlogPostsAction(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "search.posts.blog";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            jdbc.update("UPDATE post SET title = ? WHERE id = ?", WORD + " 정리", postId);
            MockHttpServletRequestBuilder request =
                    get("/api/search/posts")
                            .queryParam("q", WORD)
                            .queryParam("blog", authorHandle(postId));
            return listed(call(mockMvc, request, session), "$.items[*].id", postId);
        }
    }

    @Profile("test")
    @Component
    public static class SearchPeopleAction extends ListAction {

        public SearchPeopleAction(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "search.people";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            String handle = authorHandle(postId);
            return listed(
                    call(mockMvc, get("/api/search/people").queryParam("q", handle), session),
                    "$.items[*].handle",
                    handle);
        }
    }

    @Profile("test")
    @Component
    public static class SitemapAction extends ListAction {

        public SitemapAction(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public String name() {
            return "sitemap";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            MvcResult result = call(mockMvc, get("/sitemap.xml"), session);
            if (result.getResponse().getStatus() != 200) {
                return ActionResult.of(result);
            }
            return ActionResult.listed(200, body(result).contains("/posts/" + postId + "</loc>"));
        }
    }
}
