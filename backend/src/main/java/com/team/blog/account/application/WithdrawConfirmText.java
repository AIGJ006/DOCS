package com.team.blog.account.application;

import java.text.Normalizer;

/**
 * 소셜 가입 회원의 탈퇴 확인 문구 비교 (015 T018, research R3). 앞뒤 공백을 빼고 NFC로 정규화한 뒤 설정값({@code
 * blog.withdraw.confirm-text}, 기본 "탈퇴")과 비교한다 — 조합형으로 입력한 "탈퇴"도 통과한다. 20자를 넘으면 비교하지 않고 다르다고 본다(계약
 * {@code maxLength: 20}).
 */
public final class WithdrawConfirmText {

    /** 확인 문구 입력 최대 길이 (contracts {@code WithdrawRequest.confirmText.maxLength}). */
    public static final int MAX_LENGTH = 20;

    private WithdrawConfirmText() {}

    /** 입력이 확인 문구와 같은가. */
    public static boolean matches(String input, String expected) {
        if (input == null || input.codePointCount(0, input.length()) > MAX_LENGTH) {
            return false;
        }
        return normalize(input).equals(normalize(expected));
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
