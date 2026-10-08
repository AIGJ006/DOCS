package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.body;
import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.post.application.PostTrashService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.shared.event.PostTrashed;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 글 삭제 = 휴지통으로 {@code DELETE /api/posts/{postId}} (006 T016~T018, US1, FR-017~026·037·038, research
 * R5~R8). 테스트 이름은 인수 시나리오(US1-N)와 1:1이다.
 */
@Import(PostTestConfig.class)
class PostTrashApiIT extends IntegrationTestBase {

    private static final Duration RETENTION = Duration.ofDays(30);

    @Autowired CommittedEvents events;
    @Autowired PostTrashService trashService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private TrashApi api() {
        return new TrashApi(mockMvc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private TrashFixtures fixtures() {
        return new TrashFixtures(jdbc);
    }

    private AuthoringFixtures authoring() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    // ---- US1-1 ----

    @Test
    void US1_1_공개글_삭제하면_휴지통으로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId =
                posts().post(me)
                        .published("PUBLIC")
                        .updatedAt(instant("2026-10-01T00:00:00.123456Z"))
                        .create();
        Map<String, Object> before = fixtures().row(postId);

        MvcResult result = api().trash(session, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> json = read(result, "$");
        assertThat(json.keySet()).containsExactlyInAnyOrder("trashed", "purgeAt");
        assertThat(json.get("trashed")).isEqualTo(true);
        Instant deletedAt = fixtures().deletedAt(postId);
        assertThat(deletedAt).isNotNull();
        assertThat(Instant.parse((String) json.get("purgeAt")))
                .isEqualTo(deletedAt.plus(RETENTION));
        Map<String, Object> after = fixtures().row(postId);
        for (String column :
                new String[] {
                    "status", "visibility", "first_public_at", "published_at", "edited_at",
                    "updated_at", "hidden_at", "edit_version", "title", "content_md"
                }) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(events.of(PostTrashed.class))
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.postId()).isEqualTo(postId);
                            assertThat(e.authorId()).isEqualTo(me);
                            assertThat(e.trashedAt()).isEqualTo(deletedAt);
                        });
    }

    @Test
    void 숨긴_글과_비공개_글과_작업본_있는_글도_휴지통으로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (PostFixtures.State state :
                new PostFixtures.State[] {
                    PostFixtures.State.HIDDEN,
                    PostFixtures.State.PUBLISHED_PRIVATE,
                    PostFixtures.State.EDITING,
                    PostFixtures.State.DRAFT
                }) {
            long postId =
                    state == PostFixtures.State.DRAFT
                            ? posts().post(me).title("임시 제목").create()
                            : posts().create(me, state);
            MvcResult result = api().trash(session, postId);
            assertThat(status(result)).as(state + " " + body(result)).isEqualTo(200);
            assertThat((Boolean) read(result, "$.trashed")).isTrue();
            assertThat(fixtures().deletedAt(postId)).isNotNull();
        }
        assertThat(events.of(PostTrashed.class)).hasSize(4);
    }

    // ---- US1-2 ----

    @ParameterizedTest(name = "제목·본문 [{0}]")
    @ValueSource(strings = {"", "  ", "\n", " \t\r\n"})
    void US1_2_빈_임시글은_바로_완전삭제(String blank) throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).title(blank).contentMd(blank).create();

        MvcResult result = api().trash(session, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> json = read(result, "$");
        assertThat(json).containsExactly(Map.entry("purged", true));
        assertThat(fixtures().exists(postId)).isFalse();
        assertThat(events.of(PostPurged.class)).isEmpty();
        assertThat(events.of(PostTrashed.class)).isEmpty();
    }

    @Test
    void 제목만_빈_임시글은_휴지통으로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).title("").contentMd("본문만 있음").create();

        MvcResult result = api().trash(session, postId);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.trashed")).isTrue();
        assertThat(fixtures().deletedAt(postId)).isNotNull();
    }

    @Test
    void 제목_본문이_공백인_발행_글은_빈_임시글_판정을_하지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        // ck_post_published(btrim 공백만 지움) 때문에 탭으로 '정책상 빈 글이지만 발행 글'을 만든다 (002 T114 메모)
        long postId = posts().post(me).published("PUBLIC").title("\t").contentMd("\t").create();

        MvcResult result = api().trash(session, postId);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.trashed")).isTrue();
        assertThat(fixtures().exists(postId)).isTrue();
    }

    // ---- US1-3 ----

    @Test
    void US1_3_남의글_삭제는_404_변경없음() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long othersPost = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        long othersDraft = posts().post(other).create();
        long othersTrashed = posts().create(other, PostFixtures.State.TRASHED);

        for (long postId : new long[] {othersPost, othersDraft, othersTrashed}) {
            Map<String, Object> before = fixtures().row(postId);
            MvcResult result = api().trash(session, postId);
            assertThat(status(result)).isEqualTo(404);
            assertThat(body(result))
                    .isEqualTo(
                            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\","
                                    + "\"errors\":[],\"details\":null}");
            assertThat(fixtures().row(postId)).isEqualTo(before);
        }
        assertThat(events.all()).isEmpty();
    }

    @Test
    void 없는_글은_404() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        assertThat(status(api().trash(session, posts().nonexistentId()))).isEqualTo(404);
        assertThat(status(api().trash(session, 0))).isEqualTo(404);
        assertThat(status(api().trash(session, -5))).isEqualTo(404);
    }

    // ---- US1-4 ----

    @Test
    void US1_4_이미_휴지통이면_성공_변화없음() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult first = api().trash(session, postId);
        Map<String, Object> afterFirst = fixtures().row(postId);
        events.clear();
        MvcResult second = api().trash(session, postId);

        assertThat(status(second)).isEqualTo(200);
        assertThat((Boolean) read(second, "$.trashed")).isTrue();
        assertThat((String) read(second, "$.purgeAt")).isEqualTo(read(first, "$.purgeAt"));
        assertThat(fixtures().row(postId)).isEqualTo(afterFirst);
        assertThat(events.all()).isEmpty();
    }

    // ---- US1-6 ----

    @Test
    void US1_6_댓글_좋아요는_보존() throws Exception {
        long me = members().member().create();
        long reader = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).published("PUBLIC").draft("고치는 중", "고치는 본문").create();
        long comment = fixtures().comment(postId, reader, null);
        fixtures().comment(postId, me, comment);
        fixtures().like(postId, reader);
        fixtures().tag(postId, "spring", 0);
        fixtures().image(me, postId);
        fixtures().viewDaily(postId, LocalDate.now(), 3);
        fixtures().commentNotification(me, postId, comment, reader);
        fixtures().counts(postId, 30, 1, 2);
        Map<String, Long> relatedBefore = fixtures().related(postId);
        Map<String, Object> before = fixtures().row(postId);

        assertThat(status(api().trash(session, postId))).isEqualTo(200);

        assertThat(fixtures().related(postId)).isEqualTo(relatedBefore);
        assertThat(relatedBefore.values()).allMatch(count -> count > 0);
        Map<String, Object> after = fixtures().row(postId);
        for (String column : new String[] {"view_count", "like_count", "comment_count"}) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tag", Long.class)).isEqualTo(1);
    }

    // ---- 판정 순서 (FR-037, research R5, 42 §3) ----

    @Test
    void 비회원은_401() throws Exception {
        long author = members().member().create();
        long postId = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result = api().trash(null, postId);

        assertThat(status(result)).isEqualTo(401);
        assertThat((String) read(result, "$.code")).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void 이메일_인증_전_회원도_자기_글을_지울_수_있다() throws Exception {
        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThat(status(api().trash(session, postId))).isEqualTo(200);
    }

    @Test
    void 탈퇴_유예_회원은_403_남의_글에도_먼저() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        long mine = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long others = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        posts().withdraw(me);

        for (long postId : new long[] {mine, others, posts().nonexistentId()}) {
            MvcResult result = api().trash(session, postId);
            assertThat(status(result)).isEqualTo(403);
            assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
        }
        assertThat(fixtures().deletedAt(mine)).isNull();
    }

    @Test
    void 정지된_회원의_남은_세션은_403_남의_글에도_먼저() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        long mine = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long others = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        members().suspend(me, Instant.now().plus(Duration.ofDays(7)), "006 테스트");

        for (long postId : new long[] {mine, others}) {
            MvcResult result = api().trash(session, postId);
            assertThat(status(result)).isEqualTo(403);
            assertThat((String) read(result, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        }
        assertThat(fixtures().deletedAt(mine)).isNull();
    }

    @Test
    void CSRF_토큰이_없으면_403() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result =
                mockMvc.perform(delete("/api/posts/{id}", postId).cookie(session)).andReturn();

        assertThat(status(result)).isEqualTo(403);
        assertThat(fixtures().deletedAt(postId)).isNull();
    }

    // ---- US1-7 자동 저장 반영 (T017, FR-024, research R7·R8) ----

    @Test
    void US1_7_trash_flushesPendingAutosave_발행_글은_작업본에() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        authoring().putAutosave(postId, me, "버퍼 제목", "버퍼 본문 v13", 13, Instant.now(), true);

        assertThat(status(api().trash(session, postId))).isEqualTo(200);

        Map<String, Object> draft =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version FROM post_draft WHERE post_id = ?",
                        postId);
        assertThat(draft.get("title")).isEqualTo("버퍼 제목");
        assertThat(draft.get("content_md")).isEqualTo("버퍼 본문 v13");
        assertThat(draft.get("edit_version")).isEqualTo(13L);
        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(postId))).isFalse();
        assertThat(authoring().isDirty(postId)).isFalse();
        assertThat(fixtures().deletedAt(postId)).isNotNull();
    }

    @Test
    void US1_7_trash_flushesPendingAutosave_임시글은_글에() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).title("옛 제목").contentMd("옛 본문").create();
        authoring().putAutosave(postId, me, "새 제목", "새 본문", 13, Instant.now(), true);

        assertThat(status(api().trash(session, postId))).isEqualTo(200);

        Map<String, Object> row = fixtures().row(postId);
        assertThat(row.get("title")).isEqualTo("새 제목");
        assertThat(row.get("content_md")).isEqualTo("새 본문");
        assertThat(row.get("edit_version")).isEqualTo(13L);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(postId))).isFalse();
        assertThat(authoring().isDirty(postId)).isFalse();
    }

    @Test
    void Redis에만_내용이_있는_빈_임시글은_빈_글로_지우지_않고_휴지통으로() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).create();
        authoring().putAutosave(postId, me, "", "다른 탭에서 방금 쓴 본문", 4, Instant.now(), true);

        MvcResult result = api().trash(session, postId);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.trashed")).isTrue();
        assertThat(fixtures().row(postId).get("content_md")).isEqualTo("다른 탭에서 방금 쓴 본문");
        assertThat(fixtures().deletedAt(postId)).isNotNull();
    }

    @Test
    void Redis_장애여도_삭제는_성공한다() {
        // 세션이 Redis에 있어 장애 중 HTTP 요청은 401이 되므로 서비스를 직접 부른다 (002 T053 메모)
        long me = members().member().create();
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);

        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(trashService.trash(me, postId))
                    .isInstanceOf(PostTrashService.TrashOutcome.Trashed.class);
        }

        assertThat(fixtures().deletedAt(postId)).isNotNull();
    }

    // ---- US1-5 휴지통 글 쓰기 차단 (T018, FR-023) ----

    @Test
    void US1_5_휴지통글_쓰기는_모두_404() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().post(me).published("PUBLIC").draft("작업본", "작업본 본문").create();
        assertThat(status(api().trash(session, postId))).isEqualTo(200);
        // 다른 탭의 자동 저장 보관분이 Redis에 남아 있는 상태 (엣지 케이스 "휴지통으로 옮긴 뒤 자동 저장 도착")
        authoring().putAutosave(postId, me, "늦게 온 제목", "늦게 온 본문", 3, Instant.now(), false);
        Map<String, Object> before = fixtures().row(postId);
        Map<String, Object> draftBefore =
                jdbc.queryForMap("SELECT * FROM post_draft WHERE post_id = ?", postId);
        EditorApi editor = new EditorApi(mockMvc);

        assertNotFound(editor.autosave(session, postId, EditorApi.saveBody("자동", "저장", 3)));
        assertNotFound(editor.save(session, postId, EditorApi.saveBody("수동", "저장", 3)));
        assertNotFound(
                editor.publish(
                        session,
                        postId,
                        EditorApi.publishBody("발행", "본문", java.util.List.of(), "PUBLIC", 3)));
        assertNotFound(editor.discard(session, postId));
        assertNotFound(editor.workingCopy(session, postId));

        assertThat(fixtures().row(postId)).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM post_draft WHERE post_id = ?", postId))
                .isEqualTo(draftBefore);
    }

    @Test
    void US1_5_휴지통글_공개범위_변경은_404() throws Exception {
        Assumptions.assumeTrue(
                handlerExists("PUT", "/api/posts/1/visibility"),
                "004 PUT /api/posts/{postId}/visibility가 아직 없다");
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.TRASHED);
        Map<String, Object> before = fixtures().row(postId);

        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                put("/api/posts/{id}/visibility", postId), session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"visibility\":\"PRIVATE\"}"))
                        .andReturn();

        assertNotFound(result);
        assertThat(fixtures().row(postId)).isEqualTo(before);
    }

    private static void assertNotFound(MvcResult result) {
        assertThat(status(result)).as(body(result)).isEqualTo(404);
        assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
    }

    private boolean handlerExists(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            return chain != null;
        } catch (Exception e) {
            return false;
        }
    }
}
