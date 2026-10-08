package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.application.PostDraftQueryService;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 발행 글을 고치는 동안 독자는 마지막 발행본을 본다 (002 T089, US4 #1·#4, SC-006, FR-015·034). 005 글 상세가 없어 004 {@code
 * PostReadService.requireReadable}과 DB의 발행본 칸으로 확인한다.
 */
class PublishedWorkingCopyIT extends IntegrationTestBase {

    @Autowired AutosaveFlushJob flushJob;
    @Autowired PostReadService readService;
    @Autowired PostDraftQueryService draftQuery;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    private Map<String, Object> published(long postId) {
        return jdbc.queryForMap(
                "SELECT title, content_md, content_html, excerpt, edited_at, edit_version,"
                        + " updated_at FROM post WHERE id = ?",
                postId);
    }

    @Test
    void 자동_저장_1분_반영_수동_저장을_해도_독자는_마지막_발행본을_본다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures()
                        .posts()
                        .post(me)
                        .published("PUBLIC")
                        .title("발행 제목")
                        .contentMd("발행 본문")
                        .contentHtml("<p>발행 본문</p>")
                        .create();
        Map<String, Object> before = published(postId);

        assertThat(status(api().autosave(session, postId, saveBody("고치는 중", "고치는 본문", 1))))
                .isEqualTo(200);
        flushJob.flush();
        assertThat(status(api().save(session, postId, saveBody("수동 저장", "수동 본문", 2))))
                .isEqualTo(200);

        assertThat(published(postId)).isEqualTo(before);
        assertThat(readService.requireReadable(postId, Viewer.anonymous()).id()).isEqualTo(postId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT title FROM post_draft WHERE post_id = ?",
                                String.class,
                                postId))
                .isEqualTo("수동 저장");
    }

    @Test
    void 작업본이_있으면_에디터는_작업본을_editing_true로_연다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().publishedWithWorkingCopy(me, "작업본 제목", "작업본 본문");

        MvcResult opened = api().workingCopy(session, postId);

        assertThat(status(opened)).isEqualTo(200);
        assertThat((Boolean) read(opened, "$.editing")).isTrue();
        assertThat((String) read(opened, "$.status")).isEqualTo("PUBLISHED");
        assertThat((String) read(opened, "$.title")).isEqualTo("작업본 제목");
        assertThat((String) read(opened, "$.contentMd")).isEqualTo("작업본 본문");
        assertThat(((Number) read(opened, "$.version")).longValue()).isEqualTo(2);
        assertThat((String) read(opened, "$.url")).endsWith("/posts/" + postId);
    }

    @Test
    void 작업본이_없으면_에디터는_발행본을_editing_false로_연다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures()
                        .posts()
                        .post(me)
                        .published("PUBLIC")
                        .title("발행본")
                        .contentMd("발행 본문")
                        .create();

        MvcResult opened = api().workingCopy(session, postId);

        assertThat((Boolean) read(opened, "$.editing")).isFalse();
        assertThat((String) read(opened, "$.title")).isEqualTo("발행본");
        assertThat((String) read(opened, "$.contentMd")).isEqualTo("발행 본문");
        assertThat(((Number) read(opened, "$.version")).longValue()).isEqualTo(1);
    }

    @Test
    void findSavedAt은_작업본의_마지막_저장_시각() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        assertThat(draftQuery.findSavedAt(postId)).isEmpty();

        MvcResult saved = api().save(session, postId, saveBody("고침", "고침", 1));

        Timestamp draftUpdatedAt =
                jdbc.queryForObject(
                        "SELECT updated_at FROM post_draft WHERE post_id = ?",
                        Timestamp.class,
                        postId);
        assertThat(draftQuery.findSavedAt(postId)).contains(draftUpdatedAt.toInstant());
        assertThat(draftUpdatedAt.toInstant().toString())
                .isEqualTo(java.time.Instant.parse(read(saved, "$.savedAt")).toString());
    }
}
