package com.team.blog.notification.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 011 알림 API 호출 도우미 (테스트 전용). 바꾸는 요청에는 CSRF 쿠키·헤더를 붙인다. */
public final class NotificationApi {

    private final MockMvc mockMvc;

    public NotificationApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult unreadCount(Cookie session) throws Exception {
        return perform(get("/api/notifications/unread-count"), session);
    }

    public MvcResult list(Cookie session, Integer size, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/notifications");
        if (size != null) {
            request.queryParam("size", String.valueOf(size));
        }
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        return perform(request, session);
    }

    public MvcResult read(Cookie session, Object id) throws Exception {
        return perform(TestLogin.withCsrf(put("/api/notifications/{id}/read", id), null), session);
    }

    public MvcResult readAll(Cookie session) throws Exception {
        return perform(TestLogin.withCsrf(post("/api/notifications/read-all"), null), session);
    }

    public MvcResult delete(Cookie session, Object id) throws Exception {
        return perform(
                TestLogin.withCsrf(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/notifications/{id}", id),
                        null),
                session);
    }

    public MvcResult settings(Cookie session) throws Exception {
        return perform(get("/api/me/notification-settings"), session);
    }

    public MvcResult putSettings(Cookie session, String json) throws Exception {
        return perform(
                TestLogin.withCsrf(put("/api/me/notification-settings"), null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json),
                session);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Cookie session)
            throws Exception {
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static <T> T read(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
