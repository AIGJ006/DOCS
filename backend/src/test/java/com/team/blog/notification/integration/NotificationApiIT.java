package com.team.blog.notification.integration;

import static com.team.blog.notification.support.NotificationApi.body;
import static com.team.blog.notification.support.NotificationApi.read;
import static com.team.blog.notification.support.NotificationApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.notification.application.NotificationQueryService;
import com.team.blog.notification.support.NotificationApi;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.ListScope;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 알림 API (011 T023, US2 #1·#4~#8, contracts openapi). */
class NotificationApiIT extends NotificationTestBase {

    @Autowired private NotificationQueryService queries;
    @Autowired private CursorCodec cursorCodec;

    private NotificationApi api;
    private long a;
    private long b;
    private Cookie session;
    private long postId;

    @BeforeEach
    void setUp() {
        api = new NotificationApi(mockMvc);
        a = members().member().create();
        b = members().member().create();
        session = TestLogin.loginAs(mockMvc, a);
        postId = posts.create(a, State.PUBLISHED_PUBLIC);
    }

    @Test
    void 안_읽은_수와_no_store() throws Exception {
        notifications.single(a, "NEW_POST").post(postId).actor(b).create();
        notifications.single(a, "NEW_POST").post(postId).actor(b).create();
        notifications.single(a, "NEW_POST").post(postId).actor(b).read().create();
        notifications.single(b, "NEW_POST").post(postId).actor(a).create();

        MvcResult result = api.unreadCount(session);

        assertThat(status(result)).isEqualTo(200);
        assertThat(body(result)).isEqualTo("{\"count\":2}");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void 목록은_갱신_최신순_같으면_번호_큰_순() throws Exception {
        Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
        long old = notifications.single(a, "COMMENT").post(postId).actor(b).at(t).create();
        long sameA =
                notifications
                        .single(a, "NEW_POST")
                        .post(postId)
                        .actor(b)
                        .at(t.plusSeconds(60))
                        .create();
        long sameB =
                notifications
                        .single(a, "NEW_POST")
                        .post(postId)
                        .actor(b)
                        .at(t.plusSeconds(60))
                        .create();
        long newest = notifications.group(a, "LIKE", postId, t.plusSeconds(120), false, b);

        MvcResult result = api.list(session, null, null);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
        List<Number> ids = read(result, "$.items[*].id");
        assertThat(ids.stream().map(Number::longValue)).containsExactly(newest, sameB, sameA, old);
        assertThat((Object) read(result, "$.nextCursor")).isNull();
    }

    @Test
    void 크기는_10_또는_20_그_밖은_400() throws Exception {
        for (int i = 0; i < 25; i++) {
            notifications.single(a, "NEW_POST").post(postId).actor(b).create();
        }
        assertThat(((List<?>) read(api.list(session, 10, null), "$.items"))).hasSize(10);
        assertThat(((List<?>) read(api.list(session, 20, null), "$.items"))).hasSize(20);
        assertThat(((List<?>) read(api.list(session, null, null), "$.items"))).hasSize(20);

        MvcResult bad = api.list(session, 15, null);
        assertThat(status(bad)).isEqualTo(400);
        assertThat((String) read(bad, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(bad, "$.errors[0].field")).isEqualTo("size");
        assertThat((String) read(bad, "$.errors[0].code")).isEqualTo("INVALID_SIZE");
    }

    @Test
    void 커서로_25개를_끝까지_중복_누락_없이() throws Exception {
        Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            // 같은 시각이 섞이게 (5개씩 같은 updated_at)
            created.add(
                    notifications
                            .single(a, "NEW_POST")
                            .post(postId)
                            .actor(b)
                            .at(t.plusSeconds(i / 5))
                            .create());
        }
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MvcResult page = api.list(session, 10, cursor);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            List<Number> ids = read(page, "$.items[*].id");
            ids.forEach(id -> seen.add(id.longValue()));
            cursor = read(page, "$.nextCursor");
            pages++;
        } while (cursor != null);
        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(25);
        assertThat(new HashSet<>(seen)).containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void 다른_목록_커서는_400_INVALID_CURSOR() throws Exception {
        String foreign = cursorCodec.encode(ListScope.of("home"), List.of(1L, 1L), null);
        MvcResult result = api.list(session, 20, foreign);
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        assertThat(status(api.list(session, 20, "not-a-cursor"))).isEqualTo(400);
    }

    @Test
    void 목록은_SQL_한_번() {
        long commenter = members().member().create();
        long commentId =
                new com.team.blog.interaction.support.CommentFixtures(jdbc)
                        .on(postId, commenter)
                        .create();
        notifications
                .single(a, "COMMENT")
                .post(postId)
                .comment(commentId)
                .actor(commenter)
                .create();
        notifications.group(a, "FOLLOW", null, Instant.now(), false, b);
        notifications.single(a, "REPORT_RESOLVED").create();
        Viewer viewer = new Viewer(a, Role.USER, MemberStatus.ACTIVE, true);

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(queries.page(viewer, null, 20).items()).hasSize(3);
            assertThat(scope.count()).isEqualTo(1);
        }
    }

    @Test
    void 읽음은_204_다시_204() throws Exception {
        long id = notifications.single(a, "NEW_POST").post(postId).actor(b).create();

        assertThat(status(api.read(session, id))).isEqualTo(204);
        Object readAt =
                jdbc.queryForObject(
                        "SELECT read_at FROM notification WHERE id = ?", Object.class, id);
        assertThat(readAt).isNotNull();
        assertThat(status(api.read(session, id))).isEqualTo(204);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT read_at FROM notification WHERE id = ?", Object.class, id))
                .isEqualTo(readAt);
        assertThat(body(api.unreadCount(session))).isEqualTo("{\"count\":0}");
    }

    @Test
    void 모두_읽음은_바뀐_수() throws Exception {
        for (int i = 0; i < 12; i++) {
            notifications.single(a, "NEW_POST").post(postId).actor(b).create();
        }
        notifications.single(a, "NEW_POST").post(postId).actor(b).read().create();

        MvcResult result = api.readAll(session);

        assertThat(status(result)).isEqualTo(200);
        assertThat(body(result)).isEqualTo("{\"updated\":12}");
        assertThat(body(api.unreadCount(session))).isEqualTo("{\"count\":0}");
        assertThat(body(api.readAll(session))).isEqualTo("{\"updated\":0}");
    }

    @Test
    void 삭제는_204_다시_404() throws Exception {
        long id = notifications.single(a, "NEW_POST").post(postId).actor(b).create();

        assertThat(status(api.delete(session, id))).isEqualTo(204);
        MvcResult again = api.delete(session, id);
        assertThat(status(again)).isEqualTo(404);
        assertThat(body(again))
                .isEqualTo(
                        "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}");
    }

    @Test
    void 남의_알림과_숫자가_아닌_번호는_404() throws Exception {
        long others = notifications.single(b, "NEW_POST").post(postId).actor(a).create();
        assertThat(status(api.read(session, others))).isEqualTo(404);
        assertThat(status(api.delete(session, others))).isEqualTo(404);
        assertThat(status(api.read(session, "abc"))).isEqualTo(404);
        assertThat(status(api.delete(session, "-1"))).isEqualTo(404);
        assertThat(notifications.count(b)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT read_at FROM notification WHERE id = ?",
                                Object.class,
                                others))
                .isNull();
    }

    @Test
    void 인증_전_회원도_모두_가능() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        Cookie s = TestLogin.loginAs(mockMvc, unverified);
        long id = notifications.single(unverified, "NEW_POST").post(postId).actor(a).create();
        long id2 = notifications.single(unverified, "NEW_POST").post(postId).actor(a).create();

        assertThat(status(api.unreadCount(s))).isEqualTo(200);
        assertThat(status(api.list(s, 10, null))).isEqualTo(200);
        assertThat(status(api.read(s, id))).isEqualTo(204);
        assertThat(status(api.readAll(s))).isEqualTo(200);
        assertThat(status(api.delete(s, id2))).isEqualTo(204);
    }

    @Test
    void 비회원_401_유예_403_CSRF_없음_403() throws Exception {
        MvcResult anonymous = api.unreadCount(null);
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat((String) read(anonymous, "$.code")).isEqualTo("LOGIN_REQUIRED");
        assertThat(status(api.list(null, null, null))).isEqualTo(401);
        assertThat(status(api.readAll(null))).isEqualTo(401);

        long id = notifications.single(a, "NEW_POST").post(postId).actor(b).create();
        MvcResult noCsrf =
                mockMvc.perform(put("/api/notifications/{id}/read", id).cookie(session))
                        .andReturn();
        assertThat(status(noCsrf)).isEqualTo(403);
        assertThat((String) read(noCsrf, "$.code")).isEqualTo("CSRF_REJECTED");

        posts.withdraw(a);
        MvcResult withdrawn = api.unreadCount(session);
        assertThat(status(withdrawn)).isEqualTo(403);
        assertThat((String) read(withdrawn, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
    }
}
