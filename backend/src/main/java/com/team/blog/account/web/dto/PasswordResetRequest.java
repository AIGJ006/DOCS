package com.team.blog.account.web.dto;

/** 비밀번호 찾기 요청 (contracts {@code requestPasswordReset}). */
public record PasswordResetRequest(String email) {}
