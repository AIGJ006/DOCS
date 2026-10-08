package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.shared.event.FriendAccepted;
import com.team.blog.shared.event.FriendRequested;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** US7 친구 (FR-054~056, C-FRIEND-1, R-27, contracts/events.md §1). */
class FriendshipIntegrationTest extends IntegrationTestBase {

    @Autowired FriendEventRecorder events;

    private long a;
    private long b;
    private Cookie sessionA;
    private Cookie sessionB;

    @BeforeEach
    void twoMembers() {
        events.clear();
        a = members().member().handle("alice").nickname("앨리스").create();
        b = members().member().handle("bob").nickname("밥밥이").create();
        sessionA = TestLogin.loginAs(mockMvc, a);
        sessionB = TestLogin.loginAs(mockMvc, b);
    }

    private ResultActions request(Cookie session, String handle) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(put("/api/members/" + handle + "/friend"), session));
    }

    private ResultActions remove(Cookie session, String handle) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(delete("/api/members/" + handle + "/friend"), session));
    }

    private ResultActions view(Cookie session, String handle) throws Exception {
        return mockMvc.perform(get("/api/members/" + handle + "/friend").cookie(session));
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM friendship", Integer.class);
    }

    @Test
    @DisplayName("#1·#2 A 요청 → B 받은 요청에 A → B 맞요청 = 수락 → 양쪽 친구 목록, 이벤트 각 1번")
    void requestThenAccept() throws Exception {
        request(sessionA, "bob")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUEST_SENT"));
        assertThat(events.of(FriendRequested.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.requesterId()).isEqualTo(a);
                            assertThat(e.receiverId()).isEqualTo(b);
                        });
        view(sessionB, "alice").andExpect(jsonPath("$.status").value("REQUEST_RECEIVED"));
        mockMvc.perform(get("/api/me/friend-requests").cookie(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].handle").value("alice"))
                .andExpect(jsonPath("$.items[0].nickname").value("앨리스"))
                .andExpect(jsonPath("$.items[0].profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.items[0].requestedAt").isString())
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mockMvc.perform(get("/api/me/friend-requests").cookie(sessionA))
                .andExpect(jsonPath("$.items.length()").value(0));

        request(sessionB, "alice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FRIENDS"));
        assertThat(rows()).isEqualTo(1);
        assertThat(events.of(FriendAccepted.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.requesterId()).isEqualTo(a);
                            assertThat(e.accepterId()).isEqualTo(b);
                        });
        for (var pair : List.of(List.of(sessionA, "bob"), List.of(sessionB, "alice"))) {
            mockMvc.perform(get("/api/me/friends").cookie((Cookie) pair.get(0)))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].handle").value(pair.get(1)))
                    .andExpect(jsonPath("$.items[0].friendsSince").isString());
        }
        view(sessionA, "bob").andExpect(jsonPath("$.status").value("FRIENDS"));
    }

    @Test
    @DisplayName("이미 친구·내가 보낸 요청 재전송 → 변화 없음, 이벤트 없음 (EV-4)")
    void repeatedRequestsChangeNothing() throws Exception {
        request(sessionA, "bob").andExpect(jsonPath("$.status").value("REQUEST_SENT"));
        request(sessionA, "bob").andExpect(jsonPath("$.status").value("REQUEST_SENT"));
        assertThat(events.of(FriendRequested.class)).hasSize(1);
        request(sessionB, "alice").andExpect(jsonPath("$.status").value("FRIENDS"));
        events.clear();
        request(sessionA, "bob").andExpect(jsonPath("$.status").value("FRIENDS"));
        request(sessionB, "alice").andExpect(jsonPath("$.status").value("FRIENDS"));
        assertThat(events.all()).isEmpty();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    @DisplayName("#4 거절·취소·끊기 → 행 삭제, 이벤트 없음, 관계 없어도 200 NONE")
    void removeWithoutEvents() throws Exception {
        request(sessionA, "bob");
        events.clear();
        remove(sessionB, "alice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
        assertThat(rows()).isZero();
        request(sessionA, "bob");
        remove(sessionA, "bob").andExpect(jsonPath("$.status").value("NONE"));
        assertThat(rows()).isZero();
        request(sessionA, "bob");
        request(sessionB, "alice");
        events.clear();
        remove(sessionA, "bob").andExpect(jsonPath("$.status").value("NONE"));
        assertThat(rows()).isZero();
        remove(sessionA, "bob").andExpect(status().isOk());
        assertThat(events.all()).isEmpty();
        mockMvc.perform(get("/api/me/friends").cookie(sessionB))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("#5 자기 자신 → 400 CANNOT_FRIEND_SELF, 상태 SELF")
    void cannotFriendSelf() throws Exception {
        request(sessionA, "alice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FRIEND_SELF"));
        view(sessionA, "alice").andExpect(jsonPath("$.status").value("SELF"));
        assertThat(rows()).isZero();
    }

    @Test
    @DisplayName("상태 NONE, 없는 주소·탈퇴 유예·익명 처리 회원 → 404 NOT_FOUND")
    void notFoundTargets() throws Exception {
        view(sessionA, "bob").andExpect(jsonPath("$.status").value("NONE"));
        members().member().handle("leaving").status("WITHDRAWN").create();
        members().member().handle("gone").deleted().create();
        for (String handle : new String[] {"nobody", "leaving", "gone"}) {
            view(sessionA, handle)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            request(sessionA, handle).andExpect(status().isNotFound());
            remove(sessionA, handle).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("#6 친구 목록은 본인만(/api/me/friends), 비로그인 401")
    void listsAreOwnOnly() throws Exception {
        mockMvc.perform(get("/api/me/friends")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/friend-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/bob/friend")).andExpect(status().isUnauthorized());
        mockMvc.perform(TestLogin.withCsrf(put("/api/members/bob/friend"), null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/bob/friends").cookie(sessionA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록 커서: 수락 최신순으로 나눠 받고 중복·빠짐 없음, 다른 목록 커서 → 400 INVALID_CURSOR")
    void cursorPaging() throws Exception {
        List<String> handles = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String handle = "friend" + i;
            long id = members().member().handle(handle).create();
            jdbc.update(
                    "INSERT INTO friendship (member_a_id, member_b_id, requested_by, status, created_at, accepted_at)"
                            + " VALUES (LEAST(?, ?), GREATEST(?, ?), ?, 'ACCEPTED', now(), now() - (? * interval '1 minute'))",
                    a,
                    id,
                    a,
                    id,
                    id,
                    i % 5);
            handles.add(handle);
        }
        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var builder = get("/api/me/friends").cookie(sessionA);
            if (cursor != null) {
                builder.param("cursor", cursor);
            }
            String body =
                    mockMvc.perform(builder)
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            List<String> page = JsonPath.read(body, "$.items[*].handle");
            seen.addAll(page);
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null && pages < 10);
        assertThat(pages).isEqualTo(2);
        assertThat(seen)
                .hasSize(25)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(handles);
        String firstPage =
                mockMvc.perform(get("/api/me/friends").cookie(sessionA))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String friendsCursor = JsonPath.read(firstPage, "$.nextCursor");
        mockMvc.perform(
                        get("/api/me/friend-requests")
                                .param("cursor", friendsCursor)
                                .cookie(sessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mockMvc.perform(get("/api/me/friends").param("cursor", "garbage").cookie(sessionA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    @DisplayName("인증 전 회원도 요청할 수 있다 (ACCOUNT_WRITE), 정지 회원은 403")
    void unverifiedCanRequestSuspendedCannot() throws Exception {
        long c = members().member().handle("carol").emailVerified(false).create();
        request(TestLogin.loginAs(mockMvc, c), "bob")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUEST_SENT"));
        members().suspend(a, null, "스팸");
        request(sessionA, "bob")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }
}
