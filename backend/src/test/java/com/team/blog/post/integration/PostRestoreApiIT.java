package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.body;
import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostRestored;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 휴지통에서 복구 {@code POST /api/posts/{postId}/restore} (006 T026, US2, FR-027·028, SC-002, research
 * R2·R3·R19). 권한 매트릭스 행은 004 {@code PermissionMatrixIT}가 {@code RestorePostAction}으로 실행한다.
 */
@Import(PostTestConfig.class)
class PostRestoreApiIT extends IntegrationTestBase {

    @Autowired CommittedEvents events;

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
    void clearEvents() {
        events.clear();
    }

    /** {@code deleted_at}을 뺀 글 행. */
    private Map<String, Object> rowWithoutDeletedAt(long postId) {
        Map<String, Object> row = new LinkedHashMap<>(fixtures().row(postId));
        row.remove("deleted_at");
        return row;
    }

    @Test
    void US2_1_공개글_복구하면_원래_위치() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                posts().post(me)
                        .published("PUBLIC")
                        .updatedAt(Instant.parse("2026-10-01T00:00:00.123456Z"))
                        .create();
        Map<String, Object> before = rowWithoutDeletedAt(postId);
        assertThat(status(api().trash(session, postId))).isEqualTo(200);
        events.clear();

        MvcResult result = api().restore(session, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> json = read(result, "$");
        assertThat(json)
                .containsExactly(
                        Map.entry("restored", true),
                        Map.entry("status", "PUBLISHED"),
                        Map.entry("visibility", "PUBLIC"));
        assertThat(fixtures().deletedAt(postId)).isNull();
        assertThat(rowWithoutDeletedAt(postId)).isEqualTo(before);
        assertThat(events.of(PostRestored.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.postId()).isEqualTo(postId);
                            assertThat(e.authorId()).isEqualTo(me);
                            assertThat(e.restoredAt()).isNotNull();
                        });
    }

    @Test
    void US2_2_임시글_복구하면_원래_위치() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        Instant base = Instant.parse("2026-10-03T00:00:00Z");
        long oldest = posts().post(me).title("가").updatedAt(base).create();
        long middle = posts().post(me).title("나").updatedAt(base.plusSeconds(60)).create();
        long newest = posts().post(me).title("다").updatedAt(base.plusSeconds(120)).create();
        List<Long> before = draftOrder(me);
        assertThat(before).containsExactly(newest, middle, oldest);

        assertThat(status(api().trash(session, middle))).isEqualTo(200);
        assertThat(draftOrder(me)).containsExactly(newest, oldest);
        MvcResult result = api().restore(session, middle);

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.status")).isEqualTo("DRAFT");
        assertThat(draftOrder(me)).isEqualTo(before);
    }

    @Test
    void US2_3_작업본_있는_발행글_복구() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).published("PRIVATE").draft("고치던 제목", "고치던 본문").create();
        Map<String, Object> draftBefore =
                jdbc.queryForMap("SELECT * FROM post_draft WHERE post_id = ?", postId);
        assertThat(status(api().trash(session, postId))).isEqualTo(200);

        MvcResult result = api().restore(session, postId);

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat(jdbc.queryForMap("SELECT * FROM post_draft WHERE post_id = ?", postId))
                .isEqualTo(draftBefore);
    }

    @Test
    void US2_4_휴지통에_없는_글_복구는_404() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long normal = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long draft = posts().post(me).title("임시").create();
        long othersTrashed = posts().create(other, PostFixtures.State.TRASHED);

        for (long postId : new long[] {normal, draft, othersTrashed}) {
            Map<String, Object> before = fixtures().row(postId);
            MvcResult result = api().restore(session, postId);
            assertThat(status(result)).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
            assertThat(fixtures().row(postId)).isEqualTo(before);
        }
        assertThat(status(api().restore(session, posts().nonexistentId()))).isEqualTo(404);
        assertThat(events.all()).isEmpty();
    }

    @Test
    void 숨긴_글은_복구해도_숨김_유지() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.HIDDEN);
        Map<String, Object> before = rowWithoutDeletedAt(postId);
        assertThat(status(api().trash(session, postId))).isEqualTo(200);

        assertThat(status(api().restore(session, postId))).isEqualTo(200);

        assertThat(rowWithoutDeletedAt(postId)).isEqualTo(before);
        assertThat(fixtures().row(postId).get("hidden_at")).isNotNull();
    }

    @Test
    void 댓글_좋아요_태그는_복구_뒤에도_같다() throws Exception {
        long me = members().member().create();
        long reader = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long comment = fixtures().comment(postId, reader, null);
        fixtures().comment(postId, me, comment);
        fixtures().like(postId, reader);
        fixtures().tag(postId, "jpa", 0);
        fixtures().tag(postId, "성능", 1);
        fixtures().counts(postId, 10, 1, 2);
        Map<String, Long> related = fixtures().related(postId);
        Map<String, Object> before = rowWithoutDeletedAt(postId);

        assertThat(status(api().trash(session, postId))).isEqualTo(200);
        assertThat(status(api().restore(session, postId))).isEqualTo(200);

        assertThat(fixtures().related(postId)).isEqualTo(related);
        assertThat(rowWithoutDeletedAt(postId)).isEqualTo(before);
    }

    @Test
    void 판정_순서_비회원_401_계정_상태_403_인증_전_200() throws Exception {
        long author = members().member().create();
        long trashed = posts().create(author, PostFixtures.State.TRASHED);
        MvcResult anonymous = api().restore(null, trashed);
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat((String) read(anonymous, "$.code")).isEqualTo("LOGIN_REQUIRED");

        long withdrawn = members().member().create();
        long withdrawnPost = posts().create(withdrawn, PostFixtures.State.TRASHED);
        Cookie withdrawnSession = TestLogin.loginAs(mockMvc, withdrawn);
        posts().withdraw(withdrawn);
        MvcResult w = api().restore(withdrawnSession, withdrawnPost);
        assertThat(status(w)).isEqualTo(403);
        assertThat((String) read(w, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
        MvcResult wOthers = api().restore(withdrawnSession, trashed);
        assertThat((String) read(wOthers, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");

        long suspended = members().member().create();
        long suspendedPost = posts().create(suspended, PostFixtures.State.TRASHED);
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(Duration.ofDays(3)), "006 테스트");
        MvcResult s = api().restore(suspendedSession, suspendedPost);
        assertThat(status(s)).isEqualTo(403);
        assertThat((String) read(s, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat((String) read(api().restore(suspendedSession, trashed), "$.code"))
                .isEqualTo("ACCOUNT_SUSPENDED");

        long unverified = members().member().emailVerified(false).create();
        long unverifiedPost = posts().create(unverified, PostFixtures.State.TRASHED);
        assertThat(status(api().restore(TestLogin.loginAs(mockMvc, unverified), unverifiedPost)))
                .isEqualTo(200);
        assertThat(fixtures().deletedAt(trashed)).isNotNull();
        assertThat(fixtures().deletedAt(withdrawnPost)).isNotNull();
        assertThat(fixtures().deletedAt(suspendedPost)).isNotNull();
    }

    private List<Long> draftOrder(long authorId) {
        return jdbc.queryForList(
                "SELECT id FROM post WHERE author_id = ? AND status = 'DRAFT' AND deleted_at IS NULL"
                        + " ORDER BY updated_at DESC, id DESC",
                Long.class,
                authorId);
    }
}
