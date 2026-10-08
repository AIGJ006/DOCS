package com.team.blog.account.web.dto;

import com.team.blog.account.application.WithdrawCommand;

/**
 * 탈퇴 신청 본문 (015 contracts {@code WithdrawRequest}). 모르는 칸(예: 사유)은 받아도 버린다 — 저장하지 않는다(FR-004). 비밀번호는
 * {@link #toString()}에 나오지 않는다.
 */
public record WithdrawRequest(Boolean confirmed, String password, String confirmText) {

    public WithdrawCommand toCommand() {
        return new WithdrawCommand(confirmed, password, confirmText);
    }

    @Override
    public String toString() {
        return "WithdrawRequest[confirmed=" + confirmed + ", ***]";
    }
}
