package com.team.blog.media.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 사진 업로드 API 시험 도우미 (presign·complete 요청과 응답 읽기). */
public final class ImageApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;

    public ImageApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** 글 사진 presign 본문. */
    public static String postBody(String contentType, long size, String thumbType, long thumbSize) {
        return """
                {"purpose":"POST","contentType":"%s","size":%d,"thumbContentType":"%s","thumbSize":%d}
                """
                .formatted(contentType, size, thumbType, thumbSize);
    }

    /** 프로필 사진 presign 본문. */
    public static String profileBody(long size) {
        return """
                {"purpose":"PROFILE","contentType":"image/webp","size":%d}
                """
                .formatted(size);
    }

    public MvcResult presign(Cookie session, String body) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(post("/api/images/presign"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    public MvcResult complete(Cookie session, long imageId) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(post("/api/images/{id}/complete", imageId), session))
                .andReturn();
    }

    public static JsonNode json(MvcResult result) {
        return JSON.readTree(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }
}
