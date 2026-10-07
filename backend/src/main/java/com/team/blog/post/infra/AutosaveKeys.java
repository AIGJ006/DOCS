package com.team.blog.post.infra;

/** 자동 저장 Redis 키 (data-model §4). */
public final class AutosaveKeys {

    /** DB에 아직 반영하지 않은 글 번호 Set. */
    public static final String DIRTY = "autosave:dirty";

    static final String FIELD_MEMBER_ID = "memberId";
    static final String FIELD_TITLE = "title";
    static final String FIELD_CONTENT_MD = "contentMd";
    static final String FIELD_VERSION = "version";
    static final String FIELD_SAVED_AT = "savedAt";

    private AutosaveKeys() {}

    /** {@code autosave:post:{postId}} Hash. */
    public static String post(long postId) {
        return "autosave:post:" + postId;
    }
}
