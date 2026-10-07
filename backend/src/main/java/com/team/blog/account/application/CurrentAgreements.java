package com.team.blog.account.application;

import java.time.LocalDate;

/** 이용약관·개인정보 처리방침의 현재 버전·시행일과 화면 경로 (FR-011, {@code GET /api/agreements/current}). */
public record CurrentAgreements(Document terms, Document privacy) {

    public record Document(String version, LocalDate effectiveDate, String path) {}
}
