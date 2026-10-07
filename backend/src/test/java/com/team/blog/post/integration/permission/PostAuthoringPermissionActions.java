package com.team.blog.post.integration.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 002 권한 매트릭스 실행기 (T042·T070·T090). {@code post-write.csv}의 {@code owner=002} 행을 실행한다. 쓰기 요청 본문은 그
 * 행동이 성공할 수 있는 값(유효한 제목·본문, 현재 버전)으로 채워 거부 이유가 권한뿐이게 한다. 요청 본문의 {@code authorId}는 서버가 무시해야 한다(원칙
 * III).
 */
public final class PostAuthoringPermissionActions {

    static final String OWNER = "002";

    private PostAuthoringPermissionActions() {}

    /** 현재 버전 = max(post, post_draft) — 행 준비는 Redis 보관분을 만들지 않는다. 없는 글이면 0. */
    static long currentVersion(JdbcTemplate jdbc, Long postId) {
        if (postId == null) {
            return 0;
        }
        List<Long> versions =
                jdbc.queryForList(
                        "SELECT GREATEST(p.edit_version, COALESCE(d.edit_version, 0)) FROM post p"
                                + " LEFT JOIN post_draft d ON d.post_id = p.id WHERE p.id = ?",
                        Long.class,
                        postId);
        return versions.isEmpty() ? 0 : versions.get(0);
    }

    static MockHttpServletRequestBuilder withSession(
            MockHttpServletRequestBuilder request, Cookie session) {
        if (session != null) {
            request.cookie(session);
        }
        return request;
    }

    /** [새 글] {@code POST /api/posts}. */
    @Profile("test")
    @Component
    public static class CreatePostAction implements PermissionAction {
        @Override
        public String name() {
            return "post.create";
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("authorId", 999_999L);
            body.put("title", "권한 매트릭스");
            return ActionResult.of(
                    mockMvc.perform(
                                    TestLogin.withCsrf(post("/api/posts"), session)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(EditorApi.json(body)))
                            .andReturn());
        }
    }

    /** 에디터 열기 {@code GET /api/posts/{id}/working-copy} (읽기, 계정 상태 판정 없음). */
    @Profile("test")
    @Component
    public static class OpenEditorAction implements PermissionAction {
        @Override
        public String name() {
            return "post.editor";
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
            return ActionResult.of(
                    mockMvc.perform(
                                    withSession(
                                            get("/api/posts/{postId}/working-copy", postId),
                                            session))
                            .andReturn());
        }
    }

    /** 발행 {@code POST /api/posts/{id}/publish}. */
    @Profile("test")
    @Component
    public static class PublishAction implements PermissionAction {
        private final JdbcTemplate jdbc;

        public PublishAction(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String name() {
            return "post.publish";
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            Map<String, Object> body =
                    EditorApi.publishBody(
                            "권한 매트릭스",
                            "본문",
                            List.of("spring"),
                            "PUBLIC",
                            currentVersion(jdbc, postId));
            body.put("authorId", 999_999L);
            return ActionResult.of(
                    mockMvc.perform(
                                    TestLogin.withCsrf(
                                                    post("/api/posts/{postId}/publish", postId),
                                                    session)
                                            .header("Idempotency-Key", UUID.randomUUID().toString())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(EditorApi.json(body)))
                            .andReturn());
        }
    }

    /**
     * 자동 저장·수동 저장 공통: 휴지통 글이면 Redis 보관분을 미리 넣어 "키가 있어도 404"를 재현하고, 거부되면 Redis Hash가 그대로인지
     * 확인한다(SC-008 — 하네스의 DB 스냅샷 비교에 Redis를 더함).
     */
    abstract static class SaveAction implements PermissionAction {
        private final JdbcTemplate jdbc;
        private final StringRedisTemplate redis;

        SaveAction(JdbcTemplate jdbc, StringRedisTemplate redis) {
            this.jdbc = jdbc;
            this.redis = redis;
        }

        abstract String path();

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            String key = "autosave:post:" + postId;
            List<Long> trashedAuthor =
                    jdbc.queryForList(
                            "SELECT author_id FROM post WHERE id = ? AND deleted_at IS NOT NULL",
                            Long.class,
                            postId);
            if (!trashedAuthor.isEmpty()) {
                redis.opsForHash()
                        .putAll(
                                key,
                                Map.of(
                                        "memberId", String.valueOf(trashedAuthor.get(0)),
                                        "title", "휴지통 전 입력",
                                        "contentMd", "본문",
                                        "version", "9",
                                        "savedAt", Instant.now().toString()));
            }
            Map<Object, Object> before = redis.opsForHash().entries(key);
            long version = currentVersion(jdbc, postId);
            Map<String, Object> body = EditorApi.saveBody("권한 매트릭스", "본문", version);
            body.put("authorId", 999_999L);
            ActionResult result =
                    ActionResult.of(
                            mockMvc.perform(
                                            TestLogin.withCsrf(put(path(), postId), session)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(EditorApi.json(body)))
                                    .andReturn());
            if (result.status() >= 400) {
                Map<Object, Object> after = redis.opsForHash().entries(key);
                if (!after.equals(before)) {
                    throw new AssertionError("거부된 요청이 Redis 보관분을 바꿨습니다: " + before + " → " + after);
                }
            }
            return result;
        }
    }

    /** 자동 저장 {@code PUT /api/posts/{id}/autosave}. */
    @Profile("test")
    @Component
    public static class AutosaveAction extends SaveAction {
        public AutosaveAction(JdbcTemplate jdbc, StringRedisTemplate redis) {
            super(jdbc, redis);
        }

        @Override
        public String name() {
            return "post.autosave";
        }

        @Override
        String path() {
            return "/api/posts/{postId}/autosave";
        }
    }

    /** 수동 저장 {@code PUT /api/posts/{id}/working-copy}. */
    @Profile("test")
    @Component
    public static class ManualSaveAction extends SaveAction {
        public ManualSaveAction(JdbcTemplate jdbc, StringRedisTemplate redis) {
            super(jdbc, redis);
        }

        @Override
        public String name() {
            return "post.save";
        }

        @Override
        String path() {
            return "/api/posts/{postId}/working-copy";
        }
    }

    /**
     * 변경 취소 {@code DELETE /api/posts/{id}/working-copy} (T090). 거부되면 작업본({@code post_draft})도 그대로인지
     * 확인한다(하네스의 {@code post} 스냅샷 비교에 더함).
     */
    @Profile("test")
    @Component
    public static class DiscardAction implements PermissionAction {
        private final JdbcTemplate jdbc;

        public DiscardAction(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String name() {
            return "post.discard";
        }

        @Override
        public String owner() {
            return OWNER;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            List<Map<String, Object>> before =
                    jdbc.queryForList(
                            "SELECT title, content_md, edit_version FROM post_draft WHERE post_id = ?",
                            postId);
            ActionResult result =
                    ActionResult.of(
                            mockMvc.perform(
                                            TestLogin.withCsrf(
                                                    delete(
                                                            "/api/posts/{postId}/working-copy",
                                                            postId),
                                                    session))
                                    .andReturn());
            if (result.status() >= 400) {
                List<Map<String, Object>> after =
                        jdbc.queryForList(
                                "SELECT title, content_md, edit_version FROM post_draft"
                                        + " WHERE post_id = ?",
                                postId);
                if (!after.equals(before)) {
                    throw new AssertionError("거부된 변경 취소가 작업본을 바꿨습니다: " + before + " → " + after);
                }
            }
            return result;
        }
    }
}
