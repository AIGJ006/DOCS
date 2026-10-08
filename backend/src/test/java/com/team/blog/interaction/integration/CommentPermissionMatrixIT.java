package com.team.blog.interaction.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 댓글 권한 매트릭스 (007 T017·T025·T035·T043, research R13, SC-001·SC-006). 004 하네스 {@code comment.csv}.
 */
class CommentPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/comment.csv", numLinesToSkip = 1)
    void comment(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
