package com.team.blog.account.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Provider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 블로그 주소 미리 채우기 ①~⑩ (FR-017, 08 §3, R-15, SC-007). 08 §3 예시 12개를 위에서부터 차례로 가입했다고 보고, 각 행은 앞 행들의 결과가
 * 이미 있는 상태에서 계산한다.
 */
class HandleSuggesterTest {

    private static final String RANDOM = "RANDOM";

    /** 08 §3 예시 표 (가입 수단, 이메일, 미리 채워지는 주소). 난수 6자리는 정규식으로 본다. */
    private static final List<String[]> EXAMPLES =
            List.of(
                    new String[] {"LOCAL", "kim755030@naver.com", "kim755030"},
                    new String[] {"LOCAL", "kim755030@daum.net", "kim755030_2"},
                    new String[] {"GOOGLE", "kim755030@gmail.com", "go-kim755030"},
                    new String[] {"GITHUB", "kim755030@naver.com", "gi-kim755030"},
                    new String[] {"LOCAL", "kim755030@gmail.com", "kim755030_3"},
                    new String[] {"LOCAL", "gokim@naver.com", "gokim"},
                    new String[] {"LOCAL", "Kim.Min-Seo+blog@naver.com", "kim_min_seo"},
                    new String[] {"LOCAL", "_kim__min_@x.com", "kim_min"},
                    new String[] {"LOCAL", "admin@x.com", "admin_2"},
                    new String[] {"LOCAL", "ab@x.com", RANDOM},
                    new String[] {"GOOGLE", "김민서@한국.kr", "go-" + RANDOM},
                    new String[] {
                        "GITHUB", "12345678+octocat@users.noreply.github.com", "gi-12345678"
                    });

    static Stream<Arguments> examples() {
        List<Arguments> rows = new ArrayList<>();
        Set<String> existing = new HashSet<>();
        for (String[] row : EXAMPLES) {
            rows.add(Arguments.of(Provider.valueOf(row[0]), row[1], row[2], Set.copyOf(existing)));
            if (!row[2].contains(RANDOM)) {
                existing.add(row[2]);
            }
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "[{index}] {0} {1} → {2}")
    @MethodSource("examples")
    void matchesDocumentExamples(
            Provider provider, String email, String expected, Set<String> existing) {
        HandleSuggester suggester = suggester(existing);
        String suggested = suggester.suggest(email, provider);
        if (expected.contains(RANDOM)) {
            String prefix = expected.replace(RANDOM, "");
            assertThat(suggested).matches(java.util.regex.Pattern.quote(prefix) + "user_\\d{6}");
        } else {
            assertThat(suggested).isEqualTo(expected);
        }
        assertThat(HandlePolicy.matchesFormat(suggested)).isTrue();
    }

    @Test
    void emailDerivedHandlesNeverContainHyphenOrUppercase() {
        HandleSuggester suggester = suggester(Set.of());
        for (String email :
                List.of(
                        "Kim.Min-Seo@x.com",
                        "A-B-C-D@x.com",
                        "UPPER.CASE@x.com",
                        "x--y..z@x.com")) {
            String handle = suggester.suggest(email, Provider.LOCAL);
            assertThat(handle).doesNotContain("-").isLowerCase();
        }
    }

    @Test
    void steps1to8AreAPureFunction() {
        assertThat(HandleSuggester.bodyFromEmail("kim+blog@naver.com")).isEqualTo("kim");
        assertThat(HandleSuggester.bodyFromEmail("Kim.Min-Seo@x.com")).isEqualTo("kim_min_seo");
        assertThat(HandleSuggester.bodyFromEmail("_kim__min_@x.com")).isEqualTo("kim_min");
        // ⑦ 30자로 자르고 끝의 _는 지운다
        assertThat(HandleSuggester.bodyFromEmail("abcdefghijklmnopqrstuvwxyz123.4567@x.com"))
                .isEqualTo("abcdefghijklmnopqrstuvwxyz123");
        assertThat(HandleSuggester.bodyFromEmail("abcdefghijklmnopqrstuvwxyz1234567@x.com"))
                .hasSize(30);
        // ⑧ 3자 미만이면 user_ + 6자리
        assertThat(HandleSuggester.bodyFromEmail("ab@x.com")).isNull();
        assertThat(HandleSuggester.bodyFromEmail("김민서@x.com")).isNull();
        assertThat(HandleSuggester.bodyFromEmail("no-at-sign")).isEqualTo("no_at_sign");
    }

    @Test
    void randomFallbackIsUserPlusSixDigits() {
        HandleSuggester suggester = suggester(Set.of());
        for (int i = 0; i < 50; i++) {
            assertThat(suggester.suggest("a@x.com", Provider.LOCAL)).matches("user_\\d{6}");
        }
    }

    @Test
    void nextAvailablePicksFirstFreeNumber() {
        HandleSuggester suggester =
                suggester(Set.of("kim", "kim_2", "kim_4", "kimchi", "kim_x", "gokim"));
        assertThat(suggester.nextAvailable("kim")).isEqualTo("kim_3");
        assertThat(suggester.nextAvailable("lee")).isEqualTo("lee");
        assertThat(suggester.nextAvailable("go-admin")).isEqualTo("go-admin_2");
    }

    @Test
    void nextAvailableQueriesLookupOnce() {
        int[] calls = {0};
        HandleSuggester suggester =
                new HandleSuggester(
                        PolicyTestLists.reservedHandles(),
                        base -> {
                            calls[0]++;
                            return Set.of(base, base + "_2");
                        });
        assertThat(suggester.nextAvailable("kim")).isEqualTo("kim_3");
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void overflowingSuffixTrimsBodyEnd() {
        // 본문 36자 + "_2"는 본문 36자(전체 39자)를 넘는다 → 본문 끝을 잘라 맞춘다 (끝이 _면 지움)
        String body36 = "abcdefghijklmnopqrstuvwxyz012345678_";
        String base = "gi-" + body36.substring(0, 35) + "9";
        HandleSuggester suggester = suggester(Set.of(base));
        String next = suggester.nextAvailable(base);
        assertThat(next).hasSizeLessThanOrEqualTo(39).endsWith("_2");
        assertThat(HandlePolicy.matchesFormat(next)).isTrue();
        assertThat(next).startsWith("gi-abcdefghijklmnopqrstuvwxyz01234");

        // 이메일 가입(접두어 없음)은 본문이 곧 전체라 36자가 한도다
        String underscoreAtCut = "abcdefghijklmnopqrstuvwxyz0123456_78";
        HandleSuggester another = suggester(Set.of(underscoreAtCut));
        String local = another.nextAvailable(underscoreAtCut);
        assertThat(local).isEqualTo("abcdefghijklmnopqrstuvwxyz0123456_2");
        assertThat(HandlePolicy.matchesFormat(local)).isTrue();
    }

    private static HandleSuggester suggester(Set<String> existing) {
        return new HandleSuggester(
                PolicyTestLists.reservedHandles(),
                base ->
                        existing.stream()
                                .filter(h -> h.equals(base) || h.startsWith(base + "_"))
                                .collect(java.util.stream.Collectors.toSet()));
    }
}
