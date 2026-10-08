package com.team.blog.moderation.infra;

import com.team.blog.moderation.domain.ReportReason;
import java.time.Instant;

/** {@code report} 한 행 (data-model §1-2). {@code detail}은 기타일 때만, 30일 정리 뒤 {@code null}. */
public record ReportRow(
        long id,
        long caseId,
        long reporterId,
        ReportReason reason,
        String detail,
        Instant createdAt) {}
