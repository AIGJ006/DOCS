package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 에디터 열기 {@code GET /api/posts/{id}/working-copy} (002 T041, FR-034, B-1). */
class WorkingCopyReadIT extends IntegrationTestBase {

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void 임시글이면_글_내용() throws Exception {
        long me = members().member().create();
        long postId =
                fixtures()
                        .posts()
                        .post(me)
                        .title("임시 제목")
                        .contentMd("임시 본문")
                        .visibility("PRIVATE")
                        .editVersion(2)
                        .create();

        MvcResult result = api().workingCopy(TestLogin.loginAs(mockMvc, me), postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(((Number) read(result, "$.postId")).longValue()).isEqualTo(postId);
        assertThat((String) read(result, "$.status")).isEqualTo("DRAFT");
        assertThat((String) read(result, "$.title")).isEqualTo("임시 제목");
        assertThat((String) read(result, "$.contentMd")).isEqualTo("임시 본문");
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(2);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat((Boolean) read(result, "$.editing")).isFalse();
        assertThat((List<?>) read(result, "$.tags")).isEmpty();
        assertThat((Object) read(result, "$.url")).isNull();
        assertThat((String) read(result, "$.savedAt")).isNotBlank();
    }

    @Test
    void Redis_보관분_버전이_더_크면_Redis_내용() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).title("DB").contentMd("DB 본문").create();
        Instant savedAt = Instant.parse("2026-10-07T05:03:12Z");
        fixtures().putAutosave(postId, me, "레디스", "레디스 본문", 4, savedAt, true);

        MvcResult result = api().workingCopy(TestLogin.loginAs(mockMvc, me), postId);

        assertThat((String) read(result, "$.title")).isEqualTo("레디스");
        assertThat((String) read(result, "$.contentMd")).isEqualTo("레디스 본문");
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(4);
        assertThat(Instant.parse(read(result, "$.savedAt"))).isEqualTo(savedAt);
        assertThat((Boolean) read(result, "$.editing")).isFalse();
    }

    @Test
    void 발행_글은_작업본과_태그와_주소() throws Exception {
        long me = members().member().handle("bob").create();
        long postId = fixtures().publishedWithWorkingCopy(me, "고치는 제목", "고치는 본문");
        long tag =
                jdbc.queryForObject(
                        "INSERT INTO tag (name) VALUES ('jpa') RETURNING id", Long.class);
        long tag2 =
                jdbc.queryForObject(
                        "INSERT INTO tag (name) VALUES ('spring') RETURNING id", Long.class);
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, 1), (?, ?, 0)",
                postId,
                tag,
                postId,
                tag2);

        MvcResult result = api().workingCopy(TestLogin.loginAs(mockMvc, me), postId);

        assertThat((String) read(result, "$.status")).isEqualTo("PUBLISHED");
        assertThat((String) read(result, "$.title")).isEqualTo("고치는 제목");
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(2);
        assertThat((Boolean) read(result, "$.editing")).isTrue();
        assertThat((List<String>) read(result, "$.tags")).containsExactly("spring", "jpa");
        assertThat((String) read(result, "$.url")).isEqualTo("/@bob/posts/" + postId);
    }

    @Test
    void 작업본_없는_발행_글은_발행본이고_수정_중_아님_Redis가_더_크면_수정_중() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).published("PUBLIC").title("발행").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult plain = api().workingCopy(session, postId);
        assertThat((String) read(plain, "$.title")).isEqualTo("발행");
        assertThat((Boolean) read(plain, "$.editing")).isFalse();
        assertThat(((Number) read(plain, "$.version")).longValue()).isEqualTo(1);

        fixtures().putAutosave(postId, me, "자동 저장", "본문", 2, Instant.now(), true);
        MvcResult editing = api().workingCopy(session, postId);
        assertThat((String) read(editing, "$.title")).isEqualTo("자동 저장");
        assertThat((Boolean) read(editing, "$.editing")).isTrue();
    }

    @Test
    void 남의_글_없는_글_휴지통_글은_같은_404() throws Exception {
        long owner = members().member().create();
        long me = members().member().create();
        long others = fixtures().posts().post(owner).title("t").create();
        long trashed = fixtures().posts().post(me).published("PUBLIC").trashed().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult a = api().workingCopy(session, others);
        MvcResult b = api().workingCopy(session, fixtures().posts().nonexistentId());
        MvcResult c = api().workingCopy(session, trashed);

        for (MvcResult r : List.of(a, b, c)) {
            assertThat(status(r)).isEqualTo(404);
            assertThat((String) read(r, "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(body(a)).isEqualTo(body(b)).isEqualTo(body(c));
    }

    @Test
    void 비회원은_401() throws Exception {
        MvcResult result = api().workingCopy(null, 1L);
        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }
}
