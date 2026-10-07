package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.BusinessRuleException;

/** 임시글의 변경 취소 → 409 {@code NOT_PUBLISHED}. */
public class NotPublishedException extends BusinessRuleException {

    public NotPublishedException() {
        super(PostReasonCode.NOT_PUBLISHED);
    }
}
