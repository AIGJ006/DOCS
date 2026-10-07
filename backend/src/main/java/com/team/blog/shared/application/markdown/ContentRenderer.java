package com.team.blog.shared.application.markdown;

/**
 * 본문 렌더러 (12 §2, FR-040 "하나의 렌더러"). Markdown 원문 → 정화된 HTML·요약·작성자 사진 키·썸네일. 발행·미리보기·다시 렌더링이 이 Bean
 * 하나만 쓴다. 원문은 바꾸지 않는다.
 */
public interface ContentRenderer {

    /**
     * @param contentMd Markdown 원문 ({@code null}이면 빈 글)
     * @param ctx 사진 판별 기준 (발행·다시 렌더링은 글 작성자, 미리보기는 로그인한 본인)
     * @throws ContentTooComplexException 목록·인용 중첩 초과 또는 렌더링 시간 초과
     */
    RenderedContent render(String contentMd, ImageContext ctx);
}
