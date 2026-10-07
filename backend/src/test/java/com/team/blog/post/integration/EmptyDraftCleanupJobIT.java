package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.EmptyDraftCleanupJob;
import com.team.blog.post.domain.EmptyDraftPolicy;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 빈 임시글 정리 (002 T114, US7 #2, FR-050, B-9, EV §3). 만든 지도 고친 지도 24시간이 지난, 제목·본문이 공백뿐인 임시글을 휴지통을 거치지
 * 않고 지운다. Redis 보관분이 있거나 Redis가 장애면 지우지 않는다. 이벤트는 없다.
 */
@Import(PostTestConfig.class)
class EmptyDraftCleanupJobIT extends IntegrationTestBase {

    @Autowired EmptyDraftCleanupJob job;
    @Autowired CommittedEvents events;
    @Autowired CircuitBreakerRegistry circuitBreakers;

    private final Instant old = Instant.now().minus(Duration.ofHours(25));

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @AfterEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
    }

    private boolean exists(long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ?", Long.class, postId)
                == 1;
    }

    @Test
    void 하루가_지난_빈_임시글만_완전_삭제한다() {
        long me = members().member().create();
        long empty = fixtures().oldDraft(me, "", "", old);
        long blank = fixtures().oldDraft(me, "  ", "\n\t \r\n", old);
        long recentlyEdited =
                fixtures()
                        .posts()
                        .post(me)
                        .createdAt(old)
                        .updatedAt(Instant.now().minus(Duration.ofHours(1)))
                        .create();
        long recentlyCreated = fixtures().oldDraft(me, "", "", Instant.now());
        long titled = fixtures().oldDraft(me, "제목만", "", old);
        long bodied = fixtures().oldDraft(me, "", "본문만", old);
        long imageOnly =
                fixtures().oldDraft(me, "", "![](local:0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c)", old);
        long published =
                fixtures()
                        .posts()
                        .post(me)
                        .published("PUBLIC")
                        // ck_post_published(btrim 공백만)를 지나면서 정책상 빈 제목인 탭
                        .title("\t")
                        .contentMd("")
                        .createdAt(old)
                        .updatedAt(old)
                        .create();
        long trashed = fixtures().posts().post(me).createdAt(old).updatedAt(old).trashed().create();
        long autosaved = fixtures().oldDraft(me, "", "", old);
        fixtures().putAutosave(autosaved, me, "", "", 1, Instant.now(), false);

        int deleted = job.cleanup();

        assertThat(deleted).isEqualTo(2);
        assertThat(exists(empty)).isFalse();
        assertThat(exists(blank)).isFalse();
        assertThat(
                        List.of(
                                recentlyEdited,
                                recentlyCreated,
                                titled,
                                bodied,
                                imageOnly,
                                published,
                                trashed,
                                autosaved))
                .allMatch(this::exists);
        assertThat(events.all()).isEmpty();
    }

    @Test
    void Redis_장애면_그날은_건너뛴다() {
        long me = members().member().create();
        long empty = fixtures().oldDraft(me, "", "", old);
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).transitionToForcedOpenState();

        int deleted = job.cleanup();

        assertThat(deleted).isZero();
        assertThat(exists(empty)).isTrue();
    }

    @Test
    void 대상이_100개를_넘어도_모두_지운다() {
        long me = members().member().create();
        for (int i = 0; i < 230; i++) {
            fixtures().oldDraft(me, "", " ", old);
        }

        int deleted = job.cleanup();

        assertThat(deleted).isEqualTo(230);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post WHERE author_id = ?", Long.class, me))
                .isZero();
    }

    @Test
    void SQL_판정은_EmptyDraftPolicy와_같다() {
        long me = members().member().create();
        List<String> samples =
                List.of("", " ", "\t", "\r\n", " \t\r\n ", "a", " a ", "　", "​", " ", "\n\n글\n");
        Map<Long, Boolean> expected = new LinkedHashMap<>();
        for (String title : samples) {
            for (String content : samples) {
                long id = fixtures().oldDraft(me, title, content, old);
                expected.put(id, EmptyDraftPolicy.isEmpty(title, content));
            }
        }

        job.cleanup();

        expected.forEach(
                (id, empty) ->
                        assertThat(exists(id)).as("post %d 빈 글=%s", id, empty).isEqualTo(!empty));
    }
}
