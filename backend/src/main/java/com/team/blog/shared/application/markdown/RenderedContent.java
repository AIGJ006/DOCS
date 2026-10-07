package com.team.blog.shared.application.markdown;

import java.util.List;

/**
 * 렌더링 결과 (002 data-model §6).
 *
 * @param html 정화된 HTML ({@code post.content_html})
 * @param excerpt 요약, 최대 200자 (대상 글자가 없으면 빈 문자열)
 * @param ownedImageKeys 본문에 쓴 작성자 사진의 저장 키, 본문 순서·중복 없음 (발행 때 {@code post_image} 연결)
 * @param thumbnailUrl 첫 작성자 사진의 썸네일 주소(없으면 원본 주소), 작성자 사진이 없으면 {@code null}
 * @param renderVersion 이 결과를 만든 규칙 버전 ({@link RenderVersion#CURRENT})
 */
public record RenderedContent(
        String html,
        String excerpt,
        List<String> ownedImageKeys,
        String thumbnailUrl,
        int renderVersion) {

    public RenderedContent {
        ownedImageKeys = List.copyOf(ownedImageKeys);
    }
}
