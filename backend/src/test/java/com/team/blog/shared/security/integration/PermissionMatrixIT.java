package com.team.blog.shared.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import com.team.blog.support.permission.PendingRowReport;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.support.permission.actions.SetVisibilityAction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 공용 권한 매트릭스 러너 (004 T045, US3 인수 1·2·3, FR-025·FR-026·FR-033·FR-034·FR-035, SC-001·SC-003·SC-008).
 * {@code post-read.csv}·{@code post-write.csv}의 <b>모든</b> 행을 실행한다 — 기능별 러너(002·005·006)를 따로 두지 않고
 * 행동 실행기({@link PermissionAction} Bean)만 각 기능이 등록한다.
 *
 * <ul>
 *   <li>쓰기 행은 거부되면 요청 전후 {@code PostSnapshot}이 같다(SC-003, 하네스가 확인).
 *   <li>쓰기 행마다 본문·매개변수에 다른 회원 번호({@code authorId}·{@code memberId}·{@code ownerId}·{@code userId})를
 *       끼워 넣은 변형을 한 번 더 돌린다 — 결과가 같고 작성자가 바뀌지 않는다(42 §12 #2).
 *   <li>관리자가 남의 글을 수정·삭제·공개 범위 변경하면 404다(42 §12 #5) — CSV의 {@code ADMIN} 행.
 *   <li>실행기가 없는 행동(아직 착수하지 않은 기능 소유)은 {@code pending: <owner>}로 건너뛰고 {@code PendingRowReport}가
 *       owner별로 집계한다.
 * </ul>
 */
class PermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/post-read.csv", numLinesToSkip = 1)
    void post_read(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/post-write.csv", numLinesToSkip = 1)
    void post_write(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }

    @ParameterizedTest(name = "{0} × {1} × {2} + authorId → {3} {4}")
    @CsvFileSource(resources = "/permission/post-write.csv", numLinesToSkip = 1)
    void post_write_작성자_번호_끼워_넣기(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verifyWithForeignOwner(actor, target, action, status, code, owner);
    }

    /** 아직 착수하지 않은 Tier B·C 기능 — 이 owner의 행만 대기로 남아도 된다 (014 숨김). 007 댓글 행은 comment.csv로 옮겼다. */
    private static final Set<String> NOT_STARTED = Set.of("014");

    /**
     * 004 T075 (SC-001): Tier A 행(저장·발행·변경 취소·삭제·복구·영구 삭제·공개 범위·읽기)은 건너뜀 0건. 실행 후 대기 행이 착수 전 기능
     * 소유뿐인지 본다.
     */
    @AfterAll
    static void Tier_A_대기_행은_0건() {
        Map<String, List<String>> pending =
                PendingRowReport.pendingOwners(PermissionMatrixIT.class);
        assertThat(pending.keySet())
                .as("대기 행 " + PendingRowReport.summary(pending))
                .isSubsetOf(NOT_STARTED);
    }

    @Test
    void 공개_범위_변경_실행기가_등록되어_있다() {
        assertThat(registry().find("post.visibility"))
                .get()
                .isInstanceOf(SetVisibilityAction.class);
    }

    @Test
    void 관리자가_남의_글을_바꾸는_행은_모두_404다() throws IOException {
        Set<String> contentWrites =
                Set.of(
                        "post.editor",
                        "post.autosave",
                        "post.save",
                        "post.publish",
                        "post.discard",
                        "post.visibility",
                        "post.trash",
                        "post.restore",
                        "post.purge");
        List<String[]> adminRows =
                rows("/permission/post-write.csv").stream()
                        .filter(cols -> cols[0].equals("ADMIN"))
                        .filter(cols -> contentWrites.contains(cols[2]))
                        .toList();

        assertThat(adminRows).extracting(cols -> cols[2]).containsAll(contentWrites);
        assertThat(adminRows)
                .allSatisfy(
                        cols -> {
                            assertThat(cols[3]).as(String.join(",", cols)).isEqualTo("404");
                            assertThat(cols[4]).as(String.join(",", cols)).isEqualTo("NOT_FOUND");
                        });
    }

    private static List<String[]> rows(String resource) throws IOException {
        try (InputStream in = PermissionMatrixIT.class.getResourceAsStream(resource);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .filter(l -> !l.isBlank())
                    .map(l -> l.split(",", -1))
                    .toList();
        }
    }
}
