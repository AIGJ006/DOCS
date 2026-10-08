package com.team.blog.account.web.dto;

/** 복구 응답 (015 contracts {@code RestoreResult}): 항상 {@code ACTIVE}. */
public record RestoreResult(String status) {

    public static RestoreResult active() {
        return new RestoreResult("ACTIVE");
    }
}
