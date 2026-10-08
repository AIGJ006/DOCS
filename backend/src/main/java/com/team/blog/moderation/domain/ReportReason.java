package com.team.blog.moderation.domain;

/**
 * 신고 사유 (V1 {@code ck_report_reason}, FR-002). 숨김 사유도 같은 목록이고 {@code hidden_reason}(30자)에 코드로 저장한다
 * (Clarifications Q3).
 */
public enum ReportReason {
    /** 스팸·광고 */
    SPAM,
    /** 욕설·혐오 */
    ABUSE,
    /** 음란·선정 */
    SEXUAL,
    /** 개인정보 노출 */
    PRIVACY,
    /** 저작권 침해 */
    COPYRIGHT,
    /** 기타 — 설명 필수 */
    OTHER
}
