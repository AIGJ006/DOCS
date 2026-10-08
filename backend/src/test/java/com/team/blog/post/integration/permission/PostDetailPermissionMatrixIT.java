package com.team.blog.post.integration.permission;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.bytes;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.discovery.support.ReadingPermissionActions;
import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import com.team.blog.support.permission.Actor;
import com.team.blog.support.permission.TargetState;
import jakarta.servlet.http.Cookie;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 005 읽기 권한 매트릭스 러너 (T028, US2 #2, Q-8, SC-009). 004 {@code post-read.csv}에서 {@code owner=005} 행만
 * 실행한다({@code read-detail-api}·{@code read-detail-page}).
 *
 * <p>404는 이유를 구분하지 않는다 — API 본문은 공통 JSON과, 화면 본문은 004 {@link NotFoundPageRenderer} 출력과 바이트 단위로 같고
 * 모두 {@code Cache-Control: private, no-store}다(research R-17, FR-042).
 *
 * <p>화면 404 행은 CSV의 {@code expectedCode}가 비어 있다 — 본문이 HTML이라 오류 {@code code}가 없다. 같은 404임은 아래 두
 * 테스트가 본문 바이트로 확인한다.
 */
class PostDetailPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    static Stream<Arguments> rows() throws IOException {
        try (InputStream in =
                        PostDetailPermissionMatrixIT.class.getResourceAsStream(
                                "/permission/post-read.csv");
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().skip(1).filter(l -> !l.isBlank()).toList();
            return lines.stream()
                    .map(line -> line.split(",", -1))
                    .filter(cols -> ReadingPermissionActions.OWNER.equals(cols[5].strip()))
                    .map(cols -> Arguments.of((Object[]) cols))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @MethodSource("rows")
    void post_read_005_행(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @Test
    void API_404_본문은_모든_이유에서_같다() throws Exception {
        String expected =
                "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],"
                        + "\"details\":null}";
        for (TargetState target :
                new TargetState[] {
                    TargetState.PUBLISHED_PRIVATE,
                    TargetState.DRAFT,
                    TargetState.TRASHED,
                    TargetState.HIDDEN,
                    TargetState.AUTHOR_WITHDRAWN,
                    TargetState.NONEXISTENT
                }) {
            Scenario scenario = arrange(Actor.MEMBER, target);
            MvcResult api =
                    mockMvc.perform(
                                    get("/api/posts/{id}", scenario.postId())
                                            .cookie(scenario.session()))
                            .andReturn();
            assertThat(status(api)).as(target.name()).isEqualTo(404);
            assertThat(body(api)).as(target.name()).isEqualTo(expected);
            assertThat(cacheControl(api)).as(target.name()).isEqualTo("private, no-store");
        }
        // 숫자가 아닌 글 번호도 같은 404
        MvcResult notANumber = mockMvc.perform(get("/api/posts/{id}", "abc")).andReturn();
        assertThat(status(notANumber)).isEqualTo(404);
        assertThat(body(notANumber)).isEqualTo(expected);
        assertThat(cacheControl(notANumber)).isEqualTo("private, no-store");
    }

    @Test
    void 화면_404_본문은_공통_404_HTML과_바이트_단위로_같다() throws Exception {
        byte[] expected = notFoundPageRenderer.render().getBody();
        Scenario scenario = arrange(Actor.MEMBER, TargetState.PUBLISHED_PRIVATE);
        Cookie session = scenario.session();
        String authorHandle =
                jdbc.queryForObject(
                        "SELECT handle FROM member WHERE id = ?",
                        String.class,
                        scenario.authorId());

        for (Object postId : new Object[] {scenario.postId(), "abc", 999_999_999L}) {
            MvcResult page =
                    mockMvc.perform(
                                    get("/@{handle}/posts/{id}", authorHandle, postId)
                                            .cookie(session))
                            .andReturn();
            assertThat(status(page)).as(String.valueOf(postId)).isEqualTo(404);
            assertThat(bytes(page)).as(String.valueOf(postId)).isEqualTo(expected);
            assertThat(cacheControl(page))
                    .as(String.valueOf(postId))
                    .isEqualTo("private, no-store");
        }
    }
}
