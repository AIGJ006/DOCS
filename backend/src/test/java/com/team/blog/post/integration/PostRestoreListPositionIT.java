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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 복구 뒤 공개 목록 위치 (006 T027, US2-1, 13 D-4, SC-002). 홈·블로그는 {@code first_public_at} 순이다(005). */
class PostRestoreListPositionIT extends IntegrationTestBase {

    @Test
    void US2_1_복구하면_홈과_블로그의_순서와_글_수가_삭제_전과_같다() throws Exception {
        long me = members().member().create();
        String handle = new TrashFixtures(jdbc).handleOf(me);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        PostFixtures posts = new PostFixtures(jdbc);
        Instant base = Instant.parse("2026-09-20T00:00:00Z");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Instant at = base.plusSeconds(3600L * i);
            ids.add(posts.post(me).published("PUBLIC").firstPublicAt(at).updatedAt(at).create());
        }
        ReadingApi reading = new ReadingApi(mockMvc);
        List<Integer> homeBefore = read(reading.home(null, null), "$.items[*].id");
        List<Integer> blogBefore = read(reading.blogPosts(null, handle, null), "$.items[*].id");
        Number countBefore = read(reading.blogHeader(null, handle), "$.publicPostCount");
        long third = ids.get(2);

        TrashApi api = new TrashApi(mockMvc);
        assertThat(status(api.trash(session, third))).isEqualTo(200);
        List<Integer> homeTrashed = read(reading.home(null, null), "$.items[*].id");
        assertThat(homeTrashed).doesNotContain((int) third);
        assertThat(status(api.restore(session, third))).isEqualTo(200);

        assertThat((List<Integer>) read(reading.home(null, null), "$.items[*].id"))
                .isEqualTo(homeBefore);
        assertThat((List<Integer>) read(reading.blogPosts(null, handle, null), "$.items[*].id"))
                .isEqualTo(blogBefore);
        assertThat(
                        ((Number) read(reading.blogHeader(null, handle), "$.publicPostCount"))
                                .longValue())
                .isEqualTo(countBefore.longValue());
        assertThat(homeBefore).hasSize(5);
    }
}
