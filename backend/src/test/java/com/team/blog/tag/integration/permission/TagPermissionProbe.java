package com.team.blog.tag.integration.permission;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.tag.support.TagFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** 008 목록 행동 실행기 공용: 대상 글에 고유 태그를 붙이고, 응답에 그 태그(또는 그 글)가 있는지 본다. */
final class TagPermissionProbe {

    /** 대상 글에만 붙이는 태그. 행마다 DB가 비워지므로 고정 이름이어도 된다. */
    static final String TAG = "perm-tag";

    static final String OWNER = "008";

    private TagPermissionProbe() {}

    static void attach(JdbcTemplate jdbc, Long postId) {
        if (postId != null) {
            new TagFixtures(jdbc).attach(postId, TAG);
        }
    }

    /** 200이면 {@code jsonPath}로 뽑은 목록에 {@code expected}가 있는지, 아니면 상태·오류 코드. */
    static ActionResult listed(MvcResult result, String jsonPath, Object expected)
            throws Exception {
        if (result.getResponse().getStatus() != 200) {
            return ActionResult.of(result);
        }
        String body =
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Object> values = JsonPath.read(body, jsonPath);
        boolean included =
                values.stream()
                        .anyMatch(
                                v ->
                                        v instanceof Number n && expected instanceof Number e
                                                ? n.longValue() == e.longValue()
                                                : String.valueOf(v)
                                                        .equals(String.valueOf(expected)));
        return ActionResult.listed(200, included);
    }
}
