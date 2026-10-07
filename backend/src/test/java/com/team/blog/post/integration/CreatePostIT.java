package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** [새 글] {@code POST /api/posts} (002 T038, US1 #1, FR-001, research B-1). */
class CreatePostIT extends IntegrationTestBase {

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    @Test
    void 기본_공개_범위가_나만_보기인_회원의_새_글은_나만_보기_임시글이다() throws Exception {
        long me = members().member().defaultVisibility("PRIVATE").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult result = api().createPost(session, null);

        assertThat(status(result)).isEqualTo(201);
        long postId = ((Number) read(result, "$.postId")).longValue();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/posts/" + postId + "/working-copy");
        assertThat((String) read(result, "$.status")).isEqualTo("DRAFT");
        assertThat(((Number) read(result, "$.version")).longValue()).isZero();
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat((String) read(result, "$.title")).isEmpty();
        assertThat((String) read(result, "$.contentMd")).isEmpty();
        assertThat((Boolean) read(result, "$.editing")).isFalse();
        assertThat((List<?>) read(result, "$.tags")).isEmpty();
        assertThat((Object) read(result, "$.url")).isNull();
        assertThat((String) read(result, "$.savedAt")).isNotBlank();

        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT author_id, status, visibility, edit_version FROM post");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("author_id", me)
                .containsEntry("status", "DRAFT")
                .containsEntry("visibility", "PRIVATE")
                .containsEntry("edit_version", 0L);
    }

    @Test
    void 기본_공개_범위가_전체_공개면_전체_공개_임시글() throws Exception {
        long me = members().member().defaultVisibility("PUBLIC").create();
        MvcResult result = api().createPost(TestLogin.loginAs(mockMvc, me), null);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PUBLIC");
    }

    @Test
    void 본문을_주면_그_내용으로_만든다() throws Exception {
        long me = members().member().create();
        MvcResult result =
                api().createPost(
                                TestLogin.loginAs(mockMvc, me),
                                Map.of("title", "따로 저장", "contentMd", "편집 중이던 본문"));

        assertThat(status(result)).isEqualTo(201);
        assertThat((String) read(result, "$.title")).isEqualTo("따로 저장");
        assertThat((String) read(result, "$.contentMd")).isEqualTo("편집 중이던 본문");
        assertThat(jdbc.queryForObject("SELECT content_md FROM post", String.class))
                .isEqualTo("편집 중이던 본문");
    }

    @Test
    void 요청_본문의_작성자_번호는_무시한다() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        api().createPost(TestLogin.loginAs(mockMvc, me), Map.of("authorId", other, "title", "t"));
        assertThat(jdbc.queryForObject("SELECT author_id FROM post", Long.class)).isEqualTo(me);
    }

    @Test
    void 제목_101자는_400_TITLE_TOO_LONG이고_글을_만들지_않는다() throws Exception {
        long me = members().member().create();
        MvcResult result =
                api().createPost(TestLogin.loginAs(mockMvc, me), Map.of("title", "가".repeat(101)));

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("title");
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("TITLE_TOO_LONG");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post", Long.class)).isZero();
    }

    @Test
    void 본문_100001자는_400_CONTENT_TOO_LONG() throws Exception {
        long me = members().member().create();
        MvcResult result =
                api().createPost(
                                TestLogin.loginAs(mockMvc, me),
                                Map.of("contentMd", "a".repeat(100_001)));
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("CONTENT_TOO_LONG");
    }

    @Test
    void 비회원은_401() throws Exception {
        MvcResult result = api().createPost(null, null);
        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void 인증_전_회원은_403_EMAIL_NOT_VERIFIED() throws Exception {
        long me = members().member().emailVerified(false).create();
        MvcResult result = api().createPost(TestLogin.loginAs(mockMvc, me), null);
        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post", Long.class)).isZero();
    }

    @Test
    void 탈퇴_유예_회원은_403_ACCOUNT_WITHDRAWN() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        MvcResult result = api().createPost(TestLogin.loginAs(mockMvc, me), null);
        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
    }

    @Test
    void CSRF_헤더가_없으면_403() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        MvcResult result =
                mockMvc.perform(
                                post("/api/posts")
                                        .cookie(session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(status(result)).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post", Long.class)).isZero();
    }
}
