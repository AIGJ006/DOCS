package com.team.blog.tag.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.tag.support.TagFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 발행할 때 태그 붙이기 (008 T018, US1, SC-003·006, FR-009~014). 002 발행 API 그대로이고 정규화·검증만 008 {@code
 * TagNormalizer}다. 금칙어는 {@code policy/banned-words.txt}에서 읽는다(테스트에 적지 않음).
 */
class TagPublishIT extends IntegrationTestBase {

    private static final String BANNED =
            ReservedWords.readList(new ClassPathResource("policy/banned-words.txt"))
                    .iterator()
                    .next();

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private TagFixtures tags() {
        return new TagFixtures(jdbc);
    }

    private long editVersion(long postId) {
        return jdbc.queryForObject(
                "SELECT edit_version FROM post WHERE id = ?", Long.class, postId);
    }

    private MvcResult publish(Cookie session, long postId, List<String> tagInput) throws Exception {
        return api().publish(
                        session,
                        postId,
                        publishBody("태그 글", "본문", tagInput, "PUBLIC", editVersion(postId)));
    }

    @Test
    void 같은_뜻의_입력은_한_태그가_된다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);
        String fullWidth = "ｓｐｒｉｎｇ ｂｏｏｔ";

        MvcResult result =
                publish(
                        session,
                        postId,
                        List.of(
                                "Spring Boot",
                                "spring-boot",
                                "#SPRING  BOOT",
                                fullWidth,
                                "C#",
                                "C++",
                                "Node.JS",
                                ".NET",
                                "스프링  부트"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(tags().namesOf(postId))
                .containsExactly("spring-boot", "c#", "c++", "node.js", ".net", "스프링-부트");
        assertThat(tags().countByName("spring-boot")).isEqualTo(1);
    }

    @Test
    void 문제_태그를_모두_한_번에_알려준다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);

        MvcResult result =
                publish(
                        session,
                        postId,
                        List.of(
                                Character.toString(0x1F525) + "hot",
                                "a/b",
                                "ㅋㅋ",
                                "...",
                                "a".repeat(31),
                                "x" + BANNED,
                                "ok"));

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((List<String>) read(result, "$.errors[*].field"))
                .containsExactly("tags[0]", "tags[1]", "tags[2]", "tags[3]", "tags[4]", "tags[5]");
        assertThat((List<String>) read(result, "$.errors[*].code"))
                .containsExactly(
                        "INVALID_TAG",
                        "INVALID_TAG",
                        "INVALID_TAG",
                        "INVALID_TAG",
                        "TAG_TOO_LONG",
                        "TAG_BANNED_WORD");
        assertThat((String) read(result, "$.errors[5].message")).isEqualTo("쓸 수 없는 단어가 들어 있어요");
        assertThat(body(result)).doesNotContain(BANNED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag", Long.class)).isZero();
    }

    @Test
    void 열한_개면_발행되지_않는다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);
        assertThat(status(publish(session, postId, List.of("spring", "jpa")))).isEqualTo(200);
        Map<String, Object> before =
                jdbc.queryForMap("SELECT edit_version, edited_at FROM post WHERE id = ?", postId);
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("t" + i);
        }

        MvcResult result = publish(session, postId, eleven);

        assertThat(status(result)).isEqualTo(400);
        assertThat((List<String>) read(result, "$.errors[*].code"))
                .containsExactly("TOO_MANY_TAGS");
        assertThat((List<String>) read(result, "$.errors[*].field")).containsExactly("tags");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT edit_version, edited_at FROM post WHERE id = ?", postId))
                .isEqualTo(before);
        assertThat(tags().namesOf(postId)).containsExactly("spring", "jpa");
    }

    @Test
    void 입력_순서대로_처음_것만_남긴다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);

        assertThat(status(publish(session, postId, List.of("Spring", "spring", "jpa"))))
                .isEqualTo(200);

        assertThat(
                        jdbc.queryForList(
                                "SELECT t.name || ':' || pt.position FROM post_tag pt"
                                        + " JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = ?"
                                        + " ORDER BY pt.position",
                                String.class,
                                postId))
                .containsExactly("spring:0", "jpa:1");
    }

    @Test
    void 같은_새_태그로_동시에_10건_발행해도_태그는_하나() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        List<Long> postIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            postIds.add(api().createPostId(session));
        }
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long postId : postIds) {
                results.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return status(
                                            api().publish(
                                                            session,
                                                            postId,
                                                            publishBody(
                                                                    "동시 " + postId,
                                                                    "본문",
                                                                    List.of("Brand New", "공통"),
                                                                    "PUBLIC",
                                                                    0)));
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(tags().countByName("brand-new")).isEqualTo(1);
        assertThat(tags().countByName("공통")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                                        + " WHERE t.name = 'brand-new'",
                                Long.class))
                .isEqualTo(10);
        for (long postId : postIds) {
            assertThat(tags().namesOf(postId)).containsExactly("brand-new", "공통");
        }
    }

    @Test
    void 태그만_바꿔_다시_발행하면_수정됨() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);
        assertThat(status(publish(session, postId, List.of("spring")))).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT edited_at FROM post WHERE id = ?", Object.class, postId))
                .isNull();

        MvcResult result = publish(session, postId, List.of("jpa", "spring"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.editedAt")).isNotBlank();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT edited_at FROM post WHERE id = ?", Object.class, postId))
                .isNotNull();
        assertThat(tags().namesOf(postId)).containsExactly("jpa", "spring");
    }

    @Test
    void 다시_발행할_때_지금_태그를_순서대로_준다() throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, members().member().create());
        long postId = api().createPostId(session);
        assertThat(status(publish(session, postId, List.of("Node.JS", "C#", "스프링  부트"))))
                .isEqualTo(200);

        MvcResult editor = api().workingCopy(session, postId);

        assertThat(status(editor)).as(body(editor)).isEqualTo(200);
        assertThat((List<String>) read(editor, "$.tags"))
                .containsExactly("node.js", "c#", "스프링-부트");
    }
}
