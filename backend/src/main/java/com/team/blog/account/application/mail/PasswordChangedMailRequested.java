package com.team.blog.account.application.mail;

/** 비밀번호 변경 알림 (FR-045). 커밋 후 보낸다. */
public record PasswordChangedMailRequested(long memberId) {}
