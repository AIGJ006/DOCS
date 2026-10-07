package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.tag.application.TagService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 발행과 자동 저장·1분 반영의 경쟁 (002 T091, spec Edge Case 1, research B-3 ③·④). 발행 트랜잭션 안(태그 확정 ⑤)과 커밋 뒤 ⑨
 * 직전에 다른 요청을 끼워 넣는다.
 */
class PublishAutosaveRaceIT extends IntegrationTestBase {

    private static final Instant SAVED_AT = Instant.parse("2026-10-07T05:03:12.123456Z");

    @MockitoSpyBean TagService tagService;
    @MockitoSpyBean RedisAutosaveStore store;
    @Autowired AutosaveFlushJob flushJob;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @AfterEach
    void resetSpies() {
        reset(tagService, store);
    }

    private long postVersion(long postId) {
        return jdbc.queryForObject(
                "SELECT edit_version FROM post WHERE id = ?", Long.class, postId);
    }

    @Test
    void 발행_중에_끼어든_다른_탭의_자동_저장은_지우지_않고_새_버전_다음_번호로_남겨_작업본이_된다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        // 이 탭이 Redis에 버전 2까지 저장했다
        fixtures().putAutosave(postId, me, "발행할 제목", "발행할 본문", 2, SAVED_AT, true);
        AtomicInteger otherTab = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            // ④에서 버전 2를 확인한 뒤, 커밋 전에 다른 탭이 자동 저장한다(행 잠금과 무관한 Redis 경로)
                            MvcResult other =
                                    CompletableFuture.supplyAsync(
                                                    () -> {
                                                        try {
                                                            return api().autosave(
                                                                            session,
                                                                            postId,
                                                                            saveBody(
                                                                                    "끼어든 탭",
                                                                                    "끼어든 본문", 2));
                                                        } catch (Exception e) {
                                                            throw new IllegalStateException(e);
                                                        }
                                                    })
                                            .get(10, TimeUnit.SECONDS);
                            otherTab.set(status(other));
                            return invocation.callRealMethod();
                        })
                .when(tagService)
                .replacePostTags(eq(postId), anyList());

        MvcResult published =
                api().publish(
                                session,
                                postId,
                                publishBody("발행할 제목", "발행할 본문", List.of(), "PUBLIC", 2));

        assertThat(status(published)).as(body(published)).isEqualTo(200);
        assertThat(otherTab.get()).isEqualTo(200);
        assertThat(postVersion(postId)).isEqualTo(3);
        // 커밋 후 ⑨: Redis 버전 3 > 확인한 2 → 지우지 않고 새 DB 버전 3 + 1 = 4로 다시 매김, dirty 유지
        Map<Object, Object> hash = fixtures().autosaveHash(postId);
        assertThat(hash).containsEntry("version", "4").containsEntry("title", "끼어든 탭");
        assertThat(fixtures().isDirty(postId)).isTrue();

        flushJob.flush();
        Map<String, Object> draft =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version FROM post_draft WHERE post_id = ?",
                        postId);
        assertThat(draft)
                .containsEntry("title", "끼어든 탭")
                .containsEntry("content_md", "끼어든 본문")
                .containsEntry("edit_version", 4L);
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("발행할 제목");

        // 그 탭은 자기 저장이 버전 3이라고 알고 있다 → 다음 저장은 409로 비교 창을 거친다
        redis.delete("ratelimit:autosave:" + me);
        MvcResult next = api().autosave(session, postId, saveBody("끼어든 탭 계속", "계속", 3));
        assertThat(status(next)).isEqualTo(409);
        assertThat(((Number) read(next, "$.details.server.version")).longValue()).isEqualTo(4);
        assertThat((String) read(next, "$.details.server.title")).isEqualTo("끼어든 탭");
    }

    @Test
    void 발행_커밋과_Redis_정리_사이에_1분_반영이_돌아도_작업본이_되살아나지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).published("PUBLIC").create();
        fixtures().putAutosave(postId, me, "옛 보관분", "옛 본문", 2, SAVED_AT, true);
        doAnswer(
                        invocation -> {
                            flushJob.flush();
                            return invocation.callRealMethod();
                        })
                .when(store)
                .release(eq(postId), anyLong(), anyLong());

        MvcResult published =
                api().publish(session, postId, publishBody("새 발행", "새 본문", List.of(), "PUBLIC", 2));

        assertThat(status(published)).as(body(published)).isEqualTo(200);
        assertThat(postVersion(postId)).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_draft WHERE post_id = ?",
                                Integer.class,
                                postId))
                .isZero();
        assertThat(fixtures().autosaveHash(postId)).isEmpty();
        assertThat(fixtures().isDirty(postId)).isFalse();
        assertThat((Boolean) read(api().workingCopy(session, postId), "$.editing")).isFalse();
    }
}
