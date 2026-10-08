package com.team.blog.tag.application.suggest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 002 {@code blog.post.*} 중 추천 요청 검사에 쓰는 값 (013 research R4 ⑥). post 모듈 설정 클래스를 tag 모듈이 직접 가져다 쓰지
 * 않으려고 같은 접두어를 따로 읽는다(discovery {@code ReadingProperties}와 같은 방식). 기본값은 002 {@code
 * PostAuthoringProperties}와 같다.
 *
 * @param maxTags 글 하나의 태그 최대 수
 * @param titleMax 제목 최대 길이 (코드 포인트)
 * @param contentMax 본문 최대 길이 (코드 포인트)
 */
@Validated
@ConfigurationProperties("blog.post")
public record SuggestPostLimits(
        @Min(0) @Max(100) @DefaultValue("10") int maxTags,
        @Min(1) @DefaultValue("100") int titleMax,
        @Min(1) @DefaultValue("100000") int contentMax) {}
