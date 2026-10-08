package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import com.team.blog.support.permission.PendingRowReport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** 알림 권한 매트릭스 (011 T024, research R15, SC-007). 남의 알림 읽음·삭제는 관리자도 404. */
class NotificationPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/notification.csv", numLinesToSkip = 1)
    void notification(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @AfterAll
    static void 대기_행은_0건() {
        assertThat(PendingRowReport.pendingOwners(NotificationPermissionMatrixIT.class)).isEmpty();
    }
}
