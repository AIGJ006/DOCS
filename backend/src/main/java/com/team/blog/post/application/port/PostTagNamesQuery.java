package com.team.blog.post.application.port;

import java.util.List;

/**
 * 글의 태그 이름 (입력 순서 {@code post_tag.position}). 구현은 008 {@code TagNamesQueryAdapter}({@code
 * TagService.tagNamesOf}에 맡긴다).
 *
 * <p>조회가 실패하면 상세는 빈 목록으로 계속 응답한다(원칙 V, research R-30).
 */
public interface PostTagNamesQuery {

    List<String> namesInOrder(long postId);
}
