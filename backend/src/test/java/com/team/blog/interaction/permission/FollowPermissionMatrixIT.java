package com.team.blog.interaction.permission;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 팔로우·피드 권한 매트릭스 (010 T043, research R11, 헌법 III). 004 하네스로 {@code follow.csv}의 모든 행을 실행한다 — 판정 순서
 * 401 → 403 → 404 → 400, 목록은 비회원도 200, 피드는 로그인 필요.
 */
class FollowPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/follow.csv", numLinesToSkip = 1)
    void follow(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
