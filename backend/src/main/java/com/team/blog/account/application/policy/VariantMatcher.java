package com.team.blog.account.application.policy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 변형 4가지 포함 검사 (09 §4-2, FR-025). 입력을 소문자로 바꾼 뒤 ① 그대로 ② 숫자 제거 ③ 숫자→영문 치환(0→o, 1→i, 3→e, 4→a, 5→s,
 * 7→t) ④ 1→l 중 하나라도 목록의 단어를 포함하면 걸린다. 각 변형에서 예외 단어를 먼저 지운다. 금칙어 필터와 닉네임 예약어 검사(09 §5)가 같은 규칙을 쓴다.
 *
 * <p>어떤 단어에 걸렸는지는 밖으로 내보내지 않는다(N-5).
 */
final class VariantMatcher {

    private final List<String> words;
    private final List<String> exceptions;

    VariantMatcher(Collection<String> words, Collection<String> exceptions) {
        this.words = normalize(words);
        // 긴 예외 단어부터 지워야 짧은 예외 단어가 긴 단어의 일부만 지우는 일이 없다
        this.exceptions =
                normalize(exceptions).stream()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList();
    }

    boolean matches(String input) {
        if (input == null || input.isEmpty() || words.isEmpty()) {
            return false;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        return containsAny(lower)
                || containsAny(lower.replaceAll("[0-9]", ""))
                || containsAny(substituteDigits(lower))
                || containsAny(lower.replace('1', 'l'));
    }

    private boolean containsAny(String variant) {
        String cleaned = variant;
        for (String exception : exceptions) {
            cleaned = cleaned.replace(exception, "");
        }
        for (String word : words) {
            if (cleaned.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String substituteDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            out.append(
                    switch (c) {
                        case '0' -> 'o';
                        case '1' -> 'i';
                        case '3' -> 'e';
                        case '4' -> 'a';
                        case '5' -> 's';
                        case '7' -> 't';
                        default -> c;
                    });
        }
        return out.toString();
    }

    private static List<String> normalize(Collection<String> values) {
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
