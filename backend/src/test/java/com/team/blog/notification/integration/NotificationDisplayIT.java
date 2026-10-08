package com.team.blog.notification.integration;

import static com.team.blog.notification.support.NotificationApi.body;
import static com.team.blog.notification.support.NotificationApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.notification.support.NotificationApi;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 보여 줄 때 다시 판단 (011 T038, US4 #1~#4, SC-004, research R10). */
class NotificationDisplayIT extends NotificationTestBase {

    private NotificationApi api;
    private CommentFixtures comments;
    private long a;
    private long b;
    private long c;
    private long postId;
    private Cookie sessionB;

    @BeforeEach
    void setUp() {
        api = new NotificationApi(mockMvc);
        comments = new CommentFixtures(jdbc);
        a = members().member().handle("writera").create();
        b = members().member().create();
        c = members().member().nickname("처음닉네임").handle("actorc").create();
        postId = posts.post(a).title("지금 제목").published("PUBLIC").create();
        sessionB = TestLogin.loginAs(mockMvc, b);
    }

    /** B의 댓글에 C가 단 답글 → B가 받은 REPLY 알림. */
    private long replyToB(String content) {
        long root = comments.on(postId, b).content("B 최상위").create();
        long reply = comments.on(postId, c).parent(root).content(content).create();
        notifications.single(b, "REPLY").post(postId).comment(reply).actor(c).create();
        return reply;
    }

    private Map<String, Object> first(Cookie session) throws Exception {
        MvcResult result = api.list(session, 10, null);
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(200);
        return read(result, "$.items[0]");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 읽을_수_있으면_제목_미리보기_댓글_주소() throws Exception {
        long reply = replyToB("안녕하세요");
        Map<String, Object> item = first(sessionB);
        assertThat(item.get("type")).isEqualTo("REPLY");
        assertThat(item.get("read")).isEqualTo(false);
        assertThat(item.get("post"))
                .isEqualTo(Map.of("title", "지금 제목", "url", "/@writera/posts/" + postId));
        assertThat(item.get("comment")).isEqualTo(Map.of("id", (int) reply, "preview", "안녕하세요"));
        assertThat((Map<String, Object>) item.get("actor"))
                .containsEntry("handle", "actorc")
                .containsEntry("nickname", "처음닉네임");
        assertThat(((Map<?, ?>) item.get("actor")).get("profileImageUrl")).isNull();
        assertThat(item.get("url"))
                .isEqualTo("/@writera/posts/" + postId + "?comment=" + reply + "#comment-" + reply);
        assertThat(item.get("othersCount")).isEqualTo(0);
    }

    @Test
    void 볼_수_없게_된_글은_제목_미리보기_주소_없음() throws Exception {
        replyToB("곧 볼 수 없음");
        String[] changes = {
            "UPDATE post SET visibility = 'PRIVATE', first_public_at = first_public_at WHERE id = ?",
            "UPDATE post SET deleted_at = now() WHERE id = ?",
            "UPDATE post SET hidden_at = now(), hidden_by = (SELECT id FROM member ORDER BY id LIMIT 1),"
                    + " hidden_reason = 'SPAM' WHERE id = ?",
        };
        String reset =
                "UPDATE post SET visibility = 'PUBLIC', deleted_at = NULL, hidden_at = NULL,"
                        + " hidden_by = NULL, hidden_reason = NULL WHERE id = ?";
        for (String change : changes) {
            jdbc.update(reset, postId);
            jdbc.update(change, postId);
            Map<String, Object> item = first(sessionB);
            assertThat(item.get("post")).as(change).isEqualTo(Map.of("unavailable", true));
            assertThat(item.get("comment")).as(change).isNull();
            assertThat(item.get("url")).as(change).isNull();
            assertThat(body(api.list(sessionB, 10, null))).doesNotContain("지금 제목");
        }
        // 다시 공개하면 지금 제목
        jdbc.update(reset, postId);
        jdbc.update("UPDATE post SET title = '바뀐 제목' WHERE id = ?", postId);
        assertThat(((Map<?, ?>) first(sessionB).get("post")).get("title")).isEqualTo("바뀐 제목");

        // 작성자 탈퇴 유예
        posts.withdraw(a);
        assertThat(first(sessionB).get("post")).isEqualTo(Map.of("unavailable", true));
    }

    @Test
    void 받는_사람이_작성자면_자기_비공개_글_제목이_보인다() throws Exception {
        long commentId = comments.on(postId, c).content("작성자에게").create();
        notifications.single(a, "COMMENT").post(postId).comment(commentId).actor(c).create();
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", postId);

        Map<String, Object> item = first(TestLogin.loginAs(mockMvc, a));
        assertThat(((Map<?, ?>) item.get("post")).get("title")).isEqualTo("지금 제목");
        assertThat(item.get("url")).isNotNull();
    }

    @Test
    void 행동자_닉네임_변경과_탈퇴() throws Exception {
        replyToB("닉네임");
        jdbc.update("UPDATE member SET nickname = '바뀐닉네임' WHERE id = ?", c);
        assertThat(((Map<?, ?>) first(sessionB).get("actor")).get("nickname")).isEqualTo("바뀐닉네임");

        posts.withdraw(c);
        MvcResult result = api.list(sessionB, 10, null);
        assertThat((Object) read(result, "$.items[0].actor")).isEqualTo(Map.of("withdrawn", true));
        assertThat(body(result)).doesNotContain("바뀐닉네임").doesNotContain("actorc");

        jdbc.update("UPDATE member SET deleted_at = now(), nickname = NULL WHERE id = ?", c);
        assertThat(first(sessionB).get("actor")).isEqualTo(Map.of("withdrawn", true));
    }

    @Test
    void 미리보기는_지금_내용_공백_한_칸_앞_50자() throws Exception {
        long reply = replyToB("처음 내용");
        String fifty = "가".repeat(48) + "😀😀";
        jdbc.update(
                "UPDATE comment SET content = ? WHERE id = ?",
                "  **굵게**\n\n  줄바꿈   " + fifty + "끝",
                reply);
        String preview = (String) ((Map<?, ?>) first(sessionB).get("comment")).get("preview");
        String expectedHead = "**굵게** 줄바꿈 " + fifty;
        String cut = new String(expectedHead.codePoints().limit(50).toArray(), 0, 50);
        assertThat(preview).isEqualTo(cut + "…");
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(51);

        jdbc.update("UPDATE comment SET content = ? WHERE id = ?", "😀".repeat(50), reply);
        assertThat(((Map<?, ?>) first(sessionB).get("comment")).get("preview"))
                .isEqualTo("😀".repeat(50));
        jdbc.update("UPDATE comment SET content = ? WHERE id = ?", "😀".repeat(51), reply);
        assertThat(((Map<?, ?>) first(sessionB).get("comment")).get("preview"))
                .isEqualTo("😀".repeat(50) + "…");
    }
}
