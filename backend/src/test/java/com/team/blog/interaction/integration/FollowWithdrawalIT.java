package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.FollowApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.application.FollowPurgeService;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴하는 회원의 팔로우 관계 (010 T039 US4, SC-006·SC-008). 015가 없어도 확인하도록 탈퇴 유예·복구는 DB에서 {@code
 * status}·{@code withdrawn_at}을 직접 바꾸고, 정리는 015 단계가 부를 {@link FollowPurgeService#purgeByMember}를
 * 트랜잭션 안에서 부른다.
 */
class FollowWithdrawalIT extends IntegrationTestBase {

    @Autowired FollowPurgeService purgeService;
    @Autowired TransactionTemplate tx;

    private long a;
    private long b;
    private long other;
    private Cookie bSession;
    private long aPost;

    @BeforeEach
    void setUp() {
        a = members().member().handle("leaver_a").create();
        b = members().member().handle("stay_b").create();
        other = members().member().create();
        FollowFixtures f = new FollowFixtures(jdbc);
        f.follow(a, b);
        f.follow(b, a);
        f.follow(other, b);
        f.follow(b, other);
        aPost = new PostFixtures(jdbc).create(a, PostFixtures.State.PUBLISHED_PUBLIC);
        bSession = TestLogin.loginAs(mockMvc, b);
    }

    private FollowApi api() {
        return new FollowApi(mockMvc);
    }

    private record Snapshot(
            long followers,
            long following,
            List<String> followerList,
            List<String> followingList,
            List<Number> feed) {}

    private Snapshot snapshot() throws Exception {
        MvcResult header = api().header(null, "stay_b");
        return new Snapshot(
                ((Number) read(header, "$.followerCount")).longValue(),
                ((Number) read(header, "$.followingCount")).longValue(),
                read(api().followers(null, "stay_b", null), "$.items[*].handle"),
                read(api().following(null, "stay_b", null), "$.items[*].handle"),
                read(api().feed(bSession, null), "$.items[*].id"));
    }

    @Test
    void US4_1_유예중_빠짐_US4_2_복구하면_그대로() throws Exception {
        Snapshot before = snapshot();
        assertThat(before.followers()).isEqualTo(2);
        assertThat(before.followerList()).contains("leaver_a");
        assertThat(before.feed()).extracting(Number::longValue).containsExactly(aPost);

        new FollowFixtures(jdbc).withdraw(a);

        Snapshot during = snapshot();
        assertThat(during.followers()).isEqualTo(1);
        assertThat(during.following()).isEqualTo(1);
        assertThat(during.followerList()).doesNotContain("leaver_a");
        assertThat(during.followingList()).doesNotContain("leaver_a");
        assertThat(during.feed()).isEmpty();
        assertThat(new FollowFixtures(jdbc).rowsOf(a)).as("유예 중에는 행이 남는다").isEqualTo(2);

        new FollowFixtures(jdbc).restore(a);

        Snapshot after = snapshot();
        assertThat(after.equals(before)).as(after + " = " + before).isTrue();
    }

    @Test
    void US4_3_정리하면_양방향_0행_남의_관계는_그대로() {
        Integer deleted = tx.execute(status -> purgeService.purgeByMember(a));

        assertThat(deleted).isEqualTo(2);
        FollowFixtures f = new FollowFixtures(jdbc);
        assertThat(f.rowsOf(a)).isZero();
        assertThat(f.rows(other, b)).isEqualTo(1);
        assertThat(f.rows(b, other)).isEqualTo(1);
        Integer again = tx.execute(status -> purgeService.purgeByMember(a));
        assertThat(again).as("멱등").isZero();
    }

    @Test
    void 트랜잭션_밖_호출이면_예외() {
        assertThatThrownBy(() -> purgeService.purgeByMember(a))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(new FollowFixtures(jdbc).rowsOf(a)).isEqualTo(2);
    }

    @Test
    void 정리_순서는_65() {
        assertThat(FollowPurgeService.WITHDRAWAL_PURGE_ORDER).isEqualTo(65);
    }
}
