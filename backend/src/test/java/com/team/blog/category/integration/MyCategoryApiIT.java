package com.team.blog.category.integration;

import static com.team.blog.category.support.CategoryApi.body;
import static com.team.blog.category.support.CategoryApi.read;
import static com.team.blog.category.support.CategoryApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.category.support.CategoryApi;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 내 카테고리 관리 (017 US1 #1~#12, FR-002~FR-015). */
class MyCategoryApiIT extends IntegrationTestBase {

    private CategoryApi api;
    private long me;
    private Cookie session;

    @BeforeEach
    void setUp() {
        api = new CategoryApi(mockMvc);
        me = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
    }

    private String code(MvcResult result) {
        return read(result, "$.code");
    }

    private List<Map<String, Object>> items() throws Exception {
        MvcResult result = api.myList(session);
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        return read(result, "$.items");
    }

    private List<String> topNames() throws Exception {
        return items().stream().map(i -> (String) i.get("name")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> childNames(String parent) throws Exception {
        return items().stream()
                .filter(i -> parent.equals(i.get("name")))
                .flatMap(i -> ((List<Map<String, Object>>) i.get("children")).stream())
                .map(c -> (String) c.get("name"))
                .toList();
    }

    @Test
    void US1_1_2_최상위와_하위를_맨_아래에_만든다() throws Exception {
        MvcResult created = api.create(session, "개발", null);
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        assertThat(created.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat((String) read(created, "$.name")).isEqualTo("개발");
        assertThat((Object) read(created, "$.parentId")).isNull();
        long dev = ((Number) read(created, "$.id")).longValue();
        api.createOk(session, "일상", null);
        api.createOk(session, "Spring", dev);
        api.createOk(session, "React", dev);

        assertThat(topNames()).containsExactly("개발", "일상");
        assertThat(childNames("개발")).containsExactly("Spring", "React");
        assertThat(((Number) read(api.myList(session), "$.maxCount")).intValue()).isEqualTo(100);
    }

    @Test
    void US1_3_하위_아래에는_못_만든다() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long spring = api.createOk(session, "Spring", dev);

        MvcResult result = api.create(session, "Boot", spring);

        assertThat(status(result)).isEqualTo(400);
        assertThat(code(result)).isEqualTo("CATEGORY_DEPTH_EXCEEDED");
    }

    @Test
    void US1_4_같은_상위_안에서만_이름이_겹치면_안_된다() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long life = api.createOk(session, "일상", null);
        api.createOk(session, "Spring", dev);

        MvcResult dup = api.create(session, " spring ", dev);
        assertThat(status(dup)).isEqualTo(409);
        assertThat(code(dup)).isEqualTo("CATEGORY_NAME_DUPLICATED");
        assertThat(status(api.create(session, "Spring", life))).isEqualTo(201);
        assertThat(code(api.create(session, "개발", null))).isEqualTo("CATEGORY_NAME_DUPLICATED");

        // 다른 회원은 같은 이름을 쓸 수 있다
        Cookie other = TestLogin.loginAs(mockMvc, members().member().create());
        assertThat(status(api.create(other, "개발", null))).isEqualTo(201);
    }

    @Test
    void US1_5_이름_칸_오류() throws Exception {
        MvcResult empty = api.create(session, "   ", null);
        assertThat(status(empty)).isEqualTo(400);
        assertThat(code(empty)).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(empty, "$.errors[0].field")).isEqualTo("name");
        assertThat((String) read(empty, "$.errors[0].code")).isEqualTo("CATEGORY_NAME_REQUIRED");

        MvcResult longName = api.create(session, "가".repeat(31), null);
        assertThat((String) read(longName, "$.errors[0].code")).isEqualTo("CATEGORY_NAME_TOO_LONG");

        long dev = api.createOk(session, "개발", null);
        MvcResult rename = api.update(session, dev, "{\"name\":\"\"}");
        assertThat((String) read(rename, "$.errors[0].code")).isEqualTo("CATEGORY_NAME_REQUIRED");
    }

    @Test
    void US1_6_순서_바꾸기() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long life = api.createOk(session, "일상", null);
        long a = api.createOk(session, "A", dev);
        long b = api.createOk(session, "B", dev);

        MvcResult top = api.reorder(session, null, List.of(life, dev));
        assertThat(status(top)).as(body(top)).isEqualTo(200);
        assertThat(topNames()).containsExactly("일상", "개발");

        assertThat(status(api.reorder(session, dev, List.of(b, a)))).isEqualTo(200);
        assertThat(childNames("개발")).containsExactly("B", "A");
    }

    @Test
    void 순서_묶음이_다르면_409() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long life = api.createOk(session, "일상", null);
        long note = api.createOk(session, "메모", null);

        for (List<Long> stale :
                List.of(
                        List.of(dev, life),
                        List.of(dev, life, life),
                        List.of(dev, life, note, 999L))) {
            MvcResult result = api.reorder(session, null, stale);
            assertThat(status(result)).as(stale.toString()).isEqualTo(409);
            assertThat(code(result)).isEqualTo("CATEGORY_ORDER_STALE");
        }
        assertThat(topNames()).containsExactly("개발", "일상", "메모");
    }

    @Test
    void US1_7_다른_상위로_옮기면_맨_아래_최상위로도() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long life = api.createOk(session, "일상", null);
        long spring = api.createOk(session, "Spring", dev);
        api.createOk(session, "React", dev);
        api.createOk(session, "여행", life);

        MvcResult moved = api.update(session, spring, "{\"parentId\":" + life + "}");
        assertThat(status(moved)).as(body(moved)).isEqualTo(200);
        assertThat(childNames("일상")).containsExactly("여행", "Spring");
        assertThat(childNames("개발")).containsExactly("React");

        assertThat(status(api.update(session, spring, "{\"parentId\":null}"))).isEqualTo(200);
        assertThat(topNames()).containsExactly("개발", "일상", "Spring");

        // 이름만 바꾸면 상위·자리는 그대로
        MvcResult renamed = api.update(session, dev, "{\"name\":\"  개발  노트 \"}");
        assertThat((String) read(renamed, "$.name")).isEqualTo("개발 노트");
        assertThat(topNames()).containsExactly("개발 노트", "일상", "Spring");
    }

    @Test
    void US1_8_하위가_있으면_다른_카테고리_아래로_못_간다() throws Exception {
        long dev = api.createOk(session, "개발", null);
        long life = api.createOk(session, "일상", null);
        long spring = api.createOk(session, "Spring", dev);

        assertThat(code(api.update(session, dev, "{\"parentId\":" + life + "}")))
                .isEqualTo("CATEGORY_DEPTH_EXCEEDED");
        // 자기 자신·하위를 상위로
        assertThat(code(api.update(session, life, "{\"parentId\":" + life + "}")))
                .isEqualTo("CATEGORY_DEPTH_EXCEEDED");
        assertThat(code(api.update(session, life, "{\"parentId\":" + spring + "}")))
                .isEqualTo("CATEGORY_DEPTH_EXCEEDED");
        // 옮긴 곳에 같은 이름이 있으면 409
        api.createOk(session, "Spring", life);
        assertThat(code(api.update(session, spring, "{\"parentId\":" + life + "}")))
                .isEqualTo("CATEGORY_NAME_DUPLICATED");
    }

    @Test
    void US1_9_10_하위가_있으면_못_지우고_지우면_글은_분류_없음() throws Exception {
        PostFixtures posts = new PostFixtures(jdbc);
        CategoryFixtures fixtures = new CategoryFixtures(jdbc);
        long dev = api.createOk(session, "개발", null);
        long spring = api.createOk(session, "Spring", dev);
        long react = api.createOk(session, "React", dev);
        long p1 = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long p2 = posts.create(me, PostFixtures.State.DRAFT);
        fixtures.assign(p1, spring);
        fixtures.assign(p2, spring);

        MvcResult blocked = api.delete(session, dev);
        assertThat(status(blocked)).isEqualTo(409);
        assertThat(code(blocked)).isEqualTo("CATEGORY_HAS_CHILDREN");
        assertThat(topNames()).containsExactly("개발");

        // 관리 목록의 글 수는 내 글 전부(임시 포함)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) items().get(0).get("children");
        assertThat(children.get(0)).containsEntry("postCount", 2);
        assertThat(items().get(0)).containsEntry("postCount", 2);

        MvcResult deleted = api.delete(session, spring);
        assertThat(status(deleted)).as(body(deleted)).isEqualTo(204);
        assertThat(fixtures.categoryOf(p1)).isNull();
        assertThat(fixtures.categoryOf(p2)).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post WHERE id IN (?, ?)", Long.class, p1, p2))
                .isEqualTo(2);
        assertThat(childNames("개발")).containsExactly("React");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT position FROM category WHERE id = ?", Integer.class, react))
                .isZero();
    }

    @Test
    void US1_11_개수_상한() throws Exception {
        CategoryFixtures fixtures = new CategoryFixtures(jdbc);
        for (int i = 0; i < 100; i++) {
            fixtures.create(me, null, "c" + i);
        }
        MvcResult result = api.create(session, "하나 더", null);
        assertThat(status(result)).isEqualTo(400);
        assertThat(code(result)).isEqualTo("TOO_MANY_CATEGORIES");
    }

    @Test
    void US1_12_남의_카테고리와_없는_번호는_같은_404() throws Exception {
        long other = members().member().create();
        long othersCategory = new CategoryFixtures(jdbc).create(other, null, "남의 것");

        for (Object id : List.of(othersCategory, 999_999L, "abc")) {
            MvcResult patch = api.update(session, id, "{\"name\":\"x\"}");
            MvcResult delete = api.delete(session, id);
            assertThat(status(patch)).as("patch " + id).isEqualTo(404);
            assertThat(status(delete)).as("delete " + id).isEqualTo(404);
            assertThat(body(patch)).isEqualTo(body(delete));
        }
        // 남의 것을 상위로 고르면 400
        MvcResult asParent = api.create(session, "x", othersCategory);
        assertThat(status(asParent)).isEqualTo(400);
        assertThat(code(asParent)).isEqualTo("INVALID_CATEGORY");
        assertThat((String) read(asParent, "$.errors[0].field")).isEqualTo("parentId");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM category WHERE id = ?",
                                String.class,
                                othersCategory))
                .isEqualTo("남의 것");
    }

    @Test
    void 비회원_401_인증_전_403() throws Exception {
        assertThat(status(api.myList(null))).isEqualTo(401);
        assertThat(status(api.create(null, "x", null))).isEqualTo(401);

        long unverified = members().member().emailVerified(false).create();
        Cookie s = TestLogin.loginAs(mockMvc, unverified);
        MvcResult result = api.create(s, "x", null);
        assertThat(status(result)).isEqualTo(403);
        assertThat(code(result)).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(status(api.myList(s))).isEqualTo(200);
    }
}
