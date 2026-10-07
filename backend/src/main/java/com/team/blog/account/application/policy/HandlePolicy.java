package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.account.domain.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 블로그 주소 검사 (FR-016·019, 08 §2·§5, R-15).
 *
 * <ul>
 *   <li>형식: {@link #FORMAT} — DB {@code ck_member_handle}과 같은 정규식이며, 길이(본문 3~36자)도 여기서 정해지므로 설정값이
 *       아닌 코드 상수다(data-model §6).
 *   <li>접두어와 가입 수단 일치: 이메일 가입은 접두어 없음, Google {@code go-}, GitHub {@code gi-}.
 *   <li>예약어: 접두어를 뺀 <b>본문</b>이 목록과 같으면 거부({@code go-admin}도 거부).
 *   <li>금칙어: 본문에 {@link BannedWordFilter}.
 * </ul>
 *
 * 중복은 DB 조회가 필요하므로 여기서 보지 않는다({@link HandleSuggester}·가입 Service·{@code uq_member_handle}).
 */
public class HandlePolicy {

    /** 08 §2 형식 (DB {@code ck_member_handle}과 같음). */
    public static final Pattern FORMAT =
            Pattern.compile("^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$");

    /** 본문 최대 길이 (DB CHECK와 같아야 하는 코드 상수). */
    public static final int MAX_BODY_LENGTH = 36;

    private static final Pattern PREFIX = Pattern.compile("^(go|gi)-");

    private final Set<String> reserved;
    private final BannedWordFilter bannedWordFilter;

    public HandlePolicy(Collection<String> reservedHandles, BannedWordFilter bannedWordFilter) {
        this.reserved = Set.copyOf(reservedHandles.stream().map(HandlePolicy::lower).toList());
        this.bannedWordFilter = bannedWordFilter;
    }

    /** 실패 이유 코드 목록 (통과하면 빈 목록). 형식이 틀리면 형식 오류 하나만, 아니면 접두어 불일치·예약어·금칙어를 순서대로 모두 담는다. */
    public List<AccountReasonCode> validate(String handle, Provider provider) {
        if (!matchesFormat(handle)) {
            return List.of(AccountReasonCode.HANDLE_INVALID_FORMAT);
        }
        List<AccountReasonCode> failures = new ArrayList<>(2);
        if (!prefixOf(handle).equals(provider.handlePrefix())) {
            failures.add(AccountReasonCode.HANDLE_PREFIX_MISMATCH);
        }
        String body = body(handle);
        if (isReservedBody(body)) {
            failures.add(AccountReasonCode.HANDLE_RESERVED);
        }
        if (bannedWordFilter.containsBanned(body)) {
            failures.add(AccountReasonCode.HANDLE_BANNED_WORD);
        }
        return List.copyOf(failures);
    }

    /** 본문이 예약어인가 (대소문자 무시, 완전 일치). */
    public boolean isReservedBody(String body) {
        return body != null && reserved.contains(lower(body));
    }

    public static boolean matchesFormat(String handle) {
        return handle != null && FORMAT.matcher(handle).matches();
    }

    /** {@code go-}·{@code gi-} 접두어(없으면 빈 문자열). */
    public static String prefixOf(String handle) {
        var matcher = PREFIX.matcher(handle);
        return matcher.find() ? matcher.group() : "";
    }

    /** 접두어를 뺀 본문. */
    public static String body(String handle) {
        return handle.substring(prefixOf(handle).length());
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
