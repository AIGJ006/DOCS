package com.team.blog.shared.application.markdown;

/**
 * 작성자가 올린 사진 한 장 ({@code image} 행 요약).
 *
 * @param storageKey 원본 저장 키
 * @param thumbStorageKey 640px 썸네일 키 (없는 옛 사진이면 {@code null})
 */
public record OwnedImage(String storageKey, String thumbStorageKey) {}
