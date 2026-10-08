package com.team.blog.post.integration;

import static com.team.blog.post.support.VisibilityApi.body;
import static com.team.blog.post.support.VisibilityApi.cacheControl;
import static com.team.blog.post.support.VisibilityApi.read;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.post.support.VisibilityApi;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.ReferenceListQueries;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.permission.PostSnapshot;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공개 범위 변경 (004 T028, US1 인수 1~6 + Edge, FR-016~FR-020, quickstart 시나리오 1·3·4·5). 실제 PostgreSQL에서
 * {@code PUT /api/posts/{postId}/visibility}를 부르고 다른 회원의 상세 판정({@link PostReadService})과 공용 목록 조건으로
 * 만든 홈 대표 쿼리({@link ReferenceListQueries})로 결과를 본다.
 */
class VisibilityChangeIT extends IntegrationTestBase {

    @Autowired private PostReadService postReadService;
    @Autowired private VisibilityFilter visibilityFilter;

    private PostFixtures posts;
    private VisibilityApi api;
    private ReferenceListQueries lists;
    private long author;
    private Cookie authorSession;
    private long other;
    private Viewer otherViewer;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
        api = new VisibilityApi(mockMvc);
        lists = new ReferenceListQueries(jdbc, visibilityFilter);
        author = members().member().create();
        authorSession = TestLogin.loginAs(mockMvc, author);
        other = members().member().create();
        otherViewer = new Viewer(other, Role.USER, MemberStatus.ACTIVE, true);
    }

    private Map<String, Object> row(long postId) {
        return jdbc.queryForMap(
                "SELECT visibility, status, first_public_at, edited_at, edit_version, updated_at,"
                        + " published_at, hidden_at, like_count, comment_count FROM post WHERE id = ?",
                postId);
    }

    private static Instant instant(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant();
    }

    private Instant hoursAgo(long hours) {
        return Instant.now().minus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.MICROS);
    }

    /** 발행 공개 글 — 최초 공개 = 발행 = {@code at}, 다시 발행 {@code at + 1분}(edited_at). */
    private long publicPostAt(Instant at) {
        return posts.post(author)
                .published("PUBLIC")
                .firstPublicAt(at)
                .editedAt(Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS))
                .create();
    }

    @Test
    void US1_1_비공개로_바꾸면_즉시_다른_회원에게_404이고_수정됨이_생기지_않는다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Map<String, Object> before = row(postId);
        assertThat(postReadService.requireReadable(postId, otherViewer).id()).isEqualTo(postId);

        MvcResult result = api.change(authorSession, postId, "PRIVATE");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat(Instant.parse(read(result, "$.firstPublicAt")))
                .isEqualTo(instant(before.get("first_public_at")));
        assertThatThrownBy(() -> postReadService.requireReadable(postId, otherViewer))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postReadService.requireReadable(postId, Viewer.anonymous()))
                .isInstanceOf(PostNotFoundException.class);
        Map<String, Object> after = row(postId);
        assertThat(after.get("visibility")).isEqualTo("PRIVATE");
        assertThat(after.get("edited_at")).isEqualTo(before.get("edited_at"));
        assertThat(after.get("edit_version")).isEqualTo(before.get("edit_version"));
        assertThat(after.get("published_at")).isEqualTo(before.get("published_at"));
        assertThat(lists.home(otherViewer)).doesNotContain(postId);
        assertThat(lists.blog(otherViewer, author)).doesNotContain(postId);
    }

    @Test
    void US1_2_공개_비공개_공개로_바꿔도_최초_공개_일자와_홈_순서가_그대로() throws Exception {
        long older = publicPostAt(hoursAgo(23));
        long target = publicPostAt(hoursAgo(20));
        long newer = publicPostAt(hoursAgo(10));
        Instant firstPublicAt = instant(row(target).get("first_public_at"));
        List<Long> homeBefore = lists.home(otherViewer);
        assertThat(homeBefore).containsExactly(newer, target, older);

        assertThat(status(api.change(authorSession, target, "PRIVATE"))).isEqualTo(200);
        assertThat(lists.home(otherViewer)).containsExactly(newer, older);
        MvcResult back = api.change(authorSession, target, "PUBLIC");

        assertThat(status(back)).isEqualTo(200);
        assertThat(Instant.parse(read(back, "$.firstPublicAt"))).isEqualTo(firstPublicAt);
        assertThat(instant(row(target).get("first_public_at"))).isEqualTo(firstPublicAt);
        assertThat(lists.home(otherViewer)).isEqualTo(homeBefore);
        assertThat(postReadService.requireReadable(target, otherViewer).id()).isEqualTo(target);
    }

    @Test
    void US1_3_댓글과_좋아요는_지워지지_않고_다시_공개하면_그대로_돌아온다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long commenter = members().member().create();
        jdbc.update(
                "INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '첫 댓글'),"
                        + " (?, ?, '둘째 댓글')",
                postId,
                commenter,
                postId,
                other);
        jdbc.update(
                "INSERT INTO post_like (post_id, member_id) VALUES (?, ?), (?, ?)",
                postId,
                commenter,
                postId,
                other);
        jdbc.update("UPDATE post SET like_count = 2, comment_count = 2 WHERE id = ?", postId);

        assertThat(status(api.change(authorSession, postId, "PRIVATE"))).isEqualTo(200);
        assertThat(counts(postId)).containsExactly(2L, 2L, 2, 2);
        assertThat(status(api.change(authorSession, postId, "PUBLIC"))).isEqualTo(200);

        assertThat(counts(postId)).containsExactly(2L, 2L, 2, 2);
    }

    private List<Object> counts(long postId) {
        Map<String, Object> r = row(postId);
        return List.of(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM comment WHERE post_id = ?", Long.class, postId),
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post_like WHERE post_id = ?", Long.class, postId),
                r.get("like_count"),
                r.get("comment_count"));
    }

    @Test
    void US1_4_수정_중인_글도_바로_바뀌고_작업본과_편집_버전은_그대로() throws Exception {
        long postId = posts.create(author, PostFixtures.State.EDITING);
        Map<String, Object> draftBefore =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version, updated_at FROM post_draft"
                                + " WHERE post_id = ?",
                        postId);
        Object versionBefore = row(postId).get("edit_version");

        assertThat(status(api.change(authorSession, postId, "PRIVATE"))).isEqualTo(200);

        assertThat(row(postId).get("visibility")).isEqualTo("PRIVATE");
        assertThat(row(postId).get("edit_version")).isEqualTo(versionBefore);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT title, content_md, edit_version, updated_at FROM post_draft"
                                        + " WHERE post_id = ?",
                                postId))
                .isEqualTo(draftBefore);
    }

    @Test
    void US1_5_남의_글_없는_글_휴지통_글은_404이고_글이_바뀌지_않는다() throws Exception {
        Cookie otherSession = TestLogin.loginAs(mockMvc, other);
        long others = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long trashed = posts.create(other, PostFixtures.State.TRASHED);
        long missing = posts.nonexistentId();
        String notFound =
                "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],"
                        + "\"details\":null}";

        PostSnapshot othersBefore = PostSnapshot.take(jdbc, others).orElseThrow();
        PostSnapshot trashedBefore = PostSnapshot.take(jdbc, trashed).orElseThrow();
        List<MvcResult> results = new ArrayList<>();
        results.add(api.change(otherSession, others, "PRIVATE"));
        results.add(
                api.send(
                        otherSession,
                        others,
                        "{\"visibility\":\"PRIVATE\",\"authorId\":" + author + "}"));
        results.add(api.change(otherSession, missing, "PRIVATE"));
        results.add(api.change(otherSession, trashed, "PRIVATE"));

        for (MvcResult result : results) {
            assertThat(status(result)).isEqualTo(404);
            assertThat(body(result)).isEqualTo(notFound);
            assertThat(cacheControl(result)).isEqualTo("private, no-store");
        }
        assertThat(PostSnapshot.take(jdbc, others)).contains(othersBefore);
        assertThat(PostSnapshot.take(jdbc, trashed)).contains(trashedBefore);
    }

    @Test
    void US1_6_비공개로_발행한_글을_처음_공개하면_지금이_최초_공개_일자이고_홈_맨_위() throws Exception {
        publicPostAt(hoursAgo(2));
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        assertThat(row(postId).get("first_public_at")).isNull();
        Instant start = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MvcResult result = api.change(authorSession, postId, "PUBLIC");

        assertThat(status(result)).isEqualTo(200);
        Instant firstPublicAt = Instant.parse(read(result, "$.firstPublicAt"));
        assertThat(firstPublicAt).isBetween(start, Instant.now());
        assertThat(instant(row(postId).get("first_public_at"))).isEqualTo(firstPublicAt);
        assertThat(lists.home(otherViewer).get(0)).isEqualTo(postId);
    }

    @Test
    void 같은_값이면_200이고_updated_at도_그대로() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        PostSnapshot before = PostSnapshot.take(jdbc, postId).orElseThrow();

        MvcResult result = api.change(authorSession, postId, "PUBLIC");

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PUBLIC");
        assertThat(PostSnapshot.take(jdbc, postId)).contains(before);
    }

    @Test
    void 임시글은_값만_저장한다() throws Exception {
        long postId = posts.post(author).visibility("PUBLIC").title("임시").create();
        Instant updatedBefore = instant(row(postId).get("updated_at"));

        MvcResult result = api.change(authorSession, postId, "PRIVATE");

        assertThat(status(result)).isEqualTo(200);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat((Object) read(result, "$.firstPublicAt")).isNull();
        Map<String, Object> after = row(postId);
        assertThat(after.get("status")).isEqualTo("DRAFT");
        assertThat(after.get("visibility")).isEqualTo("PRIVATE");
        assertThat(after.get("first_public_at")).isNull();
        assertThat(after.get("published_at")).isNull();
        assertThat(instant(after.get("updated_at"))).isAfter(updatedBefore);

        assertThat(status(api.change(authorSession, postId, "PUBLIC"))).isEqualTo(200);
        assertThat(row(postId).get("first_public_at")).isNull();
    }

    @Test
    void 모르는_값과_FRIENDS는_400_INVALID_VISIBILITY_남의_글이면_404가_먼저() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        PostSnapshot before = PostSnapshot.take(jdbc, postId).orElseThrow();

        for (String value : new String[] {"FRIENDS", "PROTECTED", "public", " PUBLIC", ""}) {
            MvcResult result = api.change(authorSession, postId, value);
            assertThat(status(result)).as(value).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_VISIBILITY");
            assertThat((String) read(result, "$.message")).isEqualTo("공개 범위를 다시 선택해 주세요");
            assertThat((String) read(result, "$.errors[0].field")).isEqualTo("visibility");
            assertThat((String) read(result, "$.errors[0].code")).isEqualTo("INVALID_VISIBILITY");
            assertThat((Object) read(result, "$.details")).isNull();
        }
        MvcResult missingValue = api.send(authorSession, postId, "{}");
        assertThat(status(missingValue)).isEqualTo(400);
        assertThat((String) read(missingValue, "$.code")).isEqualTo("INVALID_VISIBILITY");
        assertThat(PostSnapshot.take(jdbc, postId)).contains(before);

        Cookie otherSession = TestLogin.loginAs(mockMvc, other);
        assertThat(status(api.change(otherSession, postId, "FRIENDS"))).isEqualTo(404);
        assertThat(status(api.change(otherSession, posts.nonexistentId(), "FRIENDS")))
                .isEqualTo(404);
    }

    @Test
    void 숨긴_글은_공개_범위를_바꿔도_숨김이_유지된다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.HIDDEN);
        Object hiddenAt = row(postId).get("hidden_at");

        assertThat(status(api.change(authorSession, postId, "PRIVATE"))).isEqualTo(200);
        assertThat(status(api.change(authorSession, postId, "PUBLIC"))).isEqualTo(200);

        assertThat(row(postId).get("hidden_at")).isEqualTo(hiddenAt);
        assertThat(lists.home(otherViewer)).doesNotContain(postId);
        assertThatThrownBy(() -> postReadService.requireReadable(postId, otherViewer))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void 동시_요청_10건은_한_번에_하나씩_반영되고_최초_공개_일자는_한_번만_기록된다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                String value = i % 2 == 0 ? "PUBLIC" : "PRIVATE";
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return api.change(authorSession, postId, value);
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            Set<String> firstPublicValues = new HashSet<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(60, TimeUnit.SECONDS);
                assertThat(status(result)).as(body(result)).isEqualTo(200);
                String value = read(result, "$.firstPublicAt");
                if (value != null) {
                    firstPublicValues.add(value);
                }
                if ("PUBLIC".equals(read(result, "$.visibility"))) {
                    assertThat(value).isNotNull();
                }
            }
            assertThat(firstPublicValues).hasSize(1);
            Map<String, Object> after = row(postId);
            assertThat(instant(after.get("first_public_at")))
                    .isEqualTo(Instant.parse(firstPublicValues.iterator().next()));
            assertThat(after.get("visibility")).isIn("PUBLIC", "PRIVATE");
        } finally {
            pool.shutdownNow();
        }
    }
}
