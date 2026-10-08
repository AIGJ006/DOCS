package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 없는 블로그·탈퇴 신청·익명 처리 회원의 블로그 API (005 T044, US3 #4, Q-6, FR-022). */
class BlogNotFoundIntegrationTest extends IntegrationTestBase {

    private static final String NOT_FOUND_BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    /**
     * 익명 처리된 회원 (001: {@code member.deleted_at}). 51의 {@code ck_member_deleted}·{@code
     * ck_member_withdrawn} 때문에 {@code status='WITHDRAWN'}·{@code withdrawn_at}도 함께 넣는다.
     */
    private String anonymizedHandle() {
        long id = fixture.memberId("B");
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now(),"
                        + " deleted_at = now(), nickname = NULL WHERE id = ?",
                id);
        return PostReadingFixture.B_HANDLE;
    }

    @Test
    void 없는_주소_탈퇴_신청_익명_처리_회원은_모두_같은_404() throws Exception {
        for (String handle :
                new String[] {"nobody_here", PostReadingFixture.C_HANDLE, anonymizedHandle()}) {
            MvcResult header = api().blogHeader(null, handle);

            assertThat(status(header)).as(handle).isEqualTo(404);
            assertThat(body(header)).as(handle).isEqualTo(NOT_FOUND_BODY);
            assertThat(cacheControl(header)).as(handle).isEqualTo("private, no-store");
        }
    }

    @Test
    void API는_대문자_주소를_리다이렉트하지_않고_404() throws Exception {
        MvcResult result = api().blogHeader(null, "Kim755030");

        assertThat(status(result)).isEqualTo(404);
        assertThat(body(result)).isEqualTo(NOT_FOUND_BODY);
    }

    @Test
    void 없는_주소의_목록도_404() throws Exception {
        MvcResult result = api().blogPosts(null, "nobody_here", null);

        assertThat(status(result)).isEqualTo(404);
        assertThat(body(result)).isEqualTo(NOT_FOUND_BODY);
    }

    @Test
    void 정지_회원의_블로그는_200() throws Exception {
        long suspended = fixture.suspendedMember();
        String handle =
                jdbc.queryForObject(
                        "SELECT handle FROM member WHERE id = ?", String.class, suspended);

        MvcResult header = api().blogHeader(null, handle);

        assertThat(status(header)).as(body(header)).isEqualTo(200);
        assertThat((String) read(header, "$.handle")).isEqualTo(handle);
    }
}
