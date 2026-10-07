package com.team.blog.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.CapturingMailSender;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** US1 통합 테스트 도우미: 가입 요청 본문, 비동기 메일 기다리기. */
final class SignupRequests {

    static final String CURRENT_VERSION = "2026-10-07";
    static final String PASSWORD = "Blog#2026a";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SignupRequests() {}

    /** 규칙에 맞는 가입 본문. 바꿀 칸만 {@link Body#with}로 덮어쓴다. */
    static Body body(String email, String handle, String nickname) {
        Body body = new Body();
        body.values.put("email", email);
        body.values.put("handle", handle);
        body.values.put("password", PASSWORD);
        body.values.put("passwordConfirm", PASSWORD);
        body.values.put("nickname", nickname);
        body.values.put(
                "agreements",
                Map.of("termsVersion", CURRENT_VERSION, "privacyVersion", CURRENT_VERSION));
        return body;
    }

    static final class Body {
        private final Map<String, Object> values = new LinkedHashMap<>();

        Body with(String key, Object value) {
            values.put(key, value);
            return this;
        }

        Body without(String key) {
            values.remove(key);
            return this;
        }

        String json() {
            return JSON.writeValueAsString(values);
        }

        MockHttpServletRequestBuilder request() {
            return request(null);
        }

        MockHttpServletRequestBuilder request(Cookie session) {
            return TestLogin.withCsrf(
                    post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json()),
                    session);
        }
    }

    /** 커밋 후 비동기로 보내는 메일이 {@code count}통이 될 때까지 기다린다(최대 5초). */
    static void awaitMailCount(CapturingMailSender mail, String email, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (mail.countTo(email) < count) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                        "메일 " + count + "통을 기다렸지만 " + mail.countTo(email) + "통: " + email);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /** 잠깐 기다려도 메일 수가 그대로인지 (보내지 않았음을 확인할 때). */
    static void assertMailCountStays(CapturingMailSender mail, String email, int count) {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (mail.countTo(email) != count) {
            throw new AssertionError("메일 수가 " + count + "가 아니라 " + mail.countTo(email));
        }
    }
}
