package com.team.blog.account.application.policy;

import com.team.blog.account.domain.Provider;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 이메일로 블로그 주소 미리 채우기 (FR-017, 08 §3, R-15).
 *
 * <pre>
 * ① @ 앞부분 ② + 뒤 버림 ③ 소문자 ④ . - → _ ⑤ [a-z0-9_] 외 제거 ⑥ 연속 _ 하나로·처음·끝 _ 제거
 * ⑦ 30자로 자름(끝 _ 제거) ⑧ 3자 미만 → user_ + 6자리 난수 ⑨ 가입 수단 접두어
 * ⑩ 예약어이거나 이미 있으면 _2, _3 … 중 비어 있는 첫 번호
 * </pre>
 *
 * ⑩에서 번호를 붙여 본문이 36자를 넘으면 본문 끝을 잘라 맞춘다(끝이 {@code _}면 지움). 이메일에서 만든 주소에는 {@code -}·대문자가 생기지 않는다.
 */
public class HandleSuggester {

    /** ⑦ 미리 채우기 본문 최대 길이. */
    static final int PREFILL_MAX_LENGTH = 30;

    private static final int MIN_LENGTH = 3;

    private final Set<String> reserved;
    private final HandleLookup lookup;
    private final RandomGenerator random;

    public HandleSuggester(Collection<String> reservedHandles, HandleLookup lookup) {
        this(reservedHandles, lookup, new SecureRandom());
    }

    public HandleSuggester(
            Collection<String> reservedHandles, HandleLookup lookup, RandomGenerator random) {
        this.reserved =
                Set.copyOf(reservedHandles.stream().map(w -> w.toLowerCase(Locale.ROOT)).toList());
        this.lookup = lookup;
        this.random = random;
    }

    /** ①~⑩ 전체. */
    public String suggest(String email, Provider provider) {
        String body = bodyFromEmail(email);
        if (body == null) {
            body = "user_" + String.format("%06d", random.nextInt(1_000_000));
        }
        return nextAvailable(provider.handlePrefix() + body);
    }

    /**
     * ⑩ 예약어(본문 기준)도 아니고 아직 없는 주소면 그대로, 아니면 {@code _2}, {@code _3} … 중 비어 있는 첫 번호. 조회는 보통 한 번이다(본문을
     * 잘라야 할 때만 잘린 본문으로 한 번 더).
     */
    public String nextAvailable(String base) {
        String prefix = HandlePolicy.prefixOf(base);
        String body = base.substring(prefix.length());
        Set<String> taken = lookup.takenWithBase(base);
        if (!reserved.contains(body) && !taken.contains(base)) {
            return base;
        }
        String queriedStem = base;
        for (int n = 2; ; n++) {
            String suffix = "_" + n;
            String stem = prefix + fitBody(body, suffix);
            if (!stem.equals(queriedStem)) {
                taken = lookup.takenWithBase(stem);
                queriedStem = stem;
            }
            String candidate = stem + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** ①~⑧ 중 ⑦까지(난수 제외). 결과가 3자 미만이면 null(→ ⑧). 화면의 미리 채우기와 같은 규칙이다. */
    static String bodyFromEmail(String email) {
        if (email == null) {
            return null;
        }
        String local = email.strip();
        int at = local.indexOf('@');
        if (at >= 0) {
            local = local.substring(0, at); // ①
        }
        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus); // ②
        }
        String value =
                local.toLowerCase(Locale.ROOT) // ③
                        .replace('.', '_')
                        .replace('-', '_') // ④
                        .replaceAll("[^a-z0-9_]", "") // ⑤
                        .replaceAll("_+", "_")
                        .replaceAll("^_|_$", ""); // ⑥
        if (value.length() > PREFILL_MAX_LENGTH) {
            value = value.substring(0, PREFILL_MAX_LENGTH).replaceAll("_+$", ""); // ⑦
        }
        return value.length() < MIN_LENGTH ? null : value;
    }

    private static String fitBody(String body, String suffix) {
        int max = HandlePolicy.MAX_BODY_LENGTH - suffix.length();
        if (body.length() <= max) {
            return body;
        }
        return body.substring(0, max).replaceAll("_+$", "");
    }
}
