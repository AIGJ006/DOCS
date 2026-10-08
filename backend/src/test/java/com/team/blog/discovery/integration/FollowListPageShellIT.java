package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.bytes;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 팔로워·팔로잉 목록 주소의 첫 응답 {@code GET /@{handle}/followers|following} (010 T032, research R9). */
class FollowListPageShellIT extends IntegrationTestBase {

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    @BeforeEach
    void setUp() {
        members().member().handle("shell_b").nickname("셸주인").create();
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 대문자_주소는_소문자로_301하고_쿼리를_유지한다() throws Exception {
        for (String tail : new String[] {"followers", "following"}) {
            MvcResult result = api().getAs(null, "/@Shell_B/" + tail + "?x=1");
            assertThat(status(result)).as(tail).isEqualTo(301);
            assertThat(result.getResponse().getHeader("Location"))
                    .isEqualTo("/@shell_b/" + tail + "?x=1");
            assertThat(status(api().getAs(null, "/@NOBODY/" + tail))).isEqualTo(301);
        }
    }

    @Test
    void 없는_주소_유예_익명_처리는_공통_404_화면() throws Exception {
        long withdrawn = members().member().handle("gone_b").create();
        new FollowFixtures(jdbc).withdraw(withdrawn);
        members().member().handle("anon_b").status("WITHDRAWN").deleted().create();

        for (String handle : new String[] {"nobody_here", "gone_b", "anon_b"}) {
            for (String tail : new String[] {"followers", "following"}) {
                MvcResult result = api().getAs(null, "/@" + handle + "/" + tail);
                assertThat(status(result)).as(handle + tail).isEqualTo(404);
                assertThat(bytes(result)).isEqualTo(notFoundPageRenderer.render().getBody());
                assertThat(cacheControl(result)).isEqualTo("private, no-store");
            }
        }
    }

    @Test
    void 정상이면_200_SPA_셸() throws Exception {
        for (String tail : new String[] {"followers", "following"}) {
            MvcResult result = api().getAs(null, "/@shell_b/" + tail);
            assertThat(status(result)).as(body(result)).isEqualTo(200);
            assertThat(result.getResponse().getContentType()).startsWith("text/html");
            assertThat(body(result)).contains("<title>셸주인 (@shell_b)</title>");
            assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        }
    }
}
