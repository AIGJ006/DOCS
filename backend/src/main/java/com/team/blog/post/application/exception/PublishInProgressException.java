package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.BusinessRuleException;

/** 같은 {@code Idempotency-Key}의 발행이 처리 중 → 409 {@code IN_PROGRESS} (05 §6). */
public class PublishInProgressException extends BusinessRuleException {

    public PublishInProgressException() {
        super(PostReasonCode.IN_PROGRESS);
    }
}
