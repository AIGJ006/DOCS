package com.team.blog.post.domain;

import com.team.blog.shared.error.NotFoundException;

/**
 * 없는 글·볼 수 없는 글·남의 글·휴지통 글 → 404 (research R-02·R-26, FR-013·FR-014). 응답 본문은 이유와 상관없이 항상 같고({@link
 * NotFoundException}), 생성자 문구는 서버 로그(DEBUG)에만 남는다.
 */
public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException() {
        super();
    }

    /**
     * @param logMessage 서버 로그에만 남기는 이유 (응답에 실리지 않음)
     */
    public PostNotFoundException(String logMessage) {
        super(logMessage);
    }
}
