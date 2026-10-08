package com.team.blog.moderation.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 014 API 호출 도우미 (신고·관리자). */
public final class ReportApi {

    private final MockMvc mvc;

    public ReportApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    public MvcResult report(Cookie session, String type, long id, String reason, String detail)
            throws Exception {
        String body =
                "{\"targetType\":\""
                        + type
                        + "\",\"targetId\":"
                        + id
                        + ",\"reason\":\""
                        + reason
                        + "\""
                        + (detail == null ? "" : ",\"detail\":\"" + detail + "\"")
                        + "}";
        return raw(session, body);
    }

    public MvcResult reportPost(Cookie session, long postId, String reason) throws Exception {
        return report(session, "POST", postId, reason, null);
    }

    public MvcResult raw(Cookie session, String json) throws Exception {
        return perform(
                post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(json),
                session);
    }

    public MvcResult cases(Cookie session, String tab, String cursor) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/admin/reports");
        if (tab != null) {
            req.param("tab", tab);
        }
        if (cursor != null) {
            req.param("cursor", cursor);
        }
        if (session != null) {
            req.cookie(session);
        }
        return mvc.perform(req).andReturn();
    }

    public MvcResult detail(Cookie session, long caseId) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/admin/reports/{id}", caseId);
        if (session != null) {
            req.cookie(session);
        }
        return mvc.perform(req).andReturn();
    }

    public MvcResult resolve(Cookie session, long caseId, String action, String reason)
            throws Exception {
        String body =
                "{\"action\":\""
                        + action
                        + "\""
                        + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                        + "}";
        return perform(
                post("/api/admin/reports/{id}/resolution", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                session);
    }

    public MvcResult hide(Cookie session, String type, long id, String reason) throws Exception {
        return perform(
                put(path(type, id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"),
                session);
    }

    public MvcResult unhide(Cookie session, String type, long id) throws Exception {
        return perform(delete(path(type, id)), session);
    }

    public MvcResult member(Cookie session, String handle) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/admin/members/{h}", handle);
        if (session != null) {
            req.cookie(session);
        }
        return mvc.perform(req).andReturn();
    }

    public MvcResult suspend(Cookie session, String handle, String duration, String reason)
            throws Exception {
        String body =
                "{"
                        + (duration == null ? "" : "\"duration\":\"" + duration + "\",")
                        + "\"reason\":"
                        + (reason == null ? "null" : "\"" + reason + "\"")
                        + "}";
        return perform(
                post("/api/admin/members/{h}/suspensions", handle)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                session);
    }

    public MvcResult lift(Cookie session, String handle) throws Exception {
        return perform(delete("/api/admin/members/{h}/suspensions/current", handle), session);
    }

    private static String path(String type, long id) {
        return ("POST".equals(type) ? "/api/admin/posts/" : "/api/admin/comments/")
                + id
                + "/hidden";
    }

    private MvcResult perform(MockHttpServletRequestBuilder req, Cookie session) throws Exception {
        return mvc.perform(TestLogin.withCsrf(req, session)).andReturn();
    }

    public static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    public static String body(MvcResult r) {
        return new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static <T> T read(MvcResult r, String path) {
        return (T) JsonPath.read(body(r), path);
    }
}
