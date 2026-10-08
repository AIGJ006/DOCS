package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.post.support.VisibilityApi;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 404 동일성 (004 T041, FR-013·FR-014, SC-002, research R-26·R-30). 남의 비공개·임시·휴지통·숨김·탈퇴 유예 작성자 글과 없는
 * 번호의 응답이 상태·본문 바이트·{@code Content-Type}·{@code Cache-Control}까지 같아 이유를 알 수 없음을 확인한다.
 *
 * <p>상세 판정은 테스트 전용 {@code ReadProbeController}({@code GET /api/__test/posts/{postId}} → {@code
 * PostReadService.requireReadable})로 부른다. 005 상세 API·화면 비교는 Polish T073·T074가 더한다.
 */
class NotFoundIndistinguishableIT extends IntegrationTestBase {

    private static final List<State> UNSEEN =
            List.of(
                    State.PUBLISHED_PRIVATE,
                    State.DRAFT,
                    State.TRASHED,
                    State.HIDDEN,
                    State.AUTHOR_WITHDRAWN);

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    private PostFixtures posts;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
    }

    /** 비교할 응답 부분. {@code Date} 등 매번 다른 헤더는 뺀다. */
    private record Shape(int status, String body, String contentType, String cacheControl) {
        static Shape of(MvcResult result) {
            var response = result.getResponse();
            return new Shape(
                    response.getStatus(),
                    VisibilityApi.body(result),
                    response.getContentType(),
                    response.getHeader("Cache-Control"));
        }
    }

    private Shape read(long postId, Cookie session) throws Exception {
        var request = get("/api/__test/posts/{postId}", postId);
        if (session != null) {
            request.cookie(session);
        }
        return Shape.of(mockMvc.perform(request).andReturn());
    }

    /** 상태마다 다른 작성자의 글을 하나씩 (탈퇴 유예는 작성자 전체를 바꾸므로). */
    private Map<State, Long> unseenPosts() {
        Map<State, Long> ids = new LinkedHashMap<>();
        for (State state : UNSEEN) {
            ids.put(state, posts.create(members().member().create(), state));
        }
        return ids;
    }

    @Test
    void 볼_수_없는_글과_없는_번호의_API_404가_바이트까지_같다() throws Exception {
        Map<State, Long> unseen = unseenPosts();
        long missing = posts.nonexistentId();
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());

        for (Cookie session : Arrays.asList(null, member, admin)) {
            Shape expected = read(missing, session);
            assertThat(expected.status()).isEqualTo(404);
            assertThat(expected.body())
                    .isEqualTo(
                            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\","
                                    + "\"errors\":[],\"details\":null}");
            assertThat(expected.cacheControl()).isEqualTo("private, no-store");
            assertThat(expected.contentType()).startsWith("application/json");
            for (Map.Entry<State, Long> e : unseen.entrySet()) {
                assertThat(read(e.getValue(), session))
                        .as(e.getKey() + " (" + (session == null ? "비회원" : "회원") + ")")
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void 남의_글과_없는_글의_공개_범위_변경_응답이_같다() throws Exception {
        Map<State, Long> unseen = unseenPosts();
        long othersPublic = posts.create(members().member().create(), State.PUBLISHED_PUBLIC);
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long myTrashed = posts.create(me, State.TRASHED);
        VisibilityApi api = new VisibilityApi(mockMvc);

        Shape expected = Shape.of(api.change(session, posts.nonexistentId(), "PRIVATE"));
        assertThat(expected.status()).isEqualTo(404);
        assertThat(expected.cacheControl()).isEqualTo("private, no-store");

        Map<String, Long> targets = new LinkedHashMap<>();
        unseen.forEach((state, id) -> targets.put("남의 " + state, id));
        targets.put("남의 공개 글", othersPublic);
        targets.put("내 휴지통 글", myTrashed);
        for (Map.Entry<String, Long> e : targets.entrySet()) {
            assertThat(Shape.of(api.change(session, e.getValue(), "PRIVATE")))
                    .as(e.getKey())
                    .isEqualTo(expected);
        }
    }

    @Test
    void 공통_404_화면은_요청과_무관하게_같다() {
        ResponseEntity<byte[]> first = notFoundPageRenderer.render();
        ResponseEntity<byte[]> second = notFoundPageRenderer.render();

        assertThat(first.getStatusCode().value()).isEqualTo(404);
        assertThat(second.getStatusCode()).isEqualTo(first.getStatusCode());
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(second.getHeaders().getCacheControl())
                .isEqualTo(first.getHeaders().getCacheControl())
                .isEqualTo("private, no-store");
        assertThat(second.getHeaders().getContentType())
                .isEqualTo(first.getHeaders().getContentType());
    }
}
