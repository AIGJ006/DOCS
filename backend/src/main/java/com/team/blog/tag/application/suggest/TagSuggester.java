package com.team.blog.tag.application.suggest;

/**
 * 태그 추천 공급자 (013 T015, data-model §3). 구현은 {@code tag.infra.ai}의 {@code GeminiTagSuggester}·{@code
 * OllamaTagSuggester}. {@link TagSuggesterRouter}가 공급자마다 하나를 고른다 — 같은 공급자 Bean이 여럿이면 순서(@{@code
 * Order}/{@code Ordered})가 가장 앞선 것(시험의 가짜 공급자가 이렇게 실제 Bean을 대신한다).
 */
public interface TagSuggester {

    Provider provider();

    /**
     * 설정상 쓸 수 있는가. Gemini는 키가 비어 있으면, Ollama는 {@code ollama.enabled = false}면 {@code false} — Bean은
     * 있지만 라우터가 고르지 않는다.
     */
    boolean isConfigured();

    /** 예외를 던지지 않는다 — 실패도 {@link SuggestOutcome}으로 돌려준다. */
    SuggestOutcome suggest(TagSuggestInput input);
}
