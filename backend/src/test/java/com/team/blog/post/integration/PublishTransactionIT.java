package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.event.DomainEvent;
import com.team.blog.shared.event.PostEdited;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.tag.application.TagService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 발행 트랜잭션 경계와 이벤트 (002 T040, 05 §7, contracts/events.md §1, FR-030·039). */
@Import({PostTestConfig.class, PublishTransactionIT.FailingListenerConfig.class})
class PublishTransactionIT extends IntegrationTestBase {

    @Autowired CommittedEvents events;

    @MockitoSpyBean TagService tagService;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
        FailingListener.FAIL.set(false);
    }

    @AfterEach
    void resetSpy() {
        reset(tagService);
        FailingListener.FAIL.set(false);
    }

    @Test
    void 전체_공개로_최초_발행하면_커밋_후_PostPublished와_PostWentPublic_각_1번() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody("제목", "본문", List.of(), "PUBLIC", 0))))
                .isEqualTo(200);

        assertThat(events.of(PostPublished.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.postId()).isEqualTo(postId);
                            assertThat(e.authorId()).isEqualTo(me);
                        });
        assertThat(events.of(PostWentPublic.class)).hasSize(1);
        assertThat(events.of(PostEdited.class)).isEmpty();
    }

    @Test
    void 나만_보기_최초_발행은_PostPublished만_다시_발행은_PostEdited() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);

        api().publish(session, postId, publishBody("제목", "본문", List.of(), "PRIVATE", 0));
        assertThat(events.all())
                .extracting(DomainEvent::getClass)
                .containsExactly(PostPublished.class);

        events.clear();
        api().publish(session, postId, publishBody("제목", "본문 2", List.of(), "PRIVATE", 1));
        assertThat(events.all())
                .extracting(DomainEvent::getClass)
                .containsExactly(PostEdited.class);

        events.clear();
        api().publish(session, postId, publishBody("제목", "본문 3", List.of(), "PUBLIC", 2));
        assertThat(events.all())
                .extracting(DomainEvent::getClass)
                .containsExactlyInAnyOrder(PostEdited.class, PostWentPublic.class);
    }

    @Test
    void 트랜잭션_안_단계가_실패하면_롤백되어_이벤트_없음_글_불변_Redis_보관분_그대로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        Instant savedAt = Instant.parse("2026-10-01T00:00:00Z");
        fixtures().putAutosave(postId, me, "보관 제목", "보관 본문", 2, savedAt, true);
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();
        doThrow(new IllegalStateException("태그 확정 실패"))
                .when(tagService)
                .replacePostTags(anyLong(), anyList());

        MvcResult result =
                api().publish(session, postId, publishBody("제목", "본문", List.of("a"), "PUBLIC", 2));

        assertThat(status(result)).isEqualTo(500);
        assertThat(events.all()).isEmpty();
        assertThat(fixtures().snapshot(postId)).contains(before);
        assertThat(fixtures().autosaveHash(postId))
                .containsEntry("version", "2")
                .containsEntry("title", "보관 제목");
        assertThat(fixtures().isDirty(postId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag", Long.class)).isZero();
    }

    @Test
    void 커밋_후_확인한_버전_이하인_Redis_보관분을_지운다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        fixtures().putAutosave(postId, me, "보관 제목", "보관 본문", 3, Instant.now(), true);

        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody("제목", "본문", List.of(), "PUBLIC", 3))))
                .isEqualTo(200);

        assertThat(fixtures().autosaveHash(postId)).isEmpty();
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void 이벤트_리스너가_예외를_던져도_발행은_200() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        FailingListener.FAIL.set(true);

        MvcResult result =
                api().publish(session, postId, publishBody("제목", "본문", List.of(), "PUBLIC", 0));

        assertThat(status(result)).as(EditorApi.body(result)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("PUBLISHED");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingListenerConfig {
        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    /** 커밋 후 예외를 던지는 구독자 (원칙 V: 구독자 실패는 발행자에게 전파되지 않음). */
    static class FailingListener {
        static final AtomicBoolean FAIL = new AtomicBoolean();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(PostPublished event) {
            if (FAIL.get()) {
                throw new IllegalStateException("구독자 실패");
            }
        }
    }
}
