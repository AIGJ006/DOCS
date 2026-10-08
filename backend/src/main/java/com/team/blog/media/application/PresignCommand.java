package com.team.blog.media.application;

import java.util.List;

/**
 * 업로드 준비 요청 (contracts/openapi.yaml {@code PresignRequest}). 칸은 모두 받은 그대로(없으면 {@code null})이고, 검사는
 * {@link ImageUploadService#presign}이 계정 상태 확인 뒤에 한다(판정 순서 401 → 403 → 400).
 *
 * @param unknownFields 계약에 없는 칸 이름 (파일 이름 등 — 저장하지 않고 400, US1 #5)
 */
public record PresignCommand(
        String purpose,
        String contentType,
        Long size,
        String thumbContentType,
        Long thumbSize,
        List<String> unknownFields) {

    public PresignCommand {
        unknownFields = unknownFields == null ? List.of() : List.copyOf(unknownFields);
    }
}
