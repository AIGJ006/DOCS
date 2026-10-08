package com.team.blog.media.application;

/**
 * 완료 확인을 통과한 사진 (contracts/openapi.yaml {@code UploadedImage}). 주소는 지금의 공개 주소 + 저장 키다.
 *
 * @param thumbUrl 썸네일 주소 (프로필 사진이면 {@code null})
 */
public record UploadedImage(
        long imageId,
        String url,
        String thumbUrl,
        String contentType,
        int width,
        int height,
        long sizeBytes) {}
