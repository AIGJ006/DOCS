package com.team.blog.media.application;

import com.team.blog.media.infra.storage.ImageStorage.UploadTarget;
import java.time.Instant;

/**
 * 업로드 준비 결과 (contracts/openapi.yaml {@code ImageUploadTicket}).
 *
 * @param thumbUpload 썸네일 업로드 주소 (프로필 사진이면 {@code null})
 */
public record ImageUploadTicket(
        long imageId, UploadTarget upload, UploadTarget thumbUpload, Instant expiresAt) {}
