package com.team.blog.support.ai;

import com.team.blog.tag.application.suggest.Provider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 시험 프로필의 가짜 AI 공급자 (013 T015). 테스트 소스의 컴포넌트 스캔 대상이라 모든 통합 시험 컨텍스트에 함께 들어간다 — 새 컨텍스트(연결 풀)를 만들지
 * 않으려고 {@code @MockitoBean}·{@code @TestConfiguration} 대신 이렇게 둔다. 실제 {@code
 * GeminiTagSuggester}·{@code OllamaTagSuggester} Bean도 만들어지지만 순서가 뒤라 라우터가 고르지 않는다(외부로 나가는 요청 없음).
 */
@Profile("test")
@Configuration(proxyBeanMethods = false)
public class FakeAiConfiguration {

    @Bean
    FakeAi fakeAi() {
        return new FakeAi(
                new FakeTagSuggester(Provider.GEMINI), new FakeTagSuggester(Provider.OLLAMA));
    }

    @Bean
    FakeTagSuggester fakeGeminiSuggester(FakeAi fakeAi) {
        return fakeAi.gemini();
    }

    @Bean
    FakeTagSuggester fakeOllamaSuggester(FakeAi fakeAi) {
        return fakeAi.ollama();
    }

    /** 두 가짜 공급자 묶음. */
    public record FakeAi(FakeTagSuggester gemini, FakeTagSuggester ollama) {

        public void reset() {
            gemini.reset();
            ollama.reset();
        }

        public int totalCalls() {
            return gemini.calls() + ollama.calls();
        }
    }
}
