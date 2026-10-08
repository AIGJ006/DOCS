package com.team.blog.post.integration;

import static com.team.blog.post.support.VisibilityApi.body;
import static com.team.blog.post.support.VisibilityApi.read;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 새 글의 기본 공개 범위 (004 T055·T056, US5 인수 1~3, FR-023·FR-024, quickstart 시나리오 8).
 *
 * <p>기본값 저장({@code PATCH /api/me/settings})은 001 T118 소유로 이 브랜치에 아직 없다 — 그 칸은 핸들러가 있을 때만 돌고 없으면
 * {@code pending: 001}로 건너뛴다. 새 글이 회원의 {@code default_visibility}로 시작하는지는 DB 값을 직접 바꿔 002 {@code
 * POST /api/posts}로 확인한다.
 */
class DefaultVisibilityIT extends IntegrationTestBase {

    private static final String SETTINGS = "/api/me/settings";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private EditorApi editor;

    @BeforeEach
    void setUp() {
        editor = new EditorApi(mockMvc);
    }

    private String defaultVisibility(long memberId) {
        return jdbc.queryForObject(
                "SELECT default_visibility FROM member WHERE id = ?", String.class, memberId);
    }

    private String newPostVisibility(Cookie session) throws Exception {
        long postId = editor.createPostId(session);
        return jdbc.queryForObject(
                "SELECT visibility FROM post WHERE id = ?", String.class, postId);
    }

    private void requireSettingsApi() {
        boolean exists;
        try {
            exists =
                    handlerMapping.getHandler(new MockHttpServletRequest("PATCH", SETTINGS))
                            != null;
        } catch (Exception e) {
            exists = false;
        }
        Assumptions.assumeTrue(exists, "pending: 001 (PATCH /api/me/settings, 001 T118)");
    }

    private MvcResult patchSettings(Cookie session, String json) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(patch(SETTINGS), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andReturn();
    }

    @Test
    void US5_1_새_회원의_기본_공개_범위는_PUBLIC이고_새_글도_PUBLIC() throws Exception {
        // 001 가입 경로는 열 값을 주지 않는다 = DB 기본값
        long me = members().member().defaultVisibility("PRIVATE").create();
        jdbc.update("UPDATE member SET default_visibility = DEFAULT WHERE id = ?", me);
        assertThat(defaultVisibility(me)).isEqualTo("PUBLIC");

        assertThat(newPostVisibility(TestLogin.loginAs(mockMvc, me))).isEqualTo("PUBLIC");
    }

    @Test
    void US5_2_기본_공개_범위가_PRIVATE면_새_임시글은_PRIVATE() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        jdbc.update("UPDATE member SET default_visibility = 'PRIVATE' WHERE id = ?", me);

        assertThat(newPostVisibility(session)).isEqualTo("PRIVATE");

        jdbc.update("UPDATE member SET default_visibility = 'PUBLIC' WHERE id = ?", me);
        assertThat(newPostVisibility(session)).as("이미 만든 글은 그대로, 새 글만 따른다").isEqualTo("PUBLIC");
    }

    @Test
    void 설정_API로_PRIVATE를_저장하면_새_글이_PRIVATE() throws Exception {
        requireSettingsApi();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        assertThat(status(patchSettings(session, "{\"defaultVisibility\":\"PRIVATE\"}")))
                .isEqualTo(200);

        assertThat(defaultVisibility(me)).isEqualTo("PRIVATE");
        assertThat(newPostVisibility(session)).isEqualTo("PRIVATE");
    }

    @Test
    void 허용되지_않는_값은_400_INVALID_VISIBILITY_defaultVisibility_칸() throws Exception {
        requireSettingsApi();
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        for (String value : new String[] {"FRIENDS", "PROTECTED", "public", ""}) {
            MvcResult result = patchSettings(session, "{\"defaultVisibility\":\"" + value + "\"}");
            assertThat(status(result)).as(value).isEqualTo(400);
            assertThat((String) read(result, "$.code")).as(value).isEqualTo("INVALID_VISIBILITY");
            assertThat((String) read(result, "$.errors[0].field"))
                    .as(value)
                    .isEqualTo("defaultVisibility");
        }
        assertThat(defaultVisibility(me)).isEqualTo("PUBLIC");
    }

    @Test
    void 비회원은_401() throws Exception {
        requireSettingsApi();

        MvcResult result = patchSettings(null, "{\"defaultVisibility\":\"PRIVATE\"}");

        assertThat(status(result)).isEqualTo(401);
        assertThat(body(result)).contains("\"code\":\"LOGIN_REQUIRED\"");
    }

    @Test
    void US5_3_본문에_다른_회원_번호를_넣어도_본인_값만_바뀐다() throws Exception {
        requireSettingsApi();
        long me = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult result =
                patchSettings(
                        session,
                        "{\"defaultVisibility\":\"PRIVATE\",\"memberId\":"
                                + other
                                + ",\"userId\":"
                                + other
                                + "}");

        assertThat(status(result)).isIn(200, 400);
        assertThat(defaultVisibility(other)).isEqualTo("PUBLIC");
    }
}
