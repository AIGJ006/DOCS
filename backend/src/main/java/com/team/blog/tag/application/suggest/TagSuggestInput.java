package com.team.blog.tag.application.suggest;

import java.util.List;

/**
 * 공급자에 넘기는 값 (013 data-model §3). 회원 번호·닉네임·이메일·다른 글은 없다(FR-012).
 *
 * @param text 정리하고 그 공급자 최대 길이로 자른 제목 + 본문
 * @param truncated 잘랐는가
 * @param popularTags 인기 태그 (상위 50 — 공급자별로 {@code PromptBuilder}가 고른다)
 * @param currentTags 이미 붙인 태그 (정규화한 이름)
 */
public record TagSuggestInput(
        String text, boolean truncated, List<String> popularTags, List<String> currentTags) {

    public TagSuggestInput {
        popularTags = List.copyOf(popularTags);
        currentTags = List.copyOf(currentTags);
    }
}
