package com.team.blog.moderation.web.dto;

/** 신고 접수 응답 — 새 신고·중복 모두 {@code {accepted: true}} (contracts {@code ReportAccepted}). */
public record ReportAccepted(boolean accepted) {

    public static final ReportAccepted ACCEPTED = new ReportAccepted(true);
}
