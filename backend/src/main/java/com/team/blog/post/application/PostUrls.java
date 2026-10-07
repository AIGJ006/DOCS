package com.team.blog.post.application;

/** 글 주소 규칙 (005 화면 경로와 같음). */
public final class PostUrls {

    private PostUrls() {}

    /** {@code /@{handle}/posts/{postId}}. */
    public static String of(String handle, long postId) {
        return "/@" + handle + "/posts/" + postId;
    }
}
