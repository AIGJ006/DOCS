package com.team.blog.post.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.post.domain.PrivateVisibilityRule;
import com.team.blog.post.domain.PublicVisibilityRule;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.shared.security.Viewer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 공용 목록 조건 (06 R-2·R-2a·R-2b, FR-007·FR-009, research R-04). 조건은 부분 인덱스 {@code ix_post_feed}·{@code
 * ix_post_blog}의 WHERE와 같고, 작성자 본인 예외가 없다(06 V-8·H1).
 */
class VisibilityFilterTest {

    static final String EXPECTED =
            "p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL"
                    + " AND p.hidden_at IS NULL AND m.withdrawn_at IS NULL";

    private final VisibilityFilter filter =
            new VisibilityFilter(
                    new VisibilityRegistry(
                            List.of(new PublicVisibilityRule(), new PrivateVisibilityRule())));

    @Test
    void 비회원_전체_목록_조건() {
        SqlCondition condition = filter.forViewer(Viewer.anonymous(), null);
        assertThat(condition.sql()).isEqualTo(EXPECTED);
        assertThat(condition.params()).isEmpty();
    }

    @Test
    void 블로그_목록은_작성자_조건과_파라미터가_붙는다() {
        SqlCondition condition = filter.forViewer(Viewer.anonymous(), 7L);
        assertThat(condition.sql()).isEqualTo(EXPECTED + " AND p.author_id = :authorId");
        assertThat(condition.params()).isEqualTo(Map.of("authorId", 7L));
    }

    @Test
    void 블로그_주인이_봐도_조건이_같다() {
        Viewer owner = new Viewer(7L, Role.USER, MemberStatus.ACTIVE, true);
        Viewer admin = new Viewer(8L, Role.ADMIN, MemberStatus.ACTIVE, true);
        SqlCondition anonymous = filter.forViewer(Viewer.anonymous(), 7L);
        assertThat(filter.forViewer(owner, 7L)).isEqualTo(anonymous);
        assertThat(filter.forViewer(admin, 7L)).isEqualTo(anonymous);
        assertThat(filter.forViewer(owner, null).sql()).isEqualTo(EXPECTED);
    }

    @Test
    void 파라미터_맵은_바꿀_수_없다() {
        SqlCondition condition = filter.forViewer(Viewer.anonymous(), 7L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> condition.params().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
