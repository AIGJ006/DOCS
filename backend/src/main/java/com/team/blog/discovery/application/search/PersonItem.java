package com.team.blog.discovery.application.search;

/**
 * 사람 검색 결과 한 명 (openapi {@code PersonItem}).
 *
 * @param handle 블로그 주소
 * @param nickname 닉네임
 * @param profileImageUrl 작은 프로필 사진 (없으면 {@code null})
 * @param bioFirstLine 소개의 첫 줄 (없거나 비면 {@code null})
 */
public record PersonItem(
        String handle, String nickname, String profileImageUrl, String bioFirstLine) {

    /** 소개 첫 줄 — 010 목록 화면과 같은 규칙(첫 줄바꿈 앞, 앞뒤 공백 제거). */
    public static String firstLine(String bio) {
        if (bio == null) {
            return null;
        }
        String line = bio.split("\r\n|\n|\r", 2)[0].strip();
        return line.isEmpty() ? null : line;
    }
}
