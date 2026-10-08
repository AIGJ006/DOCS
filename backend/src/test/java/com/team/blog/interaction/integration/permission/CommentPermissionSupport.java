package com.team.blog.interaction.integration.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.permission.ActionResult;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 댓글 권한 실행기 공용 (007 T017·T029·T038, research R13). 대상 댓글 준비와 거부 전후 댓글 행 비교({@code CommentSnapshot})를
 * 맡는다.
 */
@Profile("test")
@Component
public class CommentPermissionSupport {

    /** 없는 글에 대한 행동에 쓰는 없는 댓글 번호. */
    static final long NONEXISTENT_COMMENT = 9_000_000_000L;

    private final JdbcTemplate jdbc;

    public CommentPermissionSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 글의 댓글 행 전부 (거부 전후 비교). */
    List<Map<String, Object>> snapshot(Long postId) {
        if (postId == null) {
            return List.of();
        }
        return jdbc.queryForList(
                "SELECT id, author_id, parent_id, content, updated_at, deleted_at, hidden_at"
                        + " FROM comment WHERE post_id = ? ORDER BY id",
                postId);
    }

    boolean postExists(Long postId) {
        return postId != null
                && jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ?", Long.class, postId)
                        > 0;
    }

    /**
     * 수정·삭제 대상 댓글을 넣는다. 행위자가 "MEMBER"(글 작성자가 아니고 인증한 ACTIVE 일반 회원)면 그 행위자가 쓴 댓글, 그 밖에는 다른 회원이 쓴
     * 댓글이다(R13 — MEMBER만 자기 댓글).
     */
    long arrangeTarget(MockMvc mockMvc, Cookie session, long postId) throws Exception {
        Long actor = actorId(mockMvc, session);
        long postAuthor = jdbc.queryForObject("SELECT author_id FROM post WHERE id = ?", Long.class, postId);
        long commentAuthor =
                actor != null && isPlainMember(actor, postAuthor)
                        ? actor
                        : new MemberFixtures(jdbc).member().create();
        long id =
                jdbc.queryForObject(
                        "INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, '권한 시험"
                                + " 댓글') RETURNING id",
                        Long.class,
                        postId,
                        commentAuthor);
        jdbc.update("UPDATE post SET comment_count = comment_count + 1 WHERE id = ?", postId);
        return id;
    }

    /** 실행 결과가 거부면 전후 댓글 행이 같아야 한다. */
    ActionResult checked(MvcResult result, Long postId, List<Map<String, Object>> before) {
        ActionResult outcome = ActionResult.of(result);
        if (outcome.status() >= 400 && !snapshot(postId).equals(before)) {
            throw new AssertionError("거부된 댓글 요청이 댓글 행을 바꿨다: postId=" + postId);
        }
        return outcome;
    }

    private boolean isPlainMember(long actor, long postAuthor) {
        if (actor == postAuthor) {
            return false;
        }
        return jdbc.queryForObject(
                "SELECT count(*) FROM member m JOIN auth_identity a ON a.member_id = m.id WHERE"
                        + " m.id = ? AND m.role = 'USER' AND m.status = 'ACTIVE' AND"
                        + " a.email_verified_at IS NOT NULL",
                Long.class,
                actor)
                > 0;
    }

    private static Long actorId(MockMvc mockMvc, Cookie session) throws Exception {
        if (session == null) {
            return null;
        }
        MvcResult me = mockMvc.perform(get("/api/me").cookie(session)).andReturn();
        if (me.getResponse().getStatus() != 200) {
            return null;
        }
        Number id =
                JsonPath.read(
                        new String(me.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                        "$.memberId");
        return id.longValue();
    }
}
