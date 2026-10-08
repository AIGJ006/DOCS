package com.team.blog.category.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.purge.WithdrawPurgeJob;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 탈퇴 30일 정리 때 카테고리 삭제 (017 FR-050, research R10). */
class CategoryWithdrawalPurgeIT extends IntegrationTestBase {

    @Autowired private WithdrawPurgeJob job;

    @Test
    void 익명_처리되면_카테고리를_모두_지운다() {
        CategoryFixtures categories = new CategoryFixtures(jdbc);
        PostFixtures posts = new PostFixtures(jdbc);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long leaver = members().member().create();
        long stay = members().member().create();
        long dev = categories.create(leaver, null, "개발");
        long spring = categories.create(leaver, dev, "Spring");
        categories.assign(posts.create(leaver, PostFixtures.State.PUBLISHED_PUBLIC), spring);
        long kept = categories.create(stay, null, "남는 것");
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(31))),
                leaver);

        job.run(now);

        assertThat(count("SELECT count(*) FROM category WHERE member_id = ?", leaver)).isZero();
        assertThat(count("SELECT count(*) FROM category WHERE id = ?", kept)).isEqualTo(1);
        // 다시 불려도 같다
        job.run(now);
        assertThat(count("SELECT count(*) FROM category WHERE member_id = ?", leaver)).isZero();
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }
}
