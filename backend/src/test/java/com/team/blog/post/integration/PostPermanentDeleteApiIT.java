package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.body;
import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 영구 삭제 {@code DELETE /api/posts/{postId}/permanent} (006 T052, US4, FR-029, SC-004, research R19).
 * 권한 매트릭스 행은 {@code TrashPermissionMatrixIT}가 {@code PurgePostAction}으로 실행한다. 딸린 행 정리는 {@code
 * PostPurgeIT}가 본다.
 */
@Import(PostTestConfig.class)
class PostPermanentDeleteApiIT extends IntegrationTestBase {

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

    @Test
    void US4_1_휴지통글_영구삭제() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat(status(api().trash(session, postId))).isEqualTo(200);
        events.clear();

        MvcResult result = api().purge(session, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> json = read(result, "$");
        assertThat(json).containsExactly(Map.entry("purged", true));
        assertThat(fixtures().exists(postId)).isFalse();
        assertThat(events.of(PostPurged.class)).containsExactly(new PostPurged(postId, me));
    }

    @Test
    void US4_2_휴지통에_없는_글은_404() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long normal = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long draft = posts().post(me).title("임시").contentMd("본문").create();
        long othersTrashed = posts().create(other, PostFixtures.State.TRASHED);

        for (long postId : new long[] {normal, draft, othersTrashed}) {
            Map<String, Object> before = fixtures().row(postId);
            MvcResult result = api().purge(session, postId);
            assertThat(status(result)).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
            assertThat((String) read(result, "$.message")).isEqualTo("볼 수 없는 페이지예요");
            assertThat((List<?>) read(result, "$.errors")).isEmpty();
            assertThat((Object) read(result, "$.details")).isNull();
            assertThat(fixtures().row(postId)).isEqualTo(before);
        }
        assertThat(status(api().purge(session, posts().nonexistentId()))).isEqualTo(404);
        assertThat(events.all()).isEmpty();
    }

    @Test
    void 같은_글_두_번째_영구삭제는_404() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.TRASHED);

        assertThat(status(api().purge(session, postId))).isEqualTo(200);
        MvcResult second = api().purge(session, postId);

        assertThat(status(second)).isEqualTo(404);
        assertThat((String) read(second, "$.code")).isEqualTo("NOT_FOUND");
        assertThat(events.of(PostPurged.class)).hasSize(1);
    }

    @Test
    void 판정_순서_비회원_401_계정_상태_403_인증_전_200() throws Exception {
        long author = members().member().create();
        long trashed = posts().create(author, PostFixtures.State.TRASHED);
        MvcResult anonymous = api().purge(null, trashed);
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat((String) read(anonymous, "$.code")).isEqualTo("LOGIN_REQUIRED");

        long withdrawn = members().member().create();
        long withdrawnPost = posts().create(withdrawn, PostFixtures.State.TRASHED);
        Cookie withdrawnSession = TestLogin.loginAs(mockMvc, withdrawn);
        posts().withdraw(withdrawn);
        MvcResult w = api().purge(withdrawnSession, withdrawnPost);
        assertThat(status(w)).isEqualTo(403);
        assertThat((String) read(w, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");

        long suspended = members().member().create();
        long suspendedPost = posts().create(suspended, PostFixtures.State.TRASHED);
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(Duration.ofDays(3)), "006 테스트");
        MvcResult s = api().purge(suspendedSession, suspendedPost);
        assertThat(status(s)).isEqualTo(403);
        assertThat((String) read(s, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat((String) read(api().purge(suspendedSession, trashed), "$.code"))
                .isEqualTo("ACCOUNT_SUSPENDED");

        long unverified = members().member().emailVerified(false).create();
        long unverifiedPost = posts().create(unverified, PostFixtures.State.TRASHED);
        assertThat(status(api().purge(TestLogin.loginAs(mockMvc, unverified), unverifiedPost)))
                .isEqualTo(200);
        assertThat(fixtures().exists(trashed)).isTrue();
        assertThat(fixtures().exists(withdrawnPost)).isTrue();
        assertThat(fixtures().exists(suspendedPost)).isTrue();
        assertThat(fixtures().exists(unverifiedPost)).isFalse();
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.TRASHED);

        MvcResult result =
                mockMvc.perform(delete("/api/posts/{id}/permanent", postId).cookie(session))
                        .andReturn();

        assertThat(status(result)).isEqualTo(403);
        assertThat(fixtures().exists(postId)).isTrue();
    }
}
