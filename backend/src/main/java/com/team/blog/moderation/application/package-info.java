/**
 * moderation 모듈의 application: 신고 접수({@code ReportService}), 관리자 처리·직접 숨김·해제, 사건 조회, 회원 정지 관리, 대상 없음
 * 자동 종료({@code ReportPostPurgeStep}·{@code OrphanCaseCloser}·{@code ReportWithdrawalPurgeStep}),
 * 30일 보관 정리({@code ReportSnapshotCleanupJob}).
 */
package com.team.blog.moderation.application;
