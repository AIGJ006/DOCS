package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.BusinessRuleException;

/** 같은 {@code Idempotency-Key}로 다른 내용 → 422 {@code IDEMPOTENCY_KEY_REUSED} (05 §6, research B-8). */
public class IdempotencyKeyReusedException extends BusinessRuleException {

    public IdempotencyKeyReusedException() {
        super(PostReasonCode.IDEMPOTENCY_KEY_REUSED);
    }
}
