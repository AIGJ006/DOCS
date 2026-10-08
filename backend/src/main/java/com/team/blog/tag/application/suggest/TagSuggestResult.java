package com.team.blog.tag.application.suggest;

import java.util.List;

/**
 * 추천 결과 (013 data-model §4 {@code TagSuggestResponse}).
 *
 * @param tags 정규화·검사 뒤 0~5개
 * @param provider 이 결과를 만든 공급자 (재사용이면 저장된 값)
 * @param cached 재사용 저장소로 답했는가
 * @param truncated 정리된 입력이 그 공급자 최대 길이를 넘었는가
 * @param remainingToday 이번 요청 뒤 오늘 남은 횟수
 */
public record TagSuggestResult(
        List<String> tags,
        Provider provider,
        boolean cached,
        boolean truncated,
        int remainingToday) {

    public TagSuggestResult {
        tags = List.copyOf(tags);
    }
}
