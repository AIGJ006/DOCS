package com.team.blog.tag.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** 013 AI 태그 추천 API 시험 도우미. */
public final class AiSuggestApi {

    /** 현재 AI 동의 버전 (application.yml 기본값). */
    public static final String VERSION = "2026-10-08";

    /** 정리 후 100자를 넘는 본문 (Spring·JPA 이야기). */
    public static final String LONG_BODY =
            """
            ## JPA N+1 문제

            Spring Boot에서 `@OneToMany` 연관관계를 지연 로딩으로 두고 목록을 읽으면 글마다 댓글 쿼리가 한 번씩 더 나간다.
            fetch join이나 batch size 설정으로 쿼리 수를 줄일 수 있다. Hibernate의 영속성 컨텍스트와 트랜잭션 범위도 함께 본다.
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    public AiSuggestApi(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    /** 동의 픽스처 (현재 버전). */
    public void consent(long memberId) {
        consent(memberId, VERSION);
    }

    public void consent(long memberId, String version) {
        jdbc.update(
                "INSERT INTO member_agreement (member_id, type, version, agreed_at) VALUES (?, 'AI', ?, ?)"
                        + " ON CONFLICT (member_id, type) DO UPDATE SET version = EXCLUDED.version",
                memberId,
                version,
                Timestamp.from(Instant.now()));
    }

    public static Map<String, Object> body(
            String title, String contentMd, List<String> currentTags, boolean refresh) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("contentMd", contentMd);
        body.put("currentTags", currentTags);
        body.put("refresh", refresh);
        return body;
    }

    public static Map<String, Object> body(List<String> currentTags) {
        return body("JPA N+1 정리", LONG_BODY, currentTags, false);
    }

    public MvcResult suggest(Cookie session, Object postId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/posts/{id}/tag-suggestions", postId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(JSON.writeValueAsString(body)),
                                session))
                .andReturn();
    }

    public MvcResult suggest(Cookie session, Object postId) throws Exception {
        return suggest(session, postId, body(List.of()));
    }

    public MvcResult status(Cookie session, Object postId) throws Exception {
        var request = get("/api/posts/{id}/tag-suggestions/status", postId);
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }
}
