package com.team.blog.support.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 권한 매트릭스 하네스 자체 확인 (004 T021~T023): 실행기 찾기·pending 건너뛰기·중복 이름 거부·스냅샷 비교·픽스처(7개 글 상태가 CHECK를 통과).
 * CSV 행 실행(읽기·쓰기 전부, 작성자 번호 끼워 넣기 변형)은 US3 {@code PermissionMatrixIT}(T045)가 맡는다.
 */
class PermissionHarnessIT extends AbstractPermissionMatrixIT {

    @Test
    void 컴포넌트_스캔으로_실행기를_찾는다() {
        assertThat(registry().find("post.read")).get().isInstanceOf(ReadPostAction.class);
    }

    @Test
    void 실행기가_없는_행동은_pending으로_건너뛴다() {
        assertThatThrownBy(
                        () ->
                                verify(
                                        "AUTHOR",
                                        "PUBLISHED_PUBLIC",
                                        "post.no-such-action",
                                        "200",
                                        null,
                                        "999"))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("pending: 999");
    }

    @Test
    void 같은_이름의_다른_실행기가_둘이면_거부한다() {
        PermissionAction other =
                new PermissionAction() {
                    @Override
                    public String name() {
                        return "post.read";
                    }

                    @Override
                    public String owner() {
                        return "004";
                    }

                    @Override
                    public ActionResult perform(
                            MockMvc mockMvc, jakarta.servlet.http.Cookie session, Long postId) {
                        return new ActionResult(200, null, null);
                    }
                };
        assertThatThrownBy(() -> new PermissionActionRegistry(List.of(new ReadPostAction(), other)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 거부된_쓰기_전후_스냅샷이_다르면_실패한다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.EDITING);
        PostSnapshot before = PostSnapshot.take(jdbc, postId).orElseThrow();
        assertThat(PostSnapshot.take(jdbc, postId)).contains(before);

        jdbc.update("UPDATE post_draft SET content_md = '바뀜' WHERE post_id = ?", postId);
        assertThat(PostSnapshot.take(jdbc, postId)).get().isNotEqualTo(before);

        jdbc.update("DELETE FROM post_draft WHERE post_id = ?", postId);
        jdbc.update("DELETE FROM post WHERE id = ?", postId);
        assertThat(PostSnapshot.take(jdbc, postId)).isEmpty();
    }

    @Test
    void 픽스처의_글_상태가_스키마_CHECK를_통과하고_의도한_값을_가진다() {
        PostFixtures posts = new PostFixtures(jdbc);
        for (PostFixtures.State state : PostFixtures.State.values()) {
            long author = members().member().create();
            long id = posts.create(author, state);
            var row =
                    jdbc.queryForMap(
                            "SELECT p.status, p.visibility, p.first_public_at, p.deleted_at,"
                                    + " p.hidden_at, m.status AS member_status,"
                                    + " (SELECT count(*) FROM post_draft d WHERE d.post_id = p.id)"
                                    + " AS drafts"
                                    + " FROM post p JOIN member m ON m.id = p.author_id"
                                    + " WHERE p.id = ?",
                            id);
            switch (state) {
                case PUBLISHED_PUBLIC -> assertThat(row.get("first_public_at")).isNotNull();
                case PUBLISHED_PRIVATE -> assertThat(row.get("visibility")).isEqualTo("PRIVATE");
                case EDITING -> assertThat(row.get("drafts")).isEqualTo(1L);
                case DRAFT -> assertThat(row.get("status")).isEqualTo("DRAFT");
                case TRASHED -> assertThat(row.get("deleted_at")).isNotNull();
                case HIDDEN -> assertThat(row.get("hidden_at")).isNotNull();
                case AUTHOR_WITHDRAWN ->
                        assertThat(row.get("member_status")).isEqualTo("WITHDRAWN");
            }
        }
    }

    @Test
    void 작성자_번호_끼워_넣기_필터는_켠_동안만_본문과_매개변수에_더한다() throws Exception {
        var session = TestLogin.loginAs(mockMvc, members().member().create());
        String plain = echo(session, "{\"visibility\":\"PRIVATE\"}");
        assertThat((String) JsonPath.read(plain, "$.body"))
                .isEqualTo("{\"visibility\":\"PRIVATE\"}");

        String injected =
                OwnerFieldInjector.armed(77L, () -> echo(session, "{\"visibility\":\"PRIVATE\"}"));
        String body = JsonPath.read(injected, "$.body");
        for (String field : OwnerFieldInjector.FIELDS) {
            assertThat(body).contains("\"" + field + "\":77");
            assertThat((String) JsonPath.read(injected, "$.params." + field)).isEqualTo("77");
        }
        assertThat(body).contains("\"visibility\":\"PRIVATE\"");
        assertThat(
                        (String)
                                JsonPath.read(
                                        OwnerFieldInjector.armed(77L, () -> echo(session, "{}")),
                                        "$.body"))
                .isEqualTo("{\"authorId\":77,\"memberId\":77,\"ownerId\":77,\"userId\":77}");
    }

    private String echo(jakarta.servlet.http.Cookie session, String json) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(post("/api/__test/echo"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
