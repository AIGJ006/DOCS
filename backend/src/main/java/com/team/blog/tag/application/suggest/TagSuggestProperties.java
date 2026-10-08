package com.team.blog.tag.application.suggest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * AI 태그 추천 설정값 ({@code blog.ai.tag-suggest.*}, 013 data-model §6, research R15, constitution VII).
 * 기본값은 {@code application.yml}에 둔다. 글 태그 상한·제목·본문 길이는 002 {@code blog.post.*}를 {@link
 * SuggestPostLimits}로 함께 읽는다.
 *
 * @param enabled 기능 켜기 (끄면 상태 API {@code available = false}, 추천 503 {@code DISABLED})
 * @param promptVersion 프롬프트 판 — 올리면 같은 내용 재사용 키가 바뀐다
 * @param minInputChars 정리 후 최소 길이 (코드 포인트, 미만이면 422)
 * @param maxSuggestions 한 번에 돌려줄 최대 개수
 * @param dailyLimitPerMember 회원당 하루 AI 호출 수 (한국 시간 0시 초기화)
 * @param cache 재사용 저장소
 * @param popular 인기 태그 목록
 * @param gemini 외부 AI
 * @param ollama 자체 AI
 */
@Validated
@ConfigurationProperties("blog.ai.tag-suggest")
public record TagSuggestProperties(
        @DefaultValue("true") boolean enabled,
        @Min(1) @DefaultValue("1") int promptVersion,
        @Min(1) @DefaultValue("100") int minInputChars,
        @Min(1) @Max(10) @DefaultValue("5") int maxSuggestions,
        @Min(1) @DefaultValue("20") int dailyLimitPerMember,
        @Valid @NotNull @DefaultValue Cache cache,
        @Valid @NotNull @DefaultValue Popular popular,
        @Valid @NotNull @DefaultValue Gemini gemini,
        @Valid @NotNull @DefaultValue Ollama ollama) {

    /**
     * @param exactTtl 같은 내용 재사용 보관 기간 (모든 사용자 공유)
     * @param postTtl 같은 글 비슷한 내용 보관 기간
     * @param similarityThreshold 3-gram Jaccard 기준 (0~1)
     * @param postInputChars 같은 글 항목에 남기는 정리된 입력 앞부분 길이
     */
    public record Cache(
            @NotNull @DefaultValue("30d") Duration exactTtl,
            @NotNull @DefaultValue("7d") Duration postTtl,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.9") double similarityThreshold,
            @Min(1) @DefaultValue("8000") int postInputChars) {}

    /**
     * @param size 프롬프트에 넣는 인기 태그 수 (008 {@code TagQueryService.top()} 앞부분)
     * @param ttl 하루 목록 보관 기간
     */
    public record Popular(
            @Min(0) @DefaultValue("50") int size, @NotNull @DefaultValue("1d") Duration ttl) {}

    /**
     * @param baseUrl API 주소
     * @param model 모델 이름
     * @param apiKey {@code GEMINI_API_KEY}. 비어 있으면 Gemini를 고르지 않는다
     * @param dailyLimit 우리가 세는 하루 외부 호출 한도 (넘으면 다음 초기화까지 자체 AI)
     * @param quotaZone 공급자 하루 한도 초기화 시간대
     * @param connectTimeout 연결 시간 제한
     * @param timeout 응답 시간 제한
     * @param cooldown 분당 한도·시간 초과·서버 오류 뒤 외부 AI를 쉬는 시간
     * @param unknown429ExhaustCount 종류 모르는 429가 하루에 이만큼이면 하루 한도 소진으로 본다
     * @param maxInputChars 보내는 정리된 입력 최대 길이
     */
    public record Gemini(
            @NotBlank @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
            @NotBlank @DefaultValue("gemini-flash-lite") String model,
            @DefaultValue("") String apiKey,
            @Min(1) @DefaultValue("450") int dailyLimit,
            @NotNull @DefaultValue("America/Los_Angeles") ZoneId quotaZone,
            @NotNull @DefaultValue("3s") Duration connectTimeout,
            @NotNull @DefaultValue("10s") Duration timeout,
            @NotNull @DefaultValue("60s") Duration cooldown,
            @Min(1) @DefaultValue("3") int unknown429ExhaustCount,
            @Min(1) @DefaultValue("8000") int maxInputChars) {

        /** 키가 있는가. */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * @param enabled 자체 AI를 쓰는가 (끄면 비공개 글은 추천할 수 없다)
     * @param baseUrl Ollama 주소 (Compose 안 {@code http://ollama:11434})
     * @param model 모델 이름
     * @param numThread 추론 스레드 수 (배포 서버 성능 코어 수 이하)
     * @param connectTimeout 연결 시간 제한
     * @param timeout 응답 시간 제한
     * @param maxInputChars 보내는 정리된 입력 최대 길이
     * @param maxConcurrency 동시 처리 수 (넘으면 503 {@code BUSY})
     */
    public record Ollama(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("http://localhost:11434") String baseUrl,
            @NotBlank @DefaultValue("qwen2.5:1.5b") String model,
            @Min(1) @DefaultValue("4") int numThread,
            @NotNull @DefaultValue("3s") Duration connectTimeout,
            @NotNull @DefaultValue("30s") Duration timeout,
            @Min(1) @DefaultValue("2000") int maxInputChars,
            @Min(1) @DefaultValue("1") int maxConcurrency) {}

    /** 공급자별 최대 입력 길이. */
    public int maxInputChars(Provider provider) {
        return provider == Provider.GEMINI ? gemini.maxInputChars() : ollama.maxInputChars();
    }
}
