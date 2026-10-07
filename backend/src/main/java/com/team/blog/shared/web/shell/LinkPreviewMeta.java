package com.team.blog.shared.web.shell;

/**
 * 첫 응답에 넣는 링크 미리보기·검색 엔진 메타 (005 data-model §5, 40 §5, research R-18·R-27). 값이 {@code null}인 항목은
 * 태그를 만들지 않는다. 값은 {@link SpaShellRenderer}가 HTML 속성으로 이스케이프한다.
 *
 * @param title {@code <title>} (없으면 셸의 원래 제목을 둔다)
 * @param description {@code <meta name="description">}
 * @param canonicalUrl {@code <link rel="canonical">} (쿼리 문자열 없는 절대 주소)
 * @param ogType {@code article}(글) / {@code profile}(블로그)
 * @param ogTitle 미리보기 제목
 * @param ogDescription 미리보기 설명
 * @param ogImage 대표 이미지 절대 주소 (글 첫 사진 <b>원본</b> 또는 기본 이미지)
 * @param publishedTime {@code article:published_time} (최초 공개 일자)
 * @param modifiedTime {@code article:modified_time} (재발행 일자, 있을 때만)
 * @param noindex 검색 엔진 수집 금지 ({@code <meta name="robots" content="noindex">})
 */
public record LinkPreviewMeta(
        String title,
        String description,
        String canonicalUrl,
        String ogType,
        String ogTitle,
        String ogDescription,
        String ogImage,
        String publishedTime,
        String modifiedTime,
        boolean noindex) {

    /** 공개 글·블로그가 아닐 때의 공통 문구 (06 §3-1, FR-045). 004 {@code NotFoundPageRenderer}의 문구와 같다. */
    public static final String UNAVAILABLE_TITLE = "볼 수 없는 글이에요";

    public static final String UNAVAILABLE_DESCRIPTION = "친구 공개·비공개 글이거나 삭제된 글입니다.";

    /** 메타를 넣지 않는다 (셸 원래 값 그대로). */
    public static LinkPreviewMeta empty() {
        return new LinkPreviewMeta(null, null, null, null, null, null, null, null, null, false);
    }

    /** 볼 수 없는 글·작성자가 보는 비공개·숨김·임시 글: 공통 문구 + {@code noindex} (FR-045). */
    public static LinkPreviewMeta unavailable() {
        return new LinkPreviewMeta(
                null,
                null,
                null,
                "article",
                UNAVAILABLE_TITLE,
                UNAVAILABLE_DESCRIPTION,
                null,
                null,
                null,
                true);
    }
}
