package com.team.blog.account.application.mail;

/**
 * 비밀번호 찾기 접수 (FR-042). 응답을 먼저 돌려주고 {@code mailExecutor}에서 이메일로 계정을 찾아 보낸다 — 응답 시간이 가입 여부와
 * 무관하다(SC-004). 이메일은 메모리 안에서만 오가고 로그에 남기지 않는다.
 */
public record PasswordResetMailRequested(String email) {

    @Override
    public String toString() {
        return "PasswordResetMailRequested[email=***]";
    }
}
