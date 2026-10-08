package com.team.blog.discovery.support;

import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 005 글 읽기 픽스처 로더·행위자 도우미 (tasks T005). {@code fixtures/post-reading.sql}(quickstart §1)을 적용하고 글·회원
 * 번호를 이름으로 찾는다. 테이블은 {@code IntegrationTestBase}가 각 테스트 전에 비우므로 테스트의 {@code @BeforeEach}에서 {@link
 * #load}를 부른다.
 *
 * <pre>{@code
 * PostReadingFixture f = PostReadingFixture.load(jdbc);
 * long id = f.postOf("A", "republished");
 * Cookie a = f.loginAs(mockMvc, "A");
 * }</pre>
 *
 * 추가 회원·글은 001 {@link MemberFixtures}, 004 {@link PostFixtures}로 만든다. SQL 수는 001 {@code
 * support.SqlCounter}로 센다.
 */
public final class PostReadingFixture {

    public static final String SCRIPT = "fixtures/post-reading.sql";

    public static final String A_HANDLE = "kim755030";
    public static final String B_HANDLE = "na_ms";
    public static final String C_HANDLE = "kang_sc";
    public static final String D_HANDLE = "empty_d";
    public static final String ADMIN_HANDLE = "admin_ops";

    /** 회원 A 프로필 사진 키 (썸네일·원본). */
    public static final String A_PROFILE_THUMB_KEY = "profiles/2026/09/a1b2c3d4_thumb.webp";

    public static final String A_PROFILE_ORIGINAL_KEY = "profiles/2026/09/a1b2c3d4.png";

    private static final Map<String, String> HANDLES =
            Map.of(
                    "A", A_HANDLE,
                    "B", B_HANDLE,
                    "C", C_HANDLE,
                    "D", D_HANDLE,
                    "ADMIN", ADMIN_HANDLE);

    private static final Map<String, String> TITLES =
            Map.ofEntries(
                    Map.entry("A:republished", "JPA N+1 정리"),
                    Map.entry("A:code", "코드 블록이 있는 글"),
                    Map.entry("A:editing", "수정 중인 글"),
                    Map.entry("A:xss", "제목 \"><script>alert(1)</script>"),
                    Map.entry("A:thumb", "사진이 있는 글"),
                    Map.entry("A:wentPublic", "나중에 공개한 글"),
                    Map.entry("A:private1", "비공개 글 1"),
                    Map.entry("A:private2", "비공개 글 2"),
                    Map.entry("A:private3", "비공개 글 3"),
                    Map.entry("A:draft", "임시글"),
                    Map.entry("A:trashed", "휴지통 글"),
                    Map.entry("A:hidden", "숨겨진 글"),
                    Map.entry("B:same1", "B 같은 시각 1"),
                    Map.entry("B:same2", "B 같은 시각 2"),
                    Map.entry("C:c1", "C1 글"),
                    Map.entry("C:c2", "C2 글"));

    /** 홈(전체 목록)에 나와야 하는 20개 글의 제목 (first_public_at DESC, id DESC). */
    public static final List<String> HOME_TITLES =
            List.of(
                    "JPA N+1 정리",
                    "B1 글",
                    "코드 블록이 있는 글",
                    "수정 중인 글",
                    "B2 글",
                    "제목 \"><script>alert(1)</script>",
                    "사진이 있는 글",
                    "B3 글",
                    "B 같은 시각 1",
                    "B 같은 시각 2",
                    "나중에 공개한 글",
                    "A07 글",
                    "B4 글",
                    "A08 글",
                    "A09 글",
                    "B5 글",
                    "A10 글",
                    "A11 글",
                    "B6 글",
                    "A12 글");

    /** 회원 A 블로그에 나와야 하는 12개 글의 제목. */
    public static final List<String> A_BLOG_TITLES =
            HOME_TITLES.stream().filter(t -> !t.startsWith("B") && !t.startsWith("C")).toList();

    private final JdbcTemplate jdbc;

    private PostReadingFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 픽스처 SQL을 적용한다 (테이블이 비어 있어야 한다). */
    public static PostReadingFixture load(JdbcTemplate jdbc) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(jdbc.getDataSource());
        return new PostReadingFixture(jdbc);
    }

    /** 회원 번호. {@code who}는 {@code A}·{@code B}·{@code C}·{@code D}·{@code ADMIN} 또는 handle. */
    public long memberId(String who) {
        String handle = HANDLES.getOrDefault(who, who);
        return jdbc.queryForObject("SELECT id FROM member WHERE handle = ?", Long.class, handle);
    }

    /** 글 번호. {@code name}은 별칭(예: {@code republished}) 또는 제목 그대로. */
    public long postOf(String who, String name) {
        String title = TITLES.getOrDefault(who + ":" + name, name);
        return jdbc.queryForObject(
                "SELECT p.id FROM post p JOIN member m ON m.id = p.author_id"
                        + " WHERE m.handle = ? AND p.title = ?",
                Long.class,
                HANDLES.getOrDefault(who, who),
                title);
    }

    /** 제목 → 글 번호. */
    public long postByTitle(String title) {
        return jdbc.queryForObject("SELECT id FROM post WHERE title = ?", Long.class, title);
    }

    /** 제목 목록 → 글 번호 목록 (순서 유지). */
    public List<Long> idsOf(List<String> titles) {
        return titles.stream().map(this::postByTitle).toList();
    }

    /** 픽스처 회원으로 로그인한 세션. */
    public Cookie loginAs(MockMvc mockMvc, String who) {
        return TestLogin.loginAs(mockMvc, memberId(who));
    }

    /** 이 테스트용 추가 회원 (작성자 아님): 이메일 인증 전. */
    public long unverifiedMember() {
        return new MemberFixtures(jdbc).member().emailVerified(false).create();
    }

    /** 이 테스트용 추가 회원: 정지. */
    public long suspendedMember() {
        MemberFixtures members = new MemberFixtures(jdbc);
        long id = members.member().create();
        members.suspend(id, Instant.now().plus(Duration.ofDays(7)), "005 픽스처");
        return id;
    }

    /** 지금 시각으로 공개 발행한 새 글 (004 {@link PostFixtures}). */
    public long publishNow(String who, String title) {
        Instant now = Instant.now();
        return new PostFixtures(jdbc)
                .post(memberId(who))
                .title(title)
                .published("PUBLIC")
                .firstPublicAt(now)
                .createdAt(now)
                .create();
    }

    public void trash(long postId) {
        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", postId);
    }

    /** 공개 → 비공개 (first_public_at은 그대로 남는다). */
    public void makePrivate(long postId) {
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", postId);
    }

    public long viewCount(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM post WHERE id = ?", Long.class, postId);
    }
}
