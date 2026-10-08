package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import com.team.blog.support.permission.PendingRowReport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 사진 업로드 권한 매트릭스 (003 T023·T031, research R26, ANALYSIS-tier-a R6). {@code image.csv}의 모든 행 — 비회원
 * 401, 인증 전·정지·탈퇴 유예 403, 회원·관리자 presign 201·본인 사진 complete 200, 다른 회원 사진 complete 404.
 */
class ImagePermissionMatrixIT extends AbstractPermissionMatrixIT {

    @BeforeEach
    void clearStorage() {
        MinioContainerSupport.clearImages();
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/image.csv", numLinesToSkip = 1)
    void image(String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @AfterAll
    static void 대기_행은_0건() {
        assertThat(PendingRowReport.pendingOwners(ImagePermissionMatrixIT.class)).isEmpty();
    }
}
