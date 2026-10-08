package com.team.blog.notification.integration;

import static com.team.blog.notification.support.NotificationApi.body;
import static com.team.blog.notification.support.NotificationApi.read;
import static com.team.blog.notification.support.NotificationApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationApi;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 알림 종류 끄기 (011 T048, US6 #1·#2). */
class NotificationSettingsIT extends NotificationTestBase {

    private static final String ALL_ON =
            "{\"COMMENT\":true,\"REPLY\":true,\"LIKE\":true,\"FOLLOW\":true,\"NEW_POST\":true}";
    private static final String LIKE_OFF =
            "{\"COMMENT\":true,\"REPLY\":true,\"LIKE\":false,\"FOLLOW\":true,\"NEW_POST\":true}";

    private NotificationApi api;
    private NotificationActions actions;
    private long a;
    private Cookie session;

    @BeforeEach
    void setUp() {
        api = new NotificationApi(mockMvc);
        actions = new NotificationActions(mockMvc, jdbc);
        a = members().member().create();
        session = TestLogin.loginAs(mockMvc, a);
    }

    @Test
    void 새_회원은_다섯_종류_모두_켜짐() throws Exception {
        MvcResult result = api.settings(session);
        assertThat(status(result)).isEqualTo(200);
        assertThat(body(result)).isEqualTo(ALL_ON);
    }

    @Test
    void 좋아요를_끄면_새_좋아요_알림만_안_생긴다() throws Exception {
        long b = members().member().create();
        long c = members().member().create();
        long d = members().member().create();
        long postId = posts.create(a, State.PUBLISHED_PUBLIC);
        actions.like(b, postId);
        awaiter.untilCount(a, 1);

        MvcResult put = api.putSettings(session, LIKE_OFF);
        assertThat(status(put)).as(body(put)).isEqualTo(200);
        assertThat(body(put)).isEqualTo(LIKE_OFF);
        assertThat(body(api.settings(session))).isEqualTo(LIKE_OFF);

        // 읽어 두면 새 좋아요는 새 묶음이 될 자리 — 꺼져 있어 생기지 않는다
        jdbc.update("UPDATE notification SET read_at = now() WHERE receiver_id = ?", a);
        actions.like(c, postId);
        actions.comment(c, postId, "댓글은 켜져 있음", null);
        awaiter.untilCount(a, 2);
        assertThat(notifications.count(a, "LIKE")).isEqualTo(1);
        assertThat(notifications.count(a, "COMMENT")).isEqualTo(1);

        assertThat(status(api.putSettings(session, ALL_ON))).isEqualTo(200);
        actions.like(d, postId);
        awaiter.untilCount(a, 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 키_빠짐_문자열_값_모르는_키는_400() throws Exception {
        MvcResult missing =
                api.putSettings(
                        session,
                        "{\"COMMENT\":true,\"REPLY\":true,\"FOLLOW\":true,\"NEW_POST\":true}");
        assertThat(status(missing)).isEqualTo(400);
        assertThat((String) read(missing, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((List<String>) read(missing, "$.errors[*].field")).containsExactly("LIKE");
        assertThat((String) read(missing, "$.errors[0].code")).isEqualTo("REQUIRED");

        MvcResult text =
                api.putSettings(
                        session,
                        "{\"COMMENT\":\"false\",\"REPLY\":true,\"LIKE\":true,\"FOLLOW\":true,\"NEW_POST\":true}");
        assertThat(status(text)).isEqualTo(400);
        assertThat((List<String>) read(text, "$.errors[*].field")).containsExactly("COMMENT");

        MvcResult unknown =
                api.putSettings(
                        session,
                        "{\"COMMENT\":true,\"REPLY\":true,\"LIKE\":true,\"FOLLOW\":true,\"NEW_POST\":true,"
                                + "\"REPORT_RESOLVED\":false}");
        assertThat(status(unknown)).isEqualTo(400);
        assertThat((List<String>) read(unknown, "$.errors[*].field"))
                .containsExactly("REPORT_RESOLVED");

        assertThat(body(api.settings(session))).isEqualTo(ALL_ON);
    }

    @Test
    void 인증_전_회원도_바꾸고_정지된_남은_세션은_403() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        Cookie s = TestLogin.loginAs(mockMvc, unverified);
        assertThat(status(api.putSettings(s, LIKE_OFF))).isEqualTo(200);

        members().suspend(a, Instant.now().plus(7, ChronoUnit.DAYS), "설정 시험");
        MvcResult suspended = api.putSettings(session, LIKE_OFF);
        assertThat(status(suspended)).isEqualTo(403);
        assertThat((String) read(suspended, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(status(api.settings(session))).isEqualTo(200);
    }
}
