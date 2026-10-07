package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.TitleNormalizer;
import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.markdown.HtmlSafetyChecker;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MvcResult;

/** 발행 경로의 정화 (002 T058, US2 #1·#2·#5·#7, docs/12 §9-1, SC-009). */
class PublishSanitizeIT extends IntegrationTestBase {

    static Stream<Arguments> corpus() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:markdown/xss/*.md");
        return Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(
                        r -> {
                            try {
                                return Arguments.of(
                                        r.getFilename(),
                                        r.getContentAsString(StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
    }

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private String column(String column, long postId) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM post WHERE id = ?", String.class, postId);
    }

    @ParameterizedTest(name = "본문 {0}")
    @MethodSource("corpus")
    void 공격_문자열_본문을_발행해도_실행_가능한_스크립트가_없다(String name, String attack) throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult result =
                api().publish(session, postId, publishBody("제목", attack, List.of(), "PUBLIC", 0));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        String html = column("content_html", postId);
        assertThat(HtmlSafetyChecker.problems(html)).as(name + " → " + html).isEmpty();
        assertThat(column("content_md", postId)).isEqualTo(attack);
    }

    @ParameterizedTest(name = "제목 {0}")
    @MethodSource("corpus")
    void 공격_문자열_제목은_정리만_하고_글자_그대로_저장한다(String name, String attack) throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String title = within100(attack);

        MvcResult result =
                api().publish(session, postId, publishBody(title, "본문", List.of(), "PUBLIC", 0));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(column("title", postId)).isEqualTo(TitleNormalizer.normalize(title));
    }

    /** 정리 후 100자를 넘으면 앞 100자만 (제목 길이 규칙은 별도 테스트). */
    private static String within100(String raw) {
        String normalized = TitleNormalizer.normalize(raw);
        if (normalized.codePointCount(0, normalized.length()) <= 100) {
            return raw;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, 100));
    }

    @Test
    void 인용_25단계는_400_CONTENT_TOO_COMPLEX이고_임시글_유지_15단계는_200() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        MvcResult deep =
                api().publish(
                                session,
                                postId,
                                publishBody("제목", ">".repeat(25) + " 깊다", List.of(), "PUBLIC", 0));
        assertThat(status(deep)).isEqualTo(400);
        assertThat((String) read(deep, "$.errors[0].code")).isEqualTo("CONTENT_TOO_COMPLEX");
        assertThat(column("status", postId)).isEqualTo("DRAFT");

        MvcResult ok =
                api().publish(
                                session,
                                postId,
                                publishBody("제목", ">".repeat(15) + " 얕다", List.of(), "PUBLIC", 0));
        assertThat(status(ok)).as(body(ok)).isEqualTo(200);
    }

    @Test
    void 본문_100000자_발행은_1초_안에_끝난다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        // 데우기: 첫 렌더링의 클래스 로딩 시간을 빼고 잰다
        api().publish(
                        session,
                        api().createPostId(session),
                        publishBody("데우기", "본문 **굵게**", List.of(), "PUBLIC", 0));

        StringBuilder md = new StringBuilder();
        String paragraph = "## 소제목\n\n" + "가나다라 **굵게** `코드` [링크](https://spring.io) ".repeat(20);
        while (md.length() + paragraph.length() + 2 <= 100_000) {
            md.append(paragraph).append("\n\n");
        }
        while (md.length() < 100_000) {
            md.append('가');
        }
        assertThat(md.length()).isEqualTo(100_000);
        long postId = api().createPostId(session);

        long start = System.nanoTime();
        MvcResult result =
                api().publish(
                                session,
                                postId,
                                publishBody("긴 글", md.toString(), List.of(), "PUBLIC", 0));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void 제목_표_체크리스트_코드_블록은_기대_HTML이고_h1은_없다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (String fixture :
                List.of(
                        "01-heading-h2",
                        "02-heading-h6",
                        "05-table-align",
                        "06-checklist",
                        "07-code-block")) {
            String md = resource("markdown/syntax/" + fixture + ".md");
            String expected = resource("markdown/syntax/" + fixture + ".html");
            long postId = api().createPostId(session);

            MvcResult result =
                    api().publish(session, postId, publishBody("제목", md, List.of(), "PUBLIC", 0));

            assertThat(status(result)).as(fixture + " " + body(result)).isEqualTo(200);
            String html = column("content_html", postId);
            assertThat(html.strip()).as(fixture).isEqualTo(expected.strip());
            assertThat(html).doesNotContain("<h1");
        }
    }

    private static String resource(String path) throws IOException {
        return new PathMatchingResourcePatternResolver()
                .getResource("classpath:" + path)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
