package com.team.blog.post.web.dto;

/**
 * 자동 저장·수동 저장 요청 (contracts {@code SaveRequest}). 제목·본문은 입력 그대로 저장하고(정리는 발행 때), {@code local:} 사진
 * 주소도 받는다. 태그 등 다른 필드는 받지 않는다(FR-006).
 *
 * @param baseVersion 이 내용이 출발한 서버 편집 버전 (없으면 0)
 */
public record SaveRequest(String title, String contentMd, Long baseVersion) {

    public long baseVersionOrZero() {
        return baseVersion == null ? 0 : baseVersion;
    }
}
