package com.team.blog.media.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.media.application.UploadedImage;

/**
 * complete 응답 (contracts/openapi.yaml {@code UploadedImage}). {@code thumbUrl}은 프로필 사진이면 {@code
 * null}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UploadedImageResponse(
        long imageId,
        String url,
        String thumbUrl,
        String contentType,
        int width,
        int height,
        long sizeBytes) {

    public static UploadedImageResponse of(UploadedImage image) {
        return new UploadedImageResponse(
                image.imageId(),
                image.url(),
                image.thumbUrl(),
                image.contentType(),
                image.width(),
                image.height(),
                image.sizeBytes());
    }
}
