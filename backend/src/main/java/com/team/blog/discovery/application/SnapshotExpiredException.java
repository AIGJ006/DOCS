package com.team.blog.discovery.application;

import com.team.blog.shared.error.ApiException;

/** 커서의 트렌딩 스냅샷이 사라짐 → 410 {@code SNAPSHOT_EXPIRED} (012 research R5, FR-011). */
public class SnapshotExpiredException extends ApiException {

    public SnapshotExpiredException() {
        super(DiscoveryReasonCode.SNAPSHOT_EXPIRED);
    }
}
