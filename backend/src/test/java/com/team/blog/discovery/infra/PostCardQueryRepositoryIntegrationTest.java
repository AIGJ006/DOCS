package com.team.blog.discovery.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.CardFilter;
import com.team.blog.discovery.application.PostListCursor.CursorKey;
import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.SqlCapture;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 카드 조회 SQL 1번 (005 T009, SC-002, C-READ-1 #6, research R-06·R-07, Edge Cases 동순위). 실제
 * PostgreSQL(원칙 VIII).
 */
class PostCardQueryRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired private PostCardQueryRepository repository;
    @Autowired private VisibilityFilter visibilityFilter;
    @Autowired private PostQueryRepository postQueryRepository;
    @Autowired private DataSource dataSource;

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private static List<String> titles(List<PostCardRow> rows) {
        return rows.stream().map(PostCardRow::title).toList();
    }

    @Test
    void 공개_노출_글만_최신순으로() {
        List<PostCardRow> rows =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 50);

        assertThat(titles(rows)).containsExactlyElementsOf(PostReadingFixture.HOME_TITLES);
    }

    @Test
    void limit만큼만_읽는다() {
        List<PostCardRow> rows =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 10);

        assertThat(titles(rows))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES.subList(0, 10));
    }

    @Test
    void 커서_이후만_읽고_같은_마이크로초_두_글이_경계에서_빠지지_않는다() {
        // 9번째("B 같은 시각 1") 뒤부터 → 10번째("B 같은 시각 2")가 같은 시각이어도 나와야 한다
        List<PostCardRow> first =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 9);
        PostCardRow last = first.get(8);
        assertThat(last.title()).isEqualTo("B 같은 시각 1");

        List<PostCardRow> next =
                repository.findCards(
                        Viewer.anonymous(),
                        CardFilter.all(),
                        new CursorKey(last.firstPublicAt(), last.id()),
                        50);

        assertThat(titles(next))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES.subList(9, 20));
        assertThat(next.get(0).firstPublicAt()).isEqualTo(last.firstPublicAt());
    }

    @Test
    void 끝까지_이어_받으면_중복도_누락도_없다() {
        List<String> seen = new ArrayList<>();
        CursorKey after = null;
        for (int page = 0; page < 10; page++) {
            List<PostCardRow> rows =
                    repository.findCards(Viewer.anonymous(), CardFilter.all(), after, 3);
            if (rows.isEmpty()) {
                break;
            }
            seen.addAll(titles(rows));
            PostCardRow last = rows.get(rows.size() - 1);
            after = new CursorKey(last.firstPublicAt(), last.id());
        }
        assertThat(seen).containsExactlyElementsOf(PostReadingFixture.HOME_TITLES);
    }

    @Test
    void 작성자_조건이면_그_사람의_공개_글만() {
        long a = fixture.memberId("A");

        List<PostCardRow> rows =
                repository.findCards(Viewer.anonymous(), CardFilter.author(a), null, 50);

        assertThat(titles(rows)).containsExactlyElementsOf(PostReadingFixture.A_BLOG_TITLES);
        assertThat(rows).allSatisfy(r -> assertThat(r.handle()).isEqualTo("kim755030"));
    }

    @Test
    void 작성자_본인이_봐도_비공개_숨김_글은_없다() {
        long a = fixture.memberId("A");
        Viewer author = new Viewer(a, com.team.blog.account.domain.Role.USER, null, true);

        assertThat(titles(repository.findCards(author, CardFilter.author(a), null, 50)))
                .containsExactlyElementsOf(PostReadingFixture.A_BLOG_TITLES);
        assertThat(titles(repository.findCards(author, CardFilter.all(), null, 50)))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES);
    }

    @Test
    void 카드_값과_프로필_사진은_썸네일_키() {
        List<PostCardRow> rows =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 50);

        PostCardRow republished = rows.get(0);
        assertThat(republished.title()).isEqualTo("JPA N+1 정리");
        assertThat(republished.likeCount()).isEqualTo(12);
        assertThat(republished.commentCount()).isEqualTo(3);
        assertThat(republished.nickname()).isEqualTo("김민서");
        assertThat(republished.profileKey()).isEqualTo(PostReadingFixture.A_PROFILE_THUMB_KEY);
        assertThat(republished.firstPublicAt().toString()).isEqualTo("2026-09-28T10:00:00.000001Z");

        PostCardRow b1 = rows.get(1);
        assertThat(b1.handle()).isEqualTo("na_ms");
        assertThat(b1.profileKey()).isNull();

        PostCardRow thumb =
                rows.stream().filter(r -> r.title().equals("사진이 있는 글")).findFirst().orElseThrow();
        assertThat(thumb.thumbnailUrl())
                .isEqualTo("http://localhost:9000/blog/images/2026/09/3f2a9c1e_thumb.webp");
        PostCardRow a12 = rows.get(19);
        assertThat(a12.excerpt()).isNull();
    }

    @Test
    void 썸네일이_없는_옛_프로필_사진은_원본_키() {
        jdbc.update("UPDATE image SET thumb_storage_key = NULL WHERE purpose = 'PROFILE'");

        PostCardRow first =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 1).get(0);

        assertThat(first.profileKey()).isEqualTo(PostReadingFixture.A_PROFILE_ORIGINAL_KEY);
    }

    @Test
    void 떼어낸_프로필_사진은_쓰지_않는다() {
        jdbc.update("UPDATE image SET detached_at = now() WHERE purpose = 'PROFILE'");

        PostCardRow first =
                repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 1).get(0);

        assertThat(first.profileKey()).isNull();
    }

    @Test
    void 요청_한_번에_SQL_한_번이고_본문_컬럼을_읽지_않는다() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 10);
            assertThat(scope.count()).isEqualTo(1);
        }

        SqlCapture capture = new SqlCapture(dataSource);
        PostCardQueryRepository captured =
                new PostCardQueryRepository(JdbcClient.create(capture), visibilityFilter);
        CursorKey after =
                new CursorKey(
                        java.time.OffsetDateTime.parse("2026-09-20T10:00:00.123456Z"), 1_000_000);
        captured.findCards(Viewer.anonymous(), CardFilter.author(fixture.memberId("A")), after, 10);

        assertThat(capture.statements()).hasSize(1);
        String sql = capture.statements().get(0);
        assertThat(sql).doesNotContain("content_md").doesNotContain("content_html");
        assertThat(sql)
                .contains(
                        "p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL"
                                + " AND p.hidden_at IS NULL");
        assertThat(sql).contains("(p.first_public_at, p.id) <");
    }

    @Test
    void 블로그_공개_글_수는_목록과_같은_조건() {
        assertThat(
                        postQueryRepository.countListedByAuthor(
                                Viewer.anonymous(), fixture.memberId("A")))
                .isEqualTo(12);
        assertThat(
                        postQueryRepository.countListedByAuthor(
                                Viewer.anonymous(), fixture.memberId("B")))
                .isEqualTo(8);
        assertThat(
                        postQueryRepository.countListedByAuthor(
                                Viewer.anonymous(), fixture.memberId("C")))
                .isZero();
        assertThat(
                        postQueryRepository.countListedByAuthor(
                                Viewer.anonymous(), fixture.memberId("D")))
                .isZero();
    }
}
