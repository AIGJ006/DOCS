package com.team.blog.tag.application.suggest;

import java.time.Instant;
import java.util.List;

/**
 * 같은 글 비슷한 내용 재사용 값 ({@code ai:tag:post:{postId}}, 013 data-model §2).
 *
 * @param input 정리된 입력 앞부분 ({@code cache.post-input-chars} 코드 포인트)
 * @param tags 정규화된 이름
 * @param provider 만든 공급자
 * @param createdAt 만든 시각
 */
public record PostCachedSuggestion(
        String input, List<String> tags, Provider provider, Instant createdAt) {
    public PostCachedSuggestion {
        tags = List.copyOf(tags);
    }
}
