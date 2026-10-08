package com.team.blog.discovery.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 번호로 카드 읽기 (012 T005, research R2) — 공용 조건, 빈 목록, SQL 1번. */
class PostCardQueryRepositoryByIdsIT extends IntegrationTestBase {

    @Autowired PostCardQueryRepository cards;

    @Test
    void 볼_수_없는_글은_빠지고_SQL은_1번() {
        PostFixtures posts = new PostFixtures(jdbc);
        long author = members().member().handle("byids").create();
        long visible1 = posts.create(author, State.PUBLISHED_PUBLIC);
        long visible2 = posts.create(author, State.EDITING);
        long privatePost = posts.create(author, State.PUBLISHED_PRIVATE);
        long draft = posts.create(author, State.DRAFT);
        long trashed = posts.create(author, State.TRASHED);
        long hidden = posts.create(author, State.HIDDEN);
        long other = members().member().create();
        long withdrawn = posts.create(other, State.AUTHOR_WITHDRAWN);

        long missing = posts.nonexistentId();
        List<PostCardRow> rows;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            rows =
                    cards.findCardsByIds(
                            List.of(
                                    visible1,
                                    privatePost,
                                    draft,
                                    trashed,
                                    hidden,
                                    withdrawn,
                                    visible2,
                                    missing));
            assertThat(scope.count()).isEqualTo(1);
        }

        assertThat(rows).extracting(PostCardRow::id).containsExactlyInAnyOrder(visible1, visible2);
        PostCardRow row = rows.stream().filter(r -> r.id() == visible1).findFirst().orElseThrow();
        assertThat(row.handle()).isEqualTo("byids");
        assertThat(row.firstPublicAt()).isNotNull();
    }

    @Test
    void 빈_목록이면_SQL_없이_빈_목록() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(cards.findCardsByIds(List.of())).isEmpty();
            assertThat(scope.count()).isZero();
        }
    }
}
