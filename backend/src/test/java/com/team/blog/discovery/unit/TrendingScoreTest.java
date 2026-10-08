package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.team.blog.discovery.application.trending.TrendingProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 트렌딩 점수식 (012 T022, FR-004, data-model §8). */
class TrendingScoreTest {

    private final TrendingProperties p =
            new TrendingProperties(
                    Duration.ofDays(7),
                    "0 */10 * * * *",
                    true,
                    100,
                    Duration.ofMinutes(30),
                    3,
                    3,
                    2,
                    0.1,
                    2,
                    1.5,
                    18);

    @Test
    void 점수식은_가중합_나누기_경과시간_더하기_2의_1_5제곱() {
        // (3×4 + 2×2 + 0.1×10) / (1 + 2)^1.5 = 17 / 5.196...
        assertThat(p.score(4, 2, 10, 1)).isCloseTo(17 / Math.pow(3, 1.5), within(1e-9));
        assertThat(p.score(0, 0, 0, 5)).isZero();
    }

    @Test
    void 같은_반응이면_1시간_된_글이_하루_된_글보다_위() {
        assertThat(p.score(5, 1, 30, 1)).isGreaterThan(p.score(5, 1, 30, 24));
    }

    @Test
    void 가중치는_설정값을_따른다() {
        TrendingProperties likesOnly =
                new TrendingProperties(
                        Duration.ofDays(7),
                        "-",
                        false,
                        100,
                        Duration.ofMinutes(30),
                        3,
                        1,
                        0,
                        0,
                        2,
                        1,
                        18);
        assertThat(likesOnly.score(6, 100, 1000, 0)).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void 좋아요_하나가_조회_서른보다_무겁다() {
        assertThat(p.score(1, 0, 0, 3)).isGreaterThan(p.score(0, 0, 29, 3));
    }
}
