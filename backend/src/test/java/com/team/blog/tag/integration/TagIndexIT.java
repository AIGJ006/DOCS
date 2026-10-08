package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.support.TagApi;
import com.team.blog.tag.support.TagFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 전체 태그 목록 (008 T047, US4 #1~#3, FR-029, research R8). 공개 글 수 많은 순(같으면 이름 순) 상위 {@code
 * blog.tag.top-limit}(100)개, 캐시 없음 — 공개에서 빠진 글은 바로 다음 요청에 반영된다.
 */
class TagIndexIT extends IntegrationTestBase {

    private TagApi api() {
        return new TagApi(mockMvc);
    }

    private TagFixtures tags() {
        return new TagFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private List<Map<String, Object>> top(String uri) throws Exception {
        MvcResult result = api().getRaw(null, uri);
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
        return read(result, "$.items");
    }

    private static List<Object> names(List<Map<String, Object>> items) {
        return items.stream().map(i -> i.get("name")).toList();
    }

    @Test
    void 공개_글_수_순_상위_100() throws Exception {
        long author = members().member().create();
        // 3개짜리 2종(이름 순 a3-x, a3-y), 2개짜리 1종, 그다음 1개짜리 100종
        for (int i = 0; i < 3; i++) {
            tags().attach(
                            posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                            "a3-y",
                            "a3-x");
        }
        for (int i = 0; i < 2; i++) {
            tags().attach(posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC), "b2");
        }
        for (int i = 0; i < 100; i++) {
            tags().attach(
                            posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                            String.format("one-%03d", i));
        }

        List<Map<String, Object>> items = top("/api/tags?limit=500");

        assertThat(items).hasSize(100);
        assertThat(names(items).subList(0, 4)).containsExactly("a3-x", "a3-y", "b2", "one-000");
        assertThat(items.get(0)).containsEntry("postCount", 3);
        assertThat(items.get(2)).containsEntry("postCount", 2);
        assertThat(items.get(99)).containsEntry("name", "one-096").containsEntry("postCount", 1);
    }

    @Test
    void 공개_글_수_0인_태그는_없다() throws Exception {
        long author = members().member().create();
        tags().attach(posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC), "java");
        for (PostFixtures.State state :
                List.of(
                        PostFixtures.State.PUBLISHED_PRIVATE,
                        PostFixtures.State.DRAFT,
                        PostFixtures.State.TRASHED,
                        PostFixtures.State.HIDDEN)) {
            tags().attach(posts().create(author, state), "hidden-" + state.name().toLowerCase());
        }
        long gone = members().member().create();
        tags().attach(posts().create(gone, PostFixtures.State.AUTHOR_WITHDRAWN), "gone");
        tags().tagId("unused");

        assertThat(names(top("/api/tags"))).containsExactly("java");
    }

    @Test
    void 없으면_빈_목록() throws Exception {
        assertThat(top("/api/tags")).isEmpty();
    }

    @Test
    void 비공개로_바꾸면_다음_요청에서_빠진다() throws Exception {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long toPrivate = attachPublic(author, "to-private");
        long toTrash = attachPublic(author, "to-trash");
        long toHidden = attachPublic(author, "to-hidden");
        long other = members().member().create();
        attachPublic(other, "to-withdrawn");
        assertThat(names(top("/api/tags")))
                .containsExactlyInAnyOrder("to-private", "to-trash", "to-hidden", "to-withdrawn");

        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", toPrivate);
        assertThat(names(top("/api/tags"))).doesNotContain("to-private").hasSize(3);

        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", toTrash);
        assertThat(names(top("/api/tags"))).doesNotContain("to-trash").hasSize(2);

        jdbc.update(
                "UPDATE post SET hidden_at = now(), hidden_by = ?, hidden_reason = 'SPAM'"
                        + " WHERE id = ?",
                admin,
                toHidden);
        assertThat(names(top("/api/tags"))).doesNotContain("to-hidden").hasSize(1);

        posts().withdraw(other);
        assertThat(top("/api/tags")).isEmpty();
    }

    private long attachPublic(long author, String tag) {
        long id = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        tags().attach(id, tag);
        return id;
    }
}
