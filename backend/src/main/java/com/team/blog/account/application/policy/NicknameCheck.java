package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.FieldError;

/**
 * 닉네임 검사 결과.
 *
 * @param normalized 정리한 값(앞뒤 공백 제거 + NFC). 통과하면 이 값을 저장한다(09 §2)
 * @param failure 첫 실패 코드(09 §3 순서). 통과하면 null
 */
public record NicknameCheck(String normalized, AccountReasonCode failure) {

    public boolean valid() {
        return failure == null;
    }

    /** 칸 오류. 금칙어 문구는 어떤 단어에 걸렸는지 담지 않는다. */
    public FieldError toFieldError(String field) {
        if (failure == null) {
            throw new IllegalStateException("통과한 닉네임입니다");
        }
        return new FieldError(field, failure.code(), failure.defaultMessage());
    }
}
