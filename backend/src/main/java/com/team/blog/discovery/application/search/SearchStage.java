package com.team.blog.discovery.application.search;

/**
 * 글 검색 단계 (012 data-model §3, research R7, FR-027). 관련도순은 {@link #TITLE} → {@link #TITLE_OR_TAG} →
 * {@link #ANYWHERE}, 최신순은 {@link #ANY} 하나. {@link #code()}는 커서 키 첫 칸이다.
 */
public enum SearchStage {
    /** 최신순: 모든 단어가 어딘가에. */
    ANY(0),
    /** ① 모든 단어가 제목에. */
    TITLE(1),
    /** ② ①이 아니고 모든 단어가 제목 또는 태그에. */
    TITLE_OR_TAG(2),
    /** ③ ①②가 아니고 모든 단어가 제목·태그·본문(3글자 이상만) 어딘가에. */
    ANYWHERE(3);

    private final int code;

    SearchStage(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** 코드 → 단계. 없으면 {@code null}. */
    public static SearchStage ofCode(long code) {
        for (SearchStage stage : values()) {
            if (stage.code == code) {
                return stage;
            }
        }
        return null;
    }
}
