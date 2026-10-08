package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.infra.FollowRepository;
import com.team.blog.interaction.infra.FollowRepository.FollowRow;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** 팔로우 저장·수·목록 SQL (010 T004, contracts/follow-sql.md §1~§3). */
class FollowRepositoryIT extends IntegrationTestBase {

    @Autowired FollowRepository follows;

    private FollowFixtures fixtures() {
        return new FollowFixtures(jdbc);
    }

    @Test
    void insertIfAbsent는_처음만_true() {
        long a = members().member().create();
        long b = members().member().create();
        Instant now = Instant.now();

        assertThat(follows.insertIfAbsent(a, b, now)).isTrue();
        assertThat(follows.insertIfAbsent(a, b, now)).isFalse();
        assertThat(fixtures().rows(a, b)).isEqualTo(1);
        assertThat(follows.exists(a, b)).isTrue();
        assertThat(follows.exists(b, a)).isFalse();
    }

    @Test
    void deleteIfPresent는_있을_때만_true() {
        long a = members().member().create();
        long b = members().member().create();

        assertThat(follows.deleteIfPresent(a, b)).isFalse();
        fixtures().follow(a, b);
        assertThat(follows.deleteIfPresent(a, b)).isTrue();
        assertThat(follows.deleteIfPresent(a, b)).isFalse();
        assertThat(fixtures().rows(a, b)).isZero();
    }

    @Test
    void 자기_팔로우_행은_ck_follow_self_위반() {
        long a = members().member().create();
        assertThatThrownBy(() -> follows.insertIfAbsent(a, a, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_follow_self");
    }

    @Test
    void 수는_탈퇴_유예_회원을_빼고_정지_회원은_센다() {
        long b = members().member().create();
        long active = members().member().create();
        long suspended = members().member().create();
        long withdrawn = members().member().create();
        members().suspend(suspended, Instant.now().plus(7, ChronoUnit.DAYS), "시험");
        for (long m : new long[] {active, suspended, withdrawn}) {
            fixtures().follow(m, b);
            fixtures().follow(b, m);
        }
        fixtures().withdraw(withdrawn);

        assertThat(follows.countFollowers(b)).isEqualTo(2);
        assertThat(follows.countFollowing(b)).isEqualTo(2);
        assertThat(follows.followerIdsOf(b)).containsExactly(active, suspended);

        fixtures().restore(withdrawn);
        assertThat(follows.countFollowers(b)).isEqualTo(3);
        assertThat(follows.countFollowing(b)).isEqualTo(3);
    }

    @Test
    void 팔로워_목록은_최근순_같은_시각이면_회원_번호_큰_순_커서_이후만() {
        long b = members().member().create();
        Instant base = Instant.parse("2026-10-01T00:00:00.123456Z");
        long m1 = members().member().create();
        long m2 = members().member().create();
        long m3 = members().member().create();
        long m4 = members().member().create();
        fixtures().follow(m1, b, base);
        fixtures().follow(m2, b, base.plusSeconds(10));
        fixtures().follow(m3, b, base.plusSeconds(10));
        fixtures().follow(m4, b, base.plusSeconds(20));

        List<FollowRow> first = follows.pageFollowers(b, null, null, 3);
        assertThat(first).extracting(FollowRow::memberId).containsExactly(m4, m3, m2);
        assertThat(first.get(0).createdAt()).isEqualTo(base.plusSeconds(20));

        FollowRow last = first.get(1);
        List<FollowRow> rest = follows.pageFollowers(b, last.createdAt(), last.memberId(), 10);
        assertThat(rest).extracting(FollowRow::memberId).containsExactly(m2, m1);
    }

    @Test
    void 팔로잉_목록은_팔로우한_사람이_항목() {
        long a = members().member().create();
        long x = members().member().create();
        long y = members().member().create();
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        fixtures().follow(a, x, base);
        fixtures().follow(a, y, base.plusSeconds(1));
        fixtures().follow(x, a, base.plusSeconds(2));

        assertThat(follows.pageFollowing(a, null, null, 10))
                .extracting(FollowRow::memberId)
                .containsExactly(y, x);
        assertThat(follows.pageFollowers(a, null, null, 10))
                .extracting(FollowRow::memberId)
                .containsExactly(x);
    }

    @Test
    void 목록은_탈퇴_신청_회원을_빼고_현재_프로필_사진_키를_준다() {
        long b = members().member().create();
        long withPhoto = members().member().handle("photo").nickname("사진").create();
        long thumbless = members().member().create();
        long withdrawn = members().member().create();
        long anonymized = members().member().status("WITHDRAWN").deleted().create();
        jdbc.update("UPDATE member SET bio = '첫 줄\n둘째 줄' WHERE id = ?", withPhoto);
        fixtures().profileImage(withPhoto, "profile/p.png", "profile/p-thumb.webp");
        fixtures().profileImage(thumbless, "profile/t.png", null);
        for (long m : new long[] {withPhoto, thumbless, withdrawn, anonymized}) {
            fixtures().follow(m, b);
        }
        fixtures().withdraw(withdrawn);

        List<FollowRow> rows = follows.pageFollowers(b, null, null, 10);

        assertThat(rows)
                .extracting(FollowRow::memberId)
                .containsExactlyInAnyOrder(withPhoto, thumbless);
        FollowRow photo =
                rows.stream().filter(r -> r.memberId() == withPhoto).findFirst().orElseThrow();
        assertThat(photo.handle()).isEqualTo("photo");
        assertThat(photo.nickname()).isEqualTo("사진");
        assertThat(photo.bio()).isEqualTo("첫 줄\n둘째 줄");
        assertThat(photo.profileKey()).isEqualTo("profile/p-thumb.webp");
        FollowRow plain =
                rows.stream().filter(r -> r.memberId() == thumbless).findFirst().orElseThrow();
        assertThat(plain.profileKey()).isEqualTo("profile/t.png");
    }

    @Test
    void followedAmong은_보는_사람이_팔로우한_번호만() {
        long viewer = members().member().create();
        long x = members().member().create();
        long y = members().member().create();
        long z = members().member().create();
        fixtures().follow(viewer, x);
        fixtures().follow(viewer, z);
        fixtures().follow(y, viewer);

        assertThat(follows.followedAmong(viewer, List.of(x, y, z))).containsExactlyInAnyOrder(x, z);
        assertThat(follows.followedAmong(viewer, List.of())).isEmpty();
    }

    @Test
    void hasFollowing과_deleteAllOf() {
        long a = members().member().create();
        long b = members().member().create();
        long c = members().member().create();
        assertThat(follows.hasFollowing(a)).isFalse();
        fixtures().follow(a, b);
        fixtures().follow(c, a);
        fixtures().follow(b, c);
        assertThat(follows.hasFollowing(a)).isTrue();

        assertThat(follows.deleteAllOf(a)).isEqualTo(2);
        assertThat(fixtures().rowsOf(a)).isZero();
        assertThat(fixtures().rows(b, c)).isEqualTo(1);
    }
}
