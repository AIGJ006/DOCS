package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.OwnedPost;
import com.team.blog.post.application.PostOwnershipQuery;
import com.team.blog.post.domain.Visibility;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.fixture.PostFixtures;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * post 공개 조회 {@link PostOwnershipQuery#findOwned} (013 T006, research R2). 조건은 002 {@code isOwned}와
 * 같다 — 작성자 본인 + 휴지통 아님. 상태·관리자 숨김은 보지 않는다(편집 중일 수 있음).
 */
class PostOwnershipQueryIT extends IntegrationTestBase {

    @Autowired private PostOwnershipQuery query;

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    @Test
    void 내_공개_비공개_임시_숨김_글은_번호와_공개_범위() {
        long me = members().member().create();
        long pub = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long priv = posts().create(me, PostFixtures.State.PUBLISHED_PRIVATE);
        long draft = posts().post(me).visibility("PRIVATE").create();
        long hidden = posts().create(me, PostFixtures.State.HIDDEN);

        assertThat(query.findOwned(pub, me)).contains(new OwnedPost(pub, Visibility.PUBLIC));
        assertThat(query.findOwned(priv, me)).contains(new OwnedPost(priv, Visibility.PRIVATE));
        assertThat(query.findOwned(draft, me)).contains(new OwnedPost(draft, Visibility.PRIVATE));
        assertThat(query.findOwned(hidden, me)).contains(new OwnedPost(hidden, Visibility.PUBLIC));
    }

    @Test
    void 남의_글_없는_글_휴지통_글은_빈_값() {
        long me = members().member().create();
        long other = members().member().create();
        long others = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        long trashed = posts().create(me, PostFixtures.State.TRASHED);

        assertThat(query.findOwned(others, me)).isEmpty();
        assertThat(query.findOwned(posts().nonexistentId(), me)).isEmpty();
        assertThat(query.findOwned(trashed, me)).isEmpty();
    }

    @Test
    void SQL은_한_번() {
        long me = members().member().create();
        long pub = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        Optional<OwnedPost> found;
        int count;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            found = query.findOwned(pub, me);
            count = scope.count();
        }
        assertThat(found).isPresent();
        assertThat(count).isEqualTo(1);
    }
}
