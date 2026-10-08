package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.domain.CommentText;
import com.team.blog.interaction.support.CommentApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 댓글 XSS (007 T027, SC-005, US2 #8). 002 {@code ContentRendererXssTest}의 공격 문자열 자원(12 §9-1)을 댓글로
 * 등록하고, 응답이 정리한 입력과 글자 그대로 같으며 JSON으로만 나가는지 본다(댓글은 HTML로 바꾸지 않는다).
 */
class CommentXssIT extends IntegrationTestBase {

    @Test
    void 공격_문자열은_글자_그대로() throws Exception {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Resource[] samples =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:markdown/xss/*.md");
        assertThat(samples).hasSizeGreaterThanOrEqualTo(30);
        CommentApi api = new CommentApi(mockMvc);
        for (Resource sample : samples) {
            String raw = sample.getContentAsString(StandardCharsets.UTF_8);
            if (raw.codePointCount(0, raw.length()) > 1000) {
                raw = raw.substring(0, raw.offsetByCodePoints(0, 1000));
            }
            String expected = CommentText.normalize(raw);
            // 회원마다 1분 10개 제한이 있어 공격 문자열마다 새 회원으로 쓴다
            long writer = members().member().create();
            MvcResult result = api.create(TestLogin.loginAs(mockMvc, writer), postId, raw);

            assertThat(status(result)).as(sample.getFilename()).isEqualTo(201);
            assertThat(result.getResponse().getContentType())
                    .startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat((String) read(result, "$.content"))
                    .as(sample.getFilename())
                    .isEqualTo(expected);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT content FROM comment WHERE id = ?",
                                    String.class,
                                    CommentApi.id(result)))
                    .isEqualTo(expected);
        }
    }
}
