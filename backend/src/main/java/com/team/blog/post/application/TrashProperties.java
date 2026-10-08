package com.team.blog.post.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 휴지통 설정값 ({@code blog.post.trash}, 006 data-model §5, constitution VII). 기본값은 {@code
 * application.yml}에도 같게 둔다.
 *
 * @param retention 휴지통 보관 기간 (기본 30일, 13 D-1). {@code purgeAt = deletedAt + retention}
 * @param purgeCron 휴지통 비우기 배치 시각 (기본 매일 03:30, {@code blog.time-zone})
 * @param purgeBatchSize 배치가 한 번에 고르는 글 수 (기본 100, 13 §2-5)
 * @param purgeMaxDuration 배치 한 번 실행의 상한 시간 (기본 30분). 넘으면 다음 묶음을 시작하지 않는다
 */
@Validated
@ConfigurationProperties("blog.post.trash")
public record TrashProperties(
        @NotNull @DefaultValue("P30D") Duration retention,
        @NotBlank @DefaultValue("0 30 3 * * *") String purgeCron,
        @Min(1) @DefaultValue("100") int purgeBatchSize,
        @NotNull @DefaultValue("PT30M") Duration purgeMaxDuration) {}
