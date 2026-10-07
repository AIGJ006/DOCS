package com.team.blog.shared.infra.ratelimit;

/** 요청 제한 결과. */
public sealed interface RateLimitResult {

    boolean allowed();

    /** 허용. */
    record Allowed() implements RateLimitResult {
        @Override
        public boolean allowed() {
            return true;
        }
    }

    /**
     * 거부.
     *
     * @param retryAfterSeconds 창이 끝날 때까지 남은 초 (1 이상) — {@code Retry-After}
     */
    record Denied(long retryAfterSeconds) implements RateLimitResult {
        @Override
        public boolean allowed() {
            return false;
        }
    }
}
