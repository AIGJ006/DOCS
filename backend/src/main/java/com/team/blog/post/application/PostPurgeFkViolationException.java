package com.team.blog.post.application;

/**
 * 완전 삭제 중 FK 위반 (006 research R22). SQLSTATE {@code 23001}({@code restrict_violation})과 {@code
 * 23503}({@code foreign_key_violation})을 모두 이것으로 바꾼다. 51 기준으로 {@code post}·{@code comment}를 가리키는
 * FK는 모두 CASCADE 또는 SET NULL이라 정상 흐름에서는 생기지 않는다 — 생기면 스키마가 바뀐 것이다. 요청이면 500, 배치면 그 글만 건너뛴다.
 */
public class PostPurgeFkViolationException extends RuntimeException {

    private final long postId;

    public PostPurgeFkViolationException(long postId, Throwable cause) {
        super("완전 삭제 중 FK 위반: postId=" + postId, cause);
        this.postId = postId;
    }

    public long postId() {
        return postId;
    }
}
