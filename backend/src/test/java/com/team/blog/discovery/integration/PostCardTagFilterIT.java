package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.CardFilter;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardRow;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.support.TagFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 카드 조회의 태그 조건 (008 T008, research R6). {@code CardFilter(null, tagId)}는 그 태그 글만, {@code
 * CardFilter(ownerId, tagId)}는 그 블로그의 그 태그 글만이고, 태그 없는 호출은 005 결과 그대로다(005 {@code
 * PostCardQueryRepositoryIntegrationTest}·{@code HomeListIntegrationTest}·{@code
 * BlogPageIntegrationTest}가 함께 확인).
 */
class PostCardTagFilterIT extends IntegrationTestBase {

    @Autowired private PostCardQueryRepository repository;

    private TagFixtures tags;
    private long kim;
    private long na;
    private long kimSpring1;
    private long kimSpring2;
    private long kimJpa;
    private long naSpring;
    private long kimPrivateSpring;

    @BeforeEach
    void seed() {
        tags = new TagFixtures(jdbc);
        PostFixtures posts = new PostFixtures(jdbc);
        kim = members().member().create();
        na = members().member().create();
        Instant base = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MICROS);
        kimSpring1 =
                posts.post(kim).published("PUBLIC").firstPublicAt(base).title("김 스프링 1").create();
        kimSpring2 =
                posts.post(kim)
                        .published("PUBLIC")
                        .firstPublicAt(base.plusSeconds(10))
                        .title("김 스프링 2")
                        .create();
        kimJpa =
                posts.post(kim)
                        .published("PUBLIC")
                        .firstPublicAt(base.plusSeconds(20))
                        .title("김 JPA")
                        .create();
        naSpring =
                posts.post(na)
                        .published("PUBLIC")
                        .firstPublicAt(base.plusSeconds(30))
                        .title("나 스프링")
                        .create();
        kimPrivateSpring = posts.post(kim).published("PRIVATE").title("김 비공개 스프링").create();
        tags.attach(kimSpring1, "spring", "jpa");
        tags.attach(kimSpring2, "spring");
        tags.attach(kimJpa, "jpa");
        tags.attach(naSpring, "spring");
        tags.attach(kimPrivateSpring, "spring");
    }

    private static List<Long> ids(List<PostCardRow> rows) {
        return rows.stream().map(PostCardRow::id).toList();
    }

    @Test
    void 태그_조건이면_그_태그의_공개_글만_최신순() {
        long spring = tags.tagId("spring");

        assertThat(
                        ids(
                                repository.findCards(
                                        Viewer.anonymous(),
                                        new CardFilter(null, spring),
                                        null,
                                        50)))
                .containsExactly(naSpring, kimSpring2, kimSpring1);
    }

    @Test
    void 블로그와_태그_조건이면_그_블로그의_그_태그_글만() {
        long spring = tags.tagId("spring");
        long jpa = tags.tagId("jpa");

        assertThat(
                        ids(
                                repository.findCards(
                                        Viewer.anonymous(), new CardFilter(kim, spring), null, 50)))
                .containsExactly(kimSpring2, kimSpring1);
        assertThat(
                        ids(
                                repository.findCards(
                                        Viewer.anonymous(), new CardFilter(kim, jpa), null, 50)))
                .containsExactly(kimJpa, kimSpring1);
        assertThat(ids(repository.findCards(Viewer.anonymous(), new CardFilter(na, jpa), null, 50)))
                .isEmpty();
    }

    @Test
    void 아무_글에도_안_쓰인_태그는_빈_목록() {
        long unused = tags.tagId("unused");

        assertThat(repository.findCards(Viewer.anonymous(), new CardFilter(null, unused), null, 50))
                .isEmpty();
    }

    @Test
    void 태그_없는_호출은_기존_결과_그대로() {
        assertThat(ids(repository.findCards(Viewer.anonymous(), CardFilter.all(), null, 50)))
                .containsExactly(naSpring, kimJpa, kimSpring2, kimSpring1);
        assertThat(ids(repository.findCards(Viewer.anonymous(), CardFilter.author(kim), null, 50)))
                .containsExactly(kimJpa, kimSpring2, kimSpring1);
        assertThat(repository.cardQuery(Viewer.anonymous(), CardFilter.all(), null, 10).sql())
                .doesNotContain("post_tag");
    }

    @Test
    void 태그_조건은_EXISTS_한_줄이고_SQL은_한_번이다() {
        var query = repository.cardQuery(Viewer.anonymous(), new CardFilter(null, 7L), null, 10);

        assertThat(query.sql())
                .contains(
                        "EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = :tagId)");
        assertThat(query.params()).containsEntry("tagId", 7L);
    }
}
