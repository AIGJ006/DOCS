package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.nextCursor;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static com.team.blog.discovery.support.ReadingApi.titles;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 개인 블로그 머리말·목록 (005 T043, US3 #1~#5, Q-5, SC-008). */
class BlogPageIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 머리말은_누가_봐도_공개_글_12개이고_isMe만_다르다() throws Exception {
        Cookie author = fixture.loginAs(mockMvc, "A");
        Cookie other = fixture.loginAs(mockMvc, "B");

        for (Cookie session : new Cookie[] {null, other, author}) {
            MvcResult result = api().blogHeader(session, PostReadingFixture.A_HANDLE);

            assertThat(status(result)).as(body(result)).isEqualTo(200);
            assertThat((String) read(result, "$.handle")).isEqualTo("kim755030");
            assertThat((String) read(result, "$.nickname")).isEqualTo("김민서");
            assertThat((String) read(result, "$.bio")).isEqualTo("스프링 백엔드를 공부합니다.\n하루에 한 글씩 씁니다.");
            assertThat((String) read(result, "$.profileImageUrl"))
                    .isEqualTo(
                            "http://localhost:9000/blog/" + PostReadingFixture.A_PROFILE_THUMB_KEY);
            assertThat(((Number) read(result, "$.publicPostCount")).intValue()).isEqualTo(12);
            assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        }

        assertThat((Boolean) read(api().blogHeader(author, PostReadingFixture.A_HANDLE), "$.isMe"))
                .isTrue();
        assertThat((Boolean) read(api().blogHeader(other, PostReadingFixture.A_HANDLE), "$.isMe"))
                .isFalse();
        assertThat((Boolean) read(api().blogHeader(null, PostReadingFixture.A_HANDLE), "$.isMe"))
                .isFalse();
    }

    @Test
    void 작성자_본인이_봐도_공개_글만_9개_다음_3개() throws Exception {
        Cookie author = fixture.loginAs(mockMvc, "A");

        MvcResult first = api().blogPosts(author, PostReadingFixture.A_HANDLE, null);
        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(titles(first))
                .containsExactlyElementsOf(PostReadingFixture.A_BLOG_TITLES.subList(0, 9));
        assertThat(nextCursor(first)).isNotBlank();
        assertThat(cacheControl(first)).isEqualTo("private, no-cache");

        MvcResult second = api().blogPosts(author, PostReadingFixture.A_HANDLE, nextCursor(first));
        assertThat(titles(second))
                .containsExactlyElementsOf(PostReadingFixture.A_BLOG_TITLES.subList(9, 12));
        assertThat(nextCursor(second)).isNull();

        // 비공개·임시·휴지통·숨김 글은 한 건도 없다
        assertThat(titles(first))
                .doesNotContain("비공개 글 1", "비공개 글 2", "비공개 글 3", "임시글", "휴지통 글", "숨겨진 글");
        assertThat(titles(second))
                .doesNotContain("비공개 글 1", "비공개 글 2", "비공개 글 3", "임시글", "휴지통 글", "숨겨진 글");
        // 남의 글도 없다
        assertThat(titles(first)).noneMatch(t -> t.startsWith("B"));
    }

    @Test
    void 글이_없는_블로그는_0개_빈_목록() throws Exception {
        MvcResult header = api().blogHeader(null, PostReadingFixture.D_HANDLE);
        assertThat(((Number) read(header, "$.publicPostCount")).intValue()).isZero();
        assertThat((Object) read(header, "$.bio")).isNull();
        assertThat((Object) read(header, "$.profileImageUrl")).isNull();

        MvcResult posts = api().blogPosts(null, PostReadingFixture.D_HANDLE, null);
        assertThat(titles(posts)).isEmpty();
        assertThat(nextCursor(posts)).isNull();
    }

    @Test
    void 다른_목록에서_온_커서는_400() throws Exception {
        String blogCursor = nextCursor(api().blogPosts(null, PostReadingFixture.A_HANDLE, null));
        String homeCursor = nextCursor(api().home(null, null));
        assertThat(blogCursor).isNotBlank();
        assertThat(homeCursor).isNotBlank();

        MvcResult blogToHome = api().home(null, blogCursor);
        MvcResult homeToBlog = api().blogPosts(null, PostReadingFixture.A_HANDLE, homeCursor);
        // 회원 B 블로그는 공개 글 8개로 커서가 없다 — A 블로그의 커서를 B 블로그에 보낸다
        MvcResult otherToBlog = api().blogPosts(null, PostReadingFixture.B_HANDLE, blogCursor);

        for (MvcResult result : new MvcResult[] {blogToHome, homeToBlog, otherToBlog}) {
            assertThat(status(result)).as(body(result)).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
    }

    @Test
    void 목록_요청_한_번에_SQL은_주인_1번_카드_1번() throws Exception {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            MvcResult result = api().blogPosts(null, PostReadingFixture.A_HANDLE, null);
            assertThat(status(result)).isEqualTo(200);
            assertThat(scope.count()).isEqualTo(2);
        }
    }
}
