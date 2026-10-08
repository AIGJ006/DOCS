package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.FollowApi.body;
import static com.team.blog.interaction.support.FollowApi.read;
import static com.team.blog.interaction.support.FollowApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.interaction.application.FollowQueryService;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 팔로워·팔로잉 목록 API (010 T030 US3, contracts {@code listFollowers}·{@code listFollowing}). */
class FollowListApiIT extends IntegrationTestBase {

    @Autowired FollowQueryService followQueryService;

    private long owner;
    private final Instant base = Instant.parse("2026-10-01T00:00:00.000001Z");

    @BeforeEach
    void setUp() {
        owner = members().member().handle("list_b").create();
    }

    private FollowApi api() {
        return new FollowApi(mockMvc);
    }

    private FollowFixtures fixtures() {
        return new FollowFixtures(jdbc);
    }

    private static List<String> handles(MvcResult result) {
        return read(result, "$.items[*].handle");
    }

    @Test
    void US3_1_비회원도_수와_목록() throws Exception {
        long a = members().member().handle("fan_a").create();
        long x = members().member().handle("star_x").create();
        fixtures().follow(a, owner, base);
        fixtures().follow(owner, x, base);

        MvcResult followers = api().followers(null, "list_b", null);
        MvcResult following = api().following(null, "list_b", null);

        assertThat(status(followers)).as(body(followers)).isEqualTo(200);
        assertThat(handles(followers)).containsExactly("fan_a");
        assertThat(handles(following)).containsExactly("star_x");
        assertThat(followers.getResponse().getHeader("Cache-Control"))
                .isEqualTo("private, no-cache");
        assertThat((Boolean) read(followers, "$.items[0].followedByMe")).isFalse();
        assertThat((Boolean) read(followers, "$.items[0].isMe")).isFalse();
        MvcResult header = api().header(null, "list_b");
        assertThat(((Number) read(header, "$.followerCount")).intValue()).isEqualTo(1);
        assertThat(((Number) read(header, "$.followingCount")).intValue()).isEqualTo(1);
    }

    @Test
    void US3_2_항목_칸과_정렬() throws Exception {
        long viewer = members().member().handle("viewer_v").create();
        long photo = members().member().handle("photo_p").nickname("사진").create();
        jdbc.update("UPDATE member SET bio = '첫 줄\n둘째 줄' WHERE id = ?", photo);
        fixtures().profileImage(photo, "profiles/p.png", "profiles/p_thumb.webp");
        long plain = members().member().handle("plain_q").create();
        fixtures().follow(plain, owner, base);
        fixtures().follow(photo, owner, base.plusSeconds(1));
        fixtures().follow(viewer, owner, base.plusSeconds(1));
        fixtures().follow(viewer, photo);
        Cookie session = TestLogin.loginAs(mockMvc, viewer);

        MvcResult result = api().followers(session, "list_b", null);

        // 같은 created_at이면 회원 번호 큰 순 (photo < viewer? 만든 순서: viewer, photo, plain)
        assertThat(handles(result)).containsExactly("photo_p", "viewer_v", "plain_q");
        Map<String, Object> first = read(result, "$.items[0]");
        assertThat(first)
                .containsOnlyKeys(
                        "handle", "nickname", "profileImageUrl", "bio", "followedByMe", "isMe");
        assertThat(first.get("nickname")).isEqualTo("사진");
        assertThat((String) first.get("profileImageUrl")).endsWith("profiles/p_thumb.webp");
        assertThat(first.get("bio")).isEqualTo("첫 줄\n둘째 줄");
        assertThat(first.get("followedByMe")).isEqualTo(true);
        assertThat(first.get("isMe")).isEqualTo(false);
        assertThat((Boolean) read(result, "$.items[1].isMe")).isTrue();
        assertThat((Boolean) read(result, "$.items[1].followedByMe")).isFalse();
        assertThat(read(result, "$.items[2].profileImageUrl") == null).isTrue();
        assertThat(read(result, "$.items[2].bio") == null).isTrue();
    }

    @Test
    void 사십오명이면_20_20_5_중복_누락_0() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            long m = members().member().handle(String.format("fan_m%02d", i)).create();
            // 다섯 명씩 같은 시각
            fixtures().follow(m, owner, base.plusSeconds(i / 5));
            expected.add(String.format("fan_m%02d", i));
        }

        List<String> seen = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        String cursor = null;
        do {
            MvcResult page = api().followers(null, "list_b", cursor);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            sizes.add(handles(page).size());
            seen.addAll(handles(page));
            cursor = read(page, "$.nextCursor");
        } while (cursor != null);

        assertThat(sizes).containsExactly(20, 20, 5);
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void US3_3_유예회원_빠졌다가_복구하면_돌아옴() throws Exception {
        long a = members().member().handle("fan_a").create();
        long x = members().member().handle("star_x").create();
        fixtures().follow(a, owner, base);
        fixtures().follow(owner, x, base);
        fixtures().withdraw(a);
        fixtures().withdraw(x);

        assertThat(handles(api().followers(null, "list_b", null))).isEmpty();
        assertThat(handles(api().following(null, "list_b", null))).isEmpty();
        assertThat(((Number) read(api().header(null, "list_b"), "$.followerCount")).intValue())
                .isZero();

        fixtures().restore(a);
        fixtures().restore(x);
        assertThat(handles(api().followers(null, "list_b", null))).containsExactly("fan_a");
        assertThat(handles(api().following(null, "list_b", null))).containsExactly("star_x");
        assertThat(((Number) read(api().header(null, "list_b"), "$.followerCount")).intValue())
                .isEqualTo(1);
    }

    @Test
    void US3_4_빈_목록() throws Exception {
        MvcResult result = api().followers(null, "list_b", null);
        assertThat(handles(result)).isEmpty();
        assertThat((String) read(result, "$.nextCursor")).isNull();
    }

    @Test
    void US3_5_없는_유예_주소_404() throws Exception {
        long withdrawn = members().member().handle("gone_c").create();
        fixtures().withdraw(withdrawn);
        MvcResult missing = api().followers(null, "nobody_here", null);
        for (MvcResult result :
                List.of(
                        missing,
                        api().following(null, "nobody_here", null),
                        api().followers(null, "gone_c", null),
                        api().following(null, "LIST_B", null))) {
            assertThat(status(result)).isEqualTo(404);
            assertThat(result.getResponse().getContentAsByteArray())
                    .isEqualTo(missing.getResponse().getContentAsByteArray());
        }
        assertThat((String) read(missing, "$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void 다른_목록_커서는_400() throws Exception {
        long other = members().member().handle("other_o").create();
        for (int i = 0; i < 21; i++) {
            long m = members().member().create();
            fixtures().follow(m, owner, base.plusSeconds(i));
            fixtures().follow(m, other, base.plusSeconds(i));
            fixtures().follow(owner, m, base.plusSeconds(i));
        }
        String followersCursor = read(api().followers(null, "list_b", null), "$.nextCursor");
        assertThat(followersCursor).isNotNull();

        for (MvcResult result :
                List.of(
                        api().following(null, "list_b", followersCursor),
                        api().followers(null, "other_o", followersCursor),
                        api().followers(null, "list_b", "깨진값"))) {
            assertThat(status(result)).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
        assertThat(status(api().followers(null, "list_b", followersCursor))).isEqualTo(200);
    }

    @Test
    void SQL은_주인_1번_목록_1번_로그인이면_팔로우_여부_1번_더() {
        long viewer = members().member().create();
        for (int i = 0; i < 3; i++) {
            long m = members().member().create();
            fixtures().follow(m, owner, base.plusSeconds(i));
            fixtures().profileImage(m, "profiles/" + i + ".png", null);
        }
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(followQueryService.followers("list_b", null, Viewer.anonymous()).items())
                    .hasSize(3);
            assertThat(scope.count()).isEqualTo(2);
        }
        Viewer loggedIn =
                new Viewer(
                        viewer,
                        com.team.blog.account.domain.Role.USER,
                        com.team.blog.account.domain.MemberStatus.ACTIVE,
                        true);
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(followQueryService.followers("list_b", null, loggedIn).items()).hasSize(3);
            assertThat(scope.count()).isEqualTo(3);
        }
    }

    @Test
    void 클라이언트_size는_무시한다() throws Exception {
        for (int i = 0; i < 25; i++) {
            fixtures().follow(members().member().create(), owner, base.plusSeconds(i));
        }
        MvcResult result =
                mockMvc.perform(get("/api/members/list_b/followers").param("size", "100"))
                        .andReturn();
        assertThat(handles(result)).hasSize(20);
    }

    @Test
    void 탈퇴_유예_회원이_로그인해_목록을_보면_403() throws Exception {
        long withdrawn = members().member().status("WITHDRAWN").create();
        MvcResult result = api().followers(TestLogin.loginAs(mockMvc, withdrawn), "list_b", null);
        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
    }
}
