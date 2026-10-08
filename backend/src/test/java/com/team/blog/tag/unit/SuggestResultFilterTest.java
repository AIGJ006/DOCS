package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.policy.BannedWordFilter;
import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.tag.application.suggest.SuggestPostLimits;
import com.team.blog.tag.application.suggest.SuggestResultFilter;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.domain.TagNormalizer;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 결과 검사 (013 T026, contracts/providers.md §7, SC-007). */
class SuggestResultFilterTest {

    private static final List<String> BANNED =
            List.copyOf(ReservedWords.readList(new ClassPathResource("policy/banned-words.txt")));

    private final SuggestResultFilter filter =
            new SuggestResultFilter(
                    new TagNormalizer(
                            new BannedWordFilter(
                                    BANNED,
                                    ReservedWords.readList(
                                            new ClassPathResource(
                                                    "policy/banned-words-exceptions.txt")))),
                    properties(),
                    new SuggestPostLimits(10, 100, 100_000));

    static TagSuggestProperties properties() {
        return new TagSuggestProperties(
                true,
                1,
                100,
                5,
                20,
                new TagSuggestProperties.Cache(Duration.ofDays(30), Duration.ofDays(7), 0.9, 8000),
                new TagSuggestProperties.Popular(50, Duration.ofDays(1)),
                new TagSuggestProperties.Gemini(
                        "http://x",
                        "m",
                        "",
                        450,
                        ZoneId.of("America/Los_Angeles"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(60),
                        3,
                        8000),
                new TagSuggestProperties.Ollama(
                        true,
                        "http://y",
                        "m",
                        4,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        2000,
                        1));
    }

    @Test
    void 정규화하고_거부된_것과_중복을_뺀다() {
        assertThat(
                        filter.accepted(
                                List.of(
                                        "Spring Boot",
                                        "spring-boot",
                                        "#JPA",
                                        "!!!",
                                        BANNED.get(0),
                                        "a".repeat(31),
                                        "Docker")))
                .containsExactly("spring-boot", "jpa", "docker");
    }

    @Test
    void 이미_붙인_태그는_대소문자_공백_차이도_뺀다() {
        assertThat(
                        filter.select(
                                List.of("spring-boot", "jpa", "docker"),
                                List.of("Spring Boot", " JPA ")))
                .containsExactly("docker");
    }

    @Test
    void 남은_자리만큼_자른다() {
        List<String> accepted = List.of("a1", "a2", "a3", "a4", "a5");
        assertThat(filter.select(accepted, List.of())).hasSize(5);
        List<String> eight = IntStream.range(0, 8).mapToObj(i -> "t" + i).toList();
        assertThat(filter.slots(eight)).isEqualTo(2);
        assertThat(filter.select(accepted, eight)).containsExactly("a1", "a2");
        List<String> ten = IntStream.range(0, 10).mapToObj(i -> "t" + i).toList();
        assertThat(filter.slots(ten)).isZero();
        assertThat(filter.select(accepted, ten)).isEmpty();
    }
}
