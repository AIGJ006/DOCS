package com.team.blog.media.application;

/**
 * 내 사진 저장 공간 (contracts/openapi.yaml {@code StorageUsage}, research R25).
 *
 * @param todayCount 오늘(서비스 시간대) presign 통과 장수. Redis 장애면 {@code null}
 * @param limits 화면이 고르는 순간 검사에 쓰는 한도 (헌법 VII — 화면에 숫자를 따로 두지 않는다)
 */
public record StorageUsage(
        long usedBytes, long quotaBytes, Integer todayCount, int dailyLimit, Limits limits) {

    public record Limits(
            int maxUploadBytes,
            int maxThumbBytes,
            long maxSourceBytes,
            int longSide,
            int thumbMaxWidth,
            int gifMaxSide,
            int gifMaxFrames) {}
}
