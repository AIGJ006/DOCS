package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.moderation.application.AdminMemberService;
import com.team.blog.moderation.application.CaseQueryService;
import com.team.blog.moderation.application.CaseResolutionService;
import com.team.blog.moderation.application.DirectHideService;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 관리자 경로 두 겹 (014 T027, US2 #1, SC-007). 004 AdminPathIT 표를 이 기능의 실제 경로로 다시 본다. */
class AdminAccessIT extends IntegrationTestBase {

    private static final String LOGIN_REQUIRED =
            "{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요해요\",\"errors\":[],\"details\":null}";
    private static final String NOT_FOUND =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    @Autowired CaseQueryService caseQueries;
    @Autowired CaseResolutionService resolution;
    @Autowired DirectHideService directHide;
    @Autowired AdminMemberService adminMembers;

    private record Shape(int status, String cacheControl, String body) {}

    private Shape send(MockHttpServletRequestBuilder request, Cookie session) throws Exception {
        MockHttpServletResponse r =
                mockMvc.perform(TestLogin.withCsrf(request, session)).andReturn().getResponse();
        return new Shape(
                r.getStatus(),
                r.getHeader("Cache-Control"),
                r.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<MockHttpServletRequestBuilder> apiRequests(long postId, String handle) {
        return List.of(
                get("/api/admin/reports"),
                get("/api/admin/reports/1"),
                get("/api/admin/members/{h}", handle),
                put("/api/admin/posts/{id}/hidden", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"));
    }

    @Test
    void 비회원은_같은_401_일반_회원은_같은_404_관리자는_통과() throws Exception {
        long author = members().member().handle("target01").create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = new ReportFixtures(jdbc).pendingPost(postId, author);
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());

        for (MockHttpServletRequestBuilder req : apiRequests(postId, "target01")) {
            Shape anon = send(req, null);
            assertThat(anon.status()).isEqualTo(401);
            assertThat(anon.body()).isEqualTo(LOGIN_REQUIRED);
        }
        for (MockHttpServletRequestBuilder req : apiRequests(postId, "target01")) {
            Shape m = send(req, member);
            assertThat(m.status()).isEqualTo(404);
            assertThat(m.body()).isEqualTo(NOT_FOUND);
            assertThat(m.cacheControl()).isEqualTo("private, no-store");
        }
        for (String page : List.of("/admin/reports", "/admin/reports/1", "/admin/members/a")) {
            assertThat(send(get(page), null).status()).isEqualTo(401);
            assertThat(send(get(page), member).status()).isEqualTo(404);
        }

        assertThat(send(get("/api/admin/reports"), admin).status()).isEqualTo(200);
        assertThat(send(get("/api/admin/reports/{id}", caseId), admin).status()).isEqualTo(200);
        assertThat(send(get("/api/admin/members/target01"), admin).status()).isEqualTo(200);
        Shape hidden = send(apiRequests(postId, "target01").get(3), admin);
        assertThat(hidden.status()).isEqualTo(200);
        assertThat(hidden.cacheControl()).contains("no-store");
    }

    @Test
    void 경로_규칙을_건너뛰어도_서비스가_일반_회원을_404로_막는다() {
        long memberId = members().member().create();
        long author = members().member().handle("target02").create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = new ReportFixtures(jdbc).pendingPost(postId, author);
        Viewer member = new Viewer(memberId, Role.USER, MemberStatus.ACTIVE, true);

        assertThatThrownBy(() -> caseQueries.list(member, CaseQueryService.Tab.PENDING, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> caseQueries.detail(member, caseId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> resolution.resolve(member, caseId, "REJECT", null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> directHide.hide(member, ReportTargetType.POST, postId, "SPAM"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> directHide.unhide(member, ReportTargetType.POST, postId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> adminMembers.view(member, "target02"))
                .isInstanceOf(NotFoundException.class);
        assertThat(new ReportFixtures(jdbc).status(caseId)).isEqualTo("PENDING");
    }
}
