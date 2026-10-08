package com.team.blog.tag.permission;

import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code tag-suggest.post}: {@code POST /api/posts/{id}/tag-suggestions} (013 T020, research R14).
 * 대상 글 작성자에게 AI 동의 픽스처를 넣고 가짜 공급자가 답한다. Bean 이름은 008 자동완성 실행기({@code TagSuggestAction})와 겹치지 않게 따로
 * 준다.
 */
@Profile("test")
@Component("aiTagSuggestAction")
public class TagSuggestAction implements PermissionAction {

    static final String OWNER = "013";

    private final JdbcTemplate jdbc;
    private final FakeAi fakeAi;

    public TagSuggestAction(JdbcTemplate jdbc, FakeAi fakeAi) {
        this.jdbc = jdbc;
        this.fakeAi = fakeAi;
    }

    @Override
    public String name() {
        return "tag-suggest.post";
    }

    @Override
    public String owner() {
        return OWNER;
    }

    /** 서버는 아무것도 저장하지 않지만 거부 전후 글 값이 같은지 하네스가 보게 쓰기 행동으로 둔다. */
    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        prepare(jdbc, fakeAi, postId);
        return ActionResult.of(new AiSuggestApi(mockMvc, jdbc).suggest(session, postId));
    }

    /** 대상 글 작성자 동의 픽스처(글이 있을 때), 가짜 공급자 초기화. */
    static void prepare(JdbcTemplate jdbc, FakeAi fakeAi, Long postId) {
        fakeAi.reset();
        jdbc.query("SELECT author_id FROM post WHERE id = ?", (rs, n) -> rs.getLong(1), postId)
                .forEach(author -> new AiSuggestApi(null, jdbc).consent(author));
    }
}
