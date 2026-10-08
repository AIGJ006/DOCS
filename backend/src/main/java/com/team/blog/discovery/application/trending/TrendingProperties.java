package com.team.blog.discovery.application.trending;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 트렌딩 설정값 ({@code blog.trending.*}, 012 research R17, data-model §7, constitution VII). 기본값은 {@code
 * application.yml}에 둔다.
 *
 * <p>점수 = {@code (weightLike × 좋아요 + weightCommenter × 남의 댓글 작성자 + weightView × 조회) / (경과 시간 +
 * offsetHours)^gravity} (FR-004).
 *
 * @param window 후보 기간 — 최초 공개가 이 기간 안인 글 (기본 7일, FR-003)
 * @param refreshCron 스냅샷 갱신 주기 (기본 10분마다, FR-009). {@code -}면 예약 실행을 끈다(시험 프로필)
 * @param refreshOnStartup 앱이 뜬 직후 한 번 더 만들지 (기본 true, research R4)
 * @param snapshotSize 스냅샷에 넣는 글 수 (기본 100)
 * @param snapshotTtl 스냅샷 보관 시간 (기본 30분, FR-011)
 * @param perAuthor 작성자당 최대 글 수 (기본 3, FR-007)
 * @param weightLike 좋아요 가중치 (기본 3)
 * @param weightCommenter 남의 댓글 작성자 가중치 (기본 2)
 * @param weightView 조회 가중치 (기본 0.1)
 * @param offsetHours 경과 시간 보정 (기본 2시간)
 * @param gravity 경과 시간 지수 (기본 1.5)
 * @param readChunk 건너뛰기용으로 한 번에 읽는 번호 수 (기본 18 — 9개 화면 두 배)
 */
@Validated
@ConfigurationProperties("blog.trending")
public record TrendingProperties(
        @NotNull @DefaultValue("7d") Duration window,
        @NotBlank @DefaultValue("0 */10 * * * *") String refreshCron,
        @DefaultValue("true") boolean refreshOnStartup,
        @Min(1) @Max(1000) @DefaultValue("100") int snapshotSize,
        @NotNull @DefaultValue("30m") Duration snapshotTtl,
        @Min(1) @DefaultValue("3") int perAuthor,
        @DecimalMin("0") @DefaultValue("3") double weightLike,
        @DecimalMin("0") @DefaultValue("2") double weightCommenter,
        @DecimalMin("0") @DefaultValue("0.1") double weightView,
        @DecimalMin(value = "0", inclusive = false) @DefaultValue("2") double offsetHours,
        @DecimalMin(value = "0", inclusive = false) @DefaultValue("1.5") double gravity,
        @Min(1) @Max(1000) @DefaultValue("18") int readChunk) {

    /** 점수 (FR-004). SQL과 같은 식이며 단위 시험·문서용이다. */
    public double score(long likeCount, long commenters, long viewCount, double ageHours) {
        double reactions =
                weightLike * likeCount + weightCommenter * commenters + weightView * viewCount;
        return reactions / Math.pow(Math.max(0, ageHours) + offsetHours, gravity);
    }
}
