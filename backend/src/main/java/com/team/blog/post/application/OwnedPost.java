package com.team.blog.post.application;

import com.team.blog.post.domain.Visibility;
import java.util.Objects;

/**
 * 작성자 본인의 휴지통 밖 글 (013 data-model §3). 다른 모듈은 글 주인·공개 범위를 이 값으로만 안다.
 *
 * @param id 글 번호
 * @param visibility 지금 공개 범위 (013 AI 추천: {@code PUBLIC}이 아니면 외부로 보내지 않는다)
 */
public record OwnedPost(long id, Visibility visibility) {
    public OwnedPost {
        Objects.requireNonNull(visibility, "visibility");
    }
}
