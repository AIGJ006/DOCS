package com.team.blog.post.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 내 글 관리 설정값 ({@code blog.manage}, 006 data-model §5, 41 M-6).
 *
 * @param pageSize 관리 목록 한 페이지 글 수 (기본 20). 클라이언트가 보낸 값은 쓰지 않는다(FR-006)
 */
@Validated
@ConfigurationProperties("blog.manage")
public record ManageProperties(@Min(1) @Max(100) @DefaultValue("20") int pageSize) {}
