package com.team.blog.account.application;

import java.time.Instant;

/** 이번 로그인 바로 전 로그인 (openapi {@code MySettings.previousLogin}, FR-058). */
public record PreviousLogin(Instant at, String provider) {}
