package com.team.blog.media.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.team.blog.media.application.PresignCommand;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code POST /api/images/presign} 본문 (contracts/openapi.yaml {@code PresignRequest}). 계약에 없는 칸(파일
 * 이름 등)은 무시하지 않고 모아 두었다가 400 {@code UNKNOWN_FIELD}로 돌려준다 — 값은 어디에도 저장하지 않는다(US1 #5, T030). 전역 설정이
 * 모르는 칸을 무시해도 {@link JsonAnySetter}가 먼저 받는다.
 */
public class PresignRequest {

    private String purpose;
    private String contentType;
    private Long size;
    private String thumbContentType;
    private Long thumbSize;
    private final List<String> unknownFields = new ArrayList<>();

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public void setThumbContentType(String thumbContentType) {
        this.thumbContentType = thumbContentType;
    }

    public void setThumbSize(Long thumbSize) {
        this.thumbSize = thumbSize;
    }

    @JsonAnySetter
    public void unknown(String name, Object ignored) {
        unknownFields.add(name);
    }

    public PresignCommand toCommand() {
        return new PresignCommand(
                purpose, contentType, size, thumbContentType, thumbSize, unknownFields);
    }
}
