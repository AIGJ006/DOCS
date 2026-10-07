package com.team.blog.account.web.dto;

/** 가입 결과 (contracts {@code SignupResult}). */
public record SignupResult(String handle, String nickname, boolean emailVerified) {}
