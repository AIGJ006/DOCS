package com.team.blog.media.domain;

/**
 * 완료 확인 거부 사유 — 400 {@code IMAGE_REJECTED}의 {@code details.reason} (data-model §7). 사용자 문구는 하나("올릴
 * 수 없는 사진이에요")이고 사유만 다르다.
 */
public enum ImageRejectReason {
    /** 저장소의 실제 크기가 신고보다 크다. */
    SIZE_MISMATCH,
    /** 매직 바이트 형식이 신고한 {@code Content-Type}과 다르다 (확장자 위장, US1 #3). */
    TYPE_MISMATCH,
    /** 머리말을 읽을 수 없다 (잘린 파일, 앞부분에 SOF 없음). */
    CORRUPT,
    /** 원본 가로·세로·전체 픽셀 한도 초과. */
    DIMENSION_EXCEEDED,
    /** GIF 가로·세로 한도(1920px) 초과 (FR-036). */
    GIF_TOO_LARGE,
    /** GIF 프레임 한도(300장) 초과 (FR-036). */
    GIF_TOO_MANY_FRAMES,
    /** 썸네일 형식·크기 규칙 위반 (가로 640px 초과 등). */
    THUMBNAIL_INVALID,
    /** 프로필 사진이 정확히 256×256이 아니다. */
    PROFILE_SIZE_INVALID
}
