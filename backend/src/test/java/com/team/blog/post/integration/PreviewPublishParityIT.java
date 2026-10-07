package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MvcResult;

/** 미리보기 = 발행 결과 (002 T057, SC-004, US2 #6). 정상 문법 13개 + 작성자 사진 + 외부 사진. */
class PreviewPublishParityIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final String OWNED = "owned-image";

    static Stream<Arguments> bodies() throws IOException {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:markdown/syntax/*.md");
        List<Arguments> args = new ArrayList<>();
        Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename))
                .forEach(
                        r -> {
                            try {
                                args.add(
                                        Arguments.of(
                                                r.getFilename(),
                                                r.getContentAsString(StandardCharsets.UTF_8)));
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
        args.add(Arguments.of(OWNED, "# 사진\n\n![내 사진](" + BASE + "/" + KEY + ")"));
        args.add(Arguments.of("external-image", "![밖](https://evil.example/a.png)"));
        return args.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodies")
    void 미리보기_HTML과_발행_HTML이_바이트_단위로_같다(String name, String markdown) throws Exception {
        long me = members().member().create();
        if (OWNED.equals(name)) {
            jdbc.update(
                    "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes)"
                            + " VALUES (?, ?, 'image/webp', 1000)",
                    me,
                    KEY);
        }
        Cookie session = TestLogin.loginAs(mockMvc, me);
        EditorApi api = new EditorApi(mockMvc);

        MvcResult preview = api.preview(session, markdown);
        assertThat(status(preview)).as(body(preview)).isEqualTo(200);
        String previewHtml = read(preview, "$.html");

        long postId = api.createPostId(session);
        MvcResult published =
                api.publish(session, postId, publishBody("제목", markdown, List.of(), "PUBLIC", 0));
        assertThat(status(published)).as(body(published)).isEqualTo(200);

        String stored =
                jdbc.queryForObject(
                        "SELECT content_html FROM post WHERE id = ?", String.class, postId);
        assertThat(stored.getBytes(StandardCharsets.UTF_8))
                .isEqualTo(previewHtml.getBytes(StandardCharsets.UTF_8));
        if (OWNED.equals(name)) {
            assertThat(stored).contains("<img");
        }
    }
}
