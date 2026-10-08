package com.team.blog.media.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.media.application.ImageUploadTicket;
import com.team.blog.media.infra.storage.ImageStorage.UploadTarget;
import java.time.Instant;
import java.util.Map;

/**
 * presign 응답 (contracts/openapi.yaml {@code ImageUploadTicket}). {@code thumbUpload}는 프로필 사진이면
 * {@code null}로 보낸다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ImageUploadTicketResponse(
        long imageId, Target upload, Target thumbUpload, Instant expiresAt) {

    /** 업로드 주소 ({@code UploadTarget}). */
    public record Target(String url, String method, Map<String, String> headers) {
        static Target of(UploadTarget target) {
            return target == null
                    ? null
                    : new Target(target.url(), target.method(), target.headers());
        }
    }

    public static ImageUploadTicketResponse of(ImageUploadTicket ticket) {
        return new ImageUploadTicketResponse(
                ticket.imageId(),
                Target.of(ticket.upload()),
                Target.of(ticket.thumbUpload()),
                ticket.expiresAt());
    }
}
