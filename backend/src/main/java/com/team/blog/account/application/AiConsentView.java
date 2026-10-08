package com.team.blog.account.application;

import java.time.Instant;

/**
 * AI 외부 전송 동의 상태 (013 data-model §4, {@code GET·PUT·DELETE /api/me/agreements/ai} 응답 본문).
 *
 * @param agreed 행이 있고 저장된 버전이 현재 버전과 같음
 * @param version 저장된 버전 (행이 없으면 {@code null})
 * @param currentVersion 지금 동의 문구 버전 ({@code blog.agreement.ai.version})
 * @param agreedAt 동의 시각 (행이 없으면 {@code null})
 */
public record AiConsentView(
        boolean agreed, String version, String currentVersion, Instant agreedAt) {}
