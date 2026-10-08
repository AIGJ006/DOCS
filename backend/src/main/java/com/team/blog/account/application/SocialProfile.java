package com.team.blog.account.application;

import com.team.blog.account.domain.Provider;
import java.io.Serializable;

/**
 * 소셜 제공자가 돌려준 사용자 정보 (R-06, FR-003·008). Google은 {@code sub}, GitHub은 숫자 {@code id}로 식별하고 이메일·로그인
 * 이름은 식별에 쓰지 않는다.
 *
 * @param email 제공자가 확인한 이메일(Google {@code email_verified = true}, GitHub {@code primary &&
 *     verified}). 없으면 null
 * @param emailVerified {@code email}이 제공자가 확인한 값인지 (null이면 false)
 * @param displayName 닉네임 미리 채우기용 이름 (GitHub {@code name}이 없으면 {@code login})
 * @param pictureUrl 제공자 사진 주소 원문 (검사 전). 서버는 저장하지 않는다(SC-012)
 */
public record SocialProfile(
        Provider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String pictureUrl)
        implements Serializable {}
