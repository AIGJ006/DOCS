package com.team.blog.account.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 015 권한 매트릭스 실행기 (T059, research R15). 하네스가 행마다 새 행위자를 만들므로 {@code me.withdraw}가 성공해도 다음 행에 영향이
 * 없다. 픽스처 회원은 모두 이메일 가입이므로 본인 확인은 픽스처 기본 비밀번호다.
 */
public final class WithdrawalActions {

    private WithdrawalActions() {}

    /** {@code me.withdrawal}: GET /api/me/withdrawal — 탈퇴 안내 숫자. */
    @Profile("test")
    @Component
    public static class Preview implements PermissionAction {

        @Override
        public String name() {
            return "me.withdrawal";
        }

        @Override
        public String owner() {
            return "015";
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            var request = get("/api/me/withdrawal");
            if (session != null) {
                request.cookie(session);
            }
            return ActionResult.of(mockMvc.perform(request).andReturn());
        }
    }

    /** {@code me.withdraw}: POST /api/me/withdraw — 맞는 본인 확인으로 탈퇴 신청. */
    @Profile("test")
    @Component
    public static class Withdraw implements PermissionAction {

        @Override
        public String name() {
            return "me.withdraw";
        }

        @Override
        public String owner() {
            return "015";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(
                    mockMvc.perform(
                                    TestLogin.withCsrf(
                                            post("/api/me/withdraw")
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(
                                                            "{\"confirmed\":true,\"password\":\""
                                                                    + MemberFixtures
                                                                            .DEFAULT_PASSWORD
                                                                    + "\"}"),
                                            session))
                            .andReturn());
        }
    }

    /** {@code me.restore}: POST /api/me/restore — 탈퇴 복구 (활동 중이면 변화 없이 200). */
    @Profile("test")
    @Component
    public static class Restore implements PermissionAction {

        @Override
        public String name() {
            return "me.restore";
        }

        @Override
        public String owner() {
            return "015";
        }

        @Override
        public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
            return ActionResult.of(
                    mockMvc.perform(TestLogin.withCsrf(post("/api/me/restore"), session))
                            .andReturn());
        }
    }
}
