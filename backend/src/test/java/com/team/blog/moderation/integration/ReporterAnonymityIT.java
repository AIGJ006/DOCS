package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 작성자에게 신고자가 드러나지 않는다 (014 T059, SC-004). */
class ReporterAnonymityIT extends IntegrationTestBase {

    @Autowired ModerationEventRecorder events;

    @Test
    void 숨겨진_글의_작성자가_받는_응답과_이벤트에_신고자_정보가_없다() throws Exception {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        ReportApi api = new ReportApi(mockMvc);
        List<String> nicknames = List.of("신고자가나다", "신고자라마바", "신고자사아자");
        for (String nickname : nicknames) {
            long reporter = members().member().nickname(nickname).create();
            api.reportPost(TestLogin.loginAs(mockMvc, reporter), postId, "SPAM");
        }
        long adminId = members().member().role("ADMIN").create();
        Cookie admin = TestLogin.loginAs(mockMvc, adminId);
        long caseId = jdbc.queryForObject("SELECT id FROM report_case", Long.class);
        events.clear();
        assertThat(ReportApi.status(api.resolve(admin, caseId, "HIDE", "SPAM"))).isEqualTo(200);

        Cookie authorSession = TestLogin.loginAs(mockMvc, author);
        ReadingApi reading = new ReadingApi(mockMvc);
        String detail = ReadingApi.body(reading.detail(authorSession, postId));
        String manage =
                ReadingApi.body(reading.getAs(authorSession, "/api/me/posts?tab=published"));
        String notifications = ReadingApi.body(reading.getAs(authorSession, "/api/notifications"));
        for (String body : List.of(detail, manage, notifications)) {
            for (String nickname : nicknames) {
                assertThat(body).doesNotContain(nickname);
            }
            assertThat(body).doesNotContain("reporter").doesNotContain("reportCount");
        }

        List<String> fields =
                Arrays.stream(ContentHidden.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();
        assertThat(fields).noneMatch(f -> f.toLowerCase().contains("report"));
        assertThat(events.of(ContentHidden.class)).hasSize(1);
    }
}
