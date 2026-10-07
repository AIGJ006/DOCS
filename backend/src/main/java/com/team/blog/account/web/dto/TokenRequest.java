package com.team.blog.account.web.dto;

/** 메일 링크 토큰 (contracts {@code TokenRequest}). 토큰 값은 {@link #toString()}·로그에 나오지 않는다(FR-015). */
public record TokenRequest(String token) {

    @Override
    public String toString() {
        return "TokenRequest[token=***]";
    }
}
