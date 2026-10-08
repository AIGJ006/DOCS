package com.team.blog.account.application;

import java.time.Instant;

/**
 * 내 프로필 (openapi {@code MyProfile}, FR-046).
 *
 * @param handle 읽기 전용
 * @param nicknameChangeAvailableAt null이면 지금 바꿀 수 있음, 아니면 다음 변경 가능 시각(FR-052)
 */
public record MyProfile(
        String handle,
        String nickname,
        String bio,
        Long profileImageId,
        String profileImageUrl,
        Instant nicknameChangeAvailableAt) {}
