package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.body;
import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 내 글 관리 목록 {@code GET /api/me/posts} (006 T034, US3, FR-001~012, SC-006·007, research R15~R20).
 * 테스트 이름은 인수 시나리오(US3-N)와 1:1이다.
 */
class ManagePostApiIT extends IntegrationTestBase {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final Set<String> ITEM_KEYS =
            Set.of(
                    "id",
                    "title",
                    "status",
                    "visibility",
                    "editing",
                    "hidden",
                    "updatedAt",
                    "publishedAt",
                    "editedAt",
                    "deletedAt",
                    "purgeAt",
                    "viewCount",
                    "likeCount",
                    "commentCount");

    private TrashApi api() {
        return new TrashApi(mockMvc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    /** 회원 A: 임시글 3, 발행 25(공개 20·비공개 5, 숨김 1) 중 1개 휴지통 → 발행 24·휴지통 1. 다른 회원 B 글도 둔다. */
    private record Seed(
            long me, long other, List<Long> drafts, List<Long> published, long trashed) {}

    private Seed seed() {
        long me = members().member().create();
        long other = members().member().create();
        List<Long> drafts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            drafts.add(posts().post(me).title("임시 " + i).updatedAt(at(100 + i)).create());
        }
        List<Long> published = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            PostFixtures.Builder b =
                    posts().post(me).published(i % 5 == 4 ? "PRIVATE" : "PUBLIC").updatedAt(at(i));
            if (i == 10) {
                b.hidden();
            }
            published.add(b.create());
        }
        long trashed = published.remove(0);
        new TrashFixtures(jdbc).trashedAt(trashed, BASE.plus(Duration.ofDays(2)));
        for (int i = 0; i < 4; i++) {
            posts().post(other).published("PUBLIC").updatedAt(at(200 + i)).create();
            posts().post(other).title("남의 임시글").updatedAt(at(300 + i)).create();
        }
        posts().create(other, PostFixtures.State.TRASHED);
        return new Seed(me, other, drafts, published, trashed);
    }

    private static Instant at(int minutes) {
        return BASE.plus(Duration.ofMinutes(minutes));
    }

    private static List<Long> ids(MvcResult result) {
        List<Number> raw = read(result, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void US3_1_기본탭_임시글_글수() throws Exception {
        Seed seed = seed();
        Cookie session = TestLogin.loginAs(mockMvc, seed.me());

        MvcResult result = api().list(session, null);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(ids(result))
                .containsExactly(seed.drafts().get(2), seed.drafts().get(1), seed.drafts().get(0));
        Map<String, Object> counts = read(result, "$.counts");
        assertThat(counts)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("drafts", 3, "published", 24, "trash", 1));
        assertThat((Object) read(result, "$.nextCursor")).isNull();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void US3_2_다른회원_값_무시() throws Exception {
        Seed seed = seed();
        Cookie session = TestLogin.loginAs(mockMvc, seed.me());

        MvcResult result =
                api().list(
                                session,
                                "tab=drafts&authorId="
                                        + seed.other()
                                        + "&memberId="
                                        + seed.other());

        assertThat(ids(result)).containsExactlyInAnyOrderElementsOf(seed.drafts());
        Map<String, Object> counts = read(result, "$.counts");
        assertThat(counts.get("drafts")).isEqualTo(3);
    }

    @Test
    void US3_3_비회원_401() throws Exception {
        MvcResult result = api().list(null, "tab=trash");

        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void US3_4_발행글_25개_더보기() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        List<Long> expected = new ArrayList<>();
        // 같은 updated_at 글을 섞는다 (5개씩 같은 시각)
        for (int i = 0; i < 25; i++) {
            expected.add(posts().post(me).published("PUBLIC").updatedAt(at(i / 5)).create());
        }
        List<Long> ordered =
                jdbc.queryForList(
                        "SELECT id FROM post WHERE author_id = ? ORDER BY updated_at DESC, id DESC",
                        Long.class,
                        me);

        MvcResult first = api().list(session, "tab=published&size=100");
        String cursor = read(first, "$.nextCursor");
        MvcResult second = api().list(session, "tab=published&cursor=" + enc(cursor));

        assertThat(ids(first)).hasSize(20);
        assertThat(cursor).isNotNull().matches("[A-Za-z0-9_-]+");
        assertThat(ids(second)).hasSize(5);
        assertThat((Object) read(second, "$.nextCursor")).isNull();
        assertThat((Object) read(second, "$.counts")).isNull();
        List<Long> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        assertThat(new HashSet<>(all)).hasSize(25);
        assertThat(all).isEqualTo(ordered);
        assertThat(all).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void US3_5_비공개_필터() throws Exception {
        Seed seed = seed();
        Cookie session = TestLogin.loginAs(mockMvc, seed.me());

        MvcResult privates = api().list(session, "tab=published&visibility=private");
        MvcResult publics = api().list(session, "tab=published&visibility=public");
        MvcResult draftsIgnored = api().list(session, "tab=drafts&visibility=private");
        MvcResult trashIgnored = api().list(session, "tab=trash&visibility=friends");

        List<String> privateVis = read(privates, "$.items[*].visibility");
        assertThat(privateVis).hasSize(5).containsOnly("PRIVATE");
        List<String> publicVis = read(publics, "$.items[*].visibility");
        assertThat(publicVis).hasSize(19).containsOnly("PUBLIC");
        Map<String, Object> counts = read(privates, "$.counts");
        assertThat(counts.get("published")).isEqualTo(24);
        assertThat(ids(draftsIgnored)).hasSize(3);
        assertThat(status(trashIgnored)).isEqualTo(200);
        assertThat(ids(trashIgnored)).containsExactly(seed.trashed());
    }

    @Test
    void US3_6_상태_표시_필드() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long editing =
                posts().post(me).published("PUBLIC").draft("고치는 중", "본문").updatedAt(at(5)).create();
        long plain = posts().post(me).published("PUBLIC").updatedAt(at(4)).create();
        long trashedDraft = posts().post(me).title("지운 임시글").create();
        long trashedPublished = posts().create(me, PostFixtures.State.PUBLISHED_PRIVATE);
        long sameTime = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        TrashFixtures fixtures = new TrashFixtures(jdbc);
        Instant deleted = Instant.parse("2026-10-02T05:03:12.123456Z");
        fixtures.trashedAt(trashedDraft, deleted.minusSeconds(60));
        fixtures.trashedAt(trashedPublished, deleted);
        fixtures.trashedAt(sameTime, deleted);

        MvcResult published = api().list(session, "tab=published");
        MvcResult trash = api().list(session, "tab=trash");

        assertThat(ids(published)).containsExactly(editing, plain);
        List<Boolean> editingFlags = read(published, "$.items[*].editing");
        assertThat(editingFlags).containsExactly(true, false);
        assertThat(ids(trash)).containsExactly(sameTime, trashedPublished, trashedDraft);
        List<String> statuses = read(trash, "$.items[*].status");
        assertThat(statuses).containsExactly("PUBLISHED", "PUBLISHED", "DRAFT");
        Map<String, Object> item = read(trash, "$.items[1]");
        assertThat(Instant.parse((String) item.get("deletedAt"))).isEqualTo(deleted);
        assertThat(Instant.parse((String) item.get("purgeAt")))
                .isEqualTo(deleted.plus(Duration.ofDays(30)));
        Map<String, Object> normal = read(published, "$.items[0]");
        assertThat(normal.get("deletedAt")).isNull();
        assertThat(normal.get("purgeAt")).isNull();
    }

    @Test
    void US3_7_숨긴글_발행탭에_hidden_true() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long hidden = posts().create(me, PostFixtures.State.HIDDEN);

        MvcResult result = api().list(session, "tab=published");

        assertThat(ids(result)).containsExactly(hidden);
        assertThat((Boolean) read(result, "$.items[0].hidden")).isTrue();
        Map<String, Object> counts = read(result, "$.counts");
        assertThat(counts.get("published")).isEqualTo(1);
    }

    @Test
    void US3_8_발행글_조회수_좋아요수_댓글수() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                posts().post(me)
                        .published("PUBLIC")
                        .editedAt(Instant.now().minusSeconds(60))
                        .create();
        new TrashFixtures(jdbc).counts(postId, 120, 12, 3);

        MvcResult result = api().list(session, "tab=published");

        Map<String, Object> item = read(result, "$.items[0]");
        assertThat(item.get("viewCount")).isEqualTo(120);
        assertThat(item.get("likeCount")).isEqualTo(12);
        assertThat(item.get("commentCount")).isEqualTo(3);
        assertThat(item.get("publishedAt")).isNotNull();
        assertThat(item.get("editedAt")).isNotNull();
    }

    @Test
    void 항목_키는_정해진_14개뿐이고_본문은_없다() throws Exception {
        Seed seed = seed();
        Cookie session = TestLogin.loginAs(mockMvc, seed.me());

        for (String tab : List.of("drafts", "published", "trash")) {
            MvcResult result = api().list(session, "tab=" + tab);
            List<Map<String, Object>> items = read(result, "$.items");
            assertThat(items).as(tab).isNotEmpty();
            for (Map<String, Object> item : items) {
                assertThat(item.keySet()).as(tab).isEqualTo(ITEM_KEYS);
            }
            assertThat(body(result)).doesNotContain("contentMd", "contentHtml", "authorId");
        }
    }

    @Test
    void 모르는_탭은_400_INVALID_TAB() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult result = api().list(session, "tab=all");

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("INVALID_TAB");
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("tab");
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("INVALID_TAB");
        assertThat((String) read(result, "$.message")).doesNotEndWith(".");
    }

    @Test
    void 발행글_탭의_모르는_공개범위는_400_INVALID_VISIBILITY() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MvcResult result = api().list(session, "tab=published&visibility=friends");

        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("INVALID_VISIBILITY");
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("visibility");
    }

    @Test
    void 다른_탭_커서와_풀리지_않는_커서는_400_INVALID_CURSOR() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (int i = 0; i < 21; i++) {
            posts().post(me).published(i % 2 == 0 ? "PUBLIC" : "PRIVATE").updatedAt(at(i)).create();
        }
        String publishedCursor = read(api().list(session, "tab=published"), "$.nextCursor");
        assertThat(publishedCursor).isNotNull();

        for (String query :
                List.of(
                        "tab=drafts&cursor=" + enc(publishedCursor),
                        "tab=trash&cursor=" + enc(publishedCursor),
                        "tab=published&visibility=private&cursor=" + enc(publishedCursor),
                        "tab=drafts&cursor=abc")) {
            MvcResult result = api().list(session, query);
            assertThat(status(result)).as(query).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
    }

    @Test
    void 휴지통_별칭은_같은_본문() throws Exception {
        Seed seed = seed();
        Cookie session = TestLogin.loginAs(mockMvc, seed.me());

        MvcResult alias = mockMvc.perform(get("/api/me/trash").cookie(session)).andReturn();
        MvcResult main = api().list(session, "tab=trash");

        assertThat(status(alias)).isEqualTo(200);
        assertThat(body(alias)).isEqualTo(body(main));
        assertThat(alias.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
    }

    @Test
    void 이메일_인증_전은_200_탈퇴_유예는_403() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        assertThat(status(api().list(TestLogin.loginAs(mockMvc, unverified), "tab=drafts")))
                .isEqualTo(200);

        long withdrawn = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, withdrawn);
        posts().withdraw(withdrawn);
        MvcResult result = api().list(session, "tab=drafts");
        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");

        long suspended = members().member().create();
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(Duration.ofDays(1)), "006 테스트");
        MvcResult s = api().list(suspendedSession, "tab=drafts");
        assertThat(status(s)).isEqualTo(403);
        assertThat((String) read(s, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
    }
}
