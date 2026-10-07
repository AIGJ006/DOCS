package com.team.blog.account.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 금칙어 필터 (FR-025, 09 §4, N-5, R-17). 테스트용 목록: 시발·씨발·병신·shit·fack, 예외: 시발점·시발역. */
class BannedWordFilterTest {

    private final BannedWordFilter filter = PolicyTestLists.bannedWordFilter();

    @ParameterizedTest(name = "[{index}] {0} → 거부")
    @ValueSource(
            strings = {
                "씨발놈", // ① 그대로
                "시1발", // ② 숫자 제거 (09 #4)
                "시1발왕", "병1신", "sh1t", // ③ 숫자→영문 치환 (09 #4)
                "SH1T", // 소문자화 후 검사
                "Shit", "f4ck", // ③ 4→a → fack (변형 표기가 목록에 있음)
                "xshitx", // 포함만 돼도
                "시발점시발", // 예외 단어를 지운 뒤에도 남는 금칙어
            })
    void rejectsBannedWordsAndVariants(String input) {
        assertThat(filter.containsBanned(input)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} → 허용")
    @ValueSource(strings = {"시발점", "시발역", "시발점에서", "김민서", "kim755030", "hello", "", "shirt"})
    void allowsNormalWordsAndExceptions(String input) {
        assertThat(filter.containsBanned(input)).isFalse();
    }

    @Test
    void oneToLVariantCatchesDigitOneUsedAsLetterL() {
        BannedWordFilter withL = new BannedWordFilter(java.util.Set.of("hell"), java.util.Set.of());
        // ③ 치환은 1→i라서 "he11"은 "heii"가 되지만 ④ 1→l 변형이 "hell"을 잡는다
        assertThat(withL.containsBanned("he11o")).isTrue();
        assertThat(withL.containsBanned("helo")).isFalse();
    }

    @Test
    void nullIsNotBanned() {
        assertThat(filter.containsBanned(null)).isFalse();
    }

    @Test
    void resultDoesNotExposeMatchedWord() {
        // 공개 메서드는 boolean만 돌려준다 — 걸린 단어를 알려주지 않는다 (N-5)
        Method[] publicMethods =
                Arrays.stream(BannedWordFilter.class.getDeclaredMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .toArray(Method[]::new);
        assertThat(publicMethods)
                .allSatisfy(m -> assertThat(m.getReturnType()).isEqualTo(boolean.class));
    }
}
