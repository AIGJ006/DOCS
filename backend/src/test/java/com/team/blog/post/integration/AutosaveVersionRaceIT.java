package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** 장애 뒤 버전 경쟁과 006용 즉시 반영 (002 T069, B-3 ①, EV §4). */
class AutosaveVersionRaceIT extends IntegrationTestBase {

    @Autowired AutosaveService autosaveService;
    @Autowired TransactionTemplate tx;

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void 장애_동안_DB가_5까지_오른_뒤_옛_Redis_키가_3이어도_기준_5는_200() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                fixtures().posts().post(me).title("DB 5").contentMd("DB 5").editVersion(5).create();
        fixtures().putAutosave(postId, me, "옛 Redis", "옛 Redis", 3, Instant.now(), true);

        MvcResult result =
                new EditorApi(mockMvc).autosave(session, postId, saveBody("이어 쓰기", "이어 쓰기", 5));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(((Number) read(result, "$.version")).longValue()).isEqualTo(6);
        assertThat(fixtures().autosaveHash(postId))
                .containsEntry("version", "6")
                .containsEntry("title", "이어 쓰기");
    }

    @Test
    void flushNow는_휴지통_글에도_바로_반영하고_키_정리는_커밋_후() {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).title("옛").contentMd("옛").trashed().create();
        fixtures().putAutosave(postId, me, "마지막 입력", "마지막 본문", 2, Instant.now(), true);

        tx.executeWithoutResult(
                status -> {
                    autosaveService.flushNow(postId);
                    assertThat(
                                    jdbc.queryForObject(
                                            "SELECT title FROM post WHERE id = ?",
                                            String.class,
                                            postId))
                            .isEqualTo("마지막 입력");
                    assertThat(fixtures().autosaveHash(postId)).isNotEmpty();
                });

        assertThat(fixtures().autosaveHash(postId)).isEmpty();
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void flushNow가_롤백되면_키를_남긴다() {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).create();
        fixtures().putAutosave(postId, me, "보관", "보관", 1, Instant.now(), true);

        tx.executeWithoutResult(
                status -> {
                    autosaveService.flushNow(postId);
                    status.setRollbackOnly();
                });

        assertThat(fixtures().autosaveHash(postId)).containsEntry("title", "보관");
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEmpty();
    }
}
