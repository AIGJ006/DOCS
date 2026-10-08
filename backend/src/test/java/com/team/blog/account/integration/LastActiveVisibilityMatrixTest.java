package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.account.application.LastActiveQueryService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 최근 활동 노출 조합 (SC-009, US8 #3~#6, FR-060). 보는 사람(비회원·친구 아님·요청 중·친구) × 대상 공개 × 보는 사람 공개 = 16가지. 친구 +
 * 둘 다 공개 + 값 있음일 때만 {@code lastActive} 키가 있고, 나머지는 키 자체가 없다.
 */
class LastActiveVisibilityMatrixTest extends IntegrationTestBase {

    @Autowired LastActiveQueryService lastActiveQuery;

    enum ViewerKind {
        ANONYMOUS,
        STRANGER,
        PENDING,
        FRIEND
    }

    static Stream<Arguments> matrix() {
        List<Arguments> rows = new ArrayList<>();
        for (ViewerKind kind : ViewerKind.values()) {
            for (boolean targetVisible : new boolean[] {true, false}) {
                for (boolean viewerVisible : new boolean[] {true, false}) {
                    rows.add(Arguments.of(kind, targetVisible, viewerVisible));
                }
            }
        }
        return rows.stream();
    }

    private void relate(long a, long b, String status) {
        jdbc.update(
                "INSERT INTO friendship (member_a_id, member_b_id, requested_by, status, accepted_at)"
                        + " VALUES (LEAST(?, ?), GREATEST(?, ?), ?, ?, ?)",
                a,
                b,
                a,
                b,
                a,
                status,
                "ACCEPTED".equals(status) ? Timestamp.from(Instant.now()) : null);
    }

    @ParameterizedTest(name = "[{index}] {0} 대상공개={1} 보는사람공개={2}")
    @MethodSource("matrix")
    void lastActiveOnlyForVisibleFriends(
            ViewerKind kind, boolean targetVisible, boolean viewerVisible) throws Exception {
        Instant activeAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long target =
                members().member().handle("target").lastActive(activeAt, targetVisible).create();
        long viewer =
                members()
                        .member()
                        .handle("viewer")
                        .lastActive(Instant.now(), viewerVisible)
                        .create();
        if (kind == ViewerKind.PENDING) {
            relate(viewer, target, "PENDING");
        } else if (kind == ViewerKind.FRIEND) {
            relate(viewer, target, "ACCEPTED");
        }
        boolean expected = kind == ViewerKind.FRIEND && targetVisible && viewerVisible;

        assertThat(
                        lastActiveQuery
                                .lastActiveFor(kind == ViewerKind.ANONYMOUS ? null : viewer, target)
                                .isPresent())
                .as("service")
                .isEqualTo(expected);
        if (kind != ViewerKind.ANONYMOUS) {
            Cookie session = TestLogin.loginAs(mockMvc, viewer);
            String view = body(get("/api/members/target/friend").cookie(session));
            assertThat(view.contains("\"lastActive\"")).as("friend view").isEqualTo(expected);
            assertThat(view).doesNotContain("null");
            String friends = body(get("/api/me/friends").cookie(session));
            assertThat(friends.contains("\"lastActive\"")).as("friend list").isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("HTTP 응답: 친구+둘 다 공개면 lastActive 키, 정확한 시각·lastActiveAt은 어디에도 없다")
    void httpResponsesCarryBucketOnly() throws Exception {
        Instant activeAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long target = members().member().handle("target").lastActive(activeAt, true).create();
        long viewer = members().member().handle("viewer").lastActive(Instant.now(), true).create();
        relate(viewer, target, "ACCEPTED");
        Cookie session = TestLogin.loginAs(mockMvc, viewer);
        String view = body(get("/api/members/target/friend").cookie(session));
        assertThat(view).contains("\"lastActive\":{\"bucket\":").doesNotContain("lastActiveAt");
        assertThat(view).doesNotContain(activeAt.toString().substring(0, 13));
        String friends = body(get("/api/me/friends").cookie(session));
        assertThat(friends).contains("\"lastActive\":{\"bucket\":").doesNotContain("lastActiveAt");
        assertThat(friends).doesNotContain(activeAt.toString().substring(0, 13));
        assertThat(lastActiveQuery.lastActiveFor(viewer, target)).isPresent();

        // 끊은 직후부터 없다 (#6)
        mockMvc.perform(TestLogin.withCsrf(delete("/api/members/target/friend"), session));
        assertThat(body(get("/api/members/target/friend").cookie(session)))
                .doesNotContain("lastActive");
    }

    @Test
    @DisplayName("키 자체가 없다(null도 없음): 친구가 아님·대상 비공개·값 없음")
    void keyAbsent() throws Exception {
        long target = members().member().handle("target").lastActive(Instant.now(), false).create();
        long other = members().member().handle("other").create();
        long viewer = members().member().handle("viewer").lastActive(Instant.now(), true).create();
        relate(viewer, target, "ACCEPTED");
        relate(viewer, other, "ACCEPTED");
        Cookie session = TestLogin.loginAs(mockMvc, viewer);
        assertThat(body(get("/api/members/target/friend").cookie(session)))
                .isEqualTo("{\"status\":\"FRIENDS\"}");
        assertThat(body(get("/api/members/other/friend").cookie(session)))
                .isEqualTo("{\"status\":\"FRIENDS\"}");
        assertThat(body(get("/api/me/friends").cookie(session))).doesNotContain("lastActive");
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }
}
