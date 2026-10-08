package com.team.blog.tag.infra.ai;

import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 프롬프트 (013 T014, contracts/providers.md §2, {@code prompt-version = 1}). 순서는 고정 — ① 지시문 → ② 인기 태그
 * → ③ 이미 붙인 태그 → ④ 제목과 본문. 앞부분이 늘 같아 공급자 쪽 앞부분 재사용에 유리하다(34 §4). 회원 정보·다른 글은 넣지 않는다(FR-012).
 *
 * <p>지시문을 바꾸면 {@code blog.ai.tag-suggest.prompt-version}을 올린다(같은 내용 재사용 키가 바뀜).
 */
@Component
public class PromptBuilder {

    /** ① 고정 지시문 (Ollama는 스키마 설명을 모델에 전하지 않으므로 형식 설명도 여기에 둔다). */
    public static final String SYSTEM =
            """
            너는 기술 블로그 글에 붙일 태그를 고른다.
            - 글에 실제로 나오는 기술·주제만 고른다. 글에 없는 기술은 넣지 않는다.
            - 최대 5개. 짧은 소문자 표기를 쓴다. 아래 인기 태그에 같은 뜻이 있으면 그 표기를 쓴다.
            - 이미 붙인 태그는 고르지 않는다.
            - {"tags": [...]} JSON으로만 답한다.""";

    /** 시스템 지시문과 사용자 내용. */
    public record Prompt(String system, String user) {}

    public Prompt build(Provider provider, TagSuggestInput input) {
        List<String> popular =
                provider == Provider.OLLAMA
                        ? appearingIn(input.popularTags(), input.text())
                        : input.popularTags();
        String user =
                "인기 태그: "
                        + (popular.isEmpty() ? "없음" : String.join(", ", popular))
                        + "\n이미 붙인 태그: "
                        + (input.currentTags().isEmpty()
                                ? "없음"
                                : String.join(", ", input.currentTags()))
                        + "\n제목과 본문:\n"
                        + input.text();
        return new Prompt(SYSTEM, user);
    }

    /** 정리된 입력에 실제로 나오는 인기 태그만 (작은 모델이 목록을 그대로 베끼지 않게, 34 §6). 하이픈은 공백으로도 찾는다. */
    static List<String> appearingIn(List<String> popular, String text) {
        String haystack = text.toLowerCase(Locale.ROOT);
        return popular.stream()
                .filter(
                        tag ->
                                haystack.contains(tag)
                                        || (tag.contains("-")
                                                && haystack.contains(tag.replace('-', ' '))))
                .toList();
    }
}
