package com.team.blog.tag.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 태그 목록 노출 권한 매트릭스 (008 T029·T039·T042·T046·T048·T053·T056·T062, SC-004, research R14). 004 하네스로
 * {@code tag-list.csv}의 모든 행을 실행한다 — 대상 글에 붙인 태그(또는 그 글)가 목록에 들어가는지({@code INCLUDED}/{@code
 * EXCLUDED}) 본다. 공개 글({@code PUBLISHED_PUBLIC}, 공개본이 남은 {@code EDITING})만 들어가고, 자동완성의 작성자 본인만 모든
 * 상태에서 자기 태그를 본다.
 */
class TagPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/tag-list.csv", numLinesToSkip = 1)
    void tagList(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
