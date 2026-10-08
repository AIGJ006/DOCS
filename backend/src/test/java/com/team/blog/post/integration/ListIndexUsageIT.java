package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.ReferenceListQueries;
import com.team.blog.support.ReferenceListQueries.Statement;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공용 목록 조건이 부분 인덱스를 타는지 (004 T042, 06 R-2b, research R-27). 같은 트랜잭션에서 {@code SET LOCAL
 * enable_seqscan = off} 뒤 {@link ReferenceListQueries}의 홈·블로그 대표 쿼리를 {@code EXPLAIN (FORMAT
 * JSON)}으로 본다 — 조건 문구가 인덱스 술어와 어긋나면 순차 탐색을 꺼도 인덱스를 고를 수 없어 계획에 이름이 나오지 않는다. 큰 데이터의 실제 계획은 005
 * {@code ListIndexExplainIntegrationTest}가 본다.
 */
class ListIndexUsageIT extends IntegrationTestBase {

    @Autowired private VisibilityFilter visibilityFilter;
    @Autowired private TransactionTemplate transactionTemplate;

    private ReferenceListQueries lists;
    private long author;

    @BeforeEach
    void setUp() {
        lists = new ReferenceListQueries(jdbc, visibilityFilter);
        PostFixtures posts = new PostFixtures(jdbc);
        author = members().member().create();
        for (State state : State.values()) {
            if (state != State.AUTHOR_WITHDRAWN) {
                posts.create(author, state);
            }
        }
        posts.create(members().member().create(), State.PUBLISHED_PUBLIC);
        jdbc.execute("ANALYZE post");
    }

    private String explain(Statement statement) {
        return transactionTemplate.execute(
                tx -> {
                    jdbc.execute("SET LOCAL enable_seqscan = off");
                    return new NamedParameterJdbcTemplate(jdbc)
                            .queryForObject(
                                    "EXPLAIN (FORMAT JSON) " + statement.sql(),
                                    statement.params(),
                                    String.class);
                });
    }

    @Test
    void 홈_대표_쿼리는_ix_post_feed를_탄다() {
        assertThat(explain(lists.homeStatement(Viewer.anonymous()))).contains("\"ix_post_feed\"");
    }

    @Test
    void 블로그_대표_쿼리는_ix_post_blog를_탄다() {
        assertThat(explain(lists.blogStatement(Viewer.anonymous(), author)))
                .contains("\"ix_post_blog\"");
    }
}
