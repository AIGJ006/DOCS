package com.team.blog.post.application;

import java.time.Instant;

/**
 * 자동 저장·수동 저장 결과 (contracts {@code SaveResponse}).
 *
 * @param version 새 편집 버전 (클라이언트의 다음 {@code baseVersion})
 * @param savedAt 받아들인 시각 ("✓ 저장됨 14:03")
 */
public record SaveResult(long version, Instant savedAt) {}
