package com.team.blog.discovery.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 트렌딩·검색·sitemap 노출 권한 매트릭스 (012 T040, 헌법 III, SC-003, research R16). 004 하네스로 {@code search.csv}의
 * 모든 행을 실행한다 — 공개 글({@code PUBLISHED_PUBLIC}, 공개본이 남은 {@code EDITING})만 들어가고, 작성자 본인·관리자도 비공개·숨김 글은
 * 보지 않는다. 탈퇴 유예 행위자는 API 게이트에서 403, sitemap은 게이트 밖이라 200. 사람 검색은 탈퇴 유예 회원만 빠진다. 휴지통 글의 검색·sitemap
 * 행은 006 {@code TrashedPostPermissionMatrixIT}가 따로 맡는다(여기 행은 이 기능 실행기로 확인).
 */
class SearchPermissionMatrixIT extends AbstractPermissionMatrixIT {

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @CsvFileSource(resources = "/permission/search.csv", numLinesToSkip = 1)
    void search(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
