package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 변경 취소 {@code DELETE /api/posts/{id}/working-copy} (002 T090, US4 #3, FR-035, B-3 ⑤). 권한 행렬(행위자
 * 6종)은 {@code PostAuthoringPermissionMatrixIT}의 {@code post.discard} 행이 맡는다.
 */
@Import(PostTestConfig.class)
class DiscardWorkingCopyIT extends IntegrationTestBase {

    private static final Instant SAVED_AT = Instant.parse("2026-10-07T05:03:12.123456Z");

    @Autowired CommittedEvents events;
    @Autowired AutosaveFlushJob flushJob;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    private Map<String, Object> post(long postId) {
        return jdbc.queryForMap(
                "SELECT title, content_md, content_html, status, edit_version, updated_at,"
                        + " edited_at FROM post WHERE id = ?",
                postId);
    }

    private int draftCount(long postId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_draft WHERE post_id = ?", Integer.class, postId);
    }

    @Test
    void 작업본을_버리고_버전을_올리며_발행본은_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().publishedWithWorkingCopy(me, "고치던 제목", "고치던 본문");
        Map<String, Object> before = post(postId);
        assertThat(before.get("edit_version")).isEqualTo(1L);

        MvcResult result = api().discard(session, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(204);
        assertThat(result.getResponse().getContentLength()).isLessThanOrEqualTo(0);
        assertThat(draftCount(postId)).isZero();
        Map<String, Object> after = post(postId);
        // 현재 버전 = max(post 1, 작업본 2) = 2 → 3
        assertThat(after.get("edit_version")).isEqualTo(3L);
        assertThat(((Timestamp) after.get("updated_at")).toInstant())
                .isAfter(((Timestamp) before.get("updated_at")).toInstant());
        assertThat(after.get("title")).isEqualTo(before.get("title"));
        assertThat(after.get("content_md")).isEqualTo(before.get("content_md"));
        assertThat(after.get("content_html")).isEqualTo(before.get("content_html"));
        assertThat(after.get("edited_at")).isEqualTo(before.get("edited_at"));
        assertThat(after.get("status")).isEqualTo("PUBLISHED");
        assertThat(events.all()).isEmpty();

        MvcResult opened = api().workingCopy(session, postId);
        assertThat((Boolean) read(opened, "$.editing")).isFalse();
        assertThat((String) read(opened, "$.title")).isEqualTo(before.get("title"));
        assertThat(((Number) read(opened, "$.version")).longValue()).isEqualTo(3);
    }

    @Test
    void 커밋_후_Redis_보관분을_정리한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        fixtures().putAutosave(postId, me, "Redis에만 있는 내용", "본문", 4, SAVED_AT, true);

        assertThat(status(api().discard(session, postId))).isEqualTo(204);

        assertThat(post(postId).get("edit_version")).isEqualTo(5L);
        assertThat(fixtures().autosaveHash(postId)).isEmpty();
        assertThat(fixtures().isDirty(postId)).isFalse();
        flushJob.flush();
        assertThat(draftCount(postId)).isZero();
    }

    @Test
    void 작업본이_없어도_204이고_여러_번_보내도_결과가_같다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();

        assertThat(status(api().discard(session, postId))).isEqualTo(204);
        assertThat(status(api().discard(session, postId))).isEqualTo(204);

        assertThat(draftCount(postId)).isZero();
        assertThat(post(postId).get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void 임시글은_409_NOT_PUBLISHED이고_바뀌지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).title("임시").contentMd("임시").create();
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();

        MvcResult result = api().discard(session, postId);

        assertThat(status(result)).isEqualTo(409);
        assertThat((String) read(result, "$.code")).isEqualTo("NOT_PUBLISHED");
        assertThat((String) read(result, "$.message")).isEqualTo("발행한 글만 변경을 취소할 수 있어요");
        assertThat((List<?>) read(result, "$.errors")).isEmpty();
        assertThat(fixtures().snapshot(postId)).contains(before);
    }

    @Test
    void 버린_작업본의_버전을_들고_있던_다른_탭의_저장은_409이고_작업본은_되살아나지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        // 다른 탭이 작업본 버전 2를 만들었다
        assertThat(status(api().save(session, postId, saveBody("다른 탭", "다른 탭", 1)))).isEqualTo(200);

        assertThat(status(api().discard(session, postId))).isEqualTo(204);
        MvcResult autosaved = api().autosave(session, postId, saveBody("다른 탭 계속", "계속", 2));
        MvcResult saved = api().save(session, postId, saveBody("다른 탭 계속", "계속", 2));

        assertThat(status(autosaved)).isEqualTo(409);
        assertThat(status(saved)).isEqualTo(409);
        assertThat(((Number) read(saved, "$.details.server.version")).longValue()).isEqualTo(3);
        flushJob.flush();
        assertThat(draftCount(postId)).isZero();
    }

    @Test
    void 남의_글_없는_글_휴지통_글은_404이고_바뀌지_않는다() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long others = fixtures().publishedWithWorkingCopy(owner, "남의 작업본", "본문");
        long trashed =
                fixtures()
                        .posts()
                        .post(me)
                        .published("PUBLIC")
                        .draft("휴지통", "본문")
                        .trashed()
                        .create();
        long missing = fixtures().posts().nonexistentId();

        for (long postId : List.of(others, trashed, missing)) {
            var before = fixtures().snapshot(postId);
            int drafts = draftCount(postId);
            MvcResult result = api().discard(session, postId);
            assertThat(status(result)).isEqualTo(404);
            assertThat(body(result))
                    .isEqualTo(
                            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}");
            assertThat(fixtures().snapshot(postId)).isEqualTo(before);
            assertThat(draftCount(postId)).isEqualTo(drafts);
        }
    }
}
