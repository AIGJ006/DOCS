package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.application.ViewExclusion;
import com.team.blog.interaction.application.ViewOutcome;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 조회 제외 판정 (009 T024, FR-024·FR-031, contracts/view-pipeline.md §1 ②). */
class ViewExclusionTest {

    private static final PostView POST =
            new PostView(10, 7, PostStatus.PUBLISHED, Visibility.PUBLIC, null, null, null);

    private final ViewExclusion exclusion =
            ViewExclusion.fromResource(new ClassPathResource("policy/view-bot-user-agents.txt"));

    private static Viewer viewer(long id, Role role) {
        return new Viewer(id, role, MemberStatus.ACTIVE, true);
    }

    @Test
    void 기본_봇_단어는_대소문자를_무시한_부분_일치() {
        List<String> bots =
                List.of(
                        "Googlebot/2.1 (+http://www.google.com/bot.html)",
                        "facebookexternalhit/1.1",
                        "Slackbot-LinkExpanding 1.0",
                        "kakaotalk-scrap/1.0",
                        "Mozilla/5.0 (compatible; Discordbot/2.0)",
                        "Mozilla/5.0 HeadlessChrome/120.0",
                        "SomeCrawler",
                        "my-SPIDER",
                        "Link Preview Fetcher");
        for (String ua : bots) {
            assertThat(exclusion.reason(POST, Viewer.anonymous(), ua, null, null))
                    .as(ua)
                    .contains(ViewOutcome.EXCLUDED_BOT);
        }
        assertThat(
                        exclusion.reason(
                                POST,
                                Viewer.anonymous(),
                                "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36",
                                null,
                                null))
                .isEmpty();
        assertThat(exclusion.reason(POST, Viewer.anonymous(), null, null, null)).isEmpty();
    }

    @Test
    void 미리_불러오기_헤더는_제외() {
        assertThat(exclusion.reason(POST, Viewer.anonymous(), "Mozilla", "prefetch", null))
                .contains(ViewOutcome.EXCLUDED_PREFETCH);
        assertThat(
                        exclusion.reason(
                                POST, Viewer.anonymous(), "Mozilla", "prefetch;prerender", null))
                .contains(ViewOutcome.EXCLUDED_PREFETCH);
        assertThat(exclusion.reason(POST, Viewer.anonymous(), "Mozilla", null, "Prefetch"))
                .contains(ViewOutcome.EXCLUDED_PREFETCH);
        assertThat(exclusion.reason(POST, Viewer.anonymous(), "Mozilla", "", null)).isEmpty();
    }

    @Test
    void 작성자와_관리자는_제외() {
        assertThat(exclusion.reason(POST, viewer(7, Role.USER), "Mozilla", null, null))
                .contains(ViewOutcome.EXCLUDED_AUTHOR);
        assertThat(exclusion.reason(POST, viewer(7, Role.ADMIN), "Mozilla", null, null))
                .as("관리자도 자기 글이면 작성자")
                .contains(ViewOutcome.EXCLUDED_AUTHOR);
        assertThat(exclusion.reason(POST, viewer(8, Role.ADMIN), "Mozilla", null, null))
                .contains(ViewOutcome.EXCLUDED_ADMIN);
        assertThat(exclusion.reason(POST, viewer(8, Role.USER), "Mozilla", null, null)).isEmpty();
    }

    @Test
    void 단어_목록은_주석과_빈_줄을_건너뛴다() {
        ViewExclusion custom = ViewExclusion.of(List.of("# 주석", "", "  MyBot  "));
        assertThat(custom.reason(POST, Viewer.anonymous(), "x-mybot-1", null, null))
                .contains(ViewOutcome.EXCLUDED_BOT);
        assertThat(custom.reason(POST, Viewer.anonymous(), "주석", null, null)).isEmpty();
    }
}
