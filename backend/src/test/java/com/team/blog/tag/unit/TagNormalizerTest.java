package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.policy.BannedWordFilter;
import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.tag.domain.TagNormalization;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.domain.TagReasonCode;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.core.io.ClassPathResource;

/**
 * 태그 정규화 (008 T006, contracts/normalization.md §1·§2, SC-001). 예시 표는 화면 {@code
 * normalizeTag.test.ts}와 같은 {@code tag/normalization-cases.csv}를 읽는다. 금칙어 행은 {@code
 * policy/banned-words.txt}의 첫 단어로 바꾼다(단어를 테스트·문서에 적지 않는다).
 */
class TagNormalizerTest {

    /** CSV의 역슬래시-u 네 자리 표기. */
    private static final Pattern ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    private static final List<String> BANNED =
            List.copyOf(ReservedWords.readList(new ClassPathResource("policy/banned-words.txt")));

    private final TagNormalizer normalizer =
            new TagNormalizer(
                    new BannedWordFilter(
                            BANNED,
                            ReservedWords.readList(
                                    new ClassPathResource("policy/banned-words-exceptions.txt"))));

    /** CSV 입력 칸을 실제 문자열로 푼다 (표기 풀기 + 금칙어 자리표시). */
    static String unescape(String raw) {
        if (raw == null) {
            return "";
        }
        String banned = BANNED.get(0);
        String value =
                raw.replace(
                                "{BANNED_0_DIGIT}",
                                banned.codePoints()
                                        .mapToObj(Character::toString)
                                        .collect(Collectors.joining("1")))
                        .replace("{BANNED_0}", banned);
        Matcher m = ESCAPE.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(
                    out,
                    Matcher.quoteReplacement(Character.toString(Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(out);
        return out.toString();
    }

    @ParameterizedTest(name = "{3} [{0}] → [{1}] {2}")
    @CsvFileSource(
            resources = "/tag/normalization-cases.csv",
            numLinesToSkip = 1,
            nullValues = {})
    void 예시_표(String input, String expected, String code, String note) {
        TagNormalization result = normalizer.normalize(unescape(input));
        if (code == null || code.isEmpty()) {
            assertThat(result).isEqualTo(new TagNormalization.Accepted(expected));
        } else {
            assertThat(result)
                    .isEqualTo(new TagNormalization.Rejected(TagReasonCode.valueOf(code)));
        }
    }

    @ParameterizedTest(name = "검색어 [{0}]")
    @CsvFileSource(
            resources = "/tag/normalization-cases.csv",
            numLinesToSkip = 1,
            nullValues = {})
    void 검색어_정규화는_금칙어를_빼면_예시_표와_같다(String input, String expected, String code, String note) {
        var query = normalizer.normalizeQuery(unescape(input));
        if (code == null || code.isEmpty()) {
            assertThat(query).contains(expected);
        } else if ("TAG_BANNED_WORD".equals(code)) {
            assertThat(query).as("금칙어는 검색어에서 거르지 않는다").isPresent();
        } else {
            assertThat(query).isEmpty();
        }
    }

    @Test
    void 우선순위_허용_밖_문자가_길이보다_먼저다() {
        String fire31 = Character.toString(0x1F525).repeat(31);
        assertThat(normalizer.normalize(fire31))
                .isEqualTo(new TagNormalization.Rejected(TagReasonCode.INVALID_TAG));
    }

    @Test
    void 우선순위_길이가_금칙어보다_먼저다() {
        String longBanned = BANNED.get(0) + "a".repeat(31 - BANNED.get(0).length());
        assertThat(longBanned.codePointCount(0, longBanned.length())).isEqualTo(31);
        assertThat(normalizer.normalize(longBanned))
                .isEqualTo(new TagNormalization.Rejected(TagReasonCode.TAG_TOO_LONG));
    }

    @Test
    void 검색어_정규화는_금칙어를_거르지_않고_실패면_빈_값() {
        assertThat(normalizer.normalizeQuery("#" + BANNED.get(0).toUpperCase(Locale.ROOT)))
                .contains(BANNED.get(0));
        assertThat(normalizer.normalizeQuery("a".repeat(31))).isEmpty();
        assertThat(normalizer.normalizeQuery(Character.toString(0x1F525))).isEmpty();
        assertThat(normalizer.normalizeQuery("#")).isEmpty();
        assertThat(normalizer.normalizeQuery("   ")).isEmpty();
        assertThat(normalizer.normalizeQuery(null)).isEmpty();
        assertThat(normalizer.normalizeQuery("Spring Boot")).contains("spring-boot");
    }

    @Test
    void 터키어_언어_설정에서도_대문자_I는_i가_된다() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalizer.normalize("IDE")).isEqualTo(new TagNormalization.Accepted("ide"));
            assertThat(normalizer.normalizeQuery("LINUX")).contains("linux");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void null과_빈_문자열은_INVALID_TAG() {
        TagNormalization invalid = new TagNormalization.Rejected(TagReasonCode.INVALID_TAG);
        assertThat(normalizer.normalize(null)).isEqualTo(invalid);
        assertThat(normalizer.normalize("")).isEqualTo(invalid);
        assertThat(normalizer.normalize("   ")).isEqualTo(invalid);
    }

    @Test
    void 제어_문자는_공백보다_먼저_지워져_하이픈이_되지_않는다() {
        assertThat(normalizer.normalize("a" + (char) 9 + "b"))
                .isEqualTo(new TagNormalization.Accepted("ab"));
    }

    @Test
    void 금칙어_거부_결과에는_단어가_없다() {
        TagNormalization result = normalizer.normalize("x" + BANNED.get(0));
        assertThat(result).isEqualTo(new TagNormalization.Rejected(TagReasonCode.TAG_BANNED_WORD));
        assertThat(TagNormalization.Rejected.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("code");
        assertThat(result.toString()).doesNotContain(BANNED.get(0));
        assertThat(Arrays.stream(TagReasonCode.values()).map(TagReasonCode::defaultMessage))
                .noneMatch(message -> message.contains(BANNED.get(0)) || message.endsWith("."));
    }

    @Test
    void 오류_코드와_문구() {
        assertThat(TagReasonCode.INVALID_TAG.defaultMessage()).isEqualTo("쓸 수 없는 글자가 있어요");
        assertThat(TagReasonCode.TAG_TOO_LONG.defaultMessage()).isEqualTo("태그는 30자까지 쓸 수 있어요");
        assertThat(TagReasonCode.TAG_BANNED_WORD.defaultMessage()).isEqualTo("쓸 수 없는 단어가 들어 있어요");
        assertThat(TagReasonCode.TAG_TOO_LONG.fieldError("tags[3]"))
                .satisfies(
                        e -> {
                            assertThat(e.field()).isEqualTo("tags[3]");
                            assertThat(e.code()).isEqualTo("TAG_TOO_LONG");
                        });
        assertThat(TagReasonCode.INVALID_TAG.status().value()).isEqualTo(400);
    }
}
