package com.team.blog.shared.error;

/** 오류 문구 정리 규칙. */
final class Messages {

    private Messages() {}

    /** 문구 끝의 마침표를 뗀다 (2026-10-07 결정: 메시지 끝 마침표 없음). */
    static String withoutTrailingPeriod(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.strip();
        while (trimmed.endsWith(".") || trimmed.endsWith("。")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed;
    }
}
