package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.FriendListPage;
import com.team.blog.account.application.FriendshipService;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * SQL 수 상한 (T145, plan Performance Goals, N+1 금지). {@link SqlCounter}(테스트 DataSource 감싸기)로 센다 —
 * Hibernate Statistics·datasource-proxy를 새로 들이지 않고 004가 만든 공용 계수기를 쓴다.
 */
class QueryCountIntegrationTest extends IntegrationTestBase {

    @Autowired FriendshipService friendships;
    @Autowired AccountStatusGuard guard;

    @Test
    @DisplayName("친구 목록 한 페이지는 SQL 2번(목록 1 + 프로필 사진 1), 다음 페이지도 2번 — 친구 수와 무관")
    void friendListTwoQueriesPerPage() {
        long me = members().member().create();
        for (int i = 0; i < 25; i++) {
            long friend = members().member().create();
            attachProfileImage(friend);
            friendship(me, friend, "ACCEPTED", me);
        }
        FriendListPage first;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            first = friendships.listFriends(me, null);
            assertThat(scope.count()).isEqualTo(2);
        }
        assertThat(first.items()).hasSize(20);
        assertThat(first.items())
                .allSatisfy(item -> assertThat(item.profileImageUrl()).isNotNull());
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            FriendListPage second = friendships.listFriends(me, first.nextCursor());
            assertThat(second.items()).hasSize(5);
            assertThat(scope.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("받은 친구 요청 한 페이지는 SQL 2번")
    void receivedRequestsTwoQueries() {
        long me = members().member().create();
        for (int i = 0; i < 6; i++) {
            long other = members().member().create();
            attachProfileImage(other);
            friendship(me, other, "PENDING", other);
        }
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(friendships.listReceivedRequests(me, null).items()).hasSize(6);
            assertThat(scope.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("이메일 로그인 1회: 인증 수단 1 + 정지 1 + 동의 1 + last_login_at 1 = SQL 4번")
    void emailLoginQueries() throws Exception {
        long id = members().member().email("count@example.com").create();
        SignupRequests.agreeCurrent(jdbc, id);
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            mockMvc.perform(
                            TestLogin.withCsrf(
                                    post("/api/auth/login")
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("email", "count@example.com")
                                            .param("password", MemberFixtures.DEFAULT_PASSWORD),
                                    null))
                    .andExpect(status().isOk());
            assertThat(scope.count()).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("주소·닉네임 사용 가능 확인은 SQL 1~2번")
    void availabilityQueries() throws Exception {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            mockMvc.perform(get("/api/handles/availability").queryParam("handle", "countme01"))
                    .andExpect(status().isOk());
            assertThat(scope.count()).isBetween(1, 2);
        }
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            mockMvc.perform(get("/api/nicknames/availability").queryParam("nickname", "세는사람"))
                    .andExpect(status().isOk());
            assertThat(scope.count()).isBetween(1, 2);
        }
    }

    @Test
    @DisplayName("AccountStatusGuard 판정은 SQL 1번")
    void accountStatusGuardOneQuery() {
        long id = members().member().create();
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            guard.requireActive(id, ActionKind.ACCOUNT_WRITE);
            assertThat(scope.count()).isEqualTo(1);
        }
    }

    private void friendship(long me, long other, String status, long requestedBy) {
        jdbc.update(
                "INSERT INTO friendship (member_a_id, member_b_id, requested_by, status, created_at,"
                        + " accepted_at) VALUES (?, ?, ?, ?, now(), CASE WHEN ? = 'ACCEPTED' THEN now() END)",
                Math.min(me, other),
                Math.max(me, other),
                requestedBy,
                status,
                status);
    }

    private void attachProfileImage(long memberId) {
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height,"
                        + " status, purpose) VALUES (?, ?, 'image/webp', 1000, 256, 256, 'ATTACHED', 'PROFILE')",
                memberId,
                "profile/" + UUID.randomUUID() + ".webp");
    }
}
