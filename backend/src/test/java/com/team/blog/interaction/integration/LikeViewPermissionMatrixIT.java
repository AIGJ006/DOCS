package com.team.blog.interaction.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 좋아요·조회 기록 권한 매트릭스 (009 T019·T029, SC-004, research R12). 004 하네스로 {@code like-view.csv}의 모든 행을
 * 실행한다 — {@code post.like}·{@code post.unlike}(판정 순서 401 → 403 → 404 → 400, 거부되면 좋아요 수·행 수 그대로)와
 * {@code post.view}(볼 수 있는 글은 셌든 안 셌든 204, 볼 수 없는 글은 404).
 */
class LikeViewPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/like-view.csv", numLinesToSkip = 1)
    void likeView(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
