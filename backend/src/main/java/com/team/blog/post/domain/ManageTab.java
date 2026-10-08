package com.team.blog.post.domain;

/**
 * 내 글 관리 화면의 탭 (006 T038, FR-003). 요청 값은 소문자({@link #param()})다.
 *
 * <ul>
 *   <li>{@link #DRAFTS} — 휴지통에 없는 {@code DRAFT}
 *   <li>{@link #PUBLISHED} — 휴지통에 없는 {@code PUBLISHED} (숨긴 글 포함, 공개/비공개 필터는 이 탭에만)
 *   <li>{@link #TRASH} — {@code deleted_at IS NOT NULL} (원래 상태 무관)
 * </ul>
 */
public enum ManageTab {
    DRAFTS("drafts"),
    PUBLISHED("published"),
    TRASH("trash");

    private final String param;

    ManageTab(String param) {
        this.param = param;
    }

    /** 요청·커서에 쓰는 소문자 값. */
    public String param() {
        return param;
    }

    /**
     * 요청 값을 탭으로 바꾼다. 없거나 빈 값이면 {@link #DRAFTS}(FR-003 기본 탭).
     *
     * @throws InvalidTabException 모르는 값 (대소문자도 정확히 같아야 한다)
     */
    public static ManageTab fromParam(String raw) {
        if (raw == null || raw.isEmpty()) {
            return DRAFTS;
        }
        for (ManageTab tab : values()) {
            if (tab.param.equals(raw)) {
                return tab;
            }
        }
        throw new InvalidTabException();
    }
}
