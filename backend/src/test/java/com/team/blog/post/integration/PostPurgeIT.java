package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.PostPurgeService;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 완전 삭제 연쇄 (006 T053, US4, FR-031~035, research R10~R12·R25, 13 §2-5).
 * 댓글(007)·좋아요(009)·사진(003)·신고(014)· 알림(011) 행은 SQL 픽스처로 직접 넣는다. 사진 연결 해제·신고 사건 종료는 006이 임시로 둔
 * {@code ImagePostPurgeStep}·{@code ReportPostPurgeStep}이 한다.
 */
@Import(PostTestConfig.class)
class PostPurgeIT extends IntegrationTestBase {

    @Autowired CommittedEvents events;
    @Autowired PostPurgeService purgeService;
    @Autowired TransactionTemplate tx;

    private long me;
    private long reader;
    private Cookie session;

    private TrashApi api() {
        return new TrashApi(mockMvc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private TrashFixtures fixtures() {
        return new TrashFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        events.clear();
        me = members().member().create();
        reader = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
    }

    private void purge(long postId) throws Exception {
        assertThat(status(api().purge(session, postId))).isEqualTo(200);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void US4_1_연관행_모두_삭제() throws Exception {
        long postId = posts().post(me).published("PUBLIC").draft("고치던 제목", "고치던 본문").create();
        long comment = fixtures().comment(postId, reader, null);
        long reply = fixtures().comment(postId, me, comment);
        fixtures().comment(postId, reader, reply);
        fixtures().like(postId, reader);
        fixtures().tag(postId, "jpa", 0);
        fixtures().image(me, postId);
        fixtures().viewDaily(postId, LocalDate.parse("2026-10-01"), 7);
        long notification = fixtures().commentNotification(me, postId, comment, reader);
        fixtures().trashedAt(postId, Instant.now());

        purge(postId);

        assertThat(fixtures().related(postId))
                .allSatisfy((table, n) -> assertThat(n).as(table).isZero());
        assertThat(count("SELECT count(*) FROM comment WHERE id IN (?, ?)", comment, reply))
                .isZero();
        assertThat(count("SELECT count(*) FROM notification WHERE id = ?", notification)).isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM notification_actor WHERE notification_id = ?",
                                notification))
                .isZero();
        assertThat(fixtures().exists(postId)).isFalse();
    }

    @Test
    void US4_4_그글에서만_쓴_사진만_detached() throws Exception {
        long target = posts().create(me, PostFixtures.State.TRASHED);
        long normal = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long otherTrashed = posts().create(me, PostFixtures.State.TRASHED);
        long only = fixtures().image(me, target);
        long sharedWithNormal = fixtures().image(me, target, normal);
        long sharedWithTrash = fixtures().image(me, target, otherTrashed);
        long untouched = fixtures().image(me, normal);

        purge(target);

        assertThat(detachedAt(only)).isNotNull();
        assertThat(detachedAt(sharedWithNormal)).isNull();
        assertThat(detachedAt(sharedWithTrash)).isNull();
        assertThat(detachedAt(untouched)).isNull();
        assertThat(count("SELECT count(*) FROM image WHERE id = ?", only)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_image WHERE image_id = ?", sharedWithNormal))
                .isEqualTo(1);
    }

    private Object detachedAt(long imageId) {
        return jdbc.queryForObject(
                "SELECT detached_at FROM image WHERE id = ?", Object.class, imageId);
    }

    @Test
    void US4_5_대기신고_대상없음_종료() throws Exception {
        long reporter = members().member().create();
        long postId = posts().create(me, PostFixtures.State.TRASHED);
        long comment = fixtures().comment(postId, reader, null);
        long reply = fixtures().comment(postId, me, comment);
        long postCase = fixtures().postReport(postId, me, reporter, "PENDING");
        long commentCase = fixtures().commentReport(comment, reader, reporter, "PENDING");
        long replyCase = fixtures().commentReport(reply, me, reporter, "PENDING");
        long rejected = fixtures().postReport(postId, me, reader, "REJECTED");
        long otherPost = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long otherCase = fixtures().postReport(otherPost, me, reporter, "PENDING");

        purge(postId);

        for (long caseId : new long[] {postCase, commentCase, replyCase}) {
            Map<String, Object> row = reportCase(caseId);
            assertThat(row.get("status")).as("case " + caseId).isEqualTo("CLOSED_NO_TARGET");
            assertThat(row.get("handled_at")).isNotNull();
            assertThat(row.get("handled_by")).isNull();
            assertThat(row.get("post_id")).isNull();
            assertThat(row.get("comment_id")).isNull();
            assertThat(count("SELECT count(*) FROM report WHERE case_id = ?", caseId)).isEqualTo(1);
        }
        Map<String, Object> handled = reportCase(rejected);
        assertThat(handled.get("status")).isEqualTo("REJECTED");
        assertThat(handled.get("handled_at")).isNull();
        assertThat(handled.get("post_id")).isNull();
        Map<String, Object> other = reportCase(otherCase);
        assertThat(other.get("status")).isEqualTo("PENDING");
        assertThat(((Number) other.get("post_id")).longValue()).isEqualTo(otherPost);
    }

    private Map<String, Object> reportCase(long caseId) {
        return jdbc.queryForMap("SELECT * FROM report_case WHERE id = ?", caseId);
    }

    @Test
    void US4_6_태그는_남음() throws Exception {
        long postId = posts().create(me, PostFixtures.State.TRASHED);
        long tagId = fixtures().tag(postId, "남는태그", 0);

        purge(postId);

        assertThat(count("SELECT count(*) FROM tag WHERE id = ?", tagId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_tag WHERE tag_id = ?", tagId)).isZero();
    }

    @Test
    void US4_7_번호_재사용_안함() throws Exception {
        long postId = posts().create(me, PostFixtures.State.TRASHED);

        purge(postId);
        long next = posts().create(me, PostFixtures.State.DRAFT);

        assertThat(next).isGreaterThan(postId);
    }

    @Test
    void purgeAllByAuthor는_그_회원의_정상_휴지통_글을_모두_지운다() {
        long normal = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long draft = posts().post(me).title("임시").contentMd("본문").create();
        long trashed = posts().create(me, PostFixtures.State.TRASHED);
        long hidden = posts().create(me, PostFixtures.State.HIDDEN);
        long others = posts().create(reader, PostFixtures.State.PUBLISHED_PUBLIC);
        long othersTrashed = posts().create(reader, PostFixtures.State.TRASHED);
        fixtures().comment(others, me, null);

        Integer purged = tx.execute(s -> purgeService.purgeAllByAuthor(me));

        assertThat(purged).isEqualTo(4);
        for (long id : new long[] {normal, draft, trashed, hidden}) {
            assertThat(fixtures().exists(id)).as("post " + id).isFalse();
        }
        assertThat(fixtures().exists(others)).isTrue();
        assertThat(fixtures().exists(othersTrashed)).isTrue();
        assertThat(events.of(PostPurged.class))
                .containsExactlyInAnyOrder(
                        new PostPurged(normal, me),
                        new PostPurged(draft, me),
                        new PostPurged(trashed, me),
                        new PostPurged(hidden, me));
    }
}
