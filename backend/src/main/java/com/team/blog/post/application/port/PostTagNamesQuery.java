package com.team.blog.post.application.port;

import java.util.List;

/**
 * 글의 태그 이름 (입력 순서 {@code post_tag.position}). <b>008 태그 기능이 소유를 넘겨받는다</b> — 그때 {@code
 * TagService.findNamesInOrder}를 쓰는 구현을 Bean으로 등록하면 005의 기본 구현({@code PostReadingPorts})이 물러난다.
 *
 * <p>조회가 실패하면 상세는 빈 목록으로 계속 응답한다(원칙 V, research R-30).
 */
public interface PostTagNamesQuery {

    List<String> namesInOrder(long postId);
}
