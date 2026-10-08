package com.team.blog.moderation.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** 신고·관리자 숨김 권한 매트릭스 (014 T019·T026·T033·T043, research R13). 004 하네스 {@code moderation.csv}. */
class ModerationPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/moderation.csv", numLinesToSkip = 1)
    void moderation(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @ParameterizedTest(name = "{0} × {1} × {2} + authorId → {3} {4}")
    @CsvFileSource(resources = "/permission/moderation.csv", numLinesToSkip = 1)
    void moderation_작성자_번호_끼워_넣기(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verifyWithForeignOwner(actor, target, action, status, code, owner);
    }
}
