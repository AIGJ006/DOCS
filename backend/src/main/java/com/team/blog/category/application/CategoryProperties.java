package com.team.blog.category.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 카테고리 설정값 ({@code blog.category.*}, 017 research R11, constitution VII).
 *
 * @param maxCount 회원 한 명이 만들 수 있는 카테고리 수 (최상위·하위 합, 기본 100)
 */
@Validated
@ConfigurationProperties("blog.category")
public record CategoryProperties(@Min(1) @Max(1000) @DefaultValue("100") int maxCount) {}
