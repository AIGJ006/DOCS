package com.team.blog.discovery.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 글 읽기 설정값 (005 plan Constraints, data-model §6, constitution VII). 키는 plan 그대로 {@code
 * blog.list.*}·{@code blog.seo.*}·{@code blog.site.*}다. 001 {@code CoreProperties}·002 {@code
 * PostAuthoringProperties}처럼 같은 {@code blog} 접두어를 나눠 바인딩한다(모르는 키는 무시). 기본값은 {@code
 * application.yml}에 둔다.
 *
 * <p>{@code blog.image.public-base-url}·{@code blog.time-zone}은 001 {@code CoreProperties}가 바인딩한다.
 * 클라이언트가 보낸 {@code size}는 어디서도 쓰지 않는다(research R-04).
 *
 * @param list 목록
 * @param seo 링크 미리보기·검색 엔진 메타
 * @param site 사이트 주소
 */
@Validated
@ConfigurationProperties("blog")
public record ReadingProperties(
        @Valid @NotNull @DefaultValue List list,
        @Valid @NotNull @DefaultValue Seo seo,
        @Valid @NotNull @DefaultValue Site site) {

    /**
     * @param pageSize 한 번에 보여 줄 카드 수 (기본 9, 10 L-2). 서버는 이 값 + 1개를 읽어 다음 페이지 유무를 안다
     */
    public record List(@Min(1) @Max(100) @DefaultValue("9") int pageSize) {}

    /**
     * @param descriptionLength 미리보기 설명 최대 글자 수 (기본 160, 40 §5)
     * @param defaultOgImageUrl 대표 이미지가 없을 때 쓰는 서비스 기본 이미지 주소
     */
    public record Seo(
            @Min(1) @DefaultValue("160") int descriptionLength,
            @NotBlank
                    @Pattern(regexp = "^https?://\\S+$")
                    @DefaultValue("http://localhost:8080/og-default.png")
                    String defaultOgImageUrl) {}

    /**
     * @param baseUrl canonical 절대 주소 앞부분 (스킴 + 호스트 + 포트, 끝 {@code /} 없이)
     */
    public record Site(
            @NotBlank
                    @Pattern(regexp = "^https?://[^\\s/]+$")
                    @DefaultValue("http://localhost:8080")
                    String baseUrl) {}
}
