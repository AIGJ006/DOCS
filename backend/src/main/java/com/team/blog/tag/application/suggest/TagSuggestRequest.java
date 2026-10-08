package com.team.blog.tag.application.suggest;

import java.util.List;

/**
 * 추천 요청 본문 (013 research R3). 에디터의 지금 값이다(저장 안 된 것 포함). 길이·개수 검사는 판정 순서(400은 404·503·409 뒤)를 지키려고
 * {@code @Valid}가 아니라 {@link TagSuggestService}가 한다.
 *
 * @param title 제목
 * @param contentMd 본문 Markdown
 * @param currentTags 지금 붙인 태그 (없으면 빈 목록)
 * @param refresh [다시 추천] — 같은 글 비슷한 내용 재사용을 건너뛴다
 */
public record TagSuggestRequest(
        String title, String contentMd, List<String> currentTags, Boolean refresh) {

    /** 빈 값을 채운 사본. */
    public TagSuggestRequest {
        currentTags = currentTags == null ? List.of() : List.copyOf(nullSafe(currentTags));
    }

    public boolean isRefresh() {
        return Boolean.TRUE.equals(refresh);
    }

    private static List<String> nullSafe(List<String> tags) {
        return tags.stream().map(t -> t == null ? "" : t).toList();
    }
}
