package com.team.blog.account.application.policy;

/** 규칙 하나의 충족 여부 (화면 ✓ 표시와 1:1, FR-014). */
public record PasswordRuleResult(PasswordRule rule, boolean satisfied) {}
