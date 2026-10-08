package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 소셜 가입 마무리 결과 (contracts {@code SocialSignupResult}). {@code profilePhotoUrl}은 사진 사용을 골랐고 가져올 수 있을
 * 때만 싣는다 — 브라우저가 이 주소를 받아 003 업로드 흐름으로 복사한다. 서버는 저장하지 않는다(SC-012).
 */
public record SocialSignupResult(
        String handle,
        String nickname,
        boolean emailVerified,
        @JsonInclude(JsonInclude.Include.NON_NULL) String profilePhotoUrl,
        String redirectTo) {}
