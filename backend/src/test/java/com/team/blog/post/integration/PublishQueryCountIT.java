package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

import com.team.blog.post.application.AutosaveService;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 쿼리 수 (002 T118, plan Performance Goals, N+1 금지). 발행의 SQL 수는 태그·사진 수와 상관없고, 자동 저장 1건은 글 상태 조회 1회 +
 * 저장 Lua 1회다.
 */
class PublishQueryCountIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";

    @MockitoSpyBean StringRedisTemplate redisTemplate;
    @Autowired AutosaveService autosaveService;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    /** 태그 {@code n}개·작성자 사진 {@code n}장인 글을 발행하는 동안 이 스레드가 준비한 SQL 수. */
    private int publishStatements(int n) throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        List<String> tags = new ArrayList<>();
        StringBuilder md = new StringBuilder("본문\n\n");
        for (int i = 0; i < n; i++) {
            tags.add("tag" + i);
            String key = "images/2026/10/" + UUID.randomUUID() + ".webp";
            jdbc.update(
                    "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height)"
                            + " VALUES (?, ?, 'image/webp', 1000, 640, 480)",
                    me,
                    key);
            md.append("![사진 ").append(i).append("](").append(BASE).append('/').append(key);
            md.append(")\n\n");
        }
        MvcResult result;
        int count;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            result =
                    api().publish(
                                    session,
                                    postId,
                                    publishBody("쿼리 수", md.toString(), tags, "PUBLIC", 0));
            count = scope.count();
        }
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_tag WHERE post_id = ?",
                                Long.class,
                                postId))
                .isEqualTo(n);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_image WHERE post_id = ?",
                                Long.class,
                                postId))
                .isEqualTo(n);
        return count;
    }

    @Test
    void 발행_SQL_수는_태그_사진_수에_비례하지_않는다() throws Exception {
        int one = publishStatements(1);
        int ten = publishStatements(10);

        assertThat(ten).as("태그·사진 1개: %d, 10개: %d", one, ten).isEqualTo(one);
    }

    @Test
    void 자동_저장_1건은_글_상태_조회_1회와_저장_Lua_1회() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        clearInvocations(redisTemplate);

        int statements;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            autosaveService.autosave(postId, me, "제목", "본문", 0);
            statements = scope.count();
        }

        // 001 계정 상태 확인(1) + 글 상태 조회(1)
        assertThat(statements).isEqualTo(2);
        List<List<?>> scriptKeys =
                mockingDetails(redisTemplate).getInvocations().stream()
                        .filter(i -> i.getMethod().getName().equals("execute"))
                        .filter(i -> i.getArguments().length > 1)
                        .filter(i -> i.getArguments()[0] instanceof RedisScript)
                        .<List<?>>map(i -> (List<?>) i.getArguments()[1])
                        .toList();
        assertThat(scriptKeys.stream().filter(k -> k.contains("autosave:post:" + postId)))
                .hasSize(1);
        // 나머지 하나는 001 요청 빈도 제한(ratelimit:autosave:*)
        assertThat(scriptKeys).hasSize(2);
    }
}
