package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * "휴지통 글" 목록 누출 확인 (006 T019 ②, US1-1, FR-021·039, SC-001, 42 §5-1). 상세 판정 행 자체는 004 {@code
 * post-read.csv}의 {@code TRASHED} 행이 다루므로 여기서는 지운 뒤 목록·글 수·다른 기능 진입점에서 빠지는지를 행위자별로 본다.
 *
 * <p>태그·검색·sitemap·댓글·좋아요(008·012·005 sitemap·007·009)는 아직 없어 {@link Assumptions}로 건너뛴다(quickstart
 * §0).
 */
class TrashedPostPermissionMatrixIT extends IntegrationTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private long author;
    private String handle;
    private Cookie authorSession;
    private final Map<String, Cookie> viewers = new LinkedHashMap<>();
    private final List<Long> trashed = new ArrayList<>();
    private long remaining;
    private long publicCountBefore;

    private ReadingApi reading() {
        return new ReadingApi(mockMvc);
    }

    @BeforeEach
    void trashThreePosts() throws Exception {
        PostFixtures posts = new PostFixtures(jdbc);
        author = members().member().create();
        handle = new TrashFixtures(jdbc).handleOf(author);
        authorSession = TestLogin.loginAs(mockMvc, author);
        long publicPost = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long privatePost = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        long draft = posts.post(author).title("임시글").contentMd("본문").create();
        remaining = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        publicCountBefore = publicPostCount(null);

        TrashApi api = new TrashApi(mockMvc);
        for (long id : new long[] {publicPost, privatePost, draft}) {
            assertThat(status(api.trash(authorSession, id))).isEqualTo(200);
            trashed.add(id);
        }

        viewers.clear();
        viewers.put("비회원", null);
        viewers.put("다른 회원", TestLogin.loginAs(mockMvc, members().member().create()));
        viewers.put("작성자", authorSession);
        viewers.put("관리자", TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create()));
    }

    @Test
    void 상세는_모두에게_404() throws Exception {
        for (Map.Entry<String, Cookie> viewer : viewers.entrySet()) {
            for (long id : trashed) {
                MvcResult result = reading().detail(viewer.getValue(), id);
                assertThat(status(result)).as(viewer.getKey() + " 상세 " + id).isEqualTo(404);
                assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
            }
        }
    }

    @Test
    void 홈_목록에_없다() throws Exception {
        for (Map.Entry<String, Cookie> viewer : viewers.entrySet()) {
            List<Integer> ids = read(reading().home(viewer.getValue(), null), "$.items[*].id");
            assertThat(ids).as(viewer.getKey() + " 홈").doesNotContainAnyElementsOf(asInts(trashed));
            assertThat(ids).as(viewer.getKey() + " 홈").contains((int) remaining);
        }
    }

    @Test
    void 블로그_목록에_없고_글_수가_줄었다() throws Exception {
        for (Map.Entry<String, Cookie> viewer : viewers.entrySet()) {
            List<Integer> ids =
                    read(reading().blogPosts(viewer.getValue(), handle, null), "$.items[*].id");
            assertThat(ids)
                    .as(viewer.getKey() + " 블로그")
                    .doesNotContainAnyElementsOf(asInts(trashed))
                    .contains((int) remaining);
            assertThat(publicPostCount(viewer.getValue()))
                    .as(viewer.getKey() + " 블로그 글 수")
                    .isEqualTo(publicCountBefore - 1);
        }
    }

    @Test
    @Disabled("US3 관리 목록(GET /api/me/posts) 구현 뒤 켠다")
    void 작성자에게는_관리_목록의_휴지통_탭에만_보인다() throws Exception {
        TrashApi api = new TrashApi(mockMvc);
        List<Integer> trash = read(api.list(authorSession, "tab=trash"), "$.items[*].id");
        List<Integer> drafts = read(api.list(authorSession, "tab=drafts"), "$.items[*].id");
        List<Integer> published = read(api.list(authorSession, "tab=published"), "$.items[*].id");

        assertThat(trash).containsExactlyInAnyOrderElementsOf(asInts(trashed));
        assertThat(drafts).doesNotContainAnyElementsOf(asInts(trashed));
        assertThat(published)
                .doesNotContainAnyElementsOf(asInts(trashed))
                .contains((int) remaining);
    }

    @Test
    void 태그_목록에_없다() {
        assumeHandler("GET", "/api/tags/spring/posts", "008 태그별 글 목록");
    }

    @Test
    void 검색에_없다() {
        assumeHandler("GET", "/api/search", "012 검색");
    }

    @Test
    void sitemap에_없다() {
        assumeHandler("GET", "/sitemap.xml", "005 sitemap");
    }

    @Test
    void 댓글_목록은_404() {
        assumeHandler("GET", "/api/posts/1/comments", "007 댓글");
    }

    @Test
    void 좋아요는_404() {
        assumeHandler("PUT", "/api/posts/1/like", "009 좋아요");
    }

    private long publicPostCount(Cookie session) throws Exception {
        Number count = read(reading().blogHeader(session, handle), "$.publicPostCount");
        return count.longValue();
    }

    private void assumeHandler(String method, String path, String feature) {
        boolean exists;
        try {
            exists = handlerMapping.getHandler(new MockHttpServletRequest(method, path)) != null;
        } catch (Exception e) {
            exists = false;
        }
        Assumptions.assumeTrue(exists, feature + " 기능이 아직 없다");
        // 기능이 생기면 이 자리에 휴지통 글 미포함·404 단언을 더한다 (그 기능의 tasks 몫)
        throw new AssertionError(feature + " 기능이 생겼다 — 휴지통 글 미포함 단언을 더해야 한다");
    }

    private static List<Integer> asInts(List<Long> ids) {
        return ids.stream().map(Long::intValue).toList();
    }
}
