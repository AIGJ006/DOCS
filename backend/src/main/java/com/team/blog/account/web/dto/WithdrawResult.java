package com.team.blog.account.web.dto;

import java.time.Instant;

/** 탈퇴 신청 응답 (015 contracts {@code WithdrawResult}): 완료 화면이 그릴 복구 기한. */
public record WithdrawResult(Instant restoreDeadline) {}
