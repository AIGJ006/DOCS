package com.team.blog.interaction.integration.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.MemberFixtures;
import com.team.blog.support.permission.ActionResult;
import java.util.concurrent.Callable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 좋아요 권한 실행기 공용 (009 T019). 대상 글에 다른 회원의 좋아요 1건을 미리 넣어 두고, 요청이 거부되면(4xx) 그 글의 {@code like_count}와
 * {@code post_like} 행 수가 전후 같은지 본다(SC-004 — 004 {@code PostSnapshot}에는 카운터가 없어 여기서 따로 본다).
 */
final class LikePermissionSupport {

    static final String OWNER = "009";

    private LikePermissionSupport() {}

    /** 다른 회원의 좋아요 1건을 넣고 수를 맞춘다 (글이 없으면 아무것도 하지 않는다). */
    static void seedOtherLike(JdbcTemplate jdbc, Long postId) {
        if (postId == null || !exists(jdbc, postId)) {
            return;
        }
        long other = new MemberFixtures(jdbc).member().create();
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, other);
        jdbc.update("UPDATE post SET like_count = like_count + 1 WHERE id = ?", postId);
    }

    /** 실행하고, 거부면 좋아요 수·행 수가 그대로인지 확인한다. */
    static ActionResult performChecked(JdbcTemplate jdbc, Long postId, Callable<ActionResult> call)
            throws Exception {
        String before = snapshot(jdbc, postId);
        ActionResult result = call.call();
        if (result.status() >= 400) {
            assertThat(snapshot(jdbc, postId)).as("거부된 좋아요 요청 전후 좋아요 수·행 수").isEqualTo(before);
        }
        return result;
    }

    private static boolean exists(JdbcTemplate jdbc, long postId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM post WHERE id = ?)", Boolean.class, postId));
    }

    private static String snapshot(JdbcTemplate jdbc, Long postId) {
        if (postId == null || !exists(jdbc, postId)) {
            return "없음";
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT like_count FROM post WHERE id = ?", Integer.class, postId);
        Long rows =
                jdbc.queryForObject(
                        "SELECT count(*) FROM post_like WHERE post_id = ?", Long.class, postId);
        return count + "/" + rows;
    }
}
