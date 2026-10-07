package com.team.blog.shared.error;

import java.util.List;
import java.util.Map;

/** 업무 규칙 위반 → 이유 코드의 상태(보통 400 또는 409). 판정 순서의 마지막 단계(42 §3). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ReasonCode reasonCode) {
        super(reasonCode);
    }

    public BusinessRuleException(ReasonCode reasonCode, Map<String, Object> details) {
        super(reasonCode, details);
    }

    public BusinessRuleException(
            ReasonCode reasonCode, String message, Map<String, Object> details) {
        super(reasonCode, message, List.of(), details, Map.of());
    }
}
