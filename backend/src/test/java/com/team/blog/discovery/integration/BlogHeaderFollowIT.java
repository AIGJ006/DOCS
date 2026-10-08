package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 블로그 머리말·글 상세의 팔로우 값 (010 T015, data-model §3-4·§3-5, research R5·R8). 005 기존 칸은 그대로다. */
class BlogHeaderFollowIT extends IntegrationTestBase {

    private long owner;
    private long me;
    private Cookie session;

    @BeforeEach
    void setUp() {
        owner = members().member().handle("owner_b").nickname("주인").create();
        me = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
    }

    private ReadingApi reading() {
        return new ReadingApi(mockMvc);
    }

    private FollowFixtures fixtures() {
        return new FollowFixtures(jdbc);
    }

    private static long number(MvcResult result, String path) {
        return ((Number) read(result, path)).longValue();
    }

    @Test
    void 머리말에_팔로워_팔로잉_수와_팔로우_여부() throws Exception {
        long f1 = members().member().create();
        long f2 = members().member().create();
        long pending = members().member().create();
        long g1 = members().member().create();
        fixtures().follow(f1, owner);
        fixtures().follow(f2, owner);
        fixtures().follow(pending, owner);
        fixtures().follow(owner, g1);
        fixtures().follow(owner, pending);
        fixtures().withdraw(pending);

        MvcResult anonymous = reading().blogHeader(null, "owner_b");
        assertThat(status(anonymous)).as(body(anonymous)).isEqualTo(200);
        assertThat(number(anonymous, "$.followerCount")).isEqualTo(2);
        assertThat(number(anonymous, "$.followingCount")).isEqualTo(1);
        assertThat((Boolean) read(anonymous, "$.followedByMe")).isFalse();

        assertThat((Boolean) read(reading().blogHeader(session, "owner_b"), "$.followedByMe"))
                .isFalse();
        new FollowApi(mockMvc).follow(session, "owner_b");
        MvcResult mine = reading().blogHeader(session, "owner_b");
        assertThat((Boolean) read(mine, "$.followedByMe")).isTrue();
        assertThat(number(mine, "$.followerCount")).isEqualTo(3);

        fixtures().restore(pending);
        assertThat(number(reading().blogHeader(null, "owner_b"), "$.followerCount")).isEqualTo(4);
        assertThat(number(reading().blogHeader(null, "owner_b"), "$.followingCount")).isEqualTo(2);
    }

    @Test
    void 내_블로그면_followedByMe는_false() throws Exception {
        Cookie ownerSession = TestLogin.loginAs(mockMvc, owner);
        MvcResult result = reading().blogHeader(ownerSession, "owner_b");
        assertThat((Boolean) read(result, "$.isMe")).isTrue();
        assertThat((Boolean) read(result, "$.followedByMe")).isFalse();
    }

    @Test
    void 기존_005_칸은_그대로() throws Exception {
        jdbc.update("UPDATE member SET bio = '소개' WHERE id = ?", owner);
        new PostFixtures(jdbc).create(owner, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result = reading().blogHeader(null, "owner_b");

        Map<String, Object> header = read(result, "$");
        assertThat(header)
                .containsOnlyKeys(
                        "handle",
                        "nickname",
                        "bio",
                        "profileImageUrl",
                        "publicPostCount",
                        "isMe",
                        "followerCount",
                        "followingCount",
                        "followedByMe");
        assertThat(header.get("handle")).isEqualTo("owner_b");
        assertThat(header.get("nickname")).isEqualTo("주인");
        assertThat(header.get("bio")).isEqualTo("소개");
        assertThat(((Number) header.get("publicPostCount")).intValue()).isEqualTo(1);
        assertThat(header.get("isMe")).isEqualTo(false);
        assertThat(ReadingApi.cacheControl(result)).isEqualTo("private, no-cache");
    }

    @Test
    void 글_상세의_followingAuthor가_실제_값() throws Exception {
        long postId = new PostFixtures(jdbc).create(owner, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat((Boolean) read(reading().detail(session, postId), "$.viewer.followingAuthor"))
                .isFalse();

        new FollowApi(mockMvc).follow(session, "owner_b");

        assertThat((Boolean) read(reading().detail(session, postId), "$.viewer.followingAuthor"))
                .isTrue();
        assertThat((Boolean) read(reading().detail(null, postId), "$.viewer.followingAuthor"))
                .isFalse();
        Cookie other = TestLogin.loginAs(mockMvc, members().member().create());
        assertThat((Boolean) read(reading().detail(other, postId), "$.viewer.followingAuthor"))
                .isFalse();
    }
}
