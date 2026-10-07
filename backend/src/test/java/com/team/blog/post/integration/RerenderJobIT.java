package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.team.blog.post.application.RerenderJob;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.support.IntegrationTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 다시 렌더링 배치 (002 T113, US7 #1, SC-010, FR-048, A-11, B-10). 렌더링 규칙 버전이 오르면 발행 글의 {@code
 * content_html}· {@code excerpt}만 다시 만들고 {@code edited_at}·{@code edit_version}·{@code updated_at}은
 * 그대로 둔다. 현재 버전은 상수라 테스트는 {@link RerenderJob#rerender(int)}에 2를 넣어 부른다.
 */
@Import(PostTestConfig.class)
class RerenderJobIT extends IntegrationTestBase {

    private static final int NEXT = 2;

    @Autowired RerenderJob job;
    @Autowired CommittedEvents events;
    @Autowired MeterRegistry meters;
    @MockitoSpyBean ContentRenderer renderer;

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @AfterEach
    void resetSpy() {
        reset(renderer);
    }

    private long stale(int version) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post WHERE status = 'PUBLISHED' AND render_version < ?",
                Long.class,
                version);
    }

    private Map<String, Object> untouched(long postId) {
        return jdbc.queryForMap(
                "SELECT edited_at, edit_version, updated_at, published_at, title, content_md FROM"
                        + " post WHERE id = ?",
                postId);
    }

    private double remainingGauge() {
        return meters.get("blog.post.rerender.remaining").gauge().value();
    }

    @Test
    void 규칙_버전이_낮은_발행_글_250개를_모두_다시_렌더링하고_수정_흔적은_남기지_않는다() {
        long me = members().member().create();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            ids.add(
                    fixtures()
                            .posts()
                            .post(me)
                            .published("PUBLIC")
                            .contentMd("**굵게 " + i + "** 다시 렌더링")
                            .contentHtml("<p>옛 HTML</p>")
                            .create());
        }
        long draft = fixtures().posts().post(me).title("임시").contentMd("**임시**").create();
        Map<String, Object> before = untouched(ids.get(17));
        Map<String, Object> draftBefore =
                jdbc.queryForMap("SELECT * FROM post WHERE id = ?", draft);
        assertThat(stale(NEXT)).isEqualTo(250);

        job.rerender(NEXT);

        assertThat(stale(NEXT)).isZero();
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT content_html, excerpt, render_version FROM post WHERE id = ?",
                        ids.get(17));
        assertThat((String) row.get("content_html")).contains("<strong>굵게 17</strong>");
        assertThat((String) row.get("excerpt")).isEqualTo("굵게 17 다시 렌더링");
        assertThat(row.get("render_version")).isEqualTo(NEXT);
        assertThat(untouched(ids.get(17))).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM post WHERE id = ?", draft))
                .isEqualTo(draftBefore);
        assertThat(events.all()).isEmpty();
        assertThat(remainingGauge()).isZero();
    }

    @Test
    void 사진_판별은_글_작성자_기준이다() {
        long author = members().member().create();
        long postId = fixtures().publishedWithRenderVersion(author, 1, "작성자 글");

        job.rerender(NEXT);

        verify(renderer).render(eq("작성자 글"), eq(new ImageContext(author)));
        assertThat(postId).isPositive();
    }

    @Test
    void 읽은_뒤_편집_버전이_바뀐_글은_이번_회차에_건너뛴다() {
        long me = members().member().create();
        long racing = fixtures().publishedWithRenderVersion(me, 1, "경쟁 글");
        long calm = fixtures().publishedWithRenderVersion(me, 1, "조용한 글");
        doAnswer(
                        invocation -> {
                            jdbc.update(
                                    "UPDATE post SET edit_version = edit_version + 1,"
                                            + " content_html = '<p>사용자가 다시 발행</p>'"
                                            + " WHERE id = ?",
                                    racing);
                            return invocation.callRealMethod();
                        })
                .when(renderer)
                .render(eq("경쟁 글"), any());

        job.rerender(NEXT);

        assertThat(
                        jdbc.queryForMap(
                                "SELECT content_html, render_version FROM post WHERE id = ?",
                                racing))
                .containsEntry("content_html", "<p>사용자가 다시 발행</p>")
                .containsEntry("render_version", 1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT render_version FROM post WHERE id = ?",
                                Integer.class,
                                calm))
                .isEqualTo(NEXT);
    }

    @Test
    void 너무_복잡한_글은_건너뛰고_남은_건수로_센다() {
        long me = members().member().create();
        long complex = fixtures().publishedWithRenderVersion(me, 1, "복잡한 글");
        long fine = fixtures().publishedWithRenderVersion(me, 1, "보통 글");
        doThrow(new ContentTooComplexException()).when(renderer).render(eq("복잡한 글"), any());

        job.rerender(NEXT);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT render_version FROM post WHERE id = ?",
                                Integer.class,
                                complex))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT render_version FROM post WHERE id = ?",
                                Integer.class,
                                fine))
                .isEqualTo(NEXT);
        assertThat(remainingGauge()).isEqualTo(1.0);
    }

    @Test
    void 대상이_없으면_렌더링하지_않고_끝난다() {
        long me = members().member().create();
        fixtures().publishedWithRenderVersion(me, NEXT, "이미 최신");

        job.rerender(NEXT);

        verify(renderer, never()).render(any(), any());
        assertThat(remainingGauge()).isZero();
    }
}
