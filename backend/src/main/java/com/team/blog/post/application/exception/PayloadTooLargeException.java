package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.ApiException;

/** 자동 저장 요청 본문이 {@code blog.autosave.max-request-bytes}(1MB) 초과 → 413 {@code PAYLOAD_TOO_LARGE}. */
public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException() {
        super(PostReasonCode.PAYLOAD_TOO_LARGE);
    }
}
