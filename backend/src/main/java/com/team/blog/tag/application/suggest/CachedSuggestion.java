package com.team.blog.tag.application.suggest;

import java.time.Instant;
import java.util.List;

/**
 * 같은 내용 재사용 값 ({@code ai:tag:v{pv}:{sha256}}, 013 data-model §2). 태그는 정규화만 한 목록(이미 붙인 태그를 빼기 전)이다.
 *
 * @param tags 정규화된 이름
 * @param provider 만든 공급자
 * @param createdAt 만든 시각
 */
public record CachedSuggestion(List<String> tags, Provider provider, Instant createdAt) {
    public CachedSuggestion {
        tags = List.copyOf(tags);
    }
}
