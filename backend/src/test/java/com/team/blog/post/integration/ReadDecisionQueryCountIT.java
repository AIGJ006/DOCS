package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 판정 쿼리 수 (004 T076, plan Performance Goals). 상세 판정 {@code requireReadable} 1번 = SQL 1번(글 + 작성자
 * JOIN), 블로그 글 수 1번 = SQL 1번, 인증 요청의 {@code CurrentViewerResolver}는 PK 조회 1번(001 탈퇴 게이트의 조회를
 * 재사용)이다.
 */
class ReadDecisionQueryCountIT extends IntegrationTestBase {

    @Autowired private PostReadService postReadService;
    @Autowired private PostQueryRepository postQueryRepository;

    @Test
    void requireReadable_한_번은_SQL_한_번() {
        PostFixtures posts = new PostFixtures(jdbc);
        long author = members().member().create();
        long visible = posts.create(author, State.PUBLISHED_PUBLIC);
        long hidden = posts.create(author, State.PUBLISHED_PRIVATE);
        long missing = posts.nonexistentId();

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            postReadService.requireReadable(visible, Viewer.anonymous());
            assertThat(scope.count()).as("볼 수 있는 글").isEqualTo(1);
        }
        for (long postId : new long[] {hidden, missing}) {
            try (SqlCounter.Scope scope = SqlCounter.start()) {
                assertThatThrownBy(
                                () -> postReadService.requireReadable(postId, Viewer.anonymous()))
                        .isInstanceOf(PostNotFoundException.class);
                assertThat(scope.count()).as("404도 같은 수 " + postId).isEqualTo(1);
            }
        }
    }

    @Test
    void 블로그_글_수는_SQL_한_번() {
        long author = members().member().create();
        new PostFixtures(jdbc).create(author, State.PUBLISHED_PUBLIC);

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(postQueryRepository.countListedByAuthor(Viewer.anonymous(), author))
                    .isEqualTo(1);
            assertThat(scope.count()).isEqualTo(1);
        }
    }

    @Test
    void 인증_요청의_상세_판정은_회원_조회_한_번과_판정_한_번() throws Exception {
        PostFixtures posts = new PostFixtures(jdbc);
        long author = members().member().create();
        long postId = posts.create(author, State.PUBLISHED_PRIVATE);
        Cookie session = TestLogin.loginAs(mockMvc, author);

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            int status =
                    mockMvc.perform(get("/api/__test/posts/{postId}", postId).cookie(session))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            assertThat(status).isEqualTo(200);
            assertThat(scope.count()).as("회원 PK 조회 1 + 글 판정 1").isEqualTo(2);
        }
    }
}
