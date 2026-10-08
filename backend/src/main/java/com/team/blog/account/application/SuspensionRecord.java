package com.team.blog.account.application;

import java.time.Instant;

/**
 * 정지 이력 한 줄 (014 data-model §3, contracts {@code SuspensionRecord}).
 *
 * @param endsAt {@code null} = 영구
 * @param suspendedByHandle 정지한 관리자의 주소 (익명 처리됐으면 {@code null})
 * @param liftedByHandle 해제한 관리자의 주소 (기한 지남 자동 해제·익명 처리면 {@code null})
 */
public record SuspensionRecord(
        long id,
        String reason,
        Instant startedAt,
        Instant endsAt,
        String suspendedByHandle,
        Instant liftedAt,
        String liftedByHandle) {

    public boolean isOpen() {
        return liftedAt == null;
    }
}
