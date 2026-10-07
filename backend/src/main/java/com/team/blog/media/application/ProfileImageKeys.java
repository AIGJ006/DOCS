package com.team.blog.media.application;

/**
 * 현재 프로필 사진의 저장소 키.
 *
 * @param original {@code image.storage_key} — og:image에 쓴다
 * @param thumbnail {@code image.thumb_storage_key} — 없으면 {@code null}
 */
public record ProfileImageKeys(String original, String thumbnail) {

    /** 작은 사진(`/api/me`·친구 목록·카드·블로그 머리말)에 쓸 키: 썸네일, 없으면 원본. */
    public String display() {
        return thumbnail != null ? thumbnail : original;
    }
}
