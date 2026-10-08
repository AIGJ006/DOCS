package com.team.blog.account.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import com.team.blog.support.permission.PendingRowReport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** 탈퇴·복구 권한 매트릭스 (015 T059, research R15). {@code withdraw.csv} 18행. */
class WithdrawPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/withdraw.csv", numLinesToSkip = 1)
    void withdraw(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @AfterAll
    static void 대기_행은_0건() {
        assertThat(PendingRowReport.pendingOwners(WithdrawPermissionMatrixIT.class)).isEmpty();
    }
}
