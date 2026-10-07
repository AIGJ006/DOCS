package com.team.blog.shared.error;

import java.util.List;
import java.util.Map;

/**
 * 계정 상태 거부 → 403 ({@code EMAIL_NOT_VERIFIED}, {@code ACCOUNT_SUSPENDED}, {@code ACCOUNT_WITHDRAWN},
 * {@code REAGREEMENT_REQUIRED} 등). 판정 순서는 로그인(401) → 계정 상태(403) → 볼 수 있나(404) (42 §3).
 */
public class AccountStateException extends ApiException {

    public AccountStateException(ReasonCode reasonCode) {
        super(reasonCode);
    }

    public AccountStateException(ReasonCode reasonCode, Map<String, Object> details) {
        super(reasonCode, details);
    }

    public AccountStateException(
            ReasonCode reasonCode, String message, Map<String, Object> details) {
        super(reasonCode, message, List.of(), details, Map.of());
    }
}
