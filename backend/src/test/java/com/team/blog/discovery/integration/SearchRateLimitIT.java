package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.read;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.SearchApi;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/** 검색 요청 제한 (012 T032, FR-037·FR-039, research R12). 글·사람 합쳐 같은 방문자 1분 30번. */
@ExtendWith(OutputCaptureExtension.class)
class SearchRateLimitIT extends IntegrationTestBase {

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    /** 30번(글·사람 섞어)은 통과, 31번째는 429. */
    private void assertLimited(SearchApi visitor) throws Exception {
        for (int i = 0; i < 30; i++) {
            MvcResult ok = i % 2 == 0 ? visitor.posts("트랜잭션") : visitor.people("김민서");
            assertThat(status(ok)).as("%d번째 %s", i + 1, body(ok)).isEqualTo(200);
        }
        MvcResult denied = visitor.posts("트랜잭션");
        assertThat(status(denied)).isEqualTo(429);
        assertThat((String) read(denied, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(denied.getResponse().getHeader("Retry-After")))
                .isBetween(1, 60);
        assertThat(status(visitor.people("김민서"))).isEqualTo(429);
    }

    @Test
    void 회원은_회원_기준() throws Exception {
        long member = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, member);
        assertLimited(api().as(session));
        // 같은 회원이 다른 IP·쿠키로 와도 같은 키
        assertThat(
                        status(
                                api().as(session, new Cookie("vid", UUID.randomUUID().toString()))
                                        .from("10.0.0.9", "Other")
                                        .posts("트랜잭션")))
                .isEqualTo(429);
        // 다른 방문자는 영향 없음
        assertThat(status(api().as(new Cookie("vid", UUID.randomUUID().toString())).posts("트랜잭션")))
                .isEqualTo(200);
    }

    @Test
    void 비회원은_vid_쿠키_기준() throws Exception {
        Cookie vid = new Cookie("vid", UUID.randomUUID().toString());
        assertLimited(api().as(vid).from("10.0.0.1", "UA-1"));
        assertThat(status(api().as(vid).from("10.0.0.2", "UA-2").posts("트랜잭션"))).isEqualTo(429);
    }

    @Test
    void 쿠키_없는_비회원은_IP와_UA_기준() throws Exception {
        assertLimited(api().from("10.0.0.3", "Mozilla/5.0 Test"));
        assertThat(status(api().from("10.0.0.3", "Mozilla/5.0 Other").posts("트랜잭션")))
                .isEqualTo(200);
    }

    @Test
    void 판정_순서는_429가_맨_끝() throws Exception {
        SearchApi visitor = api().as(new Cookie("vid", UUID.randomUUID().toString()));
        for (int i = 0; i < 30; i++) {
            visitor.posts("트랜잭션");
        }
        assertThat(status(visitor.posts("트랜잭션", null, null, "nobody_here"))).isEqualTo(404);
        assertThat(status(visitor.posts("a"))).isEqualTo(400);
        assertThat(status(visitor.posts("트랜잭션", null, "broken", null))).isEqualTo(400);
        assertThat(status(visitor.people("김"))).isEqualTo(400);
        assertThat(status(visitor.posts("트랜잭션"))).isEqualTo(429);
    }

    @Test
    void Redis_정지_중에는_통과() throws Exception {
        SearchApi visitor = api().as(new Cookie("vid", UUID.randomUUID().toString()));
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 32; i++) {
                assertThat(status(visitor.posts("트랜잭션"))).isEqualTo(200);
            }
        }
    }

    @Test
    void 로그와_Redis에_검색어_원문이_없다(CapturedOutput output) throws Exception {
        long author = members().member().create();
        new SearchFixtures(jdbc).post(author).title("비밀단어조사 글").create();
        Cookie vid = new Cookie("vid", UUID.randomUUID().toString());

        assertThat(status(api().as(vid).posts("비밀단어조사"))).isEqualTo(200);
        assertThat(status(api().as(vid).people("비밀사람이름"))).isEqualTo(200);

        assertThat(output.getAll()).contains("search posts len=6 words=1");
        assertThat(output.getAll()).contains("search people len=6");
        assertThat(output.getAll()).doesNotContain("비밀단어조사").doesNotContain("비밀사람이름");
        Set<String> keys = redis.keys("*");
        assertThat(keys).anyMatch(k -> k.startsWith("ratelimit:search:v:"));
        assertThat(String.join(" ", keys)).doesNotContain("비밀").doesNotContain(vid.getValue());
    }
}
