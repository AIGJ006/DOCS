package com.team.blog.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 공통 설정값 ({@code blog.*}). 각 기능은 자기 모듈에 {@code @ConfigurationProperties("blog.<기능>")} 클래스를 더한다
 * (constitution VII). 같은 {@code blog} 접두어를 여러 클래스가 나눠 바인딩해도 된다(모르는 키는 무시).
 *
 * @param timeZone 서비스 시간대. cron·날짜 경계 계산에 쓴다 (기본 Asia/Seoul)
 * @param image 이미지 저장소 공개 주소. CSP와 이미지 주소·정화 허용 목록이 같은 값을 쓴다 (12 §8, H3)
 * @param scheduling 스케줄러 스레드 수
 * @param async 비동기 실행기(이벤트·메일) 크기
 */
@Validated
@ConfigurationProperties("blog")
public record CoreProperties(
        @NotNull @DefaultValue("Asia/Seoul") ZoneId timeZone,
        @Valid @NotNull @DefaultValue Image image,
        @Valid @NotNull @DefaultValue Scheduling scheduling,
        @Valid @NotNull @DefaultValue Async async) {

    /**
     * @param publicBaseUrl 공개 버킷 직접 주소 (필수, 예: {@code http://localhost:9000/blog})
     * @param legacyBaseUrls 예전 공개 주소 (002 본문 정화 허용 목록이 함께 받는다, 기본 없음)
     */
    public record Image(
            @NotBlank @Pattern(regexp = "^https?://[^\\s/]+(/.*)?$") String publicBaseUrl,
            @DefaultValue List<String> legacyBaseUrls) {

        /** 공개 주소의 출처(스킴 + 호스트 + 포트). CSP {@code img-src}·{@code connect-src}에 넣는다. */
        public String publicOrigin() {
            URI uri = URI.create(publicBaseUrl);
            String origin = uri.getScheme() + "://" + uri.getHost();
            return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
        }
    }

    /**
     * @param poolSize 스케줄러 스레드 수 (기본 2)
     */
    public record Scheduling(@Min(1) @DefaultValue("2") int poolSize) {}

    /**
     * @param event 도메인 이벤트 리스너 실행기
     * @param mail 메일 발송 실행기
     */
    public record Async(
            @Valid @NotNull @DefaultValue Pool event, @Valid @NotNull @DefaultValue Pool mail) {}

    /**
     * @param coreSize 기본 스레드 수
     * @param maxSize 최대 스레드 수
     * @param queueCapacity 대기열 크기 (가득 차면 경고 로그 후 버림 — 유실 허용)
     * @param awaitTermination 종료 때 남은 작업을 기다리는 시간 (기본 10초, 이벤트 실행기는 20초 — 011 research R5). 그 안에 못
     *     끝낸 작업 수는 경고 로그로 남는다
     */
    public record Pool(
            @Min(1) @DefaultValue("2") int coreSize,
            @Min(1) @DefaultValue("4") int maxSize,
            @Min(0) @DefaultValue("500") int queueCapacity,
            @NotNull @DefaultValue("10s") Duration awaitTermination) {}
}
