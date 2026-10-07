package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 닉네임 검사 (FR-022~027, 09 §2~§7, R-17). 가입(이메일·소셜)과 프로필 수정이 같은 정책을 쓴다.
 *
 * <pre>
 * ① 정리(앞뒤 공백 제거, NFC) → ② 형식 → ③ 글자 포함 → ④ 예약어(포함, 변형 4가지) → ⑤ 금칙어 → ⑥ 중복(대소문자 무시, 자기 자신 제외)
 * </pre>
 *
 * 형식(2~10자)은 DB {@code ck_member_nickname}과 같아야 하므로 코드 상수다.
 */
public class NicknamePolicy {

    /** 09 §2 형식 (DB {@code ck_member_nickname}과 같음). */
    public static final Pattern FORMAT = Pattern.compile("^[가-힣a-zA-Z0-9]{2,10}$");

    public static final int MAX_LENGTH = 10;

    private static final Pattern LETTER = Pattern.compile("[가-힣a-zA-Z]");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^가-힣a-zA-Z0-9]");

    private final VariantMatcher reserved;
    private final BannedWordFilter bannedWordFilter;
    private final NicknameLookup lookup;

    public NicknamePolicy(
            Collection<String> reservedNicknames,
            BannedWordFilter bannedWordFilter,
            NicknameLookup lookup) {
        this.reserved = new VariantMatcher(reservedNicknames, List.of());
        this.bannedWordFilter = bannedWordFilter;
        this.lookup = lookup;
    }

    /**
     * 검사한다. 정리한 값과 첫 실패 코드를 돌려준다.
     *
     * @param selfMemberId 프로필 수정이면 자기 회원 번호(중복에서 제외), 가입이면 null
     */
    public NicknameCheck check(String raw, Long selfMemberId) {
        String normalized = normalize(raw);
        return new NicknameCheck(normalized, firstFailure(normalized, selfMemberId));
    }

    /**
     * 소셜 이름으로 미리 채우기 (FR-027, 09 §7): 허용되지 않는 문자를 지우고 10자로 자른 뒤, {@link #check} 전체를 통과하면 그 값, 아니면
     * null("닉네임을 입력해 주세요").
     */
    public String suggestFromSocialName(String socialName) {
        if (socialName == null) {
            return null;
        }
        String cleaned = NOT_ALLOWED.matcher(normalize(socialName)).replaceAll("");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        NicknameCheck check = check(cleaned, null);
        return check.valid() ? check.normalized() : null;
    }

    /** ① 앞뒤 공백 제거 + NFC. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    }

    private AccountReasonCode firstFailure(String value, Long selfMemberId) {
        if (!FORMAT.matcher(value).matches()) {
            return AccountReasonCode.NICKNAME_INVALID_FORMAT;
        }
        if (!LETTER.matcher(value).find()) {
            return AccountReasonCode.NICKNAME_LETTER_REQUIRED;
        }
        if (reserved.matches(value)) {
            return AccountReasonCode.NICKNAME_RESERVED;
        }
        if (bannedWordFilter.containsBanned(value)) {
            return AccountReasonCode.NICKNAME_BANNED_WORD;
        }
        if (lookup.existsIgnoreCase(value, selfMemberId)) {
            return AccountReasonCode.NICKNAME_DUPLICATE;
        }
        return null;
    }
}
