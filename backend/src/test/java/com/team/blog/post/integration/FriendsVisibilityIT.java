package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.post.support.VisibilityApi;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.ReferenceListQueries;
import com.team.blog.support.TestLogin;
import com.team.blog.support.TestSupportConfiguration;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 친구 공개 (선택 구현, 004 T068, US8-2·US8-3, FR-048, research R-14). <b>적용자 프로필({@code friends})에서만</b>
 * 돈다 — 공통 {@code verify}에서는 건너뛰고, 환경 변수 {@code BLOG_FRIENDS_IT=true}일 때만 실행한다.
 *
 * <pre>{@code
 * BLOG_FRIENDS_IT=true ./mvnw verify -Dit.test=FriendsVisibilityIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false
 * }</pre>
 *
 * <p>공통 통합 테스트와 DB를 나누려고 자기 PostgreSQL·Redis 컨테이너를 따로 띄운다({@code V900__friends.sql}이 CHECK를 넓히므로 공용
 * DB에 적용하면 안 된다). 친구 관계는 001 {@code friendship} 표에 직접 넣는다(001 친구 API와 무관).
 */
@EnabledIfEnvironmentVariable(named = "BLOG_FRIENDS_IT", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "friends"})
@Import(TestSupportConfiguration.class)
class FriendsVisibilityIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                            DockerImageName.parse(IntegrationTestBase.POSTGRES_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("blog")
                    .withUsername("blog")
                    .withPassword("blog");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(IntegrationTestBase.REDIS_IMAGE))
                    .withExposedPorts(6379);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private VisibilityFilter visibilityFilter;
    @Autowired private VisibilityRegistry registry;
    @Autowired private TransactionTemplate transactionTemplate;

    private PostFixtures posts;
    private MemberFixtures members;
    private long author;
    private long friend;
    private long pending;
    private long stranger;

    @BeforeEach
    void setUp() {
        List<String> tables =
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                                + " AND tablename NOT IN ('flyway_schema_history', 'shedlock')",
                        String.class);
        jdbc.execute(
                "TRUNCATE TABLE "
                        + String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList())
                        + " RESTART IDENTITY CASCADE");
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushAll();
                            return null;
                        });
        members = new MemberFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        author = members.member().create();
        friend = members.member().create();
        pending = members.member().create();
        stranger = members.member().create();
        befriend(author, friend, "ACCEPTED");
        befriend(pending, author, "PENDING");
    }

    private void befriend(long requester, long other, String status) {
        jdbc.update(
                "INSERT INTO friendship (member_a_id, member_b_id, requested_by, status, accepted_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                Math.min(requester, other),
                Math.max(requester, other),
                requester,
                status,
                "ACCEPTED".equals(status) ? Timestamp.from(Instant.now()) : null);
    }

    private long friendsPost() {
        return posts.post(author).published("FRIENDS").create();
    }

    private static Viewer viewer(long id) {
        return new Viewer(id, Role.USER, MemberStatus.ACTIVE, true);
    }

    private int read(long postId, Long memberId) throws Exception {
        var request = get("/api/__test/posts/{postId}", postId);
        if (memberId != null) {
            request.cookie(TestLogin.loginAs(mockMvc, memberId));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private List<Long> friendBlog(Viewer viewer) {
        SqlCondition c = visibilityFilter.forFriendBlog(viewer, author);
        return new NamedParameterJdbcTemplate(jdbc)
                .queryForList(
                        "SELECT p.id FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + c.sql()
                                + " ORDER BY p.published_at DESC, p.id DESC LIMIT 10",
                        c.params(),
                        Long.class);
    }

    private long friendBlogCount(Viewer viewer) {
        SqlCondition c = visibilityFilter.forFriendBlog(viewer, author);
        return new NamedParameterJdbcTemplate(jdbc)
                .queryForObject(
                        "SELECT COUNT(*) FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + c.sql(),
                        c.params(),
                        Long.class);
    }

    @Test
    void 적용_환경은_FRIENDS를_허용한다() throws Exception {
        assertThat(registry.allowedValues()).contains(Visibility.FRIENDS);
        long postId = posts.post(author).published("PUBLIC").create();

        MvcResult result =
                new VisibilityApi(mockMvc)
                        .change(TestLogin.loginAs(mockMvc, author), postId, "FRIENDS");

        assertThat(VisibilityApi.status(result)).isEqualTo(200);
        assertThat((String) VisibilityApi.read(result, "$.visibility")).isEqualTo("FRIENDS");
        jdbc.update("UPDATE member SET default_visibility = 'FRIENDS' WHERE id = ?", author);
    }

    @Test
    void US8_2_수락된_친구와_작성자만_상세를_보고_나머지는_404() throws Exception {
        long postId = friendsPost();

        assertThat(read(postId, friend)).isEqualTo(200);
        assertThat(read(postId, author)).isEqualTo(200);
        assertThat(read(postId, null)).as("비회원").isEqualTo(404);
        assertThat(read(postId, stranger)).as("친구 아님").isEqualTo(404);
        assertThat(read(postId, pending)).as("요청 중").isEqualTo(404);
        assertThat(read(postId, members.member().role("ADMIN").create())).as("관리자").isEqualTo(404);
    }

    @Test
    void US8_2_친구가_보는_블로그_목록과_글_수에만_있고_홈_공용_조건에는_없다() {
        long publicPost = posts.post(author).published("PUBLIC").create();
        long postId = friendsPost();
        ReferenceListQueries lists = new ReferenceListQueries(jdbc, visibilityFilter);

        assertThat(friendBlog(viewer(friend))).containsExactlyInAnyOrder(postId, publicPost);
        assertThat(friendBlogCount(viewer(friend))).isEqualTo(2);
        for (Viewer other : List.of(Viewer.anonymous(), viewer(stranger), viewer(pending))) {
            assertThat(friendBlog(other)).containsExactly(publicPost);
            assertThat(friendBlogCount(other)).isEqualTo(1);
        }
        for (Viewer anyone : List.of(Viewer.anonymous(), viewer(friend), viewer(author))) {
            assertThat(lists.home(anyone)).doesNotContain(postId);
            assertThat(lists.listedEverywhere(anyone)).doesNotContain(postId);
            assertThat(lists.blog(anyone, author)).doesNotContain(postId);
            assertThat(lists.blogCount(anyone, author)).isEqualTo(1);
        }
    }

    @Test
    void US8_3_친구를_끊은_순간_404이고_목록에서_빠진다() throws Exception {
        long postId = friendsPost();
        assertThat(read(postId, friend)).isEqualTo(200);

        jdbc.update(
                "DELETE FROM friendship WHERE member_a_id = ? AND member_b_id = ?",
                Math.min(author, friend),
                Math.max(author, friend));

        assertThat(read(postId, friend)).isEqualTo(404);
        assertThat(friendBlog(viewer(friend))).doesNotContain(postId);
    }

    @Test
    void FRIENDS에서_PUBLIC으로_바꾸면_그_순간이_최초_공개_일자이고_홈_맨_위() throws Exception {
        long older = posts.post(author).published("PUBLIC").create();
        long postId = friendsPost();
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MvcResult result =
                new VisibilityApi(mockMvc)
                        .change(TestLogin.loginAs(mockMvc, author), postId, "PUBLIC");

        assertThat(VisibilityApi.status(result)).isEqualTo(200);
        Instant firstPublicAt = Instant.parse(VisibilityApi.read(result, "$.firstPublicAt"));
        assertThat(firstPublicAt).isAfterOrEqualTo(before);
        assertThat(new ReferenceListQueries(jdbc, visibilityFilter).home(Viewer.anonymous()))
                .containsSubsequence(postId, older)
                .first()
                .isEqualTo(postId);
    }

    @Test
    void 친구_블로그_목록은_발행_일자_최신순_같으면_번호_큰_순() {
        long first = friendsPost();
        long second = posts.post(author).published("PUBLIC").create();
        long third = friendsPost();
        Timestamp same = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
        jdbc.update("UPDATE post SET published_at = ? WHERE id IN (?, ?)", same, first, third);
        jdbc.update(
                "UPDATE post SET published_at = ?, first_public_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")),
                second);

        assertThat(friendBlog(viewer(friend))).containsExactly(second, third, first);
    }

    @Test
    void 친구_블로그_목록은_ix_post_blog_friends를_탄다() {
        friendsPost();
        SqlCondition c = visibilityFilter.forFriendBlog(viewer(friend), author);

        String plan =
                transactionTemplate.execute(
                        tx -> {
                            jdbc.execute("SET LOCAL enable_seqscan = off");
                            return new NamedParameterJdbcTemplate(jdbc)
                                    .queryForObject(
                                            "EXPLAIN (FORMAT JSON) SELECT p.id FROM post p JOIN member m"
                                                    + " ON m.id = p.author_id WHERE "
                                                    + c.sql()
                                                    + " ORDER BY p.published_at DESC, p.id DESC LIMIT 10",
                                            c.params(),
                                            String.class);
                        });

        assertThat(plan).contains("\"ix_post_blog_friends\"");
    }
}
