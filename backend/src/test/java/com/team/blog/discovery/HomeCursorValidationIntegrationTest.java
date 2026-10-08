package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.nextCursor;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 잘못된 커서 → 400 {@code INVALID_CURSOR} (005 T019, US1 #6, Q-4, FR-006). */
class HomeCursorValidationIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void expectInvalidCursor(MvcResult result) {
        assertThat(status(result)).as(body(result)).isEqualTo(400);
        assertThat(body(result))
                .isEqualTo(
                        "{\"code\":\"INVALID_CURSOR\",\"message\":\"목록을 처음부터 다시 불러와 주세요\","
                                + "\"errors\":[],\"details\":null}");
    }

    @Test
    void 풀리지_않는_값() throws Exception {
        expectInvalidCursor(api().home(null, "abc%%"));
        expectInvalidCursor(api().home(null, "!!!"));
    }

    @Test
    void 모르는_형식_버전() throws Exception {
        expectInvalidCursor(api().home(null, b64("{\"v\":2,\"l\":\"home\",\"k\":[1,1]}")));
    }

    @Test
    void 키가_빠졌거나_타입이_틀림() throws Exception {
        expectInvalidCursor(api().home(null, b64("{\"v\":1,\"l\":\"home\"}")));
        expectInvalidCursor(api().home(null, b64("{\"v\":1,\"l\":\"home\",\"k\":[\"a\",\"b\"]}")));
        expectInvalidCursor(api().home(null, b64("{\"v\":1,\"l\":\"home\",\"k\":[1]}")));
    }

    @Test
    void 블로그_목록에서_받은_커서를_홈에_보내면_거부한다() throws Exception {
        MvcResult blog = api().blogPosts(null, PostReadingFixture.A_HANDLE, null);
        String blogCursor = nextCursor(blog);
        assertThat(blogCursor).isNotBlank();

        expectInvalidCursor(api().home(null, blogCursor));
    }

    @Test
    void 홈_커서를_블로그_목록에_보내면_거부한다() throws Exception {
        String homeCursor = nextCursor(api().home(null, null));

        expectInvalidCursor(api().blogPosts(null, PostReadingFixture.A_HANDLE, homeCursor));
    }

    @Test
    void 다른_사람_블로그의_커서도_거부한다() throws Exception {
        // 회원 B 블로그는 공개 글 8개로 커서가 없으므로 A 블로그의 커서를 B 블로그에 보낸다
        String aCursor = nextCursor(api().blogPosts(null, PostReadingFixture.A_HANDLE, null));
        assertThat(aCursor).isNotBlank();

        expectInvalidCursor(api().blogPosts(null, PostReadingFixture.B_HANDLE, aCursor));
    }

    @Test
    void 커서가_비어_있으면_첫_페이지로_본다() throws Exception {
        MvcResult result = api().home(null, "");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(ReadingApi.titles(result)).hasSize(9);
        assertThat(fixture.memberId("A")).isPositive();
    }
}
