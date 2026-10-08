package com.team.blog.media.domain;

/** 사진 용도 (V1 {@code ck_image_purpose}). */
public enum ImagePurpose {
    /** 글 사진: 원본 + 640px 썸네일. */
    POST,
    /** 프로필 사진: 정확히 256×256 WebP, 썸네일 없음 (001, research R14). */
    PROFILE
}
