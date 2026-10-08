package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.FieldError;

/**
 * 소개 검사 결과.
 *
 * @param normalized 정리한 값(앞뒤 공백 제거·NFC·줄바꿈 {@code \n}·연속 빈 줄 하나). 비었으면 null — 이 값을 저장한다
 * @param failure 실패 코드. 통과하면 null
 */
public record BioCheck(String normalized, AccountReasonCode failure) {

    public boolean valid() {
        return failure == null;
    }

    public FieldError toFieldError(String field) {
        if (failure == null) {
            throw new IllegalStateException("통과한 소개입니다");
        }
        return new FieldError(field, failure.code(), failure.defaultMessage());
    }
}
