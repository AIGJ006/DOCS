package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.PostModerationService;
import com.team.blog.post.application.PostModerationService.State;
import com.team.blog.post.application.PostSnapshot;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 014가 부르는 글 숨김·해제·스냅샷·현재 상태 (014 T010, research R7). */
class PostModerationServiceIT extends IntegrationTestBase {

    @Autowired PostModerationService moderation;

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    @Test
    void 숨김은_hidden_칸을_채우고_멱등이며_수와_시각은_그대로다() {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long id = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update("UPDATE post SET like_count = 12, comment_count = 3 WHERE id = ?", id);
        Map<String, Object> before =
                jdbc.queryForMap(
                        "SELECT like_count, comment_count, first_public_at, updated_at FROM post"
                                + " WHERE id = ?",
                        id);

        assertThat(moderation.hide(id, admin, "SPAM", Instant.now())).isTrue();
        assertThat(moderation.hide(id, admin, "ABUSE", Instant.now())).isFalse();

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT hidden_at IS NOT NULL AS h, hidden_by, hidden_reason, like_count,"
                                + " comment_count, first_public_at, updated_at FROM post WHERE id = ?",
                        id);
        assertThat(row.get("h")).isEqualTo(true);
        assertThat(row.get("hidden_by")).isEqualTo(admin);
        assertThat(row.get("hidden_reason")).isEqualTo("SPAM");
        for (String col : before.keySet()) {
            assertThat(row.get(col)).as(col).isEqualTo(before.get(col));
        }

        assertThat(moderation.unhide(id)).isTrue();
        assertThat(moderation.unhide(id)).isFalse();
        Map<String, Object> after =
                jdbc.queryForMap(
                        "SELECT hidden_at, hidden_by, hidden_reason, like_count, comment_count,"
                                + " first_public_at, updated_at FROM post WHERE id = ?",
                        id);
        assertThat(after.get("hidden_at")).isNull();
        assertThat(after.get("hidden_by")).isNull();
        assertThat(after.get("hidden_reason")).isNull();
        for (String col : before.keySet()) {
            assertThat(after.get(col)).as(col).isEqualTo(before.get(col));
        }
    }

    @Test
    void 휴지통_글도_숨기고_없는_글은_예외() {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long trashed = posts().create(author, PostFixtures.State.TRASHED);

        assertThat(moderation.hide(trashed, admin, "SPAM", Instant.now())).isTrue();
        long missing = posts().nonexistentId();
        assertThatThrownBy(() -> moderation.hide(missing, admin, "SPAM", Instant.now()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> moderation.unhide(missing)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void 스냅샷은_제목과_앞_2000_코드_포인트_이모지_경계를_자르지_않는다() {
        long author = members().member().create();
        String body = "가".repeat(1999) + "😀😀😀";
        long id = posts().post(author).title("신고될 제목").published("PUBLIC").create();
        jdbc.update("UPDATE post SET content_md = ? WHERE id = ?", body, id);

        PostSnapshot s = moderation.snapshot(id).orElseThrow();
        assertThat(s.title()).isEqualTo("신고될 제목");
        assertThat(s.authorId()).isEqualTo(author);
        assertThat(s.contentHead().codePointCount(0, s.contentHead().length())).isEqualTo(2000);
        assertThat(s.contentHead()).endsWith("가😀");
        assertThat(s.hidden()).isFalse();
        assertThat(s.trashed()).isFalse();
        assertThat(moderation.snapshot(posts().nonexistentId())).isEmpty();
    }

    @Test
    void 현재_상태_6가지() {
        long author = members().member().create();
        long leaving = members().member().create();
        long pub = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long priv = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        long draft = posts().create(author, PostFixtures.State.DRAFT);
        long trashed = posts().create(author, PostFixtures.State.TRASHED);
        long hidden = posts().create(author, PostFixtures.State.HIDDEN);
        long hiddenTrashed = posts().post(author).published("PUBLIC").trashed().hidden().create();
        long withdrawn = posts().create(leaving, PostFixtures.State.AUTHOR_WITHDRAWN);

        assertThat(moderation.currentState(pub)).isEqualTo(State.PUBLIC);
        assertThat(moderation.currentState(priv)).isEqualTo(State.PRIVATE);
        assertThat(moderation.currentState(draft)).isEqualTo(State.PRIVATE);
        assertThat(moderation.currentState(trashed)).isEqualTo(State.TRASHED);
        assertThat(moderation.currentState(hidden)).isEqualTo(State.HIDDEN);
        assertThat(moderation.currentState(hiddenTrashed)).isEqualTo(State.HIDDEN);
        assertThat(moderation.currentState(withdrawn)).isEqualTo(State.AUTHOR_WITHDRAWN);
        assertThat(moderation.currentState(posts().nonexistentId())).isEqualTo(State.GONE);
    }

    @Test
    void hiddenOf는_있는_글만_돌려준다() {
        long author = members().member().create();
        long pub = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long hidden = posts().create(author, PostFixtures.State.HIDDEN);
        long missing = posts().nonexistentId();

        assertThat(moderation.hiddenOf(List.of(pub, hidden, missing)))
                .containsOnly(Map.entry(pub, false), Map.entry(hidden, true));
        assertThat(moderation.hiddenOf(List.of())).isEmpty();
    }
}
