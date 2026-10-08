package com.team.blog.notification.integration.permission;

import com.team.blog.notification.support.NotificationApi;
import com.team.blog.notification.support.NotificationFixtures;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 011 권한 매트릭스 실행기 (T024, research R15). 대상 글이 없는 행동({@code NONE})이라 하네스가 만든 행위자 본인의 알림을 여기서 만든다.
 *
 * <p>행위자 번호는 테스트 로그인이 남기는 Redis 키 {@code member:active-touch:{id}}로 찾는다(행마다 Redis를 비우고 행위자 한 명만
 * 로그인한다). {@code *.others}는 다른 회원 B의 알림 번호로 요청한다.
 */
@Profile("test")
@Component
public class NotificationPermissionActions {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public NotificationPermissionActions(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    Long actorId(Cookie session) {
        if (session == null) {
            return null;
        }
        Set<String> keys = redis.keys("member:active-touch:*");
        if (keys == null || keys.size() != 1) {
            throw new IllegalStateException("행위자를 찾지 못했습니다: " + keys);
        }
        return Long.parseLong(keys.iterator().next().substring("member:active-touch:".length()));
    }

    /** 그 회원의 안 읽은 알림 하나 (행위자가 없으면 아무 회원). */
    long notificationOf(Long memberId) {
        long receiver = memberId != null ? memberId : new MemberFixtures(jdbc).member().create();
        return new NotificationFixtures(jdbc).single(receiver, "REPORT_RESOLVED").create();
    }

    long othersNotification() {
        return notificationOf(new MemberFixtures(jdbc).member().create());
    }

    private abstract static class Base implements PermissionAction {
        protected final NotificationPermissionActions support;
        private final String name;
        private final boolean write;

        Base(NotificationPermissionActions support, String name, boolean write) {
            this.support = support;
            this.name = name;
            this.write = write;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String owner() {
            return "011";
        }

        @Override
        public boolean isWrite() {
            return write;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(run(new NotificationApi(mockMvc), session));
        }

        abstract MvcResult run(NotificationApi api, Cookie session) throws Exception;
    }

    @Profile("test")
    @Component
    static class UnreadCount extends Base {
        UnreadCount(NotificationPermissionActions s) {
            super(s, "notification.unread-count", false);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            support.notificationOf(support.actorId(session));
            return api.unreadCount(session);
        }
    }

    @Profile("test")
    @Component
    static class ListAction extends Base {
        ListAction(NotificationPermissionActions s) {
            super(s, "notification.list", false);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            support.notificationOf(support.actorId(session));
            return api.list(session, 10, null);
        }
    }

    @Profile("test")
    @Component
    static class Read extends Base {
        Read(NotificationPermissionActions s) {
            super(s, "notification.read", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            return api.read(session, support.notificationOf(support.actorId(session)));
        }
    }

    @Profile("test")
    @Component
    static class ReadOthers extends Base {
        ReadOthers(NotificationPermissionActions s) {
            super(s, "notification.read.others", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            support.actorId(session);
            return api.read(session, support.othersNotification());
        }
    }

    @Profile("test")
    @Component
    static class ReadAll extends Base {
        ReadAll(NotificationPermissionActions s) {
            super(s, "notification.read-all", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            support.notificationOf(support.actorId(session));
            return api.readAll(session);
        }
    }

    @Profile("test")
    @Component
    static class Delete extends Base {
        Delete(NotificationPermissionActions s) {
            super(s, "notification.delete", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            return api.delete(session, support.notificationOf(support.actorId(session)));
        }
    }

    @Profile("test")
    @Component
    static class DeleteOthers extends Base {
        DeleteOthers(NotificationPermissionActions s) {
            super(s, "notification.delete.others", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            support.actorId(session);
            return api.delete(session, support.othersNotification());
        }
    }

    @Profile("test")
    @Component
    static class SettingsGet extends Base {
        SettingsGet(NotificationPermissionActions s) {
            super(s, "notification.settings.get", false);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            return api.settings(session);
        }
    }

    @Profile("test")
    @Component
    static class SettingsPut extends Base {
        SettingsPut(NotificationPermissionActions s) {
            super(s, "notification.settings.put", true);
        }

        @Override
        MvcResult run(NotificationApi api, Cookie session) throws Exception {
            return api.putSettings(
                    session,
                    "{\"COMMENT\":true,\"REPLY\":true,\"LIKE\":false,\"FOLLOW\":true,\"NEW_POST\":true}");
        }
    }
}
