package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.CommentApi.id;
import static com.team.blog.interaction.support.CommentApi.read;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentEventProbe;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 댓글·답글 쓰기 (007 T026, US2, FR-009~013). */
class CommentWriteIT extends IntegrationTestBase {

    @Autowired CommentEventProbe events;

    private long author;
    private long me;
    private long other;
    private long postId;
    private Cookie mine;
    private Cookie others;

    private CommentApi api() {
        return new CommentApi(mockMvc);
    }

    private CommentFixtures comments() {
        return new CommentFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        events.arm();
        author = members().member().create();
        me = members().member().nickname("나회원").create();
        other = members().member().nickname("남회원").create();
        postId = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        mine = TestLogin.loginAs(mockMvc, me);
        others = TestLogin.loginAs(mockMvc, other);
    }

    @AfterEach
    void tearDown() {
        events.reset();
    }

    @Test
    void 작성하면_201과_댓글_수_더하기_1() throws Exception {
        MvcResult result = api().create(mine, postId, "  좋은 글 잘 읽었어요  ");

        assertThat(status(result)).isEqualTo(201);
        assertThat((String) read(result, "$.content")).isEqualTo("좋은 글 잘 읽었어요");
        assertThat((String) read(result, "$.state")).isEqualTo("NORMAL");
        assertThat((Boolean) read(result, "$.mine")).isTrue();
        assertThat((String) read(result, "$.author.nickname")).isEqualTo("나회원");
        assertThat(comments().commentCount(postId)).isEqualTo(1);
        assertThat(events.of(CommentCreated.class).size()).isEqualTo(1);
        CommentCreated event = events.of(CommentCreated.class).get(0);
        assertThat(event.postAuthorId()).isEqualTo(author);
        assertThat(event.authorId()).isEqualTo(me);
        assertThat(event.parentId()).isNull();
    }

    @Test
    void 금칙어가_있어도_거부하지_않는다() throws Exception {
        assertThat(status(api().create(mine, postId, "시발 이건 좀 아닌데"))).isEqualTo(201);
    }

    @Test
    void 답글에_답하면_같은_최상위_아래_대상_기록() throws Exception {
        long root = id(api().create(mine, postId, "최상위"));
        long reply = id(api().create(others, postId, "답글", root));
        MvcResult replyToReply = api().create(mine, postId, "답글의 답글", reply);

        assertThat(status(replyToReply)).isEqualTo(201);
        assertThat(((Number) read(replyToReply, "$.parentId")).longValue()).isEqualTo(root);
        assertThat((String) read(replyToReply, "$.replyTo.nickname")).isEqualTo("남회원");
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT parent_id, reply_to_member_id FROM comment WHERE id = ?",
                        id(replyToReply));
        assertThat(((Number) row.get("parent_id")).longValue()).isEqualTo(root);
        assertThat(((Number) row.get("reply_to_member_id")).longValue()).isEqualTo(other);
        // 최상위에 바로 단 답글은 대상 없음
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reply_to_member_id FROM comment WHERE id = ?",
                                Long.class,
                                reply))
                .isNull();
        assertThat(events.of(CommentCreated.class).size()).isEqualTo(3);
        CommentCreated last = events.of(CommentCreated.class).get(2);
        assertThat(last.parentAuthorId()).isEqualTo(me);
        assertThat(last.replyToMemberId()).isEqualTo(other);
    }

    @Test
    void 내_답글에_답하면_대상_없음() throws Exception {
        long root = id(api().create(others, postId, "최상위"));
        long reply = id(api().create(mine, postId, "내 답글", root));
        MvcResult again = api().create(mine, postId, "내 답글에 다시", reply);

        assertThat(status(again)).isEqualTo(201);
        assertThat(((Number) read(again, "$.parentId")).longValue()).isEqualTo(root);
        assertThat((Object) read(again, "$.replyTo")).isNull();
    }

    @Test
    void 비회원_401_인증_전_403_정지_403_탈퇴_유예_403() throws Exception {
        assertThat(status(api().create(null, postId, "내용"))).isEqualTo(401);
        long unverified = members().member().emailVerified(false).create();
        MvcResult u = api().create(TestLogin.loginAs(mockMvc, unverified), postId, "내용");
        assertThat(status(u)).isEqualTo(403);
        assertThat((String) read(u, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
        long suspended = members().member().create();
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(7, ChronoUnit.DAYS), "시험");
        MvcResult s = api().create(suspendedSession, postId, "내용");
        assertThat(status(s)).isEqualTo(403);
        assertThat((String) read(s, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        long leaving = members().member().create();
        Cookie leavingSession = TestLogin.loginAs(mockMvc, leaving);
        posts().withdraw(leaving);
        MvcResult w = api().create(leavingSession, postId, "내용");
        assertThat(status(w)).isEqualTo(403);
        assertThat((String) read(w, "$.code")).isEqualTo("ACCOUNT_WITHDRAWN");
        assertThat(comments().commentCount(postId)).isZero();
    }

    @Test
    void 공백만이면_COMMENT_REQUIRED() throws Exception {
        MvcResult result = api().create(mine, postId, " \n​\t ");
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("content");
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("COMMENT_REQUIRED");
        assertThat((String) read(result, "$.errors[0].message")).isEqualTo("댓글 내용을 입력해 주세요");
    }

    @Test
    void 천일_자면_COMMENT_TOO_LONG() throws Exception {
        assertThat(status(api().create(mine, postId, "😀".repeat(1000)))).isEqualTo(201);
        MvcResult result = api().create(mine, postId, "가".repeat(1001));
        assertThat(status(result)).isEqualTo(400);
        assertThat((String) read(result, "$.errors[0].field")).isEqualTo("content");
        assertThat((String) read(result, "$.errors[0].code")).isEqualTo("COMMENT_TOO_LONG");
    }

    @Test
    void 읽을_수_없는_글은_내용_검사보다_404가_먼저() throws Exception {
        long privatePost = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult result = api().create(mine, privatePost, "   ");
        assertThat(status(result)).isEqualTo(404);
        assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        assertThat(status(api().create(mine, posts().nonexistentId(), ""))).isEqualTo(404);
        assertThat(status(api().create(mine, "abc", "내용"))).isEqualTo(404);
    }

    @Test
    void 삭제_숨김_탈퇴_다른_글_댓글에는_답글_불가() throws Exception {
        long leaving = members().member().create();
        long deleted = comments().on(postId, other).deleted().create();
        comments().on(postId, me).parent(deleted).create();
        long hidden = comments().on(postId, other).hidden().create();
        long byLeaving = comments().on(postId, leaving).create();
        posts().withdraw(leaving);
        long otherPost = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long elsewhere = comments().on(otherPost, other).create();
        int before = comments().commentCount(postId);

        for (long target : new long[] {deleted, hidden, byLeaving, elsewhere, 9_999_999L}) {
            MvcResult result = api().create(mine, postId, "답글", target);
            assertThat(status(result)).as("대상 " + target).isEqualTo(400);
            assertThat((String) read(result, "$.errors[0].field")).isEqualTo("replyToCommentId");
            assertThat((String) read(result, "$.errors[0].code"))
                    .isEqualTo("REPLY_TARGET_UNAVAILABLE");
        }
        assertThat(comments().commentCount(postId)).isEqualTo(before);
    }

    @Test
    void 숨긴_글에는_쓸_수_없다() throws Exception {
        long hiddenPost = posts().create(author, PostFixtures.State.HIDDEN);
        assertThat(status(api().create(TestLogin.loginAs(mockMvc, author), hiddenPost, "작성자")))
                .isEqualTo(404);
        assertThat(status(api().create(mine, hiddenPost, "남"))).isEqualTo(404);
    }

    @Test
    void 열한_번째는_429와_Retry_After() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(status(api().create(mine, postId, "댓글 " + i))).isEqualTo(201);
        }
        MvcResult result = api().create(mine, postId, "열한 번째");
        assertThat(status(result)).isEqualTo(429);
        assertThat((String) read(result, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After"))).isPositive();
        assertThat(comments().commentCount(postId)).isEqualTo(10);
    }

    @Test
    void 앞_단계에서_걸린_요청은_세지_않는다() throws Exception {
        long privatePost = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        for (int i = 0; i < 5; i++) {
            assertThat(status(api().create(mine, postId, "  "))).isEqualTo(400);
            assertThat(status(api().create(mine, privatePost, "내용"))).isEqualTo(404);
        }
        for (int i = 0; i < 10; i++) {
            assertThat(status(api().create(mine, postId, "정상 " + i))).isEqualTo(201);
        }
    }

    @Test
    void 십초_안_같은_요청은_200과_처음_댓글() throws Exception {
        MvcResult first = api().create(mine, postId, "같은 내용");
        MvcResult second = api().create(mine, postId, "같은 내용 ");

        assertThat(status(first)).isEqualTo(201);
        assertThat(status(second)).isEqualTo(200);
        assertThat(id(second)).isEqualTo(id(first));
        assertThat(comments().commentCount(postId)).isEqualTo(1);
        assertThat(events.of(CommentCreated.class).size()).isEqualTo(1);
        assertThat(events.of(CommentCreated.class)).hasSize(1);
    }

    @Test
    void 십초_뒤_같은_내용은_새_댓글() throws Exception {
        long old =
                comments()
                        .on(postId, me)
                        .content("같은 내용")
                        .at(Instant.now().minusSeconds(11))
                        .create();
        MvcResult result = api().create(mine, postId, "같은 내용");

        assertThat(status(result)).isEqualTo(201);
        assertThat(id(result)).isNotEqualTo(old);
    }
}
