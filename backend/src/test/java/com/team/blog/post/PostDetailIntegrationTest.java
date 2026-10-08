package com.team.blog.post;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 글 상세 {@code GET /api/posts/{postId}} (005 T025, US2 #1·#3·#4, Q-7, SC-007). */
class PostDetailIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 비회원이_공개_재발행_글을_읽는다() throws Exception {
        long postId = fixture.postOf("A", "republished");

        MvcResult result = api().detail(null, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.status")).isEqualTo("PUBLISHED");
        assertThat((String) read(result, "$.visibility")).isEqualTo("PUBLIC");
        assertThat((String) read(result, "$.title")).isEqualTo("JPA N+1 정리");
        assertThat((String) read(result, "$.contentHtml"))
                .isEqualTo("<h2 id=\"n1\">N+1</h2><p>다시 발행한 본문</p>");
        assertThat((String) read(result, "$.canonicalPath"))
                .isEqualTo("/@kim755030/posts/" + postId);
        assertThat((Object) read(result, "$.editorPath")).isNull();
        assertThat((String) read(result, "$.displayedAt"))
                .isEqualTo(read(result, "$.firstPublicAt"));
        assertThat((String) read(result, "$.displayedAt")).isEqualTo("2026-09-28T10:00:00.000001Z");
        assertThat((String) read(result, "$.editedAt")).isEqualTo("2026-10-03T05:03:00Z");
        assertThat((Boolean) read(result, "$.hasCodeBlock")).isFalse();
        assertThat(((Number) read(result, "$.likeCount")).intValue()).isEqualTo(12);
        assertThat(((Number) read(result, "$.viewCount")).longValue()).isEqualTo(12345L);
        assertThat(((Number) read(result, "$.commentCount")).intValue()).isEqualTo(3);
        assertThat((String) read(result, "$.author.handle")).isEqualTo("kim755030");
        assertThat((String) read(result, "$.author.nickname")).isEqualTo("김민서");
        assertThat((String) read(result, "$.author.profileImageUrl"))
                .isEqualTo("http://localhost:9000/blog/" + PostReadingFixture.A_PROFILE_THUMB_KEY);
        assertThat((String) read(result, "$.author.bio")).contains("스프링 백엔드를 공부합니다.");
        assertThat((Boolean) read(result, "$.viewer.loggedIn")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.isAuthor")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.likedByMe")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.followingAuthor")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.emailVerified")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.isAdmin")).isFalse();
        assertThat((Object) read(result, "$.authorView")).isNull();
        assertThat(body(result)).doesNotContain("contentMd");
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
    }

    @Test
    void 태그는_입력_순서대로_나온다() throws Exception {
        MvcResult result = api().detail(null, fixture.postOf("A", "republished"));

        assertThat((List<String>) read(result, "$.tags")).containsExactly("spring", "jpa", "성능");
    }

    @Test
    void 태그가_없으면_빈_배열() throws Exception {
        MvcResult result = api().detail(null, fixture.postOf("A", "code"));

        assertThat((List<String>) read(result, "$.tags")).isEmpty();
    }

    @Test
    void 코드_블록_글만_hasCodeBlock이_true() throws Exception {
        assertThat(
                        (Boolean)
                                read(
                                        api().detail(null, fixture.postOf("A", "code")),
                                        "$.hasCodeBlock"))
                .isTrue();
        assertThat(
                        (Boolean)
                                read(
                                        api().detail(null, fixture.postOf("A", "thumb")),
                                        "$.hasCodeBlock"))
                .isFalse();
    }

    @Test
    void 공개_범위만_바꾼_글은_editedAt이_없다() throws Exception {
        MvcResult result = api().detail(null, fixture.postOf("A", "wentPublic"));

        assertThat((Object) read(result, "$.editedAt")).isNull();
        assertThat((String) read(result, "$.displayedAt")).isEqualTo("2026-09-19T10:00:00.654321Z");
        assertThat((String) read(result, "$.publishedAt")).isEqualTo("2026-09-01T10:00:00Z");
    }

    @Test
    void 독자는_수정_중인_글의_마지막_발행본을_본다() throws Exception {
        MvcResult result =
                api().detail(fixture.loginAs(mockMvc, "B"), fixture.postOf("A", "editing"));

        assertThat((String) read(result, "$.contentHtml")).isEqualTo("<p>마지막 발행본</p>");
        assertThat(body(result)).doesNotContain("고치는 중인");
        assertThat((Object) read(result, "$.authorView")).isNull();
        assertThat((Boolean) read(result, "$.viewer.loggedIn")).isTrue();
    }

    @Test
    void 상세를_세_번_읽어도_조회수는_그대로다() throws Exception {
        long postId = fixture.postOf("A", "republished");
        long before = fixture.viewCount(postId);

        for (int i = 0; i < 3; i++) {
            assertThat(status(api().detail(null, postId))).isEqualTo(200);
        }

        assertThat(fixture.viewCount(postId)).isEqualTo(before);
        assertThat(((Number) read(api().detail(null, postId), "$.viewCount")).longValue())
                .isEqualTo(before);
    }

    @Test
    void 비회원은_SQL_2번_이하_비작성자_회원은_5번_이하() throws Exception {
        long postId = fixture.postOf("A", "republished");

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(status(api().detail(null, postId))).isEqualTo(200);
            assertThat(scope.count()).as("비회원: 글+작성자, 태그").isLessThanOrEqualTo(2);
        }

        var session = fixture.loginAs(mockMvc, "B");
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(status(api().detail(session, postId))).isEqualTo(200);
            assertThat(scope.count())
                    // 010: 팔로우 여부가 실제 follow 행 조회(기본 키 1행)가 되어 좋아요(009)와 따로 1번씩 — 005 quickstart
                    // "쿼리 최대 5번"
                    .as("회원: 계정 상태(001 필터) + 글+작성자 + 태그 + 좋아요 + 팔로우")
                    .isLessThanOrEqualTo(5);
        }
    }
}
