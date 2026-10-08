package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.ids;
import static com.team.blog.discovery.support.ReadingApi.nextCursor;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 홈 목록은 누가 봐도 같다 (005 T020, 06 V-8, 42 §5-1, 004 FR-009). */
class HomeListActorIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    private List<Long> allIds(Cookie session) throws Exception {
        List<Long> all = new ArrayList<>();
        String cursor = null;
        do {
            MvcResult page = api().home(session, cursor);
            assertThat(status(page)).isEqualTo(200);
            all.addAll(ids(page));
            cursor = nextCursor(page);
        } while (cursor != null);
        return all;
    }

    @Test
    void 비회원_인증전_회원_작성자_관리자_정지회원이_모두_같은_순서를_본다() throws Exception {
        List<Long> expected = fixture.idsOf(PostReadingFixture.HOME_TITLES);

        Cookie author = fixture.loginAs(mockMvc, "A");
        Cookie other = fixture.loginAs(mockMvc, "B");
        Cookie admin = fixture.loginAs(mockMvc, "ADMIN");
        Cookie unverified = TestLogin.loginAs(mockMvc, fixture.unverifiedMember());
        Cookie suspended = TestLogin.loginAs(mockMvc, fixture.suspendedMember());

        assertThat(allIds(null)).as("비회원").containsExactlyElementsOf(expected);
        assertThat(allIds(unverified)).as("인증 전 회원").containsExactlyElementsOf(expected);
        assertThat(allIds(other)).as("회원 B").containsExactlyElementsOf(expected);
        assertThat(allIds(author)).as("작성자 A").containsExactlyElementsOf(expected);
        assertThat(allIds(admin)).as("관리자").containsExactlyElementsOf(expected);
        assertThat(allIds(suspended)).as("정지 회원").containsExactlyElementsOf(expected);
    }

    @Test
    void 작성자에게도_자기_비공개_임시_휴지통_숨김_글이_없다() throws Exception {
        List<Long> mine =
                List.of(
                        fixture.postOf("A", "private1"),
                        fixture.postOf("A", "private2"),
                        fixture.postOf("A", "private3"),
                        fixture.postOf("A", "draft"),
                        fixture.postOf("A", "trashed"),
                        fixture.postOf("A", "hidden"));

        assertThat(allIds(fixture.loginAs(mockMvc, "A"))).doesNotContainAnyElementsOf(mine);
    }

    @Test
    void 관리자에게도_남의_숨김_비공개_글이_없다() throws Exception {
        assertThat(allIds(fixture.loginAs(mockMvc, "ADMIN")))
                .doesNotContain(fixture.postOf("A", "hidden"), fixture.postOf("A", "private1"));
    }

    @Test
    void 탈퇴_신청_작성자의_글은_누구에게도_없다() throws Exception {
        long c1 = fixture.postOf("C", "c1");
        long c2 = fixture.postOf("C", "c2");

        assertThat(allIds(null)).doesNotContain(c1, c2);
        assertThat(allIds(fixture.loginAs(mockMvc, "ADMIN"))).doesNotContain(c1, c2);
    }
}
