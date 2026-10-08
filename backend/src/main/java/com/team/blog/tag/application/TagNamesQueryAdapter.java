package com.team.blog.tag.application;

import com.team.blog.post.application.port.PostTagNamesQuery;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 글 상세의 태그 이름 (008 T036). 005가 두었던 기본 구현을 넘겨받아 {@link TagService#tagNamesOf(long)}({@code
 * post_tag.position} 순서)에 맡긴다. 저장된 이름은 발행 때 이미 정규화되어 있다.
 */
@Component
public class TagNamesQueryAdapter implements PostTagNamesQuery {

    private final TagService tagService;

    public TagNamesQueryAdapter(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    public List<String> namesInOrder(long postId) {
        return tagService.tagNamesOf(postId);
    }
}
